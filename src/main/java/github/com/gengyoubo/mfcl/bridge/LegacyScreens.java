package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;

/** Actual old GuiScreen lifecycle/input semantics. Rendering capture is a separate, currently unsupported capability. */
public final class LegacyScreens {
    public static boolean guiBridgeEnabled() { return Boolean.parseBoolean(System.getProperty("mfcl.legacyGui", "true")); }
    private final ClassLoader loader;
    public LegacyScreens(ClassLoader loader) { this.loader = loader; }
    public Session openDefault(int width, int height) throws Exception {
        return open(loader.loadClass("net.minecraft.client.gui.GuiScreen").getConstructor().newInstance(), width, height);
    }
    public Session open(Object oldScreen, int width, int height) throws Exception {
        Class<?> screen = loader.loadClass("net.minecraft.client.gui.GuiScreen");
        if (!screen.isInstance(oldScreen)) throw new IllegalArgumentException("Screen belongs to another runtime");
        return new Session(oldScreen, width, height);
    }
    public final class Session implements AutoCloseable {
        private final Object screen, client, previous;
        private final Thread owner = Thread.currentThread();
        private boolean closed;
        private Session(Object screen, int width, int height) throws Exception {
            this.screen = screen;
            var mc = loader.loadClass("net.minecraft.client.Minecraft"); client = mc.getMethod("func_71410_x").invoke(null);
            previous = mc.getField("field_71462_r").get(client); mc.getField("field_71462_r").set(client, screen);
            try { resize(width, height); } catch(Exception failure) { mc.getField("field_71462_r").set(client,previous);closed=true;throw failure; }
        }
        private void check() {
            if (closed) throw new IllegalStateException("Legacy screen session is closed");
            if (Thread.currentThread() != owner) throw new IllegalStateException("Legacy screen must run on its owner thread");
        }
        public Object legacyObject() { check(); return screen; }
        public void draw(int x,int y,float partialTick) throws Exception {
            check();screen.getClass().getMethod("func_73863_a",int.class,int.class,float.class).invoke(screen,x,y,partialTick);
        }
        public void tick() throws Exception { check();screen.getClass().getMethod("func_73876_c").invoke(screen); }
        public void resize(int width, int height) throws Exception {
            check(); if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid GUI size");
            screen.getClass().getMethod("func_146280_a", loader.loadClass("net.minecraft.client.Minecraft"), int.class, int.class).invoke(screen, client, width, height);
        }
        public void mousePressed(int x, int y, int button) throws Exception { invokeProtected("func_73864_a", new Class<?>[]{int.class, int.class, int.class}, x, y, button); }
        public void mouseReleased(int x,int y,int button) throws Exception { invokeProtected("func_146286_b",new Class<?>[]{int.class,int.class,int.class},x,y,button); }
        public void keyPressed(char character, int legacyKey) throws Exception { invokeProtected("func_73869_a", new Class<?>[]{char.class, int.class}, character, legacyKey); }
        private void invokeProtected(String name, Class<?>[] signature, Object... values) throws Exception {
            check(); Method method = loader.loadClass("net.minecraft.client.gui.GuiScreen").getDeclaredMethod(name, signature); method.setAccessible(true); method.invoke(screen, values);
        }
        @Override public void close() throws Exception {
            check(); try { screen.getClass().getMethod("func_146281_b").invoke(screen); }
            finally { closed = true;loader.loadClass("net.minecraft.client.Minecraft").getField("field_71462_r").set(client, previous); }
        }
    }
}
