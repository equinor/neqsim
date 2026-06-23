/**
 * Fast process-simulation proxies based on cached thermodynamic information from one rigorous base-case run.
 *
 * <p>
 * The package starts with a K-value proxy: a solved {@code ProcessSystem} is scanned once, per-unit vapour/liquid
 * equilibrium ratios {@code K = y/x} and fallback component split factors are frozen, and later scenario sweeps are
 * propagated through the same process graph without running equation-of-state flashes in every unit. Separator-like
 * two-outlet units use the cached K-values with a Rachford-Rice split, and gas/oil/water separators use cached gas/oil
 * and water/oil ratios with a two-variable phase-fraction solve. Single-outlet and non-flash units use conservative
 * pass-through routing; unsupported multi-outlet units fall back to the frozen split factors from the base case.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.fastsimulation;
