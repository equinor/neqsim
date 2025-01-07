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

  /*
   * public boolean addPlusFraction(int start, int end) { plusFractionModel = new
   * PlusCharacterize(system); if (TBPCharacterise.hasPlusFraction()) {
   * TBPCharacterise.groupTBPfractions(); TBPCharacterise.generateTBPFractions(); return true; }
   * else { System.out.println("not able to generate pluss fraction"); return false; } }
   *
   * public boolean characterize2() { if (TBPCharacterise.groupTBPfractions()) {
   * TBPCharacterise.solve(); return true; } else { System.out.println("not able to generate pluss
   * fraction"); return false; } }
   */
}
