---
title: "Frequently Asked Questions (FAQ)"
description: "NeqSim (Non-Equilibrium Simulator) is a Java library for thermodynamic calculations and process simulation, specializing in oil and gas applications. It provides:"
---

# Frequently Asked Questions (FAQ)

## Table of Contents
- [General](#general)
- [Installation & Setup](#installation--setup)
- [Thermodynamics](#thermodynamics)
- [Process Simulation](#process-simulation)
- [Physical Properties](#physical-properties)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

---

## General

### What is NeqSim?

NeqSim (Non-Equilibrium Simulator) is a Java library for thermodynamic calculations and process simulation, specializing in oil and gas applications. It provides:
- Rigorous equations of state (SRK, PR, CPA, GERG-2008)
- 50+ unit operations for process modeling
- Physical property calculations (viscosity, density, thermal conductivity)
- Pipeline and multiphase flow simulation
- Dynamic simulation capabilities

### Where can I find the API documentation?

The full JavaDoc is available at <https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/blob/master/javadoc/site/apidocs/index.html>.

### Is NeqSim open source?

Yes, NeqSim is open source under the Apache 2.0 license. You can freely use, modify, and distribute it.

### Who maintains NeqSim?

The project is developed by Equinor with contributions from the community. Contact Even Solbraa (<esolbraa@gmail.com>) for questions.

### What Java version is required?

NeqSim requires Java 8 or higher. Java 11+ is recommended for best performance.

---

## Installation & Setup

### How do I add NeqSim to my project?

**Maven:**
```xml
<dependency>
    <groupId>com.equinor.neqsim</groupId>
    <artifactId>neqsim</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'com.equinor.neqsim:neqsim:3.0.0'
```

**Direct Download:**
Download the shaded JAR from [GitHub releases](https://github.com/equinor/neqsimsource/releases).

### How do I build NeqSim from source?

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install
```

On Windows, use `mvnw.cmd install`.

### How do I run the tests?

After cloning the repository, execute:

```bash
./mvnw test
```

To run a specific test:
```bash
./mvnw test -Dtest=YourTestClassName
```

---

## Thermodynamics

### Which equation of state should I use?

| Application | Recommended EOS |
|------------|-----------------|
| General natural gas | `SystemSrkEos` |
| Reservoir/PVT | `SystemPrEos` |
| Water + hydrocarbons | `SystemSrkCPAstatoil` |
| Glycol dehydration | `SystemSrkCPAstatoil` |
| High-accuracy natural gas | `SystemGERG2008Eos` |
| CO2 capture/CCS | `SystemSrkCPAstatoil` or `SystemSpanWagnerEos` |
| Electrolytes/brine | `SystemElectrolyteCPAstatoil` |

### How do I set up a mixing rule?

```java
// For SRK/PR - use classic van der Waals mixing rule
system.setMixingRule("classic");  // or system.setMixingRule(2);

// For CPA - use CPA mixing rule
system.setMixingRule(10);

// For advanced mixing (Huron-Vidal with NRTL)
system.setMixingRule("HV", "NRTL");
```

### Why does my flash calculation not converge?

Common causes and solutions:
1. **Near critical conditions**: Reduce step size or try different initial conditions
2. **Very heavy fractions**: Check that TBP characterization is correct
3. **Extreme T/P conditions**: Verify values are within model validity range
4. **Missing components**: Ensure all components have database parameters

### How do I calculate a phase envelope?

```java
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.calcPTphaseEnvelope();

double[] cricondenbar = ops.get("cricondenbar");
double[] cricondentherm = ops.get("cricondentherm");
```

### How do I handle plus fractions?

```java
// Add plus fraction with average properties
system.addPlusFraction("C10+", 10.0, 0.200, 820.0);

// Set characterization model
system.getCharacterization().setTBPModel("PedersenSRK");
system.getCharacterization().setPlusFractionModel("Pedersen");
system.getCharacterization().characterisePlusFraction();
```

---

## Process Simulation

### How do I create a simple process?

```java
ProcessSystem process = new ProcessSystem();

// Add feed stream
Stream feed = new Stream("Feed", fluid);
process.add(feed);

// Add equipment
Separator sep = new Separator("Sep", feed);
process.add(sep);

// Run
process.run();
```

### Equipment names must be unique - why?

`ProcessSystem` uses equipment names as identifiers. Use unique names:
```java
Separator sep1 = new Separator("HP Separator", stream1);
Separator sep2 = new Separator("LP Separator", stream2);  // Different name
```

### How do I implement a recycle?

```java
Recycle recycle = new Recycle("Recycle");
recycle.addStream(separator.getLiquidOutStream());
recycle.setTolerance(1e-6);
process.add(recycle);

// Connect output to upstream mixer
mixer.addStream(recycle.getOutletStream());
```

### How do I set compressor efficiency?

```java
// Isentropic efficiency
compressor.setIsentropicEfficiency(0.75);
compressor.setUsePolytropicCalc(false);

// OR polytropic efficiency
compressor.setPolytropicEfficiency(0.80);
compressor.setUsePolytropicCalc(true);
```

---

## Physical Properties

### How do I get viscosity?

```java
// After running flash
ops.TPflash();

// Gas viscosity
double gasVisc = system.getPhase("gas").getViscosity("cP");

// Liquid viscosity
double liqVisc = system.getPhase("oil").getViscosity("cP");
```

### What viscosity models are available?

- **Friction Theory (f-theory)**: Default for liquids
- **Lohrenz-Bray-Clark (LBC)**: For gas/oil systems
- **Corresponding States (CS)**: For heavy oils
- **Chung**: For gases

See [Viscosity Models](viscosity_models) for details.

### How do I get the speed of sound?

```java
double soundSpeed = system.getPhase("gas").getSoundSpeed();  // m/s
```

### How do I get interfacial tension?

```java
double ift = system.getInterfacialTension(0, 1);  // Phase indices
```

---

## Troubleshooting

### NullPointerException after creating system

Always run flash before accessing properties:
```java
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();  // REQUIRED before accessing properties
```

### "Component not found" error

Check component name spelling. Use exact names from the database:
```java
// Correct
system.addComponent("methane", 1.0);
system.addComponent("n-butane", 1.0);  // Note: n-butane, not nbutane

// Find available components
// Check database or use system.getComponentNames()
```

### Very slow calculations

1. Reduce number of pseudo-components in characterization
2. Use simpler EOS (SRK instead of CPA) if polar components not critical
3. Reduce number of calculation increments in pipelines
4. Check for convergence issues causing many iterations

### Process doesn't converge

1. Check for reasonable initial conditions
2. Verify equipment is connected properly
3. For recycles, provide good initial guess and check tolerance
4. Try running units individually to isolate issue

---

## Contributing

### How do I contribute to NeqSim?

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Run `./mvnw verify` to check code style
5. Submit a pull request

See [Contributing Structure](../development/contributing-structure) for details.

### How do I report a bug?

Open an issue at https://github.com/equinor/neqsim/issues with:
- NeqSim version
- Java version
- Minimal code example
- Expected vs actual behavior

### Where can I ask questions?

- [GitHub Discussions](https://github.com/equinor/neqsim/discussions) - General questions
- [GitHub Issues](https://github.com/equinor/neqsim/issues) - Bug reports and feature requests
