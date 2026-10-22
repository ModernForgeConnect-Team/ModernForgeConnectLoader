package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.*;
import org.joml.Vector3f;
import java.util.Map;

/** The named particles of 1.7.10 RenderGlobal.doSpawnParticle, translated to the host particle types. */
final class LegacyParticles {
    private static final Map<String,ParticleOptions> SIMPLE=Map.ofEntries(
            Map.entry("bubble",ParticleTypes.BUBBLE),Map.entry("suspended",ParticleTypes.UNDERWATER),Map.entry("depthsuspend",ParticleTypes.MYCELIUM),
            Map.entry("townaura",ParticleTypes.MYCELIUM),Map.entry("crit",ParticleTypes.CRIT),Map.entry("magicCrit",ParticleTypes.ENCHANTED_HIT),
            Map.entry("smoke",ParticleTypes.SMOKE),Map.entry("largesmoke",ParticleTypes.LARGE_SMOKE),Map.entry("spell",ParticleTypes.EFFECT),
            Map.entry("instantSpell",ParticleTypes.INSTANT_EFFECT),Map.entry("witchMagic",ParticleTypes.WITCH),Map.entry("note",ParticleTypes.NOTE),
            Map.entry("portal",ParticleTypes.PORTAL),Map.entry("enchantmenttable",ParticleTypes.ENCHANT),Map.entry("explode",ParticleTypes.POOF),
            Map.entry("flame",ParticleTypes.FLAME),Map.entry("lava",ParticleTypes.LAVA),Map.entry("splash",ParticleTypes.SPLASH),
            Map.entry("wake",ParticleTypes.FISHING),Map.entry("cloud",ParticleTypes.CLOUD),Map.entry("snowballpoof",ParticleTypes.ITEM_SNOWBALL),
            Map.entry("dripWater",ParticleTypes.DRIPPING_WATER),Map.entry("dripLava",ParticleTypes.DRIPPING_LAVA),Map.entry("snowshovel",ParticleTypes.ITEM_SNOWBALL),
            Map.entry("slime",ParticleTypes.ITEM_SLIME),Map.entry("heart",ParticleTypes.HEART),Map.entry("angryVillager",ParticleTypes.ANGRY_VILLAGER),
            Map.entry("happyVillager",ParticleTypes.HAPPY_VILLAGER),Map.entry("largeexplode",ParticleTypes.EXPLOSION),
            Map.entry("hugeexplosion",ParticleTypes.EXPLOSION_EMITTER),Map.entry("fireworksSpark",ParticleTypes.FIREWORK),
            Map.entry("mobSpell",ParticleTypes.ENTITY_EFFECT),Map.entry("mobSpellAmbient",ParticleTypes.AMBIENT_ENTITY_EFFECT));
    private LegacyParticles() {}
    static void spawn(Level level,String name,double x,double y,double z,double vx,double vy,double vz) {
        if(name==null)return;
        if(name.equals("reddust")) {
            // Old redstone dust takes its colour from the velocity; a zero red component meant the default red.
            float red=vx==0?1:(float)vx;
            level.addParticle(new DustParticleOptions(new Vector3f(red,(float)vy,(float)vz),1),x,y,z,0,0,0);
            return;
        }
        if(name.equals("footstep"))return; // Ground decal with no host particle counterpart.
        var type=SIMPLE.get(name);
        if(type==null) {
            // iconcrack_/blockcrack_/blockdust_ carry old numeric ids that the host registry cannot resolve.
            LegacyDiagnostics.missingMapping("particle "+(name.indexOf('_')>0?name.substring(0,name.indexOf('_')+1)+"*":name),"legacy named particle has no host translation");
            return;
        }
        level.addParticle(type,x,y,z,vx,vy,vz);
    }
}
