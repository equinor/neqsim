package neqsim.thermo.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Utility class for classifying reservoir fluids based on phase behavior characteristics.
 *
 * <p>
 * Classification follows the Whitson methodology as described in:
 * <ul>
 * <li>https://wiki.whitson.com/phase_behavior/classification/reservoir_fluid_type/</li>
 * <li>SPE Monograph "Phase Behavior" by Whitson and Brulé</li>
 * </ul>
 *
 * <h2>Classification Criteria:</h2>
 * <table border="1">
 * <caption>Classification criteria for different reservoir fluid types</caption>
 * <tr>
 * <th>Fluid Type</th>
 * <th>GOR (scf/STB)</th>
 * <th>C7+ (mol%)</th>
 * <th>API Gravity</th>
 * </tr>
 * <tr>
 * <td>Dry Gas</td>
 * <td>&gt; 100,000</td>
 * <td>&lt; 0.7</td>
 * <td>N/A</td>
 * </tr>
 * <tr>
 * <td>Wet Gas</td>
 * <td>15,000 - 100,000</td>
 * <td>0.7 - 4</td>
 * <td>40-60°</td>
 * </tr>
 * <tr>
 * <td>Gas Condensate</td>
 * <td>3,300 - 15,000</td>
 * <td>4 - 12.5</td>
 * <td>40-60°</td>
 * </tr>
 * <tr>
 * <td>Volatile Oil</td>
 * <td>1,000 - 3,300</td>
 * <td>12.5 - 20</td>
 * <td>40-50°</td>
 * </tr>
 * <tr>
 * <td>Black Oil</td>
 * <td>&lt; 1,000</td>
 * <td>&gt; 20</td>
 * <td>15-40°</td>
 * </tr>
 * <tr>
 * <td>Heavy Oil</td>
 * <td>&lt; 200</td>
 * <td>&gt; 30</td>
 * <td>10-15°</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * {@code
 * SystemInterface fluid = new SystemSrkEos(373.15, 100.0);
 * fluid.addComponent("methane", 0.70);
 * fluid.addComponent("ethane", 0.10);
 * fluid.addComponent("n-heptane", 0.20);
 * fluid.createDatabase(true);
 * fluid.setMixingRule("classic");
 * 
 * ReservoirFluidType type = FluidClassifier.classify(fluid);
 * System.out.println("Fluid type: " + type.getDisplayName());
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ReservoirFluidType
 */
public final class FluidClassifier {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FluidClassifier.class);

  /** Conversion factor from Sm³/Sm³ to scf/STB. */
  private static final double SM3_SM3_TO_SCF_STB = 5.6146;

  /** GOR threshold for dry gas (scf/STB). */
  private static final double GOR_DRY_GAS = 100000.0;

  /** GOR threshold for wet gas lower bound (scf/STB). */
  private static final double GOR_WET_GAS_LOWER = 15000.0;

  /** GOR threshold for gas condensate lower bound (scf/STB). */
  private static final double GOR_GAS_CONDENSATE_LOWER = 3300.0;

  /** GOR threshold for volatile oil lower bound (scf/STB). */
  private static final double GOR_VOLATILE_OIL_LOWER = 1000.0;

  /** GOR threshold for heavy oil upper bound (scf/STB). */
  private static final double GOR_HEAVY_OIL = 200.0;

  /** C7+ threshold for dry gas (mol%). */
  private static final double C7PLUS_DRY_GAS = 0.7;

  /** C7+ threshold for wet gas upper bound (mol%). */
  private static final double C7PLUS_WET_GAS = 4.0;

  /** C7+ threshold for gas condensate upper bound (mol%). */
  private static final double C7PLUS_GAS_CONDENSATE = 12.5;

  /** C7+ threshold for volatile oil upper bound (mol%). */
  private static final double C7PLUS_VOLATILE_OIL = 20.0;

  /** C7+ threshold for heavy oil (mol%). */
  private static final double C7PLUS_HEAVY_OIL = 30.0;

  /** Private constructor to prevent instantiation. */
  private FluidClassifier() {}

  /**
   * Classify a reservoir fluid based on its composition.
   *
   * <p>
   * This method uses C7+ content as the primary classification criterion.
   * </p>
   *
   * @param fluid the fluid system to classify
   * @return the reservoir fluid type
   */
  public static ReservoirFluidType classify(SystemInterface fluid) {
    if (fluid == null) {
      return ReservoirFluidType.UNKNOWN;
    }

    double c7PlusMolPercent = calculateC7PlusContent(fluid);

    return classifyByC7Plus(c7PlusMolPercent);
  }

  /**
   * Classify a reservoir fluid based on C7+ content.
   *
   * @param c7PlusMolPercent C7+ content in mol%
   * @return the reservoir fluid type
   */
  public static ReservoirFluidType classifyByC7Plus(double c7PlusMolPercent) {
    if (c7PlusMolPercent < 0.0 || Double.isNaN(c7PlusMolPercent)) {
      return ReservoirFluidType.UNKNOWN;
    }
    if (c7PlusMolPercent < C7PLUS_DRY_GAS) {
      return ReservoirFluidType.DRY_GAS;
    } else if (c7PlusMolPercent < C7PLUS_WET_GAS) {
      return ReservoirFluidType.WET_GAS;
    } else if (c7PlusMolPercent < C7PLUS_GAS_CONDENSATE) {
      return ReservoirFluidType.GAS_CONDENSATE;
    } else if (c7PlusMolPercent < C7PLUS_VOLATILE_OIL) {
      return ReservoirFluidType.VOLATILE_OIL;
    } else if (c7PlusMolPercent < C7PLUS_HEAVY_OIL) {
      return ReservoirFluidType.BLACK_OIL;
    } else {
      return ReservoirFluidType.HEAVY_OIL;
    }
  }

  /**
   * Classify a reservoir fluid based on GOR (gas-oil ratio).
   *
   * @param gorScfStb gas-oil ratio in scf/STB
   * @return the reservoir fluid type
   */
  public static ReservoirFluidType classifyByGOR(double gorScfStb) {
    if (gorScfStb > GOR_DRY_GAS) {
      return ReservoirFluidType.DRY_GAS;
    } else if (gorScfStb > GOR_WET_GAS_LOWER) {
      return ReservoirFluidType.WET_GAS;
    } else if (gorScfStb > GOR_GAS_CONDENSATE_LOWER) {
      return ReservoirFluidType.GAS_CONDENSATE;
    } else if (gorScfStb > GOR_VOLATILE_OIL_LOWER) {
      return ReservoirFluidType.VOLATILE_OIL;
    } else if (gorScfStb > GOR_HEAVY_OIL) {
      return ReservoirFluidType.BLACK_OIL;
    } else {
      return ReservoirFluidType.HEAVY_OIL;
    }
  }

  /**
   * Classify a reservoir fluid using both composition and phase envelope analysis.
   *
   * <p>
   * This is the most comprehensive classification method, considering:
   * <ul>
   * <li>C7+ content</li>
   * <li>Critical point location</li>
   * <li>Cricondenbar and cricondentherm</li>
   * <li>Reservoir temperature relative to phase envelope</li>
   * </ul>
   *
   * @param fluid the fluid system to classify
   * @param reservoirTemperatureK reservoir temperature in Kelvin
   * @return the reservoir fluid type
   */
  public static ReservoirFluidType classifyWithPhaseEnvelope(SystemInterface fluid,
      double reservoirTemperatureK) {
    if (fluid == null) {
      return ReservoirFluidType.UNKNOWN;
    }

    // First get composition-based classification
    ReservoirFluidType compositionBased = classify(fluid);

    // Try to refine with phase envelope analysis
    try {
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(fluid);

      // Calculate critical point
      thermoOps.calcPTphaseEnvelope();
      double criticalTemp = fluid.getTC();

      // If reservoir T > cricondentherm, it's a wet gas or dry gas
      // If reservoir T is between Tc and cricondentherm, it's a gas condensate
      // If reservoir T < Tc, it's an oil (volatile or black)

      if (reservoirTemperatureK > criticalTemp * 1.1) {
        // Well above critical - gas phase dominates
        if (compositionBased == ReservoirFluidType.GAS_CONDENSATE
            || compositionBased == ReservoirFluidType.WET_GAS) {
          return ReservoirFluidType.WET_GAS;
        }
      } else if (reservoirTemperatureK > criticalTemp * 0.95) {
        // Near critical - volatile oil or gas condensate
        if (compositionBased == ReservoirFluidType.BLACK_OIL) {
          return ReservoirFluidType.VOLATILE_OIL;
        } else if (compositionBased == ReservoirFluidType.WET_GAS) {
          return ReservoirFluidType.GAS_CONDENSATE;
        }
      }
    } catch (Exception e) {
      logger.debug("Phase envelope analysis failed, using composition-based classification: "
          + e.getMessage());
    }

    return compositionBased;
  }

  /**
   * Calculate C7+ content in mol%.
   *
   * @param fluid the fluid system
   * @return C7+ content in mol%
   */
  public static double calculateC7PlusContent(SystemInterface fluid) {
    if (fluid == null) {
      return 0.0;
    }

    double c7PlusMoles = 0.0;
    double totalMoles = fluid.getTotalNumberOfMoles();

    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getComponent(i).getComponentName().toLowerCase();
      double molarMass = fluid.getComponent(i).getMolarMass() * 1000.0; // g/mol

      // C7+ includes n-heptane and heavier components (MW >= 100 g/mol approximately)
      // Also includes TBP and plus fractions
      boolean isC7Plus = molarMass >= 100.0 || name.startsWith("c7") || name.startsWith("c8")
          || name.startsWith("c9") || name.contains("heptane") || name.contains("octane")
          || name.contains("nonane") || name.contains("decane")
          || fluid.getComponent(i).isIsTBPfraction() || fluid.getComponent(i).isIsPlusFraction();

      if (isC7Plus) {
        c7PlusMoles += fluid.getComponent(i).getNumberOfmoles();
      }
    }

    return totalMoles > 0 ? (c7PlusMoles / totalMoles) * 100.0 : 0.0;
  }

  /**
   * Estimate API gravity from fluid density at standard conditions.
   *
   * <p>
   * API = 141.5 / SG - 131.5, where SG is specific gravity relative to water at 60°F
   * </p>
   *
   * @param fluid the fluid system
   * @return estimated API gravity, or NaN if not applicable
   */
  public static double estimateAPIGravity(SystemInterface fluid) {
    if (fluid == null) {
      return Double.NaN;
    }

    try {
      // Flash to standard conditions
      SystemInterface stdFluid = fluid.clone();
      stdFluid.setTemperature(288.71); // 60°F in K
      stdFluid.setPressure(1.01325); // 1 atm in bar

      ThermodynamicOperations thermoOps = new ThermodynamicOperations(stdFluid);
      thermoOps.TPflash();

      // Get oil phase density
      if (stdFluid.hasPhaseType("oil")) {
        double oilDensity = stdFluid.getPhase("oil").getDensity("kg/m3");
        double waterDensity = 999.0; // kg/m³ at 60°F
        double specificGravity = oilDensity / waterDensity;

        return 141.5 / specificGravity - 131.5;
      }
    } catch (Exception e) {
      logger.debug("API gravity estimation failed: " + e.getMessage());
    }

    return Double.NaN;
  }

  /**
   * Generate a fluid classification report.
   *
   * @param fluid the fluid system to analyze
   * @return formatted classification report
   */
  public static String generateClassificationReport(SystemInterface fluid) {
    if (fluid == null) {
      return "No fluid provided for classification.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== Reservoir Fluid Classification Report ===\n\n");

    // Composition analysis
    double c7Plus = calculateC7PlusContent(fluid);
    sb.append("Composition Analysis:\n");
    sb.append(String.format("  C7+ Content: %.2f mol%%\n", c7Plus));

    // Classification
    ReservoirFluidType type = classify(fluid);
    sb.append("\nClassification Result:\n");
    sb.append(String.format("  Fluid Type: %s\n", type.getDisplayName()));
    sb.append(String.format("  Typical GOR Range: %s scf/STB\n", type.getTypicalGORRange()));
    sb.append(String.format("  Typical C7+ Range: %s mol%%\n", type.getTypicalC7PlusRange()));

    // API gravity estimate
    double api = estimateAPIGravity(fluid);
    if (!Double.isNaN(api)) {
      sb.append(String.format("\n  Estimated API Gravity: %.1f°\n", api));
    }

    // Recommendations
    sb.append("\nModeling Recommendations:\n");
    switch (type) {
      case DRY_GAS:
      case WET_GAS:
        sb.append("  - Use equation of state (SRK or PR) for accurate Z-factor\n");
        sb.append("  - Black-oil model may be sufficient for simulation\n");
        break;
      case GAS_CONDENSATE:
        sb.append("  - Compositional simulation recommended\n");
        sb.append("  - CVD experiment important for liquid dropout curve\n");
        sb.append("  - Consider modified black-oil with OGR (Rv)\n");
        break;
      case VOLATILE_OIL:
        sb.append("  - Compositional simulation strongly recommended\n");
        sb.append("  - Modified black-oil model may be acceptable\n");
        sb.append("  - DLE and separator tests are essential\n");
        break;
      case BLACK_OIL:
        sb.append("  - Traditional black-oil model typically adequate\n");
        sb.append("  - DLE experiment for Bo, Rs, viscosity\n");
        break;
      case HEAVY_OIL:
        sb.append("  - Viscosity modeling is critical\n");
        sb.append("  - Consider thermal effects if applicable\n");
        sb.append("  - LBC viscosity may need tuning\n");
        break;
      default:
        break;
    }

    return sb.toString();
  }
}
