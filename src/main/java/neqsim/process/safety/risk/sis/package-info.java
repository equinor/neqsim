/**
 * Safety Instrumented System (SIS) Integration Package.
 *
 * <p>
 * This package provides integration between operational risk assessment and Safety Instrumented
 * Systems (SIS) per IEC 61508/61511 standards. Key capabilities include:
 * </p>
 *
 * <ul>
 * <li><strong>Safety Instrumented Function (SIF) Modeling:</strong> Model SIFs with SIL targets,
 * architectures (1oo1, 1oo2, 2oo3, etc.), and PFD calculations</li>
 * <li><strong>Layer of Protection Analysis (LOPA):</strong> Semi-quantitative risk assessment with
 * IPL credit allocation</li>
 * <li><strong>SIL Verification:</strong> Verify SIL targets are met based on PFD, architecture, and
 * systematic capability</li>
 * <li><strong>Risk Reduction Tracking:</strong> Track how SIS reduces process hazard frequency</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.safety.risk.sis.SafetyInstrumentedFunction} - Models individual SIFs
 * with SIL, architecture, and PFD</li>
 * <li>{@link neqsim.process.safety.risk.sis.SISIntegratedRiskModel} - Extends RiskModel with SIS
 * integration</li>
 * <li>{@link neqsim.process.safety.risk.sis.LOPAResult} - LOPA analysis results with protection
 * layers</li>
 * <li>{@link neqsim.process.safety.risk.sis.SILVerificationResult} - SIL verification results</li>
 * <li>{@link neqsim.process.safety.risk.sis.SISRiskResult} - Combined risk results with SIS
 * mitigation</li>
 * </ul>
 *
 * <h2>Standards Support</h2>
 * <ul>
 * <li>IEC 61508 - Functional Safety of Electrical/Electronic/Programmable Electronic Safety-related
 * Systems</li>
 * <li>IEC 61511 - Functional Safety: Safety Instrumented Systems for the Process Industry</li>
 * <li>ISA 84.00.01 - Functional Safety: Safety Instrumented Systems</li>
 * <li>NORSOK S-001/S-002 - Technical Safety</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * // Create SIS-integrated risk model
 * SISIntegratedRiskModel riskModel = new SISIntegratedRiskModel("HIPPS Analysis");
 * 
 * // Add SIF
 * SafetyInstrumentedFunction hipps = new SafetyInstrumentedFunction("SDV-001", "Main HIPPS",
 *     SafetyInstrumentedFunction.SIFCategory.HIPPS, 2, "1oo2");
 * hipps.setProofTestIntervalYears(1);
 * riskModel.addSIF(hipps);
 * 
 * // Add IPL
 * riskModel.addIPL("PSV-001", "Pressure Safety Valve", 0.01);
 * 
 * // Perform LOPA
 * LOPAResult lopa = riskModel.performLOPA("Overpressure", 0.1, // initiating frequency
 *     1e-4); // target frequency
 * 
 * // Verify SIL
 * SILVerificationResult verification = riskModel.verifySIL(hipps);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.safety.risk.RiskModel
 */
package neqsim.process.safety.risk.sis;
