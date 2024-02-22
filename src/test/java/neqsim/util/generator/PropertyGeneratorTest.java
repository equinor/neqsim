package neqsim.util.generator;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class PropertyGeneratorTest {
  @Test
  void testCalculate() {

    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);
    fluid.addComponent("water", 0.1);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("H2S", 0.01);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("i-pentane", 0.01);
    fluid.addComponent("n-pentane", 0.01);
    fluid.addComponent("n-hexane", 0.01);
    fluid.addComponent("nC10", 0.1);
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(true);
    fluid.setMultiPhaseCheck(true);

    double[] temps = new double[] {280.0, 290.0};
    double[] pres = new double[] {10.0, 20.0};
    PropertyGenerator generator = new PropertyGenerator(fluid, temps, pres);
    generator.calculate();


  }
}
