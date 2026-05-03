---
title: Process Optimization Framework
description: Entry point for NeqSim process optimization documentation: optimizer selection, getting started, and verified code patterns.
---

# Process Optimization Framework

This page is the landing page for NeqSim process optimization. Use it to find the right optimizer quickly and start with verified code patterns for `ProcessSystem` and `ProcessModel` optimization workflows.

> **New to process optimization?** Start with the [Optimization & Constraints Guide](OPTIMIZATION_AND_CONSTRAINTS) for a comprehensive overview of all optimization and constraint capabilities.

## Related Documentation

| Document | Description |
|----------|-------------|
| **[Optimization & Constraints Guide](OPTIMIZATION_AND_CONSTRAINTS)** | **COMPREHENSIVE: Complete guide to optimization algorithms, constraint types, bottleneck analysis** |
| [Optimization Overview](OPTIMIZATION_OVERVIEW) | **START HERE**: When to use which optimizer |
| [Compressor Optimization Guide](COMPRESSOR_OPTIMIZATION_GUIDE) | Multi-train compressor optimization with VFD, driver curves, and two-stage approach |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) | Equipment capacity strategies, constraint evaluation, throughput optimization, sensitivity analysis, and FlowRateOptimizer integration |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | Complete examples for ProductionOptimizer |
| [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) | Core constraint definition and bottleneck detection |
| [Getting Started](getting-started) | Step-by-step workflow for first optimization run |
| [Batch Studies](batch-studies) | Sensitivity analysis with parameter sweeps |
| [Multi-Objective Optimization](multi-objective-optimization) | Pareto optimization for conflicting objectives |
| [Flow Rate Optimization](flow-rate-optimization) | FlowRateOptimizer and lift curves |
| [Constraint Framework](constraint-framework) | Unified ProcessConstraint interface for all optimizer layers |
| [Data Reconciliation and Steady-State Detection](data-reconciliation) | R-statistic SSD, WLS reconciliation, gross error detection, SSD-to-reconciliation bridge |
| [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) | Python/SciPy integration |

## Quick Start

1. Read [Getting Started](getting-started).
2. Choose optimizer with [Optimization Overview](OPTIMIZATION_OVERVIEW).
3. Implement constraints with [Constraint Framework](constraint-framework).
4. For multi-objective studies, use [Multi-Objective Optimization](multi-objective-optimization).
5. For NLP with equalities/inequalities, use [SQP Optimizer](sqp_optimizer).

## What changed in this page

- Removed outdated framing that described this page as a calibration-only document.
- Clarified that this is a process optimization entry point.
- Added direct link to a practical getting-started guide.
