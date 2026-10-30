package com.jailblack.editor;

import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;

public class ModelExportManager {

    public static void ensureModelsDirectory() {
        try {
            File dir = new File("models");
            if (!dir.exists()) dir.mkdirs();
        } catch (Exception ignored) {}
    }

    public static void saveModel(VoxelModel model, String filename) {
        if (model == null || filename == null || filename.isEmpty()) return;
        ensureModelsDirectory();

        Map<String, Object> data = new HashMap<>();
        data.put("name", model.name);
        data.put("sizeX", model.sizeX);
        data.put("sizeY", model.sizeY);
        data.put("sizeZ", model.sizeZ);

        List<Map<String, Object>> voxelsData = new ArrayList<>();
        if (model.voxels != null) {
            for (Voxel v : model.voxels) {
                Map<String, Object> vd = new HashMap<>();
                vd.put("x", v.x); vd.put("y", v.y); vd.put("z", v.z);
                vd.put("color", Voxel.colorToHex(v.color));
                vd.put("alpha", v.getAlpha());
                voxelsData.add(vd);
            }
        }
        data.put("voxels", voxelsData);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = new FileWriter("models/" + filename + ".jbm")) {
            gson.toJson(data, writer);
            System.out.println("Model saved: " + filename + ".jbm");
        } catch (Exception e) {
            System.err.println("Failed to save model: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static VoxelModel loadModel(String filename) {
        if (filename == null || filename.isEmpty()) return null;
        File file = new File("models/" + filename + ".jbm");
        if (!file.exists()) {
            System.err.println("Model not found: " + file.getPath());
            return null;
        }
        Gson gson = new Gson();
        try (Reader reader = new FileReader(file)) {
            Map<String, Object> data = gson.fromJson(reader, Map.class);
            String name = data.get("name") != null ? (String) data.get("name") : filename;
            int sx = data.get("sizeX") != null ? ((Number) data.get("sizeX")).intValue() : 10;
            int sy = data.get("sizeY") != null ? ((Number) data.get("sizeY")).intValue() : 10;
            int sz = data.get("sizeZ") != null ? ((Number) data.get("sizeZ")).intValue() : 10;
            VoxelModel model = new VoxelModel(name, sx, sy, sz);
            List<Map<String, Object>> voxelsData = (List<Map<String, Object>>) data.get("voxels");
            if (voxelsData != null) {
                for (Map<String, Object> vd : voxelsData) {
                    int x = ((Number) vd.get("x")).intValue();
                    int y = ((Number) vd.get("y")).intValue();
                    int z = ((Number) vd.get("z")).intValue();
                    String color = (String) vd.get("color");
                    float alpha = vd.get("alpha") != null ? ((Number) vd.get("alpha")).floatValue() : 1.0f;
                    model.voxels.add(new Voxel(x, y, z, color, alpha));
                }
            }
            model.markAABBDirty();
            System.out.println("Model loaded: " + filename + ".jbm");
            return model;
        } catch (Exception e) {
            System.err.println("Failed to load model: " + e.getMessage());
            return null;
        }
    }
}
