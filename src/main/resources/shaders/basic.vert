#version 330 core
layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aUv;

uniform mat4 uModel;
uniform mat4 uViewProj;
uniform int uMode;

out vec3 vNormal;
out vec2 vUv;
out vec3 vWorldPos;

void main() {
    vec4 worldPos = uModel * vec4(aPosition, 1.0);
    vWorldPos = worldPos.xyz;
    vNormal = mat3(uModel) * aNormal;
    vUv = aUv;

    vec4 clipPos = uViewProj * worldPos;

    if (uMode == 2) {
        float snapResolution = 120.0;
        vec2 grid = vec2(snapResolution, snapResolution);
        clipPos.xy = floor(clipPos.xy / clipPos.w * grid) / grid * clipPos.w;
    }

    gl_Position = clipPos;
}
