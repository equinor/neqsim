package neqsim.thermo.system;

import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * Thermodynamic system using the Pitzer GE model.
 */
public class SystemPitzer extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Default constructor. */
  public SystemPitzer() {
    this(298.15, 1.0, false);
  }

  /**
   * @param T temperature in K
   * @param P pressure in bara
   */
  public SystemPitzer(double T, double P) {
    this(T, P, false);
  }

  /**
   * @param T temperature in K
   * @param P pressure in bara
   * @param checkForSolids include solid phase
   */
  public SystemPitzer(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Pitzer-GE-model";
    attractiveTermNumber = 0;

    phaseArray[0] = new PhaseSrkEos();
    phaseArray[0].setTemperature(T);
    phaseArray[0].setPressure(P);
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePitzer();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].setType(neqsim.thermo.phase.PhaseType.AQUEOUS);
      setPhaseType(i, neqsim.thermo.phase.PhaseType.AQUEOUS);
    }

    if (solidPhaseCheck) {
      setNumberOfPhases(4);
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  @Override
  public void setMixingRule(String typename) {
    super.setMixingRule(neqsim.thermo.mixingrule.EosMixingRuleType
        .byName(typename.replace("-", "_")));
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i].initRefPhases(false);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemPitzer clone() {
    SystemPitzer clonedSystem = null;
    try {
      clonedSystem = (SystemPitzer) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}

