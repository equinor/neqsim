---
name: paperlab_figure_accessibility_style
description: |
  Audit PaperLab figures for accessibility, readability, engineering plotting
  conventions, print safety, and style consistency across chapters.
---

# PaperLab Figure Accessibility Style

## When to Use

USE WHEN: a book has many figures, generated plots, process diagrams, maps, or
technical images that must be readable in HTML, PDF, projection, and print.

Pair with:

- `technical_figure_understanding` for content interpretation,
- `figure_discussion` for teaching context,
- `generate_publication_figures` for regeneration.

## Checks

| Area | Ready Signal |
|------|--------------|
| axes | labels include quantity and unit |
| legend | readable and does not cover data |
| color | colorblind-safe and print-safe |
| text | readable at book scale |
| caption | states what the figure shows and why it matters |
| provenance | source notebook, script, or document is known |
| style | line widths, fonts, and palettes are consistent |

## Figure Classes

- teaching figure,
- evidence figure,
- schematic,
- process diagram,
- map or layout,
- decorative support.

## Output Schema

```json
{
  "figure": "figures/ch20_optimization_frontier.png",
  "class": "evidence figure",
  "status": "minor-revision",
  "issues": ["legend overlaps high-value data", "caption lacks engineering implication"],
  "recommended_fix": "Move legend outside plot and add one sentence on the trade-off."
}
```

## Safety Rules

- Do not change scientific meaning for style consistency.
- Preserve original source figures when provenance requires exact reproduction.
- Keep accessibility fixes compatible with grayscale PDF.