package github.com.gengyoubo.mfcl.content;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyBlockLogic;
import github.com.gengyoubo.mfcl.bridge.LegacyPackets;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Original server commands. The old mods' own FMLServerStartingEvent handlers run once against a stand-in old server
 * whose command manager collects their ICommands; each one is exposed as a host Brigadier literal taking the raw
 * argument string, and executes against old actors of every online player inside one player-data transaction.
 */
public final class LegacyCommands {
    private record Actor(ServerPlayer host, Object legacy, byte[] before) {}
    private final ModernContentBridge content;
    private Object server, players;
    private Map<String,Object> commands;

    private LegacyCommands(ModernContentBridge content) { this.content = content; }
    static void install(ModernContentBridge content) {
        var commands = new LegacyCommands(content);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(commands::register);
    }
    private ClassLoader loader() { return content.services().loader(); }
    private Class<?> type(String name) throws ClassNotFoundException { return loader().loadClass(name); }

    private void register(RegisterCommandsEvent event) {
        Map<String,Object> found;
        try { found = commands(); }
        catch (Exception | LinkageError failure) { LogUtils.getLogger().error("MFCL could not start original server commands", failure); return; }
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        for (var entry : found.entrySet()) {
            String name = entry.getKey(); Object command = entry.getValue();
            if (dispatcher.getRoot().getChild(name) != null) { LogUtils.getLogger().warn("MFCL skipped original command /{}: the host already owns it", name); continue; }
            dispatcher.register(Commands.literal(name).requires(source -> permitted(source, command))
                    .executes(context -> execute(context, command, name, ""))
                    .then(Commands.argument("arguments", StringArgumentType.greedyString())
                            .suggests((context, builder) -> suggest(context, builder, command))
                            .executes(context -> execute(context, command, name, StringArgumentType.getString(context, "arguments")))));
        }
        LogUtils.getLogger().info("MFCL original server commands: {}", found.keySet());
    }

    /** Fires the old FMLServerStartingEvent once per runtime and returns every name and alias it registered. */
    @SuppressWarnings("unchecked")
    private synchronized Map<String,Object> commands() throws Exception {
        if (commands != null) return commands;
        commands = content.services().items().inLegacy(() -> {
            Class<?> serverType = type("net.minecraft.server.MinecraftServer");
            var unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
            // Never constructed or started: it only answers getServer(), getCommandManager() and the online-player list.
            server = allocate.invoke(unsafe, type("net.minecraft.server.integrated.IntegratedServer"));
            Object manager = type("net.minecraft.command.CommandHandler").getConstructor().newInstance();
            set(serverType, server, "field_71321_q", manager);
            players = allocate.invoke(unsafe, type("net.minecraft.server.integrated.IntegratedPlayerList"));
            set(type("net.minecraft.server.management.ServerConfigurationManager"), players, "field_72404_b", new ArrayList<>());
            set(serverType, server, "field_71318_t", players);
            set(serverType, null, "field_71309_l", server);

            Object loader = type("cpw.mods.fml.common.Loader").getMethod("instance").invoke(null);
            Field controllerField = loader.getClass().getDeclaredField("modController"); controllerField.setAccessible(true);
            Object controller = controllerField.get(loader);
            Field errorsField = controller.getClass().getDeclaredField("errors"); errorsField.setAccessible(true);
            Object event = type("cpw.mods.fml.common.event.FMLServerStartingEvent").getConstructor(Object[].class).newInstance((Object)new Object[]{server});
            Class<?> fmlEvent = type("cpw.mods.fml.common.event.FMLEvent"), modContainer = type("cpw.mods.fml.common.FMLModContainer");
            Method apply = fmlEvent.getMethod("applyModContainer", type("cpw.mods.fml.common.ModContainer"));
            for (Object mod : (List<?>) loader.getClass().getMethod("getActiveModList").invoke(loader)) {
                if (!modContainer.isInstance(mod)) continue;
                String id = (String) mod.getClass().getMethod("getModId").invoke(mod);
                Object errors = errorsField.get(controller);
                int before = ((Collection<?>) errors.getClass().getMethod("get", Object.class).invoke(errors, id)).size();
                apply.invoke(event, mod);
                modContainer.getMethod("handleModStateEvent", fmlEvent).invoke(mod, event);
                var failures = new ArrayList<Throwable>((Collection<Throwable>) errors.getClass().getMethod("get", Object.class).invoke(errors, id));
                // The stand-in server has no worlds or network; mods whose handler needs one keep loading without it.
                for (Throwable failure : failures.subList(before, failures.size()))
                    LogUtils.getLogger().warn("MFCL original server-start handler of {} failed; its other server features may be missing", id, failure);
            }
            return new TreeMap<String,Object>((Map<String,Object>) manager.getClass().getMethod("func_71555_a").invoke(manager));
        });
        return commands;
    }

