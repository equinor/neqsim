package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comparison test for gas pipeline pressure drop calculations.
 *
 * <p>
 * Compares pressure drop from different pipeline models for single-phase gas flow (pure methane).
 * All models should give similar pressure drops when using the same friction factor correlations.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class GasPipelinePressureDropComparisonTest {
  private SystemInterface pureMethane;
  private Stream gasInlet;

  // Pipeline geometry
  private static final double LENGTH = 1000.0; // 1 km
  private static final double DIAMETER = 0.3; // 300 mm
  private static final double ROUGHNESS = 1e-5; // Smooth steel pipe
  private static final double FLOW_RATE = 5.0; // kg/s
  private static final double INLET_PRESSURE = 50.0; // bara
  private static final double INLET_TEMPERATURE = 20.0; // °C

  @BeforeEach
  void setUp() {
    // Create pure methane gas
    pureMethane = new SystemSrkEos(INLET_TEMPERATURE + 273.15, INLET_PRESSURE);
    pureMethane.addComponent("methane", 1.0);
    pureMethane.setMixingRule("classic");

    // Create inlet stream
    gasInlet = new Stream("gas-inlet", pureMethane);
    gasInlet.setFlowRate(FLOW_RATE, "kg/sec");
    gasInlet.setTemperature(INLET_TEMPERATURE, "C");
    gasInlet.setPressure(INLET_PRESSURE, "bara");
    gasInlet.run();

    System.out.println("\n=== Gas Pipeline Pressure Drop Comparison ===");
    System.out.println("Fluid: Pure methane");
    System.out.println("Flow rate: " + FLOW_RATE + " kg/s");
    System.out.println("Inlet pressure: " + INLET_PRESSURE + " bara");
    System.out.println("Inlet temperature: " + INLET_TEMPERATURE + " °C");
    System.out.println("Pipe length: " + LENGTH + " m");
    System.out.println("Pipe diameter: " + DIAMETER * 1000 + " mm");
    System.out.println("Roughness: " + ROUGHNESS * 1000 + " mm");
    System.out.println();
  }

  /**
   * Test pressure drop with AdiabaticPipe (single-phase gas model).
   */
  @Test
  void testAdiabaticPipePressureDrop() {
    AdiabaticPipe pipe = new AdiabaticPipe("adiabatic-pipe", gasInlet);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setPipeWallRoughness(ROUGHNESS);
    pipe.run();

    double inletP = gasInlet.getPressure("bara");
    double outletP = pipe.getOutletStream().getPressure("bara");
    double dp = inletP - outletP;

    System.out.println("AdiabaticPipe:");
    System.out.println("  Inlet pressure:  " + inletP + " bara");
    System.out.println("  Outlet pressure: " + outletP + " bara");
    System.out.println("  Pressure drop:   " + dp + " bar");
    System.out.println();

    assertTrue(dp > 0, "Pressure drop should be positive");
    assertTrue(dp < inletP * 0.5, "Pressure drop should be less than 50% of inlet");
  }

  /**
   * Test pressure drop with PipeBeggsAndBrills (empirical correlation).
   */
  @Test
  void testPipeBeggsAndBrillsPressureDrop() {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("beggs-pipe", gasInlet);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setPipeWallRoughness(ROUGHNESS);
    pipe.setNumberOfIncrements(20);
    pipe.setAngle(0.0); // Horizontal
    pipe.run();

    double inletP = gasInlet.getPressure("bara");
    double outletP = pipe.getOutletStream().getPressure("bara");
    double dp = inletP - outletP;

    System.out.println("PipeBeggsAndBrills:");
    System.out.println("  Inlet pressure:  " + inletP + " bara");
    System.out.println("  Outlet pressure: " + outletP + " bara");
    System.out.println("  Pressure drop:   " + dp + " bar");
    System.out.println();

    assertTrue(dp > 0, "Pressure drop should be positive");
    assertTrue(dp < inletP * 0.5, "Pressure drop should be less than 50% of inlet");
  }

  /**
   * Test pressure drop with TwoFluidPipe (two-fluid model).
   */
  @Test
  void testTwoFluidPipePressureDrop() {
    TwoFluidPipe pipe = new TwoFluidPipe("two-fluid-pipe", gasInlet);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(ROUGHNESS);
    pipe.setNumberOfSections(20);

    // Horizontal pipe
    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      elevations[i] = 0.0;
    }
    pipe.setElevationProfile(elevations);
    pipe.run();

    double inletP = gasInlet.getPressure("bara");
    double[] pressureProfile = pipe.getPressureProfile();
    double outletP = pressureProfile[pressureProfile.length - 1] / 1e5; // Pa to bara
    double dp = inletP - outletP;

    System.out.println("TwoFluidPipe:");
    System.out.println("  Inlet pressure:  " + inletP + " bara");
    System.out.println("  Outlet pressure: " + outletP + " bara");
    System.out.println("  Pressure drop:   " + dp + " bar");
    System.out.println("  Pressure profile (bara): ");
    for (int i = 0; i < Math.min(5, pressureProfile.length); i++) {
      System.out.println("    [" + i + "]: " + String.format("%.4f", pressureProfile[i] / 1e5));
    }
    System.out.println("    ...");
    System.out.println("    [" + (pressureProfile.length - 1) + "]: "
        + String.format("%.4f", pressureProfile[pressureProfile.length - 1] / 1e5));
    System.out.println();

    assertTrue(dp > 0, "Pressure drop should be positive");
    assertTrue(dp < inletP * 0.5, "Pressure drop should be less than 50% of inlet");
  }

  /**
   * Compare all three pipeline models.
   */
  @Test
  void testCompareAllModels() {
    // 1. AdiabaticPipe
    AdiabaticPipe adiabaticPipe = new AdiabaticPipe("adiabatic", gasInlet);
    adiabaticPipe.setLength(LENGTH);
    adiabaticPipe.setDiameter(DIAMETER);
    adiabaticPipe.setPipeWallRoughness(ROUGHNESS);
    adiabaticPipe.run();

    double dpAdiabatic =
        gasInlet.getPressure("bara") - adiabaticPipe.getOutletStream().getPressure("bara");

    // 2. PipeBeggsAndBrills (need new stream to avoid shared state)
    Stream inlet2 = new Stream("inlet2", pureMethane.clone());
    inlet2.setFlowRate(FLOW_RATE, "kg/sec");
    inlet2.setTemperature(INLET_TEMPERATURE, "C");
    inlet2.setPressure(INLET_PRESSURE, "bara");
    inlet2.run();

    PipeBeggsAndBrills beggsPipe = new PipeBeggsAndBrills("beggs", inlet2);
    beggsPipe.setLength(LENGTH);
    beggsPipe.setDiameter(DIAMETER);
    beggsPipe.setPipeWallRoughness(ROUGHNESS);
    beggsPipe.setNumberOfIncrements(20);
    beggsPipe.setAngle(0.0);
    beggsPipe.run();

    double dpBeggs = inlet2.getPressure("bara") - beggsPipe.getOutletStream().getPressure("bara");

    // 3. TwoFluidPipe (need new stream)
    Stream inlet3 = new Stream("inlet3", pureMethane.clone());
    inlet3.setFlowRate(FLOW_RATE, "kg/sec");
    inlet3.setTemperature(INLET_TEMPERATURE, "C");
    inlet3.setPressure(INLET_PRESSURE, "bara");
    inlet3.run();

    TwoFluidPipe twoFluidPipe = new TwoFluidPipe("twofluid", inlet3);
    twoFluidPipe.setLength(LENGTH);
    twoFluidPipe.setDiameter(DIAMETER);
    twoFluidPipe.setRoughness(ROUGHNESS);
    twoFluidPipe.setNumberOfSections(20);

    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      elevations[i] = 0.0;
    }
    twoFluidPipe.setElevationProfile(elevations);
    twoFluidPipe.run();

    double[] pressureProfile = twoFluidPipe.getPressureProfile();
    double outletPTwoFluid = pressureProfile[pressureProfile.length - 1] / 1e5;
    double dpTwoFluid = inlet3.getPressure("bara") - outletPTwoFluid;

    System.out.println("=== Pressure Drop Comparison ===");
    System.out.println("AdiabaticPipe:      " + String.format("%.4f", dpAdiabatic) + " bar");
    System.out.println("PipeBeggsAndBrills: " + String.format("%.4f", dpBeggs) + " bar");
    System.out.println("TwoFluidPipe:       " + String.format("%.4f", dpTwoFluid) + " bar");
    System.out.println();

    // Calculate relative differences
    double avgDp = (dpAdiabatic + dpBeggs + dpTwoFluid) / 3.0;
    double diffAdiabaticBeggs = Math.abs(dpAdiabatic - dpBeggs) / avgDp * 100;
    double diffAdiabaticTwoFluid = Math.abs(dpAdiabatic - dpTwoFluid) / avgDp * 100;
    double diffBeggsTwoFluid = Math.abs(dpBeggs - dpTwoFluid) / avgDp * 100;

    System.out.println("Relative Differences:");
    System.out.println("  AdiabaticPipe vs PipeBeggsAndBrills: "
        + String.format("%.1f", diffAdiabaticBeggs) + "%");
    System.out.println("  AdiabaticPipe vs TwoFluidPipe:       "
        + String.format("%.1f", diffAdiabaticTwoFluid) + "%");
    System.out.println(
        "  PipeBeggsAndBrills vs TwoFluidPipe:  " + String.format("%.1f", diffBeggsTwoFluid) + "%");
    System.out.println();

    // All pressure drops should be positive
    assertTrue(dpAdiabatic > 0, "AdiabaticPipe pressure drop should be positive");
    assertTrue(dpBeggs > 0, "PipeBeggsAndBrills pressure drop should be positive");
    assertTrue(dpTwoFluid > 0, "TwoFluidPipe pressure drop should be positive");

    // All models should agree within 10% for single-phase gas flow
    // (using same Haaland friction factor correlation)
    assertTrue(diffAdiabaticBeggs < 10,
        "AdiabaticPipe and PipeBeggsAndBrills should be within 10%");
    assertTrue(diffAdiabaticTwoFluid < 10, "AdiabaticPipe and TwoFluidPipe should be within 10%");
    assertTrue(diffBeggsTwoFluid < 10, "PipeBeggsAndBrills and TwoFluidPipe should be within 10%");
  }

  /**
   * Test with higher flow rate to see larger pressure drops.
   */
  @Test
  void testHigherFlowRate() {
    double highFlowRate = 20.0; // kg/s

    // AdiabaticPipe
    Stream inlet1 = new Stream("inlet1", pureMethane.clone());
    inlet1.setFlowRate(highFlowRate, "kg/sec");
    inlet1.setTemperature(INLET_TEMPERATURE, "C");
    inlet1.setPressure(INLET_PRESSURE, "bara");
    inlet1.run();

    AdiabaticPipe adiabaticPipe = new AdiabaticPipe("adiabatic", inlet1);
    adiabaticPipe.setLength(LENGTH);
    adiabaticPipe.setDiameter(DIAMETER);
    adiabaticPipe.setPipeWallRoughness(ROUGHNESS);
    adiabaticPipe.run();

    double dpAdiabatic =
        inlet1.getPressure("bara") - adiabaticPipe.getOutletStream().getPressure("bara");

    // PipeBeggsAndBrills
    Stream inlet2 = new Stream("inlet2", pureMethane.clone());
    inlet2.setFlowRate(highFlowRate, "kg/sec");
    inlet2.setTemperature(INLET_TEMPERATURE, "C");
    inlet2.setPressure(INLET_PRESSURE, "bara");
    inlet2.run();

    PipeBeggsAndBrills beggsPipe = new PipeBeggsAndBrills("beggs", inlet2);
    beggsPipe.setLength(LENGTH);
    beggsPipe.setDiameter(DIAMETER);
    beggsPipe.setPipeWallRoughness(ROUGHNESS);
    beggsPipe.setNumberOfIncrements(20);
    beggsPipe.setAngle(0.0);
    beggsPipe.run();

    double dpBeggs = inlet2.getPressure("bara") - beggsPipe.getOutletStream().getPressure("bara");

    // TwoFluidPipe
    Stream inlet3 = new Stream("inlet3", pureMethane.clone());
    inlet3.setFlowRate(highFlowRate, "kg/sec");
    inlet3.setTemperature(INLET_TEMPERATURE, "C");
    inlet3.setPressure(INLET_PRESSURE, "bara");
    inlet3.run();

    TwoFluidPipe twoFluidPipe = new TwoFluidPipe("twofluid", inlet3);
    twoFluidPipe.setLength(LENGTH);
    twoFluidPipe.setDiameter(DIAMETER);
    twoFluidPipe.setRoughness(ROUGHNESS);
    twoFluidPipe.setNumberOfSections(20);

    double[] elevations = new double[20];
    for (int i = 0; i < 20; i++) {
      elevations[i] = 0.0;
    }
    twoFluidPipe.setElevationProfile(elevations);
    twoFluidPipe.run();

    double[] pressureProfile = twoFluidPipe.getPressureProfile();
    double outletPTwoFluid = pressureProfile[pressureProfile.length - 1] / 1e5;
    double dpTwoFluid = inlet3.getPressure("bara") - outletPTwoFluid;

    System.out.println("=== High Flow Rate (" + highFlowRate + " kg/s) Comparison ===");
    System.out.println("AdiabaticPipe:      " + String.format("%.4f", dpAdiabatic) + " bar");
    System.out.println("PipeBeggsAndBrills: " + String.format("%.4f", dpBeggs) + " bar");
    System.out.println("TwoFluidPipe:       " + String.format("%.4f", dpTwoFluid) + " bar");
    System.out.println();

    // All should be positive and larger than low flow
    assertTrue(dpAdiabatic > 0, "Pressure drop should be positive");
    assertTrue(dpBeggs > 0, "Pressure drop should be positive");
    assertTrue(dpTwoFluid > 0, "Pressure drop should be positive");
  }
}
