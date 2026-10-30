#version 330 core
in vec3 vNormal;
in vec2 vUv;
in vec3 vWorldPos;

uniform vec3 uColor;
uniform sampler2D uTexture;
uniform int uUseTexture;
uniform float uAlpha;
uniform int uMode; 
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform int uUnlit;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(vNormal);
    vec3 lightDir = normalize(uLightDir);
    float diff = max(dot(normal, lightDir), 0.0);
    
    float ambient = 0.35;
    vec3 lit = uColor * (ambient + diff * 0.85);

    fragColor = vec4(lit, uAlpha);
}
