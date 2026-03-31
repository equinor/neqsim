# Gap Statement

## The gap

While recent work has demonstrated LLM agents for individual aspects of chemical engineering — flowsheet construction from text (Tian et al., 2026), natural language access to process simulators (Liang et al., 2026), autonomous model building with GitHub Copilot (Schäfer et al., 2026) — **no existing framework integrates problem definition, domain research, computational simulation, systematic validation, uncertainty quantification, and engineering report generation into a single, structured, multi-agent workflow with validation gates**.

## Why this gap matters

1. **Fragmented workflows produce fragmented results**: An LLM that can build a flowsheet but cannot validate it against reference data, quantify uncertainty, or generate a formatted report delivers incomplete engineering value.

2. **Validation is non-negotiable in engineering**: Chemical engineering calculations inform safety-critical decisions (process design, well integrity, equipment sizing). Without systematic validation, AI-generated results cannot be trusted for professional use.

3. **Commercial software lock-in limits reproducibility**: Most prior work relies on proprietary simulators (Aspen Plus, HYSYS), preventing independent verification and limiting the community's ability to improve both the AI framework and the underlying computation.

4. **Single-discipline demonstrations don't prove generality**: A framework that works for flowsheet construction but not for economic evaluation, mechanical design, or safety analysis has limited practical value.

## How we fill it

Our agentic engineering framework addresses all four aspects:
- **End-to-end integration**: Three-stage pipeline from task definition to formatted deliverables
- **Systematic validation**: Benchmark comparison, mass/energy balance checks, Monte Carlo uncertainty, risk assessment
- **Open-source foundation**: Full reproducibility with rigorous thermodynamic library
- **Multi-discipline demonstration**: Eight case studies across six engineering disciplines
