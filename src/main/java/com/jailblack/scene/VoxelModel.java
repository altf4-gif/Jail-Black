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

    public int sizeX = 1;
    public int sizeY = 1;
    public int sizeZ = 1;

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

    public int getVoxelCount() {
        if (voxels == null) return 0;
        return voxels.size();
    }

    public void markAABBDirty() {
        aabbDirty = true;
    }

    public boolean isAABBDirty() {
        return aabbDirty;
    }

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
                if (v.x < minX) minX = v.x;
                if (v.y < minY) minY = v.y;
                if (v.z < minZ) minZ = v.z;
                if (v.x > maxX) maxX = v.x;
                if (v.y > maxY) maxY = v.y;
                if (v.z > maxZ) maxZ = v.z;
            }

            cachedAABBMin = new Vector3f(minX - 0.5f, minY - 0.5f, minZ - 0.5f);
            cachedAABBMax = new Vector3f(maxX + 0.5f, maxY + 0.5f, maxZ + 0.5f);
        }
        aabbDirty = false;
    }

    /**
     * Расширяет границы модели так, чтобы включить указанную точку.
     * Если координата отрицательная, все существующие воксели сдвигаются
     * в положительную сторону, а transform.position компенсируется, чтобы
     * модель осталась на том же месте в мире.
     */
    public void expandToInclude(int x, int y, int z) {
        expandToInclude(x, y, z, x, y, z);
    }

    /**
     * Расширяет границы модели так, чтобы включить весь диапазон [min..max]
     * по каждой оси.
     */
    public void expandToInclude(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax) {
        int shiftX = xMin < 0 ? -xMin : 0;
        int shiftY = yMin < 0 ? -yMin : 0;
        int shiftZ = zMin < 0 ? -zMin : 0;

        if (shiftX > 0 || shiftY > 0 || shiftZ > 0) {
            if (voxels != null) {
                for (Voxel v : voxels) {
                    v.x += shiftX;
                    v.y += shiftY;
                    v.z += shiftZ;
                }
            }
            transform.position.x -= shiftX;
            transform.position.y -= shiftY;
            transform.position.z -= shiftZ;

            sizeX += shiftX;
            sizeY += shiftY;
            sizeZ += shiftZ;
        }

        int newSizeX = Math.max(sizeX, xMax + shiftX + 1);
        int newSizeY = Math.max(sizeY, yMax + shiftY + 1);
        int newSizeZ = Math.max(sizeZ, zMax + shiftZ + 1);

        if (newSizeX != sizeX || newSizeY != sizeY || newSizeZ != sizeZ) {
            sizeX = newSizeX;
            sizeY = newSizeY;
            sizeZ = newSizeZ;
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
        if (voxels == null) return false;
        if (x < 0 || y < 0 || z < 0) return false;
        if (x >= sizeX || y >= sizeY || z >= sizeZ) return false;

        Voxel existing = getVoxelAt(x, y, z);
        if (existing != null) {
            existing.color = Voxel.toHex(color);
        } else {
            voxels.add(new Voxel(x, y, z, Voxel.toHex(color)));
        }
        markAABBDirty();
        return true;
    }

    public boolean addVoxel(int x, int y, int z, String hexColor) {
        if (voxels == null) return false;
        if (x < 0 || y < 0 || z < 0) return false;
        if (x >= sizeX || y >= sizeY || z >= sizeZ) return false;

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
        boolean removed = voxels.removeIf(voxel -> voxel.x == x && voxel.y == y && voxel.z == z);
        if (removed) markAABBDirty();
        return removed;
    }

    public void clear() {
        if (voxels != null) voxels.clear();
        markAABBDirty();
    }
}
// === END OF FILE ===
