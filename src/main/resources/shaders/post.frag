#version 330 core
in vec2 vUv;

uniform sampler2D uTexture;
uniform int uMode;
uniform int uPostMode;
uniform float uTime;

out vec4 fragColor;

void main() {
    vec3 color = texture(uTexture, vUv).rgb;
    fragColor = vec4(color, 1.0);
}
// === END OF FILE ===
