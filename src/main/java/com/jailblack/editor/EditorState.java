package com.jailblack.editor;

import com.jailblack.engine.*;
import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

public class EditorState {

    public enum EditorMode { MODEL_EDITOR, MAP_EDITOR }
    public enum EditMode { OBJECT_MODE, EDIT_MODE }
    public enum Tool { SELECT, ADD_VOXEL, REMOVE_VOXEL, PAINT, EYEDROPPER, FILL, MOVE, SCALE }

    private static final float MAIN_MENU_HEIGHT = 24.0f;
    private static final float TOOLBAR_HEIGHT = 40.0f;
    private static final float TOP_OFFSET = MAIN_MENU_HEIGHT + TOOLBAR_HEIGHT;

    // === GIZMO colors (ReveR-2) ===
    private static final int COL_X = 0xFF2020FF;
    private static final int COL_Y = 0xFF20FF20;
    private static final int COL_Z = 0xFFFF2020;
    private static final int COL_PLANE = 0x80FFFF00;
    private static final int COL_FREE = 0xFFFFFFFF;

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
    private boolean cursorInsideModel = false;

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

    private float hintTimer = 10.0f;
    private boolean showHints = true;

    // === Viewport image rect (экранные координаты) ===
    private float vpImageX = 250f;
    private float vpImageY = 64f;
    private float vpImageW = 1000f;
    private float vpImageH = 700f;

    // === GIZMO state ===
    private boolean viewportHovered = false;
    private boolean leftMouseDownInViewport = false;
    private boolean middleMouseDownInViewport = false;

    private final Matrix4f lastViewProjMatrix = new Matrix4f();

    private boolean gizmoValid = false;
    private float gCX, gCY;
    private final float[][] gAxisTip = new float[3][2];
    private final float[][] gNegTip = new float[3][2];
    private final float[][] gPlaneC = new float[3][2];
    private float gK = 1f;

    private boolean gizmoMoveDrag = false;
    private int gizmoMoveMode = 0;
    private boolean gizmoScaleDrag = false;
    private int gizmoScaleSlot = -1;
    private float scaleAccum = 0f;
    private int scaleSteps = 0;
    private List<Vector3f> dragStartAll = null;
    private float pressMX, pressMY;

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

        // дефолтный rect для первого кадра (до вызова renderViewport)
        vpImageW = Math.max(1, window.getWidth() - 500);
        vpImageH = Math.max(1, window.getHeight() - (int) TOP_OFFSET - 40);

