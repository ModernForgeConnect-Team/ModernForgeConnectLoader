package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftKnowledge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** Native hold-to-scan, original rewards, live lens geometry and save/reload acceptance. */
final class ScannerHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private int stage,frames,before,awarded;
    private String world;
    private java.util.concurrent.CompletableFuture<?> pending;
    private boolean renderNbtChecked;
    private final java.util.concurrent.ConcurrentMap<String,java.util.concurrent.atomic.AtomicInteger> playedSounds=new java.util.concurrent.ConcurrentHashMap<>();
    private record TargetCase(net.minecraft.world.level.block.state.BlockState state,String oldKey,int metadata) {}
    private final List<TargetCase> targetCases=List.of(
            new TargetCase(Blocks.OAK_FENCE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true),"fence",0),
            new TargetCase(Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,net.minecraft.core.Direction.Axis.X),"log",4),
            new TargetCase(Blocks.SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,net.minecraft.core.Direction.Axis.Z),"log",9),
            new TargetCase(Blocks.BLUE_WOOL.defaultBlockState(),"wool",11),
            new TargetCase(Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH,true),"stained_glass_pane",15),
            new TargetCase(Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP),"wooden_slab",8),
            new TargetCase(Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,net.minecraft.core.Direction.WEST).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF,net.minecraft.world.level.block.state.properties.Half.TOP),"stone_stairs",5),
            new TargetCase(Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,net.minecraft.core.Direction.Axis.Z),"quartz_block",4),
            new TargetCase(Blocks.GRASS_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY,true),"grass",0),
            new TargetCase(Blocks.SNOW_BLOCK.defaultBlockState(),"snow",0),
            new TargetCase(Blocks.DEEPSLATE.defaultBlockState(),null,0),
            new TargetCase(Blocks.MANGROVE_FENCE.defaultBlockState(),null,0),
            new TargetCase(Blocks.STONE.defaultBlockState(),"stone",0));
    private int targetCase;
    private final BlockPos target=new BlockPos(0,-58,3);
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private ScannerHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){var probe=new ScannerHostSmokeProbe(content);MinecraftForge.EVENT_BUS.addListener(probe::tick);MinecraftForge.EVENT_BUS.addListener(probe::renderNbt);MinecraftForge.EVENT_BUS.addListener(probe::soundStarted);}
    private void soundStarted(net.minecraftforge.client.event.sound.PlaySoundSourceEvent event) {
        if(event.getChannel()!=null)playedSounds.computeIfAbsent(event.getSound().getLocation().toString(),key->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
    }
    private void renderNbt(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if(renderNbtChecked||stage==99)return;var mc=Minecraft.getInstance();if(mc.level==null)return;
        try {
            var base=content.stack("Thaumcraft:ItemChestplateCultistRobe",0,1,"");
            var anvil=base.copy();anvil.getOrCreateTag().putInt("RepairCost",7);anvil.setHoverName(net.minecraft.network.chat.Component.literal("JEI anvil preview"));
            anvil.getOrCreateTag().putString("host_only_probe","preserved");
            var prior=anvil.getTag().copy();
            for(int tint=0;tint<2;tint++)if(mc.getItemColors().getColor(anvil,tint)!=mc.getItemColors().getColor(base,tint))throw new IllegalStateException("Anvil preview changed robe tint");
            event.getGuiGraphics().renderFakeItem(anvil,2,2);event.getGuiGraphics().flush();
            if(!prior.equals(anvil.getTag()))throw new IllegalStateException("Rendering mutated host NBT");
            boolean strict=false;try{content.snapshot(anvil);}catch(UnsupportedOperationException expected){strict=true;}
            if(!strict)throw new IllegalStateException("Gameplay NBT validation was weakened");
            int variants=0;
            for(var original:content.services().items().catalog())if(original.key().startsWith("Thaumcraft:")&&!original.tag().isEmpty()) {
                var baseVariant=content.stack(original.key(),original.metadata(),1,original.tag());
                var preview=baseVariant.copy();preview.getOrCreateTag().putInt("RepairCost",3);preview.setHoverName(net.minecraft.network.chat.Component.literal("NBT preview"));
                for(int tint=0;tint<2;tint++)if(mc.getItemColors().getColor(preview,tint)!=mc.getItemColors().getColor(baseVariant,tint))throw new IllegalStateException("Preview lost original tint: "+original.key()+" "+original.tag());
                event.getGuiGraphics().renderFakeItem(preview,2,20);variants++;
            }
            event.getGuiGraphics().flush();proof.put("originalNbtVariantsRendered",variants);
            proof.put("jeiAnvilPreviewRenderedWithHostNbt",true);proof.put("renderPreservedHostNbtAndGameplayValidation",true);renderNbtChecked=true;
        }catch(Throwable failure){finish(mc,failure);}
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new IllegalStateException("Scanner acceptance timed out: "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.languageCode="zh_cn";mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                world="mfcl-scanner-smoke-"+System.currentTimeMillis();stage=1;
                mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("MFCL original scanner",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            } else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null) {
                pending=server(mc,()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();
                    level.setBlockAndUpdate(new BlockPos(0,-60,0),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());
                    player.teleportTo(.5,-59,.5);player.setYRot(0);player.setXRot(0);
                    player.getInventory().setItem(0,content.stack("Thaumcraft:ItemThaumometer",0,1,""));player.inventoryMenu.broadcastChanges();return true;
                });stage=2;
            } else if(stage==2&&pending.isDone()&&ready(mc,Blocks.STONE)) {
                pending.join();before=pool(mc,true);proof.put("initialTerra",before);hold(mc);stage=3;frames=0;
            } else if(stage==3&&++frames>=35) {
                release(mc);pending=server(mc,()->{
                    int amount=pool(mc,true);if(amount<=before||!scanned(mc,true,"minecraft:stone"))throw new IllegalStateException("Original scan did not award terra: "+before+" -> "+amount);
                    return amount;
                });stage=4;
            } else if(stage==4&&pending.isDone()) {
                awarded=(int)pending.join();if(pool(mc,false)!=awarded)return;
                if(!playedSounds.containsKey("thaumcraft:cameraticks")||!playedSounds.containsKey("minecraft:entity.experience_orb.pickup"))throw new IllegalStateException("Scan sounds never reached an audio channel: "+playedSounds);
                proof.put("scanTicksPlayedOnAudioChannel",playedSounds.get("thaumcraft:cameraticks").get());proof.put("scanRewardSoundPlayed",true);
                var drawing=LiveLegacyItemCapture.firstPerson(content,mc.player.getMainHandItem());
                if(drawing.primitives().stream().noneMatch(p->p.texture().contains("textures/aspects/terra.png")))throw new IllegalStateException("Live lens did not show the scanned terra icon");
                proof.put("nativeHeldScanAwarded",true);proof.put("serverTerraAfterScan",awarded);proof.put("clientTerraAfterScan",pool(mc,false));proof.put("liveLensContainsAspect",true);
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-scanner-aspects.png",mc.getMainRenderTarget(),message->{});
                hold(mc);stage=5;frames=0;
            } else if(stage==5&&++frames>=35) {
                release(mc);pending=server(mc,()->{
                    if(pool(mc,true)!=awarded)throw new IllegalStateException("Repeated scan awarded duplicate points");
                    mc.getSingleplayerServer().overworld().setBlockAndUpdate(target,Blocks.DIRT.defaultBlockState());return true;
                });stage=6;
            } else if(stage==6&&pending.isDone()&&ready(mc,Blocks.DIRT)) {
                pending.join();hold(mc);stage=7;frames=0;
            } else if(stage==7&&++frames>=5) {
                release(mc);stage=8;frames=0;
            } else if(stage==8&&++frames>=10) {
                pending=server(mc,()->{
                    if(pool(mc,true)!=awarded||scanned(mc,true,"minecraft:dirt"))throw new IllegalStateException("Cancelled scan completed or changed points");
                    mc.getSingleplayerServer().overworld().setBlockAndUpdate(target,Blocks.GRASS_BLOCK.defaultBlockState());return true;
                });stage=9;
            } else if(stage==9&&pending.isDone()&&ready(mc,Blocks.GRASS_BLOCK)) {
                pending.join();hold(mc);stage=10;frames=0;
            } else if(stage==10&&++frames>=35) {
                release(mc);var loader=content.services().loader();
                var messages=(List<?>)loader.loadClass("thaumcraft.client.lib.PlayerNotifications").getField("notificationList").get(null);
                if(messages.isEmpty())throw new IllegalStateException("Missing original prerequisite feedback");
                pending=server(mc,()->{
                    if(pool(mc,true)!=awarded||scanned(mc,true,"minecraft:grass"))throw new IllegalStateException("Unknown prerequisite was bypassed");
                    var level=mc.getSingleplayerServer().overworld();level.setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());mc.getSingleplayerServer().saveEverything(false,true,true);return true;
                });stage=11;
            } else if(stage==11&&pending.isDone()) {
                pending.join();proof.put("repeatScanNoDuplicateReward",true);proof.put("cancelledScanRejected",true);proof.put("originalPrerequisiteAndFeedback",true);
                mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());stage=12;
            } else if(stage==12&&mc.getSingleplayerServer()==null) {
                var loader=content.services().loader();Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
                Object knowledge=proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy);knowledge.getClass().getMethod("wipePlayerKnowledge",String.class).invoke(knowledge,"Dev");
                mc.createWorldOpenFlows().loadLevel(mc.screen,world);stage=13;
            } else if(stage==13&&mc.player!=null&&mc.level!=null&&mc.screen==null) {
                pending=server(mc,()->{if(pool(mc,true)!=awarded||!scanned(mc,true,"minecraft:stone"))throw new IllegalStateException("Original research was lost on reload");return true;});stage=14;frames=0;
            } else if(stage==14&&pending.isDone()&&++frames>20) {
                pending.join();if(pool(mc,false)!=awarded||!scanned(mc,false,"minecraft:stone"))throw new IllegalStateException("Reloaded scan state did not reach the client");
                proof.put("knowledgeSurvivedReload",true);stage=15;
            } else if(stage==15) {
                var check=targetCases.get(targetCase);
                if(check.oldKey()!=null) {
                    var old=content.legacyBlock(check.state());
                    if(!old.legacyKey().equals("minecraft:"+check.oldKey())||old.metadata()!=check.metadata())throw new IllegalStateException("Wrong block flattening: "+check+" -> "+old);
                }else {
                    boolean unmapped=false;try{content.legacyBlock(check.state());}catch(UnmappedLegacyBlockException expected){unmapped=true;}
                    if(!unmapped)throw new IllegalStateException("New block was aliased to an unrelated old block: "+check.state());
                }
                pending=server(mc,()->{mc.getSingleplayerServer().overworld().setBlockAndUpdate(target,check.state());return true;});stage=16;frames=0;
            } else if(stage==16&&pending.isDone()&&ready(mc,targetCases.get(targetCase).state().getBlock())&&++frames>5) {
                pending.join();var immediate=mc.renderBuffers().bufferSource();
                mc.getItemRenderer().renderStatic(mc.player.getMainHandItem(),net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,new com.mojang.blaze3d.vertex.PoseStack(),immediate,mc.level,0);
                immediate.endBatch();
                if(targetCases.get(targetCase).oldKey()!=null) {
                    var drawing=LiveLegacyItemCapture.firstPerson(content,mc.player.getMainHandItem());
                    if(drawing.primitives().isEmpty())throw new IllegalStateException("Mapped target lost the lens: "+targetCases.get(targetCase));
                }
                if(++targetCase<targetCases.size())stage=15;
                else {proof.put("blockTargetsRenderedWithoutCrash",targetCases.size());proof.put("oldBlockNamesAndMetadataVerified",true);proof.put("newBlocksUseIdleLensWithoutInventedAspects",true);proof.put("status","PASS");finish(mc,null);}
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private boolean ready(Minecraft mc,net.minecraft.world.level.block.Block block) {
        return mc.player!=null&&mc.screen==null&&mc.level.getBlockState(target).is(block)&&mc.player.getMainHandItem().getItem()==content.items().get("Thaumcraft:ItemThaumometer");
    }
    private <T> java.util.concurrent.CompletableFuture<T> server(Minecraft mc,java.util.concurrent.Callable<T> action) {
        return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});
    }
    private void hold(Minecraft mc) {
        mc.player.setYRot(0);mc.player.setXRot(0);mc.player.yRotO=0;mc.player.xRotO=0;mc.options.keyUse.setDown(true);
        var hit=mc.player.pick(4.5,0,false);
        if(!(hit instanceof net.minecraft.world.phys.BlockHitResult block)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)throw new IllegalStateException("Scanner test is not looking at a block");
        if(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block).consumesAction())throw new IllegalStateException("Block interaction swallowed scanner held use");
        mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        if(!mc.player.isUsingItem())throw new IllegalStateException("Scanner did not enter held-use state");
        proof.put("rightClickBlockStartsHeldUse",true);
    }
    private void release(Minecraft mc) {mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private <T> T knowledge(Minecraft mc,boolean server,java.util.concurrent.Callable<T> action) throws Exception {
        if(server&&!mc.getSingleplayerServer().isSameThread())return mc.getSingleplayerServer().submit(()->{try{return knowledge(mc,true,action);}catch(Exception failure){throw new IllegalStateException(failure);}}).join();
        return content.services().items().inLegacy(()->{try(var scope=server?ThaumcraftKnowledge.server(content.services().loader(),content.services().worlds().bound(mc.getSingleplayerServer().overworld())):new ThaumcraftKnowledge.Scope(null,null,null)){return action.call();}});
    }
    private int pool(Minecraft mc,boolean server) throws Exception {
        return knowledge(mc,server,()->{var loader=content.services().loader();Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);Object knowledge=proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy);var aspect=loader.loadClass("thaumcraft.api.aspects.Aspect");return ((Number)knowledge.getClass().getMethod("getAspectPoolFor",String.class,aspect).invoke(knowledge,"Dev",aspect.getField("EARTH").get(null))).intValue();});
    }
    private boolean scanned(Minecraft mc,boolean server,String key) throws Exception {
        return knowledge(mc,server,()->{var loader=content.services().loader();Object old=content.services().items().materialize(new LegacyClientData.Stack(key,0,1,"",""));var item=loader.loadClass("net.minecraft.item.ItemStack").getMethod("func_77973_b").invoke(old);int hash=(int)loader.loadClass("thaumcraft.common.lib.research.ScanManager").getMethod("generateItemHash",loader.loadClass("net.minecraft.item.Item"),int.class).invoke(null,item,0);Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);var map=(Map<?,?>)proxy.getClass().getMethod("getScannedObjects").invoke(proxy);return map.get("Dev") instanceof List<?> list&&list.contains("@"+hash);});
    }
    private void finish(Minecraft mc,Throwable failure) {
        stage=99;mc.options.keyUse.setDown(false);
        if(failure!=null){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_SCANNER_SMOKE_FAIL",failure);}
        try {var path=mc.gameDirectory.toPath().resolve("logs/mfcl/scanner-smoke.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_SCANNER_SMOKE_RESULT: {}",proof);if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
