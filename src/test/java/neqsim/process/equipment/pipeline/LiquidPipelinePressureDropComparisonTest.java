package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comparison test for liquid pipeline pressure drop calculations.
 *
 * <p>
 * Compares pressure drop from {@link AdiabaticPipe} against the reference {@link PipeBeggsAndBrills} correlation for
 * single-phase liquid flow (pure water). Both models use the same Haaland friction factor and the incompressible
 * Darcy-Weisbach equation, so they should agree closely.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class LiquidPipelinePressureDropComparisonTest {
  private static final Logger logger = LogManager.getLogger(LiquidPipelinePressureDropComparisonTest.class);

  private SystemInterface pureWater;

  // Pipeline geometry
  private static final double LENGTH = 1000.0; // 1 km
  private static final double DIAMETER = 0.3; // 300 mm
  private static final double ROUGHNESS = 1e-5; // Smooth steel pipe
  private static final double FLOW_RATE = 100.0; // kg/s
  private static final double INLET_PRESSURE = 50.0; // bara
  private static final double INLET_TEMPERATURE = 20.0; // °C

  @BeforeEach
  void setUp() {
    pureWater = new SystemSrkEos(INLET_TEMPERATURE + 273.15, INLET_PRESSURE);
    pureWater.addComponent("water", 1.0);
    pureWater.setMixingRule("classic");
  }

  private Stream newInlet(String name, double flowRate) {
    Stream inlet = new Stream(name, pureWater.clone());
    inlet.setFlowRate(flowRate, "kg/sec");
    inlet.setTemperature(INLET_TEMPERATURE, "C");
    inlet.setPressure(INLET_PRESSURE, "bara");
    inlet.run();
    return inlet;
  }

  /**
   * AdiabaticPipe and PipeBeggsAndBrills should agree within 15% for single-phase liquid flow.
   */
  @Test
  void testCompareAdiabaticVsBeggsLiquid() {
    Stream inlet1 = newInlet("water-inlet-1", FLOW_RATE);
    AdiabaticPipe adiabaticPipe = new AdiabaticPipe("adiabatic-liquid", inlet1);
    adiabaticPipe.setLength(LENGTH);
    adiabaticPipe.setDiameter(DIAMETER);
    adiabaticPipe.setPipeWallRoughness(ROUGHNESS);
    adiabaticPipe.run();
    double dpAdiabatic = inlet1.getPressure("bara") - adiabaticPipe.getOutletStream().getPressure("bara");

    Stream inlet2 = newInlet("water-inlet-2", FLOW_RATE);
    PipeBeggsAndBrills beggsPipe = new PipeBeggsAndBrills("beggs-liquid", inlet2);
    beggsPipe.setLength(LENGTH);
    beggsPipe.setDiameter(DIAMETER);
    beggsPipe.setPipeWallRoughness(ROUGHNESS);
    beggsPipe.setNumberOfIncrements(20);
    beggsPipe.setAngle(0.0);
    beggsPipe.run();
    double dpBeggs = inlet2.getPressure("bara") - beggsPipe.getOutletStream().getPressure("bara");

    double avgDp = (dpAdiabatic + dpBeggs) / 2.0;
    double diff = Math.abs(dpAdiabatic - dpBeggs) / avgDp * 100.0;

    logger.info("=== Liquid Pressure Drop Comparison (water) ===");
    logger.info("AdiabaticPipe:      " + String.format("%.4f", dpAdiabatic) + " bar");
    logger.info("PipeBeggsAndBrills: " + String.format("%.4f", dpBeggs) + " bar");
    logger.info("Relative difference: " + String.format("%.1f", diff) + "%");

    assertTrue(dpAdiabatic > 0, "AdiabaticPipe liquid pressure drop should be positive");
    assertTrue(dpBeggs > 0, "PipeBeggsAndBrills liquid pressure drop should be positive");
    assertTrue(diff < 15.0,
        "AdiabaticPipe (" + String.format("%.4f", dpAdiabatic) + " bar) and PipeBeggsAndBrills ("
            + String.format("%.4f", dpBeggs) + " bar) should agree within 15% for liquid, diff="
            + String.format("%.1f", diff) + "%");
  }

  /**
   * Elevation gain should add hydrostatic head to the liquid pressure drop.
   */
  @Test
  void testLiquidElevationHead() {
    double elevationGain = 100.0; // m uphill

    Stream inletFlat = newInlet("water-flat", FLOW_RATE);
    AdiabaticPipe flatPipe = new AdiabaticPipe("adiabatic-flat", inletFlat);
    flatPipe.setLength(LENGTH);
    flatPipe.setDiameter(DIAMETER);
    flatPipe.setPipeWallRoughness(ROUGHNESS);
    flatPipe.run();
    double dpFlat = inletFlat.getPressure("bara") - flatPipe.getOutletStream().getPressure("bara");

    Stream inletUp = newInlet("water-up", FLOW_RATE);
    AdiabaticPipe upPipe = new AdiabaticPipe("adiabatic-up", inletUp);
    upPipe.setLength(LENGTH);
    upPipe.setDiameter(DIAMETER);
    upPipe.setPipeWallRoughness(ROUGHNESS);
    upPipe.setInletElevation(0.0);
    upPipe.setOutletElevation(elevationGain);
    upPipe.run();
    double dpUp = inletUp.getPressure("bara") - upPipe.getOutletStream().getPressure("bara");

    // Hydrostatic head for 100 m of water ~= 1000 * 9.81 * 100 / 1e5 ~= 9.8 bar
    double extraHead = dpUp - dpFlat;
    logger.info("Liquid flat dP: " + String.format("%.4f", dpFlat) + " bar, uphill dP: " + String.format("%.4f", dpUp)
        + " bar, extra head: " + String.format("%.4f", extraHead) + " bar");

    assertTrue(extraHead > 8.0 && extraHead < 11.0,
        "Uphill hydrostatic head for 100 m water column should be ~9.8 bar");
  }

  /**
   * AdiabaticPipe should support quasi-steady transient (dynamic) evaluation for liquid flow.
   */
  @Test
  void testLiquidTransientStep() {
    Stream inlet = newInlet("water-transient", FLOW_RATE);
    AdiabaticPipe pipe = new AdiabaticPipe("adiabatic-transient-liquid", inlet);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setPipeWallRoughness(ROUGHNESS);
    pipe.run();
    double dpSteady = inlet.getPressure("bara") - pipe.getOutletStream().getPressure("bara");

    pipe.runTransient(1.0, UUID.randomUUID());
    double dpTransient = inlet.getPressure("bara") - pipe.getOutletStream().getPressure("bara");

    logger.info("Liquid steady dP: " + String.format("%.4f", dpSteady) + " bar, transient dP: "
        + String.format("%.4f", dpTransient) + " bar");

    assertTrue(dpTransient > 0, "Transient liquid pressure drop should be positive");
    assertTrue(Math.abs(dpTransient - dpSteady) / dpSteady < 1e-3,
        "Quasi-steady transient step should match the steady-state solution for constant inlet");
  }
}
