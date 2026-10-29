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
uniform float uTime;
uniform int uPostMode;
uniform int uIsPostPreview;

out vec4 fragColor;

float bayerMatrix[64] = float[](
     0.0, 32.0,  8.0, 40.0,  2.0, 34.0, 10.0, 42.0,
    48.0, 16.0, 56.0, 24.0, 50.0, 18.0, 58.0, 26.0,
    12.0, 44.0,  4.0, 36.0, 14.0, 46.0,  6.0, 38.0,
    60.0, 28.0, 52.0, 20.0, 62.0, 30.0, 54.0, 22.0,
     3.0, 35.0, 11.0, 43.0,  1.0, 33.0,  9.0, 41.0,
    51.0, 19.0, 59.0, 27.0, 49.0, 17.0, 57.0, 25.0,
    15.0, 47.0,  7.0, 39.0, 13.0, 45.0,  5.0, 37.0,
    63.0, 31.0, 55.0, 23.0, 61.0, 29.0, 53.0, 21.0
);

vec3 applyPost(vec3 color, int mode) {
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    if (mode == 0) return color;
    if (mode == 1) return (color - 0.5) * 1.5 + 0.5;
    if (mode == 2) return mix(vec3(luma), color, 1.5);
    if (mode == 3) return mix(vec3(luma), (color - 0.5) * 1.5 + 0.5, 1.5);
    if (mode == 4) return 1.0 - color;
    if (mode == 5) return vec3(luma);
    if (mode == 6) return vec3((luma - 0.5) * 1.5 + 0.5);
    if (mode == 7) return mix(color, color * vec3(1.2, 1.0, 0.7), 0.8);
    if (mode == 8) return vec3(color.g, color.r, color.b) * 1.5;
    if (mode == 9) return color * vec3(1.2, 1.1, 0.8);
    if (mode == 10) return mix(color, color * vec3(1.5, 1.0, 0.5), 0.5);
    if (mode == 11) return mix(color, vec3(0.8), 0.3);
    if (mode == 12) return color * vec3(1.5, 0.8, 0.8);
    if (mode == 13) return mix(color, vec3(0.5, 0.8, 1.0), 0.4);
    if (mode == 14) return color * 0.5;
    if (mode == 15) return pow(color, vec3(1.2)) * 1.5;
    if (mode == 16) return color * vec3(1.2, 1.0, 0.9) + 0.1;
    if (mode == 17) return mix(color, color * vec3(1.2, 0.8, 0.6), 0.7);
    if (mode == 18) return pow(color, vec3(1.5)) + vec3(0.2, 0.0, 0.0);
    if (mode == 19) return color + vec3(fract(sin(dot(vUv, vec2(12.9898,78.233))) * 43758.5453) - 0.5) * 0.1;
    if (mode == 20) return mix(color, vec3(luma) * vec3(1.0, 0.9, 0.7), 0.5);
    if (mode == 21) return mix(color, vec3(0.5, 0.6, 0.7), 0.5);
    if (mode == 22) return mix(color, vec3(0.8, 0.9, 0.9), 0.6);
    if (mode == 23) return color * 0.6 - vec3(0.1, 0.0, 0.2);
    if (mode == 24) return color * vec3(1.3, 1.1, 0.7);
    if (mode == 25) return vec3(0.0, color.g * 1.5, 0.0);
    if (mode == 26) return color * vec3(1.5, 0.2, 0.2);
    if (mode == 27) return mix(color, vec3(0.0, 0.8, 0.8), 0.5);
    if (mode == 28) return color * vec3(1.1, 1.2, 0.9);
    if (mode == 29) return mix(color, vec3(0.5, 1.0, 0.0), 0.4);
    if (mode == 30) return mix(color, vec3(1.0, 0.5, 0.8), 0.4);
    if (mode == 31) return mix(color, vec3(0.9, 0.8, 0.7), 0.3);
    if (mode == 32) return color * 0.2 + vec3(0.0, 0.0, 0.3);
    if (mode == 33) return mix(color, vec3(0.2, 0.6, 0.2), 0.4);
    if (mode == 34) return mix(color, vec3(0.0, 0.3, 0.8), 0.5);
    if (mode == 35) return mix(color, vec3(1.0, 0.8, 0.9), 0.6);
    if (mode == 36) return mix(color, vec3(0.5, 1.0, 0.0), 0.6);
    if (mode == 37) return mix(color, vec3(1.0, 0.2, 0.8), 0.5);
    if (mode == 38) return color * 1.5 + 0.2;
    if (mode == 39) return mix(color, vec3(1.0, 0.5, 0.5), 0.4);
    if (mode == 40) return color * vec3(1.2, 1.1, 0.8);
    if (mode == 41) return mix(color, vec3(0.0, 1.0, 0.0), 0.2) * 1.2;
    if (mode == 42) return mix(color, vec3(0.0, 0.5, 0.8), 0.4);
    if (mode == 43) return mix(color, vec3(0.8, 0.9, 0.8), 0.5);
    if (mode == 44) return mix(color, vec3(1.0), 0.4);
    if (mode == 45) return color * vec3(0.8, 0.9, 1.2);
    if (mode == 46) return pow(color, vec3(0.8));
    if (mode == 47) return color * vec3(1.5, 1.3, 0.8);
    if (mode == 48) return mix(color, vec3(1.0, 0.4, 0.1), 0.5);
    if (mode == 49) return color * vec3(1.5, 0.1, 0.1);
    if (mode == 50) return mix(color, vec3(0.5, 1.0, 0.0), 0.7);
    if (mode == 51) return mix(color, vec3(0.0, 0.8, 0.8), 0.3);
    if (mode == 52) return pow(color, vec3(0.5));
    if (mode == 53) return color * 0.8 + 0.1;
    if (mode == 54) return color * vec3(1.2, 1.0, 0.8);
    if (mode == 55) return color * vec3(1.2, 0.9, 0.7);
    if (mode == 56) return mix(color, vec3(0.7, 0.8, 1.0), 0.5);
    return color;
}

