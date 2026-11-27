#version 120

uniform int mode;
uniform vec2 size;
uniform float radius, checker, fade;
uniform vec4 tint;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec2 p = abs((uv - 0.5) * size) - (size * 0.5 - radius);
    float distance = length(max(p, 0.0)) + min(max(p.x, p.y), 0.0) - radius;
    vec3 rgb;
    if (mode == 0) {
        rgb = mix(vec3(1.0), tint.rgb, uv.x) * (1.0 - uv.y);
    } else if (mode == 1) {
        rgb = clamp(abs(mod(uv.y * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    } else {
        vec2 cell = floor(uv * size / checker);
        float gray = mix(0.4, 0.6, mod(cell.x + cell.y, 2.0));
        rgb = mix(vec3(gray), tint.rgb, mode == 2 ? uv.y : tint.a);
    }
    // A sub-byte dither breaks up 8-bit bands without moving the edge or the selection.
    float dither = (fract(dot(gl_FragCoord.xy, vec2(0.75487766, 0.56984029))) - 0.5) / 255.0;
    gl_FragColor = vec4(rgb + dither, fade * (1.0 - smoothstep(-0.85, 0.0, distance)));
}
