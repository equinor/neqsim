package neqsim.process.safety.selfheating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SelfHeatingInductionSolver}.
 *
 * <p>
 * The transient solver and the steady-state criticality model are independent implementations of the same physics, so
 * the most valuable tests here cross-validate them: a body held below its Frank-Kamenetskii critical temperature must
 * reach a steady profile, and one held above it must run away.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SelfHeatingInductionSolverTest {

  /** Activation energy used across the tests [J/mol]. */
  private static final double E = 110000.0;

  /** Volumetric heat-release pre-factor used across the tests [W/m3]. */
  private static final double P = 5.0e13;

  /** Effective thermal conductivity used across the tests [W/(m K)]. */
  private static final double LAMBDA = 0.09;

  /** Volumetric heat capacity used across the tests [J/(m3 K)]. */
  private static final double RHO_C = 1.8e5;

  /** Characteristic half-dimension used across the tests [m]. */
  private static final double DIMENSION_M = 0.05;

  /**
   * Compute the Frank-Kamenetskii critical temperature for the reference case.
   *
   * @return critical boundary temperature in K
   */
  private double criticalTemperature() {
    return new PorousMediaSelfHeatingAnalyzer().setGeometry(SelfHeatingGeometry.SLAB)
        .setCharacteristicDimension(DIMENSION_M, "m").setEffectiveThermalConductivity(LAMBDA)
        .setActivationEnergy(E, "J/mol").setVolumetricHeatReleasePreFactor(P).setBoundaryTemperature(400.0, "K")
        .analyze().getCriticalTemperatureK();
  }

  /**
   * Build a transient solver for the reference case at the supplied boundary temperature.
   *
   * @param boundaryTemperatureK boundary temperature in K
   * @return a configured solver
   */
  private SelfHeatingInductionSolver solver(double boundaryTemperatureK) {
    return new SelfHeatingInductionSolver().setGeometry(SelfHeatingGeometry.SLAB)
        .setCharacteristicDimension(DIMENSION_M, "m").setEffectiveThermalConductivity(LAMBDA)
        .setVolumetricHeatCapacity(RHO_C).setActivationEnergy(E, "J/mol").setVolumetricHeatReleasePreFactor(P)
        .setBoundaryTemperature(boundaryTemperatureK, "K").setNodeCount(21).setMaxTime(5.0, "day");
  }

  /**
   * Above the Frank-Kamenetskii critical temperature the body must ignite after a finite induction period.
   */
  @Test
  void supercriticalCaseIgnites() {
    SelfHeatingInductionResult result = solver(criticalTemperature() + 20.0).solve();

    assertTrue(result.isIgnited(), "a supercritical body must ignite");
    assertTrue(result.getInductionTimeS() > 0.0, "induction time must be positive");
    assertFalse(Double.isNaN(result.getInductionTimeS()), "induction time must be defined after ignition");
    assertTrue(result.getPeakTemperatureRiseK() >= result.getIgnitionRiseK(),
        "peak rise must reach the ignition criterion");
    assertFalse(result.isSteadyStateReached(), "an igniting body must not report a steady state");
  }

  /**
   * Below the Frank-Kamenetskii critical temperature the body must settle to a steady profile with only a small
   * temperature excess.
   */
  @Test
  void subcriticalCaseReachesSteadyState() {
    SelfHeatingInductionResult result = solver(criticalTemperature() - 10.0).solve();

    assertFalse(result.isIgnited(), "a subcritical body must not ignite");
    assertTrue(result.isSteadyStateReached(), "a subcritical body must reach a steady profile");
    assertTrue(result.getPeakTemperatureRiseK() < 50.0,
        "subcritical self-heating excess must stay small, was " + result.getPeakTemperatureRiseK());
    assertTrue(result.getPeakTemperatureRiseK() >= 0.0, "the body must not cool below its boundary");
  }

  /**
   * The transient and steady-state models must agree on which side of criticality a case falls, across a range of
   * boundary temperatures.
   */
  @Test
  void transientAgreesWithSteadyStateCriticality() {
    double tCrit = criticalTemperature();
    double[] offsets = new double[] { -40.0, -20.0, 25.0, 50.0 };

    for (int i = 0; i < offsets.length; i++) {
      double temperature = tCrit + offsets[i];
      SelfHeatingInductionResult transientResult = solver(temperature).solve();
      boolean steadyStatePredictsIgnition = offsets[i] > 0.0;
      assertEquals(steadyStatePredictsIgnition, transientResult.isIgnited(),
          "transient and steady-state models must agree at " + temperature + " K");
    }
  }

  /**
   * Induction time must shorten as the boundary temperature rises, which is why a small increase in surface temperature
   * can turn a slow smoulder into a prompt fire.
   */
  @Test
  void inductionTimeShortensWithTemperature() {
    double tCrit = criticalTemperature();
    double slow = solver(tCrit + 20.0).solve().getInductionTimeS();
    double fast = solver(tCrit + 60.0).solve().getInductionTimeS();

    assertTrue(fast < slow, "a hotter boundary must ignite sooner");
    assertTrue(fast > 0.0, "induction time must remain positive");
  }

  /**
   * The induction period must be long compared with a flame, confirming the delayed character of a lagging fire.
   */
  @Test
  void inductionPeriodIsSlow() {
    SelfHeatingInductionResult result = solver(criticalTemperature() + 20.0).solve();
    assertTrue(result.getInductionTimeS() > 60.0, "self-heating ignition must take far longer than a flame");
    assertEquals(result.getInductionTimeS() / 3600.0, result.getInductionTimeHours(), 1.0e-9);
  }

  /**
   * The retained history must be non-empty, ordered in time, and bounded in length.
   */
  @Test
  void historyIsOrderedAndBounded() {
    SelfHeatingInductionResult result = solver(criticalTemperature() + 20.0).solve();
    List<SelfHeatingTimePoint> history = result.getHistory();

    assertTrue(history.size() > 2, "history must contain several samples");
    assertTrue(history.size() <= 1000, "history must stay bounded");
    for (int i = 1; i < history.size(); i++) {
      assertTrue(history.get(i).getTimeS() >= history.get(i - 1).getTimeS(), "history must be ordered in time");
      assertTrue(history.get(i).getMaxTemperatureK() >= history.get(i).getCentreTemperatureK() - 1.0e-9,
          "peak temperature must not fall below the centre temperature");
    }
  }

  /**
   * A hotter start must reach ignition sooner than starting at the boundary temperature.
   */
  @Test
  void hotterInitialConditionIgnitesSooner() {
    double temperature = criticalTemperature() + 20.0;
    double fromBoundary = solver(temperature).solve().getInductionTimeS();
    double fromHot = solver(temperature).setInitialTemperature(temperature + 40.0, "K").solve().getInductionTimeS();

    assertTrue(fromHot < fromBoundary, "a pre-heated body must ignite sooner");
  }

  /**
   * Shapes without a one-dimensional reduction must be rejected by the transient solver.
   */
  @Test
  void nonOneDimensionalGeometryIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new SelfHeatingInductionSolver().setGeometry(SelfHeatingGeometry.CUBE),
        "a cube has no one-dimensional reduction");
    assertTrue(SelfHeatingGeometry.SPHERE.isOneDimensional());
    assertFalse(SelfHeatingGeometry.EQUICYLINDER.isOneDimensional());
  }

  /**
   * Missing mandatory inputs and invalid settings must be rejected.
   */
  @Test
  void invalidConfigurationIsRejected() {
    assertThrows(IllegalStateException.class,
        () -> new SelfHeatingInductionSolver().setCharacteristicDimension(0.05, "m").solve(),
        "solving without kinetics must fail");
    assertThrows(IllegalArgumentException.class, () -> new SelfHeatingInductionSolver().setNodeCount(3),
        "too few nodes must be rejected");
    assertThrows(IllegalArgumentException.class, () -> new SelfHeatingInductionSolver().setMaxTime(1.0, "fortnight"),
        "an unsupported time unit must be rejected");
  }

  /**
   * Setting bulk density and specific heat must be equivalent to setting the volumetric heat capacity directly.
   */
  @Test
  void bulkPropertiesMatchVolumetricHeatCapacity() {
    double temperature = criticalTemperature() + 20.0;
    double direct = solver(temperature).solve().getInductionTimeS();
    double fromBulk = solver(temperature).setBulkProperties(150.0, RHO_C / 150.0).solve().getInductionTimeS();

    assertEquals(direct, fromBulk, Math.max(1.0, direct * 1.0e-6));
  }

  /**
   * The result must serialise to JSON for reporting.
   */
  @Test
  void resultSerialisesToJson() {
    String json = solver(criticalTemperature() + 20.0).solve().toJson();
    assertTrue(json.contains("inductionTimeS"), "JSON must contain the induction time");
    assertTrue(json.contains("history"), "JSON must contain the temperature history");
  }
}
