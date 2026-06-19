# KTE Template Structure Modernization

## Goal

Modernize `usage-template.kte` and `group-status.kte` without changing their rendered layout, dimensions, spacing, colors, chart behavior, or report data.

## Scope

### JavaScript

- Replace function-scoped `var` declarations with `const` or `let` according to reassignment.
- Prefer arrow callbacks, destructuring, template literals, object shorthand, and collection helpers where they improve clarity.
- Organize each inline script into data initialization, pure helpers, chart option builders, and rendering entry points.
- Keep scripts as classic inline scripts so the existing globally loaded ECharts, CalHeatmap, D3, and Lucide assets continue to work unchanged.
- Preserve all chart series, labels, dimensions, date calculations, empty states, and heatmap sizing behavior.

### HTML

- Use semantic document elements such as `main`, `header`, `section`, `article`, and `footer` where they express the existing structure.
- Add accessible labels to chart containers without adding visible text.
- Preserve element order, class hooks, IDs, and conditional JTE rendering behavior.

### CSS

- Consolidate reset rules, design tokens, typography, and repeated component selectors.
- Replace static inline declarations with named classes; retain dynamic JTE values as custom properties where appropriate.
- Use modern, widely supported properties and logical organization while preserving computed layout values.
- Do not introduce CSS nesting, container queries, animations, or responsive layout changes in this pass.

## Compatibility

The templates are rendered in Chromium through the existing Playwright path. The refactor targets modern Chromium-compatible ECMAScript while avoiding module-script loading changes and external build steps.

## Verification

- Add source-level regression assertions for ES declarations, semantic structure, and removal of static inline styles.
- Run focused template tests.
- Run JTE precompilation to validate template syntax.
- Run `:erii-core:compileKotlin` to validate generated template integration.

## Non-Goals

- No visual redesign or responsive behavior changes.
- No extraction into shared static JavaScript/CSS assets.
- No JTE partial/component split.
- No dependency or chart-library upgrades.
