# AI-Powered Integrated Production Optimization: Industrial Significance and Educational Impact for Process Systems Engineering

<!-- Target Journal: Computers & Chemical Engineering -->
<!-- Paper Type: application (perspective / discussion) -->
<!-- Generated: 2026-03-31 -->
<!-- Status: DRAFT -->

## Highlights

- Integrated AI surrogates across reservoir, flow, and process domains enable real-time optimization
- Open-source simulation tools provide reproducible foundation for AI-driven digital twins
- Equipment utilization metrics from coupled models reveal true system bottlenecks
- Research-based teaching integrates AI methods directly into engineering curricula
- Industry-academia collaboration on NCS mature assets accelerates competence development

## Abstract

Mature petroleum assets on the Norwegian Continental Shelf (NCS) are governed by complex, coupled interactions between reservoir, wells, transport systems, and process facilities, yet these domains are conventionally analyzed in isolation. Integrated asset modelling (IAM) addresses this gap but remains computationally prohibitive for real-time optimization, large-scale ensemble studies, and closed-loop digital twin operations. We present the AI-PRO framework, which combines physics-informed neural networks (PINNs), deep surrogate models, and reinforcement learning (RL) agents with open-source simulation tools to enable system-level production optimization at operational speed. The framework's modular digital twin architecture couples AI surrogates across domain boundaries while enforcing conservation laws at interfaces. We discuss how equipment utilization metrics derived from integrated simulation provide structured bottleneck maps that identify actionable optimization opportunities. Beyond its industrial significance for extending plateau production and reducing energy consumption on the NCS, the framework addresses a critical educational need: training the next generation of engineers who must be fluent in both domain-specific engineering and applied artificial intelligence. We describe how the AI-PRO project at NTNU integrates research outputs into a new course module on AI for Production Systems Engineering, MSc thesis supervision with industry co-mentoring, and an open-source software library designed simultaneously as a research and teaching tool. The dual industrial-educational mission creates reinforcing feedback loops where industry case studies provide authentic teaching material and student contributions advance the open-source codebase.

## Keywords

integrated asset modelling; physics-informed neural networks; production optimization; reinforcement learning; engineering education; digital twin; open-source simulation

---

## 1. Introduction

### 1.1 Background and motivation

Production from mature petroleum fields is rarely constrained by a single element of the production chain. Instead, total field performance depends on a chain of interacting effects: reservoir pressure decline and changing inflow potential; well performance and tubing hydraulics; pressure losses in subsea pipelines and risers; multiphase flow behavior in the transport system; separator and compressor limitations on topside facilities; and export pressure constraints from downstream infrastructure \cite{Howell2006, Ng2024}. A change in any one of these elements propagates through the entire system, often in non-intuitive ways.

On the Norwegian Continental Shelf, where fields like Ekofisk and Troll have been producing for over four decades, the coupled behavior of the full production chain determines both operational performance and economic viability \cite{NPD2024}. Despite the systemic interdependence, reservoir, flow, and process analyses are conventionally carried out in isolation by separate engineering disciplines using distinct tools. This siloed approach makes it difficult to identify the true system bottleneck, quantify the production impact of equipment constraints, or evaluate how operational changes in one domain affect overall performance.

Integrated asset modelling has been recognized as a key methodology for mature fields since the early 2000s \cite{Howell2006}. Commercial IAM platforms link reservoir simulators to multiphase flow network models and process simulators, enabling systems-level analysis. These tools can quantify equipment utilization across the process plant and identify production bottlenecks. However, such coupled frameworks rely on direct simulator-to-simulator coupling and remain computationally expensive --- typically requiring minutes to hours per evaluation \cite{Ng2022a}. This computational burden makes them unsuitable for real-time optimization loops, large-scale ensemble studies, or closed-loop digital twin architectures.

Simultaneously, the petroleum engineering profession faces a growing competence challenge. The convergence of artificial intelligence and engineering requires graduates who are fluent in both rigorous domain-specific engineering (thermodynamics, fluid mechanics, process design) and modern AI methods (deep learning, reinforcement learning, uncertainty quantification) \cite{Venkatasubramanian2019}. Traditional engineering curricula have been slow to integrate these capabilities, creating a skills gap that limits the industry's ability to adopt AI-driven workflows \cite{Chen2020}.

### 1.2 The convergence of AI and process systems engineering

Artificial intelligence and machine learning offer a transformative pathway for addressing the computational bottleneck in integrated production optimization. Physics-informed neural networks embed governing equations directly into neural network architectures, enabling models that respect physical laws even when training data is sparse \cite{Raissi2019, Karniadakis2021}. Operator learning frameworks including DeepONet \cite{Lu2021} and Fourier Neural Operators \cite{Li2021} extend these capabilities by learning mappings between function spaces, enabling rapid prediction across entire families of partial differential equations.

