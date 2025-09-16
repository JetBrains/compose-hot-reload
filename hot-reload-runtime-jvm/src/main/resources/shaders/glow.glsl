uniform float2 iResolution;
uniform float iFrequency;
uniform float iTime;
uniform float4 iBaseColor;
uniform float iScale;

const float PI = 3.14159265358979323846;
const float PI2 = PI * 2.0;

vec3 rgb2hsv(vec3 c) {
    float r = c.r, g = c.g, b = c.b;
    float h = 0.0, s = 0.0, v = 0.0;

    float maxC = max(r, max(g, b));
    float minC = min(r, min(g, b));
    float delta = maxC - minC;

    // Value
    v = maxC;

    // Saturation
    if (maxC <= 0.0) return vec3(h, s, v);
    s = delta / maxC;

    // Hue
    if (delta > 0.0) {
        if (r == maxC) {
            h = (g - b) / delta;
        } else if (g == maxC) {
            h = 2.0 + (b - r) / delta;
        } else {
            h = 4.0 + (r - g) / delta;
        }
        h /= 6.0;
        if (h < 0.0) h += 1.0;
    }

    return vec3(h, s, v);
}

vec3 hsv2rgb(vec3 c) {
    float h = c.x, s = c.y, v = c.z;
    float r, g, b;

    if (s == 0.0) return vec3(v, v, v);

    float sector = h * 6.0;
    float i = floor(sector);
    float f = sector - i;

    float p = v * (1.0 - s);
    float q = v * (1.0 - s * f);
    float t = v * (1.0 - s * (1.0 - f));

    if (i == 0.0) {
        r = v; g = t; b = p;
    } else if (i == 1.0) {
        r = q; g = v; b = p;
    } else if (i == 2.0) {
        r = p; g = v; b = t;
    } else if (i == 3.0) {
        r = p; g = q; b = v;
    } else if (i == 4.0) {
        r = t; g = p; b = v;
    } else {
        r = v; g = p; b = q;
    }

    return vec3(r, g, b);
}

float4 shade1(float4 base) {
    vec3 hsv = rgb2hsv(base.rgb);
    // +18 degrees hue shift, +10% saturation, +5% value
    hsv.x = fract(hsv.x + 18.0 / 360.0);
    hsv.y = clamp(hsv.y * 1.10, 0.0, 1.0);
    hsv.z = clamp(hsv.z * 1.05, 0.0, 1.0);
    vec3 rgb = hsv2rgb(hsv);
    return float4(rgb, base.a);
}

float4 shade2(float4 base) {
    vec3 hsv = rgb2hsv(base.rgb);
    // -18 degrees hue shift, -5% saturation, -8% value
    hsv.x = fract(hsv.x - 18.0 / 360.0 + 1.0);
    hsv.y = clamp(hsv.y * 0.95, 0.0, 1.0);
    hsv.z = clamp(hsv.z * 0.92, 0.0, 1.0);
    vec3 rgb = hsv2rgb(hsv);
    return float4(rgb, base.a);
}

float sdRoundedRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - (b - vec2(r));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float glowFalloff(float d, float radius) {
    float x = clamp(1.0 - d / radius, 0.0, 1.0);
    return x * x;
}

half3 mesh3(vec2 position) {
    float minDim = min(iResolution.x, iResolution.y);
    // normalize the position from [-0.5, 0.5] to [0.0, 1.0]
    vec2 uv = (position * minDim + 0.5 * iResolution) / iResolution;

    float angle = iTime * PI2;
    float cosAngle = cos(angle);
    float sinAngle = sin(angle);
    mat2 rotation = mat2(cosAngle, -sinAngle, sinAngle, cosAngle);

    // Mesh points coordinates
    vec2 center = vec2(0.5, 0.5);
    float radius = 0.45;
    vec2 p0 = center + (rotation * (radius * vec2(1.0, 0.0)));
    vec2 p1 = center + (rotation * (radius * vec2(-0.5, 0.8660254)));
    vec2 p2 = center + (rotation * (radius * vec2(-0.5, -0.8660254)));

    // distance/weight to each mesh point
    float epsilon = 1e-4;
    float w0 = 1.0 / (distance(uv, p0) + epsilon);
    float w1 = 1.0 / (distance(uv, p1) + epsilon);
    float w2 = 1.0 / (distance(uv, p2) + epsilon);
    float wSum = w0 + w1 + w2;
    w0 /= wSum; w1 /= wSum; w2 /= wSum;

    half3 color0 = half3(iBaseColor.rgb);
    half3 color1 = half3(shade1(iBaseColor).rgb);
    half3 color2 = half3(shade2(iBaseColor).rgb);

    return half3(w0) * color0 + half3(w1) * color1 + half3(w2) * color2;
}

half4 renderGlow(vec2 position, vec2 halfSize) {
    // scale to calibrate relative sizes to the actual size of the image
    float scale = iScale / min(iResolution.x, iResolution.y);

    float base = min(halfSize.x, halfSize.y);
    float thickness = base * scale;
    float rim = base * scale;
    float radius = base * scale;

    float angle = iTime * PI2;
    float breathe = mix(0.7, 1.3, 0.5 + 0.5 * sin(angle * mix(0.25, 0.6, iFrequency)));

    // Masks
    float dist = sdRoundedRect(position, halfSize, radius);
    float rimMask = exp(-pow(abs(dist) / max(rim, 1e-10), 1.2));
    float haloMask = glowFalloff(abs(dist), thickness) * 0.9;

    // Combine masks and color
    float strength = clamp(rimMask * 0.9 + haloMask * 0.7, 0.0, 1.0);
    strength *= clamp(breathe, 0.8, 1.0);
    strength = clamp(strength, 0.0, 1.0);

    half3 color = mesh3(position) * half(strength);
    half alpha = half(iBaseColor.a) * half(strength);
    return half4(color, alpha);
}

half4 main(vec2 fragCoord) {
    float minDim = min(iResolution.x, iResolution.y);
    vec2 position = (fragCoord - 0.5 * iResolution) / minDim;
    vec2 halfSize = 0.5 * iResolution / minDim;
    return renderGlow(position, halfSize);
}
