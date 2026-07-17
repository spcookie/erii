# Erii Nerd Icon Subset

`EriiNerdIcons.woff2` is generated from
`JetBrainsMonoNerdFontMono-Regular.ttf` in Nerd Fonts v3.4.0.

Included glyphs:

- `U+EE9C` brain
- `U+F0C0` users
- `U+F15C` file lines
- `U+F03E` image
- `U+EDE2` open book
- `U+F086` comments
- `U+F201` chart line
- `U+F017` clock
- `U+F0CE` table
- `U+F14E` compass
- `U+EFCE` project diagram
- `U+F1DA` history
- `U+F0C6` paperclip
- `U+F21E` heartbeat
- `U+EF30` water
- `U+F0E7` bolt

Regenerate from the project root:

```bash
curl -fL \
  -o /tmp/JetBrainsMono-NerdFont.tar.xz \
  https://github.com/ryanoasis/nerd-fonts/releases/download/v3.4.0/JetBrainsMono.tar.xz

tar -xJf /tmp/JetBrainsMono-NerdFont.tar.xz \
  -C /tmp \
  JetBrainsMonoNerdFontMono-Regular.ttf

uvx --from 'fonttools[woff]' pyftsubset \
  /tmp/JetBrainsMonoNerdFontMono-Regular.ttf \
  --unicodes='U+EE9C,U+F0C0,U+F15C,U+F03E,U+EDE2,U+F086,U+F201,U+F017,U+F0CE,U+F14E,U+EFCE,U+F1DA,U+F0C6,U+F21E,U+EF30,U+F0E7' \
  --flavor=woff2 \
  --output-file=erii-cli/internal/web/static/fonts/EriiNerdIcons.woff2 \
  --layout-features='' \
  --no-hinting
```

JetBrains Mono is licensed under the SIL Open Font License 1.1. Nerd Fonts
project and glyph licensing information is available at
https://github.com/ryanoasis/nerd-fonts/.
