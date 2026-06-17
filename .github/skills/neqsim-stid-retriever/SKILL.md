---
name: neqsim-stid-retriever
description: "Retrieves engineering documents (compressor curves, mechanical drawings, line lists, P&IDs, data sheets, vendor docs, material certificates, fire/PFP documents, piping specs) from document management systems for use in NeqSim engineering tasks. Supports local directories, manual upload, and pluggable retrieval backends (e.g., stidapi for STID). USE WHEN: a task needs vendor performance data, mechanical drawings, line-list route hydraulics, water-hammer route/event evidence, trapped-liquid fire rupture evidence, or as-built documentation for process equipment."
last_verified: "2026-07-04"
---

# Document Retrieval Skill for Engineering Tasks

Retrieve engineering documents (compressor curves, mechanical drawings, data
sheets, vendor reports) for use in NeqSim task-solving workflows.

For water-hammer/liquid-hammer tasks, retrieve STID/P&ID route drawings, line
lists, stress isometrics, piping specifications, valve data sheets with closure
time, pump curves or trip logs, design-pressure basis, and relevant tagreader
event exports into the task references folder.

## ⚠️ CRITICAL: All documents go INSIDE the task folder

**ALL downloaded documents — STID drawings, PI historian exports,
vendor datasheets, P&IDs, literature PDFs — MUST be saved to
`step1_scope_and_research/references/` within the task folder.**

NEVER download or save task-related files to workspace-level directories like
`output/`, `figures/`, or any path outside `task_solve/YYYY-MM-DD_slug/`.

```python
# CORRECT — saves inside the task folder:
TASK_DIR = "task_solve/YYYY-MM-DD_slug"
out_dir = os.path.join(TASK_DIR, "step1_scope_and_research", "references")

# WRONG — saves outside the task folder:
out_dir = os.path.join(os.path.dirname(__file__), "..", "figures", "stid_docs")  # NEVER
out_dir = "output/stid_docs"  # NEVER
```

**For PDF-to-PNG conversion:** Output to the task's `figures/`:
```bash
python devtools/pdf_to_figures.py task_solve/YYYY-MM-DD_slug/step1_scope_and_research/references/ \
    --outdir task_solve/YYYY-MM-DD_slug/figures/
```

This rule ensures every task is self-contained and portable.

---

This skill is **backend-agnostic** — it works with any document source:
- Documents placed manually in the task folder
- A pre-existing local directory of downloaded files
- An auto-retrieval backend (configured separately, not part of the public repo)

## Document Sources (Priority Order)

The task solver checks these sources in order:

1. **Local directory** — user provides a path to pre-downloaded docs
2. **Task references folder** — docs already in `step1_scope_and_research/references/`
3. **Retrieval backend** — auto-fetch from a document management system (if configured
   in `devtools/doc_retrieval_config.yaml` — this file is gitignored)

This means the workflow works for **everyone**:
- Users with a retrieval backend get auto-retrieval
- External users place their own PDFs in `references/` and the same pipeline runs

---

## Option A: User Provides Documents (Works for Everyone)

Place documents in the task's references folder:

```
task_solve/YYYY-MM-DD_task_slug/
└── step1_scope_and_research/
    └── references/
        ├── compressor_curves.pdf
        ├── mechanical_drawing.pdf
        └── equipment_datasheet.pdf
```

Or point to an existing directory when creating the task:

```bash
neqsim new-task "compressor analysis" --type B \
    --refs-dir "/path/to/existing/docs"
```

The task solver will automatically:
1. Detect documents in `references/`
2. Convert PDFs to PNG images (`devtools/pdf_to_figures.py`)
3. Classify each document (curves, drawings, datasheets, etc.)
4. Extract relevant data using `view_image`
5. Filter out irrelevant documents based on task type
6. Feed relevant data into analysis notebooks

## Option B: Auto-Retrieval (Requires Backend Config — Not Public)

When a retrieval backend is configured via `devtools/doc_retrieval_config.yaml`
(gitignored — never committed), the task solver can auto-fetch documents
by equipment tag. See the config template below for setup instructions.

### STID Download Helper (Recommended)

Use `devtools/stid_download.py` to download STID documents directly into a
task folder. This ensures all documents end up in the right place:

```bash
# Download documents by tag — saves to task's references/ folder
python devtools/stid_download.py --task-dir task_solve/2026-04-16_my_task \
    --inst MYINST --tags 30PT0001 30PT0002 33AI0001

# Download + convert to PNG for AI analysis
python devtools/stid_download.py --task-dir task_solve/2026-04-16_my_task \
    --inst MYINST --tags 30PT0001 --convert-png

# Download specific document numbers
python devtools/stid_download.py --task-dir task_solve/2026-04-16_my_task \
    --inst MYINST --docs E001-AS-P-XB-00001-01 E001-AS-BI000-DS-00001
```

The helper:
- Downloads PDFs to `step1_scope_and_research/references/` inside the task folder
- Saves a `stid_retrieval_manifest.json` for traceability
- Optionally converts PDFs to PNGs in the task's `figures/` directory
- Skips already-downloaded files

### Generic retrieval interface

```python
# Generic retrieval interface used by the task solver:
from devtools.doc_retriever import retrieve_documents

docs = retrieve_documents(
    tags=['35-KA001A'],
    doc_types=['CE', 'AA', 'MD', 'DS'],
    output_dir='step1_scope_and_research/references/'
)
# Returns list of downloaded file paths, or [] if no backend configured
```

---

## Document Classification and Relevance Filtering

### Document Type Codes

| Code | Type | When Relevant |
|------|------|---------------|
| `CE` | Performance Curves / Calculations | Compressor, pump, turbine analysis |
| `DS` | Data Sheet | Any equipment analysis |
| `AA` | General Arrangement Drawing | Physical layout, sizing |
| `MD` | Mechanical Drawing | Detailed dimensions, nozzles |
| `RV` | Vendor Manual / Report | Operating procedures, maintenance |
| `RE` | Report | Background reference |
| `ER` | Assembly / Erection Drawing | Installation, coupling details |
| `PL` | Parts List | Spare parts, BOM |
| `PI` | P&ID | Process topology |
| `PF` | PFD | Process flow overview |
| `IN` | Instrument Data Sheet | Control system design |
| `SP` | Specification | Material/piping requirements |
| `LL` | Line list / route table | Piping hydraulic route models with `PipingRouteBuilder` |
| `MC` | Material certificate / material class sheet | Pipe grade, SMYS/SMTS, heat number, toughness, temperature limits |
| `FP` | Fireproofing / PFP specification | Required fire endurance, protection type, inspection/condition evidence |
| `FC` | Fire or consequence study | Fire zone, heat flux, exposed length/area, escalation and source-term basis |

### Relevance Scoring

The task solver filters documents by relevance to avoid wasting time on
irrelevant content. Only documents above the relevance threshold are
extracted and analyzed:

```python
DOC_RELEVANCE = {
    'compressor_analysis': {
        'CE': 1.0,   # Performance curves — essential
        'DS': 0.9,   # Data sheet — essential
        'AA': 0.7,   # General arrangement — useful
        'MD': 0.6,   # Mechanical drawing — useful
        'ER': 0.6,   # Assembly drawing — useful
        'RV': 0.5,   # Vendor manual — background
        'RE': 0.4,   # Report — background
        'PL': 0.2,   # Parts list — skip
        'SP': 0.3,   # Specification — skip
    },
    'heat_exchanger_analysis': {
        'DS': 1.0, 'CE': 0.9, 'AA': 0.7, 'MD': 0.6, 'RV': 0.5,
    },
    'separator_analysis': {
        'DS': 1.0, 'AA': 0.9, 'PI': 0.8, 'MD': 0.6, 'IN': 0.7,
    },
    'pipeline_design': {
        'LL': 1.0, 'DS': 1.0, 'SP': 0.9, 'PI': 0.8, 'CE': 0.7, 'MD': 0.6,
    },
    'trapped_liquid_fire_rupture': {
        'PI': 1.0,   # P&ID / STID isolation boundaries
        'LL': 1.0,   # Line list, ID, wall, design P/T, material class
        'SP': 0.95,  # Piping spec, flange/gasket/bolt/material class
        'MC': 0.95,  # Material certificate or material class sheet
        'FP': 0.90,  # PFP/fireproofing requirement and condition
        'FC': 0.90,  # Fire/consequence study heat flux and exposed area
        'DS': 0.75,  # Equipment/piping datasheets
        'MD': 0.70,  # Mechanical arrangements and dimensions
        'IN': 0.60,  # Instruments, alarms, trips, relief availability
        'RE': 0.55,  # Prior reports or technical notes
    },
    'general': {
        'DS': 1.0, 'CE': 0.9, 'AA': 0.7, 'PI': 0.7, 'MD': 0.6,
        'RV': 0.5, 'RE': 0.4, 'ER': 0.4, 'IN': 0.5, 'SP': 0.4,
        'PL': 0.2, 'PF': 0.6,
    },
}

def filter_relevant_docs(doc_list, task_type, min_relevance=0.5):
    """Filter documents by relevance to the task type.

    Args:
        doc_list: List of dicts with at least 'docType' or 'doc_type' key
        task_type: One of the keys in DOC_RELEVANCE
        min_relevance: Minimum score to keep (default 0.5)

    Returns:
        (relevant, filtered_out) — two lists
    """
    relevance_map = DOC_RELEVANCE.get(task_type, DOC_RELEVANCE['general'])
    relevant, filtered_out = [], []
    for doc in doc_list:
        dtype = doc.get('docType') or doc.get('doc_type', '')
        score = relevance_map.get(dtype, 0.0)
        if score >= min_relevance:
            relevant.append({**doc, '_relevance': score})
        else:
            filtered_out.append({**doc, '_relevance': score,
                                 '_reason': f'Below threshold ({score} < {min_relevance})'})
    return relevant, filtered_out
```

