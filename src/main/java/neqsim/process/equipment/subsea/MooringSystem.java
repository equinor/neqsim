package neqsim.process.equipment.subsea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Mooring system design for floating offshore structures.
 *
 * <p>
 * Calculates catenary mooring line geometry, tensions, anchor loads, and provides concept-level
 * mooring design per applicable standards. Supports single-line and multi-line configurations with
 * chain, wire rope, and polyester rope segments.
 * </p>
 *
 * <h2>Key Calculations</h2>
 * <ul>
 * <li>Catenary geometry (line profile, touchdown point)</li>
 * <li>Line tensions (fairlead, anchor, maximum)</li>
 * <li>Anchor holding capacity requirements</li>
 * <li>Line weight and length estimation</li>
 * <li>Offset and restoring force characteristics</li>
 * <li>Breaking strength and safety factor checks</li>
 * </ul>
 *
 * <h2>Line Types</h2>
 * <ul>
 * <li><b>Chain</b>: R3, R4, R5 studless chain per DNV-OS-E302</li>
 * <li><b>Wire rope</b>: Six-strand or spiral strand</li>
 * <li><b>Polyester rope</b>: Lightweight synthetic for deep water</li>
 * <li><b>Chain-wire-chain</b>: Combined catenary for deep water</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>DNV-OS-E301 — Position mooring</li>
 * <li>DNV-OS-E302 — Offshore mooring chain</li>
 * <li>API RP 2SK — Design and analysis of stationkeeping systems</li>
 * <li>ABS Guide for Position Mooring Systems</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * MooringSystem ms = new MooringSystem("Mooring Alpha");
 * ms.setWaterDepth(265.0);          // m
 * ms.setNumberOfLines(3);
 * ms.setLineType(MooringSystem.LineType.CHAIN_POLYESTER_CHAIN);
 * ms.setChainDiameter(0.127);       // m (127 mm)
 * ms.setDesignHorizontalForce(2500.0); // kN (per line)
 * ms.setDesignVerticalForce(500.0);    // kN
 * ms.setAnchorRadius(800.0);        // m
 * ms.run();
 * double tension = ms.getFairleadTension();  // kN
 * double safetyFactor = ms.getBreakingStrengthSafetyFactor();
 * }</pre>
 *
 * @author esol
 * @version 1.0
 */
