package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Salt Content in Crude Oil - ASTM D3230 (input-driven conversion).
 *
 * <p>
 * The salt content of crude oil is reported either as PTB (pounds of salt, expressed as sodium chloride equivalent, per
 * thousand barrels of crude) or as a mass concentration (mg/kg, i.e. ppmw). It is a key desalter performance and
 * corrosion-control parameter. The salt is dissolved in the entrained water (brine), so it cannot be predicted from the
 * hydrocarbon equation of state - it must be supplied from the produced-water cut and the brine salinity.
 * </p>
 *
 * <p>
 * <b>This class is input-driven.</b> Provide the water cut (volume fraction of the crude that is water) and the brine
 * salinity (mass of salt per unit volume of brine). The class then converts to PTB and mg/kg. If either input is not
 * supplied, the result is {@code NaN} and a brine assay is required.
 * </p>
 *
 * <p>
 * Conversions:
 * </p>
 *
 * <pre>
 * {@code
 * saltMassPerCrudeVolume[kg/m3] = waterCutVolFraction * brineSalinity[kg/m3]
 * PTB = saltMassPerCrudeVolume[kg/m3] * 350.51
 * mg/kg (ppmw) = saltMassPerCrudeVolume[kg/m3] / crudeDensity[kg/m3] * 1e6
 * }
 * </pre>
 *
 * <p>
 * The PTB factor 350.51 = 158.987 m3 per 1000 barrels &middot; 2.20462 lb per kg. The crude density for the ppmw basis
 * is obtained internally from {@link Standard_ASTM_D4052}.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D3230 salt = new Standard_ASTM_D3230(crudeFluid);
 * salt.setWaterCut(0.005); // 0.5 vol% water
 * salt.setBrineSalinity(35.0, "kg/m3"); // 35 g/L brine
 * salt.calculate();
 * double ptb = salt.getValue("saltContentPTB");
 * double ppmw = salt.getValue("saltContent", "mg/kg");
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D3230 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D3230.class);

  /** PTB conversion factor: 158.987 m3 per 1000 bbl times 2.20462 lb per kg. */
  private static final double PTB_FACTOR = 158.987 * 2.20462;

  /** Water cut as a volume fraction of the crude (0 to 1). */
  private double waterCutVolFraction = Double.NaN;

  /** Brine salinity in kg of salt per m3 of brine. */
  private double brineSalinityKgM3 = Double.NaN;

  /** Salt content in PTB (pounds per thousand barrels). */
  private double saltPtb = Double.NaN;

  /** Salt content in mg/kg (ppmw) of crude. */
  private double saltPpmw = Double.NaN;

  /** Optional maximum salt-content specification limit in PTB (NaN = no limit). */
  private double maxSaltSpecPtb = Double.NaN;

  /**
   * Constructor for Standard_ASTM_D3230.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the crude
   */
  public Standard_ASTM_D3230(SystemInterface thermoSystem) {
    super("Standard_ASTM_D3230", "ASTM D3230 - Salt Content in Crude Oil", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    if (Double.isNaN(waterCutVolFraction) || Double.isNaN(brineSalinityKgM3)) {
      logger.warn("Salt content requires water cut and brine salinity inputs (brine assay).");
      saltPtb = Double.NaN;
      saltPpmw = Double.NaN;
      return;
    }

    double saltMassPerCrudeVolume = waterCutVolFraction * brineSalinityKgM3;
    saltPtb = saltMassPerCrudeVolume * PTB_FACTOR;

    try {
      Standard_ASTM_D4052 d4052 = new Standard_ASTM_D4052(thermoSystem);
      d4052.calculate();
      double crudeDensity = d4052.getValue("density");
      if (!Double.isNaN(crudeDensity) && crudeDensity > 0.0) {
        saltPpmw = saltMassPerCrudeVolume / crudeDensity * 1.0e6;
      } else {
        saltPpmw = Double.NaN;
      }
    } catch (Exception ex) {
      logger.error("Crude density unavailable for ppmw basis: {}", ex.getMessage());
      saltPpmw = Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    if ("saltContent".equalsIgnoreCase(returnParameter)) {
      if ("PTB".equalsIgnoreCase(returnUnit)) {
        return saltPtb;
      } else if ("mg/kg".equalsIgnoreCase(returnUnit) || "ppmw".equalsIgnoreCase(returnUnit)
          || "ppm".equalsIgnoreCase(returnUnit)) {
        return saltPpmw;
      }
    }
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("saltContentPTB".equalsIgnoreCase(returnParameter) || "PTB".equalsIgnoreCase(returnParameter)
        || "saltContent".equalsIgnoreCase(returnParameter)) {
      return saltPtb;
    } else if ("saltContentPpmw".equalsIgnoreCase(returnParameter) || "ppmw".equalsIgnoreCase(returnParameter)
        || "mg/kg".equalsIgnoreCase(returnParameter)) {
      return saltPpmw;
    } else {
      logger.error("returnParameter not supported: {}", returnParameter);
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("saltContentPpmw".equalsIgnoreCase(returnParameter) || "ppmw".equalsIgnoreCase(returnParameter)
        || "mg/kg".equalsIgnoreCase(returnParameter)) {
      return "mg/kg";
    }
    return "PTB";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (Double.isNaN(saltPtb)) {
      return false;
    }
    if (Double.isNaN(maxSaltSpecPtb)) {
      return true;
    }
    return saltPtb <= maxSaltSpecPtb;
  }

  /**
   * Sets the water cut as a volume fraction of the crude.
   *
   * @param waterCut water volume fraction (0 to 1)
   */
  public void setWaterCut(double waterCut) {
    this.waterCutVolFraction = waterCut;
  }

  /**
   * Sets the water cut from a value and unit.
   *
   * @param waterCut water cut value
   * @param unit one of {@code "fraction"} (0 to 1) or {@code "vol%"} / {@code "%"} (0 to 100)
   */
  public void setWaterCut(double waterCut, String unit) {
    if ("vol%".equalsIgnoreCase(unit) || "%".equalsIgnoreCase(unit) || "volpercent".equalsIgnoreCase(unit)) {
      this.waterCutVolFraction = waterCut / 100.0;
    } else {
      this.waterCutVolFraction = waterCut;
    }
  }

  /**
   * Sets the brine salinity.
   *
   * @param salinity brine salinity value
   * @param unit one of {@code "kg/m3"}, {@code "g/L"} (equivalent to kg/m3), or {@code "mg/L"}
   */
  public void setBrineSalinity(double salinity, String unit) {
    if ("mg/L".equalsIgnoreCase(unit) || "mg/l".equalsIgnoreCase(unit)) {
      this.brineSalinityKgM3 = salinity / 1.0e6 * 1000.0;
    } else {
      // kg/m3 and g/L are numerically identical
      this.brineSalinityKgM3 = salinity;
    }
  }

  /**
   * Sets an optional maximum salt-content specification limit in PTB used by {@link #isOnSpec()}.
   *
   * @param maxSaltPtb maximum allowed salt content in PTB
   */
  public void setMaxSaltSpec(double maxSaltPtb) {
    this.maxSaltSpecPtb = maxSaltPtb;
  }

  /**
   * Clears any previously configured maximum salt-content specification limit.
   */
  public void clearMaxSaltSpec() {
    this.maxSaltSpecPtb = Double.NaN;
  }
}
