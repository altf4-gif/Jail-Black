package com.jailblack.engine;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;

public class Mesh {
    private final int vaoId, vboId, eboId;
    private final int indexCount, vertexCount, drawMode;

    public Mesh(float[] vertices, int[] indices) { this(vertices, indices, GL_TRIANGLES); }

    public Mesh(float[] vertices, int[] indices, int drawMode) {
        this.vertexCount = vertices.length / 8;
        this.indexCount = indices.length;
        this.drawMode = drawMode;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(vertices.length);
        vertexBuffer.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(vertexBuffer);

        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        IntBuffer indexBuffer = MemoryUtil.memAllocInt(indices.length);
        indexBuffer.put(indices).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(indexBuffer);

        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    public void bind() { glBindVertexArray(vaoId); }
    public void unbind() { glBindVertexArray(0); }

    public void draw() {
        glBindVertexArray(vaoId);
        glDrawElements(drawMode, indexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }

    public int getVaoId() { return vaoId; }
    public int getVboId() { return vboId; }
    public int getEboId() { return eboId; }
    public int getIndexCount() { return indexCount; }
    public int getVertexCount() { return vertexCount; }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        glDeleteVertexArrays(vaoId);
    }
}
