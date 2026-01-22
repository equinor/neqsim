package neqsim.process.util.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Evaluates production increase per degree of cooling and required cooling duty.
 * 
 * <p>
 * This test sweeps through different cooling temperatures (0°C to 15°C cooling) and for each:
 * <ul>
 * <li>Builds the process with the specified cooling</li>
 * <li>Runs optimization to find maximum feasible production</li>
 * <li>Records cooling duty and production increase</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Results can be used to plot:
 * <ul>
 * <li>Production increase vs cooling temperature</li>
 * <li>Cooling duty vs cooling temperature</li>
 * <li>Production increase per MW of cooling</li>
 * </ul>
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CoolingDutyProductionAnalysisTest {

  /**
   * Evaluates production increase and cooling duty for various cooling temperatures.
   * 
   * <p>
   * Outputs data in CSV format for plotting.
   * </p>
   */
  @Test
  public void testProductionVsCoolingDuty() {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("COOLING DUTY vs PRODUCTION ANALYSIS");
    System.out.println("=".repeat(80));

    // Store results
    List<Double> coolingDeltaTs = new ArrayList<>();
    List<Double> coolingDuties = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<Double> productionIncreases = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    // First get baseline (no cooling)
    double baselineFlow = runOptimizationWithCooling(0.0, coolingDeltaTs, coolingDuties,
        optimalFlows, productionIncreases, bottlenecks);

    // Sweep cooling from 1°C to 15°C in 1°C increments
    for (double deltaT = 1.0; deltaT <= 15.0; deltaT += 1.0) {
      runOptimizationWithCooling(deltaT, coolingDeltaTs, coolingDuties, optimalFlows,
          productionIncreases, bottlenecks);
    }

    // Cooling water parameters: inlet 10°C, outlet 20°C, ΔT = 10°C
    double cpWater = 4.18; // kJ/(kg·K)
    double waterDeltaT = 10.0; // °C (20°C out - 10°C in)

    // Gas standard density for conversion to MSm3/day
    // Natural gas (mainly methane) at standard conditions (15°C, 1 atm): ~0.73 kg/Sm³
    double gasStdDensity = 0.73; // kg/Sm³

    // Print results as table
    System.out.println("\n" + "=".repeat(140));
    System.out.println("RESULTS SUMMARY");
    System.out.println("=".repeat(140));
    System.out.println(String.format("%-10s %-12s %-12s %-14s %-16s %-14s %-10s %s",
        "Cooling(C)", "Duty(MW)", "CW(m3/hr)", "Flow(MSm3/d)", "Flow(kg/hr)",
        "Increase(kg/hr)", "Incr(%)", "Bottleneck"));
    System.out.println("-".repeat(140));

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double increase = optimalFlows.get(i) - baselineFlow;
      double increasePercent = (increase / baselineFlow) * 100;
      double duty = coolingDuties.get(i);
      double cwFlowM3Hr = duty * 1000.0 / (cpWater * waterDeltaT) * 3600.0 / 1000.0;
      // Convert kg/hr to MSm3/day: (kg/hr) / (kg/Sm3) * 24 / 1e6
      double flowMSm3Day = optimalFlows.get(i) / gasStdDensity * 24.0 / 1e6;
      System.out.println(String.format("%-10.1f %-12.2f %-12.0f %-14.2f %-16.0f %-14.0f %-10.2f %s",
          coolingDeltaTs.get(i), duty, cwFlowM3Hr, flowMSm3Day, optimalFlows.get(i), increase,
          increasePercent, bottlenecks.get(i)));
    }

    // Print CSV format for plotting
    System.out.println("\n" + "=".repeat(80));
    System.out.println("CSV DATA (copy for plotting)");
    System.out.println("=".repeat(80));
    System.out.println(
        "Cooling_DeltaT_C,Cooling_Duty_MW,Optimal_Flow_kg_hr,Production_Increase_kg_hr,Production_Increase_Percent,Bottleneck");
    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double increase = optimalFlows.get(i) - baselineFlow;
      double increasePercent = (increase / baselineFlow) * 100;
      System.out.println(
          String.format("%.1f,%.2f,%.0f,%.0f,%.2f,%s", coolingDeltaTs.get(i), coolingDuties.get(i),
              optimalFlows.get(i), increase, increasePercent, bottlenecks.get(i)));
    }

    // Print efficiency metrics
    System.out.println("\n" + "=".repeat(80));
    System.out.println("EFFICIENCY METRICS");
    System.out.println("=".repeat(80));
    System.out.println(String.format("%-12s %-20s %-25s", "Cooling(°C)", "kg/hr per MW cooling",
        "kg/hr per °C cooling"));
    System.out.println("-".repeat(60));

    for (int i = 1; i < coolingDeltaTs.size(); i++) {
      double increase = optimalFlows.get(i) - baselineFlow;
      double duty = coolingDuties.get(i);
      double deltaT = coolingDeltaTs.get(i);
      double kgPerMW = duty > 0 ? increase / duty : 0;
      double kgPerDegree = deltaT > 0 ? increase / deltaT : 0;
      System.out.println(String.format("%-12.1f %-20.0f %-25.0f", deltaT, kgPerMW, kgPerDegree));
    }

    // Print cooling water requirements
    // Cooling water: inlet 10°C, outlet 20°C, ΔT = 10°C
    // Q = m_water × Cp × ΔT => m_water = Q / (Cp × ΔT)
    // Cp of water ≈ 4.18 kJ/(kg·K)
    // m_water (kg/s) = Q (kW) / (4.18 × 10) = Q (MW) × 1000 / 41.8

    System.out.println("\n" + "=".repeat(80));
    System.out.println("COOLING WATER REQUIREMENTS (Inlet: 10°C, Outlet: 20°C)");
    System.out.println("=".repeat(80));
    System.out.println(String.format("%-12s %-15s %-18s %-18s %-15s", "Cooling(°C)", "Duty(MW)",
        "CW Flow(kg/s)", "CW Flow(m³/hr)", "CW Flow(kg/hr)"));
    System.out.println("-".repeat(80));

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double duty = coolingDuties.get(i);
      // m_water (kg/s) = Q (MW) × 1000 / (Cp × ΔT)
      double cwFlowKgS = duty * 1000.0 / (cpWater * waterDeltaT);
      double cwFlowM3Hr = cwFlowKgS * 3600.0 / 1000.0; // Convert kg/s to m³/hr (assuming ~1000
                                                       // kg/m³)
      double cwFlowKgHr = cwFlowKgS * 3600.0;
      System.out.println(String.format("%-12.1f %-15.2f %-18.1f %-18.1f %-15.0f",
          coolingDeltaTs.get(i), duty, cwFlowKgS, cwFlowM3Hr, cwFlowKgHr));
    }

    // Print summary at max cooling
    if (coolingDuties.size() > 1) {
      double maxDuty = coolingDuties.get(coolingDuties.size() - 1);
      double maxCwFlowKgS = maxDuty * 1000.0 / (cpWater * waterDeltaT);
      double maxCwFlowM3Hr = maxCwFlowKgS * 3600.0 / 1000.0;
      double maxIncrease = optimalFlows.get(optimalFlows.size() - 1) - baselineFlow;
      double maxIncreasePercent = (maxIncrease / baselineFlow) * 100;

      System.out.println("\n" + "-".repeat(80));
      System.out.println("SUMMARY AT MAXIMUM COOLING (15°C):");
      System.out.println(String.format("  Cooling duty required: %.2f MW", maxDuty));
      System.out.println(String.format("  Cooling water flow:    %.0f kg/s = %.0f m³/hr",
          maxCwFlowKgS, maxCwFlowM3Hr));
      System.out.println(String.format("  Production increase:   %.0f kg/hr (+%.2f%%)", maxIncrease,
          maxIncreasePercent));
      System.out.println(String.format("  Production gain per m³/hr cooling water: %.1f kg/hr",
          maxIncrease / maxCwFlowM3Hr));
    }

    // Print ASCII plot
    printAsciiPlot(coolingDeltaTs, optimalFlows, baselineFlow);
  }

  /**
   * Runs optimization for a given cooling delta T and records results.
   *
   * @param coolingDeltaT temperature drop in the cooler (°C)
   * @param coolingDeltaTs list to store delta T values
   * @param coolingDuties list to store cooling duties
   * @param optimalFlows list to store optimal flow rates
   * @param productionIncreases list to store production increases
   * @param bottlenecks list to store bottleneck equipment names
   * @return optimal flow rate for this configuration
   */
  private double runOptimizationWithCooling(double coolingDeltaT, List<Double> coolingDeltaTs,
      List<Double> coolingDuties, List<Double> optimalFlows, List<Double> productionIncreases,
      List<String> bottlenecks) {

    // Build process with specified cooling
    ProcessSystem process = buildProcess(coolingDeltaT);
    Stream inletStream = (Stream) process.getUnit("Inlet Stream");
    Heater cooler = (Heater) process.getUnit("Gas Cooler");

    double originalFlow = inletStream.getFlowRate("kg/hr");

    // Get cooling duty (negative means cooling)
    double coolingDuty = 0.0;
    if (cooler != null) {
      coolingDuty = Math.abs(cooler.getDuty() / 1e6); // Convert to MW (duty is in W, negative for
                                                      // cooling)
    }

    // Run optimization
    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.002).maxIterations(15).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    // Record results
    coolingDeltaTs.add(coolingDeltaT);
    coolingDuties.add(coolingDuty);
    optimalFlows.add(result.getOptimalRate());
    productionIncreases.add(result.getOptimalRate() - originalFlow);
    bottlenecks.add(result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A");

    System.out
        .println(String.format("Cooling ΔT=%.1f°C: Duty=%.2f MW, Optimal=%.0f kg/hr, Bottleneck=%s",
            coolingDeltaT, coolingDuty, result.getOptimalRate(),
            result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A"));

    return result.getOptimalRate();
  }

  /**
   * Builds the process system with specified cooling.
   *
   * @param coolingDeltaT temperature drop in cooler (°C), 0 means no cooling
   * @return configured ProcessSystem
   */
  private ProcessSystem buildProcess(double coolingDeltaT) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", testSystem);
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

    // Cooler and separator (always add, but set deltaT=0 for no cooling)
    StreamInterface feedToSplitter2;

    if (coolingDeltaT > 0) {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      double inletTemp = finalSeparator.getGasOutStream().getTemperature("C");
      gasCooler.setOutTemperature(inletTemp - coolingDeltaT, "C");
      // Add 0.5 bar pressure drop across the cooler
      double inletPressure = finalSeparator.getGasOutStream().getPressure("bara");
      gasCooler.setOutPressure(inletPressure - 0.5, "bara");
      gasCooler.run();
      processSystem.add(gasCooler);

      Separator coolerSeparator = new Separator("Cooler Separator", gasCooler.getOutletStream());
      coolerSeparator.run();
      processSystem.add(coolerSeparator);

      feedToSplitter2 = coolerSeparator.getGasOutStream();
    } else {
      // No cooling - add a dummy heater with 0 duty for consistent equipment naming
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      gasCooler.setOutTemperature(finalSeparator.getGasOutStream().getTemperature("C"), "C");
      gasCooler.run();
      processSystem.add(gasCooler);

      feedToSplitter2 = gasCooler.getOutletStream();
    }

    // Second splitter - 3 compressor trains
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
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    // Run process
    processSystem.run();

    // Auto-size and configure
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

    // Configure compressors
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");
    Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");

    configureCompressor1And2WithElectricDriver(ups1Comp,
        "src/test/resources/compressor_curves/example_compressor_curve.json", 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp,
        "src/test/resources/compressor_curves/compressor_curve_ups2.json", 7383.0);
    configureCompressor3WithElectricDriver(ups3Comp,
        "src/test/resources/compressor_curves/compressor_curve_ups3.json", 6726.0);

    // Initialize pipe mechanical designs
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      if (equipment instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
        pipe.initMechanicalDesign();
        pipe.getMechanicalDesign().setMaxDesignVelocity(20.0);
      }
    }

    processSystem.run();
    return processSystem;
  }

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

  private void configureCompressor1And2WithElectricDriver(Compressor compressor, String chartPath,
      double ratedSpeed) {
    try {
      compressor.loadCompressorChartFromJson(chartPath);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load compressor chart: " + chartPath, e);
    }
    compressor.setSolveSpeed(true);

    CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 44400.0);
    driver.setRatedSpeed(ratedSpeed);

    double[] speeds = {4922.0, 5041.5, 5154.0, 5273.6, 5393.1, 5505.6, 5625.1, 5744.7, 5857.2,
        5976.7, 6096.2, 6152.5, 6208.8, 6328.3, 6447.8, 6560.3, 6679.9, 6799.4, 6911.9, 7031.4,
        7151.0, 7263.5, 7383.0};
    double[] powers = {21.8, 23.6, 25.3, 27.1, 28.8, 30.5, 32.3, 33.3, 34.3, 35.3, 36.3, 36.8, 37.3,
        38.4, 39.4, 40.4, 41.4, 42.4, 43.4, 44.4, 44.4, 44.4, 44.4};
    driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

    compressor.setDriver(driver);
  }

  private void configureCompressor3WithElectricDriver(Compressor compressor, String chartPath,
      double ratedSpeed) {
    try {
      compressor.loadCompressorChartFromJson(chartPath);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load compressor chart: " + chartPath, e);
    }
    compressor.setSolveSpeed(true);

    CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 50000.0);
    driver.setRatedSpeed(ratedSpeed);

    double[] speeds = {4484.0, 4590.761905, 4697.52381, 4804.285714, 4911.047619, 5017.809524,
        5124.571429, 5231.333333, 5338.095238, 5444.857143, 5551.619048, 5658.380952, 5765.142857,
        5871.904762, 5978.666667, 6085.428571, 6192.190476, 6298.952381, 6405.714286, 6512.47619,
        6619.238095, 6726.0};
    double[] powers = {26.8, 29.0, 31.2, 33.4, 35.6, 37.8, 40.0, 40.83333333, 41.66666667, 42.5,
        43.33333333, 44.16666667, 45.0, 45.83333333, 46.66666667, 47.5, 48.33333333, 49.16666667,
        50.0, 48.96666667, 47.93333333, 46.9};
    driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

    compressor.setDriver(driver);
  }

  /**
   * Prints an ASCII bar chart of production vs cooling.
   */
  private void printAsciiPlot(List<Double> coolingDeltaTs, List<Double> optimalFlows,
      double baselineFlow) {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("PRODUCTION vs COOLING (ASCII Plot)");
    System.out.println("=".repeat(80));

    double minFlow = baselineFlow * 0.995;
    double maxFlow = optimalFlows.stream().mapToDouble(d -> d).max().orElse(baselineFlow) * 1.005;
    double range = maxFlow - minFlow;
    int barWidth = 50;

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double deltaT = coolingDeltaTs.get(i);
      double flow = optimalFlows.get(i);
      int barLen = (int) ((flow - minFlow) / range * barWidth);
      barLen = Math.max(0, Math.min(barWidth, barLen));

      String bar = "█".repeat(barLen);
      double increasePercent = ((flow - baselineFlow) / baselineFlow) * 100;
      System.out.println(String.format("%5.1f°C |%-50s| %.0f kg/hr (+%.2f%%)", deltaT, bar, flow,
          increasePercent));
    }

    System.out.println("\n        " + "-".repeat(50));
    System.out.println(String.format("        %-25s %25s", String.format("%.0f", minFlow),
        String.format("%.0f kg/hr", maxFlow)));
  }

  /**
   * Evaluates production loss due to pressure drop in cooler with NO cooling (0°C temperature
   * change). Sweeps pressure drop from 0 to 1 bar in 0.1 bar increments.
   */
  @Test
  public void testPressureDropEffectNoCooling() {
    System.out.println("\n" + "=".repeat(100));
    System.out.println("PRESSURE DROP EFFECT ON PRODUCTION (NO COOLING - 0°C temperature change)");
    System.out.println("=".repeat(100));

    // Gas standard density for conversion to MSm3/day
    double gasStdDensity = 0.73; // kg/Sm³

    // Store results
    List<Double> pressureDrops = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    // Sweep pressure drop from 0 to 1 bar in 0.1 bar increments
    for (double dP = 0.0; dP <= 1.05; dP += 0.1) {
      ProcessSystem process = buildProcessWithPressureDrop(0.0, dP); // 0°C cooling, variable dP
      Stream inletStream = (Stream) process.getUnit("Inlet Stream");
      double originalFlow = inletStream.getFlowRate("kg/hr");

      // Run optimization
      ProductionOptimizer optimizer = new ProductionOptimizer();
      OptimizationConfig config =
          new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.002).maxIterations(15).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
          proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
          ObjectiveType.MAXIMIZE);

      OptimizationResult result = optimizer.optimize(process, inletStream, config,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      pressureDrops.add(dP);
      optimalFlows.add(result.getOptimalRate());
      bottlenecks.add(result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A");

      double flowMSm3Day = result.getOptimalRate() / gasStdDensity * 24.0 / 1e6;
      System.out.println(String.format("dP=%.1f bar: Optimal=%.0f kg/hr (%.2f MSm3/d), Bottleneck=%s",
          dP, result.getOptimalRate(), flowMSm3Day,
          result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A"));
    }

    // Get baseline (0 bar dP)
    double baselineFlow = optimalFlows.get(0);
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;

    // Print results table
    System.out.println("\n" + "=".repeat(100));
    System.out.println("RESULTS SUMMARY - PRESSURE DROP EFFECT (NO COOLING)");
    System.out.println("=".repeat(100));
    System.out.println(String.format("%-10s %-14s %-16s %-16s %-12s %s",
        "dP(bar)", "Flow(MSm3/d)", "Flow(kg/hr)", "Loss(kg/hr)", "Loss(%)", "Bottleneck"));
    System.out.println("-".repeat(100));

    for (int i = 0; i < pressureDrops.size(); i++) {
      double dP = pressureDrops.get(i);
      double flow = optimalFlows.get(i);
      double flowMSm3Day = flow / gasStdDensity * 24.0 / 1e6;
      double loss = baselineFlow - flow;
      double lossPercent = (loss / baselineFlow) * 100;
      System.out.println(String.format("%-10.1f %-14.2f %-16.0f %-16.0f %-12.2f %s",
          dP, flowMSm3Day, flow, loss, lossPercent, bottlenecks.get(i)));
    }

    // Print summary
    double maxDpFlow = optimalFlows.get(optimalFlows.size() - 1);
    double maxDpMSm3Day = maxDpFlow / gasStdDensity * 24.0 / 1e6;
    double totalLoss = baselineFlow - maxDpFlow;
    double totalLossPercent = (totalLoss / baselineFlow) * 100;
    double lossPerBar = totalLoss / 1.0; // per bar

    System.out.println("\n" + "-".repeat(100));
    System.out.println("SUMMARY:");
    System.out.println(String.format("  Baseline (0 bar dP):    %.0f kg/hr = %.2f MSm3/day",
        baselineFlow, baselineMSm3Day));
    System.out.println(String.format("  At 1.0 bar dP:          %.0f kg/hr = %.2f MSm3/day",
        maxDpFlow, maxDpMSm3Day));
    System.out.println(String.format("  Total production loss:  %.0f kg/hr (%.2f%%)",
        totalLoss, totalLossPercent));
    System.out.println(String.format("  Loss per 0.1 bar dP:    ~%.0f kg/hr",
        lossPerBar / 10.0));
    System.out.println(String.format("  Loss per bar dP:        ~%.0f kg/hr = ~%.2f MSm3/day",
        lossPerBar, lossPerBar / gasStdDensity * 24.0 / 1e6));

    // Print ASCII plot
    System.out.println("\n" + "=".repeat(80));
    System.out.println("PRODUCTION vs PRESSURE DROP (ASCII Plot)");
    System.out.println("=".repeat(80));

    double minFlow = optimalFlows.stream().mapToDouble(d -> d).min().orElse(baselineFlow) * 0.995;
    double maxFlow = baselineFlow * 1.005;
    double range = maxFlow - minFlow;
    int barWidth = 50;

    for (int i = 0; i < pressureDrops.size(); i++) {
      double dP = pressureDrops.get(i);
      double flow = optimalFlows.get(i);
      int barLen = (int) ((flow - minFlow) / range * barWidth);
      barLen = Math.max(0, Math.min(barWidth, barLen));

      String bar = "█".repeat(barLen);
      double lossPercent = ((baselineFlow - flow) / baselineFlow) * 100;
      System.out.println(String.format("%4.1f bar |%-50s| %.0f kg/hr (-%.2f%%)",
          dP, bar, flow, lossPercent));
    }

    System.out.println("\n         " + "-".repeat(50));
    System.out.println(String.format("         %-25s %25s", String.format("%.0f", minFlow),
        String.format("%.0f kg/hr", maxFlow)));
  }

  /**
   * Builds the process system with specified cooling and pressure drop.
   *
   * @param coolingDeltaT temperature drop in cooler (°C)
   * @param pressureDrop pressure drop in cooler (bar)
   * @return configured ProcessSystem
   */
  private ProcessSystem buildProcessWithPressureDrop(double coolingDeltaT, double pressureDrop) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    // Create inlet stream
    Stream inletStream = new Stream("Inlet Stream", testSystem);
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

    // Cooler with specified temperature drop and pressure drop
    StreamInterface feedToSplitter2;

    if (coolingDeltaT > 0 || pressureDrop > 0) {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      double inletTemp = finalSeparator.getGasOutStream().getTemperature("C");
      double inletPressure = finalSeparator.getGasOutStream().getPressure("bara");

      gasCooler.setOutTemperature(inletTemp - coolingDeltaT, "C");
      if (pressureDrop > 0) {
        gasCooler.setOutPressure(inletPressure - pressureDrop, "bara");
      }
      gasCooler.run();
      processSystem.add(gasCooler);

      if (coolingDeltaT > 0) {
        // Add separator after cooler if there's cooling (to remove condensate)
        Separator coolerSeparator = new Separator("Cooler Separator", gasCooler.getOutletStream());
        coolerSeparator.run();
        processSystem.add(coolerSeparator);
        feedToSplitter2 = coolerSeparator.getGasOutStream();
      } else {
        feedToSplitter2 = gasCooler.getOutletStream();
      }
    } else {
      feedToSplitter2 = finalSeparator.getGasOutStream();
    }

    // Second splitter - 3 compressor trains
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
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    // Run process
    processSystem.run();

    // Auto-size and configure
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

    // Configure compressors
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");
    Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");

    configureCompressor1And2WithElectricDriver(ups1Comp,
        "src/test/resources/compressor_curves/example_compressor_curve.json", 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp,
        "src/test/resources/compressor_curves/compressor_curve_ups2.json", 7383.0);
    configureCompressor3WithElectricDriver(ups3Comp,
        "src/test/resources/compressor_curves/compressor_curve_ups3.json", 6726.0);

    // Initialize pipe mechanical designs
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      if (equipment instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
        pipe.initMechanicalDesign();
        pipe.getMechanicalDesign().setMaxDesignVelocity(20.0);
      }
    }

    processSystem.run();
    return processSystem;
  }
}
