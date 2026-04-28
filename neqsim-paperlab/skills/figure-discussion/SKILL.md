# Skill: Figure Discussion (observation → mechanism → implication → recommendation)

## When to Use

USE WHEN: writing the discussion of a figure in a paper, book chapter, or
task report. Every figure that appears in a deliverable MUST have an
accompanying discussion block that follows this contract. Loaded by
`scientific-writer`, `book-author`, `narrative-framer`, and the
`solve.task.agent` workflow.

DO NOT USE for: figure generation (use `generate_publication_figures`),
captions only (captions are a separate, terser artefact).

---

## Core principle

A figure that appears in a deliverable without a corresponding discussion
block is a wasted figure. Reviewers and readers should be able to read
the figure → discussion pair and walk away knowing **what the figure
shows, why it shows that, what it means, and what to do about it**, in
that order, without having to reverse-engineer the conclusion from the
plot.

This skill ports the contract from `task_solve/` `results.json`
`figure_discussion[]` blocks to the paper / book authoring pipeline.

---

## The four-part contract

Every figure discussion block has exactly four parts. Each is one to
three sentences. The block lives **immediately after** the figure in
the manuscript (paper.md or chapter.md) or in the corresponding
`results.json` field.

### 1. Observation — what the figure shows

State what the figure shows, with **numbers**. Do not describe the
plot ("a downward-sloping line"); describe the result ("the iteration
count drops from 28 to 16 as `n_components` increases from 4 to 12").

Rules:
- Always include at least one number with its unit.
- Always state the trend in scientific, not graphical, terms.
- Reference the figure number explicitly.

### 2. Mechanism — why it shows that

Explain the underlying physics, numerics, or algorithm. Tie back to
an equation from the methods section or a NeqSim Java class.

Rules:
- Reference an equation by number (Eq. 7) or a Java class
  (`SystemSrkEos`).
- One sentence on the dominant mechanism is enough; do not
  hand-wave with multiple competing explanations.
- If the mechanism is unclear, say so — do not invent one.

### 3. Implication — what it means for the reader

State the consequence for someone using the method, the model, or
the equipment. The reader should be able to act on this.

Rules:
- Phrase as engineering or scientific consequence, not as plot
  feature.
- Connect to the paper's claims list (Type 1) or to a primary
  finding (Type 2).
- Quantify the implication wherever possible.

### 4. Recommendation — what to do about it

A specific action. "Use SRK with classic mixing rule for systems
under 50 bara" beats "results are sensitive to mixing rule".

Rules:
- Active voice, imperative mood.
- Concrete: a parameter range, a method choice, a check to perform.
- Tied to the figure — do not recommend something the figure does
  not support.

---

## Block format (paper.md or chapter.md)

Insert immediately after the figure reference:

```markdown
![Iteration count vs number of components for SRK and PR EOS,
showing PR converging in 30 % fewer iterations on average for
mixtures with more than 8 components.](figures/iters_vs_nc.png)

**Discussion (Figure 3).**
*Observation.* Iteration count drops from 28 to 16 as `n_components`
rises from 4 to 12 for both SRK and PR, with PR consistently 30 %
lower (Figure 3).
*Mechanism.* The PR alpha function (Eq. 7) penalises low reduced
temperatures more strongly, accelerating the early Wegstein steps in
multicomponent flashes.
*Implication.* For pipeline-grade rich gas with $n_c > 8$, PR delivers
the same density accuracy at appreciably lower CPU cost than SRK.
*Recommendation.* Use PR over SRK for gas-processing flowsheets with
rich-gas streams; reserve SRK for the cubic-only legacy modules where
parity with historical results is required.
```

---

## results.json schema

The same content goes into `results.json` so downstream tooling
(consistency checker, traceability validator) can audit the
figure→claim chain:

```json
"figure_discussion": [
  {
    "figure": "iters_vs_nc.png",
    "title": "Iteration count vs number of components",
    "observation": "Iteration count drops from 28 to 16 as n_components rises from 4 to 12; PR consistently 30 % below SRK.",
    "mechanism": "PR alpha function (Eq. 7) penalises low reduced temperatures more strongly, accelerating early Wegstein steps.",
    "implication": "PR delivers the same density accuracy at lower CPU cost than SRK for rich-gas streams with nc > 8.",
    "recommendation": "Use PR for gas-processing flowsheets with rich-gas streams; keep SRK only for legacy parity.",
    "linked_results": ["median_iterations", "convergence_rate_pr_vs_srk"],
    "linked_claim_id": "C1",
    "neqsim_classes": ["SystemPrEos", "SystemSrkEos"],
    "equation_refs": ["Eq. 7"]
  }
]
```

Required keys (always):
- `figure` — filename in `figures/`
- `observation`, `mechanism`, `implication`, `recommendation` — each ≤ 3 sentences

Optional keys (include when applicable):
- `linked_results` — keys in `results.json["key_results"]` (required for task reports)
- `linked_claim_id` — claim ID from `approved_claims.json` (required for Type 1 papers only; omit for book chapters and Type 2 deliverables that have no claims manifest)
- `neqsim_classes` — Java classes used (for traceability; recommended whenever a NeqSim class produced the figure)
- `equation_refs` — equation numbers in the paper (omit for book chapters that do not number equations)

---

## Anti-patterns

The adversarial reviewer flags these. Do not produce them.

| Bad | Why | Fix |
|-----|-----|-----|
| "Figure 3 shows the trend." | No numbers, no units. | Quote the trend with values and units. |
| "The result is consistent with literature." | Vague comparison. | Cite specific values: "consistent with the 10–15 % reported by \cite{X}". |
| "More work is needed." | Filler. | Either write a falsifiable prediction or delete. |
| "As expected, X happened." | Hides the mechanism. | State the expectation source and the mechanism. |
| Recommendation is the title. | "Use the proposed method." | Recommend a *parameter range* or *operating regime*. |
| Discussion before figure. | Reader cannot follow. | Discussion comes immediately after the figure. |

---

## Length budget

| Item | Budget |
|------|--------|
| Observation | ≤ 60 words |
| Mechanism | ≤ 50 words |
| Implication | ≤ 50 words |
| Recommendation | ≤ 30 words |
| Total per figure | ≤ 180 words |

Top journals expect 4–7 main figures. At ≤ 180 words each, the
figure-discussion total is ≤ 1300 words — about half of a typical
discussion section. The remaining half is the cross-figure synthesis
(`narrative-framer` skill territory).

---

## Validation

`paperflow.py iterate --check evidence` audits:
- Every figure in `paper.md` / `chapter.md` has a discussion block.
- Every block has all four parts.
- `linked_claim_id` exists in `approved_claims.json` (Type 1 papers only — skipped when no claims manifest is present, e.g. book chapters, Type 2 task reports).
- `linked_results` keys exist in `results.json["key_results"]` when `results.json` is present.
- `neqsim_classes` strings match real classes in `src/main/java/` when supplied.
- No anti-pattern phrases (configurable list).

A failing check is a `BLOCKER` for `journal-formatter` packaging (Type 1). For book builds and task reports, a failing check is a `WARNING` — the build proceeds but the issue is logged for the next iteration.

---

## How this skill plugs into the agents

| Agent | What it does with this skill |
|-------|------------------------------|
| `figure-generator` | Returns observation candidates alongside the PNG. |
| `scientific-writer` | Drafts the four-part block from observation candidates + claims_manifest. |
| `narrative-framer` | Tightens prose; does not change content. |
| `book-author` | Same contract for chapter figures; uses `linked_claim_id` only when claim manifest is present. |
| `adversarial-reviewer` | Audits axis 6 (figures) against this skill. |
| `solve.task.agent` | Already uses the same schema in `task_solve/.../results.json`. |
