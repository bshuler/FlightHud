package net.torocraft.flighthud;

//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 "loaded game" tests: these run against a real, bootstrapped
 * Minecraft and a real Fabric loader rather than mocks, courtesy of
 * fabric-loader-junit (see the dependency comment in build.gradle.kts).
 *
 * <p>{@code FlightComputerMathTest} already covers the arithmetic headless.
 * What only a loaded game can check is whether this cell's packaging and its
 * assumptions about <em>game data</em> still hold: that {@code fabric.mod.json}
 * parses and its version ranges are satisfiable here, that the heading
 * convention still matches vanilla's own compass, and that elytra durability -
 * which changed representation at 1.20.5, from an item field to a data
 * component - still drives the health readout correctly.
 *
 * <p>Fabric cells only: NeoForge's equivalent bootstrap (junit-fml) is only
 * usable from ModDevGradle, not from Architectury Loom - see the junit-fml
 * exclusion comment in build.gradle.kts.
 */
public class LoadedGameTest {

    private static final String MOD_ID = "flighthud";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x only, and a real behavioural change rather than test plumbing:
        // an item's data components used to be baked into the Item instance at
        // construction, so Bootstrap alone was enough to read one. In 26.x they
        // are produced by BuiltInRegistries.DATA_COMPONENT_INITIALIZERS from a
        // HolderLookup.Provider - i.e. from loaded registry data - and bound
        // onto each Holder.Reference afterwards. Until that runs, constructing
        // any ItemStack throws "Components not bound yet" from
        // Holder.Reference.components(). The server does this during
        // ReloadableServerResources' load; VanillaRegistries.createLookup() is
        // the equivalent built-in-only provider available without a server.
        // (Guarded at 26.1 because that is where this matrix's 26.x line
        // starts - there is no cell between 1.21.4 and 26.2 to narrow it
        // further, and 1.21.4 demonstrably does not need it.)
        //? if >=26.1 {
        /*net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        *///?}
    }

    @Test
    void gameDataIsActuallyLoaded() {
        // Guard on the harness itself: if the bootstrap above ever silently
        // no-ops, every other assertion in this class becomes vacuous.
        assertNotNull(Items.ELYTRA, "Items.ELYTRA should be a real loaded game object");
        // The item registry moved from Registry.ITEM to BuiltInRegistries.ITEM
        // in 1.19.3; both expose getKey/keySet identically.
        //? if >=1.19.3 {
        var itemRegistry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        //?} else
        /*var itemRegistry = net.minecraft.core.Registry.ITEM;*/
        assertEquals("minecraft:elytra", itemRegistry.getKey(Items.ELYTRA).toString());
        assertTrue(itemRegistry.keySet().size() > 500,
                "the real item registry should hold the full vanilla item set");
    }

    @Test
    void modIsDiscoveredByARealFabricLoader() {
        // The processed fabric.mod.json (Stonecraft templating already
        // applied) is on the test classpath, so a real loader discovers this
        // mod exactly as the game would. Malformed or mis-templated metadata
        // fails here instead of at first launch.
        var self = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(
                () -> new AssertionError("a real Fabric loader did not discover mod id '" + MOD_ID + "'"));
        assertEquals(MOD_ID, self.getMetadata().getId());
        assertFalse(self.getMetadata().getVersion().getFriendlyString().isBlank(),
                "mod version must survive resource templating");
    }

    @Test
    void declaredDependencyRangesAreSatisfiableInThisCell() {
        // The real drift hazard in a Stonecutter matrix: a cell can compile and
        // package flawlessly while declaring a minecraft range that excludes
        // the very version it was built for, producing a jar that ships and
        // then refuses to load. Here the loader resolves the ranges against the
        // actually-loaded versions, per cell.
        var loader = FabricLoader.getInstance();
        var self = loader.getModContainer(MOD_ID).orElseThrow();
        for (ModDependency dependency : self.getMetadata().getDependencies()) {
            if (dependency.getKind() != ModDependency.Kind.DEPENDS) {
                continue;
            }
            var provider = loader.getModContainer(dependency.getModId());
            assertTrue(provider.isPresent(),
                    "fabric.mod.json requires '" + dependency.getModId() + "' but nothing provides it");
            assertTrue(dependency.matches(provider.get().getMetadata().getVersion()),
                    "fabric.mod.json requires " + dependency + " but this cell loads "
                            + dependency.getModId() + " "
                            + provider.get().getMetadata().getVersion().getFriendlyString());
        }
    }

    @Test
    void headingMatchesTheGamesOwnCompass() {
        // The HUD's heading readout must agree with the facing the game itself
        // derives from the same yaw. Direction.fromYRot is vanilla's mapping;
        // toHeading is the mod's. They have to describe the same direction.
        assertEquals(Direction.SOUTH, Direction.fromYRot(0));
        assertEquals(180f, FlightComputerMath.toHeading(0f), 0.001f);

        assertEquals(Direction.WEST, Direction.fromYRot(90));
        assertEquals(270f, FlightComputerMath.toHeading(90f), 0.001f);

        assertEquals(Direction.NORTH, Direction.fromYRot(180));
        assertEquals(0f, FlightComputerMath.wrapHeading(FlightComputerMath.toHeading(180f)), 0.001f);

        assertEquals(Direction.EAST, Direction.fromYRot(-90));
        assertEquals(90f, FlightComputerMath.toHeading(-90f), 0.001f);
    }

    @Test
    void headingWrapAgreesWithVanillaDegreeWrapping() {
        // wrapHeading normalizes into [0, 360); Mth.wrapDegrees - the game's
        // own helper - normalizes into [-180, 180). Different windows, same
        // angle: the two must never disagree about which direction is meant.
        for (float degrees = -1080f; degrees <= 1080f; degrees += 7.5f) {
            assertEquals(Mth.wrapDegrees(degrees),
                    Mth.wrapDegrees(FlightComputerMath.wrapHeading(degrees)), 0.01f,
                    "wrapHeading disagreed with Mth.wrapDegrees at " + degrees);
        }
    }

    @Test
    void elytraHealthReadoutTracksRealElytraDurability() {
        // Elytra durability is game data whose representation changed at
        // 1.20.5 (item field -> data component). Reading it off a real
        // ItemStack keeps the readout pinned to whatever the game currently
        // says, rather than to a number copied into a test.
        var elytra = new ItemStack(Items.ELYTRA);
        var maxDamage = elytra.getMaxDamage();
        assertTrue(maxDamage > 0, "a real elytra should be damageable");

        assertEquals(100f, FlightComputerMath.elytraHealthPercent(maxDamage, elytra.getDamageValue()), 0.001f);

        elytra.setDamageValue(maxDamage);
        assertEquals(0f, FlightComputerMath.elytraHealthPercent(maxDamage, elytra.getDamageValue()), 0.001f);

        elytra.setDamageValue(maxDamage / 2);
        assertEquals(50f, FlightComputerMath.elytraHealthPercent(maxDamage, elytra.getDamageValue()), 1.0f);
    }
}
//?}
