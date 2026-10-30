package com.jailblack.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VoxelModel {
    public String id;
    public Transform transform;
    public List<Voxel> voxels;
    public String name;

    public VoxelModel() {
        this.id = UUID.randomUUID().toString();
        this.transform = new Transform();
        this.voxels = new ArrayList<>();
        this.name = "VoxelModel";
    }

    public VoxelModel(float x, float y, float z) {
        this();
        this.transform.position.set(x, y, z);
    }

    public VoxelModel(String name) {
        this();
        this.name = name;
    }
}
