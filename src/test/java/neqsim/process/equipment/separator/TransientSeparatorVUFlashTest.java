package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Test class for transient separator simulation using improved VU flash calculations. This test
 * replicates a complex oil processing scenario with control systems.
 */
public class TransientSeparatorVUFlashTest {

  // Process parameters
  private static final double TIME_STEP = 10.0; // seconds
  private static final double TOTAL_TIME = 300.0; // seconds (reduced for testing)
  private static final String SEP_ORIENTATION = "horizontal";
  private static final double SEP_LENGTH = 5.0; // meters
  private static final double SEP_DIAMETER = 1.0; // meters
  private static final double SEP_LIQUID_LEVEL = 0.75; // initial level
  private static final double PRESSURE_SP = 7.0; // bars
  private static final double LEVEL_SP = 0.8;
  private static final double FLOW_SP = 50.0;
  private static final double INLET_VALVE_OPENING = 80.0; // %
  private static final double INLET_VALVE_PRESSURE = 8.0; // bar
  private static final double GAS_VALVE_OPENING = 25.0; // %
  private static final double GAS_VALVE_PRESSURE = 2.0; // bar
  private static final double LIQUID_VALVE_OPENING = 25.0; // %
  private static final double LIQUID_VALVE_PRESSURE = 2.0; // bar

  // Well conditions
  private static final double P_WELL = 9.0; // barg
  private static final double T_WELL = 40.0; // C
  private static final double FLOW_RATE = 400.0; // kg/hr

  private SystemInterface wellfluid;
  private ProcessSystem oilProcess;

  @BeforeEach
  void setUp() {
    // Setup inlet stream composition and EOS
    wellfluid = new SystemPrEos(298.15, 1.01325);
    wellfluid.addComponent("CO2", 1.5870);
    wellfluid.addComponent("methane", 52.51);
    wellfluid.addComponent("ethane", 6.24);
    wellfluid.addComponent("propane", 4.23);
    wellfluid.addComponent("i-butane", 0.855);
    wellfluid.addComponent("n-butane", 2.213);
    wellfluid.addComponent("i-pentane", 1.124);
    wellfluid.addComponent("n-pentane", 1.271);
    wellfluid.addComponent("n-hexane", 2.289);

    // Add TBP fractions
    wellfluid.addTBPfraction("C7+_cut1", 0.8501, 108.47 / 1000.0, 0.7411);
    wellfluid.addTBPfraction("C7+_cut2", 1.2802, 120.4 / 1000.0, 0.755);
    wellfluid.addTBPfraction("C7+_cut3", 1.6603, 133.64 / 1000.0, 0.7695);
    wellfluid.addTBPfraction("C7+_cut4", 6.5311, 164.70 / 1000.0, 0.799);
    wellfluid.addTBPfraction("C7+_cut5", 6.3311, 215.94 / 1000.0, 0.8387);
    wellfluid.addTBPfraction("C7+_cut6", 4.9618, 273.34 / 1000.0, 0.8754);
    wellfluid.addTBPfraction("C7+_cut7", 2.9105, 334.92 / 1000.0, 0.90731);
    wellfluid.addTBPfraction("C7+_cut8", 30.0505, 412.79 / 1000.0, 0.9575);

    wellfluid.setPressure(P_WELL);
    wellfluid.setTemperature(273.15 + T_WELL);
    wellfluid.setMixingRule("classic");
  }

