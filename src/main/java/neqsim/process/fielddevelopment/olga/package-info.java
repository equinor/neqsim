/**
 * OLGA GENKEY export package for field development.
 *
 * <p>
 * This package turns already-validated NeqSim reservoir inflow and wellbore-trajectory data into text an OLGA transient
 * multiphase flow simulator can read directly, as part of a GENKEY input case.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.olga.OlgaWellGenkeyExporter} - Exports a well's liquid inflow performance
 * relationship and trajectory as OLGA GENKEY text (a case-level {@code TABULAR} keyword plus a well network-component
 * block).</li>
 * <li>{@link neqsim.process.fielddevelopment.olga.SyntheticWellTrajectory} - Generates a synthetic (MD, TVD) wellbore
 * deviation trajectory for use when no real survey exists, interpolated from a caller-supplied depth-tier statistics
 * basis (this package ships no embedded well/field data of its own).</li>
 * </ul>
 *
 * <h2>Scope</h2>
 * <p>
 * This package deliberately exports only what its real inputs (the inflow relationship and the trajectory) determine: a
 * {@code TABULAR} inflow-performance table, {@code GEOMETRY}, {@code PIPE} segments, {@code HEATTRANSFER} (a required,
 * caller-supplied lumped coefficient, never a fabricated default), and {@code RESERVOIRCONTACT}. It never emits a
 * layered {@code WALL}/{@code MATERIAL} conduction model (optional per OLGA's own bundled reference cases, and would
 * require real conductivity/capacity/density data this package has no way to know), a case-level
 * {@code PVTFILE=}/{@code FLUID=} reference (the PVT file is declared once per case, not duplicated per well), a choke
 * valve, or segment grading -- see {@link neqsim.process.fielddevelopment.olga.OlgaWellGenkeyExporter}'s own
 * "Deliberately out of scope" Javadoc section for the reasoning behind each. Embed the exported text inside a full
 * genkey case built by the caller.
 * </p>
 *
 * @see neqsim.process.equipment.reservoir.InflowPerformance
 * @see neqsim.process.equipment.reservoir.WellFlow
 * @see neqsim.thermodynamicoperations.propertygenerator.OLGApropertyTableGenerator
 */
package neqsim.process.fielddevelopment.olga;
