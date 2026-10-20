package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;

/** Real server callbacks for the gameplay issues reported on 2026-10-21, in a disposable world. */
final class GameplayHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private net.minecraft.server.level.ServerPlayer player;
    private Pig target;
    private Object bat;
    private int stage, ticks;
    private int arrowCase=-1;
    private Pig arrowTarget;
    private float normalDamage;
    private final List<String> sounds=new ArrayList<>();
    private boolean done;
    private GameplayHostSmokeProbe(ModernContentBridge content) { this.content=content; }
    static void install(ModernContentBridge content) {
        var probe=new GameplayHostSmokeProbe(content);MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event)->{
            if(event.getSound()!=null)probe.sounds.add(event.getSound().get().getLocation().toString());
        });
    }
    private void check(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    private void test(String name,Callable<?> action) {
        try { proof.put(name,Map.of("status","PASS","result",action.call())); }
        catch(Throwable failure) {
            while(failure instanceof java.lang.reflect.InvocationTargetException&&failure.getCause()!=null)failure=failure.getCause();
            proof.put(name,Map.of("status","FAIL","failure",failure.toString()));
            LogUtils.getLogger().error("MFCL gameplay check failed: "+name,failure);
        }
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END)return;
        var level=event.getServer().overworld();
        try {
            if(stage++==0) {
                player=FakePlayerFactory.getMinecraft(level);
                var spawn=level.getSharedSpawnPos();player.moveTo(spawn.getX()+.5,200,spawn.getZ()+.5,0,0);
                for(var old:level.getEntitiesOfClass(Pig.class,player.getBoundingBox().inflate(32)))old.discard();
                for(var old:level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(32)))old.discard();
                // Keep the arena loaded so hosted entities receive ordinary server ticks with no connected players.
                level.setChunkForced(player.chunkPosition().x,player.chunkPosition().z,true);
                level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
                test("lootStackRoundTrips",()->lootMappings());
                test("treasureBags",()->bags(level));
                test("lootBlocks",()->loot(level));
                test("doorSounds",()->door(level));
                test("hellbatSummon",()->summon(level));
                return;
            }
            if(arrowCase>=0) {
                if(++ticks<15)return;
                int scenario=arrowCase;
                test("arrow"+scenario,()->verifyArrow(level,scenario));
                if(++arrowCase<13){test("fireArrow"+arrowCase,()->fireArrow(level,arrowCase));ticks=0;return;}
                finish(level);return;
            }
            if(++ticks<80)return;
            test("hellbatTicks",()->{
                var hosted=level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(32)).stream()
                        .filter(e->e.legacyOrNull()==bat).findFirst();
                check(bat!=null&&hosted.isPresent(),"Summoned bat did not survive ordinary server ticks");
                check(target.getHealth()<20,"Summoned bat never damaged its target");
                return Map.of("targetHealth",target.getHealth(),"state",content.services().entityHosting().state(bat).toString());
            });
            if(target!=null)target.discard();
            arrowCase=0;test("fireArrow0",()->fireArrow(level,0));ticks=0;
        }catch(Throwable failure){done=true;LogUtils.getLogger().error("MFCL gameplay probe failed",failure);event.getServer().halt(false);}
    }
    private void finish(ServerLevel level) throws Exception {
        done=true;
        proof.put("status",proof.values().stream().anyMatch(v->v instanceof Map<?,?> m&&"FAIL".equals(m.get("status")))?"FAIL":"PASS");
        var output=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/gameplay-smoke.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        LogUtils.getLogger().info("MFCL_GAMEPLAY_SMOKE: {}",proof);level.getServer().halt(false);
    }
    private Object bags(ServerLevel level) throws Exception {
        var results=new ArrayList<Object>();
        for(int quality=0;quality<3;quality++) {
            player.getInventory().clearContent();player.getInventory().selected=0;
            player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemLootBag",quality,2,""));
            var before=level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(8));
            var result=player.getMainHandItem().use(level,player,InteractionHand.MAIN_HAND);
            var after=level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(8));
            check(player.getMainHandItem().getCount()==1,"Bag not consumed, quality="+quality+", result="+result.getResult());
            check(after.size()>before.size(),"Bag produced no drops, quality="+quality);
            results.add(Map.of("quality",quality,"drops",after.size()-before.size()));
        }
        return results;
    }
    private Object lootMappings() throws Exception {
        var services=content.services();var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        var cases=new ArrayList<Object>();var utils=loader.loadClass("thaumcraft.common.lib.utils.Utils");
        var random=new Random(69305359L);
        for(int quality=0;quality<3;quality++)for(int i=0;i<128;i++)cases.add(utils.getMethod("generateLoot",int.class,Random.class).invoke(null,quality,random));
        // Explicitly cover the rare paths independently of the random loot distribution.
        cases.add(services.items().materialize(new LegacyClientData.Stack("minecraft:golden_helmet",12,1,"","{ench:[0:{id:0s,lvl:3s},1:{id:34s,lvl:2s}]}")));
        cases.add(services.items().materialize(new LegacyClientData.Stack("minecraft:enchanted_book",0,1,"","{StoredEnchantments:[0:{id:16s,lvl:4s}]}")));
        cases.add(services.items().materialize(new LegacyClientData.Stack("minecraft:potion",16386,1,"","")));
        var keys=new TreeSet<String>();int enchanted=0;
        for(Object original:cases) {
            var old=services.items().snapshot(original);keys.add(old.key());
            var modern=content.gameplayStack(old);check(!modern.isEmpty(),"Loot mapping empty: "+old);
            if(modern.isEnchanted()||modern.is(net.minecraft.world.item.Items.ENCHANTED_BOOK))enchanted++;
            var back=content.gameplayStack(modern);
            check(old.key().equals(back.key())&&old.metadata()==back.metadata()&&old.count()==back.count(),"Loot identity changed: "+old+" -> "+back);
            Object restored=services.items().materialize(back);
            check(Objects.equals(stackType.getField("field_77990_d").get(original),stackType.getField("field_77990_d").get(restored)),"Loot NBT changed: "+old+" -> "+back);
        }
        check(enchanted>0,"No enchanted loot was tested");return Map.of("samples",cases.size(),"enchanted",enchanted,"keys",keys);
    }
    private Object loot(ServerLevel level) throws Exception {
        var results=new ArrayList<Object>();var pos=player.blockPosition().offset(3,0,0);
        for(String key:List.of("Thaumcraft:blockLootCrate","Thaumcraft:blockLootUrn"))for(int quality=0;quality<3;quality++) {
            var state=content.blocks().get(key).defaultBlockState().setValue(ModernContentBridge.METADATA,quality);
            level.setBlock(pos,state,3);
            var drops=Block.getDrops(state,level,pos,null,player,ItemStack.EMPTY);
            check(!drops.isEmpty(),"No loot for "+key+"@"+quality);
            check(drops.stream().noneMatch(s->s.is(content.items().get(key))),"Loot block dropped itself instead of rewards: "+key);
            results.add(Map.of("block",key,"quality",quality,"drops",drops.toString()));
            level.removeBlock(pos,false);
        }
        return results;
    }
    private Object door(ServerLevel level) throws Exception {
        var pos=player.blockPosition().offset(-3,0,0);var services=content.services();var loader=services.loader();
        var block=content.blocks().get("Thaumcraft:blockArcaneDoor");
        level.setBlock(pos,block.defaultBlockState().setValue(ModernContentBridge.METADATA,0),3);
        level.setBlock(pos.above(),block.defaultBlockState().setValue(ModernContentBridge.METADATA,8),3);
        Object world=services.worlds().bound(level);Object original=loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
        original=original.getClass().getMethod("func_82594_a",String.class).invoke(original,"Thaumcraft:blockArcaneDoor");
        Object actor=content.itemUse().clientActor(world,ModernContentBridge.placer(player),null);
        Object tile=((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();tile.getClass().getField("owner").set(tile,player.getName().getString());
        int before=sounds.size();
        final Object door=original;
        for(int i=0;i<2;i++)content.logicTransaction(level,pos,()->{
            door.getClass().getMethod("func_149727_a",loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,float.class,float.class,float.class)
                    .invoke(door,world,pos.getX(),pos.getY(),pos.getZ(),actor,1,0f,0f,0f);return null;
        },true);
        check(sounds.size()-before==2,"Manual door did not emit exactly one sound per transition: "+sounds);
        before=sounds.size();
        for(boolean powered:List.of(true,false))content.logicTransaction(level,pos,()->{
            door.getClass().getMethod("onPoweredBlockChange",loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class,boolean.class)
                    .invoke(door,world,pos.getX(),pos.getY(),pos.getZ(),powered);return null;
        },true);
        check(sounds.size()-before==2,"Powered door did not emit exactly one sound per transition: "+sounds);
        var output=List.copyOf(sounds.subList(sounds.size()-4,sounds.size()));
        check(output.stream().allMatch(s->s.startsWith("minecraft:block.wooden_door.")),"Door used obsolete sound ids: "+output);
        level.removeBlock(pos.above(),false);level.removeBlock(pos,false);return output;
    }
    private Object fireArrow(ServerLevel level,int scenario) throws Exception {
        if(arrowTarget!=null)arrowTarget.discard();
        for(var entity:level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(32)))entity.discard();
        arrowTarget=EntityType.PIG.create(level);arrowTarget.moveTo(player.getX(),player.getY()+1.1,player.getZ()+4,180,0);
        arrowTarget.setNoAi(true);arrowTarget.setNoGravity(true);
        arrowTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);arrowTarget.setHealth(1000);
        arrowTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20);level.addFreshEntity(arrowTarget);
        boolean crossbow=scenario>6;int element=scenario==0?-1:(scenario-1)%6;
        player.getInventory().clearContent();player.getInventory().selected=0;
        var weapon=new ItemStack(crossbow?net.minecraft.world.item.Items.CROSSBOW:net.minecraft.world.item.Items.BOW);player.setItemInHand(InteractionHand.MAIN_HAND,weapon);
        player.getInventory().setItem(1,element<0?new ItemStack(net.minecraft.world.item.Items.ARROW,4):content.stack("Thaumcraft:PrimalArrow",element,4,""));
        weapon.use(level,player,InteractionHand.MAIN_HAND);
        weapon.releaseUsing(level,player,weapon.getUseDuration()-30);player.stopUsingItem();
        if(crossbow)weapon.use(level,player,InteractionHand.MAIN_HAND);
        if(element>=0)check(player.getInventory().getItem(1).getCount()==3,"Element arrow ammo was not consumed");
        return Map.of("weapon",crossbow?"crossbow":"bow","element",element);
    }
    private Object verifyArrow(ServerLevel level,int scenario) {
        float damage=1000-arrowTarget.getHealth();int element=scenario==0?-1:(scenario-1)%6;
        var effects=arrowTarget.getActiveEffects().toString();
        check(damage>0,"Arrow did not damage target; effects="+effects);
        if(element==-1)normalDamage=damage;
        if(element==0)check(damage>normalDamage*2,"Air arrow did not bypass armor: normal="+normalDamage+", air="+damage);
        if(element==1)check(arrowTarget.isOnFire(),"Fire arrow did not ignite target");
        if(element==2)check(arrowTarget.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Water arrow did not slow target");
        if(element==3)check(damage>normalDamage,"Earth arrow did not increase damage");
        if(element==4)check(arrowTarget.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"Order arrow did not weaken target");
        if(element==5)check(arrowTarget.hasEffect(net.minecraft.world.effect.MobEffects.WITHER),"Entropy arrow did not wither target");
        return Map.of("damage",damage,"onFire",arrowTarget.isOnFire(),"effects",effects);
    }
    private Object summon(ServerLevel level) throws Exception {
        target=EntityType.PIG.create(level);target.moveTo(player.getX(),player.getY()+1,player.getZ()+4,0,0);
        target.setNoAi(true);target.setNoGravity(true);level.addFreshEntity(target);
        var services=content.services();var loader=services.loader();Object world=services.worlds().bound(level);
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        Object wand=services.items().materialize(new LegacyClientData.Stack("Thaumcraft:WandCasting",0,1,"","{rod:\"silverwood\",cap:\"thaumium\"}"));
        Object item=stackType.getMethod("func_77973_b").invoke(wand);
        var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
        for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))
            item.getClass().getMethod("storeVis",stackType,aspectType,int.class).invoke(item,wand,aspectType.getMethod("getAspect",String.class).invoke(null,tag),10000);
        Object focus=services.items().materialize(new LegacyClientData.Stack("Thaumcraft:FocusHellbat",0,1,"",""));
        item.getClass().getMethod("setFocus",stackType,stackType).invoke(item,wand,focus);
        player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,content.gameplayStack(services.items().snapshot(wand)));
        player.getMainHandItem().use(level,player,InteractionHand.MAIN_HAND);
        var bats=level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(16)).stream()
                .filter(e->e.legacyOrNull()!=null&&e.legacyOrNull().getClass().getName().endsWith("EntityFireBat")).toList();
        check(bats.size()==1,"Expected one summoned FireBat, found "+bats.size());bat=bats.get(0).legacyOrNull();
        return content.services().entityHosting().state(bat).toString();
    }
}
