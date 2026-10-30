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
            boolean shift = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW
