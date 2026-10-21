package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import java.util.*;

/** Typed old spawner views for original structure generators; native spawners own saving and ticking. */
final class HostSpawnerTiles {
    private final ModernContentBridge content;
    private final ServerLevel level;
    private final Map<SpawnerBlockEntity,Object> views=new WeakHashMap<>();
    HostSpawnerTiles(ModernContentBridge content,ServerLevel level){this.content=content;this.level=level;}
    Object find(SpawnerBlockEntity tile) {
        if(tile.isRemoved()||!level.hasChunkAt(tile.getBlockPos())||level.getBlockEntity(tile.getBlockPos())!=tile)return null;
        Object view=views.get(tile);if(view!=null)return view;
        try {
            var pos=tile.getBlockPos();var services=content.services();
            view=services.tiles().create("minecraft:mob_spawner",0,services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ());
            var loader=services.loader();var nbt=loader.loadClass("net.minecraft.nbt.NBTTagCompound");
            Object data=nbt.getConstructor().newInstance();
            var oldTile=loader.loadClass("net.minecraft.tileentity.TileEntity");oldTile.getMethod("func_145841_b",nbt).invoke(view,data);
            String modern=tile.saveWithoutMetadata().getCompound("SpawnData").getCompound("entity").getString("id");
            var id=ResourceLocation.tryParse(modern);
            if(id!=null&&id.getNamespace().equals("minecraft")){
                String old=switch(id.getPath()){
                    case "zombified_piglin"->"PigZombie";case "magma_cube"->"LavaSlime";case "horse"->"EntityHorse";
                    case "snow_golem"->"SnowMan";case "iron_golem"->"VillagerGolem";case "wither"->"WitherBoss";
                    default->{var text=new StringBuilder();for(String part:id.getPath().split("_"))if(!part.isEmpty())text.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));yield text.toString();}
                };
                nbt.getMethod("func_74778_a",String.class,String.class).invoke(data,"EntityId",old);
                oldTile.getMethod("func_145839_a",nbt).invoke(view,data);
            }
            views.put(tile,view);return view;
        }catch(Exception failure){throw new IllegalStateException("Cannot expose native spawner to original structure generator",failure);}
    }
    boolean owns(SpawnerBlockEntity tile,Object old){return views.get(tile)==old;}
    void type(BlockPos pos,Object logic,String original) {
        if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity tile))return;
        Object view=views.get(tile);if(view==null)return;
        try {
            if(content.services().loader().loadClass("net.minecraft.tileentity.TileEntityMobSpawner").getMethod("func_145881_a").invoke(view)!=logic)return;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot identify original spawner view",failure);}
        String path=switch(original){
            case "PigZombie"->"zombified_piglin";case "LavaSlime"->"magma_cube";case "EntityHorse"->"horse";
            case "SnowMan"->"snow_golem";case "VillagerGolem"->"iron_golem";case "WitherBoss"->"wither";
            default->original.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
        };
        var key=ResourceLocation.tryParse(path.contains(":")?path:"minecraft:"+path);
        if(key==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(key))throw new UnsupportedOperationException("No host spawner entity mapping for "+original);
        content.beforeMutation(level,pos);
        tile.setEntityId(BuiltInRegistries.ENTITY_TYPE.get(key),level.random);
        tile.setChanged();content.afterCommit(level,()->{
            if(level.getBlockEntity(pos)!=tile)return;
            var state=level.getBlockState(pos);level.sendBlockUpdated(pos,state,state,3);
        });
    }
}
