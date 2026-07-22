---
name: paperlab_interactive_html_labs
description: |
  Design safe interactive HTML labs from PaperLab notebooks, with bounded inputs,
  static fallbacks, and student prompts. Use when turning computational examples
  into guided learning activities.
---

# PaperLab Interactive HTML Labs

## When to Use

USE WHEN: a chapter notebook can become a student-facing lab in the HTML book,
especially for sensitivity studies, process simulations, economics, or design
trade-offs.

Pair with:

- `neqsim-notebook-patterns` for executable notebook setup,
- `paperlab_student_readability` for student prompts,
- `paperlab_notebook_regression_baselines` for stable fallback outputs.

## Lab Structure

Each lab should have:

1. Objective and prerequisite note.
2. Fixed model setup.
3. Bounded inputs with units and physical limits.
4. Run or update action.
5. Result table and figure.
6. Interpretation prompts.
7. Static fallback for PDF, DOCX, and ODF.

## Manifest Schema

```json
{
  "lab_id": "ch10_teg_circulation_sensitivity",
  "notebook": "chapters/ch10/notebooks/ch10_s02_teg_contactor.ipynb",
  "inputs": [
    {"name": "teg_circulation", "unit": "m3/h", "min": 0.5, "max": 5.0, "default": 2.0}
  ],
  "outputs": ["water_dew_point_C", "reboiler_duty_kW"],
  "static_fallback": "figures/ch10_teg_sensitivity.png"
}
```

## Safety Rules

- Do not expose arbitrary code execution in public HTML.
- Bound all inputs with physical and computational limits.
- Always provide static fallbacks for noninteractive formats.
- Do not require network access for core student use.