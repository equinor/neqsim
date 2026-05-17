## Glossary

<!-- Format: **Term** — Definition. Keep alphabetical. -->

**Binary Interaction Parameter (BIP)** — A fitted parameter $k_{ij}$ that
corrects the geometric mean combining rule for the cross-energy parameter in
cubic equations of state.

**Equation of State (EOS)** — A mathematical relation between pressure,
volume, and temperature that describes the thermodynamic state of a fluid.

**Flash Calculation** — The computation of phase compositions and amounts at
thermodynamic equilibrium for a given feed at specified conditions (e.g., T, P).

**Fugacity** — An effective partial pressure that accounts for non-ideal
behavior. Phases are in equilibrium when component fugacities are equal across
all phases.

**Mixing Rule** — The prescription for combining pure-component EOS parameters
into mixture parameters.

**NeqSim** — Non-Equilibrium Simulator. An open-source Java toolkit for
thermodynamic calculations and process simulation.

**Acentric Factor** ($\omega$) — A dimensionless parameter that measures the
non-sphericity of a molecule, defined from the vapor pressure at $T_r = 0.7$.

**Alpha Function** ($\alpha$) — A temperature-dependent function in cubic EoS
that modifies the energy parameter to improve vapor pressure predictions.

**Anderson Acceleration** — A convergence acceleration technique that combines
previous iterates to speed up fixed-point iterations; used in CPA solvers.

**Association Energy** ($\varepsilon^{AB}$) — The energy of hydrogen bond
formation between sites A and B, with units of J/mol or K.

**Association Scheme** — A classification system (1A, 2B, 3B, 4C, etc.) that
defines the number and type of association sites on a molecule.

**Association Strength** ($\Delta^{AB}$) — The product of the radial
distribution function, Boltzmann factor, and association volume that determines
the probability of bond formation.

**Association Volume** ($\beta^{AB}$) — A dimensionless parameter proportional
to the volume available for hydrogen bond formation between sites A and B.

**Born Term** — A contribution to the Helmholtz energy in electrolyte models
that accounts for the solvation energy of ions.

**Broyden's Method** — A quasi-Newton method that approximates the Jacobian
using rank-1 updates from successive iterates.

**CCS** — Carbon Capture and Storage. The process of capturing CO$_2$ from
industrial sources, transporting it, and storing it underground.

**Combining Rule** — A prescription for obtaining cross-association parameters
from pure-component values (CR-1, CR-2, ECR, Solvation).

**CPA** — Cubic Plus Association equation of state. Combines a cubic EoS
(SRK or PR) with Wertheim's association theory.

**Cross-Association** — Hydrogen bonding between molecules of different species.

**Dense Phase** — A supercritical or compressed liquid state above the critical
pressure where no phase boundary exists.

**DIIS** — Direct Inversion in the Iterative Subspace. Equivalent to Anderson
acceleration; used to speed up convergence of fixed-point iterations.

**e-CPA** — Electrolyte CPA. An extension that adds Born solvation and MSA
ion-ion interaction terms to handle electrolyte solutions.

**Fugacity Coefficient** ($\varphi_i$) — The ratio of the fugacity of
component $i$ to its partial pressure, derived from the EoS.

**Fully Implicit** — A CPA solver strategy that eliminates the inner loop by
solving site balance equations simultaneously with the flash equations.

**Helmholtz Energy** ($A$) — The thermodynamic potential equal to internal
energy minus $TS$; the natural potential for EoS in the ($T$, $V$, $\mathbf{n}$) basis.

**Hydrogen Bond** — A strong directional intermolecular interaction between a
proton donor (OH, NH) and a proton acceptor (O, N, F lone pair).

**LLE** — Liquid–Liquid Equilibrium. The coexistence of two liquid phases.

**MEG** — Mono-Ethylene Glycol. A common hydrate inhibitor and dehydration agent.

**MSA** — Mean Spherical Approximation. A statistical mechanical model for
ion-ion electrostatic interactions in electrolyte solutions.

**PC-SAFT** — Perturbed Chain Statistical Associating Fluid Theory. A
molecular-based EoS using hard-sphere chains as the reference fluid.

**Radial Distribution Function** ($g(\rho)$) — The probability of finding a
molecule at a given distance, used in the association strength calculation.

**Reduced Variable** — A thermodynamic variable scaled by its critical value
(e.g., $T_r = T/T_c$).

**SAFT** — Statistical Associating Fluid Theory. A family of molecular-based
EoS derived from Wertheim's perturbation theory.

**Self-Association** — Hydrogen bonding between molecules of the same species.

**Site Balance** — The set of equations that determine the fraction of
unbonded association sites at equilibrium.

**Site Fraction** ($X_A$) — The fraction of association sites of type A that
are not bonded; ranges from 0 (fully bonded) to 1 (fully free).

**Solvation** — Cross-association where one molecule provides only donor sites
and the other only acceptor sites (e.g., CO$_2$–water).

**Successive Substitution** — A simple iterative method for solving fixed-point
equations; linearly convergent.

**TEG** — Triethylene Glycol. The most common glycol used in gas dehydration.

**TPT1** — First-order Thermodynamic Perturbation Theory (Wertheim). The
theoretical foundation for association models.

**VLLE** — Vapor–Liquid–Liquid Equilibrium. The coexistence of a vapor and two
liquid phases.

**Wertheim's Theory** — The statistical mechanical theory of associating fluids
developed by M.S. Wertheim (1984–86) that forms the theoretical basis for
CPA and SAFT models.
