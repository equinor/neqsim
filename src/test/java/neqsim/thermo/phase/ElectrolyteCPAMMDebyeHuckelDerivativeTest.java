package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.thermo.component.ComponentSrkCPAMM;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermo.system.SystemInterface;

/**
 * Finite-difference validation of the shared extended Debye-Huckel derivative state.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAMMDebyeHuckelDerivativeTest {
  /** Confirms the analytic dilute limits used instead of the former zero-derivative cutoff. */
  @Test
  void screeningSeriesHasAnalyticDiluteLimits() {
    assertEquals(1.0, PhaseElectrolyteCPAMM.calcTauDH(0.0), 0.0);
    assertEquals(-0.75, PhaseElectrolyteCPAMM.calcTauDHprime(0.0), 0.0);
    assertEquals(1.2, PhaseElectrolyteCPAMM.calcTauDHsecond(0.0), 0.0);
    assertEquals(-3.0, PhaseElectrolyteCPAMM.calcTauDHthird(0.0), 0.0);

    double transition = 5e-5;
    double step = 1e-8;
    double numericalFirst = (PhaseElectrolyteCPAMM.calcTauDH(transition + step)
        - PhaseElectrolyteCPAMM.calcTauDH(transition - step)) / (2.0 * step);
    assertEquals(PhaseElectrolyteCPAMM.calcTauDHprime(transition), numericalFirst, 2e-5);

    double cutoff = 0.05;
    double below = Math.nextDown(cutoff);
    assertEquals(PhaseElectrolyteCPAMM.calcTauDH(below), PhaseElectrolyteCPAMM.calcTauDH(cutoff), 1e-11);
    assertEquals(PhaseElectrolyteCPAMM.calcTauDHprime(below), PhaseElectrolyteCPAMM.calcTauDHprime(cutoff), 1e-9);
    assertEquals(PhaseElectrolyteCPAMM.calcTauDHsecond(below), PhaseElectrolyteCPAMM.calcTauDHsecond(cutoff), 1e-7);
    assertEquals(PhaseElectrolyteCPAMM.calcTauDHthird(below), PhaseElectrolyteCPAMM.calcTauDHthird(cutoff), 1e-5);
  }

  /**
   * Checks the complete temperature and volume derivative blocks against independently evaluated states.
   *
   * @param rule solvent dielectric mixing rule
   */
  @ParameterizedTest
  @EnumSource(PhaseElectrolyteCPAMM.DielectricMixingRule.class)
  void phaseDerivativesMatchFixedAmountDifferences(PhaseElectrolyteCPAMM.DielectricMixingRule rule) {
    SystemInterface fluid = fluid(rule);
    byte[] snapshot = SerializationUtils.serialize(fluid);
    PhaseElectrolyteCPAMM phase = phase(fluid);
    DebyeHuckelDerivativeState state = state(fluid);

    double temperatureStep = 2e-4 * phase.getTemperature();
    assertDerivative(state.getDT(), scalarDifference(snapshot, -2, -1, temperatureStep),
        scalarDifference(snapshot, -2, -1, temperatureStep / 2.0), "temperature first derivative " + rule);
    assertDerivative(state.getDTT(), derivativeDifference(snapshot, -2, -1, temperatureStep, Derivative.T),
        derivativeDifference(snapshot, -2, -1, temperatureStep / 2.0, Derivative.T),
        "temperature second derivative " + rule);

    double volumeStep = 2e-4 * physicalVolume(phase);
    assertDerivative(state.getDV(), scalarDifference(snapshot, -1, -1, volumeStep),
        scalarDifference(snapshot, -1, -1, volumeStep / 2.0), "volume first derivative " + rule);
    assertDerivative(state.getDVV(), derivativeDifference(snapshot, -1, -1, volumeStep, Derivative.V),
        derivativeDifference(snapshot, -1, -1, volumeStep / 2.0, Derivative.V), "volume second derivative " + rule);
    assertDerivative(state.getDVVV(), derivativeDifference(snapshot, -1, -1, volumeStep, Derivative.VV),
        derivativeDifference(snapshot, -1, -1, volumeStep / 2.0, Derivative.VV), "volume third derivative " + rule);
    assertDerivative(state.getDTV(), derivativeDifference(snapshot, -1, -1, volumeStep, Derivative.T),
        derivativeDifference(snapshot, -1, -1, volumeStep / 2.0, Derivative.T),
        "temperature-volume derivative " + rule);
  }

  /**
   * Checks all component rows and columns at fixed total volume and verifies the delegated component API.
   *
   * @param rule solvent dielectric mixing rule
   */
  @ParameterizedTest
  @EnumSource(PhaseElectrolyteCPAMM.DielectricMixingRule.class)
  void componentDerivativesMatchFixedVolumeDifferences(PhaseElectrolyteCPAMM.DielectricMixingRule rule) {
    SystemInterface fluid = fluid(rule);
    byte[] snapshot = SerializationUtils.serialize(fluid);
    PhaseElectrolyteCPAMM phase = phase(fluid);
    DebyeHuckelDerivativeState state = state(fluid);
    double amountStep = 2e-5 * phase.getNumberOfMolesInPhase();
    double temperatureStep = 2e-4 * phase.getTemperature();
    double volumeStep = 2e-4 * physicalVolume(phase);

    for (int row = 0; row < fluid.getNumberOfComponents(); row++) {
      ComponentSrkCPAMM component = (ComponentSrkCPAMM) phase.getComponent(row);
      assertEquals(state.getDN(row),
          component.dFDebyeHuckeldN(phase, fluid.getNumberOfComponents(), phase.getTemperature(), phase.getPressure()),
          0.0, "component delegation row=" + row);
      assertDerivative(state.getDN(row), scalarDifference(snapshot, row, -1, amountStep),
          scalarDifference(snapshot, row, -1, amountStep / 2.0), "amount first derivative row=" + row + ", " + rule);
      assertDerivative(state.getDNT(row), componentDifference(snapshot, -2, -1, temperatureStep, row),
          componentDifference(snapshot, -2, -1, temperatureStep / 2.0, row),
          "amount-temperature derivative row=" + row + ", " + rule);
      assertDerivative(state.getDNV(row), componentDifference(snapshot, -1, -1, volumeStep, row),
          componentDifference(snapshot, -1, -1, volumeStep / 2.0, row),
          "amount-volume derivative row=" + row + ", " + rule);
      for (int column = 0; column < fluid.getNumberOfComponents(); column++) {
        assertEquals(state.getDNN(row, column), state.getDNN(column, row),
            2e-12 * Math.max(1.0, Math.abs(state.getDNN(row, column))), "Hessian symmetry");
        assertDerivative(state.getDNN(row, column), componentDifference(snapshot, column, -1, amountStep, row),
            componentDifference(snapshot, column, -1, amountStep / 2.0, row),
            "amount Hessian row=" + row + ", column=" + column + ", " + rule);
      }
    }
  }

  /** Derivative selected for central differences. */
  private enum Derivative {
    /** First temperature derivative. */
    T,
    /** First SI-volume derivative. */
    V,
    /** Second SI-volume derivative. */
    VV
  }

  /**
   * Creates a mixed-solvent KCl phase so every dielectric rule has nontrivial composition derivatives.
   *
   * @param rule solvent dielectric mixing rule
   * @return initialized single aqueous phase
   */
  private static SystemInterface fluid(PhaseElectrolyteCPAMM.DielectricMixingRule rule) {
    SystemInterface fluid = new SystemElectrolyteCPAMM(298.15, 50.0);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("MEG", 0.2);
    fluid.addComponent("K+", 0.02);
    fluid.addComponent("Cl-", 0.02);
    fluid.setMixingRule(10);
    ((PhaseElectrolyteCPAMM) fluid.getPhase(0)).setDielectricMixingRule(rule);
    fluid.setForceSinglePhase(PhaseType.AQUEOUS);
    fluid.init(3);
    return fluid;
  }

  /**
   * Computes a central difference of the scalar Helmholtz contribution.
   *
   * @param snapshot serialized base system
   * @param variable temperature, volume or component coordinate
   * @param pair optional paired component
   * @param step coordinate step
   * @return central difference
   */
  private static double scalarDifference(byte[] snapshot, int variable, int pair, double step) {
    return (state(trial(snapshot, variable, pair, step)).getValue()
        - state(trial(snapshot, variable, pair, -step)).getValue()) / (2.0 * step);
  }

  /**
   * Computes a central difference of a phase derivative.
   *
   * @param snapshot serialized base system
   * @param variable temperature, volume or component coordinate
   * @param pair optional paired component
   * @param step coordinate step
   * @param derivative derivative to inspect
   * @return central difference
   */
  private static double derivativeDifference(byte[] snapshot, int variable, int pair, double step,
      Derivative derivative) {
    DebyeHuckelDerivativeState plus = state(trial(snapshot, variable, pair, step));
    DebyeHuckelDerivativeState minus = state(trial(snapshot, variable, pair, -step));
    return (derivative(plus, derivative) - derivative(minus, derivative)) / (2.0 * step);
  }

  /**
   * Selects a phase derivative.
   *
   * @param state derivative state
   * @param derivative requested derivative
   * @return derivative value
   */
  private static double derivative(DebyeHuckelDerivativeState state, Derivative derivative) {
    if (derivative == Derivative.T) {
      return state.getDT();
    }
    if (derivative == Derivative.V) {
      return state.getDV();
    }
    return state.getDVV();
  }

  /**
   * Computes a central difference of a component derivative.
   *
   * @param snapshot serialized base system
   * @param variable temperature, volume or component coordinate
   * @param pair optional paired component
   * @param step coordinate step
   * @param row component derivative row
   * @return central difference
   */
  private static double componentDifference(byte[] snapshot, int variable, int pair, double step, int row) {
    return (state(trial(snapshot, variable, pair, step)).getDN(row)
        - state(trial(snapshot, variable, pair, -step)).getDN(row)) / (2.0 * step);
  }

  /**
   * Creates an independent fixed-volume trial.
   *
   * @param snapshot serialized base system
   * @param variable minus two for temperature, minus one for SI volume, otherwise component index
   * @param pair optional paired component
   * @param change coordinate increment
   * @return perturbed initialized system
   */
  private static SystemInterface trial(byte[] snapshot, int variable, int pair, double change) {
    SystemInterface trial = SerializationUtils.deserialize(snapshot);
    PhaseElectrolyteCPAMM phase = phase(trial);
    double volume = physicalVolume(phase);
    if (variable >= 0) {
      trial.addComponent(variable, change, 0);
      if (pair >= 0) {
        trial.addComponent(pair, change, 0);
      }
      trial.initBeta();
      trial.init_x_y();
    } else if (variable == -2) {
      trial.setTemperature(trial.getTemperature() + change);
    } else {
      volume += change;
    }
    phase.setMolarVolume(volume / (phase.getNumberOfMolesInPhase() * 1e-5));
    phase.initElectrolyteProperties();
    return trial;
  }

  /**
   * Gets the active model phase.
   *
   * @param fluid single-phase fluid
   * @return electrolyte phase
   */
  private static PhaseElectrolyteCPAMM phase(SystemInterface fluid) {
    return (PhaseElectrolyteCPAMM) fluid.getPhase(0);
  }

  /**
   * Builds the exact derivative state.
   *
   * @param fluid initialized fluid
   * @return derivative state
   */
  private static DebyeHuckelDerivativeState state(SystemInterface fluid) {
    return new DebyeHuckelDerivativeState(phase(fluid));
  }

  /**
   * Returns physical total volume.
   *
   * @param phase phase to inspect
   * @return total volume in cubic metres
   */
  private static double physicalVolume(PhaseElectrolyteCPAMM phase) {
    return phase.getMolarVolume() * phase.getNumberOfMolesInPhase() * 1e-5;
  }

  /**
   * Checks finite values, finite-difference step convergence and analytic agreement.
   *
   * @param analytic analytic derivative
   * @param coarse central difference at the initial step
   * @param fine central difference at half the step
   * @param label diagnostic label
   */
  private static void assertDerivative(double analytic, double coarse, double fine, String label) {
    assertTrue(Double.isFinite(analytic) && Double.isFinite(coarse) && Double.isFinite(fine), label);
    double scale = Math.max(Math.abs(analytic), Math.abs(fine));
    double tolerance = 5e-10 + 2e-5 * scale;
    assertEquals(fine, coarse, tolerance, label + " step convergence");
    assertEquals(fine, analytic, tolerance, label + " analytic derivative");
  }
}
