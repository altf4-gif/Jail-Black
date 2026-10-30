package com.jailblack;

import com.jailblack.engine.*;
import com.jailblack.editor.EditorState;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.opengl.GL33.*;

public class Main {

    public static void main(String[] args) {
        Window window = new Window(1600, 900, "Jail-Black Studio");
        window.create();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

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
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);

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

        double lastTime = GLFW.glfwGetTime();

        while (!window.shouldClose()) {
            double currentTime = GLFW.glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;

            glClearColor(0.12f, 0.12f, 0.14f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            imGuiGlfw.newFrame();
            ImGui.newFrame();

            // DockSpace под тулбаром (y=50, высота = окно - 50)
            ImGui.setNextWindowPos(0, 50, ImGuiCond.Always);
            ImGui.setNextWindowSize(io.getDisplaySizeX(), io.getDisplaySizeY() - 50, ImGuiCond.Always);
            ImGui.begin("##MainDockSpaceWindow",
                    ImGuiWindowFlags.NoTitleBar |
                    ImGuiWindowFlags.NoCollapse |
                    ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoMove |
                    ImGuiWindowFlags.NoBringToFrontOnFocus |
                    ImGuiWindowFlags.NoNavFocus |
                    ImGuiWindowFlags.NoBackground);
            ImGui.dockSpace(ImGui.getID("EditorDockSpace"), 0, 0, ImGuiDockNodeFlags.PassthruCentralNode);
            ImGui.end();

            editor.updateAndRender(dt);

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

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
// === END OF FILE ===
