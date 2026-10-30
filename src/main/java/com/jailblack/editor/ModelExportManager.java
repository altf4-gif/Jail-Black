package com.jailblack.editor;

import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                Map<String, Object> voxelData = new HashMap<>();
                voxelData.put("x", v.x);
                voxelData.put("y", v.y);
                voxelData.put("z", v.z);
                voxelData.put("color", Voxel.colorToHex(v.color));
                voxelsData.add(voxelData);
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
            int sizeX = data.get("sizeX") != null ? ((Number) data.get("sizeX")).intValue() : 10;
            int sizeY = data.get("sizeY") != null ? ((Number) data.get("sizeY")).intValue() : 10;
            int sizeZ = data.get("sizeZ") != null ? ((Number) data.get("sizeZ")).intValue() : 10;

            VoxelModel model = new VoxelModel(name, sizeX, sizeY, sizeZ);

            List<Map<String, Object>> voxelsData = (List<Map<String, Object>>) data.get("voxels");
            if (voxelsData != null) {
                for (Map<String, Object> voxelData : voxelsData) {
                    int x = ((Number) voxelData.get("x")).intValue();
                    int y = ((Number) voxelData.get("y")).intValue();
                    int z = ((Number) voxelData.get("z")).intValue();
                    String color = (String) voxelData.get("color");
                    model.voxels.add(new Voxel(x, y, z, color));
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
// === END OF FILE ===
