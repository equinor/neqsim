/**
 * CFD source-term handoff schemas and exporters for safety dispersion studies.
 *
 * <p>
 * The package provides a neutral JSON contract for carrying NeqSim release source terms,
 * compositions, weather cases, inventory basis and consequence branches into external CFD and
 * commercial consequence-analysis tools. Simulator-specific exporters should build on the neutral
 * {@link neqsim.process.safety.cfd.CfdSourceTermCase} payload.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.safety.cfd;
