# Cover Letter

Dear Editor,

We are pleased to submit our manuscript titled **"Open-Source Thermodynamic and Process Simulation as Computational Backbone for AI-Driven Integrated Production Optimization: Capabilities, Gaps, and a Development Roadmap"** for consideration in *Computers & Chemical Engineering*.

## Summary

This paper addresses a timely problem at the intersection of process systems engineering and artificial intelligence: how should open-source process simulators evolve to serve as computational foundations for AI-driven production optimization? While considerable research has focused on developing AI methods (surrogates, reinforcement learning, digital twins) for process optimization, comparatively little attention has been given to the systematic requirements that these methods place on their underlying simulation tools.

## Key Contributions

We make the following contributions:

1. **Requirements taxonomy.** We define five functional requirement categories (surrogate training, RL environments, hybrid simulation, digital twin coupling, agentic AI access) that process simulators must satisfy for effective AI integration.

2. **Quantitative capability assessment.** We map an existing open-source thermodynamic and process simulation platform against these 25 specific requirements, finding 72% full coverage, 20% partial coverage, and 8% gaps.

3. **Software architecture proposals.** For each identified gap, we propose concrete interface specifications and implementation strategies — including a `SurrogateEquipment` class for hybrid physics-AI simulation and a Gymnasium-compatible RL environment wrapper.

4. **Computational feasibility analysis.** We demonstrate that direct-simulation reinforcement learning (without surrogates in the inner loop) is feasible for moderate-complexity process flowsheets at the platform's estimated 15–120 ms execution time.

5. **Identification of the equipment utilization framework as a distinctive RL asset.** We show how the platform's existing bottleneck analysis infrastructure provides structured observation vectors and reward signals that naturally map to RL-based optimization.

## Relevance to CACE

The manuscript aligns with *CACE*'s scope in cyberinfrastructure, intelligent systems, and process simulation. The development roadmap addresses the community's need for AI-ready open-source simulation tools as AI methods increasingly enter industrial process optimization.

## Competing Interest Statement

One author (E. Solbraa) is employed by Equinor ASA, an industry partner in the referenced research project. The remaining authors declare no competing interests.

The manuscript has not been submitted elsewhere and all authors have approved the submission.

We look forward to your consideration.

Sincerely,

Even Solbraa, Ashkan Jahanbani Ghahfarokhi, Thomas Alan Adams II
