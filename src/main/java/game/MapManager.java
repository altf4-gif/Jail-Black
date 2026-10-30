package game;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapManager {
    public static void save(String filename, List<VoxelObject> objects) {
        File dir = new File("maps");
        if (!dir.exists()) dir.mkdirs();
        try (PrintWriter w = new PrintWriter(new FileWriter("maps/" + filename + ".json"))) {
            w.println("{");
            w.println("  \"objects\": [");
            for (int i = 0; i < objects.size(); i++) {
                VoxelObject obj = objects.get(i);
                w.println("    {");
                w.printf("      \"px\": %.3f, \"py\": %.3f, \"pz\": %.3f,%n", obj.transform.position.x, obj.transform.position.y, obj.transform.position.z);
                w.printf("      \"rx\": %.3f, \"ry\": %.3f, \"rz\": %.3f,%n", obj.transform.rotation.x, obj.transform.rotation.y, obj.transform.rotation.z);
                w.printf("      \"sx\": %.3f, \"sy\": %.3f, \"sz\": %.3f,%n", obj.transform.scale.x, obj.transform.scale.y, obj.transform.scale.z);
                w.println("      \"voxels\": [");
                for (int j = 0; j < obj.voxels.size(); j++) {
                    Voxel v = obj.voxels.get(j);
                    w.printf("        { \"x\": %d, \"y\": %d, \"z\": %d, \"color\": \"%s\" }%s%n",
                            v.x, v.y, v.z, v.color, (j < obj.voxels.size() - 1 ? "," : ""));
                }
                w.println("      ]");
                w.println("    }" + (i < objects.size() - 1 ? "," : ""));
            }
            w.println("  ]");
            w.println("}");
        } catch (Exception e) {
            System.err.println("Save error: " + e.getMessage());
        }
    }

    public static List<VoxelObject> load(String filename) {
        List<VoxelObject> objects = new ArrayList<>();
        File file = new File("maps/" + filename + ".json");
        if (!file.exists()) return objects;

        try {
            String json = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            json = json.replaceAll("\\s+", ""); // Remove all whitespace for easy parsing

            Pattern objPattern = Pattern.compile("\\{(?=\"px\")(.*?)\\]\\}");
            Matcher objMatcher = objPattern.matcher(json);

            while (objMatcher.find()) {
                String block = objMatcher.group(1) + "]";
                VoxelObject obj = new VoxelObject();
                
                obj.transform.position.x = getFloat(block, "px");
                obj.transform.position.y = getFloat(block, "py");
                obj.transform.position.z = getFloat(block, "pz");
                obj.transform.rotation.x = getFloat(block, "rx");
                obj.transform.rotation.y = getFloat(block, "ry");
                obj.transform.rotation.z = getFloat(block, "rz");
                obj.transform.scale.x = getFloat(block, "sx");
                obj.transform.scale.y = getFloat(block, "sy");
                obj.transform.scale.z = getFloat(block, "sz");

                Pattern vPattern = Pattern.compile("\\{\"x\":(-?\\d+),\"y\":(-?\\d+),\"z\":(-?\\d+),\"color\":\"([^\"]+)\"\\}");
                Matcher vMatcher = vPattern.matcher(block);
                while (vMatcher.find()) {
                    obj.voxels.add(new Voxel(
                        Integer.parseInt(vMatcher.group(1)),
                        Integer.parseInt(vMatcher.group(2)),
                        Integer.parseInt(vMatcher.group(3)),
                        vMatcher.group(4)
                    ));
                }
                objects.add(obj);
            }
        } catch (Exception e) {
            System.err.println("Load error: " + e.getMessage());
        }
        return objects;
    }

    private static float getFloat(String text, String key) {
        Pattern p = Pattern.compile("\"" + key + "\":(-?[\\d.]+)");
        Matcher m = p.matcher(text);
        if (m.find()) return Float.parseFloat(m.group(1));
        return 0;
    }

    public static String[] getSavedMaps() {
        File dir = new File("maps");
        if (!dir.exists()) return new String[0];
        String[] files = dir.list((d, name) -> name.endsWith(".json"));
        if (files == null) return new String[0];
        for (int i = 0; i < files.length; i++) files[i] = files[i].replace(".json", "");
        return files;
    }
}
