package engine;

import java.util.ArrayList;
import java.util.List;

public final class MeshFactory {
    private MeshFactory() {}

    public static Mesh box(float width, float height, float depth) {
        float x = width / 2f, y = height / 2f, z = depth / 2f;
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float[][] uvs = {{0,0},{1,0},{1,1},{0,1}};
        quad(v, idx, 1, 0, 0, new float[]{ x, -y,  z}, new float[]{ x, -y, -z}, new float[]{ x,  y, -z}, new float[]{ x,  y,  z}, uvs);
        quad(v, idx, -1, 0, 0, new float[]{-x, -y, -z}, new float[]{-x, -y,  z}, new float[]{-x,  y,  z}, new float[]{-x,  y, -z}, uvs);
        quad(v, idx, 0, 1, 0, new float[]{-x,  y,  z}, new float[]{ x,  y,  z}, new float[]{ x,  y, -z}, new float[]{-x,  y, -z}, uvs);
        quad(v, idx, 0, -1, 0, new float[]{-x, -y, -z}, new float[]{ x, -y, -z}, new float[]{ x, -y,  z}, new float[]{-x, -y,  z}, uvs);
        quad(v, idx, 0, 0, 1, new float[]{-x, -y,  z}, new float[]{ x, -y,  z}, new float[]{ x,  y,  z}, new float[]{-x,  y,  z}, uvs);
        quad(v, idx, 0, 0, -1, new float[]{ x, -y, -z}, new float[]{-x, -y, -z}, new float[]{-x,  y, -z}, new float[]{ x,  y, -z}, uvs);
        return build(v, idx);
    }

    public static Mesh grid(float size, int divisions) {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float step = size / divisions;
        float half = size / 2f;

        for (int i = 0; i <= divisions; i++) {
            float pos = -half + i * step;
            v.add(-half); v.add(0f); v.add(pos); v.add(0f); v.add(1f); v.add(0f); v.add(0f); v.add(0f);
            v.add(half);  v.add(0f); v.add(pos); v.add(0f); v.add(1f); v.add(0f); v.add(1f); v.add(0f);
            v.add(pos); v.add(0f); v.add(-half); v.add(0f); v.add(1f); v.add(0f); v.add(0f); v.add(0f);
            v.add(pos); v.add(0f); v.add(half);  v.add(0f); v.add(1f); v.add(0f); v.add(1f); v.add(0f);
        }
        for (int i = 0; i < (divisions + 1) * 4; i += 2) {
            idx.add(i); idx.add(i + 1);
        }
        return build(v, idx);
    }

    public static Mesh quad2D() {
        float[] vertices = {
            -1, -1, 0, 0, 0, 1, 0, 0,
             1, -1, 0, 0, 0, 1, 1, 0,
             1,  1, 0, 0, 0, 1, 1, 1,
            -1,  1, 0, 0, 0, 1, 0, 1
        };
        int[] indices = { 0, 1, 2, 0, 2, 3 };
        return new Mesh(vertices, indices);
    }

    private static void quad(List<Float> vertices, List<Integer> indices, float nx, float ny, float nz, float[] p0, float[] p1, float[] p2, float[] p3, float[][] uvs) {
        int base = vertices.size() / 8;
        float[][] pts = {p0, p1, p2, p3};
        for (int i = 0; i < 4; i++) {
            float[] p = pts[i];
            vertices.add(p[0]); vertices.add(p[1]); vertices.add(p[2]); vertices.add(nx); vertices.add(ny); vertices.add(nz); vertices.add(uvs[i][0]); vertices.add(uvs[i][1]);
        }
        indices.add(base); indices.add(base + 1); indices.add(base + 2); indices.add(base); indices.add(base + 2); indices.add(base + 3);
    }

    private static Mesh build(List<Float> vertices, List<Integer> indices) {
        float[] vArr = new float[vertices.size()];
        for (int i = 0; i < vArr.length; i++) vArr[i] = vertices.get(i);
        int[] iArr = new int[indices.size()];
        for (int i = 0; i < iArr.length; i++) iArr[i] = indices.get(i);
        return new Mesh(vArr, iArr);
    }
}
