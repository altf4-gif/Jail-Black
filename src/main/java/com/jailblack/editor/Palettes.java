package com.jailblack.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class Palettes {
    public static class Palette {
        public String name;
        public List<String> colors = new ArrayList<>();
        public Palette() {}
        public Palette(String name) { this.name = name; }
    }

    public List<Palette> palettes = new ArrayList<>();
    public int activeIndex = 0;

    private final File file = new File("palettes.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Palettes() {
        load();
        if (palettes.isEmpty()) {
            palettes.add(new Palette("Palette_1"));
            activeIndex = 0;
        }
    }

    public Palette active() {
        return palettes.get(activeIndex);
    }

    public void newPalette() {
        palettes.add(new Palette("Palette_" + (palettes.size() + 1)));
        activeIndex = palettes.size() - 1;
        save();
    }

    public void addCurrent(String hex) {
        active().colors.add(hex);
        save();
    }

    public void removeColor(int idx) {
        if (idx >= 0 && idx < active().colors.size()) {
            active().colors.remove(idx);
            save();
        }
    }

    public void setActive(int idx) {
        if (idx >= 0 && idx < palettes.size()) {
            activeIndex = idx;
            save();
        }
    }

    public void save() {
        try (Writer w = new FileWriter(file)) {
            gson.toJson(this, w);
        } catch (Exception e) {
            System.err.println("Palettes save error: " + e.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;
        try (Reader r = new FileReader(file)) {
            Palettes p = gson.fromJson(r, Palettes.class);
            if (p != null && p.palettes != null && !p.palettes.isEmpty()) {
                this.palettes = p.palettes;
                this.activeIndex = Math.max(0, Math.min(p.activeIndex, palettes.size() - 1));
            }
        } catch (Exception e) {
            System.err.println("Palettes load error: " + e.getMessage());
        }
    }
}
// === END OF FILE ===