In parallel, reinforcement learning has demonstrated powerful capabilities for sequential decision-making under uncertainty \cite{Faria2022}. RL agents have been applied to well control and production optimization in reservoir settings \cite{Hourfar2019, Nasir2024}, and neural network surrogates have been used to accelerate thermodynamic property prediction for process optimization \cite{Schweidtmann2019}. However, the literature has targeted isolated system components and does not address the full integrated production chain simultaneously.

The emergence of agentic AI --- systems in which large language models operate as autonomous agents equipped with tools, memory, and planning capabilities \cite{Ferrag2025} --- adds a further dimension. General-purpose LLM tools can accelerate software development across all technical work packages, while domain-specific agentic workflows such as the Model Context Protocol (MCP) \cite{Anthropic2024MCP} enable direct LLM interaction with simulation engines, sensor data, and thermodynamic databases.

### 1.3 Scope and contributions of this paper

This paper provides a comprehensive discussion of the AI-PRO (AI-Powered integrated Reservoir, flow, and Process Optimization) project, examining both its industrial significance for the Norwegian petroleum sector and its educational impact for engineering programs at NTNU. Specifically, we make the following contributions:

1. We articulate the industrial challenges of integrated production optimization on mature NCS assets and discuss how AI-driven surrogate models can address the computational efficiency barrier while preserving physical consistency at domain interfaces (Section 2).

2. We present the AI-PRO framework architecture, including the modular digital twin design, the equipment utilization methodology for systematic bottleneck identification, and the role of open-source simulation tools as the computational backbone (Section 3).

3. We discuss the educational integration strategy, including a new course module on AI for Production Systems Engineering, industry-connected MSc thesis supervision, and the design of an open-source software library that serves as both a research and teaching resource (Section 4).

4. We analyze the synergies between the industrial and educational missions, identifying concrete feedback mechanisms that amplify impact in both domains (Section 5).

5. We discuss broader implications for engineering education and industry practice, including the role of LLM-assisted development and challenges in maintaining physical rigor in AI-augmented workflows (Section 6).

---

## 2. Industrial Context and Challenges

### 2.1 Production system complexity on the NCS

A typical producing asset on the NCS comprises an interconnected chain of subsystems: the reservoir with its pressure-dependent inflow behavior; wells with tubing hydraulics and artificial lift; subsea gathering systems with multiphase flow in pipelines and risers; topside process facilities with separators, compressors, heat exchangers, and export systems; and downstream export infrastructure with contractual pressure and quality specifications. The operational settings distributed across this chain --- choke positions, injection rates, routing choices, separator pressures, compressor operating points, and export targets --- number in the hundreds for a typical field \cite{Ng2024}.

The key engineering challenge is that these subsystems are dynamically coupled. A reduction in separator pressure on the topside increases drawdown on the wells, potentially improving inflow from the reservoir --- but simultaneously changes the gas-oil ratio entering the compressor train, which may hit capacity limits. An increase in water injection rate can maintain reservoir pressure and extend plateau production, but the additional water must be handled by the separation system, consuming capacity that might otherwise be available for oil processing. These interactions propagate through the entire system in non-obvious ways, making it impossible to optimize any single subsystem in isolation without considering its impact on the whole \cite{Howell2006}.

### 2.2 Limitations of current integrated asset modelling

Integrated asset models address the need for systems-level analysis by coupling domain-specific simulators. A reservoir model (e.g., Eclipse, OPM Flow) is linked to a multiphase flow network model (e.g., OLGA, Pipesim) and a process simulator (e.g., HYSYS, NeqSim) through interface variables: pressures, flow rates, temperatures, and fluid compositions at the boundaries between domains.

While IAM provides the correct engineering approach, several limitations constrain its practical impact:

**Computational cost.** A single coupled evaluation requires solving reservoir flow equations, wellbore and pipeline hydraulics, and process thermodynamics in a coordinated fashion. Depending on model fidelity, this takes minutes to hours per evaluation, making it impractical for optimization studies that require thousands of evaluations \cite{Ng2022a}.

**Disciplinary silos.** Different simulators use different data formats, different numerical methods, and different convergence strategies. Integration requires significant software engineering effort and is fragile --- updates to any component can break the coupling \cite{Du2025}.

**Limited uncertainty quantification.** Running ensemble studies for uncertainty quantification requires thousands of coupled evaluations, which is computationally prohibitive with full-physics models. Operators must rely on single-point estimates or simplified proxy models that may not capture coupled effects \cite{Zhong2021}.

**Reactive rather than proactive optimization.** Due to computational constraints, IAM is typically used for planning studies (well placement, facility design) rather than real-time operational optimization. Operators make daily decisions based on experience and simplified heuristics rather than model-informed optimization \cite{Howell2006}.

