package com.jailblack.editor;

import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;

public class MapManager {
    private final Scene scene;
    private final CommandHistory commandHistory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MapManager(Scene scene, CommandHistory commandHistory) {
        this.scene = scene;
        this.commandHistory = commandHistory;
    }

    public void save(String filename) {
        File dir = new File("maps");
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Failed to create maps directory");
            return;
        }
        JsonObject root = new JsonObject();
        JsonArray objects = new JsonArray();
        for (VoxelModel model : scene.getModels()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", model.name);
            obj.addProperty("id", model.id);
            obj.addProperty("sizeX", model.sizeX);
            obj.addProperty("sizeY", model.sizeY);
            obj.addProperty("sizeZ", model.sizeZ);
            JsonObject transform = new JsonObject();
            transform.addProperty("px", model.transform.position.x);
            transform.addProperty("py", model.transform.position.y);
            transform.addProperty("pz", model.transform.position.z);
            transform.addProperty("rx", model.transform.rotation.x);
            transform.addProperty("ry", model.transform.rotation.y);
            transform.addProperty("rz", model.transform.rotation.z);
            transform.addProperty("sx", model.transform.scale.x);
            transform.addProperty("sy", model.transform.scale.y);
            transform.addProperty("sz", model.transform.scale.z);
            obj.add("transform", transform);
            JsonArray voxels = new JsonArray();
            if (model.voxels != null) {
                for (Voxel v : model.voxels) {
                    JsonObject voxel = new JsonObject();
                    voxel.addProperty("x", v.x);
                    voxel.addProperty("y", v.y);
                    voxel.addProperty("z", v.z);
                    voxel.addProperty("color", v.color);
                    voxel.addProperty("alpha", v.getAlpha());
                    voxels.add(voxel);
                }
            }
            obj.add("voxels", voxels);
            objects.add(obj);
        }
        root.add("objects", objects);
        try (Writer writer = new FileWriter("maps/" + filename + ".json")) {
            gson.toJson(root, writer);
            System.out.println("Map saved: " + filename);
        } catch (IOException e) {
            System.err.println("Save error: " + e.getMessage());
        }
    }

    public void load(String filename) {
        File file = new File("maps/" + filename + ".json");
        if (!file.exists()) { System.err.println("File not found: " + filename); return; }
        try {
            String json = new String(Files.readAllBytes(file.toPath()));
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray objects = root.getAsJsonArray("objects");
            scene.clear();
            for (JsonElement element : objects) {
                JsonObject obj = element.getAsJsonObject();
                VoxelModel model = new VoxelModel();
                model.name = obj.get("name").getAsString();
                model.id = obj.get("id").getAsString();
                if (obj.has("sizeX")) model.sizeX = obj.get("sizeX").getAsInt();
                if (obj.has("sizeY")) model.sizeY = obj.get("sizeY").getAsInt();
                if (obj.has("sizeZ")) model.sizeZ = obj.get("sizeZ").getAsInt();
                JsonObject t = obj.getAsJsonObject("transform");
                model.transform.position.set(t.get("px").getAsFloat(), t.get("py").getAsFloat(), t.get("pz").getAsFloat());
                model.transform.rotation.set(t.get("rx").getAsFloat(), t.get("ry").getAsFloat(), t.get("rz").getAsFloat());
                model.transform.scale.set(t.get("sx").getAsFloat(), t.get("sy").getAsFloat(), t.get("sz").getAsFloat());
                JsonArray voxels = obj.getAsJsonArray("voxels");
                for (JsonElement ve : voxels) {
                    JsonObject v = ve.getAsJsonObject();
                    float alpha = v.has("alpha") ? v.get("alpha").getAsFloat() : 1.0f;
                    model.voxels.add(new Voxel(v.get("x").getAsInt(), v.get("y").getAsInt(), v.get("z").getAsInt(),
                            v.get("color").getAsString(), alpha));
                }
                model.markAABBDirty();
                scene.addModel(model);
            }
            System.out.println("Map loaded: " + filename);
        } catch (Exception e) {
            System.err.println("Load error: " + e.getMessage());
        }
    }

    public String[] getSavedMaps() {
        File dir = new File("maps");
        if (!dir.exists()) return new String[0];
        String[] files = dir.list((d, name) -> name.endsWith(".json"));
        if (files == null) return new String[0];
        for (int i = 0; i < files.length; i++) files[i] = files[i].replace(".json", "");
        return files;
    }
}
