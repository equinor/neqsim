package neqsim.process.util.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemPrEos;


public class testTr {

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
    // inletStream.setFlowRate(1897870.58288790, "kg/hr");
    // processSystem.run();

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
      System.out.println("Chart Max Speed: " + String.format("%.0f RPM", comp.getCompressorChart().getMaxSpeedCurve()));
      System.out.println("Chart Min Speed: " + String.format("%.0f RPM", comp.getCompressorChart().getMinSpeedCurve()));
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
            constraint.getUtilizationPercent(), constraint.getCurrentValue(),
            labelType, constraint.getDisplayDesignValue()));
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

}
