package neqsim.pvtsimulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies the executable constant-mass-expansion documentation example. */
class PvtLabTestsDocumentationTest extends NeqSimTest {
  @Test
  void testConstantMassExpansionQuickStart() {
    SystemInterface fluid = new SystemSrkEos(370.65, 350.0);
    fluid.addComponent("nitrogen", 0.39);
    fluid.addComponent("CO2", 0.30);
    fluid.addComponent("methane", 40.20);
    fluid.addComponent("ethane", 7.61);
    fluid.addComponent("propane", 7.95);
    fluid.addComponent("i-butane", 1.19);
    fluid.addComponent("n-butane", 4.08);
    fluid.addComponent("i-pentane", 1.39);
    fluid.addComponent("n-pentane", 2.15);
    fluid.addComponent("n-hexane", 2.79);
    fluid.addTBPfraction("C7", 4.28, 95.0 / 1000.0, 0.729);
    fluid.addTBPfraction("C8", 4.31, 106.0 / 1000.0, 0.749);
    fluid.addTBPfraction("C9", 3.08, 121.0 / 1000.0, 0.770);
    fluid.addTBPfraction("C10", 2.47, 135.0 / 1000.0, 0.786);
    fluid.addTBPfraction("C11", 1.91, 148.0 / 1000.0, 0.792);
    fluid.addTBPfraction("C12", 1.69, 161.0 / 1000.0, 0.804);
    fluid.addTBPfraction("C13", 1.59, 175.0 / 1000.0, 0.819);
    fluid.addTBPfraction("C14", 1.22, 196.0 / 1000.0, 0.833);
    fluid.addTBPfraction("C15", 1.25, 206.0 / 1000.0, 0.836);
    fluid.addTBPfraction("C16", 1.00, 225.0 / 1000.0, 0.843);
    fluid.addTBPfraction("C17", 0.99, 236.0 / 1000.0, 0.840);
    fluid.addTBPfraction("C18", 0.92, 245.0 / 1000.0, 0.846);
    fluid.addTBPfraction("C19", 0.60, 265.0 / 1000.0, 0.857);
    fluid.addPlusFraction("C20", 6.64, 453.0 / 1000.0, 0.918);
    fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");

    double[] pressuresBara = { 351.4, 323.2, 301.5, 275.9, 250.1, 226.1, 205.9, 197.3, 189.3, 183.3, 165.0, 131.2,
        108.3, 85.3, 55.6 };

    ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
    cce.setTemperature(97.5, "C");
    cce.setPressures(pressuresBara);
    cce.runCalc();

    double[] relativeVolume = cce.getRelativeVolume();
    double[] liquidRelativeVolume = cce.getLiquidRelativeVolume();
    double[] gasZ = cce.getZgas();
    double[] yFunction = cce.getYfactor();

    assertEquals(pressuresBara.length, relativeVolume.length);
    assertEquals(pressuresBara.length, liquidRelativeVolume.length);
    assertEquals(pressuresBara.length, gasZ.length);
    assertEquals(pressuresBara.length, yFunction.length);
    assertEquals(0.95756922523, relativeVolume[0], 0.001);
    assertEquals(1.35726592522, relativeVolume[12], 0.001);
    assertEquals(2.18937648076, yFunction[12], 0.001);
    assertTrue(Double.isFinite(cce.getSaturationPressure()));
    assertTrue(cce.getSaturationPressure() > 0.0);
  }
}
