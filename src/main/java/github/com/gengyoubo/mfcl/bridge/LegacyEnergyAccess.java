package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.lang.reflect.Method;

/** Optional CoFH 1.7.10 RF interfaces, kept entirely inside the private runtime. No independent energy buffer. */
public final class LegacyEnergyAccess {
    private final LegacyServices services;
    private Class<?> receiver,provider,connection,direction;
    private boolean checked;
    public LegacyEnergyAccess(LegacyServices services){this.services=services;}
    public synchronized boolean available() {
        if(!checked) {
            try {
                var loader=services.loader();
                receiver=loader.loadClass("cofh.api.energy.IEnergyReceiver");provider=loader.loadClass("cofh.api.energy.IEnergyProvider");
                connection=loader.loadClass("cofh.api.energy.IEnergyConnection");direction=loader.loadClass("net.minecraftforge.common.util.ForgeDirection");
            }catch(ClassNotFoundException absent){receiver=null;provider=null;connection=null;}
            checked=true;
        }
        return connection!=null;
    }
    public boolean supports(Object tile){return tile!=null&&available()&&(receiver.isInstance(tile)||provider.isInstance(tile));}
    private Object side(Integer side) {
        int value=side==null?6:side;if(value<0||value>6)throw new IllegalArgumentException("Invalid RF side: "+value);
        return direction.getEnumConstants()[value];
    }
    private boolean live(Object tile) throws Exception {
        var type=services.loader().loadClass("net.minecraft.tileentity.TileEntity");
        if(!type.isInstance(tile)||(boolean)type.getMethod("func_145837_r").invoke(tile))return false;
        Object world=type.getMethod("func_145831_w").invoke(tile);
        return LegacyWorlds.hostOrNull(world)!=null;
    }
    public boolean connected(Object tile,Integer side) throws Exception {
        return supports(tile)&&services.items().inLegacy(()->live(tile)&&(boolean)connection.getMethod("canConnectEnergy",direction).invoke(tile,side(side)));
    }
    public boolean canReceive(Object tile,Integer side) throws Exception {return supports(tile)&&receiver.isInstance(tile)&&connected(tile,side);}
    public boolean canExtract(Object tile,Integer side) throws Exception {return supports(tile)&&provider.isInstance(tile)&&connected(tile,side);}
    public int stored(Object tile,Integer side) throws Exception {return read(tile,side,"getEnergyStored");}
    public int capacity(Object tile,Integer side) throws Exception {return read(tile,side,"getMaxEnergyStored");}
    private int read(Object tile,Integer side,String name) throws Exception {
        if(!connected(tile,side))return 0;
        return services.items().inLegacy(()->{
            int result=(int)(receiver.isInstance(tile)?receiver:provider).getMethod(name,direction).invoke(tile,side(side));
            if(result<0)throw new IllegalStateException("Negative original RF "+name);return result;
        });
    }
    public int receive(Object tile,Integer side,int amount,boolean simulate) throws Exception {return transfer(tile,side,amount,simulate,true);}
    public int extract(Object tile,Integer side,int amount,boolean simulate) throws Exception {return transfer(tile,side,amount,simulate,false);}
    private int transfer(Object tile,Integer side,int amount,boolean simulate,boolean insert) throws Exception {
        if(amount<=0||!(insert?canReceive(tile,side):canExtract(tile,side)))return 0;
        return services.items().inLegacy(()->{
            Object world=services.loader().loadClass("net.minecraft.tileentity.TileEntity").getMethod("func_145831_w").invoke(tile);
            var host=LegacyWorlds.hostOrNull(world);if(host==null||host.clientSide())return 0;
            Method method=(insert?receiver:provider).getMethod(insert?"receiveEnergy":"extractEnergy",direction,int.class,boolean.class);
            return transferred((int)method.invoke(tile,side(side),amount,simulate),amount);
        });
    }
    public static int transferred(int result,int maximum) {
        if(result<0||result>maximum)throw new IllegalStateException("Energy transfer returned "+result+" for request "+maximum);return result;
    }
    /** A typed old TileEntity for a native FE neighbor; only implemented directions are advertised as RF interfaces. */
    public Object hostTile(Object world,int x,int y,int z,HostEnergyAccess endpoint) throws Exception {
        if(!available())return null;
        int mask=0;for(int side=0;side<=6;side++){if(endpoint.canReceive(side))mask|=1;if(endpoint.canExtract(side))mask|=2;}
        if(mask==0)return null;
        int kind=mask;
        return services.items().inLegacy(()->{
            String name="github.com.gengyoubo.mfcl.legacy1710.HostEnergyTile"+kind;
            Class<?> type;try{type=services.loader().loadClass(name);}catch(ClassNotFoundException missing){type=define(name,kind);}
            Object tile=type.getConstructor(HostEnergyAccess.class).newInstance(endpoint);
            var base=services.loader().loadClass("net.minecraft.tileentity.TileEntity");
            base.getMethod("func_145834_a",services.loader().loadClass("net.minecraft.world.World")).invoke(tile,world);
            base.getField("field_145851_c").setInt(tile,x);base.getField("field_145848_d").setInt(tile,y);base.getField("field_145849_e").setInt(tile,z);
            return tile;
        });
    }
    private Class<?> define(String name,int mask) throws Exception {
        String owner=name.replace('.','/'),base="net/minecraft/tileentity/TileEntity",api=Type.getInternalName(HostEnergyAccess.class);
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String[] interfaces=mask==3?new String[]{"cofh/api/energy/IEnergyHandler"}:new String[]{"cofh/api/energy/IEnergy"+(mask==1?"Receiver":"Provider")};
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,owner,null,base,interfaces);
        writer.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"endpoint","L"+api+";",null,null).visitEnd();
        var init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(L"+api+";)V",null,null);init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,base,"<init>","()V",false);
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitVarInsn(Opcodes.ALOAD,1);init.visitFieldInsn(Opcodes.PUTFIELD,owner,"endpoint","L"+api+";");init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();
        for(String method:new String[]{"canConnectEnergy","getEnergyStored","getMaxEnergyStored","receiveEnergy","extractEnergy"}) {
            boolean transfer=method.equals("receiveEnergy")||method.equals("extractEnergy");
            if(transfer&&(method.equals("receiveEnergy")?(mask&1)==0:(mask&2)==0))continue;
            String desc="(Lnet/minecraftforge/common/util/ForgeDirection;"+(transfer?"IZ":"")+")"+(method.equals("canConnectEnergy")?"Z":"I");
            var code=writer.visitMethod(Opcodes.ACC_PUBLIC,method,desc,null,null);code.visitCode();
            code.visitVarInsn(Opcodes.ALOAD,0);code.visitFieldInsn(Opcodes.GETFIELD,owner,"endpoint","L"+api+";");code.visitVarInsn(Opcodes.ALOAD,1);
            if(transfer){code.visitVarInsn(Opcodes.ILOAD,2);code.visitVarInsn(Opcodes.ILOAD,3);}
            code.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(LegacyEnergyAccess.class),method,
                    "(L"+api+";Ljava/lang/Object;"+(transfer?"IZ":"")+")"+(method.equals("canConnectEnergy")?"Z":"I"),false);
            code.visitInsn(Opcodes.IRETURN);code.visitMaxs(0,0);code.visitEnd();
        }
        var tick=writer.visitMethod(Opcodes.ACC_PUBLIC,"canUpdate","()Z",null,null);tick.visitCode();tick.visitInsn(Opcodes.ICONST_0);tick.visitInsn(Opcodes.IRETURN);tick.visitMaxs(1,1);tick.visitEnd();
        writer.visitEnd();byte[] bytes=writer.toByteArray();
        return (Class<?>)services.loader().getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class).invoke(services.loader(),name,bytes,0,bytes.length);
    }
    private static int index(Object side){return side==null?6:((Enum<?>)side).ordinal();}
    public static boolean canConnectEnergy(HostEnergyAccess endpoint,Object side){int from=index(side);return endpoint.canReceive(from)||endpoint.canExtract(from);}
    public static int getEnergyStored(HostEnergyAccess endpoint,Object side){return endpoint.stored(index(side));}
    public static int getMaxEnergyStored(HostEnergyAccess endpoint,Object side){return endpoint.capacity(index(side));}
    public static int receiveEnergy(HostEnergyAccess endpoint,Object side,int amount,boolean simulate){return amount<=0?0:endpoint.receive(index(side),amount,simulate);}
    public static int extractEnergy(HostEnergyAccess endpoint,Object side,int amount,boolean simulate){return amount<=0?0:endpoint.extract(index(side),amount,simulate);}
}
