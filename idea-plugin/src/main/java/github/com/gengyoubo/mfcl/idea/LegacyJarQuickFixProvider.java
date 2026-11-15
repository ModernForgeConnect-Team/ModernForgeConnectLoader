package github.com.gengyoubo.mfcl.idea;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

/** Adds a name-only IDE library for a user-selected old JAR at a LegacyType class literal. */
public final class LegacyJarQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
    @Override public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() { return PsiJavaCodeReferenceElement.class; }
    @Override public void registerFixes(@NotNull PsiJavaCodeReferenceElement reference, @NotNull QuickFixActionRegistrar registrar) {
        PsiClassObjectAccessExpression literal = PsiTreeUtil.getParentOfType(reference, PsiClassObjectAccessExpression.class);
        PsiAnnotation annotation = literal == null ? null : PsiTreeUtil.getParentOfType(literal, PsiAnnotation.class);
        if (annotation == null || !"github.com.gengyoubo.mfcl.api.mla.LegacyType".equals(annotation.getQualifiedName())) return;
        String name = reference.getReferenceName();
        if (name == null || name.isBlank()) return;
        registrar.register(new AddLegacyJarFix(reference, name));
    }

    private static final class AddLegacyJarFix implements IntentionAction {
        private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> reference;
        private final String name;
        AddLegacyJarFix(PsiJavaCodeReferenceElement element, String name) {
            reference = SmartPointerManager.createPointer(element); this.name = name;
        }
        @Override public @NotNull String getText() { return "Resolve legacy type using MFCL"; }
        @Override public @NotNull String getFamilyName() { return "MFCL legacy dependencies"; }
        @Override public boolean startInWriteAction() { return false; }
        @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            PsiJavaCodeReferenceElement element = reference.getElement();
            return element != null && element.isValid() && element.resolve() == null
                    && project.getBasePath() != null && ModuleUtilCore.findModuleForPsiElement(element) != null;
        }
        @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
            PsiJavaCodeReferenceElement element = reference.getElement();
            if (element == null || project.getBasePath() == null) return;
            Module module = ModuleUtilCore.findModuleForPsiElement(element);
            if (module == null) return;
            var candidates = LegacyProjectJars.find(Path.of(project.getBasePath()), name);
            if (!candidates.isEmpty()) {
                int selectedIndex = 0;
                if (candidates.size() > 1) {
                    String[] names = candidates.stream().map(LegacyProjectJars.Candidate::qualifiedName).toArray(String[]::new);
                    selectedIndex = Messages.showChooseDialog(project, "Select the class to use:", "MFCL Legacy Classes",
                            Messages.getQuestionIcon(), names, names[0]);
                    if (selectedIndex < 0) return;
                }
                var candidate = candidates.get(selectedIndex);
                VirtualFile local = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(candidate.jar());
                VirtualFile jarRoot = local == null ? null : JarFileSystem.getInstance().getJarRootForLocalFile(local);
                if (jarRoot == null) { Messages.showErrorDialog(project, "Cannot open generated name-only JAR", "MFCL Legacy Classes"); return; }
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    LegacyNamesAutoAttach.attachToModule(module, jarRoot, candidate.jar().getParent().getFileName().toString());
                    PsiJavaCodeReferenceElement current = reference.getElement();
                    if (current != null && current.isValid()) {
                        var replacement = JavaPsiFacade.getElementFactory(project).createReferenceFromText(candidate.qualifiedName(), current);
                        PsiElement inserted = current.replace(replacement);
                        JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
                    }
                });
                return;
            }
            VirtualFile selected = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
                    .withTitle("Select an existing 1.7.10 JAR"), project, null);
            if (selected == null) return;
            Path original = Path.of(selected.getPath());
            Path destination = Path.of(project.getBasePath(), ".idea", "mfcl", "legacy-stubs");
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Preparing MFCL legacy class names", false) {
                private LegacyStubJar.Result result;
                private Exception failure;
                @Override public void run(@NotNull ProgressIndicator indicator) {
                    try { result = LegacyStubJar.create(original, destination, name); }
                    catch (Exception error) { failure = error; }
                }
                @Override public void onSuccess() {
                    if (project.isDisposed() || module.isDisposed()) return;
                    if (failure != null) { Messages.showErrorDialog(project, failure.getMessage(), "MFCL Legacy JAR"); return; }
                    String selectedClass = result.matches().get(0);
                    if (result.matches().size() > 1) {
                        String[] names = result.matches().toArray(String[]::new);
                        int index = Messages.showChooseDialog(project, "Select the class to use:", "MFCL Legacy JAR",
                                Messages.getQuestionIcon(), names, names[0]);
                        if (index < 0) return;
                        selectedClass = names[index];
                    }
                    VirtualFile local = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(result.jar());
                    VirtualFile jarRoot = local == null ? null : JarFileSystem.getInstance().getJarRootForLocalFile(local);
                    if (jarRoot == null) { Messages.showErrorDialog(project, "Cannot open generated name-only JAR", "MFCL Legacy JAR"); return; }
                    String className = selectedClass;
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
                        try {
                            String libraryName = "MFCL legacy names " + result.jar().getFileName();
                            var table = model.getModuleLibraryTable();
                            var library = table.getLibraryByName(libraryName);
                            if (library == null) {
                                library = table.createLibrary(libraryName);
                                var libraryModel = library.getModifiableModel();
                                libraryModel.addRoot(jarRoot, OrderRootType.CLASSES); libraryModel.commit();
                                var order = model.findLibraryOrderEntry(library);
                                if (order != null) order.setScope(DependencyScope.PROVIDED);
                            }
                            model.commit();
                        } finally { if (!model.isDisposed()) model.dispose(); }
                        PsiJavaCodeReferenceElement current = reference.getElement();
                        if (current != null && current.isValid()) {
                            PsiJavaCodeReferenceElement replacement = JavaPsiFacade.getElementFactory(project).createReferenceFromText(className, current);
                            PsiElement inserted = current.replace(replacement);
                            JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
                        }
                    });
                    Messages.showInfoMessage(project, "Name-only IDE dependency added. For Gradle builds and the legacy runtime, "
                            + "also configure this JAR in mfcl.direct / the game instance. Gradle reimport may remove IDE-only libraries.", "MFCL Legacy JAR");
                }
            });
        }
    }
}
