package com.jailblack.scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Transform {
    public Vector3f position = new Vector3f(0, 0, 0);
    public Vector3f rotation = new Vector3f(0, 0, 0);
    public Vector3f scale = new Vector3f(1, 1, 1);

    public enum RotationOrder { XYZ, XZY, YXZ, YZX, ZXY, ZYX }
    private RotationOrder rotationOrder = RotationOrder.YXZ;

    public Matrix4f getModelMatrix() {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(position);
        float rx = (float) Math.toRadians(rotation.x);
        float ry = (float) Math.toRadians(rotation.y);
        float rz = (float) Math.toRadians(rotation.z);
        switch (rotationOrder) {
            case XYZ -> { matrix.rotateX(rx); matrix.rotateY(ry); matrix.rotateZ(rz); }
            case XZY -> { matrix.rotateX(rx); matrix.rotateZ(rz); matrix.rotateY(ry); }
            case YXZ -> { matrix.rotateY(ry); matrix.rotateX(rx); matrix.rotateZ(rz); }
            case YZX -> { matrix.rotateY(ry); matrix.rotateZ(rz); matrix.rotateX(rx); }
            case ZXY -> { matrix.rotateZ(rz); matrix.rotateX(rx); matrix.rotateY(ry); }
            case ZYX -> { matrix.rotateZ(rz); matrix.rotateY(ry); matrix.rotateX(rx); }
        }
        matrix.scale(scale);
        return matrix;
    }

    public Transform copy() {
        Transform t = new Transform();
        t.position.set(position);
        t.rotation.set(rotation);
        t.scale.set(scale);
        t.rotationOrder = rotationOrder;
        return t;
    }

    public void set(Transform other) {
        position.set(other.position);
        rotation.set(other.rotation);
        scale.set(other.scale);
        rotationOrder = other.rotationOrder;
    }

    public RotationOrder getRotationOrder() { return rotationOrder; }
    public void setRotationOrder(RotationOrder order) { this.rotationOrder = order; }
}
