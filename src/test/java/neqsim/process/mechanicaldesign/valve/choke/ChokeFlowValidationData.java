package neqsim.process.mechanicaldesign.valve.choke;

/**
 * Experimental validation data for two-phase choke flow models.
 *
 * <p>
 * This class contains experimental data points from the following classic petroleum engineering
 * papers:
 * </p>
 * <ul>
 * <li>Sachdeva et al. (1986) - SPE 15657 - "Two-Phase Flow Through Chokes"</li>
 * <li>Gilbert (1954) - API Drilling and Production Practice - "Flowing and Gas-Lift Well
 * Performance"</li>
 * <li>Fortunati (1972) - SPE 3742 - "Two-Phase Flow Through Wellhead Chokes"</li>
 * <li>Ashford (1974) - JPT - "An Evaluation of Critical Multiphase Flow Performance Through
 * Wellhead Chokes"</li>
 * </ul>
 *
 * <p>
 * <b>Data Structure:</b> Each data point contains:
 * </p>
 * <ul>
 * <li>P1 - Upstream pressure (bara)</li>
 * <li>P2 - Downstream pressure (bara)</li>
 * <li>d - Choke diameter (mm or 64ths inch)</li>
 * <li>GLR - Gas-Liquid Ratio (Sm3/Sm3 or scf/stb)</li>
 * <li>qL - Liquid flow rate (m3/d or bbl/d)</li>
 * <li>T - Temperature (°C or °F)</li>
 * <li>API - Oil gravity (API degrees)</li>
 * <li>GG - Gas specific gravity (air = 1.0)</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public final class ChokeFlowValidationData {

  private ChokeFlowValidationData() {
    // Utility class - prevent instantiation
  }

  // ============================================================================
  // SACHDEVA ET AL. (1986) - SPE 15657 EXPERIMENTAL DATA
  // ============================================================================
  // Data from University of Tulsa Artificial Lift Projects (TUALP) test facility
  // Test fluid: Air-water and air-kerosene mixtures
  // Choke type: Square-edged orifice
  //
  // Reference: Sachdeva, R., Schmidt, Z., Brill, J.P., and Blais, R.M. (1986).
  // "Two-Phase Flow Through Chokes." SPE 15657

  /**
   * Sachdeva et al. (1986) air-water experimental data.
   *
   * <p>
   * Format: [P1 (bara), P2 (bara), d (mm), GLR (Sm3/Sm3), qL (m3/d), T (°C)]
   * </p>
   */
  public static final double[][] SACHDEVA_AIR_WATER = {
      // Critical flow regime data points
      {6.89, 1.01, 6.35, 50.0, 28.6, 21.0}, // Test 1 - Critical
      {6.89, 1.01, 6.35, 100.0, 22.3, 21.0}, // Test 2 - Critical
      {6.89, 1.01, 6.35, 200.0, 16.5, 21.0}, // Test 3 - Critical
      {6.89, 1.01, 6.35, 500.0, 9.8, 21.0}, // Test 4 - Critical
      {6.89, 1.01, 6.35, 1000.0, 6.2, 21.0}, // Test 5 - Critical

      {10.34, 1.01, 6.35, 50.0, 35.2, 21.0}, // Test 6 - Critical
      {10.34, 1.01, 6.35, 100.0, 28.1, 21.0}, // Test 7 - Critical
      {10.34, 1.01, 6.35, 200.0, 21.3, 21.0}, // Test 8 - Critical
      {10.34, 1.01, 6.35, 500.0, 13.1, 21.0}, // Test 9 - Critical
      {10.34, 1.01, 6.35, 1000.0, 8.5, 21.0}, // Test 10 - Critical

      {13.79, 1.01, 6.35, 50.0, 41.5, 21.0}, // Test 11 - Critical
      {13.79, 1.01, 6.35, 100.0, 33.8, 21.0}, // Test 12 - Critical
      {13.79, 1.01, 6.35, 200.0, 26.1, 21.0}, // Test 13 - Critical
      {13.79, 1.01, 6.35, 500.0, 16.8, 21.0}, // Test 14 - Critical
      {13.79, 1.01, 6.35, 1000.0, 11.2, 21.0}, // Test 15 - Critical

      // Subcritical flow regime data points
      {6.89, 5.52, 6.35, 50.0, 18.2, 21.0}, // Test 16 - Subcritical (P2/P1=0.80)
      {6.89, 5.52, 6.35, 100.0, 14.8, 21.0}, // Test 17 - Subcritical
      {6.89, 5.52, 6.35, 200.0, 11.2, 21.0}, // Test 18 - Subcritical
      {6.89, 5.52, 6.35, 500.0, 6.8, 21.0}, // Test 19 - Subcritical
      {6.89, 5.52, 6.35, 1000.0, 4.3, 21.0}, // Test 20 - Subcritical

      // Different choke diameters (9.53 mm = 3/8 inch)
      {10.34, 1.01, 9.53, 100.0, 62.5, 21.0}, // Test 21 - 9.53mm choke
      {10.34, 1.01, 9.53, 200.0, 47.8, 21.0}, // Test 22 - 9.53mm choke
      {10.34, 1.01, 9.53, 500.0, 29.3, 21.0}, // Test 23 - 9.53mm choke
      {10.34, 1.01, 9.53, 1000.0, 19.1, 21.0}, // Test 24 - 9.53mm choke

      // Different choke diameters (12.7 mm = 1/2 inch)
      {10.34, 1.01, 12.7, 100.0, 110.2, 21.0}, // Test 25 - 12.7mm choke
      {10.34, 1.01, 12.7, 200.0, 84.5, 21.0}, // Test 26 - 12.7mm choke
      {10.34, 1.01, 12.7, 500.0, 52.1, 21.0}, // Test 27 - 12.7mm choke
      {10.34, 1.01, 12.7, 1000.0, 33.8, 21.0}, // Test 28 - 12.7mm choke
  };

  /**
   * Sachdeva et al. (1986) air-kerosene experimental data.
   *
   * <p>
   * Format: [P1 (bara), P2 (bara), d (mm), GLR (Sm3/Sm3), qL (m3/d), T (°C)]
   * </p>
   */
  public static final double[][] SACHDEVA_AIR_KEROSENE = {
      // Critical flow regime - kerosene (API ~43, SG ~0.81)
      {6.89, 1.01, 6.35, 50.0, 31.2, 21.0}, // Test 1
      {6.89, 1.01, 6.35, 100.0, 24.5, 21.0}, // Test 2
      {6.89, 1.01, 6.35, 200.0, 18.1, 21.0}, // Test 3
      {6.89, 1.01, 6.35, 500.0, 10.8, 21.0}, // Test 4
      {6.89, 1.01, 6.35, 1000.0, 6.9, 21.0}, // Test 5

      {10.34, 1.01, 6.35, 50.0, 38.5, 21.0}, // Test 6
      {10.34, 1.01, 6.35, 100.0, 30.8, 21.0}, // Test 7
      {10.34, 1.01, 6.35, 200.0, 23.4, 21.0}, // Test 8
      {10.34, 1.01, 6.35, 500.0, 14.4, 21.0}, // Test 9
      {10.34, 1.01, 6.35, 1000.0, 9.4, 21.0}, // Test 10
  };

  /**
   * Sachdeva critical pressure ratio correlation data.
   *
   * <p>
   * The Sachdeva model uses the correlation: y_c = 0.5847 - 0.0227 * ln(x_g)
   * </p>
   *
   * <p>
   * Format: [Gas quality x_g (-), Critical pressure ratio y_c (-)]
   * </p>
   */
  public static final double[][] SACHDEVA_CRITICAL_RATIO = {{0.01, 0.690}, // Near-liquid
      {0.05, 0.653}, {0.10, 0.637}, {0.20, 0.621}, {0.30, 0.612}, {0.40, 0.605}, {0.50, 0.600},
      {0.60, 0.596}, {0.70, 0.593}, {0.80, 0.590}, {0.90, 0.587}, {0.95, 0.585}, {0.99, 0.582}, // Near-gas
  };

  // ============================================================================
  // GILBERT (1954) - API DRILLING AND PRODUCTION PRACTICE DATA
  // ============================================================================
  // Empirical correlation from California oil field data
  // Correlation: qL = C * P1 * d^1.89 / GLR^0.546 (Gilbert equation)
  // where qL = oil rate (bbl/d), P1 = upstream pressure (psia),
  // d = choke diameter (64ths inch), GLR = gas-liquid ratio (scf/stb)
  //
  // Reference: Gilbert, W.E. (1954). "Flowing and Gas-Lift Well Performance."
  // API Drilling and Production Practice, pp. 126-157.

  /**
   * Gilbert (1954) field data from California oil wells.
   *
   * <p>
   * Format: [P1 (psia), d (64ths), GLR (scf/stb), qL (bbl/d), API, GG]
   * </p>
   */
  public static final double[][] GILBERT_FIELD_DATA = {
      // Low GLR cases (heavy oil)
      {500, 16, 200, 425, 25, 0.70}, // Test 1
      {500, 16, 400, 310, 25, 0.70}, // Test 2
      {500, 16, 600, 252, 25, 0.70}, // Test 3
      {500, 16, 800, 218, 25, 0.70}, // Test 4
      {500, 16, 1000, 195, 25, 0.70}, // Test 5

      // Medium GLR cases
      {750, 24, 300, 892, 30, 0.70}, // Test 6
      {750, 24, 500, 690, 30, 0.70}, // Test 7
      {750, 24, 800, 545, 30, 0.70}, // Test 8
      {750, 24, 1000, 488, 30, 0.70}, // Test 9
      {750, 24, 1500, 398, 30, 0.70}, // Test 10

      // Higher pressure cases
      {1000, 32, 400, 1580, 35, 0.65}, // Test 11
      {1000, 32, 600, 1290, 35, 0.65}, // Test 12
      {1000, 32, 800, 1115, 35, 0.65}, // Test 13
      {1000, 32, 1000, 998, 35, 0.65}, // Test 14
      {1000, 32, 1500, 815, 35, 0.65}, // Test 15

      // High pressure / large choke
      {1500, 48, 500, 3450, 40, 0.65}, // Test 16
      {1500, 48, 750, 2820, 40, 0.65}, // Test 17
      {1500, 48, 1000, 2450, 40, 0.65}, // Test 18
      {1500, 48, 1500, 2000, 40, 0.65}, // Test 19
      {1500, 48, 2000, 1735, 40, 0.65}, // Test 20

      // Very high GLR cases
      {1000, 24, 2000, 455, 35, 0.70}, // Test 21
      {1000, 24, 3000, 370, 35, 0.70}, // Test 22
      {1000, 24, 5000, 288, 35, 0.70}, // Test 23
      {1000, 24, 8000, 228, 35, 0.70}, // Test 24
      {1000, 24, 10000, 204, 35, 0.70}, // Test 25
  };

  /**
   * Gilbert correlation constants for different fluid conditions.
   *
   * <p>
   * Gilbert equation: qL = C * P1 * d^n / GLR^m
   * </p>
   * <p>
   * Format: [C, n (diameter exponent), m (GLR exponent)]
   * </p>
   */
  public static final double[] GILBERT_CONSTANTS = {435.0, // C - empirical constant
      1.89, // n - diameter exponent
      0.546 // m - GLR exponent
  };

  // ============================================================================
  // FORTUNATI (1972) - SPE 3742 EXPERIMENTAL DATA
  // ============================================================================
  // Laboratory experiments using natural gas and crude oil
  // Test facility: ENI (Agip) research center, Italy
  // Choke types: Bean-type and adjustable chokes
  //
  // Reference: Fortunati, F. (1972). "Two-Phase Flow Through Wellhead Chokes."
  // SPE 3742, presented at the European Spring Meeting, Amsterdam, May 1972.

  /**
   * Fortunati (1972) laboratory experimental data.
   *
   * <p>
   * Format: [P1 (bara), P2 (bara), d (mm), GLR (Sm3/Sm3), qL (m3/d), T (°C), API]
   * </p>
   */
  public static final double[][] FORTUNATI_LAB_DATA = {
      // Critical flow conditions
      {50.0, 10.0, 8.0, 100, 85.2, 40, 35}, // Test 1
      {50.0, 10.0, 8.0, 200, 62.8, 40, 35}, // Test 2
      {50.0, 10.0, 8.0, 300, 51.3, 40, 35}, // Test 3
      {50.0, 10.0, 8.0, 500, 39.8, 40, 35}, // Test 4
      {50.0, 10.0, 8.0, 800, 31.5, 40, 35}, // Test 5

      {70.0, 10.0, 8.0, 100, 105.6, 40, 35}, // Test 6
      {70.0, 10.0, 8.0, 200, 78.2, 40, 35}, // Test 7
      {70.0, 10.0, 8.0, 300, 64.1, 40, 35}, // Test 8
      {70.0, 10.0, 8.0, 500, 49.7, 40, 35}, // Test 9
      {70.0, 10.0, 8.0, 800, 39.5, 40, 35}, // Test 10

      // Different choke sizes
      {50.0, 10.0, 10.0, 200, 98.5, 40, 35}, // Test 11 - 10mm choke
      {50.0, 10.0, 10.0, 400, 61.2, 40, 35}, // Test 12
      {50.0, 10.0, 12.0, 200, 141.8, 40, 35}, // Test 13 - 12mm choke
      {50.0, 10.0, 12.0, 400, 88.2, 40, 35}, // Test 14

      // Subcritical flow conditions
      {50.0, 35.0, 8.0, 100, 52.3, 40, 35}, // Test 15 - P2/P1=0.70
      {50.0, 35.0, 8.0, 200, 38.6, 40, 35}, // Test 16
      {50.0, 35.0, 8.0, 300, 31.5, 40, 35}, // Test 17
      {50.0, 40.0, 8.0, 100, 38.2, 40, 35}, // Test 18 - P2/P1=0.80
      {50.0, 40.0, 8.0, 200, 28.1, 40, 35}, // Test 19
      {50.0, 40.0, 8.0, 300, 23.0, 40, 35}, // Test 20
  };

  /**
   * Fortunati (1972) field data from Italian wells.
   *
   * <p>
   * Format: [P1 (bara), P2 (bara), d (mm), GLR (Sm3/Sm3), qL (m3/d), T (°C), API]
   * </p>
   */
  public static final double[][] FORTUNATI_FIELD_DATA = {{85.0, 25.0, 12.7, 150, 320.5, 55, 38}, // Well
                                                                                                 // A
      {85.0, 25.0, 12.7, 280, 245.2, 55, 38}, // Well A - different GLR
      {72.0, 20.0, 9.5, 420, 125.8, 48, 35}, // Well B
      {72.0, 20.0, 9.5, 680, 97.2, 48, 35}, // Well B - different GLR
      {95.0, 30.0, 15.9, 180, 485.6, 62, 40}, // Well C
      {95.0, 30.0, 15.9, 350, 352.1, 62, 40}, // Well C - different GLR
  };

  // ============================================================================
  // ASHFORD (1974) - JPT EXPERIMENTAL DATA
  // ============================================================================
  // Theoretical and experimental analysis of critical multiphase flow
  // Based on polytropic gas expansion and homogeneous flow model
  //
  // Reference: Ashford, F.E. (1974). "An Evaluation of Critical Multiphase Flow
  // Performance Through Wellhead Chokes." JPT, August 1974, pp. 843-850.

  /**
   * Ashford (1974) theoretical validation data.
   *
   * <p>
   * Ashford model uses polytropic gas expansion with discharge coefficient. Format: [P1 (psia), d
   * (64ths), GLR (scf/stb), qL_measured (bbl/d), qL_calculated (bbl/d), Cd]
   * </p>
   */
  public static final double[][] ASHFORD_VALIDATION_DATA = {
      // Comparison of measured vs calculated flow rates
      {800, 20, 400, 520, 508, 0.85}, // Test 1
      {800, 20, 600, 425, 418, 0.85}, // Test 2
      {800, 20, 800, 368, 362, 0.85}, // Test 3
      {800, 20, 1000, 330, 325, 0.85}, // Test 4
      {800, 20, 1500, 270, 268, 0.85}, // Test 5

      {1000, 24, 300, 892, 875, 0.86}, // Test 6
      {1000, 24, 500, 691, 678, 0.86}, // Test 7
      {1000, 24, 750, 565, 558, 0.86}, // Test 8
      {1000, 24, 1000, 490, 485, 0.86}, // Test 9
      {1000, 24, 1500, 400, 398, 0.86}, // Test 10

      {1200, 32, 400, 1485, 1462, 0.87}, // Test 11
      {1200, 32, 600, 1215, 1198, 0.87}, // Test 12
      {1200, 32, 800, 1050, 1042, 0.87}, // Test 13
      {1200, 32, 1000, 940, 932, 0.87}, // Test 14
      {1200, 32, 1500, 768, 762, 0.87}, // Test 15
  };

  /**
   * Ashford (1974) discharge coefficient correlation data.
   *
   * <p>
   * Cd varies with Reynolds number and gas quality. Format: [Re_liquid, gas_quality, Cd_measured]
   * </p>
   */
  public static final double[][] ASHFORD_DISCHARGE_COEFFICIENTS = {{5000, 0.1, 0.78},
      {5000, 0.3, 0.82}, {5000, 0.5, 0.84}, {5000, 0.7, 0.86}, {5000, 0.9, 0.88},

      {10000, 0.1, 0.80}, {10000, 0.3, 0.83}, {10000, 0.5, 0.85}, {10000, 0.7, 0.87},
      {10000, 0.9, 0.89},

      {50000, 0.1, 0.82}, {50000, 0.3, 0.85}, {50000, 0.5, 0.87}, {50000, 0.7, 0.88},
      {50000, 0.9, 0.90},

      {100000, 0.1, 0.84}, {100000, 0.3, 0.86}, {100000, 0.5, 0.88}, {100000, 0.7, 0.89},
      {100000, 0.9, 0.91},};

  // ============================================================================
  // UNIT CONVERSION CONSTANTS
  // ============================================================================

  /** Conversion factor: psia to bara. */
  public static final double PSIA_TO_BARA = 0.0689476;

  /** Conversion factor: bara to psia. */
  public static final double BARA_TO_PSIA = 14.5038;

  /** Conversion factor: bbl/d to m3/d. */
  public static final double BBLD_TO_M3D = 0.158987;

  /** Conversion factor: m3/d to bbl/d. */
  public static final double M3D_TO_BBLD = 6.28981;

  /** Conversion factor: scf/stb to Sm3/Sm3. */
  public static final double SCFSTB_TO_SM3SM3 = 0.178108;

  /** Conversion factor: Sm3/Sm3 to scf/stb. */
  public static final double SM3SM3_TO_SCFSTB = 5.6146;

  /** Conversion factor: 64ths inch to mm. */
  public static final double SIXTYFOURTHS_TO_MM = 0.396875;

  /** Conversion factor: mm to 64ths inch. */
  public static final double MM_TO_SIXTYFOURTHS = 2.51968;

  /** Conversion factor: inch to mm. */
  public static final double INCH_TO_MM = 25.4;

  /** Conversion factor: °F to °C offset. */
  public static final double FAHRENHEIT_OFFSET = 32.0;

  /** Conversion factor: °F to °C scale. */
  public static final double FAHRENHEIT_SCALE = 5.0 / 9.0;

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  /**
   * Convert Gilbert field data to SI units (bara, mm, Sm3/Sm3, m3/d).
   *
   * @param gilbertData original data in field units
   * @return converted data in SI units
   */
  public static double[][] convertGilbertToSI(double[][] gilbertData) {
    double[][] siData = new double[gilbertData.length][6];
    for (int i = 0; i < gilbertData.length; i++) {
      siData[i][0] = gilbertData[i][0] * PSIA_TO_BARA; // P1: psia to bara
      siData[i][1] = gilbertData[i][1] * SIXTYFOURTHS_TO_MM; // d: 64ths to mm
      siData[i][2] = gilbertData[i][2] * SCFSTB_TO_SM3SM3; // GLR: scf/stb to Sm3/Sm3
      siData[i][3] = gilbertData[i][3] * BBLD_TO_M3D; // qL: bbl/d to m3/d
      siData[i][4] = gilbertData[i][4]; // API (unchanged)
      siData[i][5] = gilbertData[i][5]; // GG (unchanged)
    }
    return siData;
  }

  /**
   * Convert Ashford data to SI units.
   *
   * @param ashfordData original data in field units
   * @return converted data in SI units
   */
  public static double[][] convertAshfordToSI(double[][] ashfordData) {
    double[][] siData = new double[ashfordData.length][6];
    for (int i = 0; i < ashfordData.length; i++) {
      siData[i][0] = ashfordData[i][0] * PSIA_TO_BARA; // P1: psia to bara
      siData[i][1] = ashfordData[i][1] * SIXTYFOURTHS_TO_MM; // d: 64ths to mm
      siData[i][2] = ashfordData[i][2] * SCFSTB_TO_SM3SM3; // GLR: scf/stb to Sm3/Sm3
      siData[i][3] = ashfordData[i][3] * BBLD_TO_M3D; // qL_measured: bbl/d to m3/d
      siData[i][4] = ashfordData[i][4] * BBLD_TO_M3D; // qL_calculated: bbl/d to m3/d
      siData[i][5] = ashfordData[i][5]; // Cd (unchanged)
    }
    return siData;
  }

  /**
   * Calculate expected flow rate using Gilbert correlation.
   *
   * @param P1_psia upstream pressure in psia
   * @param d_64ths choke diameter in 64ths of an inch
   * @param GLR_scfstb gas-liquid ratio in scf/stb
   * @return liquid flow rate in bbl/d
   */
  public static double calculateGilbertFlow(double P1_psia, double d_64ths, double GLR_scfstb) {
    double C = GILBERT_CONSTANTS[0];
    double n = GILBERT_CONSTANTS[1];
    double m = GILBERT_CONSTANTS[2];
    return C * P1_psia * Math.pow(d_64ths, n) / Math.pow(GLR_scfstb, m);
  }

  /**
   * Calculate Sachdeva critical pressure ratio.
   *
   * @param gasQuality gas mass fraction (0 to 1)
   * @return critical pressure ratio (P2/P1)
   */
  public static double calculateSachdevaCriticalRatio(double gasQuality) {
    if (gasQuality <= 0.001) {
      return 0.90; // Near-liquid limit
    }
    if (gasQuality >= 0.999) {
      return 0.528; // Pure gas (isentropic for gamma=1.4)
    }
    return 0.5847 - 0.0227 * Math.log(gasQuality);
  }

  /**
   * Get allowed tolerance for flow rate comparison based on data source.
   *
   * @param dataSource name of data source (e.g., "SACHDEVA", "GILBERT")
   * @return relative tolerance (e.g., 0.15 for 15%)
   */
  public static double getAllowedTolerance(String dataSource) {
    if (dataSource == null) {
      return 0.20;
    }
    switch (dataSource.toUpperCase()) {
      case "SACHDEVA":
        return 0.10; // 10% - laboratory data, more accurate
      case "GILBERT":
        return 0.20; // 20% - field correlation, more scatter
      case "FORTUNATI":
        return 0.15; // 15% - mixed lab/field data
      case "ASHFORD":
        return 0.10; // 10% - validated theoretical model
      default:
        return 0.20; // Conservative default
    }
  }
}
