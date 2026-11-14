package github.com.gengyoubo.mfcl.directsmoke;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/** A direct class extending an original class: its MCP-named overrides must be reobfuscated to SRG. */
public final class DirectCommand extends CommandBase {
    private final String name;
    public DirectCommand(String name) { this.name = name; }
    @Override public String getCommandName() { return name; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/" + name; }
    @Override public void processCommand(ICommandSender sender, String[] arguments) { }
}
