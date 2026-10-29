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
        Window window = new Window(1280, 720, "Java 3D Engine - Capsule Player & TopDown");
        window.create();

        GL33.glEnable(GL33.GL_DEPTH_TEST);
        GL33.glEnable(GL33.GL_CULL_FACE);
        GL33.glCullFace(GL33.GL_BACK);
        GL33.glClearColor(0.08f, 0.10f, 0.13f, 1.0f);

        ShaderProgram shader = ShaderProgram.fromResources("shaders/basic.vert", "shaders/basic.frag");
        shader.use();
        shader.setUniform("uTexture", 0);

        Camera camera = new Camera();
        List<GameObject> objects = new ArrayList<>();
        Set<Mesh> meshes = new HashSet<>();
        List<Texture> textures = new ArrayList<>();

        Mesh boxMesh = MeshFactory.box(1, 1, 1);
        Mesh floorMesh = MeshFactory.box(24, 0.4f, 24);
        Mesh wallMesh = MeshFactory.box(20, 8, 1);
        Mesh pixelMesh = MeshFactory.box(0.3f, 0.3f, 0.2f);
        
        Mesh playerBodyMesh = MeshFactory.capsule(0.4f, 1.6f, 16);
        Mesh sphereMesh = MeshFactory.sphere(0.5f, 16, 16);
        Mesh tetraMesh = MeshFactory.tetrahedron(1.0f);
        
        meshes.addAll(Arrays.asList(boxMesh, floorMesh, wallMesh, pixelMesh, playerBodyMesh, sphereMesh, tetraMesh));
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

        GameObject topChaos = new GameObject(boxMesh, new Vector3f(1, 1, 1), new Vector3f(0, 8.0f, 0), 3);
        objects.add(topChaos);

        for (int i = 1; i <= 10; i++) {
            Texture tex = createGradientTexture(i);
            textures.add(tex);
            GameObject cube = new GameObject(boxMesh, new Vector3f(1, 1, 1), new Vector3f(-9f + (i-1) * 2f, 1f, -11.4f), 0);
            cube.texture = tex;
            objects.add(cube);
        }

        Random rand = new Random();
        for (int i = 0; i < 10; i++) {
            Texture tex = createRandomGradientTexture(rand);
            textures.add(tex);
            GameObject cube = new GameObject(boxMesh, new Vector3f(1, 1, 1), new Vector3f(-9f + i * 2f, 3f, -11.4f), 2);
            cube.texture = tex;
            objects.add(cube);
        }

        for (int i = 0; i < 5; i++) {
            GameObject blurSphere = new GameObject(sphereMesh, new Vector3f(0.8f, 0.8f, 0.9f), new Vector3f(-8f + i * 4f, 5.5f, -11.4f), 4);
            blurSphere.scale = 2.5f;
            blurSphere.alpha = 0.3f;
            objects.add(blurSphere);
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

        Vector3f playerBase = new Vector3f(0, 0, 8f);
        float currentHeight = 1.6f;
        boolean isWalking = false, showSkybox = false, thirdPerson = false;
        float verticalVelocity = 0;
        boolean isGrounded = true;

        // ИСПОЛЬЗУЕМ МАССИВ ДЛЯ ЛЯМБДЫ (Java требует final или effectively final)
        final boolean[] topDown = new boolean[]{false};

        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        double[] lastX = new double[1], lastY = new double[1];
        boolean[] firstMouse = new boolean[]{true};
        GLFW.glfwSetCursorPosCallback(window.getHandle(), (win, xpos, ypos) -> {
            if (firstMouse[0]) { lastX[0] = xpos; lastY[0] = ypos; firstMouse[0] = false; }
            if (!topDown[0]) camera.rotate((float)(xpos - lastX[0]), (float)(lastY[0] - ypos));
            lastX[0] = xpos; lastY[0] = ypos;
        });

        boolean[] keys = new boolean[GLFW.GLFW_KEY_LAST + 1];
        double lastTime = GLFW.glfwGetTime();
        float time = 0, lastChaosChange = 0;
        int frames = 0; double fpsTimer = 0;

        while (!window.shouldClose()) {
            double currentTime = GLFW.glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime; time += dt;

            frames++; fpsTimer += dt;
            if (fpsTimer >= 1.0) { updateFpsObjects(fpsObjects, "FPS:" + frames, 3f, 6.5f, -11.4f); frames = 0; fpsTimer -= 1.0; }

            if (GLFW.glfwGetKey(window.getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS)
                GLFW.glfwSetWindowShouldClose(window.getHandle(), true);

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
                if (w) moveDir.z -= 1;
                if (s) moveDir.z += 1;
                if (a) moveDir.x -= 1;
                if (d) moveDir.x += 1;
            } else {
                Vector3f front = camera.getFront();
                Vector3f right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();
                front.y = 0; front.normalize();
                if (w) moveDir.add(front);
                if (s) moveDir.sub(front);
                if (d) moveDir.add(right);
                if (a) moveDir.sub(right);
            }

            if (moveDir.lengthSquared() > 0) {
                moveDir.normalize().mul(vel);
                playerBase.add(moveDir);
            }

            if (isWalking) {
                float targetH = shift ? 0.8f : 1.6f;
                currentHeight += (targetH - currentHeight) * 10f * dt;

                if (space && isGrounded) {
                    verticalVelocity = 6.0f;
                    isGrounded = false;
                }
                verticalVelocity -= 20.0f * dt;
                playerBase.y += verticalVelocity * dt;

                if (playerBase.y <= 0) {
                    playerBase.y = 0;
                    verticalVelocity = 0;
                    isGrounded = true;
                }
            } else {
                currentHeight = 1.6f;
                if (space) playerBase.y += vel;
                if (shift) playerBase.y -= vel;
            }

            for (GameObject obj : objects) {
                if (obj.type == 1) {
                    obj.scale = 1.0f + (float) Math.sin(time * 4.0f + obj.position.x) * 0.6f;
                } else if (obj.type == 2) {
                    obj.rotation.y += dt * 2.5f;
                    obj.rotation.x += dt * 1.0f;
                } else if (obj.type == 3) {
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
                } else if (obj.type == 4) {
                    obj.position.y += (float) Math.sin(time * 2.0f + obj.position.x) * 0.01f;
                }
            }

            Vector3f camPos = new Vector3f();
            Vector3f target = new Vector3f();
            Vector3f camUp = new Vector3f(0, 1, 0);
            Vector3f front = camera.getFront();

            if (topDown[0]) {
                camPos.set(playerBase.x, playerBase.y + 20f, playerBase.z);
                target.set(playerBase.x, playerBase.y, playerBase.z);
                camUp.set(0, 0, -1);
            } else if (thirdPerson && isWalking) {
                camPos.set(playerBase).add(0, currentHeight, 0).sub(new Vector3f(front).mul(4.5f));
                target.set(playerBase).add(0, currentHeight * 0.5f, 0);
            } else {
                float eyeHeight = isWalking ? currentHeight - 0.2f : 0f;
                camPos.set(playerBase.x, playerBase.y + eyeHeight, playerBase.z);
                target.set(camPos).add(front);
            }

            GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);
            shader.use();
            shader.setUniform("uViewProj", camera.getViewProjection(camPos, target, camUp, window.getAspect()));

            if (showSkybox) {
                GL33.glDepthMask(false);
                GL33.glCullFace(GL33.GL_FRONT);
                shader.setUniform("uUseTexture", 0);
                shader.setUniform("uAlpha", 1.0f);
                shader.setUniform("uModel", new Matrix4f().translate(camPos).scale(200));
                shader.setUniform("uColor", new Vector3f(0.4f, 0.6f, 0.9f));
                boxMesh.draw();
                GL33.glCullFace(GL33.GL_BACK);
                GL33.glDepthMask(true);
            }

            for (GameObject obj : objects) {
                if (obj.scale <= 0 || obj.alpha < 1.0f) continue;
                Matrix4f model = new Matrix4f().translate(obj.position).rotateXYZ(obj.rotation.x, obj.rotation.y, obj.rotation.z).scale(obj.scale * obj.scaleVec.x, obj.scale * obj.scaleVec.y, obj.scale * obj.scaleVec.z);
                shader.setUniform("uModel", model);
                shader.setUniform("uColor", obj.color);
                shader.setUniform("uAlpha", 1.0f);
                if (obj.texture != null) { shader.setUniform("uUseTexture", 1); obj.texture.bind(); }
                else shader.setUniform("uUseTexture", 0);
                obj.mesh.draw();
            }

            if (isWalking) {
                float bodyScaleY = currentHeight / 1.6f;
                Matrix4f bodyModel = new Matrix4f()
                    .translate(playerBase.x, playerBase.y, playerBase.z)
                    .scale(1.0f, bodyScaleY, 1.0f);
                
                shader.setUniform("uModel", bodyModel);
                shader.setUniform("uColor", new Vector3f(0.2f, 0.8f, 0.2f));
                shader.setUniform("uUseTexture", 0);
                shader.setUniform("uAlpha", 1.0f);
                playerBodyMesh.draw();
            }

            GL33.glEnable(GL33.GL_BLEND);
            GL33.glBlendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA);
            GL33.glDepthMask(false);

            for (GameObject obj : objects) {
                if (obj.scale <= 0 || obj.alpha >= 1.0f) continue;
                Matrix4f model = new Matrix4f().translate(obj.position).rotateXYZ(obj.rotation.x, obj.rotation.y, obj.rotation.z).scale(obj.scale * obj.scaleVec.x, obj.scale * obj.scaleVec.y, obj.scale * obj.scaleVec.z);
                shader.setUniform("uModel", model);
                shader.setUniform("uColor", obj.color);
                shader.setUniform("uAlpha", obj.alpha);
                shader.setUniform("uUseTexture", 0);
                obj.mesh.draw();
            }

            GL33.glDepthMask(true);
            GL33.glDisable(GL33.GL_BLEND);

            window.swapAndPoll();
        }

        for (Mesh m : meshes) m.cleanup();
        for (Texture t : textures) t.cleanup();
        shader.cleanup();
        window.destroy();
    }

    private static void addPillar(List<GameObject> objects, Set<Mesh> meshes, float x, float z, float height, Mesh shapeMesh, Vector3f shapeColor, int shapeType) {
        Mesh pillarMesh = MeshFactory.cylinder(0.4f, height, 32);
        meshes.add(pillarMesh);
        objects.add(new GameObject(pillarMesh, new Vector3f(0.58f, 0.61f, 0.66f), new Vector3f(x, 0, z), 0));
        objects.add(new GameObject(shapeMesh, new Vector3f(shapeColor), new Vector3f(x, height + 0.5f, z), shapeType));
    }

    private static Texture createGradientTexture(int numColors) {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int bandHeight = Math.max(1, size / numColors);
        for (int i = 0; i < numColors; i++) {
            Color c = Color.getHSBColor((float) i / numColors, 0.8f, 0.9f);
            g.setColor(c); g.fillRect(0, i * bandHeight, size, bandHeight);
        }
        g.dispose();
        return Texture.createFromBufferedImage(img);
    }

    private static Texture createRandomGradientTexture(Random rand) {
        int size = 32;
        int numColors = rand.nextInt(10) + 1;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int bandHeight = Math.max(1, size / numColors);
        for (int i = 0; i < numColors; i++) {
            Color c = Color.getHSBColor(rand.nextFloat(), 0.8f, 0.9f);
            g.setColor(c); g.fillRect(0, i * bandHeight, size, bandHeight);
        }
        g.dispose();
        return Texture.createFromBufferedImage(img);
    }

    private static void updateFpsObjects(List<GameObject> pixels, String text, float startX, float startY, float startZ) {
        int pixelIndex = 0;
        float charWidth = 1.5f, pixelSize = 0.4f;
        for (int c = 0; c < text.length(); c++) {
            boolean[][] bitmap = getBitmap(text.charAt(c));
            if (bitmap == null) continue;
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
            case 'F': return new boolean[][]{{true,true,true},{true,false,false},{true,true,true},{true,false,false},{true,false,false}};
            case 'P': return new boolean[][]{{true,true,true},{true,false,true},{true,true,true},{true,false,false},{true,false,false}};
            case 'S': return new boolean[][]{{true,true,true},{true,false,false},{true,true,true},{false,false,true},{true,true,true}};
            case ':': return new boolean[][]{{false},{true},{false},{true},{false}};
            default: return null;
        }
    }
}
