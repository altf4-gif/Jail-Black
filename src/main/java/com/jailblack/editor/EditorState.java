package com.jailblack.editor;

import com.jailblack.engine.*;
import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
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

    public static final int TOOL_SELECT = 0;
    public static final int TOOL_MOVE = 1;
    public static final int TOOL_ROTATE = 2;
    public static final int TOOL_SCALE = 3;
    public static final int TOOL_ADD_VOXEL = 4;
    public static final int TOOL_REMOVE_VOXEL = 5;
    public static final int TOOL_PAINT = 6;
    public static final int TOOL_EYEDROPPER = 7;
    public static final int TOOL_FILL = 8;

    private static final int COL_X = 0xFF0000FF;
    private static final int COL_Y = 0xFF00FF00;
    private static final int COL_Z = 0xFFFF0000;
    private static final int COL_FREE = 0xFFFFFFFF;
    private static final int COL_PLANE = 0x80FFFF00;

    private static final float MAIN_MENU_HEIGHT = 24.0f;
    private static final float TOOLBAR_HEIGHT = 40.0f;
    private static final float TOP_OFFSET = MAIN_MENU_HEIGHT + TOOLBAR_HEIGHT;

    private final Window window;
    private final Camera camera;
    private final ShaderProgram basicShader, outlineShader, gridShader, ghostShader;
    private final Mesh boxMesh, gridMesh, cubeOutlineMesh, axisMesh;
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
    private int currentTool = TOOL_SELECT;
    private final Vector3f currentColor = new Vector3f(0.8f, 0.2f, 0.2f);
    private final int[] brushSize = {1};
    private boolean mirrorX = false;
    private final Vector3f ghostPosition = new Vector3f();
    private boolean showGhost = false, cursorInsideModel = false;
    private boolean showNewModelDialog = false;
    private final ImString newModelName = new ImString("NewModel", 64);
    private final int[] newModelSize = {8, 8, 32};
    private final ImString exportFilename = new ImString("model", 64);
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;
    private int hoveredVoxelX = -1, hoveredVoxelY = -1, hoveredVoxelZ = -1;
    private VoxelModel hoveredModel = null;
    private String statusMessage = "Ready";
    private float fps = 0, frameTimeAccum = 0;
    private int frameCount = 0;
    private int viewportW = 1280, viewportH = 720;
    private final Set<Integer> heldKeys = new HashSet<>();
    private boolean leftClickHandled = false;
    private float hintTimer = 10.0f;
    private boolean showHints = true;

    // Alt-mode: свободное размещение вокселей внутри модели
    private boolean altHeld = false;

    // Gizmo
    private boolean gizmoValid = false;
    private float gCX, gCY;
    private final float[][] gAxisTip = new float[3][2];
    private final float[][] gNegTip = new float[3][2];
    private final float[][] gPlaneC = new float[3][2];
    private float gK = 0.01f;
    private Matrix4f lastViewProj = new Matrix4f();
    private boolean gizmoMoveDrag = false;
    private int gizmoMoveMode = 0;
    private boolean gizmoScaleDrag = false;
    private int gizmoScaleSlot = -1;
    private float scaleAccum = 0f;
    private int scaleSteps = 0;
    private List<Vector3f> dragStartAll = null;
    private float pressMX, pressMY;
    private float gizmoImgX, gizmoImgY;

    // Toast
    private String errorMsg = null;
    private float errorTimer = 0f;

    // Explorer
    private final Set<String> expandedIds = new HashSet<>();
    private VoxelModel inspectedModel = null;
    private final Set<Integer> selectedVoxelIndices = new HashSet<>();
    private boolean renameDialogOpen = false;
    private VoxelModel renameTarget = null;
    private final ImString renameBuffer = new ImString("", 128);

    public EditorState(Window window, Camera camera,
                       ShaderProgram basicShader, ShaderProgram outlineShader,
                       ShaderProgram gridShader, ShaderProgram ghostShader,
                       Mesh boxMesh, Mesh gridMesh, Mesh cubeOutlineMesh, Mesh axisMesh,
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
        this.axisMesh = axisMesh;
        this.instancedRenderer = instancedRenderer;
        this.sceneFramebuffer = sceneFramebuffer;
        this.selectionManager = new SelectionManager(scene, selectedObjects, camera, window);
        this.spawnManager = new SpawnManager(scene, commandHistory, currentColor);
        this.mapManager = new MapManager(scene, commandHistory);
        ModelExportManager.ensureModelsDirectory();
        setupInputCallbacks();
    }

    public void setSceneFramebuffer(Framebuffer fb) { this.sceneFramebuffer = fb; }

    // ================= INPUT =================
    private void setupInputCallbacks() {
        GLFW.glfwSetCursorPosCallback(window.getHandle(), (win, xpos, ypos) -> {
            if (Double.isNaN(lastMouseX)) { lastMouseX = xpos; lastMouseY = ypos; return; }
            float dx = (float) (xpos - lastMouseX);
            float dy = (float) (ypos - lastMouseY);
            lastMouseX = xpos; lastMouseY = ypos;

            boolean lmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean mmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
            boolean rmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

            if (lmb && isMouseOverViewport() && gizmoMoveDrag) {
                applyMoveDrag(dx, dy);
            } else if (lmb && isMouseOverViewport() && gizmoScaleDrag) {
                scaleAccum += dot2(dx, dy, scaleDirX(), scaleDirY());
                scaleSteps = Math.round(scaleAccum);
                if (scaleSteps > 32) scaleSteps = 32;
                if (scaleSteps < -32) scaleSteps = -32;
            } else if (rmb) {
                camera.rotate(dx, dy);
            } else if (mmb) {
                camera.pan(dx, dy);
            }
        });

        GLFW.glfwSetMouseButtonCallback(window.getHandle(), (win, button, action, mods) -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            if (action == GLFW.GLFW_PRESS) {
                if (isMouseOverViewport()) {
                    pressMX = ImGui.getMousePosX();
                    pressMY = ImGui.getMousePosY();
                    if (!selectedObjects.isEmpty() && (currentTool == TOOL_MOVE || currentTool == TOOL_SCALE)) {
                        float mx = ImGui.getMousePosX();
                        float my = ImGui.getMousePosY();
                        if (currentTool == TOOL_MOVE) {
                            int mode = pickMoveMode(mx, my);
                            if (mode != 0) {
                                gizmoMoveDrag = true;
                                gizmoMoveMode = mode;
                                dragStartAll = copyPositions();
                            }
                        } else if (currentTool == TOOL_SCALE) {
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
            } else if (action == GLFW.GLFW_RELEASE) {
                if (gizmoMoveDrag) { gizmoMoveDrag = false; pushMoveCommand(); }
                if (gizmoScaleDrag) { gizmoScaleDrag = false; applyScaleSteps(); }
            }
        });
    }

    private List<Vector3f> copyPositions() {
        List<Vector3f> out = new ArrayList<>();
        for (VoxelModel m : selectedObjects) out.add(new Vector3f(m.transform.position));
        return out;
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

    private void applyMoveDrag(float dx, float dy) {
        if (dragStartAll == null || selectedObjects.isEmpty()) return;
        Vector3f base = dragStartAll.get(0);
        Vector3f deltaInt = new Vector3f();
        if (gizmoMoveMode >= 1 && gizmoMoveMode <= 3) {
            int a = gizmoMoveMode - 1;
            float sx = gAxisTip[a][0] - gCX, sy = gAxisTip[a][1] - gCY;
            float len = (float) Math.sqrt(sx * sx + sy * sy);
            if (len < 1e-4f) return;
            sx /= len; sy /= len;
            float world = dot2(ImGui.getMousePosX() - pressMX, ImGui.getMousePosY() - pressMY, sx, sy) * gK;
            int steps = Math.round(world);
            if (a == 0) deltaInt.set(steps, 0, 0);
            else if (a == 1) deltaInt.set(0, steps, 0);
            else deltaInt.set(0, 0, steps);
        } else if (gizmoMoveMode >= 4 && gizmoMoveMode <= 6) {
            int u = (gizmoMoveMode == 4) ? 0 : (gizmoMoveMode == 5 ? 0 : 1);
            int v = (gizmoMoveMode == 4) ? 1 : (gizmoMoveMode == 5 ? 2 : 2);
            float sux = gAxisTip[u][0] - gCX, suy = gAxisTip[u][1] - gCY;
            float svx = gAxisTip[v][0] - gCX, svy = gAxisTip[v][1] - gCY;
            float lu = (float) Math.sqrt(sux * sux + suy * suy);
            float lv = (float) Math.sqrt(svx * svx + svy * svy);
            if (lu < 1e-4f || lv < 1e-4f) return;
            sux /= lu; suy /= lu; svx /= lv; svy /= lv;
            float mx = ImGui.getMousePosX() - pressMX;
            float my = ImGui.getMousePosY() - pressMY;
            float det = sux * svy - suy * svx;
            if (Math.abs(det) < 1e-4f) return;
            float a1 = (mx * svy - my * svx) / det * gK;
            float a2 = (sux * my - suy * mx) / det * gK;
            int i1 = Math.round(a1), i2 = Math.round(a2);
            if (u == 0) deltaInt.x = i1; else if (u == 1) deltaInt.y = i1; else deltaInt.z = i1;
            if (v == 0) deltaInt.x = i2; else if (v == 1) deltaInt.y = i2; else deltaInt.z = i2;
        } else if (gizmoMoveMode == 7) {
            float wx = (ImGui.getMousePosX() - pressMX) * gK;
            float wy = (ImGui.getMousePosY() - pressMY) * gK;
            Vector3f right = camera.getRight();
            Vector3f up = camera.getUp();
            Vector3f w = new Vector3f(right).mul(wx).add(new Vector3f(up).mul(-wy));
            deltaInt.set(Math.round(base.x + w.x) - Math.round(base.x),
                    Math.round(base.y + w.y) - Math.round(base.y),
                    Math.round(base.z + w.z) - Math.round(base.z));
        }
        for (int i = 0; i < selectedObjects.size(); i++) {
            Vector3f st = dragStartAll.get(i);
            selectedObjects.get(i).transform.position.set(
                    Math.round(st.x) + deltaInt.x,
                    Math.round(st.y) + deltaInt.y,
                    Math.round(st.z) + deltaInt.z);
            selectedObjects.get(i).markAABBDirty();
        }
    }

    private void pushMoveCommand() {
        if (dragStartAll == null || selectedObjects.isEmpty()) return;
        List<VoxelModel> models = new ArrayList<>(selectedObjects);
        Vector3f[] oldP = new Vector3f[models.size()];
        Vector3f[] newP = new Vector3f[models.size()];
        boolean changed = false;
        for (int i = 0; i < models.size(); i++) {
            oldP[i] = dragStartAll.get(i);
            newP[i] = new Vector3f(models.get(i).transform.position);
            if (!oldP[i].equals(newP[i])) changed = true;
        }
        dragStartAll = null;
        if (changed) commandHistory.execute(new Commands.MoveCommand(models, oldP, newP));
    }

    private void applyScaleSteps() {
        int steps = scaleSteps;
        scaleSteps = 0;
        if (steps == 0 || selectedObjects.isEmpty() || gizmoScaleSlot < 0) return;
        VoxelModel model = selectedObjects.get(0);
        int axis = gizmoScaleSlot / 2;
        boolean positiveSide = (gizmoScaleSlot % 2 == 0);
        List<Voxel> old = new ArrayList<>();
        if (model.voxels != null) for (Voxel v : model.voxels) old.add(v.copy());
        List<Voxel> cur = new ArrayList<>();
        for (Voxel v : old) cur.add(v.copy());
        int n = Math.abs(steps);
        for (int s = 0; s < n; s++) {
            if (steps > 0) cur = addLayer(cur, axis, positiveSide);
            else {
                List<Voxel> cut = removeLayer(cur, axis, positiveSide);
                if (cut == null) break;
                cur = cut;
            }
        }
        gizmoScaleSlot = -1;
        commandHistory.execute(new Commands.VoxelEditCommand(model, old, cur));
    }

    private int coordOf(Voxel v, int axis) { return axis == 0 ? v.x : (axis == 1 ? v.y : v.z); }

    private List<Voxel> addLayer(List<Voxel> src, int axis, boolean positiveSide) {
        int edge = positiveSide ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Voxel v : src) {
            int c = coordOf(v, axis);
            if (positiveSide ? c > edge : c < edge) edge = c;
        }
        if (edge == Integer.MIN_VALUE || edge == Integer.MAX_VALUE) return src;
        List<Voxel> out = new ArrayList<>();
        for (Voxel v : src) out.add(v.copy());
        for (Voxel v : src) {
            if (coordOf(v, axis) == edge) {
                Voxel nv = new Voxel(v.x, v.y, v.z, v.color, v.getAlpha());
                if (axis == 0) nv.x = edge + (positiveSide ? 1 : -1);
                else if (axis == 1) nv.y = edge + (positiveSide ? 1 : -1);
                else nv.z = edge + (positiveSide ? 1 : -1);
                out.add(nv);
            }
        }
        return out;
    }

    private List<Voxel> removeLayer(List<Voxel> src, int axis, boolean positiveSide) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (Voxel v : src) {
            int c = coordOf(v, axis);
            if (c < min) min = c;
            if (c > max) max = c;
        }
        if (min == max) return null;
        int edge = positiveSide ? max : min;
        List<Voxel> out = new ArrayList<>();
        for (Voxel v : src) {
            if (coordOf(v, axis) != edge) out.add(v.copy());
        }
        return out;
    }

    private void tryActivateRotate() {
        currentTool = TOOL_ROTATE;
        errorMsg = "Voxel rotation is not available yet. Feature is under development.";
        errorTimer = 3.0f;
    }

    private boolean pressedOnce(int key) {
        boolean down = GLFW.glfwGetKey(window.getHandle(), key) == GLFW.GLFW_PRESS;
        if (down && !heldKeys.contains(key)) { heldKeys.add(key); return true; }
        if (!down) heldKeys.remove(key);
        return false;
    }

    private boolean isVoxelTool(int t) {
        return t == TOOL_ADD_VOXEL || t == TOOL_REMOVE_VOXEL || t == TOOL_PAINT
                || t == TOOL_EYEDROPPER || t == TOOL_FILL;
    }

    private void handleInput(float dt) {
        if (errorTimer > 0) errorTimer -= dt;
        if (hintTimer > 0f) { hintTimer -= dt; if (hintTimer <= 0f) showHints = false; }

        // Отслеживаем Alt для свободного размещения
        altHeld = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        boolean uiWantsKeyboard = ImGui.getIO().getWantCaptureKeyboard();
        if (uiWantsKeyboard) { heldKeys.clear(); } else {
            // === Объектные инструменты: цифры 1-4 ===
            if (pressedOnce(GLFW.GLFW_KEY_1)) currentTool = TOOL_SELECT;
            if (pressedOnce(GLFW.GLFW_KEY_2)) currentTool = TOOL_MOVE;
            if (pressedOnce(GLFW.GLFW_KEY_3)) tryActivateRotate();
            if (pressedOnce(GLFW.GLFW_KEY_4)) currentTool = TOOL_SCALE;

            // === Воксельные инструменты ===
            if (pressedOnce(GLFW.GLFW_KEY_B)) currentTool = TOOL_ADD_VOXEL;
            if (pressedOnce(GLFW.GLFW_KEY_X)) currentTool = TOOL_REMOVE_VOXEL;
            if (pressedOnce(GLFW.GLFW_KEY_G)) currentTool = TOOL_PAINT;
            if (pressedOnce(GLFW.GLFW_KEY_I)) currentTool = TOOL_EYEDROPPER;
            if (pressedOnce(GLFW.GLFW_KEY_F) && !selectedObjects.isEmpty()) currentTool = TOOL_FILL;

            // === H — Focus ===
            if (pressedOnce(GLFW.GLFW_KEY_H) && !selectedObjects.isEmpty()) {
                Vector3f center = new Vector3f();
                for (VoxelModel m : selectedObjects) center.add(m.transform.position);
                center.mul(1.0f / selectedObjects.size());
                camera.setPosition(new Vector3f(center.x - 10, center.y + 5, center.z));
                camera.setMode(Camera.Mode.FLY);
                statusMessage = "Focused on selected";
            }

            if (pressedOnce(GLFW.GLFW_KEY_LEFT_BRACKET)) brushSize[0] = Math.max(1, brushSize[0] - 1);
            if (pressedOnce(GLFW.GLFW_KEY_RIGHT_BRACKET)) brushSize[0] = Math.min(5, brushSize[0] + 1);
            boolean ctrl = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shift = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
            if (shift && pressedOnce(GLFW.GLFW_KEY_D) && !selectedObjects.isEmpty())
                spawnManager.duplicateModel(selectedObjects.get(0));
            if (ctrl && pressedOnce(GLFW.GLFW_KEY_Z)) {
                if (shift) commandHistory.redo(); else commandHistory.undo();
            }
            if (pressedOnce(GLFW.GLFW_KEY_DELETE) && !selectedObjects.isEmpty()) {
                commandHistory.execute(new Commands.DeleteCommand(scene, selectedObjects));
                selectedObjects.clear();
                selectedVoxelIndices.clear();
                inspectedModel = null;
            }
        }

        // === Движение камеры: WASD ===
        float vel = 10.0f * dt;
        Vector3f front = camera.getFront(), right = camera.getRight(), camPos = camera.getPosition();
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
            if (!gizmoMoveDrag && !gizmoScaleDrag) {
                if (!ImGui.getIO().getWantCaptureMouse() || isMouseOverViewport()) {
                    if (!leftClickHandled) { handleViewportClick(); leftClickHandled = true; }
                }
            }
        } else { leftClickHandled = false; }
    }

    private boolean isMouseOverViewport() {
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), mx, my);
        int vpX = 250, vpY = (int) TOP_OFFSET;
        int vpW = window.getWidth() - 500, vpH = window.getHeight() - (int) TOP_OFFSET - 40;
        return mx[0] >= vpX && mx[0] <= vpX + vpW && my[0] >= vpY && my[0] <= vpY + vpH;
    }

    private Vector3f getMouseRayDir() {
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), mx, my);
        float mX = (float)(mx[0] - 250);
        float mY = (float)(my[0] - TOP_OFFSET);
        int vpW = window.getWidth() - 500;
        int vpH = window.getHeight() - (int) TOP_OFFSET - 40;
        if (mX < 0 || mX >= vpW || mY < 0 || mY >= vpH) { mX = vpW / 2f; mY = vpH / 2f; }
        return camera.getRayDirection(mX, mY, vpW, vpH);
    }

    // ================= GHOST =================
    private void updateGhostVoxel() {
        showGhost = false;
        cursorInsideModel = false;

        // Ghost нужен только для инструментов редактирования
        if (!isVoxelTool(currentTool)) return;
        if (selectedObjects.isEmpty()) return;

        hoveredModel = selectedObjects.get(0);
        if (hoveredModel == null) return;

        Vector3f camPos = camera.getPosition();
        Vector3f camDir = getMouseRayDir();
        Vector3f modelPos = hoveredModel.transform.position;
        Vector3f localPos = new Vector3f(camPos).sub(modelPos);
        int sx = hoveredModel.sizeX, sy = hoveredModel.sizeY, sz = hoveredModel.sizeZ;

        cursorInsideModel = camPos.x >= modelPos.x && camPos.x < modelPos.x + sx
                && camPos.y >= modelPos.y && camPos.y < modelPos.y + sy
                && camPos.z >= modelPos.z && camPos.z < modelPos.z + sz;

        // DDA raycast для поиска вокселя
        int[] hit = ddaRaycast(localPos, camDir, sx, sy, sz, hoveredModel);

        if (hit != null) {
            hoveredVoxelX = hit[0];
            hoveredVoxelY = hit[1];
            hoveredVoxelZ = hit[2];

            if (currentTool == TOOL_ADD_VOXEL) {
                if (altHeld) {
                    // === ALT ЗАЖАТ: свободное размещение ===
                    // Используем плоскость через центр модели
                    Vector3f center = new Vector3f(modelPos.x + sx / 2.0f, modelPos.y + sy / 2.0f, modelPos.z + sz / 2.0f);
                    float t = new Vector3f(center).sub(camPos).dot(camDir);
                    if (t > 0.0f) {
                        Vector3f world = new Vector3f(camPos).add(new Vector3f(camDir).mul(t));
                        int cx = (int) Math.floor(world.x - modelPos.x);
                        int cy = (int) Math.floor(world.y - modelPos.y);
                        int cz = (int) Math.floor(world.z - modelPos.z);
                        if (cx >= 0 && cx < sx && cy >= 0 && cy < sy && cz >= 0 && cz < sz) {
                            ghostPosition.set(cx, cy, cz);
                            showGhost = true;
                        }
                    }
                } else {
                    // === БЕЗ ALT: привязка к грани существующего вокселя ===
                    // Разрешаем ячейку ВНЕ текущих границ — объём расширится при клике
                    int gx = hit[0] + hit[3];
                    int gy = hit[1] + hit[4];
                    int gz = hit[2] + hit[5];
                    if (gx >= -32 && gx < sx + 32 && gy >= -32 && gy < sy + 32 && gz >= -32 && gz < sz + 32) {
                        ghostPosition.set(gx, gy, gz);
                        showGhost = true;
                    }
                }
            } else {
                // Для Remove/Paint/Eyedropper/Fill - работаем с найденным вокселем
                ghostPosition.set(hit[0], hit[1], hit[2]);
                showGhost = true;
            }
        } else {
            // Луч не попал ни в один воксель
            if (currentTool == TOOL_ADD_VOXEL) {
                // Fallback: плоскость через центр модели
                Vector3f center = new Vector3f(modelPos.x + sx / 2.0f, modelPos.y + sy / 2.0f, modelPos.z + sz / 2.0f);
                float t = new Vector3f(center).sub(camPos).dot(camDir);
                if (t > 0.0f) {
                    Vector3f world = new Vector3f(camPos).add(new Vector3f(camDir).mul(t));
                    int cx = (int) Math.floor(world.x - modelPos.x);
                    int cy = (int) Math.floor(world.y - modelPos.y);
                    int cz = (int) Math.floor(world.z - modelPos.z);
                    if (cx >= 0 && cx < sx && cy >= 0 && cy < sy && cz >= 0 && cz < sz) {
                        ghostPosition.set(cx, cy, cz);
                        hoveredVoxelX = cx;
                        hoveredVoxelY = cy;
                        hoveredVoxelZ = cz;
                        showGhost = true;
                    }
                }
            }
        }

        if (showGhost) {
            String mode = altHeld ? " [FREE]" : " [SNAP]";
            statusMessage = "Target: [" + (int) ghostPosition.x + ", " + (int) ghostPosition.y + ", " + (int) ghostPosition.z + "]" + mode;
        }
    }

    private int[] ddaRaycast(Vector3f origin, Vector3f dir, int sizeX, int sizeY, int sizeZ, VoxelModel model) {
        float tMaxX, tMaxY, tMaxZ, tDeltaX, tDeltaY, tDeltaZ;
        int stepX, stepY, stepZ;
        int voxelX = (int) Math.floor(origin.x), voxelY = (int) Math.floor(origin.y), voxelZ = (int) Math.floor(origin.z);
        if (dir.x > 0) { stepX = 1; tDeltaX = 1.0f / dir.x; tMaxX = (voxelX + 1 - origin.x) / dir.x; }
        else if (dir.x < 0) { stepX = -1; tDeltaX = -1.0f / dir.x; tMaxX = (voxelX - origin.x) / dir.x; }
        else { stepX = 0; tDeltaX = Float.MAX_VALUE; tMaxX = Float.MAX_VALUE; }
        if (dir.y > 0) { stepY = 1; tDeltaY = 1.0f / dir.y; tMaxY = (voxelY + 1 - origin.y) / dir.y; }
        else if (dir.y < 0) { stepY = -1; tDeltaY = -1.0f / dir.y; tMaxY = (voxelY - origin.y) / dir.y; }
        else { stepY = 0; tDeltaY = Float.MAX_VALUE; tMaxY = Float.MAX_VALUE; }
        if (dir.z > 0) { stepZ = 1; tDeltaZ = 1.0f / dir.z; tMaxZ = (voxelZ + 1 - origin.z) / dir.z; }
        else if (dir.z < 0) { stepZ = -1; tDeltaZ = -1.0f / dir.z; tMaxZ = (voxelZ - origin.z) / dir.z; }
        else { stepZ = 0; tDeltaZ = Float.MAX_VALUE; tMaxZ = Float.MAX_VALUE; }
        int nx = 0, ny = 1, nz = 0;
        for (int step = 0; step < 1024; step++) {
            boolean inside = voxelX >= 0 && voxelX < sizeX && voxelY >= 0 && voxelY < sizeY && voxelZ >= 0 && voxelZ < sizeZ;
            if (inside && model != null && model.getVoxelAt(voxelX, voxelY, voxelZ) != null)
                return new int[]{voxelX, voxelY, voxelZ, nx, ny, nz};
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) { voxelX += stepX; tMaxX += tDeltaX; nx = -stepX; ny = 0; nz = 0; }
                else { voxelZ += stepZ; tMaxZ += tDeltaZ; nx = 0; ny = 0; nz = -stepZ; }
            } else {
                if (tMaxY < tMaxZ) { voxelY += stepY; tMaxY += tDeltaY; nx = 0; ny = -stepY; nz = 0; }
                else { voxelZ += stepZ; tMaxZ += tDeltaZ; nx = 0; ny = 0; nz = -stepZ; }
            }
            if (tMaxX > 1e9f && tMaxY > 1e9f && tMaxZ > 1e9f) break;
        }
        return null;
    }

    // ================= CLICKS =================
    private void handleViewportClick() {
        if (currentTool == TOOL_SELECT) {
            VoxelModel picked = selectionManager.pick();
            boolean shift = ImGui.getIO().getKeyShift();
            if (picked != null) {
                if (shift) { if (selectedObjects.contains(picked)) selectedObjects.remove(picked); else selectedObjects.add(picked); }
                else { selectedObjects.clear(); selectedObjects.add(picked); }
                statusMessage = "Selected: " + picked.name;
            } else if (!shift) selectedObjects.clear();
        } else if (isVoxelTool(currentTool)) {
            handleVoxelEdit();
        }
    }

    private void handleVoxelEdit() {
        if (!showGhost || hoveredModel == null) { statusMessage = "No valid target"; return; }
        int bs = brushSize[0];
        if (currentTool == TOOL_ADD_VOXEL) {
            int x = (int) ghostPosition.x, y = (int) ghostPosition.y, z = (int) ghostPosition.z;
            int preSX = hoveredModel.sizeX;

            // Собираем все целевые ячейки (brush + mirror) в локальных координатах
            List<int[]> cells = new ArrayList<>();
            for (int dx = 0; dx < bs; dx++)
                for (int dy = 0; dy < bs; dy++)
                    for (int dz = 0; dz < bs; dz++) {
                        cells.add(new int[]{x + dx, y + dy, z + dz});
                        if (mirrorX) cells.add(new int[]{preSX - 1 - (x + dx), y + dy, z + dz});
                    }

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int[] c : cells) {
                minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
                minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
                minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
            }

            // Снапшот ДО изменений (для Undo)
            List<Voxel> oldVoxels = copyVoxels(hoveredModel);
            Vector3f oldPos = new Vector3f(hoveredModel.transform.position);
            int osx = hoveredModel.sizeX, osy = hoveredModel.sizeY, osz = hoveredModel.sizeZ;

            // Сдвиг, который применит expandToInclude к отрицательным координатам
            int shiftX = Math.max(0, -minX);
            int shiftY = Math.max(0, -minY);
            int shiftZ = Math.max(0, -minZ);

            // Расширяем объём модели, если ставим снаружи (иначе — no-op)
            hoveredModel.expandToInclude(minX, minY, minZ, maxX, maxY, maxZ);

            // Ставим воксели уже в новой локальной системе координат
            for (int[] c : cells) {
                hoveredModel.addVoxel(c[0] + shiftX, c[1] + shiftY, c[2] + shiftZ, currentColor);
            }

            commandHistory.execute(new Commands.ModelEditCommand(
                    hoveredModel, oldVoxels, copyVoxels(hoveredModel),
                    oldPos, new Vector3f(hoveredModel.transform.position),
                    osx, osy, osz,
                    hoveredModel.sizeX, hoveredModel.sizeY, hoveredModel.sizeZ));

            statusMessage = "Added voxel at [" + x + ", " + y + ", " + z + "]"
                    + ((shiftX + shiftY + shiftZ > 0 || maxX >= osx || maxY >= osy || maxZ >= osz)
                       ? " (bounds expanded)" : "");
        } else if (currentTool == TOOL_REMOVE_VOXEL) {
            int x = hoveredVoxelX, y = hoveredVoxelY, z = hoveredVoxelZ;
            List<Voxel> oldVoxels = copyVoxels(hoveredModel);
            for (int dx = 0; dx < bs; dx++) for (int dy = 0; dy < bs; dy++) for (int dz = 0; dz < bs; dz++) {
                hoveredModel.removeVoxel(x+dx, y+dy, z+dz);
                if (mirrorX) hoveredModel.removeVoxel(hoveredModel.sizeX-1-(x+dx), y+dy, z+dz);
            }
            pushVoxelEdit(hoveredModel, oldVoxels, "Removed voxel at ["+x+","+y+","+z+"]");
        } else if (currentTool == TOOL_PAINT) {
            int x = hoveredVoxelX, y = hoveredVoxelY, z = hoveredVoxelZ;
            Voxel v = hoveredModel.getVoxelAt(x, y, z);
            if (v != null) {
                Vector3f oldColor = Voxel.hexToColor(v.color);
                commandHistory.execute(new Commands.PaintVoxelCommand(hoveredModel, x, y, z, oldColor, currentColor));
                statusMessage = "Painted voxel at ["+x+","+y+","+z+"]";
            }
        } else if (currentTool == TOOL_EYEDROPPER) {
            int x = hoveredVoxelX, y = hoveredVoxelY, z = hoveredVoxelZ;
            Voxel v = hoveredModel.getVoxelAt(x, y, z);
            if (v != null) {
                currentColor.set(Voxel.hexToColor(v.color));
                spawnManager.setCurrentColor(currentColor);
                statusMessage = "Picked color " + Voxel.colorToHex(v.color);
            }
        } else if (currentTool == TOOL_FILL) {
            handleFloodFill();
        }
    }

    private void handleFloodFill() {
        if (hoveredModel == null) return;
        int x = hoveredVoxelX, y = hoveredVoxelY, z = hoveredVoxelZ;
        Voxel target = hoveredModel.getVoxelAt(x, y, z);
        if (target == null) return;
        String targetColor = target.color;
        String newHex = Voxel.toHex(currentColor);
        if (targetColor.equals(newHex)) return;
        List<Voxel> oldVoxels = copyVoxels(hoveredModel);
        Set<Long> visited = new HashSet<>();
        List<int[]> queue = new ArrayList<>();
        queue.add(new int[]{x, y, z});
        while (!queue.isEmpty()) {
            int[] pos = queue.remove(queue.size() - 1);
            long key = pos[0] + pos[1]*1000L + pos[2]*1000000L;
            if (visited.contains(key)) continue;
            visited.add(key);
            Voxel v = hoveredModel.getVoxelAt(pos[0], pos[1], pos[2]);
            if (v == null || !v.color.equals(targetColor)) continue;
            v.color = newHex;
            queue.add(new int[]{pos[0]+1, pos[1], pos[2]});
            queue.add(new int[]{pos[0]-1, pos[1], pos[2]});
            queue.add(new int[]{pos[0], pos[1]+1, pos[2]});
            queue.add(new int[]{pos[0], pos[1]-1, pos[2]});
            queue.add(new int[]{pos[0], pos[1], pos[2]+1});
            queue.add(new int[]{pos[0], pos[1], pos[2]-1});
        }
        hoveredModel.markAABBDirty();
        pushVoxelEdit(hoveredModel, oldVoxels, "Flood fill from ["+x+","+y+","+z+"]");
    }

    private List<Voxel> copyVoxels(VoxelModel model) {
        List<Voxel> out = new ArrayList<>();
        if (model.voxels != null) for (Voxel v : model.voxels) out.add(v.copy());
        return out;
    }

    private void pushVoxelEdit(VoxelModel model, List<Voxel> oldVoxels, String msg) {
        List<Voxel> newVoxels = copyVoxels(model);
        commandHistory.execute(new Commands.VoxelEditCommand(model, oldVoxels, newVoxels));
        statusMessage = msg;
    }

    // ================= RENDER =================
    private boolean worldToScreen(Vector3f world, float[] out) {
        Vector4f p = new Vector4f(world, 1.0f).mul(lastViewProj);
        if (p.w <= 0) return false;
        float nx = p.x / p.w;
        float ny = p.y / p.w;
        out[0] = (nx * 0.5f + 0.5f) * viewportW;
        out[1] = (1f - (ny * 0.5f + 0.5f)) * viewportH;
        return true;
    }

    private void renderScene() {
        int w = viewportW, h = viewportH;
        if (w <= 0 || h <= 0) return;
        sceneFramebuffer.resize(w, h);
        sceneFramebuffer.bind();
        glClearColor(0.15f, 0.15f, 0.18f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        float aspect = (float) w / (float) h;
        lastViewProj = camera.getViewProjection(aspect);

        gridShader.use();
        gridShader.setUniform("uViewProj", lastViewProj);
        gridMesh.draw();

        outlineShader.use();
        outlineShader.setUniform("uViewProj", lastViewProj);
        outlineShader.setUniform("uColor", new Vector3f(0.6f, 0.6f, 0.6f));
        outlineShader.setUniform("uModel", new Matrix4f().translation(0, 0.01f, 0));
        axisMesh.draw();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        basicShader.use();
        basicShader.setUniform("uViewProj", lastViewProj);
        basicShader.setUniform("uInstanced", 1);
        basicShader.setUniform("uMode", 0);
        basicShader.setUniform("uUseTexture", 0);
        basicShader.setUniform("uAlpha", 1.0f);
        basicShader.setUniform("uUnlit", 0);
        basicShader.setUniform("uLightDir", new Vector3f(0.5f, 1.0f, 0.3f).normalize());
        basicShader.setUniform("uCamPos", camera.getPosition());
        instancedRenderer.render(scene.getModels());
        glDisable(GL_BLEND);

        // Ghost voxel + cyan wireframe
        if (showGhost && hoveredModel != null) {
            Vector3f mp = hoveredModel.transform.position;
            float gx = mp.x + ghostPosition.x, gy = mp.y + ghostPosition.y, gz = mp.z + ghostPosition.z;
            ghostShader.use();
            ghostShader.setUniform("uViewProj", lastViewProj);
            ghostShader.setUniform("uColor", currentColor);
            ghostShader.setUniform("uAlpha", 0.45f);
            ghostShader.setUniform("uLightDir", new Vector3f(0.5f, 1.0f, 0.3f).normalize());
            ghostShader.setUniform("uModel", new Matrix4f().translation(gx, gy, gz));
            boxMesh.draw();
            outlineShader.use();
            outlineShader.setUniform("uViewProj", lastViewProj);
            outlineShader.setUniform("uColor", new Vector3f(0.2f, 1.0f, 1.0f));
            outlineShader.setUniform("uModel", new Matrix4f().translation(gx, gy, gz).scale(1.01f));
            cubeOutlineMesh.draw();
        }

        // Bounding box выделенной модели: зелёный если камера внутри, красный если снаружи
        if (isVoxelTool(currentTool) && !selectedObjects.isEmpty()) {
            VoxelModel model = selectedObjects.get(0);
            outlineShader.use();
            outlineShader.setUniform("uViewProj", lastViewProj);
            outlineShader.setUniform("uColor", cursorInsideModel
                    ? new Vector3f(0.2f, 1.0f, 0.2f)
                    : new Vector3f(1.0f, 0.25f, 0.25f));
            outlineShader.setUniform("uModel", new Matrix4f()
                    .translation(model.transform.position.x + model.sizeX / 2.0f,
                            model.transform.position.y + model.sizeY / 2.0f,
                            model.transform.position.z + model.sizeZ / 2.0f)
                    .scale(model.sizeX, model.sizeY, model.sizeZ));
            cubeOutlineMesh.draw();
        }

        // Жёлтые контуры вокселей выделенных моделей
        if (!selectedObjects.isEmpty()) {
            outlineShader.use();
            outlineShader.setUniform("uViewProj", lastViewProj);
            outlineShader.setUniform("uColor", new Vector3f(1.0f, 0.85f, 0.2f));
            for (VoxelModel obj : selectedObjects) {
                if (obj.voxels == null) continue;
                Matrix4f modelMatrix = obj.transform.getModelMatrix();
                for (Voxel v : obj.voxels) {
                    outlineShader.setUniform("uModel", new Matrix4f(modelMatrix).translate(v.x, v.y, v.z).scale(1.02f));
                    cubeOutlineMesh.draw();
                }
            }
        }

        // Бирюзовый контур вокселей, выбранных в Explorer
        if (inspectedModel != null && !selectedVoxelIndices.isEmpty()) {
            outlineShader.use();
            outlineShader.setUniform("uViewProj", lastViewProj);
            outlineShader.setUniform("uColor", new Vector3f(0.0f, 1.0f, 1.0f));
            Matrix4f im = inspectedModel.transform.getModelMatrix();
            for (int idx : selectedVoxelIndices) {
                if (idx < 0 || idx >= inspectedModel.voxels.size()) continue;
                Voxel v = inspectedModel.voxels.get(idx);
                outlineShader.setUniform("uModel", new Matrix4f(im).translate(v.x, v.y, v.z).scale(1.15f));
                cubeOutlineMesh.draw();
            }
        }

        sceneFramebuffer.unbind();
        glViewport(0, 0, window.getWidth(), window.getHeight());
    }

    private void renderUI() {
        renderToolbar();
        renderExplorer();
        renderProperties();
        renderViewport();
        renderStatusBar();
        renderHintsOverlay();
        if (showNewModelDialog) renderNewModelDialog();
        if (renameDialogOpen) renderRenameDialog();
        renderErrorToast();
    }

    private String toolName() {
        switch (currentTool) {
            case TOOL_SELECT: return "Select (1)";
            case TOOL_MOVE: return "Move (2)";
            case TOOL_ROTATE: return "Rotate (3)";
            case TOOL_SCALE: return "Scale (4)";
            case TOOL_ADD_VOXEL: return "Add (B)";
            case TOOL_REMOVE_VOXEL: return "Remove (X)";
            case TOOL_PAINT: return "Paint (G)";
            case TOOL_EYEDROPPER: return "Pick (I)";
            case TOOL_FILL: return "Fill (F)";
            default: return "?";
        }
    }

    private void renderToolbar() {
        ImGui.setNextWindowPos(0, MAIN_MENU_HEIGHT, ImGuiCond.Always);
        ImGui.setNextWindowSize(window.getWidth(), TOOLBAR_HEIGHT, ImGuiCond.Always);
        ImGui.begin("Toolbar", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBackground);

        ImGui.setNextItemWidth(160);
        ImInt modeIdx = new ImInt(editorMode.ordinal());
        if (ImGui.combo("##EditorMode", modeIdx, new String[]{"Model Editor", "Map Editor"}))
            editorMode = EditorMode.values()[modeIdx.get()];
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        // Объектные инструменты (цифры 1-4)
        if (ImGui.button(currentTool == TOOL_SELECT ? "> Select (1)" : "Select (1)")) currentTool = TOOL_SELECT;
        ImGui.sameLine();
        if (ImGui.button(currentTool == TOOL_MOVE ? "> Move (2)" : "Move (2)")) currentTool = TOOL_MOVE;
        ImGui.sameLine();
        if (ImGui.button(currentTool == TOOL_ROTATE ? "> Rotate (3)" : "Rotate (3)")) tryActivateRotate();
        ImGui.sameLine();
        if (ImGui.button(currentTool == TOOL_SCALE ? "> Scale (4)" : "Scale (4)")) currentTool = TOOL_SCALE;
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        // Воксельные инструменты (буквы)
        if (ImGui.button(currentTool == TOOL_ADD_VOXEL ? "> Add (B)" : "Add (B)")) currentTool = TOOL_ADD_VOXEL;
        ImGui.sameLine();
        if (ImGui.button(currentTool == TOOL_REMOVE_VOXEL ? "> Remove (X)" : "Remove (X)")) currentTool = TOOL_REMOVE_VOXEL;
        ImGui.sameLine();
        if (ImGui.button(currentTool == TOOL_PAINT ? "> Paint (G)" : "Paint (G)")) currentTool = TOOL_PAINT;
        ImGui.sameLine();
        if (ImGui.button(currentTool == TOOL_EYEDROPPER ? "> Pick (I)" : "Pick (I)")) currentTool = TOOL_EYEDROPPER;
        ImGui.sameLine();
        if (ImGui.button(currentTool == TOOL_FILL ? "> Fill (F)" : "Fill (F)")) currentTool = TOOL_FILL;
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        ImGui.text("Brush:"); ImGui.sameLine();
        ImGui.setNextItemWidth(90);
        ImGui.sliderInt("##BrushSize", brushSize, 1, 5);
        ImGui.sameLine();
        mirrorX = ImGui.checkbox("Mirror X", mirrorX);
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        if (commandHistory.canUndo()) { if (ImGui.button("Undo")) commandHistory.undo(); }
        else { ImGui.beginDisabled(); ImGui.button("Undo"); ImGui.endDisabled(); }
        ImGui.sameLine();
        if (commandHistory.canRedo()) { if (ImGui.button("Redo")) commandHistory.redo(); }
        else { ImGui.beginDisabled(); ImGui.button("Redo"); ImGui.endDisabled(); }
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();

        if (ImGui.button("New 8x8x32")) {
            newModelName.set("Prop_" + (scene.getModels().size() + 1));
            newModelSize[0] = 8; newModelSize[1] = 32; newModelSize[2] = 8;
            showNewModelDialog = true; ImGui.openPopup("Create New Model");
        }
        ImGui.sameLine();
        if (ImGui.button("New Custom")) {
            newModelName.set("Model_" + (scene.getModels().size() + 1));
            showNewModelDialog = true; ImGui.openPopup("Create New Model");
        }
        ImGui.sameLine();
        if (!selectedObjects.isEmpty() && ImGui.button("Duplicate (Shift+D)"))
            spawnManager.duplicateModel(selectedObjects.get(0));
        ImGui.sameLine();
        if (!selectedObjects.isEmpty() && ImGui.button("Export")) {
            ModelExportManager.saveModel(selectedObjects.get(0), exportFilename.get());
            statusMessage = "Exported " + exportFilename.get() + ".jbm";
        }
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();
        ImGui.text("Objects: " + scene.getModels().size() + " | Tool: " + toolName());
        ImGui.end();
    }

    private void renderExplorer() {
        if (!editorUi.isShowExplorer()) return;
        ImGui.setNextWindowPos(0, TOP_OFFSET, ImGuiCond.Once);
        ImGui.setNextWindowSize(330, window.getHeight() - TOP_OFFSET - 40, ImGuiCond.Once);
        ImGui.begin("Explorer");
        ImGui.text("Scene: " + scene.getName());
        ImGui.text("Ctrl+LMB: multi | MMB: rename | RMB: menu");
        ImGui.separator();

        if (ImGui.button("Import Model")) {
            VoxelModel loaded = ModelExportManager.loadModel(exportFilename.get());
            if (loaded != null) {
                scene.addModel(loaded);
                selectedObjects.clear(); selectedObjects.add(loaded);
                statusMessage = "Imported " + exportFilename.get() + ".jbm";
            } else statusMessage = "Import failed";
        }
        ImGui.separator();

        boolean requestModelMenu = false;
        boolean requestVoxelMenu = false;

        int idx = 0;
        for (VoxelModel obj : scene.getModels()) {
            ImGui.pushID(idx);
            boolean isSelected = selectedObjects.contains(obj);
            boolean expanded = expandedIds.contains(obj.id);

            if (ImGui.button(expanded ? "[-]" : "[+]")) {
                if (expanded) {
                    expandedIds.remove(obj.id);
                    if (inspectedModel == obj) { inspectedModel = null; selectedVoxelIndices.clear(); }
                } else {
                    expandedIds.add(obj.id);
                }
            }
            ImGui.sameLine();

            String modelLabel = obj.name + " (" + obj.getVoxelCount() + ")##row";
            if (ImGui.selectable(modelLabel, isSelected && selectedVoxelIndices.isEmpty())) {
                boolean ctrl = ImGui.getIO().getKeyCtrl();
                if (ctrl) {
                    if (selectedObjects.contains(obj)) selectedObjects.remove(obj);
                    else selectedObjects.add(obj);
                } else { selectedObjects.clear(); selectedObjects.add(obj); }
                selectedVoxelIndices.clear();
                inspectedModel = null;
            }

            if (ImGui.isItemClicked(2)) {
                renameTarget = obj;
                renameBuffer.set(obj.name);
                renameDialogOpen = true;
                ImGui.openPopup("RenameModel");
            }

            if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                if (!selectedObjects.contains(obj)) { selectedObjects.clear(); selectedObjects.add(obj); }
                selectedVoxelIndices.clear();
                inspectedModel = null;
                requestModelMenu = true;
            }

            if (expanded) {
                ImGui.indent();
                int shown = 0;
                for (int j = 0; j < obj.voxels.size(); j++) {
                    if (shown >= 300) { ImGui.text("... and " + (obj.voxels.size() - shown) + " more"); break; }
                    Voxel v = obj.voxels.get(j);
                    Vector3f c = Voxel.hexToColor(v.color);
                    float[] cc = {c.x, c.y, c.z, 1.0f};
                    ImGui.colorButton("##vc" + j, cc, 0, 12, 12);
                    ImGui.sameLine();
                    boolean isVoxelSelected = (inspectedModel == obj && selectedVoxelIndices.contains(j));
                    String voxelLabel = "Voxel (" + v.x + ", " + v.y + ", " + v.z + ")##v" + j;
                    if (ImGui.selectable(voxelLabel, isVoxelSelected)) {
                        boolean ctrl = ImGui.getIO().getKeyCtrl();
                        inspectedModel = obj;
                        if (!selectedObjects.contains(obj)) { selectedObjects.clear(); selectedObjects.add(obj); }
                        if (ctrl) {
                            if (selectedVoxelIndices.contains(j)) selectedVoxelIndices.remove(j);
                            else selectedVoxelIndices.add(j);
                        } else {
                            selectedVoxelIndices.clear();
                            selectedVoxelIndices.add(j);
                        }
                    }
                    if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                        inspectedModel = obj;
                        if (!selectedObjects.contains(obj)) { selectedObjects.clear(); selectedObjects.add(obj); }
                        if (!selectedVoxelIndices.contains(j)) {
                            selectedVoxelIndices.clear();
                            selectedVoxelIndices.add(j);
                        }
                        requestVoxelMenu = true;
                    }
                    shown++;
                }
                ImGui.unindent();
            }
            ImGui.popID();
            idx++;
        }

        if (requestModelMenu) ImGui.openPopup("ModelContextMenu");
        if (ImGui.beginPopup("ModelContextMenu")) {
            if (ImGui.menuItem("Duplicate")) duplicateSelected();
            if (ImGui.menuItem("Rename (MMB)")) {
                if (!selectedObjects.isEmpty()) {
                    renameTarget = selectedObjects.get(0);
                    renameBuffer.set(renameTarget.name);
                    renameDialogOpen = true;
                    ImGui.openPopup("RenameModel");
                }
            }
            if (ImGui.menuItem("Combine (Group)", "", false, selectedObjects.size() > 1)) combineSelected();
            if (ImGui.menuItem("Focus")) focusSelected();
            ImGui.separator();
            if (ImGui.menuItem("Delete")) {
                commandHistory.execute(new Commands.DeleteCommand(scene, selectedObjects));
                selectedObjects.clear();
                selectedVoxelIndices.clear(); inspectedModel = null;
            }
            ImGui.endPopup();
        }

        if (requestVoxelMenu) ImGui.openPopup("VoxelContextMenu");
        if (ImGui.beginPopup("VoxelContextMenu")) {
            int count = selectedVoxelIndices.size();
            ImGui.text("Selected voxels: " + count);
            ImGui.separator();
            if (ImGui.menuItem("Delete Voxel(s)", "", false, count > 0)) deleteSelectedVoxels();
            if (ImGui.menuItem("Combine into Group", "", false, count >= 2)) combineSelectedVoxelsIntoModel();
            ImGui.separator();
            if (ImGui.menuItem("Deselect")) selectedVoxelIndices.clear();
            ImGui.endPopup();
        }
        ImGui.end();
    }

    private void renderProperties() {
        if (!editorUi.isShowProperties() || selectedObjects.isEmpty()) return;
        VoxelModel mainObj = selectedObjects.get(0);
        ImGui.setNextWindowPos(window.getWidth() - 250, TOP_OFFSET, ImGuiCond.Once);
        ImGui.setNextWindowSize(250, window.getHeight() - TOP_OFFSET - 40, ImGuiCond.Once);
        ImGui.begin("Properties");
        ImGui.text("Selected: " + selectedObjects.size());
        ImGui.separator();
        ImGui.text("Model: " + mainObj.name);
        ImGui.text("Size: " + mainObj.sizeX + "x" + mainObj.sizeY + "x" + mainObj.sizeZ);
        ImGui.text("Voxels: " + mainObj.getVoxelCount());
        if (!selectedVoxelIndices.isEmpty()) ImGui.text("Selected voxels: " + selectedVoxelIndices.size());
        ImGui.separator();
        float[] pos = {mainObj.transform.position.x, mainObj.transform.position.y, mainObj.transform.position.z};
        if (ImGui.dragFloat3("Position", pos, 0.1f)) {
            mainObj.transform.position.set(pos[0], pos[1], pos[2]);
            mainObj.markAABBDirty();
        }
        float[] rot = {mainObj.transform.rotation.x, mainObj.transform.rotation.y, mainObj.transform.rotation.z};
        if (ImGui.dragFloat3("Rotation", rot, 1.0f)) mainObj.transform.rotation.set(rot[0], rot[1], rot[2]);
        float[] scl = {mainObj.transform.scale.x, mainObj.transform.scale.y, mainObj.transform.scale.z};
        if (ImGui.dragFloat3("Scale", scl, 0.1f)) mainObj.transform.scale.set(scl[0], scl[1], scl[2]);
        ImGui.separator();
        ImGui.text("Color Palette:");
        Vector3f[] palette = {
            new Vector3f(0.8f,0.2f,0.2f), new Vector3f(0.2f,0.8f,0.2f),
            new Vector3f(0.2f,0.2f,0.8f), new Vector3f(0.8f,0.8f,0.2f),
            new Vector3f(0.8f,0.2f,0.8f), new Vector3f(0.2f,0.8f,0.8f),
            new Vector3f(0.8f,0.8f,0.8f), new Vector3f(0.2f,0.2f,0.2f)
        };
        for (int i = 0; i < palette.length; i++) {
            float[] c = {palette[i].x, palette[i].y, palette[i].z, 1.0f};
            if (ImGui.colorButton("##col"+i, c, 0, 20, 20)) {
                currentColor.set(palette[i]);
                spawnManager.setCurrentColor(currentColor);
            }
            if (i % 4 != 3) ImGui.sameLine();
        }
        ImGui.separator();
        float[] cc = {currentColor.x, currentColor.y, currentColor.z, 1.0f};
        if (ImGui.colorEdit3("Current Color", cc)) {
            currentColor.set(cc[0], cc[1], cc[2]);
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
        ImGui.begin("Viewport", ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse |
                ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoNavFocus);
        float availW = ImGui.getContentRegionAvailX(), availH = ImGui.getContentRegionAvailY();
        if (availW > 0 && availH > 0) {
            ImGui.beginChild("ViewportCanvas", availW, availH, false,
                    ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
            ImGui.image(sceneFramebuffer.getTextureId(), availW, availH, 0, 1, 1, 0);
            gizmoImgX = ImGui.getItemRectMinX();
            gizmoImgY = ImGui.getItemRectMinY();
            drawGizmo();
            ImGui.endChild();
            viewportW = (int) availW; viewportH = (int) availH;
        } else {
            gizmoValid = false;
        }
        ImGui.end();
    }

    private void drawGizmo() {
        gizmoValid = false;
        if (selectedObjects.isEmpty()) return;
        if (currentTool != TOOL_MOVE && currentTool != TOOL_SCALE) return;

        VoxelModel target = selectedObjects.get(0);
        Vector3f origin = new Vector3f(
                Math.round(target.transform.position.x),
                Math.round(target.transform.position.y),
                Math.round(target.transform.position.z));

        float[] c = new float[2];
        if (!worldToScreen(origin, c)) return;
        float dist = camera.getPosition().distance(origin);
        gK = (float) (2.0 * dist * Math.tan(Math.toRadians(camera.getFov() / 2.0)) / Math.max(viewportH, 1));

        ImDrawList dl = ImGui.getWindowDrawList();
        float cx = gizmoImgX + c[0];
        float cy = gizmoImgY + c[1];
        gCX = cx; gCY = cy;

        Vector3f[] axes = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
        int[] cols = {COL_X, COL_Y, COL_Z};

        for (int a = 0; a < 3; a++) {
            float[] t = new float[2], tn = new float[2];
            Vector3f wp = new Vector3f(origin).add(new Vector3f(axes[a]).mul(1.6f));
            Vector3f wn = new Vector3f(origin).add(new Vector3f(axes[a]).mul(-1.6f));
            if (!worldToScreen(wp, t)) { t[0] = c[0]; t[1] = c[1]; }
            if (!worldToScreen(wn, tn)) { tn[0] = c[0]; tn[1] = c[1]; }
            gAxisTip[a][0] = gizmoImgX + t[0]; gAxisTip[a][1] = gizmoImgY + t[1];
            gNegTip[a][0] = gizmoImgX + tn[0]; gNegTip[a][1] = gizmoImgY + tn[1];
            dl.addLine(cx, cy, gAxisTip[a][0], gAxisTip[a][1], cols[a], 2.5f);
            dl.addCircleFilled(gAxisTip[a][0], gAxisTip[a][1], 5f, cols[a]);
            if (currentTool == TOOL_SCALE) {
                dl.addLine(cx, cy, gNegTip[a][0], gNegTip[a][1], cols[a], 2.5f);
                dl.addCircleFilled(gNegTip[a][0], gNegTip[a][1], 5f, cols[a]);
            }
        }

        if (currentTool == TOOL_MOVE) {
            int[][] pairs = {{0, 1}, {0, 2}, {1, 2}};
            for (int p = 0; p < 3; p++) {
                Vector3f pw = new Vector3f(origin)
                        .add(new Vector3f(axes[pairs[p][0]]).mul(0.5f))
                        .add(new Vector3f(axes[pairs[p][1]]).mul(0.5f));
                float[] pc = new float[2];
                if (worldToScreen(pw, pc)) {
                    gPlaneC[p][0] = gizmoImgX + pc[0];
                    gPlaneC[p][1] = gizmoImgY + pc[1];
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

    private void renderStatusBar() {
        ImGui.setNextWindowPos(0, window.getHeight() - 40, ImGuiCond.Always);
        ImGui.setNextWindowSize(window.getWidth(), 40, ImGuiCond.Always);
        ImGui.begin("StatusBar", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBackground);
        ImGui.text(String.format("FPS: %.1f", fps));
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();
        ImGui.text("Tool: " + toolName());
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();
        if (altHeld) {
            ImGui.textColored(1.0f, 0.85f, 0.2f, 1.0f, "Alt: FREE placement");
        } else {
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "Alt: SNAP to voxels");
        }
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();
        if (showGhost) ImGui.text(String.format("Cursor: [%d, %d, %d]",
                (int)ghostPosition.x, (int)ghostPosition.y, (int)ghostPosition.z));
        else ImGui.text("Cursor: ---");
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();
        int totalVoxels = 0;
        for (VoxelModel m : scene.getModels()) totalVoxels += m.getVoxelCount();
        ImGui.text("Voxels: " + totalVoxels);
        ImGui.sameLine(); ImGui.text("|"); ImGui.sameLine();
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, statusMessage);
        ImGui.end();
    }

    private void renderHintsOverlay() {
        if (!showHints) return;
        ImGui.setNextWindowPos(260, TOP_OFFSET + 8, ImGuiCond.Always);
        ImGui.setNextWindowSize(480, 260, ImGuiCond.Always);
        ImGui.begin("##HintsOverlay", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings |
                ImGuiWindowFlags.NoFocusOnAppearing | ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.AlwaysAutoResize);
        ImGui.textColored(1.0f, 0.85f, 0.2f, 1.0f, "Quick controls:");
        ImGui.separator();
        ImGui.bulletText("1 - Select | 2 - Move | 3 - Rotate (N/A) | 4 - Scale");
        ImGui.bulletText("B - Add | X - Remove | G - Paint | I - Pick | F - Fill");
        ImGui.separator();
        ImGui.textColored(0.4f, 1.0f, 1.0f, 1.0f, "Voxel placement modes:");
        ImGui.bulletText("Normal: SNAP - voxel attaches to existing voxel surface");
        ImGui.bulletText("Hold Alt: FREE - voxel placed anywhere (no snapping)");
        ImGui.separator();
        ImGui.bulletText("LMB - tool action | Shift+LMB - multi-select");
        ImGui.bulletText("RMB + Move - rotate camera | MMB + Move - pan");
        ImGui.bulletText("Scroll - zoom | WASD / Space / Shift - fly camera");
        ImGui.bulletText("H - focus selected | Shift+D - duplicate | Del - delete");
        ImGui.bulletText("Ctrl+Z - undo | Ctrl+Shift+Z - redo");
        ImGui.bulletText("[ / ] - brush size");
        ImGui.end();
    }

    private void renderErrorToast() {
        if (errorTimer <= 0 || errorMsg == null) return;
        ImGui.setNextWindowPos(window.getWidth() * 0.5f - 260, 90, ImGuiCond.Always);
        ImGui.begin("##ErrorToast", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings);
        ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, errorMsg);
        ImGui.end();
    }

    private static boolean isAsciiAlphanumericOrSpace(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == ' ' || c == '_' || c == '-';
    }

    private static String filterEnglish(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) if (isAsciiAlphanumericOrSpace(c)) sb.append(c);
        return sb.toString();
    }

    private void renderRenameDialog() {
        ImBoolean open = new ImBoolean(true);
        ImGui.setNextWindowSize(320, 120, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal("RenameModel", open, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Rename (English letters, digits, _ - space):");
            String current = renameBuffer.get();
            String filtered = filterEnglish(current);
            if (!current.equals(filtered)) renameBuffer.set(filtered);
            ImGui.inputText("##RenameInput", renameBuffer);
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "Non-English chars are ignored");
            ImGui.separator();
            if (ImGui.button("Apply", 120, 0)) {
                if (renameTarget != null) {
                    String newName = renameBuffer.get().trim();
                    if (!newName.isEmpty()) { renameTarget.name = newName; statusMessage = "Renamed to: " + newName; }
                }
                renameDialogOpen = false; renameTarget = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) { renameDialogOpen = false; renameTarget = null; ImGui.closeCurrentPopup(); }
            ImGui.endPopup();
        }
        if (!open.get()) { renameDialogOpen = false; renameTarget = null; }
    }

    private void renderNewModelDialog() {
        ImGui.setNextWindowPos(window.getWidth() / 2 - 175, window.getHeight() / 2 - 130, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(350, 320, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal("Create New Model", ImGuiWindowFlags.NoResize)) {
            ImGui.text("Create New Voxel Model");
            ImGui.separator();
            ImGui.inputText("##ModelName", newModelName);
            ImGui.separator();
            if (ImGui.button("Small (8x8x8)", 100, 25)) { newModelSize[0]=8; newModelSize[1]=8; newModelSize[2]=8; }
            ImGui.sameLine();
            if (ImGui.button("Tall (8x8x32)", 100, 25)) { newModelSize[0]=8; newModelSize[1]=32; newModelSize[2]=8; }
            ImGui.sameLine();
            if (ImGui.button("Wide (32x8x8)", 100, 25)) { newModelSize[0]=32; newModelSize[1]=8; newModelSize[2]=8; }
            if (ImGui.button("Cube (16x16x16)", 100, 25)) { newModelSize[0]=16; newModelSize[1]=16; newModelSize[2]=16; }
            ImGui.sameLine();
            if (ImGui.button("Char (8x32x8)", 110, 25)) { newModelSize[0]=8; newModelSize[1]=32; newModelSize[2]=8; }
            ImGui.separator();
            ImGui.text("Custom Size:");
            int[] sb = {newModelSize[0], newModelSize[1], newModelSize[2]};
            if (ImGui.inputInt3("##ModelSize", sb)) {
                newModelSize[0] = Math.max(1, Math.min(128, sb[0]));
                newModelSize[1] = Math.max(1, Math.min(128, sb[1]));
                newModelSize[2] = Math.max(1, Math.min(128, sb[2]));
            }
            ImGui.text("Grid: " + newModelSize[0] + "x" + newModelSize[1] + "x" + newModelSize[2]);
            ImGui.separator();
            if (ImGui.button("Create", 150, 30)) {
                VoxelModel newModel = spawnManager.createNewModelAtCamera(
                        newModelName.get(), newModelSize[0], newModelSize[1], newModelSize[2], camera);
                if (newModel != null) {
                    selectedObjects.clear(); selectedObjects.add(newModel);
                    currentTool = TOOL_ADD_VOXEL;
                    statusMessage = "Created model: " + newModelName.get();
                    showHints = true; hintTimer = 10.0f;
                }
                showNewModelDialog = false; ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 150, 30)) { showNewModelDialog = false; ImGui.closeCurrentPopup(); }
            ImGui.endPopup();
        }
    }

    // ===== Explorer helpers =====
    private void duplicateSelected() {
        for (VoxelModel m : new ArrayList<>(selectedObjects)) {
            VoxelModel copy = m.deepCopy();
            copy.name = m.name + "_copy";
            copy.transform.position.x += 2.0f;
            commandHistory.execute(new Commands.SpawnCommand(scene, copy));
        }
    }

    private void combineSelected() {
        if (selectedObjects.size() < 2) return;
        VoxelModel first = selectedObjects.get(0);
        Vector3f origin = new Vector3f(
                Math.round(first.transform.position.x),
                Math.round(first.transform.position.y),
                Math.round(first.transform.position.z));
        VoxelModel combined = new VoxelModel(origin.x, origin.y, origin.z);
        combined.name = "Combined_" + (scene.getModels().size() + 1);
        for (VoxelModel m : selectedObjects) {
            Matrix4f mm = m.transform.getModelMatrix();
            if (m.voxels != null) {
                for (Voxel v : m.voxels) {
                    Vector3f world = mm.transformPosition(new Vector3f(v.x, v.y, v.z));
                    combined.voxels.add(new Voxel(
                            Math.round(world.x - origin.x),
                            Math.round(world.y - origin.y),
                            Math.round(world.z - origin.z), v.color, v.getAlpha()));
                }
            }
        }
        commandHistory.execute(new Commands.CombineCommand(scene, new ArrayList<>(selectedObjects), combined));
        selectedObjects.clear(); selectedObjects.add(combined);
    }

    private void focusSelected() {
        if (selectedObjects.isEmpty()) return;
        Vector3f center = new Vector3f();
        for (VoxelModel m : selectedObjects) center.add(m.transform.position);
        center.mul(1.0f / selectedObjects.size());
        camera.setPosition(new Vector3f(center.x - 10, center.y + 5, center.z));
        camera.setMode(Camera.Mode.FLY);
    }

    private void deleteSelectedVoxels() {
        if (inspectedModel == null || selectedVoxelIndices.isEmpty()) return;
        List<Integer> forwardIndices = new ArrayList<>(selectedVoxelIndices);
        java.util.Collections.sort(forwardIndices);
        List<Voxel> forwardRemoved = new ArrayList<>();
        for (int idx : forwardIndices) {
            if (idx >= 0 && idx < inspectedModel.voxels.size())
                forwardRemoved.add(inspectedModel.voxels.get(idx).copy());
        }
        commandHistory.execute(new Commands.DeleteVoxelsCommand(inspectedModel, forwardRemoved, forwardIndices));
        statusMessage = "Deleted " + forwardRemoved.size() + " voxel(s)";
        selectedVoxelIndices.clear();
    }

    private void combineSelectedVoxelsIntoModel() {
        if (inspectedModel == null || selectedVoxelIndices.size() < 2) return;
        List<Integer> indices = new ArrayList<>(selectedVoxelIndices);
        java.util.Collections.sort(indices);
        List<Voxel> picked = new ArrayList<>();
        for (int idx : indices) if (idx >= 0 && idx < inspectedModel.voxels.size()) picked.add(inspectedModel.voxels.get(idx).copy());
        if (picked.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (Voxel v : picked) { if (v.x < minX) minX = v.x; if (v.y < minY) minY = v.y; if (v.z < minZ) minZ = v.z; }
        Vector3f srcPos = inspectedModel.transform.position;
        VoxelModel newModel = new VoxelModel(srcPos.x + minX, srcPos.y + minY, srcPos.z + minZ);
        newModel.name = "Group_" + (scene.getModels().size() + 1);
        for (Voxel v : picked) newModel.voxels.add(new Voxel(v.x - minX, v.y - minY, v.z - minZ, v.color, v.getAlpha()));
        List<Voxel> forwardRemoved = new ArrayList<>();
        for (int idx : indices) if (idx >= 0 && idx < inspectedModel.voxels.size()) forwardRemoved.add(inspectedModel.voxels.get(idx).copy());
        commandHistory.execute(new Commands.DeleteVoxelsCommand(inspectedModel, forwardRemoved, indices));
        commandHistory.execute(new Commands.SpawnCommand(scene, newModel));
        statusMessage = "Created group '" + newModel.name + "' from " + picked.size() + " voxel(s)";
        selectedVoxelIndices.clear(); inspectedModel = null;
        selectedObjects.clear(); selectedObjects.add(newModel);
    }

    private int countSceneVoxels() {
        int count = 0;
        for (VoxelModel model : scene.getModels()) count += model.getVoxelCount();
        return count;
    }

    // ===== Startup UI action =====
    private void handleUiAction(EditorUiState.UiAction action) {
        if (action == EditorUiState.UiAction.NONE) return;
        if (action == EditorUiState.UiAction.CREATE_MODEL_WORKSPACE) {
            scene.getModels().clear(); selectedObjects.clear();
            selectedVoxelIndices.clear(); inspectedModel = null;
            currentTool = TOOL_ADD_VOXEL;
            statusMessage = "New model workspace: " + editorUi.getActiveModelName();
            showHints = true; hintTimer = 10.0f;
            VoxelModel newModel = spawnManager.createNewModelAtCamera(
                    editorUi.getActiveModelName(),
                    editorUi.getActiveModelWidth(),
                    editorUi.getActiveModelHeight(),
                    editorUi.getActiveModelDepth(), camera);
            if (newModel != null) { selectedObjects.clear(); selectedObjects.add(newModel); }
            return;
        }
        if (action == EditorUiState.UiAction.OPEN_MAP_WORKSPACE) {
            selectedObjects.clear(); currentTool = TOOL_SELECT;
            statusMessage = "Map workspace";
        }
    }

    // ================= MAIN LOOP =================
    public void updateAndRender(float dt) {
        frameTimeAccum += dt; frameCount++;
        if (frameTimeAccum >= 1.0f) { fps = frameCount / frameTimeAccum; frameTimeAccum = 0; frameCount = 0; }

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
        editorUi.renderStatusWindow(dt, scene.getModels().size(), countSceneVoxels(), instancedRenderer.getCount());
    }
}
// === END OF FILE ===
