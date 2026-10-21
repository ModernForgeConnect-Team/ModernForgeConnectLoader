package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Keeps old sound bookkeeping consistent while the host owns playback and ticking. */
public final class LegacyAudio {
    @FunctionalInterface public interface Backend { void play(Object sound,Runnable finished) throws Exception; }
    private static final Map<ClassLoader,Backend> BACKENDS=Collections.synchronizedMap(new WeakHashMap<>());
    private LegacyAudio() {}
    public static void attach(ClassLoader loader,Backend backend){BACKENDS.put(loader,backend);}
    @SuppressWarnings("unchecked")
    public static void play(Object sound) throws Exception {
        ClassLoader loader=sound.getClass().getClassLoader();
        Backend backend=BACKENDS.get(loader);
        if(backend==null)throw new UnsupportedOperationException("Host audio is not connected for "+sound.getClass().getName());
        Class<?> mc=loader.loadClass("net.minecraft.client.Minecraft");Object client=mc.getMethod("func_71410_x").invoke(null);
        Object handler=mc.getMethod("func_147118_V").invoke(client);
        var managerField=handler.getClass().getDeclaredField("field_147694_f");managerField.setAccessible(true);
        Object manager=managerField.get(handler);var playingField=manager.getClass().getDeclaredField("field_148629_h");playingField.setAccessible(true);
        Map<Object,Object> playing=(Map<Object,Object>)playingField.get(manager);
        if(playing.containsValue(sound))return;
        String id=UUID.randomUUID().toString();playing.put(id,sound);
        Thread thread=Thread.currentThread();ClassLoader previous=thread.getContextClassLoader();
        try{
            thread.setContextClassLoader(backend.getClass().getClassLoader());
            backend.play(sound,()->playing.remove(id));
        }catch(Exception | Error failure){playing.remove(id);throw failure;}
        finally{thread.setContextClassLoader(previous);}
    }
}
