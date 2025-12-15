package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Characterise class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Characterise implements java.io.Serializable, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Characterise.class);

  SystemInterface system = null;
  TBPCharacterize TBPCharacterise = null;
  private TBPModelInterface TBPfractionModel = null;
  private PlusFractionModel plusFractionModelSelector = null;
  private PlusFractionModelInterface plusFractionModel = null;
  private LumpingModelInterface lumpingModel = null;
  protected String TBPFractionModelName = "PedersenSRK";
  protected LumpingModel lumpingModelSelector = null;
  protected TBPfractionModel TBPfractionModelSelector;

  /**
   * <p>
   * Constructor for Characterise.
   * </p>
   */
  public Characterise() {}

  /**
   * <p>
   * Constructor for Characterise.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Characterise(SystemInterface system) {
    this.system = system;

    TBPCharacterise = new neqsim.thermo.characterization.TBPCharacterize(system);

    TBPfractionModelSelector = new TBPfractionModel();
    TBPfractionModel = TBPfractionModelSelector.getModel("");

    lumpingModelSelector = new LumpingModel(system);
    lumpingModel = lumpingModelSelector.getModel("");

    plusFractionModelSelector = new PlusFractionModel(system);
    plusFractionModel = plusFractionModelSelector.getModel("");
  }

  /**
   * <p>
   * setThermoSystem.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface system) {
    this.system = system;
  }

  /** {@inheritDoc} */
  @Override
  public Characterise clone() {
    Characterise clonedSystem = null;
    try {
      clonedSystem = (Characterise) super.clone();
      // clonedSystem.chemicalReactionOperations = (ChemicalReactionOperations)
      // chemicalReactionOperations.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }

  /**
   * <p>
   * getTBPModel.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.TBPModelInterface} object
   */
  public TBPModelInterface getTBPModel() {
    return TBPfractionModel;
  }

  /**
   * <p>
   * setTBPModel.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setTBPModel(String name) {
    TBPfractionModel = TBPfractionModelSelector.getModel(name);
  }

  /**
   * <p>
   * Setter for the field <code>lumpingModel</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setLumpingModel(String name) {
    lumpingModel = lumpingModelSelector.getModel(name);
  }

  /**
   * <p>
   * Setter for the field <code>plusFractionModel</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setPlusFractionModel(String name) {
    plusFractionModel = plusFractionModelSelector.getModel(name);
  }

  /**
   * <p>
   * Getter for the field <code>plusFractionModel</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.PlusFractionModelInterface} object
   */
  public PlusFractionModelInterface getPlusFractionModel() {
    return plusFractionModel;
  }

  /**
   * Set the gamma distribution shape parameter (alpha) for Whitson Gamma Model. Only applies when
   * using "Whitson Gamma Model" as the plus fraction model.
   *
   * <p>
   * Typical values:
   * <ul>
   * <li>Gas condensates: 0.5 - 1.0</li>
   * <li>Black oils: 1.0 - 2.0</li>
   * <li>Heavy oils: 2.0 - 4.0</li>
   * </ul>
   *
   * @param alpha shape parameter value
   * @return this Characterise instance for method chaining
   */
  public Characterise setGammaShapeParameter(double alpha) {
    if (plusFractionModel instanceof PlusFractionModel.WhitsonGammaModel) {
      ((PlusFractionModel.WhitsonGammaModel) plusFractionModel).setAlpha(alpha);
    } else {
      logger.warn("setGammaShapeParameter only applies to Whitson Gamma Model. Current model: "
          + plusFractionModel.getName());
    }
    return this;
  }

  /**
   * Set the minimum molecular weight (eta) for Whitson Gamma Model. Only applies when using
   * "Whitson Gamma Model" as the plus fraction model.
   *
   * @param eta minimum molecular weight in g/mol (typically 84-90 for C7+)
   * @return this Characterise instance for method chaining
   */
  public Characterise setGammaMinMW(double eta) {
    if (plusFractionModel instanceof PlusFractionModel.WhitsonGammaModel) {
      ((PlusFractionModel.WhitsonGammaModel) plusFractionModel).setEta(eta);
    } else {
      logger.warn("setGammaMinMW only applies to Whitson Gamma Model. Current model: "
          + plusFractionModel.getName());
    }
    return this;
  }

  /**
   * Enable automatic estimation of the gamma shape parameter (alpha) based on fluid properties.
   * Only applies when using "Whitson Gamma Model" as the plus fraction model.
   *
   * @param autoEstimate true to enable auto-estimation
   * @return this Characterise instance for method chaining
   */
  public Characterise setAutoEstimateGammaAlpha(boolean autoEstimate) {
    if (plusFractionModel instanceof PlusFractionModel.WhitsonGammaModel) {
      ((PlusFractionModel.WhitsonGammaModel) plusFractionModel).setAutoEstimateAlpha(autoEstimate);
    } else {
      logger.warn("setAutoEstimateGammaAlpha only applies to Whitson Gamma Model. Current model: "
          + plusFractionModel.getName());
    }
    return this;
  }

  /**
   * Set the density model for Whitson Gamma Model characterization. Only applies when using
   * "Whitson Gamma Model" as the plus fraction model.
   *
   * @param densityModel "UOP" for Watson K-factor (default) or "Soreide" for Søreide (1989)
   *        correlation
   * @return this Characterise instance for method chaining
   */
  public Characterise setGammaDensityModel(String densityModel) {
    if (plusFractionModel instanceof PlusFractionModel.WhitsonGammaModel) {
      ((PlusFractionModel.WhitsonGammaModel) plusFractionModel).setDensityModel(densityModel);
    } else {
      logger.warn("setGammaDensityModel only applies to Whitson Gamma Model. Current model: "
          + plusFractionModel.getName());
    }
    return this;
  }

  /**
   * <p>
   * Getter for the field <code>lumpingModel</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.LumpingModelInterface} object
   */
  public LumpingModelInterface getLumpingModel() {
    return lumpingModel;
  }

  /**
   * <p>
   * characterisePlusFraction.
   * </p>
   */
  public void characterisePlusFraction() {
    system.init(0);
    if (plusFractionModel.hasPlusFraction()) {
      if (plusFractionModel.getMPlus() > plusFractionModel.getMaxPlusMolarMass()) {
        logger.error("plus fraction molar mass too heavy for " + plusFractionModel.getName());
        plusFractionModel = plusFractionModelSelector.getModel("Pedersen Heavy Oil");
        logger.info("changing to " + plusFractionModel.getName());
      }
      boolean couldCharacerize = plusFractionModel.characterizePlusFraction(TBPfractionModel);
      if (couldCharacerize) {
        lumpingModel.generateLumpedComposition(this);
      }
    }
  }

  /**
   * Characterize this fluid to match the pseudo-component structure of a reference fluid.
   *
   * <p>
   * This method redistributes this fluid's pseudo-components to match the reference fluid's
   * pseudo-component boundaries, enabling consistent compositional modeling across multiple fluid
   * samples.
   *
   * <p>
   * Example:
   * 
   * <pre>
   * SystemInterface referenceFluid = ...;  // Fluid with "master" PC structure
   * SystemInterface myFluid = ...;         // Fluid to be matched
   *
   * SystemInterface matched = myFluid.getCharacterization()
   *     .characterizeToReference(referenceFluid);
   * </pre>
   *
   * @param referenceFluid the fluid defining the target pseudo-component structure
   * @return a new fluid with pseudo-components matching the reference
   */
  public SystemInterface characterizeToReference(SystemInterface referenceFluid) {
    return PseudoComponentCombiner.characterizeToReference(system, referenceFluid);
  }

  /**
   * Characterize this fluid to match the pseudo-component structure of a reference fluid with
   * options.
   *
   * <p>
   * This method allows specifying options for BIP transfer, normalization, and validation.
   *
   * <p>
   * Example:
   * 
   * <pre>
   * CharacterizationOptions options = CharacterizationOptions.builder()
   *     .transferBinaryInteractionParameters(true).normalizeComposition(true).build();
   *
   * SystemInterface matched =
   *     myFluid.getCharacterization().characterizeToReference(referenceFluid, options);
   * </pre>
   *
   * @param referenceFluid the fluid defining the target pseudo-component structure
   * @param options characterization options
   * @return a new fluid with pseudo-components matching the reference
   */
  public SystemInterface characterizeToReference(SystemInterface referenceFluid,
      CharacterizationOptions options) {
    return PseudoComponentCombiner.characterizeToReference(system, referenceFluid, options);
  }

  /**
   * Transfer binary interaction parameters from a reference fluid to this fluid.
   *
   * <p>
   * This copies BIPs between components that exist in both fluids. For pseudo-components, it
   * matches by position.
   *
   * @param referenceFluid the fluid containing BIPs to copy
   * @return this Characterise instance for method chaining
   */
  public Characterise transferBipsFrom(SystemInterface referenceFluid) {
    PseudoComponentCombiner.transferBinaryInteractionParameters(referenceFluid, system);
    return this;
  }
}
