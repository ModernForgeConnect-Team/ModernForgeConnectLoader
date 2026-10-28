package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.*;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Durable native custom statistics and advancement trees around the original StatBase/Achievement identities. */
public final class LegacyStatisticsBridge {
    private record Entry(String raw,ResourceLocation stat,ResourceLocation advancement,String parent,Object title,Object description,String icon,boolean special){}
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    LegacyStatisticsBridge(LegacyServices services)throws Exception {
        services.items().inLegacy(()->{
            var loader=services.loader();var stat=loader.loadClass("net.minecraft.stats.StatBase");var achievement=loader.loadClass("net.minecraft.stats.Achievement");
            var owners=new IdentityHashMap<Object,String>();for(var owned:LegacyRegistrations.statistics(loader))owners.put(owned.value(),owned.owner());
            for(Object value:(List<?>)loader.loadClass("net.minecraft.stats.StatList").getField("field_75940_b").get(null)){
                String raw=(String)stat.getField("field_75975_e").get(value),owner=owners.getOrDefault(value,"minecraft");
                var id=LegacyRegistryIds.id(owner+":legacy_stat/"+raw.replace(':','/'));ResourceLocation advancementId=null;String parent=null,icon="minecraft:book";Object description=Map.of("text",raw);boolean special=false;
                if(achievement.isInstance(value)){
                    advancementId=LegacyRegistryIds.id(owner+":legacy_achievement/"+raw.replace(':','/'));Object oldParent=achievement.getField("field_75992_c").get(value);if(oldParent!=null)parent=(String)stat.getField("field_75975_e").get(oldParent);
                    var oldIcon=services.items().snapshot(achievement.getField("field_75990_d").get(value));if(oldIcon!=null){
                        if(oldIcon.key().startsWith("minecraft:")){var mapped=HostStackMapping.renderStack(oldIcon);if(!mapped.isEmpty())icon=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mapped.getItem()).toString();}
                        else icon=LegacyRegistryIds.id(oldIcon.key()).toString();
                    }
                    var key=achievement.getDeclaredField("field_75996_k");key.setAccessible(true);description=Map.of("translate",(String)key.get(value));special=(boolean)achievement.getMethod("func_75984_f").invoke(value);
                }
                Object chat=stat.getMethod("func_150951_e").invoke(value);Object title;
                if(loader.loadClass("net.minecraft.util.ChatComponentTranslation").isInstance(chat))title=Map.of("translate",chat.getClass().getMethod("func_150268_i").invoke(chat));
                else title=Map.of("text",loader.loadClass("net.minecraft.util.IChatComponent").getMethod("func_150260_c").invoke(chat));
                entries.put(raw,new Entry(raw,id,advancementId,parent,title,description,icon,special));
            }return null;
        });
    }
    void register(RegisterEvent event){event.register(Registries.CUSTOM_STAT,registry->entries.values().forEach(entry->registry.register(entry.stat(),entry.stat())));}
    public Integer read(ServerPlayer player,String raw){var entry=entries.get(raw);return entry==null?null:player.getStats().getValue(Stats.CUSTOM.get(entry.stat()));}
    public Stat<ResourceLocation> statistic(String raw){var entry=entries.get(raw);return entry==null?null:Stats.CUSTOM.get(entry.stat());}
    public void write(ServerPlayer player,String raw,int value){
        var entry=entries.get(raw);if(entry==null){LegacyDiagnostics.missingMapping("late statistic "+raw,"Original statistic was not registered before native registry freeze");return;}
        player.getStats().setValue(player,Stats.CUSTOM.get(entry.stat()),Math.max(0,value));
        if(entry.advancement()!=null){var advancement=player.server.getAdvancements().getAdvancement(entry.advancement());if(advancement!=null){if(value>0)player.getAdvancements().award(advancement,"legacy");else player.getAdvancements().revoke(advancement,"legacy");}}
        player.getStats().sendStats(player);
    }
    void generate(LegacyContentPack pack)throws Exception {
        for(var entry:entries.values())if(entry.advancement()!=null){
            var root=new LinkedHashMap<String,Object>();var display=new LinkedHashMap<String,Object>();display.put("title",entry.title());display.put("description",entry.description());display.put("icon",Map.of("item",entry.icon()));display.put("frame",entry.special()?"challenge":"task");display.put("show_toast",true);display.put("announce_to_chat",false);
            var parent=entry.parent()==null?null:entries.get(entry.parent());if(parent!=null&&parent.advancement()!=null)root.put("parent",parent.advancement().toString());else display.put("background","minecraft:textures/gui/advancements/backgrounds/stone.png");
            root.put("display",display);root.put("criteria",Map.of("legacy",Map.of("trigger","minecraft:impossible")));pack.json("data/"+entry.advancement().getNamespace()+"/advancements/"+entry.advancement().getPath()+".json",root);
        }
    }
}
