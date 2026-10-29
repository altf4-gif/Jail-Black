package game;

import engine.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;

public final class Main {

    static final int STATE_MENU = 0;
    static final int STATE_PLAYING = 1;
    static final int STATE_SHADER_MENU = 2;
    static final int STATE_POST_MENU = 3;
    static int gameState = STATE_MENU;

    static int postMode = 0;
    static float scrollY = 0;
    static float scrollTarget = 0;

    static class GameObject {
        Mesh mesh; Vector3f color; Vector3f position; Vector3f rotation;
        Vector3f rotVel;
        float scale; int type;
        Texture texture;
        float alpha = 1.0f;
        Vector3f scaleVec = new Vector3f(1, 1, 1);

        GameObject(Mesh m, Vector3f c, Vector3f p, int t) {
            mesh = m; color = new Vector3f(c); position = new Vector3f(p); rotation = new Vector3f(); rotVel = new Vector3f(); scale = 1; type = t;
        }
    }

    public static void main(String[] args) {
        Window window = new Window(1280, 720, "Java 3D Engine - Post Processing");
        window.create();

        GL33.glEnable(GL33.GL_DEPTH_TEST);
        GL33.glEnable(GL33.GL_CULL_FACE);
        GL33.glCullFace(GL33.GL_BACK);

        ShaderProgram shader = ShaderProgram.fromResources("shaders/basic.vert", "shaders/basic.frag");
        ShaderProgram postShader = ShaderProgram.fromResources("shaders/post.vert", "shaders/post.frag");

        Camera camera = new Camera();
        List<GameObject> objects = new ArrayList<>();
        Set<Mesh> meshes = new HashSet<>();
        List<Texture> textures = new ArrayList<>();

        Mesh boxMesh = MeshFactory.box(1, 1, 1);
        Mesh floorMesh = MeshFactory.box(100, 0.4f, 100);
        Mesh wallMesh = MeshFactory.box(20, 8, 1);
        Mesh pixelMesh = MeshFactory.box(0.3f, 0.3f, 0.2f);
        Mesh playerBodyMesh = MeshFactory.capsule(0.4f, 1.6f, 16);
        Mesh sphereMesh = MeshFactory.sphere(0.5f, 16, 16);
        Mesh tetraMesh = MeshFactory.tetrahedron(1.0f);
        Mesh uiQuad = MeshFactory.box(1, 1, 0.1f);
        Mesh screenQuad = MeshFactory.quad2D();
        
        meshes.addAll(Arrays.asList(boxMesh, floorMesh, wallMesh, pixelMesh, playerBodyMesh, sphereMesh, tetraMesh, uiQuad, screenQuad));
        Mesh[] chaosMeshes = {boxMesh, sphereMesh, tetraMesh};
        Mesh[] baseShapes = {boxMesh, tetraMesh, sphereMesh};

        objects.add(new GameObject(floorMesh, new Vector3f(0.3f, 0.33f, 0.38f), new Vector3f(0, -0.2f, 0), 0));
        objects.add(new GameObject(wallMesh, new Vector3f(0.4f, 0.45f, 0.5f), new Vector3f(0, 4f, -12f), 0));

        Vector3f[] colors = { new Vector3f(0.85f, 0.35f, 0.32f), new Vector3f(0.35f, 0.78f, 0.45f), new Vector3f(0.35f, 0.55f, 0.90f) };
        float[] posX = {-2.5f, 0f, 2.5f};
        float[] centerH = {1.6f, 2.8f, 2.1f};
        float[] frontH = {0.8f, 1.2f, 0.5f};
        float[] backH = {3.8f, 3.2f, 4.2f};
        
        for (int i = 0; i < 3; i++) addPillar(objects, meshes, posX[i], 0f, centerH[i], baseShapes[i], colors[i], 0);
        for (int i = 0; i < 3; i++) addPillar(objects, meshes, posX[i], 6.0f, frontH[i], baseShapes[i], colors[i], 1);
        for (int i = 0; i < 3; i++) addPillar(objects, meshes, posX[i], -6.0f, backH[i], baseShapes[i], colors[i], 2);

        objects.add(new GameObject(boxMesh, new Vector3f(1, 1, 1), new Vector3f(0, 8.0f, 0), 3));

        for (int i = 1; i <= 10; i++) {
            Texture tex = createGradientTexture(i);
            textures.add(tex);
            GameObject cube = new GameObject(boxMesh, new Vector3f(1, 1, 1), new Vector3f(-9f + (i-1) * 2f, 1f, -11.4f), 0);
            cube.texture = tex; objects.add(cube);
        }

        Random rand = new Random();
        for (int i = 0; i < 10; i++) {
            Texture tex = createRandomGradientTexture(rand);
            textures.add(tex);
            GameObject cube = new GameObject(boxMesh, new Vector3f(1, 1, 1), new Vector3f(-9f + i * 2f, 3f, -11.4f), 2);
            cube.texture = tex; objects.add(cube);
        }

        for (int i = 0; i < 5; i++) {
            GameObject blurSphere = new GameObject(sphereMesh, new Vector3f(0.8f, 0.8f, 0.9f), new Vector3f(-8f + i * 4f, 5.5f, -11.4f), 4);
            blurSphere.scale = 2.5f; blurSphere.alpha = 0.3f; objects.add(blurSphere);
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                objects.add(new GameObject(boxMesh, new Vector3f(0.8f, 0.2f, 0.2f), new Vector3f(-6f + i * 1.2f, 4f + j * 1.2f, -11.4f), 2));
            }
        }

