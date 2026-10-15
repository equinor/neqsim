package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPAMM;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent extensive-energy and equilibrium checks for the implemented MM electrolyte terms.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAMMDerivativeConsistencyTest {
  /** The dielectric Taylor expansion supports TTV, but deliberately does not approximate TTT. */
  @Test
  void thirdDerivativesRejectUnsupportedTemperatureOrder() {
    PhaseElectrolyteCPAMM phase = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE, 1);
    int volume = phase.getNumberOfComponents();
    int temperature = volume + 1;
    assertThrows(IllegalArgumentException.class,
        () -> MMResidualDerivative.derivative(phase, -1, temperature, temperature, temperature));
    double step = step(phase, volume);
    PhaseElectrolyteCPAMM high = perturb(phase, volume, step);
    PhaseElectrolyteCPAMM low = perturb(phase, volume, -step);
    double numerical = (MMResidualDerivative.derivative(high, -1, temperature, temperature, -1)
        - MMResidualDerivative.derivative(low, -1, temperature, temperature, -1)) / (2 * step);
    close(MMResidualDerivative.derivative(phase, -1, temperature, temperature, volume), numerical, 2e-7);
  }

  /** Verifies first and mixed derivatives independently for all supported dielectric mixing rules. */
  @Test
  void finiteDifferencesAndThermodynamicIdentities() {
    for (PhaseElectrolyteCPAMM.DielectricMixingRule rule : PhaseElectrolyteCPAMM.DielectricMixingRule.values()) {
      PhaseElectrolyteCPAMM phase = mixedPhase(rule, 1);
      int count = phase.getNumberOfComponents();
      double euler = phase.getTotalVolume() * phase.dFdV() - phase.getF();
      for (int i = 0; i < count + 2; i++) {
        double step = step(phase, i);
        PhaseElectrolyteCPAMM high = perturb(phase, i, step);
        PhaseElectrolyteCPAMM low = perturb(phase, i, -step);
        close(first(phase, i), (high.getF() - low.getF()) / (2 * step), 2e-6);
        if (i < count) {
          euler += phase.getComponent(i).getNumberOfMolesInPhase() * first(phase, i);
          double gibbsDuhem = phase.getTotalVolume() * second(phase, i, count);
          for (int j = 0; j < count; j++) {
            gibbsDuhem += phase.getComponent(j).getNumberOfMolesInPhase() * second(phase, i, j);
          }
          assertEquals(0, gibbsDuhem, 2e-8, "Gibbs-Duhem identity for " + rule);
        }
        for (int j = 0; j < count + 2; j++) {
          close(second(phase, i, j), (first(high, j) - first(low, j)) / (2 * step), 2e-6);
          assertEquals(second(phase, i, j), second(phase, j, i), 2e-10, "Maxwell symmetry for " + rule);
        }
      }
      assertEquals(0, euler, 2e-8, "Extensivity for " + rule);
      assertEquals(phase.getPressure(), phase.calcPressure(), 1e-7, "EOS pressure for " + rule);
    }
  }

  /** Checks third volume derivatives of the corrected electrolyte terms without inherited CPA approximations. */
  @Test
  void electrolyteThirdVolumeDerivative() {
    PhaseElectrolyteCPAMM phase = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE, 1);
    int count = phase.getNumberOfComponents();
    double step = step(phase, count);
    PhaseElectrolyteCPAMM high = perturb(phase, count, step);
    PhaseElectrolyteCPAMM low = perturb(phase, count, -step);
    close(phase.dFDebyeHuckeldVdVdV(), (high.dFDebyeHuckeldVdV() - low.dFDebyeHuckeldVdV()) / (2 * step), 2e-8);
    close(phase.dFShortRangedVdVdV(), (high.dFShortRangedVdV() - low.dFShortRangedVdV()) / (2 * step), 2e-8);
  }

  /** Ensures each optional residual term remains consistent with the selected energy. */
  @Test
  void termSwitchesPreserveEnergyGradient() {
    PhaseElectrolyteCPAMM base = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA, 1);
    for (int mask = 0; mask < 8; mask++) {
      PhaseElectrolyteCPAMM phase = base.clone();
      phase.setDebyeHuckelOn((mask & 1) != 0);
      phase.setBornOn((mask & 2) != 0);
      phase.setShortRangeOn((mask & 4) != 0);
      phase = perturb(phase, 0, 0);
      for (int i = 0; i < phase.getNumberOfComponents() + 2; i++) {
        double step = step(phase, i);
        close(first(phase, i), (perturb(phase, i, step).getF() - perturb(phase, i, -step).getF()) / (2 * step), 2e-6);
      }
    }
  }

  /** Prevents the former artificial split into nearly pure water and a concentrated salt phase. */
  @Test
  void aqueousNaClFlashRemainsSinglePhaseAndClosesPressure() {
    for (double molality : new double[] {0.001, 0.01, 0.1, 0.5, 1, 2, 3}) {
      SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(298.15, 1);
      fluid.addComponent("water", 55.508);
      fluid.addComponent("Na+", molality);
      fluid.addComponent("Cl-", molality);
      fluid.setMixingRule(10);
      new ThermodynamicOperations(fluid).TPflash();
      assertEquals(1, fluid.getNumberOfPhases(), "Aqueous NaCl at " + molality + " mol/kg");
      fluid.init(3);
      PhaseElectrolyteCPAMM phase = (PhaseElectrolyteCPAMM) fluid.getPhase(0);
      assertEquals(1, phase.calcPressure(), 1e-7);
      assertTrue(Double.isFinite(phase.getMeanIonicActivity(1, 2)));
      assertTrue(Double.isFinite(phase.getOsmoticCoefficientOfWater()));
      assertTrue(phase.getOsmoticCoefficientOfWater() > 0);
    }
  }

  /**
   * Tests the continuous zero-ion first-derivative limit. At exactly zero ions, the finite DH Hessian is an
   * inactive-boundary solver convention; at positive trace amounts the physical Hessian diverges.
   */
  @Test
  void zeroAndTraceIonsHaveFiniteDerivatives() {
    PhaseElectrolyteCPAMM zero = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE, 0);
    double previousCurvature = Double.POSITIVE_INFINITY;
    for (double amount : new double[] {0, 1e-30, 1e-24, 1e-16, 1e-12}) {
      PhaseElectrolyteCPAMM phase = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE, amount);
      assertTrue(Double.isFinite(phase.getF()));
      ComponentSrkCPAMM ion = (ComponentSrkCPAMM) phase.getComponent(2);
      double curvature = ion.dFDebyeHuckeldNdN(2, phase, phase.getNumberOfComponents(), phase.getTemperature(),
          phase.getPressure());
      if (amount == 0) {
        assertEquals(0, curvature, 0, "Inactive-boundary solver convention");
      } else {
        assertTrue(curvature < 0);
        assertTrue(Math.abs(curvature) < previousCurvature, "The physical curvature grows toward zero ionic strength");
        previousCurvature = Math.abs(curvature);
      }
      for (int i = 0; i < phase.getNumberOfComponents() + 2; i++) {
        assertTrue(Double.isFinite(first(phase, i)));
        assertTrue(Double.isFinite(second(phase, i, i)));
        close(first(zero, i), first(phase, i), 2e-6);
      }
    }
  }

  /** Checks the DH charging function against an independent numerical quadrature at small chi and the series switch. */
  @Test
  void screeningFunctionIsStableAndContinuous() {
    int intervals = 4096;
    for (double chi : new double[] {0, 1e-15, 1e-8, 1e-6, 0.099999999, 0.1, 0.100000001, 1, 10}) {
      double integral = 0;
      double derivative = 0;
      for (int i = 0; i <= intervals; i++) {
        double t = (double) i / intervals;
        double weight = i == 0 || i == intervals ? 1 : i % 2 == 0 ? 2 : 4;
        integral += weight * t * t / (1 + chi * t);
        derivative -= weight * t * t * t / ((1 + chi * t) * (1 + chi * t));
      }
      double tau = integral / intervals;
      double tauPrime = derivative / intervals;
      assertEquals(tau, PhaseElectrolyteCPAMM.calcTauDH(chi), 2e-11);
      assertEquals(tauPrime, PhaseElectrolyteCPAMM.calcTauDHprime(chi), 2e-11);
      if (chi > 0) {
        assertEquals(tau / 3, PhaseElectrolyteCPAMM.psiDH(chi) / (chi * chi * chi), 2e-11);
      }
    }
  }

  /** Keeps the previous harmless zero result when an electrolyte helper is supplied a different phase model. */
  @Test
  void electrolyteHelpersAcceptOtherPhaseModels() {
    PhaseElectrolyteCPAMM phase = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE, 1);
    ComponentSrkCPAMM ion = (ComponentSrkCPAMM) phase.getComponent(2);
    PhaseSrkCPA other = new PhaseSrkCPA();
    assertEquals(0, ion.dFDebyeHuckeldN(other, 0, 313.15, 5));
    assertEquals(0, ion.dFDebyeHuckeldNdT(other, 0, 313.15, 5));
    assertEquals(0, ion.dFDebyeHuckeldNdV(other, 0, 313.15, 5));
    assertEquals(0, ion.dFDebyeHuckeldNdN(0, other, 0, 313.15, 5));
    assertEquals(0, ion.dFBorndN(other, 0, 313.15, 5));
    assertEquals(0, ion.dFBorndNdT(other, 0, 313.15, 5));
    assertEquals(0, ion.dFBorndNdV(other, 0, 313.15, 5));
    assertEquals(0, ion.dFBorndNdN(0, other, 0, 313.15, 5));
    assertEquals(0, ion.dFShortRangedN(other, 0, 313.15, 5));
    assertEquals(0, ion.dFShortRangedNdT(other, 0, 313.15, 5));
    assertEquals(0, ion.dFShortRangedNdV(other, 0, 313.15, 5));
    assertEquals(0, ion.dFShortRangedNdN(0, other, 0, 313.15, 5));
  }

  /**
   * Older serialized ions with cpaon=0 retain the same displacement contribution in every derivative.
   *
   * @throws Exception if the legacy serialized field cannot be inspected
   */
  @Test
  void legacyIonCpaFlagPreservesAllDerivativeOrders() throws Exception {
    PhaseElectrolyteCPAMM phase = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE, 1);
    PhaseElectrolyteCPAMM legacy = phase.clone();
    Field flag = ComponentSrkCPA.class.getDeclaredField("cpaon");
    flag.setAccessible(true);
    flag.setInt(legacy.getComponent(2), 0);
    assertEquals(first(phase, 2), first(legacy, 2), 1e-12);
    for (int i = 0; i < phase.getNumberOfComponents() + 2; i++) {
      assertEquals(second(phase, 2, i), second(legacy, 2, i), 1e-12);
    }
  }

  /**
   * Independent clones may change temperature, mixing rules and Born radii concurrently.
   *
   * @throws Exception if a concurrent derivative calculation fails
   */
  @Test
  void clonedPhasesKeepIndependentParametersAndDerivatives() throws Exception {
    PhaseElectrolyteCPAMM base = mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule.MOLAR_AVERAGE, 1);
    double originalWij = base.getWij(0, 2);
    double originalDerivative = first(base, 2);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    try {
      List<Callable<Double>> jobs = new ArrayList<Callable<Double>>();
      for (int i = 0; i < 6; i++) {
        final int index = i;
        jobs.add(() -> {
          PhaseElectrolyteCPAMM changed = base.clone();
          changed.setDielectricMixingRule(PhaseElectrolyteCPAMM.DielectricMixingRule.values()[index % 5]);
          ((ComponentSrkCPAMM) changed.getComponent(2)).setBornRadius(1.8 + 0.1 * index);
          changed = perturb(changed, changed.getNumberOfComponents() + 1, 2 + index);
          double step = step(changed, 2);
          close(first(changed, 2), (perturb(changed, 2, step).getF() - perturb(changed, 2, -step).getF()) / (2 * step),
              2e-6);
          return first(changed, 2);
        });
      }
      for (Future<Double> result : executor.invokeAll(jobs)) {
        assertTrue(Double.isFinite(result.get()));
      }
    } finally {
      executor.shutdownNow();
    }
    assertEquals(originalWij, base.getWij(0, 2), 0, "Clone must not overwrite the original Wij matrix");
    assertEquals(originalDerivative, first(base, 2), 0);
  }

  /**
   * Creates an electrically neutral mixed-solvent, mixed-valence phase.
   *
   * @param rule dielectric mixing rule
   * @param scale ion-amount multiplier
   * @return initialized aqueous phase
   */
  private static PhaseElectrolyteCPAMM mixedPhase(PhaseElectrolyteCPAMM.DielectricMixingRule rule, double scale) {
    SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(313.15, 5);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("MEG", 2);
    fluid.addComponent("Na+", scale * 0.5);
    fluid.addComponent("Ca++", scale * 0.2);
    fluid.addComponent("Cl-", scale * 0.9);
    fluid.setMixingRule(10);
    fluid.init(0);
    for (int i = 0; i < 2; i++) {
      ((PhaseElectrolyteCPAMM) fluid.getPhase(i)).setDielectricMixingRule(rule);
    }
    fluid.init(3);
    return (PhaseElectrolyteCPAMM) fluid.getPhase(1);
  }

  /**
   * Perturbs one extensive independent variable and re-equilibrates association at fixed total volume.
   *
   * @param phase original phase
   * @param direction component index, component count for V, or count + 1 for T
   * @param change perturbation size
   * @return independently initialized phase
   */
  private static PhaseElectrolyteCPAMM perturb(PhaseElectrolyteCPAMM phase, int direction, double change) {
    PhaseElectrolyteCPAMM copy = phase.clone();
    int count = copy.getNumberOfComponents();
    double volume = copy.getTotalVolume();
    if (direction < count) {
      copy.addMoles(direction, change);
    } else if (direction == count) {
      volume += change;
    } else {
      copy.setTemperature(copy.getTemperature() + change);
    }
    for (int j = 0; j < count; j++) {
      copy.getComponent(j).setx(copy.getComponent(j).getNumberOfMolesInPhase() / copy.getNumberOfMolesInPhase());
      copy.getComponent(j).setz(copy.getComponent(j).getx());
    }
    copy.setTotalVolume(volume);
    copy.setMolarVolume(volume / copy.getNumberOfMolesInPhase());
    copy.setConstantPhaseVolume(true);
    copy.calcMolarVolume(false);
    copy.init(copy.getNumberOfMolesInPhase(), count, 3, phase.getType(), 1);
    return copy;
  }

  /**
   * Returns an analytic first derivative in an extensive independent direction.
   *
   * @param phase initialized phase
   * @param direction component, volume or temperature direction
   * @return first derivative
   */
  private static double first(PhaseElectrolyteCPAMM phase, int direction) {
    int count = phase.getNumberOfComponents();
    if (direction < count) {
      return ((ComponentSrkCPAMM) phase.getComponent(direction)).dFdN(phase, count, phase.getTemperature(),
          phase.getPressure());
    }
    return direction == count ? phase.dFdV() : phase.dFdT();
  }

  /**
   * Returns an analytic second derivative.
   *
   * @param phase initialized phase
   * @param first first direction
   * @param second second direction
   * @return mixed derivative
   */
  private static double second(PhaseElectrolyteCPAMM phase, int first, int second) {
    int count = phase.getNumberOfComponents();
    if (first < count) {
      ComponentSrkCPAMM component = (ComponentSrkCPAMM) phase.getComponent(first);
      if (second < count) {
        return component.dFdNdN(second, phase, count, phase.getTemperature(), phase.getPressure());
      }
      return second == count ? component.dFdNdV(phase, count, phase.getTemperature(), phase.getPressure())
          : component.dFdNdT(phase, count, phase.getTemperature(), phase.getPressure());
    }
    if (second < count) {
      return second(phase, second, first);
    }
    if (first == count && second == count) {
      return phase.dFdVdV();
    }
    return first == count + 1 && second == count + 1 ? phase.dFdTdT() : phase.dFdTdV();
  }

  /**
   * Chooses a central-difference step scaled to the extensive independent variable.
   *
   * @param phase initialized phase
   * @param direction component, volume or temperature direction
   * @return central-difference step
   */
  private static double step(PhaseElectrolyteCPAMM phase, int direction) {
    int count = phase.getNumberOfComponents();
    return 2e-5 * (direction < count ? Math.max(0.01, phase.getComponent(direction).getNumberOfMolesInPhase())
        : direction == count ? phase.getTotalVolume() : phase.getTemperature());
  }

  /**
   * Compares independently obtained values with an absolute/relative tolerance.
   *
   * @param analytic analytic value
   * @param numeric independent numerical value
   * @param tolerance scaled tolerance
   */
  private static void close(double analytic, double numeric, double tolerance) {
    assertEquals(analytic, numeric, tolerance * Math.max(1, Math.abs(analytic)));
  }
}
