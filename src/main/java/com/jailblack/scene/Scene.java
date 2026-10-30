package com.jailblack.scene;

import java.util.ArrayList;
import java.util.List;

public class Scene {
    private final List<VoxelModel> models = new ArrayList<>();
    private String name;

    public Scene() {
        this.name = "Untitled Scene";
    }

    public Scene(String name) {
        this.name = name;
    }

    public void addModel(VoxelModel model) {
        models.add(model);
    }

    public void removeModel(VoxelModel model) {
        models.remove(model);
    }

    public void removeModels(List<VoxelModel> modelsToRemove) {
        models.removeAll(modelsToRemove);
    }

    public List<VoxelModel> getModels() {
        return models;
    }

    public void clear() {
        models.clear();
    }

    public int size() {
        return models.size();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
