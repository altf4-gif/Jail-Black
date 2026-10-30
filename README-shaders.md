# Jail-Black — пакет шейдеров

## Что внутри

`src/main/resources/shaders/` — все 10 файлов, которые `Main.java` пытается
загрузить через `ShaderProgram.fromResources(...)`:

| Файл | Кто использует |
|---|---|
| `basic.vert` / `basic.frag` | `basicShader` (воксели, instanced) |
| `grid.vert` / `grid.frag` | `gridShader` (сетка + оси) |
| `outline.vert` / `outline.frag` | `outlineShader` (контур выделения) |
| `ghost.vert` / `ghost.frag` | `ghostShader` (превью-призрак) |
| `post.vert` / `post.frag` | `postShader` (пост-обработка) |

Плюс `check.bat` — проверяет, что каждый файл заканчивается маркером
`// === END OF FILE ===`.

## Почему была ошибка `Missing entry point`

`Missing entry point` = в загруженном `.frag`/`.vert` нет `void main()`.
Причина — файл на диске был обрезан при копипасте (или отсутствовал).
`outline.*`, `grid.*`, `ghost.*`, `post.*` в репозитории вообще не было —
`Main.java` падал бы на первом же `fromResources(...)`.

## Как применить

1. Распакуй ZIP **поверх папки проекта** (папка `src/main/resources/shaders/`
   создастся автоматически).
2. Запусти `check.bat`. Должно быть `OK: 14   FAIL: 0`.
3. Если где-то `TRUNCATED` — файл не распаковался, распакуй ZIP ещё раз.
4. Запусти `run.bat`.

## Примечание

`run.bat` кладёт `src\main\resources` в classpath, поэтому
`ShaderProgram.class.getClassLoader().getResourceAsStream("shaders/basic.vert")`
найдёт файлы. Если запускаешь через Gradle — они попадут в `resources`
автоматически, потому что лежат в стандартном `src/main/resources`.
