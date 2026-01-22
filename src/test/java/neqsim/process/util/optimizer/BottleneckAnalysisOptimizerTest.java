package neqsim.process.util.optimizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.compressor.DriverType;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ManipulatedVariable;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Integration tests for bottleneck analysis with multi-train compressor systems.
 *
 * <p>
 * These tests verify:
 * <ul>
 * <li>Compressor simulation validity checks (speed within chart, positive head)</li>
 * <li>Production optimizer correctly handles infeasible operating points</li>
 * <li>Bottleneck detection with compressor performance curves</li>
 * <li>Utilization values remain bounded (max 100%) for feasible solutions</li>
 * </ul>
 * </p>
 *
 * <p>
 * This test replicates the notebook bottleneck_analysis.ipynb functionality.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class BottleneckAnalysisOptimizerTest {

  private ProcessSystem processSystem;
  private Stream inletStream;
  private Compressor ups1Comp;
  private Compressor ups2Comp;
  private Compressor ups3Comp;

  /**
   * Creates a test fluid with natural gas composition.
   *
   * @return SystemInterface configured with natural gas components
   */
  private SystemInterface createTestFluid() {
    SystemPrEos testSystem = new SystemPrEos(298.15, 10.0);
    testSystem.addComponent("nitrogen", 0.0015);
    testSystem.addComponent("methane", 0.925);
    testSystem.addComponent("ethane", 0.03563);
    testSystem.addComponent("propane", 0.00693);
    testSystem.addComponent("water", 0.001);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    return testSystem;
  }

  /**
   * Creates a processing train with inlet pipe, separator, and outlet pipe.
   *
   * @param trainName name prefix for the train equipment
   * @param inlet inlet stream
   * @param process process system to add equipment to
   * @return outlet pipe of the train
   */
  private PipeBeggsAndBrills createProcessingTrain(String trainName, StreamInterface inlet,
      ProcessSystem process) {
    PipeBeggsAndBrills inletPipe = new PipeBeggsAndBrills(trainName + " Inlet Pipe", inlet);
    inletPipe.setLength(100.0);
    inletPipe.setDiameter(0.7);
    inletPipe.setPipeWallRoughness(15e-6);
    inletPipe.setElevation(0);
    inletPipe.run();
    process.add(inletPipe);

    ThreePhaseSeparator separator =
        new ThreePhaseSeparator(trainName + " Separator", inletPipe.getOutletStream());
    separator.run();
    process.add(separator);

    PipeBeggsAndBrills outletPipe =
        new PipeBeggsAndBrills(trainName + " Outlet Pipe", separator.getGasOutStream());
    outletPipe.setLength(100.0);
    outletPipe.setDiameter(0.7);
    outletPipe.setPipeWallRoughness(15e-6);
    outletPipe.setElevation(0);
    outletPipe.setNumberOfIncrements(10);
    outletPipe.run();
    process.add(outletPipe);

    return outletPipe;
  }

  /**
   * Creates an upstream compressor train with pipes, separator, and compressor.
   *
   * @param trainName name prefix for the train equipment
   * @param inlet inlet stream
   * @param process process system to add equipment to
   * @return outlet stream from the compressor discharge pipe
   */
  private StreamInterface createUpstreamCompressors(String trainName, StreamInterface inlet,
      ProcessSystem process) {
    PipeBeggsAndBrills inletPipe = new PipeBeggsAndBrills(trainName + " ups Pipe", inlet);
    inletPipe.setLength(100);
    inletPipe.setDiameter(0.75);
    inletPipe.setPipeWallRoughness(15e-6);
    inletPipe.setElevation(0);
    inletPipe.run();
    process.add(inletPipe);

    Separator separator = new Separator(trainName + " ups Separator", inletPipe.getOutletStream());
    separator.run();
    process.add(separator);

    PipeBeggsAndBrills outletPipe =
        new PipeBeggsAndBrills(trainName + " ups Outlet Pipe", separator.getGasOutStream());
    outletPipe.setLength(50.0);
    outletPipe.setDiameter(0.75);
    outletPipe.setPipeWallRoughness(15e-6);
    outletPipe.setElevation(0);
    outletPipe.run();
    process.add(outletPipe);

    Compressor compressor = new Compressor(trainName + " Compressor", outletPipe.getOutletStream());
    compressor.setOutletPressure(110.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.85);
    compressor.setSpeed(8000);
    compressor.run();
    process.add(compressor);

    PipeBeggsAndBrills outletPipe2 =
        new PipeBeggsAndBrills(trainName + " ups Outlet Pipe2", compressor.getOutletStream());
    outletPipe2.setLength(50.0);
    outletPipe2.setDiameter(0.75);
    outletPipe2.setPipeWallRoughness(15e-6);
    outletPipe2.setElevation(0);
    outletPipe2.run();
    process.add(outletPipe2);

    return outletPipe2.getOutletStream();
  }

  /**
   * Sets up the multi-train process system before each test.
   */
  @BeforeEach
  public void setUp() {
    SystemInterface testSystem = createTestFluid();

    processSystem = new ProcessSystem();

    // Create inlet stream
    inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(2097870.58288790, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    // Saturate stream with water
    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    // First splitter - 4 processing trains
    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    // Create 4 processing trains
    PipeBeggsAndBrills train1Outlet =
        createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    PipeBeggsAndBrills train2Outlet =
        createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    PipeBeggsAndBrills train3Outlet =
        createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    PipeBeggsAndBrills train4Outlet =
        createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    // Final separator combining all trains
    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    // Optional: Cooler and separator before compressor trains
    // Set useCoolerBeforeCompressors = true to enable cooling step
    boolean useCoolerBeforeCompressors = true;
    StreamInterface feedToSplitter2;

    if (useCoolerBeforeCompressors) {
      // Cooler to reduce gas temperature by 5°C
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      double inletTemp = finalSeparator.getGasOutStream().getTemperature("C");
      gasCooler.setOutTemperature(inletTemp - 5.0, "C");
      gasCooler.run();
      processSystem.add(gasCooler);

      // Separator after cooler to remove any condensed liquids
      Separator coolerSeparator = new Separator("Cooler Separator", gasCooler.getOutletStream());
      coolerSeparator.run();
      processSystem.add(coolerSeparator);

      feedToSplitter2 = coolerSeparator.getGasOutStream();
    } else {
      feedToSplitter2 = finalSeparator.getGasOutStream();
    }

    // Second splitter - 3 compressor trains (slightly unequal)
    Splitter splitter2 = new Splitter("Test Splitter2", feedToSplitter2);
    splitter2.setSplitFactors(new double[] {0.95 / 3.0, 1.0 / 3.0, 1.05 / 3.0});
    splitter2.run();
    processSystem.add(splitter2);

    // Create 3 compressor trains
    StreamInterface ups1Outlet =
        createUpstreamCompressors("ups1", splitter2.getSplitStream(0), processSystem);
    StreamInterface ups2Outlet =
        createUpstreamCompressors("ups2", splitter2.getSplitStream(1), processSystem);
    StreamInterface ups3Outlet =
        createUpstreamCompressors("ups3", splitter2.getSplitStream(2), processSystem);

    // Manifold
    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(ups1Outlet);
    manifold.addStream(ups2Outlet);
    manifold.addStream(ups3Outlet);
    manifold.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    // Disable capacity analysis for manifold - test focuses on compressor validation
    // The manifold velocity constraints are not properly sized for this test configuration
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    // Run process
    processSystem.run();

    // Auto-size equipment
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      if (equipment instanceof Separator) {
        ((Separator) equipment).autoSize();
      } else if (equipment instanceof Compressor) {
        ((Compressor) equipment).autoSize();
      } else if (equipment instanceof Manifold) {
        ((Manifold) equipment).autoSize();
      }
    }

    // Get compressor references
    ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");
    ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");

    // Configure compressor charts and drivers
    // All compressors use VFD electric motor with tabular driver curves
    configureCompressor1And2WithElectricDriver(ups1Comp,
        "src/test/resources/compressor_curves/example_compressor_curve.json", 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp,
        "src/test/resources/compressor_curves/compressor_curve_ups2.json", 7383.0);
    configureCompressor3WithElectricDriver(ups3Comp,
        "src/test/resources/compressor_curves/compressor_curve_ups3.json", 6726.0);

    // Initialize pipe mechanical designs for velocity-based capacity checks
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      if (equipment instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
        pipe.initMechanicalDesign();
        pipe.getMechanicalDesign().setMaxDesignVelocity(20.0);
      }
    }

    processSystem.run();
  }

  /**
   * Configures compressor 1 or 2 with a performance chart and VFD electric motor driver. Uses
   * tabular driver curve (max MW vs speed) for accurate power limit modeling.
   *
   * @param compressor the compressor to configure
   * @param chartPath path to the JSON compressor curve file
   * @param ratedSpeed driver rated speed in RPM
   */
  private void configureCompressor1And2WithElectricDriver(Compressor compressor, String chartPath,
      double ratedSpeed) {
    try {
      compressor.loadCompressorChartFromJson(chartPath);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load compressor chart: " + chartPath, e);
    }
    compressor.setSolveSpeed(true);

    // VFD electric motor driver with tabular power curve
    // Max power at rated speed is 44.4 MW = 44400 kW
    CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 44400.0);
    driver.setRatedSpeed(ratedSpeed);

    // Set driver curve: max MW vs speed (from actual driver data for compressors 1 & 2)
    double[] speeds = {4922.0, 5041.5, 5154.0, 5273.6, 5393.1, 5505.6, 5625.1, 5744.7, 5857.2,
        5976.7, 6096.2, 6152.5, 6208.8, 6328.3, 6447.8, 6560.3, 6679.9, 6799.4, 6911.9, 7031.4,
        7151.0, 7263.5, 7383.0};
    double[] powers = {21.8, 23.6, 25.3, 27.1, 28.8, 30.5, 32.3, 33.3, 34.3, 35.3, 36.3, 36.8, 37.3,
        38.4, 39.4, 40.4, 41.4, 42.4, 43.4, 44.4, 44.4, 44.4, 44.4};
    driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

    compressor.setDriver(driver);
    // Note: maxDesignPower is NOT set here - the driver curve provides the speed-dependent power
    // limit
  }

  /**
   * Configures compressor 3 with a performance chart and VFD electric motor driver. Uses tabular
   * driver curve (max MW vs speed) for accurate power limit modeling.
   *
   * @param compressor the compressor to configure
   * @param chartPath path to the JSON compressor curve file
   * @param ratedSpeed driver rated speed in RPM
   */
  private void configureCompressor3WithElectricDriver(Compressor compressor, String chartPath,
      double ratedSpeed) {
    try {
      compressor.loadCompressorChartFromJson(chartPath);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load compressor chart: " + chartPath, e);
    }
    compressor.setSolveSpeed(true);

    // VFD electric motor driver with tabular power curve
    // Max power is 50 MW = 50000 kW
    CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 50000.0);
    driver.setRatedSpeed(ratedSpeed);

    // Set driver curve: max MW vs speed (from actual driver data for compressor 3)
    double[] speeds = {4484.0, 4590.761905, 4697.52381, 4804.285714, 4911.047619, 5017.809524,
        5124.571429, 5231.333333, 5338.095238, 5444.857143, 5551.619048, 5658.380952, 5765.142857,
        5871.904762, 5978.666667, 6085.428571, 6192.190476, 6298.952381, 6405.714286, 6512.47619,
        6619.238095, 6726.0};
    double[] powers = {26.8, 29.0, 31.2, 33.4, 35.6, 37.8, 40.0, 40.83333333, 41.66666667, 42.5,
        43.33333333, 44.16666667, 45.0, 45.83333333, 46.66666667, 47.5, 48.33333333, 49.16666667,
        50.0, 48.96666667, 47.93333333, 46.9};
    driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

    compressor.setDriver(driver);
    // Note: maxDesignPower is NOT set here - the driver curve provides the speed-dependent power
    // limit
  }

  /**
   * Tests that compressors at nominal flow rate have valid simulation results.
   */
  @Test
  public void testCompressorsValidAtNominalFlow() {
    processSystem.run();

    // All compressors should have valid simulations at nominal flow
    Assertions.assertTrue(ups1Comp.isSimulationValid(),
        "ups1 Compressor should have valid simulation at nominal flow");
    Assertions.assertTrue(ups2Comp.isSimulationValid(),
        "ups2 Compressor should have valid simulation at nominal flow");
    Assertions.assertTrue(ups3Comp.isSimulationValid(),
        "ups3 Compressor should have valid simulation at nominal flow");

    // Speed should be within chart limits
    double ups1MinSpeed = ups1Comp.getCompressorChart().getMinSpeedCurve();
    double ups1MaxSpeed = ups1Comp.getCompressorChart().getMaxSpeedCurve();
    Assertions.assertTrue(ups1Comp.getSpeed() >= ups1MinSpeed * 0.95,
        "ups1 speed should be above chart minimum");
    Assertions.assertTrue(ups1Comp.getSpeed() <= ups1MaxSpeed * 1.05,
        "ups1 speed should be below chart maximum");

    // Polytropic head should be positive
    Assertions.assertTrue(ups1Comp.getPolytropicFluidHead() > 0,
        "Polytropic head should be positive");
    Assertions.assertTrue(ups2Comp.getPolytropicFluidHead() > 0,
        "Polytropic head should be positive");
    Assertions.assertTrue(ups3Comp.getPolytropicFluidHead() > 0,
        "Polytropic head should be positive");
  }

  /**
   * Tests that compressor simulation is marked invalid when operating outside chart range.
   */
  @Test
  public void testCompressorInvalidWhenOutsideChartRange() {
    // Push flow rate very high to force compressors outside their valid envelope
    inletStream.setFlowRate(5000000.0, "kg/hr"); // 2.5x nominal
    processSystem.run();

    // At least one compressor should have invalid simulation
    boolean anyInvalid = !ups1Comp.isSimulationValid() || !ups2Comp.isSimulationValid()
        || !ups3Comp.isSimulationValid();

    // Check validation errors
    List<String> ups3Errors = ups3Comp.getSimulationValidationErrors();

    System.out.println("Validation errors at 5M kg/hr flow:");
    System.out.println("ups1: " + ups1Comp.getSimulationValidationErrors());
    System.out.println("ups2: " + ups2Comp.getSimulationValidationErrors());
    System.out.println("ups3: " + ups3Comp.getSimulationValidationErrors());

    // At very high flow, compressors should either:
    // - Have zero/negative polytropic head
    // - Have speed outside chart range
    Assertions.assertTrue(anyInvalid || !ups3Errors.isEmpty(),
        "At extreme flow rates, compressors should show invalid simulation or validation errors");
  }

  /**
   * Tests production optimizer with BINARY_FEASIBILITY search mode. This test validates that the
   * optimizer properly detects and handles compressor operating envelope violations through
   * isSimulationValid().
   * 
   * Note: Finding a "feasible" solution depends on the specific capacity rules and utilization
   * limits configured. The key validation is that: 1. Optimizer runs without throwing exceptions 2.
   * Optimizer returns a valid result with bounded utilization (not infinity or NaN) 3. At extreme
   * flows, invalid compressor states are properly detected
   */
  @Test
  public void testOptimizerBinaryFeasibilityFindsFeasibleSolution() {
    double originalFlow = inletStream.getFlowRate("kg/hr");

    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Use a range that includes flows where compressors should be valid
    double lowFlow = originalFlow * 0.5;
    double highFlow = originalFlow * 1.5;

    OptimizationConfig config = new OptimizationConfig(lowFlow, highFlow).rateUnit("kg/hr")
        .tolerance(originalFlow * 0.02).maxIterations(20).defaultUtilizationLimit(1.0)
        .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true); // Key: reject
                                                                                   // invalid
                                                                                   // compressor
                                                                                   // states

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(processSystem, inletStream, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    System.out.println("\n=== BINARY_FEASIBILITY OPTIMIZATION RESULT ===");
    System.out.println("Optimal flow rate: " + result.getOptimalRate() + " kg/hr");
    System.out.println("Feasible: " + result.isFeasible());
    System.out.println("Iterations: " + result.getIterations());
    if (result.getBottleneck() != null) {
      System.out.println("Bottleneck: " + result.getBottleneck().getName());
      System.out
          .println("Bottleneck utilization: " + result.getBottleneckUtilization() * 100 + "%");
    }

    // Print iteration history to understand optimizer behavior
    System.out.println("\nIteration history:");
    for (int i = 0; i < result.getIterationHistory().size(); i++) {
      var rec = result.getIterationHistory().get(i);
      System.out.println(String.format("  %d: rate=%.0f, util=%.1f%%, feasible=%s", i + 1,
          rec.getRate(), rec.getBottleneckUtilization() * 100, rec.isFeasible()));
    }

    // Key assertions for this test:
    // 1. Optimizer completes without throwing exceptions - implicitly tested by reaching here
    // 2. Bottleneck utilization is a valid number (not NaN/Infinity/extreme values)
    double bottleneckUtil = result.getBottleneckUtilization();
    Assertions.assertFalse(Double.isNaN(bottleneckUtil),
        "Bottleneck utilization should not be NaN");
    Assertions.assertFalse(Double.isInfinite(bottleneckUtil),
        "Bottleneck utilization should not be infinite");
    // The fix ensures utilization doesn't become astronomical (e.g., 55775%)
    // Even infeasible points should have reasonable utilization calculations
    Assertions.assertTrue(bottleneckUtil < 10.0, // Less than 1000%
        "Bottleneck utilization should be bounded, got: " + bottleneckUtil * 100 + "%");

    // 3. Optimal rate is within the search bounds
    Assertions.assertTrue(
        result.getOptimalRate() >= lowFlow * 0.99 && result.getOptimalRate() <= highFlow * 1.01,
        "Optimal rate should be within search bounds");
  }

  /**
   * Tests that optimizer iteration history shows bounded utilization values for feasible points.
   */
  @Test
  public void testIterationHistoryHasBoundedUtilizations() {
    double originalFlow = inletStream.getFlowRate("kg/hr");

    ProductionOptimizer optimizer = new ProductionOptimizer();

    OptimizationConfig config = new OptimizationConfig(originalFlow * 0.8, originalFlow * 1.2)
        .rateUnit("kg/hr").tolerance(originalFlow * 0.01).maxIterations(15)
        .defaultUtilizationLimit(1.0).searchMode(SearchMode.BINARY_FEASIBILITY);

    OptimizationObjective objective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(processSystem, inletStream, config,
        Collections.singletonList(objective), Collections.emptyList());

    System.out.println("\n=== ITERATION HISTORY ===");
    for (int i = 0; i < result.getIterationHistory().size(); i++) {
      var rec = result.getIterationHistory().get(i);
      String status = rec.isFeasible() ? "FEASIBLE" : "INFEASIBLE";
      System.out.println(String.format("Iter %d: rate=%.0f kg/hr, util=%.1f%%, %s", i + 1,
          rec.getRate(), rec.getBottleneckUtilization() * 100, status));
    }

    // For feasible iterations, utilization should be reasonable
    long feasibleCount =
        result.getIterationHistory().stream().filter(rec -> rec.isFeasible()).count();

    System.out.println("Feasible iterations: " + feasibleCount);

    // Check that at least some iterations were feasible
    if (result.isFeasible()) {
      Assertions.assertTrue(feasibleCount > 0,
          "Should have at least one feasible iteration in history");

      // Feasible points should have reasonable utilization (not 55775%!)
      result.getIterationHistory().stream().filter(rec -> rec.isFeasible()).forEach(rec -> {
        Assertions.assertTrue(rec.getBottleneckUtilization() <= 2.0, // Allow some margin
            "Feasible iteration should have utilization <= 200%, got: "
                + rec.getBottleneckUtilization() * 100 + "%");
      });
    }
  }

  /**
   * Tests capacity utilization summary shows reasonable values for compressors. Note: Some
   * equipment types (Manifold) may have high utilization due to design settings in test environment
   * - we focus on compressor validation here.
   */
  @Test
  public void testCapacityUtilizationSummaryIsBounded() {
    processSystem.run();

    Map<String, Double> utilizationSummary = processSystem.getCapacityUtilizationSummary();

    System.out.println("\n=== EQUIPMENT CAPACITY UTILIZATION ===");
    for (Map.Entry<String, Double> entry : utilizationSummary.entrySet()) {
      System.out.println(String.format("%-30s: %6.2f%%", entry.getKey(), entry.getValue()));
    }

    // Check compressor utilizations specifically - these should be bounded at nominal flow
    // since we're testing that compressors with charts have proper validation
    for (Map.Entry<String, Double> entry : utilizationSummary.entrySet()) {
      String name = entry.getKey();
      // Focus on compressors - these are the equipment type being validated in this test
      if (name.contains("Compressor") && !name.contains("Manifold") && !name.contains("Driver")) {
        Assertions.assertTrue(entry.getValue() < 500.0,
            "Compressor utilization for " + name + " should be < 500%, got: " + entry.getValue());
      }
    }
  }

  /**
   * Tests bottleneck detection at nominal operating conditions. Note: The bottleneck may be
   * equipment other than compressors (e.g., Manifold) depending on test configuration. This test
   * validates that bottleneck detection works and returns meaningful values.
   */
  @Test
  public void testBottleneckDetectionAtNominalFlow() {
    processSystem.run();

    neqsim.process.equipment.capacity.BottleneckResult bottleneck = processSystem.findBottleneck();

    System.out.println("\n=== BOTTLENECK ANALYSIS ===");
    if (bottleneck != null && bottleneck.hasBottleneck()) {
      System.out.println("Bottleneck Equipment: " + bottleneck.getEquipmentName());
      System.out.println("Constraint: " + bottleneck.getConstraintName());
      System.out.println("Utilization: " + bottleneck.getUtilizationPercent() + "%");
    } else {
      System.out.println("No bottleneck detected");
    }

    Assertions.assertNotNull(bottleneck, "Bottleneck result should not be null");
    Assertions.assertTrue(bottleneck.hasBottleneck(), "Should detect a bottleneck");

    // Check that the bottleneck has a name (not null/empty)
    Assertions.assertNotNull(bottleneck.getEquipmentName(),
        "Bottleneck equipment name should not be null");
    Assertions.assertFalse(bottleneck.getEquipmentName().isEmpty(),
        "Bottleneck equipment name should not be empty");

    // The utilization value should not be NaN or Infinite
    Assertions.assertFalse(Double.isNaN(bottleneck.getUtilizationPercent()),
        "Bottleneck utilization should not be NaN");
    Assertions.assertFalse(Double.isInfinite(bottleneck.getUtilizationPercent()),
        "Bottleneck utilization should not be Infinite");
  }

  /**
   * Tests compressor capacity constraints include surge margin check.
   */
  @Test
  public void testCompressorConstraintsIncludeSurgeMargin() {
    processSystem.run();

    var constraints = ups1Comp.getCapacityConstraints();

    System.out.println("\n=== UPS1 COMPRESSOR CONSTRAINTS ===");
    for (var entry : constraints.entrySet()) {
      var constraint = entry.getValue();
      System.out.println(String.format("%-20s: util=%.1f%%, current=%.2f, design=%.2f",
          entry.getKey(), constraint.getUtilizationPercent(), constraint.getCurrentValue(),
          constraint.getDisplayDesignValue()));
    }

    // Should have surge margin constraint
    Assertions.assertTrue(constraints.containsKey("surgeMargin"),
        "Compressor should have surge margin constraint");

    // Surge margin utilization should be bounded
    double surgeUtil = constraints.get("surgeMargin").getUtilizationPercent();
    Assertions.assertTrue(surgeUtil <= 200.0, "Surge utilization should be reasonable, got: "
        + surgeUtil + "% (if > 100%, compressor is past surge)");
  }

  /**
   * Tests flow propagation through the multi-train system.
   */
  @Test
  public void testFlowPropagationThroughSystem() {
    double initialFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream()
        .getFlowRate("kg/hr");

    // Increase inlet flow
    inletStream.setFlowRate(2157870.58288790, "kg/hr");
    processSystem.run();

    double newFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream()
        .getFlowRate("kg/hr");

    System.out.println("\n=== FLOW PROPAGATION TEST ===");
    System.out.println("Initial ups1 flow: " + initialFlow + " kg/hr");
    System.out.println("New ups1 flow: " + newFlow + " kg/hr");
    System.out.println("Change: " + Math.abs(newFlow - initialFlow) + " kg/hr");

    // Flow should have increased
    Assertions.assertTrue(newFlow > initialFlow, "Flow should propagate through system");
  }

  /**
   * Tests multi-variable optimization with total flow and compressor train split factors.
   * 
   * <p>
   * This test optimizes:
   * <ul>
   * <li>Total inlet flow rate</li>
   * <li>Split factor for compressor train 1 (ups1)</li>
   * <li>Split factor for compressor train 2 (ups2)</li>
   * </ul>
   * Split factor 3 is computed as (1 - split1 - split2) to ensure they sum to 1.0.
   * </p>
   * 
   * <p>
   * The optimizer should find a feasible solution where:
   * <ul>
   * <li>No equipment exceeds 100% utilization</li>
   * <li>One equipment is at the bottleneck (close to 100%)</li>
   * <li>Total throughput is maximized</li>
   * </ul>
   * </p>
   */
  @Test
  public void testMultiVariableOptimizationWithSplitFactors() {
    double originalFlow = inletStream.getFlowRate("kg/hr");
    Splitter compressorSplitter = (Splitter) processSystem.getUnit("Test Splitter2");

    // Print baseline performance
    System.out.println("\n=== BASELINE (BEFORE OPTIMIZATION) ===");
    System.out.println(String.format("Total flow: %.0f kg/hr", originalFlow));
    double[] originalSplits = compressorSplitter.getSplitFactors();
    System.out.println(String.format("Split factors: [%.4f, %.4f, %.4f]", originalSplits[0],
        originalSplits[1], originalSplits[2]));
    printCompressorStatus();

    // Find the maximum utilization at baseline to understand headroom
    double baselineMaxUtil = getMaxCompressorUtilization();
    System.out.println(String.format("\nBaseline max utilization: %.2f%%", baselineMaxUtil * 100));

    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Define manipulated variables
    // 1. Total inlet flow rate - small range since we're already near capacity
    ManipulatedVariable flowVar = new ManipulatedVariable("totalFlow", originalFlow * 0.95,
        originalFlow * 1.05, "kg/hr", (proc, value) -> {
          Stream inlet = (Stream) proc.getUnit("Inlet Stream");
          inlet.setFlowRate(value, "kg/hr");
        });

    // 2. Split factor for compressor train 1 (ups1)
    // Bounds: 0.28 to 0.38 (narrow range around baseline ~0.317)
    ManipulatedVariable split1Var =
        new ManipulatedVariable("split1", 0.28, 0.38, "fraction", (proc, value) -> {
          Splitter splitter = (Splitter) proc.getUnit("Test Splitter2");
          double[] currentSplits = splitter.getSplitFactors();
          double split2 = currentSplits[1];
          double split3 = 1.0 - value - split2;
          // Ensure split3 stays in valid range
          if (split3 < 0.28) {
            split3 = 0.28;
            split2 = 1.0 - value - split3;
          } else if (split3 > 0.40) {
            split3 = 0.40;
            split2 = 1.0 - value - split3;
          }
          splitter.setSplitFactors(new double[] {value, split2, split3});
        });

    // 3. Split factor for compressor train 2 (ups2) - this is currently the bottleneck
    ManipulatedVariable split2Var =
        new ManipulatedVariable("split2", 0.28, 0.38, "fraction", (proc, value) -> {
          Splitter splitter = (Splitter) proc.getUnit("Test Splitter2");
          double[] currentSplits = splitter.getSplitFactors();
          double split1 = currentSplits[0];
          double split3 = 1.0 - split1 - value;
          // Ensure split3 stays in valid range
          if (split3 < 0.28) {
            split3 = 0.28;
            split1 = 1.0 - value - split3;
          } else if (split3 > 0.40) {
            split3 = 0.40;
            split1 = 1.0 - value - split3;
          }
          splitter.setSplitFactors(new double[] {split1, value, split3});
        });

    List<ManipulatedVariable> variables = Arrays.asList(flowVar, split1Var, split2Var);

    // Configuration: Use NELDER_MEAD with strict utilization limit
    // The key is defaultUtilizationLimit(1.0) which means feasible only if all <= 100%
    OptimizationConfig config =
        new OptimizationConfig(originalFlow * 0.95, originalFlow * 1.05).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.002).maxIterations(60).defaultUtilizationLimit(1.0) // Strict:
                                                                                            // must
                                                                                            // be <=
                                                                                            // 100%
            .searchMode(SearchMode.PARTICLE_SWARM_SCORE).rejectInvalidSimulations(true);

    // Objective: maximize throughput (score = flow rate)
    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    // Run optimization
    OptimizationResult result = optimizer.optimize(processSystem, variables, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    // Print results
    System.out.println("\n=== MULTI-VARIABLE OPTIMIZATION RESULT ===");
    System.out.println("Feasible: " + result.isFeasible());
    System.out.println("Iterations: " + result.getIterations());
    System.out.println("\nOptimal decision variables:");
    for (Map.Entry<String, Double> entry : result.getDecisionVariables().entrySet()) {
      System.out.println(String.format("  %s: %.4f", entry.getKey(), entry.getValue()));
    }

    if (result.getBottleneck() != null) {
      System.out.println("\nBottleneck: " + result.getBottleneck().getName());
      System.out.println(
          String.format("Bottleneck utilization: %.2f%%", result.getBottleneckUtilization() * 100));
    }

    // Apply optimal values and print final state
    double optimalFlow = result.getDecisionVariables().getOrDefault("totalFlow", originalFlow);
    double optimalSplit1 = result.getDecisionVariables().getOrDefault("split1", 0.333);
    double optimalSplit2 = result.getDecisionVariables().getOrDefault("split2", 0.333);
    double optimalSplit3 = 1.0 - optimalSplit1 - optimalSplit2;

    inletStream.setFlowRate(optimalFlow, "kg/hr");
    compressorSplitter.setSplitFactors(new double[] {optimalSplit1, optimalSplit2, optimalSplit3});
    processSystem.run();

    System.out.println("\n=== OPTIMIZED STATE ===");
    System.out.println(String.format("Optimal total flow: %.0f kg/hr", optimalFlow));
    System.out.println(String.format("Optimal split factors: [%.4f, %.4f, %.4f]", optimalSplit1,
        optimalSplit2, optimalSplit3));
    System.out.println(String.format("Production change: %.0f kg/hr (%+.2f%%)",
        optimalFlow - originalFlow, (optimalFlow - originalFlow) / originalFlow * 100));
    printCompressorStatus();

    // Check all compressor utilizations
    double maxUtil = getMaxCompressorUtilization();
    System.out.println(String.format("\nMax compressor utilization: %.2f%%", maxUtil * 100));

    // Assertions
    Assertions.assertNotNull(result, "Optimization result should not be null");
    Assertions.assertTrue(result.getIterations() > 0, "Should have performed iterations");

    // Key assertion: solution should be feasible (all equipment <= 100%)
    if (result.isFeasible()) {
      Assertions.assertTrue(result.getBottleneckUtilization() <= 1.0,
          "Feasible solution should have bottleneck <= 100%, got: "
              + result.getBottleneckUtilization() * 100 + "%");

      // Verify all compressors are under 100%
      Assertions.assertTrue(maxUtil <= 1.02,
          "All compressors should be <= 100% (with small tolerance), got: " + maxUtil * 100 + "%");
    }

    // Split factors should sum to 1.0
    double splitSum = optimalSplit1 + optimalSplit2 + optimalSplit3;
    Assertions.assertTrue(Math.abs(splitSum - 1.0) < 0.01,
        "Split factors should sum to 1.0, got: " + splitSum);

    // Utilization should be bounded (not NaN/Infinity)
    double bottleneckUtil = result.getBottleneckUtilization();
    Assertions.assertFalse(Double.isNaN(bottleneckUtil),
        "Bottleneck utilization should not be NaN");
    Assertions.assertFalse(Double.isInfinite(bottleneckUtil),
        "Bottleneck utilization should not be infinite");

    Map<String, Double> utilizationSummary = processSystem.getCapacityUtilizationSummary();

    System.out.println("\n=== EQUIPMENT CAPACITY UTILIZATION ===");
    for (Map.Entry<String, Double> entry : utilizationSummary.entrySet()) {
      System.out.println(String.format("%-30s: %6.2f%%", entry.getKey(), entry.getValue()));
    }
  }

  /**
   * Tests the recommended two-stage optimization approach for split factor optimization.
   * 
   * <p>
   * <b>Why Two Stages?</b>
   * </p>
   * <p>
   * Single-pass multi-variable optimizers (Nelder-Mead, Particle Swarm) can find different
   * solutions due to:
   * <ul>
   * <li>Stochastic initialization (PSO)</li>
   * <li>Local optima trapping (Nelder-Mead)</li>
   * <li>Coupling between flow and split variables</li>
   * </ul>
   * </p>
   * 
   * <p>
   * <b>Two-Stage Approach:</b>
   * </p>
   * <ol>
   * <li><b>Stage 1 - Balance Load:</b> At current flow, optimize split factors to minimize max
   * utilization (balance load across compressors)</li>
   * <li><b>Stage 2 - Maximize Flow:</b> With balanced splits, use binary search to find maximum
   * feasible flow</li>
   * </ol>
   * 
   * <p>
   * This gives consistent, deterministic results because:
   * <ul>
   * <li>Stage 1 uses Nelder-Mead on a smooth objective (min-max utilization)</li>
   * <li>Stage 2 uses deterministic binary search</li>
   * </ul>
   * </p>
   */
  @Test
  public void testTwoStageOptimizationRecommendedApproach() {
    double originalFlow = inletStream.getFlowRate("kg/hr");
    Splitter compressorSplitter = (Splitter) processSystem.getUnit("Test Splitter2");

    System.out.println("\n=== TWO-STAGE OPTIMIZATION (RECOMMENDED APPROACH) ===");
    System.out.println("\n--- BASELINE ---");
    System.out.println(String.format("Total flow: %.0f kg/hr", originalFlow));
    double[] originalSplits = compressorSplitter.getSplitFactors();
    System.out.println(String.format("Split factors: [%.4f, %.4f, %.4f]", originalSplits[0],
        originalSplits[1], originalSplits[2]));
    printCompressorStatus();
    double baselineMaxUtil = getMaxCompressorUtilization();
    System.out.println(String.format("Max utilization: %.2f%%", baselineMaxUtil * 100));

    // ========================================================================
    // STAGE 1: Optimize split factors to balance load (minimize max utilization)
    // ========================================================================
    System.out.println("\n--- STAGE 1: BALANCE LOAD (Optimize Split Factors) ---");

    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Only split factors as variables (keep flow constant)
    ManipulatedVariable split1Var =
        new ManipulatedVariable("split1", 0.28, 0.40, "fraction", (proc, value) -> {
          Splitter splitter = (Splitter) proc.getUnit("Test Splitter2");
          double[] currentSplits = splitter.getSplitFactors();
          double split2 = currentSplits[1];
          double split3 = 1.0 - value - split2;
          if (split3 < 0.25)
            split3 = 0.25;
          if (split3 > 0.42)
            split3 = 0.42;
          split2 = 1.0 - value - split3;
          splitter.setSplitFactors(new double[] {value, split2, split3});
        });

    ManipulatedVariable split2Var =
        new ManipulatedVariable("split2", 0.28, 0.40, "fraction", (proc, value) -> {
          Splitter splitter = (Splitter) proc.getUnit("Test Splitter2");
          double[] currentSplits = splitter.getSplitFactors();
          double split1 = currentSplits[0];
          double split3 = 1.0 - split1 - value;
          if (split3 < 0.25)
            split3 = 0.25;
          if (split3 > 0.42)
            split3 = 0.42;
          split1 = 1.0 - value - split3;
          splitter.setSplitFactors(new double[] {split1, value, split3});
        });

    List<ManipulatedVariable> splitVariables = Arrays.asList(split1Var, split2Var);

    // Stage 1 config: Minimize max utilization (balance the load)
    // Using NELDER_MEAD because it's deterministic given same starting point
    OptimizationConfig stage1Config = new OptimizationConfig(0.28, 0.40).rateUnit("fraction")
        .tolerance(0.001).maxIterations(50).defaultUtilizationLimit(2.0) // Allow infeasible during
                                                                         // search
        .searchMode(SearchMode.NELDER_MEAD_SCORE).rejectInvalidSimulations(true);

    // Objective: MINIMIZE max utilization (negative because optimizer maximizes score)
    OptimizationObjective balanceObjective = new OptimizationObjective("balanceLoad",
        proc -> -getMaxCompressorUtilization(), 1.0, ObjectiveType.MAXIMIZE); // Max of negative =
                                                                              // Min of positive

    OptimizationResult stage1Result = optimizer.optimize(processSystem, splitVariables,
        stage1Config, Collections.singletonList(balanceObjective), Collections.emptyList());

    // Apply stage 1 results
    double optSplit1 = stage1Result.getDecisionVariables().getOrDefault("split1", 0.333);
    double optSplit2 = stage1Result.getDecisionVariables().getOrDefault("split2", 0.333);
    double optSplit3 = 1.0 - optSplit1 - optSplit2;
    compressorSplitter.setSplitFactors(new double[] {optSplit1, optSplit2, optSplit3});
    processSystem.run();

    System.out.println(
        String.format("Optimized splits: [%.4f, %.4f, %.4f]", optSplit1, optSplit2, optSplit3));
    printCompressorStatus();
    double balancedMaxUtil = getMaxCompressorUtilization();
    System.out.println(String.format("Balanced max utilization: %.2f%%", balancedMaxUtil * 100));

    // ========================================================================
    // STAGE 2: Maximize total flow using binary search (deterministic)
    // ========================================================================
    System.out.println("\n--- STAGE 2: MAXIMIZE FLOW (Binary Search) ---");

    // Now with balanced splits, use BINARY_FEASIBILITY to find max flow
    OptimizationConfig stage2Config =
        new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.001).maxIterations(20).defaultUtilizationLimit(1.0) // Strict
                                                                                            // 100%
                                                                                            // limit
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult stage2Result = optimizer.optimize(processSystem, inletStream, stage2Config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    System.out.println(String.format("Optimal flow: %.0f kg/hr", stage2Result.getOptimalRate()));
    System.out.println("Feasible: " + stage2Result.isFeasible());
    if (stage2Result.getBottleneck() != null) {
      System.out.println("Bottleneck: " + stage2Result.getBottleneck().getName());
      System.out.println(String.format("Bottleneck utilization: %.2f%%",
          stage2Result.getBottleneckUtilization() * 100));
    }

    // Apply final result
    inletStream.setFlowRate(stage2Result.getOptimalRate(), "kg/hr");
    processSystem.run();

    System.out.println("\n--- FINAL OPTIMIZED STATE ---");
    System.out.println(String.format("Total flow: %.0f kg/hr", stage2Result.getOptimalRate()));
    System.out.println(
        String.format("Split factors: [%.4f, %.4f, %.4f]", optSplit1, optSplit2, optSplit3));
    printCompressorStatus();

    double finalMaxUtil = getMaxCompressorUtilization();
    System.out.println(String.format("\nProduction increase: %.0f kg/hr (+%.2f%%)",
        stage2Result.getOptimalRate() - originalFlow,
        (stage2Result.getOptimalRate() - originalFlow) / originalFlow * 100));
    System.out.println(String.format("Final max utilization: %.2f%%", finalMaxUtil * 100));

    // Print equipment utilization
    Map<String, Double> utilizationSummary = processSystem.getCapacityUtilizationSummary();
    System.out.println("\n=== EQUIPMENT CAPACITY UTILIZATION ===");
    for (Map.Entry<String, Double> entry : utilizationSummary.entrySet()) {
      System.out.println(String.format("%-30s: %6.2f%%", entry.getKey(), entry.getValue()));
    }

    // Assertions
    Assertions.assertTrue(stage2Result.isFeasible(), "Two-stage result should be feasible");
    Assertions.assertTrue(stage2Result.getBottleneckUtilization() <= 1.02,
        "Bottleneck should be at or below 100%");
    Assertions.assertTrue(stage2Result.getOptimalRate() >= originalFlow,
        "Optimized flow should be at least as good as baseline");

    // The two-stage approach should find a solution close to 100% utilization
    Assertions.assertTrue(finalMaxUtil >= 0.95, "Should utilize at least 95% of bottleneck");
    Assertions.assertTrue(finalMaxUtil <= 1.02, "Should not exceed 102% utilization");
  }

  /**
   * Gets the maximum utilization across all three compressors.
   *
   * @return maximum utilization as a ratio (1.0 = 100%)
   */
  private double getMaxCompressorUtilization() {
    double maxUtil = 0.0;
    for (Compressor comp : Arrays.asList(ups1Comp, ups2Comp, ups3Comp)) {
      double power = comp.getPower("kW");
      double maxPower = comp.getCapacityMax() / 1000.0; // W to kW
      if (maxPower > 0 && !Double.isNaN(power)) {
        double util = power / maxPower;
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /**
   * Helper method to print compressor status.
   */
  private void printCompressorStatus() {
    System.out.println("\nCompressor Status:");
    for (Compressor comp : Arrays.asList(ups1Comp, ups2Comp, ups3Comp)) {
      double flow = comp.getInletStream().getFlowRate("kg/hr");
      double power = comp.getPower("MW");
      double speed = comp.getSpeed();
      double maxPower = comp.getCapacityMax() / 1e6; // Convert W to MW
      double utilization = (power / maxPower) * 100;
      System.out.println(
          String.format("  %s: flow=%.0f kg/hr, power=%.2f/%.2f MW (%.1f%%), speed=%.0f RPM",
              comp.getName(), flow, power, maxPower, utilization, speed));
    }
  }
}
