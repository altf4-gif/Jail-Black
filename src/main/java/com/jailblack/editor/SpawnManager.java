package com.jailblack.editor;

import com.jailblack.engine.Camera;
import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import org.joml.Vector3f;

public class SpawnManager {
    private final Scene scene;
    private final CommandHistory commandHistory;
    private Vector3f currentColor;
    private float currentAlpha = 1.0f;

    public SpawnManager(Scene scene, CommandHistory commandHistory, Vector3f currentColor) {
        this(scene, commandHistory, currentColor, 1.0f);
    }

    public SpawnManager(Scene scene, CommandHistory commandHistory, Vector3f currentColor, float currentAlpha) {
        this.scene = scene;
        this.commandHistory = commandHistory;
        this.currentColor = currentColor;
        this.currentAlpha = currentAlpha;
    }

    public void setCurrentColor(Vector3f color) {
        this.currentColor = color;
    }

    public void setCurrentAlpha(float alpha) {
        this.currentAlpha = alpha;
    }

    public Vector3f getCurrentColor() {
        return currentColor;
    }

    public VoxelModel createNewModel(String name, int sizeX, int sizeY, int sizeZ) {
        VoxelModel model = new VoxelModel(name, sizeX, sizeY, sizeZ);
        if (model.transform != null) {
            model.transform.position.set(0.0f, 0.0f, 0.0f);
            model.transform.rotation.set(0.0f, 0.0f, 0.0f);
            model.transform.scale.set(1.0f, 1.0f, 1.0f);
        }
        scene.getModels().add(model);
        return model;
    }

    public void spawnInFront(Camera camera, Vector3f color) {
        Vector3f spawnPos = new Vector3f(camera.getPosition())
                .add(new Vector3f(camera.getFront()).mul(5f));
        spawnPos.x = Math.round(spawnPos.x);
        spawnPos.y = Math.round(spawnPos.y);
        spawnPos.z = Math.round(spawnPos.z);

        VoxelModel newObj = new VoxelModel(spawnPos.x, spawnPos.y, spawnPos.z);
        newObj.name = "VoxelObject_" + (scene.getModels().size() + 1);
        newObj.voxels.add(new Voxel(0, 0, 0, Voxel.toHex(color)));
        commandHistory.execute(new Commands.SpawnCommand(scene, newObj));
    }

    public void spawnAt(float x, float y, float z, Vector3f color) {
        VoxelModel newObj = new VoxelModel(x, y, z);
        newObj.voxels.add(new Voxel(0, 0, 0, Voxel.toHex(color)));
        commandHistory.execute(new Commands.SpawnCommand(scene, newObj));
    }

    public void duplicateModel(VoxelModel source) {
        if (source == null) return;

        VoxelModel copy = new VoxelModel(
                source.name + "_copy",
                source.sizeX,
                source.sizeY,
                source.sizeZ);

        if (source.voxels != null) {
            copy.voxels.clear();
            for (Voxel voxel : source.voxels) copy.voxels.add(voxel.copy());
        }

        if (source.transform != null && copy.transform != null) {
            copy.transform.position.set(source.transform.position);
            copy.transform.position.x += 2.0f;
            copy.transform.rotation.set(source.transform.rotation);
            copy.transform.scale.set(source.transform.scale);
        }

        copy.markAABBDirty();
        scene.getModels().add(copy);
    }
}
// === END OF FILE ===
