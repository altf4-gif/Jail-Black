# Jail-Black

Java 3D voxel editor/engine built on LWJGL 3 + ImGui.

## Build & Run

```

run.bat

```

Or, if Gradle is installed:

```

gradle run

```

## IntelliJ IDEA

1. File → Open → выбрать папку проекта
2. Дождаться Gradle sync
3. Открыть `src/main/java/com/jailblack/Main.java`, нажать зелёный ▶

## Исправление (эта версия)

Файл `src/main/java/com/jailblack/engine/InstancedRenderer.java` исправлен:
в LWJGL 3 у `glBufferSubData` нет перегрузки с параметром `size`.
Вместо `glBufferSubData(target, offset, size, float[])` теперь используется
`FloatBuffer.wrap(data, 0, count * 19)` и трёхаргументный вызов
`glBufferSubData(target, offset, buffer)`.

## Примечание

В архиве нет папки `src/main/resources/shaders/` (шейдеры .vert/.frag),
так как они не были найдены в репозитории при сборке. Если проект требует
шейдеры, положите их в `src/main/resources/shaders/`.
