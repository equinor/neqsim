---
name: neqsim_in_writing
description: |
  Author scientifically rigorous chapters and papers where every quantitative
  claim is backed by a NeqSim simulation, regression test, or notebook. Covers
  the dual-boot setup cell, claim → test linkage, equation → Java method
  cross-references, units enforcement against `nomenclature.yaml`, and
  notebook-driven figure / results-table injection.
---

# NeqSim integration for scientific writing

This skill is loaded by `book_author.agent` and `solve.task.agent` whenever a
chapter or paper section must defend a numerical statement with a NeqSim
artifact. It complements `book_creation` (which handles structural authoring)
and `journal_formatting` (which handles paper layout).

The objective is **traceability**: a reader (or reviewer) following any number,
table, or figure in the manuscript must be able to land on a runnable NeqSim
artifact in the repo within one click.

---

## 1. Standard chapter setup cell (Jupyter)

Every chapter notebook **must** start with the dual-boot cell. It tries the
local devtools build first (so an editor sees fresh code) and falls back to
the published `neqsim` PyPI package on Colab / external readers.

```python
import importlib, subprocess, sys

try:
    from neqsim_dev_setup import neqsim_init, neqsim_classes
    ns = neqsim_init(recompile=False)
    ns = neqsim_classes(ns)
    NEQSIM_MODE = "devtools"
except Exception:
    try:
        import neqsim
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "neqsim"])
    from neqsim import jneqsim
    NEQSIM_MODE = "pip"

print(f"NeqSim loaded via {NEQSIM_MODE}")
```

Canonical imports inside the chapter body:

```python
from neqsim import jneqsim
SystemSrkEos     = jneqsim.thermo.system.SystemSrkEos
SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations
ProcessSystem    = jneqsim.process.processmodel.ProcessSystem
```

> **Never** instantiate JPype classes ad-hoc with `JClass("...")` inside book
> chapters — readers cannot easily spot the class. Use the named bindings above.

---

## 2. Claim → test linkage

Every assertion in prose that contains a number, equation, or qualitative
behavioral statement *that depends on simulation* MUST carry a comment marker
linking it to a JUnit test or a regression baseline.

```markdown
The pressure dependency of the SRK alpha function is monotonic in T_r
between 0.7 and 1.3 [^claim1].

<!-- @neqsim:claim
  test: src/test/java/neqsim/thermo/component/AlphaFunctionMonotonicityTest.java
  baseline: src/test/resources/baselines/srk_alpha_envelope.json
-->
```

The `book-check` accessibility/consistency gate parses these blocks and warns
when:

* the referenced test file is missing,
* the baseline JSON is missing,
* the test does not contain the claim's numeric tolerance.

When asked to write a new claim:

1. Search for an existing test that already validates the claim
   (`grep_search` on the formula or expected behavior).
2. If none exists, generate a new JUnit 5 test under `src/test/java/...` and
   add the baseline to `src/test/resources/baselines/<topic>.json`.
3. Insert the `@neqsim:claim` block immediately after the sentence.

---

## 3. Equation → Java method cross-references

When introducing an equation that NeqSim implements numerically, attach a
pointer to the Java method so readers can audit the numerics:

```markdown
The Soave alpha function

$$ \alpha(T_r, \omega) = [1 + m(\omega)(1 - \sqrt{T_r})]^2 $$
<!-- @neqsim:eq method=neqsim.thermo.component.ComponentSrk#calcAlpha -->
```

These markers are NOT rendered visibly. They are picked up by the
`book-render-html` post-processor to attach a small source-link footnote in
the HTML/EPUB output and to populate the back-of-book "Index of NeqSim
methods" appendix.

---

## 4. Notebook-driven figures and tables

### 4a. Figures

Generate every chapter figure inside the chapter notebook, save to
`<chapter_dir>/figures/`, and insert into `chapter.md` with **non-empty alt
text** (the accessibility gate enforces this):

```markdown
![Pressure–temperature envelope of a 70/30 mol% methane–ethane mixture
calculated with SRK EOS, showing the cricondentherm at 235 K and 4.6 MPa.](
  figures/pt_envelope_methane_ethane.png)
<!-- @neqsim:figure source=01_phase_envelope.ipynb cell=12 -->
```

The alt text must describe what the figure *shows scientifically*, not just
"phase envelope". A blind reviewer should be able to grasp the conclusion
from alt text alone.

### 4b. Auto-injected results tables

Tag a notebook cell that produces a pandas DataFrame for direct inclusion
into the chapter. The `book-build` pipeline will replace the matching
marker in `chapter.md` on each run:

In the notebook:

```python
results_df  # last expression in the cell
# tag the cell metadata: { "neqsim": { "table_id": "srk_validation" } }
```

In `chapter.md`:

