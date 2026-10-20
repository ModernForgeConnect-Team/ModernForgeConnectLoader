package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Disposable-world regression for player knowledge, packets and deferred travel on a failed tile callback. */
public final class OuterPlayerHostAcceptance {
    private OuterPlayerHostAcceptance() {}
    public static void verifyRollback(ModernContentBridge content,ServerPlayer player,Map<String,Object> proof) throws Exception {
        var services=content.services();var loader=services.loader();var level=player.serverLevel();
        Object world=services.worlds().bound(level);var pos=player.blockPosition().above(4);
        var beforeBlock=level.getBlockState(pos);byte[] before=player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
        int[] effects={0};
        var managerType=loader.loadClass("thaumcraft.common.lib.research.ResearchManager");
        try {
            LegacyTilePlayers.run(content,level,pos,()->{
                Object actor=LegacyWorlds.closestPlayer(world,player.getX(),player.getY(),player.getZ(),2);
                if(actor==null)throw new IllegalStateException("Player query did not resolve the host player");
                if((boolean)managerType.getMethod("isResearchComplete",String.class,String.class).invoke(null,player.getName().getString(),"RESEARCHER1"))throw new IllegalStateException("Rollback fixture is already completed");
                Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
                Object manager=proxy.getClass().getMethod("getResearchManager").invoke(proxy);
                managerType.getMethod("completeResearch",loader.loadClass("net.minecraft.entity.player.EntityPlayer"),String.class).invoke(manager,actor,"RESEARCHER1");
                Object packet=loader.loadClass("thaumcraft.common.lib.network.playerdata.PacketResearchComplete").getConstructor(String.class).newInstance("RESEARCHER1");
                Object wrapper=loader.loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null);
                wrapper.getClass().getMethod("sendTo",loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage"),loader.loadClass("net.minecraft.entity.player.EntityPlayerMP")).invoke(wrapper,packet,actor);
                level.setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                content.afterCommit(level,()->effects[0]++);
                throw new IllegalStateException("intentional tile player rollback");
            });
            throw new AssertionError("The tile failure was swallowed");
        }catch(IllegalStateException expected){if(!expected.getMessage().equals("intentional tile player rollback"))throw expected;}
        if(!beforeBlock.equals(level.getBlockState(pos))||effects[0]!=0||!Arrays.equals(before,player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA)))throw new IllegalStateException("Failed tile callback committed world/player/deferred effects");
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){
            if((boolean)managerType.getMethod("isResearchComplete",String.class,String.class).invoke(null,player.getName().getString(),"RESEARCHER1"))throw new IllegalStateException("Failed tile callback retained research in memory");
            return null;
        }});
        proof.put("failedTilePlayerKnowledgeWorldAndEffectsRollback",true);
    }
}
