# Feature: AI Extraction Skill — Unstructured Documents → NeqSim JSON Process Model

## Summary

Create a Copilot skill (and companion agent) that reads **unstructured information** from various document types (text descriptions, PFDs, data sheets, operational reports, Excel tables, images/sketches) and converts it into the **canonical NeqSim JSON format** that `ProcessSystem.fromJson()` already accepts. This closes the gap between "I have a pile of engineering documents" and "I have a running NeqSim simulation."

---

## Motivation

NeqSim already has a powerful **JSON process builder** (`ProcessSystem.fromJson()` / `ProcessSystem.fromJsonAndRun()`) that can declaratively build and run complete process simulations from structured JSON. The evaluation notebook ([json_process_builder_evaluation.ipynb](../../examples/notebooks/json_process_builder_evaluation.ipynb)) confirms this works well for:

- Linear and branching process trains
- 40+ equipment types via `EquipmentFactory`
- Dot-notation stream wiring (`"HP Sep.gasOut"`)
- Structured error responses with remediation hints
- Template-based sessions for optimization loops

**The missing piece** is the "first mile" — converting messy, real-world engineering information into that clean JSON. Today, engineers must manually translate PFDs, design basis documents, and operating data into either Python code or JSON by hand. This is tedious, error-prone, and the #1 barrier to adoption.

### What Engineers Actually Have

| Source Type | Example | Information Content |
|-------------|---------|---------------------|
| Text descriptions | "The well stream at 65 bara and 80°C enters a 3-phase separator, gas goes to a compressor at 120 bara, liquids to a letdown valve at 15 bara" | Topology, conditions, equipment types |
| Process Flow Diagrams (PFDs) | PNG/PDF images of process sketches | Equipment layout, stream connectivity, tag numbers |
| Data sheets | Equipment data sheets (PDF/Excel) | Design pressures, temperatures, materials, sizes |
| Operating reports | Daily/monthly production reports | Flow rates, compositions, operating points |
| Excel spreadsheets | Heat & mass balance tables, well test data | Compositions, conditions, multi-stream data |
| Design basis documents | FEED/concept study reports | Fluid compositions, design envelopes, constraints |
| P&IDs | Piping & Instrumentation Diagrams | Valve types, instrument tags, control loops |

### The Gap

```
Today:   Documents → (manual reading & coding) → NeqSim API calls → Results
Target:  Documents → (AI extraction skill) → JSON → ProcessSystem.fromJson() → Results
```

---

## Proposed Architecture

```
┌─────────────────────────────────────────────┐
│              Input Sources                   │
│  Text / PDF / Images / Excel / Tables / OCR  │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│     Skill: neqsim-process-extraction        │
│  (Copilot skill with LLM-guided parsing)    │
│                                             │
│  1. Source classification & chunking         │
│  2. Equipment identification & typing        │
│  3. Stream topology extraction               │
│  4. Operating condition extraction           │
│  5. Fluid composition extraction             │
│  6. Missing data detection & flagging        │
│  7. Assumption tracking                      │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│     Canonical Intermediate Schema            │
│  (streams, units, connections, fluids)       │
│                                             │
│  - Equipment type mapping (60+ synonyms)     │
│  - Unit normalization (barg→bara, °C→K)      │
│  - Composition validation (sum to 1.0)       │
│  - Orphan stream detection                   │
│  - Template matching for known topologies    │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│     NeqSim JSON Builder Format               │
│  ProcessSystem.fromJson() / fromJsonAndRun() │
│                                             │
│  ✓ Already exists and works                  │
│  ✓ 40+ equipment types                      │
│  ✓ Dot-notation stream wiring               │
│  ✓ Structured error responses               │
│  ✓ Session management for iterations        │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│     Validation + Simulation + Report         │
│  SimulationResult with error codes & fixes   │
└─────────────────────────────────────────────┘
```

### Key Design Principle

> **The LLM extracts structured data into a constrained JSON schema. It does NOT write NeqSim code.**
>
> This is fundamentally more reliable than code-generation because:
> 1. The JSON schema is finite and well-defined
> 2. Validation is deterministic (rule-based, not LLM-based)
> 3. `ProcessSystem.fromJson()` handles all API calls correctly
> 4. Errors are structured and actionable

---

## Deliverables

### 1. Copilot Skill: `neqsim-process-extraction`

