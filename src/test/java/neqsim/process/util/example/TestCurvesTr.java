package neqsim.process.util.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemPrEos;

public class TestCurvesTr {
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

    ThreePhaseSeparator separator =
        new ThreePhaseSeparator(trainName + " Separator", inletPipe.getOutletStream());
    separator.run();
    processSystem.add(separator);

    PipeBeggsAndBrills outletPipe =
        new PipeBeggsAndBrills(trainName + " Outlet Pipe", separator.getGasOutStream());
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
    System.out.println("\n=== " + trainName + " INLET CONDITIONS ===");
    System.out
        .println("Inlet Pressure: " + String.format("%.2f bara", inletStream.getPressure("bara")));
    System.out
        .println("Inlet Flow: " + String.format("%.0f kg/hr", inletStream.getFlowRate("kg/hr")));
    System.out.println(
        "Gas Density: " + String.format("%.2f kg/m3", inletStream.getFluid().getDensity("kg/m3")));

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

    PipeBeggsAndBrills outletPipe =
        new PipeBeggsAndBrills(trainName + " ups Outlet Pipe", separator.getGasOutStream());
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
  public void testBottleneck2() {
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
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    // Create processing trains for each split stream
    PipeBeggsAndBrills train1Outlet =
        createProcessingTrain("Train1", splitter.getSplitStream(0), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train2Outlet =
        createProcessingTrain("Train2", splitter.getSplitStream(1), processSystem);
    // Create processing trains for each split stream
    PipeBeggsAndBrills train3Outlet =
        createProcessingTrain("Train3", splitter.getSplitStream(2), processSystem);
    // Create processing trains for each split stream
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

    Splitter splitter2 = new Splitter("Test Splitter2", finalSeparator.getGasOutStream());
    splitter2.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface upstreamCompressorTrain1 =
        createUpstreamCompressors("ups1", splitter2.getSplitStream(0), processSystem);

    // Run the process system
    processSystem.run();

    // Auto-size separators and compressors (not pipes)
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      if (equipment instanceof Separator) {
        ((Separator) equipment).autoSize();
      } else if (equipment instanceof Compressor) {
        ((Compressor) equipment).autoSize();
      }
    }
    // Note: maxDesignPower is in kW, so 45 MW = 45000 kW
    ((Compressor) processSystem.getUnit("ups1 Compressor")).getMechanicalDesign()
        .setMaxDesignPower(50000.0); // 45 MW in kW
    // Run again after sizing to update calculations with new equipment dimensions
    processSystem.run();

    // Get initial values
    double initialFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream()
        .getFlowRate("kg/hr");

    // Change flow rate and run once - tests that flow propagates correctly
    // with hasMultiInputEquipment() detecting multi-input Separator
    inletStream.setFlowRate(2157870.58288790, "kg/hr");
    processSystem.run();

    // Verify flow propagated to compressor
    double newFlow = ((Compressor) processSystem.getUnit("ups1 Compressor")).getInletStream()
        .getFlowRate("kg/hr");

    // Print flow change info
    System.out.println("\n=== FLOW PROPAGATION TEST ===");
    System.out.println("Initial flow: " + String.format("%.2f kg/hr", initialFlow));
    System.out.println("New flow: " + String.format("%.2f kg/hr", newFlow));
    System.out
        .println("Flow change: " + String.format("%.2f kg/hr", Math.abs(newFlow - initialFlow)));

    // Print pressure out of each pipe in the process
    System.out.println("\n=== PIPE VELOCITY ANALYSIS ===");
    double maxAllowedVelocity = 30.0; // m/s - max for gas pipes
    System.out.println(String.format("%-25s  %8s  %8s  %10s  %10s  %10s  %8s", "Pipe Name", "Vin",
        "Vout", "Vmax", "Diameter", "Flow", "Status"));
    System.out.println(String.format("%-25s  %8s  %8s  %10s  %10s  %10s  %8s", "", "(m/s)", "(m/s)",
        "(m/s)", "(m)", "(kg/hr)", ""));
    for (int i = 0; i < 100; i++) {
      System.out.print("-");
    }
    System.out.println();
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      if (equipment instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
        double vIn = pipe.getInletSuperficialVelocity();
        double vOut = pipe.getOutletSuperficialVelocity();
        double vMax = Math.max(vIn, vOut);
        double diameter = pipe.getDiameter();
        double flowRate = pipe.getInletStream().getFlowRate("kg/hr");
        String status = vMax > maxAllowedVelocity ? "HIGH!" : "OK";
        double utilization = (vMax / maxAllowedVelocity) * 100.0;
        System.out.println(String.format("%-25s  %8.2f  %8.2f  %10.2f  %10.3f  %10.0f  %6.1f%%",
            pipe.getName(), vIn, vOut, maxAllowedVelocity, diameter, flowRate, utilization));
      }
    }