  @Test
  @DisplayName("Test transient separator simulation with VU flash calculations")
  void testTransientSeparatorWithVUFlash() {
    // Construct the process

    // Well stream
    Stream wellStream = new Stream("well stream", wellfluid);
    wellStream.setFlowRate(FLOW_RATE, "kg/hr");

    // Inlet valve
    ThrottlingValve lcv00 = new ThrottlingValve("LCV-00", wellStream);
    lcv00.setPercentValveOpening(INLET_VALVE_OPENING);
    lcv00.setOutletPressure(INLET_VALVE_PRESSURE);
    lcv00.setCalculateSteadyState(false);
    lcv00.setMinimumValveOpening(0.01);
    lcv00.setMaximumValveOpening(85.0);
    lcv00.setCv(120.0);

    // 1st stage separator
    Separator v001 = new Separator("V-001", lcv00.getOutletStream());
    v001.setCalculateSteadyState(false);
    v001.setOrientation(SEP_ORIENTATION);
    v001.setSeparatorLength(SEP_LENGTH);
    v001.setInternalDiameter(SEP_DIAMETER);
    v001.setLiquidLevel(SEP_LIQUID_LEVEL);

    // Oil outlet control valve
    ThrottlingValve lcv001 = new ThrottlingValve("LCV-001", v001.getLiquidOutStream());
    lcv001.setPercentValveOpening(LIQUID_VALVE_OPENING);
    lcv001.setOutletPressure(LIQUID_VALVE_PRESSURE);
    lcv001.setCalculateSteadyState(false);
    lcv001.setMinimumValveOpening(1.0);
    lcv001.setMaximumValveOpening(85.0);

    // Gas outlet control valve
    ThrottlingValve pcv001 = new ThrottlingValve("PCV-001", v001.getGasOutStream());
    pcv001.setPercentValveOpening(GAS_VALVE_OPENING);
    pcv001.setOutletPressure(GAS_VALVE_PRESSURE);
    pcv001.setCalculateSteadyState(false);
    pcv001.setMinimumValveOpening(0.01);
    pcv001.setCv(80.0);

    // Measurement devices
    LevelTransmitter lt01 = new LevelTransmitter(v001);
    lt01.setMaximumValue(0.99);
    lt01.setMinimumValue(0.01);

    PressureTransmitter pt01 = new PressureTransmitter(v001.getGasOutStream());
    pt01.setUnit("bar");
    pt01.setMaximumValue(50.0);
    pt01.setMinimumValue(1.0);

    VolumeFlowTransmitter ft01 = new VolumeFlowTransmitter(lcv001.getOutletStream());
    ft01.setUnit("kg/hr");
    ft01.setMinimumValue(1.0);
    ft01.setMaximumValue(500.0);

    // Controllers
    ControllerDeviceBaseClass lc01 = new ControllerDeviceBaseClass();
    lc01.setTransmitter(lt01);
    lc01.setReverseActing(true);
    lc01.setControllerSetPoint(LEVEL_SP);
    lc01.setControllerParameters(1.0, 400.0, 0.0); // Kp, Ti, Td
    lc01.setOutputLimits(0.0, 100.0);

    ControllerDeviceBaseClass pc01 = new ControllerDeviceBaseClass();
    pc01.setTransmitter(pt01);
    pc01.setReverseActing(false);
    pc01.setControllerSetPoint(PRESSURE_SP);
    pc01.setControllerParameters(1.0, 400.0, 0.0);
    pc01.setOutputLimits(0.0, 100.0);

    ControllerDeviceBaseClass fc01 = new ControllerDeviceBaseClass();
    fc01.setTransmitter(ft01);
    fc01.setReverseActing(false);
    fc01.setControllerSetPoint(FLOW_SP);
    fc01.setControllerParameters(0.1, 1.0, 0.0);

    // Process system
    oilProcess = new ProcessSystem("oil process");
    oilProcess.add(wellStream);
    oilProcess.add(lcv00);
    oilProcess.add(v001);
    oilProcess.add(lcv001);
    oilProcess.add(pcv001);
    oilProcess.add(lt01);

    // Set controllers
    lcv00.setController(lc01);
    pcv001.setController(pc01);
    lcv001.setController(fc01);

    // Initial steady state run
    oilProcess.run();

    // Verify initial conditions
    assertNotNull(v001);
    assertTrue(v001.getLiquidLevel() >= 0.0 && v001.getLiquidLevel() <= 1.0);
    assertTrue(v001.getThermoSystem().getPressure() > 0.0);

    // Store initial state
    double initialLevel = v001.getLiquidLevel();
    double initialPressure = v001.getThermoSystem().getPressure();

    System.out.printf("Initial conditions: Level=%.3f, Pressure=%.3f bar%n", initialLevel,
        initialPressure);

    // Data collection for monitoring
    List<Double> timeData = new ArrayList<>();
    List<Double> levelData = new ArrayList<>();
    List<Double> pressureData = new ArrayList<>();
    List<Double> liquidVolumeData = new ArrayList<>();
    List<Double> valveOpeningData = new ArrayList<>();

    // Setup transient simulation
    oilProcess.setTimeStep(TIME_STEP);
    oilProcess.runTransient();

    // Run transient simulation
    int steps = (int) (TOTAL_TIME / TIME_STEP);
    for (int i = 0; i < steps; i++) {
      try {
        double currentTime = oilProcess.getTime();
        double currentLevel = v001.getLiquidLevel();
        double currentPressure = v001.getThermoSystem().getPressure();
        double liquidVolume = v001.calcLiquidVolume();
        double valveOpening = lcv00.getPercentValveOpening();

        // Store data
        timeData.add(currentTime);
        levelData.add(currentLevel);
        pressureData.add(currentPressure);
        liquidVolumeData.add(liquidVolume);
        valveOpeningData.add(valveOpening);

        // Print progress every 5 steps
        if (i % 5 == 0) {
          System.out.printf(
              "Step %d: Time=%.1f s, Level=%.3f, Pressure=%.3f bar, "
                  + "LiqVol=%.3f m³, ValveOpening=%.1f%%%n",
              i, currentTime, currentLevel, currentPressure, liquidVolume, valveOpening);
        }

        // Assertions to verify physical consistency
        assertTrue(currentLevel >= 0.0 && currentLevel <= 1.0,
            "Liquid level must be between 0 and 1");
        assertTrue(currentPressure > 0.0, "Pressure must be positive");
        assertTrue(liquidVolume >= 0.0, "Liquid volume must be non-negative");
        assertTrue(valveOpening >= 0.0 && valveOpening <= 100.0,
            "Valve opening must be between 0 and 100%");

        // Run next time step
        oilProcess.runTransient(TIME_STEP);

      } catch (Exception e) {
        System.err.printf("Error at step %d: %s%n", i, e.getMessage());
        throw new RuntimeException("Simulation failed at step " + i, e);
      }
    }

    // Final verification
    assertTrue(timeData.size() == steps, "Correct number of time steps recorded");
    assertTrue(levelData.size() == steps, "Level data recorded for all steps");
    assertTrue(pressureData.size() == steps, "Pressure data recorded for all steps");

    // Check for numerical stability - values should be finite and within reasonable bounds
    for (int i = 0; i < levelData.size(); i++) {
      assertTrue(Double.isFinite(levelData.get(i)), "Level at step " + i + " should be finite");
      assertTrue(Double.isFinite(pressureData.get(i)),
          "Pressure at step " + i + " should be finite");
      assertTrue(Double.isFinite(liquidVolumeData.get(i)),
          "Liquid volume at step " + i + " should be finite");
    }

    // Verify the simulation ran for the expected duration
    double finalTime = timeData.get(timeData.size() - 1);
    assertTrue(Math.abs(finalTime - TOTAL_TIME) < TIME_STEP,
        "Simulation should run for approximately the total time");

    // Print summary
    double finalLevel = levelData.get(levelData.size() - 1);
    double finalPressure = pressureData.get(pressureData.size() - 1);
    double finalVolume = liquidVolumeData.get(liquidVolumeData.size() - 1);

    System.out.printf("%nSimulation completed successfully:%n");
    System.out.printf("Final conditions: Level=%.3f, Pressure=%.3f bar, LiqVol=%.3f m³%n",
        finalLevel, finalPressure, finalVolume);
    System.out.printf("Total simulation time: %.1f seconds (%d steps)%n", finalTime, steps);

    // Test that the enhanced VU flash provided stable results
    double levelVariance = calculateVariance(levelData);
    double pressureVariance = calculateVariance(pressureData);

    System.out.printf("Level variance: %.6f, Pressure variance: %.6f%n", levelVariance,
        pressureVariance);

    // The enhanced VU flash should provide stable results with reasonable variance
    assertTrue(levelVariance < 1.0, "Level variance should be reasonable");
    assertTrue(pressureVariance < 100.0, "Pressure variance should be reasonable");
  }

