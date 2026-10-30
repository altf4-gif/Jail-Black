package com.jailblack.scene;

public class Voxel {
    public int x, y, z;
    public String color; // Формат: #RRGGBB или #RRGGBBAA

    public Voxel() {}

    public Voxel(int x, int y, int z, String color) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
    }

    public float getAlpha() {
        if (color == null || color.length() < 9) return 1.0f;
        try {
            int a = Integer.parseInt(color.substring(7, 9), 16);
            return a / 255f;
        } catch (Exception e) {
            return 1.0f;
        }
    }
}
// === END OF FILE ===
