package net.torocraft.flighthud.config.loader;

import java.io.File;

//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//?} elif neoforge {
/*import net.neoforged.fml.loading.FMLPaths;
*///?} elif forge {
/*import net.minecraftforge.fml.loading.FMLPaths;
*///?}

public class ConfigFolder {

  public static File get() {
    //? if fabric {
    return FabricLoader.getInstance().getConfigDir().toFile();
    //?} elif neoforge {
    /*return FMLPaths.CONFIGDIR.get().toFile();
    *///?} elif forge {
    /*return FMLPaths.CONFIGDIR.get().toFile();
    *///?}
  }

}
