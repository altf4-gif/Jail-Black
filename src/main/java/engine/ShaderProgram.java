package engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

public class ShaderProgram {
    private final int programId;
    private final FloatBuffer matrixBuffer = MemoryUtil.memAllocFloat(16);
    private final Map<String, Integer> uniformCache = new HashMap<>();

    private ShaderProgram(int programId) {
        this.programId = programId;
    }

    public static ShaderProgram fromResources(String vertexPath, String fragmentPath) {
        String vertexSource = loadResource(vertexPath);
        String fragmentSource = loadResource(fragmentPath);
        int vertexShader = compile(GL_VERTEX_SHADER, vertexSource, vertexPath);
        int fragmentShader = compile(GL_FRAGMENT_SHADER, fragmentSource, fragmentPath);
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Program link error:\n" + log);
        }

        glDetachShader(program, vertexShader);
        glDetachShader(program, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return new ShaderProgram(program);
    }

    private static int compile(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compile error " + name + ":\n" + log);
        }
        return shader;
    }

    private static String loadResource(String path) {
        try (InputStream in = ShaderProgram.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }

    public void use() {
        glUseProgram(programId);
    }

    private int location(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setUniform(String name, Matrix4f value) {
        int location = location(name);
        if (location < 0) return;
        value.get(matrixBuffer);
        glUniformMatrix4fv(location, false, matrixBuffer);
    }

    public void setUniform(String name, Vector3f value) {
        int location = location(name);
        if (location < 0) return;
        glUniform3f(location, value.x, value.y, value.z);
    }

    public void cleanup() {
        glUseProgram(0);
        glDeleteProgram(programId);
        MemoryUtil.memFree(matrixBuffer);
    }
}
