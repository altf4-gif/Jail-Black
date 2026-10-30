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

        public SpawnCommand(Scene scene, VoxelModel model) {
            this.scene = scene;
            this.model = model;
        }

        @Override
        public void execute() { scene.addModel(model); }

        @Override
        public void undo() { scene.removeModel(model); }

        @Override
        public String getDescription() { return "Spawn " + model.name; }
    }

    public static class DeleteCommand extends Command {
        private final Scene scene;
        private final List<VoxelModel> models;

        public DeleteCommand(Scene scene, List<VoxelModel> models) {
            this.scene = scene;
            this.models = new ArrayList<>(models);
        }

        @Override
        public void execute() { scene.removeModels(models); }

        @Override
        public void undo() {
            for (VoxelModel m : models) scene.addModel(m);
        }

        @Override
        public String getDescription() { return "Delete " + models.size() + " object(s)"; }
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

        @Override
        public void execute() {
            for (int i = 0; i < models.size(); i++) models.get(i).transform.position.set(newPositions[i]);
        }

        @Override
        public void undo() {
            for (int i = 0; i < models.size(); i++) models.get(i).transform.position.set(oldPositions[i]);
        }

        @Override
        public String getDescription() { return "Move " + models.size() + " object(s)"; }
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

        @Override
        public void execute() {
            for (VoxelModel model : models)
                for (Voxel v : model.voxels) v.color = newColor;
        }

        @Override
        public void undo() {
            for (int i = 0; i < models.size(); i++) {
                VoxelModel model = models.get(i);
                for (int j = 0; j < model.voxels.size(); j++) model.voxels.get(j).color = oldColors[i][j];
            }
        }

        @Override
        public String getDescription() { return "Change color"; }
    }
}
