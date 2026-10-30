#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aUv;

uniform mat4 uViewProj;

out vec2 vUv;
out vec3 vWorldPos;

void main() {
    vWorldPos = aPos;
    vUv = aUv;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
// === END OF FILE ===
