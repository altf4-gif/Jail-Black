package game;

import engine.*;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImDrawList;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import java.util.*;

public final class Main {

    static final int STATE_MENU = 0;
    static final int STATE_PLAYING = 1;
    static final int STATE_EDIT_MENU = 2;
    static final int STATE_EDITOR = 3;

    static int gameState = STATE_MENU;

    static List<VoxelObject> editorObjects = new ArrayList<>();
    static List<VoxelObject> selectedObjects = new ArrayList<>();
    static Vector3f currentColor = new Vector3f(0.85f, 0.35f, 0.32f);
    static Vector3f[] palette = {
        new Vector3f(0.85f, 0.35f, 0.32f), // Red
        new Vector3f(0.35f, 0.78f, 0.45f), // Green
        new Vector3f(0.35f, 0.55f, 0.90f), // Blue
        new Vector3f(0.9f, 0.9f, 0.9f),    // White
        new Vector3f(0.2f, 0.2f, 0.2f),    // Black
        new Vector3f(0.9f, 0.8f, 0.2f),    // Yellow
        new Vector3f(0.8f, 0.4f, 0.8f),    // Purple
        new Vector3f(0.2f, 0.8f, 0.8f)     // Cyan
    };

    static int currentTool = 0; // 0 = Select, 1 = Translate, 2 = Rotate, 3 = Scale