### 2.3 The equipment utilization perspective

A critical insight is that the process model in an integrated framework serves not only to calculate plant conditions, but also to quantify the utilization of key process equipment. For each piece of equipment --- compressors, separators, valves, pipelines, heat exchangers --- the utilization can be expressed as the percentage of available capacity currently being used.

This structured picture of equipment utilization provides the foundation for targeted optimization. By computing utilization metrics across the full integrated model, operators can identify:

- **Active bottlenecks**: equipment at or near 100% utilization that constrain total system throughput.
- **Latent capacity**: equipment operating well below rated capacity that could accommodate additional production.
- **Sensitivity hotspots**: equipment whose utilization changes disproportionately in response to operating parameter adjustments.

An integrated simulation model that computes these metrics systematically transforms production optimization from an art (experienced engineers making intuitive adjustments) into a science (data-driven identification and ranking of optimization opportunities). This methodology provides a quantitative basis for operational decision-making that is currently absent from most field operations.

### 2.4 The energy efficiency and emissions dimension

More efficient production operations directly contribute to the environmental targets of the Norwegian Climate Action Plan \cite{NorwegianClimate2021}. The NCS petroleum sector accounts for a substantial fraction of Norway's greenhouse gas emissions, with gas turbine-driven compressors being the dominant emission source. Optimizing compressor operation through better load balancing across the integrated system can reduce specific energy consumption per barrel of oil equivalent produced.

Similarly, separator optimization reduces recirculation and reprocessing, and lower wellhead backpressure extends natural drive recovery before artificial lift is required, deferring the energy cost of gas lift or water injection. Each of these improvements requires understanding the coupled system response --- exactly the capability that AI-driven integrated optimization provides.

---

## 3. The AI-PRO Framework

### 3.1 Architecture overview

The AI-PRO framework is structured around three core technical pillars: (i) physics-informed surrogate models that emulate each domain of the production system at a fraction of the computational cost; (ii) a modular digital twin architecture that couples surrogates with open-source simulators across domain boundaries with enforced physical conservation; and (iii) reinforcement learning agents for closed-loop, multi-variable production optimization with embedded uncertainty quantification.

The framework is designed to be modular and extensible. Each domain surrogate (reservoir, wellbore/pipeline, process) can be independently developed, validated, and updated. The coupling layer enforces conservation laws (mass, energy, momentum) at the interfaces between domains, ensuring that the physically consistent behavior of the full-physics simulation is preserved in the surrogate-mediated digital twin.

### 3.2 Physics-informed surrogate models

The central innovation is training surrogate models that do not merely fit input-output relationships but incorporate the governing physics as structural constraints. For the reservoir domain, this means embedding Darcy's law, material balance, and multiphase flow equations into the neural network loss function \cite{Raissi2019}. For the wellbore and pipeline domain, the Beggs and Brill or OLGA-type multiphase flow correlations provide the physical constraints. For the process domain, thermodynamic equations of state (Soave-Redlich-Kwong, Peng-Robinson) and mass/energy balance closures serve as the physics priors \cite{Schweidtmann2019, Michelsen2007}.

The surrogate models are trained on datasets generated by running the full-physics simulators (OPM Flow for reservoir, NeqSim for process) across the relevant operating envelope: varying pressures, temperatures, flow rates, and fluid compositions that span the expected operating range of the NCS asset under study. The physics constraints serve two purposes: they reduce the amount of training data required (because the model does not need to learn first principles from data alone), and they ensure physically plausible predictions even in regions of the operating space where training data is sparse.

Operator learning frameworks such as DeepONet \cite{Lu2021} and Fourier Neural Operators \cite{Li2021} are particularly relevant for the reservoir domain, where the surrogate must learn to map initial conditions and boundary conditions to time-dependent pressure and saturation fields. These frameworks learn function-to-function mappings rather than point-to-point correlations, enabling rapid prediction for new well configurations or operating strategies without retraining.

### 3.3 Interface conservation and digital twin coupling

A key technical challenge is ensuring physical conservation at the interfaces between domain surrogates. At the reservoir--wellbore interface, mass flow rates and pressures must be consistent. At the wellbore--process interface, flow rates, compositions, temperatures, and pressures must satisfy continuity constraints. Violation of these constraints leads to physically meaningless results that accumulate errors as the coupled system is iterated.

The AI-PRO framework addresses this through a conservation enforcement layer that operates at each domain interface. This layer implements hard constraints rather than soft penalty terms, ensuring that mass and energy are exactly conserved at every evaluation. The enforcement mechanism draws on techniques from constrained neural network optimization, where the network architecture itself guarantees constraint satisfaction rather than relying on penalty-based training \cite{Raissi2019}.

