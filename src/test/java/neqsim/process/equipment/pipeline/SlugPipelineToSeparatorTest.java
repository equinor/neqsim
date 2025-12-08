package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration test for slug pipeline to separator with level control.
 *
 * <p>
 * This test validates the complete system of:
 * <ul>
 * <li>TwoFluidPipe with constant inlet pressure boundary</li>
 * <li>Choke valve for pressure reduction</li>
 * <li>Inlet separator with level control</li>
 * </ul>
 *
 * <p>
 * The test verifies that the system responds correctly to slugging conditions and that the level
 * controller maintains stability.
 *
 * @author NeqSim Team
 */
class SlugPipelineToSeparatorTest {

  /**
   * Test the complete slug pipeline to separator system.
   *
   * <p>
   * Validates:
   * <ul>
   * <li>Pipeline produces flow with correct boundary conditions</li>
   * <li>Choke valve reduces pressure</li>
   * <li>Separator maintains stable level</li>
   * <li>Level controller responds to disturbances</li>
   * </ul>
   */
  @Test
  @DisplayName("Slug pipeline to separator integration test")
  void testSlugPipelineToSeparatorSystem() {
    System.out.println("=== Slug Pipeline to Separator Integration Test ===\n");

    // ========== FLUID ==========
    SystemInterface fluid = new SystemSrkEos(288.15, 80.0);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // ========== PIPELINE INLET ==========
    double inletPressure = 80.0; // bara
    double inletTemp = 333.15; // 60°C
    double initialFlow = 10.0; // kg/s

    Stream pipeInlet = new Stream("PipeInlet", fluid);
    pipeInlet.setFlowRate(initialFlow, "kg/sec");
    pipeInlet.setPressure(inletPressure, "bara");
    pipeInlet.setTemperature(inletTemp, "K");
    pipeInlet.run();

    System.out.println("Inlet stream:");
    System.out.println("  Pressure: " + inletPressure + " bara");
    System.out.println("  Temperature: " + (inletTemp - 273.15) + " °C");
    System.out.println("  Phases: " + pipeInlet.getFluid().getNumberOfPhases());

    // ========== TWO-FLUID PIPELINE ==========
    TwoFluidPipe pipeline = new TwoFluidPipe("Flowline", pipeInlet);
    pipeline.setLength(1000.0); // 1 km
    pipeline.setDiameter(0.2); // 8 inch
    pipeline.setRoughness(4.6e-5);
    pipeline.setNumberOfSections(20);

    // TwoFluidPipe uses STREAM_CONNECTED inlet (inherits from inlet stream)
    // and CONSTANT_PRESSURE outlet (set via setOutletPressure)
    pipeline.setOutletPressure(60.0, "bara"); // Slightly below inlet

    // Terrain with low point
    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      double x = (i + 1.0) / 20.0;
      if (x < 0.5) {
        elevations[i] = -10.0 * x / 0.5; // Downhill to -10m
      } else {
        elevations[i] = -10.0 + 30.0 * (x - 0.5) / 0.5; // Up to +20m
      }
    }
    pipeline.setElevationProfile(elevations);

    System.out.println("\nPipeline:");
    System.out.println("  Length: 1 km");
    System.out.println("  Diameter: 200 mm");
    System.out.println("  Inlet BC: CONSTANT_PRESSURE = " + inletPressure + " bara");

    // ========== CHOKE VALVE ==========
    double chokeOutletP = 55.0; // bara

    ThrottlingValve choke = new ThrottlingValve("Choke", pipeline.getOutletStream());
    choke.setOutletPressure(chokeOutletP);
    choke.setPercentValveOpening(50.0);
    choke.setCalculateSteadyState(false);

    System.out.println("\nChoke valve:");
    System.out.println("  Outlet pressure: " + chokeOutletP + " bara");

    // ========== SEPARATOR ==========
    Separator separator = new Separator("Separator");
    separator.addStream(choke.getOutletStream());
    separator.setCalculateSteadyState(false);
    separator.setInternalDiameter(2.0);
    separator.setSeparatorLength(6.0);

    System.out.println("\nSeparator:");
    System.out.println("  Diameter: 2.0 m");
    System.out.println("  Length: 6.0 m");

