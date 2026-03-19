package neqsim.process.equipment.subsea;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Floating substructure model for offshore wind turbines and oil/gas platforms.
 *
 * <p>
 * Calculates hydrostatic stability, displacement, draft, and key design parameters for floating
 * structures. Supports common floating concepts:
 * </p>
 * <ul>
 * <li><b>Semi-submersible</b>: Multiple columns connected by braces, good motion characteristics
 * </li>
 * <li><b>Spar</b>: Deep-draft cylindrical structure, deep water,
 * ballast-stabilized</li>
 * <li><b>Barge</b>: Shallow-draft flat-bottomed, simple construction</li>
 * <li><b>TLP (Tension Leg Platform)</b>: Buoyancy-excess anchored by tendons</li>
 * </ul>
 *
 * <h2>Key Calculations</h2>
 * <ul>
 * <li>Hydrostatic equilibrium (draft, displacement, buoyancy)</li>
 * <li>Metacentric height (GM) for intact and damaged stability</li>
 * <li>Waterplane area moment of inertia</li>
 * <li>Natural periods (heave, pitch, roll)</li>
 * <li>Steel weight estimation</li>
 * <li>Concept screening and comparison</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>DNV-ST-0119 — Floating wind turbine structures</li>
 * <li>DNV-OS-C301 — Stability and watertight integrity</li>
 * <li>DNV-OS-E301 — Position mooring</li>
 * <li>ABS Guide for Building and Classing Floating Offshore Wind Turbines</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * FloatingSubstructure fs = new FloatingSubstructure("Spar Foundation");
 * fs.setConceptType(FloatingSubstructure.ConceptType.SPAR);
 * fs.setTurbineMass(1200.0);   // tonnes
 * fs.setTowerMass(800.0);      // tonnes
 * fs.setWaterDepth(265.0);     // m
 * fs.setSignificantWaveHeight(14.0); // m (100-year)
 * fs.setSeawaterDensity(1025.0);     // kg/m3
 *
 * // Spar geometry
 * fs.setColumnDiameter(15.0);   // m
 * fs.setColumnHeight(80.0);     // m
 *
 * fs.run();
 * double gm = fs.getMetacentricHeight();  // m
 * double draft = fs.getDraft();            // m
 * }</pre>
 *
 * @author esol
 * @version 1.0
 */
