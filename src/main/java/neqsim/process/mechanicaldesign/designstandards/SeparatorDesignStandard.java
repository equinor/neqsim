package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.data.ProcessDesignParameters;
import neqsim.process.mechanicaldesign.MechanicalDesignMarginResult;

/**
 * SeparatorDesignStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SeparatorDesignStandard.class);

  /**
   * getFg.
   *
   * @return the Fg
   */
  public double getFg() {
    return Fg;
  }

  /**
   * setFg.
   *
   * @param Fg the Fg to set
   */
  public void setFg(double Fg) {
    this.Fg = Fg;
    fgExplicitlySet = true;
  }

  double gasLoadFactor = 0.11;
  private double Fg = 0.8;
  /** Whether the project explicitly selected the gas area fraction. */
  private boolean fgExplicitlySet;
  /** Whether the project explicitly selected the flow margin. */
  private boolean volumeFactorExplicitlySet;
  private double volumetricDesignFactor = 1.0;
  private final MechanicalDesignMarginResult safetyMargins;

  /**
   * Constructor for SeparatorDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public SeparatorDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    safetyMargins = computeSafetyMargins();
    java.util.Map<String, ProcessDesignParameters.Value> values = loadProcessRequirements("Separator", name);
    gasLoadFactor = ProcessDesignParameters.require(values, "GasLoadFactor").midpoint();
    Fg = ProcessDesignParameters.require(values, "Fg").midpoint();
    volumetricDesignFactor = ProcessDesignParameters.require(values, "VolumetricDesignFactor").midpoint();
  }

  /**
   * Getter for the field <code>gasLoadFactor</code>.
   *
   * @return a double
   */
  public double getGasLoadFactor() {
    if (standardName.equals("StatoilTR")) {
      return gasLoadFactor;
    } else {
      return gasLoadFactor;
    }
  }

  /**
   * Getter for the field <code>volumetricDesignFactor</code>.
   *
   * @return a double
   */
  public double getVolumetricDesignFactor() {
    if (standardName.equals("StatoilTR")) {
      return volumetricDesignFactor;
    } else {
      return volumetricDesignFactor;
    }
  }

  /**
   * Setter for the field <code>volumetricDesignFactor</code>.
   *
   * @param volumetricDesignFactor the volumetricDesignFactor to set
   */
  public void setVolumetricDesignFactor(double volumetricDesignFactor) {
    this.volumetricDesignFactor = volumetricDesignFactor;
    volumeFactorExplicitlySet = true;
  }

  /**
   * Check ownership of the gas-area fraction.
   *
   * @return true after an explicit setter call
   */
  public boolean isFgExplicitlySet() {
    return fgExplicitlySet;
  }

  /**
   * Check ownership of the volumetric flow margin.
   *
   * @return true after an explicit setter call
   */
  public boolean isVolumetricDesignFactorExplicitlySet() {
    return volumeFactorExplicitlySet;
  }

  /**
   * getLiquidRetentionTime.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   * @return a double
   */
  public double getLiquidRetentionTime(String name, MechanicalDesign equipmentInn) {
    double retTime = 90.0;
    double dens = ((SeparatorInterface) equipmentInn.getProcessEquipment()).getThermoSystem().getPhase(1)
        .getPhysicalProperties().getDensity() / 1000.0;

    // select correct residensetime from database
    // to be implmented
    if (name.equals("API12J")) {
      if (dens < 0.85) {
        retTime = 60.0;
      } else if (dens > 0.93) {
        retTime = 180.0;
      }
    }
    return retTime;
  }

  public MechanicalDesignMarginResult getSafetyMargins() {
    return safetyMargins;
  }

  /**
   * Resolves the equipment company or this standard's company key.
   *
   * @return canonical company identifier
   */
  private String resolveCompanyIdentifier() {
    String identifier = equipment != null ? equipment.getCompanySpecificDesignStandards() : null;
    if (identifier == null || identifier.isEmpty()) {
      return normalizeCompanyIdentifier(standardName);
    }
    return normalizeCompanyIdentifier(identifier);
  }
}
