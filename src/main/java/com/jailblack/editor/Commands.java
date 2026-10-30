package com.jailblack.editor;

import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Commands {

    public static class SpawnCommand extends Command {
        private final Scene scene;
        private final VoxelModel model;
        public SpawnCommand(Scene scene, VoxelModel model) { this.scene = scene; this.model = model; }
        @Override public void execute() { scene.addModel(model); }
        @Override public void undo() { scene.removeModel(model); }
        @Override public String getDescription() { return "Spawn " + model.name; }
    }

    public static class DeleteCommand extends Command {
        private final Scene scene;
        private final List<VoxelModel> models;
        public DeleteCommand(Scene scene, List<VoxelModel> models) {
            this.scene = scene;
            this.models = new ArrayList<>(models);
        }
        @Override public void execute() { scene.removeModels(models); }
        @Override public void undo() { for (VoxelModel m : models) scene.addModel(m); }
        @Override public String getDescription() { return "Delete " + models.size() + " object(s)"; }
    }

    public static class MoveCommand extends Command {
        private final List<VoxelModel> models;
        private final Vector3f[] oldPositions;
        private final Vector3f[] newPositions;
        public MoveCommand(List<VoxelModel> models, Vector3f[] oldPositions, Vector3f[] newPositions) {
            this.models = new ArrayList<>(models);
            this.oldPositions = oldPositions;
            this.newPositions = newPositions;
        }
        @Override public void execute() { for (int i = 0; i < models.size(); i++) models.get(i).transform.position.set(newPositions[i]); }
        @Override public void undo() { for (int i = 0; i < models.size(); i++) models.get(i).transform.position.set(oldPositions[i]); }
        @Override public String getDescription() { return "Move " + models.size() + " object(s)"; }
    }

    public static class ColorCommand extends Command {
        private final List<VoxelModel> models;
        private final String[][] oldColors;
        private final String newColor;
        public ColorCommand(List<VoxelModel> models, String[][] oldColors, String newColor) {
            this.models = new ArrayList<>(models);
            this.oldColors = oldColors;
            this.newColor = newColor;
        }
        @Override public void execute() {
            for (VoxelModel model : models) for (Voxel v : model.voxels) v.color = newColor;
        }
        @Override public void undo() {
            for (int i = 0; i < models.size(); i++) {
                List<Voxel> vs = models.get(i).voxels;
                for (int j = 0; j < vs.size(); j++) vs.get(j).color = oldColors[i][j];
            }
        }
        @Override public String getDescription() { return "Change color"; }
    }

    // Scale-слои: снапшот до/после
    public static class VoxelEditCommand extends Command {
        private final VoxelModel model;
        private final List<Voxel> oldVoxels;
        private final List<Voxel> newVoxels;
        public VoxelEditCommand(VoxelModel model, List<Voxel> oldVoxels, List<Voxel> newVoxels) {
            this.model = model;
            this.oldVoxels = copy(oldVoxels);
            this.newVoxels = copy(newVoxels);
        }
        private static List<Voxel> copy(List<Voxel> src) {
            List<Voxel> out = new ArrayList<>();
            for (Voxel v : src) out.add(new Voxel(v.x, v.y, v.z, v.color));
            return out;
        }
        @Override public void execute() { model.voxels.clear(); model.voxels.addAll(copy(newVoxels)); }
        @Override public void undo() { model.voxels.clear(); model.voxels.addAll(copy(oldVoxels)); }
        @Override public String getDescription() { return "Voxel edit"; }
    }

    // Объединение нескольких объектов в один
    public static class CombineCommand extends Command {
        private final Scene scene;
        private final List<VoxelModel> sources;
        private final VoxelModel combined;
        public CombineCommand(Scene scene, List<VoxelModel> sources, VoxelModel combined) {
            this.scene = scene;
            this.sources = new ArrayList<>(sources);
            this.combined = combined;
        }
        @Override public void execute() { scene.removeModels(sources); scene.addModel(combined); }
        @Override public void undo() { scene.removeModel(combined); for (VoxelModel m : sources) scene.addModel(m); }
        @Override public String getDescription() { return "Combine " + sources.size() + " objects"; }
    }
}
// === END OF FILE ===
