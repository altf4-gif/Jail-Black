#version 330 core
in vec3 vNormal;
in vec2 vUv;

uniform vec3 uColor;
uniform sampler2D uTexture;
uniform int uUseTexture;
uniform float uAlpha;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(vNormal);
    float diffuse = max(dot(normal, normalize(vec3(0.45, 1.0, 0.35))), 0.0);
    float ambient = 0.35;
    
    vec4 texColor = vec4(1.0);
    if (uUseTexture == 1) {
        texColor = texture(uTexture, vUv);
    }
    
    vec3 lit = uColor * texColor.rgb * (ambient + diffuse * 0.85);
    fragColor = vec4(lit, uAlpha);
}
