package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.component.ComponentModifiedFurstElectrolyteEos;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Validates stationary long-range second derivatives on independently evaluated, charge-compatible states.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteLongRangeDerivativeTest {
  /**
   * Checks every component row along neutral-component and electrically neutral salt-pair perturbations.
   *
   * @param cpa whether to include CPA association
   * @param solvent whether MEG is present in the aqueous solvent
   */
  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void compositionHessianMatchesFixedVolumeDifferences(boolean cpa, boolean solvent) {
    SystemInterface fluid = fluid(cpa, solvent);
    byte[] snapshot = SerializationUtils.serialize(fluid);
    PhaseModifiedFurstElectrolyteEos phase = phase(fluid);
    int count = fluid.getNumberOfComponents();
    for (int column = 0; column < count - 1; column++) {
      int pair = column == count - 2 ? count - 1 : -1;
      double step = 1e-7 * phase.getNumberOfMolesInPhase();
      SystemInterface plus = fixedVolumeTrial(snapshot, column, pair, step);
      SystemInterface minus = fixedVolumeTrial(snapshot, column, pair, -step);
      SystemInterface plusFine = fixedVolumeTrial(snapshot, column, pair, step / 2.0);
      SystemInterface minusFine = fixedVolumeTrial(snapshot, column, pair, -step / 2.0);
      for (int row = 0; row < count; row++) {
        ComponentModifiedFurstElectrolyteEos component = component(fluid, row);
        double analytic = component.dFLRdNdN(column, phase, count, phase.getTemperature(), phase.getPressure());
        if (pair >= 0) {
          analytic += component.dFLRdNdN(pair, phase, count, phase.getTemperature(), phase.getPressure());
        }
        double coarse = (compositionDerivative(plus, row) - compositionDerivative(minus, row)) / (2.0 * step);
        double fine = (compositionDerivative(plusFine, row) - compositionDerivative(minusFine, row)) / step;
        assertDerivative(analytic, coarse, fine, "LR composition row=" + row + ", column=" + column);
      }
    }
  }

  /**
   * Checks the complete temperature and volume blocks of the long-range Hessian at fixed component amounts.
   *
   * @param cpa whether to include CPA association
   * @param solvent whether MEG is present in the aqueous solvent
   */
  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void temperatureAndVolumeHessianMatchesDifferences(boolean cpa, boolean solvent) {
    SystemInterface fluid = fluid(cpa, solvent);
    byte[] snapshot = SerializationUtils.serialize(fluid);
    PhaseModifiedFurstElectrolyteEos phase = phase(fluid);
    for (int variable = -2; variable < 0; variable++) {
      double step = 1e-5 * (variable == -2 ? phase.getTemperature() : phase.getVolume());
      SystemInterface plus = fixedVolumeTrial(snapshot, variable, -1, step);
      SystemInterface minus = fixedVolumeTrial(snapshot, variable, -1, -step);
      SystemInterface plusFine = fixedVolumeTrial(snapshot, variable, -1, step / 2.0);
      SystemInterface minusFine = fixedVolumeTrial(snapshot, variable, -1, -step / 2.0);
      for (int row = 0; row < fluid.getNumberOfComponents(); row++) {
        ComponentModifiedFurstElectrolyteEos component = component(fluid, row);
        double analytic = variable == -2
            ? component.dFLRdNdT(phase, fluid.getNumberOfComponents(), phase.getTemperature(), phase.getPressure())
            : component.dFLRdNdV(phase, fluid.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
        double coarse = (compositionDerivative(plus, row) - compositionDerivative(minus, row)) / (2.0 * step);
        double fine = (compositionDerivative(plusFine, row) - compositionDerivative(minusFine, row)) / step;
        assertDerivative(analytic, coarse, fine, "LR component=" + row + ", variable=" + variable);
      }
      double analyticT = variable == -2 ? phase.dFLRdTdT() : phase.dFLRdTdV();
      double coarseT = (phase(plus).dFLRdT() - phase(minus).dFLRdT()) / (2.0 * step);
      double fineT = (phase(plusFine).dFLRdT() - phase(minusFine).dFLRdT()) / step;
      assertDerivative(analyticT, coarseT, fineT, "LR temperature block, variable=" + variable);
      double analyticV = variable == -2 ? phase.dFLRdTdV() : phase.dFLRdVdV();
      double coarseV = (phase(plus).dFLRdV() - phase(minus).dFLRdV()) / (2.0 * step);
      double fineV = (phase(plusFine).dFLRdV() - phase(minusFine).dFLRdV()) / step;
      assertDerivative(analyticV, coarseV, fineV, "LR volume block, variable=" + variable);
    }
  }

  /**
   * Ensures the corrected model Hessian also produces the constant-pressure fugacity Jacobian used by a flash.
   *
   * @param cpa whether to include CPA association
   * @param solvent whether MEG is present in the aqueous solvent
   */
  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void fugacityJacobianMatchesChargePreservingDifferences(boolean cpa, boolean solvent) {
    SystemInterface fluid = fluid(cpa, solvent);
    byte[] snapshot = SerializationUtils.serialize(fluid);
    int count = fluid.getNumberOfComponents();
    for (int column = 0; column < count - 1; column++) {
      int pair = column == count - 2 ? count - 1 : -1;
      // Trace neutral solutes need a phase-scale floor to resolve the solvent's logarithmic fugacity response.
      double step = Math.max(2e-7 * fluid.getTotalNumberOfMoles(),
          1e-5 * fluid.getPhase(0).getComponent(column).getNumberOfMolesInPhase());
      SystemInterface plus = pressureTrial(snapshot, column, pair, step);
      SystemInterface minus = pressureTrial(snapshot, column, pair, -step);
      SystemInterface plusFine = pressureTrial(snapshot, column, pair, step / 2.0);
      SystemInterface minusFine = pressureTrial(snapshot, column, pair, -step / 2.0);
      for (int row = 0; row < count; row++) {
        double analytic = fluid.getPhase(0).getComponent(row).getdfugdn(column);
        if (pair >= 0) {
          analytic += fluid.getPhase(0).getComponent(row).getdfugdn(pair);
        }
        double coarse = (plus.getPhase(0).getComponent(row).getLogFugacityCoefficient()
            - minus.getPhase(0).getComponent(row).getLogFugacityCoefficient()) / (2.0 * step);
        double fine = (plusFine.getPhase(0).getComponent(row).getLogFugacityCoefficient()
            - minusFine.getPhase(0).getComponent(row).getLogFugacityCoefficient()) / step;
        assertDerivative(analytic, coarse, fine, "fugacity row=" + row + ", column=" + column);
      }
    }
  }

  /** A phase retaining ion component slots with zero ionic amounts has exactly zero shielding and LR contribution. */
  @Test
  void zeroIonicInventoryHasZeroShielding() {
    SystemInterface fluid = new SystemFurstElectrolyteEos(298.15, 50.0);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.0);
    fluid.addComponent("Cl-", 0.0);
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.AQUEOUS);
    fluid.init(1);
    fluid.init(3);
    PhaseModifiedFurstElectrolyteEos phase = phase(fluid);
    assertEquals(0.0, phase.getShieldingParameter(), 0.0);
    assertEquals(0.0, phase.FLR(), 0.0);
    assertEquals(0.0, phase.dFLRdVdV(), 0.0);
    assertEquals(0.0, phase.dFLRdTdT(), 0.0);
  }

  /**
   * Creates an initialized liquid with a neutral dissolved gas, optional mixed solvent and balanced salt.
   *
   * @param cpa whether to include association
   * @param solvent whether to add MEG
   * @return single aqueous phase at 298.15 K and 50 bara
   */
  private static SystemInterface fluid(boolean cpa, boolean solvent) {
    SystemInterface fluid = cpa ? new SystemElectrolyteCPAstatoil(298.15, 50.0)
        : new SystemFurstElectrolyteEos(298.15, 50.0);
    fluid.addComponent("nitrogen", 0.002);
    fluid.addComponent("water", 1.0);
    if (solvent) {
      fluid.addComponent("MEG", 0.2);
    }
    fluid.addComponent("Na+", 0.018);
    fluid.addComponent("Cl-", 0.018);
    fluid.setMixingRule(cpa ? 10 : 2);
    fluid.init(0);
    fluid.setForceSinglePhase(PhaseType.AQUEOUS);
    fluid.init(1);
    fluid.init(3);
    return fluid;
  }

  /**
   * Evaluates electrolyte Helmholtz terms at fixed total volume with an independent trial for every perturbation.
   *
   * @param snapshot serialized initialized reference state
   * @param variable minus two for temperature, minus one for volume, otherwise component index
   * @param pair paired counter-ion index, or minus one for a neutral variable
   * @param change coordinate increment
   * @return detached phase with refreshed electrolyte and component caches
   */
  private static SystemInterface fixedVolumeTrial(byte[] snapshot, int variable, int pair, double change) {
    SystemInterface trial = SerializationUtils.deserialize(snapshot);
    PhaseModifiedFurstElectrolyteEos phase = phase(trial);
    double volume = phase.getVolume();
    if (variable >= 0) {
      changeAmounts(trial, variable, pair, change);
    } else if (variable == -2) {
      trial.setTemperature(trial.getTemperature() + change);
    } else {
      volume += change;
    }
    phase.setMolarVolume(volume / phase.getNumberOfMolesInPhase());
    phase.volInit();
    for (int component = 0; component < trial.getNumberOfComponents(); component++) {
      phase.getComponent(component).Finit(phase, phase.getTemperature(), phase.getPressure(),
          trial.getTotalNumberOfMoles(), 1.0, trial.getNumberOfComponents(), 3);
    }
    return trial;
  }

  /**
   * Perturbs component amounts and re-evaluates the same phase root at constant temperature and pressure.
   *
   * @param snapshot serialized reference state
   * @param component neutral component or first salt ion
   * @param pair paired counter-ion index, or minus one
   * @param change mole increment
   * @return detached initialized trial
   */
  private static SystemInterface pressureTrial(byte[] snapshot, int component, int pair, double change) {
    SystemInterface trial = SerializationUtils.deserialize(snapshot);
    changeAmounts(trial, component, pair, change);
    trial.init(3);
    return trial;
  }

  /**
   * Applies a neutral component or electroneutral ion-pair perturbation and updates phase amounts.
   *
   * @param trial independent single-phase fluid
   * @param component perturbed component index
   * @param pair optional counter-ion index
   * @param change mole increment
   */
  private static void changeAmounts(SystemInterface trial, int component, int pair, double change) {
    trial.addComponent(component, change, 0);
    if (pair >= 0) {
      trial.addComponent(pair, change, 0);
    }
    trial.initBeta();
    trial.init_x_y();
  }

  /**
   * Reads the long-range component derivative from initialized caches.
   *
   * @param fluid initialized trial
   * @param index component index
   * @return component Helmholtz derivative
   */
  private static double compositionDerivative(SystemInterface fluid, int index) {
    PhaseModifiedFurstElectrolyteEos phase = phase(fluid);
    return component(fluid, index).dFLRdN(phase, fluid.getNumberOfComponents(), phase.getTemperature(),
        phase.getPressure());
  }

  /**
   * Gets the common electrolyte phase implementation for both models.
   *
   * @param fluid single-phase fluid
   * @return the active phase
   */
  private static PhaseModifiedFurstElectrolyteEos phase(SystemInterface fluid) {
    return (PhaseModifiedFurstElectrolyteEos) fluid.getPhase(0);
  }

  /**
   * Gets the common electrolyte component implementation for both models.
   *
   * @param fluid single-phase fluid
   * @param index component index
   * @return the active component
   */
  private static ComponentModifiedFurstElectrolyteEos component(SystemInterface fluid, int index) {
    return (ComponentModifiedFurstElectrolyteEos) fluid.getPhase(0).getComponent(index);
  }

  /**
   * Checks finite values, finite-difference step convergence and analytic agreement at a resolvable accuracy.
   *
   * @param analytic declared derivative
   * @param coarse central difference at the initial step
   * @param fine central difference at half the step
   * @param label diagnostic description
   */
  private static void assertDerivative(double analytic, double coarse, double fine, String label) {
    assertTrue(Double.isFinite(analytic) && Double.isFinite(coarse) && Double.isFinite(fine), label);
    double tolerance = 1e-9 + 5e-7 * Math.max(Math.abs(analytic), Math.abs(fine));
    assertEquals(fine, coarse, tolerance, label + " step convergence");
    assertEquals(fine, analytic, tolerance, label + " analytic derivative");
  }
}