        List<GameObject> fpsObjects = new ArrayList<>();
        for (int i = 0; i < 75; i++) { 
            GameObject p = new GameObject(pixelMesh, new Vector3f(0.2f, 1f, 0.2f), new Vector3f(0, 0, 0), 0);
            p.scale = 0.0f; fpsObjects.add(p); objects.add(p);
        }
        updateFpsObjects(fpsObjects, "FPS:0", 3f, 6.5f, -11.4f);

        GameObject starObject = new GameObject(sphereMesh, new Vector3f(1.0f, 1.0f, 0.2f), new Vector3f(30, 80, 30), 0);
        starObject.scale = 15.0f;

        Framebuffer mainBuffer = new Framebuffer(1280, 720);

        Vector3f playerBase = new Vector3f(0, 0, 8f);
        float currentHeight = 1.6f;
        boolean isWalking = false, showSkybox = false, thirdPerson = false, starFollowsCam = false;
        int graphicsMode = 0; // 0 = Default Nice
        float verticalVelocity = 0;
        boolean isGrounded = true;
        final boolean[] topDown = new boolean[]{false};

        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        double[] lastX = new double[1], lastY = new double[1];
        boolean[] firstMouse = new boolean[]{true};
        GLFW.glfwSetCursorPosCallback(window.getHandle(), (win, xpos, ypos) -> {
            if (firstMouse[0]) { lastX[0] = xpos; lastY[0] = ypos; firstMouse[0] = false; }
            if (gameState == STATE_PLAYING && !topDown[0]) camera.rotate((float)(xpos - lastX[0]), (float)(lastY[0] - ypos));
            lastX[0] = xpos; lastY[0] = ypos;
        });

        GLFW.glfwSetScrollCallback(window.getHandle(), (win, xoffset, yoffset) -> {
            if (gameState == STATE_POST_MENU) {
                scrollTarget -= (float)yoffset * 60.0f;
                float maxScroll = 800; // Максимальная прокрутка
                if (scrollTarget < 0) scrollTarget = 0;
                if (scrollTarget > maxScroll) scrollTarget = maxScroll;
            }
        });

        boolean[] keys = new boolean[GLFW.GLFW_KEY_LAST + 1];
        double lastTime = GLFW.glfwGetTime();
        float time = 0, lastChaosChange = 0;
        int frames = 0; double fpsTimer = 0;

        String[] postNames = {
            "Default", "Contrast", "Saturated", "Saturated Contrast", "Inverted", "Grayscale", "Grayscale Contrast", "Unchona", "Acid Sky",
            "Adventure", "Amber", "Cloudy", "Crime", "Cyan Misty", "Dark", "Delux", "Dust", "Evening",
            "Fear", "Film", "Flashback", "Fog", "Ghost", "Gloom", "Golden Age", "Green Matrix", "Horror",
            "Lagoon", "Landscape", "Lime", "Lovely", "Memories", "Midnight", "Nature", "Ocean", "Pastel",
            "Poison", "Extra Pink", "Radiant", "Romantic", "Scenic", "Scifi", "Sea", "Serenity", "Soft",
            "Storm", "Style", "Sunny", "Sunset", "Terror", "Toxic", "Tropic", "Ultra Clarity", "Vacant",
            "Warm", "Western", "Winter"
        };

        while (!window.shouldClose()) {
            double currentTime = GLFW.glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime; time += dt;

            frames++; fpsTimer += dt;
            if (fpsTimer >= 1.0) { updateFpsObjects(fpsObjects, "FPS:" + frames, 3f, 6.5f, -11.4f); frames = 0; fpsTimer -= 1.0; }

            GL33.glClearColor(0.08f, 0.08f, 0.08f, 1.0f);
            GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);

