package com.glass.launcher.glass

/**
 * AGSL (Android 13+). Draws a whole glass pane: refracted backdrop, body tint, specular rim light,
 * fine border, liquid wobble and touch ripple. All math is in pixels; `dpx` converts dp-sized constants.
 */
const val GLASS_AGSL = """
uniform shader sharp;
uniform shader soft;
uniform float2 size;
uniform float radius;
uniform float time;
uniform float2 touch;
uniform float touchAmt;
uniform float4 tint;
uniform float4 accent;
uniform float mixv;
uniform float refr;
uniform float disp;
uniform float spec;
uniform float liquid;
uniform float border;
uniform float dpx;

float sdRR(float2 p) {
    float2 q = abs(p - size * 0.5) - (size * 0.5 - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float3 bg(float2 c) {
    return mix(float4(sharp.eval(c)), float4(soft.eval(c)), mixv).rgb;
}

half4 main(float2 p) {
    float d = sdRR(p);
    float aa = 1.0 - smoothstep(-0.75, 0.75, d);
    if (aa <= 0.0) { return half4(0.0); }

    float e = 1.0;
    float2 g = float2(sdRR(p + float2(e, 0.0)) - sdRR(p - float2(e, 0.0)),
                      sdRR(p + float2(0.0, e)) - sdRR(p - float2(0.0, e)));
    float2 n = g / (length(g) + 0.00001);

    float depth = min(size.x, size.y) * 0.5;
    float edge = clamp(-d / (depth * 0.55 + 0.001), 0.0, 1.0);
    float bend = pow(1.0 - edge, 2.4);

    // refraction: sample from further inside near the rim (lens effect)
    float2 c = p - n * bend * refr * depth * 0.55;

    // liquid wobble
    c += liquid * float2(sin(p.y * 0.04 / dpx + time * 1.4), cos(p.x * 0.035 / dpx - time * 1.1)) * 2.5 * dpx;

    // touch: ripple + gentle lens pull toward the finger
    if (touchAmt > 0.001) {
        float2 td = p - touch;
        float tl = length(td);
        float rip = touchAmt * exp(-tl / (45.0 * dpx)) * sin(tl / (7.0 * dpx) - time * 9.0);
        c += td / (tl + 0.001) * rip * 4.0 * dpx;
        c -= td * touchAmt * exp(-tl / (90.0 * dpx)) * 0.10;
    }

    float3 col = bg(c);
    if (disp > 0.01) {
        float2 off = n * bend * disp * 7.0 * dpx;
        col = float3(bg(c + off).r, col.g, bg(c - off).b);
    }

    // body tint (keeps text readable, stays thin so the wallpaper shows through)
    col = mix(col, tint.rgb, tint.a);

    float ang = -2.2 + sin(time * 0.35) * 0.35 * liquid;
    float2 L = float2(cos(ang), sin(ang));
    float facing = max(dot(n, L), 0.0);
    float back = max(dot(n, -L), 0.0);

    // rim light
    float rimW = pow(1.0 - edge, 6.0);
    col += float3(rimW * (0.20 + 1.0 * facing + 0.30 * back) * spec * 0.85);

    // top sheen + soft specular spot
    float sheen = smoothstep(0.55, 0.0, p.y / size.y);
    col += float3(sheen * spec * 0.10);
    float2 q = (p - float2(size.x * (0.30 + 0.08 * sin(time * 0.6) * liquid), size.y * 0.18)) / (size * 0.5 + 1.0);
    col += float3(exp(-dot(q, q) * 3.0) * spec * 0.11);

    // accent caustic along the bottom edge
    col += accent.rgb * smoothstep(0.65, 1.0, p.y / size.y) * spec * 0.07;

    // fine border: bright where it faces the light, accent-tinted on the far side
    float line = 1.0 - smoothstep(0.0, 1.1 * dpx, abs(d + 0.6 * dpx));
    float3 bc = mix(float3(1.0), accent.rgb, clamp(1.0 - facing * 1.6, 0.0, 1.0) * 0.55);
    col = mix(col, bc, line * border * (0.22 + 0.78 * facing + 0.18 * back));

    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * aa), half(aa));
}
"""