public class MooringSystem extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Mooring line material types.
   */
  public enum LineType {
    /** Studless R4 chain. */
    CHAIN("Chain"),
    /** Six-strand wire rope. */
    WIRE_ROPE("Wire Rope"),
    /** Polyester synthetic rope. */
    POLYESTER("Polyester"),
    /** Chain-polyester-chain (deep water). */
    CHAIN_POLYESTER_CHAIN("Chain-Polyester-Chain");

    private final String displayName;

    LineType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name string
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Anchor types.
   */
  public enum AnchorType {
    /** Drag embedment anchor. */
    DRAG_EMBEDMENT("Drag Embedment"),
    /** Suction pile anchor. */
    SUCTION_PILE("Suction Pile"),
    /** Driven pile anchor. */
    DRIVEN_PILE("Driven Pile"),
    /** Gravity anchor (concrete/steel). */
    GRAVITY("Gravity");

    private final String displayName;

    AnchorType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name string
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  // --- Input parameters ---

  /** Water depth [m]. */
  private double waterDepth = 265.0;

  /** Number of mooring lines. */
  private int numberOfLines = 3;

  /** Line type. */
  private LineType lineType = LineType.CHAIN_POLYESTER_CHAIN;

  /** Anchor type. */
  private AnchorType anchorType = AnchorType.SUCTION_PILE;

  /** Chain nominal diameter [m]. */
  private double chainDiameter = 0.127; // 127 mm

  /** Chain grade (R3=3, R4=4, R5=5). */
  private int chainGrade = 4;

  /** Polyester rope diameter [m] (for hybrid lines). */
  private double polyesterDiameter = 0.200; // 200mm

  /** Wire rope diameter [m]. */
  private double wireRopeDiameter = 0.127;

  /** Design horizontal force per line at fairlead [kN]. */
  private double designHorizontalForce = 2500.0;

  /** Design vertical force per line at fairlead [kN]. */
  private double designVerticalForce = 500.0;

  /** Anchor radius (horizontal distance fairlead to anchor) [m]. */
  private double anchorRadius = 800.0;

  /** Fairlead depth below waterline [m]. */
  private double fairleadDepth = 15.0;

  /** Seabed soil type for anchor design. */
  private String soilType = "soft clay";

  /** Required safety factor per DNV-OS-E301 (ULS). */
  private double requiredSafetyFactor = 1.80;

  /** Seawater density [kg/m3]. */
  private double seawaterDensity = 1025.0;

  // --- Computed results ---

  /** Total line length per line [m]. */
  private double lineLength = 0.0;

  /** Chain segment length (at seabed end) [m]. */
  private double bottomChainLength = 0.0;

  /** Polyester / wire segment length [m]. */
  private double middleSegmentLength = 0.0;

  /** Top chain segment length [m]. */
  private double topChainLength = 0.0;

  /** Fairlead tension (resultant) per line [kN]. */
  private double fairleadTension = 0.0;

  /** Anchor tension (horizontal at seabed) per line [kN]. */
  private double anchorTension = 0.0;

  /** Maximum line tension [kN]. */
  private double maxLineTension = 0.0;

  /** Line weight in water per unit length [kN/m]. */
  private double lineWeightPerMeter = 0.0;

  /** Minimum breaking load of critical section [kN]. */
  private double minimumBreakingLoad = 0.0;

  /** Safety factor (MBL / max tension). */
  private double safetyFactor = 0.0;

  /** Touchdown length (line lying on seabed) [m]. */
  private double touchdownLength = 0.0;

  /** Catenary angle at fairlead [deg]. */
  private double fairleadAngle = 0.0;

  /** Total mooring system steel weight [tonnes]. */
  private double totalWeight = 0.0;

  /** Estimated mooring system cost [MNOK]. */
  private double estimatedCost = 0.0;

  /** Anchor holding capacity required [kN]. */
  private double requiredAnchorCapacity = 0.0;

  /** Restoring force coefficient (horizontal stiffness) [kN/m]. */
  private double restoringStiffness = 0.0;

  /** Maximum vessel offset [m] (at design load). */
  private double maxOffset = 0.0;

  /**
   * Default constructor.
   */
  public MooringSystem() {
    this("MooringSystem");
  }

  /**
   * Construct with name.
   *
   * @param name equipment name
   */
  public MooringSystem(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    calculateLineProperties();
    calculateCatenary();
    calculateBreakingStrength();
    calculateAnchorRequirements();
    calculateRestoringForce();
    estimateCost();
    setCalculationIdentifier(id);
  }

  /**
   * Calculate line weight and segment lengths based on line type.
   */
  private void calculateLineProperties() {
    double effectiveDepth = waterDepth - fairleadDepth;

    switch (lineType) {
      case CHAIN:
        // R4 studless chain: weight ~ 21.9 * d^2 kg/m (d in mm)
        double dMm = chainDiameter * 1000.0;
        lineWeightPerMeter = getChainWeightPerMeter(dMm);
        bottomChainLength = 0;
        middleSegmentLength = 0;
        topChainLength = 0;
        // Entire line is chain for pure chain mooring
        break;

      case WIRE_ROPE:
        double dWireMm = wireRopeDiameter * 1000.0;
        lineWeightPerMeter = 0.034 * dWireMm * dWireMm / 1000.0; // kN/m approx
        break;

      case POLYESTER:
        double dPolyMm = polyesterDiameter * 1000.0;
        lineWeightPerMeter = 0.005 * dPolyMm * dPolyMm / 1000.0; // kN/m approx (nearly neutral)
        break;

      case CHAIN_POLYESTER_CHAIN:
        // Bottom chain + polyester middle + top chain
        double dChainMm = chainDiameter * 1000.0;
        double chainWeight = getChainWeightPerMeter(dChainMm);

        // Bottom chain: ~100-200m on seabed for catenary
        bottomChainLength = 200.0;
        // Top chain: depth + some catenary
        topChainLength = fairleadDepth + 50.0;
        // Middle: polyester covering most of the depth
        middleSegmentLength = effectiveDepth * 1.05; // 5% margin
        lineWeightPerMeter = chainWeight; // Critical section is chain
        break;

      default:
        lineWeightPerMeter = 0.1;
    }
  }

  /**
   * Calculate chain weight in water per meter [kN/m].
   *
   * @param diameterMm chain diameter in mm
   * @return submerged weight per meter in kN/m
   */
  private double getChainWeightPerMeter(double diameterMm) {
    // R4 studless: W_air ~ 21.9 * d^2 kg/m, submerged ~ 0.87 * W_air
    double wAir = 21.9 * diameterMm * diameterMm / 1.0e6; // tonnes/m
    double wSubmerged = wAir * 0.87; // buoyancy reduction
    return wSubmerged * 9.81; // kN/m
  }

  /**
   * Calculate catenary geometry and tensions.
   */
  private void calculateCatenary() {
    double effectiveDepth = waterDepth - fairleadDepth;

    // Fairlead tension from components
    fairleadTension =
        Math.sqrt(designHorizontalForce * designHorizontalForce
            + designVerticalForce * designVerticalForce);
    fairleadAngle =
        Math.toDegrees(Math.atan2(designVerticalForce, designHorizontalForce));

    if (lineType == LineType.CHAIN || lineType == LineType.WIRE_ROPE) {
      // Classic catenary equations
      // Horizontal tension H = T * cos(alpha)
      // w = weight per unit length
      // y = (H/w) * (cosh(w*x/H) - 1)
      // At fairlead: depth = (H/w) * (cosh(w*L/H) - 1) where L is horizontal dist

      double h = designHorizontalForce; // kN (horizontal component)
      double w = lineWeightPerMeter; // kN/m

      if (w > 0 && h > 0) {
        // Catenary parameter a = H/w
        double a = h / w;

        // Suspended line length: s = a * sinh(x_h / a)
        // where x_h is horizontal distance of suspended line
        // From depth: d = a * (cosh(x_h/a) - 1)
        // Solve for x_h: x_h = a * acosh(d/a + 1)

        double coshArg = effectiveDepth / a + 1.0;
        double xSuspended = a * Math.log(coshArg + Math.sqrt(coshArg * coshArg - 1.0));

        // Suspended line length
        double sSuspended = a * Math.sinh(xSuspended / a);

        // Touchdown length (line on seabed)
        touchdownLength = Math.max(0, anchorRadius - xSuspended);

        lineLength = sSuspended + touchdownLength;
        maxLineTension = fairleadTension; // Max at fairlead for catenary

        // Anchor tension (horizontal only if touchdown > 0)
        if (touchdownLength > 0) {
          anchorTension = h; // Pure horizontal at anchor
        } else {
          anchorTension = fairleadTension; // Taut mooring
        }
      }
    } else if (lineType == LineType.CHAIN_POLYESTER_CHAIN) {
      // Hybrid line: chain catenary at bottom, taut polyester in middle
      double h = designHorizontalForce;
      double dChainMm = chainDiameter * 1000.0;
      double chainW = getChainWeightPerMeter(dChainMm);

      // Bottom chain catenary
      double aBottom = h / chainW;
      double bottomCatenaryDepth = 20.0; // m of depth taken by bottom catenary
      double coshArgB = bottomCatenaryDepth / aBottom + 1.0;
      double xBottom = aBottom * Math.log(coshArgB + Math.sqrt(coshArgB * coshArgB - 1.0));
      double sBottom = aBottom * Math.sinh(xBottom / aBottom);

      touchdownLength = Math.max(0, bottomChainLength - sBottom);

      // Middle polyester: nearly vertical (small catenary sag)
      double polyDepth = effectiveDepth - bottomCatenaryDepth;
      double polyLength = polyDepth * 1.02; // 2% stretch

      // Top chain
      double topLength = topChainLength;

      lineLength = sBottom + touchdownLength + polyLength + topLength;
      middleSegmentLength = polyLength;

      maxLineTension = fairleadTension;
      anchorTension = h;
    } else {
      // Polyester taut leg
      lineLength = Math.sqrt(effectiveDepth * effectiveDepth + anchorRadius * anchorRadius);
      maxLineTension = fairleadTension;
      anchorTension = fairleadTension;
      touchdownLength = 0;
    }

    // Total system weight
    double dChainMm = chainDiameter * 1000.0;
    double chainWeightAir = 21.9 * dChainMm * dChainMm / 1.0e6; // tonnes/m
    totalWeight = lineLength * chainWeightAir * numberOfLines;
    if (lineType == LineType.CHAIN_POLYESTER_CHAIN) {
      // Polyester is much lighter
      double polyWeightPerM = 0.02; // tonnes/m approx
      totalWeight = (bottomChainLength + topChainLength) * chainWeightAir * numberOfLines
          + middleSegmentLength * polyWeightPerM * numberOfLines;
    }
  }

  /**
   * Calculate minimum breaking load and safety factor.
   */
  private void calculateBreakingStrength() {
    double dMm = chainDiameter * 1000.0;

    switch (lineType) {
      case CHAIN:
      case CHAIN_POLYESTER_CHAIN:
        // MBL for R4 studless chain: MBL = 0.0274 * d^2 * (44 - 0.08*d) kN (d in mm)
        minimumBreakingLoad = 0.0274 * dMm * dMm * (44.0 - 0.08 * dMm);
        if (chainGrade == 3) {
          minimumBreakingLoad *= 0.85;
        } else if (chainGrade == 5) {
          minimumBreakingLoad *= 1.15;
        }
        break;
      case WIRE_ROPE:
        double dWireMm = wireRopeDiameter * 1000.0;
        minimumBreakingLoad = 0.5 * dWireMm * dWireMm; // kN approx for 6-strand
        break;
      case POLYESTER:
        double dPolyMm = polyesterDiameter * 1000.0;
        minimumBreakingLoad = 0.12 * dPolyMm * dPolyMm; // kN approx
        break;
      default:
        minimumBreakingLoad = 10000.0;
    }

    safetyFactor = maxLineTension > 0 ? minimumBreakingLoad / maxLineTension : 0.0;
  }

  /**
   * Calculate anchor holding capacity requirements.
   */
  private void calculateAnchorRequirements() {
    // Required anchor capacity with safety factor
    double anchorDesignFactor = 1.5; // DNV-OS-E301
    requiredAnchorCapacity = anchorTension * anchorDesignFactor;

    // For suction piles: account for vertical component if taut mooring
    if (anchorType == AnchorType.SUCTION_PILE && touchdownLength <= 0) {
      double verticalAtAnchor = designVerticalForce;
      requiredAnchorCapacity = Math.sqrt(anchorTension * anchorTension
          + verticalAtAnchor * verticalAtAnchor) * anchorDesignFactor;
    }
  }

  /**
   * Calculate restoring force and maximum offset.
   */
  private void calculateRestoringForce() {
    // Simplified restoring stiffness for catenary mooring
    // k ~ n * w * (T_h / (T_h + w*h))^2 where h is depth
    double h = designHorizontalForce;
    double w = lineWeightPerMeter;
    double depth = waterDepth - fairleadDepth;

    if (w > 0 && h > 0) {
      double denominatorTerm = h + w * depth;
      restoringStiffness =
          numberOfLines * w * (h / denominatorTerm) * (h / denominatorTerm) * 1.5;
    }

    if (restoringStiffness > 0) {
      // Total horizontal design force from all lines
      double totalHForce = designHorizontalForce * numberOfLines;
      maxOffset = totalHForce / restoringStiffness;
    }
  }

  /**
   * Estimate mooring system cost.
   */
  private void estimateCost() {
    // Chain cost: ~2000-4000 NOK/m for heavy offshore chain
    double dMm = chainDiameter * 1000.0;
    double chainCostPerM = 0.002 * dMm * dMm + 500.0; // NOK/m simplified

    double lineCost = 0;
    switch (lineType) {
      case CHAIN:
        lineCost = lineLength * chainCostPerM * numberOfLines / 1.0e6; // MNOK
        break;
      case CHAIN_POLYESTER_CHAIN:
        double polyesterCostPerM = 800.0; // NOK/m
        lineCost = ((bottomChainLength + topChainLength) * chainCostPerM
            + middleSegmentLength * polyesterCostPerM) * numberOfLines / 1.0e6;
        break;
      case WIRE_ROPE:
        double wireCostPerM = 1500.0;
        lineCost = lineLength * wireCostPerM * numberOfLines / 1.0e6;
        break;
      case POLYESTER:
        double polyCostPerM = 600.0;
        lineCost = lineLength * polyCostPerM * numberOfLines / 1.0e6;
        break;
      default:
        lineCost = 10.0;
    }

    // Anchor cost
    double anchorCostPerUnit;
    switch (anchorType) {
      case DRAG_EMBEDMENT:
        anchorCostPerUnit = 2.0; // MNOK
        break;
      case SUCTION_PILE:
        anchorCostPerUnit = 5.0; // MNOK
        break;
      case DRIVEN_PILE:
        anchorCostPerUnit = 8.0; // MNOK
        break;
      case GRAVITY:
        anchorCostPerUnit = 3.0; // MNOK
        break;
      default:
        anchorCostPerUnit = 5.0;
    }
    double anchorCost = anchorCostPerUnit * numberOfLines;

    // Installation cost (marine operations)
    double installCost = numberOfLines * 3.0; // ~3 MNOK per line installation

    estimatedCost = lineCost + anchorCost + installCost;
  }

  // ---- Result getters ----

  /**
   * Get total line length per line [m].
   *
   * @return line length in metres
   */
  public double getLineLength() {
    return lineLength;
  }

  /**
   * Get fairlead tension per line [kN].
   *
   * @return fairlead tension in kN
   */
  public double getFairleadTension() {
    return fairleadTension;
  }

  /**
   * Get anchor tension per line [kN].
   *
   * @return anchor tension in kN
   */
  public double getAnchorTension() {
    return anchorTension;
  }

  /**
   * Get maximum line tension [kN].
   *
   * @return max tension in kN
   */
  public double getMaxLineTension() {
    return maxLineTension;
  }

  /**
   * Get minimum breaking load [kN].
   *
   * @return MBL in kN
   */
  public double getMinimumBreakingLoad() {
    return minimumBreakingLoad;
  }

  /**
   * Get breaking strength safety factor.
   *
   * @return safety factor (MBL / max tension)
   */
  public double getBreakingStrengthSafetyFactor() {
    return safetyFactor;
  }

  /**
   * Check if the safety factor meets the required minimum per DNV-OS-E301.
   *
   * @return true if safety factor meets requirement
   */
  public boolean isSafetyFactorAdequate() {
    return safetyFactor >= requiredSafetyFactor;
  }

  /**
   * Get touchdown length [m] (line lying on seabed).
   *
   * @return touchdown length in metres
   */
  public double getTouchdownLength() {
    return touchdownLength;
  }

  /**
   * Get fairlead angle [deg].
   *
   * @return fairlead angle in degrees
   */
  public double getFairleadAngle() {
    return fairleadAngle;
  }

  /**
   * Get total mooring system weight [tonnes].
   *
   * @return total weight in tonnes
   */
  public double getTotalWeight() {
    return totalWeight;
  }

  /**
   * Get estimated mooring system cost [MNOK].
   *
   * @return cost in MNOK
   */
  public double getEstimatedCost() {
    return estimatedCost;
  }

  /**
   * Get required anchor holding capacity [kN].
   *
   * @return required anchor capacity in kN
   */
  public double getRequiredAnchorCapacity() {
    return requiredAnchorCapacity;
  }

  /**
   * Get horizontal restoring stiffness [kN/m].
   *
   * @return restoring stiffness in kN/m
   */
  public double getRestoringStiffness() {
    return restoringStiffness;
  }

  /**
   * Get maximum vessel offset at design load [m].
   *
   * @return maximum offset in metres
   */
  public double getMaxOffset() {
    return maxOffset;
  }

  /**
   * Get line weight in water per unit length [kN/m].
   *
   * @return line weight per meter in kN/m
   */
  public double getLineWeightPerMeter() {
    return lineWeightPerMeter;
  }

  /**
   * Get all design results as a map for JSON reporting.
   *
   * @return map of design results
   */
  public Map<String, Object> getDesignResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("lineType", lineType.getDisplayName());
    results.put("anchorType", anchorType.getDisplayName());
    results.put("numberOfLines", numberOfLines);
    results.put("waterDepth_m", waterDepth);
    results.put("chainDiameter_mm", chainDiameter * 1000.0);
    results.put("lineLength_m", Math.round(lineLength));
    results.put("touchdownLength_m", Math.round(touchdownLength));
    results.put("fairleadTension_kN", Math.round(fairleadTension));
    results.put("anchorTension_kN", Math.round(anchorTension));
    results.put("maxLineTension_kN", Math.round(maxLineTension));
    results.put("minimumBreakingLoad_kN", Math.round(minimumBreakingLoad));
    results.put("safetyFactor", Math.round(safetyFactor * 100.0) / 100.0);
    results.put("safetyFactorAdequate", isSafetyFactorAdequate());
    results.put("requiredAnchorCapacity_kN", Math.round(requiredAnchorCapacity));
    results.put("restoringStiffness_kN_per_m", Math.round(restoringStiffness));
    results.put("maxOffset_m", Math.round(maxOffset * 10.0) / 10.0);
    results.put("totalWeight_tonnes", Math.round(totalWeight));
    results.put("estimatedCost_MNOK", Math.round(estimatedCost));

    if (lineType == LineType.CHAIN_POLYESTER_CHAIN) {
      results.put("bottomChainLength_m", Math.round(bottomChainLength));
      results.put("polyesterLength_m", Math.round(middleSegmentLength));
      results.put("topChainLength_m", Math.round(topChainLength));
    }

    return results;
  }

  /**
   * Get catenary profile as list of (x, z) coordinate pairs.
   * x is horizontal distance from fairlead [m], z is depth below waterline [m].
   *
   * @param nPoints number of points along the line
   * @return list of double arrays [x, z]
   */
  public List<double[]> getCatenaryProfile(int nPoints) {
    List<double[]> profile = new ArrayList<double[]>();
    double effectiveDepth = waterDepth - fairleadDepth;
    double h = designHorizontalForce;
    double w = lineWeightPerMeter;

    if (w <= 0 || h <= 0 || lineType == LineType.POLYESTER) {
      // Taut mooring — straight line
      for (int i = 0; i <= nPoints; i++) {
        double t = (double) i / nPoints;
        double x = t * anchorRadius;
        double z = fairleadDepth + t * effectiveDepth;
        profile.add(new double[] {x, z});
      }
      return profile;
    }

    double a = h / w;

    // Catenary range
    double coshArg = effectiveDepth / a + 1.0;
    double xSuspended = a * Math.log(coshArg + Math.sqrt(coshArg * coshArg - 1.0));

    for (int i = 0; i <= nPoints; i++) {
      double t = (double) i / nPoints;
      double xCat = t * xSuspended;
      double zCat = a * (Math.cosh(xCat / a) - 1.0);
      // Convert to waterline reference
      double x = xCat;
      double z = waterDepth - zCat;
      profile.add(new double[] {x, z});
    }

    // Add touchdown segment
    if (touchdownLength > 0) {
      for (int i = 1; i <= 10; i++) {
        double t = (double) i / 10;
        double x = xSuspended + t * touchdownLength;
        double z = waterDepth;
        profile.add(new double[] {x, z});
      }
    }

    return profile;
  }

  // ---- Setters ----

  /**
   * Set water depth [m].
   *
   * @param depth water depth in metres
   */
  public void setWaterDepth(double depth) {
    this.waterDepth = depth;
  }

  /**
   * Set number of mooring lines.
   *
   * @param n number of lines
   */
  public void setNumberOfLines(int n) {
    this.numberOfLines = n;
  }

  /**
   * Set mooring line type.
   *
   * @param type line type
   */
  public void setLineType(LineType type) {
    this.lineType = type;
  }

  /**
   * Set anchor type.
   *
   * @param type anchor type
   */
  public void setAnchorType(AnchorType type) {
    this.anchorType = type;
  }

  /**
   * Set chain nominal diameter [m].
   *
   * @param diameter chain diameter in metres
   */
  public void setChainDiameter(double diameter) {
    this.chainDiameter = diameter;
  }

  /**
   * Set chain grade (3=R3, 4=R4, 5=R5).
   *
   * @param grade chain grade
   */
  public void setChainGrade(int grade) {
    this.chainGrade = grade;
  }

  /**
   * Set polyester rope diameter [m].
   *
   * @param diameter polyester diameter in metres
   */
  public void setPolyesterDiameter(double diameter) {
    this.polyesterDiameter = diameter;
  }

  /**
   * Set design horizontal force per line at fairlead [kN].
   *
   * @param force horizontal force in kN
   */
  public void setDesignHorizontalForce(double force) {
    this.designHorizontalForce = force;
  }

  /**
   * Set design vertical force per line at fairlead [kN].
   *
   * @param force vertical force in kN
   */
  public void setDesignVerticalForce(double force) {
    this.designVerticalForce = force;
  }

  /**
   * Set anchor radius [m].
   *
   * @param radius anchor radius in metres
   */
  public void setAnchorRadius(double radius) {
    this.anchorRadius = radius;
  }

  /**
   * Set fairlead depth below waterline [m].
   *
   * @param depth fairlead depth in metres
   */
  public void setFairleadDepth(double depth) {
    this.fairleadDepth = depth;
  }

  /**
   * Set seabed soil type.
   *
   * @param type soil type description
   */
  public void setSoilType(String type) {
    this.soilType = type;
  }

  /**
   * Set required safety factor.
   *
   * @param sf required safety factor
   */
  public void setRequiredSafetyFactor(double sf) {
    this.requiredSafetyFactor = sf;
  }

  /**
   * Set seawater density [kg/m3].
   *
   * @param density seawater density in kg/m3
   */
  public void setSeawaterDensity(double density) {
    this.seawaterDensity = density;
  }
}