```markdown
<!-- @neqsim:table id=srk_validation source=01_phase_envelope.ipynb -->
<!-- @neqsim:table-end -->
```

Everything between the markers is regenerated from the notebook output. Hand
edits inside the block will be silently overwritten — put narrative *outside*
the block.

---

## 5. Units enforcement

Each book has a `nomenclature.yaml` listing every symbol with its SI unit.
Whenever a chapter writes `T = 300 K` or `P = 60 bar`, a build-time linter
checks that:

1. the symbol appears in `nomenclature.yaml`,
2. the unit is consistent with the SI base unit declared there,
3. NeqSim getter/setter calls in the chapter notebook use the matching unit
   string (e.g., `fluid.getDensity("kg/m3")`, `setFlowRate(value, "kg/hr")`).

When introducing a new symbol, add it to `nomenclature.yaml` BEFORE writing
the prose. NeqSim default units are documented in
[`docs/development/CODE_PATTERNS.md`](../../docs/development/CODE_PATTERNS.md).

Common defaults:

| Quantity | NeqSim API default | Allowed strings |
|----------|-------------------|------------------|
| Temperature (constructor) | K | `"K"`, `"C"` |
| Pressure (constructor) | bara | `"bara"`, `"barg"`, `"Pa"`, `"MPa"` |
| Flow rate | mol/sec | `"kg/hr"`, `"kg/sec"`, `"Sm3/hr"`, `"MSm3/day"` |
| Density | kg/m³ | `"kg/m3"`, `"mol/m3"` |
| Viscosity | kg/(m·s) | `"kg/msec"`, `"cP"` |

---

## 6. Regression baselines tied to chapter examples

When a chapter walks through a worked example, capture its output as a
regression baseline so that EOS code changes that break the example
will fail CI. The corresponding test goes in
`src/test/java/neqsim/book/<book_slug>/`:

```java
// src/test/java/neqsim/book/cpa_eos_2026/Chapter03Example2Test.java
@Test
void chapter3Example2_methaneEthaneEnvelope_matchesBaseline() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.3);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.calcPTphaseEnvelope(true, 1.0);

    BaselineComparator.assertMatch(
        "src/test/resources/baselines/book/cpa_eos_2026/ch03_ex2_envelope.json",
        ops, tolerance(0.5, "K"), tolerance(2.0, "%"));
}
```

If the example is dropped or rewritten, the test must be retired in the
same PR — orphan baselines fail the consistency gate.

---

## 7. Citation hygiene for NeqSim-derived results

When citing a NeqSim-computed value in prose:

* If the value is generated *for this manuscript*, cite the chapter's
  notebook by its DOI-able artifact path (e.g., a Zenodo deposit) or
  the repo commit hash.
* If the value is reproduced from an earlier NeqSim publication, cite
  the paper, *not* the code repository.
* Never cite "NeqSim software" without specifying version and EOS.

The `book-enrich-bib` command validates external DOIs via Crossref but
does **not** invent DOIs for software. For software citations use the
CITATION.cff file at the repo root.

---

## 8. Workflow summary (per chapter)

1. `paperflow.py book-add-chapter <book_dir> --title "..."`
2. Author the notebook in `<chapter_dir>/notebooks/`. Tag figure/table
   cells with `neqsim` metadata.
3. Run the notebook (`paperflow.py book-run-notebooks <book_dir> --chapter <slug>`).
4. Write prose in `chapter.md`, inserting figure/table/claim markers.
5. Run the consistency + accessibility checks
   (`paperflow.py book-check <book_dir>`).
6. Live-preview iteratively: `paperflow.py book-preview <book_dir>`.
7. Validate citations: `paperflow.py book-enrich-bib <book_dir>`.
8. Render the final formats: `paperflow.py book-render <book_dir> --format pdf`
   (and `--format epub`, `--format docx` as required by the publisher).
9. Add a JUnit baseline test for every worked example.

---

## 9. Anti-patterns (will fail review)

* **Numbers without provenance** — every numeric in prose must trace to a
  notebook cell or a JUnit test.
* **`var`, `List.of`, text blocks** in any Java snippet shown in the book
  (Java 8 only — see `neqsim-java8-rules`).
* **Bare `JClass("...")` calls** in book code — readers will not find the
  class. Use named bindings (§1).
* **Editing inside `@neqsim:table` blocks** — overwritten on next build.
* **Generic alt text** ("Figure 3", "Plot of results") — fails accessibility
  gate.
* **Citing software without a version** — both reviewers and CI will reject.

---

## 10. Hand-off to other agents

* `book_author.agent` — owns chapter prose; calls this skill to insert claim
  markers and to choose units.
* `solve.task.agent` — owns notebook code; produces tagged figure/table cells
  whose markers this skill consumes.
* `journal_formatting` — for journal articles instead of books, the same
  `@neqsim:*` markers apply but the renderer is the paper pipeline.
