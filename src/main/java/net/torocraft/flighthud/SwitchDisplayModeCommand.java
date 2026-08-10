package net.torocraft.flighthud;

// Fabric-only: Forge/NeoForge have their own, differently-typed client command
// source, and the "/flighthud toggle" chat command is a deliberate Fabric-only
// scope reduction (the keybind covers every loader) -- see FlightHud's CLAUDE.md
// "Multi-loader scope decisions". On non-fabric nodes this whole file compiles
// down to just the package statement (a valid, empty compilation unit).
//? if fabric && >=1.19 {
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

public class SwitchDisplayModeCommand implements Command<FabricClientCommandSource> {

  @Override
  public int run(CommandContext<FabricClientCommandSource> context) {
    FlightHud.CONFIG_SETTINGS.toggleDisplayMode(Minecraft.getInstance().player.isFallFlying());
    FlightHud.CONFIG_LOADER_SETTINGS.save(FlightHud.CONFIG_SETTINGS);
    return 0;
  }

}
//?}
