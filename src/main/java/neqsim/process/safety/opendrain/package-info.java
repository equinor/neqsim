/**
 * Open-drain review support using normalized STID/P&amp;ID and optional tagreader evidence.
 *
 * <p>
 * The package evaluates NORSOK S-001 Clause 9 requirements without connecting directly to STID,
 * document-management systems, or plant historians. External workflows normalize evidence into
 * JSON, while {@link neqsim.process.safety.opendrain.OpenDrainProcessEvidenceCalculator} can derive
 * process liquid leak rates, liquid density, pressure, fire-water load, and drain capacity from
 * NeqSim streams and process equipment.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
package neqsim.process.safety.opendrain;