The digital twin API is designed as a standardized middleware layer that enables plug-and-play coupling: any domain surrogate that satisfies the interface contract (defined variable types, units, and conservation constraints) can be swapped in or out without modifying the rest of the system. This modularity is critical for both research (enabling comparison of different surrogate architectures) and operations (enabling incremental updates as better models become available).

### 3.4 Open-source simulation backbone

The open-source tools OPM Flow \cite{OPM2024} and NeqSim \cite{NeqSim2024} serve as the computational backbone for the framework. OPM Flow provides industry-grade reservoir simulation based on the open Norne and SPE benchmark datasets, while NeqSim provides thermodynamic calculations (equations of state, flash calculations, phase equilibria) and process simulation (separators, compressors, heat exchangers, pipelines, distillation columns).

The choice of open-source tools is motivated by three considerations:

**Reproducibility.** All simulation results can be independently verified by any researcher with access to the public codebase, eliminating the reproducibility barriers inherent in proprietary simulation software.

**Extensibility.** AI surrogates can be embedded directly into the simulation loop. For example, a trained neural network for thermodynamic property prediction can replace the equation-of-state evaluation inside NeqSim's flash algorithm during surrogate-mediated simulation runs, enabling hybrid architectures, where some calculations use the rigorous model and others use the fast surrogate.

**Educational accessibility.** Students can inspect, modify, and learn from the full source code rather than interacting with a black-box commercial tool. This transparency is essential for the educational mission described in Section 4.

NeqSim in particular provides a comprehensive Java-based API for thermodynamic and process simulation that has been validated against industrial data and is actively maintained by Equinor and NTNU. Its programmatic interface makes it well-suited for integration with AI frameworks, and its existing support for agentic workflows through the Model Context Protocol \cite{Anthropic2024MCP} demonstrates the potential for LLM-mediated simulation access.

### 3.5 Reinforcement learning for multi-variable optimization

The ultimate goal is deploying RL agents that optimize production decisions across the full integrated system. The RL agent observes the current state of the system (pressures, flow rates, equipment utilizations, fluid compositions) and selects actions (choke adjustments, separator pressure changes, routing decisions, injection rate modifications, compressor operating points) to maximize a cumulative reward signal that balances production rate, energy efficiency, equipment integrity, and safety margins.

The key challenges are:

**Multi-variable action space.** Typical NCS fields have dozens of controllable variables that interact non-linearly. The RL agent must learn a policy over this high-dimensional action space without exhaustive exploration.

**Coupled constraints.** Actions taken in one domain (e.g., changing a choke position) have consequences in other domains (e.g., change in separator liquid level) that the RL agent must anticipate through its learned model of system dynamics.

**Uncertainty-aware decisions.** Operators need not just recommended actions but also confidence bounds. The RL agent must embed uncertainty quantification --- whether through Bayesian deep learning \cite{Gal2016}, ensemble methods, or conformal prediction \cite{Angelopoulos2023} --- to communicate prediction confidence and enable risk-aware decision-making.

**Safety constraints.** Certain operating regions are forbidden (maximum pressures, minimum flow rates, equipment trip limits). The RL agent must respect hard safety constraints at all times, which requires constrained policy optimization methods rather than standard RL algorithms \cite{Faria2022}.

The AI surrogates from Section 3.2 serve as the RL training environment. Because the surrogates evaluate thousands of times faster than the full-physics models, the RL agent can explore and learn from millions of simulated episodes, acquiring a rich understanding of system dynamics that would be impossible with the original simulators alone.

### 3.6 Equipment utilization methodology

The framework introduces a systematic methodology for deriving equipment utilization metrics from integrated simulation outputs. For each major piece of process equipment, utilization is computed as:

$$U_i = \frac{Q_i^{\text{actual}}}{Q_i^{\text{rated}}} \times 100\%$$

where $Q_i^{\text{actual}}$ is the current operating parameter (flow rate, power, duty, or other relevant metric) and $Q_i^{\text{rated}}$ is the design rated capacity. The utilization vector $\mathbf{U} = [U_1, U_2, \ldots, U_n]$ across all $n$ pieces of equipment provides a structured bottleneck map.

By computing sensitivity derivatives $\partial U_i / \partial x_j$ where $x_j$ are operational variables, the framework identifies which adjustments most effectively relieve bottlenecks and redistribute capacity. The RL agent can then be trained with reward shaping that incentivizes operation within a target utilization envelope, avoiding both equipment overload and inefficient under-utilization.

---

## 4. Educational Integration

### 4.1 The competence gap in AI-augmented engineering

