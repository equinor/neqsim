# Skill: Technical Figure Understanding

## Purpose

Read engineering figures deeply enough that a book, paper, or report can explain
what the figure shows and why it matters. This skill complements
`figure-discussion`: this skill extracts and classifies visual evidence;
`figure-discussion` turns that evidence into observation, mechanism,
implication, and recommendation prose.

Use this skill for lecture slides, notebook plots, process diagrams, flow maps,
equipment sketches, case-study layouts, decision matrices, and source-document
figures that need to be placed into textbook or engineering-report context.

## Inputs

For every figure, gather as many of these inputs as available:

| Input | Examples |
|-------|----------|
| Image file | `figures/ch04_s06_ipr_vlp.png` |
| Existing caption or alt text | Markdown image alt text, `results.json.figure_captions` |
| Source metadata | lecture folder, PPTX slide, PDF page, notebook path, generated script |
| Surrounding text | preceding and following chapter paragraphs |
| Chapter plan | `chapter_outlines.yaml`, learning objectives, section heading |
| Computed data | notebook outputs, `results.json`, CSV tables |

## Figure Classes

Classify the figure before writing about it:

| Class | What to extract |
|-------|-----------------|
| Plot | axes, units, scales, legend, trends, extrema, intersections, thresholds |
| Flow map | axes, operating points, regime boundaries, transition risks |
| Process diagram | unit operations, stream directions, recycle loops, control signals |
| Layout/map | distance, route, host/tieback relationships, bottlenecks |
| Decision matrix | options, criteria, dominant trade-off, ranking |
| Equipment sketch | internals, dimensions, physical function, design constraint |
| Table screenshot | row/column headers, numeric values, units, caveats |
| Cover/decorative art | topic signalling only; usually exempt from discussion gate |
| Photo/corporate image | provenance and rights check before inclusion |

## Extraction Checklist

For each figure, record:

1. `visual_type`: one of the classes above.
2. `visible_text`: OCR or manual transcription of labels and annotations.
3. `axes`: label, unit, and scale for each axis if applicable.
4. `data_readout`: quantitative values that are visibly supported.
5. `trend_summary`: the dominant trend or comparison.
6. `chapter_section`: the section where the figure belongs.
7. `learning_objective_links`: learning objectives supported by the figure.
8. `source`: file, slide/page, notebook, or generated script.
9. `confidence`: `high`, `medium`, or `low`.
10. `review_status`: `draft`, `needs-human-check`, `approved`, or `exempt`.

## Safety Rules

- Do not invent numbers from a low-resolution plot. If values are not readable,
  say the figure qualitatively shows the trend.
- Use OCR/vision output as evidence candidates, not as final truth.
- Prefer notebook data or `results.json` over eyeballing plot values.
- Preserve provenance. A useful figure without source metadata is not release-ready.
- Mark cover art, decorative images, and photo-like corporate slides as `exempt`
  or `needs-rights-check` instead of forcing technical interpretation.
- Do not claim standards compliance unless the figure or surrounding text cites
  the standard explicitly.

## Dossier Schema

Every figure-understanding pass should write a dossier record:

```json
{
  "figure": "ch04_s06_ipr_vlp.png",
  "figure_path": "chapters/ch04_flow_performance_production_systems/figures/ch04_s06_ipr_vlp.png",
  "source": "notebook",
  "source_file": "chapters/ch04_flow_performance_production_systems/notebooks/ch04_s06_ipr_vlp.ipynb",
  "chapter": "ch04_flow_performance_production_systems",
  "section": "4.3 Vertical-Lift Performance",
  "visual_type": "plot",
  "visible_text": ["IPR", "VLP", "Operating point"],
  "axes": [
    {"label": "Gas rate", "unit": "kSm3/d", "scale": "linear"},
    {"label": "Bottom-hole pressure", "unit": "bara", "scale": "linear"}
  ],
  "data_readout": ["Operating point: 586 kSm3/d, 161 bara"],
  "caption": "IPR and VLP intersection for a gas well.",
  "observation": "...",
  "mechanism": "...",
  "implication": "...",
  "recommendation": "...",
  "linked_learning_objectives": ["LO1", "LO5"],
  "confidence": "high",
  "review_status": "needs-human-check"
}
```

## Discussion Handoff

After extracting evidence, hand the record to the `figure-discussion` skill.
The final manuscript block must appear immediately after the figure unless the
figure is exempt cover art.

Minimum block:

```markdown
**Discussion (Figure 4.2).**
*Observation.* State the supported visual result with numbers or a clear
qualitative reading.
*Mechanism.* Explain the physics, engineering logic, equation, or NeqSim class
behind the result.
*Implication.* Explain what the result means for design, operation, economics,
or student reasoning.
*Recommendation.* Give a specific action tied to the figure.
```

## Review Rubric

Before approving a figure dossier, check:

- The figure is in the right chapter and section.
- Caption is standalone and not filename-like.
- Units are visible or supplied by notebook/source data.
- Any numbers in the discussion are traceable to the figure or data.
- The recommendation is supported by the figure, not by unrelated knowledge.
- Source/provenance is sufficient for release.
