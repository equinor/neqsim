package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
    double inputMass = 6.78 * tempSystem.getPhase(0).getComponent("methane").getMolarMass() + 10.13 * 0.170
        + 10.62 * 0.381;
    tempSystem.getCharacterization().characterisePlusFraction();
    double characterizedMass = 0.0;
    for (int component = 0; component < tempSystem.getNumberOfComponents(); component++) {
      characterizedMass += tempSystem.getPhase(0).getComponent(component).getNumberOfmoles()
          * tempSystem.getPhase(0).getComponent(component).getMolarMass();
    }
    assertEquals(6.78 + 10.13 + 10.62, tempSystem.getTotalNumberOfMoles(), 1e-10);
    assertEquals(inputMass, characterizedMass, inputMass * 1e-11);
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
    // Corrected Pedersen mean MW changes the wax-former slate; retain the same tolerance.
    assertEquals(0.28263214407722204, sepSim.getThermoSystem().getPhaseFraction("wax", "mass"), 1e-8);
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

  /**
   * A feed whose wax formers are named n-paraffins, set up with {@code addTBPWax()} as documented, must give a
   * mass-conserving wax fraction (#5210, reported by Patrick Mockridge). Named wax formers get the Pedersen melting
   * temperature and heat of fusion, and the wax flash must converge every phase fraction when the liquid freezes out.
   */
  @Test
  void testNamedWaxFormerFeed() {
    WaxFractionSim wax = new WaxFractionSim(methaneEicosane(true));
    wax.setTemperaturesAndPressures(new double[] {280.0, 270.0, 260.0}, new double[] {5.0, 5.0, 5.0});
    wax.runCalc();
    assertWaxMassBalance(wax);

    SystemInterface fluid = wax.getThermoSystem();
    double eicosaneMass = 0.7 * fluid.getPhase(0).getComponent("nC20").getMolarMass();
    double feedMass = eicosaneMass + 0.3 * fluid.getPhase(0).getComponent("methane").getMolarMass();
    for (double fraction : wax.getWaxFraction()) {
      // Far below the nC20 melting point practically all of it is wax and methane is gas.
      assertEquals(eicosaneMass / feedMass, fraction, 1e-3);
    }
    assertTrue(fluid.hasPhaseType("gas"));
    assertEquals(0.3, fluid.getBeta(fluid.getPhaseNumberOfPhase("gas")), 1e-4);

    double meltingTemperature = fluid.getPhase(0).getComponent("nC20").getTriplePointTemperature();
    assertTrue(meltingTemperature > 305.0 && meltingTemperature < 315.0,
        "Pedersen melting temperature of nC20: " + meltingTemperature);
    assertEquals(meltingTemperature, fluid.getPhases()[5].getComponent("nC20").getTriplePointTemperature(), 1e-12);
  }

  /**
   * The named-wax-former properties do not depend on whether the wax phase is added before or after
   * {@code addTBPWax()}, and no wax forms above the melting temperature (#5210).
   */
  @Test
  void testNamedWaxFormerIsIndependentOfSetupOrder() {
    double[] temperatures = {330.0, 300.0, 280.0};
    double[] pressures = {5.0, 5.0, 5.0};
    WaxFractionSim waxPhaseFirst = new WaxFractionSim(methaneEicosane(true));
    waxPhaseFirst.setTemperaturesAndPressures(temperatures, pressures);
    waxPhaseFirst.runCalc();
    WaxFractionSim waxModelFirst = new WaxFractionSim(methaneEicosane(false));
    waxModelFirst.setTemperaturesAndPressures(temperatures, pressures);
    waxModelFirst.runCalc();

    assertArrayEquals(waxPhaseFirst.getWaxFraction(), waxModelFirst.getWaxFraction(), 1e-9);
    assertEquals(0.0, waxPhaseFirst.getWaxFraction()[0], 1e-12);
    assertTrue(waxPhaseFirst.getWaxFraction()[1] > 0.9);
  }

  /**
   * Named wax formers without database fusion data keep following the Pedersen tuning multipliers when
   * {@code addTBPWax()} is called again, while a named wax former with measured data keeps its database values (#5210).
   */
  @Test
  void testNamedWaxFormerFollowsTuningAndKeepsMeasuredData() {
    SystemInterface fluid = methaneEicosane(true);
    fluid.addComponent("nC16", 0.1);
    double measuredNc16 = fluid.getPhase(0).getComponent("nC16").getTriplePointTemperature();
    fluid.getWaxModel().addTBPWax();
    double correlated = fluid.getPhase(0).getComponent("nC20").getTriplePointTemperature();
    fluid.getWaxModel().setParameterWaxTriplePointTemperature(0, 1.01);
    fluid.getWaxModel().addTBPWax();
    assertEquals(1.01 * correlated, fluid.getPhase(0).getComponent("nC20").getTriplePointTemperature(), 1e-9);
    assertEquals(measuredNc16, fluid.getPhase(0).getComponent("nC16").getTriplePointTemperature(), 0.0);
  }

  /**
   * Builds the methane/n-eicosane wax feed of issue #5210.
   *
   * @param waxPhaseFirst true to add the wax phase before calling {@code addTBPWax()} (the order in the issue)
   * @return a new SRK fluid prepared for wax calculations
   */
  private static SystemInterface methaneEicosane(boolean waxPhaseFirst) {
    SystemInterface fluid = new SystemSrkEos(300.0, 5.0);
    fluid.addComponent("methane", 0.3);
    fluid.addComponent("nC20", 0.7);
    fluid.setMixingRule(2);
    if (!waxPhaseFirst) {
      fluid.getWaxModel().addTBPWax();
    }
    fluid.addSolidComplexPhase("wax");
    fluid.setMultiphaseWaxCheck(true);
    fluid.setMultiPhaseCheck(true);
    if (waxPhaseFirst) {
      fluid.getWaxModel().addTBPWax();
    }
    return fluid;
  }
}
