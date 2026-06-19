package neqsim.process.processmodel.dexpi;

import java.util.Locale;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Classifies a process stream into a P&amp;ID service category and supplies the corresponding line-style attributes
 * (line weight, line type and colour) used when drawing the connecting pipe.
 *
 * <p>
 * The classification and styling follow widely used process-industry drawing conventions:
 * </p>
 *
 * <table>
 * <caption>Service categories, line styles and governing conventions</caption>
 * <tr>
 * <th>Service</th>
 * <th>Line weight (ISO 15519-1 hierarchy)</th>
 * <th>Line type (ISO 10628-2)</th>
 * </tr>
 * <tr>
 * <td>Main process</td>
 * <td>Heaviest</td>
 * <td>Solid</td>
 * </tr>
 * <tr>
 * <td>Secondary process</td>
 * <td>Medium</td>
 * <td>Solid</td>
 * </tr>
 * <tr>
 * <td>Utility (steam, cooling water, air, nitrogen)</td>
 * <td>Light</td>
 * <td>Dashed</td>
 * </tr>
 * <tr>
 * <td>Flare / vent / relief</td>
 * <td>Medium</td>
 * <td>Dash-dot</td>
 * </tr>
 * <tr>
 * <td>Drain / sewer</td>
 * <td>Light</td>
 * <td>Dotted</td>
 * </tr>
 * <tr>
 * <td>Fuel gas</td>
 * <td>Medium</td>
 * <td>Dashed</td>
 * </tr>
 * </table>
 *
 * <p>
 * The DEXPI {@code LineType} integer codes used here are: 0 = solid, 1 = dashed, 2 = dotted, 3 = dash-dot.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class DexpiServiceClassifier {

  /** ISO 15519-1 heaviest line weight (main process lines). */
  static final double WEIGHT_MAIN_PROCESS = 0.6;
  /** ISO 15519-1 medium line weight (secondary process, flare, fuel). */
  static final double WEIGHT_SECONDARY = 0.35;
  /** ISO 15519-1 lightest line weight (utilities, drains). */
  static final double WEIGHT_UTILITY = 0.18;

  /** Private constructor — utility class. */
  private DexpiServiceClassifier() {
  }

  /**
   * P&amp;ID service categories with their associated drawing style.
   */
  enum ServiceType {
    /** Primary process flow (largest weight, solid). */
    MAIN_PROCESS(WEIGHT_MAIN_PROCESS, 0, "0.501960784", "0.501960784", "0"),
    /** Secondary process flow (medium weight, solid). */
    SECONDARY_PROCESS(WEIGHT_SECONDARY, 0, "0.501960784", "0.501960784", "0"),
    /** Steam / cooling water / instrument air / nitrogen (light, dashed). */
    UTILITY(WEIGHT_UTILITY, 1, "0.4", "0.4", "0.4"),
    /** Flare / vent / relief headers (medium, dash-dot, red). */
    FLARE(WEIGHT_SECONDARY, 3, "0.7", "0", "0"),
    /** Open / closed drain and sewer (light, dotted). */
    DRAIN(WEIGHT_UTILITY, 2, "0.4", "0.4", "0.4"),
    /** Fuel gas (medium, dashed, orange). */
    FUEL_GAS(WEIGHT_SECONDARY, 1, "0.8", "0.4", "0");

    /** ISO 15519-1 line weight in mm. */
    private final double lineWeight;
    /** DEXPI line type code (0 solid, 1 dashed, 2 dotted, 3 dash-dot). */
    private final int lineType;
    /** Presentation colour red component (0-1). */
    private final String colorR;
    /** Presentation colour green component (0-1). */
    private final String colorG;
    /** Presentation colour blue component (0-1). */
    private final String colorB;

    /**
     * Creates a service type.
     *
     * @param lineWeight ISO 15519-1 line weight in mm
     * @param lineType   DEXPI line type code
     * @param colorR     presentation red component (0-1)
     * @param colorG     presentation green component (0-1)
     * @param colorB     presentation blue component (0-1)
     */
    ServiceType(double lineWeight, int lineType, String colorR, String colorG, String colorB) {
      this.lineWeight = lineWeight;
      this.lineType = lineType;
      this.colorR = colorR;
      this.colorG = colorG;
      this.colorB = colorB;
    }

    /**
     * Returns the ISO 15519-1 line weight for this service.
     *
     * @return line weight in mm
     */
    double getLineWeight() {
      return lineWeight;
    }

    /**
     * Returns the DEXPI line-type code for this service.
     *
     * @return 0 solid, 1 dashed, 2 dotted, 3 dash-dot
     */
    int getLineType() {
      return lineType;
    }

    /**
     * Returns the presentation red component.
     *
     * @return red component (0-1) as a string
     */
    String getColorR() {
      return colorR;
    }

    /**
     * Returns the presentation green component.
     *
     * @return green component (0-1) as a string
     */
    String getColorG() {
      return colorG;
    }

    /**
     * Returns the presentation blue component.
     *
     * @return blue component (0-1) as a string
     */
    String getColorB() {
      return colorB;
    }

    /**
     * Returns the NORSOK Z-003 / PIP fluid (service) code for this service category.
     *
     * <p>
     * The codes follow common process-industry usage: {@code PG} process gas/general process, {@code FL} flare/relief,
     * {@code DR} drains, {@code FG} fuel gas, {@code UT} utility and {@code PL} secondary process liquid. The code is
     * used when composing a full line-identification number.
     * </p>
     *
     * @return the two-letter fluid code (never null)
     */
    String getFluidCode() {
      switch (this) {
      case FLARE:
	return "FL";
      case DRAIN:
	return "DR";
      case FUEL_GAS:
	return "FG";
      case UTILITY:
	return "UT";
      case SECONDARY_PROCESS:
	return "PL";
      case MAIN_PROCESS:
      default:
	return "PG";
      }
    }
  }

  /**
   * Classifies a stream by its name into a service category.
   *
   * <p>
   * Name-based classification keys off common process-industry naming such as "flare", "vent", "relief", "drain",
   * "fuel", "steam", "cooling water", "instrument air" and "nitrogen". Anything not matching a utility or special
   * header is treated as a process stream.
   * </p>
   *
   * @param name the stream name (may be null)
   * @return the classified service type (never null; defaults to {@link ServiceType#MAIN_PROCESS})
   */
  static ServiceType classifyByName(String name) {
    if (name == null) {
      return ServiceType.MAIN_PROCESS;
    }
    String n = name.toLowerCase(Locale.ROOT);
    if (n.contains("flare") || n.contains("vent") || n.contains("relief") || n.contains("blowdown")) {
      return ServiceType.FLARE;
    }
    if (n.contains("drain") || n.contains("sewer") || n.contains("sump")) {
      return ServiceType.DRAIN;
    }
    if (n.contains("fuel")) {
      return ServiceType.FUEL_GAS;
    }
    if (n.contains("steam") || n.contains("cooling water") || n.contains("cw ") || n.contains("instrument air")
	|| n.contains("utility") || n.contains("nitrogen") || n.contains(" n2") || n.startsWith("n2")
	|| n.contains("glycol") || n.contains("seal gas") || n.contains("flush")) {
      return ServiceType.UTILITY;
    }
    return ServiceType.MAIN_PROCESS;
  }

  /**
   * Classifies a stream using its name and, when available, its fluid contents and flow rate.
   *
   * <p>
   * Name keywords take precedence. When the name is non-committal, a small flow relative to the supplied main-process
   * reference flow demotes the stream to {@link ServiceType#SECONDARY_PROCESS} so the line-weight hierarchy of ISO
   * 15519-1 is respected.
   * </p>
   *
   * @param stream              the process stream (may be null)
   * @param mainProcessFlowKgHr the reference main-process flow in kg/hr used to scale weight, or a non-positive value
   *                            to skip the relative-weight demotion
   * @return the classified service type (never null)
   */
  static ServiceType classify(StreamInterface stream, double mainProcessFlowKgHr) {
    if (stream == null) {
      return ServiceType.MAIN_PROCESS;
    }
    ServiceType byName = classifyByName(stream.getName());
    if (byName != ServiceType.MAIN_PROCESS) {
      return byName;
    }
    // Pure-water streams from a separator water outlet are drawn as a process line but with the
    // secondary weight, since they are rarely the main hydrocarbon line.
    SystemInterface fluid = null;
    try {
      fluid = stream.getFluid();
    } catch (RuntimeException ex) {
      fluid = null;
    }
    if (fluid != null && isPredominantlyWater(fluid)) {
      return ServiceType.SECONDARY_PROCESS;
    }
    if (mainProcessFlowKgHr > 0.0) {
      double flow = 0.0;
      try {
	flow = stream.getFlowRate("kg/hr");
      } catch (RuntimeException ex) {
	flow = 0.0;
      }
      if (flow > 0.0 && flow < 0.2 * mainProcessFlowKgHr) {
	return ServiceType.SECONDARY_PROCESS;
      }
    }
    return ServiceType.MAIN_PROCESS;
  }

  /**
   * Tests whether a fluid is predominantly water by mole fraction.
   *
   * @param fluid the thermodynamic system (must not be null)
   * @return true if water is present and exceeds 50 mol%
   */
  private static boolean isPredominantlyWater(SystemInterface fluid) {
    try {
      if (!fluid.getPhase(0).hasComponent("water")) {
	return false;
      }
      double z = fluid.getPhase(0).getComponent("water").getz();
      return z > 0.5;
    } catch (RuntimeException ex) {
      return false;
    }
  }
}
