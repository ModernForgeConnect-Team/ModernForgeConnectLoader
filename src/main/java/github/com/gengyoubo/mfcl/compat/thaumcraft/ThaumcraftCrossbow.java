package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.LegacyServices;

/** TC4 chooses the original arrow type and elemental metadata; the shared host bridge owns crossbow firing. */
final class ThaumcraftCrossbow {
    private ThaumcraftCrossbow() {}
    static void install(LegacyServices services) {
        services.crossbowAmmo().register("Thaumcraft:PrimalArrow",(world,shooter,ammunition)->{
            var loader=services.loader();
            return loader.loadClass("thaumcraft.common.entities.projectile.EntityPrimalArrow")
                    .getConstructor(loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.entity.EntityLivingBase"),float.class,int.class)
                    .newInstance(world,shooter,3.0f,ammunition.metadata());
        });
    }
}
