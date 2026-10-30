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

    public void setCurrentColor(Vector3f color) { this.currentColor = color; }
    public void setCurrentAlpha(float alpha) { this.currentAlpha = alpha; }
    public Vector3f getCurrentColor() { return currentColor; }
    public float getCurrentAlpha() { return currentAlpha; }

    public VoxelModel createNewModel(String name, int sizeX, int sizeY, int sizeZ) {
        VoxelModel model = new VoxelModel(name, sizeX, sizeY, sizeZ);
        model.transform.position.set(0, 0, 0);
        model.transform.rotation.set(0, 0, 0);
        model.transform.scale.set(1, 1, 1);

        int cx = sizeX / 2;
        int cy = 0;
        int cz = sizeZ / 2;
        model.voxels.add(new Voxel(cx, cy, cz, Voxel.toHex(currentColor)));
        model.markAABBDirty();

        commandHistory.execute(new Commands.SpawnCommand(scene, model));
        return model;
    }

    public VoxelModel createNewModelAtCamera(String name, int sizeX, int sizeY, int sizeZ, Camera camera) {
        VoxelModel model = new VoxelModel(name, sizeX, sizeY, sizeZ);

        Vector3f front = camera.getFront();
        Vector3f spawnPos = new Vector3f(camera.getPosition()).add(new Vector3f(front).mul(8.0f));
        spawnPos.x = Math.round(spawnPos.x);
        spawnPos.y = Math.max(0, Math.round(spawnPos.y));
        spawnPos.z = Math.round(spawnPos.z);

        model.transform.position.set(spawnPos);
        model.transform.rotation.set(0, 0, 0);
        model.transform.scale.set(1, 1, 1);

        int cx = sizeX / 2;
        int cy = 0;
        int cz = sizeZ / 2;
        model.voxels.add(new Voxel(cx, cy, cz, Voxel.toHex(currentColor)));
        model.markAABBDirty();

        commandHistory.execute(new Commands.SpawnCommand(scene, model));
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
        VoxelModel copy = source.deepCopy();
        copy.transform.position.x += 2.0f;
        commandHistory.execute(new Commands.SpawnCommand(scene, copy));
    }
}
// === END OF FILE ===