### Trapped-Liquid Fire Rupture Evidence Retrieval

For tasks involving trapped liquid, blocked-in liquid, no pressure relief,
fire exposure, PFP demand, pipe rupture, or flange failure, use task type
`trapped_liquid_fire_rupture` and search for this evidence pack:

1. P&ID/STID or isometric showing isolation valves, vents, drains, relief paths,
   line numbers, and segment boundaries.
2. Line list or route table with NPS/ID, wall thickness/schedule, length, design
   pressure, design temperature, and material class.
3. Piping specification with flange class, gasket/bolt material, corrosion
   allowance, and pressure-temperature rating basis.
4. Material certificate or material class sheet with SMYS/SMTS and toughness notes.
5. Fire-zone, fire-study, layout, or consequence documents with exposed length,
   heat flux, fire duration, and escalation basis.
6. PFP/fireproofing requirement and inspection records showing required endurance
   and whether protection can be credited.
7. Relief/thermal relief/blowdown design basis showing availability, set pressure,
   discharge path, and creditability.
8. Design basis or technical requirements with acceptance criteria and standards.

Write retrieval attempts, missing documents, and fallback assumptions into the
manifest so `neqsim-trapped-liquid-fire-rupture` can include them in the final
evidence matrix.

---

## PDF-to-Image Extraction

After documents are in `references/`, convert to images for AI analysis:

```python
import fitz  # pymupdf

def pdf_to_pngs(pdf_path, output_dir, dpi=200):
    """Convert PDF pages to numbered PNG images."""
    import os
    doc = fitz.open(pdf_path)
    base = os.path.splitext(os.path.basename(pdf_path))[0]
    paths = []
    for i, page in enumerate(doc):
        pix = page.get_pixmap(dpi=dpi)
        out = os.path.join(output_dir, f"{base}_page{i+1}.png")
        pix.save(out)
        paths.append(out)
    doc.close()
    return paths
```

Or use the built-in utility:

```bash
python devtools/pdf_to_figures.py step1_scope_and_research/references/ --outdir figures/
```

Then use `view_image` on extracted PNGs to read compressor curves,
mechanical drawings, and data sheets.

---

## Retrieval Manifest

After retrieval/classification, create a manifest for traceability:

```python
manifest = {
    "source": "local" | "backend" | "manual",
    "retrieval_date": "2026-04-16",
    "task_type": "compressor_analysis",
    "tags_searched": ["35-KA001A", "35-KA001B"],
    "documents_retrieved": [
        {
            "filename": "performance_curves.pdf",
            "doc_type": "CE",
            "title": "Performance Curves Compressor B",
            "relevance": 1.0,
            "pages": 41,
            "used_in_analysis": True
        }
    ],
    "documents_filtered_out": [
        {
            "filename": "parts_list.pdf",
            "doc_type": "PL",
            "title": "Spare Parts List",
            "relevance": 0.2,
            "reason": "Below relevance threshold (0.5)"
        }
    ]
}
# Save as step1_scope_and_research/retrieval_manifest.json
```

The task solver uses this manifest to:
- Know which documents to analyze in step 2
- Skip irrelevant documents automatically
- Record data provenance in the final report

---

## Task Solver Integration

### Route-Level Piping Hydraulics

When retrieved STID documents include line lists, E3D route tables, stress
isometrics, or P&IDs with enough line geometry, hand off the extracted route to
`PipingRouteBuilder` for the NeqSim hydraulic model. This is the preferred path
for compressor suction/discharge pressure-drop studies and debottlenecking tasks.

Required extraction fields:

