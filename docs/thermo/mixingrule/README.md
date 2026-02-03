---
title: "Mixing Rules Package"
description: "Documentation for mixing rules in NeqSim equations of state."
---

# Mixing Rules Package

Documentation for mixing rules in NeqSim equations of state.

## Table of Contents
- [Overview](#overview)
- [Classic Mixing Rules](#classic-mixing-rules)
- [CPA Mixing Rules](#cpa-mixing-rules)
- [Activity Coefficient Mixing Rules](#activity-coefficient-mixing-rules)
- [Binary Interaction Parameters](#binary-interaction-parameters)

---

## Overview

**Location:** `neqsim.thermo.mixingrule`

Mixing rules define how pure component EoS parameters combine in mixtures.

---

## Classic Mixing Rules

### Van der Waals One-Fluid Rules

$$a_m = \sum_i \sum_j x_i x_j \sqrt{a_i a_j}(1 - k_{ij})$$

$$b_m = \sum_i x_i b_i$$

### Usage

```java
// Classic mixing rule
fluid.setMixingRule("classic");
// or
fluid.setMixingRule(2);
```

### Asymmetric kij

```java
// Set binary interaction parameter
fluid.getInterphaseProperties().setParameter("kij", "CO2", "methane", 0.12);
```

---

## CPA Mixing Rules

For associating systems (water, alcohols, amines).

### Usage

```java
// CPA-specific mixing rule
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(T, P);
fluid.setMixingRule(10);
```

### Cross-Association

Cross-association between different associating molecules is handled via combining rules:

$$\epsilon^{A_iB_j} = \frac{\epsilon^{A_i} + \epsilon^{B_j}}{2}$$

$$\beta^{A_iB_j} = \sqrt{\beta^{A_i}\beta^{B_j}}$$

---

## Activity Coefficient Mixing Rules

### Huron-Vidal

```java
// SRK with Huron-Vidal mixing
SystemSrkHuronVidal fluid = new SystemSrkHuronVidal(T, P);
fluid.setMixingRule("HV");
```

### Wong-Sandler

```java
// Wong-Sandler mixing rule
fluid.setMixingRule("WS");
```

### Schwarzentruber-Renon

Combines EoS with UNIFAC.

```java
SystemSrkSchwartzentruberRenon fluid = new SystemSrkSchwartzentruberRenon(T, P);
```

---

## Binary Interaction Parameters

### Accessing kij

```java
double kij = fluid.getInterphaseProperties().getParameter("kij", "CO2", "methane");
```

### Setting kij

```java
fluid.getInterphaseProperties().setParameter("kij", "CO2", "methane", 0.12);
```

### Temperature-Dependent kij

$$k_{ij}(T) = k_{ij}^0 + k_{ij}^1 \cdot T + k_{ij}^2 \cdot T^2$$

---

## Mixing Rule Numbers

| Number | Mixing Rule |
|--------|-------------|
| 1 | No mixing |
| 2 | Classic (Van der Waals) |
| 4 | Huron-Vidal |
| 7 | Wong-Sandler |
| 9 | Schwarzentruber-Renon |
| 10 | CPA |

---

## Related Documentation

- [INTER Table Guide](../inter_table_guide) - Binary parameters database
- [Mixing Rules Guide](../mixing_rules_guide) - Detailed guide
- [Thermo Package](../) - Package overview
