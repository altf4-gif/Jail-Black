#version 330 core
in vec3 vNormal;
in vec2 vUv;
in vec3 vWorldPos;
in vec3 vInstanceColor;

uniform vec3 uColor;
uniform sampler2D uTexture;
uniform int uUseTexture;
uniform float uAlpha;
uniform int uMode;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform int uUnlit;
uniform bool uInstanced;

out vec4 fragColor;

void main() {
    vec3 baseColor = uInstanced ? vInstanceColor : uColor;

    if (uUnlit == 1) {
        fragColor = vec4(baseColor, uAlpha);
        return;
    }

    vec3 normal = normalize(vNormal);
    if (uMode == 10) {
        normal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    }

    vec3 lightDir = normalize(uLightDir);
    float diff = max(dot(normal, lightDir), 0.0);

    vec4 texColor = vec4(1.0);
    if (uUseTexture == 1) {
        texColor = texture(uTexture, vUv);
    }
    baseColor = baseColor * texColor.rgb;
    float ambient = 0.35;

    if (uMode == 8) {
        if (diff > 0.6) diff = 1.0;
        else if (diff > 0.2) diff = 0.5;
        else diff = 0.0;
        ambient = 0.2;
    } else if (uMode == 9) {
        diff = 0.0;
        ambient = 1.0;
    } else if (uMode == 12) {
        baseColor = vec3(0.6);
        ambient = 0.1;
        vec3 viewDir = normalize(uCamPos - vWorldPos);
        vec3 halfVec = normalize(lightDir + viewDir);
        float spec = pow(max(dot(normal, halfVec), 0.0), 128.0);
        baseColor += vec3(spec) * 1.5;
    } else if (uMode == 1) {
        diff = step(0.3, diff);
        ambient = 0.5;
    }

    vec3 lit = baseColor * (ambient + diff * 0.85);
    fragColor = vec4(lit, uAlpha);
}
// === END OF FILE ===
