package com.jailblack.editor;

import com.jailblack.engine.*;
import com.jailblack.scene.Scene;
import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
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

    private static final int COL_X = 0xFF0000FF;
    private static final int COL_Y = 0xFF00FF00;
    private static final int COL_Z = 0xFFFF0000;
    private static final int COL_FREE = 0xFFFFFFFF;
    private static final int COL_PLANE = 0x80FFFF00;

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
    private final Palettes palettes = new Palettes();

    private final java.util.Set<String> expandedIds = new java.util.HashSet<>();
    private VoxelModel inspectedModel = null;
    private int inspectedVoxelIndex = -1;

    private final Vector3f currentColor = new Vector3f(0.85f, 0.35f, 0.32f);
    private float currentAlpha = 1.0f;
    private final ImBoolean paletteOpen = new ImBoolean(true);

    private int currentTool = TOOL_BLOCK;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public static boolean viewportHovered = false;
    private boolean leftMouseDownInViewport = false;
    private boolean middleMouseDownInViewport = false;

    // Гизмо
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

    private String errorMsg = null;
    private float errorTimer = 0f;

    private int viewportW = 1280;
    private int viewportH = 720;

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
                if (gizmoMoveDrag) {
                    applyMoveDrag(dx, dy);
                } else if (gizmoScaleDrag) {
                    scaleAccum += dot2(dx, dy, scaleDirX(), scaleDirY());
                    scaleSteps = Math.round(scaleAccum);
                    if (scaleSteps > 32) scaleSteps = 32;
                    if (scaleSteps < -32) scaleSteps = -32;
                } else {
                    camera.rotate(dx, dy);
                }
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
                if (leftMouseDownInViewport && !selectedObjects.isEmpty()) {
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
        for (Voxel v : model.voxels) old.add(new Voxel(v.x, v.y, v.z, v.color));
        List<Voxel> cur = new ArrayList<>();
        for (Voxel v : old) cur.add(new Voxel(v.x, v.y, v.z, v.color));

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
        for (Voxel v : src) out.add(new Voxel(v.x, v.y, v.z, v.color));
        for (Voxel v : src) {
            if (coordOf(v, axis) == edge) {
                Voxel nv = new Voxel(v.x, v.y, v.z, v.color);
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
            if (coordOf(v, axis) != edge) out.add(new Voxel(v.x, v.y, v.z, v.color));
        }
        return out;
    }

    private void tryActivateRotate() {
        currentTool = TOOL_ROTATE;
        errorMsg = "Voxel rotation is not available yet. Feature is under development.";
        errorTimer = 3.0f;
    }

    public void updateAndRender(float dt) {
        if (errorTimer > 0) errorTimer -= dt;
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

    private void duplicateSelected() {
        for (VoxelModel m : new ArrayList<>(selectedObjects)) {
            VoxelModel copy = new VoxelModel(m.transform.position.x + 2, m.transform.position.y, m.transform.position.z + 2);
            copy.name = m.name + "_copy";
            for (Voxel v : m.voxels) copy.voxels.add(new Voxel(v.x, v.y, v.z, v.color));
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
            for (Voxel v : m.voxels) {
                Vector3f world = mm.transformPosition(new Vector3f(v.x, v.y, v.z));
                combined.voxels.add(new Voxel(
                        Math.round(world.x - origin.x),
                        Math.round(world.y - origin.y),
                        Math.round(world.z - origin.z),
                        v.color));
            }
        }
        commandHistory.execute(new Commands.CombineCommand(scene, new ArrayList<>(selectedObjects), combined));
        selectedObjects.clear();
        selectedObjects.add(combined);
    }

    private void focusSelected() {
        if (selectedObjects.isEmpty()) return;
        Vector3f center = new Vector3f();
        for (VoxelModel m : selectedObjects) center.add(m.transform.position);
        center.mul(1.0f / selectedObjects.size());
        camera.focus(center, 12f);
    }

    private void renderScene() {
        int w = viewportW;
        int h = viewportH;
        if (w <= 0 || h <= 0) return;

        if (sceneFramebuffer.getWidth() != w || sceneFramebuffer.getHeight() != h) {
            sceneFramebuffer.cleanup();
            sceneFramebuffer = new Framebuffer(w, h);
        }

        sceneFramebuffer.bind();
        glClearColor(0.45f, 0.55f, 0.8f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        float aspect = (float) w / (float) h;
        lastViewProj = camera.getViewProjection(aspect);

        gridShader.use();
        gridShader.setUniform("uViewProj", lastViewProj);
        gridMesh.draw();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        basicShader.use();
        basicShader.setUniform("uViewProj", lastViewProj);
        basicShader.setUniform("uInstanced", 1);
        basicShader.setUniform("uMode", 0);
        basicShader.setUniform("uUseTexture", 0);
        basicShader.setUniform("uAlpha", 1.0f);
        basicShader.setUniform("uLightDir", new Vector3f(0.5f, 1.0f, 0.3f).normalize());
        basicShader.setUniform("uCamPos", camera.getPosition());
        instancedRenderer.render(scene.getModels());

        glDisable(GL_BLEND);

        outlineShader.use();
        outlineShader.setUniform("uViewProj", lastViewProj);
        outlineShader.setUniform("uColor", new Vector3f(1.0f, 0.8f, 0.0f));
        if (!selectedObjects.isEmpty()) {
            for (VoxelModel obj : selectedObjects) {
                Matrix4f modelMatrix = obj.transform.getModelMatrix();
                for (Voxel v : obj.voxels) {
                    Matrix4f voxelMatrix = new Matrix4f(modelMatrix).translate(v.x, v.y, v.z).scale(1.05f);
                    outlineShader.setUniform("uModel", voxelMatrix);
                    cubeOutlineMesh.draw();
                }
            }
        }
        if (inspectedModel != null) {
            if (inspectedVoxelIndex >= 0 && inspectedVoxelIndex < inspectedModel.voxels.size()) {
                Voxel iv = inspectedModel.voxels.get(inspectedVoxelIndex);
                outlineShader.setUniform("uColor", new Vector3f(0.0f, 1.0f, 1.0f));
                Matrix4f im = inspectedModel.transform.getModelMatrix();
                Matrix4f ivm = new Matrix4f(im).translate(iv.x, iv.y, iv.z).scale(1.15f);
                outlineShader.setUniform("uModel", ivm);
                cubeOutlineMesh.draw();
            } else {
                inspectedModel = null;
                inspectedVoxelIndex = -1;
            }
        }

        sceneFramebuffer.unbind();
        glViewport(0, 0, window.getWidth(), window.getHeight());
    }

    private boolean worldToScreen(Vector3f world, float[] out) {
        Vector4f p = new Vector4f(world, 1.0f).mul(lastViewProj);
        if (p.w <= 0) return false;
        float nx = p.x / p.w;
        float ny = p.y / p.w;
        out[0] = (nx * 0.5f + 0.5f) * viewportW;
        out[1] = (1f - (ny * 0.5f + 0.5f)) * viewportH;
        return true;
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
        if (ImGui.button("Rotate")) tryActivateRotate(); ImGui.sameLine();
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
            case TOOL_ROTATE: return "Rotate (N/A)";
            case TOOL_SCALE: return "Scale";
            default: return "?";
        }
    }

    private void renderExplorer() {
        float h = window.getHeight();
        ImGui.setNextWindowPos(40, 120, ImGuiCond.Once);
        ImGui.setNextWindowSize(330, h - 240, ImGuiCond.Once);
        ImGui.begin("Explorer");

        ImGui.text("Scene: " + scene.getName());
        ImGui.text("Ctrl+LMB: multi | RMB: menu | [+]: contents");
        ImGui.separator();

        boolean requestMenu = false;
        int idx = 0;
        for (VoxelModel obj : scene.getModels()) {
            ImGui.pushID(idx);
            boolean isSelected = selectedObjects.contains(obj);
            boolean expanded = expandedIds.contains(obj.id);

            if (ImGui.button(expanded ? "[-]" : "[+]")) {
                if (expanded) expandedIds.remove(obj.id);
                else expandedIds.add(obj.id);
            }
            ImGui.sameLine();

            if (ImGui.selectable(obj.name + " (" + obj.voxels.size() + ")##row", isSelected)) {
                boolean ctrl = ImGui.getIO().getKeyCtrl();
                if (ctrl) {
                    if (selectedObjects.contains(obj)) selectedObjects.remove(obj);
                    else selectedObjects.add(obj);
                } else {
                    selectedObjects.clear();
                    selectedObjects.add(obj);
                }
            }

            // ПКМ по строке: выделить объект и запросить меню
            if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                if (!selectedObjects.contains(obj)) {
                    selectedObjects.clear();
                    selectedObjects.add(obj);
                }
                requestMenu = true;
            }

            if (expanded) {
                ImGui.indent();
                int shown = 0;
                for (int j = 0; j < obj.voxels.size(); j++) {
                    if (shown >= 300) {
                        ImGui.text("... and " + (obj.voxels.size() - shown) + " more");
                        break;
                    }
                    Voxel v = obj.voxels.get(j);
                    Vector3f c = InstancedRenderer.hexToColor(v.color);
                    float[] cc = {c.x, c.y, c.z, 1.0f};
                    ImGui.colorButton("##vc" + j, cc, 0, 12, 12);
                    ImGui.sameLine();
                    boolean selVox = (inspectedModel == obj && inspectedVoxelIndex == j);
                    if (ImGui.selectable("Voxel (" + v.x + ", " + v.y + ", " + v.z + ")##v" + j, selVox)) {
                        inspectedModel = obj;
                        inspectedVoxelIndex = j;
                        if (!selectedObjects.contains(obj)) {
                            selectedObjects.clear();
                            selectedObjects.add(obj);
                        }
                    }
                    shown++;
                }
                ImGui.unindent();
            }

            ImGui.popID();
            idx++;
        }

        // Меню открывается ВНЕ pushID - ID стеки совпадают
        if (requestMenu) {
            ImGui.openPopup("ExplorerContextMenu");
        }
        if (ImGui.beginPopup("ExplorerContextMenu")) {
            if (ImGui.menuItem("Select")) { }
            if (ImGui.menuItem("Duplicate")) duplicateSelected();
            if (ImGui.menuItem("Combine (Group)", "", false, selectedObjects.size() > 1)) combineSelected();
            if (ImGui.menuItem("Focus")) focusSelected();
            ImGui.separator();
            if (ImGui.menuItem("Delete")) {
                commandHistory.execute(new Commands.DeleteCommand(scene, selectedObjects));
                selectedObjects.clear();
            }
            ImGui.endPopup();
        }

        ImGui.end();
    }

    private void renderProperties() {
        float w = window.getWidth();
        float h = window.getHeight();
        ImGui.setNextWindowPos(w - 280, 120, ImGuiCond.Once);
        ImGui.setNextWindowSize(240, h - 240, ImGuiCond.Once);
        if (ImGui.begin("Properties")) {
            if (!selectedObjects.isEmpty()) {
                VoxelModel mainObj = selectedObjects.get(0);

                ImGui.text("Selected: " + selectedObjects.size());
                ImGui.separator();

                float[] pos = {mainObj.transform.position.x, mainObj.transform.position.y, mainObj.transform.position.z};
                if (ImGui.dragFloat3("Position", pos, 0.1f)) {
                    mainObj.transform.position.set(pos[0], pos[1], pos[2]);
                }

                ImGui.separator();
                if (ImGui.button("Apply color to selected")) recolorSelected();

                ImGui.separator();
                ImGui.text("Contents:");
                ImGui.text("Voxels: " + mainObj.voxels.size());

                if (!mainObj.voxels.isEmpty()) {
                    int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, minz = Integer.MAX_VALUE;
                    int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE, maxz = Integer.MIN_VALUE;
                    java.util.Map<String, Integer> colorCounts = new java.util.LinkedHashMap<>();
                    for (Voxel v : mainObj.voxels) {
                        if (v.x < minx) minx = v.x;
                        if (v.y < miny) miny = v.y;
                        if (v.z < minz) minz = v.z;
                        if (v.x > maxx) maxx = v.x;
                        if (v.y > maxy) maxy = v.y;
                        if (v.z > maxz) maxz = v.z;
                        colorCounts.merge(v.color, 1, Integer::sum);
                    }
                    ImGui.text("Bounds:");
                    ImGui.text("(" + minx + ", " + miny + ", " + minz + ")");
                    ImGui.text("(" + maxx + ", " + maxy + ", " + maxz + ")");
                    ImGui.text("Size: " + (maxx - minx + 1) + " x " + (maxy - miny + 1) + " x " + (maxz - minz + 1));

                    ImGui.separator();
                    ImGui.text("Colors inside:");
                    int ci = 0;
                    for (java.util.Map.Entry<String, Integer> e : colorCounts.entrySet()) {
                        Vector3f c = InstancedRenderer.hexToColor(e.getKey());
                        float[] cc = {c.x, c.y, c.z, 1.0f};
                        ImGui.colorButton("##cc" + ci, cc, 0, 14, 14);
                        ImGui.sameLine();
                        ImGui.text(e.getKey() + "  x" + e.getValue());
                        ci++;
                        if (ci > 32) {
                            ImGui.text("...");
                            break;
                        }
                    }
                }
            } else {
                ImGui.text("Nothing selected");
                ImGui.text("Ctrl+LMB in Explorer: multi");
                ImGui.text("Shift+RMB: pick object");
            }
        }
        ImGui.end();
    }

    private void renderPalette() {
        if (!paletteOpen.get()) return;
        float w = window.getWidth();
        float h = window.getHeight();
        ImGui.setNextWindowPos(w - 560, 120, ImGuiCond.Once);
        ImGui.setNextWindowSize(240, h - 240, ImGuiCond.Once);
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
            ImGui.text("My palettes:");

            String[] names = new String[palettes.palettes.size()];
            for (int i = 0; i < names.length; i++) names[i] = palettes.palettes.get(i).name;
            ImInt sel = new ImInt(palettes.activeIndex);
            if (ImGui.combo("##PalSel", sel, names)) palettes.setActive(sel.get());

            if (ImGui.button("New palette")) palettes.newPalette();
            ImGui.sameLine();
            if (ImGui.button("Add color")) palettes.addCurrent(toHex(currentColor, currentAlpha));

            List<String> cols = palettes.active().colors;
            for (int i = 0; i < cols.size(); i++) {
                Vector3f c = InstancedRenderer.hexToColor(cols.get(i));
                float[] cc = {c.x, c.y, c.z, 1.0f};
                if (ImGui.colorButton("##my" + i, cc, 0, 22, 22)) {
                    applyColor(c.x, c.y, c.z, 1.0f);
                }
                if (ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                    palettes.removeColor(i);
                    break;
                }
                if (i % 5 != 4) ImGui.sameLine();
            }

            ImGui.separator();
            if (ImGui.button("Apply to selected")) recolorSelected();
        }
        ImGui.end();
    }

    private void renderViewport() {
        float w = window.getWidth();
        float h = window.getHeight();
        ImGui.setNextWindowPos(400, 120, ImGuiCond.Once);
        ImGui.setNextWindowSize(w - 980, h - 240, ImGuiCond.Once);
        if (ImGui.begin("Viewport", ImGuiWindowFlags.NoScrollbar)) {
            ImGui.text("LMB+drag = Orbit/Gizmo | RMB = Block spawn / pick | Shift+RMB = select | MMB = Pan | Wheel = Zoom");

            float availW = ImGui.getContentRegionAvailX();
            float availH = ImGui.getContentRegionAvailY();

            if (availW > 0 && availH > 0) {
                ImGui.beginChild("ViewportCanvas", availW, availH, false,
                    ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);

                ImGui.image(sceneFramebuffer.getTextureId(), availW, availH, 0, 1, 1, 0);
                float imgX = ImGui.getItemRectMinX();
                float imgY = ImGui.getItemRectMinY();

                viewportHovered = ImGui.isWindowHovered();

                drawGizmo(imgX, imgY);

                ImGui.endChild();

                viewportW = (int) availW;
                viewportH = (int) availH;
            } else {
                viewportHovered = false;
                gizmoValid = false;
            }
        }
        ImGui.end();

        if (errorTimer > 0 && errorMsg != null) {
            ImGui.setNextWindowPos(window.getWidth() * 0.5f - 260, 90, ImGuiCond.Always);
            ImGui.begin("##ErrorToast",
                    ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize |
                    ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings);
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, errorMsg);
            ImGui.end();
        }
    }

    private void drawGizmo(float imgX, float imgY) {
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
}
// === END OF FILE ===
