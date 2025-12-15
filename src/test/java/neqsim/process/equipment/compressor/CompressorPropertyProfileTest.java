package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class CompressorPropertyProfileTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  double pressure_Out = 150.0;
  ProcessSystem processOps = null;
  neqsim.process.equipment.compressor.Compressor compressor1 = null;

  /**
   * <p>
   * setUp.
   * </p>
   *
   * @throws java.lang.Exception if any.
   */
  @BeforeEach
  public void setUp() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    compressor1 = new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicEfficiency(0.89);
    processOps.add(inletStream);
    processOps.add(compressor1);
  }

  @Test
  public void testRunCalculation() {
    compressor1.setPolytropicMethod("detailed"); // Need detailed method for multi-step profile
    compressor1.setNumberOfCompressorCalcSteps(40);
    compressor1.getPropertyProfile().setActive(true);
    processOps.run();
    // double density3 = compressor1.getPropertyProfile().getFluid().get(3).getDensity("kg/m3");
    double density39 = compressor1.getPropertyProfile().getFluid().get(39).getDensity("kg/m3");
    assertEquals(85.4664664074326, density39, 59.465718447138336 / 100.0);
  }

  @Test
  public void testFailRunCalculation() {
    try {
      compressor1.setNumberOfCompressorCalcSteps(40);
      compressor1.getPropertyProfile().setActive(false);
      processOps.run();
      compressor1.getPropertyProfile().getFluid().get(3);
      assert (false);
    } catch (Exception ex) {
      assert (true);
    }
  }
}
