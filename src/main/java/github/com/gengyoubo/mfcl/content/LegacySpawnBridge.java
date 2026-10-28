package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import java.util.*;

/** Apply old addSpawn/removeSpawn weights and group bounds when the entity has a native Mob representation. */
public final class LegacySpawnBridge {
    private final ModernContentBridge content;
    private final Map<String,EntityType<?>> entities=new HashMap<>();
    LegacySpawnBridge(ModernContentBridge content)throws Exception{
        this.content=content;var loader=content.services().loader();
        content.services().items().inLegacy(()->{
            var old=(Map<?,?>)loader.loadClass("net.minecraft.entity.EntityList").getField("field_75625_b").get(null);
            var aliases=Map.of("mooshroom","MushroomCow","magma_cube","LavaSlime","zombified_piglin","PigZombie","iron_golem","VillagerGolem","snow_golem","SnowMan","horse","EntityHorse","ocelot","Ozelot","wither","WitherBoss");
            for(var entry:BuiltInRegistries.ENTITY_TYPE.entrySet())if(entry.getKey().location().getNamespace().equals("minecraft")){
                String name=entry.getKey().location().getPath(),requested=aliases.getOrDefault(name,name.replace("_",""));
                for(var original:old.entrySet())if(original.getKey().toString().equalsIgnoreCase(requested)&&loader.loadClass("net.minecraft.entity.EntityLiving").isAssignableFrom((Class<?>)original.getValue()))entities.put(((Class<?>)original.getValue()).getName(),entry.getValue());
            }
            for(var rule:LegacyRegistrations.spawns(loader))if(!entities.containsKey(rule.entity().getName()))LegacyDiagnostics.missingMapping("natural spawn "+rule.entity().getName(),"Original custom entity has only an Entity envelope, not a native Mob type; rule retained but not injected into NaturalSpawner");
            return null;
        });
        MinecraftForge.EVENT_BUS.addListener(this::spawns);
    }
    private static String category(MobCategory category){return switch(category){case MONSTER->"monster";case CREATURE->"creature";case AMBIENT->"ambient";case WATER_CREATURE->"waterCreature";default->"";};}
    private void spawns(LevelEvent.PotentialSpawns event){
        if(event.getLevel().isClientSide())return;String category=category(event.getMobCategory());if(category.isEmpty())return;
        Object biome;try{var holder=event.getLevel().getBiome(event.getPos());String name=holder.unwrapKey().orElseThrow().location().toString();biome=content.services().biomes().resolve(name);}catch(UnsupportedOperationException unmapped){return;}
        for(var rule:LegacyRegistrations.spawns(content.services().loader()))if(rule.category().equals(category)&&rule.biomes().contains(biome)){
            var type=entities.get(rule.entity().getName());if(type==null)continue;
            for(var old:List.copyOf(event.getSpawnerDataList()))if(old.type==type)event.removeSpawnerData(old);
            if(rule.weight()>0&&rule.min()>=0&&rule.max()>0&&rule.max()>=rule.min())event.addSpawnerData(new MobSpawnSettings.SpawnerData(type,rule.weight(),rule.min(),rule.max()));
        }
    }
}
