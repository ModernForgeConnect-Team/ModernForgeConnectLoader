package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real native item aging and original TC4 expiration subscribers in a disposable, enclosed water fixture. */
final class DroppedItemFluidHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<?> pending;
    private volatile boolean running, done;
    private Throwable serverFailure;
    private int stage,visualFrames;
    private volatile boolean screenshotSaved;
    private ItemEntity salt, drySalt, stick, stackedSalt;
    private final Map<String,BlockPos> naturalFluids=new LinkedHashMap<>();
    private final Map<String,Boolean> verticalFlow=new LinkedHashMap<>();
    private final Map<String,BlockPos> sealedFluids=new LinkedHashMap<>();
    private final Map<String,Boolean> unsealedFlow=new LinkedHashMap<>();
    private boolean openedSeals;
    private final BlockPos source = new BlockPos(0, 200, 0), dry = source.east(4), control = source.east(8), stacked = source.east(12);
    private DroppedItemFluidHostSmokeProbe(ModernContentBridge content) { this.content = content; }
    static void install(ModernContentBridge content) {
        var probe = new DroppedItemFluidHostSmokeProbe(content);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(probe::clientTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(probe::serverTick);
    }
    private void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Dropped-item fluid acceptance timed out");
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            if (stage == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-dropped-items-" + System.currentTimeMillis(),
                        new LevelSettings("MFCL dropped-item fluids", GameType.SURVIVAL, false, net.minecraft.world.Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42, false, false), access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                stage++;
            } else if (stage == 1) {
                if (mc.player == null || mc.screen != null) return;
                pending = mc.getSingleplayerServer().submit(() -> setup(mc.getSingleplayerServer().overworld())); stage++;
            } else if (stage == 2 && !running) {
                if(serverFailure!=null){finish(mc,serverFailure);return;}
                prepareVisual(mc);stage=3;
            } else if(stage==3&&++visualFrames>50){
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-fluid-visual.png",mc.getMainRenderTarget(),message->screenshotSaved=true);stage=4;
            } else if(stage==4&&screenshotSaved){mc.options.hideGui=false;finish(mc,null);}
        } catch (Throwable failure) { finish(mc, failure); }
    }
    private void prepareVisual(Minecraft mc) throws Exception {
        var center=new BlockPos(0,208,3);var fluid=content.blocks().get("Thaumcraft:blockFluidPure");
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)mc.level.setBlock(center.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            int meta=x==0&&z==0?0:Math.min(7,1+(Math.abs(x)+Math.abs(z))*2);
            mc.level.setBlock(center.offset(x,0,z),fluid.defaultBlockState().setValue(ModernContentBridge.METADATA,meta),2);
        }
        // Exposed falling edge, partial neighbours, and a plant next to the liquid.
        mc.level.setBlock(center.offset(-1,1,0),fluid.defaultBlockState().setValue(ModernContentBridge.METADATA,8),2);
        mc.level.setBlock(center.offset(2,0,0),Blocks.GRASS.defaultBlockState(),2);
        mc.getSingleplayerServer().submit(()->{
            var serverPlayer=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);serverPlayer.setNoGravity(true);
            serverPlayer.connection.teleport(3.5,210.0,7.5,143.13f,31.55f);return true;
        }).join();
        mc.player.setNoGravity(true);mc.player.setPos(3.5,210.0,7.5);mc.player.setYRot(143.13f);mc.player.setXRot(31.55f);mc.player.yRotO=143.13f;mc.player.xRotO=31.55f;
        mc.options.hideGui=true;visualFrames=0;
        var meshes=new ArrayList<Object>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var position=center.offset(x,0,z);var state=mc.level.getBlockState(position);int meta=state.getValue(ModernContentBridge.METADATA);
            var geometry=content.blockRendering().capture("Thaumcraft:blockFluidPure",meta,content.renderView(mc.level,position));
            meshes.add(Map.of("x",x,"z",z,"meta",meta,"layers",geometry));
        }
        var meshFile=mc.gameDirectory.toPath().resolve("logs/mfcl/fluid-visual-mesh.json");
        java.nio.file.Files.createDirectories(meshFile.getParent());java.nio.file.Files.writeString(meshFile,new com.google.gson.GsonBuilder().create().toJson(meshes));
        proof.put("visualMeshCells",meshes.size());
    }
    private void setup(ServerLevel level) {
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        level.setBlockAndUpdate(source.south(4).below(), Blocks.STONE.defaultBlockState());
        player.connection.teleport(.5, 200, 4.5, 0, 0);
        try {verifyBuckets(level,player);verifyFluidCollisions(level);verifyNativeTank(level);} catch(Exception failure){throw new IllegalStateException("Bucket acceptance failed",failure);}
        player.connection.teleport(.5, 200, 4.5, 0, 0);
        well(level, source, true); well(level, dry, false); well(level, control, true); well(level, stacked, true);
        salt = drop(level, source, content.stack("Thaumcraft:ItemBathSalts", 0, 1, ""));
        drySalt = drop(level, dry, content.stack("Thaumcraft:ItemBathSalts", 0, 1, ""));
        stick = drop(level, control, new ItemStack(Items.STICK));
        stackedSalt = drop(level, stacked, content.stack("Thaumcraft:ItemBathSalts", 0, 3, ""));
        // Observe normal scheduled world ticks in open air. Keep fixtures in loaded chunks and
        // separate them so one fluid cannot alter another's result.
        String[] keys={"Thaumcraft:blockFluidPure","Thaumcraft:blockFluidDeath","Thaumcraft:blockFluxGoo","Thaumcraft:blockFluxGas"};
        for(int i=0;i<keys.length;i++){
            var block=content.blocks().get(keys[i]);require(block!=null,"Missing fluid "+keys[i]);
            var pos=new BlockPos(i*8,200,12);naturalFluids.put(keys[i],pos);verticalFlow.put(keys[i],false);
            var initial=block.defaultBlockState().setValue(ModernContentBridge.METADATA,i==0?0:i==1?3:7);
            var nativeFluid=initial.getFluidState();
            if(i<2){
                require(!nativeFluid.isEmpty()&&nativeFluid.isSource()&&nativeFluid.getAmount()==8,"Missing native source fluid state for "+keys[i]);
                var thin=block.defaultBlockState().setValue(ModernContentBridge.METADATA,i==0?7:0).getFluidState();
                require(!thin.isEmpty()&&!thin.isSource()&&thin.getAmount()<8&&thin.getType()==nativeFluid.getType(),"Incorrect native partial fluid state for "+keys[i]);
                require(nativeFluid.getFluidType()!=net.minecraftforge.common.ForgeMod.WATER_TYPE.get(),"Original fluid used vanilla water identity: "+keys[i]);
            }else require(nativeFluid.isEmpty(),"Non-liquid goo and gas should not apply water movement physics");
            level.setBlockAndUpdate(pos,initial);
            var sealed=new BlockPos(-4-i*8,200,12);sealedFluids.put(keys[i],sealed);unsealedFlow.put(keys[i],false);
            well(level,sealed,false);level.setBlockAndUpdate(sealed,block.defaultBlockState().setValue(ModernContentBridge.METADATA,i==0?0:i==1?3:7));
        }
        proof.put("nativeFluidStateSourceAndPartialLevels",true);
        proof.put("bathSaltLifespan", salt.lifespan);
        require(salt.lifespan == 200 && drySalt.lifespan == 200 && stackedSalt.lifespan == 200, "Original bath salt lifespan was not imported");
        require(stick.lifespan == 6000, "Vanilla drop lifetime changed");
        // Expire in flowing water within this tick, before its scheduled flow can change the test block.
        var flowing = source.west(4); well(level, flowing, true);
        level.setBlock(flowing, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 2);
        var flowSalt = drop(level, flowing, content.stack("Thaumcraft:ItemBathSalts", 0, 1, ""));
        var data = flowSalt.saveWithoutId(new net.minecraft.nbt.CompoundTag()); data.putShort("Age", (short)199); flowSalt.load(data);
        flowSalt.tick();
        require(flowSalt.isRemoved() && level.getBlockState(flowing).is(Blocks.WATER), "Flowing water incorrectly converted or salt failed to expire");
        proof.put("flowingWaterRejected", true); running = true;
    }
    private void verifyBuckets(ServerLevel level,net.minecraft.server.level.ServerPlayer player) throws Exception {
        var pos=new BlockPos(4,206,2);player.connection.teleport(4.5,205,4.5,180,0);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,new net.minecraft.world.phys.Vec3(4.5,206.5,2.5));
        String[] fluids={"Thaumcraft:blockFluidPure","Thaumcraft:blockFluidDeath"},buckets={"Thaumcraft:ItemBucketPure","Thaumcraft:ItemBucketDeath"};int[] sourceMeta={0,3};
        var checks=new LinkedHashMap<String,Object>();
        for(int i=0;i<fluids.length;i++){
            var block=content.blocks().get(fluids[i]);
            level.setBlockAndUpdate(pos,block.defaultBlockState().setValue(ModernContentBridge.METADATA,sourceMeta[i]));
            player.getInventory().clearContent();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
            var result=player.gameMode.useItem(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            var old=content.gameplayStack(player.getMainHandItem());
            require(result.consumesAction()&&level.getBlockState(pos).isAir()&&old!=null&&old.key().equals(buckets[i]),"Survival bucket pickup failed for "+fluids[i]+": "+result+" "+old);
            level.setBlockAndUpdate(pos,block.defaultBlockState().setValue(ModernContentBridge.METADATA,sourceMeta[i]));
            player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
            result=player.gameMode.useItem(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            require(result.consumesAction()&&level.getBlockState(pos).isAir()&&player.getMainHandItem().is(Items.BUCKET)&&player.getInventory().countItem(content.items().get(buckets[i]))==1,"Creative bucket pickup failed for "+fluids[i]);
            player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();
            level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos.south(),Blocks.AIR.defaultBlockState());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,content.stack(buckets[i],0,1,""));
            result=player.gameMode.useItem(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            require(result.consumesAction()&&level.getBlockState(pos.south()).is(block)&&level.getBlockState(pos.south()).getValue(ModernContentBridge.METADATA)==sourceMeta[i]&&player.getMainHandItem().is(Items.BUCKET),"Survival filled bucket failed for "+fluids[i]+": "+result+" "+player.getMainHandItem());
            level.setBlockAndUpdate(pos.south(),Blocks.AIR.defaultBlockState());player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,content.stack(buckets[i],0,1,""));
            result=player.gameMode.useItem(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            require(result.consumesAction()&&level.getBlockState(pos.south()).is(block)&&content.gameplayStack(player.getMainHandItem()).key().equals(buckets[i]),"Creative filled bucket failed for "+fluids[i]+": result="+result+", target="+level.getBlockState(pos.south())+", held="+player.getMainHandItem());
            level.setBlockAndUpdate(pos.south(),Blocks.AIR.defaultBlockState());player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();
            level.setBlockAndUpdate(pos,block.defaultBlockState().setValue(ModernContentBridge.METADATA,sourceMeta[i]==0?1:2));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
            player.gameMode.useItem(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            require(level.getBlockState(pos).is(block)&&player.getMainHandItem().is(Items.BUCKET),"Bucket collected non-source "+fluids[i]+": state="+level.getBlockState(pos)+", held="+player.getMainHandItem());
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());checks.put(fluids[i],"pickup, placement, survival, creative and non-source PASS");
        }
        proof.put("nativeEmptyBucketPickup",checks);player.getInventory().clearContent();
    }
    private void verifyFluidCollisions(ServerLevel level) {
        var pig=net.minecraft.world.entity.EntityType.PIG.create(level);require(pig!=null,"Cannot create fluid collision fixture");
        var pos=new BlockPos(6,205,8);pig.moveTo(6.5,205,8.5,0,0);pig.setNoAi(true);level.addFreshEntity(pig);
        try {
            var death=content.blocks().get("Thaumcraft:blockFluidDeath").defaultBlockState().setValue(ModernContentBridge.METADATA,3);
            level.setBlockAndUpdate(pos,death);
            pig.updateFluidHeightAndDoFluidPushing(state->true);var type=death.getFluidState().getFluidType();
            require(pig.getFluidTypeHeight(type)>0&&type.canSwim(pig),"Living entity did not detect the native liquid type");
            proof.put("nativeEntityImmersionDetected",true);
            float before=pig.getHealth();death.entityInside(level,pos,pig);
            require(pig.getHealth()<before,"Death fluid caused no living-entity damage");
            var goo=content.blocks().get("Thaumcraft:blockFluxGoo").defaultBlockState().setValue(ModernContentBridge.METADATA,3);
            level.setBlockAndUpdate(pos,goo);pig.setDeltaMovement(1,0,1);goo.entityInside(level,pos,pig);
            var exhaustion=content.potions().effect("thaumcraft:visexhaust");
            require(pig.getDeltaMovement().x<1&&pig.hasEffect(exhaustion),"Flux goo did not slow or exhaust the entity");
            pig.removeAllEffects();var gas=content.blocks().get("Thaumcraft:blockFluxGas").defaultBlockState().setValue(ModernContentBridge.METADATA,7);
            boolean gasEffect=false;
            for(int i=0;i<500&&!gasEffect;i++){
                level.setBlockAndUpdate(pos,gas);gas.entityInside(level,pos,pig);
                gasEffect=pig.hasEffect(exhaustion)||pig.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION);
            }
            require(gasEffect,"Flux gas produced no original probabilistic effect in 500 collisions");
            proof.put("nativeFluidCollisionEffects",Map.of("deathDamage",true,"gooSlowsAndExhausts",true,"gasEffect",true));
        } finally {pig.discard();level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}
    }
    private void verifyNativeTank(ServerLevel level) throws Exception {
        if(!content.blocks().containsKey("ExtraUtilities:drum")){proof.put("nativeTankCustomFluid","SKIP: XU is not installed");return;}
        var fluid=content.legacyFluid("fluidpure");require(fluid!=null,"Pure fluid has no native identity");
        var pos=new BlockPos(8,205,8);level.setBlockAndUpdate(pos,content.modernBlock(new github.com.gengyoubo.mfcl.bridge.HostWorldAccess.Block("ExtraUtilities:drum",0)));
        var tile=level.getBlockEntity(pos);require(tile!=null,"Missing drum tile");
        var tank=tile.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).orElseThrow(()->new IllegalStateException("Missing native fluid capability"));
        var filledBucket=content.stack("Thaumcraft:ItemBucketPure",0,1,"");
        var itemHandler=filledBucket.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(()->new IllegalStateException("Old filled bucket has no native fluid capability"));
        require(itemHandler.getFluidInTank(0).getFluid()==fluid&&itemHandler.getFluidInTank(0).getAmount()==1000,"Old bucket did not expose original liquid");
        var simulated=itemHandler.drain(1000,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        require(simulated.getFluid()==fluid&&simulated.getAmount()==1000&&itemHandler.getContainer().getItem()==filledBucket.getItem(),"Old bucket simulation changed its contents");
        var stack=new net.minecraftforge.fluids.FluidStack(fluid,1000);
        require(fluid.getBucket()==filledBucket.getItem(),"Native fluid does not identify its original bucket");
        require(tank.fill(stack,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)==1000&&tank.getFluidInTank(0).isEmpty(),"Fluid simulation modified the tank");
        require(tank.fill(stack,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==1000,"Cannot fill old drum with native TC4 fluid");
        var stored=tank.getFluidInTank(0);require(stored.getAmount()==1000&&stored.getFluid()==fluid,"Native tank view lost the fluid identity");
        var drained=tank.drain(1000,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        require(drained.getAmount()==1000&&drained.getFluid()==fluid&&tank.getFluidInTank(0).isEmpty(),"Native custom-fluid transfer did not round trip");
        var filled=net.minecraftforge.fluids.FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET),tank,1000,null,true);
        // Refill after the explicit drain, then exercise the actual modern container/tank utility in both directions.
        require(!filled.isSuccess(),"Empty tank filled a bucket");
        require(tank.fill(stack,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==1000,"Cannot refill old drum");
        filled=net.minecraftforge.fluids.FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET),tank,1000,null,true);
        require(filled.isSuccess()&&filled.getResult().getItem()==filledBucket.getItem()&&tank.getFluidInTank(0).isEmpty(),"Native empty bucket could not fill from old drum");
        var emptied=net.minecraftforge.fluids.FluidUtil.tryEmptyContainer(filled.getResult(),tank,1000,null,true);
        require(emptied.isSuccess()&&emptied.getResult().is(Items.BUCKET)&&tank.getFluidInTank(0).getFluid()==fluid&&tank.getFluidInTank(0).getAmount()==1000,"Original bucket could not empty into old drum");
        proof.put("nativeTankCustomFluid",true);proof.put("nativeFluidContainerExchange",true);level.removeBlock(pos,false);
    }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !running || done) return;
        try {
            var level = event.getServer().overworld();
            if(!openedSeals&&salt.getAge()>=80){
                for(var entry:sealedFluids.entrySet()){
                    var target=entry.getKey().equals("Thaumcraft:blockFluxGas")?entry.getValue().above():entry.getValue().below();
                    require(level.getBlockState(target).is(Blocks.STONE),"Sealed fluid escaped before opening: "+entry.getKey());
                    level.removeBlock(target,false);
                }
                openedSeals=true;
            }
            if(openedSeals)for(var entry:sealedFluids.entrySet()){
                var position=entry.getKey().equals("Thaumcraft:blockFluxGas")?entry.getValue().above():entry.getValue().below();
                if(level.getBlockState(position).is(content.blocks().get(entry.getKey())))unsealedFlow.put(entry.getKey(),true);
            }
            for(var entry:naturalFluids.entrySet()){
                var position=entry.getKey().equals("Thaumcraft:blockFluxGas")?entry.getValue().above():entry.getValue().below();
                if(level.getBlockState(position).is(content.blocks().get(entry.getKey())))verticalFlow.put(entry.getKey(),true);
            }
            if (!salt.isRemoved()) {
                require(salt.getAge() < 200 && level.getBlockState(source).is(Blocks.WATER), "Salt converted early or failed to expire at 200 ticks");
                if (salt.getAge() >= 180) proof.put("noConversionBeforeExpiration", true);
                return;
            }
            require(Boolean.TRUE.equals(proof.get("noConversionBeforeExpiration")), "Natural item aging was not observed");
            require(level.getBlockState(source).is(content.blocks().get("Thaumcraft:blockFluidPure")), "Expired salt did not convert the water source");
            require(drySalt.isRemoved() && level.getBlockState(dry).isAir(), "Dry salt did not expire normally");
            require(!stick.isRemoved() && stick.getItem().getCount() == 1 && level.getBlockState(control).is(Blocks.WATER), "Ordinary item converted water or disappeared");
            require(stackedSalt.isRemoved() && level.getBlockState(stacked).is(content.blocks().get("Thaumcraft:blockFluidPure")), "Stacked salts diverged from original expiration behavior");
            var observations=new LinkedHashMap<String,Object>();
            for(var entry:naturalFluids.entrySet()){
                var pos=entry.getValue();int adjacent=0;
                for(var direction:net.minecraft.core.Direction.values())if(level.getBlockState(pos.relative(direction)).is(content.blocks().get(entry.getKey())))adjacent++;
                var state=level.getBlockState(pos);
                observations.put(entry.getKey(),Map.of("adjacentSameFluid",adjacent,"hostFluidStateEmpty",state.getFluidState().isEmpty(),"centerStillPresent",state.is(content.blocks().get(entry.getKey()))));
            }
            proof.put("naturalFluidObservationsAfter200Ticks",observations);
            proof.put("naturalVerticalFlowObserved",new LinkedHashMap<>(verticalFlow));
            require(verticalFlow.values().stream().allMatch(Boolean::booleanValue),"Original fluids failed to flow in their expected vertical directions: "+verticalFlow);
            proof.put("neighbourOpeningFlowObserved",new LinkedHashMap<>(unsealedFlow));
            require(unsealedFlow.values().stream().allMatch(Boolean::booleanValue),"Original fluids did not resume after opening a neighbour: "+unsealedFlow);
            proof.put("sourceWaterConvertedAt200Ticks", true); proof.put("drySaltExpiresWithoutConversion", true);
            proof.put("ordinaryItemUnaffected", true); proof.put("stackedSaltsMatchOriginalBehavior", true); running = false;
        } catch (Throwable failure) { serverFailure = failure; running = false; }
    }
    private static void well(ServerLevel level, BlockPos pos, boolean water) {
        for (var side : net.minecraft.core.Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }
    private static ItemEntity drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        var drop = new ItemEntity(level, pos.getX()+.5, pos.getY()+.25, pos.getZ()+.5, stack);
        drop.setDeltaMovement(0,0,0); drop.setNoGravity(true); drop.setNeverPickUp(); level.addFreshEntity(drop); return drop;
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private void finish(Minecraft mc, Throwable failure) {
        done = true; proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) { proof.put("failure", failure.toString()); com.mojang.logging.LogUtils.getLogger().error("MFCL dropped-item fluid acceptance failed", failure); }
        try {
            var output = mc.gameDirectory.toPath().resolve("logs/mfcl/dropped-item-fluid-smoke.json"); java.nio.file.Files.createDirectories(output.getParent());
            java.nio.file.Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception error) { throw new IllegalStateException(error); }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_DROPPED_ITEM_FLUID_RESULT {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); } mc.stop();
    }
}
