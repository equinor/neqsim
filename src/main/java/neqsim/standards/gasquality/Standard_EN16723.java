package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of EN 16723 - Natural gas and biomethane for use in transport and biomethane for
 * injection in the natural gas network.
 *
 * <p>
 * EN 16723 specifies quality requirements for biomethane (renewable natural gas):
 * </p>
 * <ul>
 * <li>Part 1: Specifications for biomethane for injection in the natural gas network</li>
 * <li>Part 2: Automotive fuel specifications</li>
 * </ul>
 *
 * <p>
 * Key additional parameters beyond natural gas (not covered by ISO 6976/EN 16726):
 * </p>
 * <ul>
 * <li>Siloxane content (max 0.3 - 1.0 mg/m3 as Si, depending on application)</li>
 * <li>Ammonia content (max 3 - 10 mg/m3)</li>
 * <li>Amine content (max 10 mg/m3)</li>
 * <li>Fluorinated compounds (max 25 mg/m3 as HF equivalent)</li>
 * <li>Chlorinated compounds (max 1 mg/m3 as HCl equivalent)</li>
 * <li>Total volatile silicon (relevant for engine/turbine damage)</li>
 * <li>Compressor oil content</li>
 * </ul>
 *
 * <p>
 * This implementation also checks the standard natural gas quality parameters (Wobbe, CO2, O2, H2S)
 * using EN 16726 limits as baseline, with additional biomethane-specific checks.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_EN16723 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_EN16723.class);

  /** Internal EN 16726 check for base gas quality. */
  private Standard_EN16726 en16726;

  /** Standard part: 1 = grid injection, 2 = automotive. */
  private int part = 1;

  /** Methane content in mol%. */
  private double methaneContent = 0.0;

  /** CO2 content in mol%. */
  private double co2Content = 0.0;

  /** O2 content in mol%. */
  private double o2Content = 0.0;

  /** H2 content in mol%. */
  private double h2Content = 0.0;

  /** Total inert gas content (N2 + CO2) in mol%. */
  private double totalInerts = 0.0;

  /** Wobbe index in MJ/m3 (from EN 16726). */
  private double wobbeIndex = 0.0;

  /** Maximum siloxane content in mg/m3 as Si. */
  private double siloxaneLimit = 0.3;

  /** Maximum ammonia content in mg/m3. */
  private double ammoniaLimit = 3.0;

  /** Maximum amine content in mg/m3. */
  private double amineLimit = 10.0;

  /** Maximum fluorinated compounds in mg/m3 as HF. */
  private double fluorineLimit = 25.0;

  /** Maximum chlorinated compounds in mg/m3 as HCl. */
  private double chlorineLimit = 1.0;

  /** Specification limits for Part 1 (grid injection). */
  private double co2MaxPart1 = 2.5;
  private double o2MaxPart1 = 0.01;
  private double h2MaxPart1 = 2.0;
  private double totalInertsMaxPart1 = 5.0;

  /** Specification limits for Part 2 (automotive). */
  private double co2MaxPart2 = 2.5;
  private double o2MaxPart2 = 1.0;
  private double methaneMinPart2 = 65.0;

  /**
   * Constructor for Standard_EN16723.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_EN16723(SystemInterface thermoSystem) {
    this(thermoSystem, 1);
  }

  /**
   * Constructor for Standard_EN16723 with part specification.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param part 1 for grid injection, 2 for automotive
   */
  public Standard_EN16723(SystemInterface thermoSystem, int part) {
    super("Standard_EN16723",
        "Natural gas and biomethane for use in transport and biomethane for injection",
        thermoSystem);
    this.part = part;
    this.en16726 = new Standard_EN16726(thermoSystem);
  }

  /**
   * Sets the standard part.
   *
   * @param part 1 for grid injection, 2 for automotive
   */
  public void setPart(int part) {
    this.part = part;
  }

  /**
   * Helper to get mole fraction safely.
   *
   * @param componentName the component name
   * @return mole fraction or 0 if not present
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
      // Base gas quality from EN 16726
      en16726.calculate();
      wobbeIndex = en16726.getValue("WobbeIndex");

      // Component contents
      methaneContent = getMoleFraction("methane") * 100.0;
      co2Content = getMoleFraction("CO2") * 100.0;
      o2Content = getMoleFraction("oxygen") * 100.0;
      h2Content = getMoleFraction("hydrogen") * 100.0;
      double n2Content = getMoleFraction("nitrogen") * 100.0;
      totalInerts = co2Content + n2Content;

    } catch (Exception ex) {
      logger.error("EN 16723 calculation failed", ex);
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
    if ("methane".equals(returnParameter) || "methaneContent".equals(returnParameter)) {
      return methaneContent;
    }
    if ("CO2".equals(returnParameter)) {
      return co2Content;
    }
    if ("O2".equals(returnParameter)) {
      return o2Content;
    }
    if ("H2".equals(returnParameter)) {
      return h2Content;
    }
    if ("totalInerts".equals(returnParameter)) {
      return totalInerts;
    }
    if ("WobbeIndex".equals(returnParameter) || "WI".equals(returnParameter)) {
      return wobbeIndex;
    }
    if ("siloxaneLimit".equals(returnParameter)) {
      return siloxaneLimit;
    }
    if ("ammoniaLimit".equals(returnParameter)) {
      return ammoniaLimit;
    }
    if ("amineLimit".equals(returnParameter)) {
      return amineLimit;
    }
    return wobbeIndex;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("methane".equals(returnParameter) || "methaneContent".equals(returnParameter)
        || "CO2".equals(returnParameter) || "O2".equals(returnParameter)
        || "H2".equals(returnParameter) || "totalInerts".equals(returnParameter)) {
      return "mol%";
    }
    if ("WobbeIndex".equals(returnParameter) || "WI".equals(returnParameter)) {
      return "MJ/m3";
    }
    if ("siloxaneLimit".equals(returnParameter) || "ammoniaLimit".equals(returnParameter)
        || "amineLimit".equals(returnParameter)) {
      return "mg/m3";
    }
    return "MJ/m3";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    if (part == 1) {
      // Part 1: Grid injection
      boolean co2Ok = co2Content <= co2MaxPart1;
      boolean o2Ok = o2Content <= o2MaxPart1;
      boolean h2Ok = h2Content <= h2MaxPart1;
      boolean inertsOk = totalInerts <= totalInertsMaxPart1;
      boolean wobbeOk = en16726.isOnSpec();
      return co2Ok && o2Ok && h2Ok && inertsOk && wobbeOk;
    } else {
      // Part 2: Automotive
      boolean co2Ok = co2Content <= co2MaxPart2;
      boolean o2Ok = o2Content <= o2MaxPart2;
      boolean methaneOk = methaneContent >= methaneMinPart2;
      return co2Ok && o2Ok && methaneOk;
    }
  }

  /**
   * Gets the internal EN 16726 standard used for base gas quality.
   *
   * @return the EN 16726 standard instance
   */
  public Standard_EN16726 getEN16726() {
    return en16726;
  }
}