    static ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    static ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    public static void main(String[] args) {
        Window window = new Window(1280, 720, "Java 3D Engine - ImGui Editor");
        window.create();

        GL33.glEnable(GL33.GL_DEPTH_TEST);
        GL33.glEnable(GL33.GL_CULL_FACE);
        GL33.glCullFace(GL33.GL_BACK);

        ShaderProgram shader = ShaderProgram.fromResources("shaders/basic.vert", "shaders/basic.frag");

        Camera camera = new Camera();
        Set<Mesh> meshes = new HashSet<>();
        Mesh boxMesh = MeshFactory.box(1, 1, 1);
        Mesh gridMesh = MeshFactory.grid(40.0f, 40);
        meshes.add(boxMesh); meshes.add(gridMesh);

        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.getFonts().addFontDefault();
        imGuiGlfw.init(window.getHandle(), true);
        imGuiGl3.init("#version 330 core");

        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        double[] lastX = new double[1], lastY = new double[1];
        boolean[] firstMouse = new boolean[]{true};
        
        GLFW.glfwSetCursorPosCallback(window.getHandle(), (win, xpos, ypos) -> {
            if (ImGui.getIO().getWantCaptureMouse()) return;
            if (firstMouse[0]) { lastX[0] = xpos; lastY[0] = ypos; firstMouse[0] = false; }
            
            if (gameState == STATE_PLAYING) {
                camera.rotate((float)(xpos - lastX[0]), (float)(ypos - lastY[0]));
            } else if (gameState == STATE_EDITOR) {
                if (GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) {
                    camera.rotate((float)(xpos - lastX[0]), (float)(ypos - lastY[0]));
                }
            }
            lastX[0] = xpos; lastY[0] = ypos;
        });

        double lastTime = GLFW.glfwGetTime();

        while (!window.shouldClose()) {
            double currentTime = GLFW.glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;

            GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);

            imGuiGlfw.newFrame();
            ImGui.newFrame();

            if (gameState == STATE_MENU) {
                ImGui.setNextWindowPos(1280/2 - 100, 720/2 - 100, ImGuiCond.Always);
                ImGui.setNextWindowSize(200, 200, ImGuiCond.Always);
                ImGui.begin("Main Menu", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove);
                
                if (ImGui.button("SIMULATION", 180, 40)) {
                    gameState = STATE_PLAYING;
                    GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                    firstMouse[0] = true;
                }
                if (ImGui.button("EDIT", 180, 40)) {
                    gameState = STATE_EDIT_MENU;
                }
                if (ImGui.button("EXIT", 180, 40)) {
                    GLFW.glfwSetWindowShouldClose(window.getHandle(), true);
                }
                ImGui.end();
            } 
            else if (gameState == STATE_EDIT_MENU) {
                ImGui.setNextWindowPos(1280/2 - 150, 720/2 - 150, ImGuiCond.Always);
                ImGui.setNextWindowSize(300, 300, ImGuiCond.Always);
                ImGui.begin("Map Editor", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove);

                if (ImGui.button("CREATE NEW", 280, 40)) {
                    editorObjects.clear();
                    selectedObjects.clear();
                    camera.setPosition(new Vector3f(0, 5, 10));
                    gameState = STATE_EDITOR;
                    GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                    firstMouse[0] = true;
                }

                ImGui.text("Saved Maps:");
                String[] savedMaps = MapManager.getSavedMaps();
                for (int i = 0; i < savedMaps.length; i++) {
                    if (i > 5) break;
                    if (ImGui.button(savedMaps[i], 280, 30)) {
                        editorObjects = MapManager.load(savedMaps[i]);
                        selectedObjects.clear();
                        camera.setPosition(new Vector3f(0, 5, 10));
                        gameState = STATE_EDITOR;
                        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                        firstMouse[0] = true;
                    }
                }

                if (ImGui.button("BACK", 280, 40)) {
                    gameState = STATE_MENU;
                }
                ImGui.end();
            }
            else if (gameState == STATE_EDITOR) {
                if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                    gameState = STATE_EDIT_MENU;
                    GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }

                if (!ImGui.getIO().getWantCaptureKeyboard()) {
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS) currentTool = 0;
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) currentTool = 1;
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_E) == GLFW.GLFW_PRESS) currentTool = 2;
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS) currentTool = 3;

                    float vel = 10.0f * dt;
                    Vector3f front = camera.getFront();
                    Vector3f right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();
                    Vector3f camPos = camera.getPosition();
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) camPos.add(new Vector3f(front).mul(vel));
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) camPos.sub(new Vector3f(front).mul(vel));
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) camPos.add(right.mul(vel));
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) camPos.sub(right.mul(vel));
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) camPos.y += vel;
                    if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) camPos.y -= vel;
                    camera.setPosition(camPos);
                }

                VoxelObject hitObj = null;
                if (!ImGui.getIO().getWantCaptureMouse()) {
                    float minDist = Float.MAX_VALUE;
                    for (VoxelObject obj : editorObjects) {
                        Matrix4f model = obj.transform.getModelMatrix();
                        Vector3f min = new Vector3f(Float.MAX_VALUE);
                        Vector3f max = new Vector3f(-Float.MAX_VALUE);
                        if (obj.voxels.isEmpty()) {
                            min.set(obj.transform.position);
                            max.set(obj.transform.position);
                        }
                        for (Voxel v : obj.voxels) {
                            Vector3f p = new Vector3f(v.x, v.y, v.z);
                            model.transformPosition(p);
                            min.min(p);
                            max.max(p);
                        }
                        min.sub(0.5f, 0.5f, 0.5f);
                        max.add(0.5f, 0.5f, 0.5f);

                        Vector3f camPos = camera.getPosition();
                        if (rayIntersectsAABB(camPos, camera.getFront(), min, max)) {
                            float dist = camPos.distance(new Vector3f(min).add(max).mul(0.5f));
                            if (dist < minDist) {
                                minDist = dist;
                                hitObj = obj;
                            }
                        }
                    }

                    boolean lmb = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                    boolean shift = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
                    
                    if (lmb) {
                        if (hitObj != null) {
                            if (shift) {
                                if (selectedObjects.contains(hitObj)) selectedObjects.remove(hitObj);
                                else selectedObjects.add(hitObj);
                            } else {
                                selectedObjects.clear();
                                selectedObjects.add(hitObj);
                            }
                        } else {
                            Vector3f spawnPos = new Vector3f(camera.getPosition()).add(new Vector3f(camera.getFront()).mul(5f));
                            spawnPos.x = Math.round(spawnPos.x);
                            spawnPos.y = Math.round(spawnPos.y);
                            spawnPos.z = Math.round(spawnPos.z);
                            
                            VoxelObject newObj = new VoxelObject(spawnPos.x, spawnPos.y, spawnPos.z);
                            String hex = String.format("#%02x%02x%02x", (int)(currentColor.x*255), (int)(currentColor.y*255), (int)(currentColor.z*255));
                            newObj.voxels.add(new Voxel(0, 0, 0, hex));
                            editorObjects.add(newObj);
                            selectedObjects.clear();
                            selectedObjects.add(newObj);
                        }
                    }
                }

                if (!ImGui.getIO().getWantCaptureKeyboard() && GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS) {
                    editorObjects.removeAll(selectedObjects);
                    selectedObjects.clear();
                }

                if (!ImGui.getIO().getWantCaptureKeyboard() && GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS) {
                    MapManager.save("my_map", editorObjects);
                    gameState = STATE_EDIT_MENU;
                    GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }

                // --- РЕНДЕР 3D ---
                GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);
                shader.use();
                Vector3f camPos = camera.getPosition();
                Matrix4f viewProj = camera.getViewProjection(window.getAspect());
                shader.setUniform("uViewProj", viewProj);
                shader.setUniform("uMode", 0);
                shader.setUniform("uUseTexture", 0);
                shader.setUniform("uAlpha", 1.0f);
                shader.setUniform("uLightDir", new Vector3f(0.5f, 1.0f, 0.3f).normalize());

                shader.setUniform("uModel", new Matrix4f().translate(0, 0, 0));
                shader.setUniform("uColor", new Vector3f(0.2f, 0.2f, 0.2f));
                gridMesh.draw();

                for (VoxelObject obj : editorObjects) {
                    Matrix4f objModel = obj.transform.getModelMatrix();
                    for (Voxel v : obj.voxels) {
                        Matrix4f model = new Matrix4f(objModel).translate(v.x, v.y, v.z);
                        shader.setUniform("uModel", model);
                        shader.setUniform("uColor", hexToColor(v.color));
                        boxMesh.draw();
                    }
                }

                // --- ГИЗМО (Визуал через ImDrawList) ---
                if (!selectedObjects.isEmpty()) {
                    ImDrawList drawList = ImGui.getForegroundDrawList();
                    for (VoxelObject obj : selectedObjects) {
                        Vector3f pos = obj.transform.position;
                        Vector4f ndc = new Vector4f(pos, 1.0f).mul(viewProj);
                        if (ndc.w > 0) {
                            float sx = (ndc.x / ndc.w + 1.0f) * 0.5f * window.getWidth();
                            float sy = (1.0f - ndc.y / ndc.w) * 0.5f * window.getHeight();

                            if (currentTool == 1) { // Move
                                // X Axis
                                Vector4f ndcX = new Vector4f(pos.x + 2, pos.y, pos.z, 1.0f).mul(viewProj);
                                if (ndcX.w > 0) {
                                    float ex = (ndcX.x / ndcX.w + 1.0f) * 0.5f * window.getWidth();
                                    float ey = (1.0f - ndcX.y / ndcX.w) * 0.5f * window.getHeight();
                                    drawList.addLine(sx, sy, ex, ey, 0xFF0000FF, 3.0f); // Red
                                    drawList.addTriangleFilled(ex, ey, ex - 10, ey - 5, ex - 5, ey + 10, 0xFF0000FF);
                                }
                                // Y Axis
                                Vector4f ndcY = new Vector4f(pos.x, pos.y + 2, pos.z, 1.0f).mul(viewProj);
                                if (ndcY.w > 0) {
                                    float ex = (ndcY.x / ndcY.w + 1.0f) * 0.5f * window.getWidth();
                                    float ey = (1.0f - ndcY.y / ndcY.w) * 0.5f * window.getHeight();
                                    drawList.addLine(sx, sy, ex, ey, 0xFF00FF00, 3.0f); // Green
                                    drawList.addTriangleFilled(ex, ey, ex - 5, ey + 10, ex + 5, ey + 10, 0xFF00FF00);
                                }
                                // Z Axis
                                Vector4f ndcZ = new Vector4f(pos.x, pos.y, pos.z + 2, 1.0f).mul(viewProj);
                                if (ndcZ.w > 0) {
                                    float ex = (ndcZ.x / ndcZ.w + 1.0f) * 0.5f * window.getWidth();
                                    float ey = (1.0f - ndcZ.y / ndcZ.w) * 0.5f * window.getHeight();
                                    drawList.addLine(sx, sy, ex, ey, 0xFFFF0000, 3.0f); // Blue
                                    drawList.addTriangleFilled(ex, ey, ex + 10, ey, ex, ey + 10, 0xFFFF0000);
                                }
                            } else if (currentTool == 2) { // Rotate
                                drawList.addCircle(sx, sy, 40, 0xFF0000FF, 32, 2.0f);
                                drawList.addCircle(sx, sy, 50, 0xFF00FF00, 32, 2.0f);
                                drawList.addCircle(sx, sy, 60, 0xFFFF0000, 32, 2.0f);
                            } else if (currentTool == 3) { // Scale
                                drawList.addRectFilled(sx + 30, sy - 5, sx + 40, sy + 5, 0xFF0000FF); // X
                                drawList.addRectFilled(sx - 5, sy - 40, sx + 5, sy - 30, 0xFF00FF00); // Y
                                drawList.addRectFilled(sx - 30, sy + 30, sx - 20, sy + 40, 0xFFFF0000); // Z
                            }
                        }
                    }
                }

                // --- UI ---
                ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
                ImGui.setNextWindowSize(1280, 40, ImGuiCond.Always);
                ImGui.begin("Toolbar", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBackground);
                if (ImGui.button("Select (Q)")) currentTool = 0; ImGui.sameLine();
                if (ImGui.button("Move (W)")) currentTool = 1; ImGui.sameLine();
                if (ImGui.button("Rotate (E)")) currentTool = 2; ImGui.sameLine();
                if (ImGui.button("Scale (R)")) currentTool = 3; ImGui.sameLine();
                if (ImGui.button("Group")) {
                    if (selectedObjects.size() > 1) {
                        VoxelObject group = new VoxelObject();
                        for (VoxelObject obj : selectedObjects) {
                            for (Voxel v : obj.voxels) {
                                group.voxels.add(new Voxel(v.x, v.y, v.z, v.color));
                            }
                        }
                        editorObjects.removeAll(selectedObjects);
                        editorObjects.add(group);
                        selectedObjects.clear();
                        selectedObjects.add(group);
                    }
                }
                ImGui.sameLine();
                if (ImGui.button("+ Spawn")) {
                    Vector3f spawnPos = new Vector3f(camera.getPosition()).add(new Vector3f(camera.getFront()).mul(5f));
                    VoxelObject newObj = new VoxelObject(spawnPos.x, spawnPos.y, spawnPos.z);
                    newObj.voxels.add(new Voxel(0, 0, 0, "#808080"));
                    editorObjects.add(newObj);
                }
                ImGui.sameLine();
                if (ImGui.button("Save Map (ENTER)")) {
                    MapManager.save("my_map", editorObjects);
                    gameState = STATE_EDIT_MENU;
                }
                ImGui.end();

                // Окна Explorer и Properties теперь можно перетаскивать
                ImGui.setNextWindowPos(0, 40, ImGuiCond.Once);
                ImGui.setNextWindowSize(250, 680, ImGuiCond.Once);
                ImGui.begin("Explorer");
                ImGui.text("Workspace");
                for (VoxelObject obj : editorObjects) {
                    if (ImGui.selectable("VoxelObject_" + obj.id.substring(0, 5), selectedObjects.contains(obj))) {
                        selectedObjects.clear();
                        selectedObjects.add(obj);
                    }
                }
                ImGui.end();

                if (!selectedObjects.isEmpty()) {
                    VoxelObject mainObj = selectedObjects.get(0);
                    ImGui.setNextWindowPos(1030, 40, ImGuiCond.Once);
                    ImGui.setNextWindowSize(250, 680, ImGuiCond.Once);
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
                    ImGui.text("Color:");

                    for (int i = 0; i < palette.length; i++) {
                        float[] c = {palette[i].x, palette[i].y, palette[i].z, 1.0f};
                        if (ImGui.colorButton("##col" + i, c, 0, 20, 20)) {
                            String hex = String.format("#%02x%02x%02x", (int)(c[0]*255), (int)(c[1]*255), (int)(c[2]*255));
                            for (VoxelObject obj : selectedObjects) {
                                for (Voxel v : obj.voxels) v.color = hex;
                            }
                        }
                        if (i % 4 != 3) ImGui.sameLine();
                    }

                    ImGui.end();
                }
            }

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            window.swapAndPoll();
        }

        imGuiGl3.dispose();
        imGuiGlfw.dispose();
        ImGui.destroyContext();

        for (Mesh m : meshes) m.cleanup();
        shader.cleanup();
        window.destroy();
    }

    private static boolean rayIntersectsAABB(Vector3f rayOrigin, Vector3f rayDir, Vector3f boxMin, Vector3f boxMax) {
        float tmin = (boxMin.x - rayOrigin.x) / rayDir.x;
        float tmax = (boxMax.x - rayOrigin.x) / rayDir.x;
        if (tmin > tmax) { float temp = tmin; tmin = tmax; tmax = temp; }
        float tymin = (boxMin.y - rayOrigin.y) / rayDir.y;
        float tymax = (boxMax.y - rayOrigin.y) / rayDir.y;
        if (tymin > tymax) { float temp = tymin; tymin = tymax; tymax = temp; }
        if ((tmin > tymax) || (tymin > tmax)) return false;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;
        float tzmin = (boxMin.z - rayOrigin.z) / rayDir.z;
        float tzmax = (boxMax.z - rayOrigin.z) / rayDir.z;
        if (tzmin > tzmax) { float temp = tzmin; tzmin = tzmax; tzmax = temp; }
        if ((tmin > tzmax) || (tzmin > tmax)) return false;
        if (tzmin > tmin) tmin = tzmin;
        if (tzmax < tmax) tmax = tzmax;
        return tmax >= 0;
    }

    private static Vector3f hexToColor(String hex) {
        if (hex == null || hex.length() < 7) return new Vector3f(1, 1, 1);
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return new Vector3f(r / 255f, g / 255f, b / 255f);
        } catch (Exception e) {
            return new Vector3f(1, 1, 1);
        }
    }
}
