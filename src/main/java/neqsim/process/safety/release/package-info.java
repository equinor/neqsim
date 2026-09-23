/**
 * Source term generation for consequence modeling.
 *
 * <p>
 * This package provides tools for calculating release source terms for QRA consequence analysis:
 * <ul>
 * <li>{@link neqsim.process.safety.release.LeakModel} - Mass flow, jet velocity, and momentum calculations</li>
 * <li>{@link neqsim.process.safety.release.SourceTermResult} - Time-series release data with export capabilities</li>
 * <li>{@link neqsim.process.safety.release.ReleaseOrientation} - Jet orientation for dispersion modeling</li>
 * <li>{@link neqsim.process.safety.release.ReleaseSolidRiskAssessment} - Fail-closed mixture-specific solid and hydrate
 * applicability assessment</li>
 * <li>{@link neqsim.process.safety.release.IdealGasFannoPipeReleaseModel} - Assessed quasi-steady ideal-gas pipe
 * release with specified Darcy friction</li>
 * </ul>
 *
 * <p>
 * The source terms can be exported in formats compatible with consequence modeling tools:
 * <ul>
 * <li>PHAST (DNV)</li>
 * <li>FLACS (Gexcon)</li>
 * <li>KFX (DNV)</li>
 * <li>OpenFOAM</li>
 * </ul>
 *
 * <p>
 * LeakModel time integration is limited to a rigid, adiabatic, well-mixed gas inventory. It conserves component mass
 * and energy and fails explicitly if condensation occurs. The scalar release correlations remain screening methods. The
 * homogeneous-equilibrium model assesses its resolved stations for equilibrium solids and hydrate risk, but does not
 * calculate solid-bearing release flow. The finite-pipe model excludes real-gas transient decompression, heat transfer,
 * multiphase slip and solid-bearing transport.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * SystemInterface gas = new SystemSrkEos(300.0, 50.0);
 * gas.addComponent("methane", 1.0);
 * gas.setMixingRule("classic");
 * gas.init(0);
 * gas.init(1);
 *
 * LeakModel leak = LeakModel.builder().fluid(gas).holeDiameter(0.025) // 25mm hole
 *     .vesselVolume(10.0) // 10 m³
 *     .scenarioName("HP Separator Leak").build();
 *
 * SourceTermResult result = leak.calculateSourceTerm(300.0, 1.0); // 5 minutes
 * double peakFlowKgS = result.getPeakMassFlowRate();
 * double totalReleasedKg = result.getTotalMassReleased();
 * </pre>
 *
 * @see neqsim.process.safety.risk Probabilistic risk analysis
 * @see neqsim.process.safety.envelope Safety envelope calculations
 * @see neqsim.process.safety.InitiatingEvent Standard initiating events
 * @see neqsim.process.safety.BoundaryConditions Environmental conditions
 */
package neqsim.process.safety.release;
