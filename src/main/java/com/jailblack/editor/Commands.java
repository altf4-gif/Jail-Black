package com.jailblack.editor;

import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Commands {

    public static class SpawnCommand extends Command {
        private final Scene scene;
        private final VoxelModel model;
        public SpawnCommand(Scene scene, VoxelModel model) {
            this.scene = scene; this.model = model;
        }
        @Override public void execute() { scene.addModel(model); }
        @Override public void undo() { scene.removeModel(model); }
        @Override public String getDescription() { return "Spawn " + model.name; }
    }

    public static class DeleteCommand extends Command {
        private final Scene scene;
        private final List<VoxelModel> models;
        public DeleteCommand(Scene scene, List<VoxelModel> models) {
            this.scene = scene; this.models = new ArrayList<>(models);
        }
        @Override public void execute() { scene.removeModels(models); }
        @Override public void undo() { for (VoxelModel m : models) scene.addModel(m); }
        @Override public String getDescription() { return "Delete " + models.size() + " object(s)"; }
    }

    public static class MoveCommand extends Command {
        private final List<VoxelModel> models;
        private final Vector3f[] oldPositions, newPositions;
        public MoveCommand(List<VoxelModel> models, Vector3f[] oldPositions, Vector3f[] newPositions) {
            this.models = new ArrayList<>(models);
            this.oldPositions = oldPositions;
            this.newPositions = newPositions;
        }
        @Override public void execute() {
            for (int i = 0; i < models.size(); i++) {
                models.get(i).transform.position.set(newPositions[i]);
                models.get(i).markAABBDirty();
            }
        }
        @Override public void undo() {
            for (int i = 0; i < models.size(); i++) {
                models.get(i).transform.position.set(oldPositions[i]);
                models.get(i).markAABBDirty();
            }
        }
        @Override public String getDescription() { return "Move " + models.size() + " object(s)"; }
    }

    public static class TransformCommand extends Command {
        private final VoxelModel model;
        private final Vector3f oldPos, oldRot, oldScale, newPos, newRot, newScale;
        public TransformCommand(VoxelModel model,
                                Vector3f oldPos, Vector3f oldRot, Vector3f oldScale,
                                Vector3f newPos, Vector3f newRot, Vector3f newScale) {
            this.model = model;
            this.oldPos = new Vector3f(oldPos); this.oldRot = new Vector3f(oldRot); this.oldScale = new Vector3f(oldScale);
            this.newPos = new Vector3f(newPos); this.newRot = new Vector3f(newRot); this.newScale = new Vector3f(newScale);
        }
        @Override public void execute() {
            model.transform.position.set(newPos);
            model.transform.rotation.set(newRot);
            model.transform.scale.set(newScale);
            model.markAABBDirty();
        }
        @Override public void undo() {
            model.transform.position.set(oldPos);
            model.transform.rotation.set(oldRot);
            model.transform.scale.set(oldScale);
            model.markAABBDirty();
        }
        @Override public String getDescription() { return "Transform " + model.name; }
    }

    public static class ColorCommand extends Command {
        private final List<VoxelModel> models;
        private final String[] oldColors;
        private final String newColor;
        public ColorCommand(List<VoxelModel> models, String[] oldColors, String newColor) {
            this.models = new ArrayList<>(models);
            this.oldColors = oldColors;
            this.newColor = newColor;
        }
        @Override public void execute() {
            for (VoxelModel model : models) {
                if (model.voxels == null) continue;
                for (Voxel v : model.voxels) v.color = newColor;
                model.markAABBDirty();
            }
        }
        @Override public void undo() {
            int idx = 0;
            for (VoxelModel model : models) {
                if (model.voxels == null) continue;
                for (Voxel v : model.voxels) {
                    if (idx < oldColors.length) v.color = oldColors[idx];
                    idx++;
                }
                model.markAABBDirty();
            }
        }
        @Override public String getDescription() { return "Change color"; }
    }

    public static class VoxelEditCommand extends Command {
        private final VoxelModel model;
        private final List<Voxel> oldVoxels, newVoxels;
        public VoxelEditCommand(VoxelModel model, List<Voxel> oldVoxels, List<Voxel> newVoxels) {
            this.model = model;
            this.oldVoxels = copy(oldVoxels);
            this.newVoxels = copy(newVoxels);
        }
        private static List<Voxel> copy(List<Voxel> src) {
            List<Voxel> out = new ArrayList<>();
            if (src != null) for (Voxel v : src) out.add(v.copy());
            return out;
        }
        @Override public void execute() {
            model.voxels.clear(); model.voxels.addAll(copy(newVoxels)); model.markAABBDirty();
        }
        @Override public void undo() {
            model.voxels.clear(); model.voxels.addAll(copy(oldVoxels)); model.markAABBDirty();
        }
        @Override public String getDescription() { return "Voxel edit"; }
    }

    public static class PaintVoxelCommand extends Command {
        private final VoxelModel model;
        private final int x, y, z;
        private final String oldColor, newColor;
        public PaintVoxelCommand(VoxelModel model, int x, int y, int z, Vector3f oldColor, Vector3f newColor) {
            this.model = model; this.x = x; this.y = y; this.z = z;
            this.oldColor = oldColor == null ? "#FFFFFF" : Voxel.toHex(oldColor);
            this.newColor = newColor == null ? "#FFFFFF" : Voxel.toHex(newColor);
        }
        @Override public void execute() {
            if (model == null) return;
            Voxel voxel = model.getVoxelAt(x, y, z);
            if (voxel != null) { voxel.color = newColor; model.markAABBDirty(); }
        }
        @Override public void undo() {
            if (model == null) return;
            Voxel voxel = model.getVoxelAt(x, y, z);
            if (voxel != null) { voxel.color = oldColor; model.markAABBDirty(); }
        }
        @Override public String getDescription() { return "Paint voxel"; }
    }

    public static class FillCommand extends Command {
        private final VoxelModel model;
        private final List<Voxel> oldVoxels, newVoxels;
        public FillCommand(VoxelModel model, List<Voxel> oldVoxels, List<Voxel> newVoxels) {
            this.model = model;
            this.oldVoxels = copy(oldVoxels);
            this.newVoxels = copy(newVoxels);
        }
        private static List<Voxel> copy(List<Voxel> src) {
            List<Voxel> out = new ArrayList<>();
            if (src != null) for (Voxel v : src) out.add(v.copy());
            return out;
        }
        @Override public void execute() {
            model.voxels.clear(); model.voxels.addAll(copy(newVoxels)); model.markAABBDirty();
        }
        @Override public void undo() {
            model.voxels.clear(); model.voxels.addAll(copy(oldVoxels)); model.markAABBDirty();
        }
        @Override public String getDescription() { return "Fill volume"; }
    }

    public static class ClearCommand extends Command {
        private final VoxelModel model;
        private final List<Voxel> oldVoxels;
        public ClearCommand(VoxelModel model) {
            this.model = model;
            this.oldVoxels = new ArrayList<>();
            if (model.voxels != null) for (Voxel v : model.voxels) oldVoxels.add(v.copy());
        }
        @Override public void execute() { model.voxels.clear(); model.markAABBDirty(); }
        @Override public void undo() {
            model.voxels.clear();
            for (Voxel v : oldVoxels) model.voxels.add(v.copy());
            model.markAABBDirty();
        }
        @Override public String getDescription() { return "Clear model"; }
    }

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

    /**
     * Удаляет набор вокселей из модели по индексам.
     * execute(): удаляет с конца (по убыванию индекса) — промежуточные индексы не сдвигаются.
     * undo(): вставляет по возрастанию — элементы с меньшим индексом восстанавливаются первыми,
     *         поэтому более поздние индексы остаются валидными.
     */
    public static class DeleteVoxelsCommand extends Command {
        private final VoxelModel model;
        private final List<Voxel> removedVoxels;
        private final List<Integer> removedIndices;

        public DeleteVoxelsCommand(VoxelModel model, List<Voxel> removedVoxels, List<Integer> removedIndices) {
            this.model = model;
            this.removedVoxels = new ArrayList<>();
            for (Voxel v : removedVoxels) this.removedVoxels.add(v.copy());
            this.removedIndices = new ArrayList<>(removedIndices);
        }

        @Override public void execute() {
            List<Integer> sorted = new ArrayList<>(removedIndices);
            sorted.sort(Collections.reverseOrder());
            for (int idx : sorted) {
                if (idx >= 0 && idx < model.voxels.size()) {
                    model.voxels.remove(idx);
                }
            }
            model.markAABBDirty();
        }

        @Override public void undo() {
            for (int i = 0; i < removedIndices.size(); i++) {
                int idx = removedIndices.get(i);
                if (idx >= 0 && idx <= model.voxels.size()) {
                    model.voxels.add(idx, removedVoxels.get(i).copy());
                }
            }
            model.markAABBDirty();
        }

        @Override public String getDescription() { return "Delete " + removedVoxels.size() + " voxel(s)"; }
    }

    /**
     * Комплексная правка модели: воксели + bounds (sizeX/Y/Z) + позиция трансформа.
     * Нужна, когда при добавлении вокселя за пределы объёма модель одновременно
     * расширяется (expandToInclude сдвигает transform.position) — обычный
     * VoxelEditCommand этого не откатывает.
     */
    public static class ModelEditCommand extends Command {
        private final VoxelModel model;
        private final List<Voxel> oldVoxels, newVoxels;
        private final Vector3f oldPos, newPos;
        private final int osx, osy, osz, nsx, nsy, nsz;

        public ModelEditCommand(VoxelModel model, List<Voxel> oldVoxels, List<Voxel> newVoxels,
                                Vector3f oldPos, Vector3f newPos,
                                int osx, int osy, int osz, int nsx, int nsy, int nsz) {
            this.model = model;
            this.oldVoxels = copy(oldVoxels);
            this.newVoxels = copy(newVoxels);
            this.oldPos = new Vector3f(oldPos);
            this.newPos = new Vector3f(newPos);
            this.osx = osx; this.osy = osy; this.osz = osz;
            this.nsx = nsx; this.nsy = nsy; this.nsz = nsz;
        }
        private static List<Voxel> copy(List<Voxel> src) {
            List<Voxel> out = new ArrayList<>();
            if (src != null) for (Voxel v : src) out.add(v.copy());
            return out;
        }
        private void apply(List<Voxel> vox, Vector3f pos, int sx, int sy, int sz) {
            model.voxels.clear();
            for (Voxel v : vox) model.voxels.add(v.copy());
            model.transform.position.set(pos);
            model.sizeX = sx; model.sizeY = sy; model.sizeZ = sz;
            model.markAABBDirty();
        }
        @Override public void execute() { apply(newVoxels, newPos, nsx, nsy, nsz); }
        @Override public void undo() { apply(oldVoxels, oldPos, osx, osy, osz); }
        @Override public String getDescription() { return "Edit " + model.name + " (voxels+bounds)"; }
    }
}
// === END OF FILE ===
