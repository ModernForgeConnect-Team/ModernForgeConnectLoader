package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import java.nio.file.*;
import java.util.*;

/** Diagnostic execution of original TC4 recipes and held wand callbacks; failures are retained as audit data. */
public final class ThaumcraftWandAuditProbe {
    private final LegacyServices services;
    private final ClassLoader loader;
    private final Class<?> stackType, playerType, inventoryType, aspectType;
    private final Object world, player;
    private final LegacyItemUse use;
    private final LegacyTiles.Placer source = new LegacyTiles.Placer(new UUID(701,702),"WandAudit",4.5,64,-2,0,24,false,false);
    private final Map<String,Object> tiles = new HashMap<>();
    private final Map<String,HostWorldAccess.Block> blocks = new HashMap<>();
    private final Map<String,Object> report = new LinkedHashMap<>();
    private Object recipe;
    private String outputKey;
    private ThaumcraftWandAuditProbe(LegacyServices services) throws Exception {
        this.services=services;loader=services.loader();stackType=loader.loadClass("net.minecraft.item.ItemStack");
        playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");inventoryType=loader.loadClass("net.minecraft.inventory.IInventory");
        aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");use=new LegacyItemUse(services);
        world=services.worlds().bind(this,new HostWorldAccess(){
            public String dimensionKey(){return "mfcl:wand_audit";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
            public long gameTime(){return 100;}public long dayTime(){return 100;}public void setDayTime(long value){}
            public Block block(int x,int y,int z){return blocks.getOrDefault(x+","+y+","+z,new Block("minecraft:air",0));}
            public boolean setBlock(int x,int y,int z,Block block,int flags){blocks.put(x+","+y+","+z,block);return true;}
            public Object tileEntity(int x,int y,int z){return tiles.get(x+","+y+","+z);}
            public void setTileEntity(int x,int y,int z,Object tile){tiles.put(x+","+y+","+z,tile);}
            public void markTileDirty(int x,int y,int z,Object tile){}public void blockUpdated(int x,int y,int z){}
            public void blockEvent(int x,int y,int z,String block,int event,int data){}
            public boolean originalBlockRayTrace(){return true;}
        });
        try(var state=services.playerState().enter(world)){player=use.clientActor(world,source,null);}
    }
    private Object stack(String key,int meta,String tag) throws Exception {return services.items().materialize(new LegacyClientData.Stack(key,meta,1,"",tag));}
    private Object aspect(String tag) throws Exception {return aspectType.getMethod("getAspect",String.class).invoke(null,tag);}
    private Object item(Object stack) throws Exception {return stackType.getMethod("func_77973_b").invoke(stack);}
    private Object copy(Object stack) throws Exception {return stackType.getMethod("func_77946_l").invoke(stack);}
    private Object wand(boolean charged) throws Exception {
        Object stack=stack("Thaumcraft:WandCasting",0,"{rod:\"wood\",cap:\"iron\"}");
        if(charged)for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))
            item(stack).getClass().getMethod("storeVis",stackType,aspectType,int.class).invoke(item(stack),stack,aspect(tag),2500);
        return stack;
    }
    private Map<String,Integer> vis(Object wand) throws Exception {
        var values=new LinkedHashMap<String,Integer>();
        for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))
            values.put(tag,(int)item(wand).getClass().getMethod("getVis",stackType,aspectType).invoke(item(wand),wand,aspect(tag)));
        return values;
    }
    private void audit(String name,java.util.concurrent.Callable<Map<String,Object>> action) {
        try(var packets=name.equals("nodeChargingHeldUseBridge")||name.equals("passiveRodInventoryTick")?null:LegacyPackets.capture(source.id())) {
            var entry=new LinkedHashMap<String,Object>();entry.put("status","PASS");entry.putAll(action.call());report.put(name,entry);
        }catch(Throwable failure){while(failure instanceof java.lang.reflect.InvocationTargetException&&failure.getCause()!=null)failure=failure.getCause();
            report.put(name,Map.of("status","FAIL","failure",failure.toString()));}
    }
    private Object table(Object wand) throws Exception {
        Object tile=services.tiles().create("Thaumcraft:blockTable",15,world,0,64,0);
        blocks.put("0,64,0",new HostWorldAccess.Block("Thaumcraft:blockTable",15));tiles.put("0,64,0",tile);
        var type=recipe.getClass();var inputs=(Object[])type.getMethod("getInput").invoke(recipe);
        int width=type.getField("width").getInt(recipe);
        for(int i=0;i<inputs.length;i++) {
            Object input=inputs[i];if(input instanceof List<?> options)input=options.isEmpty()?null:options.get(0);
            if(input!=null){input=stackType.getMethod("func_77946_l").invoke(input);stackType.getField("field_77994_a").setInt(input,1);
                if((int)stackType.getMethod("func_77960_j").invoke(input)==32767)stackType.getMethod("func_77964_b",int.class).invoke(input,0);}
            inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(tile,(i/width)*3+i%width,input);
        }
        inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(tile,10,wand);
        return tile;
    }
    private Object container(Object tile) throws Exception {
        Object inventory=playerType.getField("field_71071_by").get(player);
        Object container=loader.loadClass("thaumcraft.common.container.ContainerArcaneWorkbench")
                .getConstructor(inventory.getClass(),tile.getClass()).newInstance(inventory,tile);
        playerType.getField("field_71070_bA").set(player,container);return container;
    }
    private Object slot(Object tile,int index) throws Exception {return inventoryType.getMethod("func_70301_a",int.class).invoke(tile,index);}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void run() throws Exception {
        try(var state=services.playerState().enter(world)) {
            audit("basicIronWoodWandAssembly",()->{
                Object cap=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandCap").getField("caps").get(null)).get("iron");
                Object rod=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandRod").getField("rods").get(null)).get("wood");
                Object grid=loader.loadClass("net.minecraft.inventory.InventoryCrafting").getConstructor(loader.loadClass("net.minecraft.inventory.Container"),int.class,int.class)
                        .newInstance(loader.loadClass("thaumcraft.common.container.ContainerDummy").getConstructor().newInstance(),3,3);
                inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(grid,0,cap.getClass().getMethod("getItem").invoke(cap));
                inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(grid,4,rod.getClass().getMethod("getItem").invoke(rod));
                inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(grid,8,cap.getClass().getMethod("getItem").invoke(cap));
                var managerType=loader.loadClass("net.minecraft.item.crafting.CraftingManager");Object manager=managerType.getMethod("func_77594_a").invoke(null);
                Object result=managerType.getMethod("func_82787_a",grid.getClass(),loader.loadClass("net.minecraft.world.World")).invoke(manager,grid,world);
                check(result!=null&&services.items().snapshot(result).key().equals("Thaumcraft:WandCasting"),"Basic wand recipe did not match");
                return Map.of("output",services.items().snapshot(result));
            });
            var recipes=(List<?>)loader.loadClass("thaumcraft.api.ThaumcraftApi").getMethod("getCraftingRecipes").invoke(null);
            for(Object candidate:recipes)if(candidate.getClass().getSimpleName().equals("ShapedArcaneRecipe")) {
                Object output=candidate.getClass().getMethod("getRecipeOutput").invoke(candidate);
                Object cost=candidate.getClass().getMethod("getAspects").invoke(candidate);
                Object[] aspects=(Object[])cost.getClass().getMethod("getAspects").invoke(cost);boolean small=aspects.length>0;
                for(Object aspect:aspects)if((int)cost.getClass().getMethod("getAmount",aspectType).invoke(cost,aspect)>15)small=false;
                if(output!=null&&small){recipe=candidate;outputKey=services.items().snapshot(output).key();break;}
            }
            check(recipe!=null,"No small original arcane recipe found");
            String research=(String)recipe.getClass().getMethod("getResearch").invoke(recipe);
            report.put("recipe",Map.of("output",outputKey,"research",research,"type",recipe.getClass().getName()));
            Object knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null));
            @SuppressWarnings("unchecked") var completed=(Map<String,ArrayList<String>>)knowledge.getClass().getField("researchCompleted").get(knowledge);
            var learned=completed.computeIfAbsent(source.name(),ignored->new ArrayList<>());if(!learned.contains(research))learned.add(research);
            audit("advancedGoldGreatwoodWandAssembly",()->{
                Object cap=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandCap").getField("caps").get(null)).get("gold");
                Object rod=((Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandRod").getField("rods").get(null)).get("greatwood");
                for(Object part:List.of(cap,rod)){String key=(String)part.getClass().getMethod("getResearch").invoke(part);if(!learned.contains(key))learned.add(key);}
                Object tile=services.tiles().create("Thaumcraft:blockTable",15,world,0,64,0);tiles.put("0,64,0",tile);blocks.put("0,64,0",new HostWorldAccess.Block("Thaumcraft:blockTable",15));
                Object tool=stack("Thaumcraft:WandCasting",0,"{rod:\"silverwood\",cap:\"thaumium\"}");
                for(String tag:List.of("aer","ignis","aqua","terra","ordo","perditio"))item(tool).getClass().getMethod("storeVis",stackType,aspectType,int.class).invoke(item(tool),tool,aspect(tag),10000);
                inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(tile,2,copy(cap.getClass().getMethod("getItem").invoke(cap)));
                inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(tile,4,copy(rod.getClass().getMethod("getItem").invoke(rod)));
                inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(tile,6,copy(cap.getClass().getMethod("getItem").invoke(cap)));
                inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(tile,10,tool);
                Object inventory=playerType.getField("field_71071_by").get(player);inventory.getClass().getMethod("func_70437_b",stackType).invoke(inventory,new Object[]{null});
                Object container=container(tile);check(slot(tile,9)!=null,"Advanced wand recipe produced no output");var before=vis(tool);
                container.getClass().getMethod("func_75144_a",int.class,int.class,int.class,playerType).invoke(container,0,0,0,player);
                Object output=inventory.getClass().getMethod("func_70445_o").invoke(inventory);check(output!=null,"Advanced wand was lost");
                var result=services.items().snapshot(output);check(result.key().equals("Thaumcraft:WandCasting")&&result.tag().contains("greatwood")&&result.tag().contains("gold"),"Wrong advanced wand parts");
                check(!vis(tool).equals(before),"Advanced wand did not consume vis");
                inventory.getClass().getMethod("func_70437_b",stackType).invoke(inventory,new Object[]{null});
                return Map.of("output",result,"beforeToolVis",before,"afterToolVis",vis(tool));
            });
            audit("arcaneRejectsMissingWand",()->{Object tile=table(null);container(tile);check(slot(tile,9)==null,"Output exists without a wand");return Map.of("outputAbsent",true);});
            audit("arcaneRejectsEmptyWand",()->{Object tile=table(wand(false));container(tile);check(slot(tile,9)==null,"Output exists with no vis");return Map.of("outputAbsent",true);});
            audit("arcaneResearchGate",()->{learned.remove(research);try{Object tile=table(wand(true));container(tile);check(slot(tile,9)==null,"Locked recipe has an output");return Map.of("outputAbsent",true);}finally{learned.add(research);}});
            audit("arcaneCraftConsumesIngredientsAndVis",()->{
                Object charged=wand(true),tile=table(charged),container=container(tile),output=slot(tile,9);
                check(output!=null,"Charged wand and known recipe produced no output");
                Object expected=stackType.getMethod("func_77946_l").invoke(charged);
                Object cost=recipe.getClass().getMethod("getAspects",inventoryType).invoke(recipe,tile);
                item(expected).getClass().getMethod("consumeAllVisCrafting",stackType,playerType,cost.getClass(),boolean.class).invoke(item(expected),expected,player,cost,true);
                var before=vis(charged);
                container.getClass().getMethod("func_75144_a",int.class,int.class,int.class,playerType).invoke(container,0,0,0,player);
                Object inventory=playerType.getField("field_71071_by").get(player),carried=inventory.getClass().getMethod("func_70445_o").invoke(inventory);
                check(carried!=null&&services.items().snapshot(carried).key().equals(outputKey),"Crafted item was lost");
                for(int i=0;i<9;i++)check(slot(tile,i)==null,"Ingredient remained in slot "+i);
                check(vis(charged).equals(vis(expected))&&!vis(charged).equals(before),"Vis was not deducted exactly once");
                byte[] saved=services.tiles().save(tile);Object loaded=services.tiles().create("Thaumcraft:blockTable",15,world,0,64,0);services.tiles().load(loaded,saved);
                check(vis(slot(loaded,10)).equals(vis(charged)),"Wand vis did not survive tile NBT reload");
                return Map.of("output",services.items().snapshot(carried),"beforeRawVis",before,"afterRawVis",vis(charged),"savedVisPreserved",true);
            });
            audit("nodeChargingHeldUseBridge",()->{
                blocks.put("4,64,0",new HostWorldAccess.Block("Thaumcraft:blockAiry",0));
                Object node=services.tiles().create("Thaumcraft:blockAiry",0,world,4,64,0);tiles.put("4,64,0",node);
                Object aspects=loader.loadClass("thaumcraft.api.aspects.AspectList").getConstructor().newInstance();
                aspects.getClass().getMethod("add",aspectType,int.class).invoke(aspects,aspect("aer"),20);
                node.getClass().getMethod("setAspects",aspects.getClass()).invoke(node,aspects);
                Object empty=wand(false);var start=services.items().snapshot(empty);use.rightClick(world,source,start);
                var active=services.playerInventory().read(player).get(0);check(active!=null&&active.tag().contains("IIUX"),"Node did not begin wand use");
                for(int remaining=100;remaining>0;remaining--){use.usingTick(world,source,active,remaining);active=services.playerInventory().read(player).get(0);}
                Object after=services.items().materialize(services.playerInventory().read(player).get(0));
                int left=(int)aspects.getClass().getMethod("getAmount",aspectType).invoke(aspects,aspect("aer"));
                var observation=new LinkedHashMap<String,Object>();observation.put("nodeAirBefore",20);observation.put("nodeAirAfter",left);observation.put("heldWandRawVis",vis(after));
                observation.put("status",vis(after).get("aer")>0?"PASS":"FAIL");
                if(vis(after).get("aer")==0)observation.put("failure","Held-use bridge does not return changed wand NBT to the held inventory stack");
                return observation;
            });
            audit("focusChangePacketBridge",()->{
                Object focus=null;
                for(var candidate:services.items().catalog()) {
                    Object stack=services.items().materialize(candidate);
                    if(loader.loadClass("thaumcraft.api.wands.ItemFocusBasic").isInstance(item(stack))){focus=stack;break;}
                }
                check(focus!=null,"No original focus fixture");
                Object held=wand(false),inventory=playerType.getField("field_71071_by").get(player);
                inventory.getClass().getField("field_70461_c").setInt(inventory,0);
                inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,0,held);
                inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,1,copy(focus));
                String key=(String)item(focus).getClass().getMethod("getSortingHelper",stackType).invoke(item(focus),focus);
                var packetType=loader.loadClass("thaumcraft.common.lib.network.misc.PacketFocusChangeToServer");
                Object packet=packetType.getConstructor(playerType,String.class).newInstance(player,key);
                Object channel=loader.loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null);
                LegacyPackets.receiveServer(services,LegacyPackets.encode(channel,packet,"SERVER"),player,null);
                Object attached=item(held).getClass().getMethod("getFocusItem",stackType).invoke(item(held),held);
                check(attached!=null&&services.items().snapshot(attached).key().equals(services.items().snapshot(focus).key()),"Focus was not equipped");
                check(inventory.getClass().getMethod("func_70301_a",int.class).invoke(inventory,1)==null,"Equipping duplicated a focus");
                packet=packetType.getConstructor(playerType,String.class).newInstance(player,"REMOVE");
                LegacyPackets.receiveServer(services,LegacyPackets.encode(channel,packet,"SERVER"),player,null);
                check(item(held).getClass().getMethod("getFocusItem",stackType).invoke(item(held),held)==null,"Focus was not removed");
                check(inventory.getClass().getMethod("func_70301_a",int.class).invoke(inventory,1)!=null,"Removed focus was lost");
                packet=packetType.getConstructor(playerType,String.class).newInstance(player,"missing-focus");
                try{LegacyPackets.receiveServer(services,LegacyPackets.encode(channel,packet,"SERVER"),player,null);throw new AssertionError("Unknown focus was accepted");}catch(IllegalArgumentException expected){}
                return Map.of("status","PASS","equippedAndReturnedExactlyOnce",true,"unknownFocusRejected",true);
            });
            audit("passiveRodInventoryTick",()->{
                var rods=(Map<?,?>)loader.loadClass("thaumcraft.api.wands.WandRod").getField("rods").get(null);Object rod=null;
                for(Object candidate:rods.values())if(candidate.getClass().getMethod("getOnUpdate").invoke(candidate)!=null){rod=candidate;break;}
                check(rod!=null,"No passive rod fixture");String tag=(String)rod.getClass().getMethod("getTag").invoke(rod);
                Object held=stack("Thaumcraft:WandCasting",0,"{rod:\""+tag+"\",cap:\"gold\"}");
                Object inventory=playerType.getField("field_71071_by").get(player);inventory.getClass().getMethod("func_70299_a",int.class,stackType).invoke(inventory,0,held);
                check(use.inventoryUpdates(services.items().snapshot(held)),"Original wand update was not detected");
                use.inventoryTick(world,source,0,200,true);Object updated=inventory.getClass().getMethod("func_70301_a",int.class).invoke(inventory,0);
                check(vis(updated).values().stream().mapToInt(Integer::intValue).sum()>0,"Passive wand rod did not replenish vis");
                return Map.of("rod",tag,"vis",vis(updated));
            });
        }
    }
    /** TC4's ClientRegistry bindings reach the host list, and host input drives the original KeyBinding semantics. */
    private void keyBindings() {
        audit("originalKeyBindings",()->{
            var keys=new LegacyKeyBindings(services);var bindings=keys.modBindings();
            var found=new LinkedHashMap<String,Object>();for(var binding:bindings)found.put(binding.description(),binding.category()+":"+binding.defaultCode());
            check(found.entrySet().containsAll(Map.of("Change Wand Focus","key.categories.misc:33","Activate Hover Harness","key.categories.misc:35","Misc Wand Toggle","key.categories.misc:34").entrySet()),"Unexpected original bindings "+found);
            var focus=bindings.stream().filter(binding->binding.description().equals("Change Wand Focus")).findFirst().orElseThrow();
            var type=loader.loadClass("net.minecraft.client.settings.KeyBinding");
            check(keys.apply(List.of(new LegacyKeyBindings.State(focus,47,true,2)),true),"Press was not reported");
            check((int)type.getMethod("func_151463_i").invoke(focus.handle())==47,"Rebinding did not reach the original binding");
            check((boolean)type.getMethod("func_151470_d").invoke(focus.handle()),"Held state was not applied");
            check((boolean)type.getMethod("func_151468_f").invoke(focus.handle())&&(boolean)type.getMethod("func_151468_f").invoke(focus.handle())
                    &&!(boolean)type.getMethod("func_151468_f").invoke(focus.handle()),"isPressed did not consume exactly two presses");
            keys.apply(List.of(new LegacyKeyBindings.State(focus,47,true,1)),false);
            check(!(boolean)type.getMethod("func_151470_d").invoke(focus.handle())&&!(boolean)type.getMethod("func_151468_f").invoke(focus.handle()),"Screen focus did not release the binding");
            keys.postKeyInput();
            var names=keys.translate("zh_cn",List.of("key.categories.misc","Change Wand Focus"));
            return Map.of("bindings",found,"translations",names);
        });
    }
    public static void main(String[] args) throws Exception {
        Path game=Path.of(args[0]);
        try(var runtime=new EmbeddedLegacyRuntime(game,Path.of(args[1]),new LegacyRuntimeService().inspect(game))) {
            var boot=runtime.boot();if(!Boolean.TRUE.equals(boot.get("legacyFmlStarted")))throw new IllegalStateException(boot.toString());
            var services=runtime.services();services.items().inLegacy(()->{var audit=new ThaumcraftWandAuditProbe(services);audit.run();audit.keyBindings();
                Path output=game.resolve("logs/mfcl/wand-functions-audit.json");Files.createDirectories(output.getParent());
                Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(audit.report));
                System.out.println("MFCL_WAND_AUDIT_WRITTEN "+output);return null;});
        }
    }
}
