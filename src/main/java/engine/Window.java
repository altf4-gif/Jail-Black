package engine;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glViewport;

public class Window {
    private final int initialWidth;
    private final int initialHeight;
    private final String title;
    private long handle = MemoryUtil.NULL;
    private int framebufferWidth;
    private int framebufferHeight;

    public Window(int width, int height, String title) {
        this.initialWidth = width;
        this.initialHeight = height;
        this.title = title;
    }

    public void create() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        handle = glfwCreateWindow(initialWidth, initialHeight, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) {
            throw new IllegalStateException("Failed to create GLFW window");
        }

        GLFWVidMode videoMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (videoMode != null) {
            glfwSetWindowPos(handle, (videoMode.width() - initialWidth) / 2, (videoMode.height() - initialHeight) / 2);
        }

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        glfwShowWindow(handle);
        GL.createCapabilities();

        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(handle, w, h);
        framebufferWidth = Math.max(w[0], 1);
        framebufferHeight = Math.max(h[0], 1);
        glViewport(0, 0, framebufferWidth, framebufferHeight);

        glfwSetFramebufferSizeCallback(handle, (window, width, height) -> {
            framebufferWidth = Math.max(width, 1);
            framebufferHeight = Math.max(height, 1);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
        });
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void swapAndPoll() {
        glfwSwapBuffers(handle);
        glfwPollEvents();
    }

    public void destroy() {
        if (handle != MemoryUtil.NULL) {
            glfwDestroyWindow(handle);
            handle = MemoryUtil.NULL;
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    public long getHandle() {
        return handle;
    }

    public float getAspect() {
        return (float) framebufferWidth / (float) framebufferHeight;
    }
}
