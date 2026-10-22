package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Keep original limits/bookkeeping; map each live original ticket to its own transient host region tickets. */
public final class LegacyChunkTickets {
    private record Chunk(int x,int z){}
    private record Ticket(Object world,UUID id,Set<Chunk> chunks){}
    private static final Map<Object,Ticket> ACTIVE=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<Object,Boolean> WORLDS=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private LegacyChunkTickets(){}
    @SuppressWarnings("unchecked") public static void ensure(Object world) throws Exception {
        var host=LegacyWorlds.hostOrNull(world);if(host==null||host.clientSide())return;
        var loader=world.getClass().getClassLoader();var manager=loader.loadClass("net.minecraftforge.common.ForgeChunkManager");
        var tickets=manager.getDeclaredField("tickets");tickets.setAccessible(true);
        ((Map<Object,Object>)tickets.get(null)).computeIfAbsent(world,ignored->{try{return loader.loadClass("com.google.common.collect.HashMultimap").getMethod("create").invoke(null);}catch(Exception failure){throw new IllegalStateException(failure);}});
        var forced=manager.getDeclaredField("forcedChunks");forced.setAccessible(true);
        if(!((Map<?,?>)forced.get(null)).containsKey(world))((Map<Object,Object>)forced.get(null)).put(world,loader.loadClass("com.google.common.collect.ImmutableSetMultimap").getMethod("of").invoke(null));
        WORLDS.put(world,true);
    }
    public static void update(Object original,boolean release) throws Exception {
        if(original==null)return;Object world=original.getClass().getField("world").get(original);var host=LegacyWorlds.hostOrNull(world);if(host==null||host.clientSide())return;
        var ticket=ACTIVE.computeIfAbsent(original,key->new Ticket(world,UUID.randomUUID(),new HashSet<>()));var requested=new HashSet<Chunk>();
        if(!release)for(Object pair:(Collection<?>)original.getClass().getMethod("getChunkList").invoke(original))requested.add(new Chunk(pair.getClass().getField("field_77276_a").getInt(pair),pair.getClass().getField("field_77275_b").getInt(pair)));
        for(var previous:ticket.chunks())if(!requested.contains(previous))host.forceChunk(ticket.id(),previous.x(),previous.z(),false);
        for(var chunk:requested)host.forceChunk(ticket.id(),chunk.x(),chunk.z(),true);
        ticket.chunks().clear();ticket.chunks().addAll(requested);if(release)ACTIVE.remove(original);
    }
    @SuppressWarnings("unchecked") static void unload(Object world){
        if(WORLDS.remove(world)==null)return;
        for(var entry:new ArrayList<>(ACTIVE.entrySet()))if(entry.getValue().world()==world)try{update(entry.getKey(),true);}catch(Exception failure){throw new IllegalStateException("Cannot release legacy chunk tickets",failure);}
        try{
            var type=world.getClass().getClassLoader().loadClass("net.minecraftforge.common.ForgeChunkManager");
            for(String name:List.of("tickets","forcedChunks","dormantChunkCache")){var field=type.getDeclaredField(name);field.setAccessible(true);((Map<?,?>)field.get(null)).remove(world);}
        }catch(Exception failure){throw new IllegalStateException("Cannot clear original chunk ticket bookkeeping",failure);}
    }
}
