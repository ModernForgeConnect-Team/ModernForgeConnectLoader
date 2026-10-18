package github.com.gengyoubo.mfcl.compat.nei;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

public final class NeiAdapter implements LegacyModAdapter {
    private NeiBackend backend;
    private LegacyServices services;
    @Override public String id() { return "nei"; }
    @Override public boolean supports(Set<String> mods) { return mods.contains("NotEnoughItems"); }
    @Override public Map<String, Object> initialize(LegacyServices services) throws Exception {
        this.services = services;
        var field = services.loader().loadClass("codechicken.nei.ClientHandler").getDeclaredField("instance"); field.setAccessible(true);
        if (field.get(null) == null) throw new IllegalStateException("Original NEI client lifecycle did not initialize its handler");
        services.items().initializeContext(services.gameDirectory(), List.of("assets/minecraft/lang/en_US.lang", "assets/nei/lang/en_US.lang"));
        backend = new NeiBackend(services.items());
        services.containers().addInputHooks(new LegacyContainers.InputHooks() {
            private Object manager(Object screen) throws Exception { return services.loader().loadClass("codechicken.nei.guihook.GuiContainerManager")
                    .getMethod("getManager",services.loader().loadClass("net.minecraft.client.gui.inventory.GuiContainer")).invoke(null,screen); }
            public boolean mouse(Object screen,int x,int y,int button) throws Exception { var m=manager(screen);return (Boolean)m.getClass().getMethod("mouseClicked",int.class,int.class,int.class).invoke(m,x,y,button); }
            public boolean key(Object screen,char character,int key) throws Exception { var m=manager(screen);return (Boolean)m.getClass().getMethod("firstKeyTyped",char.class,int.class).invoke(m,character,key)
                    ||(Boolean)m.getClass().getMethod("lastKeyTyped",int.class,char.class).invoke(m,key,character); }
            public void release(Object screen,int x,int y,int button) throws Exception { var m=manager(screen);m.getClass().getMethod("mouseUp",int.class,int.class,int.class).invoke(m,x,y,button); }
        });
        var result = new LinkedHashMap<String, Object>();
        result.put("data", backend.initialize()); result.put("status", "READY");
        var table = backend.search("crafting").stream().filter(s -> s.key().equals("minecraft:crafting_table")).findFirst().orElseThrow();
        result.put("craftingTableRecipes", backend.recipes(table, false).size());
        result.put("craftingTableUsages", backend.recipes(table, true).size());
        return result;
    }
    @Override public void installClient() {
        if (backend == null) throw new IllegalStateException("Adapter is not initialized");
        NeiClientUi.install(backend, services.screens());
        if (Boolean.getBoolean("mfcl.test.neiUiSmoke")) NeiHostSmokeProbe.install();
    }
}
