package github.com.gengyoubo.mfcl.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/** Reports errors that can be established without starting the isolated runtime. */
public final class LegacyAnnotator implements Annotator {
    private static final String MLA = "github.com.gengyoubo.mfcl.api.mla.";
    @Override public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiAnnotation annotation)) return;
        String kind = annotation.getQualifiedName();
        if ((MLA + "LegacyType").equals(kind)) {
            checkType(annotation, holder);
        } else if ((MLA + "LegacyMethod").equals(kind) || (MLA + "LegacyField").equals(kind)
                || (MLA + "LegacyConstructor").equals(kind)) {
            boolean field = (MLA + "LegacyField").equals(kind);
            checkDescriptor(annotation, holder, !field);
            if (!(MLA + "LegacyConstructor").equals(kind)) checkName(annotation, holder, field);
        }
    }
    private static void checkType(PsiAnnotation annotation, AnnotationHolder holder) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
        PsiAnnotationMemberValue type = annotation.findDeclaredAttributeValue("type");
        // Constants that are not literals are left to Java and the runtime.
        if (value != null && !(value instanceof PsiLiteralExpression)) return;
        if (type != null && !(type instanceof PsiClassObjectAccessExpression)) return;
        boolean hasName = value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text && !text.isBlank();
        boolean hasType = type instanceof PsiClassObjectAccessExpression classObject && !PsiTypes.voidType().equals(classObject.getOperand().getType());
        if (hasName == hasType) error(holder, annotation, "@LegacyType must specify exactly one non-empty value or type");
    }
    private static void checkDescriptor(PsiAnnotation annotation, AnnotationHolder holder, boolean method) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("descriptor");
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text && !JvmDescriptor.valid(text, method))
            error(holder, literal, "Invalid JVM " + (method ? "method" : "field") + " descriptor");
    }
    private static void checkName(PsiAnnotation annotation, AnnotationHolder holder, boolean field) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("name");
        if (!(value instanceof PsiLiteralExpression literal) || !(literal.getValue() instanceof String name)) return;
        if (name.isBlank()) { error(holder, literal, "Legacy member name must not be empty"); return; }
        var spec = LegacyReferenceContributor.spec(literal);
        if (spec == null) return;
        var targets = LegacyReferenceContributor.targetClasses(spec);
        if (targets.isEmpty()) return;
        PsiClass target = targets.get(0);
        var file = target.getContainingFile();
        var virtual = file == null ? null : file.getVirtualFile();
        String path = virtual == null ? "" : virtual.getPath().replace('\\', '/');
        // Name-only stubs and modern Minecraft classes are not authoritative for the old runtime.
        if (path.contains("legacy-names-1.7.10.jar") || target.getMethods().length == 0 && target.getFields().length == 0) return;
        String owner = target.getQualifiedName();
        if (owner == null) return;
        if ((owner.startsWith("net.minecraft.") || owner.startsWith("net.minecraftforge.") || owner.startsWith("cpw.mods."))
                && !path.contains("minecraft-forge-1.7.10-mcp.jar")) return;
        // MCP JARs use mapped names; do not flag valid SRG names that need runtime mapping.
        if (path.contains("minecraft-forge-1.7.10-mcp.jar") && (name.startsWith("func_") || name.startsWith("field_"))) return;
        boolean found = field ? target.findFieldByName(name, true) != null : target.findMethodsByName(name, true).length > 0;
        if (!found) error(holder, literal, "No legacy " + (field ? "field" : "method") + " named '" + name + "' in " + owner);
    }
    private static void error(AnnotationHolder holder, PsiElement location, String message) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(location).create();
    }
}
