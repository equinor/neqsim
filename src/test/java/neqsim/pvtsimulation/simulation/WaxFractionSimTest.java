package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

public class WaxFractionSimTest {
  @Test
  void testRunCalc() {
    NeqSimDataBase.setCreateTemporaryTables(true);
    SystemInterface tempSystem = new SystemSrkEos(298.0, 10.0);
    tempSystem.addComponent("methane", 6.78);
    tempSystem.addTBPfraction("C19", 10.13, 170.0 / 1000.0, 0.7814);
    tempSystem.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.850871882888);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.getWaxModel().addTBPWax();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.addSolidComplexPhase("wax");
    tempSystem.setMultiphaseWaxCheck(true);
    tempSystem.setMultiPhaseCheck(true);
    NeqSimDataBase.setCreateTemporaryTables(false);
    WaxFractionSim sepSim = new WaxFractionSim(tempSystem);
    double[] temps = {293.15, 283.15, 273.15, 264.15, 263, 262, 261};
    double[] pres = {5, 5, 5.0, 5.0, 5.0, 5.0, 5.0};
    sepSim.setTemperaturesAndPressures(temps, pres);
    sepSim.runCalc();
    assertEquals(0.2683853533110433, sepSim.getThermoSystem().getPhaseFraction("wax", "mass"),
        0.001);
  }

  @Test
  void testRunCalc2() {
    NeqSimDataBase.setCreateTemporaryTables(true);
    SystemInterface tempSystem = new SystemSrkEos(298.0, 10.0);
    tempSystem.addComponent("methane", 6.78);
    tempSystem.addTBPfraction("C19", 10.13, 170.0 / 1000.0, 0.7814);
    tempSystem.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.850871882888);
    tempSystem.getWaxModel().addTBPWax();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.addSolidComplexPhase("wax");
    tempSystem.setMultiphaseWaxCheck(true);
    tempSystem.setMultiPhaseCheck(true);
    NeqSimDataBase.setCreateTemporaryTables(false);
    tempSystem.init(0);
    tempSystem.init(1);
    WaxFractionSim sepSim = new WaxFractionSim(tempSystem);
    double[] temps = {293.15, 283.15, 273.15, 264.15, 263, 262, 261};
    double[] pres = {5, 5, 5.0, 5.0, 5.0, 5.0, 5.0};
    sepSim.setTemperaturesAndPressures(temps, pres);
    sepSim.runCalc();
    NeqSimDataBase.setCreateTemporaryTables(false);
    assertEquals(0.24895564649970403, sepSim.getThermoSystem().getPhaseFraction("wax", "mass"),
        0.001);
  }
}
