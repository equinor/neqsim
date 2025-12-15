/**
 * PVT Regression Framework for tuning equation of state models against experimental data.
 * 
 * <h2>Overview</h2>
 * <p>
 * This package provides a comprehensive framework for regressing EOS model parameters against
 * experimental PVT data. The framework supports:
 * </p>
 * <ul>
 * <li>Constant Composition Expansion (CCE/CME) data</li>
 * <li>Constant Volume Depletion (CVD) data</li>
 * <li>Differential Liberation (DLE/DL) data</li>
 * <li>Separator test data</li>
 * <li>Swelling test data</li>
 * <li>Viscosity data</li>
 * </ul>
 * 
 * <h2>Tunable Parameters</h2>
 * <p>
 * The following parameters can be optimized:
 * </p>
 * <ul>
 * <li>Binary Interaction Parameters (BIPs) - methane/C7+, C2-C6/C7+, CO2/HC, N2/HC</li>
 * <li>Volume translation parameters for C7+ fractions</li>
 * <li>Critical property multipliers (Tc, Pc, omega) for C7+ fractions</li>
 * <li>Plus fraction characterization parameters (molar mass, gamma distribution)</li>
 * </ul>
 * 
 * <h2>Mathematical Formulation</h2>
 * <p>
 * The regression uses weighted least squares minimization:
 * </p>
 * 
 * <pre>
 * F(θ) = Σ wᵢ · [(yᵢ,calc - yᵢ,exp) / yᵢ,exp]²
 * </pre>
 * 
 * <p>
 * Multi-objective optimization combines multiple experiment types:
 * </p>
 * 
 * <pre>
 * F_total = w_CCE · F_CCE + w_CVD · F_CVD + w_DLE · F_DLE + w_SEP · F_SEP
 * </pre>
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // Create base fluid
 * SystemInterface fluid = new SystemSrkEos(373.15, 200.0);
 * fluid.addComponent("methane", 0.70);
 * fluid.addComponent("ethane", 0.10);
 * fluid.addComponent("propane", 0.05);
 * fluid.addPlusFraction("C7+", 0.15, 0.150, 0.82);
 * fluid.setMixingRule(2);
 * 
 * // Create regression
 * PVTRegression regression = new PVTRegression(fluid);
 * 
 * // Add experimental data
 * double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
 * double[] relativeVolumes = {0.98, 1.00, 1.08, 1.25, 1.55};
 * regression.addCCEData(pressures, relativeVolumes, 373.15);
 * 
 * // Add regression parameters
 * regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10, 0.03);
 * regression.addRegressionParameter(RegressionParameter.VOLUME_SHIFT_C7PLUS);
 * 
 * // Run regression
 * RegressionResult result = regression.runRegression();
 * 
 * // Get tuned fluid
 * SystemInterface tunedFluid = result.getTunedFluid();
 * </pre>
 * 
 * <h2>References</h2>
 * <ul>
 * <li>Pedersen, K.S., Christensen, P.L., "Phase Behavior of Petroleum Reservoir Fluids", CRC Press,
 * 2007</li>
 * <li>Whitson, C.H., Brulé, M.R., "Phase Behavior", SPE Monograph, 2000</li>
 * </ul>
 * 
 * @author NeqSim Development Team
 * @see neqsim.pvtsimulation.simulation.ConstantMassExpansion
 * @see neqsim.pvtsimulation.simulation.ConstantVolumeDepletion
 * @see neqsim.pvtsimulation.simulation.DifferentialLiberation
 * @see neqsim.pvtsimulation.simulation.SeparatorTest
 * @see neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt
 */
package neqsim.pvtsimulation.regression;
