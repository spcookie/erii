import {Mesh, Program, Renderer, Triangle} from './vendor/ogl.min.mjs';

const DEFAULTS = Object.freeze({
    speed: 2.5,
    rayColor1: '#EAB308',
    rayColor2: '#96C8FF',
    intensity: 2,
    spread: 2,
    origin: 'top-left',
    tilt: 0,
    saturation: 1.5,
    blend: 0.75,
    falloff: 1.6,
    opacity: 1
});

const THEME_RAY_COLORS = Object.freeze({
    light: Object.freeze({
        rayColor1: '#D97706',
        rayColor2: '#3B82F6'
    }),
    dark: Object.freeze({
        rayColor1: '#EAB308',
        rayColor2: '#96C8FF'
    })
});

const VERTEX_SHADER = `
attribute vec2 position;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
}`;

const FRAGMENT_SHADER = `
precision highp float;

uniform float iTime;
uniform vec2 iResolution;
uniform float iSpeed;
uniform vec3 iRayColor1;
uniform vec3 iRayColor2;
uniform float iIntensity;
uniform float iSpread;
uniform float iFlipX;
uniform float iFlipY;
uniform float iTilt;
uniform float iSaturation;
uniform float iBlend;
uniform float iFalloff;
uniform float iOpacity;

float rayStrength(vec2 raySource, vec2 rayRefDirection, vec2 coord, float seedA, float seedB, float speed) {
    vec2 sourceToCoord = coord - raySource;
    float cosAngle = dot(normalize(sourceToCoord), rayRefDirection);
    return clamp(
        (0.45 + 0.15 * sin(cosAngle * seedA + iTime * speed)) +
        (0.3 + 0.2 * cos(-cosAngle * seedB + iTime * speed)),
        0.0,
        1.0
    ) * clamp((iResolution.x - length(sourceToCoord)) / iResolution.x, 0.5, 1.0);
}

void main() {
    vec2 fragCoord = gl_FragCoord.xy;
    if (iFlipX > 0.5) fragCoord.x = iResolution.x - fragCoord.x;
    if (iFlipY > 0.5) fragCoord.y = iResolution.y - fragCoord.y;

    vec2 coord = vec2(fragCoord.x, iResolution.y - fragCoord.y);
    vec2 rayPos = vec2(iResolution.x * 1.1, -0.5 * iResolution.y);

    float tiltRad = iTilt * 3.14159265 / 180.0;
    float cs = cos(tiltRad);
    float sn = sin(tiltRad);
    vec2 rel = coord - rayPos;
    vec2 tiltedCoord = vec2(rel.x * cs - rel.y * sn, rel.x * sn + rel.y * cs) + rayPos;

    float halfSpread = iSpread * 0.275;
    vec2 rayRefDir1 = normalize(vec2(cos(0.785398 + halfSpread), sin(0.785398 + halfSpread)));
    vec2 rayRefDir2 = normalize(vec2(cos(0.785398 - halfSpread), sin(0.785398 - halfSpread)));

    vec4 rays1 = vec4(iRayColor1, 1.0) *
        rayStrength(rayPos, rayRefDir1, tiltedCoord, 36.2214, 21.11349, iSpeed);
    vec4 rays2 = vec4(iRayColor2, 1.0) *
        rayStrength(rayPos, rayRefDir2, tiltedCoord, 22.3991, 18.0234, iSpeed * 0.2);

    vec4 color = rays1 * (1.0 - iBlend) * 0.9 + rays2 * iBlend * 0.9;

    float distanceToLight =
        length(fragCoord.xy - vec2(rayPos.x, iResolution.y - rayPos.y)) / iResolution.y;
    float brightness = iIntensity * 0.4 / pow(max(distanceToLight, 0.001), iFalloff);
    color.rgb *= brightness;

    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    color.rgb = mix(vec3(gray), color.rgb, iSaturation);

    color.a = max(color.r, max(color.g, color.b)) * iOpacity;
    gl_FragColor = color;
}`;

function hexToRgb(hex) {
    const match = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return match
        ? [
            parseInt(match[1], 16) / 255,
            parseInt(match[2], 16) / 255,
            parseInt(match[3], 16) / 255
        ]
        : [1, 1, 1];
}

function originToFlip(origin) {
    switch (origin) {
        case 'top-left':
            return [1, 0];
        case 'bottom-right':
            return [0, 1];
        case 'bottom-left':
            return [1, 1];
        default:
            return [0, 0];
    }
}

function finiteNumber(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}

