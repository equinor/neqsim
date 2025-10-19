package neqsim.process.equipment.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.Fluid;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

class CO2ElectrolyzerTest extends neqsim.NeqSimTest {

  @Test
  void testSelectivityBasedConversionAndEnergyDemand() {
    SystemInterface feedFluid = new Fluid().create2(new String[] {"CO2", "water"},
        new double[] {0.95, 0.05}, "mole/sec");
    feedFluid.setTemperature(298.15);
    feedFluid.setPressure(20.0);

    Stream feedStream = new Stream("CO2 feed", feedFluid);
    feedStream.setTemperature(298.15, "K");
    feedStream.setPressure(20.0, "bara");
    feedStream.run();

    CO2Electrolyzer electrolyzer = new CO2Electrolyzer("CO2 electrolyzer", feedStream);
    double coSelectivity = 0.7;
    double hydrogenSelectivity = 0.3;
    double conversion = 0.55;

    electrolyzer.setCO2Conversion(conversion);
    electrolyzer.setGasProductSelectivity("CO", coSelectivity);
    electrolyzer.setGasProductSelectivity("H2", hydrogenSelectivity);
    electrolyzer.setProductFaradaicEfficiency("CO", 0.9);
    electrolyzer.setElectronsPerMoleProduct("H2", 2.0);

    electrolyzer.run();

    double inletCo2 = feedStream.getThermoSystem().getComponent("CO2").getFlowRate("mole/sec");
    double convertedCo2 = inletCo2 * conversion;
    double expectedCo2 = inletCo2 - convertedCo2;
    double expectedCo = convertedCo2 * coSelectivity;
    double expectedHydrogen = convertedCo2 * hydrogenSelectivity;

    SystemInterface gasProduct = electrolyzer.getGasProductStream().getThermoSystem();
    String co2Name = ComponentInterface.getComponentNameFromAlias("CO2");
    String coName = ComponentInterface.getComponentNameFromAlias("CO");
    String hydrogenName = ComponentInterface.getComponentNameFromAlias("H2");

    assertEquals(expectedCo2,
        gasProduct.getComponent(co2Name).getFlowRate("mole/sec"), 1e-6);
    assertEquals(expectedCo, gasProduct.getComponent(coName).getFlowRate("mole/sec"), 1e-6);
    assertEquals(expectedHydrogen,
        gasProduct.getComponent(hydrogenName).getFlowRate("mole/sec"), 1e-6);
    double electronMoles = expectedCo * 2.0 / 0.9 + expectedHydrogen * 2.0;
    double expectedPower = electronMoles / 0.95 * 96485.3329 * 2.7;
    assertEquals(expectedPower, electrolyzer.getEnergyStream().getDuty(), 1e-3);
  }
}
