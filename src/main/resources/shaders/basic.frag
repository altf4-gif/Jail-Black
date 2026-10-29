#version 330 core

in vec3 vNormal;
in vec3 vWorldPosition;

uniform vec3 uColor;
out vec4 fragColor;

const vec3 LIGHT_DIRECTION = normalize(vec3(0.45, 1.0, 0.35));

void main() {
    vec3 normal = normalize(vNormal);
    float diffuse = max(dot(normal, LIGHT_DIRECTION), 0.0);
    float ambient = 0.35;
    vec3 lit = uColor * (ambient + diffuse * 0.85);
    fragColor = vec4(lit, 1.0);
}
