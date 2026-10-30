#version 330 core
in vec2 vUv;
in vec3 vWorldPos;

out vec4 fragColor;

void main() {
    float dist = length(vWorldPos.xz);
    float fade = 1.0 - smoothstep(20.0, 50.0, dist);

    vec3 color = vec3(0.35);
    if (abs(vWorldPos.z) < 0.001) color = vec3(0.6, 0.15, 0.15);
    if (abs(vWorldPos.x) < 0.001) color = vec3(0.15, 0.15, 0.6);

    fragColor = vec4(color * fade + 0.03, 1.0);
}
// === END OF FILE ===
