package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData.ClientSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/** Read launcher identity before old mods construct or handle preInit. */
public final class ClientSessionBridge {
    private ClientSessionBridge() {}
    public static ClientSession capture() {
        User user = Minecraft.getInstance().getUser();
        // 1.7.10 authlib predates Microsoft accounts; authenticated accounts use its mojang type.
        return new ClientSession(user.getName(), user.getUuid(), user.getAccessToken(),
                user.getType() == User.Type.LEGACY ? "legacy" : "mojang");
    }
}
