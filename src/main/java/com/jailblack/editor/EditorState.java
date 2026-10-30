package com.jailblack.editor;

import com.jailblack.engine.*;
import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class EditorState {
    public static final int TOOL_SELECT = 0;
    public static final int TOOL_BLOCK = 1;
    public static final int TOOL_MOVE = 2;
    public static final int TOOL_ROTATE = 3;
    public static final int TOOL_SCALE = 4;

    private final Window window;
    private final Camera camera;
    private final ShaderProgram basicShader;
    private final ShaderProgram outlineShader;
    private final ShaderProgram gridShader;
    private final ShaderProgram ghostShader;
    private final Mesh boxMesh;
    private final Mesh gridMesh;
    private final Mesh cubeOutlineMesh;
    private final InstancedRenderer instancedRenderer;
    private Framebuffer sceneFramebuffer;

    private final Scene scene = new Scene("Main Scene");
    private final List<VoxelModel> selectedObjects = new ArrayList<>();
    private final CommandHistory commandHistory = new CommandHistory();
    private final SelectionManager selectionManager;
    private final SpawnManager spawnManager;
    private final MapManager mapManager;

    private final Vector3f currentColor = new Vector3f(0.85f, 0.35f, 0.32f);
    private float currentAlpha = 1.0f;
    private final ImBoolean paletteOpen = new ImBoolean(true);
    private final Vector3f[] palette = {
        new Vector3f(0.85f, 0.35f, 0.32f),
        new Vector3f(0.35f, 0.78f, 0.45f),
        new Vector3f(0.35f, 0.55f, 0.90f),
        new Vector3f(0.9f, 0.9f, 0.9f),
        new Vector3f(0.2f, 0.2f, 0.2f),
        new Vector3f(0.9f, 0.8f, 0.2f),
        new Vector3f(0.8f, 0.4f, 0.8f),
        new Vector3f(0.2f, 0.8f, 0.8f)
    };

    private int currentTool = TOOL_BLOCK;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public static boolean viewportHovered = false;
    private boolean leftMouseDownInViewport = false;
    private boolean middleMouseDownInViewport = false;

    public EditorState(Window window, Camera camera,
                       ShaderProgram basicShader, ShaderProgram outlineShader,
                       ShaderProgram gridShader, ShaderProgram ghostShader,
                       Mesh boxMesh, Mesh gridMesh, Mesh cubeOutlineMesh,
                       InstancedRenderer instancedRenderer, Framebuffer sceneFramebuffer) {
        this.window = window;
        this.camera = camera;
        this.basicShader = basicShader;
        this.outlineShader = outlineShader;
        this.gridShader = gridShader;
        this.ghostShader = ghostShader;
        this.boxMesh = boxMesh;
        this.gridMesh = gridMesh;
        this.cubeOutlineMesh = cubeOutlineMesh;
        this.instancedRenderer = instancedRenderer;
        this.sceneFramebuffer = sceneFramebuffer;

        this.selectionManager = new SelectionManager(scene, selectedObjects, camera, window);
        this.spawnManager = new SpawnManager(scene, commandHistory, currentColor, currentAlpha);
        this.mapManager = new MapManager(scene, commandHistory);

        setupInputCallbacks();
    }

    private void setupInputCallbacks() {
        GLFW.glfwSetCursorPosCallback(window.getHandle(), (win, xpos, ypos) -> {
            if (Double.isNaN(lastMouseX)) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                return;
            }
            float dx = (float) (xpos - lastMouseX);
            float dy = (float) (ypos - lastMouseY);
            lastMouseX = xpos;
            lastMouseY = ypos;

            boolean lmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean mmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

            if (lmb && leftMouseDownInViewport) {
                camera.rotate(dx, dy);
            } else if (mmb && middleMouseDownInViewport) {
                camera.pan(dx, dy);
            }
        });

        GLFW.glfwSetMouseButtonCallback(window.getHandle(), (win, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                leftMouseDownInViewport = viewportHovered;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_RELEASE) {
                leftMouseDownInViewport = false;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_PRESS) {
                middleMouseDownInViewport = viewportHovered;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_RELEASE) {
                middleMouseDownInViewport = false;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS) {
                if (viewportHovered) {
                    boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
                    if (currentTool == TOOL_BLOCK && !shift) {
                        handleSpawn();
                    } else {
                        handlePick(shift);
                    }
                }
            }
        });
    }

    public void updateAndRender(float dt) {
        handleInput(dt);
        renderScene();
        renderUI();
    }

    private void handleInput(float dt) {
        if (ImGui.getIO().getWantCaptureKeyboard()) return;

        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS) currentTool = TOOL_SELECT;
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_B) == GLFW.GLFW_PRESS) currentTool = TOOL_BLOCK;

        float vel = 10.0f * dt;
        Vector3f front = camera.getFront();
        Vector3f right = camera.getRight();
        Vector3f camPos = camera.getPosition();

        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS)
            camPos.add(new Vector3f(front).mul(vel));
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS)
            camPos.sub(new Vector3f(front).mul(vel));
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS)
            camPos.add(new Vector3f(right).mul(vel));
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS)
            camPos.sub(new Vector3f(right).mul(vel));
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS)
            camPos.y += vel;
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS)
            camPos.y -= vel;

        camera.setPosition(camPos);

        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS &&
            GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) {
            if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
                commandHistory.redo();
            } else {
                commandHistory.undo();
            }
        }

        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS) {
            if (!selectedObjects.isEmpty()) {
                commandHistory.execute(new Commands.DeleteCommand(scene, selectedObjects));
                selectedObjects.clear();
            }
        }
    }

    private void handleSpawn() {
        spawnManager.spawnInFront(camera, currentColor, currentAlpha);
        if (!scene.getModels().isEmpty()) {
            selectedObjects.clear();
            selectedObjects.add(scene.getModels().get(scene.getModels().size() - 1));
        }
    }

    private void handlePick(boolean toggle) {
        VoxelModel picked = selectionManager.pick();
        if (picked != null) {
            if (toggle) {
                if (selectedObjects.contains(picked)) selectedObjects.remove(picked);
                else selectedObjects.add(picked);
            } else {
                selectedObjects.clear();
                selectedObjects.add(picked);
            }
        } else if (!toggle) {
            selectedObjects.clear();
        }
    }

    private void applyColor(float r, float g, float b, float a) {
        currentColor.set(r, g, b);
        currentAlpha = a;
        spawnManager.setCurrentColor(currentColor);
        spawnManager.setCurrentAlpha(currentAlpha);
    }

    public static String toHex(Vector3f c, float a) {
        return String.format("#%02x%02x%02x%02x",
                (int) (c.x * 255), (int) (c.y * 255), (int) (c.z * 255), (int) (a * 255));
    }

    private void recolorSelected() {
        if (selectedObjects.isEmpty()) return;
        String[][] old = new String[selectedObjects.size()][];
        for (int i = 0; i < selectedObjects.size(); i++) {
            List<Voxel> vs = selectedObjects.get(i).voxels;
            old[i] = new String[vs.size()];
            for (int j = 0; j < vs.size(); j++) old[i][j] = vs.get(j).color;
        }
        commandHistory.execute(new Commands.ColorCommand(selectedObjects, old, toHex(currentColor, currentAlpha)));
    }

    private void renderScene() {
        int w = window.getWidth();
        int h = window.getHeight();
        if (w <= 0 || h <= 0) return;

        if (sceneFramebuffer.getWidth() != w || sceneFramebuffer.getHeight() != h) {
            sceneFramebuffer.cleanup();
            sceneFramebuffer = new Framebuffer(w, h);
        }

        sceneFramebuffer.bind();
        glClearColor(0.45f, 0.55f, 0.8f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        Matrix4f viewProj = camera.getViewProjection(window.getAspect());

        gridShader.use();
        gridShader.setUniform("uViewProj", viewProj);
        gridMesh.draw();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        basicShader.use();
        basicShader.setUniform("uViewProj", viewProj);
        basicShader.setUniform("uInstanced", 1);
        basicShader.setUniform("uMode", 0);
        basicShader.setUniform("uUseTexture", 0);
        basicShader.setUniform("uAlpha", 1.0f);
        basicShader.setUniform("uLightDir", new Vector3f(0.5f, 1.0f, 0.3f).normalize());
        basicShader.setUniform("uCamPos", camera.getPosition());
        instancedRenderer.render(scene.getModels());

        glDisable(GL_BLEND);

        if (!selectedObjects.isEmpty()) {
            outlineShader.use();
            outlineShader.setUniform("uViewProj", viewProj);
            for (VoxelModel obj : selectedObjects) {
                Matrix4f modelMatrix = obj.transform.getModelMatrix();
                for (Voxel v : obj.voxels) {
                    Matrix4f voxelMatrix = new Matrix4f(modelMatrix).translate(v.x, v.y, v.z).scale(1.05f);
                    outlineShader.setUniform("uModel", voxelMatrix);
                    cubeOutlineMesh.draw();
                }
            }
        }

        sceneFramebuffer.unbind();
        glViewport(0, 0, w, h);
    }

    private void renderUI() {
        renderToolbar();
        renderExplorer();
        renderProperties();
        renderPalette();
        renderViewport();
    }

    private void renderToolbar() {
        ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
        ImGui.setNextWindowSize(window.getWidth(), 50, ImGuiCond.Always);
        ImGui.begin("Toolbar", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBackground);

        if (ImGui.button("Select (Q)")) currentTool = TOOL_SELECT; ImGui.sameLine();
        if (ImGui.button("Block (B)")) currentTool = TOOL_BLOCK; ImGui.sameLine();
        if (ImGui.button("Move")) currentTool = TOOL_MOVE; ImGui.sameLine();
        if (ImGui.button("Rotate")) currentTool = TOOL_ROTATE; ImGui.sameLine();
        if (ImGui.button("Scale")) currentTool = TOOL_SCALE; ImGui.sameLine();

        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        if (ImGui.button("Undo")) commandHistory.undo(); ImGui.sameLine();
        if (ImGui.button("Redo")) commandHistory.redo(); ImGui.sameLine();

        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        if (ImGui.button("Save")) mapManager.save("my_map"); ImGui.sameLine();
        if (ImGui.button("Load")) mapManager.load("my_map"); ImGui.sameLine();
        if (ImGui.button("Clear")) {
            commandHistory.execute(new Commands.DeleteCommand(scene, new ArrayList<>(scene.getModels())));
            selectedObjects.clear();
        }
        ImGui.sameLine();

        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        ImGui.text("Color:"); ImGui.sameLine();
        float[] colorRGB = {currentColor.x, currentColor.y, currentColor.z, currentAlpha};
        if (ImGui.colorEdit4("##ToolbarColor", colorRGB,
                ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoLabel |
                ImGuiColorEditFlags.AlphaPreview | ImGuiColorEditFlags.AlphaBar)) {
            applyColor(colorRGB[0], colorRGB[1], colorRGB[2], colorRGB[3]);
        }
        ImGui.sameLine();

        if (ImGui.button(paletteOpen.get() ? "[-] Palette" : "[+] Palette")) {
            paletteOpen.set(!paletteOpen.get());
        }
        ImGui.sameLine();

        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();
        ImGui.text("Tool: " + toolName(currentTool) + "  |  Objects: " + scene.getModels().size());

        ImGui.end();
    }

    private String toolName(int tool) {
        switch (tool) {
            case TOOL_SELECT: return "Select";
            case TOOL_BLOCK: return "Block (spawn)";
            case TOOL_MOVE: return "Move";
            case TOOL_ROTATE: return "Rotate";
            case TOOL_SCALE: return "Scale";
            default: return "?";
        }
    }

    private void renderExplorer() {
        ImGui.setNextWindowPos(0, 50, ImGuiCond.Once);
        ImGui.setNextWindowSize(250, window.getHeight() - 50, ImGuiCond.Once);
        ImGui.begin("Explorer");

        ImGui.text("Scene: " + scene.getName());
        ImGui.separator();

        for (VoxelModel obj : scene.getModels()) {
            boolean isSelected = selectedObjects.contains(obj);
            if (ImGui.selectable(obj.name + "##" + obj.id.substring(0, 5), isSelected)) {
                selectedObjects.clear();
                selectedObjects.add(obj);
            }
        }

        ImGui.end();
    }

    private void renderProperties() {
        if (selectedObjects.isEmpty()) return;

        VoxelModel mainObj = selectedObjects.get(0);
        ImGui.setNextWindowPos(window.getWidth() - 250, 50, ImGuiCond.Once);
        ImGui.setNextWindowSize(250, window.getHeight() - 50, ImGuiCond.Once);
        ImGui.begin("Properties");

        ImGui.text("Selected: " + selectedObjects.size());
        ImGui.separator();

        float[] pos = {mainObj.transform.position.x, mainObj.transform.position.y, mainObj.transform.position.z};
        if (ImGui.dragFloat3("Position", pos, 0.1f)) {
            mainObj.transform.position.set(pos[0], pos[1], pos[2]);
        }

        float[] rot = {mainObj.transform.rotation.x, mainObj.transform.rotation.y, mainObj.transform.rotation.z};
        if (ImGui.dragFloat3("Rotation", rot, 1.0f)) {
            mainObj.transform.rotation.set(rot[0], rot[1], rot[2]);
        }

        float[] scl = {mainObj.transform.scale.x, mainObj.transform.scale.y, mainObj.transform.scale.z};
        if (ImGui.dragFloat3("Scale", scl, 0.1f)) {
            mainObj.transform.scale.set(scl[0], scl[1], scl[2]);
        }

        ImGui.separator();
        if (ImGui.button("Apply color to selected")) {
            recolorSelected();
        }

        ImGui.end();
    }

    private void renderPalette() {
        if (!paletteOpen.get()) return;

        ImGui.setNextWindowPos(480, 120, ImGuiCond.Once);
        ImGui.setNextWindowSize(460, 560, ImGuiCond.Once);
        if (ImGui.begin("Palette", paletteOpen, 0)) {

            float[] rgba = {currentColor.x, currentColor.y, currentColor.z, currentAlpha};
            if (ImGui.colorPicker4("##PalettePicker", rgba,
                    ImGuiColorEditFlags.AlphaBar |
                    ImGuiColorEditFlags.AlphaPreviewHalf |
                    ImGuiColorEditFlags.DisplayHex |
                    ImGuiColorEditFlags.DisplayRGB |
                    ImGuiColorEditFlags.DisplayHSV |
                    ImGuiColorEditFlags.PickerHueBar |
                    ImGuiColorEditFlags.NoSidePreview |
                    ImGuiColorEditFlags.NoLabel)) {
                applyColor(rgba[0], rgba[1], rgba[2], rgba[3]);
            }

            ImGui.separator();
            ImGui.text("HEX: " + toHex(currentColor, currentAlpha));
            ImGui.sameLine();
            if (ImGui.smallButton("Copy")) {
                GLFW.glfwSetClipboardString(window.getHandle(), toHex(currentColor, currentAlpha));
            }

            int[] rgb = {
                Math.round(currentColor.x * 255),
                Math.round(currentColor.y * 255),
                Math.round(currentColor.z * 255)
            };
            if (ImGui.sliderInt3("RGB", rgb, 0, 255)) {
                applyColor(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f, currentAlpha);
            }

            float[] a = {currentAlpha};
            if (ImGui.sliderFloat("Alpha", a, 0.0f, 1.0f, "%.2f")) {
                applyColor(currentColor.x, currentColor.y, currentColor.z, a[0]);
            }

            ImGui.separator();
            ImGui.text("Quick palette:");
            for (int i = 0; i < palette.length; i++) {
                float[] c = {palette[i].x, palette[i].y, palette[i].z, 1.0f};
                if (ImGui.colorButton("##col" + i, c, 0, 24, 24)) {
                    applyColor(palette[i].x, palette[i].y, palette[i].z, 1.0f);
                }
                if (i % 4 != 3) ImGui.sameLine();
            }

            ImGui.separator();
            if (ImGui.button("Apply to selected")) {
                recolorSelected();
            }
        }
        ImGui.end();
    }

    private void renderViewport() {
        ImGui.setNextWindowPos(250, 50, ImGuiCond.Once);
        ImGui.setNextWindowSize(window.getWidth() - 500, window.getHeight() - 50, ImGuiCond.Once);
        ImGui.begin("Viewport", ImGuiWindowFlags.NoScrollbar);

        ImGui.text("LMB+drag = Orbit | RMB = Block spawn / pick | Shift+RMB = select | MMB = Pan | Wheel = Zoom");

        float availW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();

        if (availW > 0 && availH > 0) {
            ImGui.beginChild("ViewportCanvas", availW, availH, false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

            ImGui.image(sceneFramebuffer.getTextureId(), availW, availH, 0, 1, 1, 0);

            viewportHovered = ImGui.isWindowHovered();

            ImGui.endChild();
        } else {
            viewportHovered = false;
        }

        ImGui.end();
    }
}
// === END OF FILE ===
