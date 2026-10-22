package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.PlacementSoundAdapter;

/** TC4's custom door item bypasses ItemBlock's placement audio. Emit it once after a successful placement. */
public final class ThaumcraftDoorAdapter extends PlacementSoundAdapter {
    // onItemUse(stack, player, world=3, x=4, y=5, z=6, ...); local 11 holds the door block it places.
    public ThaumcraftDoorAdapter() { super("thaumcraft.common.blocks.ItemArcaneDoor","func_77648_a","placeDoorBlock",3,11,4,5,6); }
}