void main() {
    if (uIsPostPreview == 1) {
        // Красивый градиент по горизонтали (vUv.x) со ступеньками
        float hue = vUv.x;
        float stepHue = floor(hue * 40.0) / 40.0;
        vec3 rainbow = 0.5 + 0.5 * cos(6.28318 * (stepHue + vec3(0.0, 0.33, 0.67)));

        vec3 previewColor = applyPost(rainbow, uPostMode);

        // Черная рамка
        if (vUv.x < 0.04 || vUv.x > 0.96 || vUv.y < 0.04 || vUv.y > 0.96) {
            previewColor = vec3(0.1);
        }
        // Темная подложка для текста снизу
        if (vUv.y < 0.3) {
            previewColor = mix(previewColor, vec3(0.0), 0.8);
        }

        fragColor = vec4(previewColor, uAlpha);
        return;
    }

    vec3 baseColor = uColor;
    if (uUnlit == 1) {
        fragColor = vec4(uColor, uAlpha);
        return;
    }

    vec3 normal = normalize(vNormal);
    
    if (uMode == 10) { // Low-poly
        normal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));
    }

    vec3 lightDir = normalize(uLightDir);
    float diff = max(dot(normal, lightDir), 0.0);
    
    vec4 texColor = vec4(1.0);
    if (uUseTexture == 1) {
        texColor = texture(uTexture, vUv);
    }
    
    baseColor = uColor * texColor.rgb;
    float ambient = 0.35;

    if (uMode == 8) { // Cel-shading
        if (diff > 0.6) diff = 1.0;
        else if (diff > 0.2) diff = 0.5;
        else diff = 0.0;
        ambient = 0.2;
    } else if (uMode == 9) { // Flat
        diff = 0.0;
        ambient = 1.0;
    } else if (uMode == 12) { // Aluminum
        baseColor = vec3(0.6);
        ambient = 0.1;
        vec3 viewDir = normalize(uCamPos - vWorldPos);
        vec3 halfVec = normalize(lightDir + viewDir);
        float spec = pow(max(dot(normal, halfVec), 0.0), 128.0);
        baseColor += vec3(spec) * 1.5;
    } else if (uMode == 1) { // Minecraft
        diff = step(0.3, diff); 
        ambient = 0.5;
    }

    vec3 lit = baseColor * (ambient + diff * 0.85);

    if (uMode == 3) { // Dither
        int x = int(mod(gl_FragCoord.x, 8.0));
        int y = int(mod(gl_FragCoord.y, 8.0));
        float threshold = bayerMatrix[x + y * 8] / 64.0;
        float luma = dot(lit, vec3(0.299, 0.587, 0.114));
        float dithered = floor(luma * 4.0 + threshold) / 4.0;
        lit = vec3(dithered * 0.5, dithered * 0.8, dithered * 0.9); 
    }

    fragColor = vec4(lit, uAlpha);
}
