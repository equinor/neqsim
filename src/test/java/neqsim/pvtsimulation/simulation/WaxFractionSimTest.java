package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * Regression checks for characterized wax curves and inventory-based mass fractions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WaxFractionSimTest {
  /** Verifies a characterized plus-fraction wax curve and its final mass balance. */
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
    assertEquals(0.2850772245845892, sepSim.getThermoSystem().getPhaseFraction("wax", "mass"), 1e-8);
    assertWaxMassBalance(sepSim);
  }

  /** Verifies a wax curve without splitting the plus fraction and its final mass balance. */
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
    assertEquals(0.265356710992052, sepSim.getThermoSystem().getPhaseFraction("wax", "mass"), 1e-8);
    assertWaxMassBalance(sepSim);
  }

  /**
   * Independently checks phase masses against the feed component inventory and the reported curve.
   *
   * @param simulation completed wax curve
   */
  private static void assertWaxMassBalance(WaxFractionSim simulation) {
    SystemInterface fluid = simulation.getThermoSystem();
    double feedMass = 0.0;
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      feedMass += fluid.getPhase(0).getComponent(component).getNumberOfmoles()
          * fluid.getPhase(0).getComponent(component).getMolarMass();
    }
    double totalFraction = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double mass = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        mass += fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase()
            * fluid.getPhase(phase).getComponent(component).getMolarMass();
      }
      double fraction = fluid.getPhaseFraction(fluid.getPhase(phase).getPhaseTypeName(), "mass");
      assertTrue(Double.isFinite(fraction) && fraction >= 0.0 && fraction <= 1.0);
      assertEquals(mass / feedMass, fraction, 1e-10);
      totalFraction += fraction;
    }
    assertEquals(1.0, totalFraction, 1e-7);
    double[] curve = simulation.getWaxFraction();
    assertEquals(curve[curve.length - 1], fluid.getPhaseFraction("wax", "mass"), 1e-12);
  }
}
