#version 330 core
in vec2 vUv;
uniform sampler2D uSceneTexture;
out vec4 fragColor;

void main() {
    vec3 color = texture(uSceneTexture, vUv).rgb;
    fragColor = vec4(color, 1.0);
}
