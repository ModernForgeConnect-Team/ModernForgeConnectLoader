package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.*;

/** Actual Player.interactOn dispatch into hosted TC4 golems, including item fallback and entity-first consumption. */
final class GolemHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private boolean done;
    private GolemHostSmokeProbe(ModernContentBridge content) { this.content=content; }
    static void install(ModernContentBridge content) { MinecraftForge.EVENT_BUS.addListener(new GolemHostSmokeProbe(content)::tick); }
    private void check(boolean value,String name) { if(!value)throw new AssertionError(name);proof.put(name,true); }
    private ItemStack bell() { return content.stack("Thaumcraft:GolemBell",0,1,""); }

    private void tick(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END)return;done=true;
        var server=event.getServer();var level=server.overworld();
        var spawned=new ArrayList<LegacyHostedEntity>();
        try {
            var services=content.services();var loader=services.loader();var world=services.worlds().bound(level);
            var player=FakePlayerFactory.getMinecraft(level);player.getInventory().clearContent();player.getInventory().selected=4;
            player.setPos(0.5,100,0.5);player.setOnGround(true);level.getChunkAt(new BlockPos(0,100,0));
            // Register the player so original callbacks can resolve its body and identity.
            level.addNewPlayer(player);
            var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");
            var marker=loader.loadClass("thaumcraft.common.entities.golems.Marker");
            for(int index=0;index<2;index++) {
                final int n=index;
                Object original=services.items().inLegacy(()->{
                    Object golem=type.getConstructor(loader.loadClass("net.minecraft.world.World")).newInstance(world);
                    type.getMethod("func_70107_b",double.class,double.class,double.class).invoke(golem,1.5+n,100d,0.5d);
                    type.getMethod("func_110171_b",int.class,int.class,int.class,int.class).invoke(golem,10+n,100,20+n,16);
                    type.getField("homeFacing").setInt(golem,2+n);
                    type.getMethod("setOwner",String.class).invoke(golem,player.getName().getString());
                    var markers=new ArrayList<Object>();
                    markers.add(marker.getConstructor(int.class,int.class,int.class,int.class,byte.class,byte.class).newInstance(30+n,100,40+n,0,(byte)1,(byte)2));
                    type.getMethod("setMarkers",ArrayList.class).invoke(golem,markers);
                    return golem;
                });
                var hosted=LegacyHostedEntity.host(content,level,original);check(level.addFreshEntity(hosted),"spawnGolem"+index);spawned.add(hosted);
            }
            var first=spawned.get(0);var second=spawned.get(1);
            check(!bell().isEmpty(),"originalBellRegistered");
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));
            for(boolean creative:new boolean[]{false,true}) {
                player.getAbilities().instabuild=creative;player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,bell());
                check(player.interactOn(first,InteractionHand.MAIN_HAND).consumesAction(),"mainHandConsumedCreative"+creative);
                bound(player.getMainHandItem(),first,0,"mainHandCreative"+creative);
            }
            player.getAbilities().instabuild=false;player.setShiftKeyDown(true);
            check(player.interactOn(second,InteractionHand.MAIN_HAND).consumesAction(),"sneakingRebindConsumed");
            bound(player.getMainHandItem(),second,1,"sneakingRebind");player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK,3));player.setItemInHand(InteractionHand.OFF_HAND,bell());
            check(player.interactOn(first,InteractionHand.OFF_HAND).consumesAction(),"offHandConsumed");
            bound(player.getOffhandItem(),first,0,"offHand");
            check(player.getMainHandItem().is(Items.STICK)&&player.getMainHandItem().getCount()==3,"offHandPreservesMainHand");
            check(player.getInventory().getItem(0).is(Items.DIAMOND)&&player.getInventory().getItem(0).getCount()==7,"otherSlotsUnchanged");
            ItemStack persisted=ItemStack.of(player.getOffhandItem().save(new CompoundTag()));
            bound(persisted,first,0,"bellNbtRoundTrip");
            // Entity-owned interaction must run even when the item does not override itemInteractionForEntity.
            player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemGolemCore",0,2,""));
            check(player.interactOn(first,InteractionHand.MAIN_HAND).consumesAction(),"entityCoreInsertionConsumed");
            check(services.items().inLegacy(()->(byte)type.getMethod("getCore").invoke(first.legacyOrNull()))==0,"entityCallbackInstalledCore");
            check(player.getMainHandItem().getCount()==1,"coreConsumedOnce");
            // A reload has no original instance until interact restores it; binding must use the new host entity ID.
            var saved=second.saveWithoutId(new CompoundTag());second.discard();
            var restored=new LegacyHostedEntity(content.legacyEntityType(),level);restored.load(saved);restored.setUUID(UUID.randomUUID());
            check(level.addFreshEntity(restored),"spawnRestoredGolem");spawned.add(restored);
            player.setItemInHand(InteractionHand.MAIN_HAND,bell());
            check(player.interactOn(restored,InteractionHand.MAIN_HAND).consumesAction(),"restoredGolemConsumed");
            bound(player.getMainHandItem(),restored,1,"restoredGolem");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            check(player.interactOn(restored,InteractionHand.MAIN_HAND)!=InteractionResult.FAIL,"emptyHandSafe");
            var pig=EntityType.PIG.create(level);pig.setPos(3,100,0);level.addFreshEntity(pig);
            player.setItemInHand(InteractionHand.MAIN_HAND,bell());
            check(player.interactOn(pig,InteractionHand.MAIN_HAND)==InteractionResult.PASS,"vanillaNonGolemPass");
            check(!content.gameplayStack(player.getMainHandItem()).tag().contains("golemid"),"nonGolemDoesNotBind");pig.discard();
            player.discard();proof.put("status","PASS");
        } catch(Throwable failure) {
            proof.put("status","FAIL");proof.put("failure",failure.toString());LogUtils.getLogger().error("MFCL_GOLEM_SMOKE_FAIL",failure);
        } finally {
            spawned.forEach(LegacyHostedEntity::discard);
            try {
                var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/golem-smoke.json");Files.createDirectories(path.getParent());
                Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(proof));
            } catch(Exception failure) { throw new IllegalStateException(failure); }
            LogUtils.getLogger().info("MFCL_GOLEM_SMOKE_DONE: {}",proof);server.halt(false);
        }
    }

    private void bound(ItemStack bell,LegacyHostedEntity target,int index,String name) throws Exception {
        var services=content.services();
        services.items().inLegacy(()->{
            Object old=services.items().materialize(content.gameplayStack(bell));var stack=services.loader().loadClass("net.minecraft.item.ItemStack");
            Object tag=stack.getMethod("func_77978_p").invoke(old);var nbt=tag.getClass();
            check((int)nbt.getMethod("func_74762_e",String.class).invoke(tag,"golemid")==target.getId(),name+"Identity");
            for(var field:Map.of("golemhomex",10+index,"golemhomey",100,"golemhomez",20+index,"golemhomeface",2+index).entrySet())
                check((int)nbt.getMethod("func_74762_e",String.class).invoke(tag,field.getKey())==field.getValue(),name+field.getKey());
            var item=services.loader().loadClass("thaumcraft.common.entities.golems.ItemGolemBell");
            var markers=(List<?>)item.getMethod("getMarkers",stack).invoke(null,old);
            check(markers.size()==1&&markers.get(0).getClass().getField("x").getInt(markers.get(0))==30+index,name+"Markers");
            return null;
        });
    }
}
