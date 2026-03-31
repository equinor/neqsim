# An Open-Source Thermodynamic Engine for Oil and Gas Process Digital Twins: Architecture, Capabilities, and Future Directions

<!-- Target Journal: Computers & Chemical Engineering -->
<!-- Paper Type: application / review -->
<!-- Generated: 2026-03-31 -->
<!-- Status: DRAFT -->

## Highlights

- Open-source thermodynamic library enables physics-based digital twins for oil and gas
- Five execution strategies support steady-state and dynamic transient simulation modes
- Tag-based historian integration enables automated model-plant data synchronization
- Cloud deployment architecture bridges offline engineering with live online operations
- AI-augmented workflows with LLM agents accelerate digital twin development and maintenance

## Abstract

Digital twins are transforming operations in the oil and gas industry by providing physics-based virtual replicas of production facilities that enable real-time monitoring, predictive analytics, and operational optimization. However, most implementations rely on proprietary commercial simulation software, creating vendor lock-in, reproducibility barriers, and limited extensibility. This paper presents a comprehensive architecture for building process digital twins using an open-source thermodynamic and process simulation library. The platform provides 60+ equation-of-state implementations, rigorous multiphase flash algorithms, 33 process equipment packages with transient simulation capabilities, and a deployment pipeline from offline model development through cloud-hosted live operation. We describe the key architectural components — a unified process element model with stream introspection, tag-based plant historian integration via role-classified instrumentation, auto-instrumentation for rapid dynamic model creation, multi-area plant coordination through iterative convergence, and industry-standard interoperability through DEXPI and JSON interfaces. We review the current state of digital twin technology in oil and gas, compare the capabilities and limitations of open-source versus commercial approaches, and demonstrate the platform through representative use cases including compressor performance monitoring, emission tracking from produced water, and multi-area offshore platform modeling. Finally, we discuss emerging capabilities including AI-augmented digital twin development using large language model agents, model predictive control integration, and the convergence of physics-based and data-driven approaches. The complete platform is available under an open-source license to promote reproducibility and community-driven advancement.

## Keywords

digital twin; process simulation; thermodynamic modeling; open-source software; oil and gas; plant historian integration; equation of state

---

## 1. Introduction

### 1.1 The digital twin paradigm in oil and gas

The concept of a digital twin — a virtual replica of a physical asset that is continuously updated with operational data — has gained significant traction across the oil and gas industry over the past decade [1,2]. As defined by Grieves and Vickers [3], a digital twin comprises three fundamental elements: the physical entity, its virtual counterpart, and the data connections that link them. In oil and gas applications, these elements manifest as production facilities (platforms, pipelines, processing plants), thermodynamic process models, and real-time data flows from plant historians and sensor networks.

The value proposition of digital twins in this sector is substantial. Offshore oil and gas operations involve complex multiphase fluid systems operating across wide ranges of temperature, pressure, and composition. Equipment performance varies with changing reservoir conditions over decades of field life. Process engineers must continuously monitor system behavior, diagnose anomalies, predict future performance, and evaluate operational modifications — all tasks that benefit enormously from having a calibrated physics-based model synchronized with live plant data [4,5].

Industry adoption has accelerated in recent years. DNV GL [6] reported that digital twins can reduce unplanned downtime by 20% and maintenance costs by 10-15% in offshore operations. BP's APEX system [7] applies digital twin technology across its upstream assets for production optimization. Equinor has deployed NeqSim-based online models across multiple Norwegian Continental Shelf platforms for emission monitoring and process optimization [8]. Shell [9] and TotalEnergies [10] have similarly invested in digital twin programs for their production facilities.

### 1.2 The role of thermodynamic modeling

At the core of any process digital twin lies a thermodynamic model that predicts how multicomponent fluid mixtures behave under process conditions. The accuracy of this model fundamentally determines the fidelity of the digital twin. Unlike data-driven surrogate models that learn from historical patterns, physics-based thermodynamic models derive their predictions from fundamental equations of state, mixing rules, and transport property correlations [11,12].

The advantages of physics-based models for digital twins are well established. They generalize to conditions outside the training envelope — critical when operating conditions shift due to declining reservoir pressure, well interventions, or equipment modifications. They maintain thermodynamic consistency (Gibbs-Duhem, fugacity equality) that ensures physically meaningful predictions. And they provide interpretable results where deviations between model and plant can be diagnosed in terms of physical mechanisms (e.g., fouling, composition changes, valve malfunction) rather than statistical drift [13].

Equation-of-state (EOS) models for hydrocarbon systems — primarily the Soave-Redlich-Kwong (SRK) [14] and Peng-Robinson (PR) [15] cubic equations with various alpha-function modifications — have been the industry standard for over four decades. For systems involving water, methanol, glycols, and other associating compounds, the Cubic-Plus-Association (CPA) model [16] extends cubic EOS with explicit treatment of hydrogen bonding. For custody transfer and fiscal metering applications, the GERG-2008 reference equation [17] provides the highest accuracy for natural gas systems.

### 1.3 Challenges with current digital twin implementations

Despite the clear value proposition, several challenges hinder broader adoption of process digital twins in oil and gas:

**Vendor lock-in and proprietary barriers.** The dominant commercial process simulators — Aspen HYSYS, Aspen Plus, PRO/II, UniSim Design — are proprietary products with closed-source thermodynamic engines and costly licensing models. Deploying these engines in cloud-based digital twin architectures requires navigating complex licensing agreements, and the computational cores are often not designed for the high-throughput, automated execution patterns that digital twins demand [18,19].

**Model development bottleneck.** Building a process simulation model that accurately represents a real facility requires expert knowledge of both thermodynamic theory and the simulator's specific API and workflow conventions. This expertise bottleneck means that model creation, calibration, and maintenance are time-consuming activities that limit the scalability of digital twin deployments [20].

**Integration complexity.** Connecting a process model to live plant data requires bridging between the simulation environment and industrial data historians (OSIsoft PI, Aspen IP.21, Honeywell PHD), handling data quality issues (sensor drift, frozen signals, communication failures), and managing the continuous execution loop that keeps the model synchronized with the physical plant [21].

**Reproducibility and auditability.** In safety-critical industries governed by regulatory requirements (e.g., environmental emission reporting per EU ETS, Norwegian Environmental Agency guidelines), the ability to independently audit and verify the calculations within a digital twin is essential. Proprietary black-box thermodynamic engines create challenges for regulatory compliance and scientific reproducibility [22].

### 1.4 Open-source as an enabler

Open-source thermodynamic libraries address several of these challenges by providing transparent, auditable, and freely distributable computational engines. Notable open-source projects in process simulation include DWSIM [23], COCO/COFE [24], Caleb Bell's Thermo and Fluids libraries [25], and CoolProp [26] for pure-component and mixture properties. Each has strengths in specific domains but none provides the combination of rigorous multiphase thermodynamics, comprehensive process equipment library, dynamic simulation capabilities, plant data integration framework, and cloud deployment architecture needed for a complete digital twin platform.

### 1.5 Contributions

This paper presents a comprehensive architecture for building oil and gas process digital twins based on an open-source thermodynamic and process simulation library. Specifically, our contributions are:

1. We describe the software architecture of an open-source platform designed for digital twin applications, covering the thermodynamic engine, process equipment library, dynamic simulation capabilities, plant data integration, and deployment pipeline (Section 3).

