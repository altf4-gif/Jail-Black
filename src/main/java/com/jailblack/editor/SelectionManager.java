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

            if (obj.voxels.isEmpty()) {
                min.set(obj.transform.position);
                max.set(obj.transform.position);
            }

            for (Voxel v : obj.voxels) {
                Vector3f p = new Vector3f(v.x, v.y, v.z);
                model.transformPosition(p);
                min.min(p);
                max.max(p);
            }

            min.sub(0.5f, 0.5f, 0.5f);
            max.add(0.5f, 0.5f, 0.5f);

            if (rayIntersectsAABB(camPos, camDir, min, max)) {
                float dist = camPos.distance(new Vector3f(min).add(max).mul(0.5f));
                if (dist < minDist) {
                    minDist = dist;
                    hitObj = obj;
                }
            }
        }

        return hitObj;
    }

    private boolean rayIntersectsAABB(Vector3f rayOrigin, Vector3f rayDir, Vector3f boxMin, Vector3f boxMax) {
        float tmin = (boxMin.x - rayOrigin.x) / rayDir.x;
        float tmax = (boxMax.x - rayOrigin.x) / rayDir.x;
        if (tmin > tmax) { float temp = tmin; tmin = tmax; tmax = temp; }

        float tymin = (boxMin.y - rayOrigin.y) / rayDir.y;
        float tymax = (boxMax.y - rayOrigin.y) / rayDir.y;
        if (tymin > tymax) { float temp = tymin; tymin = tymax; tymax = temp; }

        if ((tmin > tymax) || (tymin > tmax)) return false;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        float tzmin = (boxMin.z - rayOrigin.z) / rayDir.z;
        float tzmax = (boxMax.z - rayOrigin.z) / rayDir.z;
        if (tzmin > tzmax) { float temp = tzmin; tzmin = tzmax; tzmax = temp; }

        if ((tmin > tzmax) || (tzmin > tmax)) return false;
        if (tzmin > tmin) tmin = tzmin;
        if (tzmax < tmax) tmax = tzmax;

        return tmax >= 0;
    }

    public void selectAll() {
        selectedObjects.clear();
        selectedObjects.addAll(scene.getModels());
    }

    public void clearSelection() {
        selectedObjects.clear();
    }
}
