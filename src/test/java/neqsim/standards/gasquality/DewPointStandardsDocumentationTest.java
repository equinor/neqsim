package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the complete examples and limitations in the dew-point standards guide. */
class DewPointStandardsDocumentationTest extends neqsim.NeqSimTest {
  @Test
  void documentedWaterDewPointWorkflow() {
    SystemInterface wetGas = new SystemGERGwaterEos(268.15, 20.0);
    wetGas.addComponent("methane", 0.9);
    wetGas.addComponent("water", 0.0000051);
    wetGas.createDatabase(true);
    wetGas.setMixingRule(8);
    wetGas.init(0);

    Standard_ISO18453 waterDewPoint = new Standard_ISO18453(wetGas);
    waterDewPoint.setPressure(70.0);
    waterDewPoint.calculate();

    double waterDewPointC = waterDewPoint.getValue("dewPointTemperature", "C");
    double calculationPressureBara = waterDewPoint.getValue("pressure");

    assertEquals(-21.775841183117222, waterDewPointC, 1.0e-8);
    assertEquals(70.0, calculationPressureBara, 1.0e-12);
    assertEquals("C", waterDewPoint.getUnit("dewPointTemperature"));
    assertEquals(waterDewPointC + 273.15,
        waterDewPoint.getValue("dewPointTemperature", "K"), 1.0e-12);

    double maximumWaterDewPointC = -8.0;
    boolean withinWaterDewPointLimit =
        Double.isFinite(waterDewPointC) && waterDewPointC <= maximumWaterDewPointC;
    assertTrue(withinWaterDewPointLimit);
  }

  @Test
  void documentedHydrocarbonDewPointWorkflow() {
    SystemInterface richGas = createRichGas();

    BestPracticeHydrocarbonDewPoint hydrocarbonDewPoint =
        new BestPracticeHydrocarbonDewPoint(richGas);
    hydrocarbonDewPoint.calculate();

    double hydrocarbonDewPointC =
        hydrocarbonDewPoint.getValue("hydrocarbondewpointTemperature", "C");
    double calculationPressureBara = hydrocarbonDewPoint.getValue("pressure");

    assertTrue(Double.isFinite(hydrocarbonDewPointC));
    assertTrue(hydrocarbonDewPointC > -100.0 && hydrocarbonDewPointC < 100.0);
    assertEquals(50.0, calculationPressureBara, 1.0e-12);

    double maximumHydrocarbonDewPointC = -2.0;
    boolean comparisonIsDefined =
        Double.isFinite(hydrocarbonDewPointC)
            && Double.isFinite(maximumHydrocarbonDewPointC);
    assertTrue(comparisonIsDefined);
  }

  @Test
  void hydrocarbonReferencePressureSetterDoesNotChangeFixedPressure() {
    BestPracticeHydrocarbonDewPoint hydrocarbonDewPoint =
        new BestPracticeHydrocarbonDewPoint(createRichGas());
    hydrocarbonDewPoint.setReferencePressure(80.0);
    hydrocarbonDewPoint.calculate();

    assertEquals(80.0, hydrocarbonDewPoint.getReferencePressure(), 1.0e-12);
    assertEquals(50.0, hydrocarbonDewPoint.getValue("pressure"), 1.0e-12);
  }

  private static SystemInterface createRichGas() {
    SystemInterface richGas = new SystemSrkEos(293.15, 70.0);
    richGas.addComponent("methane", 0.85);
    richGas.addComponent("ethane", 0.05);
    richGas.addComponent("propane", 0.03);
    richGas.addComponent("i-butane", 0.01);
    richGas.addComponent("n-butane", 0.015);
    richGas.addComponent("i-pentane", 0.005);
    richGas.addComponent("n-pentane", 0.005);
    richGas.addComponent("n-hexane", 0.003);
    richGas.addComponent("nitrogen", 0.02);
    richGas.addComponent("CO2", 0.012);
    richGas.setMixingRule("classic");
    return richGas;
  }
}
