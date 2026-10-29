package game;

import engine.Camera;
import engine.Mesh;
import engine.MeshFactory;
import engine.ShaderProgram;
import engine.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.List;

public final class Main {

    private static final class SceneObject {
        final Mesh mesh;
        final Matrix4f model;
        final Vector3f color;

        SceneObject(Mesh mesh, Matrix4f model, Vector3f color) {
            this.mesh = mesh;
            this.model = model;
            this.color = color;
        }
    }

    private Main() {
    }

    public static void main(String[] args) {
        Window window = new Window(1280, 720, "Java 3D Engine - LWJGL");
        window.create();

        GL33.glEnable(GL33.GL_DEPTH_TEST);
        GL33.glEnable(GL33.GL_CULL_FACE);
        GL33.glCullFace(GL33.GL_BACK);
        GL33.glClearColor(0.08f, 0.10f, 0.13f, 1.0f);

        ShaderProgram shader = ShaderProgram.fromResources(
                "shaders/basic.vert",
                "shaders/basic.frag"
        );

        Camera camera = new Camera();
        List<SceneObject> scene = buildScene();

        GLFW.glfwSetScrollCallback(window.getHandle(),
                (win, xOffset, yOffset) -> camera.zoom((float) yOffset * 0.7f));

        double[] mouseX = new double[1];
        double[] mouseY = new double[1];
        double lastX = 0.0;
        double lastY = 0.0;
        boolean dragging = false;

        while (!window.shouldClose()) {
            GLFW.glfwGetCursorPos(window.getHandle(), mouseX, mouseY);

            boolean leftButtonDown = GLFW.glfwGetMouseButton(
                    window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (leftButtonDown && dragging) {
                camera.rotate((float) (mouseX[0] - lastX), (float) (mouseY[0] - lastY));
            }
            dragging = leftButtonDown;
            lastX = mouseX[0];
            lastY = mouseY[0];

            GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);

            shader.use();
            shader.setUniform("uViewProj", camera.viewProjection(window.getAspect()));

            for (SceneObject object : scene) {
                shader.setUniform("uModel", object.model);
                shader.setUniform("uColor", object.color);
                object.mesh.draw();
            }

            window.swapAndPoll();
        }

        for (SceneObject object : scene) {
            object.mesh.cleanup();
        }
        shader.cleanup();
        window.destroy();
    }

    private static List<SceneObject> buildScene() {
        List<SceneObject> scene = new ArrayList<>();

        scene.add(new SceneObject(
                MeshFactory.box(12f, 0.4f, 12f),
                new Matrix4f().translation(0f, -0.2f, 0f),
                new Vector3f(0.30f, 0.33f, 0.38f)
        ));

        float[] pillarX = {-2.5f, 0f, 2.5f};
        float[] pillarHeight = {1.6f, 2.8f, 2.1f};
        float pillarRadius = 0.4f;
        Vector3f pillarColor = new Vector3f(0.58f, 0.61f, 0.66f);

        for (int i = 0; i < pillarX.length; i++) {
            scene.add(new SceneObject(
                    MeshFactory.cylinder(pillarRadius, pillarHeight[i], 32),
                    new Matrix4f().translation(pillarX[i], 0f, 0f),
                    pillarColor
            ));
        }

        float cubeSize = 0.9f;
        scene.add(new SceneObject(
                MeshFactory.box(cubeSize, cubeSize, cubeSize),
                new Matrix4f().translation(pillarX[0], pillarHeight[0] + cubeSize / 2f, 0f),
                new Vector3f(0.85f, 0.35f, 0.32f)
        ));

        float tetraSize = 1.0f;
        scene.add(new SceneObject(
                MeshFactory.tetrahedron(tetraSize),
                new Matrix4f().translation(pillarX[1], pillarHeight[1] + tetraSize / 2f, 0f),
                new Vector3f(0.35f, 0.78f, 0.45f)
        ));

        float sphereRadius = 0.5f;
        scene.add(new SceneObject(
                MeshFactory.sphere(sphereRadius, 32, 48),
                new Matrix4f().translation(pillarX[2], pillarHeight[2] + sphereRadius, 0f),
                new Vector3f(0.35f, 0.55f, 0.90f)
        ));

        return scene;
    }
}
