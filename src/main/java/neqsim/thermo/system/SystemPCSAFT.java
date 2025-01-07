package neqsim.thermo.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePCSAFTRahmat;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the PC-SAFT EoS equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPCSAFT extends SystemSrkEos {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SystemPCSAFT.class);
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPCSAFT.
   * </p>
   */
  public SystemPCSAFT() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemPCSAFT.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPCSAFT(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemPCSAFT.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPCSAFT(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "PCSAFT-EOS";
    attractiveTermNumber = 0;

    // Recreates phases created in super constructor SystemSrkEos
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePCSAFTRahmat();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    commonInitialization();

    if (solidPhaseCheck) {
      setNumberOfPhases(5);
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }

    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
    this.useVolumeCorrection(false);
  }

  /** {@inheritDoc} */
  @Override
  public void addTBPfraction(String componentName2, double numberOfMoles, double molarMass,
      double density) {
    // componentName = (componentName + "_" + getFluidName());
    super.addTBPfraction(componentName2, numberOfMoles, molarMass, density);
    // addComponent(componentName2, numberOfMoles, 290.0, 30.0, 0.11);
    String componentName =
        getPhase(0).getComponent(getPhase(0).getNumberOfComponents() - 1).getComponentName();
    for (int i = 0; i < numberOfPhases; i++) {
      // getPhase(phaseIndex[i]).getComponent(componentName).setMolarMass(molarMass);
      // getPhase(phaseIndex[i]).getComponent(componentName).setIsTBPfraction(true);

      double mSaft = 0.0249 * molarMass * 1e3 + 0.9711;
      double epskSaftm = 6.5446 * molarMass * 1e3 + 177.92;
      double msigm = 1.6947 * molarMass * 1e3 + 23.27;
      getPhase(phaseIndex[i]).getComponent(componentName).setmSAFTi(mSaft);
      getPhase(phaseIndex[i]).getComponent(componentName).setEpsikSAFT(epskSaftm / mSaft);
      getPhase(phaseIndex[i]).getComponent(componentName)
          .setSigmaSAFTi(Math.pow(msigm / mSaft, 1.0 / 3.0) / 1.0e10);
      logger.info("Saft parameters: m " + mSaft + " epsk " + epskSaftm / mSaft + " sigma "
          + Math.pow(msigm / mSaft, 1.0 / 3.0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemPCSAFT clone() {
    SystemPCSAFT clonedSystem = null;
    try {
      clonedSystem = (SystemPCSAFT) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }

  /**
   * <p>
   * commonInitialization.
   * </p>
   */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(false);
    setImplementedPressureDeriativesofFugacity(false);
    setImplementedTemperatureDeriativesofFugacity(false);
  }
}
