package com.jailblack.editor;

import com.jailblack.engine.*;
import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

public class EditorState {

    public enum EditorMode { MODEL_EDITOR, MAP_EDITOR }
    public enum EditMode { OBJECT_MODE, EDIT_MODE }
    public enum Tool { SELECT, ADD_VOXEL, REMOVE_VOXEL, PAINT, EYEDROPPER, FILL }

    private static final float MAIN_MENU_HEIGHT = 24.0f;
    private static final float TOOLBAR_HEIGHT = 40.0f;
    private static final float TOP_OFFSET = MAIN_MENU_HEIGHT + TOOLBAR_HEIGHT;

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

    private final EditorUiState editorUi = new EditorUiState();

    private final Scene scene = new Scene("Main Scene");
    private final List<VoxelModel> selectedObjects = new ArrayList<>();
    private final CommandHistory commandHistory = new CommandHistory();
    private final SelectionManager selectionManager;
    private final SpawnManager spawnManager;
    private final MapManager mapManager;

    private EditorMode editorMode = EditorMode.MODEL_EDITOR;
    private EditMode editMode = EditMode.EDIT_MODE;
    private Tool currentTool = Tool.ADD_VOXEL;
    private final Vector3f currentColor = new Vector3f(0.8f, 0.2f, 0.2f);

    private final int[] brushSize = {1};
    private boolean mirrorX = false;

    private final Vector3f ghostPosition = new Vector3f();
    private boolean showGhost = false;

    private boolean showNewModelDialog = false;
    private final ImString newModelName = new ImString("NewModel", 64);
    private final int[] newModelSize = {8, 8, 32};
    private final ImString exportFilename = new ImString("model", 64);

    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    private int hoveredVoxelX = -1, hoveredVoxelY = -1, hoveredVoxelZ = -1;
    private VoxelModel hoveredModel = null;

    private String statusMessage = "Ready";
    private float fps = 0;
    private float frameTimeAccum = 0;
    private int frameCount = 0;

    private int viewportW = 1280;
    private int viewportH = 720;

    private final Set<Integer> heldKeys = new HashSet<>();
    private boolean leftClickHandled = false;

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
        this.spawnManager = new SpawnManager(scene, commandHistory, currentColor);
        this.mapManager = new MapManager(scene, commandHistory);

