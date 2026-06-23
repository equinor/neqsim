/**
 * Fire rupture screening for process piping segments.
 *
 * <p>
 * The package extends NeqSim process safety calculations with screening models for trapped liquid exposed to fire and
 * blowdown pipe segments exposed to prescribed fire scenarios. It reuses trapped inventory, fire heat transfer,
 * temperature-dependent material data, release source terms, and barrier demand objects from the existing safety
 * framework. The pipe-fire workflow also includes evidence references, readiness verdicts, standards screening,
 * deterministic uncertainty cases, and source-term handoff packages for STID/TR2000-driven agent workflows.
 * </p>
 */
package neqsim.process.safety.rupture;
