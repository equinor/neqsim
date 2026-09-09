package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;

/**
 * Accuracy and robustness tests for CO2 hydrate equilibrium in pure water and in brine.
 *
 * <p>
 * The pure water reference points are the carbon dioxide Lw-H-V equilibrium data of Deaton and Frost (1946), as
 * tabulated by Sloan and Koh, <i>Clathrate Hydrates of Natural Gases</i>, 3rd edition. They span the whole
 * hydrate-stable range between the ice point and the upper quadruple point at about 283 K and 4.5 MPa, which is the
 * range that matters for CO2 hydrate risk in drilling fluids and in CO2 transport.
 * </p>
 *
 * <p>
 * The brine tests check salt inhibition. They deliberately assert on the temperature suppression relative to fresh
 * water rather than on absolute temperatures, because the suppression is the quantity that an empirical drilling-fluid
 * correlation is built on, and it is far less sensitive to the choice of reference data set.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
@Tag("slow")
public class CO2HydrateEquilibriumTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CO2HydrateEquilibriumTest.class);

  /** Deaton and Frost (1946) CO2 Lw-H-V equilibrium temperatures in Kelvin. */
  private static final double[] REFERENCE_TEMPERATURES = { 273.7, 274.3, 275.4, 276.5, 277.6, 279.3, 280.4, 281.5,
      282.6 };

  /** Deaton and Frost (1946) CO2 Lw-H-V equilibrium pressures in bara. */
  private static final double[] REFERENCE_PRESSURES = { 12.9, 14.2, 15.4, 17.5, 20.1, 24.4, 27.9, 31.9, 38.5 };

  /** Molar mass of water in g/mol. */
  private static final double WATER_MOLAR_MASS = 18.015;

  /** Molar mass of sodium chloride in g/mol. */
  private static final double SODIUM_CHLORIDE_MOLAR_MASS = 58.44;

  /**
   * Verifies the CO2 hydrate equilibrium curve in pure water against Deaton and Frost (1946).
   *
   * @throws Exception if a hydrate equilibrium calculation fails
   */
  @Test
  @DisplayName("CO2 hydrate curve in pure water matches Deaton and Frost (1946)")
  public void testCO2HydrateCurveInPureWater() throws Exception {
    double sumAbsoluteDeviation = 0.0;
    double maximumAbsoluteDeviation = 0.0;

    for (int point = 0; point < REFERENCE_PRESSURES.length; point++) {
      SystemInterface fluid = new SystemSrkCPAstatoil(280.0, REFERENCE_PRESSURES[point]);
      fluid.addComponent("CO2", 0.5);
      fluid.addComponent("water", 0.5);
      fluid.setMixingRule(10);
      fluid.setHydrateCheck(true);

      new ThermodynamicOperations(fluid).hydrateFormationTemperature();
      double calculated = fluid.getTemperature();
      double deviation = calculated - REFERENCE_TEMPERATURES[point];

      logger.info("CO2 hydrate at {} bara: calculated {} K, reference {} K, deviation {} K", REFERENCE_PRESSURES[point],
          calculated, REFERENCE_TEMPERATURES[point], deviation);

      sumAbsoluteDeviation += Math.abs(deviation);
      maximumAbsoluteDeviation = Math.max(maximumAbsoluteDeviation, Math.abs(deviation));
    }

    double averageAbsoluteDeviation = sumAbsoluteDeviation / REFERENCE_PRESSURES.length;
    logger.info("CO2 hydrate in pure water: AAD {} K, maximum deviation {} K", averageAbsoluteDeviation,
        maximumAbsoluteDeviation);

    assertTrue(averageAbsoluteDeviation < 0.5,
        "CO2 hydrate curve average deviation must stay within experimental scatter, got " + averageAbsoluteDeviation
            + " K");
    assertTrue(maximumAbsoluteDeviation < 0.8,
        "No CO2 hydrate point may deviate by more than 0.8 K, got " + maximumAbsoluteDeviation + " K");
  }

  /**
   * Verifies that NaCl suppresses the CO2 hydrate temperature monotonically and by a physically credible amount.
   *
   * @throws Exception if a hydrate equilibrium calculation fails
   */
  @Test
  @DisplayName("NaCl suppresses the CO2 hydrate temperature monotonically")
  public void testSaltSuppressionOfCO2HydrateTemperature() throws Exception {
    double[] saltMassPercent = { 0.0, 5.0, 10.0, 15.0 };
    double[] hydrateTemperature = new double[saltMassPercent.length];

    for (int point = 0; point < saltMassPercent.length; point++) {
      hydrateTemperature[point] = calculateBrineHydrateTemperature(saltMassPercent[point], 20.1);
      logger.info("CO2 hydrate at 20.1 bara with {} wt% NaCl: {} K", saltMassPercent[point], hydrateTemperature[point]);
    }

    for (int point = 1; point < saltMassPercent.length; point++) {
      assertTrue(hydrateTemperature[point] < hydrateTemperature[point - 1],
          "Increasing NaCl content must lower the CO2 hydrate temperature, but " + saltMassPercent[point] + " wt% gave "
              + hydrateTemperature[point] + " K against " + hydrateTemperature[point - 1] + " K at "
              + saltMassPercent[point - 1] + " wt%");
    }

    // Published CO2 hydrate suppression in NaCl brine is roughly 2 K at 5 wt%, 4 to 5 K at 10 wt%
    // and 7 to 9 K at 15 wt% (Dholabhai, Englezos, Kalogerakis and Bishnoi, 1991). The bands below
    // are deliberately wide enough to accommodate the scatter between published data sets while
    // still failing if the salt activity contribution is lost or double counted.
    assertSuppressionWithinBand(hydrateTemperature[0] - hydrateTemperature[1], 1.0, 3.5, "5 wt% NaCl");
    assertSuppressionWithinBand(hydrateTemperature[0] - hydrateTemperature[2], 2.5, 7.0, "10 wt% NaCl");
    assertSuppressionWithinBand(hydrateTemperature[0] - hydrateTemperature[3], 5.0, 12.0, "15 wt% NaCl");
  }

  /**
   * Verifies that a negligible feed component cannot corrupt CPA fugacities during initialization.
   *
   * <p>
   * A cross associating component at a negligible overall concentration can make the association Hessian singular and
   * return NaN fugacity coefficients. The initialization cutoff is pinned here using trace feed amounts. Phase-local
   * depletion of a material feed component must not disable its association sites.
   * </p>
   */
  @Test
  @DisplayName("Trace associating components leave CPA fugacity coefficients finite")
  public void testTraceAssociatingComponentDoesNotCorruptFugacities() {
    double referenceFugacityCoefficient = waterFugacityCoefficientWithTraceCO2(0.0);
    assertTrue(Double.isFinite(referenceFugacityCoefficient), "Pure water reference fugacity must be finite");

    double[] traceMoles = { 1.0e-50, 1.0e-45, 1.0e-40, 1.0e-35, 1.0e-30, 1.0e-25, 1.0e-21 };
    for (int point = 0; point < traceMoles.length; point++) {
      double fugacityCoefficient = waterFugacityCoefficientWithTraceCO2(traceMoles[point]);
      assertTrue(Double.isFinite(fugacityCoefficient),
          "A trace of " + traceMoles[point] + " mol CO2 must not make the water fugacity coefficient non-finite");
      assertEquals(referenceFugacityCoefficient, fugacityCoefficient, 1.0e-12,
          "A trace of " + traceMoles[point] + " mol CO2 must not shift the water fugacity coefficient");
    }
  }

  /**
   * Verifies that the same trace limit holds for the electrolyte CPA model used for brine.
   */
  @Test
  @DisplayName("Trace associating components leave electrolyte CPA fugacity coefficients finite")
  public void testTraceAssociatingComponentInBrineDoesNotCorruptFugacities() {
    double[] traceMoles = { 1.0e-50, 1.0e-40, 1.0e-30, 1.0e-25 };
    double referenceFugacityCoefficient = Double.NaN;

    for (int point = 0; point < traceMoles.length; point++) {
      SystemInterface brine = new SystemElectrolyteCPAstatoil(275.0, 20.1);
      double waterMoles = 900.0 / WATER_MOLAR_MASS;
      double saltMoles = 100.0 / SODIUM_CHLORIDE_MOLAR_MASS;
      brine.addComponent("CO2", traceMoles[point]);
      brine.addComponent("water", waterMoles);
      brine.addComponent("Na+", saltMoles);
      brine.addComponent("Cl-", saltMoles);
      brine.setMixingRule(10);
      brine.init(0);
      brine.init(1);

      double fugacityCoefficient = brine.getPhase(0).getComponent("water").getFugacityCoefficient();
      assertTrue(Double.isFinite(fugacityCoefficient),
          "A trace of " + traceMoles[point] + " mol CO2 must not make the brine water fugacity coefficient non-finite");

      if (point == 0) {
        referenceFugacityCoefficient = fugacityCoefficient;
      } else {
        assertEquals(referenceFugacityCoefficient, fugacityCoefficient, 1.0e-12,
            "The brine water fugacity coefficient must not depend on a negligible CO2 trace");
      }
    }
  }

  /**
   * Verifies that a hydrate calculation never reports a salt brine as more hydrate prone than fresh water.
   *
   * <p>
   * A CO2 rich brine at high salinity used to drive the flash into a degenerate phase split. The water fugacity
   * equality was then satisfied by an artefact, and the solver returned a converged looking temperature well above the
   * fresh water hydrate temperature, which is thermodynamically impossible for an inhibited aqueous phase. Adding salt
   * can only lower the hydrate temperature, so the calculation must either return a lower temperature or report that it
   * has no answer.
   * </p>
   *
   * @throws Exception if the fresh water reference calculation fails
   */
  @Test
  @DisplayName("A CO2 rich brine never reports a hydrate temperature above fresh water")
  public void testDegeneratePhaseSplitDoesNotProduceASilentAnswer() throws Exception {
    double freshWaterHydrateTemperature = calculateBrineHydrateTemperature(0.0, 20.1);

    double waterMoles = 1000.0 * (1.0 - 15.0 / 100.0) / WATER_MOLAR_MASS;
    double saltMoles = 1000.0 * 15.0 / 100.0 / SODIUM_CHLORIDE_MOLAR_MASS;

    SystemInterface brine = new SystemElectrolyteCPAstatoil(280.0, 20.1);
    brine.addComponent("CO2", waterMoles * 0.5);
    brine.addComponent("water", waterMoles);
    brine.addComponent("Na+", saltMoles);
    brine.addComponent("Cl-", saltMoles);
    brine.setMixingRule(10);
    brine.setHydrateCheck(true);

    double reportedTemperature;
    try {
      new ThermodynamicOperations(brine).hydrateFormationTemperature();
      reportedTemperature = brine.getTemperature();
    } catch (IsNaNException ex) {
      logger.info("CO2 rich 15 wt% NaCl brine correctly reported a failure: {}", ex.getMessage());
      assertTrue(Double.isNaN(brine.getTemperature()), "A rejected root must not leave a finite temperature");
      return;
    } finally {
      assertEquals(waterMoles * 0.5, brine.getPhase(0).getComponent("CO2").getNumberOfmoles(), 1.0e-8);
      assertEquals(waterMoles, brine.getPhase(0).getComponent("water").getNumberOfmoles(), 1.0e-8);
      assertEquals(saltMoles, brine.getPhase(0).getComponent("Na+").getNumberOfmoles(), 1.0e-8);
      assertEquals(saltMoles, brine.getPhase(0).getComponent("Cl-").getNumberOfmoles(), 1.0e-8);
    }

    assertTrue(Double.isFinite(reportedTemperature) && reportedTemperature < freshWaterHydrateTemperature,
        "A 15 wt% NaCl brine reported a hydrate temperature of " + reportedTemperature
            + " K, which is not below the fresh water value of " + freshWaterHydrateTemperature + " K");
  }

  /**
   * Assert that a hydrate temperature suppression falls inside a published band.
   *
   * @param suppression calculated suppression relative to fresh water in Kelvin
   * @param minimum lowest accepted suppression in Kelvin
   * @param maximum highest accepted suppression in Kelvin
   * @param label description of the brine used in the assertion message
   */
  private static void assertSuppressionWithinBand(double suppression, double minimum, double maximum, String label) {
    assertTrue(suppression > minimum && suppression < maximum, "CO2 hydrate suppression for " + label + " is "
        + suppression + " K, outside the expected band of " + minimum + " to " + maximum + " K");
  }

  /**
   * Calculate the CO2 hydrate equilibrium temperature of a water continuous NaCl brine.
   *
   * @param saltMassPercent sodium chloride content of the aqueous phase in mass percent
   * @param pressure pressure in bara
   * @return hydrate equilibrium temperature in Kelvin
   * @throws Exception if the hydrate equilibrium calculation fails
   */
  private static double calculateBrineHydrateTemperature(double saltMassPercent, double pressure) throws Exception {
    double waterMoles = 1000.0 * (1.0 - saltMassPercent / 100.0) / WATER_MOLAR_MASS;
    double saltMoles = 1000.0 * saltMassPercent / 100.0 / SODIUM_CHLORIDE_MOLAR_MASS;

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(280.0, pressure);
    fluid.addComponent("CO2", waterMoles * 0.10);
    fluid.addComponent("water", waterMoles);
    if (saltMoles > 0.0) {
      fluid.addComponent("Na+", saltMoles);
      fluid.addComponent("Cl-", saltMoles);
    }
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);

    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    double temperature = fluid.getTemperature();
    assertTrue(Double.isFinite(temperature),
        "Hydrate temperature for " + saltMassPercent + " wt% NaCl must be a converged number");
    return temperature;
  }

  /**
   * Evaluate the water fugacity coefficient of a water phase holding a trace of CO2.
   *
   * @param traceMoles number of moles of CO2 added to 55.5 mol of water
   * @return fugacity coefficient of water
   */
  private static double waterFugacityCoefficientWithTraceCO2(double traceMoles) {
    SystemInterface fluid = new SystemSrkCPAstatoil(275.0, 20.1);
    if (traceMoles > 0.0) {
      fluid.addComponent("CO2", traceMoles);
    }
    fluid.addComponent("water", 55.5);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(1);
    return fluid.getPhase(0).getComponent("water").getFugacityCoefficient();
  }
}
