package com.jailblack;

import com.jailblack.engine.*;
import com.jailblack.editor.EditorState;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.opengl.GL33.*;

public class Main {

    public static void main(String[] args) {
        Window window = new Window(1600, 900, "Jail-Black Studio");
        window.create();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_MULTISAMPLE);

        ShaderProgram basicShader = ShaderProgram.fromResources("shaders/basic.vert", "shaders/basic.frag");
        ShaderProgram outlineShader = ShaderProgram.fromResources("shaders/outline.vert", "shaders/outline.frag");
        ShaderProgram gridShader = ShaderProgram.fromResources("shaders/grid.vert", "shaders/grid.frag");
        ShaderProgram ghostShader = ShaderProgram.fromResources("shaders/ghost.vert", "shaders/ghost.frag");
        ShaderProgram postShader = ShaderProgram.fromResources("shaders/post.vert", "shaders/post.frag");

        Camera camera = new Camera();

        Mesh boxMesh = MeshFactory.box(1, 1, 1);
        Mesh gridMesh = MeshFactory.grid(100.0f, 100);
        Mesh cubeOutlineMesh = MeshFactory.cubeOutline();

        InstancedRenderer instancedRenderer = new InstancedRenderer(boxMesh, 100_000);

        Framebuffer sceneFramebuffer = new Framebuffer(window.getWidth(), window.getHeight());

        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.getFonts().addFontDefault();
        io.addConfigFlags(imgui.flag.ImGuiConfigFlags.DockingEnable);
        io.addConfigFlags(imgui.flag.ImGuiConfigFlags.ViewportsEnable);

        ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
        ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
        imGuiGlfw.init(window.getHandle(), true);
        imGuiGl3.init("#version 330 core");

        EditorState editor = new EditorState(window, camera,
                basicShader, outlineShader, gridShader, ghostShader,
                boxMesh, gridMesh, cubeOutlineMesh,
                instancedRenderer, sceneFramebuffer);

        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);

        GLFW.glfwSetScrollCallback(window.getHandle(), (win, xoffset, yoffset) -> {
            if (!ImGui.getIO().getWantCaptureMouse()) {
                camera.zoom((float) yoffset);
            }
        });

        GLFW.glfwSetFramebufferSizeCallback(window.getHandle(), (win, w, h) -> {
            if (w > 0 && h > 0) {
                sceneFramebuffer.cleanup();
                editor.setSceneFramebuffer(new Framebuffer(w, h));
            }
        });

        double lastTime = GLFW.glfwGetTime();

        while (!window.shouldClose()) {
            double currentTime = GLFW.glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;

            glClearColor(0.1f, 0.1f, 0.1f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            imGuiGlfw.newFrame();
            ImGui.newFrame();

            editor.updateAndRender(dt);

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            if (io.hasConfigFlags(imgui.flag.ImGuiConfigFlags.ViewportsEnable)) {
                ImGui.updatePlatformWindows();
                ImGui.renderPlatformWindowsDefault();
            }

            window.swapAndPoll();
        }

        instancedRenderer.cleanup();
        sceneFramebuffer.cleanup();
        boxMesh.cleanup();
        gridMesh.cleanup();
        cubeOutlineMesh.cleanup();
        basicShader.cleanup();
        outlineShader.cleanup();
        gridShader.cleanup();
        ghostShader.cleanup();
        postShader.cleanup();

        imGuiGl3.dispose();
        imGuiGlfw.dispose();
        ImGui.destroyContext();

        window.destroy();
    }
}
