package game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VoxelObject {
    public String id;
    public Transform transform;
    public List<Voxel> voxels;

    public VoxelObject() {
        this.id = UUID.randomUUID().toString();
        this.transform = new Transform();
        this.voxels = new ArrayList<>();
    }

    public VoxelObject(float x, float y, float z) {
        this();
        this.transform.position.set(x, y, z);
    }
}