**Location:** `.github/skills/neqsim-process-extraction/SKILL.md`

The skill should contain:

- **Equipment type mapping** — 60+ natural-language synonyms → NeqSim equipment types (separator, scrubber, KO drum, flash drum → `Separator`; choke valve, JT valve, letdown valve → `ThrottlingValve`; etc.)
- **Outlet port mapping** — which ports each equipment type exposes (`gasOut`, `liquidOut`, `oilOut`, `waterOut`, `outlet`)
- **Unit conversion rules** — barg→bara, °C→K, MMSCFD→kg/hr, psi→bara, etc.
- **Composition normalization** — sum-to-1 checks, component name mapping (C1→methane, C2→ethane, etc.)
- **Extraction prompts** — structured templates for extracting: equipment list, topology, conditions, compositions from different source types
- **Confidence scoring** — how confident the extraction is (composition provided? conditions specified? topology clear?)
- **Assumption tracking** — what defaults were used when information was missing
- **Missing data flagging** — what the source didn't specify that the simulation needs
- **Template library** — 10-20 common process topologies for pattern matching (gas dehydration, compression train, HP/LP separation, etc.)

### 2. Copilot Agent: `extract process to neqsim json`

**Location:** `.github/agents/extract.process.agent.md`

An agent that:

1. Accepts unstructured input (pasted text, file references, image descriptions)
2. Loads the `neqsim-process-extraction` skill
3. Extracts equipment, topology, conditions, and compositions
4. Produces the NeqSim JSON builder format
5. Runs the simulation via `ProcessSystem.fromJsonAndRun()`
6. Reports results with confidence score, assumptions used, and missing information flagged

### 3. Canonical Intermediate Schema

A documented JSON schema for the intermediate representation between raw extraction and NeqSim JSON:

```json
{
  "source": {
    "type": "text|pfd|datasheet|excel|image",
    "description": "Brief description of input source",
    "raw_text": "Original text if applicable"
  },
  "extraction": {
    "confidence": 0.75,
    "assumptions": [
      "Default SRK EOS (not specified in source)",
      "Flow rate assumed 50000 kg/hr (not specified)"
    ],
    "missing_information": [
      "Feed composition not provided",
      "Compressor efficiency not specified"
    ],
    "warnings": [
      "Composition sums to 0.98 — normalized to 1.0"
    ]
  },
  "fluids": [
    {
      "id": "feed_gas",
      "model": "SRK",
      "temperature_C": 50.0,
      "pressure_bara": 65.0,
      "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05}
    }
  ],
  "equipment": [
    {
      "id": "V-101",
      "type": "ThreePhaseSeparator",
      "name": "Inlet Separator",
      "tag": "20VA001",
      "design_pressure_barg": 70,
      "design_temperature_C": 100
    }
  ],
  "streams": [
    {
      "id": "S-001",
      "type": "material",
      "from": null,
      "to": "V-101",
      "port": "inlet",
      "fluid_ref": "feed_gas",
      "flow_rate_kg_hr": 75000.0
    }
  ],
  "connections": [
    {"from": "V-101.gasOut", "to": "K-101.inlet"},
    {"from": "V-101.oilOut", "to": "VLV-101.inlet"}
  ]
}
```

### 4. Equipment Type Mapping Table

A comprehensive mapping file (JSON or CSV) with 60+ entries:

| Natural Language | NeqSim Type | Category |
|------------------|-------------|----------|
| separator, 2-phase separator, flash drum, KO drum, scrubber, slug catcher | `Separator` | Separation |
| 3-phase separator, three-phase separator, production separator | `ThreePhaseSeparator` | Separation |
| compressor, export compressor, recompressor, booster compressor | `Compressor` | Compression |
| cooler, aftercooler, air cooler, fin fan cooler | `Cooler` | Heat Transfer |
| heater, pre-heater, line heater | `Heater` | Heat Transfer |
| heat exchanger, shell & tube, plate HX | `HeatExchanger` | Heat Transfer |
| valve, choke valve, JT valve, letdown valve, control valve | `ThrottlingValve` | Valves |
| pump, export pump, booster pump | `Pump` | Pumps |
| mixer, junction, manifold | `Mixer` | Mixing |
| splitter, tee | `Splitter` | Splitting |
| expander, turbo-expander | `Expander` | Expansion |

