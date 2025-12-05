/**
 * Transient multiphase pipe flow models.
 *
 * <p>
 * This package provides classes for simulating transient two-phase (gas-liquid) flow in pipelines.
 * The models are based on the drift-flux formulation and include:
 * </p>
 *
 * <ul>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.TransientPipe} - Main transient
 * simulator using finite volume method with AUSM+ flux splitting</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.PipeSection} - Represents a single
 * discretization cell with state variables</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.FlowRegimeDetector} - Mechanistic flow
 * pattern detection based on Taitel-Dukler and Barnea models</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.DriftFluxModel} - Drift-flux closure
 * relations for slip and holdup</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker} - Tracks
 * liquid pooling at terrain low points</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.SlugTracker} - Lagrangian slug tracking
 * for terrain-induced slugging</li>
 * </ul>
 *
 * <h2>Physical Models</h2>
 *
 * <p>
 * <b>Drift-Flux Model:</b> The core model uses the Zuber-Findlay drift-flux formulation:
 * </p>
 * 
 * <pre>
 * v_G = C_0 * v_m + v_d
 * </pre>
 * <p>
 * where C_0 is the distribution coefficient and v_d is the drift velocity. Flow-regime-dependent
 * correlations (Bendiksen, Harmathy) provide closure.
 * </p>
 *
 * <p>
 * <b>Flow Regime Detection:</b> Uses mechanistic models:
 * </p>
 * <ul>
 * <li>Taitel-Dukler (1976) for horizontal and near-horizontal flow</li>
 * <li>Barnea (1987) unified model for inclined flow</li>
 * </ul>
 *
 * <p>
 * <b>Numerical Method:</b> Explicit finite volume with AUSM+ flux splitting, adaptive CFL-based
 * time stepping, and periodic thermodynamic flash updates using NeqSim equations of state.
 * </p>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * // Create fluid
 * SystemInterface fluid = new SystemSrkEos(300, 50);
 * fluid.addComponent("methane", 0.7);
 * fluid.addComponent("n-pentane", 0.3);
 * fluid.setMixingRule("classic");
 * fluid.setMultiPhaseCheck(true);
 *
 * Stream inlet = new Stream("inlet", fluid);
 * inlet.setFlowRate(5, "kg/sec");
 * inlet.run();
 *
 * // Create transient pipe with terrain
 * TransientPipe pipe = new TransientPipe("Pipeline", inlet);
 * pipe.setLength(1000);
 * pipe.setDiameter(0.2);
 * pipe.setNumberOfSections(50);
 * pipe.setMaxSimulationTime(3600);
 *
 * double[] elevations = new double[50];
 * // Set terrain profile...
 * pipe.setElevationProfile(elevations);
 *
 * pipe.run();
 *
 * // Access results
 * double[] pressures = pipe.getPressureProfile();
 * double[] holdups = pipe.getLiquidHoldupProfile();
 * </pre>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Taitel, Y. and Dukler, A.E. (1976) - AIChE Journal 22(1)</li>
 * <li>Barnea, D. (1987) - Int. J. Multiphase Flow 13(1)</li>
 * <li>Bendiksen, K.H. (1984) - Int. J. Multiphase Flow 10(4)</li>
 * <li>Zuber, N. and Findlay, J.A. (1965) - J. Heat Transfer 87(4)</li>
 * </ul>
 *
 * @since 3.0
 * @author Even Solbraa
 */
package neqsim.process.equipment.pipeline.twophasepipe;