| Field | Purpose |
|-------|---------|
| `segment_id` | Stable line-list row id or generated route segment id |
| `from_node`, `to_node` | Equipment/nozzle/node topology |
| `length`, `length_unit` | Straight pipe length |
| `internal_diameter`, `diameter_unit` | Hydraulic diameter for `PipeBeggsAndBrills` |
| `wall_thickness`, `wall_thickness_unit` | Optional metadata and generated pipe wall thickness |
| `elevation_change`, `elevation_unit` | Static head contribution |
| `minor_losses` | Fittings/valves/reducers as K values |
| `source_ref` | Drawing number, page, row, or isometric reference |

Save the extracted route table and `route.toJson()` in the task folder. See
`docs/process/piping_route_builder.md` for the full builder workflow.

### In task_spec.md

```markdown
## Data Sources

- **Equipment tags:** 35-KA001A, 35-KA001B (export compressors)
- **Document source:** Local directory / Auto-retrieval / User-provided
- **Key documents used:**
  - performance_curves.pdf: Vendor performance maps (41 pages)
  - as_built_curves.pdf: Shop test results (4 pages)
  - general_arrangement.pdf: GA drawing with dimensions
- **Documents filtered out:** 8 (parts lists, generic specs — below relevance)
```

### In analysis notebook

```python
# Load retrieval manifest to know what's available
import json
manifest_path = TASK_DIR / 'step1_scope_and_research' / 'retrieval_manifest.json'
if manifest_path.exists():
    with open(manifest_path) as f:
        manifest = json.load(f)

    # Work only with relevant documents
    curve_docs = [d for d in manifest['documents_retrieved']
                  if d['doc_type'] == 'CE' and d['used_in_analysis']]
    print(f"Analyzing {len(curve_docs)} performance curve documents")
```

### In results.json

```json
{
    "data_sources": {
        "retrieval_method": "local",
        "documents_retrieved": 13,
        "documents_analyzed": 5,
        "documents_filtered_out": 8,
        "key_documents": [
            "performance_curves.pdf — Vendor Performance Maps",
            "as_built_curves.pdf — Shop Test Results"
        ]
    }
}
```

### Loading into NeqSim

```python
from neqsim import jneqsim

# Create compressor with performance curves from extracted data
compressor = jneqsim.process.equipment.compressor.Compressor("Export Comp", feed)

# If curve data has been digitized from the images:
chart = compressor.getCompressorChart()
chart.setHeadUnit("kJ/kg")
chart.setUseCompressorChart(True)

# Add speed curves (extracted from performance map)
for speed, points in curve_data.items():
    curve = jneqsim.process.equipment.compressor.CompressorCurve(speed)
    for flow, head, eff in points:
        curve.addCurveDataPoint(flow, head, eff)
    chart.addCurve(curve)
```

---

## Manual + Auto Coexistence

Users can **always** add documents manually to `references/`, even when a
retrieval backend is configured. The two approaches coexist:

```
step1_scope_and_research/references/
├── [auto-retrieved]    performance_curves_35KA001A.pdf    (from backend)
├── [auto-retrieved]    datasheet_35KA001A.pdf             (from backend)
├── [manual]            vendor_email_attachment.pdf         (user dropped in)
├── [manual]            field_test_report_2025.xlsx         (user dropped in)
└── [manual]            photo_nameplate.jpg                 (user dropped in)
```

The retrieval manifest tracks the source of each document:

```json
{
    "documents_retrieved": [
        {"filename": "performance_curves.pdf", "source": "backend", "doc_type": "CE"},
        {"filename": "vendor_email_attachment.pdf", "source": "manual", "doc_type": "RE"},
        {"filename": "field_test_report_2025.xlsx", "source": "manual", "doc_type": "DS"}
    ]
}
```

**Rules:**
- Manual documents are never overwritten by auto-retrieval
- Manual documents are classified and relevance-scored the same way
- The agent should ask "Do you have additional documents to add?" before
  leaving Step 1 (scope & research)
- During analysis, the user can drop more files in `references/` at any time;
  the agent should re-scan the folder if it detects new files

---

## Iterative Retrieval During Analysis

The initial retrieval in Step 1 may not cover everything. During Step 2
(analysis), the agent may discover it needs additional documents — for example:

- Found performance curves but needs mechanical drawing for nozzle dimensions
- Analyzing compressor A but needs data for compressor B (parallel train)
- Needs P&ID to understand upstream/downstream connections
- Needs instrument datasheets to set up control system model
- Needs material certificates for corrosion/fatigue analysis

### How It Works

When the agent identifies a **data gap** during analysis, it follows this
protocol:

