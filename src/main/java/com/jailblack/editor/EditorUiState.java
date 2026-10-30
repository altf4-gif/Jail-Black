package com.jailblack.editor;

import com.jailblack.engine.Window;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class EditorUiState {
    public enum WorkspaceKind { NONE, MODEL, MAP }
    public enum VoxelTool { ADD, REMOVE, PAINT, EYEDROPPER, BRUSH }
    public enum BrushShape { CUBE, SPHERE }
    public enum UiAction { NONE, CREATE_MODEL_WORKSPACE, OPEN_MAP_WORKSPACE }

    private static class PaletteGroup {
        String name;
        final List<Vector3f> colors = new ArrayList<>();
        PaletteGroup(String name) { this.name = name; }
    }

    private boolean startupVisible = true;
    private boolean createModelPopupRequested = false;
    private boolean createModelPopupOpen = false;

    private WorkspaceKind workspaceKind = WorkspaceKind.NONE;

    private final ImString modelName = new ImString("Untitled Model", 128);
    private final ImInt modelWidth = new ImInt(16);
    private final ImInt modelHeight = new ImInt(16);
    private final ImInt modelDepth = new ImInt(16);

    private String activeModelName = "Untitled Model";
    private int activeModelWidth = 16;
    private int activeModelHeight = 16;
    private int activeModelDepth = 16;

    private VoxelTool voxelTool = VoxelTool.ADD;
    private BrushShape brushShape = BrushShape.CUBE;
    private final int[] paintBrushSize = {1};

    private boolean showGhostVoxel = true;
    private boolean showGhostInAllTools = false;

    private boolean mirrorX = false;
    private boolean mirrorY = false;
    private boolean mirrorZ = false;

    private boolean showGrid = true;
    private boolean showBounds = true;

    private boolean showExplorer = true;
    private boolean showProperties = true;
    private boolean showViewport = true;
    private boolean showStatusWindow = true;
    private boolean showPaletteManager = true;
    private boolean showModelProperties = true;

    private boolean fillSelectionRequested = false;

    private final float[] pickedColor = {1.0f, 1.0f, 1.0f, 1.0f};
    private final float[] newPaletteColor = {1.0f, 1.0f, 1.0f, 1.0f};
    private final ImString newPaletteGroupName = new ImString("New Group", 128);

    private final List<PaletteGroup> paletteGroups = new ArrayList<>();
    private PaletteGroup activePaletteGroup;

    public EditorUiState() {
        createDefaultFolders();
        createDefaultPaletteGroups();
    }

    private void createDefaultFolders() {
        try {
            Files.createDirectories(Paths.get("maps"));
            Files.createDirectories(Paths.get("models"));
            Files.createDirectories(Paths.get("models", "Environment"));
            Files.createDirectories(Paths.get("models", "Vegetation"));
            Files.createDirectories(Paths.get("models", "Walls"));
            Files.createDirectories(Paths.get("models", "Floors"));
            Files.createDirectories(Paths.get("models", "Custom"));
            Files.createDirectories(Paths.get("palettes"));
        } catch (Exception ignored) {}
    }

    private void createDefaultPaletteGroups() {
        PaletteGroup defaultGroup = new PaletteGroup("Default");
        defaultGroup.colors.add(new Vector3f(1.0f, 1.0f, 1.0f));
        defaultGroup.colors.add(new Vector3f(0.8f, 0.1f, 0.1f));
        defaultGroup.colors.add(new Vector3f(0.1f, 0.8f, 0.1f));
        defaultGroup.colors.add(new Vector3f(0.1f, 0.3f, 1.0f));
        defaultGroup.colors.add(new Vector3f(0.9f, 0.8f, 0.2f));
        defaultGroup.colors.add(new Vector3f(0.1f, 0.1f, 0.1f));

        PaletteGroup environmentGroup = new PaletteGroup("Environment");
        environmentGroup.colors.add(new Vector3f(0.35f, 0.22f, 0.12f));
        environmentGroup.colors.add(new Vector3f(0.15f, 0.45f, 0.12f));
        environmentGroup.colors.add(new Vector3f(0.45f, 0.45f, 0.45f));

        paletteGroups.add(defaultGroup);
        paletteGroups.add(environmentGroup);
        activePaletteGroup = defaultGroup;
    }

    public UiAction renderStartup(Window window) {
        UiAction action = UiAction.NONE;

        ImGui.setNextWindowPos(window.getWidth() * 0.5f, window.getHeight() * 0.5f, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowSize(480, 260, ImGuiCond.Always);

        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove;

        ImGui.begin("Jail-Black Studio", flags);
        ImGui.text("Choose Editor");
        ImGui.separator();

        if (ImGui.button("Model Editor", 200, 48)) {
            workspaceKind = WorkspaceKind.MODEL;
            requestCreateModelPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Map Editor", 200, 48)) {
            workspaceKind = WorkspaceKind.MAP;
            startupVisible = false;
            action = UiAction.OPEN_MAP_WORKSPACE;
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Project Folders");
        ImGui.bulletText("Maps: /maps");
        ImGui.bulletText("Models: /models");
        ImGui.bulletText("Palettes: /palettes");
        ImGui.spacing();
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "Model Editor is the current development target.");
        ImGui.end();

        UiAction popupAction = renderCreateModelDialog();
        if (popupAction != UiAction.NONE) action = popupAction;
        return action;
    }

    public UiAction renderMainMenuBar() {
        UiAction action = UiAction.NONE;

        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New Model", "Ctrl+N")) {
                    workspaceKind = WorkspaceKind.MODEL;
                    requestCreateModelPopup();
                }
                ImGui.menuItem("Save Model JSON", "Ctrl+S");
                ImGui.menuItem("Load Model JSON", "Ctrl+O");
                ImGui.separator();
                ImGui.menuItem("Save Map", "");
                ImGui.menuItem("Load Map", "");
                ImGui.separator();
                ImGui.menuItem("Open Models Folder", "");
                ImGui.menuItem("Open Maps Folder", "");
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Edit")) {
                ImGui.menuItem("Select All", "Ctrl+A");
                ImGui.menuItem("Deselect", "Esc");
                ImGui.menuItem("Duplicate", "Ctrl+D");
                ImGui.menuItem("Delete", "Delete");
                ImGui.separator();
                if (ImGui.menuItem("Fill Selection", "")) fillSelectionRequested = true;
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Voxel")) {
                if (ImGui.menuItem("Add Voxel", "B", voxelTool == VoxelTool.ADD)) voxelTool = VoxelTool.ADD;
                if (ImGui.menuItem("Remove Voxel", "X", voxelTool == VoxelTool.REMOVE)) voxelTool = VoxelTool.REMOVE;
                if (ImGui.menuItem("Paint Voxel", "G", voxelTool == VoxelTool.PAINT)) voxelTool = VoxelTool.PAINT;
                if (ImGui.menuItem("Eyedropper", "Alt+LMB", voxelTool == VoxelTool.EYEDROPPER)) voxelTool = VoxelTool.EYEDROPPER;
                if (ImGui.menuItem("Brush", "Shift+B", voxelTool == VoxelTool.BRUSH)) voxelTool = VoxelTool.BRUSH;

                ImGui.separator();
                ImGui.text("Paint Brush");
                ImGui.sliderInt("Size", paintBrushSize, 1, 16);
                ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "Size affects painting only.");

                if (ImGui.radioButton("Cube Brush", brushShape == BrushShape.CUBE)) brushShape = BrushShape.CUBE;
                ImGui.sameLine();
                if (ImGui.radioButton("Sphere Brush", brushShape == BrushShape.SPHERE)) brushShape = BrushShape.SPHERE;

                ImGui.separator();
                showGhostVoxel = checkbox("Show Ghost Voxel", showGhostVoxel);
                showGhostInAllTools = checkbox("Ghost In All Tools", showGhostInAllTools);

                ImGui.separator();
                if (ImGui.menuItem("Fill Selection", "")) fillSelectionRequested = true;
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Mirror")) {
                mirrorX = checkbox("Mirror X", mirrorX);
                mirrorY = checkbox("Mirror Y", mirrorY);
                mirrorZ = checkbox("Mirror Z", mirrorZ);
                ImGui.separator();
                if (ImGui.menuItem("Clear Mirror")) { mirrorX = false; mirrorY = false; mirrorZ = false; }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("View")) {
                showGrid = checkbox("Grid", showGrid);
                showBounds = checkbox("Model Bounds", showBounds);
                ImGui.separator();
                ImGui.menuItem("Focus Model", "F");
                ImGui.menuItem("Reset Camera", "");
                ImGui.separator();
                ImGui.menuItem("Snap to Grid: Always On", "");
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Palettes")) {
                showPaletteManager = checkbox("Palette Manager", showPaletteManager);
                ImGui.separator();
                for (PaletteGroup group : paletteGroups) {
                    if (ImGui.menuItem(group.name, "", activePaletteGroup == group)) activePaletteGroup = group;
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Windows")) {
                showExplorer = checkbox("Explorer", showExplorer);
                showProperties = checkbox("Properties", showProperties);
                showViewport = checkbox("Viewport", showViewport);
                showModelProperties = checkbox("Model Properties", showModelProperties);
                showStatusWindow = checkbox("Status", showStatusWindow);
                showPaletteManager = checkbox("Palette Manager", showPaletteManager);
                ImGui.endMenu();
            }

            ImGui.endMainMenuBar();
        }

        UiAction popupAction = renderCreateModelDialog();
        if (popupAction != UiAction.NONE) action = popupAction;
        return action;
    }

    private UiAction renderCreateModelDialog() {
        UiAction action = UiAction.NONE;

        if (createModelPopupRequested) {
            ImGui.openPopup("Create Model");
            createModelPopupRequested = false;
            createModelPopupOpen = true;
        }

        if (!createModelPopupOpen) return action;

        ImBoolean open = new ImBoolean(true);

        if (ImGui.beginPopupModal("Create Model", open, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.inputText("Model Name", modelName);
            ImGui.inputInt("Width (X)", modelWidth);
            ImGui.inputInt("Height (Y)", modelHeight);
            ImGui.inputInt("Depth (Z)", modelDepth);

            ImGui.separator();
            ImGui.text("Preview");
            ImGui.bulletText("Bounds will be shown on all edges.");
            ImGui.bulletText("Snap to Grid is always enabled.");
            ImGui.bulletText("Model data will be saved as JSON.");

            ImGui.spacing();

            if (ImGui.button("Create", 120, 0)) {
                activeModelName = modelName.get().trim().isEmpty() ? "Untitled Model" : modelName.get().trim();
                activeModelWidth = clampSize(modelWidth.get());
                activeModelHeight = clampSize(modelHeight.get());
                activeModelDepth = clampSize(modelDepth.get());

                modelWidth.set(activeModelWidth);
                modelHeight.set(activeModelHeight);
                modelDepth.set(activeModelDepth);

                workspaceKind = WorkspaceKind.MODEL;
                startupVisible = false;
                createModelPopupOpen = false;

                action = UiAction.CREATE_MODEL_WORKSPACE;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                createModelPopupOpen = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }

        if (!open.get()) createModelPopupOpen = false;
        return action;
    }

    public void renderModelPropertiesWindow() {
        if (!showModelProperties) return;

        ImGui.setNextWindowSize(300, 220, ImGuiCond.Once);
        ImGui.begin("Model Properties");

        ImGui.text("Name: " + activeModelName);
        ImGui.text("Type: " + workspaceKind.name());
        ImGui.separator();
        ImGui.text("Size");
        ImGui.bulletText("Width X: " + activeModelWidth);
        ImGui.bulletText("Height Y: " + activeModelHeight);
        ImGui.bulletText("Depth Z: " + activeModelDepth);
        ImGui.separator();
        ImGui.text("Rules");
        ImGui.bulletText("Snap to Grid: Always On");
        ImGui.bulletText("Bounds: All Edges");
        ImGui.bulletText("Format: JSON");

        ImGui.end();
    }

    public void renderPaletteManagerWindow() {
        if (!showPaletteManager) return;

        ImGui.setNextWindowSize(340, 420, ImGuiCond.Once);
        ImGui.begin("Palette Manager");

        ImGui.text("Groups");
        ImGui.separator();

        for (PaletteGroup group : paletteGroups) {
            boolean selected = activePaletteGroup == group;
            if (ImGui.selectable(group.name, selected)) activePaletteGroup = group;
        }

        ImGui.spacing();
        ImGui.inputText("New Group", newPaletteGroupName);

        if (ImGui.button("Create Palette Group")) {
            String groupName = newPaletteGroupName.get().trim();
            if (!groupName.isEmpty()) {
                PaletteGroup group = new PaletteGroup(groupName);
                paletteGroups.add(group);
                activePaletteGroup = group;
            }
        }

        ImGui.separator();
        ImGui.text("Picked Color");
        ImGui.colorButton("##PickedColor", pickedColor, 0, 32, 32);
        ImGui.sameLine();
        ImGui.text("Eyedropper output will appear here.");
        ImGui.separator();
        ImGui.text("Active Group");

        if (activePaletteGroup != null) {
            ImGui.text(activePaletteGroup.name);
            ImGui.colorEdit4("New Color", newPaletteColor);

            if (ImGui.button("Add Color")) {
                activePaletteGroup.colors.add(new Vector3f(
                        newPaletteColor[0], newPaletteColor[1], newPaletteColor[2]));
            }

            ImGui.spacing();
            int index = 0;
            for (Vector3f color : activePaletteGroup.colors) {
                float[] rgba = {color.x, color.y, color.z, 1.0f};
                if (ImGui.colorButton("##PaletteColor" + index, rgba, 0, 26, 26)) {
                    pickedColor[0] = color.x;
                    pickedColor[1] = color.y;
                    pickedColor[2] = color.z;
                    pickedColor[3] = 1.0f;
                }
                if ((index + 1) % 8 != 0) ImGui.sameLine();
                index++;
            }
        }

        ImGui.end();
    }

    public void renderStatusWindow(float dt, int objectCount, int voxelCount, int instanceCount) {
        if (!showStatusWindow) return;

        ImGui.setNextWindowSize(360, 190, ImGuiCond.Once);
        ImGui.begin("Status");

        float fps = dt > 0.0f ? 1.0f / dt : 0.0f;

        ImGui.text("Workspace: " + workspaceKind.name());
        ImGui.text("Tool: " + voxelTool.name());
        ImGui.text("Brush Shape: " + brushShape.name());
        ImGui.text("Paint Brush Size: " + paintBrushSize[0]);
        ImGui.separator();
        ImGui.text("Cursor Voxel: -");
        ImGui.text("Objects: " + objectCount);
        ImGui.text("Voxels: " + voxelCount);
        ImGui.text("Instances: " + instanceCount);
        ImGui.separator();
        ImGui.text("FPS: " + String.format("%.1f", fps));
        ImGui.text("Snap to Grid: Always On");

        ImGui.end();
    }

    private boolean checkbox(String label, boolean value) {
        ImBoolean b = new ImBoolean(value);
        if (ImGui.checkbox(label, b)) return b.get();
        return value;
    }

    private int clampSize(int value) {
        return Math.max(1, Math.min(value, 512));
    }

    public void requestCreateModelPopup() { createModelPopupRequested = true; }
    public boolean isStartupVisible() { return startupVisible; }
    public boolean isShowExplorer() { return showExplorer; }
    public boolean isShowProperties() { return showProperties; }
    public boolean isShowViewport() { return showViewport; }
    public boolean isShowGrid() { return showGrid; }
    public boolean isShowBounds() { return showBounds; }
    public boolean isShowGhostVoxel() { return showGhostVoxel; }
    public boolean isShowGhostInAllTools() { return showGhostInAllTools; }
    public boolean isMirrorX() { return mirrorX; }
    public boolean isMirrorY() { return mirrorY; }
    public boolean isMirrorZ() { return mirrorZ; }
    public VoxelTool getVoxelTool() { return voxelTool; }
    public BrushShape getBrushShape() { return brushShape; }
    public int getPaintBrushSize() { return paintBrushSize[0]; }
    public WorkspaceKind getWorkspaceKind() { return workspaceKind; }
    public String getActiveModelName() { return activeModelName; }
    public int getActiveModelWidth() { return activeModelWidth; }
    public int getActiveModelHeight() { return activeModelHeight; }
    public int getActiveModelDepth() { return activeModelDepth; }

    public boolean consumeFillSelectionRequest() {
        boolean value = fillSelectionRequested;
        fillSelectionRequested = false;
        return value;
    }
}
// === END OF FILE ===
