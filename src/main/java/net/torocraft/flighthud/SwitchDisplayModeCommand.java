package net.torocraft.flighthud;

// Fabric-only: Forge/NeoForge have their own, differently-typed client command
// source, and the "/flighthud toggle" chat command is a deliberate Fabric-only
// scope reduction (the keybind covers every loader) -- see FlightHud's CLAUDE.md
// "Multi-loader scope decisions". On non-fabric nodes this whole file compiles
// down to just the package statement (a valid, empty compilation unit).
//? if fabric {
/*import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class SwitchDisplayModeCommand implements Command<FabricClientCommandSource> {

  @Override
  public int run(CommandContext<FabricClientCommandSource> context) {
    FlightHud.CONFIG_SETTINGS.toggleDisplayMode();
    return 0;
  }

}
*///?}
