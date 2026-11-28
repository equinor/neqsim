---
title: "Mathematical Models in NeqSim"
description: "Source-verified overview of NeqSim thermodynamic and transport model families, mixing-rule selection, initialization boundaries, and validation responsibilities."
keywords: "NeqSim, equation of state, SRK, Peng-Robinson, CPA, GERG-2008, EOS-CG, mixing rule, thermodynamic model"
---

NeqSim separates the thermodynamic model, its mixture rule, phase-equilibrium operations, and
transport-property correlations. Select each layer explicitly and validate it for the intended
components, phase topology, temperature, pressure, and composition. A class being available does
not establish accuracy for a particular engineering case.

## Cubic equations of state

NeqSim's cubic equations of state can be written in the general form

$$P=\frac{RT}{v-b}-\frac{a(T)}{(v+\epsilon b)(v+\sigma b)}$$

where $P$ is absolute pressure, $T$ is temperature, $v$ is molar volume, $R$ is the gas constant,
$a(T)$ is the temperature-dependent attraction parameter, and $b$ is the co-volume parameter.

Common families include:

- **Peng-Robinson (PR):** `SystemPrEos` and related volume-corrected or alpha-function variants.
  These are widely used for hydrocarbon gas, condensate, and oil systems.
- **Soave-Redlich-Kwong (SRK):** `SystemSrkEos` and its specialized variants. SRK remains a
  common gas-processing baseline.
- **Cubic-Plus-Association (CPA):** cubic models with an association contribution for
  hydrogen-bonding systems. CPA requires its model-compatible mixing rule and parameter coverage.
- **Model-specific cubic hybrids:** Huron-Vidal, Wong-Sandler, UMR-PRU, and Søreide-Whitson
  workflows add model-specific liquid-phase or aqueous-mixture behavior.

Do not choose a model from the family name alone. Confirm component coverage, mixture parameters,
caloric behavior, and phase-equilibrium evidence against the operating envelope.

## Complete Java 8 model-selection example

The program below selects SRK and the database-backed classic mixing rule by enum name, performs a
TP flash, initializes properties, and checks the configured rule and resulting state.

<!-- mathematical-models-example: cubic-eos-selection -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class MathematicalModelsQuickStart {
  private static final Logger logger =
      LogManager.getLogger(MathematicalModelsQuickStart.class);

  private MathematicalModelsQuickStart() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("n-heptane", 0.10);
    fluid.setMixingRule(EosMixingRuleType.CLASSIC);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double densityKgM3 = fluid.getDensity("kg/m3");
    assert fluid.getMixingRule() == EosMixingRuleType.CLASSIC;
    assert fluid.getNumberOfPhases() >= 1;
    assert Double.isFinite(densityKgM3);
    assert densityKgM3 > 0.0;

    logger.info(
        "Model: {}, mixing rule: {}, phases: {}, density: {} kg/m3",
        fluid.getModelName(),
        fluid.getMixingRule(),
        fluid.getNumberOfPhases(),
        densityKgM3);
  }
}
```

The literal fence is compiled with Java 8 source/target, run with assertions, and owned by
[`MathematicalModelsDocumentationTest`](../../src/test/java/neqsim/documentation/MathematicalModelsDocumentationTest.java).
Run the focused regression with
`./mvnw -Dtest=MathematicalModelsDocumentationTest test`.

## Select the mixture rule independently

The system class selects the thermodynamic model. The mixing rule determines how mixture
parameters and excess-Gibbs contributions are assembled. Prefer `EosMixingRuleType` names over
legacy integers:

| Enum | Legacy value | Meaning |
|---|---:|---|
| `NO` | 1 | Classic cubic rule with every binary interaction parameter set to zero |
| `CLASSIC` | 2 | Classic cubic rule with binary interaction parameters from the NeqSim database |
| `CLASSIC_HV` | 3 | Huron-Vidal rule with database parameters |
| `HV` | 4 | Huron-Vidal rule including the temperature-dependent HVDijT term |
| `WS` | 5 | Wong-Sandler rule |
| `CPA_MIX` | 7 | Classic CPA mixture rule with CPA binary interaction parameters |
| `CLASSIC_T_CPA` | 9 | Temperature-dependent CPA binary interaction parameters |
| `CLASSIC_TX_CPA` | 10 | Temperature- and composition-dependent CPA interaction parameters |
| `SOREIDE_WHITSON` | 11 | Søreide-Whitson aqueous-gas mixture rule |

Legacy value 2 is **not** Huron-Vidal, and legacy value 1 is **not** the database-backed classic
rule. Likewise, value 4 selects `HV`, not Wong-Sandler. Named enums make these distinctions
reviewable and avoid silently changing the intended thermodynamics.

Some system families impose additional compatibility requirements. CPA-family and UMR-PRU
workflows must use the rule documented for that model; a numerically valid but incompatible rule
can return a physically misleading state. See the
[Mixing Rules Guide](mixing_rules_guide) and
[Fluid Creation Guide](fluid_creation_guide) for current model-specific setup.

## Reference Helmholtz equations

Reference equations use multiparameter Helmholtz-energy formulations rather than a cubic
pressure-explicit form:

- **GERG-2008** (`SystemGERG2008Eos`) targets covered natural-gas mixtures.
- **EOS-CG** (`SystemEOSCGEos`) extends the covered mixture space for combustion-gas and
  carbon-capture applications.

These models have explicit component and validity boundaries. See
[GERG-2008 and EOS-CG](gerg2008_eoscg) and the
[Cryogenic EOS Validation](cryogenic_eos_validation) guide for source-labelled regression
evidence and limitations.

## Activity-coefficient and electrolyte models

NRTL, UNIQUAC, UNIFAC, Huron-Vidal, Wong-Sandler, and electrolyte formulations are not
interchangeable labels. Their standard states, parameters, and supported phase-equilibrium
workflows differ. Configure the intended system class and its compatible rule, then verify that
the required components and interactions are present.

Use the [Electrolyte CPA Model](ElectrolyteCPAModel),
[Pitzer Parameter Provenance](pitzer_parameter_provenance), and
[Electrolyte Phase Boundaries](electrolyte_phase_boundaries) guides for supported aqueous and
electrolyte workflows. Do not infer electrolyte accuracy from a successful hydrocarbon flash.

## Hydrate and solid models

Hydrate and solid calculations require explicit phase/model setup and dedicated operations.
Separate hydrate structure, inhibitor, and reference-state assumptions from the underlying fluid
equation of state. Start with the [Hydrate Models Guide](hydrate_models),
[Empirical Solid Fugacity References](empirical_solid_fugacity), and
[Solid Helmholtz Models](solid_helmholtz_models).

## Transport-property boundary

A thermodynamic model establishes equilibrium and thermodynamic state functions. Viscosity,
thermal conductivity, diffusion, and interfacial tension use separate property models. After the
applicable flash, initialize the required properties before reading them. Model selection for
transport properties does not replace the equilibrium calculation.

See [Physical Property Calculations](physical_properties) and the maintained
[physical-properties package guide](../physical_properties/README).

## Engineering validation

For a design or operating calculation, record:

1. thermodynamic system class and mixing rule;
2. component and binary-parameter coverage;
3. temperature, pressure, composition, and phase-topology range;
4. property initialization and unit basis;
5. convergence, material, energy, pressure, and fugacity closure where applicable; and
6. comparison with representative measurements or a traceable reference.

A converged flash is numerical evidence, not model qualification. Preserve the model's documented
validity boundary and escalate unsupported compositions or phase regimes instead of tuning
parameters silently.
