package net.torocraft.flighthud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.torocraft.flighthud.config.HudConfig;
import net.torocraft.flighthud.config.SettingsConfig;
import net.torocraft.flighthud.config.loader.ConfigLoader;
import org.lwjgl.glfw.GLFW;

//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
*///?} elif neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
*///?} elif forge && <1.19 {
/*import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
*///?} elif forge {
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//?}

//? if fabric && >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} elif fabric {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}

//? if fabric && >=26.1 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*///?} elif fabric {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
*///?}

// KeyMapping's 4th constructor argument changed from a plain String to a proper
// KeyMapping.Category record type starting at MC 1.21.9 (confirmed via javap across
// the sibling critical-orientation mod's full version matrix). Building a custom
// Category requires KeyMapping.Category.register(<namespaced id>), and the id type
// itself was renamed ResourceLocation -> Identifier starting at MC 1.21.11. FlightHud's
// matrix has nothing between 1.21.4 and 26.2, so only the >=1.21.11 (Identifier) and
// else (String) branches are actually reachable, but the >=1.21.9 (ResourceLocation)
// branch is kept for parity with critical-orientation and future-proofing.
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} elif >=1.21.9 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Client entry point: loads config, registers the toggle keybind, registers
 * the (Fabric-only) "/flighthud toggle" command, and hands off HUD-render-hook
 * registration to {@link HudHooks} (which replaces the old {@code
 * mixin/InGameHudMixin.java} -- see HudHooks' class doc for why).
 */
//? if fabric {
/*public class FlightHud implements ClientModInitializer {
*///?} elif neoforge {
/*@Mod(FlightHud.MODID)
public class FlightHud {
*///?} elif forge {
@Mod(FlightHud.MODID)
public class FlightHud {
//?}
  public static final String MODID = "flighthud";

  public static SettingsConfig CONFIG_SETTINGS = new SettingsConfig();
  public static HudConfig CONFIG_MIN = new HudConfig();
  public static HudConfig CONFIG_FULL = new HudConfig();

  public static ConfigLoader<SettingsConfig> CONFIG_LOADER_SETTINGS = new ConfigLoader<>(
      new SettingsConfig(),
      FlightHud.MODID + ".settings.json",
      config -> FlightHud.CONFIG_SETTINGS = config);

  public static ConfigLoader<HudConfig> CONFIG_LOADER_FULL = new ConfigLoader<>(
      new HudConfig(),
      FlightHud.MODID + ".full.json",
      config -> FlightHud.CONFIG_FULL = config);

  public static ConfigLoader<HudConfig> CONFIG_LOADER_MIN = new ConfigLoader<>(
      HudConfig.getDefaultMinSettings(),
      FlightHud.MODID + ".min.json",
      config -> FlightHud.CONFIG_MIN = config);

  //? if >=1.21.11 {
  /*private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MODID, "main"));
  *///?} elif >=1.21.9 {
  /*private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MODID, "main"));
  *///?} else {
  private static final String CATEGORY = "category.flighthud.toggleDisplayMode";
  //?}

  private static KeyMapping keyBinding;

  //? if fabric {
  /*@Override
  public void onInitializeClient() {
    loadConfig();
    setupKeyCode();
    setupCommand();
    HudHooks.register();
  }
  *///?} elif neoforge {
  /*public FlightHud(IEventBus modEventBus) {
    loadConfig();
    modEventBus.addListener(this::onRegisterKeyMappings);
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTick());
    HudHooks.register();
  }

  private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
    setupKeyCode();
    event.register(keyBinding);
  }
  *///?} elif forge && <1.19 {
  /*public FlightHud() {
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
  }

  private void onClientSetup(FMLClientSetupEvent event) {
    loadConfig();
    setupKeyCode();
    ClientRegistry.registerKeyBinding(keyBinding);
    MinecraftForge.EVENT_BUS.addListener(this::onClientTickForge);
    HudHooks.register();
  }

  private void onClientTickForge(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
      onClientTick();
    }
  }
  *///?} elif forge {
  public FlightHud() {
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterKeyMappings);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(HudHooks::registerOverlays);
    MinecraftForge.EVENT_BUS.addListener(this::onClientTickForge);
  }

  private void onClientSetup(FMLClientSetupEvent event) {
    loadConfig();
  }

  private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
    setupKeyCode();
    event.register(keyBinding);
  }

  private void onClientTickForge(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
      onClientTick();
    }
  }
  //?}

  private static void loadConfig() {
    CONFIG_LOADER_SETTINGS.load();
    CONFIG_LOADER_FULL.load();
    CONFIG_LOADER_MIN.load();
  }

  private static void setupKeyCode() {
    keyBinding = new KeyMapping("key.flighthud.toggleDisplayMode", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);

    //? if fabric && >=26.1 {
    /*KeyMappingHelper.registerKeyMapping(keyBinding);
    *///?} elif fabric {
    /*KeyBindingHelper.registerKeyBinding(keyBinding);
    *///?}

    //? if fabric {
    /*ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
    *///?}
  }

  private static void onClientTick() {
    while (keyBinding.consumeClick()) {
      CONFIG_SETTINGS.toggleDisplayMode();
    }
  }

  //? if fabric {
  /*private static void setupCommand() {
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
      //? if >=26.1 {
      /^dispatcher.register(ClientCommands.literal("flighthud")
          .then(ClientCommands.literal("toggle").executes(new SwitchDisplayModeCommand())));
      ^///?} else {
      dispatcher.register(ClientCommandManager.literal("flighthud")
          .then(ClientCommandManager.literal("toggle").executes(new SwitchDisplayModeCommand())));
      //?}
    });
  }
  *///?}
}
