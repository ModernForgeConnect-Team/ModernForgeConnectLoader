package github.com.gengyoubo.mfcl.idea;

import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.nio.file.Path;

/** Import only an unambiguous old class name used in a LegacyType class literal. */
public final class LegacyTypeAutoImport implements ReferenceImporter {
    @Override public boolean isAddUnambiguousImportsOnTheFlyEnabled(@NotNull PsiFile file) {
        return file instanceof PsiJavaFile;
    }

    @Override public BooleanSupplier computeAutoImportAtOffset(@NotNull Editor editor, @NotNull PsiFile file,
                                                               int offset, boolean allowCaretNearReference) {
        if (!(file instanceof PsiJavaFile) || DumbService.isDumb(file.getProject())) return null;
        return ReadAction.compute(() -> {
            PsiElement leaf = file.findElementAt(offset);
            if (leaf == null && offset > 0) leaf = file.findElementAt(offset - 1);
            PsiJavaCodeReferenceElement reference = leaf == null ? null : PsiTreeUtil.getParentOfType(leaf, PsiJavaCodeReferenceElement.class, false);
            if (reference == null || reference.isQualified() || reference.resolve() != null) return null;
            PsiClassObjectAccessExpression literal = PsiTreeUtil.getParentOfType(reference, PsiClassObjectAccessExpression.class);
            PsiAnnotation annotation = literal == null ? null : PsiTreeUtil.getParentOfType(literal, PsiAnnotation.class);
            if (annotation == null || !"github.com.gengyoubo.mfcl.api.mla.LegacyType".equals(annotation.getQualifiedName())) return null;
            String simpleName = reference.getReferenceName();
            if (simpleName == null || simpleName.isBlank()) return null;
            Map<String, PsiClass> choices = new LinkedHashMap<>();
            for (var virtual : FilenameIndex.getVirtualFilesByName(file.getProject(), simpleName + ".class", reference.getResolveScope())) {
                String path = virtual.getPath().replace('\\', '/');
                if (!path.contains("legacy-names-1.7.10.jar!/") && !path.contains("/.idea/mfcl/legacy-stubs/")) continue;
                PsiFile source = PsiManager.getInstance(file.getProject()).findFile(virtual);
                if (source instanceof PsiClassOwner classFile) for (PsiClass candidate : classFile.getClasses()) {
                    String qualified = candidate.getQualifiedName();
                    if (qualified != null && simpleName.equals(candidate.getName())) choices.putIfAbsent(qualified, candidate);
                }
            }
            if (choices.isEmpty()) {
                String base = file.getProject().getBasePath();
                var found = LegacyProjectJars.find(base == null ? null : Path.of(base), simpleName);
                if (found.size() != 1 || hasConflictingImport((PsiJavaFile) file, simpleName, found.get(0).qualifiedName())) return null;
                var candidate = found.get(0);
                var referencePointer = SmartPointerManager.createPointer(reference);
                return () -> {
                    var current = referencePointer.getElement();
                    if (current == null || !current.isValid() || current.resolve() != null) return false;
                    var module = ModuleUtilCore.findModuleForPsiElement(current);
                    var local = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(candidate.jar());
                    var jarRoot = local == null ? null : JarFileSystem.getInstance().getJarRootForLocalFile(local);
                    if (module == null || jarRoot == null) return false;
                    WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
                        LegacyNamesAutoAttach.attachToModule(module, jarRoot, candidate.jar().getParent().getFileName().toString());
                        var replacement = JavaPsiFacade.getElementFactory(file.getProject()).createReferenceFromText(candidate.qualifiedName(), current);
                        PsiElement inserted = current.replace(replacement);
                        JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences(inserted);
                    });
                    return true;
                };
            }
            if (choices.size() != 1) return null;
            String qualified = choices.keySet().iterator().next();
            if (hasConflictingImport((PsiJavaFile) file, simpleName, qualified)) return null;
            var referencePointer = SmartPointerManager.createPointer(reference);
            var targetPointer = SmartPointerManager.createPointer(choices.get(qualified));
            return () -> {
                PsiJavaCodeReferenceElement current = referencePointer.getElement();
                PsiClass target = targetPointer.getElement();
                if (current == null || target == null || !current.isValid() || current.resolve() != null) return false;
                WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
                    PsiElement bound = current.bindToElement(target);
                    JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences(bound);
                });
                return true;
            };
        });
    }

    private static boolean hasConflictingImport(PsiJavaFile file, String simpleName, String qualified) {
        PsiImportList imports = file.getImportList();
        if (imports != null) for (PsiImportStatement statement : imports.getImportStatements()) {
            String existing = statement.getQualifiedName();
            if (!statement.isOnDemand() && existing != null && existing.endsWith("." + simpleName) && !existing.equals(qualified)) return true;
        }
        return false;
    }
}
