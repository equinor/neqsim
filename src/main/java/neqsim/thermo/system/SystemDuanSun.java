package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseDuanSun;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * This class defines a thermodynamic system using the Duan Sun method used for CO2.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemDuanSun extends SystemEos {
  private static final long serialVersionUID = 1000;

  protected String[] CapeOpenProperties11 =
      {"molecularWeight", "fugacityCoefficient", "logFugacityCoefficient"};

  /**
   * <p>
   * Constructor for SystemDuanSun.
   * </p>
   */
  public SystemDuanSun() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemDuanSun.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemDuanSun(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemDuanSun.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemDuanSun(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    attractiveTermNumber = 0;
    modelName = "Duan-Sun-model";

    phaseArray[0] = new PhaseSrkEos();
    phaseArray[0].setTemperature(T);
    phaseArray[0].setPressure(P);
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseDuanSun(); // new PhaseGENRTLmodifiedWS();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      setNumberOfPhases(4);
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemDuanSun clone() {
    SystemDuanSun clonedSystem = null;
    try {
      clonedSystem = (SystemDuanSun) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface fluid1 = new SystemSrkCPA(298.15, 10.0);

    fluid1.addComponent("CO2", 1.0);
    fluid1.addComponent("nitrogen", 1.0);
    fluid1.addComponent("water", 1.0);
    fluid1.addComponent("NaCl", 1.0);
    fluid1.setMixingRule(2);

    try {
      ThermodynamicOperations testOps = new ThermodynamicOperations(fluid1);
      testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    fluid1.display();
  }
}
