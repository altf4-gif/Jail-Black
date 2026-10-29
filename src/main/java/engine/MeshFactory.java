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

    public static Mesh cylinder(float radius, float height, int segments) {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float nx = (float) Math.cos(angle), nz = (float) Math.sin(angle);
            float px = nx * radius, pz = nz * radius;
            float u = (float) i / segments;
            v.add(px); v.add(0f); v.add(pz); v.add(nx); v.add(0f); v.add(nz); v.add(u); v.add(0f);
            v.add(px); v.add(height); v.add(pz); v.add(nx); v.add(0f); v.add(nz); v.add(u); v.add(1f);
        }
        for (int i = 0; i < segments; i++) {
            int b0 = i * 2, t0 = i * 2 + 1, b1 = (i + 1) * 2, t1 = (i + 1) * 2 + 1;
            idx.add(b0); idx.add(t1); idx.add(b1);
            idx.add(b0); idx.add(t0); idx.add(t1);
        }
        int topCenter = v.size() / 8;
        v.add(0f); v.add(height); v.add(0f); v.add(0f); v.add(1f); v.add(0f); v.add(0.5f); v.add(0.5f);
        int topRing = v.size() / 8;
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            v.add((float) Math.cos(angle) * radius); v.add(height); v.add((float) Math.sin(angle) * radius); v.add(0f); v.add(1f); v.add(0f); v.add((float)Math.cos(angle)*0.5f+0.5f); v.add((float)Math.sin(angle)*0.5f+0.5f);
        }
        for (int i = 0; i < segments; i++) { idx.add(topCenter); idx.add(topRing + i + 1); idx.add(topRing + i); }
        int bottomCenter = v.size() / 8;
        v.add(0f); v.add(0f); v.add(0f); v.add(0f); v.add(-1f); v.add(0f); v.add(0.5f); v.add(0.5f);
        int bottomRing = v.size() / 8;
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            v.add((float) Math.cos(angle) * radius); v.add(0f); v.add((float) Math.sin(angle) * radius); v.add(0f); v.add(-1f); v.add(0f); v.add((float)Math.cos(angle)*0.5f+0.5f); v.add((float)Math.sin(angle)*0.5f+0.5f);
        }
        for (int i = 0; i < segments; i++) { idx.add(bottomCenter); idx.add(bottomRing + i); idx.add(bottomRing + i + 1); }
        return build(v, idx);
    }

    public static Mesh sphere(float radius, int stacks, int slices) {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i <= stacks; i++) {
            double phi = Math.PI * i / stacks;
            float ny = (float) Math.cos(phi);
            float ringRadius = (float) Math.sin(phi);
            for (int j = 0; j <= slices; j++) {
                double theta = 2.0 * Math.PI * j / slices;
                float nx = ringRadius * (float) Math.cos(theta);
                float nz = ringRadius * (float) Math.sin(theta);
                v.add(nx * radius); v.add(ny * radius); v.add(nz * radius); v.add(nx); v.add(ny); v.add(nz); v.add((float)j / slices); v.add((float)i / stacks);
            }
        }
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int a = i * (slices + 1) + j; int b = a + slices + 1;
                idx.add(a); idx.add(a + 1); idx.add(b); idx.add(a + 1); idx.add(b + 1); idx.add(b);
            }
        }
        return build(v, idx);
    }

    public static Mesh tetrahedron(float size) {
        float k = size / 2f;
        float[][] p = { { k, k, k}, { k, -k, -k}, {-k, k, -k}, {-k, -k, k} };
        int[][] faces = { {0, 1, 2}, {0, 2, 3}, {0, 3, 1}, {1, 3, 2} };
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float[][] uvs = {{0.5f, 1f}, {0f, 0f}, {1f, 0f}};
        for (int[] face : faces) {
            float cx = (p[face[0]][0] + p[face[1]][0] + p[face[2]][0]) / 3f;
            float cy = (p[face[0]][1] + p[face[1]][1] + p[face[2]][1]) / 3f;
            float cz = (p[face[0]][2] + p[face[1]][2] + p[face[2]][2]) / 3f;
            float length = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            float nx = cx / length, ny = cy / length, nz = cz / length;
            int base = v.size() / 8;
            for (int i = 0; i < 3; i++) {
                int vertex = face[i];
                v.add(p[vertex][0]); v.add(p[vertex][1]); v.add(p[vertex][2]); v.add(nx); v.add(ny); v.add(nz); v.add(uvs[i][0]); v.add(uvs[i][1]);
            }
            idx.add(base); idx.add(base + 1); idx.add(base + 2);
        }
        return build(v, idx);
    }

    public static Mesh capsule(float radius, float height, int segments) {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float r = radius, h = height;
        for (int i = 0; i <= segments/2; i++) {
            double phi = Math.PI * i / (segments);
            float ny = (float) Math.cos(phi), ringRadius = (float) Math.sin(phi);
            for (int j = 0; j <= segments; j++) {
                double theta = 2.0 * Math.PI * j / segments;
                float nx = ringRadius * (float) Math.cos(theta), nz = ringRadius * (float) Math.sin(theta);
                v.add(nx * r); v.add(h - r + ny * r); v.add(nz * r); v.add(nx); v.add(ny); v.add(nz); v.add((float)j / segments); v.add(0.5f + (float)i / segments * 0.5f);
            }
        }
        for (int i = 0; i < segments/2; i++) {
            for (int j = 0; j < segments; j++) {
                int a = i * (segments + 1) + j, b = a + segments + 1;
                idx.add(a); idx.add(a + 1); idx.add(b); idx.add(a + 1); idx.add(b + 1); idx.add(b);
            }
        }
        int botBase = v.size() / 8;
        for (int i = 0; i <= segments/2; i++) {
            double phi = Math.PI / 2 + Math.PI * i / (segments);
            float ny = (float) Math.cos(phi), ringRadius = (float) Math.sin(phi);
            for (int j = 0; j <= segments; j++) {
                double theta = 2.0 * Math.PI * j / segments;
                float nx = ringRadius * (float) Math.cos(theta), nz = ringRadius * (float) Math.sin(theta);
                v.add(nx * r); v.add(r + ny * r); v.add(nz * r); v.add(nx); v.add(ny); v.add(nz); v.add((float)j / segments); v.add((float)i / segments * 0.5f);
            }
        }
        for (int i = 0; i < segments/2; i++) {
            for (int j = 0; j < segments; j++) {
                int a = botBase + i * (segments + 1) + j, b = a + segments + 1;
                idx.add(a); idx.add(a + 1); idx.add(b); idx.add(a + 1); idx.add(b + 1); idx.add(b);
            }
        }
        int cylBase = v.size() / 8;
        for (int i = 0; i <= 1; i++) {
            float y = (i == 0) ? r : (h - r);
            for (int j = 0; j <= segments; j++) {
                double theta = 2.0 * Math.PI * j / segments;
                float nx = (float) Math.cos(theta), nz = (float) Math.sin(theta);
                v.add(nx * r); v.add(y); v.add(nz * r); v.add(nx); v.add(0f); v.add(nz); v.add((float)j / segments); v.add((float)i);
            }
        }
        for (int j = 0; j < segments; j++) {
            int a = cylBase + j, b = a + 1, c = a + segments + 1, d = c + 1;
            idx.add(a); idx.add(c); idx.add(b); idx.add(b); idx.add(c); idx.add(d);
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
