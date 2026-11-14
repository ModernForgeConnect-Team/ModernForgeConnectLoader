package github.com.gengyoubo.mfcl.apismoke;

import github.com.gengyoubo.mfcl.api.LegacyObject;
import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.mfcl.api.mla.LegacyField;
import github.com.gengyoubo.mfcl.api.mla.LegacyMethod;
import github.com.gengyoubo.mfcl.api.mla.LegacyType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import java.util.function.BiFunction;

/**
 * Host MLA contracts naming original classes by class literal, compiled against the member-less legacy name stubs.
 * At runtime the host cannot load EntityPlayer/ItemStack (TypeNotPresentException carries the name), while
 * MinecraftServer resolves to the host's own class of the same name; both must still bind the original classes.
 */
public final class ClassLiteralProbe implements BiFunction<LegacyRuntime, LegacyObject, String> {
    @LegacyType(type = EntityPlayer.class) public interface Player {
        @LegacyMethod(name = "getCurrentArmor") Stack armor(int slot);
    }
    @LegacyType(type = ItemStack.class) public interface Stack {
        @LegacyMethod(name = "getDisplayName") String name();
        @LegacyField(name = "stackSize") int count();
    }
    /** A nested original class. */
    @LegacyType(type = EntityPlayer.EnumStatus.class) public interface SleepStatus { }
    /** Same name as a host class. */
    @LegacyType(type = MinecraftServer.class) public interface Server { }

    @Override public String apply(LegacyRuntime api, LegacyObject player) {
        api.accessor(SleepStatus.class);
        api.accessor(Server.class);
        Stack helmet = api.accessor(Player.class, player).armor(3);
        if (helmet.count() != 1) throw new IllegalStateException("Class-literal contract read " + helmet.count() + " helmets");
        return helmet.name();
    }
}
