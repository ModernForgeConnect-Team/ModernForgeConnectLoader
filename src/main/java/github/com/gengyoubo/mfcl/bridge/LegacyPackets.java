package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Carries registered old SimpleNetworkWrapper client messages over the host connection. */
public final class LegacyPackets {
    public record Message(String channel, int discriminator, byte[] payload) {}
    private record Handler(Object instance, Class<?> type, int discriminator, String side) {}
    private static final class Channel {
        final String name; final List<Handler> handlers = new ArrayList<>(); final NetworkRegistrationGuard guard;
        Channel(String name) { this.name = name; guard = NetworkRegistrationGuard.legacy(name); }
    }
    private static final Map<Object,Channel> CHANNELS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Object> WORLD=new ThreadLocal<>();
    static AutoCloseable world(Object world){
        Object before=WORLD.get();WORLD.set(world);
        return ()->{if(before==null)WORLD.remove();else WORLD.set(before);};
    }
    /** Broadcasts use the current authoritative callback's world so host transaction rollback can discard them. */
    public static boolean broadcastClientPayload(String channel,byte[] payload){
        var host=LegacyWorlds.hostOrNull(WORLD.get());
        if(host==null||host.clientSide())return false;
        if(payload.length>1024*1024)throw new IllegalArgumentException("Old broadcast packet exceeds host limit");
        host.broadcastPacket(new Message(channel,0,payload.clone()));return true;
    }
    public interface ServerPolicy { boolean prepare(Object packet,Object player,LegacyDeviceContainers.Descriptor device) throws Exception; }
    private static final Map<ClassLoader,List<ServerPolicy>> SERVER_POLICIES=new java.util.WeakHashMap<>();
    private static final ThreadLocal<Object> SERVER_PLAYER=new ThreadLocal<>();
    @FunctionalInterface public interface ClientReceiver { void receive(byte[] payload) throws Exception; }
    private static final Map<ClassLoader,Map<String,ClientReceiver>> CLIENT_RECEIVERS=new java.util.WeakHashMap<>();
    public static synchronized void clientReceiver(ClassLoader loader,String channel,ClientReceiver receiver) {
        CLIENT_RECEIVERS.computeIfAbsent(loader,key->new LinkedHashMap<>()).put(channel,receiver);
    }
    private static synchronized ClientReceiver clientReceiver(ClassLoader loader,String channel) {
        return CLIENT_RECEIVERS.getOrDefault(loader,Map.of()).get(channel);
    }
    /** Raw event channels retain their original codec; the host owns routing and transport. */
    public static boolean sendClientPayload(Object player,String channel,byte[] payload) throws Exception {
        var entity=player.getClass().getClassLoader().loadClass("net.minecraft.entity.Entity");
        var host=LegacyWorlds.hostOrNull(entity.getField("field_70170_p").get(player));
        if(host==null||host.clientSide())return false;
        if(payload.length>1024*1024)throw new IllegalArgumentException("Old client packet exceeds host limit");
        UUID recipient=(UUID)entity.getMethod("func_110124_au").invoke(player);
        var message=new Message(channel,0,payload.clone());
        Scope scope=CURRENT.get();
        if(scope!=null) {
            if(!scope.recipients.contains(recipient))throw new UnsupportedOperationException("Old callback sent a packet to another player");
            scope.messages.add(message);scope.addressed.computeIfAbsent(recipient,key->new ArrayList<>()).add(message);
        } else host.sendPacket(recipient,message);
        return true;
    }
    public static void allowServer(ClassLoader loader,ServerPolicy policy){SERVER_POLICIES.computeIfAbsent(loader,key->new ArrayList<>()).add(policy);}
    private LegacyPackets() {}
    public static void channel(Object wrapper, String name) { CHANNELS.put(wrapper,new Channel(name)); }
    /** Fails the original registerMessage call, and so legacy initialization, on an ambiguous ID or message type. */
    public static void register(Object wrapper, Object handler, Class<?> message, int discriminator, Object side) {
        Channel channel=CHANNELS.get(wrapper);
        if(channel==null)throw new IllegalStateException("Old network message "+message.getName()+" registered on an unknown channel");
        channel.guard.register(discriminator,message,side.toString());
        channel.handlers.add(new Handler(handler,message,discriminator&0xFF,side.toString()));
    }
    public static Scope capture(UUID recipient) { return new Scope(Set.of(recipient)); }
    /** Captures old client packets for any of these players, kept per recipient (e.g. a command targeting another player). */
    public static Scope captureFor(Set<UUID> recipients) { return new Scope(Set.copyOf(recipients)); }
    /** Also captures the one client request accepted by {@code completion}; any other old serverbound packet fails. */
    public static Scope captureScan(UUID recipient,java.util.function.Predicate<Object> completion) {var scope=new Scope(Set.of(recipient));scope.completion=Objects.requireNonNull(completion);return scope;}
    public static final class Scope implements AutoCloseable {
        private final Set<UUID> recipients;
        private final List<Message> messages = new ArrayList<>();
        private final Map<UUID,List<Message>> addressed = new LinkedHashMap<>();
        private java.util.function.Predicate<Object> completion;
        private Object scanRequest;
        private Scope(Set<UUID> recipients) {
            if (CURRENT.get()!=null) throw new IllegalStateException("Nested old packet capture");
            this.recipients=recipients;CURRENT.set(this);
        }
        public List<Message> messages() { return List.copyOf(messages); }
        public List<Message> messages(UUID recipient) { return List.copyOf(addressed.getOrDefault(recipient,List.of())); }
        public Object scanRequest() {return scanRequest;}
        @Override public void close() { CURRENT.remove(); }
    }
    public static boolean sendToServer(Object wrapper,Object message) throws Exception {
        if(LegacyDeviceInput.send(wrapper,message))return true;
        Scope scope=CURRENT.get();if(scope==null||scope.completion==null)return false;
        if(!scope.completion.test(message))throw new UnsupportedOperationException("Unexpected packet during scan: "+message.getClass().getName());
        scope.scanRequest=message;return true;
    }
    public static Message encode(Object wrapper,Object message,String side) throws Exception {
        Channel channel=Objects.requireNonNull(CHANNELS.get(wrapper),"Unregistered old network channel");
        Handler handler=channel.handlers.stream().filter(h->h.side.equals(side)&&h.type==message.getClass()).findFirst().orElseThrow();
        var loader=message.getClass().getClassLoader();Class<?> bufferType=loader.loadClass("io.netty.buffer.ByteBuf");
        Object buffer=loader.loadClass("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        try {
            loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage").getMethod("toBytes",bufferType).invoke(message,buffer);
            int size=(int)bufferType.getMethod("readableBytes").invoke(buffer);
            if(size>1024*1024)throw new IllegalArgumentException("Old packet exceeds host limit");
            byte[] payload=new byte[size];bufferType.getMethod("readBytes",byte[].class).invoke(buffer,(Object)payload);
            return new Message(channel.name,handler.discriminator,payload);
        }finally{bufferType.getMethod("release").invoke(buffer);}
    }
    /** Only the authenticated player is visible to this packet's legacy entity-ID lookup. */
    public static Object serverEntity(Object world,int id) throws Exception {
        Object player=SERVER_PLAYER.get();if(player==null)return null;
        var entity=player.getClass().getClassLoader().loadClass("net.minecraft.entity.Entity");
        return entity.getField("field_70170_p").get(player)==world&&(int)entity.getMethod("func_145782_y").invoke(player)==id?player:null;
    }
    /** Only the authenticated player is visible to this packet's legacy name lookup. */
    public static Object serverPlayer(Object world,String username) throws Exception {
        Object player=SERVER_PLAYER.get();if(player==null||username==null)return null;
        var entity=player.getClass().getClassLoader().loadClass("net.minecraft.entity.Entity");
        if(entity.getField("field_70170_p").get(player)!=world)return null;
        return username.equals(player.getClass().getMethod("func_70005_c_").invoke(player))?player:null;
    }
    public static void receiveServer(LegacyServices services,Message message,Object player,LegacyDeviceContainers.Descriptor device) throws Exception {
        var loader=services.loader();
        Channel channel=CHANNELS.entrySet().stream().filter(e->e.getKey().getClass().getClassLoader()==loader&&e.getValue().name.equals(message.channel())).map(Map.Entry::getValue).findFirst().orElseThrow();
        Handler handler=channel.handlers.stream().filter(h->h.side.equals("SERVER")&&h.discriminator==message.discriminator()).findFirst().orElseThrow();
        Class<?> bufferType=loader.loadClass("io.netty.buffer.ByteBuf"),messageType=loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage");
        Object buffer=loader.loadClass("io.netty.buffer.Unpooled").getMethod("wrappedBuffer",byte[].class).invoke(null,(Object)message.payload());
        Object packet;
        try {packet=handler.type.getConstructor().newInstance();messageType.getMethod("fromBytes",bufferType).invoke(packet,buffer);
            if((int)bufferType.getMethod("readableBytes").invoke(buffer)!=0)throw new IllegalArgumentException("Trailing device packet data");
        }finally{bufferType.getMethod("release").invoke(buffer);}
        boolean allowed=false;for(var policy:SERVER_POLICIES.getOrDefault(loader,List.of()))if(policy.prepare(packet,player,device)){allowed=true;break;}
        if(!allowed)throw new IllegalArgumentException("Unsupported original device packet: "+handler.type.getName());
        Object world=loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p").get(player);
        Object provider=loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
        int dimension=provider.getClass().getField("field_76574_g").getInt(provider);
        var worldsField=loader.loadClass("net.minecraftforge.common.DimensionManager").getDeclaredField("worlds");worldsField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Integer,Object> worlds=(Map<Integer,Object>)worldsField.get(null);
        Object previousWorld=worlds.put(dimension,world),previousPlayer=SERVER_PLAYER.get();SERVER_PLAYER.set(player);
        try {
            Class<?> netHandler=loader.loadClass("net.minecraft.network.NetHandlerPlayServer");
            var unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);Object unsafe=unsafeField.get(null);
            Object connection=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,netHandler);
            netHandler.getField("field_147369_b").set(connection,player);
            Class<?> context=loader.loadClass("cpw.mods.fml.common.network.simpleimpl.MessageContext"),side=loader.loadClass("cpw.mods.fml.relauncher.Side");
            var constructor=context.getDeclaredConstructor(loader.loadClass("net.minecraft.network.INetHandler"),side);constructor.setAccessible(true);
            Object reply=loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessageHandler").getMethod("onMessage",messageType,context).invoke(handler.instance,packet,constructor.newInstance(connection,side.getField("SERVER").get(null)));
            if(reply!=null)throw new UnsupportedOperationException("Original device packet returned an unsupported reply");
        }finally{
            if(previousWorld==null)worlds.remove(dimension);else worlds.put(dimension,previousWorld);
            if(previousPlayer==null)SERVER_PLAYER.remove();else SERVER_PLAYER.set(previousPlayer);
        }
    }
    public static boolean sendTo(Object wrapper, Object message, Object player) throws Exception {
        Scope scope=CURRENT.get();if(scope==null)return false;
        var loader=message.getClass().getClassLoader();
        var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object profile=playerType.getMethod("func_146103_bH").invoke(player);
        Object recipient=profile.getClass().getMethod("getId").invoke(profile);
        if(!scope.recipients.contains(recipient))
            throw new UnsupportedOperationException("Old item callback sent a packet to another player");
        Channel channel=Objects.requireNonNull(CHANNELS.get(wrapper),"Unregistered old network channel");
        Handler handler=channel.handlers.stream().filter(h->h.side.equals("CLIENT")&&h.type==message.getClass()).findFirst().orElseThrow();
        Class<?> bufferType=loader.loadClass("io.netty.buffer.ByteBuf");
        Object buffer=loader.loadClass("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        try {
            loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage").getMethod("toBytes",bufferType).invoke(message,buffer);
            int size=(int)bufferType.getMethod("readableBytes").invoke(buffer);
            if(size>1024*1024)throw new IllegalArgumentException("Old client packet exceeds host limit");
            byte[] payload=new byte[size];bufferType.getMethod("readBytes",byte[].class).invoke(buffer,(Object)payload);
            var encoded=new Message(channel.name,handler.discriminator,payload);
            scope.messages.add(encoded);scope.addressed.computeIfAbsent((UUID)recipient,key->new ArrayList<>()).add(encoded);return true;
        } finally {bufferType.getMethod("release").invoke(buffer);}
    }
    public static void receiveClient(LegacyServices services, Message message) throws Exception {
        services.items().inLegacy(()->{
            if(LegacyEmbeddedPackets.receive(services,message))return null;
            var loader=services.loader();
            var receiver=clientReceiver(loader,message.channel());
            if(receiver!=null){receiver.receive(message.payload());return null;}
            Channel channel=CHANNELS.entrySet().stream().filter(e->e.getKey().getClass().getClassLoader()==loader&&e.getValue().name.equals(message.channel())).map(Map.Entry::getValue).findFirst().orElseThrow();
            Handler handler=channel.handlers.stream().filter(h->h.side.equals("CLIENT")&&h.discriminator==message.discriminator()).findFirst().orElseThrow();
            Class<?> bufferType=loader.loadClass("io.netty.buffer.ByteBuf");
            Object buffer=loader.loadClass("io.netty.buffer.Unpooled").getMethod("wrappedBuffer",byte[].class).invoke(null,(Object)message.payload());
            try {
                Object packet=handler.type.getConstructor().newInstance();
                Class<?> messageType=loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage");
                messageType.getMethod("fromBytes",bufferType).invoke(packet,buffer);
                Class<?> contextType=loader.loadClass("cpw.mods.fml.common.network.simpleimpl.MessageContext"),sideType=loader.loadClass("cpw.mods.fml.relauncher.Side");
                var constructor=contextType.getDeclaredConstructor(loader.loadClass("net.minecraft.network.INetHandler"),sideType);constructor.setAccessible(true);
                Object context=constructor.newInstance(null,sideType.getField("CLIENT").get(null));
                Object reply=loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessageHandler").getMethod("onMessage",messageType,contextType).invoke(handler.instance,packet,context);
                if(reply!=null)throw new UnsupportedOperationException("Old client handler needs a server reply bridge");
                return null;
            } finally {bufferType.getMethod("release").invoke(buffer);}
        });
    }
}
