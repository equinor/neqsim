package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class SaturationPressureTest extends neqsim.NeqSimTest {
  @BeforeAll
  static void setUpBeforeClass() {
  }

  @Test
  void testCalcSaturationPressure() {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 20, 10.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 0.59);
    tempSystem.addComponent("methane", 87.42);
    tempSystem.addComponent("ethane", 3.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 0.38);
    tempSystem.addTBPfraction("C7", 0.05, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 0.069, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 0.014, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.0078, 135.3 / 1000.0, 0.7864);
    tempSystem.setMixingRule(2);
    SimulationInterface satPresSim = new SaturationPressure(tempSystem);
    satPresSim.run();
    assertEquals(satPresSim.getThermoSystem().getPressure(), 126.195102691, 0.1);
  }

  /**
   * Regression test for the trivial-split dew-point artifact.
   *
   * <p>
   * This near-critical retrograde gas (UMR-PRU, HV / UNIFAC_UMRPRU) has its upper dew point at 0 degC around 106.2
   * bara. Above that pressure the TP flash can converge to a trivial split — two phases with essentially identical
   * composition and density — which is a non-physical solution of the flash equations. Before the trivial-split
   * collapse guard in {@code TPflash}, the saturation pressure search treated those identical-phase points as two-phase
   * and returned a spurious value near 109 bara. The correct dew point is confirmed by the phase-envelope saturation
   * solver (~105.9 bara). This test guards against reintroducing the regression.
   * </p>
   */
  @Test
  void testTrivialSplitDoesNotInflateDewPointPressure() {
    double f = 0.6185035705566406;
    SystemInterface fluid = new neqsim.thermo.system.SystemUMRPRUMCEos(273.15, 10.0);
    fluid.addComponent("CO2", 0.016177945);
    fluid.addComponent("nitrogen", 0.006857943);
    fluid.addComponent("methane", 0.784699957);
    fluid.addComponent("ethane", 0.095835435);
    fluid.addComponent("propane", 0.058808079);
    fluid.addComponent("i-butane", 0.007946427);
    fluid.addComponent("n-butane", 0.018245426);
    fluid.addComponent("i-pentane", 0.003795847);
    fluid.addComponent("n-pentane", 0.003870575);
    fluid.addComponent("2-m-C5", f * 0.000556670);
    fluid.addComponent("3-m-C5", f * 0.000284215);
    fluid.addComponent("n-hexane", f * 0.000755472);
    fluid.addComponent("n-heptane", f * 0.000334932);
    fluid.addComponent("c-hexane", f * 0.000783208);
    fluid.addComponent("c-C7", f * 0.0002968081);
    fluid.addComponent("benzene", f * 0.000156906);
    fluid.addComponent("n-octane", f * 0.00006321321);
    fluid.addComponent("toluene", f * 0.00007374866);
    fluid.addComponent("c-C8", f * 0.00002366789);
    fluid.addComponent("n-nonane", f * 0.00001558264);
    fluid.addComponent("m-Xylene", f * 0.00002170802);
    fluid.addComponent("nC10", f * 0.00001768925);
    fluid.addComponent("nC11", f * 0.00000009800553);
    fluid.addComponent("nC12", f * 0.0000004900277);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    fluid.setTemperature(0.0, "C");

    SaturationPressure satPresSim = new SaturationPressure(fluid);
    satPresSim.run();

    // Correct upper dew point ~106.2 bara; the trivial-split regression returned ~109 bara.
    assertEquals(106.2, satPresSim.getSaturationPressure(), 1.0);
  }
}
