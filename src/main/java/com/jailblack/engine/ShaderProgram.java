package com.jailblack.engine;

import org.joml.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL33.*;

public class ShaderProgram {
    private final int programId;

    public ShaderProgram(String vertSrc, String fragSrc) {
        int vertId = compileShader(vertSrc, GL_VERTEX_SHADER);
        int fragId = compileShader(fragSrc, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertId);
        glAttachShader(programId, fragId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Error linking shader: " + glGetProgramInfoLog(programId));
        }

        glDetachShader(programId, vertId);
        glDetachShader(programId, fragId);
        glDeleteShader(vertId);
        glDeleteShader(fragId);
    }

    private int compileShader(String src, int type) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);

        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);
            throw new RuntimeException("Shader compile error: " + log);
        }
        return id;
    }

    public static ShaderProgram fromResources(String vertPath, String fragPath) {
        try {
            String vertSrc = loadResource(vertPath);
            String fragSrc = loadResource(fragPath);
            return new ShaderProgram(vertSrc, fragSrc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load shaders: " + e.getMessage(), e);
        }
    }

    private static String loadResource(String path) throws Exception {
        try (InputStream is = ShaderProgram.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void use() { glUseProgram(programId); }

    public int getUniformLocation(String name) {
        return glGetUniformLocation(programId, name);
    }

    public void setUniform(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }

    public void setUniform(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    public void setUniform(String name, boolean value) {
        glUniform1i(getUniformLocation(name), value ? 1 : 0);
    }

    public void setUniform(String name, Vector3f value) {
        glUniform3f(getUniformLocation(name), value.x, value.y, value.z);
    }

    public void setUniform(String name, Vector4f value) {
        glUniform4f(getUniformLocation(name), value.x, value.y, value.z, value.w);
    }

    public void setUniform(String name, Matrix4f value) {
        float[] arr = new float[16];
        value.get(arr);
        glUniformMatrix4fv(getUniformLocation(name), false, arr);
    }

    public void cleanup() {
        glUseProgram(0);
        glDeleteProgram(programId);
    }

    public int getProgramId() { return programId; }
}
