package com.jailblack.scene;

import org.joml.Vector3f;

import java.util.Locale;

public class Voxel {
    public int x, y, z;
    public String color;

    public Voxel() {}

    public Voxel(int x, int y, int z, String color) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
    }

    public Voxel copy() {
        return new Voxel(x, y, z, color);
    }

    public static Vector3f hexToColor(String hex) {
        if (hex == null) return new Vector3f(1.0f, 1.0f, 1.0f);

        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() < 6) return new Vector3f(1.0f, 1.0f, 1.0f);

        try {
            int r = Integer.parseInt(value.substring(0, 2), 16);
            int g = Integer.parseInt(value.substring(2, 4), 16);
            int b = Integer.parseInt(value.substring(4, 6), 16);
            return new Vector3f(r / 255.0f, g / 255.0f, b / 255.0f);
        } catch (Exception e) {
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
        } catch (Exception e) {
            return 1.0f;
        }
    }

    public static String toHex(Vector3f color) {
        if (color == null) return "#FFFFFF";

        int r = clamp255(color.x);
        int g = clamp255(color.y);
        int b = clamp255(color.z);
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b);
    }

    public static String colorToHex(String color) {
        if (color == null || color.isEmpty()) return "#FFFFFF";
        return color;
    }

    private static int clamp255(float value) {
        int result = (int) (value * 255.0f);
        if (result < 0) return 0;
        if (result > 255) return 255;
        return result;
    }
}
// === END OF FILE ===
