package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SeparatorTestTest {
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = new SystemSrkEos(298.0, 300.0);
    tempSystem.addComponent("nitrogen", 0.64);
    tempSystem.addComponent("CO2", 3.53);
    tempSystem.addComponent("methane", 70.78);
    tempSystem.addComponent("ethane", 8.94);
    tempSystem.addComponent("propane", 5.05);
    tempSystem.addComponent("i-butane", 0.85);
    tempSystem.addComponent("n-butane", 1.68);
    tempSystem.addComponent("i-pentane", 0.62);
    tempSystem.addComponent("n-pentane", 0.79);
    tempSystem.addComponent("n-hexane", 0.83);
    tempSystem.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
    tempSystem.addTBPfraction("C8", 1.06, 104.6 / 1000.0, 0.7602);
    tempSystem.addTBPfraction("C9", 0.79, 119.1 / 1000.0, 0.7677);
    tempSystem.addTBPfraction("C10", 0.57, 133.0 / 1000.0, 0.79);
    tempSystem.addTBPfraction("C11", 0.38, 155.0 / 1000.0, 0.795);
    tempSystem.addTBPfraction("C12", 0.37, 162.0 / 1000.0, 0.806);
    tempSystem.addTBPfraction("C13", 0.32, 177.0 / 1000.0, 0.824);
    tempSystem.addTBPfraction("C14", 0.27, 198.0 / 1000.0, 0.835);
    tempSystem.addTBPfraction("C15", 0.23, 202.0 / 1000.0, 0.84);
    tempSystem.addTBPfraction("C16", 0.19, 215.0 / 1000.0, 0.846);
    tempSystem.addTBPfraction("C17", 0.17, 234.0 / 1000.0, 0.84);
    tempSystem.addTBPfraction("C18", 0.13, 251.0 / 1000.0, 0.844);
    tempSystem.addTBPfraction("C19", 0.13, 270.0 / 1000.0, 0.854);
    tempSystem.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.88);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);

    SeparatorTest sepSim = new SeparatorTest(tempSystem);
    double[] temps = {313.15, 313.15, 313.15, 313.15, 313.15, 313.15, 313.15};
    double[] pres = {500, 400, 200, 100, 50.0, 5.0, ThermodynamicConstantsInterface.referencePressure};
    sepSim.setSeparatorConditions(temps, pres);
    sepSim.runCalc();

    assertEquals(1.1224612120760051, sepSim.getBofactor()[4], 0.0001);
  }

  /** Verifies stock-tank properties against a separate flash of the oil carried from the final separator. */
  @Test
  void testStockTankFlashesCarriedOil() {
    double stageTemperature = 350.0;
    double stagePressure = 50.0;
    SystemInterface feed = new SystemSrkEos(stageTemperature, stagePressure);
    feed.addComponent("methane", 0.70);
    feed.addComponent("nC10", 0.30);
    feed.setMixingRule(2);

    SystemInterface stage = feed.clone();
    new ThermodynamicOperations(stage).TPflash();
    assertTrue(stage.hasPhaseType("gas") && stage.hasPhaseType("oil"));
    SystemInterface stockTank = stage.phaseToSystem("oil");
    stockTank.setPressure(ThermodynamicConstantsInterface.referencePressure);
    stockTank.setTemperature(288.15);
    new ThermodynamicOperations(stockTank).TPflash();
    stockTank.initPhysicalProperties();
    assertTrue(stockTank.hasPhaseType("gas") && stockTank.hasPhaseType("oil"));

    SeparatorTest test = new SeparatorTest(feed);
    test.setSeparatorConditions(new double[] {stageTemperature}, new double[] {stagePressure});
    test.runCalc();

    assertEquals(stockTank.getPhase("oil").getDensity("kg/m3"), test.getStockTankOilDensity(), 1e-6);
    assertEquals(stockTank.getPhase("gas").getMolarMass() * 1000.0 / ThermodynamicConstantsInterface.molarMassAir,
        test.getStockTankGasGravity(), 1e-9);
    assertEquals(stage.getPhase("oil").getVolume() / stockTank.getPhase("oil").getVolume(), test.getBofactor()[0],
        1e-8);
  }
}
