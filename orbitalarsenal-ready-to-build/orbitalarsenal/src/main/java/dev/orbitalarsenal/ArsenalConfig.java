package dev.orbitalarsenal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/orbitalarsenal.json (replaces DogOrbital's config.yml). */
final class ArsenalConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbitalarsenal.json");

    /** How many dogs one rod releases. */
    int dogsPerRod = 20;
    /** Minimum raw damage anything takes from a TNT minecart explosion. */
    double tntMinecartDamage = 200.0;
    /** Time of the last /cleardogs (dogs born before this vanish when their chunk loads). */
    long clearedAt = 0L;

    static ArsenalConfig load() {
        ArsenalConfig c = null;
        try {
            if (Files.exists(FILE)) c = GSON.fromJson(Files.readString(FILE), ArsenalConfig.class);
        } catch (Exception e) {
            OrbitalArsenal.LOGGER.warn("Could not read {}, using defaults", FILE, e);
        }
        if (c == null) c = new ArsenalConfig();
        c.dogsPerRod = Math.max(1, c.dogsPerRod);
        c.tntMinecartDamage = Math.max(0, c.tntMinecartDamage);
        c.save();
        return c;
    }

    void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            OrbitalArsenal.LOGGER.warn("Could not write {}", FILE, e);
        }
    }
}