The petroleum engineering profession, and process systems engineering more broadly, faces a growing competence gap at the intersection of domain engineering and artificial intelligence \cite{Venkatasubramanian2019}. Graduates proficient in reservoir engineering may lack the mathematical foundations of deep learning. Graduates with strong data science skills may lack the physical intuition needed to evaluate whether an AI model's predictions are physically plausible. This gap is not merely additive --- it requires a new pedagogical approach that integrates AI methods with domain-specific engineering education from the ground up \cite{Chen2020, Zawacki2019}.

At NTNU, the departments of Geoscience and Petroleum (IGV) and Energy and Process Engineering (EPT) have strong traditions in reservoir engineering, process simulation, and thermodynamics. The AI-PRO project creates a natural vehicle for embedding AI competence into these engineering programs through research-based teaching, where methods developed in the research project are translated directly into course material and student projects.

### 4.2 New course module: AI for Production Systems Engineering

A dedicated course module covering AI-driven surrogate modelling, digital twin architectures, and data-driven optimization will be developed within the context of complex engineering systems. The module is designed to be piloted within an existing Production Engineering course at IGV and/or EPT before the project concludes. The curriculum is structured around the following learning outcomes:

**LO1: Surrogate modelling fundamentals.** Students will understand the principles of physics-informed neural networks, operator learning, and hybrid neural-numerical architectures. Hands-on exercises will involve training surrogate models for simple reservoir and process systems using the project's open-source tools.

**LO2: Digital twin architectures.** Students will learn how multiple simulation models are coupled in a digital twin framework, including the role of interface conservation, middleware design, and data integration from sensors and historians.

**LO3: Reinforcement learning for engineering optimization.** Students will implement RL agents for prototypical production optimization problems, learning to formulate reward functions, handle constraints, and evaluate policy performance.

**LO4: Uncertainty quantification and decision support.** Students will apply Bayesian and ensemble methods to quantify prediction uncertainty, and will learn to communicate confidence bounds to decision-makers in operationally meaningful terms.

**LO5: Critical evaluation of AI in engineering.** Crucially, students will learn not only how to apply AI methods but how to evaluate their reliability --- assessing physical plausibility, identifying failure modes, understanding distribution shift, and recognizing the limitations of data-driven approaches in safety-critical engineering contexts.

This last learning outcome is particularly important. Engineering education has a responsibility to produce graduates who can serve as informed practitioners and critical evaluators of AI tools, not merely users who accept model outputs uncritically.

### 4.3 MSc thesis integration

MSc thesis topics will be defined in direct connection with the project work packages, giving students hands-on experience with AI methods applied to real engineering systems. Each thesis addresses a specific sub-problem from the research, providing students with authentic research experience while contributing to the project deliverables. Examples include:

