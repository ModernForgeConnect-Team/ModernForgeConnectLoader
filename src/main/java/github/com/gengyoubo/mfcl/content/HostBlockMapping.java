package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Read-side vanilla block flattening. World mutations still use the separately validated write mapping. */
public final class HostBlockMapping {
    private final Map<Block,HostWorldAccess.Block> blocks=new IdentityHashMap<>();
    public HostBlockMapping(Set<String> oldKeys) {
        // Reuse the explicit old item variant table rather than maintaining a second list of wood/color names.
        for(int meta=0;meta<16;meta++)for(String key:oldKeys)if(key.startsWith("minecraft:")) {
            var stack=HostStackMapping.renderStack(new LegacyClientData.Stack(key,meta,1,"",""));
            if(stack.getItem() instanceof BlockItem item)blocks.putIfAbsent(item.getBlock(),new HostWorldAccess.Block(key,meta));
        }
        // Blocks without an item (air, fluids, crops...) can retain their registry identity.
        // Do not alias reused names: old snow is modern snow_block; old stone_stairs is cobblestone_stairs.
        for(String key:oldKeys)if(key.startsWith("minecraft:")&&HostStackMapping.renderStack(new LegacyClientData.Stack(key,0,1,"","")).isEmpty()) {
            var id=ResourceLocation.tryParse(key);
            if(BuiltInRegistries.BLOCK.containsKey(id))blocks.putIfAbsent(BuiltInRegistries.BLOCK.get(id),new HostWorldAccess.Block(key,0));
        }
        Map<String,String> blockOnly=Map.ofEntries(
                Map.entry("oak_sign","standing_sign"),Map.entry("oak_wall_sign","wall_sign"),
                Map.entry("redstone_wall_torch","redstone_torch"),Map.entry("wall_torch","torch"),
                Map.entry("carrots","carrots"),Map.entry("potatoes","potatoes"),
                Map.entry("nether_portal","portal"),Map.entry("tripwire","tripwire"),Map.entry("dead_bush","deadbush"));
        blockOnly.forEach((modern,old)->{
            if(oldKeys.contains("minecraft:"+old))blocks.put(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft",modern)),new HostWorldAccess.Block("minecraft:"+old,0));
        });
    }
    public HostWorldAccess.Block read(BlockState state) {
        var mapped=blocks.get(state.getBlock());
        if(mapped==null)throw new UnmappedLegacyBlockException("No old Block representation: "+state);
        String key=mapped.legacyKey();int meta=mapped.metadata();
        // Connection, shape and waterlogged properties do not change the original block/item variant.
        // Old fences/panes/stairs compute their connections from neighboring world cells.
        if(state.getBlock() instanceof RotatedPillarBlock&&state.hasProperty(BlockStateProperties.AXIS)) {
            var axis=state.getValue(BlockStateProperties.AXIS);
            if(key.equals("minecraft:log")||key.equals("minecraft:log2"))meta|=axis==Direction.Axis.X?4:axis==Direction.Axis.Z?8:0;
            else if(key.equals("minecraft:quartz_block")&&meta==2)meta=axis==Direction.Axis.X?3:axis==Direction.Axis.Z?4:2;
            else if(key.equals("minecraft:hay_block"))meta=axis==Direction.Axis.X?4:axis==Direction.Axis.Z?8:0;
        }
        if(state.getBlock() instanceof SlabBlock) {
            var slab=state.getValue(BlockStateProperties.SLAB_TYPE);
            if(slab==SlabType.DOUBLE)key=key.equals("minecraft:wooden_slab")?"minecraft:double_wooden_slab":"minecraft:double_stone_slab";
            else if(slab==SlabType.TOP)meta|=8;
        } else if(state.getBlock() instanceof StairBlock) {
            meta=switch(state.getValue(BlockStateProperties.HORIZONTAL_FACING)){case EAST->0;case WEST->1;case SOUTH->2;default->3;};
            if(state.getValue(BlockStateProperties.HALF)==Half.TOP)meta|=4;
        } else if(state.getBlock() instanceof FenceGateBlock) {
            meta=horizontal(state);if(state.getValue(BlockStateProperties.OPEN))meta|=4;
        } else if(state.getBlock() instanceof LeavesBlock) {
            if(state.getValue(BlockStateProperties.PERSISTENT))meta|=4;
        } else if(state.getBlock() instanceof LiquidBlock)meta=state.getValue(LiquidBlock.LEVEL);
        return new HostWorldAccess.Block(key,meta);
    }
    private static int horizontal(BlockState state) {
        return switch(state.getValue(BlockStateProperties.HORIZONTAL_FACING)){case SOUTH->0;case WEST->1;case NORTH->2;default->3;};
    }
}
