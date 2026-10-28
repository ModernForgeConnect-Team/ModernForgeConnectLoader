package github.com.gengyoubo.mfcl.content;

import com.google.common.collect.ImmutableSet;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.trading.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Old profession IDs and IVillageTradeHandler offers exposed through real native villager menus. */
public final class LegacyVillagerBridge {
    private static final String ID="mfcl_legacy_profession",STAMP="mfcl_legacy_trades",INDICES="mfcl_legacy_trade_indices";
    private final ModernContentBridge content;
    private final Map<Integer,VillagerProfession> professions=new LinkedHashMap<>();
    private final Map<Integer,ResourceLocation> names=new LinkedHashMap<>();
    LegacyVillagerBridge(ModernContentBridge content){
        this.content=content;
        LegacyRegistrations.professions(content.services().loader()).forEach((id,owner)->{
            var key=LegacyRegistryIds.id(owner+":legacy_villager/"+id);names.put(id,key);
            professions.put(id,new VillagerProfession(key.toString(),poi->false,poi->false,ImmutableSet.of(),ImmutableSet.of(),net.minecraft.sounds.SoundEvents.VILLAGER_WORK_LIBRARIAN));
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event)->{
            if(!event.getLevel().isClientSide&&event.getTarget() instanceof Villager villager&&!villager.isBaby())ensureTrades(villager);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event)->{
            if(event.getEntity() instanceof Villager villager&&!villager.level().isClientSide){Integer id=original(villager);if(id==null||id<5||villager.getTradingPlayer()!=null)return;
                var data=villager.getPersistentData();if(data.getString(STAMP).isEmpty()||villager.getOffers().stream().noneMatch(MerchantOffer::isOutOfStock))return;
                int idle=data.getInt("mfcl_legacy_restock_idle")+1;if(idle>=40){villager.restock();idle=0;}data.putInt("mfcl_legacy_restock_idle",idle);
            }
        });
    }
    void register(RegisterEvent event){event.register(Registries.VILLAGER_PROFESSION,registry->professions.forEach((id,value)->registry.register(names.get(id),value)));}
    public VillagerProfession profession(int id){return switch(id){case 0->VillagerProfession.FARMER;case 1->VillagerProfession.LIBRARIAN;case 2->VillagerProfession.CLERIC;case 3->VillagerProfession.TOOLSMITH;case 4->VillagerProfession.BUTCHER;default->professions.get(id);};}
    public void apply(Villager villager,int id){
        var type=profession(id);if(type==null)throw new IllegalArgumentException("No native profession for old villager ID "+id);
        villager.setVillagerData(villager.getVillagerData().setProfession(type));villager.getPersistentData().putInt(ID,id);
        // The old professions have no job-site POI. An experienced villager retains its profession without one.
        if(id>=5&&villager.getVillagerXp()==0)villager.setVillagerXp(1);
    }
    private Integer original(Villager villager){
        var type=villager.getVillagerData().getProfession();for(var entry:professions.entrySet())if(entry.getValue()==type)return entry.getKey();
        if(type==VillagerProfession.FARMER)return 0;if(type==VillagerProfession.LIBRARIAN)return 1;if(type==VillagerProfession.CLERIC)return 2;
        if(type==VillagerProfession.ARMORER||type==VillagerProfession.TOOLSMITH||type==VillagerProfession.WEAPONSMITH)return 3;
        if(type==VillagerProfession.BUTCHER||type==VillagerProfession.LEATHERWORKER)return 4;return null;
    }
    public void ensureTrades(Villager villager){
        if(villager.level().isClientSide)return;Integer profession=original(villager);if(profession==null)return;
        try{content.services().items().inLegacy(()->{try(var scope=content.services().playerState().enter(content.services().worlds().bound(villager.level()))){
            var loader=content.services().loader();var registry=loader.loadClass("cpw.mods.fml.common.registry.VillagerRegistry");Object instance=registry.getMethod("instance").invoke(null);
            var field=registry.getDeclaredField("tradeHandlers");field.setAccessible(true);
            Object map=field.get(instance);int count=((Collection<?>)loader.loadClass("com.google.common.collect.Multimap").getMethod("get",Object.class).invoke(map,profession)).size();if(count==0)return null;
            String key=BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession())+"/"+count;var data=villager.getPersistentData();if(key.equals(data.getString(STAMP)))return null;
            var offers=villager.getOffers();if(data.getString(STAMP).startsWith(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession())+"/")){
                int[] old=data.getIntArray(INDICES);for(int i=old.length-1;i>=0;i--)if(old[i]>=0&&old[i]<offers.size())offers.remove(old[i]);
            }
            var oldWorld=loader.loadClass("net.minecraft.world.World");Object old=loader.loadClass("net.minecraft.entity.passive.EntityVillager").getConstructor(oldWorld,int.class).newInstance(content.services().worlds().bound(villager.level()),profession);
            old.getClass().getMethod("func_70107_b",double.class,double.class,double.class).invoke(old,villager.getX(),villager.getY(),villager.getZ());
            var list=loader.loadClass("net.minecraft.village.MerchantRecipeList");Object recipes=list.getConstructor().newInstance();
            registry.getMethod("manageVillagerTrades",list,loader.loadClass("net.minecraft.entity.passive.EntityVillager"),int.class,java.util.Random.class).invoke(null,recipes,old,profession,new java.util.Random(villager.getRandom().nextLong()));
            var recipe=loader.loadClass("net.minecraft.village.MerchantRecipe");var indices=new ArrayList<Integer>();
            for(Object offer:(List<?>)recipes)try{
                Object a=recipe.getMethod("func_77394_a").invoke(offer),b=recipe.getMethod("func_77396_b").invoke(offer),sell=recipe.getMethod("func_77397_d").invoke(offer);
                var first=content.gameplayStack(content.services().items().snapshot(a));var second=b==null?net.minecraft.world.item.ItemStack.EMPTY:content.gameplayStack(content.services().items().snapshot(b));var result=content.gameplayStack(content.services().items().snapshot(sell));
                Object tag=recipe.getMethod("func_77395_g").invoke(offer);var nbt=loader.loadClass("net.minecraft.nbt.NBTTagCompound");int uses=(int)nbt.getMethod("func_74762_e",String.class).invoke(tag,"uses"),max=(int)nbt.getMethod("func_74762_e",String.class).invoke(tag,"maxUses");
                indices.add(offers.size());offers.add(new MerchantOffer(first,second,result,Math.max(0,uses),Math.max(0,max),1,0f));
            }catch(Exception unmapped){LegacyDiagnostics.legacyFailure("villager trade "+profession,unmapped);}
            data.putString(STAMP,key);data.putIntArray(INDICES,indices.stream().mapToInt(Integer::intValue).toArray());return null;
        }});}catch(Exception failure){throw new IllegalStateException("Cannot expose original villager trades",failure);}
    }
    public boolean spawn(Object old,net.minecraft.server.level.ServerLevel level)throws Exception{
        var loader=content.services().loader();var type=loader.loadClass("net.minecraft.entity.passive.EntityVillager");if(old.getClass()!=type)return false;
        int id=(int)type.getMethod("func_70946_n").invoke(old);var villager=net.minecraft.world.entity.EntityType.VILLAGER.create(level);if(villager==null)return false;
        var state=content.services().entityHosting().state(old);villager.moveTo(state.x(),state.y(),state.z(),state.yaw(),state.pitch());villager.setAge((int)type.getMethod("func_70874_b").invoke(old));apply(villager,id);ensureTrades(villager);content.afterCommit(level,()->level.addFreshEntity(villager));return true;
    }
    void generateSkins(LegacyContentPack pack)throws Exception{
        var loader=content.services().loader();Object registry=loader.loadClass("cpw.mods.fml.common.registry.VillagerRegistry").getMethod("instance").invoke(null);var field=registry.getClass().getDeclaredField("newVillagers");field.setAccessible(true);var skins=(Map<?,?>)field.get(registry);
        for(var entry:names.entrySet()){
            var key=entry.getValue();var path=pack.directory().resolve("assets/"+key.getNamespace()+"/textures/entity/villager/profession/"+key.getPath()+".png");java.nio.file.Files.createDirectories(path.getParent());Object skin=skins==null?null:skins.get(entry.getKey());
            if(skin!=null){String raw=skin.toString();int colon=raw.indexOf(':');try(var input=loader.getResourceAsStream("assets/"+(colon<0?"minecraft":raw.substring(0,colon))+"/"+raw.substring(colon+1))){if(input!=null){java.nio.file.Files.copy(input,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);continue;}}}
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB),"PNG",path.toFile());
        }
    }
}