    private boolean permitted(CommandSourceStack source, Object command) {
        try { return content.services().items().inLegacy(() -> (boolean) type("net.minecraft.command.ICommand").getMethod("func_71519_b", type("net.minecraft.command.ICommandSender")).invoke(command, console(source, null))); }
        catch (Exception | LinkageError failure) { return source.hasPermission(4); }
    }

    private int execute(CommandContext<CommandSourceStack> context, Object command, String name, String input) {
        var source = context.getSource();
        String[] arguments = input.isBlank() ? new String[0] : input.trim().split(" +");
        for (int i = 0; i < arguments.length; i++) arguments[i] = selector(source, arguments[i]);
        try {
            return transaction(source, true, sender -> {
                type("net.minecraft.command.ICommand").getMethod("func_71515_b", type("net.minecraft.command.ICommandSender"), String[].class).invoke(command, sender, arguments);
                return 1;
            });
        } catch (Exception | LinkageError failure) {
            Throwable cause = failure;
            while ((cause instanceof InvocationTargetException || cause instanceof IllegalStateException) && cause.getCause() != null) cause = cause.getCause();
            String message = commandError(cause);
            if (message == null) {
                LogUtils.getLogger().error("MFCL original command /{} {} failed", name, input, failure);
                message = "原版命令执行失败：" + cause;
            }
            source.sendFailure(Component.literal(message));
            return 0;
        }
    }

    /** Old PlayerSelector only sees the old world's (empty) entity list, so host selectors naming one player become that name. */
    private static String selector(CommandSourceStack source, String argument) {
        if (!argument.startsWith("@")) return argument;
        try {
            var reader = new com.mojang.brigadier.StringReader(argument);
            var players = new net.minecraft.commands.arguments.selector.EntitySelectorParser(reader).parse().findPlayers(source);
            return !reader.canRead() && players.size() == 1 ? players.get(0).getGameProfile().getName() : argument;
        } catch (Exception unresolved) { return argument; }
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, Object command) {
        String input = builder.getRemaining();
        String[] arguments = (input + "\u0000").split(" +", -1);
        arguments[arguments.length - 1] = arguments[arguments.length - 1].replace("\u0000", "");
        int last = input.lastIndexOf(' ') + 1;
        try {
            List<?> options = transaction(context.getSource(), false, sender ->
                    (List<?>) type("net.minecraft.command.ICommand").getMethod("func_71516_a", type("net.minecraft.command.ICommandSender"), String[].class).invoke(command, sender, arguments));
            if (options != null) {
                var offset = builder.createOffset(builder.getStart() + last);
                for (Object option : options) offset.suggest(String.valueOf(option));
                return offset.buildFuture();
            }
        } catch (Exception | LinkageError failure) { LogUtils.getLogger().debug("MFCL original command completion failed", failure); }
        return builder.buildFuture();
    }