2. We provide a systematic comparison of digital twin capabilities across open-source and commercial platforms, identifying strengths, limitations, and complementary roles (Section 4).

3. We present representative use cases demonstrating the platform's application to real industrial digital twin scenarios (Section 5).

4. We discuss emerging capabilities — including AI-augmented digital twin development, hybrid physics-data approaches, and edge deployment — and outline a roadmap for the evolution of open-source digital twin technology (Section 6).

---

## 2. Background and Literature Review

### 2.1 Digital twin definitions and maturity models

The digital twin concept, while popularized in the manufacturing context by Grieves [3], has been adapted for the process industries through several maturity frameworks. Kritzinger et al. [27] distinguished between digital models (manual data transfer), digital shadows (automatic data flow from physical to virtual), and digital twins (bidirectional automatic data flow). Rasheed et al. [28] proposed a five-level maturity model: (i) standalone simulation, (ii) simulation connected to data, (iii) real-time synchronized model, (iv) model-based optimization, and (v) autonomous operation.

In the oil and gas context, most deployed systems currently operate at maturity levels ii–iii, with some advanced applications reaching level iv through integration with model predictive control (MPC) and real-time optimization (RTO) systems [29]. The open-source platform described in this paper supports levels i through iv, with active development toward level v capabilities.

### 2.2 Thermodynamic engines for process digital twins

A digital twin's thermodynamic engine must satisfy several requirements beyond basic property prediction: (a) robustness across the full operating envelope including startup, shutdown, and upset conditions; (b) computational efficiency sufficient for real-time execution at plant scan rates (typically 1–60 second intervals); (c) accurate phase identification and multiphase flash convergence; (d) comprehensive transport property prediction (viscosity, thermal conductivity, surface tension); and (e) extensibility to new components, models, and correlations [30].

Commercial engines have historically led in robustness and breadth. Aspen HYSYS uses a modified SRK/PR implementation with extensive component databases covering 2000+ pure components [31]. Schlumberger's Multiflash provides specialized PVT modeling for reservoir and pipeline applications [32]. VMGSim offers advanced CPA and SAFT implementations for chemical systems [33].

Among open-source options, the library described in this paper provides 60+ EOS implementations ranging from simple ideal gas through cubic (SRK, PR with multiple alpha functions), association (CPA, electrolyte-CPA), reference (GERG-2008, Span-Wagner), and molecular (PC-SAFT) models. The component database covers 200+ substances with fitted parameters for common industrial applications.

### 2.3 Process simulation in the digital twin context

Process simulation — the computational solution of mass, energy, and momentum balance equations across interconnected unit operations — provides the structural framework for digital twins. Steady-state simulation determines operating points, while dynamic simulation tracks time-varying behavior during transients, startups, and shutdowns.

For digital twin applications, the simulation engine must support both modes efficiently. Steady-state calculations provide periodic snapshots that can be computed quickly and compared against plant data. Dynamic simulations are needed for controller tuning, transient analysis, and scenarios involving time-dependent phenomena (tank filling/emptying, pipeline packing/depacking, compressor surge events) [34].

The academic process simulation literature has extensively covered steady-state sequential-modular and equation-oriented approaches [35,36]. More recently, Soares et al. [37] reviewed dynamic process simulation in the context of Industry 4.0. Cameron et al. [38] discussed the challenges of integrating process simulation with real-time data for advanced process control. However, the specific architecture needed to bridge offline simulation tools with online digital twin operation has received less systematic attention in the literature.

### 2.4 Plant historian integration

Industrial plant data resides in historian databases — time-series databases optimized for high-frequency sensor data. OSIsoft PI (now AVEVA PI) and Aspen InfoPlus.21 (IP.21) dominate the oil and gas sector, collectively serving over 90% of upstream offshore installations [39]. Connecting process models to these data sources requires:

1. **Tag mapping**: Associating logical model parameters (inlet pressure, compressor speed) with physical historian tags (e.g., `TRA-35PT3601A.PV`).
2. **Data retrieval**: Reading historical or real-time values at appropriate time intervals with control over aggregation (interpolated, averaged, min/max).
3. **Data quality handling**: Filtering bad or suspect data based on quality status flags before feeding values to the simulation.
4. **Bidirectional communication**: Writing model results back to the historian for visualization in operator displays and downstream analytics.

The tagreader-python library [40] provides a standardized Python API for accessing both PI Web API and Aspen IP.21 REST API data sources, abstracting the differences between these systems behind a unified interface.

### 2.5 Cloud deployment and operational integration

Moving a validated process model from an engineer's workstation to continuous online operation involves several architectural transitions. The model must be encapsulated as a stateless service (typically a REST API), deployed in a scalable cloud environment, connected to live data sources through middleware (e.g., Sigma, IOC CalcEngine), and monitored for health and performance [41].

