package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.util.gerg.GERG2008Type;

/**
 * This class defines a thermodynamic system using the GERG2008Eos equation of state.
 *
 * <p>
 * The system can use either the standard GERG-2008 model or the GERG-2008-H2 variant with improved
 * hydrogen parameters. The default is the standard GERG-2008 model.
 * </p>
 *
 * @author victorigi
 * @version $Id: $Id
 */

// --- DISCLAIMER BEGIN ---
// This class is not yet done
// Some of the properties releated to the helmholtz energy and its derivatives
// are not yet implemented
// --- DISCLAIMER END ---
public class SystemGERG2008Eos extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** The GERG-2008 model variant to use. Default is STANDARD. */
  private GERG2008Type gergModelType = GERG2008Type.STANDARD;

  /**
   * <p>
   * Constructor for SystemGERG2008Eos.
   * </p>
   */
  public SystemGERG2008Eos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemGERG2008Eos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemGERG2008Eos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemGERG2008Eos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemGERG2008Eos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "GERG2008-EOS";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGERG2008Eos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      setNumberOfPhases(5);
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }

    // What could set hydratecheck? Will never be true
    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
    this.useVolumeCorrection(false);
    commonInitialization();
  }

  /** {@inheritDoc} */
  @Override
  public SystemGERG2008Eos clone() {
    SystemGERG2008Eos clonedSystem = null;
    try {
      clonedSystem = (SystemGERG2008Eos) super.clone();
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

  /**
   * Get the current GERG-2008 model type.
   *
   * @return the GERG model type (STANDARD or HYDROGEN_ENHANCED)
   */
  public GERG2008Type getGergModelType() {
    return gergModelType;
  }

  /**
   * Set the GERG-2008 model type.
   *
   * <p>
   * Use {@link GERG2008Type#STANDARD} for the original GERG-2008 model, or
   * {@link GERG2008Type#HYDROGEN_ENHANCED} for the GERG-2008-H2 model with improved hydrogen
   * parameters from Beckmüller et al. (2022).
   * </p>
   *
   * @param modelType the GERG model type to use
   */
  public void setGergModelType(GERG2008Type modelType) {
    this.gergModelType = modelType;
    if (modelType == GERG2008Type.HYDROGEN_ENHANCED) {
      modelName = "GERG2008-H2-EOS";
    } else {
      modelName = "GERG2008-EOS";
    }
    // Update all phases to use the new model type
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseGERG2008Eos) {
        ((PhaseGERG2008Eos) phaseArray[i]).setGergModelType(modelType);
      }
    }
  }

  /**
   * Enable the GERG-2008-H2 model with improved hydrogen parameters.
   *
   * <p>
   * This is a convenience method equivalent to calling
   * {@code setGergModelType(GERG2008Type.HYDROGEN_ENHANCED)}.
   * </p>
   *
   * <p>
   * Reference: Beckmüller, R., Thol, M., Sampson, I., Lemmon, E.W., Span, R. (2022). "Extension of
   * the equation of state for natural gases GERG-2008 with improved hydrogen parameters". Fluid
   * Phase Equilibria, 557, 113411.
   * </p>
   */
  public void useHydrogenEnhancedModel() {
    setGergModelType(GERG2008Type.HYDROGEN_ENHANCED);
  }

  /**
   * Check if the hydrogen-enhanced GERG-2008-H2 model is being used.
   *
   * @return true if using GERG-2008-H2, false if using standard GERG-2008
   */
  public boolean isUsingHydrogenEnhancedModel() {
    return gergModelType == GERG2008Type.HYDROGEN_ENHANCED;
  }
}
