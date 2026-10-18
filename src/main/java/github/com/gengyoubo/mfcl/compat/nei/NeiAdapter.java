package github.com.gengyoubo.mfcl.compat.nei;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

public final class NeiAdapter implements LegacyModAdapter {
    private NeiBackend backend;
    @Override public String id() { return "nei"; }
    @Override public boolean supports(Set<String> mods) { return mods.contains("NotEnoughItems"); }
    @Override public void beforeBoot(ClassLoader loader) throws Exception {
        loader.getClass().getMethod("registerBytecodeAdapter", String.class).invoke(loader,
                "github.com.gengyoubo.mfcl.legacy1710.compat.codechicken.CodeChickenNamespaceAdapter");
    }
    @Override public Map<String, Object> initialize(LegacyServices services) throws Exception {
        var field = services.loader().loadClass("codechicken.nei.ClientHandler").getDeclaredField("instance"); field.setAccessible(true);
        if (field.get(null) == null) throw new IllegalStateException("Original NEI client lifecycle did not initialize its handler");
        services.items().initializeContext(services.gameDirectory(), List.of("assets/minecraft/lang/en_US.lang", "assets/nei/lang/en_US.lang"));
        backend = new NeiBackend(services.items());
        var result = new LinkedHashMap<String, Object>();
        result.put("data", backend.initialize()); result.put("status", "READY");
        var table = backend.search("crafting").stream().filter(s -> s.key().equals("minecraft:crafting_table")).findFirst().orElseThrow();
        result.put("craftingTableRecipes", backend.recipes(table, false).size());
        result.put("craftingTableUsages", backend.recipes(table, true).size());
        return result;
    }
    @Override public void installClient() {
        if (backend == null) throw new IllegalStateException("Adapter is not initialized");
        NeiClientUi.install(backend);
        if (Boolean.getBoolean("mfcl.test.neiUiSmoke")) NeiHostSmokeProbe.install();
    }
}
