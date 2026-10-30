#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aUv;

layout (location = 3) in mat4 instanceModel;
layout (location = 7) in vec4 instanceColor;

uniform mat4 uViewProj;
uniform mat4 uModel;
uniform vec3 uColor;
uniform bool uInstanced;

out vec3 vNormal;
out vec2 vUv;
out vec3 vWorldPos;
out vec4 vInstanceColor;

void main() {
    mat4 model = uInstanced ? instanceModel : uModel;
    vec4 color = uInstanced ? instanceColor : vec4(uColor, 1.0);

    vec4 worldPos = model * vec4(aPos, 1.0);
    vWorldPos = worldPos.xyz;
    vNormal = mat3(transpose(inverse(model))) * aNormal;
    vUv = aUv;
    vInstanceColor = color;
    gl_Position = uViewProj * worldPos;
}
// === END OF FILE ===
