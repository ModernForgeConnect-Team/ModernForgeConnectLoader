package github.com.gengyoubo.mfcl.bridge;

/** Original ItemBlock and special Item placement callbacks, including their NBT and stack consumption. */
public final class LegacyItemPlacement {
    /** {@code inventory} is the old main inventory afterwards; the selected slot is reported through {@code stack}. */
    public record Result(boolean used, LegacyClientData.Stack stack, java.util.List<LegacyClientData.Stack> inventory) {}
    private final LegacyServices services;
    public LegacyItemPlacement(LegacyServices services) { this.services = services; }
    public Result use(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, java.util.List<LegacyClientData.Stack> contents, int selected,
                      int x, int y, int z, int side, float hitX, float hitY, float hitZ) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item"), stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer"), worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot), item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = services.tiles().actor(world, placer, snapshot);
            services.playerInventory().write(player, contents);
            services.playerInventory().select(player, selected);
            // The selected slot holds the very stack passed to the callbacks, so in-place consumption stays consistent.
            Object main = loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getField("field_70462_a").get(playerType.getField("field_71071_by").get(player));
            ((Object[]) main)[selected] = stack;
            // Items such as ItemEssence call player.inventoryContainer.detectAndSendChanges() from onItemUseFirst.
            if (playerType.getField("field_71069_bz").get(player) == null) {
                Class<?> inventoryType = loader.loadClass("net.minecraft.entity.player.InventoryPlayer");
                Object inventory = playerType.getField("field_71071_by").get(player);
                Object container = loader.loadClass("net.minecraft.inventory.ContainerPlayer").getConstructor(inventoryType,boolean.class,playerType).newInstance(inventory,false,player);
                playerType.getField("field_71069_bz").set(player,container);
                if (playerType.getField("field_71070_bA").get(player) == null) playerType.getField("field_71070_bA").set(player,container);
            }
            Class<?>[] signature = {stackType,playerType,worldType,int.class,int.class,int.class,int.class,float.class,float.class,float.class};
            Object[] arguments = {stack,player,world,x,y,z,side,hitX,hitY,hitZ};
            boolean used = (boolean)itemType.getMethod("onItemUseFirst",signature).invoke(item,arguments);
            if (!used) used = (boolean)itemType.getMethod("func_77648_a",signature).invoke(item,arguments);
            int count = stackType.getField("field_77994_a").getInt(stack);
            return new Result(used, count <= 0 ? null : services.items().snapshot(stack), services.playerInventory().read(player));
        });
    }
}
