package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of EN 16726 - Gas infrastructure - Quality of gas - Group H.
 *
 * <p>
 * EN 16726 defines the quality requirements for high-calorific natural gas (Group H) in the
 * European gas grid. It specifies limits for Wobbe index, relative density, total sulfur, H2S,
 * mercaptan sulfur, oxygen, CO2, water dew point, hydrocarbon dew point, and hydrogen content.
 * </p>
 *
 * <p>
 * The standard defines a quality corridor within which gas must fall for cross-border transmission:
 * </p>
 * <ul>
 * <li>Wobbe Index: 46.44 to 54.72 MJ/m3 (at 25/0 degrees C combustion/metering)</li>
 * <li>Relative density: 0.555 to 0.700</li>
 * <li>Total sulfur: max 30 mg/m3</li>
 * <li>H2S + COS: max 5 mg/m3</li>
 * <li>CO2: max 2.5 mol%</li>
 * <li>O2: max 0.001 mol% (transmission), max 0.01 mol% (distribution)</li>
 * <li>H2: max 2 mol% (default, can vary by national annex)</li>
 * <li>Water dew point: max -8 degrees C at 70 bar (default)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_EN16726 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_EN16726.class);

  /** ISO 6976 calculator for Wobbe index. */
  private Standard_ISO6976 iso6976;

  /** Calculated Wobbe index in MJ/m3. */
  private double wobbeIndex = 0.0;

  /** Calculated relative density. */
  private double relativeDensity = 0.0;

  /** Calculated GCV in MJ/m3. */
  private double grossCalorificValue = 0.0;

  /** CO2 content in mol%. */
  private double co2Content = 0.0;

  /** H2S content in mg/m3. */
  private double h2sContent = 0.0;

  /** Total sulfur content in mg/m3. */
  private double totalSulfur = 0.0;

  /** O2 content in mol%. */
  private double o2Content = 0.0;

  /** H2 content in mol%. */
  private double h2Content = 0.0;

  /** Water content in mg/m3. */
  private double waterContent = 0.0;

  /** Specification limits - Wobbe index min in MJ/m3. */
  private double wobbeIndexMin = 46.44;

  /** Specification limits - Wobbe index max in MJ/m3. */
  private double wobbeIndexMax = 54.72;

  /** Specification limits - relative density min. */
  private double relativeDensityMin = 0.555;

  /** Specification limits - relative density max. */
  private double relativeDensityMax = 0.700;

  /** Specification limits - max total sulfur in mg/m3. */
  private double totalSulfurMax = 30.0;

  /** Specification limits - max H2S + COS in mg/m3. */
  private double h2sMax = 5.0;

  /** Specification limits - max CO2 in mol%. */
  private double co2Max = 2.5;

  /** Specification limits - max O2 in mol% (transmission). */
  private double o2MaxTransmission = 0.001;

  /** Specification limits - max O2 in mol% (distribution). */
  private double o2MaxDistribution = 0.01;

  /** Specification limits - max H2 in mol%. */
  private double h2Max = 2.0;

  /** Network type: "transmission" or "distribution". */
  private String networkType = "transmission";

  /**
   * Constructor for Standard_EN16726.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_EN16726(SystemInterface thermoSystem) {
    super("Standard_EN16726", "Gas infrastructure - Quality of gas - Group H", thermoSystem);
    // EN 16726 uses 25C combustion reference, 0C metering reference for Wobbe
    this.iso6976 = new Standard_ISO6976(thermoSystem, 0, 25, "volume");
  }

  /**
   * Sets the network type for O2 limit selection.
   *
   * @param type "transmission" or "distribution"
   */
  public void setNetworkType(String type) {
    this.networkType = type;
  }

  /**
   * Gets the network type.
   *
   * @return the network type
   */
  public String getNetworkType() {
    return networkType;
  }

  /**
   * Sets a custom H2 limit (may vary by national annex).
   *
   * @param maxH2MolPercent maximum hydrogen content in mol%
   */
  public void setH2Limit(double maxH2MolPercent) {
    this.h2Max = maxH2MolPercent;
  }

  /**
   * Helper to get mole fraction safely.
   *
   * @param componentName the component name
   * @return mole fraction or 0 if component not present
   */
  private double getMoleFraction(String componentName) {
    if (thermoSystem.getPhase(0).hasComponent(componentName)) {
      return thermoSystem.getPhase(0).getComponent(componentName).getz();
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      // Calculate Wobbe index and GCV via ISO 6976
      iso6976.setReferenceState("real");
      iso6976.setReferenceType("volume");
      iso6976.calculate();

      wobbeIndex = iso6976.getValue("SuperiorWobbeIndex") / 1000.0; // kJ/m3 to MJ/m3
      grossCalorificValue = iso6976.getValue("SuperiorCalorificValue") / 1000.0;
      relativeDensity = iso6976.getValue("RelativeDensity");

      // Component contents
      co2Content = getMoleFraction("CO2") * 100.0; // mol%
      o2Content = getMoleFraction("oxygen") * 100.0;
      h2Content = getMoleFraction("hydrogen") * 100.0;

      // H2S in mg/m3 (approximate: ppmv * MW_H2S / molar_volume_at_STP)
      double h2sMolFrac = getMoleFraction("H2S");
      double molarVolumeStd = 22.414; // L/mol at 0C, 1 atm
      h2sContent = h2sMolFrac * 34.08 / molarVolumeStd * 1e6; // mg/m3 at 0C

      // Total sulfur (H2S + mercaptans + COS + SO2)
      totalSulfur = h2sContent;
      double cosMolFrac = getMoleFraction("COS");
      if (cosMolFrac > 0.0) {
        totalSulfur += cosMolFrac * 60.07 / molarVolumeStd * 1e6;
      }

      // Water content in mg/m3
      double waterMolFrac = getMoleFraction("water");
      waterContent = waterMolFrac * 18.015 / molarVolumeStd * 1e6;

    } catch (Exception ex) {
      logger.error("EN 16726 calculation failed", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("WobbeIndex".equals(returnParameter) || "WI".equals(returnParameter)) {
      return wobbeIndex;
    }
    if ("GCV".equals(returnParameter) || "grossCalorificValue".equals(returnParameter)) {
      return grossCalorificValue;
    }
    if ("relativeDensity".equals(returnParameter)) {
      return relativeDensity;
    }
    if ("CO2".equals(returnParameter)) {
      return co2Content;
    }
    if ("H2S".equals(returnParameter)) {
      return h2sContent;
    }
    if ("totalSulfur".equals(returnParameter)) {
      return totalSulfur;
    }
    if ("O2".equals(returnParameter)) {
      return o2Content;
    }
    if ("H2".equals(returnParameter)) {
      return h2Content;
    }
    if ("water".equals(returnParameter)) {
      return waterContent;
    }
    return wobbeIndex;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("WobbeIndex".equals(returnParameter) || "WI".equals(returnParameter)
        || "GCV".equals(returnParameter) || "grossCalorificValue".equals(returnParameter)) {
      return "MJ/m3";
    }
    if ("relativeDensity".equals(returnParameter)) {
      return "-";
    }
    if ("CO2".equals(returnParameter) || "O2".equals(returnParameter)
        || "H2".equals(returnParameter)) {
      return "mol%";
    }
    if ("H2S".equals(returnParameter) || "totalSulfur".equals(returnParameter)
        || "water".equals(returnParameter)) {
      return "mg/m3";
    }
    return "MJ/m3";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    double o2Max = "transmission".equals(networkType) ? o2MaxTransmission : o2MaxDistribution;

    boolean wobbeOk = wobbeIndex >= wobbeIndexMin && wobbeIndex <= wobbeIndexMax;
    boolean rdOk = relativeDensity >= relativeDensityMin && relativeDensity <= relativeDensityMax;
    boolean co2Ok = co2Content <= co2Max;
    boolean h2sOk = h2sContent <= h2sMax;
    boolean sulfurOk = totalSulfur <= totalSulfurMax;
    boolean o2Ok = o2Content <= o2Max;
    boolean h2Ok = h2Content <= h2Max;

    return wobbeOk && rdOk && co2Ok && h2sOk && sulfurOk && o2Ok && h2Ok;
  }

  /**
   * Gets the Wobbe index minimum limit.
   *
   * @return min Wobbe in MJ/m3
   */
  public double getWobbeIndexMin() {
    return wobbeIndexMin;
  }

  /**
   * Gets the Wobbe index maximum limit.
   *
   * @return max Wobbe in MJ/m3
   */
  public double getWobbeIndexMax() {
    return wobbeIndexMax;
  }
}
