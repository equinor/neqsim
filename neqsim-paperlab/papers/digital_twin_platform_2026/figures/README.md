# Figure Descriptions — Digital Twin Platform Paper

## Figure 1: Platform Architecture (Six-Layer Stack)

**Caption:** Platform architecture for open-source process digital twins. Six interconnected layers provide the complete stack from thermodynamic calculations through cloud-deployed live operation.

**Description:** Layered architecture diagram showing:

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 6: Deployment & Operation                                │
│  [NeqSimAPI REST] [Sigma/CalcEngine] [Omnia TimeSeries]        │
├─────────────────────────────────────────────────────────────────┤
│  Layer 5: Data Integration                                      │
│  [tagreader-python] [Tag Mapping] [Data Quality] [PI/IP.21]    │
├─────────────────────────────────────────────────────────────────┤
│  Layer 4: Instrumentation & Control                             │
│  [27+ Transmitters] [PID Controllers] [Alarm Manager]          │
│  [Tag Roles: INPUT / OUTPUT / BENCHMARK]                       │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: Process Orchestration                                 │
│  [ProcessSystem] [ProcessModel (multi-area)] [Recycle/Adjuster]│
│  [5 Execution Strategies] [Progress Listeners]                 │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: Equipment Library                                     │
│  [33 packages] [run() + runTransient(dt)]                      │
│  [Stream Introspection] [DEXPI Import/Export]                  │
├─────────────────────────────────────────────────────────────────┤
│  Layer 1: Thermodynamic Engine                                  │
│  [60+ EOS] [Flash Algorithms] [Transport Properties]           │
│  [200+ Components] [Phase Identification]                      │
└─────────────────────────────────────────────────────────────────┘
```

**Format:** Vector (EPS/PDF), full column width.

---

## Figure 2: Data Integration Workflow (Five-Step Pipeline)

**Caption:** Five-step workflow for connecting process models to live plant data, from offline model development through cloud-deployed continuous operation.

**Description:** Flowchart showing:

```
Step 1: Build Model          Step 2: Read Plant Data
[NeqSim Python API] ──────── [tagreader → PI/IP.21]
        │                            │
        ▼                            ▼
Step 3: Calibrate ◄──────── [Compare: Model vs Plant]
[Adjust parameters]          [BENCHMARK deviations]
        │
        ▼
Step 4: Digital Twin Loop    Step 5: Cloud Deploy
[Continuous update] ──────── [NeqSimAPI → Sigma → PI]
[INPUT → run() → OUTPUT]    [Automated read-compute-write]
```

Annotations show: Tag role classification (INPUT feeds model, BENCHMARK validates, OUTPUT records), data quality filtering, and bounded measurement history.

**Format:** Vector, full column width.

---

## Figure 3: Execution Strategies

**Caption:** Five execution strategies for process simulation, selected automatically based on flowsheet topology analysis.

**Description:** Decision tree + timeline diagrams:

Left panel: Decision tree
- Has recycles? → Yes → Has feed-forward section? → Yes → Hybrid
- Has recycles? → Yes → Has feed-forward section? → No → Sequential
- Has recycles? → No → Has multi-input? → Yes → Optimized
- Has recycles? → No → Has multi-input? → No → Parallel

Right panel: Timeline showing unit execution patterns for each strategy:
- Sequential: U1→U2→U3→U4 (linear)
- Parallel: [U1,U3]→[U2,U4] (grouped by dependency level)
- Hybrid: [U1,U2] parallel → [U3,U4] sequential (mixed)
- Progress: U1→callback→U2→callback→U3→callback (with listener)

**Format:** Vector, full column width.

---

## Figure 4: Compressor Digital Twin Use Case

**Caption:** Digital twin loop for compressor performance monitoring: (a) model vs plant power comparison, (b) parity plot of discharge temperature, (c) polytropic efficiency tracking over 12-hour operating period.

**Description:** Three-panel figure:

(a) Time-series plot: Blue line = plant power (MW) from historian. Red line = simulated power from NeqSim. Typical agreement within 1-2%.

(b) Scatter/parity plot: X-axis = plant discharge T (°C), Y-axis = simulated discharge T (°C). Points clustered along 45° line. Includes MAE, RMSE, and R² annotations.

(c) Time-series of polytropic efficiency: Calculated from digitaltwin model. Shows typical operating efficiency with gradual degradation trend that indicates fouling.

**Format:** Multi-panel, full column width.

---

## Figure 5: Capability Comparison Radar Chart

**Caption:** Radar chart comparing digital twin capability dimensions across platforms: this open-source platform, Aspen HYSYS, DWSIM, and COCO/COFE.

**Description:** Spider/radar chart with 9 axes:
1. Thermodynamic breadth
2. Steady-state simulation
3. Dynamic simulation
4. Historian integration
5. Auto-instrumentation
6. Cloud deployment
7. AI/LLM integration
8. Interoperability (DEXPI)
9. Open-source/auditability

Four overlaid polygons: This platform (balanced profile), HYSYS (strong thermo/dynamic, weak cloud/AI), DWSIM (strong open-source, weak dynamics/integration), COCO (limited overall).

**Format:** Square figure, single column width.

---

## Figure 6: Cloud Deployment Pipeline

**Caption:** Production deployment pipeline from local model development to live digital twin operation with historian feedback loop.

**Description:** Horizontal pipeline diagram:

```
[Engineer     ] → [Jupyter    ] → [NeqSimAPI ] → [Sigma/     ] → [PI/IP.21   ]
[Workstation  ]   [Notebook   ]   [REST API  ]   [CalcEngine ]   [Historian   ]
                  [Model Dev  ]   [Cloud/    ]   [Middleware ]   [SCADA/DCS  ]
                  [Validation ]   [Radix     ]   [Read/Write ]   [Dashboards ]
                                                                       │
                                                       ┌───────────────┘
                                                       ▼
                                                [Operator     ]
                                                [Displays     ]
                                                [Omnia        ]
                                                [Analytics    ]
```

Annotations: "Steps 1-4: Local dev" (left), "Step 5: Cloud" (center), "Steps 6-7: Live operation" (right).

**Format:** Vector, full column width.
