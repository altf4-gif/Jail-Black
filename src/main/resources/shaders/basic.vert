#version 330 core

layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aNormal;

uniform mat4 uModel;
uniform mat4 uViewProj;

out vec3 vNormal;
out vec3 vWorldPosition;

void main() {
    vec4 worldPosition = uModel * vec4(aPosition, 1.0);
    vWorldPosition = worldPosition.xyz;
    vNormal = mat3(uModel) * aNormal;
    gl_Position = uViewProj * worldPosition;
}
