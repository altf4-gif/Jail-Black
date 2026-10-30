package com.jailblack.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    public enum Mode { FLY, ORBIT }

    private final Vector3f position = new Vector3f(0f, 5f, 10f);
    private final Vector3f orbitTarget = new Vector3f(0f, 0f, 0f);
    private float orbitDistance = 10f;
    private float yaw = -90.0f;
    private float pitch = -20.0f;
    private final Vector3f up = new Vector3f(0f, 1f, 0f);
    private Mode mode = Mode.FLY;

    public Camera() {
        updateFlyPosition();
    }

    public void setMode(Mode mode) {
        if (this.mode == mode) return;
        if (mode == Mode.ORBIT) {
            Vector3f front = getFront();
            orbitTarget.set(position).add(new Vector3f(front).mul(orbitDistance));
            orbitDistance = 10f;
        }
        this.mode = mode;
    }

    public Mode getMode() { return mode; }

    public void rotate(float deltaX, float deltaY) {
        float sensitivity = 0.15f;
        yaw += deltaX * sensitivity;
        pitch -= deltaY * sensitivity;
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        if (mode == Mode.ORBIT) {
            updateOrbitPosition();
        }
    }

    public void pan(float deltaX, float deltaY) {
        float sensitivity = 0.02f;
        Vector3f front = getFront();
        Vector3f right = new Vector3f(front).cross(up).normalize();
        Vector3f camUp = new Vector3f(right).cross(front).normalize();

        Vector3f delta = new Vector3f(right).mul(-deltaX * sensitivity)
                .add(new Vector3f(camUp).mul(deltaY * sensitivity));

        position.add(delta);
        orbitTarget.add(delta);
    }

    public void zoom(float delta) {
        if (mode == Mode.ORBIT) {
            orbitDistance *= (float) Math.exp(delta * 0.1f);
            orbitDistance = Math.max(0.5f, Math.min(500f, orbitDistance));
            updateOrbitPosition();
        } else {
            Vector3f front = getFront();
            position.add(new Vector3f(front).mul(delta * 0.5f));
        }
    }

    private void updateOrbitPosition() {
        float x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        float y = (float) Math.sin(Math.toRadians(pitch));
        float z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        position.set(orbitTarget).sub(x * orbitDistance, y * orbitDistance, z * orbitDistance);
    }

    private void updateFlyPosition() {
    }

    public void focus(Vector3f target, float distance) {
        orbitTarget.set(target);
        orbitDistance = distance;
        setMode(Mode.ORBIT);
        updateOrbitPosition();
    }

    public Vector3f getFront() {
        Vector3f front = new Vector3f();
        front.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        return front.normalize();
    }

    public Vector3f getRight() {
        return new Vector3f(getFront()).cross(up).normalize();
    }

    public Vector3f getUp() {
        return new Vector3f(getRight()).cross(getFront()).normalize();
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public void setPosition(Vector3f pos) {
        position.set(pos);
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, new Vector3f(position).add(getFront()), up);
    }

    public Matrix4f getProjectionMatrix(float aspect) {
        return new Matrix4f().perspective((float) Math.toRadians(60.0f), aspect, 0.1f, 1000.0f);
    }

    public Matrix4f getViewProjection(float aspect) {
        return new Matrix4f(getProjectionMatrix(aspect)).mul(getViewMatrix());
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
}
