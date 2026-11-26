package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks that the R134a component carries an ideal-gas heat capacity, so that refrigeration-cycle enthalpy changes
 * (compressor work) are realistic.
 *
 * @author ESOL
 * @version 1.0
 */
public class R134aRefrigerantTest extends neqsim.NeqSimTest {

  /**
   * Isentropic compression of R134a vapour from 3.57 bara (8.6 C, 3 K superheat) to 21 bara must give about 37.2 kJ/kg
   * (CoolProp Helmholtz reference), which needs the ideal-gas Cp.
   */
  @Test
  public void isentropicCompressionWorkMatchesReference() {
    SystemInterface fluid = new SystemSrkEos(281.75, 3.57);
    fluid.addComponent("R134a", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1.0, "kg/sec");

    Stream suction = new Stream("suction", fluid);
    suction.run();
    Compressor compressor = new Compressor("compressor", suction);
    compressor.setOutletPressure(21.0);
    compressor.setIsentropicEfficiency(1.0);
    compressor.run();

    double workKjPerKg = compressor.getPower() / 1.0e3;
    assertEquals(37.24, workKjPerKg, 0.04 * 37.24);
  }
}
