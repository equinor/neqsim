package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

class CalculatorLibraryTest {
  @Test
  void energyBalanceMatchesInputEnthalpy() {
    SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setTemperature(280.0, "K");
    inlet.setPressure(50.0, "bara");
    inlet.run();

    Stream outlet = new Stream("outlet", fluid.clone());
    outlet.setTemperature(320.0, "K");
    outlet.setPressure(50.0, "bara");
    outlet.run();

    Calculator calculator = new Calculator("Energy Balance");
    calculator.addInputVariable(inlet);
    calculator.setOutputVariable(outlet);
    calculator.setCalculationMethod(CalculatorLibrary.energyBalance());

    calculator.run();

    double inletEnthalpy = inlet.getThermoSystem().getEnthalpy();
    double outletEnthalpy = outlet.getThermoSystem().getEnthalpy();
    assertEquals(inletEnthalpy, outletEnthalpy, Math.abs(inletEnthalpy) * 1e-6);
  }

  @Test
  void dewPointTargetingUsesPresetResolver() {
    SystemSrkEos fluid = new SystemSrkEos(290.0, 15.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    Stream source = new Stream("source", fluid);
    source.setPressure(15.0, "bara");
    source.setTemperature(290.0, "K");
    source.run();

    Stream target = new Stream("target", fluid.clone());
    target.setPressure(12.0, "bara");
    target.setTemperature(320.0, "K");
    target.run();

    Calculator calculator = new Calculator("dew point targeter");
    calculator.addInputVariable(source);
    calculator.setOutputVariable(target);
    calculator.setCalculationMethod(CalculatorLibrary.byName("dewPointTargeting"));

    calculator.run();

    double dewPoint =
        source.getHydrocarbonDewPoint("K", target.getThermoSystem().getPressure("bara"), "bara");
    assertEquals(dewPoint, target.getTemperature("K"), 1e-6);
    assertTrue(target.getThermoSystem().getPressure() > 0.0);
  }
}
