package com.example.pvpvisuals;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** A toggleable visual module. Settings are saved to config/pvpvisuals.properties. */
public final class Module {
    public static final List<Module> ALL = new ArrayList<>();

    public static final Module HUD         = new Module("hud", "Master HUD", true);
    public static final Module COMBO       = new Module("combo", "Combo counter", true);
    public static final Module CPS         = new Module("cps", "CPS counter", true);
    public static final Module TARGET      = new Module("target", "Target HUD", true);
    public static final Module PARTICLES   = new Module("particles", "Hit particles", true);
    public static final Module KEYSTROKES  = new Module("keystrokes", "Keystrokes", true);
    public static final Module ARMOR       = new Module("armor", "Armor HUD", true);
    public static final Module EFFECTS     = new Module("effects", "Potion effects", true);
    public static final Module ITEMS       = new Module("items", "Item counts", true);
    public static final Module STATS       = new Module("stats", "FPS / Ping", true);
    public static final Module REACH       = new Module("reach", "Hit distance", true);
    public static final Module CROSSHAIR   = new Module("crosshair", "Target dot", true);

    public final String id;
    public final String label;
    public boolean enabled;

    private Module(String id, String label, boolean def) {
        this.id = id;
        this.label = label;
        this.enabled = def;
        ALL.add(this);
    }

    public void set(boolean value) {
        this.enabled = value;
        save();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpvisuals.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Properties p = new Properties();
            p.load(r);
            for (Module m : ALL) {
                String v = p.getProperty(m.id);
                if (v != null) m.enabled = Boolean.parseBoolean(v);
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        Properties p = new Properties();
        for (Module m : ALL) p.setProperty(m.id, Boolean.toString(m.enabled));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "PvP Visuals modules");
        } catch (IOException ignored) {
        }
    }
}
