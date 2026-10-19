package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.HostBiomeQueries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** RenderChunkRegion exposes tint queries but not holders; read its current loaded client level. */
public final class ClientBiomeQueries {
    private ClientBiomeQueries(){}
    public static String key(BlockPos pos){
        var level=Minecraft.getInstance().level;
        if(level==null)throw new IllegalStateException("No client level for biome rendering");
        return HostBiomeQueries.key(level,pos);
    }
}