        ModelExportManager.ensureModelsDirectory();
        setupInputCallbacks();
    }

    public void setSceneFramebuffer(Framebuffer fb) {
        this.sceneFramebuffer = fb;
    }

    // ================= INPUT (GLFW, камера + гизмо) =================

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
            boolean rmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

            if (lmb && leftMouseDownInViewport) {
                if (gizmoMoveDrag) {
                    applyMoveDrag(dx, dy);
                } else if (gizmoScaleDrag) {
                    scaleAccum += dot2(dx, dy, scaleDirX(), scaleDirY());
                    scaleSteps = Math.round(scaleAccum);
                    if (scaleSteps > 32) scaleSteps = 32;
                    if (scaleSteps < -32) scaleSteps = -32;
                }
            } else if (rmb) {
                camera.rotate(dx, dy);
            } else if (mmb && middleMouseDownInViewport) {
                camera.pan(dx, dy);
            }
        });

        GLFW.glfwSetMouseButtonCallback(window.getHandle(), (win, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                leftMouseDownInViewport = viewportHovered;
                if (leftMouseDownInViewport) {
                    pressMX = ImGui.getMousePosX();
                    pressMY = ImGui.getMousePosY();
                }
                if (leftMouseDownInViewport && !selectedObjects.isEmpty()
                        && editMode == EditMode.OBJECT_MODE) {
                    float mx = ImGui.getMousePosX();
                    float my = ImGui.getMousePosY();
                    if (currentTool == Tool.MOVE) {
                        int mode = pickMoveMode(mx, my);
                        if (mode != 0) {
                            gizmoMoveDrag = true;
                            gizmoMoveMode = mode;
                            dragStartAll = copyPositions();
                        }
                    } else if (currentTool == Tool.SCALE) {
                        int slot = pickScaleSlot(mx, my);
                        if (slot >= 0) {
                            gizmoScaleDrag = true;
                            gizmoScaleSlot = slot;
                            scaleAccum = 0f;
                            scaleSteps = 0;
                        }
                    }
                }
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_RELEASE) {
                leftMouseDownInViewport = false;
                if (gizmoMoveDrag) {
                    gizmoMoveDrag = false;
                    pushMoveCommand();
                }
                if (gizmoScaleDrag) {
                    gizmoScaleDrag = false;
                    applyScaleSteps();
                }
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_PRESS) {
                middleMouseDownInViewport = viewportHovered;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_RELEASE) {
                middleMouseDownInViewport = false;
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
        if (hintTimer > 0f) {
            hintTimer -= dt;
            if (hintTimer <= 0f) showHints = false;
        }

        boolean uiWantsKeyboard = ImGui.getIO().getWantCaptureKeyboard();

        if (uiWantsKeyboard) {
            heldKeys.clear();
        } else {
            if (pressedOnce(GLFW.GLFW_KEY_Q)) { currentTool = Tool.SELECT; editMode = EditMode.OBJECT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_B)) { currentTool = Tool.ADD_VOXEL; editMode = EditMode.EDIT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_X)) { currentTool = Tool.REMOVE_VOXEL; editMode = EditMode.EDIT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_G)) { currentTool = Tool.PAINT; editMode = EditMode.EDIT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_I)) { currentTool = Tool.EYEDROPPER; editMode = EditMode.EDIT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_M)) { currentTool = Tool.MOVE; editMode = EditMode.OBJECT_MODE; }
            if (pressedOnce(GLFW.GLFW_KEY_R)) { currentTool = Tool.SCALE; editMode = EditMode.OBJECT_MODE; }

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

        boolean ctrlKey = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
        boolean zKey = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;

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
        if (ctrlKey && !zKey)
            camPos.y -= vel;

        camera.setPosition(camPos);
    }

    // ================= MOUSE =================

    private void handleMouseInput() {
        long win = window.getHandle();

        if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
            if (!ImGui.getIO().getWantCaptureMouse() || viewportHovered) {
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
        if (vpImageW <= 0f || vpImageH <= 0f) return false;

        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), mx, my);

        return mx[0] >= vpImageX && mx[0] <= vpImageX + vpImageW
                && my[0] >= vpImageY && my[0] <= vpImageY + vpImageH;
    }

    // ================= MOUSE RAY =================

    /**
     * Строит луч из позиции мыши на экране: unproject через инверсную viewProj.
     * Пишет нормированное направление в outDirection. Возвращает false, если
     * обратная матрица вырождена или размеры viewport нулевые.
     */
    private boolean getMouseRay(double mouseX, double mouseY, Vector3f outDirection) {
        if (vpImageW <= 0f || vpImageH <= 0f) return false;

        float ndcX = (float) ((mouseX - vpImageX) / vpImageW * 2.0 - 1.0);
        float ndcY = (float) (1.0 - (mouseY - vpImageY) / vpImageH * 2.0);

        Matrix4f viewProj = camera.getViewProjection(vpImageW / vpImageH);
        Matrix4f invViewProj = new Matrix4f();
        try {
            viewProj.invert(invViewProj);
        } catch (Exception e) {
            return false;
        }

        Vector4f nearPoint = new Vector4f(ndcX, ndcY, -1.0f, 1.0f).mul(invViewProj);
        if (Math.abs(nearPoint.w) < 1e-7f) return false;
        nearPoint.div(nearPoint.w);

        Vector4f farPoint = new Vector4f(ndcX, ndcY, 1.0f, 1.0f).mul(invViewProj);
        if (Math.abs(farPoint.w) < 1e-7f) return false;
        farPoint.div(farPoint.w);

        outDirection.set(
                farPoint.x - nearPoint.x,
                farPoint.y - nearPoint.y,
                farPoint.z - nearPoint.z).normalize();

        return true;
    }

    // ================= GHOST + DDA =================

    private void updateGhostVoxel() {
        showGhost = false;
        cursorInsideModel = false;

        if (editMode != EditMode.EDIT_MODE) return;
        if (selectedObjects.isEmpty()) return;

        hoveredModel = selectedObjects.get(0);
        if (hoveredModel == null) return;

        if (!isMouseOverViewport()) return;

        double[] mx = new double[1];
        double[] my = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), mx, my);

        Vector3f rayDir = new Vector3f();
        if (!getMouseRay(mx[0], my[0], rayDir)) return;

        Vector3f camPos = camera.getPosition();
        Vector3f modelPos = hoveredModel.transform.position;
        Vector3f localRayOrigin = new Vector3f(camPos).sub(modelPos);

        cursorInsideModel =
                camPos.x >= modelPos.x && camPos.x < modelPos.x + hoveredModel.sizeX &&
                camPos.y >= modelPos.y && camPos.y < modelPos.y + hoveredModel.sizeY &&
                camPos.z >= modelPos.z && camPos.z < modelPos.z + hoveredModel.sizeZ;

        int[] hit = ddaRaycast(localRayOrigin, rayDir,
                hoveredModel.sizeX, hoveredModel.sizeY, hoveredModel.sizeZ,
                hoveredModel);

        if (hit != null) {
            hoveredVoxelX = hit[0];
            hoveredVoxelY = hit[1];
            hoveredVoxelZ = hit[2];

            if (currentTool == Tool.ADD_VOXEL) {
                ghostPosition.set(hit[0] + hit[3], hit[1] + hit[4], hit[2] + hit[5]);
            } else {
                ghostPosition.set(hit[0], hit[1], hit[2]);
            }

            boolean outside = ghostPosition.x < 0 || ghostPosition.x >= hoveredModel.sizeX
                    || ghostPosition.y < 0 || ghostPosition.y >= hoveredModel.sizeY
                    || ghostPosition.z < 0 || ghostPosition.z >= hoveredModel.sizeZ;

            showGhost = true;

            if (outside && currentTool == Tool.ADD_VOXEL) {
                statusMessage = "Click to expand bounds to ["
                        + (int) ghostPosition.x + ", " + (int) ghostPosition.y + ", "
                        + (int) ghostPosition.z + "]";
            } else {
                statusMessage = "Target: [" + (int) ghostPosition.x + ", "
                        + (int) ghostPosition.y + ", " + (int) ghostPosition.z + "]";
            }
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

        int nx = 0, ny = 0, nz = 0;
        float adx = Math.abs(dir.x), ady = Math.abs(dir.y), adz = Math.abs(dir.z);
        if (adx >= ady && adx >= adz) {
            nx = dir.x > 0 ? -1 : 1;
        } else if (ady >= adz) {
            ny = dir.y > 0 ? -1 : 1;
        } else {
            nz = dir.z > 0 ? -1 : 1;
        }

        int[] firstEntry = null;
        boolean wasInside = false;

        for (int step = 0; step < 1024; step++) {
            boolean inside =
                    voxelX >= 0 && voxelX < sizeX &&
                    voxelY >= 0 && voxelY < sizeY &&
                    voxelZ >= 0 && voxelZ < sizeZ;

            if (inside && !wasInside && firstEntry == null) {
                firstEntry = new int[]{voxelX, voxelY, voxelZ, nx, ny, nz};
            }
            wasInside = inside;

            if (inside && model != null && model.getVoxelAt(voxelX, voxelY, voxelZ) != null) {
                return new int[]{voxelX, voxelY, voxelZ, nx, ny, nz};
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
            statusMessage = "No valid target - move cursor over the model";
            return;
        }

        int bs = brushSize[0];

        if (currentTool == Tool.ADD_VOXEL) {
            int x = (int) ghostPosition.x;
            int y = (int) ghostPosition.y;
            int z = (int) ghostPosition.z;

            List<Voxel> oldVoxels = copyVoxels(hoveredModel);

            // расширяем границы так, чтобы вся кисть влезла (включая отрицательные координаты)
            hoveredModel.expandToInclude(
                    Math.min(x, x + bs - 1),
                    Math.min(y, y + bs - 1),
                    Math.min(z, z + bs - 1),
                    Math.max(x, x + bs - 1),
                    Math.max(y, y + bs - 1),
                    Math.max(z, z + bs - 1));

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

    // ================= GIZMO =================

    private boolean worldToScreen(Vector3f world, float[] out) {
        Vector4f p = new Vector4f(world, 1.0f).mul(lastViewProjMatrix);
        if (p.w <= 0) return false;
        float nx = p.x / p.w;
        float ny = p.y / p.w;
        out[0] = (nx * 0.5f + 0.5f) * viewportW;
        out[1] = (1f - (ny * 0.5f + 0.5f)) * viewportH;
        return true;
    }

    private void drawGizmo(float imgX, float imgY) {
        gizmoValid = false;
        if (selectedObjects.isEmpty()) return;
        if (currentTool != Tool.MOVE && currentTool != Tool.SCALE) return;

        VoxelModel target = selectedObjects.get(0);
        Vector3f origin = new Vector3f(target.transform.position);

        float[] c = new float[2];
        if (!worldToScreen(origin, c)) return;

        float dist = camera.getPosition().distance(origin);
        gK = (float) (2.0 * dist * Math.tan(Math.toRadians(30.0)) / Math.max(viewportH, 1));

        ImDrawList dl = ImGui.getWindowDrawList();

        float cx = imgX + c[0];
        float cy = imgY + c[1];
        gCX = cx; gCY = cy;

        Vector3f[] axes = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
        int[] cols = {COL_X, COL_Y, COL_Z};

        for (int a = 0; a < 3; a++) {
            float[] t = new float[2];
            float[] tn = new float[2];
            Vector3f wp = new Vector3f(origin).add(new Vector3f(axes[a]).mul(1.6f));
            Vector3f wn = new Vector3f(origin).add(new Vector3f(axes[a]).mul(-1.6f));
            if (!worldToScreen(wp, t)) { t[0] = c[0]; t[1] = c[1]; }
            if (!worldToScreen(wn, tn)) { tn[0] = c[0]; tn[1] = c[1]; }
            gAxisTip[a][0] = imgX + t[0]; gAxisTip[a][1] = imgY + t[1];
            gNegTip[a][0] = imgX + tn[0]; gNegTip[a][1] = imgY + tn[1];

            dl.addLine(cx, cy, gAxisTip[a][0], gAxisTip[a][1], cols[a], 2.5f);
            dl.addCircleFilled(gAxisTip[a][0], gAxisTip[a][1], 5f, cols[a]);

            if (currentTool == Tool.SCALE) {
                dl.addLine(cx, cy, gNegTip[a][0], gNegTip[a][1], cols[a], 2.5f);
                dl.addCircleFilled(gNegTip[a][0], gNegTip[a][1], 5f, cols[a]);
            }
        }

        if (currentTool == Tool.MOVE) {
            int[][] pairs = {{0, 1}, {0, 2}, {1, 2}};
            for (int p = 0; p < 3; p++) {
                Vector3f pw = new Vector3f(origin)
                        .add(new Vector3f(axes[pairs[p][0]]).mul(0.5f))
                        .add(new Vector3f(axes[pairs[p][1]]).mul(0.5f));
                float[] pc = new float[2];
                if (worldToScreen(pw, pc)) {
                    gPlaneC[p][0] = imgX + pc[0];
                    gPlaneC[p][1] = imgY + pc[1];
                    dl.addRectFilled(gPlaneC[p][0] - 6, gPlaneC[p][1] - 6,
                            gPlaneC[p][0] + 6, gPlaneC[p][1] + 6, COL_PLANE);
                }
            }
            dl.addCircleFilled(cx, cy, 7f, COL_FREE);
        }

        if (gizmoScaleDrag) {
            dl.addText(cx + 14, cy - 14, COL_FREE, "Steps: " + scaleSteps);
        }

        gizmoValid = true;
    }

    private float dot2(float ax, float ay, float bx, float by) { return ax * bx + ay * by; }

    private float scaleDirX() {
        int axis = gizmoScaleSlot / 2;
        float sign = (gizmoScaleSlot % 2 == 0) ? 1f : -1f;
        float dx = gAxisTip[axis][0] - gCX;
        float dy = gAxisTip[axis][1] - gCY;
        if (sign < 0) { dx = -dx; dy = -dy; }
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return 0f;
        return dx / len;
    }

    private float scaleDirY() {
        int axis = gizmoScaleSlot / 2;
        float sign = (gizmoScaleSlot % 2 == 0) ? 1f : -1f;
        float dx = gAxisTip[axis][0] - gCX;
        float dy = gAxisTip[axis][1] - gCY;
        if (sign < 0) { dx = -dx; dy = -dy; }
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return 0f;
        return dy / len;
    }

    private float distToSeg(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay;
        float wx = px - ax, wy = py - ay;
        float ll = vx * vx + vy * vy;
        float t = ll > 0 ? Math.max(0, Math.min(1, (wx * vx + wy * vy) / ll)) : 0f;
        float dx = px - (ax + t * vx);
        float dy = py - (ay + t * vy);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private int pickMoveMode(float mx, float my) {
        if (!gizmoValid) return 0;
        for (int p = 0; p < 3; p++) {
            float dx = mx - gPlaneC[p][0], dy = my - gPlaneC[p][1];
            if (dx * dx + dy * dy < 100) return 4 + p;
        }
        for (int a = 0; a < 3; a++) {
            if (distToSeg(mx, my, gCX, gCY, gAxisTip[a][0], gAxisTip[a][1]) < 7) return 1 + a;
        }
        float dcx = mx - gCX, dcy = my - gCY;
        if (dcx * dcx + dcy * dcy < 81) return 7;
        return 0;
    }

    private int pickScaleSlot(float mx, float my) {
        if (!gizmoValid) return -1;
        for (int a = 0; a < 3; a++) {
            float dx = mx - gAxisTip[a][0], dy = my - gAxisTip[a][1];
            if (dx * dx + dy * dy < 81) return a * 2;
            dx = mx - gNegTip[a][0]; dy = my - gNegTip[a][1];
            if (dx * dx + dy * dy < 81) return a * 2 + 1;
        }
        return -1;
    }

    private List<Vector3f> copyPositions() {
        List<Vector3f> out = new ArrayList<>();
        for (VoxelModel m : selectedObjects) out.add(new Vector3f(m.transform.position));
        return out;
    }

    private void applyMoveDrag(float dx, float dy) {
        if (dragStartAll == null || selectedObjects.isEmpty()) return;
        Vector3f base = dragStartAll.get(0);

        if (gizmoMoveMode >= 1 && gizmoMoveMode <= 3) {
            int a = gizmoMoveMode - 1;
            Vector3f axis = new Vector3f();
            if (a == 0) axis.set(1, 0, 0);
            else if (a == 1) axis.set(0, 1, 0);
            else axis.set(0, 0, 1);
            float[] sx = new float[2], sy = new float[2];
            Vector3f wp = new Vector3f(base).add(axis);
            worldToScreen(wp, sx);
            worldToScreen(base, sy);
            float world = dot2(dx, dy, sx[0] - sy[0], sx[1] - sy[1]) * gK;
            Vector3f delta = new Vector3f(axis).mul(world);
            for (int i = 0; i < selectedObjects.size(); i++) {
                selectedObjects.get(i).transform.position.set(dragStartAll.get(i)).add(delta);
                selectedObjects.get(i).markAABBDirty();
            }
        } else if (gizmoMoveMode >= 4 && gizmoMoveMode <= 6) {
            int u = (gizmoMoveMode == 4) ? 0 : (gizmoMoveMode == 5 ? 0 : 1);
            int v = (gizmoMoveMode == 4) ? 1 : (gizmoMoveMode == 5 ? 2 : 2);
            Vector3f axisU = new Vector3f(), axisV = new Vector3f();
            if (u == 0) axisU.set(1, 0, 0); else if (u == 1) axisU.set(0, 1, 0); else axisU.set(0, 0, 1);
            if (v == 0) axisV.set(1, 0, 0); else if (v == 1) axisV.set(0, 1, 0); else axisV.set(0, 0, 1);
            float mx = dx * gK;
            float my = dy * gK;
            Vector3f delta = new Vector3f(axisU).mul(mx).add(new Vector3f(axisV).mul(my));
            for (int i = 0; i < selectedObjects.size(); i++) {
                selectedObjects.get(i).transform.position.set(dragStartAll.get(i)).add(delta);
                selectedObjects.get(i).markAABBDirty();
            }
        } else if (gizmoMoveMode == 7) {
            float wx = dx * gK;
            float wy = dy * gK;
            Vector3f right = camera.getRight();
            Vector3f up = camera.getUp();
            Vector3f delta = new Vector3f(right).mul(wx).add(new Vector3f(up).mul(wy));
            for (int i = 0; i < selectedObjects.size(); i++) {
                selectedObjects.get(i).transform.position.set(dragStartAll.get(i)).add(delta);
                selectedObjects.get(i).markAABBDirty();
            }
        }
    }

    private void pushMoveCommand() {
        if (dragStartAll == null || selectedObjects.isEmpty()) return;
        Vector3f[] oldP = new Vector3f[selectedObjects.size()];
        Vector3f[] newP = new Vector3f[selectedObjects.size()];
        for (int i = 0; i < selectedObjects.size(); i++) {
            oldP[i] = dragStartAll.get(i);
            newP[i] = new Vector3f(selectedObjects.get(i).transform.position);
        }
        commandHistory.execute(new Commands.MoveCommand(selectedObjects, oldP, newP));
        dragStartAll = null;
    }

    private void applyScaleSteps() {
        if (scaleSteps == 0 || selectedObjects.isEmpty() || gizmoScaleSlot < 0) return;
        int axis = gizmoScaleSlot / 2;
        VoxelModel m = selectedObjects.get(0);
        List<Voxel> oldVoxels = copyVoxels(m);

        float factor = 1.0f + scaleSteps * 0.1f;
        if (factor < 0.1f) factor = 0.1f;

        for (Voxel v : m.voxels) {
            if (axis == 0) v.x = Math.round(v.x * factor);
            else if (axis == 1) v.y = Math.round(v.y * factor);
            else v.z = Math.round(v.z * factor);
        }
        m.markAABBDirty();

        List<Voxel> newVoxels = copyVoxels(m);
        commandHistory.execute(new Commands.VoxelEditCommand(m, oldVoxels, newVoxels));

        gizmoScaleSlot = -1;
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
        lastViewProjMatrix.set(viewProj);

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

        // ghost + wireframe (мировые координаты — modelPos + ghostPosition)
        if (showGhost && editMode == EditMode.EDIT_MODE && hoveredModel != null) {
            Vector3f modelPos = hoveredModel.transform.position;
            float gx = modelPos.x + ghostPosition.x;
            float gy = modelPos.y + ghostPosition.y;
            float gz = modelPos.z + ghostPosition.z;

            ghostShader.use();
            ghostShader.setUniform("uViewProj", viewProj);
            ghostShader.setUniform("uColor", currentColor);
            ghostShader.setUniform("uAlpha", 0.45f);
            ghostShader.setUniform("uLightDir", new Vector3f(0.5f, 1.0f, 0.3f).normalize());
            Matrix4f ghostMatrix = new Matrix4f().translation(gx, gy, gz);
            ghostShader.setUniform("uModel", ghostMatrix);
            boxMesh.draw();

            outlineShader.use();
            outlineShader.setUniform("uViewProj", viewProj);
            outlineShader.setUniform("uColor", new Vector3f(0.2f, 1.0f, 1.0f));
            Matrix4f ghostOutline = new Matrix4f().translation(gx, gy, gz).scale(1.01f);
            outlineShader.setUniform("uModel", ghostOutline);
            cubeOutlineMesh.draw();
        }

        if (editMode == EditMode.EDIT_MODE && !selectedObjects.isEmpty()) {
            VoxelModel model = selectedObjects.get(0);
            outlineShader.use();
            outlineShader.setUniform("uViewProj", viewProj);

            if (cursorInsideModel) {
                outlineShader.setUniform("uColor", new Vector3f(0.2f, 1.0f, 0.2f));
            } else {
                outlineShader.setUniform("uColor", new Vector3f(1.0f, 0.25f, 0.25f));
            }

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
        renderHintsOverlay();

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
        if (ImGui.button("Move (M)")) { currentTool = Tool.MOVE; editMode = EditMode.OBJECT_MODE; }
        ImGui.sameLine();
        if (ImGui.button("Scale (R)")) { currentTool = Tool.SCALE; editMode = EditMode.OBJECT_MODE; }
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

            vpImageX = ImGui.getItemRectMinX();
            vpImageY = ImGui.getItemRectMinY();
            vpImageW = availW;
            vpImageH = availH;

            viewportHovered = ImGui.isWindowHovered();

            if (viewportHovered && editMode == EditMode.OBJECT_MODE) {
                drawGizmo(vpImageX, vpImageY);
            } else {
                gizmoValid = false;
            }
        } else {
            viewportHovered = false;
            gizmoValid = false;
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

        if (cursorInsideModel) {
            ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, "Inside: YES");
        } else {
            ImGui.textColored(1.0f, 0.5f, 0.4f, 1.0f, "Inside: NO");
        }
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();

        int totalVoxels = 0;
        for (VoxelModel m : scene.getModels()) totalVoxels += m.getVoxelCount();
        ImGui.text("Voxels: " + totalVoxels);
        ImGui.sameLine();
        ImGui.text("|");
        ImGui.sameLine();
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, statusMessage);

        ImGui.end();
    }

    private void renderHintsOverlay() {
        if (!showHints) return;

        ImGui.setNextWindowPos(260, TOP_OFFSET + 8, ImGuiCond.Always);
        ImGui.setNextWindowSize(420, 210, ImGuiCond.Always);
        ImGui.begin("##HintsOverlay",
                ImGuiWindowFlags.NoTitleBar |
                ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoSavedSettings |
                ImGuiWindowFlags.NoFocusOnAppearing |
                ImGuiWindowFlags.NoInputs |
                ImGuiWindowFlags.AlwaysAutoResize);

        ImGui.textColored(1.0f, 0.85f, 0.2f, 1.0f, "Quick controls:");
        ImGui.separator();

        if (editMode == EditMode.EDIT_MODE) {
            ImGui.bulletText("LMB - place / paint / remove voxel");
            ImGui.bulletText("B - add, X - remove, G - paint, I - pick color");
            ImGui.bulletText("Aim at the bounding box; bounds auto-expand");
        } else {
            ImGui.bulletText("LMB - select object");
            ImGui.bulletText("Shift + LMB - multi-select");
            ImGui.bulletText("M - Move gizmo, R - Scale gizmo");
        }

        ImGui.bulletText("RMB + Move - rotate camera");
        ImGui.bulletText("MMB + Move - pan camera");
        ImGui.bulletText("Scroll - zoom");
        ImGui.bulletText("WASD / Space / LCtrl - fly camera");
        ImGui.bulletText("F - focus selected model");

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
                    focusOnModel(newModel);
                    statusMessage = "Created model: " + newModelName.get();
                    showHints = true;
                    hintTimer = 10.0f;
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

            VoxelModel m = spawnManager.createNewModel(
                    editorUi.getActiveModelName(),
                    editorUi.getActiveModelWidth(),
                    editorUi.getActiveModelHeight(),
                    editorUi.getActiveModelDepth());

            if (m != null) {
                selectedObjects.add(m);
                focusOnModel(m);
                statusMessage = "Created model: " + editorUi.getActiveModelName();
            } else {
                statusMessage = "Failed to create model";
            }

            currentTool = Tool.ADD_VOXEL;
            editMode = EditMode.EDIT_MODE;
            showHints = true;
            hintTimer = 10.0f;
            return;
        }

        if (action == EditorUiState.UiAction.OPEN_MAP_WORKSPACE) {
            selectedObjects.clear();
            currentTool = Tool.SELECT;
            statusMessage = "Map workspace";
        }
    }

    private void focusOnModel(VoxelModel m) {
        Vector3f center = new Vector3f(
                m.transform.position.x + m.sizeX / 2f,
                m.transform.position.y + m.sizeY / 2f,
                m.transform.position.z + m.sizeZ / 2f);

        float maxDim = Math.max(m.sizeX, Math.max(m.sizeY, m.sizeZ));
        float dist = maxDim * 2.2f + 6.0f;

        camera.setPosition(new Vector3f(
                center.x + dist,
                center.y + dist * 0.7f,
                center.z + dist));
        camera.setYaw(-135f);
        camera.setPitch(-25f);
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
