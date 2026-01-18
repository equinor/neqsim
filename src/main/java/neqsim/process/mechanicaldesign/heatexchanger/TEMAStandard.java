package neqsim.process.mechanicaldesign.heatexchanger;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * TEMA (Tubular Exchanger Manufacturers Association) standard designations for shell and tube heat
 * exchangers.
 *
 * <p>
 * TEMA uses a three-letter designation to specify heat exchanger configuration:
 * <ul>
 * <li>First letter: Front head type (stationary)</li>
 * <li>Second letter: Shell type</li>
 * <li>Third letter: Rear head type</li>
 * </ul>
 *
 * <p>
 * Example: "AES" means A-type front head, E-type shell, S-type rear head.
 * </p>
 *
 * <h2>Common TEMA Designations</h2>
 * <table>
 * <caption>Common TEMA heat exchanger designations and applications</caption>
 * <tr>
 * <th>Type</th>
 * <th>Application</th>
 * </tr>
 * <tr>
 * <td>AES</td>
 * <td>Most common type, removable tube bundle</td>
 * </tr>
 * <tr>
 * <td>BEM</td>
 * <td>Fixed tubesheet, clean fluids</td>
 * </tr>
 * <tr>
 * <td>AEU</td>
 * <td>U-tube, thermal expansion handling</td>
 * </tr>
 * <tr>
 * <td>AKT</td>
 * <td>Kettle reboiler</td>
 * </tr>
 * </table>
 *
 * <h2>References</h2>
 * <ul>
 * <li>TEMA Standards, 10th Edition (2019)</li>
 * <li>ASME Section VIII, Division 1</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TEMAStandard {

  /**
   * TEMA front head types (stationary head).
   */
  public enum FrontHeadType {
    /** Channel and removable cover - Most common, easy tube access. */
    A("Channel and Removable Cover",
        "Bolted cover allows individual tube access without disturbing piping"),
    /** Bonnet (integral cover) - More economical, requires breaking piping for access. */
    B("Bonnet (Integral Cover)", "More economical than A, must break piping to access tubes"),
    /** Channel integral with tubesheet and removable cover. */
    C("Channel Integral with Tubesheet",
        "Tubesheet serves as backing flange, good for high pressure"),
    /** Channel integral with tubesheet and removable cover (large bore). */
    N("Channel Integral with Tubesheet (Large)", "Similar to C but for larger sizes"),
    /** Special high-pressure closure. */
    D("Special High Pressure Closure", "For extreme pressures, Breech-Lock type");

    private final String description;
    private final String notes;

    FrontHeadType(String description, String notes) {
      this.description = description;
      this.notes = notes;
    }

    /**
     * Gets the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets application notes.
     *
     * @return notes
     */
    public String getNotes() {
      return notes;
    }
  }

  /**
   * TEMA shell types.
   */
  public enum ShellType {
    /** One-pass shell - Most common. */
    E("One-Pass Shell", "Most common, simplest design", 1.0),
    /** Two-pass shell with longitudinal baffle. */
    F("Two-Pass Shell with Longitudinal Baffle", "Better temperature approach, complex baffle",
        0.8),
    /** Split flow - Shell fluid enters at center. */
    G("Split Flow", "Lower shell-side pressure drop", 0.6),
    /** Double split flow - Two inlets. */
    H("Double Split Flow", "Very low pressure drop, large exchangers", 0.5),
    /** Divided flow - Outlet at center. */
    J("Divided Flow", "Common for condensers, single nozzle exit", 0.65),
    /** Kettle type reboiler. */
    K("Kettle Type Reboiler", "Large shell for vapor disengagement", 0.7),
    /** Cross flow. */
    X("Cross Flow", "Very low pressure drop, gas cooling", 0.3);

    private final String description;
    private final String notes;
    private final double pressureDropFactor;

    ShellType(String description, String notes, double pressureDropFactor) {
      this.description = description;
      this.notes = notes;
      this.pressureDropFactor = pressureDropFactor;
    }

    /**
     * Gets the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets application notes.
     *
     * @return notes
     */
    public String getNotes() {
      return notes;
    }

    /**
     * Gets pressure drop factor relative to E-shell.
     *
     * @return pressure drop factor (1.0 = E-shell baseline)
     */
    public double getPressureDropFactor() {
      return pressureDropFactor;
    }
  }

  /**
   * TEMA rear head types.
   */
  public enum RearHeadType {
    /** Fixed tubesheet like B stationary head. */
    L("Fixed Tubesheet like B Head", "Economical, no shell expansion allowance", false),
    /** Fixed tubesheet like A stationary head. */
    M("Fixed Tubesheet like A Head", "Most common fixed tubesheet type", false),
    /** Fixed tubesheet like C stationary head. */
    N("Fixed Tubesheet like N Head", "For high-pressure fixed applications", false),
    /** Outside packed floating head. */
    P("Outside Packed Floating Head", "Allows thermal expansion, bundle removable", true),
    /** Floating head with backing device. */
    S("Floating Head with Backing Device", "Very common, allows inspection both sides", true),
    /** Pull-through floating head. */
    T("Pull-Through Floating Head", "Bundle easily removed, larger shell required", true),
    /** U-tube bundle. */
    U("U-Tube Bundle", "Excellent thermal expansion, only 1 tubesheet", false),
    /** Externally sealed floating tubesheet. */
    W("Externally Sealed Floating Tubesheet", "For hazardous fluids, prevents mixing", true);

    private final String description;
    private final String notes;
    private final boolean floating;

    RearHeadType(String description, String notes, boolean floating) {
      this.description = description;
      this.notes = notes;
      this.floating = floating;
    }

    /**
     * Gets the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets application notes.
     *
     * @return notes
     */
    public String getNotes() {
      return notes;
    }

    /**
     * Returns whether this is a floating head type.
     *
     * @return true if floating head
     */
    public boolean isFloating() {
      return floating;
    }
  }

  /**
   * TEMA class for construction standards.
   */
  public enum TEMAClass {
    /** Most stringent, for severe service (typically petrochemical). */
    R("Severe Service", "Refineries, petrochemical plants", 1.0, 12.7, 4.76),
    /** Moderate requirements, general process use. */
    C("Moderate Service", "General process, commercial applications", 0.8, 9.5, 3.18),
    /** Least stringent, HVAC and general applications. */
    B("Light Service", "HVAC, general commercial", 0.6, 6.35, 2.11);

    private final String description;
    private final String notes;
    private final double costFactor;
    private final double minTubeWallMm;
    private final double minCorrosionAllowanceMm;

    TEMAClass(String description, String notes, double costFactor, double minTubeWallMm,
        double minCorrosionAllowanceMm) {
      this.description = description;
      this.notes = notes;
      this.costFactor = costFactor;
      this.minTubeWallMm = minTubeWallMm;
      this.minCorrosionAllowanceMm = minCorrosionAllowanceMm;
    }

    /**
     * Gets the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets application notes.
     *
     * @return notes
     */
    public String getNotes() {
      return notes;
    }

    /**
     * Gets cost factor relative to Class B.
     *
     * @return cost factor
     */
    public double getCostFactor() {
      return costFactor;
    }

    /**
     * Gets minimum tube wall thickness in mm.
     *
     * @return minimum tube wall thickness
     */
    public double getMinTubeWallMm() {
      return minTubeWallMm;
    }

    /**
     * Gets minimum corrosion allowance in mm.
     *
     * @return minimum corrosion allowance
     */
    public double getMinCorrosionAllowanceMm() {
      return minCorrosionAllowanceMm;
    }
  }

  /**
   * Standard tube sizes per TEMA.
   */
  public enum StandardTubeSize {
    /** 3/8 inch OD. */
    TUBE_3_8_INCH(9.525, new double[] {0.711, 0.889, 1.245}),
    /** 1/2 inch OD. */
    TUBE_1_2_INCH(12.7, new double[] {0.889, 1.245, 1.651}),
    /** 5/8 inch OD. */
    TUBE_5_8_INCH(15.875, new double[] {1.245, 1.651, 2.108}),
    /** 3/4 inch OD. */
    TUBE_3_4_INCH(19.05, new double[] {1.245, 1.651, 2.108, 2.769}),
    /** 1 inch OD. */
    TUBE_1_INCH(25.4, new double[] {1.245, 1.651, 2.108, 2.769, 3.404});

    private final double outerDiameterMm;
    private final double[] availableWallThicknessesMm;

    StandardTubeSize(double outerDiameterMm, double[] availableWallThicknessesMm) {
      this.outerDiameterMm = outerDiameterMm;
      this.availableWallThicknessesMm = availableWallThicknessesMm;
    }

    /**
     * Gets tube outer diameter in mm.
     *
     * @return outer diameter
     */
    public double getOuterDiameterMm() {
      return outerDiameterMm;
    }

    /**
     * Gets tube outer diameter in inches.
     *
     * @return outer diameter in inches
     */
    public double getOuterDiameterInch() {
      return outerDiameterMm / 25.4;
    }

    /**
     * Gets available wall thicknesses in mm.
     *
     * @return array of wall thicknesses
     */
    public double[] getAvailableWallThicknessesMm() {
      return availableWallThicknessesMm.clone();
    }
  }

  /**
   * Standard tube pitches per TEMA.
   */
  public enum TubePitchPattern {
    /** Triangular (30°) - maximum tube count. */
    TRIANGULAR_30("Triangular 30°", 30, 1.25, 1.1),
    /** Rotated triangular (60°) - better shellside flow. */
    TRIANGULAR_60("Rotated Triangular 60°", 60, 1.25, 1.15),
    /** Square (90°) - easy cleaning. */
    SQUARE_90("Square 90°", 90, 1.25, 1.0),
    /** Rotated square (45°) - better heat transfer. */
    SQUARE_45("Rotated Square 45°", 45, 1.25, 1.05);

    private final String description;
    private final int layoutAngle;
    private final double minPitchRatio;
    private final double heatTransferFactor;

    TubePitchPattern(String description, int layoutAngle, double minPitchRatio,
        double heatTransferFactor) {
      this.description = description;
      this.layoutAngle = layoutAngle;
      this.minPitchRatio = minPitchRatio;
      this.heatTransferFactor = heatTransferFactor;
    }

    /**
     * Gets the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets layout angle in degrees.
     *
     * @return layout angle
     */
    public int getLayoutAngle() {
      return layoutAngle;
    }

    /**
     * Gets minimum pitch to tube OD ratio per TEMA.
     *
     * @return minimum pitch ratio
     */
    public double getMinPitchRatio() {
      return minPitchRatio;
    }

    /**
     * Gets heat transfer enhancement factor relative to square.
     *
     * @return heat transfer factor
     */
    public double getHeatTransferFactor() {
      return heatTransferFactor;
    }
  }

  /**
   * Baffle types per TEMA.
   */
  public enum BaffleType {
    /** Single segmental - most common. */
    SINGLE_SEGMENTAL("Single Segmental", 0.75, 1.0),
    /** Double segmental - lower pressure drop. */
    DOUBLE_SEGMENTAL("Double Segmental", 0.5, 0.6),
    /** Triple segmental - very low pressure drop. */
    TRIPLE_SEGMENTAL("Triple Segmental", 0.35, 0.4),
    /** No tubes in window. */
    NO_TUBES_IN_WINDOW("No-Tubes-In-Window", 0.6, 0.5),
    /** Disc and doughnut. */
    DISC_AND_DOUGHNUT("Disc and Doughnut", 0.5, 0.55),
    /** Rod baffles. */
    ROD_BAFFLES("Rod Baffles", 0.2, 0.3);

    private final String description;
    private final double heatTransferFactor;
    private final double pressureDropFactor;

    BaffleType(String description, double heatTransferFactor, double pressureDropFactor) {
      this.description = description;
      this.heatTransferFactor = heatTransferFactor;
      this.pressureDropFactor = pressureDropFactor;
    }

    /**
     * Gets the description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets heat transfer factor relative to single segmental.
     *
     * @return heat transfer factor
     */
    public double getHeatTransferFactor() {
      return heatTransferFactor;
    }

    /**
     * Gets pressure drop factor relative to single segmental.
     *
     * @return pressure drop factor
     */
    public double getPressureDropFactor() {
      return pressureDropFactor;
    }
  }

  // Pre-defined common TEMA configurations
  private static final Map<String, TEMAConfiguration> COMMON_CONFIGURATIONS;

  static {
    Map<String, TEMAConfiguration> configs = new HashMap<String, TEMAConfiguration>();

    // Most common configurations
    configs.put("AES", new TEMAConfiguration(FrontHeadType.A, ShellType.E, RearHeadType.S,
        "Most common type, fully accessible bundle", EnumSet.allOf(TEMAClass.class)));

    configs.put("BEM", new TEMAConfiguration(FrontHeadType.B, ShellType.E, RearHeadType.M,
        "Fixed tubesheet, economical for clean fluids", EnumSet.allOf(TEMAClass.class)));

    configs.put("AEU", new TEMAConfiguration(FrontHeadType.A, ShellType.E, RearHeadType.U,
        "U-tube, excellent thermal expansion handling", EnumSet.allOf(TEMAClass.class)));

    configs.put("AET", new TEMAConfiguration(FrontHeadType.A, ShellType.E, RearHeadType.T,
        "Pull-through floating head, easy bundle removal", EnumSet.allOf(TEMAClass.class)));

    configs.put("AEP", new TEMAConfiguration(FrontHeadType.A, ShellType.E, RearHeadType.P,
        "Outside packed floating head, moderate cost", EnumSet.allOf(TEMAClass.class)));

    configs.put("AKT", new TEMAConfiguration(FrontHeadType.A, ShellType.K, RearHeadType.T,
        "Kettle reboiler, common in distillation", EnumSet.of(TEMAClass.R, TEMAClass.C)));

    configs.put("AJW", new TEMAConfiguration(FrontHeadType.A, ShellType.J, RearHeadType.W,
        "Divided flow condenser, hazardous service", EnumSet.of(TEMAClass.R)));

    configs.put("BEU", new TEMAConfiguration(FrontHeadType.B, ShellType.E, RearHeadType.U,
        "U-tube with bonnet, economical", EnumSet.allOf(TEMAClass.class)));

    configs.put("CFU", new TEMAConfiguration(FrontHeadType.C, ShellType.F, RearHeadType.U,
        "Two-pass U-tube, better approach temp", EnumSet.of(TEMAClass.R, TEMAClass.C)));

    configs.put("NEN", new TEMAConfiguration(FrontHeadType.N, ShellType.E, RearHeadType.N,
        "High pressure fixed tubesheet", EnumSet.of(TEMAClass.R)));

    COMMON_CONFIGURATIONS = Collections.unmodifiableMap(configs);
  }

  /**
   * Represents a complete TEMA configuration.
   */
  public static class TEMAConfiguration {
    private final FrontHeadType frontHead;
    private final ShellType shell;
    private final RearHeadType rearHead;
    private final String description;
    private final Set<TEMAClass> applicableClasses;

    /**
     * Creates a new TEMA configuration.
     *
     * @param frontHead front head type
     * @param shell shell type
     * @param rearHead rear head type
     * @param description configuration description
     * @param applicableClasses applicable TEMA classes
     */
    public TEMAConfiguration(FrontHeadType frontHead, ShellType shell, RearHeadType rearHead,
        String description, Set<TEMAClass> applicableClasses) {
      this.frontHead = frontHead;
      this.shell = shell;
      this.rearHead = rearHead;
      this.description = description;
      this.applicableClasses = EnumSet.copyOf(applicableClasses);
    }

    /**
     * Gets the TEMA designation string.
     *
     * @return TEMA designation (e.g., "AES")
     */
    public String getDesignation() {
      return frontHead.name() + shell.name() + rearHead.name();
    }

    /**
     * Gets the front head type.
     *
     * @return front head type
     */
    public FrontHeadType getFrontHead() {
      return frontHead;
    }

    /**
     * Gets the shell type.
     *
     * @return shell type
     */
    public ShellType getShell() {
      return shell;
    }

    /**
     * Gets the rear head type.
     *
     * @return rear head type
     */
    public RearHeadType getRearHead() {
      return rearHead;
    }

    /**
     * Gets the configuration description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets applicable TEMA classes.
     *
     * @return set of applicable TEMA classes
     */
    public Set<TEMAClass> getApplicableClasses() {
      return Collections.unmodifiableSet(applicableClasses);
    }

    /**
     * Checks if bundle is removable.
     *
     * @return true if bundle can be removed
     */
    public boolean isBundleRemovable() {
      return rearHead.isFloating() || rearHead == RearHeadType.U;
    }

    /**
     * Checks if this configuration handles thermal expansion well.
     *
     * @return true if good thermal expansion handling
     */
    public boolean hasGoodThermalExpansion() {
      return rearHead.isFloating() || rearHead == RearHeadType.U;
    }

    /**
     * Gets relative cost factor.
     *
     * @return cost factor (1.0 = baseline)
     */
    public double getCostFactor() {
      double factor = 1.0;

      // Front head cost
      if (frontHead == FrontHeadType.D) {
        factor += 0.4;
      } else if (frontHead == FrontHeadType.A) {
        factor += 0.15;
      } else if (frontHead == FrontHeadType.C || frontHead == FrontHeadType.N) {
        factor += 0.1;
      }

      // Shell cost
      if (shell == ShellType.K) {
        factor += 0.3;
      } else if (shell == ShellType.F) {
        factor += 0.2;
      }

      // Rear head cost
      if (rearHead == RearHeadType.T) {
        factor += 0.25;
      } else if (rearHead == RearHeadType.S) {
        factor += 0.2;
      } else if (rearHead == RearHeadType.P || rearHead == RearHeadType.W) {
        factor += 0.15;
      }

      return factor;
    }
  }

  /**
   * Gets a common TEMA configuration by designation.
   *
   * @param designation TEMA designation (e.g., "AES")
   * @return configuration or null if not found
   */
  public static TEMAConfiguration getConfiguration(String designation) {
    return COMMON_CONFIGURATIONS.get(designation.toUpperCase());
  }

  /**
   * Gets all common TEMA configurations.
   *
   * @return map of designations to configurations
   */
  public static Map<String, TEMAConfiguration> getCommonConfigurations() {
    return COMMON_CONFIGURATIONS;
  }

  /**
   * Creates a custom TEMA configuration.
   *
   * @param frontHead front head type letter (A, B, C, N, D)
   * @param shell shell type letter (E, F, G, H, J, K, X)
   * @param rearHead rear head type letter (L, M, N, P, S, T, U, W)
   * @return new configuration
   * @throws IllegalArgumentException if any letter is invalid
   */
  public static TEMAConfiguration createConfiguration(char frontHead, char shell, char rearHead) {
    FrontHeadType front = FrontHeadType.valueOf(String.valueOf(frontHead));
    ShellType shellType = ShellType.valueOf(String.valueOf(shell));
    RearHeadType rear = RearHeadType.valueOf(String.valueOf(rearHead));

    return new TEMAConfiguration(front, shellType, rear, "Custom configuration",
        EnumSet.allOf(TEMAClass.class));
  }

  /**
   * Recommends TEMA configuration based on application.
   *
   * @param needsCleanable true if shell side needs mechanical cleaning
   * @param hasLargeTemperatureDifference true if large temperature differential
   * @param isHighPressure true if high-pressure application
   * @param isHazardous true if hazardous fluid involved
   * @return recommended TEMA designation
   */
  public static String recommendConfiguration(boolean needsCleanable,
      boolean hasLargeTemperatureDifference, boolean isHighPressure, boolean isHazardous) {

    if (isHazardous) {
      if (hasLargeTemperatureDifference) {
        return "AEW"; // Sealed floating head
      }
      return "BEM"; // Fixed tubesheet, fully sealed
    }

    if (isHighPressure) {
      if (hasLargeTemperatureDifference) {
        return "NEU"; // U-tube with high-pressure heads
      }
      return "NEN"; // Fixed tubesheet, high-pressure
    }

    if (hasLargeTemperatureDifference) {
      if (needsCleanable) {
        return "AES"; // Floating head, cleanable
      }
      return "AEU"; // U-tube
    }

    if (needsCleanable) {
      return "AES"; // Most versatile
    }

    return "BEM"; // Economical fixed tubesheet
  }

  /**
   * Calculates minimum tube pitch per TEMA standards.
   *
   * @param tubeOD tube outer diameter in mm
   * @param pattern tube pitch pattern
   * @return minimum tube pitch in mm
   */
  public static double calculateMinTubePitch(double tubeOD, TubePitchPattern pattern) {
    return tubeOD * pattern.getMinPitchRatio();
  }

  /**
   * Calculates estimated tube count for given shell diameter.
   *
   * @param shellID shell inside diameter in mm
   * @param tubeOD tube outer diameter in mm
   * @param tubePitch tube pitch in mm
   * @param pattern tube layout pattern
   * @param tubePasses number of tube passes
   * @return estimated tube count
   */
  public static int estimateTubeCount(double shellID, double tubeOD, double tubePitch,
      TubePitchPattern pattern, int tubePasses) {

    // Effective shell area (accounting for pass lanes and bundle clearance)
    double effectiveDiameter = shellID - 2.0 * tubeOD; // Bundle clearance
    double passLaneFactor = 1.0 - 0.05 * (tubePasses - 1); // Pass lane reduction
    double effectiveArea = Math.PI * Math.pow(effectiveDiameter / 2.0, 2) * passLaneFactor;

    // Tube count based on pattern
    double areaPerTube;
    int layoutAngle = pattern.getLayoutAngle();
    if (layoutAngle == 30 || layoutAngle == 60) {
      // Triangular
      areaPerTube = tubePitch * tubePitch * Math.sqrt(3.0) / 2.0;
    } else {
      // Square
      areaPerTube = tubePitch * tubePitch;
    }

    return (int) (effectiveArea / areaPerTube);
  }

  /**
   * Gets maximum allowable unsupported tube span per TEMA.
   *
   * @param tubeOD tube outer diameter in mm
   * @param tubeMaterial tube material (CARBON_STEEL, STAINLESS, etc.)
   * @return maximum unsupported span in mm
   */
  public static double getMaxUnsupportedSpan(double tubeOD, String tubeMaterial) {
    // TEMA Table R-4.52 / C-4.52 / B-4.52 (simplified)
    double baseSpan;

    if (tubeOD <= 12.7) {
      baseSpan = 660; // 26"
    } else if (tubeOD <= 19.05) {
      baseSpan = 889; // 35"
    } else if (tubeOD <= 25.4) {
      baseSpan = 1067; // 42"
    } else {
      baseSpan = 1321; // 52"
    }

    // Material factor
    double materialFactor = 1.0;
    if (tubeMaterial != null) {
      String upper = tubeMaterial.toUpperCase();
      if (upper.contains("STAINLESS") || upper.contains("SS")) {
        materialFactor = 0.95;
      } else if (upper.contains("COPPER") || upper.contains("CU")) {
        materialFactor = 0.75;
      } else if (upper.contains("TITANIUM") || upper.contains("TI")) {
        materialFactor = 0.90;
      }
    }

    return baseSpan * materialFactor;
  }

  /**
   * Calculates minimum baffle spacing per TEMA.
   *
   * @param shellID shell inside diameter in mm
   * @param temaClass TEMA class
   * @return minimum baffle spacing in mm
   */
  public static double getMinBaffleSpacing(double shellID, TEMAClass temaClass) {
    // TEMA minimum is 1/5 shell diameter or 2" (50.8mm), whichever is greater
    double minByDiameter = shellID / 5.0;
    double absoluteMin = 50.8; // 2 inches

    if (temaClass == TEMAClass.B) {
      absoluteMin = 38.1; // 1.5 inches for Class B
    }

    return Math.max(minByDiameter, absoluteMin);
  }

  /**
   * Gets maximum baffle spacing per TEMA.
   *
   * @param shellID shell inside diameter in mm
   * @return maximum baffle spacing in mm
   */
  public static double getMaxBaffleSpacing(double shellID) {
    // TEMA maximum is shell diameter
    return shellID;
  }

  private TEMAStandard() {
    // Private constructor - utility class
  }
}
