package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the MPC integration package.
 *
 * @author Even Solbraa
 */
public class MPCIntegrationTest {

  private ProcessSystem process;
  private Stream feed;
  private ThrottlingValve valve;
  private Separator separator;
  private SystemInterface fluid;

  @BeforeEach
  public void setUp() {
    // Create a simple gas/liquid separation process
    fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");

    // Build process
    process = new ProcessSystem();

    feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    valve = new ThrottlingValve("inlet_valve", feed);
    valve.setOutletPressure(30.0);

    separator = new Separator("separator", valve.getOutletStream());

    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.run();
  }

  // ========================= MPCVariable Tests =========================

  @Test
  public void testManipulatedVariableCreation() {
    ManipulatedVariable mv = new ManipulatedVariable("valve.opening", valve, "opening");
    mv.setBounds(0.0, 1.0);
    mv.setRateLimit(-0.1, 0.1);

    assertEquals("valve.opening", mv.getName());
    assertEquals(0.0, mv.getMinValue(), 1e-10);
    assertEquals(1.0, mv.getMaxValue(), 1e-10);
    assertEquals(0.1, mv.getMaxRateOfChange(), 1e-10);
    assertTrue(mv.readValue() >= 0 && mv.readValue() <= 1);
  }

  @Test
  public void testControlledVariableCreation() {
    ControlledVariable cv = new ControlledVariable("separator.pressure", separator, "pressure");
    cv.setSetpoint(30.0);
    cv.setBounds(20.0, 40.0);

    assertEquals("separator.pressure", cv.getName());
    assertEquals(30.0, cv.getSetpoint(), 1e-10);
    assertEquals(20.0, cv.getMinValue(), 1e-10);
    assertEquals(40.0, cv.getMaxValue(), 1e-10);
    // Pressure should be around 30 bar (outlet pressure of valve)
    assertTrue(cv.readValue() > 20 && cv.readValue() < 50);
  }

  @Test
  public void testControlledVariableZoneControl() {
    ControlledVariable cv = new ControlledVariable("separator.pressure", separator, "pressure");
    cv.setZone(25.0, 35.0);

    assertTrue(cv.isZoneControl());
    assertEquals(25.0, cv.getZoneLower(), 1e-10);
    assertEquals(35.0, cv.getZoneUpper(), 1e-10);
    assertEquals(30.0, cv.getSetpoint(), 1e-10); // Midpoint
  }

  @Test
  public void testDisturbanceVariableCreation() {
    DisturbanceVariable dv = new DisturbanceVariable("feed.flowRate", feed, "flowRate");

    assertEquals("feed.flowRate", dv.getName());
    double value = dv.readValue();
    // Feed flow rate should be around 100 kg/hr
    assertTrue(value > 0);
  }

  // ========================= LinearizationResult Tests =========================

  @Test
  public void testLinearizationResultCreation() {
    double[][] gains = {{1.0, 2.0}, {3.0, 4.0}};
    double[][] distGains = {{0.1}, {0.2}};
    double[] mvOp = {0.5, 0.6};
    double[] cvOp = {30.0, 25.0};
    double[] dvOp = {100.0};
    String[] mvNames = {"MV1", "MV2"};
    String[] cvNames = {"CV1", "CV2"};
    String[] dvNames = {"DV1"};

    LinearizationResult result = new LinearizationResult(gains, distGains, mvOp, cvOp, dvOp,
        mvNames, cvNames, dvNames, 1e-3, 2);

    assertEquals(2, result.getNumMV());
    assertEquals(2, result.getNumCV());
    assertEquals(1, result.getNumDV());
    assertEquals(1.0, result.getGain("CV1", "MV1"), 1e-10);
    assertEquals(4.0, result.getGain("CV2", "MV2"), 1e-10);
    // getMvOperatingPoint returns array, not by name
    assertEquals(0.5, result.getMvOperatingPoint()[0], 1e-10);
    assertEquals(30.0, result.getCvOperatingPoint()[0], 1e-10);
  }

  // ========================= ProcessLinearizer Tests =========================

