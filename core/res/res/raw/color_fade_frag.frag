#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES texUnit;
uniform float opacity;
uniform float gamma;
uniform bool forceWhite;
varying vec2 UV;

void main()
{
    if (forceWhite) {
        gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
    } else {
        vec4 color = texture2D(texUnit, UV);
        vec3 rgb = clamp(pow(color.rgb * opacity, vec3(gamma)), 0.0, 1.0);
        gl_FragColor = vec4(rgb, 1.0);
    }
}
