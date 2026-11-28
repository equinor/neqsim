---
title: "Non-equilibrium two-phase pipe flow"
description: "Non-equilibrium gas/oil heat and mass transfer in the TwoFluidPipe transient: multicomponent film model, transfer coefficients from the local flow, film and droplet interfaces, stable two-resistance rate law, verification, measured effects and limits."
---

# Non-equilibrium two-phase pipe flow

`TwoFluidPipe` normally lets gas and liquid approach phase equilibrium over a fixed relaxation time.
`FilmInterphaseTransfer` (package `neqsim.process.equipment.pipeline.twophasepipe`) replaces that with rates
calculated from the local flow: how fast components and heat cross the gas-liquid interface of each cell.
It answers questions such as:

- how far condensation lags a blowdown;
- how long liquid survives a repressurisation;
- where the equilibrium assumption is safe and where it is not.

It uses the fluid-boundary film model of `neqsim.fluidmechanics`
([interphase heat and mass transfer](../fluidmechanics/InterphaseHeatMassTransfer.md)). No rate coefficient
is supplied by the user.

## Quick start

```java
pipe.run();                                              // steady state
pipe.prepareConservativeEnergyTransientFromSteadyState();
pipe.setComponentTransportEnabled(true);
pipe.setIncludeMassTransfer(true);

FilmInterphaseTransfer film = new FilmInterphaseTransfer();
film.setUpdateInterval(300.0);     // s: hold the film coefficients of a cell this long
pipe.setFilmInterphaseTransfer(film);

pipe.runTransient(60.0, id);
FilmInterphaseTransfer.Result r = pipe.getLastFilmInterphaseTransferResult();
double[] tau = r.getSlowestTimeConstantsSeconds();       // slowest major component, per film cell
double[] cellTime = r.getCharacteristicTimesSeconds();   // distance to equilibrium over present rate
```

It requires the conservative-energy Euler transient with component transport and mass transfer, no slug
tracking and no other finite-rate closure, and dry gas/oil of hydrocarbons and inert gases.

## Model

### What is solved in each wet cell

1. **A two-phase flow node** is built from the transported gas and oil compositions (they are not assumed to
   be in equilibrium), the cell pressure and temperature, the holdup and the phase speeds: stratified, annular
   or slug according to the flow regime of the cell.
2. **The Krishna-Standart film model** is solved on that node: Maxwell-Stefan diffusion through a gas film and a
   liquid film, phase equilibrium at the interface only, and an interface heat balance. The unknowns are both
   interface compositions, every component flux and the interface temperature ($3n+1$). The solver checks every
   residual and throws rather than return an unconverged flux.
3. **Transfer coefficients** come from the flow state of the node (table below).
4. **A rate law** turns the film solution into component sources for the transient.

### Transfer coefficients

| Path | Mass transfer | Heat transfer |
|------|---------------|---------------|
| Gas side, turbulent | Chilton-Colburn on the interfacial friction factor: $k_G = (f_i/8)\,Sc^{-2/3}\lvert u\rvert$ | $h_G = (f_i/8)\,Pr^{-2/3}\rho c_p\lvert u\rvert$ |
| Gas side, laminar | $Sh = 3.66$ | $Nu = 3.66$ |
| Gas side, natural convection | $0.15\,(Gr\,Sc)^{1/3}$, unstable stratification only | same with $Pr$ |
| Liquid film | Yih and Chen, $Sh^* = a\,Re^b\,Sc^{1/2}$ | same relation with $Pr$ |
| Liquid at rest | laminar layer, $3.41\,D/\delta$ | $3.41\,\lambda/\delta$ |
| Droplet, gas side | Ranz and Marshall, $Sh = 2 + 0.6\,Re_d^{1/2}Sc^{1/3}$ at the settling speed | not used |
| Droplet, liquid side | rigid sphere, $Sh = 6.58$ | not used |

- $f_i = f_w(1+75\alpha_L)$ in stratified flow and the Wallis form $f_w(1+300\,\delta/D)$ in annular flow;
  $f_w$ is the Darcy wall friction factor (Haaland). The slug node applies its own enhancement factors.
- The gas speed is the speed relative to the liquid; coefficients depend on its magnitude.
- A coefficient is evaluated for every component pair from the pair diffusivity. The film model combines them
  with the finite-flux and thermodynamic-factor corrections.

### Interfaces

| Interface | Area per metre of pipe | When |
|-----------|------------------------|------|
| Stratified surface | chord of the liquid layer | stratified, slug, bubble cells |
| Gas core | $\pi D\sqrt{\alpha_G}$ | annular and mist cells; cells with net condensation when wall condensation is on |
| Droplets (optional) | $6\,E\,\alpha_L A/d_{32}$ | where the gas entrains liquid, with `setDropletField(true)` |

