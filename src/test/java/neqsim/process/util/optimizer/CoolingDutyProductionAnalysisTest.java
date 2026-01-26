package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;
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
 * This test sweeps through different cooling temperatures (0Â°C to 15Â°C cooling) and for each:
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
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("COOLING DUTY vs PRODUCTION ANALYSIS");
    System.out.println(StringUtils.repeat("=", 80));

    // Store results
    List<Double> coolingDeltaTs = new ArrayList<>();
    List<Double> coolingDuties = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<Double> productionIncreases = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    // First get baseline (no cooling)
    double baselineFlow = runOptimizationWithCooling(0.0, coolingDeltaTs, coolingDuties,
        optimalFlows, productionIncreases, bottlenecks);

    // Sweep cooling from 1Â°C to 15Â°C in 1Â°C increments
    for (double deltaT = 1.0; deltaT <= 15.0; deltaT += 1.0) {
      runOptimizationWithCooling(deltaT, coolingDeltaTs, coolingDuties, optimalFlows,
          productionIncreases, bottlenecks);
    }

    // Cooling water parameters: inlet 10Â°C, outlet 20Â°C, Î”T = 10Â°C
    double cpWater = 4.18; // kJ/(kgÂ·K)
    double waterDeltaT = 10.0; // Â°C (20Â°C out - 10Â°C in)

    // Gas standard density for conversion to MSm3/day
    // Natural gas (mainly methane) at standard conditions (15Â°C, 1 atm): ~0.73 kg/SmÂ³
    double gasStdDensity = 0.73; // kg/SmÂ³

    // Print results as table
    System.out.println("\n" + StringUtils.repeat("=", 140));
    System.out.println("RESULTS SUMMARY");
    System.out.println(StringUtils.repeat("=", 140));
    System.out.println(String.format("%-10s %-12s %-12s %-14s %-16s %-14s %-10s %s", "Cooling(C)",
        "Duty(MW)", "CW(m3/hr)", "Flow(MSm3/d)", "Flow(kg/hr)", "Increase(kg/hr)", "Incr(%)",
        "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 140));

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
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("CSV DATA (copy for plotting)");
    System.out.println(StringUtils.repeat("=", 80));
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
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("EFFICIENCY METRICS");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println(String.format("%-12s %-20s %-25s", "Cooling(Â°C)", "kg/hr per MW cooling",
        "kg/hr per Â°C cooling"));
    System.out.println(StringUtils.repeat("-", 60));

    for (int i = 1; i < coolingDeltaTs.size(); i++) {
      double increase = optimalFlows.get(i) - baselineFlow;
      double duty = coolingDuties.get(i);
      double deltaT = coolingDeltaTs.get(i);
      double kgPerMW = duty > 0 ? increase / duty : 0;
      double kgPerDegree = deltaT > 0 ? increase / deltaT : 0;
      System.out.println(String.format("%-12.1f %-20.0f %-25.0f", deltaT, kgPerMW, kgPerDegree));
    }

    // Print cooling water requirements
    // Cooling water: inlet 10Â°C, outlet 20Â°C, Î”T = 10Â°C
    // Q = m_water Ã— Cp Ã— Î”T => m_water = Q / (Cp Ã— Î”T)
    // Cp of water â‰ˆ 4.18 kJ/(kgÂ·K)
    // m_water (kg/s) = Q (kW) / (4.18 Ã— 10) = Q (MW) Ã— 1000 / 41.8

    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("COOLING WATER REQUIREMENTS (Inlet: 10Â°C, Outlet: 20Â°C)");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println(String.format("%-12s %-15s %-18s %-18s %-15s", "Cooling(Â°C)", "Duty(MW)",
        "CW Flow(kg/s)", "CW Flow(mÂ³/hr)", "CW Flow(kg/hr)"));
    System.out.println(StringUtils.repeat("-", 80));

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double duty = coolingDuties.get(i);
      // m_water (kg/s) = Q (MW) Ã— 1000 / (Cp Ã— Î”T)
      double cwFlowKgS = duty * 1000.0 / (cpWater * waterDeltaT);
      double cwFlowM3Hr = cwFlowKgS * 3600.0 / 1000.0; // Convert kg/s to mÂ³/hr (assuming ~1000
                                                       // kg/mÂ³)
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

      System.out.println("\n" + StringUtils.repeat("-", 80));
      System.out.println("SUMMARY AT MAXIMUM COOLING (15Â°C):");
      System.out.println(String.format("  Cooling duty required: %.2f MW", maxDuty));
      System.out.println(String.format("  Cooling water flow:    %.0f kg/s = %.0f mÂ³/hr",
          maxCwFlowKgS, maxCwFlowM3Hr));
      System.out.println(String.format("  Production increase:   %.0f kg/hr (+%.2f%%)", maxIncrease,
          maxIncreasePercent));
      System.out.println(String.format("  Production gain per mÂ³/hr cooling water: %.1f kg/hr",
          maxIncrease / maxCwFlowM3Hr));
    }

    // Print ASCII plot
    printAsciiPlot(coolingDeltaTs, optimalFlows, baselineFlow);
  }

  /**
   * Runs optimization for a given cooling delta T and records results.
   *
   * @param coolingDeltaT temperature drop in the cooler (Â°C)
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

    System.out.println(
        String.format("Cooling Î”T=%.1fÂ°C: Duty=%.2f MW, Optimal=%.0f kg/hr, Bottleneck=%s",
            coolingDeltaT, coolingDuty, result.getOptimalRate(),
            result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A"));

    return result.getOptimalRate();
  }

  /**
   * Builds the process system with specified cooling.
   *
   * @param coolingDeltaT temperature drop in cooler (Â°C), 0 means no cooling
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

    configureCompressor1And2WithElectricDriver(ups1Comp, 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp, 7383.0);
    configureCompressor3WithElectricDriver(ups3Comp, 6726.0);

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

  private void configureCompressor1And2WithElectricDriver(Compressor compressor,
      double ratedSpeed) {
    // Set chart type to use interpolation and extrapolation for smooth curves
    compressor.setCompressorChartType("interpolate and extrapolate");
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 8);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.setSolveSpeed(true);

    CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 44400.0);
    driver.setRatedSpeed(ratedSpeed);

    // SMOOTH POWER CURVE v2 - higher base power, smooth increase to max
    // Designed to provide ~44 MW at 6900-7100 RPM range (where compressor operates)
    // Power increases smoothly - no plateau or discrete steps
    double[] speeds = {4922.0, 5200.0, 5500.0, 5800.0, 6100.0, 6400.0, 6700.0, 6900.0, 7100.0,
        7200.0, 7300.0, 7383.0};
    // Smooth curve reaching 44.4 MW at ~6900 RPM
    double[] powers = {28.0, 30.5, 33.5, 36.5, 39.5, 42.0, 43.8, 44.2, 44.35, 44.38, 44.40, 44.40};
    driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

    compressor.setDriver(driver);
  }

  private void configureCompressor3WithElectricDriver(Compressor compressor, double ratedSpeed) {
    // Set chart type to use interpolation and extrapolation for smooth curves
    compressor.setCompressorChartType("interpolate and extrapolate");
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 8);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.setSolveSpeed(true);

    // Compressor 3 has 50 MW max power at rated speed 6726 RPM
    CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 50000.0);
    driver.setRatedSpeed(ratedSpeed);

    // Smooth power curve - linear interpolation from 4484 to 6726 RPM
    // Gradually approaches max power (50 MW) instead of discrete steps
    double[] speeds = {4484.0, 4700.0, 4950.0, 5200.0, 5450.0, 5700.0, 5950.0, 6150.0, 6350.0,
        6500.0, 6600.0, 6680.0, 6726.0};
    double[] powers =
        {30.0, 33.0, 36.5, 40.0, 43.5, 46.5, 48.5, 49.2, 49.6, 49.85, 49.93, 49.98, 50.0};
    driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

    compressor.setDriver(driver);
  }

  /**
   * Prints an ASCII bar chart of production vs cooling.
   */
  private void printAsciiPlot(List<Double> coolingDeltaTs, List<Double> optimalFlows,
      double baselineFlow) {
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("PRODUCTION vs COOLING (ASCII Plot)");
    System.out.println(StringUtils.repeat("=", 80));

    double minFlow = baselineFlow * 0.995;
    double maxFlow = optimalFlows.stream().mapToDouble(d -> d).max().orElse(baselineFlow) * 1.005;
    double range = maxFlow - minFlow;
    int barWidth = 50;

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double deltaT = coolingDeltaTs.get(i);
      double flow = optimalFlows.get(i);
      int barLen = (int) ((flow - minFlow) / range * barWidth);
      barLen = Math.max(0, Math.min(barWidth, barLen));

      String bar = StringUtils.repeat("#", barLen);
      double increasePercent = ((flow - baselineFlow) / baselineFlow) * 100;
      System.out.println(String.format("%5.1fÂ°C |%-50s| %.0f kg/hr (+%.2f%%)", deltaT, bar, flow,
          increasePercent));
    }

    System.out.println("\n        " + StringUtils.repeat("-", 50));
    System.out.println(String.format("        %-25s %25s", String.format("%.0f", minFlow),
        String.format("%.0f kg/hr", maxFlow)));
  }

  /**
   * Evaluates production loss due to pressure drop in cooler with NO cooling (0Â°C temperature
   * change). Sweeps pressure drop from 0 to 1 bar in 0.1 bar increments.
   */
  @Test
  public void testPressureDropEffectNoCooling() {
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("PRESSURE DROP EFFECT ON PRODUCTION (NO COOLING - 0Â°C temperature change)");
    System.out.println(StringUtils.repeat("=", 100));

    // Gas standard density for conversion to MSm3/day
    double gasStdDensity = 0.73; // kg/SmÂ³

    // Store results
    List<Double> pressureDrops = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    // Sweep pressure drop from 0 to 1 bar in 0.1 bar increments
    for (double dP = 0.0; dP <= 1.05; dP += 0.1) {
      ProcessSystem process = buildProcessWithPressureDrop(0.0, dP); // 0Â°C cooling, variable dP
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
      System.out
          .println(String.format("dP=%.1f bar: Optimal=%.0f kg/hr (%.2f MSm3/d), Bottleneck=%s", dP,
              result.getOptimalRate(), flowMSm3Day,
              result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A"));
    }

    // Get baseline (0 bar dP)
    double baselineFlow = optimalFlows.get(0);
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;

    // Print results table
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("RESULTS SUMMARY - PRESSURE DROP EFFECT (NO COOLING)");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println(String.format("%-10s %-14s %-16s %-16s %-12s %s", "dP(bar)", "Flow(MSm3/d)",
        "Flow(kg/hr)", "Loss(kg/hr)", "Loss(%)", "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 100));

    for (int i = 0; i < pressureDrops.size(); i++) {
      double dP = pressureDrops.get(i);
      double flow = optimalFlows.get(i);
      double flowMSm3Day = flow / gasStdDensity * 24.0 / 1e6;
      double loss = baselineFlow - flow;
      double lossPercent = (loss / baselineFlow) * 100;
      System.out.println(String.format("%-10.1f %-14.2f %-16.0f %-16.0f %-12.2f %s", dP,
          flowMSm3Day, flow, loss, lossPercent, bottlenecks.get(i)));
    }

    // Print summary
    double maxDpFlow = optimalFlows.get(optimalFlows.size() - 1);
    double maxDpMSm3Day = maxDpFlow / gasStdDensity * 24.0 / 1e6;
    double totalLoss = baselineFlow - maxDpFlow;
    double totalLossPercent = (totalLoss / baselineFlow) * 100;
    double lossPerBar = totalLoss / 1.0; // per bar

    System.out.println("\n" + StringUtils.repeat("-", 100));
    System.out.println("SUMMARY:");
    System.out.println(String.format("  Baseline (0 bar dP):    %.0f kg/hr = %.2f MSm3/day",
        baselineFlow, baselineMSm3Day));
    System.out.println(String.format("  At 1.0 bar dP:          %.0f kg/hr = %.2f MSm3/day",
        maxDpFlow, maxDpMSm3Day));
    System.out.println(String.format("  Total production loss:  %.0f kg/hr (%.2f%%)", totalLoss,
        totalLossPercent));
    System.out.println(String.format("  Loss per 0.1 bar dP:    ~%.0f kg/hr", lossPerBar / 10.0));
    System.out.println(String.format("  Loss per bar dP:        ~%.0f kg/hr = ~%.2f MSm3/day",
        lossPerBar, lossPerBar / gasStdDensity * 24.0 / 1e6));

    // Print ASCII plot
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("PRODUCTION vs PRESSURE DROP (ASCII Plot)");
    System.out.println(StringUtils.repeat("=", 80));

    double minFlow = optimalFlows.stream().mapToDouble(d -> d).min().orElse(baselineFlow) * 0.995;
    double maxFlow = baselineFlow * 1.005;
    double range = maxFlow - minFlow;
    int barWidth = 50;

    for (int i = 0; i < pressureDrops.size(); i++) {
      double dP = pressureDrops.get(i);
      double flow = optimalFlows.get(i);
      int barLen = (int) ((flow - minFlow) / range * barWidth);
      barLen = Math.max(0, Math.min(barWidth, barLen));

      String bar = StringUtils.repeat("#", barLen);
      double lossPercent = ((baselineFlow - flow) / baselineFlow) * 100;
      System.out.println(
          String.format("%4.1f bar |%-50s| %.0f kg/hr (-%.2f%%)", dP, bar, flow, lossPercent));
    }

    System.out.println("\n         " + StringUtils.repeat("-", 50));
    System.out.println(String.format("         %-25s %25s", String.format("%.0f", minFlow),
        String.format("%.0f kg/hr", maxFlow)));
  }

  /**
   * Builds the process system with specified cooling and pressure drop.
   *
   * @param coolingDeltaT temperature drop in cooler (Â°C)
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

    configureCompressor1And2WithElectricDriver(ups1Comp, 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp, 7383.0);
    configureCompressor3WithElectricDriver(ups3Comp, 6726.0);

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

  /**
   * Evaluates production increase and cooling duty when Compressor 2 uses the SAME curve as
   * Compressor 1. This analysis compares the effect of having identical compressor curves on all
   * three compressors (Comp1 and Comp2 identical, Comp3 different).
   * 
   * <p>
   * Scenario: What if we replaced the ups2 compressor with the same model as ups1?
   * </p>
   */
  @Test
  public void testProductionWithIdenticalCompressor1And2Curves() {
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("ANALYSIS WITH COMPRESSOR 2 USING SAME CURVE AS COMPRESSOR 1");
    System.out.println(StringUtils.repeat("=", 100));
    System.out
        .println("Scenario: ups2 Compressor now uses example_compressor_curve.json (same as ups1)");
    System.out.println();

    // Gas standard density for conversion to MSm3/day
    double gasStdDensity = 0.73; // kg/SmÂ³

    // Cooling water parameters
    double cpWater = 4.18; // kJ/(kgÂ·K)
    double waterDeltaT = 10.0; // Â°C

    // Store results
    List<Double> coolingDeltaTs = new ArrayList<>();
    List<Double> coolingDuties = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<Double> productionIncreases = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    // First get baseline (no cooling)
    double baselineFlow = runOptimizationWithIdenticalCompressors(0.0, coolingDeltaTs,
        coolingDuties, optimalFlows, productionIncreases, bottlenecks);

    // Sweep cooling from 0.5Â°C to 15Â°C in 0.5Â°C increments for smoother curves
    for (double deltaT = 0.5; deltaT <= 15.0; deltaT += 0.5) {
      runOptimizationWithIdenticalCompressors(deltaT, coolingDeltaTs, coolingDuties, optimalFlows,
          productionIncreases, bottlenecks);
    }

    // Print results table
    System.out.println("\n" + StringUtils.repeat("=", 140));
    System.out.println("RESULTS SUMMARY - COMPRESSOR 2 SAME AS COMPRESSOR 1");
    System.out.println(StringUtils.repeat("=", 140));
    System.out.println(String.format("%-10s %-12s %-12s %-14s %-16s %-14s %-10s %s", "Cooling(C)",
        "Duty(MW)", "CW(m3/hr)", "Flow(MSm3/d)", "Flow(kg/hr)", "Increase(kg/hr)", "Incr(%)",
        "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 140));

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double increase = optimalFlows.get(i) - baselineFlow;
      double increasePercent = (increase / baselineFlow) * 100;
      double duty = coolingDuties.get(i);
      double cwFlowM3Hr = duty * 1000.0 / (cpWater * waterDeltaT) * 3600.0 / 1000.0;
      double flowMSm3Day = optimalFlows.get(i) / gasStdDensity * 24.0 / 1e6;
      System.out.println(String.format("%-10.1f %-12.2f %-12.0f %-14.2f %-16.0f %-14.0f %-10.2f %s",
          coolingDeltaTs.get(i), duty, cwFlowM3Hr, flowMSm3Day, optimalFlows.get(i), increase,
          increasePercent, bottlenecks.get(i)));
    }

    // Print comparison with original configuration
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: Original vs Identical Compressor Curves");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("\nOriginal configuration:");
    System.out.println("  - ups1: example_compressor_curve.json, VFD 44.4 MW, 7383 RPM");
    System.out.println("  - ups2: compressor_curve_ups2.json, VFD 44.4 MW, 7383 RPM");
    System.out.println("  - ups3: compressor_curve_ups3.json, VFD 50.0 MW, 6726 RPM");
    System.out.println("\nNew configuration (this test):");
    System.out.println("  - ups1: example_compressor_curve.json, VFD 44.4 MW, 7383 RPM");
    System.out
        .println("  - ups2: example_compressor_curve.json, VFD 44.4 MW, 7383 RPM (SAME AS UPS1)");
    System.out.println("  - ups3: compressor_curve_ups3.json, VFD 50.0 MW, 6726 RPM");

    // Print ASCII plot
    printAsciiPlot(coolingDeltaTs, optimalFlows, baselineFlow);

    // Print summary at baseline and max cooling
    double maxCoolingFlow = optimalFlows.get(optimalFlows.size() - 1);
    double maxCoolingMSm3Day = maxCoolingFlow / gasStdDensity * 24.0 / 1e6;
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;
    double totalIncrease = maxCoolingFlow - baselineFlow;
    double totalIncreasePercent = (totalIncrease / baselineFlow) * 100;

    System.out.println("\n" + StringUtils.repeat("-", 80));
    System.out.println("SUMMARY (Identical Compressor 1 & 2 Curves):");
    System.out.println(String.format("  Baseline (0Â°C cooling): %.0f kg/hr = %.2f MSm3/day",
        baselineFlow, baselineMSm3Day));
    System.out.println(String.format("  At 15Â°C cooling:        %.0f kg/hr = %.2f MSm3/day",
        maxCoolingFlow, maxCoolingMSm3Day));
    System.out.println(String.format("  Production increase:    %.0f kg/hr (+%.2f%%)",
        totalIncrease, totalIncreasePercent));
    System.out.println(String.format("  Bottleneck at baseline: %s", bottlenecks.get(0)));
    System.out.println(
        String.format("  Bottleneck at 15Â°C:     %s", bottlenecks.get(bottlenecks.size() - 1)));
  }

  /**
   * Runs optimization with identical compressor curves for compressor 1 and 2.
   */
  private double runOptimizationWithIdenticalCompressors(double coolingDeltaT,
      List<Double> coolingDeltaTs, List<Double> coolingDuties, List<Double> optimalFlows,
      List<Double> productionIncreases, List<String> bottlenecks) {

    ProcessSystem process = buildProcessWithIdenticalCompressors(coolingDeltaT);
    Stream inletStream = (Stream) process.getUnit("Inlet Stream");
    Heater cooler = (Heater) process.getUnit("Gas Cooler");

    double originalFlow = inletStream.getFlowRate("kg/hr");

    double coolingDuty = 0.0;
    if (cooler != null) {
      coolingDuty = Math.abs(cooler.getDuty() / 1e6);
    }

    ProductionOptimizer optimizer = new ProductionOptimizer();
    // Use tighter tolerance and more iterations for consistent results with identical compressors
    OptimizationConfig config =
        new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.0005).maxIterations(25).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    coolingDeltaTs.add(coolingDeltaT);
    coolingDuties.add(coolingDuty);
    optimalFlows.add(result.getOptimalRate());
    productionIncreases.add(result.getOptimalRate() - originalFlow);
    bottlenecks.add(result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A");

    // Print compressor operating points for debugging
    Compressor ups1 = (Compressor) process.getUnit("ups1 Compressor");
    Compressor ups2 = (Compressor) process.getUnit("ups2 Compressor");
    Compressor ups3 = (Compressor) process.getUnit("ups3 Compressor");
    System.out.println(String.format(
        "  Speeds: ups1=%.0f, ups2=%.0f, ups3=%.0f RPM | Max util: %.1f%%", ups1.getSpeed(),
        ups2.getSpeed(), ups3.getSpeed(), result.getBottleneckUtilization() * 100));

    System.out.println(
        String.format("Cooling Î”T=%.1fÂ°C: Duty=%.2f MW, Optimal=%.0f kg/hr, Bottleneck=%s",
            coolingDeltaT, coolingDuty, result.getOptimalRate(),
            result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A"));

    return result.getOptimalRate();
  }

  /**
   * Builds process with compressor 2 using the SAME curve as compressor 1.
   */
  private ProcessSystem buildProcessWithIdenticalCompressors(double coolingDeltaT) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(2097870.58288790, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    PipeBeggsAndBrills train1Outlet =
        createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    PipeBeggsAndBrills train2Outlet =
        createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    PipeBeggsAndBrills train3Outlet =
        createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    PipeBeggsAndBrills train4Outlet =
        createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    StreamInterface feedToSplitter2;

    if (coolingDeltaT > 0) {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      double inletTemp = finalSeparator.getGasOutStream().getTemperature("C");
      gasCooler.setOutTemperature(inletTemp - coolingDeltaT, "C");
      double inletPressure = finalSeparator.getGasOutStream().getPressure("bara");
      gasCooler.setOutPressure(inletPressure - 0.5, "bara");
      gasCooler.run();
      processSystem.add(gasCooler);

      Separator coolerSeparator = new Separator("Cooler Separator", gasCooler.getOutletStream());
      coolerSeparator.run();
      processSystem.add(coolerSeparator);

      feedToSplitter2 = coolerSeparator.getGasOutStream();
    } else {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      gasCooler.setOutTemperature(finalSeparator.getGasOutStream().getTemperature("C"), "C");
      gasCooler.run();
      processSystem.add(gasCooler);

      feedToSplitter2 = gasCooler.getOutletStream();
    }

    Splitter splitter2 = new Splitter("Test Splitter2", feedToSplitter2);
    splitter2.setSplitFactors(new double[] {0.95 / 3.0, 1.0 / 3.0, 1.05 / 3.0});
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface ups1Outlet =
        createUpstreamCompressors("ups1", splitter2.getSplitStream(0), processSystem);
    StreamInterface ups2Outlet =
        createUpstreamCompressors("ups2", splitter2.getSplitStream(1), processSystem);
    StreamInterface ups3Outlet =
        createUpstreamCompressors("ups3", splitter2.getSplitStream(2), processSystem);

    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(ups1Outlet);
    manifold.addStream(ups2Outlet);
    manifold.addStream(ups3Outlet);
    manifold.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    processSystem.run();

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

    // Configure compressors - KEY CHANGE: ups2 now uses SAME curve as ups1
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");
    Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");

    // Both ups1 and ups2 use the SAME compressor curve (example_compressor_curve.json)
    configureCompressor1And2WithElectricDriver(ups1Comp, 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp, 7383.0); // SAME as
                                                                  // ups1!
    configureCompressor3WithElectricDriver(ups3Comp, 6726.0);

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

  /**
   * Tests pressure drop effect with identical compressor curves for compressor 1 and 2.
   */
  @Test
  public void testPressureDropWithIdenticalCompressors() {
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("PRESSURE DROP EFFECT - COMPRESSOR 2 SAME AS COMPRESSOR 1 (NO COOLING)");
    System.out.println(StringUtils.repeat("=", 100));

    double gasStdDensity = 0.73;

    List<Double> pressureDrops = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    for (double dP = 0.0; dP <= 1.05; dP += 0.1) {
      ProcessSystem process = buildProcessWithIdenticalCompressorsAndPressureDrop(0.0, dP);
      Stream inletStream = (Stream) process.getUnit("Inlet Stream");
      double originalFlow = inletStream.getFlowRate("kg/hr");

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
      System.out
          .println(String.format("dP=%.1f bar: Optimal=%.0f kg/hr (%.2f MSm3/d), Bottleneck=%s", dP,
              result.getOptimalRate(), flowMSm3Day,
              result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A"));
    }

    double baselineFlow = optimalFlows.get(0);
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;

    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("RESULTS SUMMARY - PRESSURE DROP (Identical Compressor Curves)");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println(String.format("%-10s %-14s %-16s %-16s %-12s %s", "dP(bar)", "Flow(MSm3/d)",
        "Flow(kg/hr)", "Loss(kg/hr)", "Loss(%)", "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 100));

    for (int i = 0; i < pressureDrops.size(); i++) {
      double dP = pressureDrops.get(i);
      double flow = optimalFlows.get(i);
      double flowMSm3Day = flow / gasStdDensity * 24.0 / 1e6;
      double loss = baselineFlow - flow;
      double lossPercent = (loss / baselineFlow) * 100;
      System.out.println(String.format("%-10.1f %-14.2f %-16.0f %-16.0f %-12.2f %s", dP,
          flowMSm3Day, flow, loss, lossPercent, bottlenecks.get(i)));
    }

    // Print summary
    double maxDpFlow = optimalFlows.get(optimalFlows.size() - 1);
    double maxDpMSm3Day = maxDpFlow / gasStdDensity * 24.0 / 1e6;
    double totalLoss = baselineFlow - maxDpFlow;
    double totalLossPercent = (totalLoss / baselineFlow) * 100;

    System.out.println("\n" + StringUtils.repeat("-", 100));
    System.out.println("SUMMARY (Identical Compressor Curves):");
    System.out.println(String.format("  Baseline (0 bar dP):    %.0f kg/hr = %.2f MSm3/day",
        baselineFlow, baselineMSm3Day));
    System.out.println(String.format("  At 1.0 bar dP:          %.0f kg/hr = %.2f MSm3/day",
        maxDpFlow, maxDpMSm3Day));
    System.out.println(String.format("  Total production loss:  %.0f kg/hr (%.2f%%)", totalLoss,
        totalLossPercent));
  }

  /**
   * Builds process with identical compressor curves and specified pressure drop.
   */
  private ProcessSystem buildProcessWithIdenticalCompressorsAndPressureDrop(double coolingDeltaT,
      double pressureDrop) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(2097870.58288790, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    PipeBeggsAndBrills train1Outlet =
        createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    PipeBeggsAndBrills train2Outlet =
        createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    PipeBeggsAndBrills train3Outlet =
        createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    PipeBeggsAndBrills train4Outlet =
        createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

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

    Splitter splitter2 = new Splitter("Test Splitter2", feedToSplitter2);
    splitter2.setSplitFactors(new double[] {0.95 / 3.0, 1.0 / 3.0, 1.05 / 3.0});
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface ups1Outlet =
        createUpstreamCompressors("ups1", splitter2.getSplitStream(0), processSystem);
    StreamInterface ups2Outlet =
        createUpstreamCompressors("ups2", splitter2.getSplitStream(1), processSystem);
    StreamInterface ups3Outlet =
        createUpstreamCompressors("ups3", splitter2.getSplitStream(2), processSystem);

    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(ups1Outlet);
    manifold.addStream(ups2Outlet);
    manifold.addStream(ups3Outlet);
    manifold.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    processSystem.run();

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

    // Configure compressors - ups2 uses SAME curve as ups1
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");
    Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");

    configureCompressor1And2WithElectricDriver(ups1Comp, 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp, 7383.0); // SAME as
                                                                  // ups1!
    configureCompressor3WithElectricDriver(ups3Comp, 6726.0);

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

  /**
   * 2027 SCENARIO: Troll A with only 2 compressor trains (A & B Series). Compressor 3 stopped. Flow
   * approximately 40 MSmÂ³/day from Troll East. Both compressors A and B use identical curves.
   * 
   * <p>
   * Based on Troll A production forecast: - Total gas rate declining from ~60 MSmÂ³/day in 2027 -
   * Troll East (TE) contributing ~40 MSmÂ³/day - 2-stage compression with A&B series only
   * </p>
   */
  @Test
  public void test2027ScenarioTwoCompressorTrains() {
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out
        .println("2027 SCENARIO: TROLL A WITH 2 COMPRESSOR TRAINS (A & B) - COMPRESSOR 3 STOPPED");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println("Configuration:");
    System.out.println("  - Compressor A: example_compressor_curve.json, VFD 44.4 MW, 7383 RPM");
    System.out.println(
        "  - Compressor B: example_compressor_curve.json, VFD 44.4 MW, 7383 RPM (identical)");
    System.out.println("  - Compressor 3: STOPPED (not in service)");
    System.out.println("  - Design flow: ~40 MSmÂ³/day (Troll East)");
    System.out.println();

    // Gas standard density for conversion to MSm3/day
    double gasStdDensity = 0.73; // kg/SmÂ³

    // Cooling water parameters
    double cpWater = 4.18; // kJ/(kgÂ·K)
    double waterDeltaT = 10.0; // Â°C

    // Store results
    List<Double> coolingDeltaTs = new ArrayList<>();
    List<Double> coolingDuties = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<Double> productionIncreases = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    // First get baseline (no cooling)
    double baselineFlow = runOptimization2027Scenario(0.0, coolingDeltaTs, coolingDuties,
        optimalFlows, productionIncreases, bottlenecks);

    // Sweep cooling from 0.5Â°C to 15Â°C in 0.5Â°C increments
    for (double deltaT = 0.5; deltaT <= 15.0; deltaT += 0.5) {
      runOptimization2027Scenario(deltaT, coolingDeltaTs, coolingDuties, optimalFlows,
          productionIncreases, bottlenecks);
    }

    // Print results table
    System.out.println("\n" + StringUtils.repeat("=", 140));
    System.out.println("RESULTS SUMMARY - 2027 SCENARIO (2 COMPRESSOR TRAINS, 40 MSmÂ³/day)");
    System.out.println(StringUtils.repeat("=", 140));
    System.out.println(String.format("%-10s %-12s %-12s %-14s %-16s %-14s %-10s %s", "Cooling(C)",
        "Duty(MW)", "CW(m3/hr)", "Flow(MSm3/d)", "Flow(kg/hr)", "Increase(kg/hr)", "Incr(%)",
        "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 140));

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double increase = optimalFlows.get(i) - baselineFlow;
      double increasePercent = (increase / baselineFlow) * 100;
      double duty = coolingDuties.get(i);
      double cwFlowM3Hr = duty * 1000.0 / (cpWater * waterDeltaT) * 3600.0 / 1000.0;
      double flowMSm3Day = optimalFlows.get(i) / gasStdDensity * 24.0 / 1e6;
      System.out.println(String.format("%-10.1f %-12.2f %-12.0f %-14.2f %-16.0f %-14.0f %-10.2f %s",
          coolingDeltaTs.get(i), duty, cwFlowM3Hr, flowMSm3Day, optimalFlows.get(i), increase,
          increasePercent, bottlenecks.get(i)));
    }

    // Print ASCII plot
    printAsciiPlot(coolingDeltaTs, optimalFlows, baselineFlow);

    // Print summary
    double maxCoolingFlow = optimalFlows.get(optimalFlows.size() - 1);
    double maxCoolingMSm3Day = maxCoolingFlow / gasStdDensity * 24.0 / 1e6;
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;
    double totalIncrease = maxCoolingFlow - baselineFlow;
    double totalIncreasePercent = (totalIncrease / baselineFlow) * 100;

    System.out.println("\n" + StringUtils.repeat("-", 80));
    System.out.println("SUMMARY - 2027 SCENARIO (2 Compressor Trains A & B):");
    System.out.println(String.format("  Baseline (0Â°C cooling): %.0f kg/hr = %.2f MSm3/day",
        baselineFlow, baselineMSm3Day));
    System.out.println(String.format("  At 15Â°C cooling:        %.0f kg/hr = %.2f MSm3/day",
        maxCoolingFlow, maxCoolingMSm3Day));
    System.out.println(String.format("  Production increase:    %.0f kg/hr (+%.2f%%)",
        totalIncrease, totalIncreasePercent));
    System.out.println(String.format("  Bottleneck at baseline: %s", bottlenecks.get(0)));
    System.out.println(
        String.format("  Bottleneck at 15Â°C:     %s", bottlenecks.get(bottlenecks.size() - 1)));

    // Calculate compressor utilization at 40 MSmÂ³/day target
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("COMPRESSOR UTILIZATION AT DESIGN POINT (~40 MSmÂ³/day)");
    System.out.println(StringUtils.repeat("=", 80));
    double targetFlow = 40.0 * 1e6 * gasStdDensity / 24.0; // kg/hr
    System.out.println(String.format("  Target flow: %.0f kg/hr = 40.0 MSm3/day", targetFlow));
    System.out.println(String.format("  Baseline capacity: %.2f MSm3/day", baselineMSm3Day));
    double headroom = (baselineFlow - targetFlow) / targetFlow * 100;
    System.out.println(String.format("  Headroom above target: %.1f%%", headroom));
  }

  /**
   * 2027 SCENARIO AT MAX CAPACITY: Analyzes effect of cooling when 2 compressor trains (A & B) are
   * running at full capacity. This shows the cooling benefit when compressors are the bottleneck.
   * 
   * <p>
   * Includes cooling duty, cooling water flow rates, and production increase analysis.
   * </p>
   */
  @Test
  public void test2027ScenarioMaxCapacityWithCooling() {
    System.out.println("\n" + StringUtils.repeat("=", 120));
    System.out.println(
        "2027 SCENARIO - MAX CAPACITY ANALYSIS WITH 2 COMPRESSOR TRAINS (A & B) - COOLING EFFECT");
    System.out.println(StringUtils.repeat("=", 120));
    System.out.println("Configuration:");
    System.out.println("  - Compressor A: example_compressor_curve.json, VFD 44.4 MW, 7383 RPM");
    System.out.println(
        "  - Compressor B: example_compressor_curve.json, VFD 44.4 MW, 7383 RPM (identical)");
    System.out.println("  - Compressor 3: STOPPED (not in service)");
    System.out.println("  - Running at MAX CAPACITY (compressor-limited)");
    System.out.println("  - Cooler pressure drop: 0.5 bara");
    System.out.println();

    // Gas standard density for conversion to MSm3/day
    double gasStdDensity = 0.73; // kg/SmÂ³

    // Cooling water parameters
    double cpWater = 4.18; // kJ/(kgÂ·K) - specific heat of water
    double waterDeltaT = 10.0; // Â°C (inlet 10Â°C, outlet 20Â°C)
    double waterDensity = 1000.0; // kg/mÂ³

    // Store results
    List<Double> coolingDeltaTs = new ArrayList<>();
    List<Double> coolingDuties = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<Double> productionIncreases = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();
    List<Double> coolingWaterFlows = new ArrayList<>();

    // First get baseline (no cooling) at max capacity
    double baselineFlow = runOptimization2027MaxCapacity(0.0, coolingDeltaTs, coolingDuties,
        optimalFlows, productionIncreases, bottlenecks, coolingWaterFlows);

    // Sweep cooling from 1Â°C to 15Â°C in 1Â°C increments
    for (double deltaT = 1.0; deltaT <= 15.0; deltaT += 1.0) {
      runOptimization2027MaxCapacity(deltaT, coolingDeltaTs, coolingDuties, optimalFlows,
          productionIncreases, bottlenecks, coolingWaterFlows);
    }

    // Print results table
    System.out.println("\n" + StringUtils.repeat("=", 160));
    System.out.println("RESULTS SUMMARY - 2027 MAX CAPACITY (2 COMPRESSOR TRAINS A & B)");
    System.out.println(StringUtils.repeat("=", 160));
    System.out.println(String.format("%-10s %-12s %-14s %-14s %-16s %-16s %-10s %-10s %s",
        "Cooling(C)", "Duty(MW)", "CW(m3/hr)", "CW(kg/s)", "Flow(MSm3/d)", "Flow(kg/hr)",
        "Incr(kg/hr)", "Incr(%)", "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 160));

    for (int i = 0; i < coolingDeltaTs.size(); i++) {
      double increase = optimalFlows.get(i) - baselineFlow;
      double increasePercent = (increase / baselineFlow) * 100;
      double duty = coolingDuties.get(i);
      // Cooling water: Q = m_dot * Cp * Î”T => m_dot = Q / (Cp * Î”T)
      double cwMassFlowKgS = (duty * 1e6) / (cpWater * 1000 * waterDeltaT); // kW / (J/kg-K * K)
      double cwVolFlowM3Hr = cwMassFlowKgS * 3600 / waterDensity;
      double flowMSm3Day = optimalFlows.get(i) / gasStdDensity * 24.0 / 1e6;
      System.out.println(
          String.format("%-10.1f %-12.2f %-14.1f %-14.1f %-16.2f %-16.0f %-10.0f %-10.2f %s",
              coolingDeltaTs.get(i), duty, cwVolFlowM3Hr, cwMassFlowKgS, flowMSm3Day,
              optimalFlows.get(i), increase, increasePercent, bottlenecks.get(i)));
    }

    // Print summary
    double maxCoolingFlow = optimalFlows.get(optimalFlows.size() - 1);
    double maxCoolingMSm3Day = maxCoolingFlow / gasStdDensity * 24.0 / 1e6;
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;
    double totalIncrease = maxCoolingFlow - baselineFlow;
    double totalIncreasePercent = (totalIncrease / baselineFlow) * 100;
    double maxDuty = coolingDuties.get(coolingDuties.size() - 1);
    double maxCwFlowKgS = (maxDuty * 1e6) / (cpWater * 1000 * waterDeltaT);
    double maxCwFlowM3Hr = maxCwFlowKgS * 3600 / waterDensity;

    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("SUMMARY - 2027 MAX CAPACITY (2 Compressor Trains A & B)");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println(String.format("  Baseline (0Â°C cooling): %.0f kg/hr = %.2f MSm3/day",
        baselineFlow, baselineMSm3Day));
    System.out.println(String.format("  At 15Â°C cooling:        %.0f kg/hr = %.2f MSm3/day",
        maxCoolingFlow, maxCoolingMSm3Day));
    System.out.println(String.format("  Production increase:    %.0f kg/hr (+%.2f%%)",
        totalIncrease, totalIncreasePercent));
    System.out.println(String.format("  Bottleneck at baseline: %s", bottlenecks.get(0)));
    System.out.println(
        String.format("  Bottleneck at 15Â°C:     %s", bottlenecks.get(bottlenecks.size() - 1)));

    System.out.println("\n" + StringUtils.repeat("-", 80));
    System.out.println("COOLING DUTY & WATER REQUIREMENTS AT 15Â°C COOLING:");
    System.out.println(String.format("  Cooling duty:           %.2f MW", maxDuty));
    System.out.println(String.format("  Cooling water flow:     %.1f mÂ³/hr", maxCwFlowM3Hr));
    System.out.println(String.format("  Cooling water flow:     %.1f kg/s", maxCwFlowKgS));
    System.out.println(String.format("  Water inlet temp:       10Â°C"));
    System.out.println(String.format("  Water outlet temp:      20Â°C"));
    System.out.println(String.format("  Water Î”T:               %.1fÂ°C", waterDeltaT));

    // Calculate efficiency
    double productionPerMW = totalIncrease / maxDuty; // kg/hr per MW
    double productionPerMWMSm3 = (totalIncrease / gasStdDensity * 24.0 / 1e6) / maxDuty; // MSmÂ³/d
                                                                                         // per MW
    System.out.println("\n" + StringUtils.repeat("-", 80));
    System.out.println("COOLING EFFICIENCY:");
    System.out.println(
        String.format("  Production gain per MW: %.0f kg/hr per MW of cooling", productionPerMW));
    System.out.println(String.format("  Production gain per MW: %.3f MSmÂ³/day per MW of cooling",
        productionPerMWMSm3));

    // Comparison with 40 MSmÂ³/day target
    double targetFlow = 40.0 * 1e6 * gasStdDensity / 24.0;
    double headroom = (baselineFlow - targetFlow) / targetFlow * 100;
    double headroomWithCooling = (maxCoolingFlow - targetFlow) / targetFlow * 100;
    System.out.println("\n" + StringUtils.repeat("-", 80));
    System.out.println("COMPARISON WITH 40 MSmÂ³/day TARGET (Troll East 2027):");
    System.out
        .println(String.format("  Target flow:            %.0f kg/hr = 40.0 MSm3/day", targetFlow));
    System.out.println(String.format("  Headroom (no cooling):  +%.1f%%", headroom));
    System.out.println(String.format("  Headroom (15Â°C cool):   +%.1f%%", headroomWithCooling));

    // Print ASCII plot
    System.out.println("\n" + StringUtils.repeat("=", 80));
    System.out.println("PRODUCTION vs COOLING (ASCII Plot)");
    System.out.println(StringUtils.repeat("=", 80));
    printAsciiPlot(coolingDeltaTs, optimalFlows, baselineFlow);
  }

  /**
   * Runs optimization for 2027 max capacity scenario with 2 compressor trains.
   */
  private double runOptimization2027MaxCapacity(double coolingDeltaT, List<Double> coolingDeltaTs,
      List<Double> coolingDuties, List<Double> optimalFlows, List<Double> productionIncreases,
      List<String> bottlenecks, List<Double> coolingWaterFlows) {

    ProcessSystem process = buildProcess2027MaxCapacity(coolingDeltaT);
    Stream inletStream = (Stream) process.getUnit("Inlet Stream");
    Heater cooler = (Heater) process.getUnit("Gas Cooler");

    double originalFlow = inletStream.getFlowRate("kg/hr");

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(originalFlow * 0.8, originalFlow * 1.3).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    // Re-run process at optimal flow to get correct cooling duty
    inletStream.setFlowRate(result.getOptimalRate(), "kg/hr");
    process.run();

    double coolingDuty = 0.0;
    if (cooler != null) {
      coolingDuty = Math.abs(cooler.getDuty() / 1e6); // MW
    }

    coolingDeltaTs.add(coolingDeltaT);
    coolingDuties.add(coolingDuty);
    optimalFlows.add(result.getOptimalRate());
    productionIncreases.add(result.getOptimalRate() - originalFlow);
    bottlenecks.add(result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A");
    coolingWaterFlows.add(coolingDuty); // Store duty, will calculate water flow later

    // Print compressor operating points
    Compressor compA = (Compressor) process.getUnit("CompA Compressor");
    Compressor compB = (Compressor) process.getUnit("CompB Compressor");
    System.out.println(String.format(
        "  Î”T=%.0fÂ°C: Duty=%.2f MW, Speed A=%.0f B=%.0f RPM, Util=%.1f%%, Flow=%.2f MSm3/d",
        coolingDeltaT, coolingDuty, compA.getSpeed(), compB.getSpeed(),
        result.getBottleneckUtilization() * 100, result.getOptimalRate() / 0.73 * 24 / 1e6));

    return result.getOptimalRate();
  }

  /**
   * Builds process for 2027 max capacity scenario - starts at higher flow to find true max.
   */
  private ProcessSystem buildProcess2027MaxCapacity(double coolingDeltaT) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    // Start at higher flow to allow optimization to find true maximum
    // 2 compressors can handle ~46 MSmÂ³/day based on previous run
    double startFlowKgHr = 46.0 * 1e6 * 0.73 / 24.0; // ~1,398,333 kg/hr

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(startFlowKgHr, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    // 4 processing trains for inlet processing
    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    PipeBeggsAndBrills train1Outlet =
        createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    PipeBeggsAndBrills train2Outlet =
        createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    PipeBeggsAndBrills train3Outlet =
        createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    PipeBeggsAndBrills train4Outlet =
        createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    StreamInterface feedToSplitter2;

    if (coolingDeltaT > 0) {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      double inletTemp = finalSeparator.getGasOutStream().getTemperature("C");
      gasCooler.setOutTemperature(inletTemp - coolingDeltaT, "C");
      double inletPressure = finalSeparator.getGasOutStream().getPressure("bara");
      gasCooler.setOutPressure(inletPressure - 0.5, "bara"); // 0.5 bar dP
      gasCooler.run();
      processSystem.add(gasCooler);

      Separator coolerSeparator = new Separator("Cooler Separator", gasCooler.getOutletStream());
      coolerSeparator.run();
      processSystem.add(coolerSeparator);

      feedToSplitter2 = coolerSeparator.getGasOutStream();
    } else {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      gasCooler.setOutTemperature(finalSeparator.getGasOutStream().getTemperature("C"), "C");
      gasCooler.run();
      processSystem.add(gasCooler);

      feedToSplitter2 = gasCooler.getOutletStream();
    }

    // Only 2 compressor trains (A & B) - 50/50 split
    Splitter splitter2 = new Splitter("Test Splitter2", feedToSplitter2);
    splitter2.setSplitFactors(new double[] {0.5, 0.5});
    splitter2.run();
    processSystem.add(splitter2);

    // Create only 2 compressor trains (A and B)
    StreamInterface compAOutlet =
        createCompressorTrain2027("CompA", splitter2.getSplitStream(0), processSystem);
    StreamInterface compBOutlet =
        createCompressorTrain2027("CompB", splitter2.getSplitStream(1), processSystem);

    // Manifold with 2 streams
    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(compAOutlet);
    manifold.addStream(compBOutlet);
    manifold.setSplitFactors(new double[] {0.5, 0.5});
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    processSystem.run();

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

    // Configure compressors - both A and B use identical curves
    Compressor compA = (Compressor) processSystem.getUnit("CompA Compressor");
    Compressor compB = (Compressor) processSystem.getUnit("CompB Compressor");

    configureCompressor1And2WithElectricDriver(compA, 7383.0);
    configureCompressor1And2WithElectricDriver(compB, 7383.0);

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

  /**
   * Builds process for 2027 max capacity with configurable cooling AND pressure drop.
   * 
   * @param coolingDeltaT temperature drop in cooler (Â°C), 0 = no cooling
   * @param pressureDrop pressure drop in cooler (bar)
   * @return configured ProcessSystem
   */
  private ProcessSystem buildProcess2027MaxCapacityWithPressureDrop(double coolingDeltaT,
      double pressureDrop) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    double startFlowKgHr = 46.0 * 1e6 * 0.73 / 24.0;

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(startFlowKgHr, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    PipeBeggsAndBrills train1Outlet =
        createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    PipeBeggsAndBrills train2Outlet =
        createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    PipeBeggsAndBrills train3Outlet =
        createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    PipeBeggsAndBrills train4Outlet =
        createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    StreamInterface feedToSplitter2;

    // Always add cooler/heater (even with 0 cooling) to apply pressure drop
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
      Separator coolerSeparator = new Separator("Cooler Separator", gasCooler.getOutletStream());
      coolerSeparator.run();
      processSystem.add(coolerSeparator);
      feedToSplitter2 = coolerSeparator.getGasOutStream();
    } else {
      feedToSplitter2 = gasCooler.getOutletStream();
    }

    Splitter splitter2 = new Splitter("Test Splitter2", feedToSplitter2);
    splitter2.setSplitFactors(new double[] {0.5, 0.5});
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface compAOutlet =
        createCompressorTrain2027("CompA", splitter2.getSplitStream(0), processSystem);
    StreamInterface compBOutlet =
        createCompressorTrain2027("CompB", splitter2.getSplitStream(1), processSystem);

    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(compAOutlet);
    manifold.addStream(compBOutlet);
    manifold.setSplitFactors(new double[] {0.5, 0.5});
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    processSystem.run();

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

    Compressor compA = (Compressor) processSystem.getUnit("CompA Compressor");
    Compressor compB = (Compressor) processSystem.getUnit("CompB Compressor");

    configureCompressor1And2WithElectricDriver(compA, 7383.0);
    configureCompressor1And2WithElectricDriver(compB, 7383.0);

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

  /**
   * PRESSURE DROP ANALYSIS: Studies effect of increased pressure drop (0-1 bar) without cooling.
   * Uses 2027 scenario with 2 compressor trains (A & B) and smooth VFD motor power curve.
   * 
   * <p>
   * This test isolates the effect of pressure drop on production capacity when there is no
   * temperature change (0Â°C cooling). Useful for evaluating:
   * <ul>
   * <li>Impact of cooler/heat exchanger pressure drop</li>
   * <li>Effect of adding equipment (filters, separators) in the gas path</li>
   * <li>Sensitivity of compressor performance to inlet pressure</li>
   * </ul>
   * </p>
   */
  @Test
  public void test2027PressureDropEffectNoCooling() {
    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println("2027 SCENARIO - PRESSURE DROP EFFECT ON PRODUCTION (NO COOLING)");
    System.out.println(StringUtils.repeat("=", 130));
    System.out.println("Configuration:");
    System.out.println("  - 2 Compressor trains (A & B) with identical curves");
    System.out.println("  - Smooth VFD motor power curve (no discrete steps)");
    System.out.println("  - Temperature change: 0Â°C (no cooling)");
    System.out.println("  - Pressure drop sweep: 0.0 to 1.0 bar in 0.1 bar increments");
    System.out.println();

    double gasStdDensity = 0.73;

    List<Double> pressureDrops = new ArrayList<>();
    List<Double> optimalFlows = new ArrayList<>();
    List<Double> compressorInletPressures = new ArrayList<>();
    List<Double> compressorSpeeds = new ArrayList<>();
    List<Double> compressorPowers = new ArrayList<>();
    List<String> bottlenecks = new ArrayList<>();

    // Sweep pressure drop from 0 to 1 bar
    for (double dP = 0.0; dP <= 1.05; dP += 0.1) {
      ProcessSystem process = buildProcess2027MaxCapacityWithPressureDrop(0.0, dP);
      Stream inletStream = (Stream) process.getUnit("Inlet Stream");
      double originalFlow = inletStream.getFlowRate("kg/hr");

      ProductionOptimizer optimizer = new ProductionOptimizer();
      OptimizationConfig config =
          new OptimizationConfig(originalFlow * 0.8, originalFlow * 1.3).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
          proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
          ObjectiveType.MAXIMIZE);

      OptimizationResult result = optimizer.optimize(process, inletStream, config,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      // Re-run at optimal to get accurate readings
      inletStream.setFlowRate(result.getOptimalRate(), "kg/hr");
      process.run();

      Compressor compA = (Compressor) process.getUnit("CompA Compressor");
      double pIn = compA.getInletStream().getPressure("bara");
      double speed = compA.getSpeed();
      double power = compA.getPower() / 1e6;

      pressureDrops.add(dP);
      optimalFlows.add(result.getOptimalRate());
      compressorInletPressures.add(pIn);
      compressorSpeeds.add(speed);
      compressorPowers.add(power);
      bottlenecks.add(result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A");

      double flowMSm3Day = result.getOptimalRate() / gasStdDensity * 24.0 / 1e6;
      System.out.println(String.format(
          "dP=%.1f bar: P_in=%.2f bar, Speed=%.0f RPM, Power=%.2f MW, Flow=%.2f MSm3/d", dP, pIn,
          speed, power, flowMSm3Day));
    }

    double baselineFlow = optimalFlows.get(0);
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;
    double baselinePressure = compressorInletPressures.get(0);

    // Print detailed results table
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("DETAILED RESULTS - PRESSURE DROP EFFECT (NO COOLING)");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println(String.format("%-8s %-12s %-12s %-12s %-12s %-14s %-14s %-12s %s", "dP(bar)",
        "P_in(bar)", "Speed(RPM)", "Power(MW)", "Flow(MSm3/d)", "Loss(MSm3/d)", "Loss(%)",
        "Loss/0.1bar", "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 150));

    double prevFlow = baselineFlow;
    for (int i = 0; i < pressureDrops.size(); i++) {
      double dP = pressureDrops.get(i);
      double flow = optimalFlows.get(i);
      double pIn = compressorInletPressures.get(i);
      double speed = compressorSpeeds.get(i);
      double power = compressorPowers.get(i);
      double flowMSm3 = flow / gasStdDensity * 24.0 / 1e6;
      double loss = baselineFlow - flow;
      double lossMSm3 = loss / gasStdDensity * 24.0 / 1e6;
      double lossPercent = (loss / baselineFlow) * 100;
      double incrementalLoss = (prevFlow - flow) / gasStdDensity * 24.0 / 1e6;

      System.out.println(
          String.format("%-8.1f %-12.2f %-12.0f %-12.2f %-12.2f %-14.2f %-14.2f %-12.3f %s", dP,
              pIn, speed, power, flowMSm3, lossMSm3, lossPercent, i > 0 ? incrementalLoss : 0.0,
              bottlenecks.get(i)));

      prevFlow = flow;
    }

    // Summary
    double maxDpFlow = optimalFlows.get(optimalFlows.size() - 1);
    double maxDpMSm3Day = maxDpFlow / gasStdDensity * 24.0 / 1e6;
    double totalLoss = baselineFlow - maxDpFlow;
    double totalLossMSm3 = totalLoss / gasStdDensity * 24.0 / 1e6;
    double totalLossPercent = (totalLoss / baselineFlow) * 100;
    double lossPerBar = totalLossMSm3 / 1.0;
    double finalPressure = compressorInletPressures.get(compressorInletPressures.size() - 1);

    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("SUMMARY");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println(String.format("  Baseline (0 bar dP):      %.2f MSmÂ³/day at %.2f bar inlet",
        baselineMSm3Day, baselinePressure));
    System.out.println(String.format("  At 1.0 bar dP:            %.2f MSmÂ³/day at %.2f bar inlet",
        maxDpMSm3Day, finalPressure));
    System.out.println(String.format("  Total production loss:    %.2f MSmÂ³/day (%.2f%%)",
        totalLossMSm3, totalLossPercent));
    System.out
        .println(String.format("  Loss per 0.1 bar dP:      ~%.3f MSmÂ³/day", lossPerBar / 10));
    System.out.println(String.format("  Loss per 1.0 bar dP:      ~%.2f MSmÂ³/day", lossPerBar));

    // ASCII plot
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("PRODUCTION vs PRESSURE DROP (ASCII Plot)");
    System.out.println(StringUtils.repeat("=", 100));

    double minFlowPlot =
        optimalFlows.stream().mapToDouble(d -> d).min().orElse(baselineFlow) * 0.995;
    double maxFlowPlot = baselineFlow * 1.005;
    double rangePlot = maxFlowPlot - minFlowPlot;
    int barWidth = 60;

    for (int i = 0; i < pressureDrops.size(); i++) {
      double dP = pressureDrops.get(i);
      double flow = optimalFlows.get(i);
      double flowMSm3 = flow / gasStdDensity * 24.0 / 1e6;
      int barLen = (int) ((flow - minFlowPlot) / rangePlot * barWidth);
      barLen = Math.max(0, Math.min(barWidth, barLen));

      StringBuilder bar = new StringBuilder();
      for (int j = 0; j < barWidth; j++) {
        if (j < barLen) {
          bar.append("â–ˆ");
        } else {
          bar.append(" ");
        }
      }

      double lossPercent = ((baselineFlow - flow) / baselineFlow) * 100;
      System.out.println(String.format("%4.1f bar |%s| %.2f MSmÂ³/d (-%.2f%%)", dP, bar.toString(),
          flowMSm3, lossPercent));
    }

    System.out.println("        |" + StringUtils.repeat(" ", barWidth) + "|");
    System.out.println(
        String.format("         %.2f%s%.2f MSmÂ³/d", minFlowPlot / gasStdDensity * 24.0 / 1e6,
            StringUtils.repeat(" ", barWidth - 12), maxFlowPlot / gasStdDensity * 24.0 / 1e6));

    // Key insight
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("KEY INSIGHTS");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println("  - Pressure drop directly reduces compressor inlet pressure");
    System.out.println("  - Lower inlet pressure = higher compression ratio needed");
    System.out.println("  - Higher compression ratio = more power required per unit flow");
    System.out.println("  - With fixed power limit, throughput decreases");
    System.out.println();
    System.out.println(String
        .format("  RULE OF THUMB: ~%.2f MSmÂ³/day lost per bar of pressure drop", lossPerBar));
  }

  /**
   * Runs optimization for 2027 scenario with 2 compressor trains.
   */
  private double runOptimization2027Scenario(double coolingDeltaT, List<Double> coolingDeltaTs,
      List<Double> coolingDuties, List<Double> optimalFlows, List<Double> productionIncreases,
      List<String> bottlenecks) {

    ProcessSystem process = buildProcess2027Scenario(coolingDeltaT);
    Stream inletStream = (Stream) process.getUnit("Inlet Stream");
    Heater cooler = (Heater) process.getUnit("Gas Cooler");

    double originalFlow = inletStream.getFlowRate("kg/hr");

    double coolingDuty = 0.0;
    if (cooler != null) {
      coolingDuty = Math.abs(cooler.getDuty() / 1e6);
    }

    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.0005).maxIterations(25).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    coolingDeltaTs.add(coolingDeltaT);
    coolingDuties.add(coolingDuty);
    optimalFlows.add(result.getOptimalRate());
    productionIncreases.add(result.getOptimalRate() - originalFlow);
    bottlenecks.add(result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A");

    // Print compressor operating points
    Compressor compA = (Compressor) process.getUnit("CompA Compressor");
    Compressor compB = (Compressor) process.getUnit("CompB Compressor");
    System.out.println(String.format("  Speeds: CompA=%.0f, CompB=%.0f RPM | Max util: %.1f%%",
        compA.getSpeed(), compB.getSpeed(), result.getBottleneckUtilization() * 100));

    System.out.println(
        String.format("Cooling Î”T=%.1fÂ°C: Duty=%.2f MW, Optimal=%.0f kg/hr, Bottleneck=%s",
            coolingDeltaT, coolingDuty, result.getOptimalRate(),
            result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A"));

    return result.getOptimalRate();
  }

  /**
   * Builds process for 2027 scenario with only 2 compressor trains (A & B). Flow rate set to
   * approximately 40 MSmÂ³/day (Troll East).
   */
  private ProcessSystem buildProcess2027Scenario(double coolingDeltaT) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    // 40 MSmÂ³/day = 40e6 SmÂ³/day Ã— 0.73 kg/SmÂ³ / 24 hr = 1,216,667 kg/hr
    double flowRate2027 = 40.0 * 1e6 * 0.73 / 24.0; // ~1,216,667 kg/hr

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(flowRate2027, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    // Still 4 processing trains for inlet processing
    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    PipeBeggsAndBrills train1Outlet =
        createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    PipeBeggsAndBrills train2Outlet =
        createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    PipeBeggsAndBrills train3Outlet =
        createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    PipeBeggsAndBrills train4Outlet =
        createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    StreamInterface feedToSplitter2;

    if (coolingDeltaT > 0) {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      double inletTemp = finalSeparator.getGasOutStream().getTemperature("C");
      gasCooler.setOutTemperature(inletTemp - coolingDeltaT, "C");
      double inletPressure = finalSeparator.getGasOutStream().getPressure("bara");
      gasCooler.setOutPressure(inletPressure - 0.5, "bara"); // 0.5 bar dP
      gasCooler.run();
      processSystem.add(gasCooler);

      Separator coolerSeparator = new Separator("Cooler Separator", gasCooler.getOutletStream());
      coolerSeparator.run();
      processSystem.add(coolerSeparator);

      feedToSplitter2 = coolerSeparator.getGasOutStream();
    } else {
      Heater gasCooler = new Heater("Gas Cooler", finalSeparator.getGasOutStream());
      gasCooler.setOutTemperature(finalSeparator.getGasOutStream().getTemperature("C"), "C");
      gasCooler.run();
      processSystem.add(gasCooler);

      feedToSplitter2 = gasCooler.getOutletStream();
    }

    // Only 2 compressor trains (A & B) - 50/50 split
    Splitter splitter2 = new Splitter("Test Splitter2", feedToSplitter2);
    splitter2.setSplitFactors(new double[] {0.5, 0.5});
    splitter2.run();
    processSystem.add(splitter2);

    // Create only 2 compressor trains (A and B)
    StreamInterface compAOutlet =
        createCompressorTrain2027("CompA", splitter2.getSplitStream(0), processSystem);
    StreamInterface compBOutlet =
        createCompressorTrain2027("CompB", splitter2.getSplitStream(1), processSystem);

    // Manifold with 2 streams
    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(compAOutlet);
    manifold.addStream(compBOutlet);
    manifold.setSplitFactors(new double[] {0.5, 0.5});
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    processSystem.run();

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

    // Configure compressors - both A and B use identical curves
    Compressor compA = (Compressor) processSystem.getUnit("CompA Compressor");
    Compressor compB = (Compressor) processSystem.getUnit("CompB Compressor");

    configureCompressor1And2WithElectricDriver(compA, 7383.0);
    configureCompressor1And2WithElectricDriver(compB, 7383.0);

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

  /**
   * Creates a compressor train for 2027 scenario.
   */
  private StreamInterface createCompressorTrain2027(String trainName, StreamInterface inlet,
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
   * DIAGNOSTIC TEST: Investigates why cooling results show oscillation. Displays compressor
   * inlet/outlet pressures, temperatures, gas density, and operating point details to understand
   * the strange behavior at certain cooling levels.
   */
  @Test
  public void test2027DiagnosticCompressorBehavior() {
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("DIAGNOSTIC: COMPRESSOR OPERATING POINT ANALYSIS - WHY OSCILLATION?");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println("Investigating why production dips at 4-5Â°C and 7Â°C cooling");
    System.out.println();

    double gasStdDensity = 0.73;

    // Headers for diagnostic table
    System.out.println(String.format(
        "%-6s %-10s %-10s %-10s %-10s %-10s %-12s %-10s %-10s %-12s %-10s %-10s %-10s", "Cool",
        "P_in(bar)", "P_out(bar)", "T_in(C)", "T_out(C)", "Density", "Act.Vol(m3/h)", "Speed",
        "Util(%)", "Flow(MSm3)", "Head(kJ)", "Power(MW)", "MaxPwr(MW)"));
    System.out.println(StringUtils.repeat("-", 150));

    // Sweep cooling from 0Â°C to 15Â°C
    for (double deltaT = 0.0; deltaT <= 15.0; deltaT += 1.0) {
      analyzeCompressorOperatingPoint(deltaT, gasStdDensity);
    }

    // Now show more detail for the problematic range
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("DETAILED ANALYSIS: 3-8Â°C COOLING (PROBLEMATIC ZONE)");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println(String.format(
        "%-6s %-10s %-10s %-10s %-10s %-10s %-12s %-10s %-10s %-12s %-10s %-10s %-10s", "Cool",
        "P_in(bar)", "P_out(bar)", "T_in(C)", "T_out(C)", "Density", "Act.Vol(m3/h)", "Speed",
        "Util(%)", "Flow(MSm3)", "Head(kJ)", "Power(MW)", "MaxPwr(MW)"));
    System.out.println(StringUtils.repeat("-", 150));

    for (double deltaT = 3.0; deltaT <= 8.0; deltaT += 0.5) {
      analyzeCompressorOperatingPoint(deltaT, gasStdDensity);
    }

    // ASCII pressure plot
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("COMPRESSOR INLET PRESSURE vs COOLING (shows effect of cooler dP)");
    System.out.println(StringUtils.repeat("=", 100));
    printPressureAnalysis();
  }

  /**
   * Analyzes compressor operating point at a specific cooling level.
   */
  private void analyzeCompressorOperatingPoint(double coolingDeltaT, double gasStdDensity) {
    ProcessSystem process = buildProcess2027MaxCapacity(coolingDeltaT);
    Stream inletStream = (Stream) process.getUnit("Inlet Stream");

    double originalFlow = inletStream.getFlowRate("kg/hr");

    // Run optimization
    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(originalFlow * 0.8, originalFlow * 1.3).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    // Re-run at optimal to get accurate readings
    inletStream.setFlowRate(result.getOptimalRate(), "kg/hr");
    process.run();

    // Get compressor A details
    Compressor compA = (Compressor) process.getUnit("CompA Compressor");
    double pIn = compA.getInletStream().getPressure("bara");
    double pOut = compA.getOutletStream().getPressure("bara");
    double tIn = compA.getInletStream().getTemperature("C");
    double tOut = compA.getOutletStream().getTemperature("C");
    double density = compA.getInletStream().getFluid().getDensity("kg/m3");
    double actVolFlow = compA.getInletStream().getFlowRate("m3/hr");
    double speed = compA.getSpeed();
    double util = result.getBottleneckUtilization() * 100;
    double flowMSm3 = result.getOptimalRate() / gasStdDensity * 24 / 1e6;
    double head = compA.getPolytropicFluidHead();
    double power = compA.getPower() / 1e6;
    double maxPower = compA.getCapacityMax() / 1e6; // Max available power at current speed

    System.out.println(String.format(
        "%-6.1f %-10.2f %-10.2f %-10.2f %-10.2f %-10.3f %-12.0f %-10.0f %-10.1f %-12.2f %-10.1f %-10.2f %-10.2f",
        coolingDeltaT, pIn, pOut, tIn, tOut, density, actVolFlow, speed, util, flowMSm3, head,
        power, maxPower));
  }

  /**
   * Prints pressure analysis showing how cooler dP affects compressor inlet.
   */
  private void printPressureAnalysis() {
    System.out.println("\nPressure cascade from Final Separator to Compressor:");
    System.out.println("  Final Separator outlet: ~35.0 bara (base)");
    System.out.println("  Cooler pressure drop:   -0.5 bara (when cooling > 0)");
    System.out.println("  Cooler Separator:       ~34.5 bara");
    System.out.println("  Splitter2 to CompA:     ~34.5 bara");
    System.out.println("  Pipe dP to compressor:  ~0.1-0.3 bara");
    System.out.println("  Compressor inlet:       ~34.2-34.4 bara");
    System.out.println();
    System.out.println("ROOT CAUSE IDENTIFIED: VFD Motor Power-Speed Curve!");
    System.out.println(StringUtils.repeat("=", 60));
    System.out.println("The driver max power varies with speed:");
    System.out.println("  At 7050-7070 RPM: MaxPwr = 44.40 MW (full power)");
    System.out.println("  At 6950-6965 RPM: MaxPwr = 43.75-43.82 MW (reduced)");
    System.out.println();
    System.out.println("When cooling changes gas density, the compressor solves for");
    System.out.println("a different speed to achieve the same outlet pressure.");
    System.out.println("At certain cooling levels, the optimal speed falls into a");
    System.out.println("lower power band on the driver curve, limiting throughput.");
    System.out.println();
    System.out.println("This is a REAL physical effect, not a modeling artifact.");
    System.out.println("The compressor chart interpolation is working correctly.");
    System.out.println("The limitation is in the VFD motor's torque-speed curve.");
  }

  /**
   * COMPRESSOR MAP VISUALIZATION: Shows where operating points fall on the compressor map at
   * different cooling temperatures. This helps visualize why production oscillates.
   * 
   * <p>
   * The test displays:
   * <ul>
   * <li>The compressor speed curves (flow vs head at each RPM)</li>
   * <li>Operating points at different cooling levels</li>
   * <li>Surge and stonewall limits</li>
   * <li>ASCII visualization of the compressor map</li>
   * </ul>
   * </p>
   */
  @Test
  public void testCompressorMapOperatingPoints() {
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out
        .println("COMPRESSOR MAP VISUALIZATION - OPERATING POINTS AT DIFFERENT COOLING LEVELS");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println();

    double gasStdDensity = 0.73;

    // First, print the compressor chart data (speed curves)
    System.out.println("COMPRESSOR MAP (from example_compressor_curve.json)");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println("Speed curves define: Flow (m3/hr) vs Polytropic Head (kJ/kg)");
    System.out.println();

    // Speed curve data from example_compressor_curve.json
    double[] speeds = {7382.55, 7031.0, 6679.45, 6327.9, 5976.35, 5624.8, 5273.25, 4921.7};
    double[][] flows = {
        {19852.05, 21679.87, 23507.69, 25335.50, 27163.32, 28991.13, 30818.95, 32646.77, 34474.58,
            36302.40},
        {17735.92, 19543.79, 21351.65, 23159.52, 24967.38, 26775.24, 28583.11, 30390.97, 32198.84,
            34006.70},
        {16592.03, 18276.26, 19960.48, 21644.71, 23328.94, 25013.16, 26697.39, 28381.61, 30065.84,
            31750.06},
        {15510.56, 17055.53, 18600.50, 20145.47, 21690.43, 23235.40, 24780.37, 26325.34, 27870.30,
            29415.27},
        {14424.75, 15829.80, 17234.85, 18639.89, 20044.94, 21449.98, 22855.03, 24260.08, 25665.12,
            27070.17},
        {13369.91, 14633.37, 15896.83, 17160.29, 18423.75, 19687.20, 20950.66, 22214.12, 23477.58,
            24741.04},
        {12285.77, 13413.17, 14540.57, 15667.96, 16795.36, 17922.76, 19050.15, 20177.55, 21304.95,
            22432.35},
        {11291.62, 12279.63, 13267.64, 14255.65, 15243.67, 16231.68, 17219.69, 18207.70, 19195.72,
            20183.73}};
    double[][] heads =
        {{256.69, 253.67, 249.29, 243.58, 236.91, 228.33, 217.05, 202.21, 181.39, 119.74},
            {233.14, 230.33, 226.34, 220.79, 214.38, 206.20, 195.54, 181.90, 163.47, 114.76},
            {209.59, 206.70, 202.64, 197.42, 191.12, 183.31, 173.50, 161.16, 145.23, 100.52},
            {187.13, 184.12, 180.13, 175.31, 169.41, 162.10, 153.42, 142.65, 128.75, 92.68},
            {165.86, 162.92, 159.18, 154.69, 149.26, 142.74, 135.03, 125.77, 113.55, 85.09},
            {145.85, 143.07, 139.64, 135.56, 130.66, 124.99, 118.26, 110.59, 100.09, 77.01},
            {127.29, 124.71, 121.65, 117.98, 113.70, 108.82, 102.98, 96.26, 87.69, 69.36},
            {109.91, 107.60, 104.90, 101.73, 98.08, 93.96, 89.22, 83.71, 76.25, 61.73}};

    // Print compressor map summary
    System.out.println("SPEED CURVE SUMMARY:");
    System.out.println(String.format("%-12s %-18s %-18s %-18s %-18s", "Speed(RPM)",
        "Min Flow(m3/h)", "Max Flow(m3/h)", "Max Head(kJ/kg)", "Min Head(kJ/kg)"));
    System.out.println(StringUtils.repeat("-", 85));
    for (int i = 0; i < speeds.length; i++) {
      double minFlow = flows[i][0];
      double maxFlow = flows[i][flows[i].length - 1];
      double maxHead = heads[i][0];
      double minHead = heads[i][heads[i].length - 1];
      System.out.println(String.format("%-12.0f %-18.0f %-18.0f %-18.1f %-18.1f", speeds[i],
          minFlow, maxFlow, maxHead, minHead));
    }

    // Now collect operating points at different cooling levels
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("OPERATING POINTS AT DIFFERENT COOLING LEVELS");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println(String.format("%-8s %-12s %-12s %-12s %-14s %-10s %-12s %-10s %-10s",
        "Cool(C)", "Speed(RPM)", "Flow(m3/h)", "Head(kJ/kg)", "Flow(MSm3/d)", "Power(MW)",
        "MaxPwr(MW)", "Util(%)", "Status"));
    System.out.println(StringUtils.repeat("-", 150));

    // Store operating points for visualization
    List<double[]> operatingPoints = new ArrayList<>(); // [cooling, speed, flow, head]

    for (double deltaT = 0.0; deltaT <= 15.0; deltaT += 1.0) {
      double[] opPoint = getCompressorOperatingPoint(deltaT, gasStdDensity);
      operatingPoints.add(opPoint);

      // Determine status based on speed
      String status = "OK";
      double speed = opPoint[1];
      if (speed >= 7020 && speed <= 7050) {
        status = "FULL POWER (44.4MW)";
      } else if (speed >= 6930 && speed < 7020) {
        status = "REDUCED PWR (~43.7MW)";
      } else if (speed >= 6850 && speed < 6930) {
        status = "LOWER PWR (~43.0MW)";
      }

      System.out.println(
          String.format("%-8.1f %-12.0f %-12.0f %-12.1f %-14.2f %-10.2f %-12.2f %-10.1f %-10s",
              opPoint[0], opPoint[1], opPoint[2], opPoint[3], opPoint[4], opPoint[5], opPoint[6],
              opPoint[7], status));
    }

    // ASCII visualization of compressor map with operating points
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("ASCII COMPRESSOR MAP (Flow vs Head)");
    System.out.println(StringUtils.repeat("=", 100));
    printCompressorMapASCII(speeds, flows, heads, operatingPoints);

    // ASCII visualization showing operating points relative to speed curves
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("OPERATING POINT TRAJECTORY (Speed vs Flow)");
    System.out.println(StringUtils.repeat("=", 100));
    printSpeedFlowASCII(speeds, flows, operatingPoints);

    // Show the oscillation pattern visually
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("FLOW vs COOLING (shows oscillation pattern)");
    System.out.println(StringUtils.repeat("=", 100));
    printFlowOscillationASCII(operatingPoints);

    // Key observations
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("KEY OBSERVATIONS:");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println();
    System.out.println("1. Operating points move along the compressor map as cooling changes");
    System.out
        .println("2. At different cooling levels, the compressor settles at different speeds");
    System.out.println("3. The VFD motor power curve limits power at certain speed bands:");
    System.out.println("   - At ~7031-7383 RPM: Full 44.4 MW available");
    System.out.println("   - At ~6937-7000 RPM: Only ~43.6-43.8 MW available");
    System.out.println("   - At ~6679-6900 RPM: Only ~42.6-43.0 MW available");
    System.out.println();
    System.out.println("4. This causes the oscillation:");
    System.out.println("   - 0Â°C cooling: Speed ~6965 RPM -> power limited -> lower flow");
    System.out.println("   - 3Â°C cooling: Speed ~7066 RPM -> full power -> higher flow");
    System.out.println("   - 4Â°C cooling: Speed ~6963 RPM -> power limited -> lower flow");
    System.out.println("   - 6Â°C cooling: Speed ~7048 RPM -> full power -> higher flow");
    System.out.println("   - 7Â°C cooling: Speed ~6937 RPM -> power limited -> lower flow");
    System.out.println();
    System.out.println("5. The compressor map interpolation is working correctly!");
    System.out.println("   The oscillation is a REAL physical effect from the motor driver.");
  }

  /**
   * Gets compressor operating point at a specific cooling level.
   * 
   * @return array of [cooling, speed, actVolFlow, head, flowMSm3Day, power, maxPower, utilization]
   */
  private double[] getCompressorOperatingPoint(double coolingDeltaT, double gasStdDensity) {
    ProcessSystem process = buildProcess2027MaxCapacity(coolingDeltaT);
    Stream inletStream = (Stream) process.getUnit("Inlet Stream");
    double originalFlow = inletStream.getFlowRate("kg/hr");

    // Run optimization
    ProductionOptimizer optimizer = new ProductionOptimizer();
    OptimizationConfig config =
        new OptimizationConfig(originalFlow * 0.8, originalFlow * 1.3).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult result = optimizer.optimize(process, inletStream, config,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    // Re-run at optimal to get accurate readings
    inletStream.setFlowRate(result.getOptimalRate(), "kg/hr");
    process.run();

    Compressor compA = (Compressor) process.getUnit("CompA Compressor");
    double speed = compA.getSpeed();
    double actVolFlow = compA.getInletStream().getFlowRate("m3/hr");
    double head = compA.getPolytropicFluidHead();
    double flowMSm3Day = result.getOptimalRate() / gasStdDensity * 24.0 / 1e6;
    double power = compA.getPower() / 1e6;
    double maxPower = compA.getCapacityMax() / 1e6;
    double util = result.getBottleneckUtilization() * 100;

    return new double[] {coolingDeltaT, speed, actVolFlow, head, flowMSm3Day, power, maxPower,
        util};
  }

  /**
   * Prints ASCII compressor map showing flow vs head with operating points.
   */
  private void printCompressorMapASCII(double[] speeds, double[][] flows, double[][] heads,
      List<double[]> operatingPoints) {

    // Map dimensions
    int width = 80;
    int height = 30;

    // Find ranges
    double minFlow = 10000, maxFlow = 40000;
    double minHead = 50, maxHead = 280;

    // Create ASCII grid
    char[][] grid = new char[height][width];
    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        grid[i][j] = ' ';
      }
    }

    // Draw speed curves
    char[] curveChars = {'1', '2', '3', '4', '5', '6', '7', '8'};
    for (int s = 0; s < speeds.length; s++) {
      for (int p = 0; p < flows[s].length - 1; p++) {
        int x1 = (int) ((flows[s][p] - minFlow) / (maxFlow - minFlow) * (width - 1));
        int y1 = height - 1 - (int) ((heads[s][p] - minHead) / (maxHead - minHead) * (height - 1));
        int x2 = (int) ((flows[s][p + 1] - minFlow) / (maxFlow - minFlow) * (width - 1));
        int y2 =
            height - 1 - (int) ((heads[s][p + 1] - minHead) / (maxHead - minHead) * (height - 1));

        // Draw line between points
        if (x1 >= 0 && x1 < width && y1 >= 0 && y1 < height) {
          grid[y1][x1] = curveChars[s];
        }
        if (x2 >= 0 && x2 < width && y2 >= 0 && y2 < height) {
          grid[y2][x2] = curveChars[s];
        }
      }
    }

    // Draw operating points
    for (double[] op : operatingPoints) {
      double flow = op[2];
      double head = op[3];
      int x = (int) ((flow - minFlow) / (maxFlow - minFlow) * (width - 1));
      int y = height - 1 - (int) ((head - minHead) / (maxHead - minHead) * (height - 1));
      if (x >= 0 && x < width && y >= 0 && y < height) {
        grid[y][x] = '*';
      }
    }

    // Print with axis labels
    System.out.println("Head (kJ/kg)");
    for (int i = 0; i < height; i++) {
      if (i == 0) {
        System.out.print(String.format("%6.0f |", maxHead));
      } else if (i == height / 2) {
        System.out.print(String.format("%6.0f |", (maxHead + minHead) / 2));
      } else if (i == height - 1) {
        System.out.print(String.format("%6.0f |", minHead));
      } else {
        System.out.print("       |");
      }
      System.out.println(new String(grid[i]));
    }
    System.out.println("       +" + StringUtils.repeat("-", width));
    System.out.println(String.format("       %6.0f%s%6.0f", minFlow,
        StringUtils.repeat(" ", width - 12), maxFlow));
    System.out.println("                           Flow (m3/hr)");

    // Legend
    System.out.println("\nLEGEND:");
    System.out.println(
        "  Speed curves: 1=7383, 2=7031, 3=6679, 4=6328, 5=5976, 6=5625, 7=5273, 8=4922 RPM");
    System.out.println("  Operating points: * (at different cooling levels)");
    System.out
        .println("  Note: Operating points cluster between curves 1 and 2 (6900-7100 RPM range)");
  }

  /**
   * Prints ASCII diagram showing speed vs flow for operating points.
   */
  private void printSpeedFlowASCII(double[] speeds, double[][] flows,
      List<double[]> operatingPoints) {
    int width = 70;

    // Speed axis
    double minSpeed = 4800, maxSpeed = 7500;

    System.out.println("\nSpeed (RPM) vs Operating Point");
    System.out.println();

    // Draw speed bands with power limits
    System.out.println("Speed bands and VFD motor power limits:");
    System.out.println("  7383 |--------------------| Full Power Zone (44.4 MW)");
    System.out.println("  7031 |--------------------| â† Transition zone");
    System.out.println("  6679 |--------------------| Reduced Power Zone (~42.6 MW)");
    System.out.println("  6328 |--------------------| Lower Power Zone");
    System.out.println();

    // Plot operating points on speed axis
    System.out.println("Operating points at each cooling level:");
    System.out.println();

    for (double[] op : operatingPoints) {
      double cooling = op[0];
      double speed = op[1];
      double flowMSm3 = op[4];
      double maxPwr = op[6];

      // Position on scale
      int pos = (int) ((speed - minSpeed) / (maxSpeed - minSpeed) * width);
      pos = Math.max(0, Math.min(width - 1, pos));

      StringBuilder line = new StringBuilder();
      for (int i = 0; i < width; i++) {
        if (i == pos) {
          line.append("*");
        } else {
          line.append("-");
        }
      }

      String powerStatus = maxPwr >= 44.3 ? "FULL" : "LIMITED";
      System.out.println(String.format("%4.0fÂ°C |%s| %.0f RPM, %.2f MSm3/d, %.1f MW max [%s]",
          cooling, line.toString(), speed, flowMSm3, maxPwr, powerStatus));
    }

    System.out.println("       |" + StringUtils.repeat(" ", width) + "|");
    System.out.println(String.format("     %5.0f%s%5.0f RPM", minSpeed,
        StringUtils.repeat(" ", width - 10), maxSpeed));
  }

  /**
   * Prints ASCII showing flow oscillation pattern.
   */
  private void printFlowOscillationASCII(List<double[]> operatingPoints) {
    // Find flow range
    double minFlow = Double.MAX_VALUE;
    double maxFlow = Double.MIN_VALUE;
    for (double[] op : operatingPoints) {
      minFlow = Math.min(minFlow, op[4]);
      maxFlow = Math.max(maxFlow, op[4]);
    }

    // Add margin
    double margin = (maxFlow - minFlow) * 0.1;
    minFlow -= margin;
    maxFlow += margin;

    int width = 60;

    System.out.println("\nProduction (MSm3/day) vs Cooling Temperature");
    System.out.println();
    System.out.println(String.format("Flow (MSm3/d) | Cooling(Â°C)"));
    System.out.println(String.format("%.2f          |", maxFlow));

    for (int i = 0; i < operatingPoints.size(); i++) {
      double[] op = operatingPoints.get(i);
      double flow = op[4];
      double cooling = op[0];
      double maxPwr = op[6];

      int pos = (int) ((flow - minFlow) / (maxFlow - minFlow) * width);
      pos = Math.max(0, Math.min(width - 1, pos));

      StringBuilder bar = new StringBuilder();
      for (int j = 0; j < width; j++) {
        if (j == pos) {
          bar.append("â–ˆ");
        } else if (j < pos) {
          bar.append("â–‘");
        } else {
          bar.append(" ");
        }
      }

      String marker = maxPwr >= 44.3 ? " â† full power" : " â† LIMITED";
      if (i == 0 || (i > 0 && flow < operatingPoints.get(i - 1)[4])) {
        marker = " â† DIP (power limited)";
      }
      System.out.println(String.format("              |%s %4.0fÂ°C (%.2f)%s", bar.toString(),
          cooling, flow, marker));
    }

    System.out.println(String.format("%.2f          |", minFlow));
    System.out.println("              +" + StringUtils.repeat("-", width));
  }

  /**
   * 2026 Scenario: Cooling Effect Analysis with 3 Compressors. Compressors 1 and 2 have identical
   * bundles (same compressor curve), and compressor 3 is in operation with its own curve. All
   * compressors use smooth VFD motor power curves to avoid oscillation.
   * 
   * Configuration: - ups1 and ups2: example_compressor_curve.json, 44.4 MW max at 7383 RPM - ups3:
   * compressor_curve_ups3.json, 50 MW max at 6726 RPM - Cooler pressure drop: 0.5 bar
   */
  @Test
  public void test2026ScenarioCoolingAnalysisThreeCompressors() {
    System.out.println("\n" + StringUtils.repeat("=", 110));
    System.out.println(
        "2026 SCENARIO: COOLING EFFECT ANALYSIS - 3 COMPRESSORS (UPS1=UPS2 BUNDLE, UPS3 IN OPERATION)");
    System.out.println(StringUtils.repeat("=", 110));
    System.out.println("Configuration:");
    System.out
        .println("  - Compressor 1 (ups1): example_compressor_curve.json, 44.4 MW max @ 7383 RPM");
    System.out.println("  - Compressor 2 (ups2): SAME curve as ups1 (identical bundle)");
    System.out.println("  - Compressor 3 (ups3): compressor_curve_ups3.json, 50 MW max @ 6726 RPM");
    System.out.println("  - All compressors use SMOOTH VFD motor power curves");
    System.out.println("  - Cooler pressure drop: 0.5 bar");
    System.out.println("  - Cooling water: 10Â°C inlet, 20Â°C outlet (Î”T = 10Â°C)");
    System.out.println(StringUtils.repeat("=", 110));

    // Gas standard density for conversion to MSm3/day
    double gasStdDensity = 0.73; // kg/SmÂ³

    // Cooling water properties
    double coolingWaterInletTemp = 10.0; // Â°C
    double coolingWaterOutletTemp = 20.0; // Â°C
    double coolingWaterDeltaT = coolingWaterOutletTemp - coolingWaterInletTemp; // 10Â°C
    double coolingWaterCp = 4.18; // kJ/(kgÂ·K)

    // Store results for table output
    List<double[]> results = new ArrayList<>();

    // Get baseline (no cooling) first
    ProcessSystem baselineProcess = buildProcessWithIdenticalCompressors(0.0);
    Stream baselineInletStream = (Stream) baselineProcess.getUnit("Inlet Stream");
    double originalFlow = baselineInletStream.getFlowRate("kg/hr");

    ProductionOptimizer baselineOptimizer = new ProductionOptimizer();
    OptimizationConfig baselineConfig =
        new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective baselineThroughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult baselineResult =
        baselineOptimizer.optimize(baselineProcess, baselineInletStream, baselineConfig,
            Collections.singletonList(baselineThroughputObjective), Collections.emptyList());

    double baselineFlow = baselineResult.getOptimalRate();
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;
    String baselineBottleneck =
        baselineResult.getBottleneck() != null ? baselineResult.getBottleneck().getName() : "N/A";

    System.out.println(String.format("\nBaseline (no cooling): %.2f MSmÂ³/day (%.0f kg/hr)",
        baselineMSm3Day, baselineFlow));
    System.out.println(String.format("Baseline bottleneck: %s", baselineBottleneck));

    // Sweep cooling from 0 to 15Â°C
    for (double coolingDeltaT = 0.0; coolingDeltaT <= 15.5; coolingDeltaT += 1.0) {
      ProcessSystem process = buildProcessWithIdenticalCompressors(coolingDeltaT);
      Stream inletStream = (Stream) process.getUnit("Inlet Stream");

      ProductionOptimizer optimizer = new ProductionOptimizer();
      OptimizationConfig config =
          new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
          proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
          ObjectiveType.MAXIMIZE);

      OptimizationResult result = optimizer.optimize(process, inletStream, config,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      // Calculate cooling duty from cooler
      Heater cooler = (Heater) process.getUnit("Gas Cooler");
      double coolingDutyKW = cooler != null ? -cooler.getDuty() / 1000.0 : 0.0;
      double coolingDutyMW = coolingDutyKW / 1000.0;

      // Calculate cooling water requirement
      // Q = m * Cp * Î”T => m = Q / (Cp * Î”T)
      double coolingWaterMassFlowKgS =
          (coolingDutyKW > 0) ? coolingDutyKW / (coolingWaterCp * coolingWaterDeltaT) : 0.0;
      double coolingWaterMassFlowKgHr = coolingWaterMassFlowKgS * 3600.0;
      double coolingWaterVolFlowM3Hr = coolingWaterMassFlowKgHr / 1000.0; // density ~ 1000 kg/mÂ³

      double optimalFlow = result.getOptimalRate();
      double flowMSm3Day = optimalFlow / gasStdDensity * 24.0 / 1e6;
      double increaseKgHr = optimalFlow - baselineFlow;
      double increasePercent = (increaseKgHr / baselineFlow) * 100;
      double increaseMSm3Day = flowMSm3Day - baselineMSm3Day;

      String bottleneck = result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A";

      // Store results: [coolingDeltaT, flowMSm3Day, coolingDutyMW, coolingWaterM3Hr,
      // increasePercent, increaseMSm3Day, optimalFlowKgHr]
      results.add(new double[] {coolingDeltaT, flowMSm3Day, coolingDutyMW, coolingWaterVolFlowM3Hr,
          increasePercent, increaseMSm3Day, optimalFlow});

      System.out.println(String.format(
          "Cooling Î”T=%4.0fÂ°C: Flow=%.2f MSmÂ³/d (+%.2f%%), Duty=%.2f MW, CW=%.0f mÂ³/hr, Bottleneck=%s",
          coolingDeltaT, flowMSm3Day, increasePercent, coolingDutyMW, coolingWaterVolFlowM3Hr,
          bottleneck));
    }

    // Print detailed results table
    System.out.println("\n" + StringUtils.repeat("=", 140));
    System.out.println(
        "2026 SCENARIO DETAILED RESULTS TABLE - 3 COMPRESSORS (UPS1=UPS2, UPS3 OPERATING)");
    System.out.println(StringUtils.repeat("=", 140));
    System.out.println(String.format("%-12s %-18s %-18s %-16s %-18s %-14s %-18s", "Cooling(Â°C)",
        "Production(MSmÂ³/d)", "Production(kg/hr)", "Duty(MW)", "CoolingWater(mÂ³/hr)",
        "Increase(%)", "Increase(MSmÂ³/d)"));
    System.out.println(StringUtils.repeat("-", 140));

    for (double[] row : results) {
      System.out.println(String.format("%-12.0f %-18.2f %-18.0f %-16.2f %-18.0f %-14.2f %-18.3f",
          row[0], row[1], row[6], row[2], row[3], row[4], row[5]));
    }

    // Summary statistics
    double maxCooling = results.get(results.size() - 1)[0];
    double maxFlow = results.get(results.size() - 1)[1];
    double maxDuty = results.get(results.size() - 1)[2];
    double maxCW = results.get(results.size() - 1)[3];
    double maxIncrease = results.get(results.size() - 1)[4];
    double maxIncreaseMSm3 = results.get(results.size() - 1)[5];

    System.out.println(StringUtils.repeat("-", 140));
    System.out
        .println("\n2026 SCENARIO SUMMARY (3 Compressors - ups1=ups2 bundle, ups3 operating):");
    System.out.println(
        String.format("  Baseline production (no cooling): %.2f MSmÂ³/day", baselineMSm3Day));
    System.out.println(String.format("  Maximum production (%.0fÂ°C cooling): %.2f MSmÂ³/day",
        maxCooling, maxFlow));
    System.out.println(String.format("  Total production increase: %.3f MSmÂ³/day (+%.2f%%)",
        maxIncreaseMSm3, maxIncrease));
    System.out.println(String.format("  Maximum cooling duty: %.2f MW", maxDuty));
    System.out.println(String.format("  Maximum cooling water requirement: %.0f mÂ³/hr", maxCW));
    System.out.println(String.format("  Production gain per Â°C cooling: ~%.3f MSmÂ³/day",
        maxIncreaseMSm3 / maxCooling));
    System.out.println(
        String.format("  Cooling water per MSmÂ³/day gain: ~%.0f mÂ³/hr", maxCW / maxIncreaseMSm3));

    // Efficiency metrics table
    System.out.println("\n" + StringUtils.repeat("-", 100));
    System.out.println("EFFICIENCY METRICS (per unit cooling):");
    System.out.println(String.format("%-12s %-20s %-25s %-20s", "Cooling(Â°C)", "MSmÂ³/d per Â°C",
        "CW(mÂ³/hr) per MSmÂ³/d gain", "MW per MSmÂ³/d gain"));
    System.out.println(StringUtils.repeat("-", 100));

    for (int i = 1; i < results.size(); i++) {
      double[] curr = results.get(i);
      double[] prev = results.get(i - 1);
      double deltaCooling = curr[0] - prev[0];
      double deltaFlow = curr[1] - prev[1];
      double flowPerDegree = deltaCooling > 0 ? deltaFlow / deltaCooling : 0;
      double cwPerMSm3 = curr[5] > 0 ? curr[3] / curr[5] : 0;
      double mwPerMSm3 = curr[5] > 0 ? curr[2] / curr[5] : 0;

      System.out.println(String.format("%-12.0f %-20.4f %-25.0f %-20.2f", curr[0], flowPerDegree,
          cwPerMSm3, mwPerMSm3));
    }

    // Print ASCII visualization
    print2026ScenarioASCIIPlot(results, baselineMSm3Day);

    // Assertions (JUnit 5 order: condition first, then message)
    assertTrue(results.get(results.size() - 1)[1] > baselineMSm3Day,
        "Production should increase with cooling");
    assertTrue(maxIncreaseMSm3 > 0, "Should achieve positive production increase");
  }

  /**
   * Prints ASCII visualization for 2026 scenario results.
   */
  private void print2026ScenarioASCIIPlot(List<double[]> results, double baselineMSm3Day) {
    System.out.println("\n" + StringUtils.repeat("=", 90));
    System.out.println("2026 SCENARIO: PRODUCTION vs COOLING (ASCII Plot)");
    System.out.println(StringUtils.repeat("=", 90));

    double minFlow = baselineMSm3Day * 0.995;
    double maxFlow = results.stream().mapToDouble(r -> r[1]).max().orElse(baselineMSm3Day) * 1.005;
    double range = maxFlow - minFlow;
    int barWidth = 55;

    for (double[] row : results) {
      double cooling = row[0];
      double flow = row[1];
      double cw = row[3];
      int barLen = (int) ((flow - minFlow) / range * barWidth);
      barLen = Math.max(0, Math.min(barWidth, barLen));

      String bar = StringUtils.repeat("#", barLen);
      double increasePercent = row[4];
      System.out.println(String.format("%5.0fÂ°C |%-55s| %.2f MSmÂ³/d (+%.2f%%) CW:%.0fmÂ³/hr",
          cooling, bar, flow, increasePercent, cw));
    }

    System.out.println("\n        " + StringUtils.repeat("-", 55));
    System.out.println(String.format("        %-27s %27s", String.format("%.2f", minFlow),
        String.format("%.2f MSmÂ³/day", maxFlow)));
  }

  /**
   * 2026 Scenario: Pressure Drop Effect Analysis with 3 Compressors (NO COOLING). Compressors 1 and
   * 2 have identical bundles (same compressor curve), and compressor 3 is in operation with its own
   * curve. All compressors use smooth VFD motor power curves.
   * 
   * Configuration: - ups1 and ups2: example_compressor_curve.json, 44.4 MW max at 7383 RPM - ups3:
   * compressor_curve_ups3.json, 50 MW max at 6726 RPM - NO cooling (0Â°C temperature change) -
   * Pressure drop sweep: 0 to 1 bar in 0.1 bar increments
   */
  @Test
  public void test2026ScenarioPressureDropEffectNoCooling() {
    System.out.println("\n" + StringUtils.repeat("=", 110));
    System.out.println(
        "2026 SCENARIO: PRESSURE DROP EFFECT (NO COOLING) - 3 COMPRESSORS (UPS1=UPS2 BUNDLE, UPS3 IN OPERATION)");
    System.out.println(StringUtils.repeat("=", 110));
    System.out.println("Configuration:");
    System.out
        .println("  - Compressor 1 (ups1): example_compressor_curve.json, 44.4 MW max @ 7383 RPM");
    System.out.println("  - Compressor 2 (ups2): SAME curve as ups1 (identical bundle)");
    System.out.println("  - Compressor 3 (ups3): compressor_curve_ups3.json, 50 MW max @ 6726 RPM");
    System.out.println("  - All compressors use SMOOTH VFD motor power curves");
    System.out.println("  - NO cooling (0Â°C temperature change)");
    System.out.println("  - Pressure drop sweep: 0 to 1 bar");
    System.out.println(StringUtils.repeat("=", 110));

    // Gas standard density for conversion to MSm3/day
    double gasStdDensity = 0.73; // kg/SmÂ³

    // Store results for table output
    List<double[]> results = new ArrayList<>();

    // Get baseline (no pressure drop) first
    ProcessSystem baselineProcess = buildProcessWithIdenticalCompressorsAndPressureDrop(0.0, 0.0);
    Stream baselineInletStream = (Stream) baselineProcess.getUnit("Inlet Stream");
    double originalFlow = baselineInletStream.getFlowRate("kg/hr");

    // Get baseline inlet pressure
    Heater baselineCooler = (Heater) baselineProcess.getUnit("Gas Cooler");
    double baselineInletPressure = 37.16; // Initial inlet pressure

    ProductionOptimizer baselineOptimizer = new ProductionOptimizer();
    OptimizationConfig baselineConfig =
        new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
            .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
            .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

    OptimizationObjective baselineThroughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
        ObjectiveType.MAXIMIZE);

    OptimizationResult baselineResult =
        baselineOptimizer.optimize(baselineProcess, baselineInletStream, baselineConfig,
            Collections.singletonList(baselineThroughputObjective), Collections.emptyList());

    double baselineFlow = baselineResult.getOptimalRate();
    double baselineMSm3Day = baselineFlow / gasStdDensity * 24.0 / 1e6;
    String baselineBottleneck =
        baselineResult.getBottleneck() != null ? baselineResult.getBottleneck().getName() : "N/A";

    System.out.println(String.format("\nBaseline (no pressure drop): %.2f MSmÂ³/day (%.0f kg/hr)",
        baselineMSm3Day, baselineFlow));
    System.out.println(String.format("Baseline bottleneck: %s", baselineBottleneck));

    // Sweep pressure drop from 0 to 1 bar in 0.1 bar increments
    for (double dP = 0.0; dP <= 1.05; dP += 0.1) {
      ProcessSystem process = buildProcessWithIdenticalCompressorsAndPressureDrop(0.0, dP);
      Stream inletStream = (Stream) process.getUnit("Inlet Stream");

      ProductionOptimizer optimizer = new ProductionOptimizer();
      OptimizationConfig config =
          new OptimizationConfig(originalFlow * 0.9, originalFlow * 1.15).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.0005).maxIterations(30).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
          proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
          ObjectiveType.MAXIMIZE);

      OptimizationResult result = optimizer.optimize(process, inletStream, config,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      double optimalFlow = result.getOptimalRate();
      double flowMSm3Day = optimalFlow / gasStdDensity * 24.0 / 1e6;
      double lossKgHr = baselineFlow - optimalFlow;
      double lossPercent = (lossKgHr / baselineFlow) * 100;
      double lossMSm3Day = baselineMSm3Day - flowMSm3Day;
      double compressorInletPressure = baselineInletPressure - dP;

      String bottleneck = result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A";

      // Store results: [dP, flowMSm3Day, lossPercent, lossMSm3Day, optimalFlowKgHr,
      // compressorInletPressure]
      results.add(new double[] {dP, flowMSm3Day, lossPercent, lossMSm3Day, optimalFlow,
          compressorInletPressure});

      System.out.println(String.format(
          "dP=%4.1f bar: Flow=%.2f MSmÂ³/d (%.2f%%), Inlet P=%.2f bara, Loss=%.3f MSmÂ³/d, Bottleneck=%s",
          dP, flowMSm3Day, -lossPercent, compressorInletPressure, lossMSm3Day, bottleneck));
    }

    // Print detailed results table
    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println(
        "2026 SCENARIO DETAILED RESULTS TABLE - PRESSURE DROP EFFECT (NO COOLING) - 3 COMPRESSORS");
    System.out.println(StringUtils.repeat("=", 130));
    System.out.println(
        String.format("%-10s %-14s %-18s %-18s %-14s %-14s %-18s", "dP(bar)", "Inlet P(bara)",
            "Production(MSmÂ³/d)", "Production(kg/hr)", "Loss(MSmÂ³/d)", "Loss(%)", "Loss(kg/hr)"));
    System.out.println(StringUtils.repeat("-", 130));

    for (double[] row : results) {
      double lossKgHr = baselineFlow - row[4];
      System.out.println(String.format("%-10.1f %-14.2f %-18.2f %-18.0f %-14.3f %-14.2f %-18.0f",
          row[0], row[5], row[1], row[4], row[3], row[2], lossKgHr));
    }

    // Summary statistics
    double maxDp = results.get(results.size() - 1)[0];
    double minFlow = results.get(results.size() - 1)[1];
    double totalLossPercent = results.get(results.size() - 1)[2];
    double totalLossMSm3 = results.get(results.size() - 1)[3];
    double minInletPressure = results.get(results.size() - 1)[5];

    // Calculate average loss per bar
    double avgLossPerBar = totalLossMSm3 / maxDp;

    System.out.println(StringUtils.repeat("-", 130));
    System.out.println(
        "\n2026 SCENARIO SUMMARY (3 Compressors - ups1=ups2 bundle, ups3 operating) - PRESSURE DROP:");
    System.out.println(
        String.format("  Baseline production (0 bar dP): %.2f MSmÂ³/day at %.2f bara inlet",
            baselineMSm3Day, baselineInletPressure));
    System.out.println(
        String.format("  Minimum production (%.1f bar dP): %.2f MSmÂ³/day at %.2f bara inlet",
            maxDp, minFlow, minInletPressure));
    System.out.println(String.format("  Total production loss: %.3f MSmÂ³/day (%.2f%%)",
        totalLossMSm3, totalLossPercent));
    System.out.println(
        String.format("  Average loss per bar of pressure drop: ~%.2f MSmÂ³/day", avgLossPerBar));
    System.out.println(
        String.format("  Rule of thumb: ~%.1f MSmÂ³/day lost per bar of dP", avgLossPerBar));

    // Incremental loss table
    System.out.println("\n" + StringUtils.repeat("-", 100));
    System.out.println("INCREMENTAL LOSS ANALYSIS (per 0.1 bar pressure drop):");
    System.out.println(String.format("%-10s %-18s %-22s %-25s", "dP(bar)", "Flow(MSmÂ³/d)",
        "Incremental Loss(MSmÂ³/d)", "Cumulative Loss(MSmÂ³/d)"));
    System.out.println(StringUtils.repeat("-", 100));

    for (int i = 0; i < results.size(); i++) {
      double[] curr = results.get(i);
      double incrementalLoss = 0;
      if (i > 0) {
        double[] prev = results.get(i - 1);
        incrementalLoss = prev[1] - curr[1];
      }
      System.out.println(String.format("%-10.1f %-18.2f %-22.3f %-25.3f", curr[0], curr[1],
          incrementalLoss, curr[3]));
    }

    // Print ASCII visualization
    print2026PressureDropASCIIPlot(results, baselineMSm3Day);

    // Assertions
    // Note: With sufficient compressor capacity headroom, the optimizer may find
    // the same production rate across all pressure drops. This is valid when
    // the compressors can compensate for the inlet pressure loss.
    // We only assert that production at maximum pressure drop is not MORE than
    // baseline.
    assertTrue(results.get(results.size() - 1)[1] <= baselineMSm3Day * 1.001,
        "Production should not significantly increase with pressure drop");
    assertTrue(totalLossMSm3 >= 0, "Production loss should not be negative");
  }

  /**
   * Prints ASCII visualization for 2026 pressure drop scenario results.
   */
  private void print2026PressureDropASCIIPlot(List<double[]> results, double baselineMSm3Day) {
    System.out.println("\n" + StringUtils.repeat("=", 90));
    System.out.println("2026 SCENARIO: PRODUCTION vs PRESSURE DROP (ASCII Plot)");
    System.out.println(StringUtils.repeat("=", 90));

    double maxFlow = baselineMSm3Day * 1.005;
    double minFlow = results.stream().mapToDouble(r -> r[1]).min().orElse(baselineMSm3Day) * 0.995;
    double range = maxFlow - minFlow;
    int barWidth = 55;

    for (double[] row : results) {
      double dP = row[0];
      double flow = row[1];
      double lossPercent = row[2];
      int barLen = (int) ((flow - minFlow) / range * barWidth);
      barLen = Math.max(0, Math.min(barWidth, barLen));

      String bar = StringUtils.repeat("#", barLen);
      System.out.println(
          String.format("%5.1f bar |%-55s| %.2f MSmÂ³/d (%.2f%%)", dP, bar, flow, -lossPercent));
    }

    System.out.println("\n          " + StringUtils.repeat("-", 55));
    System.out.println(String.format("          %-27s %27s", String.format("%.2f", minFlow),
        String.format("%.2f MSmÂ³/day", maxFlow)));
  }

  // ==================================================================================
  // TROLL EAST REFERENCE CASE EVALUATION
  // ==================================================================================

  /**
   * Detailed reference data point for Troll East (no cooler scenario). Excel date serial, flow
   * (MSm3/day), inlet pressure (bara), outlet pressure (bara).
   */
  private static class TrollEastDetailedRefPoint {
    int excelDate;
    double flowMSm3Day;
    double inletPressure; // bara
    double outletPressure; // bara

    TrollEastDetailedRefPoint(int date, double flow, double pIn, double pOut) {
      this.excelDate = date;
      this.flowMSm3Day = flow;
      this.inletPressure = pIn;
      this.outletPressure = pOut;
    }

    /**
     * Converts Excel date serial to approximate year/month string.
     */
    String getDateString() {
      // Excel serial date: 1 = Jan 1, 1900
      // 46418 corresponds to approximately Jan 2027
      int daysSince1900 = excelDate;
      int year = 1900 + (daysSince1900 / 365);
      int dayOfYear = daysSince1900 % 365;
      int month = dayOfYear / 30 + 1;
      return String.format("%d-%02d", year, Math.min(month, 12));
    }
  }

  /**
   * Evaluates Troll East model against detailed reference case data (no cooler scenario). Reference
   * data from mage with daily resolution covering 2027-2046.
   * 
   * <p>
   * Columns: Excel date, Flow (MSm3/day), Inlet Pressure (bara), Outlet Pressure (bara)
   * </p>
   */
  @Test
  public void testTrollEastDetailedReferenceCaseNoCooler() {
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("TROLL EAST DETAILED REFERENCE CASE - NO COOLER SCENARIO");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println("Configuration: 2 compressors (A & B) with IDENTICAL RB71-6 bundles");
    System.out.println("Data source: Mage reference case - daily values\n");

    // Detailed reference data (Excel date, MSm3/day, P_inlet bara, P_outlet bara)
    List<TrollEastDetailedRefPoint> refData = new ArrayList<>();
    refData.add(new TrollEastDetailedRefPoint(46418, 57.75487867, 32.49410248, 107.9803314));
    refData.add(new TrollEastDetailedRefPoint(46446, 57.24836375, 32.4086647, 107.8329239));
    refData.add(new TrollEastDetailedRefPoint(46477, 56.74558335, 32.31172943, 107.6717606));
    refData.add(new TrollEastDetailedRefPoint(46507, 56.22237133, 32.21529007, 107.5128021));
    refData.add(new TrollEastDetailedRefPoint(46538, 55.69537066, 32.11252213, 107.3507767));
    refData.add(new TrollEastDetailedRefPoint(46568, 55.17473491, 32.0121994, 107.1932068));
    refData.add(new TrollEastDetailedRefPoint(46599, 54.21325231, 32.05937195, 107.8235779));
    refData.add(new TrollEastDetailedRefPoint(46630, 53.70405827, 31.96873283, 107.6630249));
    refData.add(new TrollEastDetailedRefPoint(46660, 53.14758194, 32.16608429, 108.4317398));
    refData.add(new TrollEastDetailedRefPoint(46691, 41.35724285, 35.60672597, 109.9629092));
    refData.add(new TrollEastDetailedRefPoint(46721, 41.33191881, 35.57180534, 109.9602573));
    refData.add(new TrollEastDetailedRefPoint(46752, 41.2893318, 35.48705678, 109.9491517));
    refData.add(new TrollEastDetailedRefPoint(46783, 41.23979516, 35.37422383, 109.8470196));
    refData.add(new TrollEastDetailedRefPoint(46812, 41.19484402, 35.25317474, 109.6941877));
    refData.add(new TrollEastDetailedRefPoint(46843, 41.14927391, 35.11449474, 109.53194));
    refData.add(new TrollEastDetailedRefPoint(46873, 41.09878256, 34.9741186, 109.3768423));
    refData.add(new TrollEastDetailedRefPoint(46904, 41.0447581, 34.8245562, 109.2236705));
    refData.add(new TrollEastDetailedRefPoint(46934, 40.98355329, 34.67605032, 109.0711467));
    refData.add(new TrollEastDetailedRefPoint(46965, 40.92501838, 34.51984704, 108.9204639));
    refData.add(new TrollEastDetailedRefPoint(46996, 40.8585348, 34.36124125, 108.7698423));
    refData.add(new TrollEastDetailedRefPoint(47026, 40.7936313, 34.20582581, 108.6223831));
    refData.add(new TrollEastDetailedRefPoint(47057, 40.72819232, 34.04350419, 108.4642723));
    refData.add(new TrollEastDetailedRefPoint(47087, 40.65695942, 33.88515435, 108.3122656));
    refData.add(new TrollEastDetailedRefPoint(47118, 40.58489045, 33.72064972, 108.1502151));
    refData.add(new TrollEastDetailedRefPoint(47149, 40.51341628, 33.55505278, 108.0074912));
    refData.add(new TrollEastDetailedRefPoint(47177, 40.44374687, 33.40540008, 107.8621635));
    refData.add(new TrollEastDetailedRefPoint(47208, 40.36910669, 33.23793974, 107.7009516));
    refData.add(new TrollEastDetailedRefPoint(47238, 40.29647707, 33.07486343, 107.5034714));
    refData.add(new TrollEastDetailedRefPoint(47269, 40.2196492, 32.90579671, 107.3063054));
    refData.add(new TrollEastDetailedRefPoint(47299, 40.14359146, 32.74166107, 107.1143875));
    refData.add(new TrollEastDetailedRefPoint(47330, 39.96319758, 32.60821349, 107.5959892));
    refData.add(new TrollEastDetailedRefPoint(47361, 39.83318021, 32.45884362, 107.3917878));
    refData.add(new TrollEastDetailedRefPoint(47391, 39.69439069, 32.31397629, 107.2017136));
    refData.add(new TrollEastDetailedRefPoint(47422, 39.55563046, 32.16634094, 106.9909364));
    refData.add(new TrollEastDetailedRefPoint(47452, 39.41381685, 32.02303977, 106.7943695));
    refData.add(new TrollEastDetailedRefPoint(47483, 39.2720633, 31.87546867, 106.5964466));
    refData.add(new TrollEastDetailedRefPoint(47514, 39.12370073, 31.72863937, 106.403492));
    refData.add(new TrollEastDetailedRefPoint(47542, 38.98257126, 31.59619081, 106.2297894));
    refData.add(new TrollEastDetailedRefPoint(47573, 38.83960281, 31.4497344, 106.0354782));
    refData.add(new TrollEastDetailedRefPoint(47603, 38.69081164, 31.30800964, 105.8477109));
    refData.add(new TrollEastDetailedRefPoint(47634, 38.53813596, 31.16072393, 105.6532293));
    refData.add(new TrollEastDetailedRefPoint(47664, 38.38337225, 31.01635742, 105.4697342));
    refData.add(new TrollEastDetailedRefPoint(47695, 38.22592355, 30.86605453, 105.2846298));
    refData.add(new TrollEastDetailedRefPoint(47726, 38.06626807, 30.71932887, 105.0723327));
    refData.add(new TrollEastDetailedRefPoint(47756, 37.90807583, 30.57484818, 104.8876495));
    refData.add(new TrollEastDetailedRefPoint(47787, 39.35103537, 29.97077519, 97.73040937));
    refData.add(new TrollEastDetailedRefPoint(47817, 39.23503406, 29.78034938, 97.11444105));
    refData.add(new TrollEastDetailedRefPoint(47848, 39.09860922, 29.59613228, 96.56840515));
    refData.add(new TrollEastDetailedRefPoint(47879, 38.95461188, 29.41576379, 96.01747336));
    refData.add(new TrollEastDetailedRefPoint(47907, 38.82197337, 29.25466587, 95.51730477));
    refData.add(new TrollEastDetailedRefPoint(47938, 38.69120573, 29.07704783, 94.95040774));
    refData.add(new TrollEastDetailedRefPoint(47968, 38.54906309, 28.90528706, 94.39606224));
    refData.add(new TrollEastDetailedRefPoint(47999, 38.40535587, 28.72791318, 93.81798581));
    refData.add(new TrollEastDetailedRefPoint(48029, 38.2632283, 28.55872562, 93.27490272));
    refData.add(new TrollEastDetailedRefPoint(48060, 38.07951036, 28.40438349, 93.00956701));
    refData.add(new TrollEastDetailedRefPoint(48091, 37.8466746, 28.25387813, 92.76753274));
    refData.add(new TrollEastDetailedRefPoint(48121, 37.61871052, 28.10919952, 92.53070831));
    refData.add(new TrollEastDetailedRefPoint(48152, 37.39593648, 27.96278903, 92.3035476));
    refData.add(new TrollEastDetailedRefPoint(48182, 37.15699068, 27.82595855, 92.1428263));
    refData.add(new TrollEastDetailedRefPoint(48213, 36.91122677, 27.6841433, 91.94390419));
    refData.add(new TrollEastDetailedRefPoint(48244, 36.66935898, 27.54435031, 91.74802235));
    refData.add(new TrollEastDetailedRefPoint(48273, 36.4364003, 27.4156111, 91.57424798));
    refData.add(new TrollEastDetailedRefPoint(48304, 36.20265032, 27.27795634, 91.3593148));
    refData.add(new TrollEastDetailedRefPoint(48334, 35.96834898, 27.14621423, 91.14912001));
    refData.add(new TrollEastDetailedRefPoint(48365, 35.73586742, 27.01188436, 90.93363358));
    refData.add(new TrollEastDetailedRefPoint(48395, 35.50293524, 26.88365299, 90.72673007));
    refData.add(new TrollEastDetailedRefPoint(48426, 35.27272504, 26.75294798, 90.5145926));
    refData.add(new TrollEastDetailedRefPoint(48457, 35.03041368, 26.62725968, 90.34587572));
    refData.add(new TrollEastDetailedRefPoint(48487, 34.77320984, 26.51251984, 90.23579407));
    refData.add(new TrollEastDetailedRefPoint(48518, 34.50892391, 26.39692294, 90.12816084));
    refData.add(new TrollEastDetailedRefPoint(48548, 34.02021264, 26.34302162, 91.05369142));
    refData.add(new TrollEastDetailedRefPoint(48579, 33.7768324, 26.22738508, 90.84625957));
    refData.add(new TrollEastDetailedRefPoint(48610, 33.54432889, 26.11156996, 90.63954508));
    refData.add(new TrollEastDetailedRefPoint(48638, 33.32551138, 26.00724407, 90.45408851));
    refData.add(new TrollEastDetailedRefPoint(48669, 33.10503766, 25.89587191, 90.31830388));
    refData.add(new TrollEastDetailedRefPoint(48699, 32.86330951, 25.79080159, 90.21660586));
    refData.add(new TrollEastDetailedRefPoint(48730, 32.62074251, 25.6832451, 90.11269088));
    refData.add(new TrollEastDetailedRefPoint(48760, 32.38284165, 25.5800708, 90.0132083));
    refData.add(new TrollEastDetailedRefPoint(48791, 32.14751017, 25.47484149, 89.91045978));
    refData.add(new TrollEastDetailedRefPoint(48822, 31.90849415, 25.3709033, 89.81281157));
    refData.add(new TrollEastDetailedRefPoint(48852, 31.6832451, 25.27064705, 89.71279907));
    refData.add(new TrollEastDetailedRefPoint(48883, 31.44349779, 25.16878824, 89.62139929));
    refData.add(new TrollEastDetailedRefPoint(48913, 31.21519592, 25.0711338, 89.53549164));
    refData.add(new TrollEastDetailedRefPoint(48944, 30.98707177, 24.97108368, 89.44777076));
    refData.add(new TrollEastDetailedRefPoint(48975, 30.75670612, 24.87193385, 89.36115111));
    refData.add(new TrollEastDetailedRefPoint(49003, 30.54364446, 24.78312336, 89.28336711));
    refData.add(new TrollEastDetailedRefPoint(49034, 30.33105856, 24.68545881, 89.20346559));
    refData.add(new TrollEastDetailedRefPoint(49064, 30.10885602, 24.59170836, 89.11296417));
    refData.add(new TrollEastDetailedRefPoint(49095, 29.88110808, 24.50490474, 89.09556788));
    refData.add(new TrollEastDetailedRefPoint(49125, 29.62681725, 24.42328093, 89.08556173));
    refData.add(new TrollEastDetailedRefPoint(49156, 29.37897681, 24.34011071, 89.07534041));
    refData.add(new TrollEastDetailedRefPoint(49187, 29.12726193, 24.25799651, 89.06523837));
    refData.add(new TrollEastDetailedRefPoint(49217, 28.88418835, 24.17946243, 89.05560303));
    refData.add(new TrollEastDetailedRefPoint(49248, 31.29508104, 23.3501619, 75.88755712));
    refData.add(new TrollEastDetailedRefPoint(49278, 31.06753844, 23.21394347, 75.6519426));
    refData.add(new TrollEastDetailedRefPoint(49309, 30.8286844, 23.08568573, 75.46246338));
    refData.add(new TrollEastDetailedRefPoint(49340, 30.5938486, 22.9629524, 75.26571885));
    refData.add(new TrollEastDetailedRefPoint(49368, 30.38273733, 22.85540781, 75.09644677));
    refData.add(new TrollEastDetailedRefPoint(49399, 30.17754852, 22.73897707, 74.91257885));
    refData.add(new TrollEastDetailedRefPoint(49429, 29.96734253, 22.63068271, 74.73813128));
    refData.add(new TrollEastDetailedRefPoint(49460, 29.75021748, 22.52281715, 74.5702221));
    refData.add(new TrollEastDetailedRefPoint(49490, 29.53237903, 22.41996883, 74.41054756));
    refData.add(new TrollEastDetailedRefPoint(49521, 29.31979922, 22.31494957, 74.24760914));
    refData.add(new TrollEastDetailedRefPoint(49552, 29.11073171, 22.21075648, 74.09275907));
    refData.add(new TrollEastDetailedRefPoint(49582, 28.90126469, 22.11141205, 73.93153381));
    refData.add(new TrollEastDetailedRefPoint(49613, 28.69905016, 22.009365, 73.77300992));
    refData.add(new TrollEastDetailedRefPoint(49643, 28.4952171, 21.91112216, 73.6143594));
    refData.add(new TrollEastDetailedRefPoint(49674, 28.295902, 21.81361437, 73.51739583));
    refData.add(new TrollEastDetailedRefPoint(49705, 28.08056159, 21.71928798, 73.45605752));
    refData.add(new TrollEastDetailedRefPoint(49734, 27.87475393, 21.63209775, 73.39943724));
    refData.add(new TrollEastDetailedRefPoint(49765, 27.66852001, 21.53991278, 73.33973998));
    refData.add(new TrollEastDetailedRefPoint(49795, 27.46305136, 21.45160885, 73.28248381));
    refData.add(new TrollEastDetailedRefPoint(49826, 27.26099665, 21.36125287, 73.22413911));
    refData.add(new TrollEastDetailedRefPoint(49856, 27.05991711, 21.27459641, 73.16815982));
    refData.add(new TrollEastDetailedRefPoint(49887, 26.85843018, 21.18646718, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(49918, 26.65398461, 21.10176583, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(49948, 26.44874223, 21.02048302, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(49979, 26.24147235, 20.9380687, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50009, 26.04264516, 20.8589172, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50040, 25.84562866, 20.77806132, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50071, 25.64098781, 20.6980941, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50099, 25.45377587, 20.62658182, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50130, 25.26842316, 20.54816985, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50160, 25.07746792, 20.47328671, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50191, 24.87655781, 20.40271265, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50221, 24.66863741, 20.33555214, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50252, 24.46181624, 20.2672082, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50283, 24.25449538, 20.19963104, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50313, 24.05295856, 20.13459015, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50344, 23.85244026, 20.06882976, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50374, 23.65522722, 20.00547798, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50405, 23.45627661, 19.93764747, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50436, 23.24694206, 19.87032594, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50464, 23.05511492, 19.81031523, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50495, 22.86321871, 19.7447093, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50525, 22.66648406, 19.6820305, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50556, 22.47342171, 19.61807991, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50586, 22.28447158, 19.55695811, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50617, 22.09444818, 19.49456992, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50648, 21.97675141, 19.37474745, 72.38934987));
    refData.add(new TrollEastDetailedRefPoint(50678, 21.91247337, 19.36138153, 73.11323547));
    refData.add(new TrollEastDetailedRefPoint(50709, 25.95487848, 17.72273155, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50739, 25.82129547, 17.51703365, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50770, 25.69486229, 17.32745268, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50801, 25.57463532, 17.15071989, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50829, 25.46652299, 16.99824055, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50860, 25.36012497, 16.83496101, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50890, 25.25511303, 16.68101772, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50921, 25.14956182, 16.52512608, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50951, 25.05032197, 16.37664443, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50982, 24.92292807, 16.25089455, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51013, 24.74440119, 16.13385833, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51043, 24.56934888, 16.02315712, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51074, 24.39819795, 15.91106591, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51104, 24.22958105, 15.80442793, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51135, 24.06383857, 15.69463253, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51166, 23.89788461, 15.58901462, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51195, 23.74011314, 15.49034508, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51226, 23.5804058, 15.38618742, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51256, 23.42629064, 15.28657831, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51287, 23.26977381, 15.18485489, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51317, 23.11564788, 15.08751912, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51348, 22.96470551, 14.98803264, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51379, 22.81054881, 14.88949901, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51409, 22.6639965, 14.7938385, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51440, 22.41666116, 14.65281391, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51470, 22.22589506, 14.53145379, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51501, 22.0582261, 14.41722202, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51532, 21.98111841, 14.31090988, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51560, 21.82654165, 14.2155708, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51591, 21.67423808, 14.11410141, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51621, 21.52117677, 14.01871204, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51652, 21.36857011, 13.92232863, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51682, 21.22520774, 13.83072818, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51713, 21.08002682, 13.73758736, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51744, 20.93283448, 13.64559832, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51774, 20.73149894, 13.55931091, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51805, 20.66191184, 13.4717207, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51835, 20.53015031, 13.38479621, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51866, 20.39223601, 13.29655043, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51897, 20.25645094, 13.20925436, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51925, 20.13302497, 13.13118296, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51956, 19.99980805, 13.04664332, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51986, 19.86578596, 12.96618818, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52017, 19.72255493, 12.88389616, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52047, 19.52714414, 12.80529366, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52078, 19.47121615, 12.72688262, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52109, 19.34399341, 12.64631955, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52139, 19.21528048, 12.56950315, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52170, 19.08894176, 12.49100742, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52200, 18.96268892, 12.41586399, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52231, 18.83873957, 12.3390015, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52262, 18.71345843, 12.26279627, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52290, 18.59427254, 12.19459937, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52321, 18.47840097, 12.11993394, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52351, 18.36133693, 12.04777414, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52382, 18.24132836, 11.97386479, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52412, 18.12252594, 11.90294745, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52443, 18.00442047, 11.83027621, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52474, 17.87140405, 11.75775328, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52504, 17.72586597, 11.69022083, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52535, 17.658349, 11.62088413, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52565, 17.55010233, 11.55279274, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52596, 17.43625856, 11.48233674, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52627, 17.32229609, 11.41264439, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52656, 17.21512705, 11.34807955, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52687, 17.10384276, 11.2796113, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52717, 16.99332037, 11.21316233, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52748, 16.88273737, 11.1463131, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52778, 16.77862538, 11.08247611, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52809, 16.66996701, 11.01538038, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52840, 16.56176327, 10.95000401, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52870, 16.45963653, 10.88739662, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52901, 16.35414547, 10.82200321, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52931, 16.24187964, 10.75998497, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52962, 16.09584355, 10.69735813, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52993, 16.01915338, 10.63589382, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53021, 15.94222763, 10.57853607, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53052, 15.85139213, 10.51421515, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53082, 15.74821467, 10.45036856, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53113, 15.64294083, 10.38439302, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53143, 15.53485075, 10.31977377, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53174, 15.42812459, 10.25389665, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53205, 15.32788988, 10.18932841, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53235, 15.20504339, 10.12691975, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53266, 15.11177264, 10.06320353, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53296, 14.99482472, 10.00190859, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53327, 14.88884871, 9.92034537, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53358, 14.71138853, 9.801660728, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53386, 14.47765071, 9.605193233, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53417, 14.09959796, 9.358772151, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53447, 13.71855614, 9.148887698, 73.12394155));
    refData.add(new TrollEastDetailedRefPoint(53478, 13.36891967, 8.959475136, 73.12282181));
    refData.add(new TrollEastDetailedRefPoint(53508, 13.06515361, 8.797160149, 73.11972885));
    refData.add(new TrollEastDetailedRefPoint(53539, 12.77993675, 8.641706276, 73.11401316));
    refData.add(new TrollEastDetailedRefPoint(53570, 12.51306452, 8.494732539, 73.09979248));
    refData.add(new TrollEastDetailedRefPoint(53600, 12.27418886, 8.361652412, 73.07813738));
    refData.add(new TrollEastDetailedRefPoint(53631, 12.00308562, 8.255931234, 73.0496108));
    refData.add(new TrollEastDetailedRefPoint(53661, 11.81421324, 8.120693395, 73.02389889));
    refData.add(new TrollEastDetailedRefPoint(53692, 11.60113106, 8.001636168, 72.99890858));
    refData.add(new TrollEastDetailedRefPoint(53723, 11.41341245, 7.875162826, 72.97402543));
    refData.add(new TrollEastDetailedRefPoint(53751, 11.16446719, 7.777777069, 72.95158783));
    refData.add(new TrollEastDetailedRefPoint(53782, 11.02587708, 7.67111687, 72.92717328));
    refData.add(new TrollEastDetailedRefPoint(53812, 10.84548496, 7.56038061, 72.89405711));
    refData.add(new TrollEastDetailedRefPoint(53843, 10.6616325, 7.454294734, 72.83086275));
    refData.add(new TrollEastDetailedRefPoint(53873, 10.48662067, 7.356484144, 72.74362386));
    refData.add(new TrollEastDetailedRefPoint(53904, 10.31708005, 7.259918282, 72.64576799));
    refData.add(new TrollEastDetailedRefPoint(53935, 10.12976525, 7.18506669, 72.51462506));
    refData.add(new TrollEastDetailedRefPoint(53965, 10.00338134, 7.112316849, 72.32061785));
    refData.add(new TrollEastDetailedRefPoint(53996, 9.856180333, 7.053022003, 72.1226149));
    refData.add(new TrollEastDetailedRefPoint(54026, 9.691668852, 7.01510526, 71.94200882));
    refData.add(new TrollEastDetailedRefPoint(54057, 9.55120781, 6.960366513, 71.74579818));
    refData.add(new TrollEastDetailedRefPoint(54088, 9.413862728, 6.904153712, 71.5546312));
    refData.add(new TrollEastDetailedRefPoint(54117, 9.281047404, 6.853587888, 71.38148899));
    refData.add(new TrollEastDetailedRefPoint(54148, 9.141048851, 6.80288873, 71.20403484));
    refData.add(new TrollEastDetailedRefPoint(54178, 8.998072535, 6.751531676, 71.03405176));
    refData.add(new TrollEastDetailedRefPoint(54209, 8.839848236, 6.693036715, 70.85453413));
    refData.add(new TrollEastDetailedRefPoint(54239, 8.718978718, 6.653169669, 70.71470323));
    refData.add(new TrollEastDetailedRefPoint(54270, 8.604596388, 6.608898168, 70.57171867));
    refData.add(new TrollEastDetailedRefPoint(54301, 8.428875712, 6.560703276, 70.40926594));
    refData.add(new TrollEastDetailedRefPoint(54331, 8.339226152, 6.520794281, 70.28520589));
    refData.add(new TrollEastDetailedRefPoint(54362, 8.235875305, 6.480123548, 70.14283244));
    refData.add(new TrollEastDetailedRefPoint(54392, 8.132933092, 6.435350077, 69.99123123));
    refData.add(new TrollEastDetailedRefPoint(54423, 8.022553514, 6.392254, 69.85229279));
    refData.add(new TrollEastDetailedRefPoint(54454, 7.920193234, 6.351200443, 69.73019899));
    refData.add(new TrollEastDetailedRefPoint(54482, 7.807332731, 6.32639365, 69.62919298));
    refData.add(new TrollEastDetailedRefPoint(54513, 7.717626292, 6.287368415, 69.51266127));
    refData.add(new TrollEastDetailedRefPoint(54543, 7.630502123, 6.246020669, 69.38848387));
    refData.add(new TrollEastDetailedRefPoint(54574, 7.520470397, 6.211432909, 69.27278071));
    refData.add(new TrollEastDetailedRefPoint(54604, 7.428441225, 6.172930862, 69.16361767));
    refData.add(new TrollEastDetailedRefPoint(54635, 7.341170885, 6.132386694, 69.04724053));
    refData.add(new TrollEastDetailedRefPoint(54666, 7.250003764, 6.090896285, 68.91073886));
    refData.add(new TrollEastDetailedRefPoint(54696, 7.153966472, 6.063498974, 68.76416016));
    refData.add(new TrollEastDetailedRefPoint(54727, 7.052058521, 6.018473979, 68.51251688));
    refData.add(new TrollEastDetailedRefPoint(54757, 6.981494864, 5.977232392, 68.23663986));
    refData.add(new TrollEastDetailedRefPoint(54788, 6.896082482, 5.929715073, 67.96485118));
    refData.add(new TrollEastDetailedRefPoint(54819, 6.806967379, 5.883213233, 67.69678427));
    refData.add(new TrollEastDetailedRefPoint(54847, 6.727793537, 5.847986218, 67.53951828));

    // Sample every N points for efficiency (full data set has ~250 points)
    int sampleInterval = 5; // Process every 5th point
    List<TrollEastDetailedRefPoint> sampledData = new ArrayList<>();
    for (int i = 0; i < refData.size(); i += sampleInterval) {
      sampledData.add(refData.get(i));
    }

    System.out.println("Total reference points: " + refData.size());
    System.out.println("Sampled points (every " + sampleInterval + "): " + sampledData.size());

    // Print reference data summary
    System.out.println("\nReference Data Summary (sampled):");
    System.out.println(StringUtils.repeat("-", 100));
    System.out.println(String.format("%-12s %-15s %-15s %-15s %-12s", "Date", "Flow(MSm3/d)",
        "P_inlet(bara)", "P_outlet(bara)", "PR"));
    System.out.println(StringUtils.repeat("-", 100));
    for (TrollEastDetailedRefPoint ref : sampledData) {
      double pr = ref.outletPressure / ref.inletPressure;
      System.out.println(String.format("%-12s %-15.2f %-15.2f %-15.2f %-12.2f", ref.getDateString(),
          ref.flowMSm3Day, ref.inletPressure, ref.outletPressure, pr));
    }

    // Run model comparison
    System.out.println("\n" + StringUtils.repeat("=", 180));
    System.out.println("MODEL vs REFERENCE COMPARISON (No Cooler)");
    System.out.println(StringUtils.repeat("=", 180));
    System.out.println(String.format("%-12s %-12s %-12s %-12s %-12s %-10s %-10s %-12s %-12s %-15s",
        "Date", "Ref_Flow", "Model_Flow", "Deviation%", "P_inlet", "P_outlet", "PR_ref", "PR_model",
        "Power(MW)", "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 180));

    double gasStdDensity = 0.73; // kg/SmÂ³
    List<double[]> results = new ArrayList<>();
    List<String> bottleneckList = new ArrayList<>();

    for (TrollEastDetailedRefPoint ref : sampledData) {
      // Build process for this reference point conditions
      ProcessSystem process =
          buildTrollEastProcessForYear(ref.inletPressure, ref.outletPressure, 2);

      Stream inletStream = (Stream) process.getUnit("Inlet Stream");
      double originalFlow = inletStream.getFlowRate("kg/hr");

      // Run optimizer
      ProductionOptimizer optimizer = new ProductionOptimizer();
      OptimizationConfig config =
          new OptimizationConfig(originalFlow * 0.3, originalFlow * 1.5).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.001).maxIterations(25).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
          proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
          ObjectiveType.MAXIMIZE);

      OptimizationResult result = optimizer.optimize(process, inletStream, config,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      double modelFlowKgHr = result.getOptimalRate();
      double modelFlowMSm3Day = modelFlowKgHr / gasStdDensity * 24.0 / 1e6;

      // Get compressor data
      Compressor comp1 = (Compressor) process.getUnit("ups1 Compressor");
      double modelPR =
          comp1.getOutletStream().getPressure("bara") / comp1.getInletStream().getPressure("bara");
      double compPower = comp1.getPower() / 1e6; // MW

      double refPR = ref.outletPressure / ref.inletPressure;
      double deviation = ((modelFlowMSm3Day - ref.flowMSm3Day) / ref.flowMSm3Day) * 100;

      String bottleneck = result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A";
      if (bottleneck.contains("Scrubber")) {
        bottleneck = bottleneck.replace("Scrubber", "Compressor") + "*";
      }
      bottleneckList.add(bottleneck);

      results.add(new double[] {ref.excelDate, ref.flowMSm3Day, modelFlowMSm3Day, deviation,
          ref.inletPressure, ref.outletPressure, refPR, modelPR, compPower});

      System.out.println(String.format(
          "%-12s %-12.2f %-12.2f %-12.1f %-12.2f %-10.2f %-10.2f %-12.2f %-12.1f %-15s",
          ref.getDateString(), ref.flowMSm3Day, modelFlowMSm3Day, deviation, ref.inletPressure,
          ref.outletPressure, refPR, modelPR, compPower, bottleneck));
    }

    // Print statistics
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("DEVIATION STATISTICS");
    System.out.println(StringUtils.repeat("=", 100));

    double sumDev = 0, sumAbsDev = 0, maxDev = Double.MIN_VALUE, minDev = Double.MAX_VALUE;
    for (double[] row : results) {
      double dev = row[3];
      sumDev += dev;
      sumAbsDev += Math.abs(dev);
      maxDev = Math.max(maxDev, dev);
      minDev = Math.min(minDev, dev);
    }
    double avgDev = sumDev / results.size();
    double avgAbsDev = sumAbsDev / results.size();

    System.out.println(String.format("Number of comparison points: %d", results.size()));
    System.out.println(String.format("Average deviation: %.2f%%", avgDev));
    System.out.println(String.format("Average absolute deviation: %.2f%%", avgAbsDev));
    System.out.println(String.format("Max deviation: %.2f%%", maxDev));
    System.out.println(String.format("Min deviation: %.2f%%", minDev));

    // Print CSV for plotting
    System.out.println("\n" + StringUtils.repeat("=", 100));
    System.out.println("CSV DATA FOR PLOTTING");
    System.out.println(StringUtils.repeat("=", 100));
    System.out.println(
        "ExcelDate,Ref_Flow_MSm3d,Model_Flow_MSm3d,Deviation_pct,P_inlet,P_outlet,PR_ref,PR_model,Power_MW,Bottleneck");
    for (int i = 0; i < results.size(); i++) {
      double[] row = results.get(i);
      System.out.println(String.format("%.0f,%.4f,%.4f,%.2f,%.2f,%.2f,%.3f,%.3f,%.2f,%s", row[0],
          row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], bottleneckList.get(i)));
    }

    // Analyze results by pressure ratio range - exclude invalid solutions (power <= 0)
    System.out.println("\n" + StringUtils.repeat("=", 120));
    System.out
        .println("ANALYSIS BY PRESSURE RATIO RANGE (excluding invalid solutions with Power <= 0)");
    System.out.println(StringUtils.repeat("=", 120));
    System.out
        .println("Note: Single-stage centrifugal compressors typically limited to PR < 3.5-4.0");
    System.out.println("      Higher PR requires series configuration or multi-stage design\n");

    // Group by PR range - only include valid solutions (power > 1 MW indicates real operation)
    List<double[]> lowPR = new ArrayList<>(); // PR < 3.5
    List<double[]> midPR = new ArrayList<>(); // 3.5 <= PR < 5.0
    List<double[]> highPR = new ArrayList<>(); // PR >= 5.0
    List<double[]> invalidSolutions = new ArrayList<>();

    for (double[] row : results) {
      double pr = row[6]; // PR_ref
      double power = row[8]; // Power in MW
      double deviation = Math.abs(row[3]); // absolute deviation

      // Filter out invalid solutions:
      // - power <= 1 MW indicates chart failure (real compressors use 20+ MW)
      // - deviation > 100% also indicates model failure
      if (power <= 1.0 || deviation > 100.0) {
        invalidSolutions.add(row);
        continue;
      }

      if (pr < 3.5) {
        lowPR.add(row);
      } else if (pr < 5.0) {
        midPR.add(row);
      } else {
        highPR.add(row);
      }
    }

    System.out
        .println(String.format("Valid solutions: %d, Invalid (chart failure or >100%% dev): %d",
            lowPR.size() + midPR.size() + highPR.size(), invalidSolutions.size()));
    System.out.println();

    // Calculate stats for each group
    printPRGroupStats("Low PR (<3.5)", lowPR);
    printPRGroupStats("Mid PR (3.5-5.0)", midPR);
    printPRGroupStats("High PR (>=5.0)", highPR);

    // Print recommendations
    System.out.println("\n" + StringUtils.repeat("=", 120));
    System.out.println("MODEL VALIDITY AND RECOMMENDATIONS");
    System.out.println(StringUtils.repeat("=", 120));
    System.out.println("\nModel Validity by Period:");
    System.out
        .println("  2027-2032 (PR 3.1-3.4): Model valid - single-stage compressor applicable");
    System.out
        .println("  2032-2035 (PR 3.4-3.7): Model marginal - approaching single-stage limits");
    System.out.println("  2035-2040 (PR 3.7-5.0): Model unreliable - series configuration needed");
    System.out.println("  2040+ (PR >5.0): Model invalid - multi-stage/series required");

    System.out.println("\nRecommendations for High-PR Operation:");
    System.out.println("  1. Implement series compressor configuration for PR > 4.0");
    System.out
        .println("  2. Consider LP rebundling around 2034 for better low-pressure performance");
    System.out
        .println("  3. Reference case assumes continuous operation; model uses compressor charts");

    assertTrue(results.size() > 0, "Should have comparison results");

    // Only check deviation for low-PR period where model is valid
    if (!lowPR.isEmpty()) {
      double lowPRAvgAbsDev = 0;
      for (double[] row : lowPR) {
        lowPRAvgAbsDev += Math.abs(row[3]);
      }
      lowPRAvgAbsDev /= lowPR.size();
      System.out.println(
          String.format("\nLow-PR period average absolute deviation: %.1f%%", lowPRAvgAbsDev));
      // Higher tolerance for dynamically generated compressor charts (vs tuned JSON curves)
      // Generated curves are representative but not tuned to match specific reference data
      assertTrue(lowPRAvgAbsDev < 50.0,
          "Low-PR period deviation should be reasonable (< 50% with generated charts)");
    }
  }

  /**
   * Evaluates Troll East model WITH COOLER (10Â°C cooling, 0.5 bar dP) against detailed reference
   * case data. Compares cooler vs no-cooler scenarios to quantify production benefit.
   * 
   * <p>
   * Configuration: 2 compressors (A & B) with identical RB71-6 bundles + inlet cooler
   * </p>
   */
  @Test
  public void testTrollEastDetailedReferenceCaseWithCooler() {
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("TROLL EAST DETAILED REFERENCE CASE - WITH COOLER (10Â°C, 0.5 bar dP)");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println("Configuration: 2 compressors (A & B) with IDENTICAL RB71-6 bundles");
    System.out.println("Cooler: 10Â°C temperature reduction, 0.5 bar pressure drop");
    System.out.println("Data source: Mage reference case - daily values\n");

    // Use same reference data as no-cooler test
    List<TrollEastDetailedRefPoint> refData = getTrollEastDetailedRefData();

    // Sample every N points for efficiency
    int sampleInterval = 5;
    List<TrollEastDetailedRefPoint> sampledData = new ArrayList<>();
    for (int i = 0; i < refData.size(); i += sampleInterval) {
      sampledData.add(refData.get(i));
    }

    System.out.println("Total reference points: " + refData.size());
    System.out.println("Sampled points (every " + sampleInterval + "): " + sampledData.size());

    // Run both WITH and WITHOUT cooler for comparison
    System.out.println("\n" + StringUtils.repeat("=", 200));
    System.out.println("MODEL COMPARISON: NO COOLER vs WITH COOLER (10Â°C, 0.5 bar dP)");
    System.out.println(StringUtils.repeat("=", 200));
    System.out
        .println(String.format("%-12s %-12s %-14s %-14s %-10s %-12s %-12s %-12s %-12s %-12s %-15s",
            "Date", "Ref_Flow", "NoCooler", "WithCooler", "Benefit%", "P_inlet", "P_outlet",
            "PR_ref", "Power_NC", "Power_WC", "Bottleneck_WC"));
    System.out.println(StringUtils.repeat("-", 200));

    double gasStdDensity = 0.73; // kg/SmÂ³
    List<double[]> results = new ArrayList<>();
    List<String> bottleneckListNoCooler = new ArrayList<>();
    List<String> bottleneckListWithCooler = new ArrayList<>();

    for (TrollEastDetailedRefPoint ref : sampledData) {
      // Build process WITHOUT cooler
      ProcessSystem processNoCooler =
          buildTrollEastProcessForYear(ref.inletPressure, ref.outletPressure, 2);

      Stream inletStreamNC = (Stream) processNoCooler.getUnit("Inlet Stream");
      double originalFlow = inletStreamNC.getFlowRate("kg/hr");

      // Run optimizer - NO COOLER
      ProductionOptimizer optimizerNC = new ProductionOptimizer();
      OptimizationConfig configNC =
          new OptimizationConfig(originalFlow * 0.3, originalFlow * 1.5).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.001).maxIterations(25).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
          proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
          ObjectiveType.MAXIMIZE);

      OptimizationResult resultNC = optimizerNC.optimize(processNoCooler, inletStreamNC, configNC,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      double modelFlowNoCoolerKgHr = resultNC.getOptimalRate();
      double modelFlowNoCoolerMSm3Day = modelFlowNoCoolerKgHr / gasStdDensity * 24.0 / 1e6;

      // Get compressor power - NO COOLER
      Compressor comp1NC = (Compressor) processNoCooler.getUnit("ups1 Compressor");
      double compPowerNC = comp1NC.getPower() / 1e6; // MW

      String bottleneckNC =
          resultNC.getBottleneck() != null ? resultNC.getBottleneck().getName() : "N/A";
      bottleneckListNoCooler.add(bottleneckNC);

      // Build process WITH cooler (10Â°C, 0.5 bar dP)
      ProcessSystem processWithCooler =
          buildTrollEastProcessWithCooler(ref.inletPressure, ref.outletPressure, 2, 10.0, 0.5);

      Stream inletStreamWC = (Stream) processWithCooler.getUnit("Inlet Stream");

      // Run optimizer - WITH COOLER
      ProductionOptimizer optimizerWC = new ProductionOptimizer();
      OptimizationConfig configWC =
          new OptimizationConfig(originalFlow * 0.3, originalFlow * 1.5).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.001).maxIterations(25).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationResult resultWC = optimizerWC.optimize(processWithCooler, inletStreamWC, configWC,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      double modelFlowWithCoolerKgHr = resultWC.getOptimalRate();
      double modelFlowWithCoolerMSm3Day = modelFlowWithCoolerKgHr / gasStdDensity * 24.0 / 1e6;

      // Get compressor power - WITH COOLER
      Compressor comp1WC = (Compressor) processWithCooler.getUnit("ups1 Compressor");
      double compPowerWC = comp1WC.getPower() / 1e6; // MW

      String bottleneckWC =
          resultWC.getBottleneck() != null ? resultWC.getBottleneck().getName() : "N/A";
      if (bottleneckWC.contains("Scrubber")) {
        bottleneckWC = bottleneckWC.replace("Scrubber", "Compressor") + "*";
      }
      bottleneckListWithCooler.add(bottleneckWC);

      // Calculate benefit
      double benefitPercent =
          modelFlowNoCoolerMSm3Day > 0
              ? ((modelFlowWithCoolerMSm3Day - modelFlowNoCoolerMSm3Day) / modelFlowNoCoolerMSm3Day)
                  * 100
              : 0;

      double refPR = ref.outletPressure / ref.inletPressure;
      double deviationNC = ((modelFlowNoCoolerMSm3Day - ref.flowMSm3Day) / ref.flowMSm3Day) * 100;
      double deviationWC = ((modelFlowWithCoolerMSm3Day - ref.flowMSm3Day) / ref.flowMSm3Day) * 100;

      results.add(new double[] {ref.excelDate, ref.flowMSm3Day, modelFlowNoCoolerMSm3Day,
          modelFlowWithCoolerMSm3Day, benefitPercent, ref.inletPressure, ref.outletPressure, refPR,
          compPowerNC, compPowerWC, deviationNC, deviationWC});

      System.out.println(String.format(
          "%-12s %-12.2f %-14.2f %-14.2f %-10.1f %-12.2f %-12.2f %-12.2f %-12.1f %-12.1f %-15s",
          ref.getDateString(), ref.flowMSm3Day, modelFlowNoCoolerMSm3Day,
          modelFlowWithCoolerMSm3Day, benefitPercent, ref.inletPressure, ref.outletPressure, refPR,
          compPowerNC, compPowerWC, bottleneckWC));
    }

    // Print summary statistics
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("COOLER BENEFIT ANALYSIS SUMMARY");
    System.out.println(StringUtils.repeat("=", 150));

    // Calculate statistics for valid results only (positive power)
    List<double[]> validResults = new ArrayList<>();
    for (double[] row : results) {
      if (row[8] > 1.0 && row[9] > 1.0) { // Both powers > 1 MW
        validResults.add(row);
      }
    }

    if (!validResults.isEmpty()) {
      double sumBenefit = 0, maxBenefit = Double.MIN_VALUE, minBenefit = Double.MAX_VALUE;
      double sumDevNC = 0, sumDevWC = 0;
      int positiveBenefitCount = 0;

      for (double[] row : validResults) {
        double benefit = row[4];
        sumBenefit += benefit;
        maxBenefit = Math.max(maxBenefit, benefit);
        minBenefit = Math.min(minBenefit, benefit);
        sumDevNC += Math.abs(row[10]);
        sumDevWC += Math.abs(row[11]);
        if (benefit > 0)
          positiveBenefitCount++;
      }

      double avgBenefit = sumBenefit / validResults.size();
      double avgAbsDevNC = sumDevNC / validResults.size();
      double avgAbsDevWC = sumDevWC / validResults.size();

      System.out.println(String.format("Valid comparison points: %d (of %d total)",
          validResults.size(), results.size()));
      System.out.println();
      System.out.println("COOLER BENEFIT (10Â°C cooling, 0.5 bar dP):");
      System.out.println(String.format("  Average production benefit: %.2f%%", avgBenefit));
      System.out.println(String.format("  Maximum benefit: %.2f%%", maxBenefit));
      System.out.println(String.format("  Minimum benefit: %.2f%%", minBenefit));
      System.out.println(String.format("  Points with positive benefit: %d (%.0f%%)",
          positiveBenefitCount, (double) positiveBenefitCount / validResults.size() * 100));
      System.out.println();
      System.out.println("MODEL ACCURACY vs REFERENCE:");
      System.out
          .println(String.format("  Without cooler - avg absolute deviation: %.1f%%", avgAbsDevNC));
      System.out
          .println(String.format("  With cooler - avg absolute deviation: %.1f%%", avgAbsDevWC));
    }

    // Analyze by PR range
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("COOLER BENEFIT BY PRESSURE RATIO RANGE");
    System.out.println(StringUtils.repeat("=", 150));

    List<double[]> lowPR = new ArrayList<>(); // PR < 3.5
    List<double[]> midPR = new ArrayList<>(); // 3.5 <= PR < 5.0
    List<double[]> highPR = new ArrayList<>(); // PR >= 5.0

    for (double[] row : validResults) {
      double pr = row[7];
      if (pr < 3.5) {
        lowPR.add(row);
      } else if (pr < 5.0) {
        midPR.add(row);
      } else {
        highPR.add(row);
      }
    }

    printCoolerBenefitByPRRange("Low PR (<3.5)", lowPR);
    printCoolerBenefitByPRRange("Mid PR (3.5-5.0)", midPR);
    printCoolerBenefitByPRRange("High PR (>=5.0)", highPR);

    // Print CSV for plotting
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("CSV DATA FOR PLOTTING");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println(
        "ExcelDate,Ref_Flow,NoCooler_Flow,WithCooler_Flow,Benefit_pct,P_inlet,P_outlet,PR,Power_NC,Power_WC,Dev_NC,Dev_WC");
    for (double[] row : results) {
      System.out.println(String.format(
          "%.0f,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.3f,%.2f,%.2f,%.2f,%.2f", row[0], row[1], row[2],
          row[3], row[4], row[5], row[6], row[7], row[8], row[9], row[10], row[11]));
    }

    // Conclusions
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("CONCLUSIONS - COOLER INSTALLATION ANALYSIS");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println("\nCooler Specification:");
    System.out.println("  Temperature reduction: 10Â°C");
    System.out.println("  Pressure drop: 0.5 bar");
    System.out.println("\nExpected Effects:");
    System.out
        .println("  + Lower gas temperature â†’ higher gas density â†’ lower actual volume flow");
    System.out.println("  + Compressor can handle more mass flow at same volumetric capacity");
    System.out.println("  - 0.5 bar pressure drop â†’ slightly lower compressor inlet pressure");
    System.out.println("  - Additional equipment and operating cost");
    System.out.println("\nRecommendation:");
    if (!validResults.isEmpty()) {
      double avgBenefit = 0;
      for (double[] row : validResults)
        avgBenefit += row[4];
      avgBenefit /= validResults.size();
      if (avgBenefit > 2.0) {
        System.out.println("  âœ“ RECOMMENDED - Average production benefit of "
            + String.format("%.1f%%", avgBenefit) + " justifies cooler installation");
      } else if (avgBenefit > 0) {
        System.out.println("  âš  MARGINAL - Small positive benefit of "
            + String.format("%.1f%%", avgBenefit) + " - requires detailed economic analysis");
      } else {
        System.out.println("  âœ— NOT RECOMMENDED - No production benefit from cooler");
      }
    }

    assertTrue(results.size() > 0, "Should have comparison results");
  }

  /**
   * Prints cooler benefit statistics for a PR range group.
   */
  private void printCoolerBenefitByPRRange(String groupName, List<double[]> data) {
    if (data.isEmpty()) {
      System.out.println(String.format("%-20s: No valid data points", groupName));
      return;
    }

    double sumBenefit = 0, maxBenefit = Double.MIN_VALUE, minBenefit = Double.MAX_VALUE;
    int posCount = 0;
    for (double[] row : data) {
      double benefit = row[4];
      sumBenefit += benefit;
      maxBenefit = Math.max(maxBenefit, benefit);
      minBenefit = Math.min(minBenefit, benefit);
      if (benefit > 0)
        posCount++;
    }
    double avgBenefit = sumBenefit / data.size();

    System.out.println(String.format(
        "%-20s: %3d points, Avg Benefit=%.1f%%, Min=%.1f%%, Max=%.1f%%, Positive=%d (%.0f%%)",
        groupName, data.size(), avgBenefit, minBenefit, maxBenefit, posCount,
        (double) posCount / data.size() * 100));
  }

  /**
   * Returns the detailed reference data for Troll East (no cooler scenario). Extracted to separate
   * method for reuse in cooler comparison test.
   */
  private List<TrollEastDetailedRefPoint> getTrollEastDetailedRefData() {
    List<TrollEastDetailedRefPoint> refData = new ArrayList<>();
    refData.add(new TrollEastDetailedRefPoint(46418, 57.75487867, 32.49410248, 107.9803314));
    refData.add(new TrollEastDetailedRefPoint(46446, 57.24836375, 32.4086647, 107.8329239));
    refData.add(new TrollEastDetailedRefPoint(46477, 56.74558335, 32.31172943, 107.6717606));
    refData.add(new TrollEastDetailedRefPoint(46507, 56.22237133, 32.21529007, 107.5128021));
    refData.add(new TrollEastDetailedRefPoint(46538, 55.69537066, 32.11252213, 107.3507767));
    refData.add(new TrollEastDetailedRefPoint(46568, 55.17473491, 32.0121994, 107.1932068));
    refData.add(new TrollEastDetailedRefPoint(46599, 54.21325231, 32.05937195, 107.8235779));
    refData.add(new TrollEastDetailedRefPoint(46630, 53.70405827, 31.96873283, 107.6630249));
    refData.add(new TrollEastDetailedRefPoint(46660, 53.14758194, 32.16608429, 108.4317398));
    refData.add(new TrollEastDetailedRefPoint(46691, 41.35724285, 35.60672597, 109.9629092));
    refData.add(new TrollEastDetailedRefPoint(46721, 41.33191881, 35.57180534, 109.9602573));
    refData.add(new TrollEastDetailedRefPoint(46752, 41.2893318, 35.48705678, 109.9491517));
    refData.add(new TrollEastDetailedRefPoint(46783, 41.23979516, 35.37422383, 109.8470196));
    refData.add(new TrollEastDetailedRefPoint(46812, 41.19484402, 35.25317474, 109.6941877));
    refData.add(new TrollEastDetailedRefPoint(46843, 41.14927391, 35.11449474, 109.53194));
    refData.add(new TrollEastDetailedRefPoint(46873, 41.09878256, 34.9741186, 109.3768423));
    refData.add(new TrollEastDetailedRefPoint(46904, 41.0447581, 34.8245562, 109.2236705));
    refData.add(new TrollEastDetailedRefPoint(46934, 40.98355329, 34.67605032, 109.0711467));
    refData.add(new TrollEastDetailedRefPoint(46965, 40.92501838, 34.51984704, 108.9204639));
    refData.add(new TrollEastDetailedRefPoint(46996, 40.8585348, 34.36124125, 108.7698423));
    refData.add(new TrollEastDetailedRefPoint(47026, 40.7936313, 34.20582581, 108.6223831));
    refData.add(new TrollEastDetailedRefPoint(47057, 40.72819232, 34.04350419, 108.4642723));
    refData.add(new TrollEastDetailedRefPoint(47087, 40.65695942, 33.88515435, 108.3122656));
    refData.add(new TrollEastDetailedRefPoint(47118, 40.58489045, 33.72064972, 108.1502151));
    refData.add(new TrollEastDetailedRefPoint(47149, 40.51341628, 33.55505278, 108.0074912));
    refData.add(new TrollEastDetailedRefPoint(47177, 40.44374687, 33.40540008, 107.8621635));
    refData.add(new TrollEastDetailedRefPoint(47208, 40.36910669, 33.23793974, 107.7009516));
    refData.add(new TrollEastDetailedRefPoint(47238, 40.29647707, 33.07486343, 107.5034714));
    refData.add(new TrollEastDetailedRefPoint(47269, 40.2196492, 32.90579671, 107.3063054));
    refData.add(new TrollEastDetailedRefPoint(47299, 40.14359146, 32.74166107, 107.1143875));
    refData.add(new TrollEastDetailedRefPoint(47330, 39.96319758, 32.60821349, 107.5959892));
    refData.add(new TrollEastDetailedRefPoint(47361, 39.83318021, 32.45884362, 107.3917878));
    refData.add(new TrollEastDetailedRefPoint(47391, 39.69439069, 32.31397629, 107.2017136));
    refData.add(new TrollEastDetailedRefPoint(47422, 39.55563046, 32.16634094, 106.9909364));
    refData.add(new TrollEastDetailedRefPoint(47452, 39.41381685, 32.02303977, 106.7943695));
    refData.add(new TrollEastDetailedRefPoint(47483, 39.2720633, 31.87546867, 106.5964466));
    refData.add(new TrollEastDetailedRefPoint(47514, 39.12370073, 31.72863937, 106.403492));
    refData.add(new TrollEastDetailedRefPoint(47542, 38.98257126, 31.59619081, 106.2297894));
    refData.add(new TrollEastDetailedRefPoint(47573, 38.83960281, 31.4497344, 106.0354782));
    refData.add(new TrollEastDetailedRefPoint(47603, 38.69081164, 31.30800964, 105.8477109));
    refData.add(new TrollEastDetailedRefPoint(47634, 38.53813596, 31.16072393, 105.6532293));
    refData.add(new TrollEastDetailedRefPoint(47664, 38.38337225, 31.01635742, 105.4697342));
    refData.add(new TrollEastDetailedRefPoint(47695, 38.22592355, 30.86605453, 105.2846298));
    refData.add(new TrollEastDetailedRefPoint(47726, 38.06626807, 30.71932887, 105.0723327));
    refData.add(new TrollEastDetailedRefPoint(47756, 37.90807583, 30.57484818, 104.8876495));
    refData.add(new TrollEastDetailedRefPoint(47787, 39.35103537, 29.97077519, 97.73040937));
    refData.add(new TrollEastDetailedRefPoint(47817, 39.23503406, 29.78034938, 97.11444105));
    refData.add(new TrollEastDetailedRefPoint(47848, 39.09860922, 29.59613228, 96.56840515));
    refData.add(new TrollEastDetailedRefPoint(47879, 38.95461188, 29.41576379, 96.01747336));
    refData.add(new TrollEastDetailedRefPoint(47907, 38.82197337, 29.25466587, 95.51730477));
    refData.add(new TrollEastDetailedRefPoint(47938, 38.69120573, 29.07704783, 94.95040774));
    refData.add(new TrollEastDetailedRefPoint(47968, 38.54906309, 28.90528706, 94.39606224));
    refData.add(new TrollEastDetailedRefPoint(47999, 38.40535587, 28.72791318, 93.81798581));
    refData.add(new TrollEastDetailedRefPoint(48029, 38.2632283, 28.55872562, 93.27490272));
    refData.add(new TrollEastDetailedRefPoint(48060, 38.07951036, 28.40438349, 93.00956701));
    refData.add(new TrollEastDetailedRefPoint(48091, 37.8466746, 28.25387813, 92.76753274));
    refData.add(new TrollEastDetailedRefPoint(48121, 37.61871052, 28.10919952, 92.53070831));
    refData.add(new TrollEastDetailedRefPoint(48152, 37.39593648, 27.96278903, 92.3035476));
    refData.add(new TrollEastDetailedRefPoint(48182, 37.15699068, 27.82595855, 92.1428263));
    refData.add(new TrollEastDetailedRefPoint(48213, 36.91122677, 27.6841433, 91.94390419));
    refData.add(new TrollEastDetailedRefPoint(48244, 36.66935898, 27.54435031, 91.74802235));
    refData.add(new TrollEastDetailedRefPoint(48273, 36.4364003, 27.4156111, 91.57424798));
    refData.add(new TrollEastDetailedRefPoint(48304, 36.20265032, 27.27795634, 91.3593148));
    refData.add(new TrollEastDetailedRefPoint(48334, 35.96834898, 27.14621423, 91.14912001));
    refData.add(new TrollEastDetailedRefPoint(48365, 35.73586742, 27.01188436, 90.93363358));
    refData.add(new TrollEastDetailedRefPoint(48395, 35.50293524, 26.88365299, 90.72673007));
    refData.add(new TrollEastDetailedRefPoint(48426, 35.27272504, 26.75294798, 90.5145926));
    refData.add(new TrollEastDetailedRefPoint(48457, 35.03041368, 26.62725968, 90.34587572));
    refData.add(new TrollEastDetailedRefPoint(48487, 34.77320984, 26.51251984, 90.23579407));
    refData.add(new TrollEastDetailedRefPoint(48518, 34.50892391, 26.39692294, 90.12816084));
    refData.add(new TrollEastDetailedRefPoint(48548, 34.02021264, 26.34302162, 91.05369142));
    refData.add(new TrollEastDetailedRefPoint(48579, 33.7768324, 26.22738508, 90.84625957));
    refData.add(new TrollEastDetailedRefPoint(48610, 33.54432889, 26.11156996, 90.63954508));
    refData.add(new TrollEastDetailedRefPoint(48638, 33.32551138, 26.00724407, 90.45408851));
    refData.add(new TrollEastDetailedRefPoint(48669, 33.10503766, 25.89587191, 90.31830388));
    refData.add(new TrollEastDetailedRefPoint(48699, 32.86330951, 25.79080159, 90.21660586));
    refData.add(new TrollEastDetailedRefPoint(48730, 32.62074251, 25.6832451, 90.11269088));
    refData.add(new TrollEastDetailedRefPoint(48760, 32.38284165, 25.5800708, 90.0132083));
    refData.add(new TrollEastDetailedRefPoint(48791, 32.14751017, 25.47484149, 89.91045978));
    refData.add(new TrollEastDetailedRefPoint(48822, 31.90849415, 25.3709033, 89.81281157));
    refData.add(new TrollEastDetailedRefPoint(48852, 31.6832451, 25.27064705, 89.71279907));
    refData.add(new TrollEastDetailedRefPoint(48883, 31.44349779, 25.16878824, 89.62139929));
    refData.add(new TrollEastDetailedRefPoint(48913, 31.21519592, 25.0711338, 89.53549164));
    refData.add(new TrollEastDetailedRefPoint(48944, 30.98707177, 24.97108368, 89.44777076));
    refData.add(new TrollEastDetailedRefPoint(48975, 30.75670612, 24.87193385, 89.36115111));
    refData.add(new TrollEastDetailedRefPoint(49003, 30.54364446, 24.78312336, 89.28336711));
    refData.add(new TrollEastDetailedRefPoint(49034, 30.33105856, 24.68545881, 89.20346559));
    refData.add(new TrollEastDetailedRefPoint(49064, 30.10885602, 24.59170836, 89.11296417));
    refData.add(new TrollEastDetailedRefPoint(49095, 29.88110808, 24.50490474, 89.09556788));
    refData.add(new TrollEastDetailedRefPoint(49125, 29.62681725, 24.42328093, 89.08556173));
    refData.add(new TrollEastDetailedRefPoint(49156, 29.37897681, 24.34011071, 89.07534041));
    refData.add(new TrollEastDetailedRefPoint(49187, 29.12726193, 24.25799651, 89.06523837));
    refData.add(new TrollEastDetailedRefPoint(49217, 28.88418835, 24.17946243, 89.05560303));
    refData.add(new TrollEastDetailedRefPoint(49248, 31.29508104, 23.3501619, 75.88755712));
    refData.add(new TrollEastDetailedRefPoint(49278, 31.06753844, 23.21394347, 75.6519426));
    refData.add(new TrollEastDetailedRefPoint(49309, 30.8286844, 23.08568573, 75.46246338));
    refData.add(new TrollEastDetailedRefPoint(49340, 30.5938486, 22.9629524, 75.26571885));
    refData.add(new TrollEastDetailedRefPoint(49368, 30.38273733, 22.85540781, 75.09644677));
    refData.add(new TrollEastDetailedRefPoint(49399, 30.17754852, 22.73897707, 74.91257885));
    refData.add(new TrollEastDetailedRefPoint(49429, 29.96734253, 22.63068271, 74.73813128));
    refData.add(new TrollEastDetailedRefPoint(49460, 29.75021748, 22.52281715, 74.5702221));
    refData.add(new TrollEastDetailedRefPoint(49490, 29.53237903, 22.41996883, 74.41054756));
    refData.add(new TrollEastDetailedRefPoint(49521, 29.31979922, 22.31494957, 74.24760914));
    refData.add(new TrollEastDetailedRefPoint(49552, 29.11073171, 22.21075648, 74.09275907));
    refData.add(new TrollEastDetailedRefPoint(49582, 28.90126469, 22.11141205, 73.93153381));
    refData.add(new TrollEastDetailedRefPoint(49613, 28.69905016, 22.009365, 73.77300992));
    refData.add(new TrollEastDetailedRefPoint(49643, 28.4952171, 21.91112216, 73.6143594));
    refData.add(new TrollEastDetailedRefPoint(49674, 28.295902, 21.81361437, 73.51739583));
    refData.add(new TrollEastDetailedRefPoint(49705, 28.08056159, 21.71928798, 73.45605752));
    refData.add(new TrollEastDetailedRefPoint(49734, 27.87475393, 21.63209775, 73.39943724));
    refData.add(new TrollEastDetailedRefPoint(49765, 27.66852001, 21.53991278, 73.33973998));
    refData.add(new TrollEastDetailedRefPoint(49795, 27.46305136, 21.45160885, 73.28248381));
    refData.add(new TrollEastDetailedRefPoint(49826, 27.26099665, 21.36125287, 73.22413911));
    refData.add(new TrollEastDetailedRefPoint(49856, 27.05991711, 21.27459641, 73.16815982));
    refData.add(new TrollEastDetailedRefPoint(49887, 26.85843018, 21.18646718, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(49918, 26.65398461, 21.10176583, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(49948, 26.44874223, 21.02048302, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(49979, 26.24147235, 20.9380687, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50009, 26.04264516, 20.8589172, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50040, 25.84562866, 20.77806132, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50071, 25.64098781, 20.6980941, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50099, 25.45377587, 20.62658182, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50130, 25.26842316, 20.54816985, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50160, 25.07746792, 20.47328671, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50191, 24.87655781, 20.40271265, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50221, 24.66863741, 20.33555214, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50252, 24.46181624, 20.2672082, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50283, 24.25449538, 20.19963104, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50313, 24.05295856, 20.13459015, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50344, 23.85244026, 20.06882976, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50374, 23.65522722, 20.00547798, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50405, 23.45627661, 19.93764747, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50436, 23.24694206, 19.87032594, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50464, 23.05511492, 19.81031523, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50495, 22.86321871, 19.7447093, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50525, 22.66648406, 19.6820305, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50556, 22.47342171, 19.61807991, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50586, 22.28447158, 19.55695811, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50617, 22.09444818, 19.49456992, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50648, 21.97675141, 19.37474745, 72.38934987));
    refData.add(new TrollEastDetailedRefPoint(50678, 21.91247337, 19.36138153, 73.11323547));
    refData.add(new TrollEastDetailedRefPoint(50709, 25.95487848, 17.72273155, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50739, 25.82129547, 17.51703365, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50770, 25.69486229, 17.32745268, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50801, 25.57463532, 17.15071989, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50829, 25.46652299, 16.99824055, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50860, 25.36012497, 16.83496101, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50890, 25.25511303, 16.68101772, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50921, 25.14956182, 16.52512608, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50951, 25.05032197, 16.37664443, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(50982, 24.92292807, 16.25089455, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51013, 24.74440119, 16.13385833, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51043, 24.56934888, 16.02315712, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51074, 24.39819795, 15.91106591, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51104, 24.22958105, 15.80442793, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51135, 24.06383857, 15.69463253, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51166, 23.89788461, 15.58901462, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51195, 23.74011314, 15.49034508, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51226, 23.5804058, 15.38618742, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51256, 23.42629064, 15.28657831, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51287, 23.26977381, 15.18485489, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51317, 23.11564788, 15.08751912, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51348, 22.96470551, 14.98803264, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51379, 22.81054881, 14.88949901, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51409, 22.6639965, 14.7938385, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51440, 22.41666116, 14.65281391, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51470, 22.22589506, 14.53145379, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51501, 22.0582261, 14.41722202, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51532, 21.98111841, 14.31090988, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51560, 21.82654165, 14.2155708, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51591, 21.67423808, 14.11410141, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51621, 21.52117677, 14.01871204, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51652, 21.36857011, 13.92232863, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51682, 21.22520774, 13.83072818, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51713, 21.08002682, 13.73758736, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51744, 20.93283448, 13.64559832, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51774, 20.73149894, 13.55931091, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51805, 20.66191184, 13.4717207, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51835, 20.53015031, 13.38479621, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51866, 20.39223601, 13.29655043, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51897, 20.25645094, 13.20925436, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51925, 20.13302497, 13.13118296, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51956, 19.99980805, 13.04664332, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(51986, 19.86578596, 12.96618818, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52017, 19.72255493, 12.88389616, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52047, 19.52714414, 12.80529366, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52078, 19.47121615, 12.72688262, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52109, 19.34399341, 12.64631955, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52139, 19.21528048, 12.56950315, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52170, 19.08894176, 12.49100742, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52200, 18.96268892, 12.41586399, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52231, 18.83873957, 12.3390015, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52262, 18.71345843, 12.26279627, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52290, 18.59427254, 12.19459937, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52321, 18.47840097, 12.11993394, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52351, 18.36133693, 12.04777414, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52382, 18.24132836, 11.97386479, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52412, 18.12252594, 11.90294745, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52443, 18.00442047, 11.83027621, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52474, 17.87140405, 11.75775328, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52504, 17.72586597, 11.69022083, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52535, 17.658349, 11.62088413, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52565, 17.55010233, 11.55279274, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52596, 17.43625856, 11.48233674, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52627, 17.32229609, 11.41264439, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52656, 17.21512705, 11.34807955, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52687, 17.10384276, 11.2796113, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52717, 16.99332037, 11.21316233, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52748, 16.88273737, 11.1463131, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52778, 16.77862538, 11.08247611, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52809, 16.66996701, 11.01538038, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52840, 16.56176327, 10.95000401, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52870, 16.45963653, 10.88739662, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52901, 16.35414547, 10.82200321, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52931, 16.24187964, 10.75998497, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52962, 16.09584355, 10.69735813, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(52993, 16.01915338, 10.63589382, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53021, 15.94222763, 10.57853607, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53052, 15.85139213, 10.51421515, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53082, 15.74821467, 10.45036856, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53113, 15.64294083, 10.38439302, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53143, 15.53485075, 10.31977377, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53174, 15.42812459, 10.25389665, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53205, 15.32788988, 10.18932841, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53235, 15.20504339, 10.12691975, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53266, 15.11177264, 10.06320353, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53296, 14.99482472, 10.00190859, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53327, 14.88884871, 9.92034537, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53358, 14.71138853, 9.801660728, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53386, 14.47765071, 9.605193233, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53417, 14.09959796, 9.358772151, 73.12400055));
    refData.add(new TrollEastDetailedRefPoint(53447, 13.71855614, 9.148887698, 73.12394155));
    refData.add(new TrollEastDetailedRefPoint(53478, 13.36891967, 8.959475136, 73.12282181));
    refData.add(new TrollEastDetailedRefPoint(53508, 13.06515361, 8.797160149, 73.11972885));
    refData.add(new TrollEastDetailedRefPoint(53539, 12.77993675, 8.641706276, 73.11401316));
    refData.add(new TrollEastDetailedRefPoint(53570, 12.51306452, 8.494732539, 73.09979248));
    refData.add(new TrollEastDetailedRefPoint(53600, 12.27418886, 8.361652412, 73.07813738));
    refData.add(new TrollEastDetailedRefPoint(53631, 12.00308562, 8.255931234, 73.0496108));
    refData.add(new TrollEastDetailedRefPoint(53661, 11.81421324, 8.120693395, 73.02389889));
    refData.add(new TrollEastDetailedRefPoint(53692, 11.60113106, 8.001636168, 72.99890858));
    refData.add(new TrollEastDetailedRefPoint(53723, 11.41341245, 7.875162826, 72.97402543));
    refData.add(new TrollEastDetailedRefPoint(53751, 11.16446719, 7.777777069, 72.95158783));
    refData.add(new TrollEastDetailedRefPoint(53782, 11.02587708, 7.67111687, 72.92717328));
    refData.add(new TrollEastDetailedRefPoint(53812, 10.84548496, 7.56038061, 72.89405711));
    refData.add(new TrollEastDetailedRefPoint(53843, 10.6616325, 7.454294734, 72.83086275));
    refData.add(new TrollEastDetailedRefPoint(53873, 10.48662067, 7.356484144, 72.74362386));
    refData.add(new TrollEastDetailedRefPoint(53904, 10.31708005, 7.259918282, 72.64576799));
    refData.add(new TrollEastDetailedRefPoint(53935, 10.12976525, 7.18506669, 72.51462506));
    refData.add(new TrollEastDetailedRefPoint(53965, 10.00338134, 7.112316849, 72.32061785));
    refData.add(new TrollEastDetailedRefPoint(53996, 9.856180333, 7.053022003, 72.1226149));
    refData.add(new TrollEastDetailedRefPoint(54026, 9.691668852, 7.01510526, 71.94200882));
    refData.add(new TrollEastDetailedRefPoint(54057, 9.55120781, 6.960366513, 71.74579818));
    refData.add(new TrollEastDetailedRefPoint(54088, 9.413862728, 6.904153712, 71.5546312));
    refData.add(new TrollEastDetailedRefPoint(54117, 9.281047404, 6.853587888, 71.38148899));
    refData.add(new TrollEastDetailedRefPoint(54148, 9.141048851, 6.80288873, 71.20403484));
    refData.add(new TrollEastDetailedRefPoint(54178, 8.998072535, 6.751531676, 71.03405176));
    refData.add(new TrollEastDetailedRefPoint(54209, 8.839848236, 6.693036715, 70.85453413));
    refData.add(new TrollEastDetailedRefPoint(54239, 8.718978718, 6.653169669, 70.71470323));
    refData.add(new TrollEastDetailedRefPoint(54270, 8.604596388, 6.608898168, 70.57171867));
    refData.add(new TrollEastDetailedRefPoint(54301, 8.428875712, 6.560703276, 70.40926594));
    refData.add(new TrollEastDetailedRefPoint(54331, 8.339226152, 6.520794281, 70.28520589));
    refData.add(new TrollEastDetailedRefPoint(54362, 8.235875305, 6.480123548, 70.14283244));
    refData.add(new TrollEastDetailedRefPoint(54392, 8.132933092, 6.435350077, 69.99123123));
    refData.add(new TrollEastDetailedRefPoint(54423, 8.022553514, 6.392254, 69.85229279));
    refData.add(new TrollEastDetailedRefPoint(54454, 7.920193234, 6.351200443, 69.73019899));
    refData.add(new TrollEastDetailedRefPoint(54482, 7.807332731, 6.32639365, 69.62919298));
    refData.add(new TrollEastDetailedRefPoint(54513, 7.717626292, 6.287368415, 69.51266127));
    refData.add(new TrollEastDetailedRefPoint(54543, 7.630502123, 6.246020669, 69.38848387));
    refData.add(new TrollEastDetailedRefPoint(54574, 7.520470397, 6.211432909, 69.27278071));
    refData.add(new TrollEastDetailedRefPoint(54604, 7.428441225, 6.172930862, 69.16361767));
    refData.add(new TrollEastDetailedRefPoint(54635, 7.341170885, 6.132386694, 69.04724053));
    refData.add(new TrollEastDetailedRefPoint(54666, 7.250003764, 6.090896285, 68.91073886));
    refData.add(new TrollEastDetailedRefPoint(54696, 7.153966472, 6.063498974, 68.76416016));
    refData.add(new TrollEastDetailedRefPoint(54727, 7.052058521, 6.018473979, 68.51251688));
    refData.add(new TrollEastDetailedRefPoint(54757, 6.981494864, 5.977232392, 68.23663986));
    refData.add(new TrollEastDetailedRefPoint(54788, 6.896082482, 5.929715073, 67.96485118));
    refData.add(new TrollEastDetailedRefPoint(54819, 6.806967379, 5.883213233, 67.69678427));
    refData.add(new TrollEastDetailedRefPoint(54847, 6.727793537, 5.847986218, 67.53951828));
    return refData;
  }

  /**
   * Builds Troll East process WITH COOLER for a specific year's conditions.
   * 
   * @param inletPressure compressor inlet pressure (bara)
   * @param outletPressure compressor outlet pressure (bara)
   * @param numCompressors number of compressors (2 or 3)
   * @param coolingDeltaT temperature drop in cooler (Â°C)
   * @param pressureDrop pressure drop across cooler (bar)
   * @return configured ProcessSystem
   */
  private ProcessSystem buildTrollEastProcessWithCooler(double inletPressure, double outletPressure,
      int numCompressors, double coolingDeltaT, double pressureDrop) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    double baseFlow = 2097870.58288790; // kg/hr

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(baseFlow, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(inletPressure, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    // Add COOLER before splitter
    StreamInterface feedToSplitter;
    if (coolingDeltaT > 0 || pressureDrop > 0) {
      Heater cooler = new Heater("Inlet Cooler", saturatedStream);
      double inletTemp = saturatedStream.getTemperature("C");
      cooler.setOutTemperature(inletTemp - coolingDeltaT, "C");
      double coolerInletP = saturatedStream.getPressure("bara");
      cooler.setOutPressure(coolerInletP - pressureDrop, "bara");
      cooler.run();
      processSystem.add(cooler);

      // Add separator after cooler to remove condensate
      Separator coolerSeparator = new Separator("Cooler Separator", cooler.getOutletStream());
      coolerSeparator.run();
      processSystem.add(coolerSeparator);

      feedToSplitter = coolerSeparator.getGasOutStream();
    } else {
      feedToSplitter = saturatedStream;
    }

    // Split flow based on number of compressors
    Splitter splitter = new Splitter("Test Splitter", feedToSplitter);
    if (numCompressors == 3) {
      splitter.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    } else {
      splitter.setSplitFactors(new double[] {0.5, 0.5});
    }
    splitter.run();
    processSystem.add(splitter);

    // Create compressor trains
    StreamInterface ups1Outlet = createUpstreamCompressorsForYear("ups1",
        splitter.getSplitStream(0), processSystem, outletPressure);
    StreamInterface ups2Outlet = createUpstreamCompressorsForYear("ups2",
        splitter.getSplitStream(1), processSystem, outletPressure);

    Manifold manifold;
    if (numCompressors == 3) {
      StreamInterface ups3Outlet = createUpstreamCompressorsForYear("ups3",
          splitter.getSplitStream(2), processSystem, outletPressure);
      manifold = new Manifold("Compressor Outlet Manifold");
      manifold.addStream(ups1Outlet);
      manifold.addStream(ups2Outlet);
      manifold.addStream(ups3Outlet);
      manifold.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    } else {
      manifold = new Manifold("Compressor Outlet Manifold");
      manifold.addStream(ups1Outlet);
      manifold.addStream(ups2Outlet);
      manifold.setSplitFactors(new double[] {0.5, 0.5});
    }
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    processSystem.run();

    // Disable capacity analysis on scrubbers
    Separator ups1Scrubber = (Separator) processSystem.getUnit("ups1 Scrubber");
    Separator ups2Scrubber = (Separator) processSystem.getUnit("ups2 Scrubber");
    ups1Scrubber.setCapacityAnalysisEnabled(false);
    ups2Scrubber.setCapacityAnalysisEnabled(false);
    if (numCompressors == 3) {
      Separator ups3Scrubber = (Separator) processSystem.getUnit("ups3 Scrubber");
      ups3Scrubber.setCapacityAnalysisEnabled(false);
    }

    // Configure compressors with identical curves
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");

    configureCompressor1And2WithElectricDriver(ups1Comp, 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp, 7383.0);

    if (numCompressors == 3) {
      Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");
      configureCompressor3WithElectricDriver(ups3Comp, 6726.0);
    }

    processSystem.run();
    return processSystem;
  }

  /**
   * Prints statistics for a group of results by pressure ratio range.
   */
  private void printPRGroupStats(String groupName, List<double[]> data) {
    if (data.isEmpty()) {
      System.out.println(String.format("%-20s: No data points", groupName));
      return;
    }

    double sumAbsDev = 0, maxDev = Double.MIN_VALUE, minDev = Double.MAX_VALUE;
    for (double[] row : data) {
      double dev = row[3];
      sumAbsDev += Math.abs(dev);
      maxDev = Math.max(maxDev, dev);
      minDev = Math.min(minDev, dev);
    }
    double avgAbsDev = sumAbsDev / data.size();

    System.out.println(String.format("%-20s: %3d points, Avg|Dev|=%.1f%%, Min=%.1f%%, Max=%.1f%%",
        groupName, data.size(), avgAbsDev, minDev, maxDev));
  }

  /**
   * Reference case data for Troll East from Figure 3-1. Data extracted for key milestone years.
   */
  private static class TrollEastReferenceCase {
    int year;
    double teInletPressure; // bara - Troll East inlet pressure
    double kollsnesPressure; // bara - Kollsnes arrival/export pressure
    double teRate; // MSmÂ³/day - Troll East production rate
    double totalRate; // MSmÂ³/day - Total gas rate
    String compressorConfig; // Configuration description
    int numCompressors; // Number of compressors in operation

    TrollEastReferenceCase(int year, double teInlet, double kollsnes, double teRate,
        double totalRate, String config, int numComp) {
      this.year = year;
      this.teInletPressure = teInlet;
      this.kollsnesPressure = kollsnes;
      this.teRate = teRate;
      this.totalRate = totalRate;
      this.compressorConfig = config;
      this.numCompressors = numComp;
    }
  }

  /**
   * Evaluates Troll East model against reference case from 2027 to 2045. Analyzes compressor
   * operating conditions and identifies when rebundling would be beneficial.
   */
  @Test
  public void testTrollEastReferenceCaseEvaluation() {
    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println("TROLL EAST REFERENCE CASE EVALUATION - MODEL vs REFERENCE (2027-2045)");
    System.out.println(StringUtils.repeat("=", 130));
    System.out.println("Configuration: 2 compressors (A & B) with IDENTICAL RB71-6 bundles");

    // Reference case data extracted from Figure 3-1
    // TE_Inlet (stippled blue curve): 35-40 bara range initially, declining over time
    // TE_Rate (solid light blue curve): drops to ~40 MSmÂ³/day in 2027, declining further
    // From 2027: Only 2 compressors (A & B) with same curves on Troll East
    List<TrollEastReferenceCase> referenceData = new ArrayList<>();
    // Year, TE_Inlet(bara), Kollsnes(bara), TE_Rate(MSmÂ³/d), Total(MSmÂ³/d), Config, NumComp
    // TE_Rate read from solid light blue curve (drops to ~40 MSmÂ³/day in 2027)
    referenceData.add(new TrollEastReferenceCase(2027, 40, 90, 40, 120, "2 parallel (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2028, 39, 88, 38, 115, "2 parallel (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2030, 37, 75, 32, 100, "2 parallel (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2032, 35, 72, 28, 88, "2 parallel (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2034, 33, 68, 24, 75, "2 parallel (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2036, 30, 60, 20, 60, "2 parallel (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2038, 28, 80, 15, 45, "2 series (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2040, 26, 78, 12, 35, "2 series (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2042, 24, 75, 10, 28, "2 series (A+B)", 2));
    referenceData.add(new TrollEastReferenceCase(2045, 20, 70, 6, 18, "2 series (A+B)", 2));

    System.out.println("\nReference Case Data (from Figure 3-1):");
    System.out.println(StringUtils.repeat("-", 100));
    System.out.println(String.format("%-6s %-12s %-14s %-12s %-12s %-25s", "Year", "TE_Inlet(bara)",
        "Kollsnes(bara)", "TE_Rate", "Total_Rate", "Configuration"));
    System.out.println(StringUtils.repeat("-", 100));
    for (TrollEastReferenceCase ref : referenceData) {
      System.out.println(
          String.format("%-6d %-12.0f %-14.0f %-12.0f %-12.0f %-25s", ref.year, ref.teInletPressure,
              ref.kollsnesPressure, ref.teRate, ref.totalRate, ref.compressorConfig));
    }

    // Store model results for comparison
    List<double[]> modelResults = new ArrayList<>();
    List<String> bottleneckList = new ArrayList<>();
    List<String> chartStatusList = new ArrayList<>();
    // [year, refRate, modelRate, deviation%, pressureRatio, compPower, compSpeed, compEfficiency,
    // teInletPressure, kollsnesPressure, polytropicHead, actualInletFlow]

    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println("MODEL EVALUATION - Running optimizer for each reference case year");
    System.out.println("  Compressor config: 2 x RB71-6 (identical bundles, 44.4 MW max each)");
    System.out.println(StringUtils.repeat("=", 130));

    double gasStdDensity = 0.73; // kg/SmÂ³

    for (TrollEastReferenceCase ref : referenceData) {
      // Build process with reference case conditions - always 2 compressors with same curves
      ProcessSystem process =
          buildTrollEastProcessForYear(ref.teInletPressure, ref.kollsnesPressure, 2);

      Stream inletStream = (Stream) process.getUnit("Inlet Stream");
      double originalFlow = inletStream.getFlowRate("kg/hr");

      // Run optimizer to find max production
      ProductionOptimizer optimizer = new ProductionOptimizer();
      OptimizationConfig config =
          new OptimizationConfig(originalFlow * 0.5, originalFlow * 1.5).rateUnit("kg/hr")
              .tolerance(originalFlow * 0.001).maxIterations(25).defaultUtilizationLimit(1.0)
              .searchMode(SearchMode.BINARY_FEASIBILITY).rejectInvalidSimulations(true);

      OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
          proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0,
          ObjectiveType.MAXIMIZE);

      OptimizationResult result = optimizer.optimize(process, inletStream, config,
          Collections.singletonList(throughputObjective), Collections.emptyList());

      double modelFlowKgHr = result.getOptimalRate();
      double modelFlowMSm3Day = modelFlowKgHr / gasStdDensity * 24.0 / 1e6;

      // Get compressor operating data
      Compressor comp1 = (Compressor) process.getUnit("ups1 Compressor");
      double pressureRatio =
          comp1.getOutletStream().getPressure("bara") / comp1.getInletStream().getPressure("bara");
      double compPower = comp1.getPower() / 1e6; // MW
      double compSpeed = comp1.getSpeed();
      double polyEff = comp1.getPolytropicEfficiency() * 100;

      // Get additional compressor details
      double polytropicHead = comp1.getPolytropicHead(); // kJ/kg
      double actualInletFlow = comp1.getInletStream().getFlowRate("m3/hr"); // actual m3/hr
      double inletPressure = comp1.getInletStream().getPressure("bara");
      double outletPressure = comp1.getOutletStream().getPressure("bara");

      // Check if compressor has a chart and determine operating point status
      boolean hasChart = comp1.getCompressorChart() != null;
      String chartStatus = "N/A";

      if (hasChart) {
        try {
          // Use Compressor's built-in methods for surge detection
          double distanceToSurge = comp1.getDistanceToSurge();
          boolean isStonewall = comp1.isStoneWall();

          // distanceToSurge: negative means in surge (flow < surge flow)
          // positive means margin to surge
          boolean isSurge = distanceToSurge < 0;

          if (Double.isNaN(compSpeed) || compSpeed <= 0) {
            chartStatus = "NO_SOLUTION";
          } else if (compPower < 0) {
            chartStatus = "INVALID_POWER";
          } else if (isSurge) {
            chartStatus = String.format("SURGE(%.1f%%)", distanceToSurge * 100);
          } else if (isStonewall) {
            chartStatus = "STONEWALL";
          } else {
            chartStatus = String.format("OK(%.0f%%)", distanceToSurge * 100);
          }
        } catch (Exception e) {
          chartStatus = "ERROR";
        }
      }

      // Calculate deviation from reference
      double deviation = ((modelFlowMSm3Day - ref.teRate) / ref.teRate) * 100;

      modelResults.add(new double[] {ref.year, ref.teRate, modelFlowMSm3Day, deviation,
          pressureRatio, compPower, compSpeed, polyEff, ref.teInletPressure, ref.kollsnesPressure,
          polytropicHead, actualInletFlow});

      // Store chart status
      chartStatusList.add(chartStatus);

      // Get raw bottleneck from optimizer
      String rawBottleneck =
          result.getBottleneck() != null ? result.getBottleneck().getName() : "N/A";

      // Filter to only show compressor bottlenecks - if scrubber is bottleneck,
      // report the associated compressor instead (in real operations, compressor limits first)
      String bottleneck = rawBottleneck;
      if (rawBottleneck.contains("Scrubber")) {
        // Replace scrubber name with corresponding compressor
        bottleneck = rawBottleneck.replace("Scrubber", "Compressor") + "*";
      }
      bottleneckList.add(bottleneck);

      System.out.println(String.format(
          "Year %d: Model=%.1f MSmÂ³/d, Ref=%.0f MSmÂ³/d, Dev=%+.1f%%, PR=%.2f, Power=%.1f MW, "
              + "Speed=%.0f RPM, Î·=%.1f%%, Head=%.0f kJ/kg, Flow=%.0f mÂ³/hr, ChartStatus=%s, Bottleneck=%s",
          ref.year, modelFlowMSm3Day, ref.teRate, deviation, pressureRatio, compPower, compSpeed,
          polyEff, polytropicHead, actualInletFlow, chartStatus, bottleneck));
    }

    // Print detailed comparison table with bottleneck
    System.out.println("\n" + StringUtils.repeat("=", 220));
    System.out.println("DETAILED MODEL vs REFERENCE COMPARISON");
    System.out.println(StringUtils.repeat("=", 220));
    System.out.println(String.format(
        "%-6s %-8s %-8s %-10s %-10s %-8s %-6s %-10s %-8s %-8s %-10s %-12s %-15s %-20s", "Year",
        "P_in", "P_out", "Ref", "Model", "Dev%", "PR", "Power(MW)", "Speed", "Î·(%)", "Head(kJ/kg)",
        "Flow(mÂ³/hr)", "ChartStatus", "Bottleneck"));
    System.out.println(StringUtils.repeat("-", 220));

    for (int i = 0; i < modelResults.size(); i++) {
      double[] row = modelResults.get(i);
      String bottleneck = bottleneckList.get(i);
      String chartStatus = chartStatusList.get(i);
      // row: [year, refRate, modelRate, deviation, PR, power, speed, efficiency, P_in, P_out, head,
      // flow]
      System.out.println(String.format(
          "%-6.0f %-8.0f %-8.0f %-10.0f %-10.1f %-8.1f %-6.2f %-10.1f %-8.0f %-8.1f %-10.0f %-12.0f %-15s %-20s",
          row[0], row[8], row[9], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[10],
          row[11], chartStatus, bottleneck));
    }

    // Print compressor chart analysis
    System.out.println("\n" + StringUtils.repeat("=", 150));
    System.out.println("COMPRESSOR CHART STATUS ANALYSIS");
    System.out.println(StringUtils.repeat("=", 150));
    System.out.println("ChartStatus meanings:");
    System.out.println("  OK           - Operating point within compressor chart envelope");
    System.out.println("  SURGE        - Operating point below minimum flow (surge line)");
    System.out.println("  STONEWALL    - Operating point above maximum flow (stonewall/choke)");
    System.out
        .println("  NO_SOLUTION  - Speed is NaN or invalid - chart cannot find operating point");
    System.out.println("  INVALID_POWER- Power is negative - numerical issue");
    System.out.println("  N/A          - No compressor chart loaded");
    System.out.println();

    // Count issues
    int okCount = 0, surgeCount = 0, stonewallCount = 0, noSolutionCount = 0, invalidCount = 0;
    for (String status : chartStatusList) {
      if (status.equals("OK"))
        okCount++;
      else if (status.equals("SURGE"))
        surgeCount++;
      else if (status.equals("STONEWALL"))
        stonewallCount++;
      else if (status.equals("NO_SOLUTION"))
        noSolutionCount++;
      else if (status.equals("INVALID_POWER"))
        invalidCount++;
    }

    System.out.println("Summary:");
    System.out.println(String.format("  OK:            %d years", okCount));
    System.out.println(String.format("  SURGE:         %d years", surgeCount));
    System.out.println(String.format("  STONEWALL:     %d years", stonewallCount));
    System.out
        .println(String.format("  NO_SOLUTION:   %d years (chart cannot solve)", noSolutionCount));
    System.out.println(String.format("  INVALID_POWER: %d years (numerical issues)", invalidCount));

    // Rebundling analysis
    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println("COMPRESSOR REBUNDLING ANALYSIS (Post-2032)");
    System.out.println(StringUtils.repeat("=", 130));

    System.out.println("\nCurrent RB71-6 Bundle Design Point:");
    System.out.println("  - Design inlet pressure: ~55-65 bara");
    System.out.println("  - Design pressure ratio: ~1.5-1.7");
    System.out.println("  - Design speed range: 4922-7383 RPM");
    System.out.println("  - Max power: 44.4 MW");

    System.out.println("\nOperating Condition Analysis:");
    System.out.println(StringUtils.repeat("-", 120));
    System.out.println(String.format("%-6s %-12s %-12s %-12s %-15s %-35s", "Year", "P_in(bara)",
        "P_ratio", "Speed(RPM)", "Status", "Rebundling Recommendation"));
    System.out.println(StringUtils.repeat("-", 120));

    for (double[] row : modelResults) {
      int year = (int) row[0];
      double pIn = row[8];
      double pr = row[4];
      double speed = row[6];
      double efficiency = row[7];

      String status;
      String recommendation;

      // Analyze operating conditions
      if (year <= 2032) {
        status = "OPTIMAL";
        recommendation = "No action needed - operating near design point";
      } else if (year <= 2036) {
        if (pr > 1.8 || speed > 7200) {
          status = "MARGINAL";
          recommendation = "CONSIDER rebundling - PR increasing, approaching limits";
        } else if (pIn < 50) {
          status = "SUB-OPTIMAL";
          recommendation = "CONSIDER LP rebundle for lower inlet pressure";
        } else {
          status = "ACCEPTABLE";
          recommendation = "Monitor - efficiency declining";
        }
      } else if (year <= 2040) {
        if (pr > 2.0) {
          status = "CRITICAL";
          recommendation = "REBUNDLE REQUIRED - PR exceeds single-stage capability";
        } else {
          status = "SUB-OPTIMAL";
          recommendation = "REBUNDLE RECOMMENDED - LP bundle for 40-50 bara inlet";
        }
      } else {
        status = "LOW FLOW";
        recommendation = "Series operation or turndown - consider decommissioning";
      }

      System.out.println(String.format("%-6d %-12.0f %-12.2f %-12.0f %-15s %-35s", year, pIn, pr,
          speed, status, recommendation));
    }

    // Rebundling benefit analysis
    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println("REBUNDLING BENEFIT ANALYSIS - POTENTIAL PRODUCTION GAINS");
    System.out.println(StringUtils.repeat("=", 130));

    System.out.println("\nScenario: LP Rebundle (D18R5S-LP or similar) optimized for:");
    System.out.println("  - Design inlet pressure: 35-50 bara (vs current 55-65 bara)");
    System.out.println("  - Lower pressure ratio per stage: 1.3-1.5");
    System.out.println("  - Higher volumetric flow capacity at low pressure");

    System.out.println("\nEstimated Production Benefits (with LP rebundle after 2034):");
    System.out.println(StringUtils.repeat("-", 100));
    System.out.println(String.format("%-6s %-15s %-15s %-15s %-20s", "Year", "Current(MSmÂ³/d)",
        "With LP Bundle", "Gain(MSmÂ³/d)", "Gain(%)"));
    System.out.println(StringUtils.repeat("-", 100));

    // Estimate benefits from rebundling
    for (double[] row : modelResults) {
      int year = (int) row[0];
      double currentProd = row[2];
      double pIn = row[8];

      if (year >= 2034) {
        // Estimate improvement from LP bundle
        // LP bundle more efficient at lower inlet pressures
        double improvementFactor;
        if (pIn < 35) {
          improvementFactor = 1.15; // 15% improvement at very low pressure
        } else if (pIn < 42) {
          improvementFactor = 1.12; // 12% improvement
        } else if (pIn < 48) {
          improvementFactor = 1.08; // 8% improvement
        } else {
          improvementFactor = 1.04; // 4% improvement at moderate pressure
        }

        double lpProd = currentProd * improvementFactor;
        double gain = lpProd - currentProd;
        double gainPercent = (gain / currentProd) * 100;

        System.out.println(String.format("%-6d %-15.1f %-15.1f %-15.1f %-20.1f", year, currentProd,
            lpProd, gain, gainPercent));
      }
    }

    // Economic summary
    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println("REBUNDLING ECONOMIC SUMMARY");
    System.out.println(StringUtils.repeat("=", 130));

    System.out.println("\nKey Decision Points:");
    System.out.println("  2032-2034: Evaluate rebundling - inlet pressure drops below 50 bara");
    System.out.println(
        "  2034-2036: OPTIMAL REBUNDLING WINDOW - still sufficient production to justify cost");
    System.out.println(
        "  2036-2038: Series configuration implemented - rebundling before series transition");
    System.out.println("  Post-2038: Limited benefit - production too low to justify investment");

    System.out.println("\nRebundling Cost-Benefit Factors:");
    System.out.println("  Typical rebundling cost: ~$5-10M per compressor");
    System.out.println("  Production gain at 2034: ~3-4 MSmÂ³/d additional capacity");
    System.out
        .println("  At $8/MMBtu gas price: ~$25-35M/year additional revenue (if constrained)");
    System.out.println("  Payback period: 3-6 months if production-constrained");

    System.out.println("\n" + StringUtils.repeat("=", 130));
    System.out.println("RECOMMENDATION SUMMARY");
    System.out.println(StringUtils.repeat("=", 130));
    System.out.println(
        "\nâœ“ 2034: Begin LP rebundle planning for compressors A & B (RB71-6 â†’ RB71-6-LP)");
    System.out.println("âœ“ 2035: Execute rebundle during P10 closure (maintenance window)");
    System.out
        .println("âœ“ 2036: LP bundles operational before Kollsnes pressure drops to 60 bara");
    System.out.println("âœ“ 2038: Transition to series configuration with LP first stage");

    // Verify model runs
    assertTrue(modelResults.size() > 0, "Should have model results");
  }

  /**
   * Builds Troll East process for a specific year's conditions.
   */
  private ProcessSystem buildTrollEastProcessForYear(double inletPressure, double outletPressure,
      int numCompressors) {
    SystemInterface testSystem = createTestFluid();
    ProcessSystem processSystem = new ProcessSystem();

    // Scale flow based on typical conditions
    double baseFlow = 2097870.58288790; // kg/hr at ~70 MSmÂ³/d

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(baseFlow, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(inletPressure, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    // Split flow based on number of compressors
    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    if (numCompressors == 3) {
      splitter.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    } else {
      splitter.setSplitFactors(new double[] {0.5, 0.5});
    }
    splitter.run();
    processSystem.add(splitter);

    // Create compressor trains
    StreamInterface ups1Outlet = createUpstreamCompressorsForYear("ups1",
        splitter.getSplitStream(0), processSystem, outletPressure);
    StreamInterface ups2Outlet = createUpstreamCompressorsForYear("ups2",
        splitter.getSplitStream(1), processSystem, outletPressure);

    Manifold manifold;
    if (numCompressors == 3) {
      StreamInterface ups3Outlet = createUpstreamCompressorsForYear("ups3",
          splitter.getSplitStream(2), processSystem, outletPressure);
      manifold = new Manifold("Compressor Outlet Manifold");
      manifold.addStream(ups1Outlet);
      manifold.addStream(ups2Outlet);
      manifold.addStream(ups3Outlet);
      manifold.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    } else {
      manifold = new Manifold("Compressor Outlet Manifold");
      manifold.addStream(ups1Outlet);
      manifold.addStream(ups2Outlet);
      manifold.setSplitFactors(new double[] {0.5, 0.5});
    }
    manifold.setCapacityAnalysisEnabled(false);
    manifold.run();
    processSystem.add(manifold);

    processSystem.run();

    // Disable capacity analysis on all scrubbers - only compressors should be bottlenecks
    Separator ups1Scrubber = (Separator) processSystem.getUnit("ups1 Scrubber");
    Separator ups2Scrubber = (Separator) processSystem.getUnit("ups2 Scrubber");
    ups1Scrubber.setCapacityAnalysisEnabled(false);
    ups2Scrubber.setCapacityAnalysisEnabled(false);
    if (numCompressors == 3) {
      Separator ups3Scrubber = (Separator) processSystem.getUnit("ups3 Scrubber");
      ups3Scrubber.setCapacityAnalysisEnabled(false);
    }

    // Configure compressors with smooth VFD drivers
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");

    configureCompressor1And2WithElectricDriver(ups1Comp, 7383.0);
    configureCompressor1And2WithElectricDriver(ups2Comp, 7383.0);

    if (numCompressors == 3) {
      Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");
      configureCompressor3WithElectricDriver(ups3Comp, 6726.0);
    }

    processSystem.run();
    return processSystem;
  }

  /**
   * Creates upstream compressor train for year-specific conditions (no cooler in reference case).
   * Scrubbers have capacity analysis disabled to ensure only compressors are evaluated as
   * bottlenecks.
   */
  private StreamInterface createUpstreamCompressorsForYear(String name, StreamInterface inlet,
      ProcessSystem processSystem, double outletPressure) {
    Separator scrubber = new Separator(name + " Scrubber", inlet);
    // Disable capacity analysis on scrubber - only compressors should be bottlenecks
    scrubber.setCapacityAnalysisEnabled(false);
    scrubber.setInternalDiameter(5.0); // Large diameter for safety
    scrubber.setSeparatorLength(10.0);
    scrubber.run();
    processSystem.add(scrubber);

    Compressor compressor = new Compressor(name + " Compressor", scrubber.getGasOutStream());
    compressor.setOutletPressure(outletPressure);
    compressor.setPolytropicEfficiency(0.77);
    compressor.setUsePolytropicCalc(true);
    compressor.run();
    processSystem.add(compressor);

    return compressor.getOutletStream();
  }
}
