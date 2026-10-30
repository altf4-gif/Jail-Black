# Jail-Black — direct buffer fix

## Меняется один файл

`src/main/java/com/jailblack/engine/InstancedRenderer.java`.

## Что было

```java
FloatBuffer buffer = FloatBuffer.wrap(instanceData, 0, count * 20);
glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
```

`FloatBuffer.wrap(...)` создаёт **heap buffer** (Java-массив в куче JVM).
`glBufferSubData` в LWJGL передаёт содержимое через JNI; на AMD-драйвере
(`atio6axx.dll`) попытка читать heap-память как native приводит к
`EXCEPTION_ACCESS_VIOLATION` — при спавне вокселя через ПКМ.

## Что стало

```
int size = count * 20;
FloatBuffer buffer = MemoryUtil.memAllocFloat(size);
buffer.put(instanceData, 0, size);
buffer.flip();
glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
MemoryUtil.memFree(buffer);
```

- `MemoryUtil.memAllocFloat(size)` — **direct buffer** в native-памяти.
- `buffer.put(instanceData, 0, size)` — копирует первые `size` float-ов.
- `buffer.flip()` — limit = позиция после записи.
- `glBufferSubData(...)` — читает уже direct-память.
- `MemoryUtil.memFree(buffer)` — освобождает native-память.

Дополнительно:

- `if (instanceVbo == 0) return;` в `flush()` — защита от вызова
до инициализации VBO.
- `if (instanceVbo != 0)` в `cleanup()` — не зовём `glDeleteBuffers(0)`.

## Как применить

Распакуй ZIP поверх папки проекта. Остальные файлы (EditorState,
SpawnManager, Voxel, шейдеры) не тронуты.