    private interface Work<T> { T run(Object sender) throws Exception; }
    /**
     * Every online player gets an old EntityPlayerMP actor (so player arguments resolve), loaded from and, when
     * {@code persist}, saved back to its host data. Chat and old packets are delivered to whichever player they addressed.
     */
    private <T> T transaction(CommandSourceStack source, boolean persist, Work<T> work) throws Exception {
        var minecraft = source.getServer();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Original commands run on the server thread");
        ServerLevel level = source.getLevel();
        ServerPlayer caller = source.getEntity() instanceof ServerPlayer player ? player : null;
        var online = List.copyOf(minecraft.getPlayerList().getPlayers());
        var services = content.services();
        Map<UUID,List<LegacyPackets.Message>> packets = new HashMap<>();
        var actors = new ArrayList<Actor>();
        Object world = services.worlds().bound(level);
        T result = services.items().inLegacy(() -> {
            try (var state = services.playerState().enter(world)) {
                for (var player : online) {
                    Object actor = content.itemUse().clientActor(world, ModernContentBridge.placer(player), null);
                    byte[] saved = player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
                    if (saved.length != 0) services.playerData().write(actor, saved);
                    services.playerState().initialize(actor);
                    actors.add(new Actor(player, actor, services.playerData().read(actor)));
                }
                @SuppressWarnings("unchecked") List<Object> list = (List<Object>) type("net.minecraft.server.management.ServerConfigurationManager").getField("field_72404_b").get(players);
                list.clear(); actors.forEach(actor -> list.add(actor.legacy()));
                Object sender = caller == null ? console(source, world) : actors.stream().filter(actor -> actor.host() == caller).findFirst().orElseThrow().legacy();
                T value;
                try (var chat = new LegacyBlockLogic.AddressedChatCapture();
                     var capture = LegacyPackets.captureFor(online.stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet()))) {
                    try {
                        value = content.logicTransaction(level, caller == null ? net.minecraft.core.BlockPos.containing(source.getPosition()) : caller.blockPosition(), () -> work.run(sender), true);
                    } catch (Exception | Error failure) {
                        for (var actor : actors) services.playerData().write(actor.legacy(), actor.before());
                        throw failure;
                    } finally { list.clear(); }
                    for (var actor : actors) packets.put(actor.host().getUUID(), new ArrayList<>(capture.messages(actor.host().getUUID())));
                    deliver(chat.lines(), source, actors, sender);
                }
                if (persist) for (var actor : actors) {
                    byte[] after = services.playerData().read(actor.legacy());
                    if (Arrays.equals(after, actor.before())) continue;
                    actor.host().getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA, after);
                    packets.computeIfAbsent(actor.host().getUUID(), key -> new ArrayList<>()).addAll(content.itemUse().sync(world, ModernContentBridge.placer(actor.host())));
                }
                return value;
            }
        });
        if (persist) for (var actor : actors) packets.getOrDefault(actor.host().getUUID(), List.of()).forEach(packet -> ScanSyncChannel.sendToPlayer(actor.host(), packet));
        return result;
    }

    private static void deliver(List<LegacyBlockLogic.AddressedChat> lines, CommandSourceStack source, List<Actor> actors, Object sender) {
        for (var line : lines) {
            var component = Component.literal(line.text());
            if (line.recipient() == sender) { source.sendSystemMessage(component); continue; }
            actors.stream().filter(actor -> actor.legacy() == line.recipient()).findFirst()
                    .ifPresentOrElse(actor -> actor.host().sendSystemMessage(component), () -> source.sendSystemMessage(component));
        }
    }

    /** Old ICommandSender for the console, command blocks and permission checks; chat is captured like a player's. */
    private Object console(CommandSourceStack source, Object world) throws Exception {
        var senderType = type("net.minecraft.command.ICommandSender");
        String name = source.getTextName();
        var position = net.minecraft.core.BlockPos.containing(source.getPosition());
        return Proxy.newProxyInstance(loader(), new Class<?>[]{senderType}, new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "func_70005_c_": return name;
                    case "func_145748_c_": return type("net.minecraft.util.ChatComponentText").getConstructor(String.class).newInstance(name);
                    case "func_145747_a": if (!LegacyBlockLogic.chat(proxy, args[0])) source.sendSystemMessage(Component.literal((String) type("net.minecraft.util.IChatComponent").getMethod("func_150260_c").invoke(args[0]))); return null;
                    case "func_70003_b": return source.hasPermission((int) args[0]);
                    case "func_82114_b": return type("net.minecraft.util.ChunkCoordinates").getConstructor(int.class, int.class, int.class).newInstance(position.getX(), position.getY(), position.getZ());
                    case "func_130014_f_": return world != null ? world : content.services().worlds().bound(source.getLevel());
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "MFCL command sender " + name;
                    default: throw new UnsupportedOperationException("Old command sender method " + method);
                }
            }
        });
    }

    /** Old CommandException (including usage/player-not-found) as the translated, user-facing message; null otherwise. */
    private String commandError(Throwable failure) {
        try {
            var exceptionType = type("net.minecraft.command.CommandException");
            if (!exceptionType.isInstance(failure)) return null;
            Object[] arguments = (Object[]) exceptionType.getMethod("func_74844_a").invoke(failure);
            return content.services().items().inLegacy(() -> {
                Object component = type("net.minecraft.util.ChatComponentTranslation").getConstructor(String.class, Object[].class).newInstance(failure.getMessage(), arguments);
                return (String) type("net.minecraft.util.IChatComponent").getMethod("func_150260_c").invoke(component);
            });
        } catch (Exception lookup) { return failure.getMessage(); }
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true);
        if (Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) throw new IllegalStateException("Cannot replace static final " + name);
        field.set(target, value);
    }
}
