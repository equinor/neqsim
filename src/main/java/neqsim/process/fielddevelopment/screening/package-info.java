/**
 * Screening tools for flow assurance, safety, emissions, and economics.
 *
 * <p>
 * This package provides rapid screening assessments for concept-level evaluation. All screeners are
 * designed for early-phase decision support, providing classifications and recommendations without
 * requiring detailed engineering.
 * </p>
 *
 * <h2>Flow Assurance Screening</h2>
 * <p>
 * {@link neqsim.process.fielddevelopment.screening.FlowAssuranceScreener} evaluates:
 * </p>
 * <ul>
 * <li><b>Hydrate risk</b>: Uses NeqSim CPA equation of state for accurate hydrate equilibrium</li>
 * <li><b>Wax risk</b>: Correlation-based WAT estimation from fluid type</li>
 * <li><b>Corrosion</b>: CO2 and H2S content vs. material thresholds</li>
 * <li><b>Scale and erosion</b>: Based on water cut and flow rates</li>
 * </ul>
 *
 * <h2>Safety Screening</h2>
 * <p>
 * {@link neqsim.process.fielddevelopment.screening.SafetyScreener} assesses:
 * </p>
 * <ul>
 * <li>ESD system complexity requirements</li>
 * <li>Fire protection needs based on fluid properties</li>
 * <li>Manning requirements for processing location</li>
 * <li>H2S safety considerations</li>
 * </ul>
 *
 * <h2>Emissions Tracking</h2>
 * <p>
 * {@link neqsim.process.fielddevelopment.screening.EmissionsTracker} calculates:
 * </p>
 * <ul>
 * <li>Annual CO2 emissions from fuel gas, flaring, and fugitives</li>
 * <li>CO2 intensity (kg CO2 per barrel of oil equivalent)</li>
 * <li>Emissions breakdown by source category</li>
 * </ul>
 *
 * <h2>Economics Estimation</h2>
 * <p>
 * {@link neqsim.process.fielddevelopment.screening.EconomicsEstimator} provides:
 * </p>
 * <ul>
 * <li>CAPEX breakdown (facility, equipment, wells, infrastructure)</li>
 * <li>Annual OPEX estimate</li>
 * <li>Unit costs (CAPEX/OPEX per boe)</li>
 * <li>±40% accuracy range (AACE Class 5)</li>
 * </ul>
 *
 * <h2>Result Classifications</h2>
 * <p>
 * {@link neqsim.process.fielddevelopment.screening.FlowAssuranceResult} provides:
 * </p>
 * <ul>
 * <li>{@code PASS}: No mitigation required</li>
 * <li>{@code MARGINAL}: Monitoring or simple mitigation recommended</li>
 * <li>{@code FAIL}: Active mitigation mandatory</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // Flow assurance screening
 * FlowAssuranceScreener faScreener = new FlowAssuranceScreener();
 * FlowAssuranceReport faReport = faScreener.screen(concept, 4.0, 150.0);
 * 
 * // Economics estimation
 * EconomicsEstimator econ = new EconomicsEstimator();
 * EconomicsEstimator.EconomicsReport econReport = econ.estimate(concept, facilityConfig);
 * 
 * // Emissions tracking
 * EmissionsTracker emissions = new EmissionsTracker();
 * EmissionsTracker.EmissionsReport emissionsReport = emissions.calculate(concept, facilityConfig);
 * </pre>
 *
 * @since 3.0
 * @see neqsim.process.fielddevelopment.evaluation
 * @see neqsim.process.fielddevelopment.concept
 */
package neqsim.process.fielddevelopment.screening;
