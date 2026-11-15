package github.com.gengyoubo.mfcl.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keep existing Gradle-generated name-only JARs visible to IDEA, including after Gradle reimport. */
public final class LegacyNamesAutoAttach implements StartupActivity {
    @Override public void runActivity(@NotNull Project project) {
        AtomicBoolean queued = new AtomicBoolean();
        Runnable schedule = () -> {
            if (project.isDisposed() || !queued.compareAndSet(false, true)) return;
            ApplicationManager.getApplication().invokeLater(() -> {
                queued.set(false);
                if (!project.isDisposed()) attach(project);
            });
        };
        var connection = project.getMessageBus().connect(project);
        connection.subscribe(ProjectDataImportListener.TOPIC, new ProjectDataImportListener() {
            @Override public void onImportFinished(@NotNull String path) { schedule.run(); }
        });
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override public void after(@NotNull List<? extends VFileEvent> events) {
                if (events.stream().anyMatch(event -> event.getPath().endsWith("/legacy-names-1.7.10.jar"))) schedule.run();
            }
        });
        schedule.run();
    }

    private static void attach(Project project) {
        String base = project.getBasePath();
        if (base == null) return;
        File sourceSets = new File(base, "build/mfcl-direct");
        File[] directories = sourceSets.listFiles(File::isDirectory);
        if (directories == null) return;
        VirtualFile projectRoot = LocalFileSystem.getInstance().findFileByIoFile(new File(base));
        if (projectRoot == null) return;
        for (File directory : directories) {
            File jar = new File(directory, "legacy-names-1.7.10.jar");
            if (!jar.isFile()) continue;
            VirtualFile local = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
            VirtualFile root = local == null ? null : JarFileSystem.getInstance().getJarRootForLocalFile(local);
            if (root == null) continue;
            ApplicationManager.getApplication().runWriteAction(() -> {
                for (Module module : ModuleManager.getInstance(project).getModules()) {
                    if (module.isDisposed()) continue;
                    ModuleRootManager current = ModuleRootManager.getInstance(module);
                    boolean belongs = java.util.Arrays.stream(current.getContentRoots())
                            .anyMatch(content -> VfsUtilCore.isAncestor(projectRoot, content, false));
                    if (!belongs || current.getSourceRoots().length == 0 || contains(current, root)) continue;
                    attachToModule(module, root, directory.getName());
                }
            });
        }
    }

    /** Call on the EDT inside a write action. */
    static void attachToModule(Module module, VirtualFile root, String sourceSet) {
        ModuleRootManager current = ModuleRootManager.getInstance(module);
        if (contains(current, root)) return;
        ModifiableRootModel model = current.getModifiableModel();
        try {
            String name = "MFCL generated names " + sourceSet;
            var table = model.getModuleLibraryTable();
            var library = table.getLibraryByName(name);
            if (library == null) {
                library = table.createLibrary(name);
                var libraryModel = library.getModifiableModel();
                libraryModel.addRoot(root, OrderRootType.CLASSES);
                libraryModel.commit();
                var entry = model.findLibraryOrderEntry(library);
                if (entry != null) entry.setScope(DependencyScope.PROVIDED);
            }
            model.commit();
        } finally { if (!model.isDisposed()) model.dispose(); }
    }

    private static boolean contains(ModuleRootManager model, VirtualFile root) {
        for (OrderEntry entry : model.getOrderEntries()) {
            if (entry instanceof LibraryOrderEntry library) {
                for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) if (file.equals(root)) return true;
            }
        }
        return false;
    }
}
