# Jail-Black

Java 3D voxel editor/engine built on LWJGL 3 + ImGui.

## Build & Run

```

run.bat

```

Перед первым запуском удалите `imgui.ini`, если он есть в папке проекта
(старые сохранённые координаты «вылетевших» окон могут снова крашить драйвер AMD).

## Исправления в этой версии

### 1. `InstancedRenderer.java` — ошибка компиляции

В LWJGL 3 у `glBufferSubData` нет перегрузки с параметром `size`:
```java
// Было (не компилируется):
glBufferSubData(GL_ARRAY_BUFFER, 0, (long) count * 19 * Float.BYTES, instanceData);

// Стало:
FloatBuffer buffer = FloatBuffer.wrap(instanceData, 0, count * 19);
glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
```

### 2. `Main.java` — краш `EXCEPTION_ACCESS_VIOLATION` в `atio6axx.dll` (AMD)

`ImGuiConfigFlags.ViewportsEnable` создаёт отдельные нативные окна Windows
и вторичные OpenGL-контексты. Драйверы AMD + LWJGL часто падают через 2–5 секунд.
Исправление:

- `ViewportsEnable` отключён;
- добавлен `DockSpace` на весь экран (`DockSpace` + `PassthruCentralNode`),
чтобы панели стыковались внутри главного окна;
- `io.setIniFilename(null)` — `imgui.ini` больше не создаётся и не ломает запуск;
- удалён блок `ImGui.updatePlatformWindows() / renderPlatformWindowsDefault()`.

## Примечание

В архиве нет папки `src/main/resources/shaders/` (шейдеры `.vert`/`.frag`),
так как они не были найдены в репозитории. Если проект требует шейдеры,
положите их в `src/main/resources/shaders/`.

</BDS:create_file>
<BDS:create_file fileName="src/main/java/com/jailblack/Main.java">

```java
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
        glEnable(GL_MULTISAMPLE);

        // Shaders
        ShaderProgram basicShader = ShaderProgram.fromResources("shaders/basic.vert", "shaders/basic.frag");
        ShaderProgram outlineShader = ShaderProgram.fromResources("shaders/outline.vert", "shaders/outline.frag");
        ShaderProgram gridShader = ShaderProgram.fromResources("shaders/grid.vert", "shaders/grid.frag");
        ShaderProgram ghostShader = ShaderProgram.fromResources("shaders/ghost.vert", "shaders/ghost.frag");
        ShaderProgram postShader = ShaderProgram.fromResources("shaders/post.vert", "shaders/post.frag");

        Camera camera = new Camera();

        // Meshes
        Mesh boxMesh = MeshFactory.box(1, 1, 1);
        Mesh gridMesh = MeshFactory.grid(100.0f, 100);
        Mesh cubeOutlineMesh = MeshFactory.cubeOutline();

        // Instanced renderer for voxels
        InstancedRenderer instancedRenderer = new InstancedRenderer(boxMesh, 100_000);

        // Framebuffer for scene rendering
        Framebuffer sceneFramebuffer = new Framebuffer(window.getWidth(), window.getHeight());

        // ImGui setup
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null); // Отключаем сохранение layout, чтобы старые "вылетевшие" окна не крашили драйвер
        io.getFonts().addFontDefault();

        // ВКЛЮЧАЕМ ТОЛЬКО ДОКИНГ (стыковку окон)
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        // ViewportsEnable УДАЛЕН! Именно он вызывает краш AMD драйвера (atio6axx.dll)
        // io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);

        ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
        ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
        imGuiGlfw.init(window.getHandle(), true);
        imGuiGl3.init("#version 330 core");

        // Editor state
        EditorState editor = new EditorState(window, camera,
                basicShader, outlineShader, gridShader, ghostShader,
                boxMesh, gridMesh, cubeOutlineMesh,
                instancedRenderer, sceneFramebuffer);

        GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);

        // Scroll callback for zoom
        GLFW.glfwSetScrollCallback(window.getHandle(), (win, xoffset, yoffset) -> {
            if (!ImGui.getIO().getWantCaptureMouse()) {
                camera.zoom((float) yoffset);
            }
        });

        // Resize callback — обновляем и локальную переменную, и ссылку внутри EditorState
        GLFW.glfwSetFramebufferSizeCallback(window.getHandle(), (win, w, h) -> {
            if (w > 0 && h > 0) {
                sceneFramebuffer.cleanup();
                editor.setSceneFramebuffer(new Framebuffer(w, h));
            }
        });

        double lastTime = GLFW.glfwGetTime();

        while (!window.shouldClose()) {
            double currentTime = GLFW.glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;

            // Clear default framebuffer
            glClearColor(0.12f, 0.12f, 0.14f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // ImGui frame
            imGuiGlfw.newFrame();
            ImGui.newFrame();

            // Невидимое окно на весь экран для DockSpace (чтобы окна стыковались внутри редактора)
            ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
            ImGui.setNextWindowSize(io.getDisplaySizeX(), io.getDisplaySizeY(), ImGuiCond.Always);
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

            // Update editor
            editor.updateAndRender(dt);

            // Render ImGui
            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            // Блок с рендером Viewports полностью удален

            window.swapAndPoll();
        }

        // Cleanup
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