            if (gameState == STATE_MENU) {
                shader.use();
                GL33.glDisable(GL33.GL_DEPTH_TEST);
                Matrix4f uiProj = new Matrix4f().ortho(0, window.getWidth(), window.getHeight(), 0, -1, 1);
                shader.setUniform("uViewProj", uiProj);
                shader.setUniform("uUseTexture", 0);
                shader.setUniform("uAlpha", 1.0f);
                shader.setUniform("uIsPostPreview", 0);

                int btnW = 280, btnH = 60, startX = window.getWidth() / 2 - btnW / 2, startY = 150, gap = 20;
                drawUIButton(shader, uiQuad, "SIMULATION", startX, startY, btnW, btnH, new Vector3f(0.2f, 0.8f, 0.2f));
                drawUIButton(shader, uiQuad, "EDIT", startX, startY + btnH + gap, btnW, btnH, new Vector3f(0.2f, 0.5f, 0.8f));
                drawUIButton(shader, uiQuad, "MAP", startX, startY + (btnH + gap) * 2, btnW, btnH, new Vector3f(0.8f, 0.8f, 0.2f));
                drawUIButton(shader, uiQuad, "EXIT", startX, startY + (btnH + gap) * 3, btnW, btnH, new Vector3f(0.8f, 0.2f, 0.2f));

                double[] mx = new double[1], my = new double[1];
                GLFW.glfwGetCursorPos(window.getHandle(), mx, my);
                boolean clicked = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

                if (clicked) {
                    if (mx[0] >= startX && mx[0] <= startX + btnW && my[0] >= startY && my[0] <= startY + btnH) {
                        gameState = STATE_PLAYING;
                        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                        firstMouse[0] = true;
                    }
                    else if (mx[0] >= startX && mx[0] <= startX + btnW && my[0] >= startY + btnH + gap && my[0] <= startY + btnH * 2 + gap) System.out.println("EDIT");
                    else if (mx[0] >= startX && mx[0] <= startX + btnW && my[0] >= startY + (btnH + gap) * 2 && my[0] <= startY + btnH * 3 + gap * 2) System.out.println("MAP");
                    else if (mx[0] >= startX && mx[0] <= startX + btnW && my[0] >= startY + (btnH + gap) * 3 && my[0] <= startY + btnH * 4 + gap * 3) GLFW.glfwSetWindowShouldClose(window.getHandle(), true);
                }
                GL33.glEnable(GL33.GL_DEPTH_TEST);

            } else if (gameState == STATE_PLAYING) {
                if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                    gameState = STATE_MENU;
                    GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }

                boolean qP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS;
                if (qP && !keys[GLFW.GLFW_KEY_Q]) {
                    gameState = STATE_SHADER_MENU;
                    GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }
                keys[GLFW.GLFW_KEY_Q] = qP;

                boolean gP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
                if (gP && !keys[GLFW.GLFW_KEY_G]) {
                    starFollowsCam = !starFollowsCam;
                    if (!starFollowsCam) starObject.position.set(30, 80, 30);
                }
                keys[GLFW.GLFW_KEY_G] = gP;

