---
name: neqsim-agent-handoff
description: "Agent-to-agent communication schema for NeqSim. USE WHEN: composing multi-agent pipelines where one agent's output feeds another agent's input. Defines structured result formats for fluid definitions, simulation results, and design outputs that agents can pass to each other."
---

# NeqSim Agent Handoff Schema

When agents need to pass results to other agents (e.g., `@process.model` output
feeding `@mechanical.design`), use these structured formats to ensure no
information is lost.

## Handoff Principles

1. **Explicit over implicit** — include all parameters, don't assume the receiving agent can infer
2. **Units always included** — every numerical value has a unit
3. **Code-ready** — the receiving agent can directly use the values in NeqSim API calls
4. **Traceable** — include the source agent and any assumptions made

## Schema 1: Fluid Definition Handoff

Pass from `@thermo.fluid` to any other agent:

```json
{
  "handoff_type": "fluid_definition",
  "source_agent": "thermo.fluid",
  "eos_class": "SystemSrkEos",
  "mixing_rule": "classic",
  "temperature_K": 298.15,
  "pressure_bara": 60.0,
  "components": [
    {"name": "methane", "mole_fraction": 0.85},
    {"name": "ethane", "mole_fraction": 0.10},
    {"name": "propane", "mole_fraction": 0.05}
  ],
  "multi_phase_check": false,
  "characterization": null,
  "java_code": "SystemInterface fluid = new SystemSrkEos(298.15, 60.0);\nfluid.addComponent(\"methane\", 0.85);\nfluid.addComponent(\"ethane\", 0.10);\nfluid.addComponent(\"propane\", 0.05);\nfluid.setMixingRule(\"classic\");",
  "assumptions": ["Lean gas — no water, no C4+ components"]
}
```

## Schema 2: Process Simulation Handoff

Pass from `@process.model` to `@mechanical.design`, `@safety.depressuring`, etc.:

```json
{
  "handoff_type": "process_simulation",
  "source_agent": "process.model",
  "fluid_definition": { "...": "Schema 1 above" },
  "equipment": [
    {
      "name": "HP Separator",
      "type": "Separator",
      "inlet_temperature_C": 30.0,
      "inlet_pressure_bara": 60.0,
      "outlet_gas_temperature_C": 30.0,
      "outlet_gas_pressure_bara": 60.0,
      "outlet_liquid_temperature_C": 30.0,
      "outlet_liquid_pressure_bara": 60.0,
      "gas_flow_rate_kg_hr": 42000.0,
      "liquid_flow_rate_kg_hr": 8000.0,
      "gas_density_kg_m3": 45.2,
      "liquid_density_kg_m3": 520.0
    }
  ],
  "mass_balance_error_pct": 0.001,
  "energy_balance_error_pct": 0.01,
  "assumptions": ["Adiabatic separator", "No liquid carryover"]
}
```

## Schema 3: Mechanical Design Handoff

Pass from `@mechanical.design` to `@solve.task` for reporting:

```json
{
  "handoff_type": "mechanical_design",
  "source_agent": "mechanical.design",
  "equipment_name": "HP Separator",
  "design_pressure_barg": 72.0,
  "design_temperature_C": 100.0,
  "material_grade": "SA-516-70",
  "wall_thickness_mm": 28.5,
  "corrosion_allowance_mm": 3.0,
  "weight_empty_kg": 15200.0,
  "design_standard": "ASME VIII Div.1",
  "company_tr": "Equinor TR2000",
  "cost_estimate_usd": 450000.0,
  "assumptions": ["Joint efficiency 0.85", "No external loads"]
}
```

## Schema 4: Flow Assurance Handoff

Pass from `@flow.assurance` to `@solve.task` or `@process.model`:

```json
{
  "handoff_type": "flow_assurance",
  "source_agent": "flow.assurance",
  "hydrate_temperature_C": 18.5,
  "operating_temperature_C": 25.0,
  "subcooling_margin_C": 6.5,
  "hydrate_risk": "LOW",
  "wax_appearance_temperature_C": -5.0,
  "pipeline_pressure_drop_bar": 12.3,
  "arrival_temperature_C": 8.5,
  "assumptions": ["No MEG injection", "Seawater at 4 C"]
}
```

## Schema 5: Safety Analysis Handoff

Pass from `@safety.depressuring` to reporting:

```json
{
  "handoff_type": "safety_analysis",
  "source_agent": "safety.depressuring",
  "scenario": "Fire case blowdown",
  "initial_pressure_bara": 85.0,
  "final_pressure_bara": 6.9,
  "blowdown_time_minutes": 15.0,
  "minimum_temperature_C": -45.0,
  "mdmt_C": -46.0,
  "mdmt_margin_C": 1.0,
  "psv_required_area_cm2": 12.5,
  "assumptions": ["API 521 fire case", "Orifice Cd = 0.85"]
}
```

## How to Use Handoff Schemas

### Sending Agent (produces the handoff)

At the end of your work, format results into the appropriate schema:

```python
# In a notebook or agent output
handoff = {
    "handoff_type": "process_simulation",
    "source_agent": "process.model",
    "equipment": [...],
    # ... fill all fields
}
# Include in the response to the orchestrating agent
```

### Receiving Agent (consumes the handoff)

When you receive a handoff from another agent:

1. **Validate the handoff** — check all required fields are present
2. **Use the values directly** — temperatures, pressures, flows are ready to use
3. **Preserve assumptions** — carry forward assumptions from the source agent
4. **Add your own assumptions** — append to the assumptions list

### Router Agent (orchestrates handoffs)

The `@neqsim.help` router agent manages handoffs when composing multi-agent pipelines:

1. Runs Agent A, captures handoff output
2. Passes handoff as context to Agent B
3. Agent B uses handoff values as inputs
4. Final results aggregated for user

## Cross-Agent Consistency Checks

When receiving a handoff, verify consistency:

| Check | Rule |
|-------|------|
| Temperature units | Must be in C or K (never mixed) |
| Pressure units | Must be bara (never barg or psia without conversion) |
| Flow rate units | Must include unit string |
| Mass balance | Sum of outlet flows = inlet flow (within 0.1%) |
| Phase consistency | If source says 2 phases, receiving agent should see 2 phases |

If a consistency check fails, alert the user before proceeding.