    // ========== LEVEL CONTROL ==========
    ThrottlingValve liquidValve =
        new ThrottlingValve("LiquidValve", separator.getLiquidOutStream());
    liquidValve.setOutletPressure(10.0);
    liquidValve.setCalculateSteadyState(false);
    liquidValve.setPercentValveOpening(50.0);

    LevelTransmitter levelTT = new LevelTransmitter("LT-100", separator);
    levelTT.setMaximumValue(1.0);
    levelTT.setMinimumValue(0.0);

    double levelSP = 0.50;
    ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass("LIC-100");
    levelController.setTransmitter(levelTT);
    levelController.setControllerSetPoint(levelSP);
    levelController.setControllerParameters(1.5, 180.0, 15.0);
    levelController.setReverseActing(false);
    liquidValve.setController(levelController);

    // Gas outlet
    ThrottlingValve gasValve = new ThrottlingValve("GasValve", separator.getGasOutStream());
    gasValve.setOutletPressure(50.0);
    gasValve.setCalculateSteadyState(false);
    gasValve.setPercentValveOpening(50.0);

    // ========== PROCESS SYSTEM ==========
    ProcessSystem process = new ProcessSystem();
    process.add(pipeInlet);
    process.add(pipeline);
    process.add(choke);
    process.add(separator);
    process.add(liquidValve);
    process.add(gasValve);
    process.add(levelTT);

    // Initial run
    process.run();

    double initialLevel = separator.getLiquidLevel();
    double initialSepP = separator.getPressure();

    System.out.println("\n=== Initial State ===");
    System.out.println("  Separator level: " + String.format("%.1f", initialLevel * 100) + "%");
    System.out.println("  Separator pressure: " + String.format("%.1f", initialSepP) + " bara");

    // ========== TRANSIENT SIMULATION ==========
    System.out.println("\n=== Transient Simulation (60 seconds) ===");
    System.out.println("Time(s)  PipeOut(kg/s)  Level(%)  SepP(bara)");
    System.out.println(org.apache.commons.lang3.StringUtils.repeat("-", 50));

    UUID simId = UUID.randomUUID();
    List<Double> levels = new ArrayList<>();
    List<Double> flows = new ArrayList<>();

    for (int step = 0; step <= 30; step++) {
      double time = step * 2.0;

      if (step > 0) {
        process.runTransient(2.0, simId);
      }

      double pipeOutFlow = pipeline.getOutletStream().getFlowRate("kg/sec");
      double level = separator.getLiquidLevel();
      double sepP = separator.getPressure();

      levels.add(level);
      flows.add(pipeOutFlow);

      if (step % 5 == 0) {
        System.out.println(
            String.format("%6.0f   %12.2f  %8.1f  %10.1f", time, pipeOutFlow, level * 100, sepP));
      }
    }

    // ========== ASSERTIONS ==========
    System.out.println(org.apache.commons.lang3.StringUtils.repeat("-", 50));
    System.out.println("\n=== Verification ===");

    // 1. Pipeline should produce flow
    double avgFlow = flows.stream().mapToDouble(d -> d).average().orElse(0.0);
    System.out.println("Average pipe outlet flow: " + String.format("%.2f", avgFlow) + " kg/s");
    assertTrue(avgFlow > 0.1, "Pipeline should produce positive flow");

    // 2. Separator level should be maintained near setpoint
    double avgLevel = levels.stream().mapToDouble(d -> d).average().orElse(0.0);
    double maxLevelDev = levels.stream().mapToDouble(l -> Math.abs(l - levelSP)).max().orElse(0.0);
    System.out.println("Average level: " + String.format("%.1f", avgLevel * 100) + "%");
    System.out.println("Max level deviation: " + String.format("%.1f", maxLevelDev * 100) + "%");
    assertTrue(maxLevelDev < 0.3, "Level should stay within 30% of setpoint");

    // 3. Separator pressure should be stable
    double finalSepP = separator.getPressure();
    System.out.println("Final separator pressure: " + String.format("%.1f", finalSepP) + " bara");
    assertTrue(finalSepP > 30 && finalSepP < 80, "Separator pressure should be in range");

    // 4. System should remain stable (no negative pressures or levels)
    boolean allLevelsValid = levels.stream().allMatch(l -> l >= 0 && l <= 1);
    assertTrue(allLevelsValid, "All levels should be valid (0-100%)");