The entrained fraction $E$ (Ishii and Mishima) and the Sauter diameter $d_{32}$ (critical Weber number) come
from `EntrainmentDeposition`. Droplets are not transported as a separate field: $E$ is the local equilibrium
value of the cell.

**Interfacial area is the largest uncertainty.** In a 1 m line at 80 bara the entrainment correlation puts
99 % of the liquid in droplets of about 0.1 mm already at 2 m/s gas speed, which gives 280 m² of droplet
surface per metre of pipe against 0.3 m² for the stratified surface. The correlation was developed for annular
flow in small tubes and is not validated for large high-pressure lines. The droplet field is therefore off by
default: run a case without it (lower bound of the area, largest departure from equilibrium) and with it
(upper bound, close to equilibrium wherever the gas moves) and report both.

### Rate laws

**Film-flux relaxation (default).** The film solution gives a Maxwell-Stefan flux $N_i$ for every component,
with cross-diffusion and finite-flux effects. The flow model needs a source that is stable on steps of tens
of seconds, while a thin liquid layer saturates with light components within seconds. Each component is
therefore advanced with an exponential whose initial rate is its film flux:

$$\Delta m_i = \Delta m_i^{eq}\left(1 - e^{-r_i\Delta t}\right),\qquad
r_i = \frac{N_i\,M_i\,w}{\Delta m_i^{eq}}$$

- $\Delta m_i^{eq}$: change of the oil inventory of component $i$ per metre that would bring the cell to local
  equilibrium (from a flash of the conserved cell composition); $w$: interface width; $M_i$: molar mass.
- For a short step this is the film flux; for a long step it is local equilibrium. It cannot overshoot.
- A component whose flux points away from its equilibrium inventory is a transient (for example ethane
  dissolving in the liquid while the liquid as a whole evaporates). It is applied with the same exponential
  form, saturating when the bulk of the limiting phase reaches the interface composition of the film solution.
- Where droplets are included, the film flux of a component is raised by the ratio of droplet to film
  conductance, $1 + K^{d}_{ov,i}a_d/(K_{ov,i}w)$.

**Two-resistance relaxation (optional).** Each component relaxes toward local equilibrium with the rate
constant of two finite compartments exchanging through two film resistances,

$$\kappa_i = \frac{K_{ov,i}\,w + K^{d}_{ov,i}\,a_d}{A_G}\left(1 + K_i\frac{n_G}{n_L}\right),\qquad
\frac{1}{K_{ov,i}} = \frac{1}{k_{G,i}} + \frac{K_i\,c_G}{c_L\,k_{L,i}}$$

with the effective film coefficients $k_{G,i}$, $k_{L,i}$ and the interface ratio $K_i = y_i/x_i$ of the film
solution. It treats every component as if the phase amounts stayed fixed. Against the film flux of the same
film solution, at a rich-gas state at 80 bara, its initial rates were 2 to 8 times higher for butane to heptane
and of the wrong sign for ethane and propane. It is kept for comparison.

**Film flux (optional).** The Maxwell-Stefan fluxes times the interface width are applied directly. It is
explicit and only stable for steps well below the liquid-layer time scale $\delta/k_L$ (seconds). With fluxes
held for 300 s a 50 km test turned whole cells into "oil" at the onset of condensation.

### Where no film exists

- **Phase appearance and trace phases** (gas or oil volume fraction below `setMinimumHoldup`, default 1e-7):
  relaxation toward local equilibrium with `setFallbackRelaxationTime` (default 30 s).
- **Single-phase equilibrium** (the cell composition is above its phase envelope): the phase holding less mass
  can only lose components, so a miscible "oil" cannot grow by absorbing gas.
- **Dense phase** (gas and oil miscible): the film solve fails by design and the cell uses the fallback
  relaxation. `Result.getFilmFailureCount()` counts these.

### Energy

Gas and oil share the cell temperature. The interface temperature is solved by the film model and reported,
and the conservative total-energy equation of the pipe owns the latent heat. At the pipe states of the
707 km case below the interface is within 0.1 K of the bulk, and a condensate layer of a few millimetres
follows the gas temperature within seconds, so a second fluid temperature would change little there. It
would matter for thick liquid layers, flashing liquids and cryogenic service.

## Settings

