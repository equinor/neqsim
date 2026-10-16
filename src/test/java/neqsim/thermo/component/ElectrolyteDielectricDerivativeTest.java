package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Independent finite-difference checks of the electrolyte dielectric and Born chain rules at fixed volume.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ElectrolyteDielectricDerivativeTest {
  /**
   * A charge-neutral salt perturbation checks the mixed composition derivatives without leaving electroneutral states.
   *
   * @param association whether the model includes CPA association
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dielectricCompositionHessianMatchesChargeNeutralDifference(boolean association) {
    PhaseModifiedFurstElectrolyteEos reference = aqueousMixture(association);
    double volume = physicalVolume(reference);
    double increment = 1e-6;
    PhaseModifiedFurstElectrolyteEos plus = SerializationUtils.clone(reference);
    PhaseModifiedFurstElectrolyteEos minus = SerializationUtils.clone(reference);
    for (int component = 2; component < 4; component++) {
      plus.addMoles(component, increment);
      minus.addMoles(component, -increment);
    }
    setVolumeAndRefresh(plus, volume);
    setVolumeAndRefresh(minus, volume);
    for (int component = 0; component < reference.getNumberOfComponents(); component++) {
      ComponentModifiedFurstElectrolyteEos species = (ComponentModifiedFurstElectrolyteEos) reference
          .getComponent(component);
      double analytic = species.calcdiElectricdndn(2, reference, reference.getNumberOfComponents(),
          reference.getTemperature(), reference.getPressure())
          + species.calcdiElectricdndn(3, reference, reference.getNumberOfComponents(), reference.getTemperature(),
              reference.getPressure());
      double numerical = (dielectricAmountDerivative(plus, component) - dielectricAmountDerivative(minus, component))
          / (2.0 * increment);
      assertEquals(numerical, analytic, 1e-6 * Math.max(1.0, Math.abs(numerical)),
          "Dielectric composition Hessian for " + species.getComponentName());
    }
  }

  /**
   * Volume differentiation must retain the same solvent dielectric contrast as the undifferentiated model.
   *
   * @param association whether the model includes CPA association
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dielectricAmountVolumeDerivativeMatchesDifference(boolean association) {
    PhaseModifiedFurstElectrolyteEos reference = aqueousMixture(association);
    double volume = physicalVolume(reference);
    double increment = volume * 1e-5;
    PhaseModifiedFurstElectrolyteEos plus = SerializationUtils.clone(reference);
    PhaseModifiedFurstElectrolyteEos minus = SerializationUtils.clone(reference);
    setVolumeAndRefresh(plus, volume + increment);
    setVolumeAndRefresh(minus, volume - increment);
    for (int component = 0; component < reference.getNumberOfComponents(); component++) {
      ComponentModifiedFurstElectrolyteEos species = (ComponentModifiedFurstElectrolyteEos) reference
          .getComponent(component);
      double analytic = species.calcdiElectricdndV(reference, reference.getNumberOfComponents(),
          reference.getTemperature(), reference.getPressure());
      double numerical = (dielectricAmountDerivative(plus, component) - dielectricAmountDerivative(minus, component))
          / (2.0 * increment);
      assertEquals(numerical, analytic, 1e-6 * Math.max(1.0, Math.abs(numerical)),
          "Dielectric amount-volume derivative for " + species.getComponentName());
    }
  }

  /**
   * The total Born temperature curvature includes both mixed terms and the solvent dielectric curvature.
   *
   * @param association whether the model includes CPA association
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void bornTemperatureCurvatureMatchesDifference(boolean association) {
    PhaseModifiedFurstElectrolyteEos reference = aqueousMixture(association);
    double increment = 0.01;
    PhaseModifiedFurstElectrolyteEos plus = SerializationUtils.clone(reference);
    PhaseModifiedFurstElectrolyteEos minus = SerializationUtils.clone(reference);
    plus.setTemperature(reference.getTemperature() + increment);
    minus.setTemperature(reference.getTemperature() - increment);
    setVolumeAndRefresh(plus, physicalVolume(reference));
    setVolumeAndRefresh(minus, physicalVolume(reference));
    double firstDifference = (plus.FBorn() - minus.FBorn()) / (2.0 * increment);
    double secondDifference = (plus.dFBorndT() - minus.dFBorndT()) / (2.0 * increment);
    assertEquals(firstDifference, reference.dFBorndT(), Math.abs(firstDifference) * 1e-7);
    assertEquals(secondDifference, reference.dFBorndTdT(), Math.abs(secondDifference) * 1e-7);
  }

  /**
   * Constructs an aqueous liquid without a phase-split operation or any species-specific solver route.
   *
   * @param association whether to use electrolyte CPA
   * @return initialized aqueous phase
   */
  private PhaseModifiedFurstElectrolyteEos aqueousMixture(boolean association) {
    SystemInterface fluid = association ? new SystemElectrolyteCPAstatoil(298.15, 50.0)
        : new SystemFurstElectrolyteEos(298.15, 50.0);
    fluid.addComponent("methane", 0.002);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.018);
    fluid.addComponent("Cl-", 0.018);
    fluid.setMixingRule(association ? 10 : 2);
    fluid.setForceSinglePhase(PhaseType.AQUEOUS);
    fluid.init(3);
    return (PhaseModifiedFurstElectrolyteEos) fluid.getPhase(0);
  }

  /**
   * Returns the physical total volume rather than the legacy pressure-scaled molar volume.
   *
   * @param phase phase to inspect
   * @return volume in cubic metres
   */
  private double physicalVolume(PhaseModifiedFurstElectrolyteEos phase) {
    return phase.getMolarVolume() * phase.getNumberOfMolesInPhase() * 1e-5;
  }

  /**
   * Refreshes dielectric and component derivatives while preserving the independently specified total volume.
   *
   * @param phase perturbed phase
   * @param volume physical total volume in cubic metres
   */
  private void setVolumeAndRefresh(PhaseModifiedFurstElectrolyteEos phase, double volume) {
    phase.setMolarVolume(volume / (phase.getNumberOfMolesInPhase() * 1e-5));
    phase.volInit();
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      phase.getComponent(component).Finit(phase, phase.getTemperature(), phase.getPressure(),
          phase.getNumberOfMolesInPhase(), 1.0, phase.getNumberOfComponents(), 3);
    }
  }

  /**
   * Evaluates the model's first dielectric amount derivative at the current fixed-volume state.
   *
   * @param phase perturbed phase
   * @param component species being differentiated
   * @return dielectric derivative per mole
   */
  private double dielectricAmountDerivative(PhaseModifiedFurstElectrolyteEos phase, int component) {
    return ((ComponentModifiedFurstElectrolyteEos) phase.getComponent(component)).calcdiElectricdn(phase,
        phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
  }
}
