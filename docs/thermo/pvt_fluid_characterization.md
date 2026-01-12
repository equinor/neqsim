# PVT and Fluid Characterization

Accurate phase behavior predictions start with a realistic fluid description. NeqSim supports full compositional models, TBP cuts, and black-oil style pseudo-components.

## Building Compositions
1. **Known components**: Add pure components directly using critical-property data from the internal database.
2. **Plus fractions (C7+)**: Use `addPlusFraction(name, moles, molarMass, density)` when only overall heavy fraction data are available.
3. **TBP/assay data**: Use `addTBPfraction(name, moles, density, molarMass)` to preserve multiple heavy cuts with their own boiling ranges.

```java
SystemInterface oil = new SystemSrkEos(323.15, 150.0);
oil.createDatabase(true);
oil.addComponent("nitrogen", 0.01);
oil.addComponent("methane", 0.60);
oil.addTBPfraction("C7", 0.08, 0.73, 7.5);
oil.addTBPfraction("C10", 0.10, 0.80, 10.5);
oil.addPlusFraction("C20+", 0.21, 0.92, 22.0);
oil.setMixingRule(2);
```

## Tuning and Regression
- **Binary interaction parameters (kij)**: Adjust kij tables (`setBinaryInteractionParameter`) to match dew/bubble points.
- **Volume shift/critical-point matching**: Enable volume corrections on PR/SRK fluids to improve density fits.
- **Plus-fraction splitting**: Use `splitTBPfraction` to subdivide heavy cuts using predefined distillation curves.
- **Viscosity tuning**: Adjust heavy-end Watson K or user-defined viscosity correlations when matching lab rheology.

## Lumping Models

After plus-fraction splitting generates many single-carbon-number (SCN) components, lumping groups them for computational efficiency.

### Fluent API (Recommended)

```java
// PVTlumpingModel: Preserve C6-C9 TBP fractions, lump only C10+ into 6 groups
oil.getCharacterization().configureLumping()
    .model("PVTlumpingModel")
    .plusFractionGroups(6)
    .build();

// Standard model: Lump all heavy fractions (C6-C80) into 5 pseudo-components
oil.getCharacterization().configureLumping()
    .model("standard")
    .totalPseudoComponents(5)
    .build();

// Custom boundaries: Match PVT lab report groupings (C6, C7-C9, C10-C19, C20+)
oil.getCharacterization().configureLumping()
    .customBoundaries(6, 7, 10, 20)
    .build();

// No lumping: Keep all individual SCN components (for detailed studies)
oil.getCharacterization().configureLumping()
    .noLumping()
    .build();
```

### Lumping Model Comparison

| Model | Behavior | Use Case |
|-------|----------|----------|
| `PVTlumpingModel` | Preserves C6-C9 as individual pseudo-components, lumps only C10+ | Standard PVT matching |
| `standard` | Lumps all heavy fractions from C6 onwards | Minimal components for fast simulation |
| `no lumping` | Keeps all individual SCN components | Detailed compositional studies |

For more details on the mathematical background, see [Fluid Characterization Mathematics](../pvtsimulation/fluid_characterization_mathematics.md).

## Asphaltene Modeling

For fluids with asphaltene precipitation risk, use the `PedersenAsphalteneCharacterization` class:

```java
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;

// Create asphaltene characterization
PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
asphChar.setAsphalteneMW(750.0);     // Molecular weight g/mol
asphChar.setAsphalteneDensity(1.10); // Density g/cm³

// Add asphaltene as pseudo-component (before mixing rule)
asphChar.addAsphalteneToSystem(oil, 0.02);  // 2 mol% asphaltene
oil.setMixingRule("classic");

// Perform TPflash with asphaltene detection
boolean hasAsphaltene = PedersenAsphalteneCharacterization.TPflash(oil);
```

NeqSim supports two asphaltene phase types:
- `PhaseType.ASPHALTENE`: Solid asphaltene with literature-based properties
- `PhaseType.LIQUID_ASPHALTENE`: Pedersen's liquid approach using cubic EOS

## PVT Reports
After running `ThermodynamicOperations.TPflash()`, collect standard PVT outputs:
```java
oil.initProperties();
System.out.println("Bo at separator: " + oil.getPhase("oil").getVolume() / oil.getTotalNumberOfMoles());
System.out.println("GOR at separator: " + oil.getPhase("gas").getNumberOfMoles()/oil.getPhase("oil").getNumberOfMoles());
```
For multi-stage separators, clone the fluid after each flash and continue flashing at downstream conditions.

## Data Management
- Store compositions and tuned parameters as JSON using `toJson()` for reproducible studies.
- Use `addFluid(existingSystem)` to combine live-oil and gas-cap fluids or to merge lab and model data sets.
- When integrating with black-oil simulators, export pseudo-component properties (MW, density, Z-factor) for each stage.
