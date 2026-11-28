---
title: "Non-equilibrium two-phase pipe flow"
description: "Non-equilibrium heat and mass transfer between gas, oil and an aqueous phase (water, glycol) in the TwoFluidPipe transient: bulk fog formation and flashing, multicomponent film model with transfer coefficients from the local flow, film and droplet interfaces, miscible contact, liquid-liquid exchange, a rate law that is stable on pipeline time steps, verification, measured effects and limits."
---

# Non-equilibrium two-phase pipe flow

`TwoFluidPipe` normally lets gas and liquid approach phase equilibrium over a fixed relaxation time.
`FilmInterphaseTransfer` (package `neqsim.process.equipment.pipeline.twophasepipe`) replaces that with rates
calculated from the local flow: how fast components and heat cross the gas-liquid interface of each cell.
It answers questions such as:

- how far condensation lags a blowdown;
- how long liquid survives a repressurisation;
- where the equilibrium assumption is safe and where it is not.

It separates three routes to equilibrium, because they have very different rates:

| Route | When | Rate |
|-------|------|------|
| Bulk phase split | A phase is unstable on its own: gas below its dew point forms fog, oil above its bubble point flashes | Fast (bulk split time, default 30 s); needs no interface |
| Interface transfer | Both phases are stable on their own but not in equilibrium with each other | Multicomponent film model on the local flow; seconds to days |
| Miscible mixing | No mixture of the two phases splits: there is no interface | Radial turbulent mixing time of the pipe |

