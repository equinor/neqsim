package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.HybridEosGeFlashModel;
import neqsim.thermo.system.SystemInterface;

/**
 * Dedicated multiphase strategy for EOS-gas, EOS-oil and GE-aqueous phase topologies.
 *
 * <p>
 * The strategy reuses the general multiphase fugacity and phase-fraction equations, but starts from model-owned fixed
 * creation-order roles and disables phase-discovery logic that can replace those roles with clones of the wrong phase
 * implementation. Phase disappearance only changes the active {@code phaseIndex} mapping; a later flash restores the
 * original gas, oil and aqueous objects through {@link HybridEosGeFlashModel#prepareHybridEosGeFlash()}.
 * </p>
 *
 * @author NeqSim
 */
public class TPHybridEosGeFlash extends TPmultiflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Model that owns phase roles and final acceptance. */
  private final HybridEosGeFlashModel hybridModel;

  /** Maximum fixed-topology outer iterations. */
  private static final int MAXIMUM_HYBRID_ITERATIONS = 50;

  /** Fugacity-objective residual used to terminate the fixed-topology solve. */
  private static final double HYBRID_SOLVER_TOLERANCE = 1.0e-10;

  /**
   * Create a hybrid multiphase flash.
   *
   * @param system thermodynamic system to solve
   * @param hybridModel model owning the creation-order role contract
   */
  public TPHybridEosGeFlash(SystemInterface system, HybridEosGeFlashModel hybridModel) {
    super(system, false);
    this.hybridModel = hybridModel;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck()) {
      throw new UnsupportedOperationException(
          "Hybrid EOS-GE TPflash currently supports fluid phases only; disable solid and wax checks.");
    }

    hybridModel.prepareHybridEosGeFlash();
    system.init(1);
    double residual = Double.POSITIVE_INFINITY;
    for (int iteration = 0; iteration < MAXIMUM_HYBRID_ITERATIONS; iteration++) {
      setDoubleArrays();
      checkOneRemove = false;
      removePhase = false;
      residual = solveBeta();
      hybridModel.restoreHybridEosGeActivePhaseTypes();
      system.init(1);
      if (Double.isFinite(residual) && residual <= HYBRID_SOLVER_TOLERANCE && iteration >= 4) {
        break;
      }
    }

    if (!hybridModel.finishHybridEosGeFlash(phaseFractionMinimumLimit)) {
      throw new IllegalStateException("Hybrid EOS-GE TPflash did not produce an acceptable state: "
          + hybridModel.getHybridEosGeFlashDiagnostics(phaseFractionMinimumLimit) + ", solverResidual=" + residual);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcE() {
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      Erow[componentIndex] = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        Erow[componentIndex] += system.getPhase(phaseIndex).getBeta()
            * inverseFugacityCoefficient(phaseIndex, componentIndex);
      }
      if (!Double.isFinite(Erow[componentIndex]) || Erow[componentIndex] < 1.0e-100) {
        Erow[componentIndex] = 1.0e-100;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcQ() {
    calcE();
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      multTerm[componentIndex] = system.getPhase(0).getComponent(componentIndex).getz() / Erow[componentIndex];
      multTerm2[componentIndex] = system.getPhase(0).getComponent(componentIndex).getz()
          / (Erow[componentIndex] * Erow[componentIndex]);
    }

    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      dQdbeta[phaseIndex][0] = 1.0;
      for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
        dQdbeta[phaseIndex][0] -= multTerm[componentIndex] * inverseFugacityCoefficient(phaseIndex, componentIndex);
      }
    }

    for (int rowPhase = 0; rowPhase < system.getNumberOfPhases(); rowPhase++) {
      for (int columnPhase = 0; columnPhase < system.getNumberOfPhases(); columnPhase++) {
        Qmatrix[rowPhase][columnPhase] = 0.0;
        for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
          Qmatrix[rowPhase][columnPhase] += multTerm2[componentIndex]
              * inverseFugacityCoefficient(rowPhase, componentIndex)
              * inverseFugacityCoefficient(columnPhase, componentIndex);
        }
        if (rowPhase == columnPhase) {
          Qmatrix[rowPhase][columnPhase] += 1.0e-10;
        }
      }
    }
    return Q;
  }

  /** {@inheritDoc} */
  @Override
  public void setXY() {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      boolean aqueous = hybridModel.isHybridEosGeAqueousPhase(phaseIndex);
      double phaseFraction = Math.max(system.getPhase(phaseIndex).getBeta(), phaseFractionMinimumLimit);
      for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
        ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
        double feedFraction = referenceComponent.getz();
        double newMoleFraction;
        if (referenceComponent.getIonicCharge() != 0 || referenceComponent.isIsIon()) {
          newMoleFraction = aqueous ? feedFraction / phaseFraction : 1.0e-50;
        } else {
          newMoleFraction = feedFraction / Erow[componentIndex]
              * inverseFugacityCoefficient(phaseIndex, componentIndex);
        }
        if (!Double.isFinite(newMoleFraction) || newMoleFraction <= 0.0) {
          newMoleFraction = 1.0e-50;
        }
        system.getPhase(phaseIndex).getComponent(componentIndex).setx(newMoleFraction);
      }
      system.getPhase(phaseIndex).normalize();
    }
  }

  /**
   * Return an allowed phase's inverse fugacity coefficient. Ions are excluded from EOS gas and oil phases exactly
   * instead of relying on a large finite penalty.
   *
   * @param phaseIndex active phase index
   * @param componentIndex component index
   * @return inverse fugacity coefficient, or zero when the component is excluded from the phase
   */
  private double inverseFugacityCoefficient(int phaseIndex, int componentIndex) {
    ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
    if ((referenceComponent.getIonicCharge() != 0 || referenceComponent.isIsIon())
        && !hybridModel.isHybridEosGeAqueousPhase(phaseIndex)) {
      return 0.0;
    }
    double fugacityCoefficient = system.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient();
    if (!(fugacityCoefficient > 0.0) || !Double.isFinite(fugacityCoefficient)) {
      return 0.0;
    }
    return 1.0 / fugacityCoefficient;
  }

}
