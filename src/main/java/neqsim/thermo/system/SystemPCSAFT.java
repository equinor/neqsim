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
    super.addTBPfraction(componentName2, numberOfMoles, molarMass, density);
    String componentName =
        getPhase(0).getComponent(getPhase(0).getNumberOfComponents() - 1).getComponentName();
    double mwG = molarMass * 1e3; // convert kg/mol to g/mol

    // Gross & Sadowski (2001, I&EC Res, 40, 1244-1260) n-alkane correlations
    double mSaft = 0.0257 * mwG + 0.8444;
    double epskSaftm = 7.0106 * mwG + 117.10; // m * (eps/k) [K]
    double msigm3 = 1.7296 * mwG + 15.050; // m * sigma^3 [Angstrom^3]

    // Density-based correction for sigma when specific gravity is available.
    // Higher SG (e.g. aromatics) implies smaller molecular diameter at same MW.
    if (density > 0.5 && density < 1.5) {
      // Approximate n-alkane specific gravity from Riazi-Daubert type fit (C5-C20)
      double sgAlkane = 1.07 - 3.5649 / Math.pow(mwG, 0.25);
      if (sgAlkane > 0.5 && sgAlkane < 1.0) {
        msigm3 = msigm3 * (sgAlkane / density);
      }
    }

    for (int i = 0; i < numberOfPhases; i++) {
      getPhase(phaseIndex[i]).getComponent(componentName).setmSAFTi(mSaft);
      getPhase(phaseIndex[i]).getComponent(componentName).setEpsikSAFT(epskSaftm / mSaft);
      getPhase(phaseIndex[i]).getComponent(componentName)
          .setSigmaSAFTi(Math.pow(msigm3 / mSaft, 1.0 / 3.0) / 1.0e10);
      logger.info("PC-SAFT TBP params: m=" + mSaft + " eps/k=" + epskSaftm / mSaft + " sigma="
          + Math.pow(msigm3 / mSaft, 1.0 / 3.0) + " Angstrom (SG=" + density + ")");
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
    setImplementedCompositionDeriativesofFugacity(true);
    setImplementedPressureDeriativesofFugacity(true);
    setImplementedTemperatureDeriativesofFugacity(true);
  }
}
