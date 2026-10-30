# Jail-Black — ImBoolean fix

## 4 точечные правки в `EditorState.java`

1. Добавлен `import imgui.type.ImBoolean;`
2. `private final boolean[] paletteOpen = {true};`
   → `private final ImBoolean paletteOpen = new ImBoolean(true);`
3. В `renderToolbar()`:
   `paletteOpen[0]` → `paletteOpen.get()`,
   `paletteOpen[0] = !paletteOpen[0]` → `paletteOpen.set(!paletteOpen.get())`.
4. В `renderPalette()`:
   `if (!paletteOpen[0]) return;` → `if (!paletteOpen.get()) return;`

Строка `ImGui.begin("Palette", paletteOpen, 0)` не менялась — теперь она
тип-корректна, потому что `paletteOpen` имеет тип `ImBoolean`.

## Почему это работало неправильно

В `imgui-java` сигнатура `ImGui.begin(String, ImBoolean, int)` принимает
**`ImBoolean`**, а не `boolean[]`. На этой сигнатуре и держится весь
механизм «окно закрывается крестиком»: ImGui пишет `false` прямо в
переданный `ImBoolean`, и это состояние читается обратно в
`renderPalette()`.

С `boolean[]` компилятор не находил подходящую перегрузку `begin(...)` —
отсюда ошибка.

## Как применить

Распакуй ZIP поверх папки проекта. Меняется только `EditorState.java`.
Остальные файлы из прошлых пакетов не тронуты.
