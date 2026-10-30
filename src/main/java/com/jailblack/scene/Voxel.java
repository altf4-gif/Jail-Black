package com.jailblack.scene;

import org.joml.Vector3f;
import java.util.Locale;

public class Voxel {
    public int x, y, z;
    public String color;
    public float alpha = 1.0f;

    public Voxel() {}

    public Voxel(int x, int y, int z, String color) {
        this.x = x; this.y = y; this.z = z;
        this.color = color;
        this.alpha = 1.0f;
    }

    public Voxel(int x, int y, int z, String color, float alpha) {
        this.x = x; this.y = y; this.z = z;
        this.color = color;
        setAlpha(alpha);
    }

    public Voxel copy() {
        Voxel voxel = new Voxel(x, y, z, color);
        voxel.alpha = alpha;
        return voxel;
    }

    public float getAlpha() { return alpha; }

    public void setAlpha(float value) {
        alpha = Math.max(0.0f, Math.min(1.0f, value));
    }

    public Vector3f getColorVector() { return hexToColor(color); }

    public static Vector3f hexToColor(String hex) {
        if (hex == null) return new Vector3f(1.0f, 1.0f, 1.0f);
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() < 6) return new Vector3f(1.0f, 1.0f, 1.0f);
        try {
            int r = Integer.parseInt(value.substring(0, 2), 16);
            int g = Integer.parseInt(value.substring(2, 4), 16);
            int b = Integer.parseInt(value.substring(4, 6), 16);
            return new Vector3f(r / 255.0f, g / 255.0f, b / 255.0f);
        } catch (NumberFormatException e) {
            return new Vector3f(1.0f, 1.0f, 1.0f);
        }
    }

    public static float hexToAlpha(String hex) {
        if (hex == null) return 1.0f;
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() < 8) return 1.0f;
        try {
            int a = Integer.parseInt(value.substring(6, 8), 16);
            return a / 255.0f;
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }

    public static String toHex(Vector3f color) {
        if (color == null) return "#FFFFFF";
        int r = clamp255(color.x), g = clamp255(color.y), b = clamp255(color.z);
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b);
    }

    public static String toHex(Vector3f color, float alpha) {
        if (color == null) return "#FFFFFFFF";
        int r = clamp255(color.x), g = clamp255(color.y), b = clamp255(color.z);
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255.0f);
        return String.format(Locale.US, "#%02X%02X%02X%02X", r, g, b, a);
    }

    public static String colorToHex(String color) {
        return (color == null || color.isEmpty()) ? "#FFFFFF" : color;
    }

    private static int clamp255(float value) {
        return Math.max(0, Math.min(255, (int) (value * 255.0f)));
    }
}
