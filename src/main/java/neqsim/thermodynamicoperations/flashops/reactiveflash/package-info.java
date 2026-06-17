/**
 * Reactive multiphase flash algorithms using the modified RAND method.
 *
 * <p>
 * This package implements simultaneous chemical and phase equilibrium (CPE) calculations using the
 * non-stoichiometric approach. The main classes are:
 * </p>
 *
 * <ul>
 * <li>{@link neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphaseTPflash} —
 * Main entry point for the reactive TP flash algorithm.</li>
 * <li>{@link neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphasePHflash} —
 * Reactive PH flash: finds equilibrium temperature given pressure and enthalpy. Also supports PS
 * (entropy) specification.</li>
 * <li>{@link neqsim.thermodynamicoperations.flashops.reactiveflash.ModifiedRANDSolver} — Core
 * Newton-based solver for the modified RAND method.</li>
 * <li>{@link neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveStabilityAnalysis} —
 * Tangent plane distance analysis adapted for reactive systems.</li>
 * <li>{@link neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix} —
 * Element-component formula matrix A that maps elemental composition.</li>
 * </ul>
 *
 * <p>
 * The non-stoichiometric approach minimizes the total Gibbs energy subject to element balance
 * constraints A * n = b. This avoids the need to specify explicit reaction stoichiometry — the
 * number and nature of reactions are determined automatically from the formula matrix rank.
 * </p>
 *
 * <p>
 * Key references:
 * <ul>
 * <li>White, Johnson, Dantzig (1958) J. Chem. Phys. 28, 751</li>
 * <li>Tsanas, Stenby, Yan (2017) Ind. Eng. Chem. Res. 56, 11983-11995</li>
 * <li>Paterson, Michelsen, Stenby, Yan (2018) SPE Journal 23(03), 609-622</li>
 * <li>Ascani, Sadowski, Held (2023) Molecules 28, 1768</li>
 * </ul>
 *
 * @author copilot
 * @version 1.0
 */
package neqsim.thermodynamicoperations.flashops.reactiveflash;