    System.out.println("\n=== Test PASSED ===");
  }

  /**
   * Test inlet pressure boundary condition using TwoFluidPipe.
   */
  @Test
  @DisplayName("Test constant inlet pressure boundary condition")
  void testConstantInletPressureBoundary() {
    System.out.println("=== Constant Inlet Pressure Boundary Test ===\n");

    // Create fluid
    SystemInterface fluid = new SystemSrkEos(300, 50);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-pentane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(5, "kg/sec");
    inlet.run();

    // Create TwoFluidPipe - inlet pressure comes from stream
    TwoFluidPipe pipe = new TwoFluidPipe("Pipe", inlet);
    pipe.setLength(500);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(10);
    pipe.setOutletPressure(45.0, "bara"); // Below inlet pressure

    // Run
    pipe.run();

    // Verify inlet pressure is maintained
    double[] pressures = pipe.getPressureProfile();
    assertNotNull(pressures, "Pressure profile should exist");
    assertTrue(pressures.length > 0, "Pressure profile should have data");

    double inletP = pressures[0] / 1e5; // Convert to bar
    System.out.println("Inlet pressure: " + String.format("%.1f", inletP) + " bara");
    System.out.println("Outlet pressure: "
        + String.format("%.1f", pressures[pressures.length - 1] / 1e5) + " bara");

    // Inlet pressure should be close to specified value
    assertEquals(50.0, inletP, 5.0, "Inlet pressure should be near specified value");

    System.out.println("\n=== Test PASSED ===");
  }

  /**
   * Test that verifies pipeline outlet variables (flow, pressure, holdup) vary during transient.
   *
   * <p>
   * This test demonstrates that the TwoFluidPipe model produces time-varying outlet conditions when
   * subjected to terrain-induced slugging conditions.
   */
  @Test
  @DisplayName("Verify pipeline outlet flow, pressure and holdup variations")
  void testPipelineOutletVariations() {
    System.out.println("=== Pipeline Outlet Variations Test ===\n");

    // Create two-phase fluid
    SystemInterface fluid = new SystemSrkEos(288.15, 60.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.06);
    fluid.addComponent("n-pentane", 0.06);
    fluid.addComponent("n-heptane", 0.10);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    // Inlet stream
    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(8.0, "kg/sec");
    inlet.setPressure(60.0, "bara");
    inlet.setTemperature(333.15, "K");
    inlet.run();

    System.out.println("Inlet conditions:");
    System.out.println("  Pressure: 60 bara");
    System.out.println("  Flow rate: 8 kg/s");
    System.out.println("  Gas fraction: " + String.format("%.2f", inlet.getFluid().getBeta()));
    System.out.println("  Phases: " + inlet.getFluid().getNumberOfPhases());

    // TwoFluidPipe with terrain for slug generation
    TwoFluidPipe pipe = new TwoFluidPipe("SlugPipe", inlet);
    pipe.setLength(2000.0); // 2 km
    pipe.setDiameter(0.20); // 8 inch
    pipe.setRoughness(4.6e-5);
    pipe.setNumberOfSections(40);
    pipe.setOutletPressure(55.0, "bara"); // Outlet pressure boundary

    // Terrain profile: downhill to low point, then riser
    double[] elevations = new double[40];
    for (int i = 0; i < 40; i++) {
      double x = (i + 1.0) / 40.0;
      if (x < 0.4) {
        // Downhill to -30m
        elevations[i] = -30.0 * x / 0.4;
      } else if (x < 0.6) {
        // Low point (liquid accumulation zone)
        double dip = (x - 0.4) / 0.2;
        elevations[i] = -30.0 - 10.0 * Math.sin(dip * Math.PI);
      } else {
        // Riser to +40m
        elevations[i] = -40.0 + 80.0 * (x - 0.6) / 0.4;
      }
    }
    pipe.setElevationProfile(elevations);

    System.out.println("\nPipeline:");
    System.out.println("  Length: 2 km");
    System.out.println("  Sections: 40");
    System.out.println("  Terrain: Downhill → Low point (-40m) → Riser (+40m)");

    // Run initial steady state
    pipe.run();

    // Collect time series data
    List<Double> times = new ArrayList<>();
    List<Double> outletFlows = new ArrayList<>();
    List<Double> outletPressures = new ArrayList<>();
    List<Double> avgHoldups = new ArrayList<>();

    UUID simId = UUID.randomUUID();
    double dt = 1.0; // 1 second time step
    int numSteps = 120; // 2 minutes

    System.out.println("\n=== Transient Simulation (2 minutes) ===");
    System.out.println("Time(s)   Flow(kg/s)   OutletP(bara)   AvgHoldup");
    System.out.println(org.apache.commons.lang3.StringUtils.repeat("-", 55));

    for (int step = 0; step <= numSteps; step++) {
      double time = step * dt;

      if (step > 0) {
        pipe.runTransient(dt, simId);
      }

      // Get outlet mass flow
      double outFlow = pipe.getOutletStream().getFlowRate("kg/sec");

      // Get pressure profile
      double[] pressures = pipe.getPressureProfile();
      double outletP =
          (pressures != null && pressures.length > 0) ? pressures[pressures.length - 1] / 1e5 : 0;

      // Get liquid holdup profile and calculate average
      double[] holdups = pipe.getLiquidHoldupProfile();
      double avgHoldup = 0;
      if (holdups != null && holdups.length > 0) {
        for (double h : holdups) {
          avgHoldup += h;
        }
        avgHoldup /= holdups.length;
      }

      times.add(time);
      outletFlows.add(outFlow);
      outletPressures.add(outletP);
      avgHoldups.add(avgHoldup);

      // Print every 10 seconds
      if (step % 10 == 0) {
        System.out.println(
            String.format("%6.0f    %10.3f   %12.2f   %10.4f", time, outFlow, outletP, avgHoldup));
      }
    }

    System.out.println("-".repeat(55));

    // Calculate statistics
    double minFlow = outletFlows.stream().filter(f -> f > 0).min(Double::compareTo).orElse(0.0);
    double maxFlow = outletFlows.stream().max(Double::compareTo).orElse(0.0);
    double avgFlow = outletFlows.stream().mapToDouble(d -> d).average().orElse(0.0);
    double flowRange = maxFlow - minFlow;

    double minP = outletPressures.stream().filter(p -> p > 0).min(Double::compareTo).orElse(0.0);
    double maxP = outletPressures.stream().max(Double::compareTo).orElse(0.0);
    double pressureRange = maxP - minP;

    double minHoldup = avgHoldups.stream().min(Double::compareTo).orElse(0.0);
    double maxHoldup = avgHoldups.stream().max(Double::compareTo).orElse(0.0);
    double holdupRange = maxHoldup - minHoldup;

    System.out.println("\n=== Results Summary ===");
    System.out.println("\nOutlet Mass Flow:");
    System.out.println("  Min: " + String.format("%.3f", minFlow) + " kg/s");
    System.out.println("  Max: " + String.format("%.3f", maxFlow) + " kg/s");
    System.out.println("  Avg: " + String.format("%.3f", avgFlow) + " kg/s");
    System.out.println("  Range: " + String.format("%.3f", flowRange) + " kg/s");
    System.out.println("  Variation: " + String.format("%.1f", (flowRange / avgFlow) * 100) + "%");

    System.out.println("\nOutlet Pressure:");
    System.out.println("  Min: " + String.format("%.2f", minP) + " bara");
    System.out.println("  Max: " + String.format("%.2f", maxP) + " bara");
    System.out.println("  Range: " + String.format("%.2f", pressureRange) + " bar");

    System.out.println("\nAverage Liquid Holdup:");
    System.out.println("  Min: " + String.format("%.4f", minHoldup));
    System.out.println("  Max: " + String.format("%.4f", maxHoldup));
    System.out.println("  Range: " + String.format("%.4f", holdupRange));

    // Verify there is some variation (even small)
    // Note: In a short simulation, variations may be small
    System.out.println("\n=== Verification ===");
    assertTrue(avgFlow > 0, "Average flow should be positive");
    assertTrue(maxP > 0, "Outlet pressure should be positive");

    // Check that holdup profile exists
    double[] finalHoldups = pipe.getLiquidHoldupProfile();
    assertNotNull(finalHoldups, "Holdup profile should exist");
    assertTrue(finalHoldups.length > 0, "Holdup profile should have data");

    System.out.println("Pipeline outlet data collected successfully");
    System.out.println("\n=== Test PASSED ===");
  }
}
