package github.com.gengyoubo.mfcl.bridge;

/** Transports already encoded, addressed FML packets; original channel codecs and client handlers remain in charge. */
public final class LegacyEmbeddedPackets {
    private static final String PREFIX="mfcl:embedded/";
    private LegacyEmbeddedPackets() {}
    private static Object attribute(ClassLoader loader,Object channel,Object key) throws Exception {
        Object attr=loader.loadClass("io.netty.channel.Channel").getMethod("attr",loader.loadClass("io.netty.util.AttributeKey")).invoke(channel,key);
        return loader.loadClass("io.netty.util.Attribute").getMethod("get").invoke(attr);
    }
    public static boolean send(Object context,Object message,Object promise) throws Exception {
        ClassLoader loader=context.getClass().getClassLoader();
        Class<?> packetType=loader.loadClass("cpw.mods.fml.common.network.internal.FMLProxyPacket");
        if(!packetType.isInstance(message))return false;
        Object channel=loader.loadClass("io.netty.channel.ChannelHandlerContext").getMethod("channel").invoke(context);
        Class<?> outbound=loader.loadClass("cpw.mods.fml.common.network.FMLOutboundHandler");
        Object target=attribute(loader,channel,outbound.getField("FML_MESSAGETARGET").get(null));
        Class<?> registry=loader.loadClass("cpw.mods.fml.common.network.NetworkRegistry");
        Object side=attribute(loader,channel,registry.getField("CHANNEL_SOURCE").get(null));
        if(!"SERVER".equals(String.valueOf(side))||!"PLAYER".equals(String.valueOf(target)))return false;
        Object player=attribute(loader,channel,outbound.getField("FML_MESSAGETARGETARGS").get(null));
        if(player==null)return false;
        Object buffer=packetType.getMethod("payload").invoke(message);
        Class<?> buf=loader.loadClass("io.netty.buffer.ByteBuf");
        int size=(int)buf.getMethod("readableBytes").invoke(buffer);
        if(size>1024*1024)throw new IllegalArgumentException("Old embedded packet exceeds host limit");
        byte[] bytes=new byte[size];
        buf.getMethod("getBytes",int.class,byte[].class).invoke(buffer,buf.getMethod("readerIndex").invoke(buffer),bytes);
        if(!LegacyPackets.sendClientPayload(player,PREFIX+packetType.getMethod("channel").invoke(message),bytes))return false;
        buf.getMethod("release").invoke(buffer);
        loader.loadClass("io.netty.channel.ChannelPromise").getMethod("setSuccess").invoke(promise);
        return true;
    }
    public static boolean receive(LegacyServices services,LegacyPackets.Message message) throws Exception {
        if(!message.channel().startsWith(PREFIX))return false;
        if(message.discriminator()!=0)throw new IllegalArgumentException("Invalid embedded packet envelope");
        ClassLoader loader=services.loader();Class<?> side=loader.loadClass("cpw.mods.fml.relauncher.Side");
        Class<?> registry=loader.loadClass("cpw.mods.fml.common.network.NetworkRegistry");
        String name=message.channel().substring(PREFIX.length());
        Object channel=registry.getMethod("getChannel",String.class,side).invoke(registry.getField("INSTANCE").get(null),name,side.getField("CLIENT").get(null));
        if(channel==null)throw new IllegalArgumentException("Unknown old embedded channel: "+name);
        Class<?> buf=loader.loadClass("io.netty.buffer.ByteBuf");
        Object buffer=loader.loadClass("io.netty.buffer.Unpooled").getMethod("wrappedBuffer",byte[].class).invoke(null,(Object)message.payload());
        try {
            Object packet=loader.loadClass("cpw.mods.fml.common.network.internal.FMLProxyPacket").getConstructor(buf,String.class).newInstance(buffer,name);
            loader.loadClass("io.netty.channel.embedded.EmbeddedChannel").getMethod("writeInbound",Object[].class).invoke(channel,(Object)new Object[]{packet});
        }finally{if((int)buf.getMethod("refCnt").invoke(buffer)>0)buf.getMethod("release").invoke(buffer);}
        return true;
    }
}
