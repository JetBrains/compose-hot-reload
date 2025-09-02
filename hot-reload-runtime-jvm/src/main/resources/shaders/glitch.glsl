uniform float2 iResolution;
uniform float iTime;
uniform shader content;

const float PI = 3.14159265358979323846;
const float PI2 = PI * 2.0;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float scanNoise(float y, float t) {
    const float FRAMES = 60.0;
    float frame = floor(fract(t) * FRAMES);
    float nextFrame = mod(frame + 1.0, FRAMES);
    float n0 = hash11(floor(y * 0.75) + frame);
    float n1 = hash11(floor(y * 0.75) + nextFrame);
    return mix(n0, n1, frame);
}

vec2 channelOffset(float y, float t) {
    float base = (sin(t * PI2 + y * 0.05) + 1.0) * 0.5;
    float rnd = hash11(floor(y) + floor(t * 60.0));
    float magnitude = (0.5 * base + 0.5 * rnd) * 3.0;
    float direction = (rnd > 0.5 ? 1.0 : -1.0);
    return vec2(direction * magnitude, 0.0);
}

float tearAmount(float y, float t) {
    float band = floor(y / 12.0);
    float r = hash11(band + floor(t * 5.0));
    float pulse = smoothstep(0.65, 1.0, sin(t * 5.0 + band * 0.7) * 0.5 + 0.5);
    return (r * 2.0 - 1.0) * 8.0 * pulse;
}

float scanlineMask(float y) {
    float v = sin(y * 3.14159);
    return mix(0.85, 1.0, v * v);
}

float verticalJitter(float x, float t) {
    return (hash21(vec2(floor(x * 0.1), floor(t * 20.0))) - 0.5) * 2.0;
}

half4 main(vec2 fragCoord) {
    vec2 pixel = fragCoord;

    float tear = tearAmount(pixel.y, iTime);
    pixel.x += tear;

    float spike = smoothstep(0.8, 1.0, fract(iTime * 2.0));
    pixel.y += verticalJitter(pixel.x, iTime) * 2.0 * spike;

    vec2 offset = channelOffset(pixel.y, iTime);
    vec2 uvR = (pixel + offset) / iResolution;
    vec2 uvG = pixel / iResolution;
    vec2 uvB = (pixel - offset) / iResolution;

    float block = hash21(floor(pixel / 12.0));
    float blockShift = step(0.82, block) * (block - 0.82) * 40.0;
    uvR.x += blockShift / iResolution.x;
    uvG.x -= blockShift / iResolution.x * 0.5;
    uvB.x += blockShift / iResolution.x * 0.25;

    half4 cR = content.eval(uvR * iResolution);
    half4 cG = content.eval(uvG * iResolution);
    half4 cB = content.eval(uvB * iResolution);

    half3 color = half3(cR.r, cG.g, cB.b);

    float mask = scanlineMask(pixel.y * 0.75);
    float n = scanNoise(pixel.y, iTime);
    float glitchStrength = smoothstep(0.6, 1.0, n);
    half luma = dot(color, half3(0.299, 0.587, 0.114));
    color = mix(color, half3(luma, luma, luma), half(0.15) * half(glitchStrength));
    color *= half(mask);

    float flash = step(0.995, hash11(floor(pixel.y) + floor(iTime * 120.0))) * 0.25;
    color = min(color + half3(flash), half3(1.0));

    float a = content.eval(pixel).a;
    return half4(color, a);
}
