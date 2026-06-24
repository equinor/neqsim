package neqsim.process.util.example;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.compressor.DriverType;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.ManipulatedVariable;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.ParetoResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemPrEos;

public class testTr {
  private static final Logger logger = LogManager.getLogger(testTr.class);

  /**
   * Creates and configures a test fluid system with natural gas composition.
   *
   * @return configured SystemPrEos fluid
   */
  private SystemPrEos createTestFluid() {
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
   * Creates a processing train with pipe, separator, and outlet pipe.
   *
   * @param trainName unique name for this train
   * @param inletStream the inlet stream to the train
   * @param processSystem the process system to add equipment to
   * @return the outlet pipe from the train
   */
  private PipeBeggsAndBrills createProcessingTrain(String trainName,
      neqsim.process.equipment.stream.StreamInterface inletStream, ProcessSystem processSystem) {

    PipeBeggsAndBrills inletPipe = new PipeBeggsAndBrills(trainName + " Inlet Pipe", inletStream);
    inletPipe.setLength(100.0); // meters
    inletPipe.setDiameter(0.7); // meters
    inletPipe.setPipeWallRoughness(15e-6);
    inletPipe.setElevation(0);
    inletPipe.run();
    processSystem.add(inletPipe);

    ThreePhaseSeparator separator = new ThreePhaseSeparator(trainName + " Separator", inletPipe.getOutletStream());
    separator.run();
    processSystem.add(separator);

    PipeBeggsAndBrills outletPipe = new PipeBeggsAndBrills(trainName + " Outlet Pipe", separator.getGasOutStream());
    outletPipe.setLength(100.0); // meters
    outletPipe.setDiameter(0.7); // meters
    outletPipe.setPipeWallRoughness(15e-6);
    outletPipe.setElevation(0);
    outletPipe.setNumberOfIncrements(10);
    outletPipe.run();
    processSystem.add(outletPipe);

    return outletPipe;
  }

  private StreamInterface createUpstreamCompressors(String trainName,
      neqsim.process.equipment.stream.StreamInterface inletStream, ProcessSystem processSystem) {

    // Print inlet conditions to diagnose pressure drop issues
    logger.info("\n=== " + trainName + " INLET CONDITIONS ===");
    System.out.println("Inlet Pressure: " + String.format("%.2f bara", inletStream.getPressure("bara")));
    System.out.println("Inlet Flow: " + String.format("%.0f kg/hr", inletStream.getFlowRate("kg/hr")));
    logger.info("Gas Density: " + String.format("%.2f kg/m3", inletStream.getFluid().getDensity("kg/m3")));

    PipeBeggsAndBrills inletPipe = new PipeBeggsAndBrills(trainName + " ups Pipe", inletStream);
    inletPipe.setLength(100); // meters (shorter pipe)
    inletPipe.setDiameter(0.75); // meters (larger diameter to reduce pressure drop)
    inletPipe.setPipeWallRoughness(15e-6);
    inletPipe.setElevation(0);
    inletPipe.run();
    processSystem.add(inletPipe);

    Separator separator = new Separator(trainName + " ups Separator", inletPipe.getOutletStream());
    separator.run();
    processSystem.add(separator);

    PipeBeggsAndBrills outletPipe = new PipeBeggsAndBrills(trainName + " ups Outlet Pipe", separator.getGasOutStream());
    outletPipe.setLength(50.0); // meters (shorter pipe)
    outletPipe.setDiameter(0.75); // meters (larger diameter)
    outletPipe.setPipeWallRoughness(15e-6);
    outletPipe.setElevation(0);
    outletPipe.run();
    processSystem.add(outletPipe);

    Compressor compressor = new Compressor(trainName + " Compressor", outletPipe.getOutletStream());
    compressor.setOutletPressure(110.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.85);
    compressor.setSpeed(8000);
    compressor.run();
    processSystem.add(compressor);

    return compressor.getOutletStream();
  }

  @Test
  public void testBottleneck2() throws Exception {
    SystemPrEos testSystem = createTestFluid();

    ProcessSystem processSystem = new ProcessSystem();

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(2097870.58288790, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    // Saturate the stream with water using StreamSaturatorUtil
    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    // Get the water-saturated outlet stream
    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] { 0.25, 0.25, 0.25, 0.25 });
    splitter.run();
    processSystem.add(splitter);

    // Create processing trains for each split stream
    PipeBeggsAndBrills train1Outlet = createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train2Outlet = createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train3Outlet = createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train4Outlet = createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    Splitter splitter2 = new Splitter("Test Splitter2", finalSeparator.getGasOutStream());
    splitter2.setSplitFactors(new double[] { 0.95 / 3.0, 1.0 / 3.0, 1.05 / 3.0 });
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface upstreamCompressorTrain1 = createUpstreamCompressors("ups1", splitter2.getSplitStream(0),
        processSystem);
    StreamInterface upstreamCompressorTrain2 = createUpstreamCompressors("ups2", splitter2.getSplitStream(1),
        processSystem);
    StreamInterface upstreamCompressorTrain3 = createUpstreamCompressors("ups3", splitter2.getSplitStream(2),
        processSystem);

    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(upstreamCompressorTrain1);
    manifold.addStream(upstreamCompressorTrain2);
    manifold.addStream(upstreamCompressorTrain3);
    manifold.setSplitFactors(new double[] { 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0 });
    // manifold.setHeaderInnerDiameter(1.5);
    // manifold.setBranchInnerDiameter(0.6);
    manifold.run();
    processSystem.add(manifold);

    // Run the process system
    processSystem.run();

    // Auto-size separators and compressors (not pipes)
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof Separator) {
        ((Separator) equipment).autoSize();
      } else if (equipment instanceof Compressor) {
        ((Compressor) equipment).autoSize();
      } else if (equipment instanceof Manifold) {
        ((Manifold) equipment).autoSize();
      }
    }

    // Configure ups1 compressor with driver curve (speed-dependent max power)
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    ups1Comp.getMechanicalDesign().setMaxDesignPower(50000.0);
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator1 = new CompressorChartGenerator(ups1Comp);
    generator1.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart1 = generator1.generateCompressorChart("normal curves", 8);
    ups1Comp.setCompressorChart(chart1);
    ups1Comp.getCompressorChart().setUseCompressorChart(true);
    ups1Comp.setSolveSpeed(true);
    // Set up driver with speed-dependent max power curve
    // P_max(N) = maxPower * (a + b*(N/N_rated) + c*(N/N_rated)^2)
    // At N=N_rated (7383 RPM): factor = a + b + c = 1.0 (full power)
    // At N=0.7*N_rated: factor should be ~0.85 (less power at lower speeds)
    CompressorDriver driver1 = new CompressorDriver(DriverType.GAS_TURBINE, 40500.0);
    driver1.setRatedSpeed(7383.0);
    driver1.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2); // Gives ~0.86 at 70% speed, 1.0 at 100%
    ups1Comp.setDriver(driver1);

    // Configure ups2 compressor with driver curve
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");
    ups2Comp.getMechanicalDesign().setMaxDesignPower(50000.0);
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator2 = new CompressorChartGenerator(ups2Comp);
    generator2.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart2 = generator2.generateCompressorChart("normal curves", 8);
    ups2Comp.setCompressorChart(chart2);
    ups2Comp.getCompressorChart().setUseCompressorChart(true);
    ups2Comp.setSolveSpeed(true);
    CompressorDriver driver2 = new CompressorDriver(DriverType.GAS_TURBINE, 40500.0);
    driver2.setRatedSpeed(7383.0);
    driver2.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2);
    ups2Comp.setDriver(driver2);

    // Configure ups3 compressor with driver curve
    Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");
    ups3Comp.getMechanicalDesign().setMaxDesignPower(50000.0);
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator3 = new CompressorChartGenerator(ups3Comp);
    generator3.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart3 = generator3.generateCompressorChart("normal curves", 8);
    ups3Comp.setCompressorChart(chart3);
    ups3Comp.getCompressorChart().setUseCompressorChart(true);
    ups3Comp.setSolveSpeed(true);
    CompressorDriver driver3 = new CompressorDriver(DriverType.GAS_TURBINE, 45000.0);
    driver3.setRatedSpeed(6726.3); // Different rated speed for ups3
    driver3.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2);
    ups3Comp.setDriver(driver3);

    processSystem.run();

    // Get initial values
    double initialFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream().getFlowRate("kg/hr");

    // Change flow rate and run once - tests that flow propagates correctly
    // with hasMultiInputEquipment() detecting multi-input Separator

    inletStream.setFlowRate(2157870.58288790, "kg/hr");
    processSystem.run();

    // Verify flow propagated to compressor
    double newFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream().getFlowRate("kg/hr");

    // Print flow change info
    logger.info("\n=== FLOW PROPAGATION TEST ===");
    logger.info("Initial flow: " + String.format("%.2f kg/hr", initialFlow));
    logger.info("New flow: " + String.format("%.2f kg/hr", newFlow));
    System.out.println("Flow change: " + String.format("%.2f kg/hr", Math.abs(newFlow - initialFlow)));

    // Print pressure out of each pipe in the process
    logger.info("\n=== PIPE VELOCITY ANALYSIS ===");
    double maxAllowedVelocity = 30.0; // m/s - max for gas pipes
    logger.info(String.format("%-25s  %8s  %8s  %10s  %10s  %10s  %8s", "Pipe Name", "Vin", "Vout", "Vmax", "Diameter",
        "Flow", "Status"));
    logger.info(
        String.format("%-25s  %8s  %8s  %10s  %10s  %10s  %8s", "", "(m/s)", "(m/s)", "(m/s)", "(m)", "(kg/hr)", ""));
    for (int i = 0; i < 100; i++) {
      System.out.print("-");
    }

    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
        double vIn = pipe.getInletSuperficialVelocity();
        double vOut = pipe.getOutletSuperficialVelocity();
        double vMax = Math.max(vIn, vOut);
        double diameter = pipe.getDiameter();
        double flowRate = pipe.getInletStream().getFlowRate("kg/hr");
        String status = vMax > maxAllowedVelocity ? "HIGH!" : "OK";
        double utilization = (vMax / maxAllowedVelocity) * 100.0;
        logger.info(String.format("%-25s  %8.2f  %8.2f  %10.2f  %10.3f  %10.0f  %6.1f%%", pipe.getName(), vIn, vOut,
            maxAllowedVelocity, diameter, flowRate, utilization));
      }
    }

    // Print capacity utilization for all equipment
    logger.info("\n=== EQUIPMENT CAPACITY UTILIZATION ===");
    java.util.Map<String, Double> utilizationSummary = processSystem.getCapacityUtilizationSummary();
    for (java.util.Map.Entry<String, Double> entry : utilizationSummary.entrySet()) {
      logger.info(String.format("%-30s: %6.2f%%", entry.getKey(), entry.getValue()));
    }

    // Print compressor power utilization
    Compressor comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    if (comp != null) {
      double powerKW = comp.getPower("kW");
      double maxPower = comp.getMechanicalDesign().maxDesignPower;
      logger.info("\n=== COMPRESSOR POWER ===");
      logger.info("Compressor: " + comp.getName());
      logger.info("Power: " + String.format("%.2f kW (%.2f MW)", powerKW, powerKW / 1000.0));
      if (maxPower > 0) {
        logger.info("Max Design Power: " + String.format("%.2f kW (%.2f MW)", maxPower, maxPower / 1000.0));
        System.out.println("Power Utilization: " + String.format("%.2f%%", (powerKW / maxPower) * 100.0));
      }

      // Print all compressor margins/constraints
      logger.info("\n=== COMPRESSOR MARGINS ===");
      logger.info("Speed: " + String.format("%.0f RPM", comp.getSpeed()));
      logger.info("Max Speed: " + String.format("%.0f RPM", comp.getMaximumSpeed()));
      logger.info("Min Speed: " + String.format("%.0f RPM", comp.getMinimumSpeed()));
      logger.info("Chart Max Speed: " + String.format("%.0f RPM", comp.getCompressorChart().getMaxSpeedCurve()));
      logger.info("Chart Min Speed: " + String.format("%.0f RPM", comp.getCompressorChart().getMinSpeedCurve()));
      System.out.println("Polytropic Head: " + String.format("%.0f J/kg", comp.getPolytropicFluidHead()));
      logger.info("Polytropic Efficiency: " + String.format("%.1f%%", comp.getPolytropicEfficiency() * 100.0));
      logger.info("Distance to Surge: " + String.format("%.2f%%", comp.getDistanceToSurge() * 100.0));
      logger.info("Distance to Stonewall: " + String.format("%.2f%%", comp.getDistanceToStoneWall() * 100.0));
      logger.info("Inlet Volume Flow: " + String.format("%.0f m3/hr", comp.getInletStream().getFlowRate("m3/hr")));
      logger.info("Inlet Pressure: " + String.format("%.2f bara", comp.getInletStream().getPressure("bara")));
      logger.info("Outlet Pressure: " + String.format("%.2f bara", comp.getOutletStream().getPressure("bara")));

      // Print driver curve information for all compressors
      logger.info("\n=== DRIVER CURVE (Speed-Dependent Max Power) ===");
      for (neqsim.process.equipment.ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
        if (equip instanceof Compressor) {
          Compressor compressor = (Compressor) equip;
          CompressorDriver driver = compressor.getDriver();
          if (driver != null) {
            logger.info("\nCompressor: " + compressor.getName());
            logger.info("  Driver Type: " + driver.getDriverType());
            System.out.println("  Rated Power: " + String.format("%.0f kW", driver.getRatedPower()));
            System.out.println("  Rated Speed: " + String.format("%.0f RPM", driver.getRatedSpeed()));
            System.out.println("  Current Speed: " + String.format("%.0f RPM", compressor.getSpeed()));
            double maxPowerAtSpeed = driver.getMaxAvailablePowerAtSpeed(compressor.getSpeed());
            logger.info("  Max Power at Current Speed: " + String.format("%.0f kW", maxPowerAtSpeed));
            System.out.println("  Actual Power: " + String.format("%.0f kW", compressor.getPower("kW")));
            double powerUtilization = compressor.getPower("kW") / maxPowerAtSpeed * 100.0;
            logger.info("  Power Utilization (vs speed-dependent max): " + String.format("%.1f%%", powerUtilization));
          }
        }
      }

      // Print all capacity constraints
      logger.info("\n=== COMPRESSOR CAPACITY CONSTRAINTS ===");
      java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> constraints = comp
          .getCapacityConstraints();
      for (java.util.Map.Entry<String, neqsim.process.equipment.capacity.CapacityConstraint> entry : constraints
          .entrySet()) {
        neqsim.process.equipment.capacity.CapacityConstraint constraint = entry.getValue();
        String labelType = constraint.isMinimumConstraint() ? "min" : "design";
        logger.info(
            String.format("%-20s: %6.2f%% (value=%.2f, %s=%.2f)", entry.getKey(), constraint.getUtilizationPercent(),
                constraint.getCurrentValue(), labelType, constraint.getDisplayDesignValue()));
      }
    }

    // Print equipment near capacity limit
    logger.info("\n=== EQUIPMENT NEAR CAPACITY LIMIT (>90%) ===");
    java.util.List<String> nearLimit = processSystem.getEquipmentNearCapacityLimit();
    if (nearLimit.isEmpty()) {
      logger.info("No equipment near capacity limit");
    } else {
      for (String name : nearLimit) {
        logger.info("  - " + name);
      }
    }

    // Print bottleneck detection results
    neqsim.process.equipment.capacity.BottleneckResult bottleneck = processSystem.findBottleneck();
    logger.info("\n=== BOTTLENECK ANALYSIS ===");
    if (bottleneck != null && bottleneck.hasBottleneck()) {
      logger.info("Bottleneck Equipment: " + bottleneck.getEquipmentName());
      logger.info("Constraint: " + bottleneck.getConstraintName());
      System.out.println("Utilization: " + String.format("%.2f%%", bottleneck.getUtilizationPercent()));
    } else {
      logger.info("No bottleneck detected");
    }
  }

  /**
   * Generates an Eclipse VFP lift curve for the compression system.
   *
   * <p>
   * This method creates a performance table showing the relationship between inlet flow rate and required inlet
   * pressure at different operating conditions (outlet pressure, temperature).
   * </p>
   *
   * @param processSystem the process system containing the compressor
   * @param inletStreamName name of the inlet stream to vary flow rate on
   * @param outletPressures array of outlet pressures to evaluate (bara)
   * @param flowRates array of flow rates to evaluate (kg/hr)
   * @return Eclipse VFP formatted string
   */
  private String generateEclipseLiftCurve(ProcessSystem processSystem, String inletStreamName, double[] outletPressures,
      double[] flowRates) {

    StringBuilder vfp = new StringBuilder();

    // Get the inlet stream to modify flow rates
    Stream inletStream = (Stream) processSystem.getUnit(inletStreamName);
    Compressor compressor = (Compressor) processSystem.getUnit("ups1 Compressor");

    if (inletStream == null || compressor == null) {
      return "-- Error: Could not find inlet stream or compressor\n";
    }

    // Store original values to restore later
    double originalFlow = inletStream.getFlowRate("kg/hr");
    double originalOutletPressure = compressor.getOutletPressure();

    // Header
    vfp.append("-- ============================================================\n");
    vfp.append("-- Eclipse VFP Table for Compression System\n");
    vfp.append("-- Generated by NeqSim\n");
    vfp.append("-- ============================================================\n\n");

    vfp.append("VFPPROD\n");
    vfp.append("-- Table# Datum  FLO   WFR   GFR   THP   ALQ   Units\n");
    vfp.append(String.format("   1     0.0   GAS   WCT   GOR   THP   ''    METRIC /\n\n"));

    // Flow rates (gas production rate in Sm3/d)
    vfp.append("-- Flow rates (1000 Sm3/d)\n");
    vfp.append(" ");
    for (double flow : flowRates) {
      // Convert kg/hr to 1000 Sm3/d (approximate for natural gas)
      double sm3d = flow * 24.0 / 0.75 / 1000.0; // rough conversion
      vfp.append(String.format(" %.1f", sm3d));
    }
    vfp.append(" /\n\n");

    // Outlet pressures (THP = tubing head pressure, here export pressure)
    vfp.append("-- THP values (bara)\n");
    vfp.append(" ");
    for (double pout : outletPressures) {
      vfp.append(String.format(" %.1f", pout));
    }
    vfp.append(" /\n\n");

    // Water cuts (single value for gas system)
    vfp.append("-- Water cuts\n");
    vfp.append("  0.0 /\n\n");

    // GOR values (single value for gas system)
    vfp.append("-- GOR values\n");
    vfp.append("  999999 /\n\n"); // Very high for gas system

    // ALQ (artificial lift) - not used
    vfp.append("-- ALQ values\n");
    vfp.append("  0 /\n\n");

    // BHP table - inlet pressure required for each flow/THP combination
    vfp.append("-- BHP values (required inlet pressure in bara)\n");
    vfp.append("-- Rows: THP values, Columns: Flow rates\n");

    // Matrix to store results [outletPressure][flowRate]
    double[][] inletPressures = new double[outletPressures.length][flowRates.length];
    double[][] compressorPowers = new double[outletPressures.length][flowRates.length];
    double[][] compressorSpeeds = new double[outletPressures.length][flowRates.length];
    double[][] pressureRatios = new double[outletPressures.length][flowRates.length];
    double[][] surgeMargins = new double[outletPressures.length][flowRates.length];
    double[][] utilizationPercent = new double[outletPressures.length][flowRates.length];
    boolean[][] feasible = new boolean[outletPressures.length][flowRates.length];

    // Evaluate each combination
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      compressor.setOutletPressure(outletPressures[iPout], "bara");

      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        inletStream.setFlowRate(flowRates[iFlow], "kg/hr");

        try {
          processSystem.run();

          // Get required inlet pressure and check constraints
          double pIn = compressor.getInletStream().getPressure("bara");
          double pOut = compressor.getOutletStream().getPressure("bara");
          inletPressures[iPout][iFlow] = pIn;
          compressorPowers[iPout][iFlow] = compressor.getPower("kW");
          compressorSpeeds[iPout][iFlow] = compressor.getSpeed();
          pressureRatios[iPout][iFlow] = pOut / pIn;
          surgeMargins[iPout][iFlow] = compressor.getDistanceToSurge() * 100.0;

          // Check if operating point is feasible (within compressor map)
          double speedUtil = compressor.getSpeed() / compressor.getMaximumSpeed() * 100.0;
          double surgeMargin = compressor.getDistanceToSurge() * 100.0;
          double powerUtil = compressor.getPower("kW") / compressor.getMechanicalDesign().maxDesignPower * 100.0;

          utilizationPercent[iPout][iFlow] = Math.max(speedUtil, powerUtil);
          feasible[iPout][iFlow] = surgeMargin > 10 && speedUtil < 105 && powerUtil < 105;

        } catch (Exception e) {
          inletPressures[iPout][iFlow] = 9999.0; // Invalid point
          feasible[iPout][iFlow] = false;
        }
      }
    }

    // Write BHP table
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      vfp.append(String.format("-- THP = %.1f bara\n", outletPressures[iPout]));
      vfp.append(" ");
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        if (feasible[iPout][iFlow]) {
          vfp.append(String.format(" %.2f", inletPressures[iPout][iFlow]));
        } else {
          vfp.append(" 1*"); // Eclipse default value
        }
      }
      vfp.append(" /\n");
    }

    vfp.append("/\n\n");

    // Restore original values
    inletStream.setFlowRate(originalFlow, "kg/hr");
    compressor.setOutletPressure(originalOutletPressure, "bara");
    processSystem.run();

    // Add detailed performance table as comments
    vfp.append("-- ============================================================\n");
    vfp.append("-- DETAILED PERFORMANCE TABLE\n");
    vfp.append("-- ============================================================\n\n");

    vfp.append(String.format("-- %-12s", "Pout(bara)"));
    for (double flow : flowRates) {
      vfp.append(String.format(" %12.0f", flow));
    }
    vfp.append("\n");

    vfp.append("-- Required Inlet Pressure (bara):\n");
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      vfp.append(String.format("-- %-12.1f", outletPressures[iPout]));
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        if (feasible[iPout][iFlow]) {
          vfp.append(String.format(" %12.2f", inletPressures[iPout][iFlow]));
        } else {
          vfp.append(String.format(" %12s", "INFEAS"));
        }
      }
      vfp.append("\n");
    }

    vfp.append("\n-- Compressor Power (kW):\n");
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      vfp.append(String.format("-- %-12.1f", outletPressures[iPout]));
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        if (feasible[iPout][iFlow]) {
          vfp.append(String.format(" %12.0f", compressorPowers[iPout][iFlow]));
        } else {
          vfp.append(String.format(" %12s", "-"));
        }
      }
      vfp.append("\n");
    }

    vfp.append("\n-- Utilization (%):\n");
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      vfp.append(String.format("-- %-12.1f", outletPressures[iPout]));
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        if (feasible[iPout][iFlow]) {
          vfp.append(String.format(" %12.1f", utilizationPercent[iPout][iFlow]));
        } else {
          vfp.append(String.format(" %12s", ">100"));
        }
      }
      vfp.append("\n");
    }

    vfp.append("\n-- Compressor Speed (RPM):\n");
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      vfp.append(String.format("-- %-12.1f", outletPressures[iPout]));
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        if (feasible[iPout][iFlow]) {
          vfp.append(String.format(" %12.0f", compressorSpeeds[iPout][iFlow]));
        } else {
          vfp.append(String.format(" %12s", "-"));
        }
      }
      vfp.append("\n");
    }

    vfp.append("\n-- Pressure Ratio:\n");
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      vfp.append(String.format("-- %-12.1f", outletPressures[iPout]));
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        if (feasible[iPout][iFlow]) {
          vfp.append(String.format(" %12.2f", pressureRatios[iPout][iFlow]));
        } else {
          vfp.append(String.format(" %12s", "-"));
        }
      }
      vfp.append("\n");
    }

    vfp.append("\n-- Surge Margin (%):\n");
    for (int iPout = 0; iPout < outletPressures.length; iPout++) {
      vfp.append(String.format("-- %-12.1f", outletPressures[iPout]));
      for (int iFlow = 0; iFlow < flowRates.length; iFlow++) {
        if (feasible[iPout][iFlow]) {
          vfp.append(String.format(" %12.1f", surgeMargins[iPout][iFlow]));
        } else {
          vfp.append(String.format(" %12s", "-"));
        }
      }
      vfp.append("\n");
    }

    return vfp.toString();
  }

  @Test
  public void testGenerateLiftCurve() {
    SystemPrEos testSystem = createTestFluid();

    ProcessSystem processSystem = new ProcessSystem();

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(2097870.58288790, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    // Saturate the stream with water using StreamSaturatorUtil
    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    // Get the water-saturated outlet stream
    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] { 0.25, 0.25, 0.25, 0.25 });
    splitter.run();
    processSystem.add(splitter);

    // Create processing trains for each split stream
    PipeBeggsAndBrills train1Outlet = createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    PipeBeggsAndBrills train2Outlet = createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    PipeBeggsAndBrills train3Outlet = createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    PipeBeggsAndBrills train4Outlet = createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    Splitter splitter2 = new Splitter("Test Splitter2", finalSeparator.getGasOutStream());
    splitter2.setSplitFactors(new double[] { 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0 });
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface upstreamCompressorTrain1 = createUpstreamCompressors("ups1", splitter2.getSplitStream(0),
        processSystem);
    StreamInterface upstreamCompressorTrain2 = createUpstreamCompressors("ups2", splitter2.getSplitStream(1),
        processSystem);
    StreamInterface upstreamCompressorTrain3 = createUpstreamCompressors("ups3", splitter2.getSplitStream(2),
        processSystem);

    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(upstreamCompressorTrain1);
    manifold.addStream(upstreamCompressorTrain2);
    manifold.addStream(upstreamCompressorTrain3);
    manifold.setSplitFactors(new double[] { 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0 });
    manifold.setHeaderInnerDiameter(1.5);
    manifold.run();
    processSystem.add(manifold);

    // Run the process system
    processSystem.run();

    // Auto-size compressor
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof Separator) {
        ((Separator) equipment).autoSize();
      } else if (equipment instanceof Compressor) {
        ((Compressor) equipment).autoSize();
      } else if (equipment instanceof Manifold) {
        ((Manifold) equipment).autoSize();
      }
    }

    // inletStream.setTemperature(28.5, "C");
    // processSystem.run();
    // Generate lift curve for different outlet pressures and flow rates
    // Note: Total inlet flow is ~2.1 million kg/hr, split to 1/3 going to compressor train
    // So compressor sees about 700,000 kg/hr at baseline
    double[] outletPressures = { 90.0, 100.0, 110.0, 120.0, 130.0 };
    double[] flowRates = { 1500000, 1800000, 2100000, 2400000, 2700000, 3000000 }; // Total inlet
    // flows

    String vfpTable = generateEclipseLiftCurve(processSystem, "Inlet Stream", outletPressures, flowRates);

    logger.info("\n" + vfpTable);

    // Verify we generated a valid table
    Assertions.assertTrue(vfpTable.contains("VFPPROD"));
    Assertions.assertTrue(vfpTable.contains("BHP values"));
  }

  @Test
  public void testBottleneck3() throws Exception {
    SystemPrEos testSystem = createTestFluid();

    ProcessSystem processSystem = new ProcessSystem();

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(2097870.58288790, "kg/hr");
    inletStream.setTemperature(48.5, "C");
    inletStream.setPressure(37.16, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    // Saturate the stream with water using StreamSaturatorUtil
    StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", inletStream);
    saturator.run();
    processSystem.add(saturator);

    // Get the water-saturated outlet stream
    Stream saturatedStream = new Stream("Saturated Stream", saturator.getOutletStream());
    saturatedStream.run();
    processSystem.add(saturatedStream);

    Splitter splitter = new Splitter("Test Splitter", saturatedStream);
    splitter.setSplitFactors(new double[] { 0.25, 0.25, 0.25, 0.25 });
    splitter.run();
    processSystem.add(splitter);

    // Create processing trains for each split stream
    PipeBeggsAndBrills train1Outlet = createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train2Outlet = createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train3Outlet = createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train4Outlet = createProcessingTrain("Train4", splitter.getSplitStream(3), processSystem);

    ThreePhaseSeparator finalSeparator = new ThreePhaseSeparator("Final Separator");
    finalSeparator.addStream(train1Outlet.getOutletStream());
    finalSeparator.addStream(train2Outlet.getOutletStream());
    finalSeparator.addStream(train3Outlet.getOutletStream());
    finalSeparator.addStream(train4Outlet.getOutletStream());
    finalSeparator.setInternalDiameter(3.0);
    finalSeparator.run();
    processSystem.add(finalSeparator);

    Splitter splitter2 = new Splitter("Test Splitter2", finalSeparator.getGasOutStream());
    splitter2.setSplitFactors(new double[] { 0.95 / 3.0, 1.0 / 3.0, 1.05 / 3.0 });
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface upstreamCompressorTrain1 = createUpstreamCompressors("ups1", splitter2.getSplitStream(0),
        processSystem);
    StreamInterface upstreamCompressorTrain2 = createUpstreamCompressors("ups2", splitter2.getSplitStream(1),
        processSystem);
    StreamInterface upstreamCompressorTrain3 = createUpstreamCompressors("ups3", splitter2.getSplitStream(2),
        processSystem);

    Manifold manifold = new Manifold("Compressor Outlet Manifold");
    manifold.addStream(upstreamCompressorTrain1);
    manifold.addStream(upstreamCompressorTrain2);
    manifold.addStream(upstreamCompressorTrain3);
    manifold.setSplitFactors(new double[] { 1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0 });
    manifold.setHeaderInnerDiameter(1.5);
    manifold.setBranchInnerDiameter(0.6);
    manifold.run();
    processSystem.add(manifold);

    // Run the process system
    processSystem.run();

    // Auto-size separators and compressors (not pipes)
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof Separator) {
        ((Separator) equipment).autoSize();
      } else if (equipment instanceof Compressor) {
        ((Compressor) equipment).autoSize();
      } else if (equipment instanceof Manifold) {
        ((Manifold) equipment).autoSize();
      }
    }

    // Configure ups1 compressor with driver curve (speed-dependent max power)
    Compressor ups1Comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    ups1Comp.getMechanicalDesign().setMaxDesignPower(50000.0);
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator1 = new CompressorChartGenerator(ups1Comp);
    generator1.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart1 = generator1.generateCompressorChart("normal curves", 8);
    ups1Comp.setCompressorChart(chart1);
    ups1Comp.getCompressorChart().setUseCompressorChart(true);
    ups1Comp.setSolveSpeed(true);
    // Set up driver with speed-dependent max power curve
    // P_max(N) = maxPower * (a + b*(N/N_rated) + c*(N/N_rated)^2)
    // At N=N_rated (7383 RPM): factor = a + b + c = 1.0 (full power)
    // At N=0.7*N_rated: factor should be ~0.85 (less power at lower speeds)
    CompressorDriver driver1 = new CompressorDriver(DriverType.GAS_TURBINE, 40500.0);
    driver1.setRatedSpeed(7383.0);
    driver1.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2); // Gives ~0.86 at 70% speed, 1.0 at 100%
    ups1Comp.setDriver(driver1);

    // Configure ups2 compressor with driver curve
    Compressor ups2Comp = (Compressor) processSystem.getUnit("ups2 Compressor");
    ups2Comp.getMechanicalDesign().setMaxDesignPower(50000.0);
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator2 = new CompressorChartGenerator(ups2Comp);
    generator2.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart2 = generator2.generateCompressorChart("normal curves", 8);
    ups2Comp.setCompressorChart(chart2);
    ups2Comp.getCompressorChart().setUseCompressorChart(true);
    ups2Comp.setSolveSpeed(true);
    CompressorDriver driver2 = new CompressorDriver(DriverType.GAS_TURBINE, 40500.0);
    driver2.setRatedSpeed(7383.0);
    driver2.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2);
    ups2Comp.setDriver(driver2);

    // Configure ups3 compressor with driver curve
    Compressor ups3Comp = (Compressor) processSystem.getUnit("ups3 Compressor");
    ups3Comp.getMechanicalDesign().setMaxDesignPower(50000.0);
    // Generate compressor chart programmatically instead of loading from JSON
    CompressorChartGenerator generator3 = new CompressorChartGenerator(ups3Comp);
    generator3.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart3 = generator3.generateCompressorChart("normal curves", 8);
    ups3Comp.setCompressorChart(chart3);
    ups3Comp.getCompressorChart().setUseCompressorChart(true);
    ups3Comp.setSolveSpeed(true);
    CompressorDriver driver3 = new CompressorDriver(DriverType.GAS_TURBINE, 45000.0);
    driver3.setRatedSpeed(6726.3); // Different rated speed for ups3
    driver3.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2);
    ups3Comp.setDriver(driver3);

    // Set max discharge temperature constraint (168°C) for all compressors
    // This will create a capacity constraint that is checked during optimization
    ups1Comp.setMaxDischargeTemperature(168.0, "C");
    ups2Comp.setMaxDischargeTemperature(168.0, "C");
    ups3Comp.setMaxDischargeTemperature(168.0, "C");

    // Reinitialize capacity constraints to include discharge temperature
    ups1Comp.reinitializeCapacityConstraints();
    ups2Comp.reinitializeCapacityConstraints();
    ups3Comp.reinitializeCapacityConstraints();

    processSystem.run();

    // Get initial values
    double initialFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream().getFlowRate("kg/hr");

    // Change flow rate and run once - tests that flow propagates correctly
    // with hasMultiInputEquipment() detecting multi-input Separator
    inletStream.setFlowRate(1957870.58288790, "kg/hr");
    processSystem.run();

    // Verify flow propagated to compressor
    double newFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream().getFlowRate("kg/hr");

    // Print flow change info
    logger.info("\n=== FLOW PROPAGATION TEST ===");
    logger.info("Initial flow: " + String.format("%.2f kg/hr", initialFlow));
    logger.info("New flow: " + String.format("%.2f kg/hr", newFlow));
    System.out.println("Flow change: " + String.format("%.2f kg/hr", Math.abs(newFlow - initialFlow)));

    // Print pressure out of each pipe in the process
    logger.info("\n=== PIPE VELOCITY ANALYSIS ===");
    double maxAllowedVelocity = 30.0; // m/s - max for gas pipes
    logger.info(String.format("%-25s  %8s  %8s  %10s  %10s  %10s  %8s", "Pipe Name", "Vin", "Vout", "Vmax", "Diameter",
        "Flow", "Status"));
    logger.info(
        String.format("%-25s  %8s  %8s  %10s  %10s  %10s  %8s", "", "(m/s)", "(m/s)", "(m/s)", "(m)", "(kg/hr)", ""));
    for (int i = 0; i < 100; i++) {
      System.out.print("-");
    }

    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
        double vIn = pipe.getInletSuperficialVelocity();
        double vOut = pipe.getOutletSuperficialVelocity();
        double vMax = Math.max(vIn, vOut);
        double diameter = pipe.getDiameter();
        double flowRate = pipe.getInletStream().getFlowRate("kg/hr");
        String status = vMax > maxAllowedVelocity ? "HIGH!" : "OK";
        double utilization = (vMax / maxAllowedVelocity) * 100.0;
        logger.info(String.format("%-25s  %8.2f  %8.2f  %10.2f  %10.3f  %10.0f  %6.1f%%", pipe.getName(), vIn, vOut,
            maxAllowedVelocity, diameter, flowRate, utilization));
      }
    }

    // Print capacity utilization for all equipment
    logger.info("\n=== EQUIPMENT CAPACITY UTILIZATION ===");
    java.util.Map<String, Double> utilizationSummary = processSystem.getCapacityUtilizationSummary();
    for (java.util.Map.Entry<String, Double> entry : utilizationSummary.entrySet()) {
      logger.info(String.format("%-30s: %6.2f%%", entry.getKey(), entry.getValue()));
    }

    // Print compressor power utilization
    Compressor comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    if (comp != null) {
      double powerKW = comp.getPower("kW");
      double maxPower = comp.getMechanicalDesign().maxDesignPower;
      logger.info("\n=== COMPRESSOR POWER ===");
      logger.info("Compressor: " + comp.getName());
      logger.info("Power: " + String.format("%.2f kW (%.2f MW)", powerKW, powerKW / 1000.0));
      if (maxPower > 0) {
        logger.info("Max Design Power: " + String.format("%.2f kW (%.2f MW)", maxPower, maxPower / 1000.0));
        System.out.println("Power Utilization: " + String.format("%.2f%%", (powerKW / maxPower) * 100.0));
      }

      // Print all compressor margins/constraints
      logger.info("\n=== COMPRESSOR MARGINS ===");
      logger.info("Speed: " + String.format("%.0f RPM", comp.getSpeed()));
      logger.info("Max Speed: " + String.format("%.0f RPM", comp.getMaximumSpeed()));
      logger.info("Min Speed: " + String.format("%.0f RPM", comp.getMinimumSpeed()));
      logger.info("Chart Max Speed: " + String.format("%.0f RPM", comp.getCompressorChart().getMaxSpeedCurve()));
      logger.info("Chart Min Speed: " + String.format("%.0f RPM", comp.getCompressorChart().getMinSpeedCurve()));
      System.out.println("Polytropic Head: " + String.format("%.0f J/kg", comp.getPolytropicFluidHead()));
      logger.info("Polytropic Efficiency: " + String.format("%.1f%%", comp.getPolytropicEfficiency() * 100.0));
      logger.info("Distance to Surge: " + String.format("%.2f%%", comp.getDistanceToSurge() * 100.0));
      logger.info("Distance to Stonewall: " + String.format("%.2f%%", comp.getDistanceToStoneWall() * 100.0));
      logger.info("Inlet Volume Flow: " + String.format("%.0f m3/hr", comp.getInletStream().getFlowRate("m3/hr")));
      logger.info("Inlet Pressure: " + String.format("%.2f bara", comp.getInletStream().getPressure("bara")));
      logger.info("Outlet Pressure: " + String.format("%.2f bara", comp.getOutletStream().getPressure("bara")));

      // Print driver curve information for all compressors
      logger.info("\n=== DRIVER CURVE (Speed-Dependent Max Power) ===");
      for (neqsim.process.equipment.ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
        if (equip instanceof Compressor) {
          Compressor compressor = (Compressor) equip;
          CompressorDriver driver = compressor.getDriver();
          if (driver != null) {
            logger.info("\nCompressor: " + compressor.getName());
            logger.info("  Driver Type: " + driver.getDriverType());
            System.out.println("  Rated Power: " + String.format("%.0f kW", driver.getRatedPower()));
            System.out.println("  Rated Speed: " + String.format("%.0f RPM", driver.getRatedSpeed()));
            System.out.println("  Current Speed: " + String.format("%.0f RPM", compressor.getSpeed()));
            double maxPowerAtSpeed = driver.getMaxAvailablePowerAtSpeed(compressor.getSpeed());
            logger.info("  Max Power at Current Speed: " + String.format("%.0f kW", maxPowerAtSpeed));
            System.out.println("  Actual Power: " + String.format("%.0f kW", compressor.getPower("kW")));
            double powerUtilization = compressor.getPower("kW") / maxPowerAtSpeed * 100.0;
            logger.info("  Power Utilization (vs speed-dependent max): " + String.format("%.1f%%", powerUtilization));
          }
        }
      }

      // Print all capacity constraints
      logger.info("\n=== COMPRESSOR CAPACITY CONSTRAINTS ===");
      java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> constraints = comp
          .getCapacityConstraints();
      for (java.util.Map.Entry<String, neqsim.process.equipment.capacity.CapacityConstraint> entry : constraints
          .entrySet()) {
        neqsim.process.equipment.capacity.CapacityConstraint constraint = entry.getValue();
        String labelType = constraint.isMinimumConstraint() ? "min" : "design";
        logger.info(
            String.format("%-20s: %6.2f%% (value=%.2f, %s=%.2f)", entry.getKey(), constraint.getUtilizationPercent(),
                constraint.getCurrentValue(), labelType, constraint.getDisplayDesignValue()));
      }
    }

    // Print equipment near capacity limit
    logger.info("\n=== EQUIPMENT NEAR CAPACITY LIMIT (>90%) ===");
    java.util.List<String> nearLimit = processSystem.getEquipmentNearCapacityLimit();
    if (nearLimit.isEmpty()) {
      logger.info("No equipment near capacity limit");
    } else {
      for (String name : nearLimit) {
        logger.info("  - " + name);
      }
    }

    // Print bottleneck detection results
    neqsim.process.equipment.capacity.BottleneckResult bottleneck = processSystem.findBottleneck();
    logger.info("\n=== BOTTLENECK ANALYSIS ===");
    if (bottleneck != null && bottleneck.hasBottleneck()) {
      logger.info("Bottleneck Equipment: " + bottleneck.getEquipmentName());
      logger.info("Constraint: " + bottleneck.getConstraintName());
      System.out.println("Utilization: " + String.format("%.2f%%", bottleneck.getUtilizationPercent()));
    } else {
      logger.info("No bottleneck detected");
    }

    // Print discharge temperature for all compressors
    logger.info("\n=== COMPRESSOR DISCHARGE TEMPERATURES ===");
    System.out.println(String.format("Max allowable: %.0f°C", ups1Comp.getMaxDischargeTemperature("C")));
    for (neqsim.process.equipment.ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
      if (equip instanceof Compressor) {
        Compressor c = (Compressor) equip;
        if (c.getName().startsWith("ups")) {
          double dischargeTempC = c.getOutletStream().getTemperature("C");
          double maxTempC = c.getMaxDischargeTemperature("C");
          double tempUtilization = (dischargeTempC / maxTempC) * 100.0;
          String status = tempUtilization > 100.0 ? "EXCEEDED!" : (tempUtilization > 90.0 ? "WARNING" : "OK");
          logger.info(String.format("  %-15s: %.1f°C / %.1f°C (%.1f%%) %s", c.getName(), dischargeTempC, maxTempC,
              tempUtilization, status));
        }
      }
    }

    // ============================================
    // PRODUCTION OPTIMIZATION
    // Find maximum production possible while respecting all constraints
    // Using ProductionOptimizer with proper search algorithms
    // ============================================
    StringBuilder separator = new StringBuilder();
    for (int i = 0; i < 60; i++) {
      separator.append("=");
    }
    logger.info("\n" + separator.toString());
    logger.info("=== PRODUCTION OPTIMIZATION ===");
    logger.info(separator.toString());

    // Get the inlet stream to use as the decision variable
    Stream feedStream = (Stream) processSystem.getUnit("Inlet Stream");
    double currentFlow = feedStream.getFlowRate("kg/hr");
    logger.info("\nCurrent inlet flow: " + String.format("%.0f kg/hr", currentFlow));

    // Initialize mechanical design for pipes so optimizer can evaluate velocity constraints
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof PipeBeggsAndBrills) {
        ((PipeBeggsAndBrills) equipment).initMechanicalDesign();
      }
    }

    // Store original flow
    double originalFlow = currentFlow;

    // ============================================
    // Single-Variable Optimization using ProductionOptimizer
    // ============================================
    logger.info("\nRunning single-variable optimization with ProductionOptimizer...\n");

    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Configure optimization bounds and settings
    double lowFlow = currentFlow * 0.5;
    double highFlow = currentFlow * 1.5;

    OptimizationConfig singleVarConfig = new OptimizationConfig(lowFlow, highFlow).rateUnit("kg/hr")
        .tolerance(currentFlow * 0.005) // 0.5%
        // tolerance
        .maxIterations(25).defaultUtilizationLimit(1.0) // 100% utilization limit
        .searchMode(SearchMode.GOLDEN_SECTION_SCORE);

    // Define throughput maximization objective
    OptimizationObjective throughputObjective = new OptimizationObjective("throughput",
        proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);

    // Run single-variable optimization
    OptimizationResult singleVarResult = optimizer.optimize(processSystem, feedStream, singleVarConfig,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    // Print single-variable optimization results
    logger.info("=== SINGLE-VARIABLE OPTIMIZATION RESULTS ===");
    logger.info("Optimal flow rate: " + String.format("%.0f kg/hr", singleVarResult.getOptimalRate()));
    logger.info("Feasible: " + singleVarResult.isFeasible());
    logger.info("Iterations: " + singleVarResult.getIterations());
    if (singleVarResult.getBottleneck() != null) {
      logger.info("Bottleneck: " + singleVarResult.getBottleneck().getName());
      logger.info(
          "Bottleneck utilization: " + String.format("%.1f%%", singleVarResult.getBottleneckUtilization() * 100.0));
    }

    // Calculate production increase potential
    double bestFeasibleFlow = singleVarResult.getOptimalRate();
    double productionIncrease = bestFeasibleFlow - originalFlow;
    double increasePercent = (productionIncrease / originalFlow) * 100.0;
    logger.info("\n=== PRODUCTION POTENTIAL ===");
    logger.info("Current production: " + String.format("%.0f kg/hr", originalFlow));
    logger.info("Maximum production: " + String.format("%.0f kg/hr", bestFeasibleFlow));
    if (productionIncrease > 0) {
      logger.info("Potential increase: " + String.format("%.0f kg/hr (+%.1f%%)", productionIncrease, increasePercent));
    } else if (productionIncrease < 0) {
      logger.info("Current production exceeds capacity - reduce by: "
          + String.format("%.0f kg/hr (%.1f%%)", Math.abs(productionIncrease), Math.abs(increasePercent)));
    } else {
      logger.info("Operating at optimal capacity");
    }

    // ============================================
    // MULTI-VARIABLE OPTIMIZATION WITH SPLIT FACTORS
    // Using ProductionOptimizer with ManipulatedVariable to optimize both
    // total flow AND split factors simultaneously using Nelder-Mead.
    // ============================================
    logger.info("\n" + separator.toString());
    logger.info("=== MULTI-VARIABLE OPTIMIZATION ===");
    logger.info("Optimizing: Inlet Flow + Splitter Distribution");
    logger.info(separator.toString());

    // Get the splitter that distributes flow to the 3 compressor trains
    final Splitter optimizerSplitter = (Splitter) processSystem.getUnit("Test Splitter2");

    logger.info("\nUsing ProductionOptimizer with Nelder-Mead for multi-variable optimization...\n");

    // Define manipulated variables for multi-variable optimization
    // Variable 1: Inlet flow rate
    // Variable 2: Balance factor (controls split distribution)
    final double baseFlow = originalFlow;
    ManipulatedVariable flowVar = new ManipulatedVariable("InletFlow", baseFlow * 0.90, // lower
        // bound
        baseFlow * 1.15, // upper bound
        "kg/hr", (proc, val) -> {
          Stream inlet = (Stream) proc.getUnit("Inlet Stream");
          inlet.setFlowRate(val, "kg/hr");
        });

    // Balance factor: controls how flow is distributed
    // -0.10 to +0.10, where 0 = equal split
    ManipulatedVariable balanceVar = new ManipulatedVariable("BalanceFactor", -0.10, // lower bound
        0.10, // upper bound
        "fraction", (proc, val) -> {
          Splitter splitUnit = (Splitter) proc.getUnit("Test Splitter2");
          double baseVal = 1.0 / 3.0;
          double f1 = Math.max(0.2, Math.min(0.5, baseVal + val));
          double f3 = Math.max(0.2, Math.min(0.5, baseVal - val));
          double f2 = 1.0 - f1 - f3;
          splitUnit.setSplitFactors(new double[] { f1, f2, f3 });
        });

    List<ManipulatedVariable> multiVariables = Arrays.asList(flowVar, balanceVar);

    // Configure multi-variable optimization
    OptimizationConfig multiVarConfig = new OptimizationConfig(baseFlow * 0.90, baseFlow * 1.15).rateUnit("kg/hr")
        .tolerance(baseFlow * 0.005).maxIterations(30).defaultUtilizationLimit(1.0)
        .searchMode(SearchMode.NELDER_MEAD_SCORE);

    // Run multi-variable optimization
    OptimizationResult multiVarResult = optimizer.optimize(processSystem, multiVariables, multiVarConfig,
        Collections.singletonList(throughputObjective), Collections.emptyList());

    // Get optimized values
    double bestFlow = multiVarResult.getDecisionVariables().getOrDefault("InletFlow", baseFlow);
    double bestBalanceFactor = multiVarResult.getDecisionVariables().getOrDefault("BalanceFactor", 0.0);

    // Calculate final split factors
    double base = 1.0 / 3.0;
    double bestF1 = Math.max(0.2, Math.min(0.5, base + bestBalanceFactor));
    double bestF3 = Math.max(0.2, Math.min(0.5, base - bestBalanceFactor));
    double bestF2 = 1.0 - bestF1 - bestF3;

    logger.info("=== MULTI-VARIABLE OPTIMIZATION RESULTS ===");
    logger.info("Optimal inlet flow: " + String.format("%.0f kg/hr", bestFlow));
    logger.info("Optimal balance factor: " + String.format("%.3f", bestBalanceFactor));
    logger.info("Optimal split factors:");
    logger.info("  Train 1 (ups1): " + String.format("%.1f%%", bestF1 * 100));
    logger.info("  Train 2 (ups2): " + String.format("%.1f%%", bestF2 * 100));
    logger.info("  Train 3 (ups3): " + String.format("%.1f%%", bestF3 * 100));
    logger.info("Feasible: " + multiVarResult.isFeasible());
    logger.info("Iterations: " + multiVarResult.getIterations());
    if (multiVarResult.getBottleneck() != null) {
      logger.info("Limiting equipment: " + multiVarResult.getBottleneck().getName());
      logger.info("Max utilization: " + String.format("%.1f%%", multiVarResult.getBottleneckUtilization() * 100.0));
    }

    // Compare with initial
    double improvement = (bestFlow - originalFlow) / originalFlow * 100;
    logger.info("\n=== COMPARISON ===");
    logger.info("Initial flow: " + String.format("%.0f kg/hr", originalFlow));
    logger.info("Optimized flow: " + String.format("%.0f kg/hr", bestFlow));
    logger.info("Improvement: " + String.format("%.1f%%", improvement));

    // ============================================
    // PARETO MULTI-OBJECTIVE OPTIMIZATION
    // Demonstrate Pareto optimization with two objectives:
    // 1. Maximize throughput
    // 2. Minimize total compressor power
    // ============================================
    logger.info("\n" + separator.toString());
    logger.info("=== PARETO MULTI-OBJECTIVE OPTIMIZATION ===");
    logger.info("Objectives: Maximize Throughput vs Minimize Power");
    logger.info(separator.toString());

    // Define objectives for Pareto optimization
    OptimizationObjective powerObjective = new OptimizationObjective("totalPower", proc -> {
      double totalPower = 0;
      for (neqsim.process.equipment.ProcessEquipmentInterface eq : proc.getUnitOperations()) {
        if (eq instanceof Compressor && eq.getName().startsWith("ups")) {
          totalPower += ((Compressor) eq).getPower("kW");
        }
      }
      return totalPower;
    }, 1.0, ObjectiveType.MINIMIZE);

    List<OptimizationObjective> paretoObjectives = Arrays.asList(throughputObjective, powerObjective);

    // Configure Pareto optimization
    OptimizationConfig paretoConfig = new OptimizationConfig(baseFlow * 0.85, baseFlow * 1.10).rateUnit("kg/hr")
        .tolerance(baseFlow * 0.01).maxIterations(15).defaultUtilizationLimit(1.0)
        .searchMode(SearchMode.GOLDEN_SECTION_SCORE).paretoGridSize(7); // 7 weight combinations

    // Run Pareto optimization
    ParetoResult paretoResult = optimizer.optimizePareto(processSystem, feedStream, paretoConfig, paretoObjectives,
        Collections.emptyList());

    // Print Pareto results
    logger.info("\nPareto Front Size: " + paretoResult.getParetoFrontSize());
    logger.info("Total Iterations: " + paretoResult.getTotalIterations());
    logger.info("\nPareto Front:");
    logger.info(paretoResult.toMarkdownTable());

    // Print utopia and nadir points
    logger.info("Utopia Point (best individual values):");
    for (java.util.Map.Entry<String, Double> entry : paretoResult.getUtopiaPoint().entrySet()) {
      logger.info("  " + entry.getKey() + ": " + String.format("%.2f", entry.getValue()));
    }
    logger.info("\nNadir Point (worst values on Pareto front):");
    for (java.util.Map.Entry<String, Double> entry : paretoResult.getNadirPoint().entrySet()) {
      logger.info("  " + entry.getKey() + ": " + String.format("%.2f", entry.getValue()));
    }

    // Restore best solution from single-variable optimization and show final state
    feedStream.setFlowRate(singleVarResult.getOptimalRate(), "kg/hr");
    optimizerSplitter.setSplitFactors(new double[] { bestF1, bestF2, bestF3 });
    processSystem.run();

    // Show final compressor power utilization
    logger.info("\n=== FINAL COMPRESSOR POWER UTILIZATION ===");
    for (neqsim.process.equipment.ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
      if (equip instanceof Compressor) {
        Compressor c = (Compressor) equip;
        if (c.getName().startsWith("ups")) {
          double powerUtil = 0;
          if (c.getDriver() != null) {
            powerUtil = c.getPower("kW") / c.getDriver().getMaxAvailablePowerAtSpeed(c.getSpeed()) * 100;
          }
          logger.info(
              String.format("  %-15s: Power=%.0f kW, Utilization=%.1f%%", c.getName(), c.getPower("kW"), powerUtil));
        }
      }
    }
  }
}