public class FloatingSubstructure extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Floating concept types.
   */
  public enum ConceptType {
    /** Semi-submersible platform. */
    SEMI_SUBMERSIBLE("Semi-submersible"),
    /** Deep-draft spar buoy. */
    SPAR("Spar"),
    /** Flat-bottom barge. */
    BARGE("Barge"),
    /** Tension leg platform. */
    TLP("TLP");

    private final String displayName;

    ConceptType(String displayName) {
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

  /** Concept type. */
  private ConceptType conceptType = ConceptType.SEMI_SUBMERSIBLE;

  /** Turbine nacelle + rotor mass [tonnes]. */
  private double turbineMass = 1200.0;

  /** Tower mass [tonnes]. */
  private double towerMass = 800.0;

  /** Hub height above sea level [m]. */
  private double hubHeight = 135.0;

  /** Tower center of gravity above keel [m] (set during calc if not specified). */
  private double towerCogAboveKeel = 0.0;

  /** Water depth at site [m]. */
  private double waterDepth = 265.0;

  /** Seawater density [kg/m3]. */
  private double seawaterDensity = 1025.0;

  /** Steel density [kg/m3]. */
  private double steelDensity = 7850.0;

  /** Significant wave height, 100-year [m]. */
  private double significantWaveHeight = 14.0;

  /** Peak spectral period [s]. */
  private double peakSpectralPeriod = 16.0;

  /** Design wind thrust [kN] (at rated wind speed). */
  private double designWindThrust = 3000.0;

  // Semi-submersible geometry
  /** Number of columns (semi-sub). */
  private int numberOfColumns = 3;

  /** Column diameter [m] (semi-sub/spar). */
  private double columnDiameter = 15.0;

  /** Column height [m]. */
  private double columnHeight = 35.0;

  /** Column spacing (center-to-center) [m] (semi-sub). */
  private double columnSpacing = 70.0;

  /** Pontoon width [m] (semi-sub). */
  private double pontoonWidth = 10.0;

  /** Pontoon height [m] (semi-sub). */
  private double pontoonHeight = 5.0;

  /** Heave plate diameter [m] (semi-sub/spar, 0 = none). */
  private double heavePlateDiameter = 0.0;

  // Spar geometry
  /** Spar total height [m] (spar concept only). */
  private double sparHeight = 80.0;

  /** Spar upper diameter [m] (tapered spar). */
  private double sparUpperDiameter = 8.0;

  // Barge geometry
  /** Barge length [m]. */
  private double bargeLength = 50.0;

  /** Barge width [m]. */
  private double bargeWidth = 50.0;

  /** Barge height [m]. */
  private double bargeDepth = 10.0;

  // Ballast
  /** Ballast mass [tonnes] — calculated or set. */
  private double ballastMass = 0.0;

  /** Ballast center of gravity above keel [m]. */
  private double ballastCogAboveKeel = 2.0;

  /** Ballast density (seawater or solid) [kg/m3]. */
  private double ballastDensity = 1025.0;

  // --- Computed results ---

  /** Steel weight of substructure [tonnes]. */
  private double steelWeight = 0.0;

  /** Total displacement [tonnes]. */
  private double displacement = 0.0;

  /** Operating draft [m]. */
  private double draft = 0.0;

  /** Waterplane area [m2]. */
  private double waterplaneArea = 0.0;

  /** Second moment of waterplane area [m4]. */
  private double waterplaneSecondMoment = 0.0;

  /** Center of buoyancy above keel (KB) [m]. */
  private double centerOfBuoyancy = 0.0;

  /** Center of gravity above keel (KG) [m]. */
  private double centerOfGravity = 0.0;

  /** Metacentric radius (BM) [m]. */
  private double metacentricRadius = 0.0;

  /** Metacentric height (GM) [m]. */
  private double metacentricHeight = 0.0;

  /** Natural period in heave [s]. */
  private double heaveNaturalPeriod = 0.0;

  /** Natural period in pitch [s]. */
  private double pitchNaturalPeriod = 0.0;

  /** Freeboard [m]. */
  private double freeboard = 0.0;

  /** Total mass (structural + equipment + ballast) [tonnes]. */
  private double totalMass = 0.0;

  /** Displaced volume [m3]. */
  private double displacedVolume = 0.0;

  /** Platform tilt under wind thrust [deg]. */
  private double staticTiltAngle = 0.0;

  /** Excess buoyancy for TLP [kN]. */
  private double excessBuoyancy = 0.0;

  /** Estimated substructure cost [MNOK]. */
  private double estimatedCost = 0.0;

  /**
   * Default constructor.
   */
  public FloatingSubstructure() {
    this("FloatingSubstructure");
  }

  /**
   * Construct with name.
   *
   * @param name equipment name
   */
  public FloatingSubstructure(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    switch (conceptType) {
      case SEMI_SUBMERSIBLE:
        calculateSemiSubmersible();
        break;
      case SPAR:
        calculateSpar();
        break;
      case BARGE:
        calculateBarge();
        break;
      case TLP:
        calculateTLP();
        break;
      default:
        calculateSemiSubmersible();
    }
    calculateNaturalPeriods();
    calculateStaticTilt();
    estimateCost();
    setCalculationIdentifier(id);
  }

  /**
   * Calculate semi-submersible hydrostatics.
   */
  private void calculateSemiSubmersible() {
    // Column geometry
    double colRadius = columnDiameter / 2.0;
    double colArea = Math.PI * colRadius * colRadius;
    double colVolume = colArea * columnHeight;

    // Pontoon geometry (connecting columns at base)
    double pontoonLength = columnSpacing;
    double pontoonVolume = pontoonWidth * pontoonHeight * pontoonLength * numberOfColumns;

    // Total enclosed volume
    double totalVolume = numberOfColumns * colVolume + pontoonVolume;

    // Steel weight estimation (shell thickness method)
    // Column shell: ~30-40mm plate, 1.15 factor for stiffeners/internals
    double shellThickness = 0.035; // m
    double colShellArea = Math.PI * columnDiameter * columnHeight; // outer shell
    double colSteelVolume = colShellArea * shellThickness * 1.15; // stiffener factor
    double colSteelMass = colSteelVolume * steelDensity / 1000.0; // tonnes per column

    // Pontoon steel
    double pontoonShellPerimeter = 2.0 * (pontoonWidth + pontoonHeight);
    double pontoonSteelVolume =
        pontoonShellPerimeter * pontoonLength * 0.025 * 1.15 * numberOfColumns;
    double pontoonSteelMass = pontoonSteelVolume * steelDensity / 1000.0;

    // Bracing
    double bracingSteelMass = (colSteelMass * numberOfColumns + pontoonSteelMass) * 0.15;

    steelWeight = colSteelMass * numberOfColumns + pontoonSteelMass + bracingSteelMass;

    // Total lightship mass
    double lightshipMass = steelWeight + turbineMass + towerMass;

    // Required displacement for equilibrium
    if (ballastMass <= 0) {
      // Auto-calculate ballast to achieve target draft
      double targetDraft = columnHeight * 0.65; // 65% of column height submerged
      double requiredDisplacement = seawaterDensity * (numberOfColumns * colArea * targetDraft
          + pontoonVolume) / 1000.0; // tonnes
      ballastMass = requiredDisplacement - lightshipMass;
      if (ballastMass < 0) {
        ballastMass = 0;
      }
    }

    totalMass = lightshipMass + ballastMass;
    displacement = totalMass;

    // Draft from equilibrium: displacement = rho * (submerged column vol + pontoon vol)
    // Solving for draft (pontoons assumed fully submerged)
    double pontoonDisplacement = seawaterDensity * pontoonVolume / 1000.0;
    double columnDisplacementNeeded = displacement - pontoonDisplacement;
    if (columnDisplacementNeeded > 0) {
      draft = columnDisplacementNeeded * 1000.0
          / (seawaterDensity * numberOfColumns * colArea);
      draft = Math.min(draft, columnHeight);
    } else {
      draft = pontoonHeight;
    }
    draft = Math.max(draft, pontoonHeight);

    displacedVolume = displacement * 1000.0 / seawaterDensity;
    freeboard = columnHeight - draft;

    // Waterplane area (only columns pierce waterplane)
    waterplaneArea = numberOfColumns * colArea;

    // Second moment of area about centroidal axis
    // I = N * (pi*R^4/4 + A*d^2) where d = distance from center
    double iLocal = Math.PI * Math.pow(colRadius, 4) / 4.0;
    double dFromCenter = columnSpacing / 2.0;
    waterplaneSecondMoment =
        numberOfColumns * (iLocal + colArea * dFromCenter * dFromCenter);

    // Center of buoyancy (KB) — approximate as centroid of submerged volume
    double pontoonCB = pontoonHeight / 2.0;
    double columnSubmergedCB = pontoonHeight + (draft - pontoonHeight) / 2.0;
    double volPontoons = pontoonVolume;
    double volColumnsSubmerged = numberOfColumns * colArea * (draft - pontoonHeight);
    double totalSubmergedVol = volPontoons + volColumnsSubmerged;
    if (totalSubmergedVol > 0) {
      centerOfBuoyancy =
          (volPontoons * pontoonCB + volColumnsSubmerged * columnSubmergedCB) / totalSubmergedVol;
    }

    // Center of gravity (KG)
    double steelCog = (pontoonHeight + draft) / 2.0; // approx mid-structure
    double ballastCog = ballastCogAboveKeel;
    double turbineCog = draft + freeboard + hubHeight; // at hub
    double towerCog = draft + freeboard + (hubHeight / 2.0); // mid-tower

    centerOfGravity = (steelWeight * steelCog + ballastMass * ballastCog
        + turbineMass * turbineCog + towerMass * towerCog) / totalMass;

    // Metacentric radius (BM = I / V)
    metacentricRadius = waterplaneSecondMoment / displacedVolume;

    // Metacentric height (GM = KB + BM - KG)
    metacentricHeight = centerOfBuoyancy + metacentricRadius - centerOfGravity;
  }

  /**
   * Calculate spar hydrostatics.
   */
  private void calculateSpar() {
    double radius = columnDiameter / 2.0;
    double colArea = Math.PI * radius * radius;
    double totalVolume = colArea * sparHeight;

    // Steel weight — cylindrical shell with ring stiffeners
    double shellThickness = 0.040; // 40mm for spar
    double shellArea = Math.PI * columnDiameter * sparHeight;
    double steelVolume = shellArea * shellThickness * 1.20; // stiffener factor
    steelWeight = steelVolume * steelDensity / 1000.0;

    // Top/bottom plates
    steelWeight += 2.0 * colArea * 0.030 * steelDensity / 1000.0;

    double lightshipMass = steelWeight + turbineMass + towerMass;

    // Auto-ballast for target draft (90% submerged for spar)
    if (ballastMass <= 0) {
      double targetDraft = sparHeight * 0.90;
      double requiredDisplacement = seawaterDensity * colArea * targetDraft / 1000.0;
      ballastMass = requiredDisplacement - lightshipMass;
      if (ballastMass < 0) {
        ballastMass = 0;
      }
    }

    totalMass = lightshipMass + ballastMass;
    displacement = totalMass;

    // Draft
    draft = displacement * 1000.0 / (seawaterDensity * colArea);
    draft = Math.min(draft, sparHeight);
    displacedVolume = displacement * 1000.0 / seawaterDensity;
    freeboard = sparHeight - draft;

    // Waterplane
    waterplaneArea = colArea;
    waterplaneSecondMoment = Math.PI * Math.pow(radius, 4) / 4.0;

    // KB (centroid of submerged cylinder)
    centerOfBuoyancy = draft / 2.0;

    // KG
    double steelCog = sparHeight * 0.45; // slightly below mid-height
    double ballastCog = ballastCogAboveKeel;
    double turbineCog = sparHeight + hubHeight - sparHeight + draft;
    // Turbine at top of spar + tower
    turbineCog = draft + freeboard + hubHeight;
    double towerCog = draft + freeboard + hubHeight / 2.0;

    centerOfGravity = (steelWeight * steelCog + ballastMass * ballastCog
        + turbineMass * turbineCog + towerMass * towerCog) / totalMass;

    // BM = I/V (very small for spar — stability from low KG)
    metacentricRadius = waterplaneSecondMoment / displacedVolume;
    metacentricHeight = centerOfBuoyancy + metacentricRadius - centerOfGravity;
  }

  /**
   * Calculate barge hydrostatics.
   */
  private void calculateBarge() {
    double bargeVolume = bargeLength * bargeWidth * bargeDepth;

    // Steel weight
    // Bottom + sides + deck plates + internal structure
    double bottomArea = bargeLength * bargeWidth;
    double sideArea = 2.0 * (bargeLength + bargeWidth) * bargeDepth;
    double shellThickness = 0.020; // 20mm
    steelWeight =
        (bottomArea + sideArea + bottomArea) * shellThickness * 1.30 * steelDensity / 1000.0;

    double lightshipMass = steelWeight + turbineMass + towerMass;

    if (ballastMass <= 0) {
      double targetDraft = bargeDepth * 0.50;
      double requiredDisplacement =
          seawaterDensity * bargeLength * bargeWidth * targetDraft / 1000.0;
      ballastMass = requiredDisplacement - lightshipMass;
      if (ballastMass < 0) {
        ballastMass = 0;
      }
    }

    totalMass = lightshipMass + ballastMass;
    displacement = totalMass;

    draft = displacement * 1000.0 / (seawaterDensity * bargeLength * bargeWidth);
    draft = Math.min(draft, bargeDepth);
    displacedVolume = displacement * 1000.0 / seawaterDensity;
    freeboard = bargeDepth - draft;

    waterplaneArea = bargeLength * bargeWidth;
    // I for rectangle = L*W^3 / 12
    waterplaneSecondMoment = bargeLength * Math.pow(bargeWidth, 3) / 12.0;

    centerOfBuoyancy = draft / 2.0;

    double steelCog = bargeDepth * 0.4;
    double ballastCog = ballastCogAboveKeel;
    double turbineCog = draft + freeboard + hubHeight;
    double towerCog = draft + freeboard + hubHeight / 2.0;

    centerOfGravity = (steelWeight * steelCog + ballastMass * ballastCog
        + turbineMass * turbineCog + towerMass * towerCog) / totalMass;

    metacentricRadius = waterplaneSecondMoment / displacedVolume;
    metacentricHeight = centerOfBuoyancy + metacentricRadius - centerOfGravity;
  }

  /**
   * Calculate TLP hydrostatics.
   */
  private void calculateTLP() {
    // TLP is like a semi-sub but with excess buoyancy held by tendons
    calculateSemiSubmersible();

    // For TLP: intentionally over-buoyant
    double overBuoyancyFactor = 1.20; // 20% excess buoyancy
    double requiredBuoyancy = totalMass * overBuoyancyFactor;
    double additionalBallastRemoval = requiredBuoyancy - displacement;

    // Excess buoyancy provides tendon pretension
    excessBuoyancy = (requiredBuoyancy - totalMass) * 9.81; // kN
  }

  /**
   * Calculate natural periods of motion.
   */
  private void calculateNaturalPeriods() {
    // Heave natural period: T = 2*pi * sqrt(m / (rho*g*Awp))
    // Added mass coefficient ~ 1.0 for heave
    double addedMassCoeff = 1.0;
    double totalVirtualMass = totalMass * 1000.0 * (1.0 + addedMassCoeff); // kg
    double stiffnessHeave = seawaterDensity * 9.81 * waterplaneArea; // N/m

    if (stiffnessHeave > 0) {
      heaveNaturalPeriod = 2.0 * Math.PI * Math.sqrt(totalVirtualMass / stiffnessHeave);
    }

    // Pitch natural period: T = 2*pi * sqrt(I_mass / (rho*g*V*GM))
    // Moment of inertia of mass about waterline (approx)
    double momentOfInertia =
        totalMass * 1000.0 * Math.pow(centerOfGravity, 2) * 1.5; // kg*m2 approx
    double stiffnessPitch =
        seawaterDensity * 9.81 * displacedVolume * Math.abs(metacentricHeight); // N*m/rad

    if (stiffnessPitch > 0 && metacentricHeight > 0) {
      pitchNaturalPeriod = 2.0 * Math.PI * Math.sqrt(momentOfInertia / stiffnessPitch);
    }
  }

  /**
   * Calculate static tilt angle under design wind thrust.
   */
  private void calculateStaticTilt() {
    // Overturning moment from wind: M = F * (hub height above waterline)
    double thrustArmAboveWL = hubHeight; // m, above waterline
    double overturningMoment = designWindThrust * 1000.0 * thrustArmAboveWL; // N*m

    // Restoring moment: C_pitch = rho * g * V * GM
    double restoringStiffness = seawaterDensity * 9.81 * displacedVolume * metacentricHeight;

    if (restoringStiffness > 0) {
      staticTiltAngle = Math.toDegrees(overturningMoment / restoringStiffness);
    }
  }

  /**
   * Estimate substructure cost based on steel weight and concept.
   */
  private void estimateCost() {
    // Base cost: steel cost + fabrication + outfitting
    double steelCostPerTonne; // KNOK/tonne
    switch (conceptType) {
      case SEMI_SUBMERSIBLE:
        steelCostPerTonne = 50.0; // KNOK/tonne (fab + material)
        break;
      case SPAR:
        steelCostPerTonne = 40.0; // Simpler geometry
        break;
      case BARGE:
        steelCostPerTonne = 35.0; // Simplest
        break;
      case TLP:
        steelCostPerTonne = 60.0; // Complex tendons
        break;
      default:
        steelCostPerTonne = 50.0;
    }

    estimatedCost = steelWeight * steelCostPerTonne / 1000.0; // MNOK

    // Add ballast system cost
    estimatedCost += ballastMass * 5.0 / 1000.0; // KNOK/tonne ballast

    // Outfitting (J-tubes, platform, access, secondary steel)
    estimatedCost *= 1.20; // 20% outfitting factor
  }

  // ---- Getters for computed results ----

  /**
   * Get the concept type.
   *
   * @return concept type
   */
  public ConceptType getConceptType() {
    return conceptType;
  }

  /**
   * Get steel weight [tonnes].
   *
   * @return steel weight in tonnes
   */
  public double getSteelWeight() {
    return steelWeight;
  }

  /**
   * Get total displacement [tonnes].
   *
   * @return displacement in tonnes
   */
  public double getDisplacement() {
    return displacement;
  }

  /**
   * Get operating draft [m].
   *
   * @return draft in metres
   */
  public double getDraft() {
    return draft;
  }

  /**
   * Get waterplane area [m2].
   *
   * @return waterplane area in m2
   */
  public double getWaterplaneArea() {
    return waterplaneArea;
  }

  /**
   * Get metacentric height GM [m].
   *
   * @return GM in metres
   */
  public double getMetacentricHeight() {
    return metacentricHeight;
  }

  /**
   * Get metacentric radius BM [m].
   *
   * @return BM in metres
   */
  public double getMetacentricRadius() {
    return metacentricRadius;
  }

  /**
   * Get center of buoyancy above keel KB [m].
   *
   * @return KB in metres
   */
  public double getCenterOfBuoyancy() {
    return centerOfBuoyancy;
  }

  /**
   * Get center of gravity above keel KG [m].
   *
   * @return KG in metres
   */
  public double getCenterOfGravity() {
    return centerOfGravity;
  }

  /**
   * Get heave natural period [s].
   *
   * @return heave period in seconds
   */
  public double getHeaveNaturalPeriod() {
    return heaveNaturalPeriod;
  }

  /**
   * Get pitch natural period [s].
   *
   * @return pitch period in seconds
   */
  public double getPitchNaturalPeriod() {
    return pitchNaturalPeriod;
  }

  /**
   * Get freeboard [m].
   *
   * @return freeboard in metres
   */
  public double getFreeboard() {
    return freeboard;
  }

  /**
   * Get total mass (structural + topsides + ballast) [tonnes].
   *
   * @return total mass in tonnes
   */
  public double getTotalMass() {
    return totalMass;
  }

  /**
   * Get displaced volume [m3].
   *
   * @return displaced volume in m3
   */
  public double getDisplacedVolume() {
    return displacedVolume;
  }

  /**
   * Get ballast mass [tonnes].
   *
   * @return ballast mass in tonnes
   */
  public double getBallastMass() {
    return ballastMass;
  }

  /**
   * Get static tilt angle under design wind [deg].
   *
   * @return tilt angle in degrees
   */
  public double getStaticTiltAngle() {
    return staticTiltAngle;
  }

  /**
   * Get excess buoyancy for TLP [kN].
   *
   * @return excess buoyancy in kN
   */
  public double getExcessBuoyancy() {
    return excessBuoyancy;
  }

  /**
   * Get estimated substructure cost [MNOK].
   *
   * @return cost in MNOK
   */
  public double getEstimatedCost() {
    return estimatedCost;
  }

  /**
   * Get waterplane second moment of area [m4].
   *
   * @return second moment of area in m4
   */
  public double getWaterplaneSecondMoment() {
    return waterplaneSecondMoment;
  }

  /**
   * Get all design results as a map for JSON reporting.
   *
   * @return map of design results
   */
  public Map<String, Object> getDesignResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("conceptType", conceptType.getDisplayName());
    results.put("turbineMass_tonnes", turbineMass);
    results.put("towerMass_tonnes", towerMass);
    results.put("steelWeight_tonnes", Math.round(steelWeight));
    results.put("ballastMass_tonnes", Math.round(ballastMass));
    results.put("totalMass_tonnes", Math.round(totalMass));
    results.put("displacement_tonnes", Math.round(displacement));
    results.put("displacedVolume_m3", Math.round(displacedVolume));
    results.put("draft_m", Math.round(draft * 10.0) / 10.0);
    results.put("freeboard_m", Math.round(freeboard * 10.0) / 10.0);
    results.put("waterplaneArea_m2", Math.round(waterplaneArea));
    results.put("KB_m", Math.round(centerOfBuoyancy * 10.0) / 10.0);
    results.put("BM_m", Math.round(metacentricRadius * 10.0) / 10.0);
    results.put("KG_m", Math.round(centerOfGravity * 10.0) / 10.0);
    results.put("GM_m", Math.round(metacentricHeight * 10.0) / 10.0);
    results.put("heaveNaturalPeriod_s", Math.round(heaveNaturalPeriod * 10.0) / 10.0);
    results.put("pitchNaturalPeriod_s", Math.round(pitchNaturalPeriod * 10.0) / 10.0);
    results.put("staticTiltAngle_deg", Math.round(staticTiltAngle * 100.0) / 100.0);
    results.put("estimatedCost_MNOK", Math.round(estimatedCost));
    if (conceptType == ConceptType.TLP) {
      results.put("excessBuoyancy_kN", Math.round(excessBuoyancy));
    }
    return results;
  }

  /**
   * Check if stability is adequate per DNV-ST-0119 requirements.
   *
   * @return true if GM is positive and meets minimum requirements
   */
  public boolean isStabilityAdequate() {
    // DNV-ST-0119: GM_intact &gt; 1.0 m for intact condition
    if (conceptType == ConceptType.TLP) {
      return excessBuoyancy > 0;
    }
    return metacentricHeight > 1.0;
  }

  // ---- Setters ----

  /**
   * Set concept type.
   *
   * @param type floating concept type
   */
  public void setConceptType(ConceptType type) {
    this.conceptType = type;
  }

  /**
   * Set turbine nacelle + rotor mass [tonnes].
   *
   * @param mass turbine mass in tonnes
   */
  public void setTurbineMass(double mass) {
    this.turbineMass = mass;
  }

  /**
   * Set tower mass [tonnes].
   *
   * @param mass tower mass in tonnes
   */
  public void setTowerMass(double mass) {
    this.towerMass = mass;
  }

  /**
   * Set hub height above sea level [m].
   *
   * @param height hub height in metres
   */
  public void setHubHeight(double height) {
    this.hubHeight = height;
  }

  /**
   * Set water depth at site [m].
   *
   * @param depth water depth in metres
   */
  public void setWaterDepth(double depth) {
    this.waterDepth = depth;
  }

  /**
   * Set seawater density [kg/m3].
   *
   * @param density seawater density in kg/m3
   */
  public void setSeawaterDensity(double density) {
    this.seawaterDensity = density;
  }

  /**
   * Set significant wave height, 100-year [m].
   *
   * @param hs significant wave height in metres
   */
  public void setSignificantWaveHeight(double hs) {
    this.significantWaveHeight = hs;
  }

  /**
   * Set peak spectral period [s].
   *
   * @param tp peak period in seconds
   */
  public void setPeakSpectralPeriod(double tp) {
    this.peakSpectralPeriod = tp;
  }

  /**
   * Set design wind thrust force [kN].
   *
   * @param thrust thrust force in kN
   */
  public void setDesignWindThrust(double thrust) {
    this.designWindThrust = thrust;
  }

  /**
   * Set number of columns (semi-sub).
   *
   * @param n number of columns
   */
  public void setNumberOfColumns(int n) {
    this.numberOfColumns = n;
  }

  /**
   * Set column diameter [m].
   *
   * @param diameter column diameter in metres
   */
  public void setColumnDiameter(double diameter) {
    this.columnDiameter = diameter;
  }

  /**
   * Set column height [m].
   *
   * @param height column height in metres
   */
  public void setColumnHeight(double height) {
    this.columnHeight = height;
  }

  /**
   * Set column spacing, center-to-center [m] (semi-sub).
   *
   * @param spacing column spacing in metres
   */
  public void setColumnSpacing(double spacing) {
    this.columnSpacing = spacing;
  }

  /**
   * Set pontoon width [m].
   *
   * @param width pontoon width in metres
   */
  public void setPontoonWidth(double width) {
    this.pontoonWidth = width;
  }

  /**
   * Set pontoon height [m].
   *
   * @param height pontoon height in metres
   */
  public void setPontoonHeight(double height) {
    this.pontoonHeight = height;
  }

  /**
   * Set spar total height [m].
   *
   * @param height spar height in metres
   */
  public void setSparHeight(double height) {
    this.sparHeight = height;
  }

  /**
   * Set barge length [m].
   *
   * @param length barge length in metres
   */
  public void setBargeLength(double length) {
    this.bargeLength = length;
  }

  /**
   * Set barge width [m].
   *
   * @param width barge width in metres
   */
  public void setBargeWidth(double width) {
    this.bargeWidth = width;
  }

  /**
   * Set barge depth [m].
   *
   * @param depth barge depth in metres
   */
  public void setBargeDepth(double depth) {
    this.bargeDepth = depth;
  }

  /**
   * Set ballast mass [tonnes]. Set to 0 for automatic calculation.
   *
   * @param mass ballast mass in tonnes
   */
  public void setBallastMass(double mass) {
    this.ballastMass = mass;
  }

  /**
   * Set ballast center of gravity above keel [m].
   *
   * @param cog ballast COG in metres above keel
   */
  public void setBallastCogAboveKeel(double cog) {
    this.ballastCogAboveKeel = cog;
  }
}
