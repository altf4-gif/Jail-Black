package com.jailblack.scene;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VoxelModel {
    public String id;
    public Transform transform;
    public List<Voxel> voxels;
    public String name;
    public int sizeX = 1, sizeY = 1, sizeZ = 1;

    private Vector3f cachedAABBMin;
    private Vector3f cachedAABBMax;
    private boolean aabbDirty = true;

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

    public VoxelModel(String name, int sizeX, int sizeY, int sizeZ) {
        this(name);
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
    }

    public VoxelModel(int sizeX, int sizeY, int sizeZ) {
        this();
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
    }

    public int getVoxelCount() { return voxels == null ? 0 : voxels.size(); }

    public void markAABBDirty() { aabbDirty = true; }
    public boolean isAABBDirty() { return aabbDirty; }

    public Vector3f getAABBMin() {
        if (aabbDirty || cachedAABBMin == null) recalculateAABB();
        return cachedAABBMin;
    }

    public Vector3f getAABBMax() {
        if (aabbDirty || cachedAABBMax == null) recalculateAABB();
        return cachedAABBMax;
    }

    private void recalculateAABB() {
        if (voxels == null || voxels.isEmpty()) {
            cachedAABBMin = new Vector3f(transform.position).sub(0.5f, 0.5f, 0.5f);
            cachedAABBMax = new Vector3f(transform.position).add(0.5f, 0.5f, 0.5f);
        } else {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (Voxel v : voxels) {
                minX = Math.min(minX, v.x); minY = Math.min(minY, v.y); minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x); maxY = Math.max(maxY, v.y); maxZ = Math.max(maxZ, v.z);
            }
            cachedAABBMin = new Vector3f(minX - 0.5f, minY - 0.5f, minZ - 0.5f);
            cachedAABBMax = new Vector3f(maxX + 0.5f, maxY + 0.5f, maxZ + 0.5f);
        }
        aabbDirty = false;
    }

    public void expandToInclude(int x, int y, int z) {
        expandToInclude(x, y, z, x, y, z);
    }

    public void expandToInclude(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax) {
        int shiftX = Math.max(0, -xMin);
        int shiftY = Math.max(0, -yMin);
        int shiftZ = Math.max(0, -zMin);
        if (shiftX > 0 || shiftY > 0 || shiftZ > 0) {
            if (voxels != null) {
                for (Voxel v : voxels) { v.x += shiftX; v.y += shiftY; v.z += shiftZ; }
            }
            transform.position.x -= shiftX;
            transform.position.y -= shiftY;
            transform.position.z -= shiftZ;
            sizeX += shiftX; sizeY += shiftY; sizeZ += shiftZ;
        }
        int newSizeX = Math.max(sizeX, xMax + shiftX + 1);
        int newSizeY = Math.max(sizeY, yMax + shiftY + 1);
        int newSizeZ = Math.max(sizeZ, zMax + shiftZ + 1);
        if (newSizeX != sizeX || newSizeY != sizeY || newSizeZ != sizeZ) {
            sizeX = newSizeX; sizeY = newSizeY; sizeZ = newSizeZ;
        }
        markAABBDirty();
    }

    public Voxel getVoxelAt(int x, int y, int z) {
        if (voxels == null) return null;
        for (Voxel voxel : voxels) {
            if (voxel.x == x && voxel.y == y && voxel.z == z) return voxel;
        }
        return null;
    }

    public boolean addVoxel(int x, int y, int z, Vector3f color) {
        return addVoxel(x, y, z, Voxel.toHex(color));
    }

    public boolean addVoxel(int x, int y, int z, String hexColor) {
        if (voxels == null || x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ)
            return false;
        Voxel existing = getVoxelAt(x, y, z);
        if (existing != null) {
            existing.color = Voxel.colorToHex(hexColor);
        } else {
            voxels.add(new Voxel(x, y, z, Voxel.colorToHex(hexColor)));
        }
        markAABBDirty();
        return true;
    }

    public boolean removeVoxel(int x, int y, int z) {
        if (voxels == null) return false;
        boolean removed = voxels.removeIf(v -> v.x == x && v.y == y && v.z == z);
        if (removed) markAABBDirty();
        return removed;
    }

    public void clear() {
        if (voxels != null) voxels.clear();
        markAABBDirty();
    }

    public boolean containsVoxel(int x, int y, int z) {
        return getVoxelAt(x, y, z) != null;
    }

    public VoxelModel deepCopy() {
        VoxelModel copy = new VoxelModel(name + "_copy", sizeX, sizeY, sizeZ);
        copy.id = UUID.randomUUID().toString();
        copy.transform = transform.copy();
        if (voxels != null) {
            copy.voxels.clear();
            for (Voxel v : voxels) copy.voxels.add(v.copy());
        }
        copy.markAABBDirty();
        return copy;
    }
}
