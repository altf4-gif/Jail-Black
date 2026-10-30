package com.jailblack.editor;

import com.jailblack.engine.Camera;
import com.jailblack.engine.Window;
import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class SelectionManager {
    private final Scene scene;
    private final List<VoxelModel> selectedObjects;
    private final Camera camera;
    private final Window window;

    public SelectionManager(Scene scene, List<VoxelModel> selectedObjects, Camera camera, Window window) {
        this.scene = scene;
        this.selectedObjects = selectedObjects;
        this.camera = camera;
        this.window = window;
    }

    public VoxelModel pick() {
        Vector3f camPos = camera.getPosition();
        Vector3f camDir = camera.getFront();
        VoxelModel hitObj = null;
        float minDist = Float.MAX_VALUE;
        for (VoxelModel obj : scene.getModels()) {
            Matrix4f model = obj.transform.getModelMatrix();
            Vector3f min = new Vector3f(Float.MAX_VALUE);
            Vector3f max = new Vector3f(-Float.MAX_VALUE);
            if (obj.voxels == null || obj.voxels.isEmpty()) {
                min.set(obj.transform.position);
                max.set(obj.transform.position);
            } else {
                for (Voxel v : obj.voxels) {
                    Vector3f p = new Vector3f(v.x, v.y, v.z);
                    model.transformPosition(p);
                    min.min(p); max.max(p);
                }
            }
            min.sub(0.5f, 0.5f, 0.5f);
            max.add(0.5f, 0.5f, 0.5f);
            if (rayIntersectsAABB(camPos, camDir, min, max)) {
                float dist = camPos.distance(new Vector3f(min).add(max).mul(0.5f));
                if (dist < minDist) { minDist = dist; hitObj = obj; }
            }
        }
        return hitObj;
    }

    private boolean rayIntersectsAABB(Vector3f o, Vector3f d, Vector3f bMin, Vector3f bMax) {
        float tmin = (bMin.x - o.x) / d.x, tmax = (bMax.x - o.x) / d.x;
        if (tmin > tmax) { float t = tmin; tmin = tmax; tmax = t; }
        float tymin = (bMin.y - o.y) / d.y, tymax = (bMax.y - o.y) / d.y;
        if (tymin > tymax) { float t = tymin; tymin = tymax; tymax = t; }
        if ((tmin > tymax) || (tymin > tmax)) return false;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;
        float tzmin = (bMin.z - o.z) / d.z, tzmax = (bMax.z - o.z) / d.z;
        if (tzmin > tzmax) { float t = tzmin; tzmin = tzmax; tzmax = t; }
        if ((tmin > tzmax) || (tzmin > tmax)) return false;
        return tmax >= 0;
    }

    public void selectAll() { selectedObjects.clear(); selectedObjects.addAll(scene.getModels()); }
    public void clearSelection() { selectedObjects.clear(); }
}
