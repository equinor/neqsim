/**
 * Multiphase choke flow correlations for production operations.
 *
 * <p>
 * This package provides models for calculating two-phase (gas-liquid) flow through production
 * chokes. These models are essential for:
 * </p>
 * <ul>
 * <li>Wellhead choke sizing and performance prediction</li>
 * <li>Production optimization and allocation</li>
 * <li>Critical flow detection and management</li>
 * <li>Flow metering and back-allocation</li>
 * </ul>
 *
 * <h2>Available Models</h2>
 *
 * <h3>Mechanistic Models</h3>
 * <ul>
 * <li>{@link neqsim.process.mechanicaldesign.valve.choke.SachdevaChokeFlow} - Industry standard,
 * handles critical and subcritical flow</li>
 * </ul>
 *
 * <h3>Empirical Correlations</h3>
 * <ul>
 * <li>{@link neqsim.process.mechanicaldesign.valve.choke.GilbertChokeFlow} - Classic correlation
 * with variants (Gilbert, Baxendell, Ros, Achong)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // Create a two-phase fluid
 * SystemInterface fluid = new SystemSrkEos(300.0, 100.0);
 * fluid.addComponent("methane", 0.8);
 * fluid.addComponent("n-heptane", 0.2);
 * fluid.setMixingRule(2);
 * fluid.init(0);
 * fluid.init(1);
 *
 * // Create Sachdeva choke model
 * MultiphaseChokeFlow choke = MultiphaseChokeFlowFactory.createModel(ModelType.SACHDEVA);
 * choke.setChokeDiameter(32, "64ths"); // 32/64" = 0.5 inch
 *
 * // Calculate mass flow rate
 * double P1 = 100e5; // 100 bar upstream
 * double P2 = 30e5; // 30 bar downstream
 * double massFlow = choke.calculateMassFlowRate(fluid, P1, P2);
 *
 * // Get detailed results
 * Map&lt;String, Object&gt; results = choke.calculateSizingResults(fluid, P1, P2);
 * System.out.println("Flow regime: " + results.get("flowRegime"));
 * System.out.println("Gas quality: " + results.get("gasQuality"));
 * </pre>
 *
 * <h2>Integration with ThrottlingValve</h2>
 *
 * <p>
 * These models integrate with the {@link neqsim.process.equipment.valve.ThrottlingValve} unit
 * operation for process simulation:
 * </p>
 * 
 * <pre>
 * // Create production choke using ThrottlingValve
 * ThrottlingValve choke = new ThrottlingValve("Production Choke", wellStream);
 * choke.setOutletPressure(30.0, "bara");
 * choke.setPercentValveOpening(50.0);
 *
 * // Configure multiphase model via mechanical design
 * ValveMechanicalDesign design = choke.getMechanicalDesign();
 * design.setValveSizingStandard("Sachdeva"); // or "Gilbert", "Baxendell", "Ros", "Achong"
 * design.setChokeDiameter(0.5, "in");
 *
 * // Run in transient mode to calculate flow
 * choke.setCalculateSteadyState(false);
 * choke.runTransient(0.1);
 * double calculatedFlow = choke.getOutletStream().getFlowRate("kg/hr");
 * </pre>
 *
 * <h2>Model Selection</h2>
 *
 * <table border="1">
 * <caption>Model Selection Guide</caption>
 * <tr>
 * <th>Condition</th>
 * <th>Recommended Model</th>
 * <th>Reason</th>
 * </tr>
 * <tr>
 * <td>General purpose</td>
 * <td>Sachdeva</td>
 * <td>Mechanistic, handles all flow regimes</td>
 * </tr>
 * <tr>
 * <td>Quick field estimate</td>
 * <td>Gilbert</td>
 * <td>Simple, widely used in operations</td>
 * </tr>
 * <tr>
 * <td>High GLR (&gt;5000)</td>
 * <td>Achong</td>
 * <td>Developed for gas-rich conditions</td>
 * </tr>
 * <tr>
 * <td>Subcritical flow</td>
 * <td>Sachdeva</td>
 * <td>Only mechanistic model for subcritical</td>
 * </tr>
 * </table>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Sachdeva, R., et al. (1986). "Two-Phase Flow Through Chokes." SPE 15657.</li>
 * <li>Gilbert, W.E. (1954). "Flowing and Gas-Lift Well Performance." API DPP.</li>
 * <li>Baxendell, P.B. (1958). "Bean Performance - Lake Wells." Trans. AIME.</li>
 * <li>Ros, N.C.J. (1960). "An Analysis of Critical Simultaneous Gas/Liquid Flow." Trans. AIME.</li>
 * <li>Achong, I.B. (1961). "Revised Bean Performance Formula." Internal Report.</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 * @see neqsim.process.mechanicaldesign.valve.choke.MultiphaseChokeFlow
 * @see neqsim.process.mechanicaldesign.valve.choke.MultiphaseChokeFlowFactory
 */
package neqsim.process.mechanicaldesign.valve.choke;