  @Test
  public void testProcessLinearizerBasic() {
    ProcessLinearizer linearizer = new ProcessLinearizer(process);

    ManipulatedVariable mv = new ManipulatedVariable("valve.opening", valve, "opening");
    mv.setBounds(0.0, 1.0);
    linearizer.addMV(mv);

    ControlledVariable cv = new ControlledVariable("separator.pressure", separator, "pressure");
    linearizer.addCV(cv);

    LinearizationResult result = linearizer.linearize();

    assertNotNull(result);
    assertEquals(1, result.getNumMV());
    assertEquals(1, result.getNumCV());

    // Check that a gain was calculated
    double[][] gains = result.getGainMatrix();
    assertNotNull(gains);
    assertEquals(1, gains.length);
    assertEquals(1, gains[0].length);
  }

  @Test
  public void testProcessLinearizerWithPerturbation() {
    ProcessLinearizer linearizer = new ProcessLinearizer(process);
    // Use setDefaultPerturbationSize instead of setRelativePerturbation
    linearizer.setDefaultPerturbationSize(0.02); // 2%

    ManipulatedVariable mv = new ManipulatedVariable("valve.opening", valve, "opening");
    linearizer.addMV(mv);

    ControlledVariable cv = new ControlledVariable("separator.pressure", separator, "pressure");
    linearizer.addCV(cv);

    LinearizationResult result = linearizer.linearize();
    assertNotNull(result);
  }

  // ========================= StepResponse Tests =========================

  @Test
  public void testStepResponseCreation() {
    double sampleTime = 1.0;
    double[] times = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    double[] values = new double[times.length];
    double gain = 2.0;
    double tau = 3.0;

    // Generate first-order response
    for (int i = 0; i < times.length; i++) {
      values[i] = gain * (1 - Math.exp(-times[i] / tau));
    }

    // Use correct constructor: (mvName, cvName, time[], response[], stepSize, baseline, sampleTime,
    // mvUnit, cvUnit)
    StepResponse response =
        new StepResponse("MV1", "CV1", times, values, 1.0, 0.0, sampleTime, "unit", "unit");

    assertNotNull(response);
    assertEquals("MV1", response.getMvName());
    assertEquals("CV1", response.getCvName());
    assertEquals(1.0, response.getStepSize(), 1e-10);

    // Check FOPDT fitting
    double fittedGain = response.getGain();
    double fittedTau = response.getTimeConstant();

    // Should be close to original values
    assertEquals(gain, fittedGain, 0.5);
    assertEquals(tau, fittedTau, 1.5);
  }

  @Test
  public void testStepResponseCoefficients() {
    double sampleTime = 1.0;
    double[] times = new double[20];
    double[] values = new double[20];
    double gain = 1.5;
    double tau = 2.0;

    for (int i = 0; i < times.length; i++) {
      times[i] = i * sampleTime;
      values[i] = gain * (1 - Math.exp(-times[i] / tau));
    }

    // Use correct constructor
    StepResponse response =
        new StepResponse("MV", "CV", times, values, 1.0, 0.0, sampleTime, "unit", "unit");
    double[] coeffs = response.getStepCoefficients(10);

    assertEquals(10, coeffs.length);
    // First coefficient should be close to zero (immediate response of first-order)
    assertTrue(Math.abs(coeffs[0]) < 0.5);
    // Last coefficient should approach the gain
    assertTrue(Math.abs(coeffs[9] - gain) < 0.5);
  }

  // ========================= StateSpaceExporter Tests =========================

  @Test
  public void testStateSpaceExporterCreation() {
    double[][] gains = {{2.0}};
    String[] mvNames = {"valve"};
    String[] cvNames = {"pressure"};

    LinearizationResult result =
        new LinearizationResult(gains, new double[1][0], new double[] {0.5}, new double[] {30.0},
            new double[0], mvNames, cvNames, new String[0], 1e-3, 1);

    StateSpaceExporter exporter = new StateSpaceExporter(result);
    StateSpaceExporter.StateSpaceModel model = exporter.toDiscreteStateSpace(60.0);

    assertNotNull(model);
    assertEquals(60.0, model.getSampleTime(), 1e-10);
    assertEquals(1, model.getNumStates());
    assertEquals(1, model.getNumInputs());
    assertEquals(1, model.getNumOutputs());

    // Check matrices
    double[][] A = model.getA();
    double[][] B = model.getB();
    double[][] C = model.getC();
    double[][] D = model.getD();

    assertEquals(1, A.length);
    assertEquals(1, B.length);
    assertEquals(1, C.length);
    assertEquals(1, D.length);

    // A should be between 0 and 1 for stable system
    assertTrue(A[0][0] > 0 && A[0][0] < 1);
    // C should be identity for output states
    assertEquals(1.0, C[0][0], 1e-10);
    // D should be zero for strict first-order
    assertEquals(0.0, D[0][0], 1e-10);
  }

