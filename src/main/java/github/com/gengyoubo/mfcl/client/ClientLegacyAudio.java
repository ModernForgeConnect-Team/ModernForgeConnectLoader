package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Runs original ITickableSound state updates through the modern audio engine. */
final class ClientLegacyAudio {
    private ClientLegacyAudio() {}
    static void install(LegacyServices services){
        var active=new java.util.IdentityHashMap<OriginalSound,Integer>();
        LegacyAudio.attach(services.loader(),(sound,finished)->{
            var instance=new OriginalSound(services,sound,finished);
            Minecraft.getInstance().getSoundManager().play(instance);active.put(instance,0);
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent event)->{
            if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
            var mc=Minecraft.getInstance();
            active.entrySet().removeIf(entry->{
                int age=entry.getValue()+1;entry.setValue(age);var sound=entry.getKey();
                if(mc.level==null||sound.isStopped()||age>5&&!mc.getSoundManager().isActive(sound)){sound.finish();return true;}
                return false;
            });
        });
    }
    private static final class OriginalSound extends AbstractTickableSoundInstance {
        private final LegacyServices services;
        private final Object original;
        private final Class<?> type,tickable;
        private final Runnable finished;
        private boolean completed;
        OriginalSound(LegacyServices services,Object original,Runnable finished) throws Exception {
            super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(LegacySounds.modernId(original.getClass().getMethod("func_147650_b").invoke(original).toString()))),SoundSource.BLOCKS,RandomSource.create());
            this.services=services;this.original=original;this.finished=finished;
            type=services.loader().loadClass("net.minecraft.client.audio.ISound");tickable=services.loader().loadClass("net.minecraft.client.audio.ITickableSound");
            looping=(boolean)type.getMethod("func_147657_c").invoke(original);
            delay=(int)type.getMethod("func_147652_d").invoke(original);
            attenuation="NONE".equals(type.getMethod("func_147656_j").invoke(original).toString())?SoundInstance.Attenuation.NONE:SoundInstance.Attenuation.LINEAR;
            refresh();
        }
        private void refresh() throws Exception {
            volume=(float)type.getMethod("func_147653_e").invoke(original);pitch=(float)type.getMethod("func_147655_f").invoke(original);
            x=(float)type.getMethod("func_147649_g").invoke(original);y=(float)type.getMethod("func_147654_h").invoke(original);z=(float)type.getMethod("func_147651_i").invoke(original);
        }
        void finish(){if(!completed){completed=true;stop();finished.run();}}
        @Override public boolean canStartSilent(){return tickable.isInstance(original);}
        @Override public void tick() {
            try {
                boolean done=services.items().inLegacy(()->{
                    if(tickable.isInstance(original)) {
                        services.loader().loadClass("net.minecraft.server.gui.IUpdatePlayerListBox").getMethod("func_73660_a").invoke(original);
                        if((boolean)tickable.getMethod("func_147667_k").invoke(original))return true;
                    }
                    refresh();return false;
                });
                if(done||Minecraft.getInstance().level==null)finish();
            }catch(Exception failure){finish();com.mojang.logging.LogUtils.getLogger().warn("MFCL original sound update failed",failure);}
        }
    }
}
