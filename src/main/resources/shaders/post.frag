#version 330 core
in vec2 vUv;
uniform sampler2D uTexture;
uniform int uMode; 
uniform int uPostMode; 
uniform float uTime;
out vec4 fragColor;

float rand(vec2 co){
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 applyPost(vec3 color, int mode, vec2 uv) {
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
    if (mode == 19) return color + vec3(rand(uv) - 0.5) * 0.1;
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
    vec2 uv = vUv;
    vec3 color = texture(uTexture, uv).rgb;
    
    if (uMode == 5) { // ASCII
        vec2 grid = vec2(160.0, 90.0);
        vec2 cell = floor(uv * grid);
        vec2 localUv = fract(uv * grid) - 0.5;
        float luma = dot(color, vec3(0.299, 0.587, 0.114));
        float symbol = 0.0;
        if (luma > 0.8) symbol = 1.0;
        else if (luma > 0.6) symbol = step(0.0, localUv.x * localUv.y);
        else if (luma > 0.4) symbol = step(0.2, abs(localUv.x) + abs(localUv.y));
        else if (luma > 0.2) symbol = step(0.3, abs(localUv.x));
        else symbol = 0.0;
        color = vec3(symbol);
    }
    else if (uMode == 6) { // Pixel Art
        color = floor(color * 8.0) / 8.0;
        if (uv.x < 0.01 || uv.x > 0.99 || uv.y < 0.01 || uv.y > 0.99) color = vec3(0.0);
    }
    else if (uMode == 7) { // Toy
        float blurAmount = abs(uv.y - 0.5) * 0.1;
        vec3 blur = vec3(0.0);
        for(int x = -2; x <= 2; x++) {
            for(int y = -2; y <= 2; y++) {
                blur += texture(uTexture, uv + vec2(x, y) * blurAmount / 1280.0).rgb;
            }
        }
        color = mix(color, blur / 25.0, smoothstep(0.2, 0.5, abs(uv.y - 0.5)));
    }
    else if (uMode == 11) { // No AA
        color = pow(color, vec3(1.2));
    }
    else if (uMode == 13) { // Psychedelic
        float warp = sin(uv.y * 10.0 + uTime * 3.0) * 0.05;
        uv.x += warp;
        float r = texture(uTexture, uv + vec2(0.01 * sin(uTime), 0.0)).r;
        float g = texture(uTexture, uv).g;
        float b = texture(uTexture, uv - vec2(0.01 * sin(uTime), 0.0)).b;
        color = vec3(r, g, b);
        color = sin(color * 10.0 + uTime * 5.0) * 0.5 + 0.5;
    }

    color = applyPost(color, uPostMode, uv);

    fragColor = vec4(color, 1.0);
}
