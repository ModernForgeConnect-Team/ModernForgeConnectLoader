package github.com.gengyoubo.mfcl.idea;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/** Navigate LegacyType class literals to the real old class instead of the name-only compile stub. */
public final class LegacyTypeGotoDeclaration implements GotoDeclarationHandler {
    @Override public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) return null;
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(sourceElement, PsiAnnotation.class, false);
        if (annotation == null || !"github.com.gengyoubo.mfcl.api.mla.LegacyType".equals(annotation.getQualifiedName())) return null;
        PsiAnnotationMemberValue type = annotation.findDeclaredAttributeValue("type");
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
        boolean inType = type instanceof PsiClassObjectAccessExpression classObject
                && PsiTreeUtil.isAncestor(classObject.getOperand(), sourceElement, false);
        boolean inValue = value instanceof PsiLiteralExpression && PsiTreeUtil.isAncestor(value, sourceElement, false);
        if (!inType && !inValue) return null;
        var candidates = LegacyReferenceContributor.targetClasses(annotation, false);
        if (candidates.size() != 1) return null;
        PsiClass target = candidates.get(0);
        var file = target.getContainingFile();
        var virtual = file == null ? null : file.getVirtualFile();
        String path = virtual == null ? "" : virtual.getPath().replace('\\', '/');
        if (path.contains("legacy-names-1.7.10.jar") || path.contains("/.idea/mfcl/legacy-stubs/")) return null;
        String name = target.getQualifiedName();
        if (name == null) return null;
        if ((name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.") || name.startsWith("cpw.mods."))
                && !path.contains("minecraft-forge-1.7.10-mcp.jar") && !path.contains("minecraft-forge-1.7.10-srg.jar")) return null;
        return new PsiElement[]{target};
    }
}
