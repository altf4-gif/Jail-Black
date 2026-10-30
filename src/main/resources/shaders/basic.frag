#version 330 core
in vec3 vNormal;
in vec2 vUv;
in vec3 vWorldPos;
in vec3 vInstanceColor;

uniform vec3 uColor;
uniform bool uInstanced;
uniform vec3 uLightDir;
uniform vec3 uCamPos;

out vec4 fragColor;

void main() {
    vec3 baseColor = uInstanced ? vInstanceColor : uColor;
    
    vec3 normal = normalize(vNormal);
    vec3 lightDir = normalize(uLightDir);
    float diff = max(dot(normal, lightDir), 0.0);
    
    vec3 viewDir = normalize(uCamPos - vWorldPos);
    vec3 halfDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(normal, halfDir), 0.0), 32.0) * 0.3;
    
    float ambient = 0.4;
    vec3 lit = baseColor * (ambient + diff * 0.7) + vec3(spec);
    
    fragColor = vec4(lit, 1.0);
}
