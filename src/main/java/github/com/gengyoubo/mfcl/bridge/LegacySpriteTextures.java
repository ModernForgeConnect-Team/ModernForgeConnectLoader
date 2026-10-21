package github.com.gengyoubo.mfcl.bridge;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.*;

/** Exports the pixels produced by original custom sprite loaders, including their animation metadata. */
final class LegacySpriteTextures {
    record Texture(byte[] png,String metadata) {}
    static void event(ClassLoader loader,Object atlas,String phase) throws Exception {
        Class<?> event=loader.loadClass("net.minecraftforge.client.event.TextureStitchEvent$"+phase);
        Object value=event.getConstructor(loader.loadClass("net.minecraft.client.renderer.texture.TextureMap")).newInstance(atlas);
        Object bus=loader.loadClass("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
        bus.getClass().getMethod("post",loader.loadClass("cpw.mods.fml.common.eventhandler.Event")).invoke(bus,value);
    }
    static Map<String,Texture> load(ClassLoader loader,Object manager,Map<?,?> entries) throws Exception {
        Class<?> sprite=loader.loadClass("net.minecraft.client.renderer.texture.TextureAtlasSprite");
        Class<?> resources=loader.loadClass("net.minecraft.client.resources.IResourceManager");
        Class<?> location=loader.loadClass("net.minecraft.util.ResourceLocation");
        var custom=new LinkedHashMap<String,Object>();
        for(var entry:new ArrayList<>(entries.entrySet())) {
            Object path=location.getConstructor(String.class).newInstance((String)entry.getKey());
            if((boolean)sprite.getMethod("hasCustomLoader",resources,location).invoke(entry.getValue(),manager,path)) {
                sprite.getMethod("load",resources,location).invoke(entry.getValue(),manager,path);
                custom.put((String)entry.getKey(),entry.getValue());
            }
        }
        var result=new LinkedHashMap<String,Texture>();
        var encoded=new IdentityHashMap<Object,Texture>();
        for(var entry:custom.entrySet()) {
            Object pixels=pixels(sprite,entry.getValue(),Collections.newSetFromMap(new IdentityHashMap<>()));
            if(pixels==null)continue;
            Texture cached=encoded.get(pixels);if(cached!=null){result.put(entry.getKey(),cached);continue;}
            int width=(int)sprite.getMethod("func_94211_a").invoke(pixels),height=(int)sprite.getMethod("func_94216_b").invoke(pixels);
            int count=(int)sprite.getMethod("func_110970_k").invoke(pixels);
            if(width<=0||height<=0)continue;
            var image=new BufferedImage(width,height*count,BufferedImage.TYPE_INT_ARGB);
            for(int frame=0;frame<count;frame++) {
                int[][] data=(int[][])sprite.getMethod("func_147965_a",int.class).invoke(pixels,frame);
                if(data!=null&&data[0]!=null)image.setRGB(0,height*frame,width,height,data[0],0,width);
            }
            String metadata=null;
            Field animation=sprite.getDeclaredField("field_110982_k");animation.setAccessible(true);
            Object timing=animation.get(pixels);
            if(timing!=null&&count>1) {
                var values=new LinkedHashMap<String,Object>();
                values.put("frametime",timing.getClass().getMethod("func_110469_d").invoke(timing));
                int frames=(int)timing.getClass().getMethod("func_110473_c").invoke(timing);
                if(frames>0) {
                    var sequence=new ArrayList<Object>();
                    for(int index=0;index<frames;index++)sequence.add(Map.of("index",timing.getClass().getMethod("func_110468_c",int.class).invoke(timing,index),
                            "time",timing.getClass().getMethod("func_110472_a",int.class).invoke(timing,index)));
                    values.put("frames",sequence);
                }
                metadata=new com.google.gson.Gson().toJson(Map.of("animation",values));
            }
            var output=new ByteArrayOutputStream();
            try(var memory=new javax.imageio.stream.MemoryCacheImageOutputStream(output)){javax.imageio.ImageIO.write(image,"png",memory);}
            var texture=new Texture(output.toByteArray(),metadata);encoded.put(pixels,texture);result.put(entry.getKey(),texture);
        }
        return result;
    }
    /** A loader may deduplicate a sprite by retaining another sprite instead of allocating duplicate frame data. */
    private static Object pixels(Class<?> sprite,Object value,Set<Object> seen) throws Exception {
        if(!seen.add(value))return null;
        if((int)sprite.getMethod("func_110970_k").invoke(value)>0)return value;
        for(Class<?> type=value.getClass();type!=null&&sprite.isAssignableFrom(type);type=type.getSuperclass())
            for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&sprite.isAssignableFrom(field.getType())) {
                field.setAccessible(true);Object other=field.get(value);
                if(other!=null) {Object found=pixels(sprite,other,seen);if(found!=null)return found;}
            }
        return null;
    }
}
