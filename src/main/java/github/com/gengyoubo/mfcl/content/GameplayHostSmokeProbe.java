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
    private int batObservedTicks;
    private boolean batDeathPending;
    private int arrowCase=-1;
    private net.minecraft.world.entity.LivingEntity arrowTarget;
    private boolean originalPvp;
    private LegacyHostedEntity playerShot;
    private float normalDamage;
    private float incomingDamage;
    private boolean armorBypassed;
    private final List<String> sounds=new ArrayList<>();
    private boolean done;
    private GameplayHostSmokeProbe(ModernContentBridge content) { this.content=content; }
    static void install(ModernContentBridge content) {
        var probe=new GameplayHostSmokeProbe(content);MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event)->{
            if(event.getSound()!=null)probe.sounds.add(event.getSound().get().getLocation().toString());
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingHurtEvent event)->{
            if(event.getEntity()==probe.arrowTarget){probe.incomingDamage=event.getAmount();probe.armorBypassed=event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR);}
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
                originalPvp=level.getServer().isPvpAllowed();
                test("leafStates",()->leaves(level));
                test("lootStackRoundTrips",()->lootMappings());
                test("arrowDamageFlags",()->arrowFlags());
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
                if(++arrowCase<15){test("fireArrow"+arrowCase,()->fireArrow(level,arrowCase));ticks=0;return;}
                finish(level);return;
            }
            if(batDeathPending) {
                if(++ticks<80)return; // Original damage immunity and death animation run before setDead.
                test("hellbatTargetDeath",()->{
                    check(level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(32)).stream().noneMatch(e->e.legacyOrNull()==bat),"Bat kept chasing a removed target");
                    return true;
                });
                arrowCase=0;test("fireArrow0",()->fireArrow(level,0));ticks=0;return;
            }
            if(level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(32)).stream().anyMatch(e->e.legacyOrNull()==bat))batObservedTicks++;
            if(ticks==20&&target!=null)target.moveTo(player.getX()+3,target.getY(),player.getZ()+6,0,0);
            if(++ticks<80)return;
            test("hellbatTicks",()->{
                var hosted=level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(32)).stream()
                        .filter(e->e.legacyOrNull()==bat).findFirst();
                check(bat!=null&&batObservedTicks>=10,"Summoned bat did not survive ordinary server ticks");
                check(target.getHealth()<20,"Summoned bat never damaged its target");
                return Map.of("targetHealth",target.getHealth(),"observedTicks",batObservedTicks,"stillPresent",hosted.isPresent(),"state",content.services().entityHosting().state(bat).toString());
            });
            if(target!=null)target.discard();
            batDeathPending=true;ticks=0;
        }catch(Throwable failure){done=true;LogUtils.getLogger().error("MFCL gameplay probe failed",failure);event.getServer().halt(false);}
    }
    private void finish(ServerLevel level) throws Exception {
        done=true;
        level.getServer().setPvpAllowed(originalPvp);
        proof.put("status",proof.values().stream().anyMatch(v->v instanceof Map<?,?> m&&"FAIL".equals(m.get("status")))?"FAIL":"PASS");
        var output=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/gameplay-smoke.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        LogUtils.getLogger().info("MFCL_GAMEPLAY_SMOKE: {}",proof);level.getServer().halt(false);
    }
    private Object leaves(ServerLevel level) throws Exception {
        String key="Thaumcraft:blockMagicalLeaves";
        var leaves=content.declarations().blocks().stream().filter(block->block.key().equals(key)).findFirst().orElseThrow();
        var id=ModernContentBridge.id(key);
        var models=content.pack().directory().resolve("assets/"+id.getNamespace()+"/models/block/"+id.getPath());
        for(int meta=0;meta<16;meta++) {
            check(leaves.faces().get(meta).equals(leaves.faces().get(meta&1)),"Leaf state lost species texture: "+meta);
            for(String icon:leaves.faces().get(meta))check(!icon.isBlank()&&!content.pack().worldTexture(icon).equals("minecraft:item/barrier"),"Leaf state has placeholder texture: "+meta);
            check(!Files.readString(models.resolveSibling(models.getFileName()+"_"+meta+".json")).contains("minecraft:item/barrier"),"Generated leaf model has a barrier: "+meta);
        }
        var placed=new ArrayList<Integer>();var pos=player.blockPosition().offset(6,0,0);
        level.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        for(int species=0;species<2;species++) {
            level.removeBlock(pos,false);
            player.setItemInHand(InteractionHand.MAIN_HAND,content.stack(key,species,2,""));
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos),net.minecraft.core.Direction.UP,pos.below(),false);
            player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            var state=level.getBlockState(pos);
            check(state.is(content.blocks().get(key)),"Leaf item did not place species "+species);
            int meta=state.getValue(ModernContentBridge.METADATA);
            check((meta&1)==species&&!leaves.faces().get(meta).get(0).isBlank(),"Placed leaf state lost species texture: "+meta);
            placed.add(meta);
        }
        level.removeBlock(pos,false);level.removeBlock(pos.below(),false);
        check(content.pack().worldResource("minecraft:textures/entity/arrow.png").equals("minecraft:textures/entity/projectiles/arrow.png"),"Arrow renderer still uses the old vanilla resource");
        return Map.of("states",16,"placedMetadata",placed,"arrowTexture",content.pack().worldResource("minecraft:textures/entity/arrow.png"));
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
    private Object arrowFlags() throws Exception {
        var loader=content.services().loader();var entity=loader.loadClass("net.minecraft.entity.Entity");
        var type=loader.loadClass("thaumcraft.api.damagesource.DamageSourceIndirectThaumcraftEntity");
        Object source=type.getConstructor(String.class,entity,entity).newInstance("airarrow",null,null);
        type.getMethod("func_76348_h").invoke(source);
        check((boolean)type.getMethod("func_76363_c").invoke(source),"TC4 armor-bypass setter only wrote its private shadow field");
        type.getMethod("func_76361_j").invoke(source);
        check((boolean)type.getMethod("func_76347_k").invoke(source),"TC4 fire setter only wrote its private shadow field");
        return Map.of("bypassArmor",true,"fire",true);
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
        level.removeBlock(pos,false);level.removeBlock(pos.above(),false);level.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        player.getInventory().clearContent();player.getInventory().selected=0;
        player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemArcaneDoor",0,2,""));
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos),net.minecraft.core.Direction.UP,pos.below(),false);
        var context=new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,hit);
        int placementBefore=sounds.size();
        player.getMainHandItem().useOn(context);
        check(level.getBlockState(pos).is(block)&&level.getBlockState(pos.above()).is(block),"Door placement did not create both halves");
        check(player.getMainHandItem().getCount()==1,"Door placement did not consume exactly one item");
        check(sounds.size()==placementBefore+1&&sounds.get(placementBefore).equals("minecraft:block.metal.place"),"Door placement audio missing or duplicated: "+sounds);
        player.getMainHandItem().useOn(context);
        check(player.getMainHandItem().getCount()==1&&sounds.size()==placementBefore+1,"Blocked door placement consumed an item or played audio");
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
        int metadata=level.getBlockState(pos).getValue(ModernContentBridge.METADATA);tile.getClass().getField("owner").set(tile,"OtherOwner");
        before=sounds.size();
        content.logicTransaction(level,pos,()->{
            door.getClass().getMethod("func_149727_a",loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class,loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,float.class,float.class,float.class)
                    .invoke(door,world,pos.getX(),pos.getY(),pos.getZ(),actor,1,0f,0f,0f);return null;
        },true);
        check(level.getBlockState(pos).getValue(ModernContentBridge.METADATA)==metadata,"Unauthorized player opened the door");
        check(sounds.size()==before+1&&sounds.get(before).equals("thaumcraft:doorfail"),"Unauthorized door feedback missing or duplicated");
        level.removeBlock(pos.above(),false);level.removeBlock(pos,false);level.removeBlock(pos.below(),false);
        return Map.of("placement","minecraft:block.metal.place","transitions",output,"denied","thaumcraft:doorfail");
    }
    private Object fireArrow(ServerLevel level,int scenario) throws Exception {
        incomingDamage=0;armorBypassed=false;
        if(arrowTarget!=null)arrowTarget.discard();
        for(var entity:level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(32)))entity.discard();
        if(scenario>=13) {
            if(scenario==13)level.addNewPlayer(player);
            arrowTarget=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(new UUID(6930,5359+scenario),"ArrowTarget"+scenario));
            ((net.minecraft.world.entity.player.Player)arrowTarget).getAbilities().invulnerable=false;
            level.getServer().setPvpAllowed(scenario==14);
        } else arrowTarget=EntityType.PIG.create(level);
        arrowTarget.moveTo(player.getX(),player.getY()+1.1,player.getZ()+4,180,0);
        if(arrowTarget instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);
        arrowTarget.setNoGravity(true);
        arrowTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);arrowTarget.setHealth(1000);
        arrowTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20);
        if(arrowTarget instanceof net.minecraft.server.level.ServerPlayer targetPlayer)level.addNewPlayer(targetPlayer);else level.addFreshEntity(arrowTarget);
        boolean crossbow=scenario>6&&scenario<13;int element=scenario>=13?0:scenario==0?-1:(scenario-1)%6;
        player.getInventory().clearContent();player.getInventory().selected=0;
        var weapon=new ItemStack(crossbow?net.minecraft.world.item.Items.CROSSBOW:net.minecraft.world.item.Items.BOW);player.setItemInHand(InteractionHand.MAIN_HAND,weapon);
        player.getInventory().setItem(1,element<0?new ItemStack(net.minecraft.world.item.Items.ARROW,4):content.stack("Thaumcraft:PrimalArrow",element,4,""));
        weapon.use(level,player,InteractionHand.MAIN_HAND);
        weapon.releaseUsing(level,player,weapon.getUseDuration()-30);player.stopUsingItem();
        if(crossbow)weapon.use(level,player,InteractionHand.MAIN_HAND);
        // Remove critical-roll variance when comparing armor and earth damage across separate shots.
        for(var arrow:level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,player.getBoundingBox().inflate(32)))arrow.setCritArrow(false);
        var arrowType=content.services().loader().loadClass("net.minecraft.entity.projectile.EntityArrow");
        for(var hosted:level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(32)))
            if(arrowType.isInstance(hosted.legacyOrNull())) {
                arrowType.getMethod("func_70243_d",boolean.class).invoke(hosted.legacyOrNull(),false);
                if(scenario>=13)playerShot=hosted;
            }
        if(element>=0)check(player.getInventory().getItem(1).getCount()==3,"Element arrow ammo was not consumed");
        return Map.of("weapon",crossbow?"crossbow":"bow","element",element);
    }
    private Object verifyArrow(ServerLevel level,int scenario) {
        float damage=1000-arrowTarget.getHealth();int element=scenario==0?-1:(scenario-1)%6;
        if(scenario>=13) {
            check(scenario==13?damage==0:damage>0,"Player arrow hit did not honor PvP rules: "+scenario+", damage="+damage);
            if(scenario==13)check(playerShot!=null&&!playerShot.isRemoved(),"PvP-disabled arrow crashed instead of passing the protected player");
            return Map.of("pvp",scenario==14,"damage",damage);
        }
        var effects=arrowTarget.getActiveEffects().toString();
        check(damage>0,"Arrow did not damage target; effects="+effects);
        if(element==-1)normalDamage=damage;
        if(element==0)check(armorBypassed&&Math.abs(damage-incomingDamage)<.001f,"Air arrow did not bypass armor: raw="+incomingDamage+", actual="+damage+", bypass="+armorBypassed);
        if(element==1)check(arrowTarget.isOnFire(),"Fire arrow did not ignite target");
        if(element==2)check(arrowTarget.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Water arrow did not slow target");
        if(element==3)check(damage>normalDamage,"Earth arrow did not increase damage");
        if(element==4)check(arrowTarget.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"Order arrow did not weaken target");
        if(element==5)check(arrowTarget.hasEffect(net.minecraft.world.effect.MobEffects.WITHER),"Entropy arrow did not wither target");
        return Map.of("damage",damage,"onFire",arrowTarget.isOnFire(),"effects",effects,"incomingDamage",incomingDamage,"bypassedArmor",armorBypassed);
    }
    private Object summon(ServerLevel level) throws Exception {
        target=EntityType.PIG.create(level);target.moveTo(player.getX(),player.getY()+1,player.getZ()+4,0,0);
        target.setNoAi(true);target.setNoGravity(true);
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
        String before=content.gameplayStack(player.getMainHandItem()).tag();
        player.getMainHandItem().use(level,player,InteractionHand.MAIN_HAND);player.stopUsingItem();
        check(before.equals(content.gameplayStack(player.getMainHandItem()).tag()),"No-target summon consumed vis or changed the wand");
        // Separate the negative and positive cases: even an unsuccessful original cast sets a real-time cooldown.
        Object actor=content.itemUse().clientActor(world,ModernContentBridge.placer(player),null);
        loader.loadClass("thaumcraft.common.items.wands.WandManager").getMethod("setCooldown",loader.loadClass("net.minecraft.entity.EntityLivingBase"),int.class).invoke(null,actor,0);
        level.addFreshEntity(target);
        player.getMainHandItem().use(level,player,InteractionHand.MAIN_HAND);
        var bats=level.getEntitiesOfClass(LegacyHostedEntity.class,player.getBoundingBox().inflate(16)).stream()
                .filter(e->e.legacyOrNull()!=null&&e.legacyOrNull().getClass().getName().endsWith("EntityFireBat")).toList();
        check(bats.size()==1,"Expected one summoned FireBat, found "+bats.size());bat=bats.get(0).legacyOrNull();
        return content.services().entityHosting().state(bat).toString();
    }
}
