package com.jailblack.scene;

import java.util.ArrayList;
import java.util.List;

public class Scene {
    private final List<VoxelModel> models = new ArrayList<>();
    private String name;

    public Scene() { this.name = "Untitled Scene"; }
    public Scene(String name) { this.name = name; }

    public void addModel(VoxelModel model) {
        if (model != null) models.add(model);
    }

    public void removeModel(VoxelModel model) {
        if (model != null) models.remove(model);
    }

    public void removeModels(List<VoxelModel> modelsToRemove) {
        if (modelsToRemove != null) models.removeAll(modelsToRemove);
    }

    public List<VoxelModel> getModels() { return models; }
    public void clear() { models.clear(); }
    public int size() { return models.size(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public VoxelModel findModelById(String id) {
        if (id == null) return null;
        for (VoxelModel m : models) if (id.equals(m.id)) return m;
        return null;
    }

    public VoxelModel findModelByName(String name) {
        if (name == null) return null;
        for (VoxelModel m : models) if (name.equals(m.name)) return m;
        return null;
    }
}
