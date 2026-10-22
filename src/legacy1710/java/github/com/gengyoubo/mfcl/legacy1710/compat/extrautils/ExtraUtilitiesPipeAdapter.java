package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.HeightGuardAdapter;

/** Preserve XU pipe lookup while replacing its hard-coded 0..255 guard with host bounds/loading. */
public final class ExtraUtilitiesPipeAdapter extends HeightGuardAdapter {
    public ExtraUtilitiesPipeAdapter() { super("com.rwtema.extrautils.tileentity.transfernodes.TNHelper","getPipe","(Lnet/minecraft/world/IBlockAccess;III)",0,1,2,3); }
}