| Setting | Default | Meaning |
|---------|---------|---------|
| `setTransferLaw(...)` | `FILM_FLUX_RELAXATION` | Rate law, see above. |
| `setUpdateInterval(s)` | 0 | Time a cell keeps its film coefficients before the film is solved again. |
| `setDropletField(boolean)` | `false` | Add entrained droplets as a second interface (upper bound of the area). |
| `setRegimeSpecificFilm(boolean)` | `true` | Annular, mist and slug cells use their own film nodes. |
| `setWallCondensation(boolean)` | `true` | Net condensation acts on the gas-core perimeter. |
| `setInterfaceAreaFactor(x)` | 1 | Multiplier on the film interface width, for sensitivity runs. |
| `setMinimumHoldup(x)` | 1e-7 | Below this gas or oil volume fraction no film is solved. |
| `setFallbackRelaxationTime(s)` | 30 | Relaxation time where no film can be solved. |
| `setFilmCorrections(finiteFlux, thermodynamicFactor)` | both on | Corrections of the film model. |

A film solve costs 10 to 100 ms per cell (6 to 13 components). On long lines use an update interval of a few
hundred seconds: the coefficients change with the flow, which changes slowly.

## Verification

| Check | Test |
|-------|------|
| No source at equilibrium, for hydrocarbons with nitrogen and CO2 | `FilmInterphaseTransferTest`, `CoupledGasOilFilmTest` |
| Two-resistance law: no overshoot of equilibrium for steps of 1 s to 1e7 s; a very long step reaches equilibrium | `FilmInterphaseTransferTest` |
| The default law starts every component that moves toward equilibrium at its film flux (within 0.1 %) and ends at local equilibrium for a very long step | `FilmInterphaseTransferTest` |
| Rate follows gas speed, interface area and droplet area | `FilmInterphaseTransferTest` |
| The minority phase cannot grow where equilibrium is single phase | `FilmInterphaseTransferTest` |
| Turbulent coefficients against Dittus-Boelter, within 20 % for $Re = 10^4$ to $5\times10^6$ | `InterphaseStratifiedFlowAnalogyTest`, `ChiltonColburnAnalogyTest` |
| Liquid-side heat and mass transfer are the same correlation; natural convection only where unstable; Wallis factor | `InterphaseStratifiedFlowAnalogyTest` |
| Film residuals for resting and reversed liquid | `CoupledGasOilFilmTest` |
| Mass and component closure over a blowdown, shut-in, refill and hold cycle | `TwoFluidPipeFilmInterphaseTransferTest` |

These are equation, limit and conservation checks. No measured condensation or evaporation rate in a pipe is
compared.

## Measured effects

**One cell, rich gas with nitrogen and CO2 at 277 K, in equilibrium at 80 bara and then held at 80.2 bara**
(1 m bore, holdup 0.65 %, stratified, 2026-11-28):

| Gas speed | Slowest major component, film interface only | Entrained fraction with droplets | Droplet area |
|-----------|----------------------------------------------|----------------------------------|--------------|
| 2 m/s | 33 min | 0.99 | 280 m²/m |
| 12 m/s | 4.8 min | 0.99 | 10,200 m²/m |

The stratified surface is 0.31 m²/m in both cases. Light components (methane, CO2) reach equilibrium in seconds.

Results for a 707 km rich-gas line follow in the next revision of this page.

## What a state-of-the-art model would add

| Missing | Effect | Why not here |
|---------|--------|--------------|
| Comparison with measured interphase transfer in high-pressure pipe flow | Decides whether any of the rates above can be trusted quantitatively | No public data set is included in the repository. |
| Separate gas and liquid temperatures | Small for thin condensate layers (see Energy); large for thick layers and flashing liquids | Needs a second energy equation in the conservative transient. |
| A validated entrainment and droplet-size model for large high-pressure lines, with a transported droplet field | Decides the interfacial area, the largest single uncertainty here | The available correlation is for small tubes; the entrained fraction is the local equilibrium value and droplets are not transported. |
| Fog formation in supersaturated gas | Faster condensation at onset | Phase appearance uses the fallback relaxation. |
| Fully implicit multicomponent coupling in the transient | The default law starts from the coupled film fluxes but lets each component approach equilibrium on its own exponential between film solves | An implicit solve of the film equations with the cell inventories costs one film solve per component per iteration. |
| Transfer in miscible (dense-phase) contact | Decides when the last liquid disappears above the local phase envelope | No interface exists; the fallback relaxation is used. |
| Natural convection in the liquid and at the wall | Shut-in lines | Only gas-side natural convection above the interface is included. |

## Related

- [TwoFluidPipe model](TWOFLUIDPIPE_MODEL.md) and its [evidence matrix](twofluidpipe-evidence-matrix.md)
- [Interphase heat and mass transfer](../fluidmechanics/InterphaseHeatMassTransfer.md)
- [Compositional line-pack pipe](compositional-linepack-pipe.md), the equilibrium screening model
