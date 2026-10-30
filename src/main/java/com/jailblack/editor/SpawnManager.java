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

    public SpawnManager(Scene scene, CommandHistory commandHistory, Vector3f currentColor) {
        this.scene = scene;
        this.commandHistory = commandHistory;
        this.currentColor = currentColor;
    }

    public void setCurrentColor(Vector3f color) {
        this.currentColor = color;
    }

    public void spawnInFront(Camera camera, Vector3f color) {
        Vector3f spawnPos = new Vector3f(camera.getPosition())
                .add(new Vector3f(camera.getFront()).mul(5f));
        spawnPos.x = Math.round(spawnPos.x);
        spawnPos.y = Math.round(spawnPos.y);
        spawnPos.z = Math.round(spawnPos.z);

        VoxelModel newObj = new VoxelModel(spawnPos.x, spawnPos.y, spawnPos.z);
        newObj.name = "VoxelObject_" + (scene.getModels().size() + 1);

        String hex = String.format("#%02x%02x%02x",
                (int)(color.x * 255),
                (int)(color.y * 255),
                (int)(color.z * 255));
        newObj.voxels.add(new Voxel(0, 0, 0, hex));

        commandHistory.execute(new Commands.SpawnCommand(scene, newObj));
    }

    public void spawnAt(float x, float y, float z, String color) {
        VoxelModel newObj = new VoxelModel(x, y, z);
        newObj.voxels.add(new Voxel(0, 0, 0, color));
        commandHistory.execute(new Commands.SpawnCommand(scene, newObj));
    }
}
