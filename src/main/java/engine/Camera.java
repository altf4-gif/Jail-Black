package engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private final Vector3f position;
    private float yaw = -90.0f;
    private float pitch = -10.0f;
    private final Vector3f up = new Vector3f(0f, 1f, 0f);

    public Camera() {
        position = new Vector3f(0f, 3f, 8f);
    }

    public void rotate(float deltaX, float deltaY) {
        float sensitivity = 0.1f;
        yaw += deltaX * sensitivity;
        pitch -= deltaY * sensitivity;
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
    }

    public Vector3f getFront() {
        Vector3f front = new Vector3f();
        front.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        return front.normalize();
    }

    public Vector3f getPosition() { return new Vector3f(position); }
    public void setPosition(Vector3f pos) { position.set(pos); }
    public Vector3f getUp() { return new Vector3f(up); }

    public Matrix4f getViewMatrix() {
        Vector3f front = getFront();
        return new Matrix4f().lookAt(position, new Vector3f(position).add(front), up);
    }

    public Matrix4f getProjectionMatrix(float aspect) {
        return new Matrix4f().perspective((float) Math.toRadians(60.0), aspect, 0.1f, 500.0f);
    }

    public Matrix4f getViewProjection(float aspect) {
        return getProjectionMatrix(aspect).mul(getViewMatrix());
    }
}
