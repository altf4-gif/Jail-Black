package com.jailblack.engine;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINES;

public final class MeshFactory {
    private MeshFactory() {}

    public static Mesh box(float width, float height, float depth) {
        float x = width / 2f, y = height / 2f, z = depth / 2f;
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        quad(v, idx, 1, 0, 0, new float[]{x,-y,z}, new float[]{x,-y,-z}, new float[]{x,y,-z}, new float[]{x,y,z}, uvs);
        quad(v, idx, -1, 0, 0, new float[]{-x,-y,-z}, new float[]{-x,-y,z}, new float[]{-x,y,z}, new float[]{-x,y,-z}, uvs);
        quad(v, idx, 0, 1, 0, new float[]{-x,y,z}, new float[]{x,y,z}, new float[]{x,y,-z}, new float[]{-x,y,-z}, uvs);
        quad(v, idx, 0, -1, 0, new float[]{-x,-y,-z}, new float[]{x,-y,-z}, new float[]{x,-y,z}, new float[]{-x,-y,z}, uvs);
        quad(v, idx, 0, 0, 1, new float[]{-x,-y,z}, new float[]{x,-y,z}, new float[]{x,y,z}, new float[]{-x,y,z}, uvs);
        quad(v, idx, 0, 0, -1, new float[]{x,-y,-z}, new float[]{-x,-y,-z}, new float[]{-x,y,-z}, new float[]{x,y,-z}, uvs);
        return build(v, idx);
    }

    public static Mesh grid(float size, int divisions) {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float step = size / divisions;
        float half = size / 2f;
        for (int i = 0; i <= divisions; i++) {
            float pos = -half + i * step;
            boolean isCenter = (i == divisions / 2);
            float ny = isCenter ? 0f : 1f;
            v.add(-half); v.add(0f); v.add(pos); v.add(0f); v.add(ny); v.add(0f); v.add(0f); v.add(0f);
            v.add(half);  v.add(0f); v.add(pos); v.add(0f); v.add(ny); v.add(0f); v.add(1f); v.add(0f);
            v.add(pos);   v.add(0f); v.add(-half); v.add(0f); v.add(ny); v.add(0f); v.add(0f); v.add(0f);
            v.add(pos);   v.add(0f); v.add(half);  v.add(0f); v.add(ny); v.add(0f); v.add(1f); v.add(0f);
        }
        for (int i = 0; i < (divisions + 1) * 4; i += 2) { idx.add(i); idx.add(i + 1); }
        return buildLines(v, idx);
    }

    public static Mesh cubeOutline() {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float s = 0.5f;
        float[][] corners = {
            {-s,-s,-s},{s,-s,-s},{s,s,-s},{-s,s,-s},
            {-s,-s,s},{s,-s,s},{s,s,s},{-s,s,s}
        };
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},
            {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
        };
        for (float[] c : corners) {
            v.add(c[0]); v.add(c[1]); v.add(c[2]);
            v.add(0f); v.add(0f); v.add(0f);
            v.add(0f); v.add(0f);
        }
        for (int[] e : edges) { idx.add(e[0]); idx.add(e[1]); }
        return buildLines(v, idx);
    }

    public static Mesh axisLines() {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        float len = 100.0f;
        // X — red
        v.add(0f);v.add(0f);v.add(0f);v.add(1f);v.add(0f);v.add(0f);v.add(0f);v.add(0f);
        v.add(len);v.add(0f);v.add(0f);v.add(1f);v.add(0f);v.add(0f);v.add(1f);v.add(0f);
        idx.add(0); idx.add(1);
        // Y — green
        v.add(0f);v.add(0f);v.add(0f);v.add(0f);v.add(1f);v.add(0f);v.add(0f);v.add(0f);
        v.add(0f);v.add(len);v.add(0f);v.add(0f);v.add(1f);v.add(0f);v.add(1f);v.add(0f);
        idx.add(2); idx.add(3);
        // Z — blue
        v.add(0f);v.add(0f);v.add(0f);v.add(0f);v.add(0f);v.add(1f);v.add(0f);v.add(0f);
        v.add(0f);v.add(0f);v.add(len);v.add(0f);v.add(0f);v.add(1f);v.add(1f);v.add(0f);
        idx.add(4); idx.add(5);
        return buildLines(v, idx);
    }

    private static void quad(List<Float> vertices, List<Integer> indices,
                             float nx, float ny, float nz,
                             float[] p0, float[] p1, float[] p2, float[] p3, float[][] uvs) {
        int base = vertices.size() / 8;
        float[][] pts = {p0, p1, p2, p3};
        for (int i = 0; i < 4; i++) {
            float[] p = pts[i];
            vertices.add(p[0]); vertices.add(p[1]); vertices.add(p[2]);
            vertices.add(nx); vertices.add(ny); vertices.add(nz);
            vertices.add(uvs[i][0]); vertices.add(uvs[i][1]);
        }
        indices.add(base); indices.add(base+1); indices.add(base+2);
        indices.add(base); indices.add(base+2); indices.add(base+3);
    }

    private static Mesh build(List<Float> vertices, List<Integer> indices) {
        return new Mesh(toFloatArray(vertices), toIntArray(indices));
    }

    private static Mesh buildLines(List<Float> vertices, List<Integer> indices) {
        return new Mesh(toFloatArray(vertices), toIntArray(indices), GL_LINES);
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }
}