  @Test
  @DisplayName("Test VU flash convergence in separator")
  void testVUFlashConvergence() {
    // Create a simpler test focusing on VU flash performance
    Stream wellStream = new Stream("well stream", wellfluid);
    wellStream.setFlowRate(FLOW_RATE, "kg/hr");

    Separator separator = new Separator("Test-Separator", wellStream);
    separator.setCalculateSteadyState(false);
    separator.setOrientation("horizontal");
    separator.setSeparatorLength(3.0);
    separator.setInternalDiameter(1.5);
    separator.setLiquidLevel(0.5);

    ProcessSystem testProcess = new ProcessSystem("test process");
    testProcess.add(wellStream);
    testProcess.add(separator);

    // Run initial calculation
    testProcess.run();

    double initialVolume = separator.getThermoSystem().getVolume();
    double initialInternalEnergy = separator.getThermoSystem().getInternalEnergy();

    assertNotNull(separator.getThermoSystem());
    assertTrue(initialVolume > 0.0, "Initial volume should be positive");
    assertTrue(Double.isFinite(initialInternalEnergy), "Initial internal energy should be finite");

    // Test transient behavior with VU flash
    testProcess.setTimeStep(5.0);
    testProcess.runTransient();

    for (int i = 0; i < 10; i++) {
      double volume = separator.getThermoSystem().getVolume();
      double internalEnergy = separator.getThermoSystem().getInternalEnergy();
      double pressure = separator.getThermoSystem().getPressure();
      double temperature = separator.getThermoSystem().getTemperature();

      // Verify VU flash results are physically meaningful
      assertTrue(volume > 0.0, "Volume should be positive");
      assertTrue(Double.isFinite(internalEnergy), "Internal energy should be finite");
      assertTrue(pressure > 0.0, "Pressure should be positive");
      assertTrue(temperature > 0.0, "Temperature should be positive");

      testProcess.runTransient(5.0);
    }

    System.out.println("VU flash convergence test completed successfully");
  }

  /**
   * Calculate variance of a list of values
   */
  private double calculateVariance(List<Double> values) {
    if (values.size() < 2)
      return 0.0;

    double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    double variance =
        values.stream().mapToDouble(val -> Math.pow(val - mean, 2)).average().orElse(0.0);
    return variance;
  }
}
