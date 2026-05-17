package neqsim.standards.oilquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * ASTM D4294 / ISO 8754 - Standard Test Method for Sulfur in Petroleum Products.
 *
 * <p>
 * Calculates the total sulfur content of a petroleum product by summing the sulfur contribution
 * from all sulfur-containing components in the fluid. This is essential for:
 * </p>
 * <ul>
 * <li>Crude oil sour/sweet classification</li>
 * <li>Refinery hydrogen requirement estimation</li>
 * <li>Environmental compliance (SOx emissions potential)</li>
 * <li>Sales contract specification</li>
 * <li>Corrosion assessment (H2S, mercaptans)</li>
 * </ul>
 *
 * <p>
 * Sulfur-bearing components tracked:
 * </p>
 * <ul>
 * <li>H2S (hydrogen sulfide)</li>
 * <li>Mercaptans (methyl mercaptan, ethyl mercaptan)</li>
 * <li>COS (carbonyl sulfide)</li>
 * <li>CS2 (carbon disulfide)</li>
 * <li>SO2 (sulfur dioxide)</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * Standard_ASTM_D4294 standard = new Standard_ASTM_D4294(oilFluid);
 * standard.calculate();
 * double sulfurWtPct = standard.getValue("sulfur"); // wt%
 * double sulfurPpmw = standard.getValue("sulfur", "ppmw");
 * String classification = standard.getSulfurClassification();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ASTM_D4294 extends neqsim.standards.Standard {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ASTM_D4294.class);

  /** Total sulfur content in weight percent. */
  private double totalSulfurWtPct = Double.NaN;

  /** H2S contribution to sulfur in weight percent. */
  private double h2sSulfurWtPct = 0.0;

  /** Mercaptan sulfur contribution in weight percent. */
  private double mercaptanSulfurWtPct = 0.0;

  /** Molar mass of sulfur in g/mol. */
  private static final double SULFUR_MOLAR_MASS = 32.065;

  /** Molar mass of H2S in g/mol. */
  private static final double H2S_MOLAR_MASS = 34.08;

  /** Molar mass of COS in g/mol. */
  private static final double COS_MOLAR_MASS = 60.07;

  /** Measurement pressure in bara. */
  private double measurementPressure = 1.01325;

  /** Measurement temperature in Celsius. */
  private double measurementTemperatureC = 15.0;

  /**
   * Sulfur-bearing component names and the number of sulfur atoms per molecule. Each entry is
   * {componentName, numberOfSulfurAtoms}.
   */
  private static final Object[][] SULFUR_COMPONENTS =
      {{"H2S", 1}, {"hydrogen sulfide", 1}, {"methyl mercaptan", 1}, {"ethyl mercaptan", 1},
          {"COS", 1}, {"carbonyl sulfide", 1}, {"CS2", 2}, {"carbon disulfide", 2}, {"SO2", 1},
          {"sulfur dioxide", 1}, {"dimethyl sulfide", 1}, {"DMS", 1}};

  /**
   * Constructor for Standard_ASTM_D4294.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object representing the oil
   */
  public Standard_ASTM_D4294(SystemInterface thermoSystem) {
    super("Standard_ASTM_D4294", "ASTM D4294 - Sulfur in Petroleum Products", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      SystemInterface fluid = thermoSystem.clone();
      fluid.setTemperature(273.15 + measurementTemperatureC);
      fluid.setPressure(measurementPressure);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();

      totalSulfurWtPct = 0.0;
      h2sSulfurWtPct = 0.0;
      mercaptanSulfurWtPct = 0.0;

      // Find oil/liquid phase
      int oilPhase = -1;
      for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
        String phaseType = fluid.getPhase(i).getType().toString();
        if ("oil".equals(phaseType) || "liquid".equals(phaseType)) {
          oilPhase = i;
          break;
        }
      }
      if (oilPhase < 0 && fluid.getNumberOfPhases() > 0) {
        oilPhase = 0;
      }

      if (oilPhase >= 0) {
        double totalMass = 0.0;
        // Calculate total mass of the liquid phase
        for (int j = 0; j < fluid.getPhase(oilPhase).getNumberOfComponents(); j++) {
          totalMass += fluid.getPhase(oilPhase).getComponent(j).getx()
              * fluid.getPhase(oilPhase).getComponent(j).getMolarMass() * 1000.0;
        }

        if (totalMass <= 0) {
          totalSulfurWtPct = 0.0;
          return;
        }

        for (int j = 0; j < fluid.getPhase(oilPhase).getNumberOfComponents(); j++) {
          String compName = fluid.getPhase(oilPhase).getComponent(j).getName().toLowerCase();
          double moleFraction = fluid.getPhase(oilPhase).getComponent(j).getx();
          double compMolarMass = fluid.getPhase(oilPhase).getComponent(j).getMolarMass() * 1000.0; // g/mol

          int sulfurAtoms = getSulfurAtoms(compName);
          if (sulfurAtoms > 0) {
            double sulfurMassContribution = moleFraction * sulfurAtoms * SULFUR_MOLAR_MASS;
            double sulfurWt = sulfurMassContribution / totalMass * 100.0;
            totalSulfurWtPct += sulfurWt;

            if (compName.contains("h2s") || compName.contains("hydrogen sulfide")) {
              h2sSulfurWtPct += sulfurWt;
            } else if (compName.contains("mercaptan")) {
              mercaptanSulfurWtPct += sulfurWt;
            }
          }
        }
      }
    } catch (Exception ex) {
      logger.error("Sulfur calculation failed: {}", ex.getMessage());
      totalSulfurWtPct = Double.NaN;
    }
  }

  /**
   * Gets the number of sulfur atoms for a given component name.
   *
   * @param componentName the component name (lowercase)
   * @return number of sulfur atoms, 0 if not a sulfur compound
   */
  private int getSulfurAtoms(String componentName) {
    for (Object[] entry : SULFUR_COMPONENTS) {
      if (componentName.contains(((String) entry[0]).toLowerCase())) {
        return (Integer) entry[1];
      }
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    switch (returnParameter) {
      case "sulfur":
      case "totalSulfur":
        return totalSulfurWtPct;
      case "H2S_sulfur":
        return h2sSulfurWtPct;
      case "mercaptanSulfur":
        return mercaptanSulfurWtPct;
      default:
        logger.error("Unsupported parameter: {}", returnParameter);
        return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double wtPct = getValue(returnParameter);
    if (Double.isNaN(wtPct)) {
      return Double.NaN;
    }
    if ("ppmw".equalsIgnoreCase(returnUnit) || "ppm".equalsIgnoreCase(returnUnit)) {
      return wtPct * 10000.0;
    } else if ("mg/kg".equalsIgnoreCase(returnUnit)) {
      return wtPct * 10000.0;
    }
    return wtPct; // default: wt%
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return "wt%";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return !Double.isNaN(totalSulfurWtPct);
  }

  /**
   * Returns the sour/sweet classification based on total sulfur content.
   *
   * <p>
   * Classification per standard industry convention:
   * </p>
   * <ul>
   * <li>Sweet crude: sulfur &lt; 0.5 wt%</li>
   * <li>Medium sour crude: 0.5 &lt;= sulfur &lt; 1.0 wt%</li>
   * <li>Sour crude: sulfur &gt;= 1.0 wt%</li>
   * </ul>
   *
   * @return a string classification of the crude oil sulfur level
   */
  public String getSulfurClassification() {
    if (Double.isNaN(totalSulfurWtPct)) {
      return "Unknown";
    }
    if (totalSulfurWtPct < 0.5) {
      return "Sweet";
    } else if (totalSulfurWtPct < 1.0) {
      return "Medium Sour";
    } else {
      return "Sour";
    }
  }

  /**
   * Gets the measurement temperature.
   *
   * @return temperature in Celsius
   */
  public double getMeasurementTemperatureC() {
    return measurementTemperatureC;
  }

  /**
   * Sets the measurement temperature. Default is 15 C.
   *
   * @param temperatureC temperature in Celsius
   */
  public void setMeasurementTemperatureC(double temperatureC) {
    this.measurementTemperatureC = temperatureC;
  }
}
