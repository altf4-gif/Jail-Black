#version 330 core
in vec3 vNormal;
in vec2 vUv;
in vec3 vWorldPos;

uniform vec3 uColor;
uniform float uAlpha;
uniform vec3 uLightDir;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(vNormal);
    float diff = max(dot(normal, normalize(uLightDir)), 0.0);
    vec3 lit = uColor * (0.4 + diff * 0.6);
    fragColor = vec4(lit, uAlpha);
}
// === END OF FILE ===
