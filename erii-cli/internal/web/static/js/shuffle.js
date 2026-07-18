(function (global) {
    'use strict';

    const DEFAULTS = Object.freeze({
        text: '',
        shuffleDirection: 'right',
        duration: 0.35,
        shuffleTimes: 1,
        animationMode: 'evenodd',
        stagger: 0.03,
        maxDelay: 0,
        maxStaggerDelay: 0.42,
        scrambleCharset: '',
        ease: 'power3.out',
        colorFrom: '',
        colorTo: '',
        respectReducedMotion: true,
        triggerOnHover: true,
        onShuffleComplete: null
    });

    const EASINGS = Object.freeze({
        'power3.out': 'cubic-bezier(0.22, 1, 0.36, 1)',
        'power2.out': 'cubic-bezier(0.25, 1, 0.5, 1)',
        linear: 'linear'
    });

    function randomGlyph(charset, fallback) {
        if (!charset) return fallback;
        return charset.charAt(Math.floor(Math.random() * charset.length)) || fallback;
    }

    class Shuffle {
        constructor(element, options = {}) {
            if (!element) throw new Error('Shuffle requires a target element');

            this.element = element;
            this.options = {...DEFAULTS, ...options};
            this.text = String(this.options.text || element.textContent || '');
            this.animations = [];
            this.runId = 0;
            this.isPlaying = false;
            this.reducedMotion = global.matchMedia?.('(prefers-reduced-motion: reduce)') || null;

            this.handleHover = this.handleHover.bind(this);
            if (this.options.triggerOnHover) {
                this.element.addEventListener('mouseenter', this.handleHover);
            }

            this.renderStill();
        }

        setText(text) {
            const nextText = String(text || '');
            if (nextText === this.text) return;
            this.stopAnimations();
            this.text = nextText;
            this.renderStill();
        }

        handleHover() {
            if (!this.isPlaying) this.play();
        }

        stopAnimations() {
            this.runId += 1;
            this.animations.forEach((animation) => animation.cancel());
            this.animations = [];
            this.isPlaying = false;
        }

        renderStill() {
            this.element.textContent = this.text;
            this.element.classList.add('is-ready');
        }

        buildStrips() {
            const fragment = document.createDocumentFragment();
            const wrappers = [];
            const rolls = Math.max(1, Math.floor(Number(this.options.shuffleTimes) || 1));
            const direction = this.options.shuffleDirection;
            const reverse = direction === 'right' || direction === 'down';

            this.text.split('\n').forEach((line) => {
                const lineElement = document.createElement('span');
                lineElement.className = 'shuffle-line';

                Array.from(line).forEach((character) => {
                    if (character === ' ') {
                        const space = document.createElement('span');
                        space.className = 'shuffle-space';
                        space.textContent = ' ';
                        lineElement.appendChild(space);
                        return;
                    }

                    const wrapper = document.createElement('span');
                    wrapper.className = 'shuffle-char-wrapper';

                    const strip = document.createElement('span');
                    strip.className = 'shuffle-char-strip';

                    const glyphs = [];
                    if (reverse) glyphs.push(character);
                    glyphs.push(randomGlyph(this.options.scrambleCharset, character));
                    for (let index = 0; index < rolls; index += 1) {
                        glyphs.push(randomGlyph(this.options.scrambleCharset, character));
                    }
                    if (!reverse) glyphs.push(character);

                    glyphs.forEach((glyph) => {
                        const cell = document.createElement('span');
                        cell.className = 'shuffle-char';
                        cell.textContent = glyph;
                        strip.appendChild(cell);
                    });

                    const steps = glyphs.length - 1;
                    let startTransform;
                    let finalTransform;
                    if (direction === 'left') {
                        startTransform = 'translate3d(0, 0, 0)';
                        finalTransform = `translate3d(-${steps}ch, 0, 0)`;
                    } else if (direction === 'up') {
                        startTransform = 'translate3d(0, 0, 0)';
                        finalTransform = `translate3d(0, -${steps}em, 0)`;
                        wrapper.classList.add('is-vertical');
                        strip.classList.add('is-vertical');
                    } else if (direction === 'down') {
                        startTransform = `translate3d(0, -${steps}em, 0)`;
                        finalTransform = 'translate3d(0, 0, 0)';
                        wrapper.classList.add('is-vertical');
                        strip.classList.add('is-vertical');
                    } else {
                        startTransform = `translate3d(-${steps}ch, 0, 0)`;
                        finalTransform = 'translate3d(0, 0, 0)';
                    }

                    strip.style.transform = startTransform;
                    wrapper.appendChild(strip);
                    lineElement.appendChild(wrapper);
                    wrappers.push({strip, startTransform, finalTransform});
                });

                fragment.appendChild(lineElement);
            });

            this.element.replaceChildren(fragment);
            return wrappers;
        }

        delayFor(index, count) {
            const stagger = Math.max(0, Number(this.options.stagger) || 0);
            const maxStaggerDelay = Math.max(0, Number(this.options.maxStaggerDelay) || 0);

            if (this.options.animationMode === 'random') {
                return Math.random() * Math.max(0, Number(this.options.maxDelay) || 0);
            }

            const oddCount = Math.floor(count / 2);
            const groupIndex = Math.floor(index / 2);
            const oddTotal = Number(this.options.duration) + Math.max(0, oddCount - 1) * stagger;
            const evenStart = oddCount ? oddTotal * 0.7 : 0;
            const delay = index % 2 === 1
                ? groupIndex * stagger
                : evenStart + groupIndex * stagger;
            return maxStaggerDelay ? Math.min(delay, maxStaggerDelay) : delay;
        }

        play() {
            this.stopAnimations();
            const runId = this.runId;

            if (!this.text) {
                this.renderStill();
                return Promise.resolve();
            }

            if (this.options.respectReducedMotion && this.reducedMotion?.matches) {
                this.renderStill();
                this.options.onShuffleComplete?.();
                return Promise.resolve();
            }

            this.isPlaying = true;
            const wrappers = this.buildStrips();
            this.element.classList.add('is-ready');
            const duration = Math.max(0, Number(this.options.duration) || 0) * 1000;
            const easing = EASINGS[this.options.ease] || this.options.ease || EASINGS['power3.out'];

            this.animations = wrappers.map((wrapper, index) => {
                const delay = this.delayFor(index, wrappers.length) * 1000;
                const from = {transform: wrapper.startTransform};
                const to = {transform: wrapper.finalTransform};
                if (this.options.colorFrom) from.color = this.options.colorFrom;
                if (this.options.colorTo) to.color = this.options.colorTo;

                return wrapper.strip.animate([from, to], {
                    duration,
                    delay,
                    easing,
                    fill: 'forwards'
                });
            });

            return Promise.allSettled(this.animations.map((animation) => animation.finished))
                .then(() => {
                    if (runId !== this.runId) return;
                    this.animations = [];
                    this.isPlaying = false;
                    this.renderStill();
                    this.options.onShuffleComplete?.();
                });
        }

        cancel() {
            this.stopAnimations();
            this.renderStill();
        }

        destroy() {
            this.stopAnimations();
            this.element.removeEventListener('mouseenter', this.handleHover);
            this.renderStill();
        }
    }

    global.Shuffle = Shuffle;
})(window);
