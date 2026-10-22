package github.com.gengyoubo.mfcl.legacy1710.compat.ic2;

import github.com.gengyoubo.mfcl.legacy1710.boot.GuiProviderAdapter;

/** Retains IC2's actual container and screen while the host supplies transport and player authority. */
public final class Ic2GuiAdapter extends GuiProviderAdapter {
    public Ic2GuiAdapter() { super("ic2.core.Platform","launchGui","(Lnet/minecraft/entity/player/EntityPlayer;Lic2/core/IHasGui;)Z","ic2",1,2); }
}
