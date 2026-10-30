#version 330 core
in vec2 vUv;
in vec3 vWorldPos;

out vec4 fragColor;

void main() {
    float dist = length(vWorldPos.xz);
    float fade = 1.0 - smoothstep(30.0, 50.0, dist);
    vec3 color = vec3(0.4, 0.4, 0.4);
    fragColor = vec4(color, fade * 0.8);
}
