package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class SaturationTemperatureTest extends neqsim.NeqSimTest {
  /**
   * Test method for {@link neqsim.pvtsimulation.simulation.SaturationTemperature#calcSaturationTemperature()}.
   */
  @Test
  void testCalcSaturationTemperature() {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 220, 60.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 3.59);
    tempSystem.addComponent("methane", 67.42);
    tempSystem.addComponent("ethane", 9.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 0.38);
    tempSystem.addTBPfraction("C7", 0.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 0.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 0.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.08, 135.3 / 1000.0, 0.7864);
    // tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2); // "HV", "UNIFAC_UMRPRU");
    tempSystem.init(0);
    tempSystem.init(1);
    // tempSystem.saveFluid(928);

    SaturationTemperature saturationTemperature = new SaturationTemperature(tempSystem);
    CountingThermodynamicOperations countingOperations = new CountingThermodynamicOperations(tempSystem);
    saturationTemperature.thermoOps = countingOperations;

    double result = saturationTemperature.calcSaturationTemperature();

    assertEquals(380.127567672, result, 0.1);
    assertTrue(countingOperations.getFlashCount() < 115, "upper-bound search should stop after bracketing saturation");
    assertTrue(countingOperations.getMinimumTemperature() > 300.0,
        "search should not scan the expensive low-temperature range");
  }

  /** Regression test for the full-range scan reported in issue 3269. */
  @Test
  void issue3269FluidStopsAtUpperSaturationTemperature() {
    SystemInterface fluid = createIssue3269Fluid();
    SaturationTemperature saturationTemperature = new SaturationTemperature(fluid);
    CountingThermodynamicOperations countingOperations = new CountingThermodynamicOperations(fluid);
    saturationTemperature.thermoOps = countingOperations;

    double result = saturationTemperature.calcSaturationTemperature();

    assertEquals(29.4644, result - 273.15, 0.001);
    assertTrue(countingOperations.getFlashCount() < 115,
        "issue 3269 fluid should stop after bracketing the upper boundary");
    assertTrue(countingOperations.getMinimumTemperature() > 290.0,
        "issue 3269 fluid should not be flashed from the 30 K global minimum");
  }

  /**
   * checkSaturationTemperatureToPhaseEnvelope.
   *
   * @throws Exception
   */
  @Test
  @DisplayName("calculate phase envelope using UMR")
  public void checkSaturationTemperatureToPhaseEnvelope() throws Exception {
    SystemUMRPRUMCEos testSystem = new neqsim.thermo.system.SystemUMRPRUMCEos(298.0, 10.0);
    testSystem.addComponent("N2", 0.00675317857);
    testSystem.addComponent("CO2", .02833662296);
    testSystem.addComponent("methane", 0.8363194562);
    testSystem.addComponent("ethane", 0.06934307324);
    testSystem.addComponent("propane", 0.03645246567);
    testSystem.addComponent("i-butane", 0.0052133558);
    testSystem.addComponent("n-butane", 0.01013260919);
    testSystem.addComponent("i-pentane", 0.00227310164);
    testSystem.addComponent("n-pentane", 0.00224658464);
    testSystem.addComponent("2-m-C5", 0.00049491);
    testSystem.addComponent("3-m-C5", 0.00025783);
    testSystem.addComponent("n-hexane", 0.00065099);
    testSystem.addComponent("c-hexane", .00061676);
    testSystem.addComponent("n-heptane", 0.00038552);
    testSystem.addComponent("benzene", 0.00016852);
    testSystem.addComponent("n-octane", 0.00007629);
    testSystem.addComponent("c-C7", 0.0002401);
    testSystem.addComponent("toluene", 0.0000993);
    testSystem.addComponent("n-nonane", 0.00001943);
    testSystem.addComponent("c-C8", 0.00001848);
    testSystem.addComponent("m-Xylene", 0.00002216);
    testSystem.addComponent("nC10", 0.00000905);
    testSystem.addComponent("nC11", 0.000000001);
    testSystem.addComponent("nC12", 0.000000001);

    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.init(0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.calcPTphaseEnvelope();
    } catch (Exception ex) {
      assertTrue(false);
      throw new Exception(ex);
    }
    assertEquals((testOps.get("cricondentherm")[0] - 273.15), 23.469, 0.02);
    assertEquals(testOps.get("cricondentherm")[1], 46.9326702068279, 0.02);

    testSystem.setPressure(testOps.get("cricondentherm")[1], "bara");
    SaturationTemperature satTempSim = new SaturationTemperature(testSystem);
    satTempSim.run();
    assertEquals(satTempSim.getThermoSystem().getTemperature() - 273.15, 23.469396812206867, 0.001);
  }

  private static SystemInterface createIssue3269Fluid() {
    SystemInterface fluid = new SystemUMRPRUMCEos(280.0, 10.0);
    fluid.addComponent("CO2", 0.0174985900521278);
    fluid.addComponent("nitrogen", 0.0064392895437777);
    fluid.addComponent("methane", 0.792318403720856);
    fluid.addComponent("ethane", 0.098281517624855);
    fluid.addComponent("propane", 0.0536346212029457);
    fluid.addComponent("i-butane", 0.00683294050395489);
    fluid.addComponent("n-butane", 0.015396480448544);
    fluid.addComponent("i-pentane", 0.00299966568127275);
    fluid.addComponent("n-pentane", 0.0034116730093956);
    fluid.addComponent("2-m-C5", 0.000556670012883842);
    fluid.addComponent("3-m-C5", 0.000284214998828247);
    fluid.addComponent("n-hexane", 0.000755471992306411);
    fluid.addComponent("n-heptane", 0.000334932003170252);
    fluid.addComponent("c-hexane", 0.000783208000939339);
    fluid.addComponent("c-C7", 0.000296807993436232);
    fluid.addComponent("benzene", 0.000156905996846035);
    fluid.addComponent("n-octane", 0.0000632131996098906);
    fluid.addComponent("toluene", 0.0000737486989237368);
    fluid.addComponent("c-C8", 0.0000236678006775375);
    fluid.addComponent("n-nonane", 0.000015582600099151);
    fluid.addComponent("m-Xylene", 0.0000217079996218672);
    fluid.addComponent("nC10", 0.0000176893008756451);
    fluid.addComponent("nC11", 0.000000098000001003129);
    fluid.addComponent("nC12", 0.00000048999999080479);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    fluid.setPressure(57.6755867004395, "bara");
    fluid.setTemperature(24.9726753234863, "C");
    return fluid;
  }

  private static final class CountingThermodynamicOperations extends ThermodynamicOperations {
    private int flashCount;
    private double minimumTemperature = Double.POSITIVE_INFINITY;

    private CountingThermodynamicOperations(SystemInterface system) {
      super(system);
    }

    @Override
    public void TPflash() {
      flashCount++;
      minimumTemperature = Math.min(minimumTemperature, getSystem().getTemperature());
      super.TPflash();
    }

    private int getFlashCount() {
      return flashCount;
    }

    private double getMinimumTemperature() {
      return minimumTemperature;
    }
  }
}
