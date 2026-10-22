package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Original Forge 1.7.10 IFluidHandler tanks, addressed by old fluid registry names. No independent fluid buffer. */
public final class LegacyFluidAccess {
    /** {@code fluid} is null for an empty tank; {@code tagged} tanks hold NBT-bearing fluid that is not bridged. */
    public record Tank(String fluid,int amount,int capacity,boolean tagged) {}
    private final LegacyServices services;
    private Class<?> handler,direction,fluidType,stackType,registry;
    private boolean checked;
    public LegacyFluidAccess(LegacyServices services){this.services=services;}
    public synchronized boolean available() {
        if(!checked) {
            try {
                var loader=services.loader();
                handler=loader.loadClass("net.minecraftforge.fluids.IFluidHandler");direction=loader.loadClass("net.minecraftforge.common.util.ForgeDirection");
                fluidType=loader.loadClass("net.minecraftforge.fluids.Fluid");stackType=loader.loadClass("net.minecraftforge.fluids.FluidStack");
                registry=loader.loadClass("net.minecraftforge.fluids.FluidRegistry");
            }catch(ClassNotFoundException absent){handler=null;}
            checked=true;
        }
        return handler!=null;
    }
    public boolean supports(Object tile){return tile!=null&&available()&&handler.isInstance(tile);}
    private Object side(Integer side) {
        int value=side==null?6:side;if(value<0||value>6)throw new IllegalArgumentException("Invalid fluid side: "+value);
        return direction.getEnumConstants()[value];
    }
    private boolean live(Object tile) throws Exception {
        var type=services.loader().loadClass("net.minecraft.tileentity.TileEntity");
        if(!type.isInstance(tile)||(boolean)type.getMethod("func_145837_r").invoke(tile))return false;
        return LegacyWorlds.hostOrNull(type.getMethod("func_145831_w").invoke(tile))!=null;
    }
    private boolean server(Object tile) throws Exception {
        var host=LegacyWorlds.hostOrNull(services.loader().loadClass("net.minecraft.tileentity.TileEntity").getMethod("func_145831_w").invoke(tile));
        return host!=null&&!host.clientSide();
    }
    /** Old registry name of a registered fluid, or null when this runtime has no such fluid. */
    public String registered(String name) throws Exception {
        if(name==null||!available())return null;
        return services.items().inLegacy(()->registry.getMethod("getFluid",String.class).invoke(null,name)==null?null:name);
    }
    public List<Tank> tanks(Object tile,Integer side) throws Exception {
        if(!supports(tile))return List.of();
        return services.items().inLegacy(()->{
            if(!live(tile))return List.<Tank>of();
            Object[] info=(Object[])handler.getMethod("getTankInfo",direction).invoke(tile,side(side));
            if(info==null)return List.<Tank>of();
            var result=new ArrayList<Tank>();
            for(Object tank:info) {
                if(tank==null)continue;
                int capacity=tank.getClass().getField("capacity").getInt(tank);
                Object stack=tank.getClass().getField("fluid").get(tank);
                if(stack==null){result.add(new Tank(null,0,Math.max(0,capacity),false));continue;}
                int amount=stackType.getField("amount").getInt(stack);
                result.add(new Tank(amount<=0?null:name(stack),Math.max(0,amount),Math.max(0,capacity),stackType.getField("tag").get(stack)!=null));
            }
            return List.copyOf(result);
        });
    }
    private String name(Object stack) throws Exception {
        Object fluid=stackType.getMethod("getFluid").invoke(stack);
        return fluid==null?null:(String)fluidType.getMethod("getName").invoke(fluid);
    }
    private Object stack(String name,int amount) throws Exception {
        Object fluid=registry.getMethod("getFluid",String.class).invoke(null,name);
        return fluid==null?null:stackType.getConstructor(fluidType,int.class).newInstance(fluid,amount);
    }
    public int fill(Object tile,Integer side,String fluid,int amount,boolean simulate) throws Exception {
        if(amount<=0||fluid==null||!supports(tile))return 0;
        return services.items().inLegacy(()->{
            if(!live(tile)||!server(tile))return 0;
            Object stack=stack(fluid,amount);if(stack==null)return 0;
            if(!(boolean)handler.getMethod("canFill",direction,fluidType).invoke(tile,side(side),stackType.getMethod("getFluid").invoke(stack)))return 0;
            return LegacyEnergyAccess.transferred((int)handler.getMethod("fill",direction,stackType,boolean.class).invoke(tile,side(side),stack,!simulate),amount);
        });
    }
    /** Drains up to {@code amount}; {@code fluid} null drains whatever the original handler offers. Returns null when nothing moved. */
    public Tank drain(Object tile,Integer side,String fluid,int amount,boolean simulate) throws Exception {
        if(amount<=0||!supports(tile))return null;
        return services.items().inLegacy(()->{
            if(!live(tile)||!server(tile))return null;
            Object drained;
            if(fluid==null) {
                Object preview=handler.getMethod("drain",direction,int.class,boolean.class).invoke(tile,side(side),amount,false);
                if(preview==null||stackType.getField("tag").get(preview)!=null||name(preview)==null)return null;
                if(!(boolean)handler.getMethod("canDrain",direction,fluidType).invoke(tile,side(side),stackType.getMethod("getFluid").invoke(preview)))return null;
                drained=simulate?preview:handler.getMethod("drain",direction,int.class,boolean.class).invoke(tile,side(side),amount,true);
            } else {
                Object request=stack(fluid,amount);if(request==null)return null;
                if(!(boolean)handler.getMethod("canDrain",direction,fluidType).invoke(tile,side(side),stackType.getMethod("getFluid").invoke(request)))return null;
                drained=handler.getMethod("drain",direction,stackType,boolean.class).invoke(tile,side(side),request,!simulate);
            }
            if(drained==null)return null;
            int moved=LegacyEnergyAccess.transferred(stackType.getField("amount").getInt(drained),amount);
            String name=name(drained);
            if(moved==0||name==null)return null;
            if(fluid!=null&&!fluid.equals(name))throw new IllegalStateException("Original drain returned "+name+" for request "+fluid);
            if(stackType.getField("tag").get(drained)!=null) {
                if(simulate)return null;
                throw new IllegalStateException("Original drain returned NBT-bearing "+name);
            }
            return new Tank(name,moved,0,false);
        });
    }
}
