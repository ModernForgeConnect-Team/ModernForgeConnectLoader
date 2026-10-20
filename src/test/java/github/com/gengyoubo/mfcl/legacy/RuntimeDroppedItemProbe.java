package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Real EntityItem watcher contents and native IDs, without any reference mod. */
final class RuntimeDroppedItemProbe {
    static void verify(LegacyServices services,Map<String,Object> report) throws Exception {
        var loader=services.loader();Object identity=new Object();UUID firstId=new UUID(51,52),secondId=new UUID(53,54);
        var wool=new LegacyClientData.Stack("minecraft:wool",11,3,"","{mfcl_drop:\"retained\"}");
        var coal=new LegacyClientData.Stack("minecraft:coal",1,2,"","");
        var snapshots=new ArrayList<HostWorldAccess.Entity>();
        snapshots.add(new HostWorldAccess.Entity(firstId,"net.minecraft.entity.item.EntityItem","",1,2,3,0,0,0,0,.25f,.25f,false,false,71,wool));
        snapshots.add(new HostWorldAccess.Entity(secondId,"net.minecraft.entity.item.EntityItem","",2,2,3,0,0,0,0,.25f,.25f,false,false,72,coal));
        snapshots.add(new HostWorldAccess.Entity(new UUID(55,56),"net.minecraft.entity.Entity","",3,2,3,0,0,0,0,.25f,.25f,false,false,73,null));
        Object world=services.worlds().bind(identity,new HostWorldAccess() {
            public String dimensionKey(){return "mfcl:drops";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return false;}
            public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
            public Block block(int x,int y,int z){return new Block("minecraft:air",0);}public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
            public List<Entity> entities(double a,double b,double c,double d,double e,double f){return List.copyOf(snapshots);}
        });
        try {
            var item=loader.loadClass("net.minecraft.entity.item.EntityItem");var base=loader.loadClass("net.minecraft.entity.Entity");
            Object box=loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a",double.class,double.class,double.class,double.class,double.class,double.class).invoke(null,0d,0d,0d,5d,5d,5d);
            var entities=LegacyWorlds.entities(world,item,box);
            if(entities.size()!=2)throw new AssertionError("Non-item entity became a fake dropped stack");
            Object first=entities.get(0);var stack=services.items().snapshot(item.getMethod("func_92059_d").invoke(first));
            if(!stack.key().equals(wool.key())||stack.metadata()!=11||stack.count()!=3||!stack.tag().contains("retained")
                    ||(int)base.getMethod("func_145782_y").invoke(first)!=71||!firstId.equals(base.getMethod("func_110124_au").invoke(first)))throw new AssertionError("Dropped stack metadata/NBT/count or entity identity was lost: "+stack);
            Object old=item.getMethod("func_92059_d").invoke(first);old.getClass().getField("field_77994_a").setInt(old,1);
            var again=LegacyWorlds.entities(world,item,box);
            if(services.items().snapshot(item.getMethod("func_92059_d").invoke(again.get(0))).count()!=3
                    ||(int)base.getMethod("func_145782_y").invoke(again.get(0))!=71
                    ||(int)base.getMethod("func_145782_y").invoke(again.get(1))!=72)throw new AssertionError("Drop mirrors aliased their source or changed IDs between queries");
            var second=services.items().snapshot(item.getMethod("func_92059_d").invoke(again.get(1)));
            if(!second.key().equals(coal.key())||second.metadata()!=1||second.count()!=2)throw new AssertionError("Different drops shared a default stone stack");
            String coalName=(String)loader.loadClass("net.minecraft.item.ItemStack").getMethod("func_82833_r").invoke(item.getMethod("func_92059_d").invoke(again.get(1)));
            if(!LegacyItemUse.scanTargetName(loader,again.get(1),0,0).equals(coalName)||coalName.startsWith("item."))
                throw new AssertionError("Dropped item scan target used EntityItem's untranslated entity name: "+LegacyItemUse.scanTargetName(loader,again.get(1),0,0));
            report.put("genericDroppedItemStackAndIdentityVerified",true);
        }finally{services.worlds().unbind(identity);}
    }
}
