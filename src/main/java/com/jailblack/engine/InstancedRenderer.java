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
    private static final int FLOATS_PER_INSTANCE = 20;

    private final Mesh baseMesh;
    private final int instanceVbo;
    private final int capacity;
    private final float[] instanceData;
    private int count;

    public InstancedRenderer(Mesh baseMesh, int capacity) {
        this.baseMesh = baseMesh;
        this.capacity = capacity;
        this.instanceData = new float[capacity * FLOATS_PER_INSTANCE];

        baseMesh.bind();
        instanceVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) capacity * FLOATS_PER_INSTANCE * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = FLOATS_PER_INSTANCE * Float.BYTES;
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

    public void begin() { count = 0; }

    public void submit(Matrix4f model, Vector3f color, float alpha) {
        if (count >= capacity) return;
        int offset = count * FLOATS_PER_INSTANCE;
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
        if (count == 0 || instanceVbo == 0) return;
        baseMesh.bind();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        int size = count * FLOATS_PER_INSTANCE;
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
        if (models != null) {
            for (VoxelModel model : models) {
                if (model.voxels == null) continue;
                Matrix4f modelMatrix = model.transform.getModelMatrix();
                for (Voxel v : model.voxels) {
                    Matrix4f voxelMatrix = new Matrix4f(modelMatrix).translate(v.x, v.y, v.z);
                    Vector3f color = Voxel.hexToColor(v.color);
                    submit(voxelMatrix, color, v.getAlpha());
                }
            }
        }
        flush();
    }

    public void cleanup() {
        if (instanceVbo != 0) glDeleteBuffers(instanceVbo);
    }

    public int getCount() { return count; }
    public int getCapacity() { return capacity; }
}