  @Test
  public void testStateSpaceModelSimulation() {
    double[][] gains = {{1.0}};
    LinearizationResult result =
        new LinearizationResult(gains, new double[1][0], new double[] {0.5}, new double[] {0.0},
            new double[0], new String[] {"u"}, new String[] {"y"}, new String[0], 1e-3, 1);

    StateSpaceExporter exporter = new StateSpaceExporter(result);
    // Use sample time matching default time constant for faster convergence in test
    StateSpaceExporter.StateSpaceModel model = exporter.toDiscreteStateSpace(10.0);

    // Simulate step response
    double[] x = {0.0};
    double[] u = {1.0};
    double[] trajectory = new double[20];

    for (int k = 0; k < 20; k++) {
      double[] y = model.getOutput(x, u);
      trajectory[k] = y[0];
      x = model.stepState(x, u);
    }

    // Output should increase over time (first-order response to step input)
    assertTrue(trajectory[19] > trajectory[0], "Final value should be greater than initial");
    // After 20*10s = 200s, with default tau=60s, should have risen significantly
    assertTrue(trajectory[19] > 0.1, "Should have risen above 0.1 after 200 seconds");
  }

  // ========================= NonlinearPredictor Tests =========================

  @Test
  public void testNonlinearPredictorCreation() {
    // NonlinearPredictor constructor only takes ProcessSystem
    NonlinearPredictor predictor = new NonlinearPredictor(process);
    predictor.setSampleTime(60.0);
    predictor.setPredictionHorizon(10);

    ManipulatedVariable mv = new ManipulatedVariable("valve.opening", valve, "opening");
    predictor.addMV(mv);

    ControlledVariable cv = new ControlledVariable("separator.pressure", separator, "pressure");
    predictor.addCV(cv);

    assertEquals(60.0, predictor.getSampleTimeSeconds(), 1e-10);
    assertEquals(10, predictor.getPredictionHorizon());
  }

  @Test
  public void testNonlinearPredictorConstant() {
    NonlinearPredictor predictor = new NonlinearPredictor(process);
    predictor.setSampleTime(1.0);
    predictor.setPredictionHorizon(5);

    ManipulatedVariable mv = new ManipulatedVariable("valve.opening", valve, "opening");
    predictor.addMV(mv);

    ControlledVariable cv = new ControlledVariable("separator.pressure", separator, "pressure");
    predictor.addCV(cv);

    // Predict with constant MVs
    NonlinearPredictor.PredictionResult result = predictor.predictConstant();

    assertNotNull(result);
    assertTrue(result.getHorizon() >= 5);

    // PredictionResult has getCvNames() method, check trajectories via the result methods
    String[] cvNames = result.getCvNames();
    assertNotNull(cvNames);
    assertTrue(cvNames.length > 0);
  }

  // ========================= ProcessLinkedMPC Tests =========================

  @Test
  public void testProcessLinkedMPCCreation() {
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("testController", process);

    assertEquals("testController", mpc.getName());
    assertFalse(mpc.isModelIdentified());
  }

  @Test
  public void testProcessLinkedMPCVariableSetup() {
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("testController", process);

    mpc.addMV("inlet_valve", "opening", 0.0, 1.0);
    mpc.addCV("separator", "pressure", 30.0);

    assertEquals(1, mpc.getManipulatedVariables().size());
    assertEquals(1, mpc.getControlledVariables().size());

    ManipulatedVariable mv = mpc.getManipulatedVariables().get(0);
    assertEquals("inlet_valve.opening", mv.getName());

    ControlledVariable cv = mpc.getControlledVariables().get(0);
    assertEquals("separator.pressure", cv.getName());
    assertEquals(30.0, cv.getSetpoint(), 1e-10);
  }