        ModelExportManager.ensureModelsDirectory();
        setupInputCallbacks();
    }

    public void setSceneFramebuffer(Framebuffer fb) {
        this.sceneFramebuffer = fb;
    }

    // ================= INPUT =================

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

            if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) {
                camera.rotate(dx, dy);
            } else if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS) {
                camera.pan(dx, dy);
            }
        });
    }

    private boolean pressedOnce(int key) {
        boolean down = GLFW.glfwGetKey(window.getHandle(), key) == GLFW.GLFW_PRESS;
        if (down && !heldKeys.contains(key)) {
            heldKeys.add(key);
            return true;
        }
        if (!down) heldKeys.remove(key);
        return false;
    }

    private void handleInput(float dt) {
        boolean uiWantsKeyboard = ImGui.getIO().getWantCaptureKeyboard();

        if (uiWantsKeyboard) {
            heldKeys.clear();
        } else {
            if (pressedOnce(GLFW.GLFW_KEY_Q)) { currentTool = Tool.SELECT; editMode = EditMode.OBJECT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_B)) { currentTool = Tool.ADD_VOXEL; editMode = EditMode.EDIT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_X)) { currentTool = Tool.REMOVE_VOXEL; editMode = EditMode.EDIT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_G)) { currentTool = Tool.PAINT; editMode = EditMode.EDIT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_I)) { currentTool = Tool.EYEDROPPER; editMode = EditMode.EDIT_MODE; }

            if (pressedOnce(GLFW.GLFW_KEY_LEFT_BRACKET)) brushSize[0] = Math.max(1, brushSize[0] - 1);
            if (pressedOnce(GLFW.GLFW_KEY_RIGHT_BRACKET)) brushSize[0] = Math.min(5, brushSize[0] + 1);

            if (pressedOnce(GLFW.GLFW_KEY_TAB)) {
                editMode = (editMode == EditMode.OBJECT_MODE) ? EditMode.EDIT_MODE : EditMode.OBJECT_MODE;
            }

            if (pressedOnce(GLFW.GLFW_KEY_F) && !selectedObjects.isEmpty()) {
                Vector3f target = selectedObjects.get(0).transform.position;
                camera.setPosition(new Vector3f(target.x - 10, target.y + 5, target.z));
            }

            boolean ctrl = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shift = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

            if (shift && pressedOnce(GLFW.GLFW_KEY_D) && !selectedObjects.isEmpty()) {
                spawnManager.duplicateModel(selectedObjects.get(0));
            }

            if (ctrl && pressedOnce(GLFW.GLFW_KEY_Z)) {
                if (shift) commandHistory.redo();
                else commandHistory.undo();
            }

            if (pressedOnce(GLFW.GLFW_KEY_DELETE) && !selectedObjects.isEmpty()) {
                commandHistory.execute(new Commands.DeleteCommand(scene, selectedObjects));
                selectedObjects.clear();
            }
        }

        float vel = 10.0f * dt;
        Vector3f front = camera.getFront();
        Vector3f right = camera.getRight();
        Vector3f camPos = camera.getPosition();

        boolean shiftKey = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        boolean dKey = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;

        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS)
            camPos.add(new Vector3f(front).mul(vel));
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS)
            camPos.sub(new Vector3f(front).mul(vel));
        if (dKey && !shiftKey)
            camPos.add(new Vector3f(right).mul(vel));
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS)
            camPos.sub(new Vector3f(right).mul(vel));
        if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS)
            camPos.y += vel;
        if (shiftKey && !dKey)
            camPos.y -= vel;

        camera.setPosition(camPos);
    }

    // ================= MOUSE =================

    private void handleMouseInput() {
        long win = window.getHandle();

        if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
            if (!ImGui.getIO().getWantCaptureMouse() || isMouseOverViewport()) {
                if (!leftClickHandled) {
                    handleViewportClick();
                    leftClickHandled = true;
                }
            }
        } else {
            leftClickHandled = false;
        }
    }

    private boolean isMouseOverViewport() {
        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), mx, my);

        int vpX = 250;
        int vpY = (int) TOP_OFFSET;
        int vpW = window.getWidth() - 500;
        int vpH = window.getHeight() - (int) TOP_OFFSET - 40;

        return mx[0] >= vpX && mx[0] <= vpX + vpW && my[0] >= vpY && my[0] <= vpY + vpH;
    }

    // ================= GHOST + DDA =================

    private void updateGhostVoxel() {
        showGhost = false;

        if (editMode != EditMode.EDIT_MODE) return;
        if (selectedObjects.isEmpty()) return;

        hoveredModel = selectedObjects.get(0);
        if (hoveredModel == null) return;

        Vector3f camPos = camera.getPosition();
        Vector3f camDir = camera.getFront();

        Vector3f modelPos = hoveredModel.transform.position;
        Vector3f localPos = new Vector3f(camPos).sub(modelPos);

        int[] hit = ddaRaycast(localPos, camDir,
                hoveredModel.sizeX, hoveredModel.sizeY, hoveredModel.sizeZ,
                hoveredModel);

        if (hit != null) {
            hoveredVoxelX = hit[0];
            hoveredVoxelY = hit[1];
            hoveredVoxelZ = hit[2];

            if (currentTool == Tool.ADD_VOXEL) {
                ghostPosition.set(hit[0] + hit[3], hit[1] + hit[4], hit[2] + hit[5]);
                ghostPosition.x = Math.max(0, Math.min(hoveredModel.sizeX - 1, ghostPosition.x));
                ghostPosition.y = Math.max(0, Math.min(hoveredModel.sizeY - 1, ghostPosition.y));
                ghostPosition.z = Math.max(0, Math.min(hoveredModel.sizeZ - 1, ghostPosition.z));
            } else {
                ghostPosition.set(hit[0], hit[1], hit[2]);
            }

            showGhost = true;
            statusMessage = "Target: [" + (int) ghostPosition.x + ", "
                    + (int) ghostPosition.y + ", " + (int) ghostPosition.z + "]";
        }
    }

    private int[] ddaRaycast(Vector3f origin, Vector3f dir,
                             int sizeX, int sizeY, int sizeZ,
                             VoxelModel model) {
        float tMaxX, tMaxY, tMaxZ;
        float tDeltaX, tDeltaY, tDeltaZ;
        int stepX, stepY, stepZ;

        int voxelX = (int) Math.floor(origin.x);
        int voxelY = (int) Math.floor(origin.y);
        int voxelZ = (int) Math.floor(origin.z);

        if (dir.x > 0) {
            stepX = 1; tDeltaX = 1.0f / dir.x;
            tMaxX = (voxelX + 1 - origin.x) / dir.x;
        } else if (dir.x < 0) {
            stepX = -1; tDeltaX = -1.0f / dir.x;
            tMaxX = (voxelX - origin.x) / dir.x;
        } else {
            stepX = 0; tDeltaX = Float.MAX_VALUE; tMaxX = Float.MAX_VALUE;
        }

        if (dir.y > 0) {
            stepY = 1; tDeltaY = 1.0f / dir.y;
            tMaxY = (voxelY + 1 - origin.y) / dir.y;
        } else if (dir.y < 0) {
            stepY = -1; tDeltaY = -1.0f / dir.y;
            tMaxY = (voxelY - origin.y) / dir.y;
        } else {
            stepY = 0; tDeltaY = Float.MAX_VALUE; tMaxY = Float.MAX_VALUE;
        }

        if (dir.z > 0) {
            stepZ = 1; tDeltaZ = 1.0f / dir.z;
            tMaxZ = (voxelZ + 1 - origin.z) / dir.z;
        } else if (dir.z < 0) {
            stepZ = -1; tDeltaZ = -1.0f / dir.z;
            tMaxZ = (voxelZ - origin.z) / dir.z;
        } else {
            stepZ = 0; tDeltaZ = Float.MAX_VALUE; tMaxZ = Float.MAX_VALUE;
        }

        int nx = 0, ny = 1, nz = 0;
        int[] firstEntry = null;

        for (int step = 0; step < 1024; step++) {
            boolean inside =
                    voxelX >= 0 && voxelX < sizeX &&
                    voxelY >= 0 && voxelY < sizeY &&
                    voxelZ >= 0 && voxelZ < sizeZ;

            if (inside) {
                if (firstEntry == null) {
                    firstEntry = new int[]{voxelX, voxelY, voxelZ, nx, ny, nz};
                }
                if (model != null && model.getVoxelAt(voxelX, voxelY, voxelZ) != null) {
                    return new int[]{voxelX, voxelY, voxelZ, nx, ny, nz};
                }
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    voxelX += stepX; tMaxX += tDeltaX;
                    nx = -stepX; ny = 0; nz = 0;
                } else {
                    voxelZ += stepZ; tMaxZ += tDeltaZ;
                    nx = 0; ny = 0; nz = -stepZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    voxelY += stepY; tMaxY += tDeltaY;
                    nx = 0; ny = -stepY; nz = 0;
                } else {
                    voxelZ += stepZ; tMaxZ += tDeltaZ;
                    nx = 0; ny = 0; nz = -stepZ;
                }
            }

            if (tMaxX > 1e9f && tMaxY > 1e9f && tMaxZ > 1e9f) break;
        }

        return firstEntry;
    }

    // ================= CLICKS =================

    private void handleViewportClick() {
        if (editMode == EditMode.OBJECT_MODE) {
            VoxelModel picked = selectionManager.pick();
            boolean shift = ImGui.getIO().getKeyShift();

            if (picked != null) {
                if (shift) {
                    if (selectedObjects.contains(picked)) selectedObjects.remove(picked);
                    else selectedObjects.add(picked);
                } else {
                    selectedObjects.clear();
                    selectedObjects.add(picked);
                }
                statusMessage = "Selected: " + picked.name;
            } else {
                if (!shift) selectedObjects.clear();
            }
        } else if (editMode == EditMode.EDIT_MODE) {
            handleVoxelEdit();
        }
    }

    private void handleVoxelEdit() {
        if (!showGhost || hoveredModel == null) {
            statusMessage = "No valid target";
            return;
        }

        int bs = brushSize[0];

        if (currentTool == Tool.ADD_VOXEL) {
            int x = (int) ghostPosition.x;
            int y = (int) ghostPosition.y;
            int z = (int) ghostPosition.z;

            List<Voxel> oldVoxels = copyVoxels(hoveredModel);

            for (int dx = 0; dx < bs; dx++) {
                for (int dy = 0; dy < bs; dy++) {
                    for (int dz = 0; dz < bs; dz++) {
                        hoveredModel.addVoxel(x + dx, y + dy, z + dz, currentColor);
                        if (mirrorX) {
                            int mx = hoveredModel.sizeX - 1 - (x + dx);
                            hoveredModel.addVoxel(mx, y + dy, z + dz, currentColor);
                        }
                    }
                }
            }
            pushVoxelEdit(hoveredModel, oldVoxels, "Added voxel at [" + x + ", " + y + ", " + z + "]");

        } else if (currentTool == Tool.REMOVE_VOXEL) {
            handleVoxelRemove();

        } else if (currentTool == Tool.PAINT) {
            int x = hoveredVoxelX, y = hoveredVoxelY, z = hoveredVoxelZ;
            Voxel v = hoveredModel.getVoxelAt(x, y, z);
            if (v != null) {
                Vector3f oldColor = Voxel.hexToColor(v.color);
                commandHistory.execute(new Commands.PaintVoxelCommand(hoveredModel, x, y, z, oldColor, currentColor));
                statusMessage = "Painted voxel at [" + x + ", " + y + ", " + z + "]";
            }

        } else if (currentTool == Tool.EYEDROPPER) {
            int x = hoveredVoxelX, y = hoveredVoxelY, z = hoveredVoxelZ;
            Voxel v = hoveredModel.getVoxelAt(x, y, z);
            if (v != null) {
                currentColor.set(Voxel.hexToColor(v.color));
                spawnManager.setCurrentColor(currentColor);
                statusMessage = "Picked color " + Voxel.colorToHex(v.color);
            }
        }
    }

    private void handleVoxelRemove() {
        if (hoveredModel == null || !showGhost) return;

        int x = hoveredVoxelX, y = hoveredVoxelY, z = hoveredVoxelZ;
        int bs = brushSize[0];

        List<Voxel> oldVoxels = copyVoxels(hoveredModel);

        for (int dx = 0; dx < bs; dx++) {
            for (int dy = 0; dy < bs; dy++) {
                for (int dz = 0; dz < bs; dz++) {
                    hoveredModel.removeVoxel(x + dx, y + dy, z + dz);
                    if (mirrorX) {
                        int mx = hoveredModel.sizeX - 1 - (x + dx);
                        hoveredModel.removeVoxel(mx, y + dy, z + dz);
                    }
                }
            }
        }
        pushVoxelEdit(hoveredModel, oldVoxels, "Removed voxel at [" + x + ", " + y + ", " + z + "]");
    }

    private List<Voxel> copyVoxels(VoxelModel model) {
        List<Voxel> out = new ArrayList<>();
        if (model.voxels != null) {
            for (Voxel v : model.voxels) out.add(v.copy());
        }
        return out;
    }

    private void pushVoxelEdit(VoxelModel model, List<Voxel> oldVoxels, String msg) {
        List<Voxel> newVoxels = copyVoxels(model);
        commandHistory.execute(new Commands.VoxelEditCommand(model, oldVoxels, newVoxels));
        statusMessage = msg;
    }

    // ================= SCENE =================

    private void renderScene() {
        int w = viewportW;
        int h = viewportH;
        if (w <= 0 || h <= 0) return;

        if (sceneFramebuffer.getWidth() != w || sceneFramebuffer.getHeight() != h) {
            sceneFramebuffer.cleanup();
            sceneFramebuffer = new Framebuffer(w, h);
        }

        sceneFramebuffer.bind();
        glClearColor(0.15f, 0.15f, 0.18f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        Matrix4f viewProj = camera.getViewProjection((float) w / (float) h);

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

        if (showGhost && editMode == EditMode.EDIT_MODE && hoveredModel != null) {
            ghostShader.use();
            ghostShader.setUniform("uViewProj", viewProj);
            ghostShader.setUniform("uColor", currentColor);
            ghostShader.setUniform("uAlpha", 0.6f);
            ghostShader.setUniform("uLightDir", new Vector3f(0.5f, 1.0f, 0.3f).normalize());
            Matrix4f ghostMatrix = new Matrix4f().translation(
                    ghostPosition.x + hoveredModel.transform.position.x,
                    ghostPosition.y + hoveredModel.transform.position.y,
                    ghostPosition.z + hoveredModel.transform.position.z);
            ghostShader.setUniform("uModel", ghostMatrix);
            boxMesh.draw();
        }

        if (editMode == EditMode.EDIT_MODE && !selectedObjects.isEmpty()) {
            VoxelModel model = selectedObjects.get(0);
            outlineShader.use();
            outlineShader.setUniform("uViewProj", viewProj);
            outlineShader.setUniform("uColor", new Vector3f(0.2f, 1.0f, 0.2f));
            Matrix4f boundsMatrix = new Matrix4f()
                    .translation(
                            model.transform.position.x + model.sizeX / 2.0f,
                            model.transform.position.y + model.sizeY / 2.0f,
                            model.transform.position.z + model.sizeZ / 2.0f)
                    .scale(model.sizeX, model.sizeY, model.sizeZ);
            outlineShader.setUniform("uModel", boundsMatrix);
            cubeOutlineMesh.draw();
        }

        if (!selectedObjects.isEmpty()) {
            outlineShader.use();
            outlineShader.setUniform("uViewProj", viewProj);
            outlineShader.setUniform("uColor", new Vector3f(1.0f, 0.85f, 0.2f));
            for (VoxelModel obj : selectedObjects) {
                Matrix4f modelMatrix = obj.transform.getModelMatrix();
                if (obj.voxels == null) continue;
                for (Voxel v : obj.voxels) {
                    Matrix4f voxelMatrix = new Matrix4f(modelMatrix).translate(v.x, v.y, v.z).scale(1.02f);
                    outlineShader.setUniform("uModel", voxelMatrix);
                    cubeOutlineMesh.draw();
                }
            }
        }

        sceneFramebuffer.unbind();
        glViewport(0, 0, window.getWidth(), window.getHeight());
    }

    // ================= UI =================

    private void renderUI() {
        renderToolbar();
        renderExplorer();
        renderProperties();
        renderViewport();
        renderStatusBar();

        if (showNewModelDialog) renderNewModelDialog();
    }

    private void renderToolbar() {
        ImGui.setNextWindowPos(0, MAIN_MENU_HEIGHT, ImGuiCond.Always);
        ImGui.setNextWindowSize(window.getWidth(), TOOLBAR_HEIGHT, ImGuiCond.Always);
        ImGui.begin("Toolbar", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBackground);

        ImGui.setNextItemWidth(140);
        ImInt modeIdx = new ImInt(editorMode.ordinal());
        if (ImGui.combo("##EditorMode", modeIdx, new String[]{"Model Editor", "Map Editor"})) {
            editorMode = EditorMode.values()[modeIdx.get()];
        }
        ImGui.sameLine();

        ImGui.text("|");
        ImGui.sameLine();

        if (ImGui.button(editMode == EditMode.OBJECT_MODE ? "Object Mode (Tab)" : "Edit Mode (Tab)")) {
            editMode = (editMode == EditMode.OBJECT_MODE) ? EditMode.EDIT_MODE : EditMode.OBJECT_MODE;
        }
        ImGui.sameLine();

        if (ImGui.button("Select (Q)")) { currentTool = Tool.SELECT; editMode = EditMode.OBJECT_MODE; }
        ImGui.sameLine();
        if (ImGui.button("Add (B)")) { currentTool = Tool.ADD_VOXEL; editMode = EditMode.EDIT_MODE; }
        ImGui.sameLine();
        if (ImGui.button("Remove (X)")) { currentTool = Tool.REMOVE_VOXEL; editMode = EditMode.EDIT_MODE; }
        ImGui.sameLine();
        if (ImGui.button("Paint (G)")) { currentTool = Tool.PAINT; editMode = EditMode.EDIT_MODE; }
        ImGui.sameLine();
        if (ImGui.button("Pick (I)")) { currentTool = Tool.EYEDROPPER; editMode = EditMode.EDIT_MODE; }
        ImGui.sameLine();

        ImGui.text("|");
        ImGui.sameLine();

        ImGui.text("Brush:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(90);
        ImGui.sliderInt("##BrushSize", brushSize, 1, 5);
        ImGui.sameLine();

        mirrorX = ImGui.checkbox("Mirror X", mirrorX);
        ImGui.sameLine();

        ImGui.text("|");
        ImGui.sameLine();

        if (ImGui.button("Undo")) commandHistory.undo();
        ImGui.sameLine();
        if (ImGui.button("Redo")) commandHistory.redo();
        ImGui.sameLine();

        ImGui.text("|");
        ImGui.sameLine();

        if (ImGui.button("New 8x8x32")) {
            newModelName.set("Prop_" + (scene.getModels().size() + 1));
            newModelSize[0] = 8;
            newModelSize[1] = 32;
            newModelSize[2] = 8;
            showNewModelDialog = true;
            ImGui.openPopup("Create New Model");
        }
        ImGui.sameLine();
        if (ImGui.button("New Custom")) {
            newModelName.set("Model_" + (scene.getModels().size() + 1));
            showNewModelDialog = true;
            ImGui.openPopup("Create New Model");
        }
        ImGui.sameLine();

        if (!selectedObjects.isEmpty() && ImGui.button("Duplicate (Shift+D)")) {
            spawnManager.duplicateModel(selectedObjects.get(0));
        }
        ImGui.sameLine();

        if (!selectedObjects.isEmpty() && ImGui.button("Export")) {
            ModelExportManager.saveModel(selectedObjects.get(0), exportFilename.get());
            statusMessage = "Exported " + exportFilename.get() + ".jbm";
        }
        ImGui.sameLine();

        ImGui.text("|");
        ImGui.sameLine();
        ImGui.text("Objects: " + scene.getModels().size());

        ImGui.end();
    }

    private void renderExplorer() {
        if (!editorUi.isShowExplorer()) return;

        ImGui.setNextWindowPos(0, TOP_OFFSET, ImGuiCond.Once);
        ImGui.setNextWindowSize(250, window.getHeight() - TOP_OFFSET - 40, ImGuiCond.Once);
        ImGui.begin("Explorer");

        ImGui.text("Scene: " + scene.getName());
        ImGui.separator();

        if (ImGui.button("Import Model")) {
            VoxelModel loaded = ModelExportManager.loadModel(exportFilename.get());
            if (loaded != null) {
                scene.addModel(loaded);
                selectedObjects.clear();
                selectedObjects.add(loaded);
                statusMessage = "Imported " + exportFilename.get() + ".jbm";
            } else {
                statusMessage = "Import failed: " + exportFilename.get() + ".jbm";
            }
        }
        ImGui.separator();

        for (VoxelModel obj : scene.getModels()) {
            boolean isSelected = selectedObjects.contains(obj);
            String label = obj.name + " [" + obj.getVoxelCount() + " voxels]##" + obj.id.substring(0, 5);

            if (ImGui.selectable(label, isSelected)) {
                boolean ctrl = ImGui.getIO().getKeyCtrl();
                if (ctrl) {
                    if (selectedObjects.contains(obj)) selectedObjects.remove(obj);
                    else selectedObjects.add(obj);
                } else {
                    selectedObjects.clear();
                    selectedObjects.add(obj);
                }
            }

            if (ImGui.beginPopupContextItem(obj.id)) {
                if (ImGui.menuItem("Duplicate")) spawnManager.duplicateModel(obj);
                if (ImGui.menuItem("Export")) {
                    ModelExportManager.saveModel(obj, obj.name);
                    statusMessage = "Exported " + obj.name + ".jbm";
                }
                ImGui.separator();
                if (ImGui.menuItem("Delete")) {
                    List<VoxelModel> toDelete = new ArrayList<>();
                    toDelete.add(obj);
                    commandHistory.execute(new Commands.DeleteCommand(scene, toDelete));
                    selectedObjects.remove(obj);
                }
                ImGui.endPopup();
            }
        }

        ImGui.end();
    }

    private void renderProperties() {
        if (!editorUi.isShowProperties()) return;
        if (selectedObjects.isEmpty()) return;

        VoxelModel mainObj = selectedObjects.get(0);

        ImGui.setNextWindowPos(window.getWidth() - 250, TOP_OFFSET, ImGuiCond.Once);
        ImGui.setNextWindowSize(250, window.getHeight() - TOP_OFFSET - 40, ImGuiCond.Once);
        ImGui.begin("Properties");

        ImGui.text("Selected: " + selectedObjects.size());
        ImGui.separator();

        ImGui.text("Model: " + mainObj.name);
        ImGui.text("Size: " + mainObj.sizeX + " x " + mainObj.sizeY + " x " + mainObj.sizeZ);
        ImGui.text("Voxels: " + mainObj.getVoxelCount());
        ImGui.separator();

        float[] pos = {mainObj.transform.position.x, mainObj.transform.position.y, mainObj.transform.position.z};
        if (ImGui.dragFloat3("Position", pos, 0.1f)) {
            mainObj.transform.position.set(pos[0], pos[1], pos[2]);
            mainObj.markAABBDirty();
        }

        float[] rot = {mainObj.transform.rotation.x, mainObj.transform.rotation.y, mainObj.transform.rotation.z};
        if (ImGui.dragFloat3("Rotation", rot, 1.0f)) {
            mainObj.transform.rotation.set(rot[0], rot[1], rot[2]);
            mainObj.markAABBDirty();
        }

        float[] scl = {mainObj.transform.scale.x, mainObj.transform.scale.y, mainObj.transform.scale.z};
        if (ImGui.dragFloat3("Scale", scl, 0.1f)) {
            mainObj.transform.scale.set(scl[0], scl[1], scl[2]);
            mainObj.markAABBDirty();
        }

        ImGui.separator();
        ImGui.text("Color Palette:");

        Vector3f[] palette = {
                new Vector3f(0.8f, 0.2f, 0.2f),
                new Vector3f(0.2f, 0.8f, 0.2f),
                new Vector3f(0.2f, 0.2f, 0.8f),
                new Vector3f(0.8f, 0.8f, 0.2f),
                new Vector3f(0.8f, 0.2f, 0.8f),
                new Vector3f(0.2f, 0.8f, 0.8f),
                new Vector3f(0.8f, 0.8f, 0.8f),
                new Vector3f(0.2f, 0.2f, 0.2f)
        };

        for (int i = 0; i < palette.length; i++) {
            float[] c = {palette[i].x, palette[i].y, palette[i].z, 1.0f};
            if (ImGui.colorButton("##col" + i, c, 0, 20, 20)) {
                currentColor.set(palette[i]);
                spawnManager.setCurrentColor(currentColor);
            }
            if (i % 4 != 3) ImGui.sameLine();
        }

        ImGui.separator();

        float[] currentCol = {currentColor.x, currentColor.y, currentColor.z, 1.0f};
        if (ImGui.colorEdit3("Current Color", currentCol)) {
            currentColor.set(currentCol[0], currentCol[1], currentCol[2]);
            spawnManager.setCurrentColor(currentColor);
        }

        ImGui.separator();
        ImGui.text("Export Filename:");
        ImGui.inputText("##ExportFilename", exportFilename);

        ImGui.end();
    }

    private void renderViewport() {
        if (!editorUi.isShowViewport()) return;

        ImGui.setNextWindowPos(250, TOP_OFFSET, ImGuiCond.Once);
        ImGui.setNextWindowSize(window.getWidth() - 500, window.getHeight() - TOP_OFFSET - 40, ImGuiCond.Once);

        ImGui.begin("Viewport",
                ImGuiWindowFlags.NoScrollbar |
                ImGuiWindowFlags.NoScrollWithMouse |
                ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoNavFocus);

        float availW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();

        if (availW > 0 && availH > 0) {
            ImGui.image(sceneFramebuffer.getTextureId(), availW, availH, 0, 1, 1, 0);
            viewportW = (int) availW;
            viewportH = (int) availH;
        }

        ImGui.end();
    }

    private void renderStatusBar() {
        ImGui.setNextWindowPos(0, window.getHeight() - 40, ImGuiCond.Always);
        ImGui.setNextWindowSize(window.getWidth(), 40, ImGuiCond.Always);
        ImGui.begin("StatusBar", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBackground);

        ImGui.text(String.format("FPS: %.1f", fps));
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();
        ImGui.text("Tool: " + currentTool.name());
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();
        ImGui.text("Mode: " + editMode.name());
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        if (showGhost && editMode == EditMode.EDIT_MODE) {
            ImGui.text(String.format("Cursor: [%d, %d, %d]",
                    (int) ghostPosition.x, (int) ghostPosition.y, (int) ghostPosition.z));
        } else {
            ImGui.text("Cursor: ---");
        }
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        int totalVoxels = 0;
        for (VoxelModel m : scene.getModels()) totalVoxels += m.getVoxelCount();
        ImGui.text("Total Voxels: " + totalVoxels);
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, statusMessage);

        ImGui.end();
    }

    private void renderNewModelDialog() {
        ImGui.setNextWindowPos(window.getWidth() / 2 - 175, window.getHeight() / 2 - 130, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(350, 320, ImGuiCond.Appearing);

        if (ImGui.beginPopupModal("Create New Model", ImGuiWindowFlags.NoResize)) {
            ImGui.text("Create New Voxel Model");
            ImGui.separator();

            ImGui.text("Model Name:");
            ImGui.inputText("##ModelName", newModelName);

            ImGui.separator();
            ImGui.text("Preset Sizes:");

            if (ImGui.button("Small (8x8x8)", 100, 25)) {
                newModelSize[0] = 8; newModelSize[1] = 8; newModelSize[2] = 8;
            }
            ImGui.sameLine();
            if (ImGui.button("Tall (8x8x32)", 100, 25)) {
                newModelSize[0] = 8; newModelSize[1] = 32; newModelSize[2] = 8;
            }
            ImGui.sameLine();
            if (ImGui.button("Wide (32x8x8)", 100, 25)) {
                newModelSize[0] = 32; newModelSize[1] = 8; newModelSize[2] = 8;
            }

            if (ImGui.button("Cube (16x16x16)", 100, 25)) {
                newModelSize[0] = 16; newModelSize[1] = 16; newModelSize[2] = 16;
            }
            ImGui.sameLine();
            if (ImGui.button("Character (8x32x8)", 110, 25)) {
                newModelSize[0] = 8; newModelSize[1] = 32; newModelSize[2] = 8;
            }
            ImGui.sameLine();
            if (ImGui.button("Prop (16x16x16)", 100, 25)) {
                newModelSize[0] = 16; newModelSize[1] = 16; newModelSize[2] = 16;
            }

            ImGui.separator();
            ImGui.text("Custom Size (voxels):");

            int[] sizeBuffer = {newModelSize[0], newModelSize[1], newModelSize[2]};
            if (ImGui.inputInt3("##ModelSize", sizeBuffer)) {
                newModelSize[0] = Math.max(1, Math.min(128, sizeBuffer[0]));
                newModelSize[1] = Math.max(1, Math.min(128, sizeBuffer[1]));
                newModelSize[2] = Math.max(1, Math.min(128, sizeBuffer[2]));
            }

            ImGui.spacing();
            ImGui.text("Grid: " + newModelSize[0] + " x " + newModelSize[1] + " x " + newModelSize[2]);
            ImGui.text("Max voxels: " + (newModelSize[0] * newModelSize[1] * newModelSize[2]));

            ImGui.separator();

            if (ImGui.button("Create", 150, 30)) {
                VoxelModel newModel = spawnManager.createNewModel(
                        newModelName.get(), newModelSize[0], newModelSize[1], newModelSize[2]);
                if (newModel != null) {
                    selectedObjects.clear();
                    selectedObjects.add(newModel);
                    editMode = EditMode.EDIT_MODE;
                    currentTool = Tool.ADD_VOXEL;
                    statusMessage = "Created model: " + newModelName.get();
                }
                showNewModelDialog = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 150, 30)) {
                showNewModelDialog = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    // ================= UI ACTION =================

    private void handleUiAction(EditorUiState.UiAction action) {
        if (action == EditorUiState.UiAction.NONE) return;

        if (action == EditorUiState.UiAction.CREATE_MODEL_WORKSPACE) {
            scene.getModels().clear();
            selectedObjects.clear();
            currentTool = Tool.ADD_VOXEL;
            statusMessage = "New model workspace: " + editorUi.getActiveModelName();
            return;
        }

        if (action == EditorUiState.UiAction.OPEN_MAP_WORKSPACE) {
            selectedObjects.clear();
            currentTool = Tool.SELECT;
            statusMessage = "Map workspace";
        }
    }

    private int countSceneVoxels() {
        int count = 0;
        for (VoxelModel model : scene.getModels()) {
            count += model.getVoxelCount();
        }
        return count;
    }

    // ================= MAIN LOOP =================

    public void updateAndRender(float dt) {
        frameTimeAccum += dt;
        frameCount++;
        if (frameTimeAccum >= 1.0f) {
            fps = frameCount / frameTimeAccum;
            frameTimeAccum = 0;
            frameCount = 0;
        }

        if (editorUi.isStartupVisible()) {
            handleUiAction(editorUi.renderStartup(window));
            return;
        }

        handleUiAction(editorUi.renderMainMenuBar());

        handleInput(dt);
        updateGhostVoxel();
        handleMouseInput();
        renderScene();
        renderUI();

        editorUi.renderModelPropertiesWindow();
        editorUi.renderPaletteManagerWindow();
        editorUi.renderStatusWindow(
                dt,
                scene.getModels().size(),
                countSceneVoxels(),
                instancedRenderer.getCount()
        );
    }
}
// === END OF FILE ===
