# Literature Map — Agentic Engineering Framework

## Thread 1: LLM Agents for Chemical Process Simulation

| Citation | Method | Contribution | Limitations | Relevance |
|----------|--------|-------------|-------------|-----------|
| Tian et al. (2026) | Multi-agent LLM workflow | Text-to-simulation for Aspen Plus | Limited to flowsheet construction; commercial software | HIGH — closest prior work |
| Du & Yang (2025) | Review/analysis | Maps challenges of LLM agents in process simulation | Review only; no implementation | HIGH — frames the problem |
| Liang et al. (2026) | LLM agent for simulation | Natural language → DWSIM commands | Single agent; limited validation | HIGH — parallel approach |
| Schäfer et al. (2026) | GitHub Copilot + flowsheet | Autonomous process model building | Simplified models; pre-defined thermo | HIGH — demonstrates agentic AI in flowsheets |
| Lee et al. (2024) | GIPHT prompt engineering | Process improvement generation | Single-task; no end-to-end workflow | MEDIUM |
| Liu et al. (2024) | ASA-GPT | Automated simulation agent for MD | Different domain (molecular dynamics) | MEDIUM |
| Shinde & Bhosale (2026) | LLM copilot for P&ID | Automated P&ID generation in EPC | Conceptual framework only | LOW |

## Thread 2: Agentic AI Frameworks and Architecttic ures

| Citation | Method | Contribution | Limitations | Relevance |
|----------|--------|-------------|-------------|-----------|
| Ferrag et al. (2025) | Comprehensive review | Maps LLM → autonomous agent evolution | Survey, no engineering application | HIGH — architectural reference |
| Abou Ali et al. (2025) | Survey | Agentic AI architectures and applications | General, not engineering-specific | HIGH |
| Gridach et al. (2025) | Survey | Agentic AI for scientific discovery | Science-focused, not engineering | MEDIUM |
| Wei et al. (2025) | Survey | From AI for science to agentic science | Broad scope | MEDIUM |
| Hu et al. (2024) | ADAS | Automated design of agentic systems | Meta-design, not domain-specific | MEDIUM |
| Hughes et al. (2025) | Multi-expert analysis | AI agents adoption mechanisms | Industry perspective, not technical | LOW |

## Thread 3: AI in Engineering Design

| Citation | Method | Contribution | Limitations | Relevance |
|----------|--------|-------------|-------------|-----------|
| Jiang et al. (2025) | ID 4.0 paradigm | Agentic AI for engineering design | Vision paper, limited implementation | MEDIUM |
| Maldonado-Romo et al. (2026) | S5 framework | Agentic AI for sustainable design | Not process engineering | MEDIUM |
| Abbas & Wahab (2024) | Multi-agent LLM | Agile development optimization | Software dev, not engineering sim | LOW |
| Gudipati (2025) | Tool-guided agentic AI | Precision in petroleum engineering | Single-domain; limited scope | MEDIUM |

## Thread 4: AI Code Generation for Scientific Computing

| Citation | Method | Contribution | Limitations | Relevance |
|----------|--------|-------------|-------------|-----------|
| Ylihurula et al. (2024) | GitHub Copilot | Scientific computing code generation | Satellite sim; not chemical engineering | MEDIUM |
| Dhruv & Dubey (2025) | LLM code translation | Scientific computing in HPC | Code translation, not workflow | LOW |
| Shi et al. (2025) | Fine-tuned LLM | MD agent for thermodynamic parameters | Molecular dynamics, not process sim | MEDIUM |
| Carreira-Munich (2024) | DEVS Copilot | AI-assisted formal simulation modeling | DEVS formalism, not chemical engineering | LOW |

## Thread 5: Foundational Methods

| Citation | Method | Contribution | Relevance |
|----------|--------|-------------|-----------|
| Yao et al. (2023) | ReAct | Reasoning + acting in LLMs | HIGH — our agent architecture pattern |
| Schick et al. (2024) | Toolformer | Self-taught tool use by LLMs | HIGH — tool-augmented paradigm |
| Lewis et al. (2020) | RAG | Retrieval-augmented generation | HIGH — knowledge grounding |
| Vaswani et al. (2017) | Transformer | Attention mechanism | Foundation |
| Brown et al. (2020) | GPT-3 | Few-shot learning in LLMs | Foundation |
