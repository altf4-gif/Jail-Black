package engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private float yaw = 0.55f;
    private float pitch = 0.45f;
    private float distance = 11.0f;
    private final Vector3f target = new Vector3f(0f, 1.6f, 0f);
    private final Vector3f up = new Vector3f(0f, 1f, 0f);
    private static final float MIN_PITCH = -1.45f;
    private static final float MAX_PITCH = 1.45f;
    private static final float MIN_DISTANCE = 3.0f;
    private static final float MAX_DISTANCE = 45.0f;

    public void rotate(float deltaX, float deltaY) {
        yaw += deltaX * 0.005f;
        pitch += deltaY * 0.005f;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    public void zoom(float amount) {
        distance -= amount;
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }

    public Matrix4f viewMatrix() {
        Vector3f offset = new Vector3f(
                (float) (Math.cos(pitch) * Math.sin(yaw)),
                (float) Math.sin(pitch),
                (float) (Math.cos(pitch) * Math.cos(yaw))
        ).mul(distance);
        Vector3f position = new Vector3f(target).add(offset);
        return new Matrix4f().lookAt(position, target, up);
    }

    public Matrix4f viewProjection(float aspect) {
        Matrix4f projection = new Matrix4f()
                .perspective((float) Math.toRadians(60.0), aspect, 0.1f, 300.0f);
        return projection.mul(viewMatrix());
    }
}