function resolveTheme(prefersDark) {
    const requestedTheme = document.documentElement.dataset.theme || 'light';
    if (requestedTheme === 'dark' || requestedTheme === 'light') return requestedTheme;
    return prefersDark ? 'dark' : 'light';
}

function optionsFromDataset(element) {
    return {
        speed: finiteNumber(element.dataset.speed, DEFAULTS.speed),
        rayColor1: element.dataset.rayColor1 || DEFAULTS.rayColor1,
        rayColor2: element.dataset.rayColor2 || DEFAULTS.rayColor2,
        intensity: finiteNumber(element.dataset.intensity, DEFAULTS.intensity),
        spread: finiteNumber(element.dataset.spread, DEFAULTS.spread),
        origin: element.dataset.origin || DEFAULTS.origin,
        tilt: finiteNumber(element.dataset.tilt, DEFAULTS.tilt),
        saturation: finiteNumber(element.dataset.saturation, DEFAULTS.saturation),
        blend: finiteNumber(element.dataset.blend, DEFAULTS.blend),
        falloff: finiteNumber(element.dataset.falloff, DEFAULTS.falloff),
        opacity: finiteNumber(element.dataset.opacity, DEFAULTS.opacity)
    };
}

class SideRays {
    constructor(element, options = {}) {
        this.element = element;
        this.options = {...DEFAULTS, ...options};
        this.renderer = null;
        this.mesh = null;
        this.uniforms = null;
        this.animationFrame = 0;
        this.isVisible = true;
        this.isDestroyed = false;
        this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        this.colorScheme = window.matchMedia('(prefers-color-scheme: dark)');
        this.options = {
            ...this.options,
            ...THEME_RAY_COLORS[resolveTheme(this.colorScheme.matches)]
        };
        this.element.dataset.reducedMotion = String(this.reducedMotion.matches);

        this.renderFrame = this.renderFrame.bind(this);
        this.resize = this.resize.bind(this);
        this.syncPlayback = this.syncPlayback.bind(this);
        this.handleContextLost = this.handleContextLost.bind(this);
        this.handleMotionPreference = this.handleMotionPreference.bind(this);
        this.handleThemePreference = this.handleThemePreference.bind(this);

        this.resizeObserver = window.ResizeObserver
            ? new ResizeObserver(this.resize)
            : null;
        this.intersectionObserver = window.IntersectionObserver
            ? new IntersectionObserver((entries) => {
                this.isVisible = Boolean(entries[0] && entries[0].isIntersecting);
                this.syncPlayback();
            }, {threshold: 0.01})
            : null;
        this.themeObserver = window.MutationObserver
            ? new MutationObserver(this.handleThemePreference)
            : null;

        this.resizeObserver?.observe(this.element);
        this.intersectionObserver?.observe(this.element);
        this.themeObserver?.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-theme']
        });
        document.addEventListener('visibilitychange', this.syncPlayback);
        this.reducedMotion.addEventListener?.('change', this.handleMotionPreference);
        this.colorScheme.addEventListener?.('change', this.handleThemePreference);
        window.addEventListener('resize', this.resize);

        this.initialize();
    }

    initialize() {
        if (this.isDestroyed || this.renderer) return;

        try {
            const renderer = new Renderer({
                dpr: Math.min(window.devicePixelRatio || 1, 2),
                alpha: true,
                depth: false,
                antialias: false,
                powerPreference: 'low-power'
            });
            const gl = renderer.gl;
            gl.clearColor(0, 0, 0, 0);
            gl.canvas.setAttribute('aria-hidden', 'true');
            gl.canvas.addEventListener('webglcontextlost', this.handleContextLost);

            const flips = originToFlip(this.options.origin);
            const uniforms = {
                iTime: {value: 0},
                iResolution: {value: [1, 1]},
                iSpeed: {value: this.options.speed},
                iRayColor1: {value: hexToRgb(this.options.rayColor1)},
                iRayColor2: {value: hexToRgb(this.options.rayColor2)},
                iIntensity: {value: this.options.intensity},
                iSpread: {value: this.options.spread},
                iFlipX: {value: flips[0]},
                iFlipY: {value: flips[1]},
                iTilt: {value: this.options.tilt},
                iSaturation: {value: this.options.saturation},
                iBlend: {value: this.options.blend},
                iFalloff: {value: this.options.falloff},
                iOpacity: {value: this.options.opacity}
            };
            const geometry = new Triangle(gl);
            const program = new Program(gl, {
                vertex: VERTEX_SHADER,
                fragment: FRAGMENT_SHADER,
                uniforms,
                cullFace: false,
                depthTest: false,
                depthWrite: false
            });

            this.renderer = renderer;
            this.uniforms = uniforms;
            this.mesh = new Mesh(gl, {geometry, program});
            this.element.replaceChildren(gl.canvas);
            this.element.dataset.webgl = 'ready';
            this.resize();
            this.syncPlayback();
        } catch (error) {
            this.element.dataset.webgl = 'unavailable';
            console.warn('Side rays WebGL effect is unavailable; using the static background.', error);
        }
    }

    resize() {
        if (!this.renderer || !this.uniforms) return;
        const width = this.element.clientWidth;
        const height = this.element.clientHeight;
        if (!width || !height) return;

        this.renderer.dpr = Math.min(window.devicePixelRatio || 1, 2);
        this.renderer.setSize(width, height);
        this.uniforms.iResolution.value = [
            this.renderer.gl.canvas.width,
            this.renderer.gl.canvas.height
        ];

        if (this.reducedMotion.matches) this.renderOnce();
    }

    handleMotionPreference() {
        this.element.dataset.reducedMotion = String(this.reducedMotion.matches);
        this.syncPlayback();
    }

    handleThemePreference() {
        this.update(THEME_RAY_COLORS[resolveTheme(this.colorScheme.matches)]);
    }

    syncPlayback() {
        const shouldAnimate =
            !this.isDestroyed &&
            this.renderer &&
            this.isVisible &&
            !document.hidden &&
            !this.reducedMotion.matches;

        if (shouldAnimate) {
            if (!this.animationFrame) {
                this.animationFrame = requestAnimationFrame(this.renderFrame);
            }
            return;
        }

        this.pause();
        if (this.renderer && this.isVisible && !document.hidden) this.renderOnce();
    }

    renderFrame(time) {
        this.animationFrame = 0;
        if (!this.renderer || !this.uniforms || !this.mesh || this.isDestroyed) return;

        this.uniforms.iTime.value = time * 0.001;
        this.renderer.render({scene: this.mesh});
        this.syncPlayback();
    }

    renderOnce() {
        if (!this.renderer || !this.uniforms || !this.mesh || this.isDestroyed) return;
        this.uniforms.iTime.value = 0;
        this.renderer.render({scene: this.mesh});
    }

    pause() {
        if (!this.animationFrame) return;
        cancelAnimationFrame(this.animationFrame);
        this.animationFrame = 0;
    }

    update(options = {}) {
        this.options = {...this.options, ...options};
        if (!this.uniforms) return;
        const flips = originToFlip(this.options.origin);
        this.uniforms.iSpeed.value = this.options.speed;
        this.uniforms.iRayColor1.value = hexToRgb(this.options.rayColor1);
        this.uniforms.iRayColor2.value = hexToRgb(this.options.rayColor2);
        this.uniforms.iIntensity.value = this.options.intensity;
        this.uniforms.iSpread.value = this.options.spread;
        this.uniforms.iFlipX.value = flips[0];
        this.uniforms.iFlipY.value = flips[1];
        this.uniforms.iTilt.value = this.options.tilt;
        this.uniforms.iSaturation.value = this.options.saturation;
        this.uniforms.iBlend.value = this.options.blend;
        this.uniforms.iFalloff.value = this.options.falloff;
        this.uniforms.iOpacity.value = this.options.opacity;
        if (this.reducedMotion.matches) this.renderOnce();
    }

    handleContextLost() {
        this.pause();
        this.element.dataset.webgl = 'unavailable';
    }

    destroy() {
        if (this.isDestroyed) return;
        this.isDestroyed = true;
        this.pause();
        this.resizeObserver?.disconnect();
        this.intersectionObserver?.disconnect();
        this.themeObserver?.disconnect();
        document.removeEventListener('visibilitychange', this.syncPlayback);
        this.reducedMotion.removeEventListener?.('change', this.handleMotionPreference);
        this.colorScheme.removeEventListener?.('change', this.handleThemePreference);
        window.removeEventListener('resize', this.resize);

        const canvas = this.renderer?.gl.canvas;
        canvas?.removeEventListener('webglcontextlost', this.handleContextLost);
        canvas?.remove();
        this.renderer = null;
        this.uniforms = null;
        this.mesh = null;
    }
}

const sideRaysElement = document.getElementById('side-rays');
if (sideRaysElement) {
    const sideRays = new SideRays(sideRaysElement, optionsFromDataset(sideRaysElement));
    window.eriiSideRays = sideRays;
    window.addEventListener('pagehide', (event) => {
        if (event.persisted) {
            sideRays.pause();
        } else {
            sideRays.destroy();
        }
    });
    window.addEventListener('pageshow', (event) => {
        if (event.persisted) sideRays.syncPlayback();
    });
}

export {SideRays};
