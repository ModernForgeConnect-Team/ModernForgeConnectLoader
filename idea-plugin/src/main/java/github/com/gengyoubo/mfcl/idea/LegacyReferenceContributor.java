package github.com.gengyoubo.mfcl.idea;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ProcessingContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** References on annotation strings; the Java index supplies declarations and their navigation targets. */
public final class LegacyReferenceContributor extends PsiReferenceContributor {
    private static final String MLA = "github.com.gengyoubo.mfcl.api.mla.";
    private static final String MIXIN = "org.spongepowered.asm.mixin.Mixin";

    @Override public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), new PsiReferenceProvider() {
            @Override public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                PsiLiteralExpression literal = (PsiLiteralExpression) element;
                if (!(literal.getValue() instanceof String value)) return PsiReference.EMPTY_ARRAY;
                Spec spec = spec(literal);
                if (spec == null || !literal.getText().startsWith("\"")) return PsiReference.EMPTY_ARRAY;
                int end = value.indexOf('(');
                if (end < 0) end = value.indexOf(':');
                if (end < 0) end = value.length();
                // Escaped annotation values and selectors with explicit owner are intentionally left to other plugins.
                if (literal.getText().contains("\\") || value.startsWith("L") && value.contains(";")) return PsiReference.EMPTY_ARRAY;
                return new PsiReference[]{new MemberReference(literal, new TextRange(1, 1 + end), spec)};
            }
        });
    }

    record Spec(PsiAnnotation annotation, boolean field, boolean mixin) {}

    static Spec spec(PsiLiteralExpression literal) {
        PsiElement current = literal;
        while (current != null && !(current instanceof PsiNameValuePair) && !(current instanceof PsiAnnotation)) current = current.getParent();
        if (!(current instanceof PsiNameValuePair pair)) return null;
        PsiElement parent = pair.getParent();
        if (!(parent instanceof PsiAnnotationParameterList list) || !(list.getParent() instanceof PsiAnnotation annotation)) return null;
        String type = annotation.getQualifiedName(), name = pair.getName();
        if ((MLA + "LegacyMethod").equals(type) && "name".equals(name)) return new Spec(annotation, false, false);
        if ((MLA + "LegacyField").equals(type) && "name".equals(name)) return new Spec(annotation, true, false);
        if (type != null && type.startsWith("org.spongepowered.asm.mixin.injection.") && "method".equals(name))
            return new Spec(annotation, false, true);
        return null;
    }

    private static PsiClass enclosingClass(PsiAnnotation annotation) {
        PsiElement node = annotation;
        while (node != null && !(node instanceof PsiClass)) node = node.getParent();
        return (PsiClass) node;
    }

    private static void collectTargets(PsiAnnotationMemberValue value, List<String> result) {
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (var item : array.getInitializers()) collectTargets(item, result);
        } else if (value instanceof PsiClassObjectAccessExpression classObject) {
            PsiType type = classObject.getOperand().getType();
            if (type instanceof PsiClassType classType && classType.resolve() != null) {
                String name = classType.resolve().getQualifiedName();
                if (name != null) result.add(name);
            }
        } else if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String name && !name.isBlank()) {
            result.add(name.replace('$', '.'));
        }
    }

    static List<PsiClass> targetClasses(Spec spec) {
        PsiClass owner = enclosingClass(spec.annotation());
        if (owner == null) return List.of();
        PsiAnnotation target = owner.getAnnotation(spec.mixin() ? MIXIN : MLA + "LegacyType");
        if (target == null) return List.of();
        List<String> names = new ArrayList<>();
        collectTargets(target.findDeclaredAttributeValue("value"), names);
        collectTargets(target.findDeclaredAttributeValue(spec.mixin() ? "targets" : "type"), names);
        List<PsiClass> result = new ArrayList<>();
        var facade = JavaPsiFacade.getInstance(owner.getProject());
        for (String name : names) {
            List<PsiClass> candidates = new ArrayList<>(Arrays.asList(facade.findClasses(name, GlobalSearchScope.allScope(owner.getProject()))));
            if (!spec.mixin()) {
                // Gradle can put the memberless name stub on the host classpath while the actual JAR is only
                // present in an excluded build/ or run/ directory and therefore absent from IDEA's index.
                candidates.addAll(localLegacyClasses(owner, name));
                candidates.sort(Comparator.comparingInt(LegacyReferenceContributor::legacyRank));
            }
            if (!candidates.isEmpty()) result.add(candidates.get(0));
        }
        return result;
    }

    private static int legacyRank(PsiClass type) {
        var file = type.getContainingFile();
        var virtual = file == null ? null : file.getVirtualFile();
        String path = virtual == null ? "" : virtual.getPath().replace('\\', '/');
        if (path.contains("legacy-names-1.7.10.jar") || type.getMethods().length == 0 && type.getFields().length == 0) return 3;
        if (path.contains("minecraft-forge-1.7.10-mcp.jar")) return 0;
        if (path.contains("/mods/") || path.contains("/legacy-maven/") || path.contains("/legacy-dependencies/")) return 1;
        return 2;
    }

    private static List<PsiClass> localLegacyClasses(PsiClass owner, String binaryName) {
        String base = owner.getProject().getBasePath();
        if (base == null) return List.of();
        File root = new File(base);
        List<File> jars = new ArrayList<>();
        File direct = new File(root, "build/mfcl-direct");
        File[] sets = direct.listFiles(File::isDirectory);
        if (sets != null) for (File set : sets) {
            File jar = new File(set, "minecraft-forge-1.7.10-mcp.jar");
            if (jar.isFile()) jars.add(jar);
        }
        for (String directory : List.of("mods", "mods/1.7.10", "run/mods", "run/mods/1.7.10",
                "run/mfcl/legacy-maven/1.7.10", "run/mfcl/legacy-dependencies/1.7.10")) {
            File[] found = new File(root, directory).listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
            if (found != null) { Arrays.sort(found); Collections.addAll(jars, found); }
        }
        File[] origins = new File(root, ".idea/mfcl/legacy-stubs").listFiles(f -> f.isFile() && f.getName().endsWith(".origin"));
        if (origins != null) for (File origin : origins) {
            try {
                File jar = new File(Files.readString(origin.toPath()));
                if (jar.isFile() && jar.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) jars.add(jar);
            } catch (java.io.IOException | RuntimeException ignored) { /* An unavailable original provides no completion. */ }
        }
        List<PsiClass> result = new ArrayList<>();
        String path = binaryName.replace('.', '/') + ".class";
        for (File jar : jars) {
            VirtualFile local = LocalFileSystem.getInstance().findFileByIoFile(jar);
            if (local == null) local = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
            if (local == null) continue;
            VirtualFile archive = JarFileSystem.getInstance().getJarRootForLocalFile(local);
            VirtualFile entry = archive == null ? null : archive.findFileByRelativePath(path);
            PsiFile file = entry == null ? null : PsiManager.getInstance(owner.getProject()).findFile(entry);
            if (file instanceof PsiClassOwner classFile) for (PsiClass type : classFile.getClasses())
                if (binaryName.equals(type.getQualifiedName())) result.add(type);
        }
        return result;
    }

    static List<Object> variants(Spec spec) {
        List<Object> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PsiClass target : targetClasses(spec)) {
            if (spec.field()) for (PsiField field : target.getAllFields()) {
                if (seen.add(field.getName())) result.add(LookupElementBuilder.create(field, field.getName()).withTypeText(field.getType().getPresentableText()));
            } else for (PsiMethod method : target.getAllMethods()) {
                if (!method.isConstructor() && seen.add(method.getName())) result.add(LookupElementBuilder.create(method, method.getName()));
            }
        }
        return result;
    }

    private static final class MemberReference extends PsiPolyVariantReferenceBase<PsiLiteralExpression> {
        private final Spec spec;
        MemberReference(PsiLiteralExpression element, TextRange range, Spec spec) { super(element, range, true); this.spec = spec; }
        @Override public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
            String name = getValue();
            List<ResolveResult> result = new ArrayList<>();
            for (PsiClass target : targetClasses(spec)) {
                if (spec.field()) {
                    PsiField field = target.findFieldByName(name, true);
                    if (field != null) result.add(new PsiElementResolveResult(field));
                } else for (PsiMethod method : target.findMethodsByName(name, true)) result.add(new PsiElementResolveResult(method));
            }
            return result.toArray(ResolveResult.EMPTY_ARRAY);
        }
        @Override public Object @NotNull [] getVariants() {
            return variants(spec).toArray();
        }
    }
}