                boolean eP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_E) == GLFW.GLFW_PRESS;
                if (eP && !keys[GLFW.GLFW_KEY_E]) { isWalking = !isWalking; isGrounded = true; }
                keys[GLFW.GLFW_KEY_E] = eP;

                boolean rP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (rP && !keys[GLFW.GLFW_KEY_R]) showSkybox = !showSkybox;
                keys[GLFW.GLFW_KEY_R] = rP;

                boolean fP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_F) == GLFW.GLFW_PRESS;
                if (fP && !keys[GLFW.GLFW_KEY_F]) thirdPerson = !thirdPerson;
                keys[GLFW.GLFW_KEY_F] = fP;

                boolean tP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_T) == GLFW.GLFW_PRESS;
                if (tP && !keys[GLFW.GLFW_KEY_T]) topDown[0] = !topDown[0];
                keys[GLFW.GLFW_KEY_T] = tP;

                float vel = 8.0f * dt;
                boolean space = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
                boolean shift = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
                boolean w = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
                boolean s = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
                boolean a = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
                boolean d = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;

                Vector3f moveDir = new Vector3f();
                if (topDown[0]) {
                    if (w) moveDir.z -= 1; if (s) moveDir.z += 1; if (a) moveDir.x -= 1; if (d) moveDir.x += 1;
                } else {
                    Vector3f front = camera.getFront();
                    Vector3f right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();
                    front.y = 0; front.normalize();
                    if (w) moveDir.add(front); if (s) moveDir.sub(front); if (d) moveDir.add(right); if (a) moveDir.sub(right);
                }

                if (moveDir.lengthSquared() > 0) {
                    moveDir.normalize().mul(vel);
                    playerBase.add(moveDir);
                }

                if (isWalking) {
                    float targetH = shift ? 0.8f : 1.6f;
                    currentHeight += (targetH - currentHeight) * 10f * dt;
                    if (space && isGrounded) { verticalVelocity = 6.0f; isGrounded = false; }
                    verticalVelocity -= 20.0f * dt;
                    playerBase.y += verticalVelocity * dt;
                    if (playerBase.y <= 0) { playerBase.y = 0; verticalVelocity = 0; isGrounded = true; }
                } else {
                    currentHeight = 1.6f;
                    if (space) playerBase.y += vel;
                    if (shift) playerBase.y -= vel;
                }

                for (GameObject obj : objects) {
                    if (obj.type == 1) obj.scale = 1.0f + (float) Math.sin(time * 4.0f + obj.position.x) * 0.6f;
                    else if (obj.type == 2) { obj.rotation.y += dt * 2.5f; obj.rotation.x += dt * 1.0f; }
                    else if (obj.type == 3) {
                        obj.position.x = (float) Math.sin(time * 1.5f) * 4.0f;
                        obj.position.z = -2f + (float) Math.cos(time * 1.5f) * 4.0f;
                        obj.rotation.x += obj.rotVel.x * dt; obj.rotation.y += obj.rotVel.y * dt; obj.rotation.z += obj.rotVel.z * dt;
                        if (time - lastChaosChange > 1.0f) {
                            lastChaosChange = time;
                            obj.mesh = chaosMeshes[rand.nextInt(chaosMeshes.length)]; 
                            obj.color.set(rand.nextFloat() * 0.5f + 0.5f, rand.nextFloat() * 0.5f + 0.5f, rand.nextFloat() * 0.5f + 0.5f); 
                            obj.scale = 0.6f + rand.nextFloat() * 1.5f; 
                            obj.rotVel.set(rand.nextFloat() * 6 - 3, rand.nextFloat() * 6 - 3, rand.nextFloat() * 6 - 3); 
                        }
                    } else if (obj.type == 4) obj.position.y += (float) Math.sin(time * 2.0f + obj.position.x) * 0.01f;
                }

                Vector3f camPos = new Vector3f(), target = new Vector3f(), camUp = new Vector3f(0, 1, 0);
                Vector3f front = camera.getFront();

                if (topDown[0]) { camPos.set(playerBase.x, playerBase.y + 20f, playerBase.z); target.set(playerBase); camUp.set(0, 0, -1); }
                else if (thirdPerson && isWalking) { camPos.set(playerBase).add(0, currentHeight, 0).sub(new Vector3f(front).mul(4.5f)); target.set(playerBase).add(0, currentHeight * 0.5f, 0); }
                else { float eyeHeight = isWalking ? currentHeight - 0.2f : 0f; camPos.set(playerBase.x, playerBase.y + eyeHeight, playerBase.z); target.set(camPos).add(front); }

                Vector3f starPos = starFollowsCam ? new Vector3f(camPos).add(new Vector3f(camera.getFront()).mul(150)) : new Vector3f(30, 80, 30);
                Vector3f lightDir = new Vector3f(starPos).normalize(); 

                mainBuffer.bind();
                GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);
                shader.use();
                shader.setUniform("uViewProj", camera.getViewProjection(camPos, target, camUp, window.getAspect()));
                shader.setUniform("uLightDir", lightDir);
                shader.setUniform("uCamPos", camPos);
                shader.setUniform("uMode", graphicsMode);
                shader.setUniform("uTime", time);
                shader.setUniform("uIsPostPreview", 0);

                if (graphicsMode == 6) GL33.glPolygonMode(GL33.GL_FRONT_AND_BACK, GL33.GL_LINE);

                if (showSkybox) {
                    GL33.glDepthMask(false); GL33.glCullFace(GL33.GL_FRONT);
                    shader.setUniform("uUseTexture", 0); shader.setUniform("uAlpha", 1.0f);
                    shader.setUniform("uModel", new Matrix4f().translate(camPos).scale(200));
                    shader.setUniform("uColor", new Vector3f(0.4f, 0.6f, 0.9f));
                    boxMesh.draw();
                    GL33.glCullFace(GL33.GL_BACK); GL33.glDepthMask(true);
                }

                Matrix4f starModel = new Matrix4f().translate(starPos).scale(starObject.scale);
                shader.setUniform("uModel", starModel);
                shader.setUniform("uColor", starObject.color);
                shader.setUniform("uUseTexture", 0);
                shader.setUniform("uAlpha", 1.0f);
                shader.setUniform("uUnlit", 1);
                starObject.mesh.draw();
                shader.setUniform("uUnlit", 0);

                for (GameObject obj : objects) {
                    if (obj.scale <= 0 || obj.alpha < 1.0f) continue;
                    Matrix4f model = new Matrix4f().translate(obj.position).rotateXYZ(obj.rotation.x, obj.rotation.y, obj.rotation.z).scale(obj.scale);
                    shader.setUniform("uModel", model);
                    shader.setUniform("uColor", obj.color); shader.setUniform("uAlpha", 1.0f);
                    if (obj.texture != null) { shader.setUniform("uUseTexture", 1); obj.texture.bind(); }
                    else shader.setUniform("uUseTexture", 0);
                    obj.mesh.draw();
                }

                if (isWalking) {
                    float bodyScaleY = currentHeight / 1.6f;
                    Matrix4f bodyModel = new Matrix4f().translate(playerBase.x, playerBase.y, playerBase.z).scale(1.0f, bodyScaleY, 1.0f);
                    shader.setUniform("uModel", bodyModel); shader.setUniform("uColor", new Vector3f(0.2f, 0.8f, 0.2f));
                    shader.setUniform("uUseTexture", 0); shader.setUniform("uAlpha", 1.0f);
                    playerBodyMesh.draw();
                }

                GL33.glEnable(GL33.GL_BLEND); GL33.glBlendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA); GL33.glDepthMask(false);
                for (GameObject obj : objects) {
                    if (obj.scale <= 0 || obj.alpha >= 1.0f) continue;
                    Matrix4f model = new Matrix4f().translate(obj.position).rotateXYZ(obj.rotation.x, obj.rotation.y, obj.rotation.z).scale(obj.scale);
                    shader.setUniform("uModel", model); shader.setUniform("uColor", obj.color); shader.setUniform("uAlpha", obj.alpha); shader.setUniform("uUseTexture", 0);
                    obj.mesh.draw();
                }
                GL33.glDepthMask(true); GL33.glDisable(GL33.GL_BLEND);

                if (graphicsMode == 6) GL33.glPolygonMode(GL33.GL_FRONT_AND_BACK, GL33.GL_FILL);

                mainBuffer.unbind(window.getWidth(), window.getHeight());
                GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);
                postShader.use();
                postShader.setUniform("uUseTexture", 1);
                postShader.setUniform("uMode", graphicsMode);
                postShader.setUniform("uPostMode", postMode);
                postShader.setUniform("uTime", time);
                mainBuffer.getColorTexture().bind();
                screenQuad.draw();

            } else if (gameState == STATE_SHADER_MENU) {
                boolean qP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS;
                boolean escP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
                
                if ((qP && !keys[GLFW.GLFW_KEY_Q]) || (escP && !keys[GLFW.GLFW_KEY_ESCAPE])) {
                    gameState = STATE_PLAYING;
                    GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                    firstMouse[0] = true;
                }
                keys[GLFW.GLFW_KEY_Q] = qP;
                keys[GLFW.GLFW_KEY_ESCAPE] = escP;

                shader.use();
                GL33.glDisable(GL33.GL_DEPTH_TEST);
                Matrix4f uiProj = new Matrix4f().ortho(0, window.getWidth(), window.getHeight(), 0, -1, 1);
                shader.setUniform("uViewProj", uiProj);
                shader.setUniform("uUseTexture", 0);
                shader.setUniform("uAlpha", 1.0f);
                shader.setUniform("uIsPostPreview", 0);

                int startX = window.getWidth() / 2 - 310;
                int startY = 50;
                int btnW = 300;
                int btnH = 40;
                int gap = 10;
                
                String[] modesLeft = {"0. DEFAULT (NICE)", "1. MINECRAFT", "2. PS2", "3. DITHER", "4. WIREFRAME", "5. ASCII", "6. PIXEL ART", "7. TOY/DIORAMA"};
                String[] modesRight = {"8. CEL-SHADING", "9. FLAT", "10. LOW-POLY", "11. NO AA", "12. ALUMINUM", "13. PSYCHEDELIC"};
                
                for (int i = 0; i < modesLeft.length; i++) {
                    Vector3f col = (graphicsMode == i) ? new Vector3f(0.2f, 0.8f, 0.2f) : new Vector3f(0.5f, 0.5f, 0.5f);
                    drawUIButton(shader, uiQuad, modesLeft[i], startX, startY + i * (btnH + gap), btnW, btnH, col);
                }
                
                for (int i = 0; i < modesRight.length; i++) {
                    Vector3f col = (graphicsMode == i + 8) ? new Vector3f(0.2f, 0.8f, 0.2f) : new Vector3f(0.5f, 0.5f, 0.5f);
                    drawUIButton(shader, uiQuad, modesRight[i], startX + btnW + 20, startY + i * (btnH + gap), btnW, btnH, col);
                }

                Vector3f postCol = new Vector3f(0.8f, 0.5f, 0.2f);
                drawUIButton(shader, uiQuad, "POST PROCESSING", startX + btnW + 20, startY + 7 * (btnH + gap), btnW, btnH, postCol);
                
                drawUIButton(shader, uiQuad, "CLOSE (Q)", startX + (btnW + 20) / 2, startY + 9 * (btnH + gap), btnW, btnH, new Vector3f(0.8f, 0.2f, 0.2f));

                double[] mx = new double[1], my = new double[1];
                GLFW.glfwGetCursorPos(window.getHandle(), mx, my);
                boolean clicked = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

                if (clicked) {
                    for (int i = 0; i < modesLeft.length; i++) {
                        if (mx[0] >= startX && mx[0] <= startX + btnW && my[0] >= startY + i * (btnH + gap) && my[0] <= startY + i * (btnH + gap) + btnH) {
                            graphicsMode = i;
                        }
                    }
                    for (int i = 0; i < modesRight.length; i++) {
                        if (mx[0] >= startX + btnW + 20 && mx[0] <= startX + btnW * 2 + 20 && my[0] >= startY + i * (btnH + gap) && my[0] <= startY + i * (btnH + gap) + btnH) {
                            graphicsMode = i + 8;
                        }
                    }
                    if (mx[0] >= startX + btnW + 20 && mx[0] <= startX + btnW * 2 + 20 && my[0] >= startY + 7 * (btnH + gap) && my[0] <= startY + 7 * (btnH + gap) + btnH) {
                        gameState = STATE_POST_MENU;
                    }
                    if (mx[0] >= startX + (btnW + 20) / 2 && mx[0] <= startX + (btnW + 20) / 2 + btnW && my[0] >= startY + 9 * (btnH + gap) && my[0] <= startY + 9 * (btnH + gap) + btnH) {
                        gameState = STATE_PLAYING;
                        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                        firstMouse[0] = true;
                    }
                }
                GL33.glEnable(GL33.GL_DEPTH_TEST);

            } else if (gameState == STATE_POST_MENU) {
                scrollY += (scrollTarget - scrollY) * 10f * dt;

                boolean qP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS;
                boolean escP = GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
                
                if ((qP && !keys[GLFW.GLFW_KEY_Q]) || (escP && !keys[GLFW.GLFW_KEY_ESCAPE])) {
                    gameState = STATE_SHADER_MENU;
                }
                keys[GLFW.GLFW_KEY_Q] = qP;
                keys[GLFW.GLFW_KEY_ESCAPE] = escP;

                shader.use();
                GL33.glDisable(GL33.GL_DEPTH_TEST);
                Matrix4f uiProj = new Matrix4f().ortho(0, window.getWidth(), window.getHeight(), 0, -1, 1);
                shader.setUniform("uViewProj", uiProj);
                shader.setUniform("uUseTexture", 0);
                shader.setUniform("uAlpha", 1.0f);

                drawText(shader, uiQuad, "Post Processing", 20, 20, 4f, new Vector3f(1,1,1));

                int cols = 9;
                int btnW = 90;
                int btnH = 70;
                int gap = 10;
                int startX = (window.getWidth() - (cols * (btnW + gap))) / 2;
                int startY = 80;

                GL33.glEnable(GL33.GL_SCISSOR_TEST);
                GL33.glScissor(0, 0, window.getWidth(), window.getHeight() - 80); // Ограничиваем снизу

                double[] mx = new double[1], my = new double[1];
                GLFW.glfwGetCursorPos(window.getHandle(), mx, my);
                boolean clicked = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

                for (int i = 0; i < postNames.length; i++) {
                    int row = i / cols;
                    int col = i % cols;
                    float x = startX + col * (btnW + gap);
                    float y = startY + row * (btnH + gap) - scrollY;

                    if (y < 60 || y > window.getHeight()) continue;

                    // Рисуем превью с рамкой
                    Matrix4f model = new Matrix4f().translate(x + btnW/2, y + btnH/2, 0).scale(btnW, btnH, 1);
                    shader.setUniform("uModel", model); 
                    shader.setUniform("uColor", new Vector3f(1, 1, 1));
                    shader.setUniform("uPostMode", i);
                    shader.setUniform("uIsPostPreview", 1);
                    uiQuad.draw();

                    // Текст
                    drawText(shader, uiQuad, postNames[i], x + 5, y + btnH - 15, 2f, new Vector3f(1, 1, 1));

                    // Клик
                    if (clicked && mx[0] >= x && mx[0] <= x + btnW && my[0] >= y && my[0] <= y + btnH) {
                        postMode = i;
                    }
                }

                shader.setUniform("uIsPostPreview", 0);
                GL33.glDisable(GL33.GL_SCISSOR_TEST);

                drawUIButton(shader, uiQuad, "BACK (Q)", 20, window.getHeight() - 60, 200, 40, new Vector3f(0.8f, 0.2f, 0.2f));
                if (clicked && mx[0] >= 20 && mx[0] <= 220 && my[0] >= window.getHeight() - 60 && my[0] <= window.getHeight() - 20) {
                    gameState = STATE_SHADER_MENU;
                }

                GL33.glEnable(GL33.GL_DEPTH_TEST);
            }
            window.swapAndPoll();
        }

        mainBuffer.cleanup();
        for (Mesh m : meshes) m.cleanup();
        for (Texture t : textures) t.cleanup();
        shader.cleanup(); postShader.cleanup();
        window.destroy();
    }

    private static void drawText(ShaderProgram shader, Mesh uiMesh, String text, float x, float y, float pixelSize, Vector3f color) {
        shader.setUniform("uIsPostPreview", 0); // КРИТИЧНО ВАЖНО: отключаем режим превью для текста!
        float charWidth = 12f * (pixelSize / 4f);
        for (int c = 0; c < text.length(); c++) {
            boolean[][] bitmap = getBitmap(text.charAt(c));
            if (bitmap == null) continue;
            for (int py = 0; py < bitmap.length; py++) {
                for (int px = 0; px < bitmap[py].length; px++) {
                    if (bitmap[py][px]) {
                        Matrix4f pModel = new Matrix4f().translate(x + c * charWidth + px * pixelSize, y + py * pixelSize, 0).scale(pixelSize, pixelSize, 1);
                        shader.setUniform("uModel", pModel); shader.setUniform("uColor", color);
                        uiMesh.draw();
                    }
                }
            }
        }
    }

    private static void drawUIButton(ShaderProgram shader, Mesh uiMesh, String text, float x, float y, float w, float h, Vector3f color) {
        shader.setUniform("uIsPostPreview", 0);
        Matrix4f model = new Matrix4f().translate(x + w/2, y + h/2, 0).scale(w, h, 1);
        shader.setUniform("uModel", model); shader.setUniform("uColor", color);
        uiMesh.draw();
        float charWidth = 12f, pixelSize = 3f, textWidth = text.length() * charWidth;
        float startX = x + (w - textWidth) / 2f, startY = y + (h - 5 * pixelSize) / 2f;
        for (int c = 0; c < text.length(); c++) {
            boolean[][] bitmap = getBitmap(text.charAt(c));
            if (bitmap == null) continue;
            for (int py = 0; py < bitmap.length; py++) {
                for (int px = 0; px < bitmap[py].length; px++) {
                    if (bitmap[py][px]) {
                        Matrix4f pModel = new Matrix4f().translate(startX + c * charWidth + px * pixelSize, startY + py * pixelSize, 0).scale(pixelSize, pixelSize, 1);
                        shader.setUniform("uModel", pModel); shader.setUniform("uColor", new Vector3f(1, 1, 1));
                        uiMesh.draw();
                    }
                }
            }
        }
    }

    private static void addPillar(List<GameObject> objects, Set<Mesh> meshes, float x, float z, float height, Mesh shapeMesh, Vector3f shapeColor, int shapeType) {
        Mesh pillarMesh = MeshFactory.cylinder(0.4f, height, 32);
        meshes.add(pillarMesh);
        objects.add(new GameObject(pillarMesh, new Vector3f(0.58f, 0.61f, 0.66f), new Vector3f(x, 0, z), 0));
        objects.add(new GameObject(shapeMesh, new Vector3f(shapeColor), new Vector3f(x, height + 0.5f, z), shapeType));
    }

    private static Texture createGradientTexture(int numColors) {
        int size = 32; BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics(); int bandHeight = Math.max(1, size / numColors);
        for (int i = 0; i < numColors; i++) { Color c = Color.getHSBColor((float) i / numColors, 0.8f, 0.9f); g.setColor(c); g.fillRect(0, i * bandHeight, size, bandHeight); }
        g.dispose(); return Texture.createFromBufferedImage(img);
    }

    private static Texture createRandomGradientTexture(Random rand) {
        int size = 32; int numColors = rand.nextInt(10) + 1;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics(); int bandHeight = Math.max(1, size / numColors);
        for (int i = 0; i < numColors; i++) { Color c = Color.getHSBColor(rand.nextFloat(), 0.8f, 0.9f); g.setColor(c); g.fillRect(0, i * bandHeight, size, bandHeight); }
        g.dispose(); return Texture.createFromBufferedImage(img);
    }

    private static void updateFpsObjects(List<GameObject> pixels, String text, float startX, float startY, float startZ) {
        int pixelIndex = 0; float charWidth = 1.5f, pixelSize = 0.4f;
        for (int c = 0; c < text.length(); c++) {
            boolean[][] bitmap = getBitmap(text.charAt(c)); if (bitmap == null) continue;
            for (int y = 0; y < bitmap.length; y++) {
                for (int x = 0; x < bitmap[y].length; x++) {
                    if (pixelIndex >= pixels.size()) break;
                    GameObject p = pixels.get(pixelIndex++);
                    if (bitmap[y][x]) { p.position.set(startX + c * charWidth + x * pixelSize, startY - y * pixelSize, startZ); p.scale = 1.0f; }
                    else p.scale = 0.0f;
                }
            }
        }
        while (pixelIndex < pixels.size()) pixels.get(pixelIndex++).scale = 0.0f;
    }

    private static boolean[][] getBitmap(char ch) {
        switch (ch) {
            case '0': return new boolean[][]{{true,true,true},{true,false,true},{true,false,true},{true,false,true},{true,true,true}};
            case '1': return new boolean[][]{{false,true,false},{true,true,false},{false,true,false},{false,true,false},{true,true,true}};
            case '2': return new boolean[][]{{true,true,true},{false,false,true},{true,true,true},{true,false,false},{true,true,true}};
            case '3': return new boolean[][]{{true,true,true},{false,false,true},{true,true,true},{false,false,true},{true,true,true}};
            case '4': return new boolean[][]{{true,false,true},{true,false,true},{true,true,true},{false,false,true},{false,false,true}};
            case '5': return new boolean[][]{{true,true,true},{true,false,false},{true,true,true},{false,false,true},{true,true,true}};
            case '6': return new boolean[][]{{true,true,true},{true,false,false},{true,true,true},{true,false,true},{true,true,true}};
            case '7': return new boolean[][]{{true,true,true},{false,false,true},{false,true,false},{false,true,false},{false,true,false}};
            case '8': return new boolean[][]{{true,true,true},{true,false,true},{true,true,true},{true,false,true},{true,true,true}};
            case '9': return new boolean[][]{{true,true,true},{true,false,true},{true,true,true},{false,false,true},{true,true,true}};
            case 'A': return new boolean[][]{{false,true,false},{true,false,true},{true,true,true},{true,false,true},{true,false,true}};
            case 'B': return new boolean[][]{{true,true,false},{true,false,true},{true,true,false},{true,false,true},{true,true,false}};
            case 'C': return new boolean[][]{{false,true,true},{true,false,false},{true,false,false},{true,false,false},{false,true,true}};
            case 'D': return new boolean[][]{{true,true,false},{true,false,true},{true,false,true},{true,false,true},{true,true,false}};
            case 'E': return new boolean[][]{{true,true,true},{true,false,false},{true,true,true},{true,false,false},{true,true,true}};
            case 'F': return new boolean[][]{{true,true,true},{true,false,false},{true,true,true},{true,false,false},{true,false,false}};
            case 'G': return new boolean[][]{{false,true,true},{true,false,false},{true,false,true},{true,false,true},{false,true,true}};
            case 'H': return new boolean[][]{{true,false,true},{true,false,true},{true,true,true},{true,false,true},{true,false,true}};
            case 'I': return new boolean[][]{{true,true,true},{false,true,false},{false,true,false},{false,true,false},{true,true,true}};
            case 'J': return new boolean[][]{{false,false,true},{false,false,true},{false,false,true},{true,false,true},{false,true,false}};
            case 'K': return new boolean[][]{{true,false,true},{true,false,true},{true,true,false},{true,false,true},{true,false,true}};
            case 'L': return new boolean[][]{{true,false,false},{true,false,false},{true,false,false},{true,false,false},{true,true,true}};
            case 'M': return new boolean[][]{{true,false,true},{true,true,true},{true,false,true},{true,false,true},{true,false,true}};
            case 'N': return new boolean[][]{{true,false,true},{true,true,true},{true,true,true},{true,true,true},{true,false,true}};
            case 'O': return new boolean[][]{{true,true,true},{true,false,true},{true,false,true},{true,false,true},{true,true,true}};
            case 'P': return new boolean[][]{{true,true,true},{true,false,true},{true,true,true},{true,false,false},{true,false,false}};
            case 'Q': return new boolean[][]{{true,true,true},{true,false,true},{true,true,true},{true,false,true},{false,true,true}};
            case 'R': return new boolean[][]{{true,true,true},{true,false,true},{true,true,true},{true,false,true},{true,false,true}};
            case 'S': return new boolean[][]{{true,true,true},{true,false,false},{true,true,true},{false,false,true},{true,true,true}};
            case 'T': return new boolean[][]{{true,true,true},{false,true,false},{false,true,false},{false,true,false},{false,true,false}};
            case 'U': return new boolean[][]{{true,false,true},{true,false,true},{true,false,true},{true,false,true},{true,true,true}};
            case 'V': return new boolean[][]{{true,false,true},{true,false,true},{true,false,true},{false,true,false},{false,true,false}};
            case 'W': return new boolean[][]{{true,false,true},{true,false,true},{true,false,true},{true,true,true},{true,false,true}};
            case 'X': return new boolean[][]{{true,false,true},{true,false,true},{false,true,false},{true,false,true},{true,false,true}};
            case 'Y': return new boolean[][]{{true,false,true},{true,false,true},{false,true,false},{false,true,false},{false,true,false}};
            case 'Z': return new boolean[][]{{true,true,true},{false,false,true},{false,true,false},{true,false,false},{true,true,true}};
            case ':': return new boolean[][]{{false},{true},{false},{true},{false}};
            case '.': return new boolean[][]{{false},{false},{false},{false},{true}};
            case '(': return new boolean[][]{{false,true},{true,false},{true,false},{true,false},{false,true}};
            case ')': return new boolean[][]{{true,false},{false,true},{false,true},{false,true},{true,false}};
            case '-': return new boolean[][]{{false},{false},{true},{false},{false}};
            case '/': return new boolean[][]{{false,false,true},{false,true,false},{false,true,false},{true,false,false},{true,false,false}};
            case ' ': return new boolean[][]{{false},{false},{false},{false},{false}};
            default: return null;
        }
    }
}
