# Physical-property model selection and validation

Use this reference for viscosity, density, thermal conductivity, diffusion and
gas-oil-water interfacial tension. Keep the thermodynamic EOS/mixing rule,
phase-specific transport model and interfacial model as separate choices.

## Find the tested implementation

| Need | Guide | Executable source evidence |
| --- | --- | --- |
| Model sets and initialization | `docs/physical_properties/README.md` | `src/test/java/neqsim/physicalproperties/PhysicalPropertiesPackageDocumentationTest.java` |
| Viscosity and tuning | `docs/physical_properties/viscosity_models.md` | Search tests for the selected model, including the package documentation test |
| Conductivity | `docs/physical_properties/thermal_conductivity_models.md` | Inspect the selected model's tests and applicable fluid range |
| Binary, effective and Fick diffusion | `docs/physical_properties/diffusivity_models.md` | Follow the guide's coefficient conventions and source assertions |
| Surface/interfacial tension | `docs/physical_properties/interfacial_properties.md` | Follow the guide's two- and three-phase test references |
| Liquid density models | `docs/physical_properties/density_models.md` | Distinguish physical-property density from EOS volume translation |

Paths above are relative to the NeqSim repository root. In plugin-only sessions,
use runtime API inspection and retrieve the corresponding guide from the matching
release; do not assume that source-checkout scripts are installed toolkit modules.

## Calculation contract

1. Specify composition basis, EOS, mixing rule, phase options, T/P and units.
   Check parameter availability for the actual components, especially water,
   glycols, methanol, salts and heavy fractions. SRK and PR parameters are not
   interchangeable merely because a mixing rule has the same name.
2. Establish phase equilibrium; inspect the resulting phase types. Guard every
   named-phase read. Never assume phase indexes 0/1/2 mean gas/oil/water, or
   manufacture an interface when one phase is absent.
3. Select the physical-property model set or phase-specific model with the exact
   documented enum/key. Model-set selection and property-type initialization
   are different APIs: `initPhysicalProperties("GLYCOL")` does not select the
   glycol model set. Model keys can be case-sensitive.
4. Initialize affected properties after changes in T/P/composition, phase state
   or model selection. Thermodynamic initialization alone does not initialize
   transport properties. At a user-facing result boundary, use the established
   `initProperties()` pattern; for a measured numerical hot path consult
   `neqsim-thermodynamic-initialization` before selecting narrower initialization.
5. Report the phase, model, unit and validity range with each result. Distinguish
   dynamic viscosity (Pa s), kinematic viscosity (m²/s), conductivity (W/(m K)),
   diffusion (m²/s), density (kg/m³) and interfacial tension (N/m).
6. Distinguish Maxwell-Stefan binary coefficients from Fick and effective
   coefficients. Follow the guide's required effective-coefficient calculation
   step and component indexing; they are not interchangeable diffusivities.
7. For three phases, evaluate each physically present interface separately.
   Keep gas-oil, gas-aqueous and oil-aqueous pairs, model selection and units in
   the result. Density differences alone do not qualify the tension model.

## Validation and speed

Record the exact source/JAR version and selected models. Compare against
independent data for the required fluid, property and range; a positive value
or a successful flash is only a sanity check. Check repeat runs, changed state,
nearby conditions, phase appearance/disappearance and two-/three-phase cases
when applicable. Do not mask invalid values, silent fallbacks or missing phases.

For performance changes, benchmark the complete caller workflow after warm-up
against an unchanged baseline. Preserve initialization dependencies, derivative
consistency and phase-pair dispatch. Avoid evaluating every transport model in
inner flash iterations, but do not reuse properties after their state changes.
