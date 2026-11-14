package github.com.gengyoubo.mfcl.idea;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ProcessingContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import org.jetbrains.annotations.NotNull;
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

    private record Spec(PsiAnnotation annotation, boolean field, boolean mixin) {}

    private static Spec spec(PsiLiteralExpression literal) {
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

    private static List<PsiClass> targetClasses(Spec spec) {
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
            PsiClass[] candidates = facade.findClasses(name, GlobalSearchScope.allScope(owner.getProject()));
            if (!spec.mixin()) {
                // MFCL generates memberless name stubs as well as a complete MCP compile JAR.
                Arrays.sort(candidates, Comparator.comparingInt(LegacyReferenceContributor::legacyRank));
            }
            if (candidates.length > 0) result.add(candidates[0]);
        }
        return result;
    }

    private static int legacyRank(PsiClass type) {
        var file = type.getContainingFile();
        var virtual = file == null ? null : file.getVirtualFile();
        String path = virtual == null ? "" : virtual.getPath().replace('\\', '/');
        if (path.contains("minecraft-forge-1.7.10-mcp.jar")) return 0;
        if (path.contains("legacy-names-1.7.10.jar")) return 2;
        return 1;
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
            List<Object> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (PsiClass target : targetClasses(spec)) {
                if (spec.field()) for (PsiField field : target.getAllFields()) {
                    if (seen.add(field.getName())) result.add(LookupElementBuilder.create(field, field.getName()).withTypeText(field.getType().getPresentableText()));
                } else for (PsiMethod method : target.getAllMethods()) {
                    if (!method.isConstructor() && seen.add(method.getName())) result.add(LookupElementBuilder.create(method, method.getName()));
                }
            }
            return result.toArray();
        }
    }
}
