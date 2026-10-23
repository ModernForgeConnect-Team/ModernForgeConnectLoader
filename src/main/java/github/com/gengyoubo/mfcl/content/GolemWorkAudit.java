package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.*;

/** Opt-in investigation. Survival/installation are reported separately from useful work. */
final class GolemWorkAudit {
    private final ModernContentBridge content;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final List<LegacyHostedEntity> hosts=new ArrayList<>();
    private long started=-1;
    private boolean done;
    private GolemWorkAudit(ModernContentBridge content) { this.content=content; }
    static void install(ModernContentBridge content) { MinecraftForge.EVENT_BUS.addListener(new GolemWorkAudit(content)::tick); }
    private static Throwable cause(Throwable t) { while(t.getCause()!=null)t=t.getCause();return t; }
    private void tick(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();var level=server.overworld();
        try {
            if(started<0) { setup(level);started=level.getGameTime();return; }
            if(level.getGameTime()-started<240)return;
            var services=content.services();var type=services.loader().loadClass("thaumcraft.common.entities.golems.EntityGolemBase");
            services.items().inLegacy(()->{
                for(int i=0;i<rows.size();i++) {
                    var row=rows.get(i);var host=hosts.get(i);if(host==null)continue;
                    var original=host.legacyOrNull();row.put("alive",!host.isRemoved()&&original!=null);
                    row.put("position",List.of(host.getX(),host.getY(),host.getZ()));
                    if(original==null)continue;
                    row.put("originalTicks",type.getField("field_70173_aa").getInt(original));
                    row.put("health",type.getMethod("func_110143_aJ").invoke(original));
                    row.put("coreAfter",type.getMethod("getCore").invoke(original));
                    row.put("paused",type.getField("paused").get(original));row.put("inactive",type.getField("inactive").get(original));
                    row.put("bootup",type.getField("bootup").get(original));
                    row.put("aiEnabled",type.getMethod("func_70650_aV").invoke(original));
                    row.put("updater",type.getMethod("func_70071_h_").getDeclaringClass().getName());
                    Object tasks=type.getField("field_70714_bg").get(original);
                    var schedulerTicks=tasks.getClass().getDeclaredField("field_75778_d");schedulerTicks.setAccessible(true);
                    row.put("schedulerTicks",schedulerTicks.get(tasks));
                    var names=new ArrayList<String>();
                    for(Object entry:(List<?>)tasks.getClass().getField("field_75782_a").get(tasks))
                        names.add(entry.getClass().getField("field_75733_a").get(entry).getClass().getSimpleName());
                    row.put("tasks",names);
                }
                return null;
            });
            diagnose(level);
            report.put("observedServerTicks",level.getGameTime()-started);report.put("status","COMPLETE");
            finish(server,null);
        } catch(Throwable failure) { report.put("status","FAIL");report.put("failure",cause(failure).toString());finish(server,failure); }
    }
    private void setup(ServerLevel level) throws Exception {
        var services=content.services();var loader=services.loader();var world=services.worlds().bound(level);
        var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");
        var materials=loader.loadClass("thaumcraft.common.entities.golems.EnumGolemType");
        Object[] values=materials.getEnumConstants();
        for(int x=-1;x<=4;x++)for(int z=-1;z<=4;z++)level.setChunkForced(x,z,true);
        for(int x=-2;x<=64;x++)for(int z=-2;z<=60;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());
        var player=FakePlayerFactory.getMinecraft(level);player.setPos(0,100,-10);level.addNewPlayer(player);
        report.put("materials",Arrays.stream(values).map(Object::toString).toList());report.put("matrix",rows);
        for(boolean advanced:new boolean[]{false,true})for(Object material:values)for(int core=-1;core<12;core++) {
            int index=rows.size(),x=(index%16)*4,z=(index/16)*4;var row=new LinkedHashMap<String,Object>();
            row.put("material",material.toString());row.put("advanced",advanced);row.put("core",core);rows.add(row);
            LegacyHostedEntity host=null;
            try {
                Object original=services.items().inLegacy(()->{
                    Object golem=type.getConstructor(loader.loadClass("net.minecraft.world.World"),materials,boolean.class).newInstance(world,material,advanced);
                    type.getMethod("func_70107_b",double.class,double.class,double.class).invoke(golem,x+0.5,100d,z+0.5);
                    type.getMethod("func_110171_b",int.class,int.class,int.class,int.class).invoke(golem,x+2,100,z,16);
                    type.getMethod("setOwner",String.class).invoke(golem,player.getName().getString());
                    type.getMethod("setupGolem").invoke(golem);type.getMethod("setupGolemInventory").invoke(golem);
                    return golem;
                });
                host=LegacyHostedEntity.host(content,level,original);
                row.put("spawned",level.addFreshEntity(host));
                if(core>=0) {
                    player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemGolemCore",core,2,""));
                    row.put("installConsumedAction",player.interactOn(host,InteractionHand.MAIN_HAND).consumesAction());
                    row.put("installConsumedExactlyOne",player.getMainHandItem().getCount()==1);
                }
                row.put("installedCore",services.items().inLegacy(()->type.getMethod("getCore").invoke(original)));
                player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:GolemBell",0,1,""));
                row.put("bellConsumedAction",player.interactOn(host,InteractionHand.MAIN_HAND).consumesAction());
                row.put("bellHasIdentity",content.gameplayStack(player.getMainHandItem()).tag().contains("golemid"));
            } catch(Throwable failure) { row.put("failure",cause(failure).toString());LogUtils.getLogger().error("GOLEM_AUDIT_CELL: {}",row,failure); }
            hosts.add(host);
        }
        player.discard();
        LogUtils.getLogger().info("GOLEM_AUDIT_SETUP: {} combinations",rows.size());
    }
    private void diagnose(ServerLevel level) throws Exception {
        var services=content.services();var loader=services.loader();
        var results=new LinkedHashMap<String,Object>();report.put("diagnostics",results);
        Object golem=hosts.get(16).legacyOrNull();
        services.items().inLegacy(()->{
            results.put("livingUpdateEventCanceled",loader.loadClass("net.minecraftforge.common.ForgeHooks")
                    .getMethod("onLivingUpdate",loader.loadClass("net.minecraft.entity.EntityLivingBase")).invoke(null,golem));
            try {
                content.logicTransaction(level,new BlockPos(0,100,4),()->{
                    golem.getClass().getMethod("func_70636_d").invoke(golem);return null;
                },true);
                results.put("directLivingUpdate","COMPLETED");
            } catch(Throwable error) { results.put("directLivingUpdate",cause(error).toString());LogUtils.getLogger().error("GOLEM_AUDIT_DIRECT_LIVING",error); }
            results.put("bootupAfterDirectLiving",golem.getClass().getField("bootup").get(golem));
            return null;
        });
    }
    private void finish(net.minecraft.server.MinecraftServer server,Throwable failure) {
        done=true;hosts.stream().filter(Objects::nonNull).forEach(LegacyHostedEntity::discard);
        try {
            var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/golem-work-audit.json");Files.createDirectories(path.getParent());
            Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch(Exception error) { throw new IllegalStateException(error); }
        if(failure!=null)LogUtils.getLogger().error("GOLEM_AUDIT_FAIL",failure);
        else LogUtils.getLogger().info("GOLEM_AUDIT_COMPLETE");
        server.halt(false);
    }
}