1. **Log the gap** — record what's missing and why in the notebook:
   ```python
   # DATA GAP: Need mechanical drawing (AA) for 35-KA001A to get
   # nozzle sizes for piping stress analysis. Current docs only have
   # performance curves (CE) and datasheet (DS).
   ```

2. **Attempt auto-retrieval** (if backend configured):
   ```python
   # Mid-analysis retrieval for additional document types
   from devtools.doc_retriever import retrieve_documents

   additional = retrieve_documents(
       tags=['35-KA001A'],
       doc_types=['AA', 'MD'],  # specifically what's missing
       output_dir='step1_scope_and_research/references/'
   )
   if additional:
       print(f"Retrieved {len(additional)} additional documents")
       # Re-extract PNGs for new documents
       # Update retrieval manifest
   ```

3. **Ask the user** if auto-retrieval is unavailable or returned nothing:
   ```markdown
   **Data gap identified:** I need the General Arrangement drawing (AA) for
   35-KA001A to extract nozzle dimensions. Options:
   - Drop the PDF into `step1_scope_and_research/references/` and I'll continue
   - Provide the dimensions directly (suction nozzle OD, discharge nozzle OD)
   - Skip this analysis (I'll use typical values with a note on uncertainty)
   ```

4. **Update the manifest** with the new retrieval:
   ```python
   manifest['iterative_retrievals'] = manifest.get('iterative_retrievals', [])
   manifest['iterative_retrievals'].append({
       "phase": "step2_analysis",
       "reason": "Need nozzle dimensions for piping stress",
       "doc_types_requested": ["AA", "MD"],
       "tags": ["35-KA001A"],
       "documents_found": ["general_arrangement_35KA001A.pdf"],
       "source": "backend"  # or "manual" or "user_provided_value"
   })
   ```

5. **Continue analysis** with the new data, or proceed with documented
   assumptions if the document isn't available.

### Data Gap Detection Triggers

The agent should check for data gaps at these points:

| Trigger | Example Gap | Action |
|---------|------------|--------|
| Missing physical dimensions | No GA/MD drawing → can't size equipment | Request AA/MD docs |
| Missing operating conditions | No datasheet → unknown design pressure | Request DS docs |
| Upstream/downstream unknown | No P&ID → can't model recycles | Request PI docs |
| Control system needed | No instrument sheets → can't set PID params | Request IN docs |
| Parallel equipment | Only train A data, need train B comparison | Request docs for tag B |
| Material unknown | No material cert → can't check corrosion | Request SP/material cert |
| Vendor corrections needed | Shop test vs predicted curves differ | Request test report (RE) |

---

## Equipment Naming Conventions (NORSOK Z-001)

Standard tag naming for Norwegian continental shelf installations:

| Prefix | Equipment Type |
|--------|---------------|
| `KA` | Compressor |
| `PA` | Pump |
| `VA` | Valve |
| `FA` | Fan |
| `HA` | Heat exchanger |
| `DA` | Vessel / Separator |
| `BA` | Tank |
| `GA` | Generator |
| `MA` | Motor |
| `XA` | Special equipment |

---

## Backend Configuration (Gitignored — Not Public)

To enable auto-retrieval, create `devtools/doc_retrieval_config.yaml`.
This file is in `.gitignore` and never committed to the public repo.

```yaml
# devtools/doc_retrieval_config.yaml
# THIS FILE IS GITIGNORED — contains organization-specific configuration
#
# Supported backends:
#   stidapi  — STID document management (requires stidapi package + network)
#   local    — just reads from a local directory
#   none     — disabled (user must provide docs manually)

backend: none   # change to: stidapi, local

# Backend-specific settings (only needed for auto-retrieval):
# stidapi:
#   auth_method: azure_ad_sso
#   default_inst_code: YOUR_INST_CODE
```

If this file doesn't exist, the task solver works normally — it just
expects documents in `references/` instead of auto-fetching them.

---

## Related: STID Tags → Plant Historian → CSV

When STID retrieval identifies equipment tags (e.g., `35-KA001A`), the
same tags can be used to read operating data from the plant historian
(OSIsoft PI / Aspen IP.21) via **tagreader**, and the data should be
saved as CSV inside the task folder for reproducibility.

The full pipeline is documented in the **`neqsim-plant-data` skill**:

```
STID (tag search) → Tagreader (historian read) → CSV (snapshot) → NeqSim (simulation)
```

See the "STID → Tagreader → CSV → NeqSim Pipeline" section in that skill
for the complete end-to-end example with CSV persistence, data quality
filtering, and digital twin comparison — all saved to the task folder.