The interface route uses the fluid-boundary film model of `neqsim.fluidmechanics`
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
FilmInterphaseTransfer.CellMode[] mode = r.getModes();   // FILM, MISCIBLE, RELAXATION or NONE per cell
double[] fog = r.getFogRatesKgPerMetreSecond();          // liquid formed in the bulk of the gas
```

It requires the conservative-energy Euler transient with component transport and mass transfer, no slug
tracking and no other finite-rate closure. The liquid can be oil, an aqueous phase (water, glycols, alcohols; no
ions), or both; see [Aqueous phase and three phases](#aqueous-phase-and-three-phases).

## Equilibrium and non-equilibrium mode

Every case runs in two modes through the same closure, so the two results differ only by the transfer model:

```java
FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
closure.setEquilibriumMode(true);            // relaxation to local equilibrium
closure.setFallbackRelaxationTime(30.0);     // its time constant in s
pipe.setFilmInterphaseTransfer(closure);
```

| | Equilibrium mode | Non-equilibrium mode (default) |
|---|---|---|
| What moves a component | Relaxation of every component to its local equilibrium partition, one time constant | Bulk split, film transfer across the interface, miscible mixing, liquid-liquid exchange |
| Fluids | Gas with oil, with an aqueous phase, or both | The same |
| Flow regime, interfacial area, droplets | No influence | Decide the rates |
| Film solves and extra flashes | None | Yes |
| Update | $\Delta m = \Delta m^{eq}(1 - e^{-\Delta t/\tau})$, stable on any step | The same form with calculated rate constants |

- Equilibrium mode is the relaxation the pipe uses when no closure is set
  (`setMassTransferRelaxationTime`), written with the exponential update. In a 60 s blowdown of a 5 km line
  the two condensed the same liquid within 2 % (`TwoFluidPipeFilmInterphaseTransferTest`).
- Run a case in both modes and read the difference as the non-equilibrium effect. Do not compare a
  non-equilibrium run with a run made at another internal step: see the step sensitivity in
  [TwoFluidPipe model](TWOFLUIDPIPE_MODEL.md).

## Model

### What is solved in each wet cell

0. **Bulk phase split.** The gas inventory of the cell is flashed on its own at the cell pressure and
   temperature, and so is the oil inventory. What the gas sheds as liquid is fog; what the oil sheds as vapour
   is flashing. Both move with the bulk split time. The steps below act between the two stable phases that
   remain. See [Bulk phase split](#bulk-phase-split-fog-and-flashing).
1. **Miscibility test.** Mixtures of the two phases (10, 25, 50, 75 and 90 mol% oil) are flashed. If none
   splits, the phases are miscible at first contact and no film is solved. See
   [Where no film exists](#where-no-film-exists).
2. **A two-phase flow node** is built from the transported gas and oil compositions (they are not assumed to
   be in equilibrium), the cell pressure and temperature, the holdup and the phase speeds. Its kind follows the
   flow regime of the cell (see [Flow regimes](#flow-regimes)).
3. **The Krishna-Standart film model** is solved on that node: Maxwell-Stefan diffusion through a gas film and a
   liquid film, phase equilibrium at the interface only, and an interface heat balance. The unknowns are both
   interface compositions, every component flux and the interface temperature ($3n+1$). The solver checks every
   residual and throws rather than return an unconverged flux. Where the cell as a whole is single phase at
   equilibrium (a liquid evaporating into gas that can hold all of it), the interface is started from the tie
   line of a gas/oil mixture that does split.
4. **Transfer coefficients** come from the flow state of the node (table below).
5. **A rate law** turns the film solution into component sources for the transient.

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
| Liquid layer | chord of the layer | stratified, slug and churn cells |
| Gas core | $\pi D\sqrt{\alpha_G}$ | annular cells; layer cells with net condensation when wall condensation is on |
| Droplets | $6\,\alpha_L A/d_{32}$ | mist cells |
| Bubbles | $6\,\alpha_G A/d_{32}$ | bubble and dispersed-bubble cells |
| Entrained droplets (optional) | $6\,E\,\alpha_L A/d_{32}$ | added in layer and film cells where the gas entrains liquid, with `setDropletField(true)` |

### Flow regimes

The regime of a cell is the one `TwoFluidPipe` assigns. Each has its own film node and interface:

| Regime | Film node | Interface | Size and slip of the dispersed phase |
|--------|-----------|-----------|--------------------------------------|
| Stratified smooth, stratified wavy | stratified | liquid layer | – |
| Slug, churn | slug (its own enhancement of the coefficients) | liquid layer | – |
| Annular | annular (Wallis interfacial friction) | gas core | – |
| Mist | droplet (Ranz-Marshall outside, Kronig-Brink inside) | droplets | $d_{32}$ from `EntrainmentDeposition`, 10 µm to $D/10$; settling speed from the Archimedes number |
| Bubble, dispersed bubble | bubble (Ranz-Marshall in the liquid) | bubbles | Hinze, $d_{32} = 0.62\cdot0.725\,(\sigma/\rho_L)^{0.6}\varepsilon^{-0.4}$ with $\varepsilon = f u_m^3/2D$, 0.1 mm to four capillary lengths; Harmathy rise speed |
| Single-phase gas with a trace of liquid | stratified | liquid layer | – |
| Single-phase liquid with a trace of gas | bubble | bubbles | as bubble flow |

- Mist exchanges within a fraction of a second: 30 µm droplets give thousands of square metres per metre of pipe.
  A mist cell is at equilibrium for practical purposes.
- In bubble flow the liquid side controls, and the large liquid inventory makes the time longer: 84 s in a cell
  with 85 % liquid against 23 s for the same cell treated as stratified.
- Transitions are not blended: the film of a cell follows the regime label, and a change of label changes the
  rate at once. `TwoFluidPipe` itself blends its friction closures across transitions.
- `setRegimeSpecificFilm(false)` uses the stratified node everywhere, on the gas-core perimeter in annular and
  mist cells and on the chord elsewhere.
- Slug flow uses the liquid layer of the film region. Bubbles in the slug body and the mixing at the slug front
  are represented only by the enhancement factors of the slug node.

The entrained fraction $E$ (Ishii and Mishima) and the Sauter diameter $d_{32}$ (critical Weber number) come
from `EntrainmentDeposition`. Droplets are not transported as a separate field: $E$ is the local equilibrium
value of the cell.

**Interfacial area is the largest uncertainty.** In a 1 m line at 80 bara the entrainment correlation puts
99 % of the liquid in droplets of about 0.1 mm already at 2 m/s gas speed, which gives 280 m² of droplet
surface per metre of pipe against 0.3 m² for the stratified surface. The correlation was developed for annular
flow in small tubes and is not validated for large high-pressure lines. The droplet field is therefore off by
default: run a case without it (lower bound of the area, largest departure from equilibrium) and with it
(upper bound, close to equilibrium wherever the gas moves) and report both.

### Bulk phase split: fog and flashing

A film model moves components only across an interface. That is the wrong picture for a pressure fall. A rich
gas that drops below its dew point condenses in its own bulk, on nuclei that are always present, and the
droplets then settle; it does not wait for heavy components to diffuse through the gas to the pipe wall. An oil
that drops below its bubble point forms bubbles.

- The gas inventory of the cell is flashed alone at the cell pressure and temperature. If it splits, the denser
  phase is fog and goes to the oil. The oil inventory is flashed alone; if it splits, the lighter phase goes to
  the gas.
- Each is moved with `setBulkPhaseSplitTime` (default 30 s, the default equilibrium relaxation time of the
  pipe): $\Delta m = m_{shed}\,(1 - e^{-\Delta t/\tau_s})$. It is a flash of a phase's own content, so it is
  bounded and stable on any step.
- The film is solved between the phases as they are after the split, and the film rate law works on what is
  left of the distance to local equilibrium, $\Delta m_i^{eq} - \Delta m_i^{split}$.
- In the one-cell example below, a fall from 80 to 70 bara leaves 95 % of the equilibrium change to the bulk
  split and 5 % to the interface. A rise in pressure leaves both phases stable: evaporation of a liquid into
  unsaturated gas, and absorption of gas into unsaturated oil, are interface processes.
- Fog is added to the oil of the cell. It is not carried as droplets with the gas (see limits).
- `setBulkPhaseSplit(false)` sends everything through the interface. Use it only to see the size of the effect.

### Aqueous phase and three phases

A gas that carries water and glycol (carry-over from dehydration, MEG injection) drops out a small aqueous
phase when it cools, and takes it up again when it is warmed or let down. With condensate present there are three
phases. Use an equation of state that describes water and glycols, for example CPA with the multiphase check on.

- **Each liquid has its own film with the gas**, solved at its own holdup and speed. The coupled film solver
  accepts a gas/aqueous pair and any molecular component.
- **Interface share.** A liquid evaporates from its own surface only: two liquids share the gas-liquid interface
  in proportion to their volume, so a small aqueous phase beside a larger condensate layer dries out slowly, as it
  does under a floating oil layer. A vapour condenses on any surface: net condensation into a liquid sees the
  whole interface.
- **Bulk split in all directions.** The gas is flashed alone and can shed hydrocarbon fog, aqueous fog or both; the
  oil can shed vapour and free water; the aqueous phase can shed dissolved gas.
- **Hygroscopic absorption needs the interface.** A gas in equilibrium with a glycol solution is below the dew
  point of pure water. On a pressure rise it has more water than the solution allows, but nothing condenses in the
  bulk: the water is absorbed through the interface.
- **Liquid to liquid.** Where a component must leave one liquid and enter the other (water dissolved in oil
  dropping out, glycol washing out of condensate), the smaller of the two amounts passes directly between the
  liquids with `setLiquidLiquidRelaxationTime` (default 30 s). No liquid-liquid coefficient is calculated.
- **Miscibility** is tested for the gas/oil pair only.
- Diagnostics for the aqueous pair: `getAqueousModes()`, `getAqueousInterfaceWidthsMetres()`,
  `getAqueousCharacteristicTimesSeconds()`, `getAqueousFogRatesKgPerMetreSecond()`,
  `getAqueousFlashingRatesKgPerMetreSecond()`, `getLiquidLiquidRatesKgPerMetreSecond()`.
- Hydrates and ice are not modelled.

### Rate laws

**Film-flux relaxation (default).** The film solution gives a Maxwell-Stefan flux $N_i$ for every component,
with cross-diffusion and finite-flux effects. The flow model needs a source that is stable on steps of tens
of seconds, while a thin liquid layer saturates with light components within seconds. Each component is
therefore advanced with an exponential whose initial rate is its film flux:

$$\Delta m_i = \Delta m_i^{eq}\left(1 - e^{-r_i\Delta t}\right),\qquad
r_i = \left.\frac{N_i\,M_i\,w}{\Delta m_i^{eq}}\right|_{\text{film solve}}$$

- $\Delta m_i^{eq}$: change of the liquid inventory of component $i$ per metre that would bring the cell to local
  equilibrium (from a flash of the conserved cell composition, less the bulk split); $w$: interface width; $M_i$:
  molar mass.
- The rate constant $r_i$ is taken at the film solve and held. It is a property of the flow (coefficients and
  area) and changes slowly; the distance to equilibrium is taken new at every step. For a short step after a film
  solve this is the film flux; for a long step it is local equilibrium. It cannot overshoot.
- The film is solved again after the update interval, or sooner when the distance to equilibrium has doubled. A
  film solved at equilibrium has no flux and gives no rate constants; without the second rule a cell would stay
  inactive until the next update.
- A component whose film flux points away from its equilibrium inventory is held (zero rate) until the two
  agree. `Result.getCounterEquilibrium()` flags the cells where this happens. An earlier version carried such
  components toward the interface composition of the held film solution; near the dew point that let the
  liquid take up gas without limit, gave nine times the equilibrium liquid in a 50 km test and stopped a
  707 km blowdown. With the bulk split in front, what is held is small.
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
  relaxation toward local equilibrium with `setFallbackRelaxationTime` (default 30 s),
  $\Delta m = \Delta m^{eq}(1 - e^{-\Delta t/\tau})$. Mode `RELAXATION`.
- **Single-phase equilibrium** (the gas can hold all of the liquid): the phase holding less mass can only lose
  components. An interface still exists as long as some mixture of the two phases splits, and the film is
  solved. Mode `FILM`.
- **Miscible contact** (no mixture of the two phases splits into hydrocarbon phases whose densities differ by
  more than a quarter; above or near the critical pressure of the gas/oil mixing line): there is no interface
  that holds against the turbulence. The phase the cell should lose mixes into the other with the radial turbulent
  mixing time of the pipe,

  $$\tau_{mix} = \frac{R^2}{D_t} = \frac{R}{0.07\,u^*},\qquad u^* = \lvert u_G\rvert\sqrt{f/8},\quad
  f = 0.316\,Re^{-1/4}$$

  but not faster than the fallback relaxation time. Mode `MISCIBLE`;
  `Result.getMiscibleMixingTimesSeconds()` reports the time. For a 1 m line with dense gas at 2 m/s it is about
  two minutes. Gas at rest does not mix a miscible layer. The estimate ignores the stable stratification of the
  denser layer, so it is a lower estimate of the mixing time at low speed. `setMiscibleMixingTime(s)` fixes the
  time for sensitivity runs. For a rich gas and the condensate it left at 85 bara, the phases became
  miscible between 110 and 115 bara at −11 °C and between 130 and 140 bara at 0 °C.
- **Failed film solve**: the cell keeps the rate constants of its last converged film and tries again after
  one update interval. A cell without an earlier solution uses the fallback relaxation for four update
  intervals. `Result.getFilmFailureCount()` and `getLastFailure()` report it. Failures cluster in the band
  between clearly immiscible and clearly miscible gas and oil.

### Energy

Gas and oil share the cell temperature. The interface temperature is solved by the film model and reported,
and the conservative total-energy equation of the pipe owns the latent heat. At the pipe states of the
707 km case below the interface is within 0.1 K of the bulk, and a condensate layer of a few millimetres
follows the gas temperature within seconds, so a second fluid temperature would change little there. It
would matter for thick liquid layers, flashing liquids and cryogenic service.

## Settings

| Setting | Default | Meaning |
|---------|---------|---------|
| `setEquilibriumMode(boolean)` | `false` | Relaxation to local equilibrium instead of finite-rate transfer. |
| `setTransferLaw(...)` | `FILM_FLUX_RELAXATION` | Rate law, see above. |
| `setUpdateInterval(s)` | 0 | Time a cell keeps its film coefficients before the film is solved again. |
| `setDropletField(boolean)` | `false` | Add entrained droplets as a second interface (upper bound of the area). |
| `setRegimeSpecificFilm(boolean)` | `true` | Annular, mist and slug cells use their own film nodes. |
| `setWallCondensation(boolean)` | `true` | Net condensation acts on the gas-core perimeter. |
| `setInterfaceAreaFactor(x)` | 1 | Multiplier on the film interface width, for sensitivity runs. |
| `setMinimumHoldup(x)` | 1e-7 | Below this gas or oil volume fraction no film is solved. |
| `setFallbackRelaxationTime(s)` | 30 | Relaxation time where no film can be solved. |
| `setBulkPhaseSplit(boolean)` | `true` | Fog and flashing vapour move without an interface. |
| `setBulkPhaseSplitTime(s)` | 30 | Time constant of fog formation and flashing. |
| `setMiscibleMixingTime(s)` | `NaN` | Fixed mixing time of miscible cells; `NaN` uses the turbulent estimate. |
| `setLiquidLiquidRelaxationTime(s)` | 30 | Time constant of the direct exchange between oil and aqueous phase. |
| `setFilmCorrections(finiteFlux, thermodynamicFactor)` | both on | Corrections of the film model. |

A film solve costs 10 to 300 ms per cell (6 to 13 components). On long lines use an update interval of a few
hundred seconds: the coefficients change with the flow, which changes slowly. The bulk split adds one flash per
phase of a wet cell every third of the split time. With CPA and an aqueous phase a film solve takes 0.1 to 2 s
and a 20 km, 6-cell case ran four times slower than with plain relaxation.

## Verification

| Check | Test |
|-------|------|
| No source at equilibrium, for hydrocarbons with nitrogen and CO2 | `FilmInterphaseTransferTest`, `CoupledGasOilFilmTest` |
| Two-resistance law: no overshoot of equilibrium for steps of 1 s to 1e7 s; a very long step reaches equilibrium | `FilmInterphaseTransferTest` |
| The default law starts every component that moves toward equilibrium at its film flux (within 0.1 %) and ends at local equilibrium for a very long step; a component whose film flux points away from equilibrium has zero rate | `FilmInterphaseTransferTest` |
| A pressure fall is carried by fog and flashing: their sum is within 15 % of the equilibrium change, scales with the split time, and is all that is left when the interface is removed; a pressure rise sheds nothing | `FilmInterphaseTransferTest` |
| A cell that is single phase at equilibrium but whose phases are immiscible is solved with the film, without failure | `FilmInterphaseTransferTest` |
| A miscible cell mixes with $R/(0.07u^*)$, or the fixed time; gas at rest gives no mixing | `FilmInterphaseTransferTest` |
| The film starts from a tie line (equal fugacities to 1e-6) when the bulk as a whole is single phase | `InterfaceTieLineInitialisationTest` |
| Gas over water/TEG (CPA): no flux at equilibrium; water leaves the liquid on a pressure fall or a temperature rise and enters it in the opposite cases, at 3 m/s and 0.2 m/s gas speed; TEG flux below 0.1 % of the water flux | `CoupledGasAqueousFilmTest` |
| Gas with a small aqueous phase, with and without condensate: no source at equilibrium; evaporation through the stratified surface without overshoot; aqueous fog on cooling; absorption without fog on a pressure rise; every component conserved over three phases | `FilmInterphaseTransferAqueousTest` |
| Rate follows gas speed, interface area and droplet area | `FilmInterphaseTransferTest` |
| Every flow regime is solved without failure, with its own interface: layer, gas core, droplets (over 1000 times the layer surface, time below a thousandth) or bubbles | `FilmInterphaseTransferTest` |
| Equilibrium mode: no film solve, every component at the relaxation rate of the pipe, in every flow regime, for gas/oil, gas/aqueous and three phases | `FilmInterphaseTransferTest`, `FilmInterphaseTransferAqueousTest` |
| The same blowdown with the pipe's relaxation, in equilibrium mode and in non-equilibrium mode: balances close in all three, the first two agree within 2 % | `TwoFluidPipeFilmInterphaseTransferTest` |
| A 20 km line with a water/TEG phase, with and without condensate, in both modes: balances close for all phases | `TwoFluidPipeAqueousTransferModesTest` |
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

**One cell, same gas, a fall from 80 to 70 bara**: the equilibrium change is 0.202 kg of liquid per metre. The
gas sheds 0.280 kg/m as fog and the liquid flashes 0.088 kg/m, a net 0.191 kg/m (95 %) without an interface.
After a rise from 80 to 86 bara neither phase sheds anything.

**50 km line** (1.01 m bore, 10 cells, 13-component rich gas, blown down from 130 to 86 bara in 2 h, shut in
30 min, refilled toward 200 bara in 2 h with the outlet shut, then held; 2026-11-28). Liquid in the line, t:

| Time (h) | Stage | Inlet pressure (bara) | 30 s relaxation to equilibrium | Non-equilibrium model |
|----------|-------|-----------------------|--------------------------------|-----------------------|
| 1.25 | blowdown | 104 | 11 | 11 |
| 1.50 | blowdown | 99 | 64 | 62 |
| 2.00 | end of blowdown | 87 | 191 | 181 |
| 2.50 | end of shut-in | 87 | 178 | 181 |
| 2.75 | refill | 99 | 101 | 145 |
| 3.00 | refill | 112 | 9 | 95 |
| 3.25 | refill | 127 | 0 | 55 |
| 3.50 | refill | 141 | 0 | 11 |
| 4.00 | refill | 170 | 0 | 2.8 |
| 4.50 | end of refill | 199 | 0 | 0.04 |

- Condensation follows equilibrium within 5 %: the gas forms fog as it passes its dew point.
- Evaporation lags. When the equilibrium run is dry (3.25 h) the non-equilibrium run still holds 30 % of its
  liquid.
- From 3.5 h two to four cells are miscible and mix with the turbulent mixing time.
- 768 film solves, 14 failed (1.8 %), all between 127 and 185 bara; those cells kept their last rate constants.
  With the first version of the default law the same case gave 687 t of "liquid" at 1.6 h and 33 failures in
  121 solves.

**20 km line with a small aqueous phase** (1.01 m bore, 6 cells; lean gas with 150 ppm water and 20 ppm TEG,
CPA; flowing at 200 kg/s, then the inlet is shut and the line is blown down from 130 to 100 bara in 15 min,
which cools the gas from 5 to −10 °C, shut in 5 min, refilled to 196 bara in 15 min and held 5 min;
2026-11-28). The line starts with 31.1 t of glycol solution, 0.18 % of its volume. Change of the aqueous mass
from the start, kg:

| Time (min) | Stage | 30 s relaxation to equilibrium | Non-equilibrium model |
|------------|-------|--------------------------------|-----------------------|
| 5 | blowdown | +35 | +24 |
| 15 | end of blowdown | +82 | +69 |
| 20 | end of shut-in | +72 | +71 |
| 25 | refill, 130 bara | −17 | +51 |
| 30 | refill, 163 bara | −126 | +2 |
| 35 | end of refill, 196 bara | −244 | −44 |
| 40 | end of hold | −197 | −67 |

- Water condensing out of the cooling gas follows equilibrium at about 85 %: a little forms as fog and the rest is
  absorbed on the wall.
- Water returning to the compressed gas has to cross the small surface of the solution: characteristic times
  of 30 to 80 min, so only a fifth of the equilibrium amount has moved at the end of a 15 min refill.
- 156 film solves, 14 failed. The run took 1.8 times as long as the relaxation run (14 min against 8 min).

**The same 20 km line with condensate as well** (three phases: 294 t of condensate and 3.9 t of glycol solution at
the start; same sequence):

| Time (min) | Stage | Condensate, equilibrium (t) | Condensate, non-equilibrium (t) | Aqueous change, equilibrium (kg) | Aqueous change, non-equilibrium (kg) |
|------------|-------|-----------------------------|---------------------------------|----------------------------------|--------------------------------------|
| 5 | blowdown | 320 | 318 | +32 | +21 |
| 15 | end of blowdown | 360 | 358 | +78 | +69 |
| 20 | end of shut-in | 357 | 358 | +71 | +73 |
| 25 | refill, 130 bara | 307 | 319 | +4 | +79 |
| 30 | refill, 163 bara | 183 | 229 | −97 | +88 |
| 35 | end of refill, 197 bara | 21 | 116 | −202 | +65 |
| 40 | end of hold | 18 | 81 | −178 | +60 |

- Condensate and water both condense close to equilibrium during the blowdown.
- In the refill the condensate lags as in the dry case. The aqueous phase hardly evaporates at all: it holds 1 %
  of the liquid volume and so 1 % of the surface.
- 333 film solves, 41 failed, most of them for the aqueous film above 130 bara; those cells kept their last rate
  constants. The run took 28 min against 5 min in equilibrium mode.

**707 km rich-gas export line** (1.01 m bore, real seabed profile, 25 cells; inlet shut, line settled at 130 bara and
blown down through the outlet to 84 bara average in 16 h, held 12 h, repressurised from the inlet with the outlet
shut for 17 h and then opened; 60 s steps, film update every 300 s; 2026-11-28):

| Quantity | 30 s relaxation | Film interface | Film and droplets | Film, no bulk split |
|----------|-----------------|----------------|-------------------|---------------------|
| Liquid at end of blowdown (t) | 1,660 | 1,659 | 1,662 | 1,356 |
| Liquid through the outlet in blowdown (t) | 1,057 | 1,053 | 1,059 | 1,057 |
| Liquid at end of the 12 h hold (t) | 1,141 | 1,551 | 1,417 | 1,334 |
| Liquid 6 h after restart (t) | 402 | 749 | 621 | 949 |
| Liquid 12 h after restart (t) | 17 | 142 | 66 | 280 |
| Line free of liquid (h after restart) | 12.8 | 20.5 | 17.3 | 19.0 |
| Film solves / failed | – | 16,855 / 9 | 16,380 / 5 | 17,349 / 51 |
| Computer time (min) | 18 | 27 | 27 | 30 |

- **Blowdown:** the same liquid as the equilibrium run, within 0.2 %. Without the bulk split the line would hold
  18 % less, because condensation would have to diffuse to the wall. That column is a sensitivity, not a
  prediction.
- **Hold:** with relaxation a third of the liquid evaporates again in 12 h as the line settles and warms. With
  the film model 7 to 15 % does: the gas is nearly at rest and the interface is small.
- **Restart:** the liquid lasts 4.5 to 8 h longer. The last 110 t sits in the 85 km before the closed outlet,
  where the gas moves at 0.1 to 0.6 m/s at 115 to 135 bara and the characteristic times are 6 to 40 h. While the
  outlet stays shut it is barely shrinking.
- **Consequence for the case:** the outlet-opening time was taken from an equilibrium study. With the film
  interface 104 t of liquid is then within 85 km of the outlet (13 t with droplets). None reached the outlet
  (0.002 t), but only because the prescribed outlet pressure is at first above the line pressure of this model,
  so 220 to 440 t of dry gas flows in through the outlet for 1.2 to 1.5 h and evaporates most of it before
  delivery starts. That is a property of the boundary condition, not evidence that opening then is safe.
- **Droplets** bound the interfacial area from above; the truth is between the two non-equilibrium columns.
- In the two non-equilibrium columns at most one cell was miscible at a time, holding up to 9 t.
- With the droplet interface, and without the bulk split, the line took in 1.9 % and 2.4 % more gas during the
  restart than the other two runs at the same end pressures. The cause is not established. Compare liquid amounts between
  the columns, not flows.
- The runs are not mesh-converged (see [TwoFluidPipe model](TWOFLUIDPIPE_MODEL.md)).

## Limits

| Missing | Effect | Why not here |
|---------|--------|--------------|
| Comparison with measured interphase transfer in high-pressure pipe flow | Decides whether any of the rates above can be trusted quantitatively | No public data set is included in the repository. |
| A validated entrainment and droplet-size model for large high-pressure lines, with a transported droplet field | Decides the interfacial area, the largest single uncertainty here | The available correlation is for small tubes; the entrained fraction is the local equilibrium value and droplets are not transported. |
| Fog as a transported droplet field with nucleation and growth kinetics | Fog is formed with a fixed 30 s time constant and added to the liquid layer of the cell at once. In reality it travels with the gas until it settles or deposits, which carries liquid further downstream and keeps a large interface for re-evaporation | Needs a third (droplet) field in the flow equations. |
| Bubble nucleation kinetics in the liquid | Flashing of the liquid uses the same fixed time constant; a quiet liquid can stay supersaturated longer | No nucleation model. |
| Separate gas and liquid temperatures | Small for thin condensate layers (see Energy); large for thick layers and flashing liquids | Needs a second energy equation in the conservative transient. |
| Fully implicit multicomponent coupling in the transient | The film law starts from the coupled film fluxes but lets each component approach equilibrium on its own exponential between film solves, and holds components whose flux points away from equilibrium | An implicit solve of the film equations with the cell inventories costs one film solve per component per iteration. |
| Mixing of miscible layers under stable stratification | The miscible mixing time is the unstratified turbulent estimate; a dense layer under slow gas mixes more slowly | Needs an entrainment law in the gradient Richardson number. |
| Natural convection in the liquid and at the wall | Shut-in lines | Only gas-side natural convection above the interface is included. |

## Related

- [TwoFluidPipe model](TWOFLUIDPIPE_MODEL.md) and its [evidence matrix](twofluidpipe-evidence-matrix.md)
- [Interphase heat and mass transfer](../fluidmechanics/InterphaseHeatMassTransfer.md)
- [Compositional line-pack pipe](compositional-linepack-pipe.md), the equilibrium screening model
