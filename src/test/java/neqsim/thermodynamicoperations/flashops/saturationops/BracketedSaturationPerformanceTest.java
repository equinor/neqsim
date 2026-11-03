package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Frozen equilibrium and deterministic work guards for bracketed saturation calculations.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class BracketedSaturationPerformanceTest {
  /**
   * Builds a fixed-feed GERG-water gas.
   *
   * @param pressure absolute pressure in bara
   * @param ppm water mole fraction in parts per million
   * @return gas before the dew-point calculation
   */
  private SystemInterface waterGas(double pressure, double ppm) {
    SystemInterface fluid = new SystemGERGwaterEos(273.15, pressure);
    fluid.addComponent("methane", 1.0 - ppm * 1.0e-6);
    fluid.addComponent("water", ppm * 1.0e-6);
    fluid.setMixingRule(8);
    return fluid;
  }

  /** Checks strict fugacity closure and the frozen master roots on both sides of water freezing. */
  @Test
  void waterDewPointRetainsRootsWithBoundedWork() {
    double[][] cases = {{5.0, 1.0, 207.90594973773693}, {70.0, 5.0, 243.58882832396773},
        {70.0, 22.0, 257.0870561018586}, {100.0, 100.0, 276.7757237382233}};
    for (double[] point : cases) {
      SystemInterface fluid = waterGas(point[0], point[1]);
      WaterDewPointTemperatureFlash flash = new WaterDewPointTemperatureFlash(fluid);
      flash.run();
      assertEquals(point[2], fluid.getTemperature(), 1.0e-6);
      assertEquals(point[0], fluid.getPressure(), 0.0);
      assertEquals(point[1] * 1.0e-6, fluid.getPhase(0).getComponent("water").getx(), 1.0e-15);
      assertPureResidual(fluid, "water");
      assertTrue(flash.getLastRefinementEvaluations() <= 16,
          "Smooth water dew points must not require dozens of phase initializations");
    }
  }

  /** Checks controlling-component selection and the aggregate work budget for water and MEG. */
  @Test
  void waterMegDewPointRetainsControllingComponent() {
    SystemInterface fluid = new SystemGERGwaterEos(273.15, 70.0);
    fluid.addComponent("methane", 0.99985);
    fluid.addComponent("water", 0.00005);
    fluid.addComponent("MEG", 0.0001);
    fluid.setMixingRule(8);
    WaterDewPointTemperatureFlash flash = new WaterDewPointTemperatureFlash(fluid);
    flash.run();
    assertEquals(333.25498427599666, fluid.getTemperature(), 1.0e-6);
    assertEquals(1.0, fluid.getPhase(1).getComponent("MEG").getx(), 0.0);
    assertPureResidual(fluid, "MEG");
    assertTrue(flash.getLastRefinementEvaluations() <= 32);
  }

  /** Checks dissolved-species fugacity closure on the water-rich CPA branch. */
  @Test
  void mixedLiquidDewPointClosesEverySpeciesWithBoundedWork() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15, 70.0);
    fluid.addComponent("methane", 0.95 * (1.0 - 22.0e-6));
    fluid.addComponent("CO2", 0.03 * (1.0 - 22.0e-6));
    fluid.addComponent("ethane", 0.02 * (1.0 - 22.0e-6));
    fluid.addComponent("water", 22.0e-6);
    fluid.setMixingRule(10);
    WaterDewPointTemperatureFlash flash = new WaterDewPointTemperatureFlash(fluid, true);
    flash.run();
    assertEquals(248.28581681996582, fluid.getTemperature(), 1.0e-6);
    assertTrue(fluid.getPhase(1).getComponent("water").getx() > 0.5);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double gasFugacity = fluid.getPhase(0).getComponent(component).getx()
          * fluid.getPhase(0).getComponent(component).getFugacityCoefficient();
      double liquidFugacity = fluid.getPhase(1).getComponent(component).getx()
          * fluid.getPhase(1).getComponent(component).getFugacityCoefficient();
      assertEquals(0.0, Math.log(gasFugacity / liquidFugacity), 1.0e-8);
    }
    assertTrue(flash.getLastRefinementEvaluations() <= 16);
  }

  /** Checks that repeated searches count only their own refinement work. */
  @Test
  void dewPointWorkCounterResetsForChangedPressure() {
    SystemInterface fluid = waterGas(5.0, 22.0);
    WaterDewPointTemperatureFlash flash = new WaterDewPointTemperatureFlash(fluid);
    for (double pressure : new double[] {5.0, 70.0, 100.0}) {
      fluid.setPressure(pressure);
      flash.run();
      assertPureResidual(fluid, "water");
      assertTrue(flash.getLastRefinementEvaluations() <= 16);
    }
  }

  /**
   * Checks the legacy pure-condensed-reference residual independently of the root solver.
   *
   * @param fluid solved fluid
   * @param component controlling water or MEG component
   */
  private void assertPureResidual(SystemInterface fluid, String component) {
    double residual = Math.log(fluid.getPhase(0).getComponent(component).getz())
        + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient()
        - fluid.getPhase(1).getComponent(component).getLogFugacityCoefficient();
    assertEquals(0.0, residual, 1.0e-9);
  }

  /**
   * Builds the public one-kilogram-water brine fixture with explicit CO2 ternary screening assumptions.
   *
   * @param guest hydrate guest component
   * @param pressure absolute pressure in bara
   * @return one-molal NaCl brine before the incipient hydrate search
   */
  private SystemPitzer brine(String guest, double pressure) {
    SystemPitzer fluid = new SystemPitzer(280.0, pressure);
    fluid.addComponent(guest, 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    if ("CO2".equals(guest)) {
      Map<String, Double> zeta = new LinkedHashMap<String, Double>();
      zeta.put("Na+", 0.0);
      zeta.put("K+", 0.0);
      zeta.put("Ca++", 0.0);
      fluid.applyPhreeqcCo2ChlorideParameters(zeta);
    }
    return fluid;
  }

  /** Checks gas and liquid CO2 branches, methane and strict hydrate fugacity closure. */
  @Test
  void pitzerHydrateTemperatureRetainsRootsWithBoundedWork() {
    String[] guests = {"CO2", "CO2", "methane"};
    double[][] cases = {{30.0, 277.86897629737854}, {100.0, 280.3449377441406}, {100.0, 283.80502100944517}};
    for (int index = 0; index < guests.length; index++) {
      SystemPitzer fluid = brine(guests[index], cases[index][0]);
      PitzerHydrateFlash flash = new PitzerHydrateFlash(fluid, false);
      flash.run();
      assertEquals(cases[index][1], fluid.getTemperature(), 2.0e-6);
      assertEquals(cases[index][0], fluid.getPressure(), 0.0);
      assertHydrateState(fluid, flash);
      assertTrue(flash.getLastRefinementEvaluations() <= 12);
    }
  }

  /** Checks that pressure interpolation retains the first low-pressure crossing and the fixed temperature. */
  @Test
  void pitzerHydratePressureRetainsFirstCrossingWithBoundedWork() {
    SystemPitzer fluid = brine("CO2", 20.0);
    fluid.setTemperature(278.0);
    PitzerHydrateFlash flash = new PitzerHydrateFlash(fluid, true);
    flash.run();
    assertEquals(30.59331382414578, fluid.getPressure(), 2.0e-5);
    assertEquals(278.0, fluid.getTemperature(), 0.0);
    assertHydrateState(fluid, flash);
    assertTrue(flash.getLastRefinementEvaluations() <= 12);
  }

  /** Checks counter reset when one Pitzer operation crosses gas and liquid CO2 states. */
  @Test
  void pitzerHydrateWorkCounterResetsForChangedPressure() {
    SystemPitzer fluid = brine("CO2", 30.0);
    PitzerHydrateFlash flash = new PitzerHydrateFlash(fluid, false);
    for (double pressure : new double[] {30.0, 40.0, 100.0}) {
      fluid.setPressure(pressure);
      flash.run();
      assertHydrateState(fluid, flash);
      assertTrue(flash.getLastRefinementEvaluations() <= 12);
    }
  }

  /**
   * Checks the physical residual, normalized fluid phases, water activity and conserved component inventories.
   *
   * @param fluid solved Pitzer fluid
   * @param flash incipient hydrate operation
   */
  private void assertHydrateState(SystemPitzer fluid, PitzerHydrateFlash flash) {
    assertTrue(flash.isConverged());
    assertEquals(0.0, flash.getResidual(), 1.0e-8);
    assertTrue(flash.getWaterActivity() > 0.0 && flash.getWaterActivity() <= 1.0 + 1.0e-8);
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      assertTrue(fluid.getBeta(phase) >= 0.0 && fluid.getBeta(phase) <= 1.0);
      betaSum += fluid.getBeta(phase);
      double sum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        sum += fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(1.0, sum, 1.0e-12);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getNumberOfmoles(), recovered,
          1.0e-10 * fluid.getTotalNumberOfMoles());
    }
  }
}
