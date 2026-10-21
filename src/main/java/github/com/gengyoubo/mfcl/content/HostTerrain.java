package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Import detached base terrain without thousands of gameplay placement callbacks and update packets. */
final class HostTerrain {
    private HostTerrain(){}
    static void copy(ModernContentBridge content,LegacyGeneration generation,ServerLevel level,int cx,int cz) throws Exception {
        var chunk=level.getChunk(cx,cz);
        var states=new BlockState[65536];
        var palette=new HashMap<HostWorldAccess.Block,BlockState>();
        // Resolve the entire palette first. An absent mapping must not leave a half-written chunk marked complete.
        generation.terrain(level,cx,cz,(x,y,z,block)->{
            if(y>=0&&y<256)states[(y<<8)|((z&15)<<4)|(x&15)]=palette.computeIfAbsent(block,content::modernBlock);
        });
        var light=level.getChunkSource().getLightEngine();
        for(int sy=0;sy<16;sy++) {
            int sectionIndex=chunk.getSectionIndex(sy<<4);
            var section=chunk.getSection(sectionIndex);boolean wasEmpty=section.hasOnlyAir();
            section.acquire();
            try {
                for(int local=0;local<4096;local++) {
                    var state=states[(sy<<12)|local];if(state==null)continue;
                    int x=local&15,y=local>>8,z=(local>>4)&15;
                    // Preserve any player blocks/arrival fixtures placed before this queued import.
                    if(section.getBlockState(x,y,z).isAir())section.setBlockState(x,y,z,state,false);
                }
            }finally{section.release();}
            if(wasEmpty!=section.hasOnlyAir())light.updateSectionStatus(SectionPos.of(chunk.getPos(),sy),section.hasOnlyAir());
        }
        Heightmap.primeHeightmaps(chunk,chunk.getStatus().heightmapsAfter());
        chunk.initializeLightSources();chunk.setUnsaved(true);
        // One lighting pass and one full chunk update for existing watchers, rather than per-block notifications.
        light.initializeLight(chunk,false).thenCompose(ignored->light.lightChunk(chunk,false)).thenRunAsync(()->{
            if(level.getChunkSource().getChunkNow(cx,cz)!=chunk)return;
            var players=level.getChunkSource().chunkMap.getPlayers(chunk.getPos(),false);
            if(players.isEmpty())return;
            var packet=new ClientboundLevelChunkWithLightPacket(chunk,light,null,null);
            players.forEach(player->player.connection.send(packet));
        },level.getServer());
    }
}
