---
title: "Optional oil-water emulsion separation studies"
description: "On-demand dense emulsion-layer studies with published coalescence physics, disclosed screening estimates, conservation checks and agent discovery."
---

# Optional oil-water emulsion separation studies

Use this study to assess whether incoming dispersed water can be removed by
coalescence or accumulates in a water-in-oil emulsion layer. It is a separate,
explicitly invoked calculation. Existing separator, thermodynamic flash and
process execution paths do not call it or allocate its objects.

The model is a diagnostic control volume. It does not change the supplied
separator's fluid, outlet streams, level controllers or live inventory, and it
does not predict outlet oil-in-water/water-in-oil specifications. Fully coupled
vessel/outlet behavior requires additional modeling and validation.

## Physical basis and applicability

The kernel implements the constant-area dense-packed-zone model of
Zlobin et al. (2026), *Modeling the separation of water-in-oil emulsions in
continuously fed gravity settlers using millifluidic experiments*,
[DOI 10.1002/aic.70429](https://doi.org/10.1002/aic.70429).

The experiments use millimetric, nearly monodisperse droplets of brine in
dodecane with controlled surfactants. The paper does not establish predictive
kinetics for arbitrary crude oils, high-pressure gas-bearing emulsions, solids,
foam, oil-in-water dispersions, droplet breakup or chemical-specific dosing.
The first monolayers are affected by vessel walls and excluded from the paper's
comparison; predictions near an empty layer are an extrapolation.

Supply an effective horizontal oil-water interface area in m². The paper assumes
constant area. For a horizontal drum the interface area depends on level and
effective length; the circular vessel cross section is not the settling area.
An explicit constant-area approximation must be justified for a plant study.
Compare predicted heights with the available vessel height before interpretation.

## Governing equations

Let $R$ be representative droplet radius (m), $\gamma$ oil-water interfacial
tension (N/m), $\Delta\rho=\rho_w-\rho_o>0$ (kg/m³), $A$ interface area (m²),
and $\omega$ the contact-area coalescence rate (m⁻² s⁻¹).
The paper fixes $c=0.26$, $K=7.3$ and $\lambda=1$.

$$\ell_c^2=\frac{\gamma}{\Delta\rho g},\qquad\ell=\frac{\ell_c^2}{R}$$

The bottom oil fraction $\phi$ lies between zero and $c$; it is neither feed
water cut nor a uniform layer oil fraction. The published functions are

$$G(\phi)=K\frac{(c-\phi)^2}{\sqrt{\phi}}$$

$$F(\phi)=K(\sqrt c-\sqrt\phi)\left(3+\frac{c^{3/2}}{\sqrt\phi}\right)+\frac K2(3-2c-c^2)\ln\left[\frac{(1+\sqrt\phi)(1-\sqrt c)}{(1-\sqrt\phi)(1+\sqrt c)}\right]$$

$$S(\phi)=\left(\frac{1-\phi}{1-c}\right)^{1/3}\frac{K(c-\phi)^2}{K(c-\phi)^2+2(1-\phi)\sqrt\phi}$$

The conserved dispersed-water depth is $x=V_{w,DPZ}/A=\ell G(\phi)$;
layer height is $L=\ell F(\phi)$. The coalescence flux and balance are

$$q_c=\frac{4\pi}{3}\lambda\omega R^3S(\phi),\qquad\frac{dx}{dt}=\frac{Q_{w,in}}A-q_c$$

$$q^*=\frac{4\pi}{3}\lambda\omega R^3(1-c)^{-1/3},\qquad Q^*=Aq^*$$

There is a finite steady layer only for $0<Q_{w,in}<Q^*$. At or above the
critical flux the unbounded mathematical column has no finite steady height;
a real vessel reaches its geometry/operating limit instead. Increasing
$\omega$ increases critical capacity. One sentence in the paper's discussion
states the opposite, but its equations and limiting behavior establish this sign.
The implementation uses the exact constitutive law, not the later approximate
rational fit.

## Calculated properties and missing measurements

The separator adapter copies its already calculated fluid before initializing
properties. Oil/water densities and their interfacial tension come from that
snapshot. No study action re-flashes or modifies the live separator. Inspect the
EOS, fluid characterization, transport models and interfacial-tension model
before relying on these properties. Mixture properties alone do not determine
emulsion stability or a particular chemical's performance.

With no kinetic measurements, screening uses disclosed guesses for a
representative DPZ droplet radius and an effective contact-coalescence time.
The starting guesses are $R=1.35$ mm and $t_c=60$ s. The radius matches the
paper's model-droplet scale; the time is an engineering assumption, not a
universal petroleum-emulsion constant.

$$\omega_{estimate}=\frac1{\pi R^2t_c}$$

This mapping is a screening assumption about an effective contact area and
waiting time. It is not an independently validated film-drainage or adsorption
law. Mark results `ESTIMATED_UNCALIBRATED`. Vary the radius and time explicitly
to assess sensitivity; resulting ranges are scenarios, not confidence intervals.
Measured/fitted $\omega$ with provenance takes priority and still needs
independent validation. Do not infer a demulsifier dose, temperature dependence,
or electrical-coalescer performance from this estimate.

## Executable example

The following SI-input example needs no experimental data. Its radius and
contact time are disclosed guesses; it is not a calibrated plant prediction.
The same statements are executed by `EmulsionSeparationStudyTest`.

```java
EmulsionSeparationStudy study = new EmulsionSeparationStudy(1.0, 0.01, 800.0, 1000.0)
    .guessKinetics(1.35e-3, 60.0);
EmulsionSeparationStudy.Result result = study.run(1.0e-5, 600.0, 1.0);
double heightM = result.getFinalLayerHeightM();
double criticalFlowM3PerS = result.getCriticalWaterFlowM3PerS();
```

Import `neqsim.process.equipment.separator.emulsion.EmulsionSeparationStudy`.
For a calculated three-phase vessel, the constructor accepting that separator
and an explicit effective area copies its oil/water properties. Invoke `run`
only when the diagnostic study is wanted. Preserve kinetics provenance,
warnings and sensitivity scenarios with the result.

## Validation and interpretation

- Check the dispersed-water ledger: initial + supplied = accumulated + coalesced.
- Compare timestep refinements, non-negative states, no-feed drainage and the
  steady/growing boundary.
- Compare the exact constitutive functions with the authors' published numerical
  master curves, using rounding-aware tolerances.
- Assess experimental layer-height histories separately; report fitted
  parameters, initial-condition alignment, errors and applicability.
- Test that the source separator and its outlets are unchanged after a study.

Primary data are available under CC BY 4.0 at
[Zenodo concept DOI 10.5281/zenodo.17463071](https://doi.org/10.5281/zenodo.17463071),
version record [18781329](https://zenodo.org/records/18781329).
Keep equation reproduction, experimental agreement and plant qualification as
separate evidence levels.

The tests retain seven attributed rows from the published numerical master
curves and nine raw observations from the Figure 7a 3 mL/min experiment. The
critical-flow case with $R=1.35$ mm, $\omega=9500$ m⁻² s⁻¹ and
$A=\pi(0.0175\ \mathrm{m})^2$ gives $Q^*=6.2485632$ mL/min.
The numerical-curve tolerances account for the authors' printed rounding.

The limited experimental reconstruction starts from the measured 2.7778 mm
layer at 200 s, uses 3 mL/min feed until 680 s and then no feed, with
$\gamma=2.9$ mN/m, assumed $\Delta\rho=250$ kg/m³ and the supplied numerical
trajectory's fitted $\omega=9300$ m⁻² s⁻¹. One-second implicit steps give about
**0.832 mm RMSE** and **0.763 mm mean absolute error** across the nine selected
observations. This is not a fresh fit to those points or a full reproduction of
all laboratory settings. Measurement noise, including negative measured layer
heights late in drainage, remains in the raw fixture and is not a physical model
state. The attributed test-resource headers record source files and conditions.

## Agentic discovery

Use `@emulsion-separation` and the `neqsim-emulsion-separation` skill for separator
rag-layer growth, oil/water emulsion troubleshooting and coalescence-limited
capacity studies. The package route is
`neqsim.process.equipment.separator.emulsion`. Source capability discovery
locates its Java APIs, guide and tests; a navigation route is not a newly deployed
MCP operation or evidence of field qualification.

For inlet hydraulics, settling and demisting use the existing
[enhanced entrainment model](separator-entrainment-modeling.md). For bulk
emulsion viscosity and phase inversion use the
[emulsion viscosity calculator](../../pvtsimulation/flowassurance/emulsion_viscosity_calculator.md).
Pass calibrated evidence to production-chemistry and process-model workflows
when comparing operating actions.