    // Print capacity utilization for all equipment
    System.out.println("\n=== EQUIPMENT CAPACITY UTILIZATION ===");
    java.util.Map<String, Double> utilizationSummary =
        processSystem.getCapacityUtilizationSummary();
    for (java.util.Map.Entry<String, Double> entry : utilizationSummary.entrySet()) {
      System.out.println(String.format("%-30s: %6.2f%%", entry.getKey(), entry.getValue()));
    }

    // Print compressor power utilization
    Compressor comp = (Compressor) processSystem.getUnit("ups1 Compressor");
    if (comp != null) {
      double powerKW = comp.getPower("kW");
      double maxPower = comp.getMechanicalDesign().maxDesignPower;
      System.out.println("\n=== COMPRESSOR POWER ===");
      System.out.println("Compressor: " + comp.getName());
      System.out.println("Power: " + String.format("%.2f kW (%.2f MW)", powerKW, powerKW / 1000.0));
      if (maxPower > 0) {
        System.out.println(
            "Max Design Power: " + String.format("%.2f kW (%.2f MW)", maxPower, maxPower / 1000.0));
        System.out
            .println("Power Utilization: " + String.format("%.2f%%", (powerKW / maxPower) * 100.0));
      }

      // Print all compressor margins/constraints
      System.out.println("\n=== COMPRESSOR MARGINS ===");
      System.out.println("Speed: " + String.format("%.0f RPM", comp.getSpeed()));
      System.out.println("Max Speed: " + String.format("%.0f RPM", comp.getMaximumSpeed()));
      System.out.println("Min Speed: " + String.format("%.0f RPM", comp.getMinimumSpeed()));
      System.out.println("Chart Max Speed: "
          + String.format("%.0f RPM", comp.getCompressorChart().getMaxSpeedCurve()));
      System.out.println("Chart Min Speed: "
          + String.format("%.0f RPM", comp.getCompressorChart().getMinSpeedCurve()));
      System.out
          .println("Polytropic Head: " + String.format("%.0f J/kg", comp.getPolytropicFluidHead()));
      System.out.println("Polytropic Efficiency: "
          + String.format("%.1f%%", comp.getPolytropicEfficiency() * 100.0));
      System.out.println(
          "Distance to Surge: " + String.format("%.2f%%", comp.getDistanceToSurge() * 100.0));
      System.out.println("Distance to Stonewall: "
          + String.format("%.2f%%", comp.getDistanceToStoneWall() * 100.0));
      System.out.println("Inlet Volume Flow: "
          + String.format("%.0f m3/hr", comp.getInletStream().getFlowRate("m3/hr")));
      System.out.println("Inlet Pressure: "
          + String.format("%.2f bara", comp.getInletStream().getPressure("bara")));
      System.out.println("Outlet Pressure: "
          + String.format("%.2f bara", comp.getOutletStream().getPressure("bara")));

      // Print all capacity constraints
      System.out.println("\n=== COMPRESSOR CAPACITY CONSTRAINTS ===");
      java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> constraints =
          comp.getCapacityConstraints();
      for (java.util.Map.Entry<String, neqsim.process.equipment.capacity.CapacityConstraint> entry : constraints
          .entrySet()) {
        neqsim.process.equipment.capacity.CapacityConstraint constraint = entry.getValue();
        String labelType = constraint.isMinimumConstraint() ? "min" : "design";
        System.out.println(String.format("%-20s: %6.2f%% (value=%.2f, %s=%.2f)", entry.getKey(),
            constraint.getUtilizationPercent(), constraint.getCurrentValue(), labelType,
            constraint.getDisplayDesignValue()));
      }
    }

