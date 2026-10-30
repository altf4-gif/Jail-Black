# Jail-Black — fix-пакет

Здесь только 4 файла, которые относятся к двум багам,
и `check.bat` для проверки целостности после распаковки.

## Что внутри

| Файл | Зачем |
|---|---|
| `src/main/java/com/jailblack/Main.java` | убран `glfwSetFramebufferSizeCallback` (FBO пересоздаёт `EditorState`), убран `glEnable(GL_MULTISAMPLE)` |
| `src/main/java/com/jailblack/engine/Mesh.java` | добавлен `drawMode` (треугольники / линии) |
| `src/main/java/com/jailblack/engine/MeshFactory.java` | `buildLines()` создаёт mesh с `GL_LINES` → сетка и контур выделения рисуются как линии, а не как «серая каша» |
| `src/main/java/com/jailblack/editor/EditorState.java` | авто-ресайз FBO (без краша AMD `atio6axx.dll`), клик через `glfwSetMouseButtonCallback`, `uInstanced` как `int` |
| `check.bat` | проверяет, что каждый `.java` заканчивается маркером `// === END OF FILE ===` |

## Как применить

1. Распакуй архив **поверх папки проекта** — файлы заменятся один в один.
2. Запусти `check.bat` — у всех 4 файлов последняя строка должна быть
   `// === END OF FILE ===`. Если где-то `TRUNCATED` — значит ZIP распаковался
   неправильно (крайне редко), либо поверх лёг старый файл.
3. Удали `imgui.ini` в корне проекта, если он есть.
4. Запусти `run.bat`.

## Что исправлено

**Баг 1 — серая «плоскость» вместо сетки.**
`grid` и `cubeOutline` строятся как пары индексов под `GL_LINES`,
но `Mesh.draw()` рисовал их как `GL_TRIANGLES`. Теперь у `Mesh`
есть `drawMode`, а `MeshFactory.buildLines()` передаёт `GL_LINES`.

**Баг 2 — вылет при клике / ресайзе (AMD `atio6axx.dll`).**
Раньше `Main` пересоздавал FBO в `glfwSetFramebufferSizeCallback`,
а `EditorState` продолжал держать старый (удалённый) FBO → рисование
в удалённую текстуру → `EXCEPTION_ACCESS_VIOLATION`. Теперь FBO
пересоздаётся внутри `EditorState.renderScene()`, а `Main` про него
не знает. Плюс клик обрабатывается событием `glfwSetMouseButtonCallback`
(один раз на нажатие), и `uInstanced` передаётся как `int`, а не `boolean`.

## Не трогал

`InstancedRenderer.java` (там уже был фикс с `FloatBuffer.wrap`),
`Camera`, `Framebuffer`, `ShaderProgram`, `Window`, `Scene`, `Commands`,
`CommandHistory`, `MapManager`, `SelectionManager`, `SpawnManager`,
`Voxel`, `VoxelModel`, `Transform`, пакет `game/`. К этим багам они
отношения не имеют.
