package com.jailblack.engine;

import com.jailblack.scene.Voxel;
import com.jailblack.scene.VoxelModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

public class InstancedRenderer {
    private final Mesh baseMesh;
    private final int instanceVbo;
    private final int capacity;
    private final float[] instanceData;
    private int count;

    public InstancedRenderer(Mesh baseMesh, int capacity) {
        this.baseMesh = baseMesh;
        this.capacity = capacity;
        this.instanceData = new float[capacity * 20];

        baseMesh.bind();

        instanceVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) capacity * 20 * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = 20 * Float.BYTES;

        for (int i = 0; i < 4; i++) {
            int location = 3 + i;
            glEnableVertexAttribArray(location);
            glVertexAttribPointer(location, 4, GL_FLOAT, false, stride, (long) i * 4 * Float.BYTES);
            glVertexAttribDivisor(location, 1);
        }

        glEnableVertexAttribArray(7);
        glVertexAttribPointer(7, 4, GL_FLOAT, false, stride, 16L * Float.BYTES);
        glVertexAttribDivisor(7, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        baseMesh.unbind();
    }

    public void begin() {
        count = 0;
    }

    public void submit(Matrix4f model, Vector3f color, float alpha) {
        if (count >= capacity) return;
        int offset = count * 20;

        float[] mat = new float[16];
        model.get(mat);
        System.arraycopy(mat, 0, instanceData, offset, 16);

        instanceData[offset + 16] = color.x;
        instanceData[offset + 17] = color.y;
        instanceData[offset + 18] = color.z;
        instanceData[offset + 19] = alpha;

        count++;
    }

    public void flush() {
        if (count == 0) return;
        if (instanceVbo == 0) return; // VBO не создан

        baseMesh.bind();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);

        // ИСПРАВЛЕНИЕ: direct buffer через MemoryUtil (не FloatBuffer.wrap —
        // тот создаёт heap buffer, и AMD-драйвер падает при чтении через JNI)
        int size = count * 20;
        FloatBuffer buffer = MemoryUtil.memAllocFloat(size);
        buffer.put(instanceData, 0, size);
        buffer.flip();

        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);

        MemoryUtil.memFree(buffer);

        glDrawElementsInstanced(GL_TRIANGLES, baseMesh.getIndexCount(), GL_UNSIGNED_INT, 0L, count);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        baseMesh.unbind();
    }

    public void render(List<VoxelModel> models) {
        begin();
        for (VoxelModel model : models) {
            Matrix4f modelMatrix = model.transform.getModelMatrix();
            for (Voxel v : model.voxels) {
                Matrix4f voxelMatrix = new Matrix4f(modelMatrix).translate(v.x, v.y, v.z);
                Vector3f color = hexToColor(v.color);
                float alpha = v.getAlpha();
                submit(voxelMatrix, color, alpha);
            }
        }
        flush();
    }

    public static Vector3f hexToColor(String hex) {
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

    public void cleanup() {
        if (instanceVbo != 0) {
            glDeleteBuffers(instanceVbo);
        }
    }

    public int getCount() { return count; }
    public int getCapacity() { return capacity; }
}
// === END OF FILE ===
