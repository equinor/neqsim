# Thermodynamic Workflows

Use these recipes to configure fluids and run equilibrium calculations with NeqSim. The Java snippets mirror the workflow used in other language bindings.

## 1. Build a Fluid
```java
SystemInterface fluid = new SystemPrEos(313.15, 80.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.05);
fluid.addTBPfraction("C7+", 0.10, 0.45, 8.0); // name, moles, density [g/cc], MW
fluid.createDatabase(true); // enable access to component data
fluid.setMixingRule(1); // classical van der Waals mixing rule
fluid.init(0);
```

Tips:
- Always call `createDatabase(true)` before adding TBP fractions so critical properties and acentric factors are filled automatically.
- Use `addPlusFraction` for simpler heavy-end inputs, or `addFluid` to merge two existing systems.

## 2. Choose a Model and Mixing Rule
- `setMixingRule(1)`: classical quadratic kij.
- `setMixingRule(2)`: Huron–Vidal (gamma-phi) coupling.
- `setMixingRule(4)`: Wong–Sandler (NRTL-based) coupling.
- `setMixingRule(7)`: Simplified CPA cross-association rules.

For lean gas, start with PR and kij from correlations; for rich liquids or polar systems, move to SRK-Twu + Huron–Vidal or CPA-SRK.

## 3. Run Thermodynamic Operations
Instantiate `ThermodynamicOperations` with the configured fluid to access flash and envelope tools:
```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();
System.out.println("Vapor fraction: " + fluid.getPhaseFraction(0));
```
Common operations include:
- `PSflash(pressure, entropy)`, `PHflash(pressure, enthalpy)` for process simulators.
- `dewPointTemperature(pressure)` and `bubblePointPressure(temperature)` for PVT lab matches.
- `calcPTphaseEnvelope()` and `calcPseudocriticalTemperature()` for compositional screening.

## 4. Save and Reuse States
Export an EOS state to JSON or clone fluids when sweeping conditions:
```java
SystemInterface clone = fluid.clone();
clone.setTemperature(280.0);
clone.setPressure(10.0);
new ThermodynamicOperations(clone).TPflash();
```

## 5. Debugging and Validation
- Call `display()` on the fluid to dump compositions, kij values, and phase properties.
- Use `calcChemicalEquilibrium()` after setting reaction stoichiometry to couple reactions into flashes.
- Compare `getMolarMass()` and `getZ()` against lab PVT data to verify characterization accuracy.