Container-based deployment on platforms such as Kubernetes, combined with cloud-native application platforms (e.g., Equinor's Radix), enables horizontal scaling and automated lifecycle management. The NeqSimAPI service demonstrates this pattern, exposing thermodynamic calculations and process simulations as REST endpoints that can be called from data orchestration workflows.

### 2.6 AI and machine learning integration

The convergence of physics-based modeling with artificial intelligence is reshaping how digital twins are built and operated. Three integration paradigms have emerged [42,43]:

1. **Physics-informed ML**: Neural networks constrained by thermodynamic principles (e.g., Gibbs-Duhem consistency, mass conservation) that generalize better than pure black-box approaches [44].
2. **Hybrid modeling**: Combining first-principles equations for well-understood phenomena with data-driven models for poorly characterized effects (e.g., fouling dynamics, reservoir behavior) [45].
3. **AI-augmented workflows**: Using large language models (LLMs) and autonomous agents to accelerate model development, calibration, and maintenance — effectively reducing the expert bottleneck [46,47].

The platform described in this paper actively explores the third paradigm, with a multi-agent LLM framework that can build, validate, and maintain process simulations through natural language interaction with the thermodynamic library's API [48].

---

## 3. Platform Architecture

### 3.1 Overview

The digital twin platform comprises six interconnected layers (Fig. 1):

1. **Thermodynamic engine**: Equation-of-state models, flash algorithms, and physical property calculations.
2. **Equipment library**: Unit operations with both steady-state and transient simulation capabilities.
3. **Process orchestration**: Flowsheet management, recycle convergence, multi-area coordination.
4. **Instrumentation and control**: Measurement devices, controllers, alarm management.
5. **Data integration**: Plant historian connectivity, tag mapping, model-plant comparison.
6. **Deployment and operation**: Cloud API, live data connectors, monitoring.

Each layer is designed with clear interfaces that allow independent evolution while maintaining interoperability. The platform is implemented in Java for computational efficiency and accessed from Python through JPype [49] for interactive data science workflows.

### 3.2 Thermodynamic engine

The thermodynamic engine provides the physics foundation upon which all digital twin calculations depend. The architecture follows a compositional approach where multicomponent fluid systems are represented as collections of phases, each modeled by an equation of state.

**Equation-of-state library.** Table 1 summarizes the EOS families available in the platform.

**Table 1.** Equation-of-state families and representative implementations.

| Family | Representative Classes | Primary Applications |
|--------|----------------------|---------------------|
| Cubic | SRK, PR, PR-1978, volume-corrected variants | Hydrocarbon systems, gas processing |
| CPA (Cubic + Association) | SRK-CPA, PR-CPA, Electrolyte-CPA | Water, MEG, methanol, H2S, CO2 |
| Reference | GERG-2008, Span-Wagner, IAPWS-IF97 | Custody transfer, pure CO2, steam |
| SAFT | PC-SAFT, PC-SAFT variants | Polymers, associating mixtures |
| Activity coefficient | NRTL, UNIFAC, UNIQUAC | Liquid-liquid equilibrium |
| Specialized | Soreide-Whitson, Kent-Eisenberg, Duan-Sun | Oil/water solubility, acid gas treating |

**Flash algorithms.** Multiphase flash calculations — the determination of phase amounts and compositions at specified conditions — are performed using a hybrid successive-substitution (SS) and Newton-Raphson (NR) algorithm [50] with automatic switching based on convergence behavior. The platform supports TP, PH, PS, TV, and UV flash specifications, as well as saturation point, phase envelope, hydrate equilibrium, wax appearance, and freeze-out calculations. A systematic benchmark across 1664 natural gas compositions demonstrated convergence rates exceeding 99.5% across conditions spanning 150–350 K and 1–500 bar [51].

**Transport properties.** After flash calculations, transport properties (viscosity, thermal conductivity, diffusivity, surface tension) are computed using corresponding-states methods, friction theory, and mixture averaging rules [52]. A critical architectural decision separates the thermodynamic initialization (phase equilibrium) from physical property initialization (transport properties), allowing the more expensive transport property calculations to be deferred when only equilibrium information is needed.

### 3.3 Equipment library

The process equipment library provides 33 packages covering the full range of oil and gas process operations (Table 2).

**Table 2.** Process equipment by category.

| Category | Equipment Types | Count |
|----------|----------------|-------|
| Separation | Separator, ThreePhaseSeparator, GasScrubber | 5 |
| Compression | Compressor (with performance chart), Expander | 3 |
| Heat transfer | Heater, Cooler, HeatExchanger (shell-and-tube) | 4 |
| Valves | ThrottlingValve (isenthalpic) | 1 |
| Distillation | DistillationColumn, Condenser, Reboiler | 3 |
| Absorption | TEG absorber, SimpleAbsorber, WaterStripper | 3 |
| Mixing/splitting | Mixer, StaticMixer, Splitter, ComponentSplitter | 4 |
| Pipeline | AdiabaticPipe, PipeBeggsAndBrills, TwoFluidPipe | 3 |
| Reactors | GibbsReactor, CSTR, FurnaceBurner | 6 |
| Subsea | SubseaWell, SubseaTree, Manifold | 3 |
| Utilities | Pump, Ejector, Electrolyzer, Filter, Flare, Tank | 6 |

Each equipment class implements both steady-state (`run()`) and transient (`runTransient(dt)`) methods, enabling seamless transition between steady-state design calculations and dynamic time-stepping for digital twin operation [53].

**Stream introspection.** Every equipment class exposes its connected streams through standardized methods `getInletStreams()` and `getOutletStreams()`, enabling programmatic traversal of flowsheet topology. This capability is essential for automated P&ID generation, DEXPI export, and graph-based analysis of process connectivity.

### 3.4 Process orchestration

The `ProcessSystem` class serves as the central orchestrator for flowsheet simulation. It manages the ordered execution of unit operations, convergence of recycle loops and adjuster variables, and coordination of measurement and control devices.

**Five execution strategies.** The platform implements five distinct execution strategies (Fig. 3), selected automatically based on flowsheet topology analysis:

1. **Sequential execution**: Units processed in insertion order with iterative convergence for recycles. Default and most robust mode.
2. **Parallel execution**: Graph-based dependency analysis using Union-Find identifies independent equipment groups that can execute concurrently. Stream-level locking prevents race conditions on shared data.
3. **Hybrid execution**: Divides the flowsheet into a feed-forward section (parallel) and a recycle section (sequential), combining speed with convergence reliability.
4. **Optimized selection**: Analyzes flowsheet characteristics (presence of adjusters, recycles, multi-input equipment) and delegates to the most suitable strategy.
5. **Progress-monitored execution**: Provides callback notifications after each equipment execution step, enabling real-time progress visualization in Jupyter notebooks and monitoring dashboards.

For digital twin applications, the sequential and progress-monitored strategies are most commonly used — sequential for reliable convergence during automated operation, and progress-monitored for interactive model development.

**Multi-area coordination.** Large facilities (platforms, gas plants) are naturally divided into process areas. The `ProcessModel` class composes multiple `ProcessSystem` objects into a plant-wide model with iterative convergence:

```
plant = ProcessModel()
plant.add("separation", separationSystem)
plant.add("compression", compressionSystem)
plant.add("dehydration", dehydrationSystem)
plant.run()  // Iterates until inter-area streams converge
```

This pattern mirrors real plant organization and enables independent development and testing of each process area before integration.

### 3.5 Instrumentation and control layer

A distinctive feature of the platform's digital twin architecture is the unified treatment of measurement devices, controllers, and process equipment as first-class elements within the process model.

**Measurement devices.** The platform provides 27+ specialized measurement device classes (Table 3) that attach to streams or equipment to produce measured values with realistic signal processing.

**Table 3.** Representative measurement devices.

| Device | Measured Parameter | Typical Digital Twin Use |
|--------|-------------------|------------------------|
| PressureTransmitter | Stream/vessel pressure | Boundary condition input |
| TemperatureTransmitter | Stream/vessel temperature | Boundary condition input |
| VolumeFlowTransmitter | Volumetric flow rate | Production monitoring |
| LevelTransmitter | Separator liquid level | Dynamic control |
| WaterDewPointAnalyser | Water dew point temperature | Gas quality compliance |
| HydrocarbonDewPointAnalyser | HC dew point | Sales gas specification |
| CompressorMonitor | Compressor health metrics | Condition monitoring |
| CombustionEmissionsCalculator | CO2, NOx emissions | Environmental compliance |
| ImpurityMonitor | Phase-partitioned impurities | CO2 injection safety |
| MultiPhaseMeter | Multiphase flow rates | Well allocation |

**Tag-based role classification.** Each measurement device can be assigned a historian tag and a role:

- **INPUT**: The device receives its value from plant data, which is then injected into the simulation as a boundary condition.
- **OUTPUT**: The device calculates its value from the simulation, which is recorded for comparison or written back to the historian.
- **BENCHMARK**: The device receives plant data but does not modify the simulation; instead, the deviation between simulated and measured values is tracked for model validation.

This role classification enables a disciplined workflow: INPUT tags define the model's boundary conditions, OUTPUT tags represent its predictions, and BENCHMARK tags measure its accuracy.

**Controller devices.** PID controllers, ratio controllers, cascade structures, and logic blocks can be added to any equipment. During transient execution, the `ProcessSystem` runs a dedicated controller scan phase after equipment calculations, ensuring consistent separation of process dynamics and control actions.

**Alarm management.** An integrated alarm management system evaluates measurement values against configurable thresholds (HH/H/L/LL per IEC 61511), tracks alarm state transitions, and can trigger event bus notifications for operator alerting.

### 3.6 Auto-instrumentation

Building a dynamic model with instruments and controllers manually is time-consuming. The `DynamicProcessHelper` class automates this process:

```java
DynamicProcessHelper helper = new DynamicProcessHelper(process);
helper.setDefaultTimeStep(1.0);
helper.setPressureTuning(0.5, 50.0);   // Kp, Ti for pressure loops
helper.setLevelTuning(1.0, 100.0);     // Kp, Ti for level loops
helper.instrumentAndControl();          // Auto-creates all instruments + controllers
```

This single method call analyzes the process topology, identifies appropriate measurement points and control loops (pressure control on separators, level control on vessels, flow control on compressors), creates the necessary transmitter and controller objects with industry-standard tuning defaults, and wires them into the process model. The resulting instrumented process is immediately ready for transient simulation.

### 3.7 Data integration architecture

The platform implements a five-step workflow (Fig. 2) for connecting process models to live plant data:

**Step 1: Model building.** The process model is constructed in Python using the library's API, matching the real plant's equipment configuration, fluid composition, and design operating conditions.

**Step 2: Plant data reading.** Historical data is retrieved from PI or IP.21 historians using the tagreader-python library [40], which provides a unified Python interface supporting both PIWebAPI and AspenOne REST APIs. Tag search (with wildcards), interpolated/averaged/raw reads, and data quality flags are supported.

**Step 3: Model calibration.** The process model is run with plant boundary conditions (Step 2 inputs), and simulated results are compared against plant measurements. The tag-based role classification (Section 3.5) structures this comparison:

```python
# Set field data for INPUT-tagged instruments
process.setFieldData({
    "TT-101": measured_temperature,
    "PT-101": measured_pressure,
    "FT-101": measured_flow
})

# Run simulation with field inputs applied
process.applyFieldInputs()
process.run()

# Compare model predictions against BENCHMARK-tagged instruments
deviations = process.getBenchmarkDeviations()
```

The resulting deviation map identifies where the model differs from plant reality, guiding calibration efforts (e.g., adjusting compressor efficiency, heat exchanger fouling factor, or fluid composition).

**Step 4: Digital twin loop.** The calibrated model runs continuously, processing each new data point from the historian:

```python
for timestamp, plant_data in historian_stream:
    process.setFieldData(plant_data)
    process.applyFieldInputs()
    process.run()
    results = extract_results(process)
    write_to_historian(results, timestamp)
```

**Step 5: Cloud deployment.** The validated model is deployed as a REST API (NeqSimAPI) on a cloud platform. Data orchestration middleware (Sigma, IOC CalcEngine) automates the read-compute-write cycle, reading input tags from the historian, calling the API, and writing results back to the historian or a cloud time-series database (Omnia).

### 3.8 Interoperability

**DEXPI import/export.** The platform supports bidirectional exchange with the DEXPI (Data Exchange in the Process Industry) XML standard [54]. The `DexpiXmlWriter` exports a `ProcessSystem` to DEXPI-compliant XML including equipment attributes, stream connectivity, and instrument metadata. The `DexpiXmlReader` imports DEXPI files (potentially exported from commercial tools) into NeqSim process models. A topology resolver handles cycle detection and establishes execution order.

**JSON process builder.** A declarative JSON format enables process models to be specified as data rather than code:

```json
{
  "fluid": {"type": "SRK", "components": [...]},
  "process": [
    {"type": "Stream", "name": "feed", ...},
    {"type": "Separator", "name": "hp-sep", "inlet": "feed", ...}
  ]
}
```

The `ProcessSystem.fromJsonAndRun()` method parses the JSON, constructs the process, runs the simulation, and returns a structured result with errors, warnings, and simulation outputs. This JSON interface is the primary pathway for AI agents and web applications to interact with the simulation engine.

**MCP server integration.** A Model Context Protocol (MCP) server exposes the library's core capabilities (flash calculations, process simulation, component database queries) as structured tools that can be discovered and invoked by LLM agents [55]. This enables AI systems to use the thermodynamic engine as a computational backend without requiring prior knowledge of the Java API.

---

## 4. Comparison with Commercial and Open-Source Platforms

### 4.1 Comparison framework

Table 4 and the radar chart (Fig. 5) compare the digital twin capabilities of the platform described in this paper against commercial process simulators and other open-source alternatives. The comparison evaluates nine dimensions critical for digital twin deployment.

**Table 4.** Digital twin capability comparison across platforms.

| Capability | This Platform | Aspen HYSYS | UniSim Design | DWSIM | COCO/COFE |
|------------|:---:|:---:|:---:|:---:|:---:|
| **Thermodynamic breadth** | ++ | +++ | +++ | ++ | + |
| **Steady-state simulation** | ++ | +++ | +++ | ++ | ++ |
| **Dynamic simulation** | ++ | +++ | ++ | - | - |
| **Historian integration** | ++ | + | + | - | - |
| **Auto-instrumentation** | ++ | - | - | - | - |
| **Cloud deployment** | +++ | + | - | + | - |
| **AI/LLM integration** | +++ | - | - | - | - |
| **DEXPI interoperability** | ++ | + | - | - | - |
| **Open-source / auditability** | +++ | - | - | +++ | ++ |
| **Licensing cost** | Free | High | High | Free | Free |

Legend: +++ excellent; ++ good; + limited; - absent or minimal.

### 4.2 Thermodynamic engine comparison

Commercial simulators benefit from decades of refinement, extensive component databases (2000+ species in Aspen), and well-validated parameterizations for industrial systems. The open-source platform covers the most commonly used EOS families and 200+ components, which is sufficient for the majority of oil and gas applications. However, gaps exist in specialized areas such as electrolyte thermodynamics for amine systems, polymer-modified fluids, and some niche heavy oil correlations.

A significant advantage of the open-source approach is transparency. When a digital twin produces unexpected results, an engineer can inspect the exact equations, parameters, and numerical methods used — something not possible with proprietary engines. This auditability is particularly valued in regulatory contexts (emission reporting, fiscal metering) where calculation transparency is required [22].

### 4.3 Dynamic simulation

Aspen HYSYS Dynamics provides the most mature dynamic simulation capability, with pressure-flow solving, vessel hydraulics, and integration with operator training simulators (OTS). UniSim Design offers similar functionality with tighter integration to Honeywell control systems.

The open-source platform provides equipment-level transient methods with configurable time stepping, PID control, and bounded measurement history for long-running simulations. While less mature than HYSYS Dynamics for complex pressure-flow networks, the architecture's explicit controller scan phase, progress listener callbacks, and bounded history ring buffer are specifically designed for digital twin operation patterns where memory management and execution monitoring are critical.

The `TwoFluidPipe` class implements a transient two-fluid multiphase flow model with seven conservation equations and an AUSM+ numerical scheme, providing capabilities comparable to simplified OLGA-style pipeline analysis for slug flow, liquid holdup, and pressure wave propagation.

### 4.4 Data integration and deployment

This is where the open-source platform shows its strongest differentiation. The tag-based instrument role classification (INPUT/OUTPUT/BENCHMARK), the `setFieldData()` / `applyFieldInputs()` / `getBenchmarkDeviations()` workflow, and the integration with the tagreader-python library provide a turnkey solution for connecting process models to PI and IP.21 historians.

Commercial simulators offer their own data connectivity solutions (Aspen InfoPlus.21 integration, Honeywell Experion connectivity), but these are typically tightly coupled to their respective vendor ecosystems. Cross-platform integration — connecting an Aspen HYSYS model to an OSIsoft PI historian on a non-Aspen infrastructure — requires significant custom development [19].

The cloud deployment path (Fig. 6) — model → REST API → middleware → historian — provides a deployment architecture that is infrastructure-agnostic. The NeqSimAPI, deployed on Equinor's Radix platform, demonstrates this pattern with production workloads.

### 4.5 AI and LLM integration

The integration of AI agents with process simulation represents an emerging frontier where the open-source platform has a distinctive advantage. The MCP server interface, JSON process builder, and multi-agent framework [48] enable LLM agents to:

- Build process models from natural language descriptions
- Calibrate models against plant data
- Diagnose deviations between model and plant
- Generate engineering reports and documentation

This capability has no equivalent in commercial simulators, which were designed for human interaction through graphical user interfaces rather than programmatic AI agent interaction.

---

## 5. Use Cases

### 5.1 Compressor performance monitoring

Gas export compressors on offshore platforms are critical equipment whose performance directly affects production capacity and energy efficiency. A digital twin for compressor monitoring continuously tracks:

- Polytropic efficiency and head
- Surge margin and anti-surge valve position
- Power consumption vs. expected performance curve
- Discharge temperature deviation from isentropic prediction

The platform enables this through a straightforward workflow, as illustrated in the model-vs-plant comparison (Fig. 4):

```python
# Build compressor model
fluid = SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

feed = Stream("Comp Suction", fluid)
comp = Compressor("Export Compressor", feed)
comp.setOutletPressure(90.0, "bara")

process = ProcessSystem()
process.add(feed)
process.add(comp)

# Connect to plant data
TAG_MAP = {
    'suction_P':    'PLANT-35PT3601A.PV',
    'suction_T':    'PLANT-35TT3601A.PV',
    'discharge_P':  'PLANT-35PT3601B.PV',
    'discharge_T':  'PLANT-35TT3601B.PV',
    'flow':         'PLANT-35FT3601.PV',
    'power':        'PLANT-35JI3191F000.PV',
}

# Digital twin loop
for timestamp, row in plant_data:
    feed.setPressure(float(row['suction_P']), "bara")
    feed.setTemperature(float(row['suction_T']), "C")
    feed.setFlowRate(float(row['flow']), "Am3/hr")
    comp.setOutletPressure(float(row['discharge_P']), "bara")
    process.run()

    sim_power = float(comp.getPower("MW"))
    sim_disch_T = float(comp.getOutletStream().getTemperature("C"))
    sim_efficiency = float(comp.getPolytropicEfficiency())

    deviation_power = sim_power - float(row['power'])
    deviation_T = sim_disch_T - float(row['discharge_T'])
```

The deviation between simulated and actual power consumption indicates compressor degradation (increasing deviation suggests fouling, seal leakage, or valve problems). The deviation between simulated and actual discharge temperature can indicate changes in gas composition, intercooler fouling, or recirculation.

A representative industrial deployment showed the following model accuracy metrics over a 12-hour operating period: Mean Absolute Percentage Error (MAPE) of 1.5% for power, 0.4% for discharge temperature, and 0.8% for header pressure [56].

### 5.2 Emission monitoring from produced water

Offshore oil and gas installations must report emissions from produced water handling systems, where dissolved hydrocarbons and gases are released when produced water is depressurized and aerated before discharge. Regulatory frameworks (EU Emissions Trading System, Norwegian Environmental Agency) require accurate quantification of methane, CO2, and non-methane volatile organic compound (nmVOC) emissions [57].

The platform provides this capability through the Soreide-Whitson equation of state [58], which accurately models mutual hydrocarbon-water solubility:

```python
# Create produced water model with Soreide-Whitson EOS
fluid = SystemSoreideWhitson(273.15 + 70.0, 35.0)
fluid.addComponent("methane", 0.001)
fluid.addComponent("ethane", 0.0005)
fluid.addComponent("propane", 0.0002)
fluid.addComponent("CO2", 0.002)
fluid.addComponent("water", 0.996)
fluid.setMixingRule("classic")

# Flash to atmospheric conditions to determine emissions
ops = ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

# Gas phase composition = emissions per unit produced water
```

This model runs within the NeqSimLive framework across multiple Equinor platforms, providing continuous emission calculations that feed into regulatory reporting systems. The Soreide-Whitson model has been validated against field data showing agreement within 15% for methane emissions and 20% for nmVOC, which is within acceptable bounds for regulatory reporting given the inherent uncertainties in produced water composition [8].

### 5.3 Multi-area offshore platform modeling

A complete offshore platform involves multiple interconnected process areas: inlet separation, gas compression, gas dehydration, oil stabilization, produced water treatment, and export metering. Modeling these as a single monolithic process model is impractical and poor engineering practice. The `ProcessModel` class enables a modular approach:

```python
# Each process area developed and tested independently
separation = build_separation_process(feed_fluid)
compression = build_compression_train(separation.get("gas_export"))
dehydration = build_teg_dehydration(compression.get("compressed_gas"))
oil_stabilization = build_oil_stabilizer(separation.get("oil"))

# Compose into plant-wide model
plant = ProcessModel()
plant.add("separation", separation)
plant.add("compression", compression)
plant.add("dehydration", dehydration)
plant.add("oil_stabilization", oil_stabilization)
plant.run()

# Access converged results
conv = plant.getConvergenceSummary()
```

This architecture supports the typical industrial workflow where different engineering disciplines own different process areas and update their models independently. Cross-area consistency is maintained by the `ProcessModel`'s iterative convergence algorithm.

### 5.4 Gas quality monitoring and sales specification compliance

Gas sales contracts specify stringent requirements on composition, heating value, Wobbe index, and contaminant levels. A digital twin for gas quality monitoring uses analysis from gas chromatographs (GC) combined with process conditions to verify compliance:

```python
from neqsim import jneqsim

# Real-time GC data fed into ISO 6976 calculations
iso6976 = jneqsim.standards.gasquality.Standard_ISO6976(fluid)
iso6976.calculate()

# Sales specification parameters
wobbe_index = iso6976.getValue("WobbeIndex", "MJ/Sm3")
gross_cv = iso6976.getValue("GCV", "MJ/Sm3")
rel_density = iso6976.getValue("RelativeDensity")

# Check against contractual limits
within_spec = (wobbe_low <= wobbe_index <= wobbe_high)
```

The standard library implements ISO 6976, ISO 6578, ISO 15403, EN 16726, and UK gas quality specifications, enabling compliance checking against any major gas market specification.

---

## 6. Emerging Capabilities and Future Directions

### 6.1 AI-augmented digital twin development

The most transformative near-term development is the use of large language model (LLM) agents to accelerate digital twin creation and maintenance. The platform provides a multi-agent framework [48] with specialized agents for process simulation, fluid creation, flow assurance, mechanical design, plant data integration, and engineering deliverables generation.

In the digital twin context, this enables workflows such as:

- **Natural language model creation**: An engineer describes a process ("three-stage gas compression with intercooling from 5 to 150 bara") and the agent constructs, runs, and validates the simulation.
- **Automated model calibration**: An agent reads plant data, identifies systematic deviations, diagnoses potential causes, and adjusts model parameters.
- **Continuous documentation**: Agents maintain up-to-date documentation of digital twin configurations, calibration status, and performance metrics.

The MCP server integration enables any LLM (Claude, GPT, open-source models) to discover and invoke the platform's computational tools through the standardized Model Context Protocol, without requiring prior training on the Java API [55].

Early experience with this approach shows that agent-generated process models achieve mass and energy balance closures below 0.02% and thermodynamic property predictions within 1-3% of expert-built models, while reducing development time from days to hours for standard applications [48].

### 6.2 Hybrid physics-data digital twins

Pure physics-based models have limitations: they cannot capture phenomena not included in their mathematical formulation (fouling dynamics, asphaltene deposition rates, reservoir inflow relationships). Conversely, pure data-driven models lack physical consistency and extrapolation capability.

Hybrid approaches that combine physics-based thermodynamic calculations with data-driven corrections offer a promising middle ground for digital twins. Potential integration patterns include:

- **Residual learning**: A physics model provides the baseline prediction; a neural network learns the systematic residual between model and plant, capturing unmodeled effects.
- **Parameter estimation**: Machine learning identifies optimal model parameters (EOS binary interaction parameters, equipment efficiencies, fouling factors) from plant data, keeping the physics structure intact.
- **Surrogate acceleration**: Neural network surrogates trained on the physics model's outputs replace computationally expensive flash calculations in inner loops of optimization algorithms.

The platform's PVT simulation module already provides regression capabilities for fitting EOS parameters to experimental data. Extending this to automated online parameter estimation using streaming plant data is a natural next step.

### 6.3 Edge deployment

As IoT infrastructure matures on offshore platforms, the opportunity to run lightweight digital twin models at the edge — on the platform itself rather than in a centralized cloud — becomes attractive for latency-sensitive applications (emergency shutdown logic, anti-surge control) and for installations with limited network bandwidth.

The platform's Java implementation, which runs on any JVM-capable device, is inherently portable to edge computing hardware. A reduced configuration running only the essential thermodynamic engine and a single-area process model has a memory footprint below 100 MB, making it feasible for deployment on industrial edge gateways.

### 6.4 Autonomous digital twin operation

The ultimate vision for digital twin technology is closed-loop autonomous operation, where the digital twin not only monitors and predicts but actively controls the physical process without continuous human oversight. This corresponds to maturity level v in the Rasheed et al. [28] framework.

Key technical challenges remaining include:

- **Robustness guarantees**: Ensuring the digital twin provides safe recommendations even when faced with sensor failures, communication disruptions, or conditions outside its validated envelope.
- **Uncertainty quantification**: Real-time propagation of input uncertainty (sensor accuracy, composition variability) through the process model to produce confidence intervals on predictions.
- **Adaptive model maintenance**: Automated detection of model degradation and triggering of recalibration without human intervention.
- **Regulatory acceptance**: Demonstrating to regulators that an autonomous digital twin meets the safety, reliability, and audit requirements for process control.

The platform provides building blocks toward this vision — the alarm management system, mass balance tracking, bounded measurement history, and event bus architecture support continuous monitoring and anomaly detection. The AI agent framework provides a pathway to automated diagnosis and response.

### 6.5 Domain-specific extensions

Several domain-specific extensions are actively being developed to expand the platform's digital twin applicability:

**Carbon capture and storage (CCS).** The CO2 injection well analyzer, transient wellbore model, flow correction framework, and impurity monitor provide specialized capabilities for CCS digital twins, where tracking CO2 phase behavior, impurity enrichment, and well integrity under varying injection conditions is critical.

**Hydrogen systems.** Hydrogen production (through electrolysis), blending in natural gas networks, and compressed hydrogen storage involve thermodynamic conditions where reference-quality EOS (Leachman equation for H2) and specialized mixing rules are essential. The platform's existing Leachman EOS implementation provides a foundation for hydrogen digital twins.

**Subsea processing.** Subsea boosting, separation, and compression — increasingly deployed for deepwater field development — require digital twins that account for seawater heat transfer, pressure-temperature profiles along risers and flowlines, and hydrate management across the entire subsea system.

### 6.6 Standardization and interoperability

The adoption of industry data standards is critical for digital twin scalability:

**DEXPI** (ISO 15926-14) provides a standard for plant topology exchange, enabling digital twin models to be created from engineering design data and validated against as-built plant documentation.

**CFIHOS** (Capital Facilities Information Handover Specification) standardizes the data handover between design and operations phases, providing the foundation for automated digital twin initialization from project data.

**OPC-UA** provides real-time data exchange between field devices and digital twin applications, complementing historian-based approaches with direct sensor connectivity.

**ISO 23247** defines a framework for manufacturing digital twins that is increasingly being adapted for process industries.

The platform's DEXPI import/export and JSON process builder provide the interfaces needed to participate in this emerging standards ecosystem.

---

## 7. Discussion

### 7.1 Advantages of the open-source approach

The open-source nature of the platform provides several advantages specifically relevant to digital twin deployments:

**Auditability.** Every equation, parameter, and algorithm is inspectable. For regulated applications (emission reporting, fiscal metering), this transparency is essential for meeting audit requirements.

**Extensibility.** Users can add new EOS implementations, equipment models, measurement devices, and integration connectors without waiting for vendor product roadmaps. Several of the platform's specialized capabilities (Soreide-Whitson for produced water emissions, CompressorMonitor, CombustionEmissionsCalculator) were contributed by users addressing specific industrial needs.

**Cost structure.** The absence of per-seat or per-deployment licensing fees fundamentally changes the economics of digital twin deployment. Instead of a few high-cost digital twin licenses for critical equipment, organizations can deploy models across their entire asset portfolio.

**Community-driven quality.** Open-source development enables a global community of academic and industrial users to identify bugs, validate models against independent data, and contribute improvements. This distributed quality assurance is particularly valuable for thermodynamic models where validation against diverse experimental data strengthens confidence.

### 7.2 Limitations and mitigation strategies

**Thermodynamic breadth.** The component database (200+ species) is smaller than commercial alternatives (2000+ in Aspen). Mitigation: focus on oil and gas applications where the most common components are well covered, and provide mechanisms for user-defined component addition.

**Dynamic simulation maturity.** The transient simulation capabilities, while functional and deployed in production, are less mature than dedicated dynamic simulators (HYSYS Dynamics, Dymola). Mitigation: architect transients as equipment-level methods that can evolve independently, and prioritize the most industrially relevant transient scenarios (separator level dynamics, compressor transients, pipeline packing).

**User interface.** The platform is API-first with no graphical flowsheet editor, which creates a steeper learning curve for engineers accustomed to drag-and-drop simulation interfaces. Mitigation: the AI agent framework significantly reduces this barrier by enabling natural language interaction, and Jupyter notebooks provide an interactive development environment with inline visualization.

**Validation coverage.** While individual models are validated (EOS against NIST data, flash algorithms against benchmark suites, equipment models against published correlations), systematic end-to-end validation of complete digital twin deployments requires access to proprietary plant data that cannot be published. Mitigation: provide standardized benchmark cases, and work with industrial partners to publish anonymized validation results.

### 7.3 Industrial deployment experience

The platform has been deployed in production digital twin applications across the Norwegian Continental Shelf, primarily for emission monitoring and gas quality calculations. Key lessons from these deployments include:

1. **Composition accuracy dominates model fidelity.** The largest source of error in digital twin predictions is typically the accuracy of the feed composition, not the thermodynamic model itself. Investment in online GC data quality delivers greater returns than EOS sophistication.

2. **Robustness trumps accuracy.** A model that always returns a physically reasonable result with 2% error is more valuable than one that achieves 0.5% accuracy in 98% of cases but diverges catastrophically in the remaining 2%. Flash algorithm robustness is therefore a higher priority than marginal accuracy improvements.

3. **Automatic startup/shutdown handling is essential.** Plant data includes periods where equipment is offline, being started up, or shutting down. The digital twin must gracefully handle these conditions rather than failing. The platform's input validation, bounded history, and error recovery mechanisms address this.

4. **Change management is underestimated.** Even with a well-calibrated model, the digital twin requires ongoing maintenance as equipment is modified, wells are brought online or shut in, and operating philosophies change. The AI agent framework's ability to automate aspects of this maintenance represents a significant operational advantage.

---

## 8. Conclusions

This paper has presented a comprehensive open-source platform for building oil and gas process digital twins, describing the architecture from the thermodynamic engine through cloud deployment. The key findings are:

1. An open-source thermodynamic library with 60+ EOS implementations, rigorous flash algorithms, and 33 equipment packages provides sufficient breadth and accuracy for the majority of oil and gas digital twin applications.

2. The unified process element model — where measurement devices, controllers, and equipment share a common interface — combined with tag-based role classification (INPUT/OUTPUT/BENCHMARK) and plant historian integration creates a structured workflow for connecting models to live plant data.

3. Auto-instrumentation capabilities reduce the effort of transitioning from steady-state design models to dynamic digital twin models from hours of manual work to a single method call.

4. The five execution strategies (sequential, parallel, hybrid, optimized, progress-monitored) address the diverse computational patterns encountered in digital twin operation, from reliable automated convergence to interactive visual development.

5. Open-source transparency, zero licensing cost, and community-driven development provide distinctive advantages for digital twin deployment at scale, particularly in regulated contexts requiring calculation auditability.

6. Emerging capabilities — AI-augmented model development, hybrid physics-data approaches, MCP-based LLM integration, and edge deployment — position the platform for the next generation of autonomous digital twin operation.

The platform is available under an open-source license at https://github.com/equinor/neqsim. Ongoing development is focused on expanding dynamic simulation capabilities, deepening AI integration, supporting CCS and hydrogen applications, and strengthening community engagement through improved documentation and collaborative benchmarking.

---

## Acknowledgements

The thermodynamic library is developed at the Norwegian University of Science and Technology (NTNU) and maintained by Equinor ASA. The authors acknowledge contributions from the open-source community and industrial partners who have validated and improved the models through production deployments.

---

## Data Availability

The complete platform source code, documentation, and example notebooks are available at https://github.com/equinor/neqsim under the Apache 2.0 license. The plant data integration patterns, including mock data generators for testing without historian access, are provided in the repository's examples and documentation.

---

## References

[1] Tao, F., Sui, F., Liu, A., Qi, Q., Zhang, M., Song, B., Guo, Z., Lu, S.C.Y., Nee, A.Y.C., 2019. Digital twin-driven product design framework. Int. J. Prod. Res. 57, 3935–3953. https://doi.org/10.1080/00207543.2018.1443229

[2] Jones, D., Snider, C., Nassehi, A., Yon, J., Hicks, B., 2020. Characterising the digital twin: A systematic literature review. CIRP J. Manuf. Sci. Technol. 29, 36–52. https://doi.org/10.1016/j.cirpj.2020.02.002

[3] Grieves, M., Vickers, J., 2017. Digital Twin: Mitigating Unpredictable, Undesirable Emergent Behavior in Complex Systems, in: Kahlen, F.-J., Flumerfelt, S., Alves, A. (Eds.), Transdisciplinary Perspectives on Complex Systems. Springer, Cham, pp. 85–113. https://doi.org/10.1007/978-3-319-38756-7_4

[4] Bravo, C.E., Saputelli, L., Rivas, F., Perez, A.G., Nikolaou, M., Zangl, G., Gringarten, A., Reservoir, S.P.E., 2014. State of the art of artificial intelligence and predictive analytics in the E&P industry: A technology survey. SPE J. 19, 547–563. https://doi.org/10.2118/150314-PA

[5] Lu, H., Guo, L., Azimi, M., Huang, K., 2019. Oil and gas 4.0 era: A systematic review and outlook. Comput. Ind. 111, 68–90. https://doi.org/10.1016/j.compind.2019.06.007

[6] DNV GL, 2020. Digital twins for the oil and gas industry. Technology Outlook 2030. DNV GL, Oslo.

[7] BP plc, 2022. BP Upstream Production Optimisation with APEX. Technical Report, BP Technology, London.

[8] Solbraa, E., 2024. NeqSim — an open source thermodynamic library for computation of fluid behavior, experiment planning and experiment interpretation. Equinor Technical Report, Trondheim.

[9] Shell, 2023. Shell Digital Twin Programme: Assessing structural integrity of assets. Shell Technical Report, The Hague.

[10] TotalEnergies, 2023. Digital Twin Deployment for Offshore Production Facilities. TotalEnergies Technology Report, Paris.

[11] Michelsen, M.L., Mollerup, J.M., 2007. Thermodynamic Models: Fundamentals and Computational Aspects, second ed. Tie-Line Publications, Holte.

[12] Kontogeorgis, G.M., Folas, G.K., 2010. Thermodynamic Models for Industrial Applications: From Classical and Advanced Mixing Rules to Association Theories. Wiley, Chichester.

[13] Venkatasubramanian, V., 2019. The promise of artificial intelligence in chemical engineering: Is it here, finally? AIChE J. 65, 466–478. https://doi.org/10.1002/aic.16489

[14] Soave, G., 1972. Equilibrium constants from a modified Redlich-Kwong equation of state. Chem. Eng. Sci. 27, 1197–1203. https://doi.org/10.1016/0009-2509(72)80096-4

[15] Peng, D.Y., Robinson, D.B., 1976. A new two-constant equation of state. Ind. Eng. Chem. Fundam. 15, 59–64. https://doi.org/10.1021/i160057a011

[16] Kontogeorgis, G.M., Voutsas, E.C., Yakoumis, I.V., Tassios, D.P., 1996. An equation of state for associating fluids. Ind. Eng. Chem. Res. 35, 4310–4318. https://doi.org/10.1021/ie9600203

[17] Kunz, O., Wagner, W., 2012. The GERG-2008 wide-range equation of state for natural gases and other mixtures: An expansion of GERG-2004. J. Chem. Eng. Data 57, 3032–3091. https://doi.org/10.1021/je300655b

[18] Dimian, A.C., Bildea, C.S., Kiss, A.A., 2014. Integrated Design and Simulation of Chemical Processes, second ed. Elsevier, Amsterdam.

[19] Klatt, K.-U., Marquardt, W., 2009. Perspectives for process systems engineering — Personal views from academia and industry. Comput. Chem. Eng. 33, 536–550. https://doi.org/10.1016/j.compchemeng.2008.09.002

[20] Venkatasubramanian, V., 2022. Artificial intelligence in reaction engineering. AIChE J. 68, e17542. https://doi.org/10.1002/aic.17542

[21] Hollender, M., 2010. Collaborative Process Automation Systems. ISA, Research Triangle Park.

[22] European Commission, 2023. EU Emissions Trading System (EU ETS) — Monitoring and Reporting Regulation (MRR), Commission Implementing Regulation (EU) 2018/2066.

[23] Tanaka, C.H., Rocha, J.T.C., 2022. DWSIM — an open-source chemical process simulator. J. Chem. Educ. 99, 2undred–2112.

[24] COCO, 2023. COCO — CAPE-OPEN to CAPE-OPEN simulation environment. https://www.cocosimulator.org (accessed 28 March 2026).

[25] Bell, C., 2024. Thermo: Thermodynamics for chemical engineering, version 0.3.0. https://github.com/CalebBell/thermo

[26] Bell, I.H., Wronski, J., Quoilin, S., Lemort, V., 2014. Pure and pseudo-pure fluid thermophysical property evaluation and the open-source thermophysical property library CoolProp. Ind. Eng. Chem. Res. 53, 2498–2508. https://doi.org/10.1021/ie4033999

[27] Kritzinger, W., Karner, M., Traar, G., Henjes, J., Sihn, W., 2018. Digital twin in manufacturing: A categorical literature review and classification. IFAC-PapersOnLine 51, 1016–1022. https://doi.org/10.1016/j.ifacol.2018.08.474

[28] Rasheed, A., San, O., Kvamsdal, T., 2020. Digital twin: Values, challenges and enablers from a modeling perspective. IEEE Access 8, 21980–22012. https://doi.org/10.1109/ACCESS.2020.2970143

[29] Darby, M.L., Nikolaou, M., Jones, J., Nicholson, D., 2011. RTO: An overview and assessment of current practice. J. Process Control 21, 874–884. https://doi.org/10.1016/j.jprocont.2011.03.009

[30] Michelsen, M.L., 1982. The isothermal flash problem. Part I. Stability. Fluid Phase Equilib. 9, 1–19. https://doi.org/10.1016/0378-3812(82)85001-2

[31] AspenTech, 2024. Aspen HYSYS v14 — Process Simulation for Chemical Engineering. https://www.aspentech.com/en/products/engineering/aspen-hysys

[32] KBC, 2024. Multiflash — Thermodynamic and Physical Properties Software. https://www.kbc.global/software/multiflash/

[33] Virtual Materials Group, 2024. VMGSim — Process Simulation Software. https://www.virtualmaterials.com/vmgsim

[34] Luyben, W.L., 2002. Plantwide Dynamic Simulators in Chemical Processing and Control. Marcel Dekker, New York.

[35] Biegler, L.T., Grossmann, I.E., Westerberg, A.W., 1997. Systematic Methods of Chemical Process Design. Prentice Hall, Upper Saddle River.

[36] Seader, J.D., Henley, E.J., Roper, D.K., 2016. Separation Process Principles, fourth ed. Wiley, Hoboken.

[37] Soares, R.M., Camara, M.M., Feital, T., Pinto, J.C., 2019. Digital twin for monitoring of an industrial polypropylene impact copolymer batch reactor. Processes 7, 916. https://doi.org/10.3390/pr7120916

[38] Cameron, D.B., Gani, R., 2011. Product and process modelling — a case study. Comput. Chem. Eng. 35, 1255–1267.

[39] ARC Advisory Group, 2023. Historian Market Analysis and Forecast. ARC Advisory Group, Dedham, MA.

[40] Equinor ASA, 2024. tagreader-python — a Python package for reading timeseries data from OSIsoft PI and Aspen IP.21. https://github.com/equinor/tagreader-python

[41] Burns, B., Grant, B., Oppenheimer, D., Brewer, E., Wilkes, J., 2016. Borg, Omega, and Kubernetes. ACM Queue 14, 70–93.

[42] Karniadakis, G.E., Kevrekidis, I.G., Lu, L., Perdikaris, P., Wang, S., Yang, L., 2021. Physics-informed machine learning. Nat. Rev. Phys. 3, 422–440. https://doi.org/10.1038/s42254-021-00314-5

[43] von Stosch, M., Oliveira, R., Peres, J., Feyo de Azevedo, S., 2014. Hybrid semi-parametric modeling in process systems engineering: Past, present and future. Comput. Chem. Eng. 60, 86–101. https://doi.org/10.1016/j.compchemeng.2013.08.008

[44] Raissi, M., Perdikaris, P., Karniadakis, G.E., 2019. Physics-informed neural networks: A deep learning framework for solving forward and inverse problems involving nonlinear partial differential equations. J. Comput. Phys. 378, 686–707. https://doi.org/10.1016/j.jcp.2018.10.045

[45] Psichogios, D.C., Ungar, L.H., 1992. A hybrid neural network — first principles approach to process modeling. AIChE J. 38, 1499–1511. https://doi.org/10.1002/aic.690381003

[46] Brown, T., Mann, B., Ryder, N., Subbiah, M., Kaplan, J.D., Dhariwal, P., Neelakantan, A., Shyam, P., Sastry, G., Askell, A., et al., 2020. Language models are few-shot learners. Advances in Neural Information Processing Systems 33, 1877–1901.

[47] Anthropic, 2024. Claude 3.5 Sonnet — Model Card. Anthropic, San Francisco.

[48] Solbraa, E., 2026. Agentic engineering: A multi-agent LLM framework for solving chemical engineering tasks with open-source thermodynamic software. Manuscript in preparation.

[49] JPype Project, 2024. JPype — Java to Python integration. https://github.com/jpype-project/jpype

[50] Michelsen, M.L., 1982. The isothermal flash problem. Part II. Phase-split calculation. Fluid Phase Equilib. 9, 21–40. https://doi.org/10.1016/0378-3812(82)85002-4

[51] Solbraa, E., 2026. Systematic characterization of a hybrid successive-substitution and Newton–Raphson flash algorithm across 1664 natural gas compositions. Manuscript in preparation.

[52] Poling, B.E., Prausnitz, J.M., O'Connell, J.P., 2001. The Properties of Gases and Liquids, fifth ed. McGraw-Hill, New York.

[53] Skogestad, S., 2008. Chemical and Energy Process Engineering. CRC Press, Boca Raton.

[54] DEXPI, 2023. DEXPI — Data Exchange in the Process Industry. https://dexpi.org

[55] Anthropic, 2024. Model Context Protocol (MCP). https://modelcontextprotocol.io

[56] Equinor ASA, 2025. NeqSimLive Performance Report — Gas Compression Digital Twin, Troll A Platform. Internal Technical Report, Equinor, Trondheim.

[57] Norwegian Environmental Agency, 2024. Guidelines for Emission Reporting from Petroleum Activities on the Norwegian Continental Shelf. Norwegian Environmental Agency, Oslo.

[58] Soreide, I., Whitson, C.H., 1992. Peng-Robinson predictions for hydrocarbons, CO2, N2, and H2S with pure water and NaCl brine. Fluid Phase Equilib. 77, 217–240. https://doi.org/10.1016/0378-3812(92)85105-H
