package com.jailblack.scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Transform {
    public Vector3f position = new Vector3f(0, 0, 0);
    public Vector3f rotation = new Vector3f(0, 0, 0);
    public Vector3f scale = new Vector3f(1, 1, 1);

    public Matrix4f getModelMatrix() {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(position);
        matrix.rotate((float) Math.toRadians(rotation.x), new Vector3f(1, 0, 0));
        matrix.rotate((float) Math.toRadians(rotation.y), new Vector3f(0, 1, 0));
        matrix.rotate((float) Math.toRadians(rotation.z), new Vector3f(0, 0, 1));
        matrix.scale(scale);
        return matrix;
    }
}