### 5. Template Library

Pre-defined JSON templates for common process configurations:

- **Gas compression train** — multi-stage compression with intercooling
- **HP/LP separation** — two-stage separation with gas recompression
- **TEG dehydration** — absorber + regenerator + flash drum
- **Gas cooling / JT** — inlet cooling, separation, JT expansion
- **Oil stabilization** — multi-stage flash for crude stabilization
- **Subsea processing** — subsea separator + multiphase pump
- **NGL recovery** — turbo-expander + demethanizer
- **Acid gas removal** — amine absorber + regenerator
- **Water treatment** — produced water degassing + hydrocyclone
- **Export pipeline** — cooler + scrubber + metering + pipeline

Each template has fixed topology with parametric placeholders (pressures, temperatures, compositions, flow rates) that can be filled from extracted data.

---

## Phased Implementation

| Phase | Scope | Difficulty | Dependencies |
|-------|-------|-----------|--------------|
| **Phase 1** | Text descriptions → JSON for linear/branching processes | Easy | None — can start immediately |
| **Phase 2** | Template matching + parametric fill from data sheets/Excel | Medium | Phase 1 + template library |
| **Phase 3** | Image/sketch interpretation (PFD, process sketches) | Hard | Phase 1 + vision model |
| **Phase 4** | Complex topology (recycles, multi-feed, distillation) | Hard | Phase 1 + recycle solver in JSON builder |
| **Phase 5** | DEXPI / ISO 15926 P&ID import | Industry standard | Phase 4 + DEXPI schema mapping |

### Phase 1 — Text Extraction (Start Here)

Minimum viable skill that can:
- Parse a text paragraph describing a process
- Identify equipment types from natural language keywords
- Infer sequential connectivity (equipment A feeds equipment B)
- Extract numeric parameters (pressures, temperatures, flow rates) from context
- Handle dot-notation for branching (gas from separator goes to compressor, liquid to valve)
- Produce valid NeqSim JSON that runs successfully
- Report confidence, assumptions, and missing information

### Phase 2 — Template + Data Fill

- Match extracted topology to template library
- Fill template parameters from structured data (Excel columns, data sheet fields)
- Support multi-fluid processes (different compositions for different feeds)
- Handle unit conversion (barg/bara, °C/°F/K, MMSCFD/kg/hr/t/h)

### Phase 3 — Vision / Image

- Accept PFD images and extract equipment blocks + stream lines
- Map equipment symbols to NeqSim types
- Read annotations (tag numbers, conditions) from image
- Generate topology from visual layout

---

## Acceptance Criteria

### Must Have (Phase 1)

