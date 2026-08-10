package net.torocraft.flighthud.config.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.function.Consumer;

public class ConfigLoader<T extends IConfig> {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  //private final Class<T> configClass;
  private final Consumer<T> onLoad;
  private final File file;
  private final T defaultConfig;
  private FileWatcher watcher;

  /**
   * Takes a {@link File} directly rather than resolving one from a bare
   * filename itself. Resolving the config directory ({@link ConfigFolder#get()})
   * touches the live Fabric/Forge/NeoForge loader singleton, which only
   * exists inside a running game -- keeping that resolution at the call site
   * (see {@code FlightHud}'s static fields) means this otherwise-pure
   * read/save/watch class has zero loader dependency of its own and can be
   * unit tested headless against a temp-directory {@link File}.
   */
  public ConfigLoader(T defaultConfig, File file, Consumer<T> onLoad) {
    this.defaultConfig = defaultConfig;
    this.onLoad = onLoad;
    this.file = file;
  }

  public void load() {
    T config = defaultConfig;

    if (!file.exists()) {
      save(config);
    }

    config = read();
    config.update();
    onLoad.accept(config);

    if (config.shouldWatch()) {
      watch(file);
    }
  }

  @SuppressWarnings("unchecked")
  public T read() {
    try (FileReader reader = new FileReader(file)) {
      return (T) GSON.fromJson(reader, defaultConfig.getClass());
    } catch (Exception e) {
      e.printStackTrace();
      return defaultConfig;
    }
  }

  public void save(T config) {
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(GSON.toJson(config));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void watch(File file) {
    if (watcher != null) {
      return;
    }
    watcher = FileWatcher.watch(file, this::reload);
  }

  /**
   * The callback wired up to the {@link FileWatcher} started by
   * {@link #watch(File)}. Pulled out of that call site as a named,
   * package-private method (rather than an inline lambda) specifically so a
   * test can invoke the exact same reload path {@code watch()} wires up,
   * without waiting on a real filesystem-change event to fire it.
   */
  void reload() {
    load();
  }

  /**
   * Package-private test seam: lets tests observe that {@link #watch(File)}
   * is idempotent (a second call doesn't replace or duplicate the watcher)
   * without depending on real filesystem-event delivery timing.
   */
  boolean isWatching() {
    return watcher != null;
  }

}