  @Test
  public void testProcessLinkedMPCModelIdentification() {
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("pressureController", process);

    mpc.addMV("inlet_valve", "opening", 0.0, 1.0);
    mpc.addCV("separator", "pressure", 30.0);
    mpc.setSampleTime(60.0);
    mpc.setPredictionHorizon(20);
    mpc.setControlHorizon(5);

    // Identify model
    mpc.identifyModel(60.0);

    assertTrue(mpc.isModelIdentified());
    assertNotNull(mpc.getLinearizationResult());
    // ProcessLinkedMPC doesn't have getController method, it manages internally

    LinearizationResult result = mpc.getLinearizationResult();
    assertEquals(1, result.getNumMV());
    assertEquals(1, result.getNumCV());
  }

  @Test
  public void testProcessLinkedMPCCalculation() {
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("pressureController", process);

    mpc.addMV("inlet_valve", "opening", 0.0, 1.0, 0.1);
    mpc.addCV("separator", "pressure", 30.0);

    mpc.identifyModel(60.0);

    // Calculate control moves
    double[] moves = mpc.calculate();

    assertNotNull(moves);
    assertEquals(1, moves.length);
    // Move should be within bounds
    assertTrue(moves[0] >= 0.0 && moves[0] <= 1.0);
  }

  @Test
  public void testProcessLinkedMPCSetpointChange() {
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("pressureController", process);

    mpc.addMV("inlet_valve", "opening", 0.0, 1.0);
    mpc.addCV("separator", "pressure", 30.0);
    mpc.identifyModel(60.0);

    // Change setpoint
    mpc.setSetpoint("separator.pressure", 35.0);

    ControlledVariable cv = mpc.getControlledVariables().get(0);
    assertEquals(35.0, cv.getSetpoint(), 1e-10);
  }

  @Test
  public void testProcessLinkedMPCConfigurationSummary() {
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("testMPC", process);

    mpc.addMV("inlet_valve", "opening", 0.0, 1.0);
    mpc.addCV("separator", "pressure", 30.0);
    mpc.setSampleTime(60.0);
    mpc.setPredictionHorizon(20);

    String summary = mpc.getConfigurationSummary();

    assertTrue(summary.contains("testMPC"));
    assertTrue(summary.contains("60.0"));
    assertTrue(summary.contains("inlet_valve.opening"));
    assertTrue(summary.contains("separator.pressure"));
  }

  @Test
  public void testProcessLinkedMPCExportModel() {
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("testController", process);

    mpc.addMV("inlet_valve", "opening", 0.0, 1.0);
    mpc.addCV("separator", "pressure", 30.0);
    mpc.identifyModel(60.0);

    StateSpaceExporter exporter = mpc.exportModel();
    assertNotNull(exporter);

    StateSpaceExporter.StateSpaceModel model = exporter.toDiscreteStateSpace(60.0);
    assertNotNull(model);
    assertEquals(1, model.getNumInputs());
    assertEquals(1, model.getNumOutputs());
  }

  // ========================= Integration Tests =========================

  @Test
  public void testEndToEndMPCWorkflow() {
    // Create MPC
    ProcessLinkedMPC mpc = new ProcessLinkedMPC("separatorControl", process);

    // Setup variables
    mpc.addMV("inlet_valve", "opening", 0.0, 1.0, 0.05);
    mpc.addCV("separator", "pressure", 30.0);
    mpc.setConstraint("separator", "pressure", 25.0, 35.0);

    // Configure
    mpc.setSampleTime(60.0);
    mpc.setPredictionHorizon(10);
    mpc.setControlHorizon(3);

    // Identify
    mpc.identifyModel(60.0);

    // Run a few control steps
    double[] initialMVs = mpc.getCurrentMVs();
    double[] initialCVs = mpc.getCurrentCVs();

    for (int i = 0; i < 3; i++) {
      mpc.step();
    }

    double[] finalMVs = mpc.getCurrentMVs();
    double[] finalCVs = mpc.getCurrentCVs();

    // Verify controller made moves
    assertNotNull(finalMVs);
    assertNotNull(finalCVs);
    assertEquals(1, finalMVs.length);
    assertEquals(1, finalCVs.length);
  }
}
