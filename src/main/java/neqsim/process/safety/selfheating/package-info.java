/**
 * Self-heating and thermal-criticality screening for reactive porous media.
 *
 * <p>
 * This package models <i>low-temperature self-heating leading to spontaneous ignition</i> — the mechanism behind
 * so-called "lagging fires", where a combustible liquid (for example a glycol, heat-transfer oil or lubricant) soaks
 * into porous thermal insulation, gains an enormous liquid/air contact area, oxidises slowly, and cannot shed the
 * resulting heat because the insulation that holds the liquid is also the thermal barrier.
 * </p>
 *
 * <p>
 * The distinguishing feature relative to {@link neqsim.process.safety.reaction.RunawayReactionAnalyzer} is that
 * self-heating is a <b>spatially distributed</b> problem. There is no single well-stirred temperature: heat is
 * generated everywhere in the solid and conducted to the boundary, so ignition depends on the <i>size</i> of the body
 * as well as its temperature. A lumped adiabatic screening cannot express a critical thickness or a critical ambient
 * temperature; the classical criticality theories in this package can.
 * </p>
 *
 * <p>
 * Three complementary models are provided:
 * </p>
 *
 * <ul>
 * <li>{@link neqsim.process.safety.selfheating.PorousMediaSelfHeatingAnalyzer} — Frank-Kamenetskii steady-state
 * criticality for conduction-controlled bodies (large Biot number). Returns the dimensionless criticality parameter
 * &delta;, the critical ambient temperature for a given size, and the critical size for a given temperature.</li>
 * <li>{@link neqsim.process.safety.selfheating.SemenovSelfHeatingAnalyzer} — Semenov criticality for
 * surface-cooling-controlled bodies (small Biot number), for drained pools and small samples where internal gradients
 * are negligible.</li>
 * <li>{@link neqsim.process.safety.selfheating.SelfHeatingInductionSolver} — transient one-dimensional conduction with
 * an Arrhenius volumetric source, giving the induction time (time to ignition), which for lagging fires is typically
 * hours to days rather than seconds.</li>
 * </ul>
 *
 * <p>
 * {@link neqsim.process.safety.selfheating.BasketTestRegression} closes the loop by fitting the kinetic parameters from
 * standard hot-storage ("basket") test data, in which several sample sizes are each taken to their critical oven
 * temperature. This is the normal experimental route (EN 15188, ASTM E2021) for obtaining the activation energy and
 * volumetric heat-release pre-factor that the criticality models require, because those parameters cannot be derived
 * from equilibrium thermodynamics.
 * </p>
 *
 * <p>
 * References: Frank-Kamenetskii, <i>Diffusion and Heat Transfer in Chemical Kinetics</i>; Bowes, <i>Self-Heating:
 * Evaluating and Controlling the Hazards</i>; Babrauskas, <i>Ignition Handbook</i>; EN 15188; ASTM E2021.
 * </p>
 */
package neqsim.process.safety.selfheating;