- **Surrogate model development for a specific process unit** (e.g., training a PINN for a gas compression system using NeqSim as the data generator).
- **Reinforcement learning agent for a simplified production system** (e.g., optimizing choke settings and separator pressure for a two-well subsea tieback).
- **Agentic AI workflow for simulation access** (e.g., implementing an MCP-based system that allows an LLM to interact directly with NeqSim's simulation engine, live sensor data, and thermodynamic databases without custom integrations).
- **Benchmarking surrogate accuracy** against full-physics simulation results for representative NCS operating conditions.
- **Uncertainty quantification** for surrogate-based production forecasts, comparing Monte Carlo dropout, ensemble methods, and conformal prediction.

Industry co-supervision from Equinor and Solution Seeker ensures that thesis work addresses problems with genuine operational relevance. Planned research secondments at Equinor's Trondheim office expose students to industrial workflows and decision-making contexts that are difficult to replicate in a university setting.

### 4.4 Open-source software as a teaching resource

The open-source software library produced by AI-PRO is designed simultaneously as a research tool and a teaching resource. This dual-purpose design has several educational benefits:

**Transparency.** Students can read, debug, and modify the full source code, developing a deep understanding of both the engineering models and the AI methods. This contrasts sharply with commercial tools where the underlying methods are opaque.

**Documented examples.** The library includes documented examples using publicly available benchmark datasets (SPE-10, Norne, Brugge for reservoir; standard NGL and gas processing cases for NeqSim), enabling students to reproduce published results and build on them.

**Contribution pathway.** Students can contribute code improvements, additional tests, and documentation back to the open-source repository, gaining experience with professional software development practices (version control, code review, continuous integration) alongside engineering content.

**Longevity.** Because the software is open-source and community-supported, the educational materials remain usable and maintainable after the project ends, providing a lasting contribution to the university's AI teaching portfolio that is not dependent on any single researcher's continued involvement or commercial license availability.

### 4.5 Alignment with the CDIO framework

The educational approach aligns with the Conceive-Design-Implement-Operate (CDIO) framework for engineering education \cite{Crawley2014}. Students conceive the optimization problem in the context of a real production system, design the surrogate model and RL architecture, implement the solution using the open-source tools, and operate the system in a simulated environment that mirrors real field conditions. The project-based nature of the thesis work, grounded in authentic industrial problems, addresses the call for engineering education that bridges theory and practice \cite{Mills2003, Felder2005}.

---

## 5. Synergies Between Industry and Education

### 5.1 Bidirectional knowledge transfer

The AI-PRO project is structured to create reinforcing feedback loops between its industrial and educational missions. These synergies are not incidental --- they are designed into the project structure:

**Industry-to-education.** Anonymized production data and simulation models from Equinor provide authentic case studies for both PhD research and MSc thesis work. Students learn from real operational challenges rather than textbook examples, developing practical skills that are immediately relevant to industry employment. The presence of industry co-supervisors ensures that research questions are framed in operationally meaningful terms.

**Education-to-industry.** Student contributions to the open-source codebase --- test cases, documentation, additional unit operations, validation studies --- improve the software tools that industry partners use. MSc thesis results on specific sub-problems (e.g., surrogate accuracy for a particular equipment type) feed directly into the research project's work packages. The continuous stream of fresh perspectives from students often identifies issues or opportunities that experienced researchers may overlook.

### 5.2 PhD training at the interface

The project recruits two PhD candidates with complementary profiles: one focused on reservoir and flow simulation (IGV), and one focused on process simulation (EPT). Both are co-supervised across departments and by industry partners, ensuring that they develop fluency across the full production chain rather than specializing in a single domain.

This cross-disciplinary training model produces researchers who can bridge the disciplinary silos that have historically separated reservoir, flow, and process engineering. These graduates will be uniquely positioned to lead integrated optimization efforts in industry or to establish independent research programs that transcend traditional departmental boundaries.

### 5.3 Solution Seeker partnership: bridging research and deployment

The partnership with Solution Seeker provides a unique bridge between academic research and operational deployment. Solution Seeker's existing optimization platform serves as a benchmark and integration target for the AI surrogates developed in the project. This ensures that research outcomes are tested against an operationally proven system rather than evaluated only in academic isolation.

Specifically, Solution Seeker contributes: (i) methodological expertise in optimization algorithms; (ii) access to their optimization platform for benchmarking; (iii) participation in digital twin architecture design; and (iv) co-development of the open-source optimization toolbox. This collaboration ensures that the project's research outcomes are compatible with real-world deployment requirements from the earliest stages.

### 5.4 Pathway to external funding

The project is designed to generate results that are competitive for external funding applications. Targeted programs include Research Council of Norway KPN calls (for additional PhD positions), INTPART (for international research and education collaboration), Naerings-PhD (with industry partners), and EU Horizon Europe programs on AI-driven digital twins for energy systems and decarbonization.

The combination of industrial relevance (Equinor and Solution Seeker involvement), educational impact (course development and student training), and technical novelty (first integrated PINN framework spanning the full production chain) positions the project competitively for these funding mechanisms. Success in external funding would amplify the project's impact by enabling additional PhD positions, international collaborations, and expanded industrial validation.

---

## 6. Discussion

### 6.1 Addressing the knowledge gap

The AI-PRO framework addresses a critical gap identified in the literature: no unified, open, and operationally validated framework exists that (i) couples AI surrogates seamlessly across all three production system domains; (ii) enforces physical conservation laws at domain interfaces; (iii) provides systematic equipment utilization metrics; (iv) integrates UQ into a real-time closed-loop RL optimization agent; and (v) has been demonstrated on NCS-representative mature fields \cite{Ng2022a, Howell2006}. Each of these elements exists individually in the literature, but their integration into a coherent framework remains an open challenge.

The framework's originality lies in several specific aspects. First, the hard enforcement of conservation laws at interface boundaries goes beyond the soft penalty approaches common in the PINN literature \cite{Raissi2019}, ensuring that the physically consistent behavior of full-physics simulation is preserved in the surrogate domain. Second, the equipment utilization methodology translates abstract simulation outputs into actionable bottleneck maps, bridging the gap between model results and operational decisions. Third, the modular digital twin API enables plug-and-play coupling of different surrogate architectures, facilitating systematic comparison and incremental improvement.

### 6.2 The role of LLM-assisted development

A distinctive feature of the AI-PRO project is the systematic use of general-purpose LLM tools to accelerate software development across all work packages. LLM-assisted coding accelerates data pipeline development, preprocessing scripts, PINN architecture implementation, surrogate training pipelines, digital twin API development, and RL training environment construction.

This approach carries both opportunities and risks. The opportunity is significant: LLM tools can reduce the implementation burden dramatically, enabling PhD candidates to focus on research-level problems rather than routine coding tasks. The risk is that LLM-generated code may contain subtle errors, particularly in numerically sensitive thermodynamic calculations where small implementation mistakes can lead to physically wrong results \cite{Du2025}.

The mitigation strategy involves rigorous validation at every stage: all LLM-generated code must pass a comprehensive test suite that includes mass and energy balance checks, comparison against reference solutions, and regression testing against known baselines. This validation-first approach is consistent with the broader philosophy that AI tools augment but do not replace engineering judgment.

The MCP-based agentic workflow identified in the project plan, where an LLM interacts directly with simulation engines and sensor data, represents a particularly promising development. By providing the LLM with structured access to NeqSim's thermodynamic calculations through a well-defined protocol, the workflow enables natural language interaction with the simulation engine while maintaining computational rigor through the underlying physics models. This approach has implications both for research productivity and for educational accessibility, potentially lowering the barrier to entry for students who are not yet proficient in the simulation tool's programmatic API.

### 6.3 Broader educational implications

The AI-PRO educational model addresses several challenges that are common across engineering programs worldwide:

**The integration challenge.** AI and engineering are typically taught in separate departments, by faculty with different backgrounds, using different examples and assessment methods. AI-PRO demonstrates a model where AI methods are taught within the context of engineering problems, by instructors who are themselves practicing engineers and researchers. This contextualized approach ensures that students learn not just AI techniques in the abstract, but how to apply them rigorously in safety-critical engineering contexts \cite{Zawacki2019}.

**The authenticity challenge.** Project-based learning is most effective when students work on problems they perceive as genuine and consequential \cite{Mills2003}. The direct connection to NCS production operations and the involvement of industry partners (Equinor, Solution Seeker) ensures that student projects address real problems with real stakeholders, providing motivation and context that purely academic exercises cannot match.

**The sustainability challenge.** Course materials based on proprietary tools become unusable when licensing agreements change. Open-source tools and publicly available datasets provide a sustainable foundation for curriculum development that is robust to institutional and commercial changes. The open-source software library, with documented examples and benchmark datasets, ensures that educational materials remain accessible and maintainable indefinitely.

**The critical thinking challenge.** Perhaps most importantly, the curriculum emphasizes critical evaluation of AI outputs. Students must learn to answer questions such as: Is this result physically plausible? What happens if the model is extrapolated beyond its training domain? How should prediction uncertainty be communicated to a decision-maker who is not an AI expert? These skills are essential for responsible engineering practice in an era of increasing AI adoption \cite{Venkatasubramanian2019}.

### 6.4 Limitations and risk mitigation

The project faces several technical and organizational risks that merit discussion.

**Surrogate accuracy.** Physics-informed surrogates may not achieve sufficient accuracy for production-level decisions across the full operating envelope. This risk is mitigated through hybrid architectures that selectively invoke the full-physics simulator for critical calculations, and through continuous benchmarking against reference data with clear accuracy KPIs.

**Data availability.** Access to industry production data may be delayed or restricted due to confidentiality concerns. The project mitigates this by designing all methods to work with publicly available benchmark cases (SPE-10, Norne, Brugge) before applying them to proprietary datasets.

**RL training stability.** Reinforcement learning in coupled environments with high-dimensional action spaces is prone to training instability. Conservative offline RL algorithms, extensive benchmarking against gradient-based optimizers, and leverage of established RL libraries mitigate this risk.

**Integration complexity.** A fully integrated framework coupling three domain surrogates with conservation-enforcing interfaces is ambitious for a 36-month project. The modular work package design ensures that each component delivers independent value, even if the full integration requires additional time.

### 6.5 Relevance for the Norwegian Continental Shelf

The NCS is a particularly compelling testbed for AI-driven integrated production optimization for several reasons. First, the fields are mature and well-characterized, with decades of production history and detailed simulation models, providing rich datasets for surrogate training. Second, the regulatory environment and industry culture on the NCS are receptive to innovation, with the Norwegian Petroleum Directorate actively encouraging digital transformation. Third, Norway's ambitious climate targets \cite{NorwegianClimate2021} create a strong incentive for energy efficiency improvements that integrated optimization can deliver. Fourth, the concentration of petroleum expertise in Trondheim --- with NTNU, SINTEF, Equinor, and multiple technology companies --- provides an ecosystem uniquely suited to the kind of cross-disciplinary collaboration that AI-PRO requires.

The economic significance is substantial. Even modest production optimization improvements (1--3% uplift) across the NCS portfolio translate to billions of NOK in additional value over the remaining field lifetimes. Similarly, 5--10% reductions in specific energy consumption from optimized compressor and separator operation directly reduce per-barrel greenhouse gas emissions, supporting NCS operators' decarbonization commitments.

---

## 7. Conclusions

This paper has discussed the industrial significance and educational impact of the AI-PRO framework for AI-powered integrated production optimization. The key conclusions are:

1. **The computational bottleneck in integrated asset modelling is the primary obstacle** to real-time production optimization on mature NCS fields. Physics-informed surrogate models, trained on open-source simulator data and constrained by governing equations, offer a transformative pathway to achieve the speed required for operational deployment while preserving physical consistency.

2. **Equipment utilization metrics from integrated simulation** provide a quantitative, actionable basis for bottleneck identification and optimization prioritization that is currently absent from most field operations. The systematic methodology proposed --- computing utilization vectors across all major equipment and their sensitivities to operational variables --- transforms production optimization from intuitive art to data-driven science.

3. **Open-source simulation tools are essential** for building AI-driven frameworks that are reproducible, extensible, and educationally accessible. The choice of NeqSim and OPM Flow as computational backbone enables transparent validation, embedded AI integration, and student-level code inspection that proprietary tools cannot provide.

4. **The educational integration model** --- embedding research-derived AI methods into engineering curricula through a dedicated course module, industry-connected thesis supervision, and an open-source teaching library --- addresses the growing competence gap at the intersection of engineering and AI. The emphasis on critical evaluation of AI outputs alongside technical proficiency is essential for responsible engineering practice.

5. **The dual industrial-educational mission creates reinforcing synergies** where industry case studies provide authentic teaching material, student contributions advance the open-source codebase, and PhD candidates trained at the interface of domain engineering and AI are uniquely positioned for both academic and industrial careers.

6. **LLM-assisted development accelerates** the implementation of complex multi-component frameworks, provided that rigorous validation is maintained at every stage. The MCP-based agentic workflow for direct simulation access represents a particularly promising direction for both research productivity and educational accessibility.

The AI-PRO framework, by combining advances in scientific machine learning with established engineering simulation and a structured educational integration strategy, addresses both a critical industrial need (real-time integrated production optimization on the NCS) and a critical educational need (training engineers who are fluent in both domain engineering and applied AI). The open-source, modular architecture ensures that the framework's impact extends beyond the project timeframe and partner institutions.

---

## Acknowledgements

The AI-PRO project is led by the Norwegian University of Science and Technology (NTNU), with contributions from Equinor ASA and Solution Seeker AS. The computational framework builds on the open-source NeqSim library developed at NTNU and Equinor (https://github.com/equinor/neqsim) and the Open Porous Media initiative (https://opm-project.org/).

## CRediT Author Contributions

**Even Solbraa:** Conceptualization, Methodology (process simulation framework), Software (NeqSim), Writing --- original draft, Supervision.
**Ashkan Jahanbani Ghahfarokhi:** Conceptualization, Methodology (reservoir surrogate modelling), Project administration, Writing --- review and editing, Supervision.
**Thomas Alan Adams II:** Methodology (process systems engineering and optimization), Writing --- review and editing, Supervision.

## Declaration of Competing Interest

Even Solbraa is employed by Equinor ASA, which is an industry partner in the AI-PRO project. The remaining authors declare no competing interests.

## Data Availability

The AI-PRO framework is built on open-source tools. NeqSim is available at https://github.com/equinor/neqsim under the Apache-2.0 license. OPM Flow is available at https://opm-project.org/ under the GPLv3 license. The open-source software library developed through the project will be released publicly upon completion of the validation phase.

---

## References

\cite{Howell2006}
\cite{Ng2024}
\cite{Ng2022a}
\cite{Ng2022b}
\cite{Zhong2021}
\cite{Nwachukwu2018}
\cite{Schweidtmann2019}
\cite{Schweidtmann2021}
\cite{Venkatasubramanian2019}
\cite{Grossmann2005}
\cite{Pistikopoulos2021}
\cite{Raissi2019}
\cite{Li2021}
\cite{Lu2021}
\cite{Karniadakis2021}
\cite{Faria2022}
\cite{Hourfar2019}
\cite{Nasir2024}
\cite{Gal2016}
\cite{Angelopoulos2023}
\cite{Tsai2023}
\cite{Du2025}
\cite{Tian2026}
\cite{Liang2026}
\cite{Schafer2026}
\cite{Ferrag2025}
\cite{Woo2025}
\cite{Crawley2014}
\cite{Felder2005}
\cite{Mills2003}
\cite{Chen2020}
\cite{Zawacki2019}
\cite{Rasheed2020}
\cite{NPD2024}
\cite{NorwegianClimate2021}
\cite{NeqSim2024}
\cite{OPM2024}
\cite{ERT2024}
\cite{Anthropic2024MCP}
\cite{SeiderLewinWidagdo2009}
\cite{Michelsen2007}
\cite{Edgar2001}