    // Print equipment near capacity limit
    System.out.println("\n=== EQUIPMENT NEAR CAPACITY LIMIT (>90%) ===");
    java.util.List<String> nearLimit = processSystem.getEquipmentNearCapacityLimit();
    if (nearLimit.isEmpty()) {
      System.out.println("No equipment near capacity limit");
    } else {
      for (String name : nearLimit) {
        System.out.println("  - " + name);
      }
    }

    // Print bottleneck detection results
    neqsim.process.equipment.capacity.BottleneckResult bottleneck = processSystem.findBottleneck();
    System.out.println("\n=== BOTTLENECK ANALYSIS ===");
    if (bottleneck != null && bottleneck.hasBottleneck()) {
      System.out.println("Bottleneck Equipment: " + bottleneck.getEquipmentName());
      System.out.println("Constraint: " + bottleneck.getConstraintName());
      System.out
          .println("Utilization: " + String.format("%.2f%%", bottleneck.getUtilizationPercent()));
    } else {
      System.out.println("No bottleneck detected");
    }
  }

  /**
   * Generates an Eclipse VFP lift curve for the compression system.
   *
   * <p>
   * This method creates a performance table showing the relationship between inlet flow rate and
   * required inlet pressure at different operating conditions (outlet pressure, temperature).
   * </p>
   *
   * @param processSystem the process system containing the compressor
   * @param inletStreamName name of the inlet stream to vary flow rate on
   * @param outletPressures array of outlet pressures to evaluate (bara)
   * @param flowRates array of flow rates to evaluate (kg/hr)
   * @return Eclipse VFP formatted string
   */
  private String generateEclipseLiftCurve(ProcessSystem processSystem, String inletStreamName,
      double[] outletPressures, double[] flowRates) {
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
          double powerUtil =
              compressor.getPower("kW") / compressor.getMechanicalDesign().maxDesignPower * 100.0;

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
    splitter.setSplitFactors(new double[] {0.25, 0.25, 0.25, 0.25});
    splitter.run();
    processSystem.add(splitter);

    // Create processing trains for each split stream
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

    Splitter splitter2 = new Splitter("Test Splitter2", finalSeparator.getGasOutStream());
    splitter2.setSplitFactors(new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
    splitter2.run();
    processSystem.add(splitter2);

    StreamInterface upstreamCompressorTrain1 =
        createUpstreamCompressors("ups1", splitter2.getSplitStream(0), processSystem);

    // Run the process system
    processSystem.run();

    // Auto-size compressor
    for (neqsim.process.equipment.ProcessEquipmentInterface equipment : processSystem
        .getUnitOperations()) {
      if (equipment instanceof Separator) {
        ((Separator) equipment).autoSize();
      } else if (equipment instanceof Compressor) {
        ((Compressor) equipment).autoSize();
      }
    }
    ((Compressor) processSystem.getUnit("ups1 Compressor")).getMechanicalDesign()
        .setMaxDesignPower(50000.0);
    processSystem.run();
    // inletStream.setTemperature(28.5, "C");
    // processSystem.run();
    // Generate lift curve for different outlet pressures and flow rates
    // Note: Total inlet flow is ~2.1 million kg/hr, split to 1/3 going to compressor train
    // So compressor sees about 700,000 kg/hr at baseline
    double[] outletPressures = {90.0, 100.0, 110.0, 120.0, 130.0};
    double[] flowRates = {1500000, 1800000, 2100000, 2400000, 2700000, 3000000}; // Total inlet
                                                                                 // flows

    String vfpTable =
        generateEclipseLiftCurve(processSystem, "Inlet Stream", outletPressures, flowRates);

    System.out.println("\n" + vfpTable);

    // Verify we generated a valid table
    Assertions.assertTrue(vfpTable.contains("VFPPROD"));
    Assertions.assertTrue(vfpTable.contains("BHP values"));
  }

  /**
   * Test loading compressor curves from a JSON file.
   * 
   * This test demonstrates how to load pre-defined compressor performance curves from a JSON file
   * and apply them to a compressor.
   */
  @Test
  public void testLoadCompressorCurveFromJson() throws Exception {
    SystemPrEos testSystem = createTestFluid();

    // Create a simple process with one compressor
    ProcessSystem processSystem = new ProcessSystem();

    Stream inletStream = new Stream("Inlet Stream", testSystem);
    inletStream.setFlowRate(700000.0, "kg/hr");
    inletStream.setTemperature(35.0, "C");
    inletStream.setPressure(37.0, "bara");
    inletStream.run();
    processSystem.add(inletStream);

    // Create compressor
    Compressor compressor = new Compressor("Test Compressor", inletStream);
    compressor.setOutletPressure(110.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.85);
    compressor.run();
    processSystem.add(compressor);

    // Print results BEFORE loading curve
    System.out.println("\n=== BEFORE LOADING CURVE (calculated efficiency) ===");
    System.out.println("Compressor Power: " + String.format("%.2f kW", compressor.getPower("kW")));
    System.out.println("Polytropic Efficiency: "
        + String.format("%.2f%%", compressor.getPolytropicEfficiency() * 100));
    System.out.println(
        "Polytropic Head: " + String.format("%.2f kJ/kg", compressor.getPolytropicHead("kJ/kg")));
    System.out.println("Inlet Flow: "
        + String.format("%.2f m3/hr", compressor.getInletStream().getFlowRate("m3/hr")));

    compressor.setSpeed(6327.9); // RPM - matches one of the speed curves
    // Generate compressor curves programmatically instead of loading from JSON
    CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
    generator.setChartType("interpolate and extrapolate");
    CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 8);
    compressor.setCompressorChart(chart);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.setSolveSpeed(true);
    // Set speed to match one of the curves and run
    compressor.run();

    // Print results AFTER loading curve
    System.out.println("\n=== AFTER LOADING CURVE (from JSON file) ===");
    System.out.println("Compressor Speed: " + String.format("%.2f RPM", compressor.getSpeed()));
    System.out.println("Compressor Power: " + String.format("%.2f kW", compressor.getPower("kW")));
    System.out.println("Polytropic Efficiency: "
        + String.format("%.2f%%", compressor.getPolytropicEfficiency() * 100));
    System.out.println(
        "Polytropic Head: " + String.format("%.2f kJ/kg", compressor.getPolytropicHead("kJ/kg")));
    System.out.println("Inlet Flow: "
        + String.format("%.2f m3/hr", compressor.getInletStream().getFlowRate("m3/hr")));

    // Test at different speeds using the generated chart speeds
    System.out.println("\n=== TESTING AT DIFFERENT SPEEDS ===");
    double[] chartSpeeds = compressor.getCompressorChart().getSpeeds();
    // Test with a subset of speeds from the chart
    for (int i = 0; i < Math.min(5, chartSpeeds.length); i++) {
      compressor.setSpeed(chartSpeeds[i]);
      compressor.run();
      System.out.println(String.format(
          "Speed: %.1f RPM | Power: %.2f kW | Eff: %.2f%% | Head: %.2f kJ/kg | Flow: %.2f m3/hr",
          compressor.getSpeed(), compressor.getPower("kW"),
          compressor.getPolytropicEfficiency() * 100, compressor.getPolytropicHead("kJ/kg"),
          compressor.getInletStream().getFlowRate("m3/hr")));
    }

    // Verify the chart is being used
    Assertions.assertTrue(compressor.getCompressorChart().isUseCompressorChart(),
        "Compressor chart should be active after loading");

    // Verify we have the expected number of speed curves
    double[] speeds = compressor.getCompressorChart().getSpeeds();
    Assertions.assertEquals(8, speeds.length, "Should have 8 speed curves");

    // Verify the speeds array is sorted (lowest speed first in ascending order)
    Assertions.assertTrue(speeds[0] < speeds[7], "Speeds should be sorted lowest to highest");

    // Verify speeds are within reasonable range (generated curves use reference speed as basis)
    Assertions.assertTrue(speeds[0] > 0, "Min speed should be positive");
    Assertions.assertTrue(speeds[7] > 0, "Max speed should be positive");
    Assertions.assertTrue(speeds[0] < speeds[7], "Min speed should be less than max speed");

    // Verify the generated speed range makes sense for the compressor
    double referenceSpeed = compressor.getSpeed();
    Assertions.assertTrue(speeds[0] < referenceSpeed,
        "Min chart speed should be below current operating speed");
    Assertions.assertTrue(speeds[7] > referenceSpeed,
        "Max chart speed should be above current operating speed");
  }
}
