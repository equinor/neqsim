/**
 * Linear recovery-factor production allocation (proxy network).
 *
 * <p>
 * This package allocates the metered production of a process flowsheet back to its individual sources (wells,
 * templates, commingled feeds) using frozen per-unit per-component split factors and a linear proxy network. It is a
 * fast, scalable alternative to per-source component tagging: a single rigorous base-case run with one common component
 * slate is enough, after which any number of sources can be allocated by superposition.
 * </p>
 *
 * <h2>Method</h2>
 * <p>
 * Every non-stream unit that has outlet streams is a network node; streams are the edges. For each node the base case
 * fixes a split factor
 * </p>
 *
 * <pre>
 * f_u(s,k) = (molar flow of component k leaving outlet s) / (molar flow of component k entering u)
 * </pre>
 *
 * <p>
 * Holding these factors frozen, the per-component node throughput vector {@code v} contributed by a source obeys the
 * linear balance {@code (I - A_k) v = b}, where {@code A_k} is the routing matrix built from the split factors and
 * {@code b} injects the source feed at its entry node. Because every conservative unit has split factors that sum to
 * one across its outlets, the spectral radius of {@code A_k} is below one and the system always has a unique,
 * convergent solution &mdash; recycle and reflux loops included. Custody allocation is then
 * {@code alloc(j,s,k) = f_w(s,k) * v_w}.
 * </p>
 *
 * <h2>Scope</h2>
 * <p>
 * The approach applies to any conservative separation / scrubber / column / valve / cooler / heater / mixer / splitter
 * / pump / compressor network, and therefore to all oil and gas field process configurations. Reactive or mass-transfer
 * contacting units (amine, glycol, MEG, scavengers) are handled as black boxes that reproduce whatever redistribution
 * the base case produced; for hydrocarbon allocation, water and MEG handling does not affect the result.
 * </p>
 *
 * <h2>Key classes</h2>
 * <ul>
 * <li>{@link neqsim.process.allocation.SourceAllocator} &mdash; high-level facade.</li>
 * <li>{@link neqsim.process.allocation.RecoveryFactorExtractor} &mdash; builds the master component slate and frozen
 * split factors from a solved base case.</li>
 * <li>{@link neqsim.process.allocation.AllocationNetwork} &mdash; assembles the routing matrices and resolves
 * source/custody streams.</li>
 * <li>{@link neqsim.process.allocation.LinearAllocationSolver} &mdash; solves {@code (I - A_k) v = b} per component
 * (direct solve with Neumann-series fallback).</li>
 * <li>{@link neqsim.process.allocation.ProductionAllocationResult} &mdash; queryable result with unit conversion,
 * product aggregation, mass closure and JSON export.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.allocation;
