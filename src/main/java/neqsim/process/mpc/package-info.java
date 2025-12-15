/**
 * Model Predictive Control (MPC) integration package for NeqSim ProcessSystem.
 *
 * <p>
 * This package provides seamless integration between NeqSim's rigorous thermodynamic process
 * simulation and industrial Model Predictive Control (MPC) systems. It bridges the gap between the
 * physical simulation in {@link neqsim.process.processmodel.ProcessSystem} and the control
 * algorithms in {@link neqsim.process.controllerdevice.ModelPredictiveController}.
 * </p>
 *
 * <h2>Key Classes</h2>
 *
 * <h3>Variable Definitions</h3>
 * <ul>
 * <li>{@link neqsim.process.mpc.MPCVariable} - Base class for MPC variables</li>
 * <li>{@link neqsim.process.mpc.ManipulatedVariable} - Manipulated Variables (MVs) - what the
 * controller adjusts</li>
 * <li>{@link neqsim.process.mpc.ControlledVariable} - Controlled Variables (CVs) - what we want to
 * control</li>
 * <li>{@link neqsim.process.mpc.DisturbanceVariable} - Disturbance Variables (DVs) - measured but
 * uncontrolled</li>
 * </ul>
 *
 * <h3>Model Identification</h3>
 * <ul>
 * <li>{@link neqsim.process.mpc.ProcessLinearizer} - Automatic Jacobian calculation using finite
 * differences</li>
 * <li>{@link neqsim.process.mpc.LinearizationResult} - Container for gain matrices and operating
 * point data</li>
 * <li>{@link neqsim.process.mpc.StepResponseGenerator} - Automated step testing framework</li>
 * <li>{@link neqsim.process.mpc.StepResponse} - Single MV-CV step response with FOPDT fitting</li>
 * </ul>
 *
 * <h3>Prediction and Control</h3>
 * <ul>
 * <li>{@link neqsim.process.mpc.NonlinearPredictor} - Multi-step prediction using full NeqSim
 * simulation</li>
 * <li>{@link neqsim.process.mpc.ProcessLinkedMPC} - Bridge class connecting ProcessSystem to
 * MPC</li>
 * </ul>
 *
 * <h3>Export and Integration</h3>
 * <ul>
 * <li>{@link neqsim.process.mpc.StateSpaceExporter} - Export models to JSON/CSV/MATLAB for external
 * MPC</li>
 * </ul>
 *
 * <h3>Industrial Control System Integration</h3>
 * <ul>
 * <li>{@link neqsim.process.mpc.IndustrialMPCExporter} - Export step-response models, gain
 * matrices, and configurations in formats compatible with industrial MPC platforms</li>
 * <li>{@link neqsim.process.mpc.ControllerDataExchange} - Real-time bidirectional data exchange
 * interface with quality flags and execution status for PCS integration</li>
 * <li>{@link neqsim.process.mpc.SoftSensorExporter} - Export soft-sensor and estimator
 * configurations for industrial calculation engines</li>
 * <li>{@link neqsim.process.mpc.SubrModlExporter} - Export nonlinear models in SubrModl format with
 * SubrXvr definitions and DtaIx mappings</li>
 * <li>{@link neqsim.process.mpc.StateVariable} - State variable (SVR) for nonlinear MPC with bias
 * tracking and prediction</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 *
 * <h3>Basic MPC Setup</h3>
 * 
 * <pre>
 * {@code
 * // Build process system
 * ProcessSystem process = new ProcessSystem();
 * Stream feed = new Stream("feed", fluid);
 * Valve valve = new Valve("inlet_valve", feed);
 * Separator separator = new Separator("separator", valve.getOutletStream());
 * process.add(feed);
 * process.add(valve);
 * process.add(separator);
 * process.run();
 *
 * // Create linked MPC
 * ProcessLinkedMPC mpc = new ProcessLinkedMPC("pressureController", process);
 *
 * // Define variables
 * mpc.addMV("inlet_valve", "opening", 0.0, 1.0); // Valve opening 0-100%
 * mpc.addCV("separator", "pressure", 50.0); // Control to 50 bar
 * mpc.setConstraint("separator", "pressure", 40.0, 60.0); // Hard limits
 *
 * // Identify model and configure
 * mpc.setSampleTime(60.0);
 * mpc.setPredictionHorizon(20);
 * mpc.setControlHorizon(5);
 * mpc.identifyModel(60.0);
 *
 * // Control loop
 * while (running) {
 *   double[] moves = mpc.step(); // Calculate, apply, run
 *   System.out.println("MV=" + moves[0] + " CV=" + mpc.getCurrentCVs()[0]);
 *   Thread.sleep((long) (mpc.getSampleTime() * 1000));
 * }
 * }
 * </pre>
 *
 * <h3>Model Export for External MPC</h3>
 * 
 * <pre>
 * {@code
 * // After linearization
 * StateSpaceExporter exporter = mpc.exportModel();
 * StateSpaceExporter.StateSpaceModel ssModel = exporter.toDiscreteStateSpace(60.0);
 *
 * // Export to various formats
 * exporter.exportJSON("process_model.json"); // For Python
 * exporter.exportMATLAB("process_model.m"); // For MATLAB MPC Toolbox
 * exporter.exportCSV("model_"); // CSV files
 * }
 * </pre>
 *
 * <h3>Advanced: Nonlinear Prediction</h3>
 * 
 * <pre>
 * {@code
 * // Enable nonlinear prediction for highly nonlinear processes
 * mpc.setUseNonlinearPrediction(true);
 * mpc.identifyModel(60.0);
 *
 * // Predictions now use full NeqSim simulation
 * double[] moves = mpc.calculate();
 * }
 * </pre>
 *
 * <h2>Integration with Existing MPC</h2>
 *
 * <p>
 * This package is designed to work with the existing
 * {@link neqsim.process.controllerdevice.ModelPredictiveController}. The
 * {@link neqsim.process.mpc.ProcessLinkedMPC} class automatically configures the underlying MPC
 * from the ProcessSystem linearization.
 * </p>
 *
 * <h2>AI Platform Integration</h2>
 *
 * <p>
 * The package is designed for integration with AI/ML platforms that require process models for
 * optimization and control. Key integration points:
 * </p>
 * <ul>
 * <li>JSON export for Python-based MPC implementations</li>
 * <li>Step response coefficients for DMC-style controllers</li>
 * <li>State-space models for Kalman filtering and state estimation</li>
 * <li>Nonlinear predictions for training machine learning models</li>
 * </ul>
 *
 * <h2>Industrial MPC Integration</h2>
 *
 * <p>
 * The package provides seamless integration with industrial control systems through standard
 * interfaces and export formats:
 * </p>
 *
 * <h3>Step Response Model Export</h3>
 *
 * <pre>
 * {@code
 * // Export for industrial MPC systems using linear step-response models
 * IndustrialMPCExporter exporter = mpc.createIndustrialExporter();
 * exporter.setTagPrefix("UNIT1.separator");
 * exporter.setApplicationName("GasProcessing");
 *
 * // Export step response coefficients in CSV format
 * exporter.exportStepResponseCSV("step_responses.csv");
 *
 * // Export complete object structure for core configuration
 * exporter.exportObjectStructure("controller_config.json");
 *
 * // Export comprehensive configuration with all model data
 * exporter.exportComprehensiveConfiguration("mpc_config.json");
 * }
 * </pre>
 *
 * <h3>Real-Time Data Exchange</h3>
 *
 * <pre>
 * {@code
 * // Create data exchange interface for PCS integration
 * ControllerDataExchange exchange = mpc.createDataExchange();
 * exchange.setTagPrefix("UNIT1.MPC");
 *
 * // Control loop with external controller
 * while (running) {
 *   // Update inputs from process measurements
 *   exchange.updateInputs(mvValues, cvValues, dvValues);
 *   exchange.updateSetpoints(setpoints);
 *   exchange.updateLimits(cvLowLimits, cvHighLimits, mvLowLimits, mvHighLimits);
 *
 *   // Execute and get outputs
 *   exchange.execute();
 *   ControllerDataExchange.ControllerOutput output = exchange.getOutputs();
 *
 *   // Apply to process
 *   if (output.getStatus() == ControllerDataExchange.ExecutionStatus.SUCCESS) {
 *     applyMVs(output.getMvTargets());
 *   }
 * }
 * }
 * </pre>
 *
 * <h3>Soft Sensor Export</h3>
 *
 * <pre>
 * {@code
 * // Export soft-sensor configurations for calculation engines
 * SoftSensorExporter softExporter = new SoftSensorExporter(process);
 * softExporter.setTagPrefix("UNIT1");
 *
 * // Add sensors for key properties
 * softExporter.addDensitySensor("sep_gas_density", "separator", "gas outlet");
 * softExporter.addViscositySensor("sep_oil_visc", "separator", "oil outlet");
 * softExporter.addPhaseFractionSensor("sep_gas_frac", "separator");
 * softExporter.addCompositionEstimator("sep_comp", "separator", "gas outlet",
 *     new String[] {"methane", "ethane", "propane"});
 *
 * // Export in JSON and CVT formats
 * softExporter.exportConfiguration("soft_sensors.json");
 * softExporter.exportCVTFormat("soft_sensors.cvt");
 * }
 * </pre>
 *
 * <h3>Nonlinear MPC with SubrModl Export</h3>
 *
 * <pre>
 * {@code
 * // Create MPC with state variables for nonlinear model
 * ProcessLinkedMPC mpc = new ProcessLinkedMPC("wellController", process);
 * mpc.addMV("choke", "opening", 0.0, 1.0);
 * mpc.addCV("well", "pressure", 50.0);
 * mpc.addDV("reservoir", "pressure");
 *
 * // Add state variables (SVR) for internal model states
 * mpc.addSVR("well", "flowIn", "qin");
 * mpc.addSVR("well", "flowOut", "qout");
 * mpc.addSVR("choke", "cv", "cv");
 *
 * // Export for nonlinear MPC system
 * SubrModlExporter exporter = mpc.createSubrModlExporter();
 * exporter.setModelName("WellModel");
 * exporter.addParameter("Volume", 100.0, "m3");
 * exporter.addParameter("Height", 2000.0, "m");
 * exporter.addParameter("Density", 700.0, "kg/m3");
 *
 * // Export configuration files
 * exporter.exportConfiguration("well_config.txt");
 * exporter.exportMPCConfiguration("mpc_config.txt", true); // true = SQP solver
 * exporter.exportIndexTable("well_ixid.cpp");
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 * @see neqsim.process.processmodel.ProcessSystem
 * @see neqsim.process.controllerdevice.ModelPredictiveController
 */
package neqsim.process.mpc;