- [ ] Skill file at `.github/skills/neqsim-process-extraction/SKILL.md` with complete extraction rules
- [ ] Agent file at `.github/agents/extract.process.agent.md`
- [ ] Equipment type mapping with 40+ natural language synonyms
- [ ] Outlet port mapping for all supported equipment types
- [ ] Unit conversion rules (at least: barg↔bara, °C↔K, common flow rate units)
- [ ] Component name mapping (C1→methane, C2→ethane, ..., CO2, H2S, N2, H2O)
- [ ] Confidence scoring framework (0-1 scale with clear criteria)
- [ ] Assumption tracking (list of defaults used)
- [ ] Missing data detection (list of what source didn't specify)
- [ ] At least 5 worked examples in the skill showing text → JSON conversion
- [ ] All examples produce valid JSON that `ProcessSystem.fromJsonAndRun()` accepts

### Should Have (Phase 2)

- [ ] Template library with 10+ common process configurations
- [ ] Parametric template fill from tabular data
- [ ] Multi-fluid support for processes with different feed compositions
- [ ] Intermediate canonical schema documented
- [ ] Composition extraction from various formats (mole%, weight%, component tables)

### Nice to Have (Phase 3+)

- [ ] PFD image interpretation guidance
- [ ] DEXPI schema mapping reference
- [ ] Operating data bridge patterns (Excel → JSON, historian → JSON)

---

## Related Work

- **Evaluation notebook:** [json_process_builder_evaluation.ipynb](../../examples/notebooks/json_process_builder_evaluation.ipynb) — prototypes the translation layer and validates the architecture
- **JSON builder:** `ProcessSystem.fromJson()` in [ProcessSystem.java](../../src/main/java/neqsim/process/processmodel/ProcessSystem.java)
- **Equipment factory:** [EquipmentFactory.java](../../src/main/java/neqsim/process/equipment/EquipmentFactory.java)
- **Existing agents:** `solve.process.agent.md`, `router.agent.md` — could use this skill
- **ProcessPilot-NeqSimInterface** (Equinor) — production wrapper with Pydantic models and tag-based lookup

---

## Example: End-to-End Workflow

**User provides:**
> "The well stream arrives at 65 bara and 80°C. It enters a 3-phase separator.
> Gas from the separator goes to a compressor that boosts pressure to 120 bara.
> Oil from the separator goes through a letdown valve to 15 bara.
> The gas composition is approximately 80% methane, 8% ethane, 5% propane,
> 3% CO2, 2% n-butane, 1% nitrogen, 0.5% n-pentane, 0.5% n-hexane.
> Flow rate is about 75000 kg/hr."

**Skill extracts:**
```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 353.15,
    "pressure": 65.0,
    "mixingRule": "classic",
    "components": {
      "methane": 0.80, "ethane": 0.08, "propane": 0.05,
      "CO2": 0.03, "n-butane": 0.02, "nitrogen": 0.01,
      "n-pentane": 0.005, "n-hexane": 0.005
    }
  },
  "process": [
    {"type": "Stream", "name": "well stream", "properties": {"flowRate": [75000.0, "kg/hr"]}},
    {"type": "ThreePhaseSeparator", "name": "inlet separator", "inlet": "well stream"},
    {"type": "Compressor", "name": "gas compressor", "inlet": "inlet separator.gasOut", "properties": {"outletPressure": 120.0}},
    {"type": "ThrottlingValve", "name": "letdown valve", "inlet": "inlet separator.oilOut", "properties": {"outletPressure": 15.0}}
  ],
  "autoRun": true
}
```

**Agent output:**
- Simulation status: SUCCESS
- Confidence: 0.85 (composition provided, conditions specified, flow rate given)
- Assumptions: SRK EOS (not specified), classic mixing rule, default compressor efficiency 0.75
- Missing: compressor efficiency, separator operating temperature

---

## Implementation Status

### Already Delivered (Phase 1)

- [x] **Skill file:** `.github/skills/neqsim-process-extraction/SKILL.md` — complete with 14 sections covering the full extraction workflow
- [x] **Agent file:** `.github/agents/extract.process.agent.md` — 7-step workflow, error recovery, limitations
- [x] **Equipment type mapping:** 60+ natural language synonyms → NeqSim types (covers all `EquipmentEnum` values)
- [x] **Outlet port mapping:** All equipment types with their available outlet ports
- [x] **Unit conversion rules:** Temperature (°C/°F/K/R), pressure (barg/bara/psi/kPa/MPa/atm), flow rate units
- [x] **Component name mapping:** Hydrocarbons C1–C24, non-HC (CO2, H2S, N2, H2, etc.), chemicals (MEG, TEG, MDEA, etc.), aromatics
- [x] **Confidence scoring framework:** 0–1 scale with 9 criteria and 4 bands (High/Medium/Low/Very Low)
- [x] **Assumption tracking:** 8 default values with tracking text
- [x] **Missing data detection:** Integrated into the extraction workflow
- [x] **5 worked examples:** Simple text, text with composition, H&M balance table, equipment tag list, multi-stage compression
- [x] **6 process templates:** Gas dew point control, two-stage separation, multi-stage compression, JT cooling, oil stabilization, subsea tieback
- [x] **Evaluation notebook:** `examples/notebooks/json_process_builder_evaluation.ipynb` — validates the architecture end-to-end
- [x] **AGENTS.md updated:** New skill and agent registered in Key Paths and Skills Reference tables

### Remaining Work (Phase 2+)

- [ ] Expand template library to 10+ templates (add TEG dehydration, NGL recovery, acid gas removal, water treatment)
- [ ] Parametric template fill from Excel/tabular data
- [ ] Multi-fluid support integration testing
- [ ] Composition extraction from weight% format
- [ ] PFD image interpretation guidance (Phase 3)
- [ ] DEXPI schema mapping (Phase 5)
- [ ] Operating data bridge patterns (Excel → JSON, historian → JSON)

---

## Labels

`enhancement`, `ai-skills`, `process-simulation`, `json-builder`
