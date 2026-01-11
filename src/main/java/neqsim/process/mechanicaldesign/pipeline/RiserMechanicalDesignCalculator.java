package neqsim.process.mechanicaldesign.pipeline;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Calculator for riser-specific mechanical design.
 *
 * <p>
 * This class extends PipeMechanicalDesignCalculator to provide riser-specific calculations
 * including:
 * </p>
 * <ul>
 * <li>Top tension calculation (catenary and TTR)</li>
 * <li>Touchdown point stress analysis</li>
 * <li>VIV (Vortex-Induced Vibration) response</li>
 * <li>Dynamic stress from waves and currents</li>
 * <li>Heave motion response</li>
 * <li>Riser fatigue life estimation</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>DNV-OS-F201 - Dynamic Risers</li>
 * <li>DNV-RP-F204 - Riser Fatigue</li>
 * <li>DNV-RP-C203 - Fatigue Design of Offshore Structures</li>
 * <li>API RP 2RD - Design of Risers for Floating Production Systems</li>
 * </ul>
 *
 * <h2>Key Formulas</h2>
 *
 * <h3>Catenary Top Tension</h3>
 * <p>
 * For a catenary riser, the top tension is: T_top = w × H / sin(θ_top) where w = submerged weight
 * per meter, H = water depth, θ_top = top angle from horizontal
 * </p>
 *
 * <h3>VIV Response</h3>
 * <p>
 * Vortex shedding frequency: f_v = St × V / D where St = Strouhal number (~0.2), V = current
 * velocity, D = outer diameter
 * </p>
 *
 * <h3>Touchdown Point Stress</h3>
 * <p>
 * Bending stress at TDP: σ_b = E × D / (2 × R_TDP) where E = Young's modulus, D = outer diameter,
 * R_TDP = radius of curvature at touchdown
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public class RiserMechanicalDesignCalculator extends PipeMechanicalDesignCalculator {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ============ Riser Configuration ============
  /** Riser type name. */
  private String riserType = "STEEL_CATENARY_RISER";

  /** Water depth in meters. */
  private double waterDepth = 500.0;

  /** Top hangoff angle from vertical in degrees. */
  private double topAngle = 10.0;

  /** Departure angle from seabed in degrees. */
  private double departureAngle = 15.0;

  // ============ Environmental Parameters ============
  /** Current velocity at mid-depth in m/s. */
  private double currentVelocity = 0.5;

  /** Current velocity at seabed in m/s. */
  private double seabedCurrentVelocity = 0.2;

  /** Significant wave height in meters. */
  private double significantWaveHeight = 2.0;

  /** Peak wave period in seconds. */
  private double peakWavePeriod = 8.0;

  /** Platform heave motion amplitude in meters. */
  private double platformHeaveAmplitude = 2.0;

  /** Platform heave period in seconds. */
  private double platformHeavePeriod = 10.0;

  /** Seabed friction coefficient. */
  private double seabedFriction = 0.3;

  // ============ TTR Parameters ============
  /** Applied top tension for TTR in kN. */
  private double appliedTopTension = 0.0;

  /** Tension variation factor for heave. */
  private double tensionVariationFactor = 0.1;

  // ============ Lazy-Wave Parameters ============
  /** Depth of buoyancy modules from surface in meters. */
  private double buoyancyModuleDepth = 0.0;

  /** Length of buoyancy section in meters. */
  private double buoyancyModuleLength = 100.0;

  /** Buoyancy per unit length in N/m. */
  private double buoyancyPerMeter = 0.0;

  // ============ Calculated Results - Top Tension ============
  /** Calculated top tension in kN. */
  private double topTension = 0.0;

  /** Minimum top tension in kN. */
  private double minTopTension = 0.0;

  /** Maximum top tension in kN. */
  private double maxTopTension = 0.0;

  /** Bottom tension at seabed in kN. */
  private double bottomTension = 0.0;

  /** Catenary parameter a in meters. */
  private double catenaryParameter = 0.0;

  // ============ Calculated Results - Touchdown ============
  /** Touchdown point stress in MPa. */
  private double touchdownPointStress = 0.0;

  /** Radius of curvature at touchdown in meters. */
  private double touchdownCurvatureRadius = 0.0;

  /** Length of touchdown zone in meters. */
  private double touchdownZoneLength = 0.0;

  /** Bending moment at touchdown in kN.m. */
  private double touchdownBendingMoment = 0.0;

  // ============ Calculated Results - VIV ============
  /** Vortex shedding frequency in Hz. */
  private double vortexSheddingFrequency = 0.0;

  /** Riser natural frequency (first mode) in Hz. */
  private double naturalFrequency = 0.0;

  /** VIV amplitude (A/D ratio). */
  private double vivAmplitude = 0.0;

  /** VIV stress range in MPa. */
  private double vivStressRange = 0.0;

  /** VIV fatigue damage per year. */
  private double vivFatigueDamage = 0.0;

  /** Whether VIV lock-in is occurring. */
  private boolean vivLockIn = false;

  // ============ Calculated Results - Dynamic Response ============
  /** Wave-induced stress range in MPa. */
  private double waveInducedStress = 0.0;

  /** Heave-induced stress range in MPa. */
  private double heaveInducedStress = 0.0;

  /** Combined dynamic stress in MPa. */
  private double combinedDynamicStress = 0.0;

  /** Stroke requirement for TTR in meters. */
  private double strokeRequirement = 0.0;

  // ============ Calculated Results - Fatigue ============
  /** Riser fatigue life in years. */
  private double riserFatigueLife = 0.0;

  /** Total fatigue damage rate per year. */
  private double totalFatigueDamageRate = 0.0;

  /** Wave fatigue damage per year. */
  private double waveFatigueDamage = 0.0;

  // ============ Calculated Results - Collapse ============
  /** Collapse utilization factor. */
  private double collapseUtilization = 0.0;

  /** Maximum stress utilization. */
  private double maxStressUtilization = 0.0;

  // ============ Configurable Design Parameters (from database) ============
  /** Strouhal number for VIV (configurable from DNV-RP-C205). */
  private double strouhalNumber = 0.2;

  /** Drag coefficient for circular cylinder (configurable from DNV-RP-C205). */
  private double dragCoefficient = 1.0;

  /** Added mass coefficient (configurable from DNV-RP-C205). */
  private double addedMassCoefficient = 1.0;

  /** Lift coefficient for VIV (configurable from DNV-RP-C205). */
  private double liftCoefficient = 0.9;

  /** S-N curve parameter log(a) for seawater with CP (configurable from DNV-RP-C203). */
  private double snParameter = 12.164;

  /** S-N curve slope parameter m (configurable from DNV-RP-C203). */
  private double snSlope = 3.0;

  /** Fatigue design factor (configurable from DNV-RP-F204). */
  private double fatigueDesignFactor = 3.0;

  /** Dynamic amplification factor (configurable from DNV-OS-F201). */
  private double dynamicAmplificationFactor = 1.2;

  /** Stress concentration factor for girth welds (configurable from DNV-RP-F204). */
  private double stressConcentrationFactor = 1.3;

  // ============ Constants ============
  /** Gravity acceleration m/s². */
  private static final double GRAVITY = 9.81;

  /** Seawater density kg/m³. */
  private static final double SEAWATER_DENSITY = 1025.0;

  /**
   * Default constructor.
   */
  public RiserMechanicalDesignCalculator() {
    super();
  }

  // ============================================================================
  // CATENARY CALCULATIONS
  // ============================================================================

  /**
   * Calculate top tension for catenary riser (SCR, flexible, lazy-wave).
   *
   * <p>
   * For a catenary riser, the tension distribution follows: T(s) = T_bottom + w × s where s = arc
   * length from seabed, w = submerged weight per unit length
   * </p>
   *
   * @return top tension in kN
   */
  public double calculateCatenaryTopTension() {
    // Calculate submerged weight per meter
    calculateWeightsAndAreas();
    double submergedWeight = calculateSubmergedWeight(0.0); // Empty pipe
    double w = submergedWeight; // N/m

    // Catenary mechanics
    // T_horizontal = T_bottom = w × a (catenary parameter)
    // T_top = w × √(a² + s²) where s = arc length
    // a = H / (cosh(θ_top) - 1) approximately

    double thetaTop = Math.toRadians(90.0 - topAngle); // From horizontal
    double thetaBottom = Math.toRadians(departureAngle); // From horizontal

    // Simplified catenary formula
    // T_top = w × H / sin(θ_top)
    double sinTop = Math.sin(thetaTop);
    if (sinTop > 0.1) {
      topTension = w * waterDepth / sinTop / 1000.0; // Convert to kN
    } else {
      // Near-vertical case
      topTension = w * waterDepth / 1000.0;
    }

    // Calculate catenary parameter
    double cosTop = Math.cos(thetaTop);
    if (cosTop > 0.01) {
      double horizontalTension = topTension * cosTop;
      catenaryParameter = horizontalTension * 1000.0 / w; // meters
    }

    // Bottom tension (horizontal component for catenary)
    bottomTension = topTension * Math.cos(thetaTop);

    // Min/max from dynamic variation
    minTopTension = topTension * (1.0 - tensionVariationFactor);
    maxTopTension = topTension * (1.0 + tensionVariationFactor);

    return topTension;
  }

  /**
   * Calculate tension for Top Tensioned Riser.
   *
   * <p>
   * For TTR, the applied tension must exceed the riser weight to maintain positive tension
   * throughout the riser length.
   * </p>
   *
   * @return required top tension in kN
   */
  public double calculateTTRTension() {
    // Calculate submerged weight of entire riser
    calculateWeightsAndAreas();
    double submergedWeight = calculateSubmergedWeight(0.0); // N/m
    double totalWeight = submergedWeight * waterDepth / 1000.0; // kN

    // Required tension = weight + safety margin
    double safetyFactor = 1.5;
    double requiredTension = totalWeight * safetyFactor;

    if (appliedTopTension > 0) {
      topTension = appliedTopTension;
    } else {
      topTension = requiredTension;
    }

    // Bottom tension = Top tension - weight
    bottomTension = topTension - totalWeight;

    // Check minimum tension (should be positive)
    if (bottomTension < 0) {
      // Insufficient top tension
      topTension = totalWeight + 100.0; // Add 100 kN minimum
      bottomTension = 100.0;
    }

    // Dynamic variation
    minTopTension = topTension * (1.0 - tensionVariationFactor);
    maxTopTension = topTension * (1.0 + tensionVariationFactor);

    return topTension;
  }

  /**
   * Calculate stroke requirement for TTR heave compensation.
   *
   * @return stroke requirement in meters
   */
  public double calculateStrokeRequirement() {
    // Stroke = heave amplitude × safety factor + drift
    strokeRequirement = platformHeaveAmplitude * 2.5;

    // Add for wave frequency response
    strokeRequirement += significantWaveHeight * 0.5;

    return strokeRequirement;
  }

  // ============================================================================
  // TOUCHDOWN POINT CALCULATIONS
  // ============================================================================

  /**
   * Calculate stress at the touchdown point.
   *
   * <p>
   * The touchdown point (TDP) is where the riser contacts the seabed. This is a critical fatigue
   * location due to cyclic bending as the TDP moves with wave action.
   * </p>
   *
   * @return touchdown point stress in MPa
   */
  public double calculateTouchdownPointStress() {
    double od = getOuterDiameter();
    double wt = getNominalWallThickness();
    if (wt <= 0) {
      wt = getMinimumWallThickness();
    }
    if (wt <= 0) {
      wt = od * 0.05; // Assume 5% wall thickness
    }

    // Radius of curvature at TDP
    // For catenary: R = T_h / w where T_h = horizontal tension, w = submerged weight
    calculateWeightsAndAreas();
    double submergedWeight = calculateSubmergedWeight(0.0);

    if (bottomTension <= 0) {
      calculateCatenaryTopTension();
    }

    double horizontalTension = bottomTension * 1000.0; // Convert kN to N
    if (horizontalTension > 0 && submergedWeight > 0) {
      touchdownCurvatureRadius = horizontalTension / submergedWeight;
    } else {
      touchdownCurvatureRadius = 50.0; // Default 50m radius
    }

    // Minimum bend radius check
    double minBendRadius = 18.0 * od;
    if (touchdownCurvatureRadius < minBendRadius) {
      // Warning: TDP radius less than minimum
      touchdownCurvatureRadius = minBendRadius;
    }

    // Bending stress at TDP: σ = E × D / (2 × R)
    double E = getYoungsModulus(); // MPa
    touchdownPointStress = E * od / (2.0 * touchdownCurvatureRadius);

    // Calculate bending moment
    double I = Math.PI * (Math.pow(od, 4) - Math.pow(od - 2 * wt, 4)) / 64.0; // m^4
    touchdownBendingMoment = E * 1e6 * I / touchdownCurvatureRadius / 1000.0; // kN.m

    return touchdownPointStress;
  }

  /**
   * Calculate length of the touchdown zone.
   *
   * <p>
   * The touchdown zone is where the riser transitions from suspended catenary to resting on seabed.
   * This zone experiences cyclic motion with wave action.
   * </p>
   *
   * @return touchdown zone length in meters
   */
  public double calculateTouchdownZoneLength() {
    // TDZ length depends on wave height and riser stiffness
    // Approximate: L_TDZ = 3 × √(EI / T_h) × (H_s / 2)

    double od = getOuterDiameter();
    double wt = getNominalWallThickness();
    if (wt <= 0) {
      wt = getMinimumWallThickness();
    }
    if (wt <= 0) {
      wt = od * 0.05;
    }

    double I = Math.PI * (Math.pow(od, 4) - Math.pow(od - 2 * wt, 4)) / 64.0;
    double E = getYoungsModulus() * 1e6; // Pa
    double EI = E * I; // N.m²

    double horizontalTension = bottomTension * 1000.0; // N
    if (horizontalTension <= 0) {
      horizontalTension = 100000.0; // 100 kN default
    }

    double flexibilityLength = Math.sqrt(EI / horizontalTension);
    touchdownZoneLength = 3.0 * flexibilityLength * significantWaveHeight / 2.0;

    // Limit to reasonable range
    if (touchdownZoneLength < 10.0) {
      touchdownZoneLength = 10.0;
    }
    if (touchdownZoneLength > 200.0) {
      touchdownZoneLength = 200.0;
    }

    return touchdownZoneLength;
  }

  // ============================================================================
  // VIV CALCULATIONS
  // ============================================================================

  /**
   * Calculate VIV (Vortex-Induced Vibration) response.
   *
   * <p>
   * VIV occurs when vortex shedding frequency approaches the riser natural frequency. This can
   * cause significant fatigue damage.
   * </p>
   *
   * @return VIV amplitude as A/D ratio
   */
  public double calculateVIVResponse() {
    double od = getOuterDiameter();
    if (od <= 0) {
      od = 0.3; // Default 300mm
    }

    // Vortex shedding frequency
    // f_v = St × V / D
    vortexSheddingFrequency = strouhalNumber * currentVelocity / od;

    // Calculate natural frequency (first mode)
    naturalFrequency = calculateNaturalFrequency();

    // Check for lock-in
    // Lock-in occurs when f_v is within 0.7 to 1.3 of f_n
    double freqRatio = vortexSheddingFrequency / naturalFrequency;
    vivLockIn = (freqRatio > 0.7 && freqRatio < 1.3);

    // Calculate VIV amplitude
    // Without suppression, A/D can be 0.3 to 1.0
    // With strakes or fairings, A/D < 0.2
    if (vivLockIn) {
      // Resonance - high amplitude
      vivAmplitude = 0.8; // A/D ratio
    } else if (freqRatio > 0.3) {
      // Partial response
      vivAmplitude = 0.3 * Math.exp(-Math.pow(freqRatio - 1.0, 2) / 0.5);
    } else {
      // Low response
      vivAmplitude = 0.1;
    }

    // Calculate VIV stress range
    // σ = E × A × D / (2 × L²) for bending mode
    double A = vivAmplitude * od;
    double E = getYoungsModulus(); // MPa
    double L = waterDepth;
    vivStressRange = 2.0 * Math.PI * E * A * od / (L * L) * 100.0; // Simplified

    return vivAmplitude;
  }

  /**
   * Calculate natural frequency of the riser (first mode).
   *
   * @return natural frequency in Hz
   */
  private double calculateNaturalFrequency() {
    double od = getOuterDiameter();
    double wt = getNominalWallThickness();
    if (wt <= 0) {
      wt = getMinimumWallThickness();
    }
    if (wt <= 0) {
      wt = od * 0.05;
    }
    double id = od - 2 * wt;

    // Moment of inertia
    double I = Math.PI * (Math.pow(od, 4) - Math.pow(id, 4)) / 64.0;

    // Effective mass (steel + added mass + contents)
    double steelArea = Math.PI * (od * od - id * id) / 4.0;
    double steelMass = steelArea * getSteelDensity();
    double addedMass = Math.PI * od * od / 4.0 * SEAWATER_DENSITY * 1.0; // Ca = 1.0
    double effectiveMass = steelMass + addedMass;

    double E = getYoungsModulus() * 1e6; // Pa
    double EI = E * I;
    double L = waterDepth;

    // Natural frequency for tensioned beam
    // f_n = (1/2π) × √(T/mL² + (π²EI)/(mL⁴))
    double T = topTension * 1000.0; // N
    if (T <= 0) {
      T = effectiveMass * GRAVITY * L; // Approximate
    }

    double termTension = T / (effectiveMass * L * L);
    double termBending = Math.PI * Math.PI * EI / (effectiveMass * Math.pow(L, 4));

    naturalFrequency = Math.sqrt(termTension + termBending) / (2.0 * Math.PI);

    return naturalFrequency;
  }

  /**
   * Calculate VIV fatigue damage.
   *
   * @return VIV fatigue damage per year
   */
  public double calculateVIVFatigueDamage() {
    // Number of cycles per year
    // Assume VIV occurs 50% of the time at vortex shedding frequency
    double cyclesPerYear = vortexSheddingFrequency * 0.5 * 365.25 * 24 * 3600;

    // S-N curve parameters (DNV-RP-C203, D curve for seawater with CP)
    double logA = 11.764;
    double m = 3.0;

    // Calculate fatigue damage: D = n / N = n × S^m / 10^logA
    if (vivStressRange > 0) {
      double N = Math.pow(10.0, logA) / Math.pow(vivStressRange, m);
      vivFatigueDamage = cyclesPerYear / N;
    } else {
      vivFatigueDamage = 0.0;
    }

    return vivFatigueDamage;
  }

  // ============================================================================
  // DYNAMIC STRESS CALCULATIONS
  // ============================================================================

  /**
   * Calculate wave-induced stress.
   *
   * <p>
   * Waves cause cyclic loading on the riser through direct loading and vessel motion.
   * </p>
   *
   * @return wave-induced stress range in MPa
   */
  public double calculateWaveInducedStress() {
    double od = getOuterDiameter();
    double wt = getNominalWallThickness();
    if (wt <= 0) {
      wt = getMinimumWallThickness();
    }
    if (wt <= 0) {
      wt = od * 0.05;
    }

    // Wave particle velocity at mid-depth
    // Using linear wave theory: u = π × Hs / Tp × exp(-k × d)
    // Simplified for deep water
    double waveVelocity = Math.PI * significantWaveHeight / peakWavePeriod;

    // Drag and inertia forces (Morison equation simplified)
    double Cd = 1.0; // Drag coefficient
    double Cm = 2.0; // Inertia coefficient
    double rho = SEAWATER_DENSITY;

    // Force per unit length
    double dragForce = 0.5 * Cd * rho * od * waveVelocity * waveVelocity;
    double inertiaForce = Cm * rho * Math.PI * od * od / 4.0 * waveVelocity / peakWavePeriod;

    // Total force
    double totalForce = Math.sqrt(dragForce * dragForce + inertiaForce * inertiaForce);

    // Bending stress from wave loading
    // σ = M × c / I where M = F × L² / 8 (distributed load)
    double L = waterDepth;
    double moment = totalForce * L * L / 8.0;
    double I = Math.PI * (Math.pow(od, 4) - Math.pow(od - 2 * wt, 4)) / 64.0;
    double c = od / 2.0;

    waveInducedStress = moment * c / I / 1e6; // Convert to MPa

    return waveInducedStress;
  }

  /**
   * Calculate heave-induced stress.
   *
   * <p>
   * Platform heave motion causes axial stress variations in the riser.
   * </p>
   *
   * @return heave-induced stress in MPa
   */
  public double calculateHeaveInducedStress() {
    double od = getOuterDiameter();
    double wt = getNominalWallThickness();
    if (wt <= 0) {
      wt = getMinimumWallThickness();
    }
    if (wt <= 0) {
      wt = od * 0.05;
    }

    // Heave causes axial strain
    // ε = Δ / L where Δ = heave amplitude, L = riser length
    double strain = platformHeaveAmplitude / waterDepth;

    // Axial stress = E × ε
    double E = getYoungsModulus(); // MPa
    heaveInducedStress = E * strain;

    // Combined dynamic stress
    combinedDynamicStress =
        Math.sqrt(waveInducedStress * waveInducedStress + heaveInducedStress * heaveInducedStress);

    return heaveInducedStress;
  }

  // ============================================================================
  // FATIGUE CALCULATIONS
  // ============================================================================

  /**
   * Calculate total riser fatigue life.
   *
   * <p>
   * Combines fatigue damage from: - VIV - Wave loading - Heave motion - TDP cyclic bending
   * </p>
   *
   * @return fatigue life in years
   */
  public double calculateRiserFatigueLife() {
    // Calculate wave fatigue damage
    // Assume 10^7 wave cycles per year
    double waveCyclesPerYear = 1e7;
    double logA = 11.764;
    double m = 3.0;

    if (waveInducedStress > 0) {
      double N = Math.pow(10.0, logA) / Math.pow(waveInducedStress, m);
      waveFatigueDamage = waveCyclesPerYear / N;
    }

    // TDP fatigue damage
    double tdpFatigueDamage = 0.0;
    if (touchdownPointStress > 0) {
      // TDP stress range from wave motion
      double tdpStressRange = touchdownPointStress * 0.2; // 20% variation
      double N = Math.pow(10.0, logA) / Math.pow(tdpStressRange, m);
      tdpFatigueDamage = waveCyclesPerYear / N;
    }

    // Heave fatigue damage
    double heaveFatigueDamage = 0.0;
    double heaveCyclesPerYear = 365.25 * 24 * 3600 / platformHeavePeriod;
    if (heaveInducedStress > 0) {
      double N = Math.pow(10.0, logA) / Math.pow(heaveInducedStress, m);
      heaveFatigueDamage = heaveCyclesPerYear / N;
    }

    // Total damage rate (Miner's rule)
    totalFatigueDamageRate =
        vivFatigueDamage + waveFatigueDamage + tdpFatigueDamage + heaveFatigueDamage;

    // Fatigue life
    if (totalFatigueDamageRate > 0) {
      riserFatigueLife = 1.0 / totalFatigueDamageRate;
    } else {
      riserFatigueLife = 1000.0; // Effectively infinite
    }

    // Apply design factor of safety (typically 10 for inspection, 3 for no inspection)
    double designFatigueFactor = 10.0;
    riserFatigueLife = riserFatigueLife / designFatigueFactor;

    return riserFatigueLife;
  }

  // ============================================================================
  // COLLAPSE CALCULATIONS
  // ============================================================================

  /**
   * Calculate external pressure at a given depth.
   *
   * @param depth water depth in meters
   * @return external pressure in MPa
   */
  public double calculateExternalPressure(double depth) {
    // P = ρ × g × h
    double pressure = SEAWATER_DENSITY * GRAVITY * depth / 1e6; // MPa
    setExternalPressure(pressure);
    return pressure;
  }

  /**
   * Calculate collapse utilization.
   *
   * @return utilization factor (should be &lt; 1.0)
   */
  public double calculateCollapseUtilization() {
    double Pc = calculateCollapsePressure();
    double Pe = SEAWATER_DENSITY * GRAVITY * waterDepth / 1e6; // MPa

    if (Pc > 0) {
      collapseUtilization = Pe / Pc;
    } else {
      collapseUtilization = 0.0;
    }

    // Calculate stress utilization
    double allowableStress = getSmys() * 0.67; // DNV limit
    double actualStress = getHoopStress();
    if (actualStress <= 0) {
      calculateHoopStress(getDesignPressure());
      actualStress = getHoopStress();
    }

    if (allowableStress > 0) {
      maxStressUtilization = actualStress / allowableStress;
    }

    return collapseUtilization;
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Get riser type.
   *
   * @return riser type name
   */
  public String getRiserType() {
    return riserType;
  }

  /**
   * Set riser type.
   *
   * @param riserType type name
   */
  public void setRiserType(String riserType) {
    this.riserType = riserType;
  }

  /**
   * Get water depth.
   *
   * @return depth in meters
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }

  /**
   * Get top angle.
   *
   * @return angle in degrees from vertical
   */
  public double getTopAngle() {
    return topAngle;
  }

  /**
   * Set top angle.
   *
   * @param topAngle angle in degrees from vertical
   */
  public void setTopAngle(double topAngle) {
    this.topAngle = topAngle;
  }

  /**
   * Get departure angle.
   *
   * @return angle in degrees
   */
  public double getDepartureAngle() {
    return departureAngle;
  }

  /**
   * Set departure angle.
   *
   * @param departureAngle angle in degrees
   */
  public void setDepartureAngle(double departureAngle) {
    this.departureAngle = departureAngle;
  }

  /**
   * Get current velocity.
   *
   * @return velocity in m/s
   */
  public double getCurrentVelocity() {
    return currentVelocity;
  }

  /**
   * Set current velocity.
   *
   * @param currentVelocity velocity in m/s
   */
  public void setCurrentVelocity(double currentVelocity) {
    this.currentVelocity = currentVelocity;
  }

  /**
   * Get seabed current velocity.
   *
   * @return velocity in m/s
   */
  public double getSeabedCurrentVelocity() {
    return seabedCurrentVelocity;
  }

  /**
   * Set seabed current velocity.
   *
   * @param seabedCurrentVelocity velocity in m/s
   */
  public void setSeabedCurrentVelocity(double seabedCurrentVelocity) {
    this.seabedCurrentVelocity = seabedCurrentVelocity;
  }

  /**
   * Get significant wave height.
   *
   * @return height in meters
   */
  public double getSignificantWaveHeight() {
    return significantWaveHeight;
  }

  /**
   * Set significant wave height.
   *
   * @param significantWaveHeight height in meters
   */
  public void setSignificantWaveHeight(double significantWaveHeight) {
    this.significantWaveHeight = significantWaveHeight;
  }

  /**
   * Get peak wave period.
   *
   * @return period in seconds
   */
  public double getPeakWavePeriod() {
    return peakWavePeriod;
  }

  /**
   * Set peak wave period.
   *
   * @param peakWavePeriod period in seconds
   */
  public void setPeakWavePeriod(double peakWavePeriod) {
    this.peakWavePeriod = peakWavePeriod;
  }

  /**
   * Get platform heave amplitude.
   *
   * @return amplitude in meters
   */
  public double getPlatformHeaveAmplitude() {
    return platformHeaveAmplitude;
  }

  /**
   * Set platform heave amplitude.
   *
   * @param platformHeaveAmplitude amplitude in meters
   */
  public void setPlatformHeaveAmplitude(double platformHeaveAmplitude) {
    this.platformHeaveAmplitude = platformHeaveAmplitude;
  }

  /**
   * Get platform heave period.
   *
   * @return period in seconds
   */
  public double getPlatformHeavePeriod() {
    return platformHeavePeriod;
  }

  /**
   * Set platform heave period.
   *
   * @param platformHeavePeriod period in seconds
   */
  public void setPlatformHeavePeriod(double platformHeavePeriod) {
    this.platformHeavePeriod = platformHeavePeriod;
  }

  /**
   * Get seabed friction.
   *
   * @return friction coefficient
   */
  public double getSeabedFriction() {
    return seabedFriction;
  }

  /**
   * Set seabed friction.
   *
   * @param seabedFriction friction coefficient
   */
  public void setSeabedFriction(double seabedFriction) {
    this.seabedFriction = seabedFriction;
  }

  /**
   * Get applied top tension.
   *
   * @return tension in kN
   */
  public double getAppliedTopTension() {
    return appliedTopTension;
  }

  /**
   * Set applied top tension for TTR.
   *
   * @param appliedTopTension tension in kN
   */
  public void setAppliedTopTension(double appliedTopTension) {
    this.appliedTopTension = appliedTopTension;
  }

  /**
   * Get tension variation factor.
   *
   * @return factor
   */
  public double getTensionVariationFactor() {
    return tensionVariationFactor;
  }

  /**
   * Set tension variation factor.
   *
   * @param tensionVariationFactor factor
   */
  public void setTensionVariationFactor(double tensionVariationFactor) {
    this.tensionVariationFactor = tensionVariationFactor;
  }

  /**
   * Get buoyancy module depth.
   *
   * @return depth in meters
   */
  public double getBuoyancyModuleDepth() {
    return buoyancyModuleDepth;
  }

  /**
   * Set buoyancy module depth.
   *
   * @param buoyancyModuleDepth depth in meters
   */
  public void setBuoyancyModuleDepth(double buoyancyModuleDepth) {
    this.buoyancyModuleDepth = buoyancyModuleDepth;
  }

  /**
   * Get buoyancy module length.
   *
   * @return length in meters
   */
  public double getBuoyancyModuleLength() {
    return buoyancyModuleLength;
  }

  /**
   * Set buoyancy module length.
   *
   * @param buoyancyModuleLength length in meters
   */
  public void setBuoyancyModuleLength(double buoyancyModuleLength) {
    this.buoyancyModuleLength = buoyancyModuleLength;
  }

  /**
   * Get buoyancy per meter.
   *
   * @return buoyancy in N/m
   */
  public double getBuoyancyPerMeter() {
    return buoyancyPerMeter;
  }

  /**
   * Set buoyancy per meter.
   *
   * @param buoyancyPerMeter buoyancy in N/m
   */
  public void setBuoyancyPerMeter(double buoyancyPerMeter) {
    this.buoyancyPerMeter = buoyancyPerMeter;
  }

  // ============ Configurable Design Parameter Setters ============

  /**
   * Set Strouhal number for VIV calculation.
   *
   * @param strouhalNumber Strouhal number (typically 0.18-0.22 per DNV-RP-C205)
   */
  public void setStrouhalNumber(double strouhalNumber) {
    this.strouhalNumber = strouhalNumber;
  }

  /**
   * Get Strouhal number.
   *
   * @return Strouhal number
   */
  public double getStrouhalNumber() {
    return strouhalNumber;
  }

  /**
   * Set drag coefficient.
   *
   * @param dragCoefficient drag coefficient (typically 0.9-1.2 per DNV-RP-C205)
   */
  public void setDragCoefficient(double dragCoefficient) {
    this.dragCoefficient = dragCoefficient;
  }

  /**
   * Get drag coefficient.
   *
   * @return drag coefficient
   */
  public double getDragCoefficient() {
    return dragCoefficient;
  }

  /**
   * Set added mass coefficient.
   *
   * @param addedMassCoefficient added mass coefficient (typically 1.0 per DNV-RP-C205)
   */
  public void setAddedMassCoefficient(double addedMassCoefficient) {
    this.addedMassCoefficient = addedMassCoefficient;
  }

  /**
   * Get added mass coefficient.
   *
   * @return added mass coefficient
   */
  public double getAddedMassCoefficient() {
    return addedMassCoefficient;
  }

  /**
   * Set lift coefficient for VIV.
   *
   * @param liftCoefficient lift coefficient (typically 0.8-1.0 per DNV-RP-C205)
   */
  public void setLiftCoefficient(double liftCoefficient) {
    this.liftCoefficient = liftCoefficient;
  }

  /**
   * Get lift coefficient.
   *
   * @return lift coefficient
   */
  public double getLiftCoefficient() {
    return liftCoefficient;
  }

  /**
   * Set S-N curve parameter log(a) for fatigue calculation.
   *
   * @param snParameter S-N curve parameter (12.164 for seawater with CP per DNV-RP-C203)
   */
  public void setSnParameter(double snParameter) {
    this.snParameter = snParameter;
  }

  /**
   * Get S-N curve parameter.
   *
   * @return S-N curve parameter log(a)
   */
  public double getSnParameter() {
    return snParameter;
  }

  /**
   * Set S-N curve slope parameter m.
   *
   * @param snSlope slope parameter (typically 3.0 per DNV-RP-C203)
   */
  public void setSnSlope(double snSlope) {
    this.snSlope = snSlope;
  }

  /**
   * Get S-N curve slope.
   *
   * @return S-N curve slope parameter m
   */
  public double getSnSlope() {
    return snSlope;
  }

  /**
   * Set fatigue design factor.
   *
   * @param fatigueDesignFactor fatigue design factor (3-10 per DNV-RP-F204)
   */
  public void setFatigueDesignFactor(double fatigueDesignFactor) {
    this.fatigueDesignFactor = fatigueDesignFactor;
  }

  /**
   * Get fatigue design factor.
   *
   * @return fatigue design factor
   */
  public double getFatigueDesignFactor() {
    return fatigueDesignFactor;
  }

  /**
   * Set dynamic amplification factor.
   *
   * @param dynamicAmplificationFactor DAF (typically 1.1-1.3 per DNV-OS-F201)
   */
  public void setDynamicAmplificationFactor(double dynamicAmplificationFactor) {
    this.dynamicAmplificationFactor = dynamicAmplificationFactor;
  }

  /**
   * Get dynamic amplification factor.
   *
   * @return dynamic amplification factor
   */
  public double getDynamicAmplificationFactor() {
    return dynamicAmplificationFactor;
  }

  /**
   * Set stress concentration factor for girth welds.
   *
   * @param stressConcentrationFactor SCF (typically 1.2-1.5 per DNV-RP-F204)
   */
  public void setStressConcentrationFactor(double stressConcentrationFactor) {
    this.stressConcentrationFactor = stressConcentrationFactor;
  }

  /**
   * Get stress concentration factor.
   *
   * @return stress concentration factor
   */
  public double getStressConcentrationFactor() {
    return stressConcentrationFactor;
  }

  // ============ Calculated Result Getters ============

  /**
   * Get calculated top tension.
   *
   * @return tension in kN
   */
  public double getTopTension() {
    return topTension;
  }

  /**
   * Get minimum top tension.
   *
   * @return tension in kN
   */
  public double getMinTopTension() {
    return minTopTension;
  }

  /**
   * Get maximum top tension.
   *
   * @return tension in kN
   */
  public double getMaxTopTension() {
    return maxTopTension;
  }

  /**
   * Get bottom tension.
   *
   * @return tension in kN
   */
  public double getBottomTension() {
    return bottomTension;
  }

  /**
   * Get catenary parameter.
   *
   * @return catenary parameter in meters
   */
  public double getCatenaryParameter() {
    return catenaryParameter;
  }

  /**
   * Get touchdown point stress.
   *
   * @return stress in MPa
   */
  public double getTouchdownPointStress() {
    return touchdownPointStress;
  }

  /**
   * Get touchdown curvature radius.
   *
   * @return radius in meters
   */
  public double getTouchdownCurvatureRadius() {
    return touchdownCurvatureRadius;
  }

  /**
   * Get touchdown zone length.
   *
   * @return length in meters
   */
  public double getTouchdownZoneLength() {
    return touchdownZoneLength;
  }

  /**
   * Get touchdown bending moment.
   *
   * @return moment in kN.m
   */
  public double getTouchdownBendingMoment() {
    return touchdownBendingMoment;
  }

  /**
   * Get vortex shedding frequency.
   *
   * @return frequency in Hz
   */
  public double getVortexSheddingFrequency() {
    return vortexSheddingFrequency;
  }

  /**
   * Get natural frequency.
   *
   * @return frequency in Hz
   */
  public double getNaturalFrequency() {
    return naturalFrequency;
  }

  /**
   * Get VIV amplitude.
   *
   * @return A/D ratio
   */
  public double getVIVAmplitude() {
    return vivAmplitude;
  }

  /**
   * Get VIV stress range.
   *
   * @return stress in MPa
   */
  public double getVIVStressRange() {
    return vivStressRange;
  }

  /**
   * Get VIV fatigue damage per year.
   *
   * @return damage rate
   */
  public double getVIVFatigueDamage() {
    return vivFatigueDamage;
  }

  /**
   * Check if VIV lock-in is occurring.
   *
   * @return true if lock-in
   */
  public boolean isVIVLockIn() {
    return vivLockIn;
  }

  /**
   * Get wave-induced stress.
   *
   * @return stress in MPa
   */
  public double getWaveInducedStress() {
    return waveInducedStress;
  }

  /**
   * Get heave-induced stress.
   *
   * @return stress in MPa
   */
  public double getHeaveInducedStress() {
    return heaveInducedStress;
  }

  /**
   * Get combined dynamic stress.
   *
   * @return stress in MPa
   */
  public double getCombinedDynamicStress() {
    return combinedDynamicStress;
  }

  /**
   * Get stroke requirement for TTR.
   *
   * @return stroke in meters
   */
  public double getStrokeRequirement() {
    return strokeRequirement;
  }

  /**
   * Get riser fatigue life.
   *
   * @return life in years
   */
  public double getRiserFatigueLife() {
    return riserFatigueLife;
  }

  /**
   * Get total fatigue damage rate.
   *
   * @return damage per year
   */
  public double getTotalFatigueDamageRate() {
    return totalFatigueDamageRate;
  }

  /**
   * Get wave fatigue damage per year.
   *
   * @return damage rate
   */
  public double getWaveFatigueDamage() {
    return waveFatigueDamage;
  }

  /**
   * Get collapse utilization.
   *
   * @return utilization factor
   */
  public double getCollapseUtilization() {
    return collapseUtilization;
  }

  /**
   * Get maximum stress utilization.
   *
   * @return utilization factor
   */
  public double getMaxStressUtilization() {
    return maxStressUtilization;
  }

  /**
   * Set external pressure.
   *
   * @param pressure pressure in MPa
   */
  private void setExternalPressure(double pressure) {
    // Store in parent if method exists
  }

  // ============================================================================
  // JSON OUTPUT
  // ============================================================================

  /**
   * Get riser-specific results as a map.
   *
   * @return map of results
   */
  public Map<String, Object> toRiserMap() {
    Map<String, Object> result = new HashMap<String, Object>();

    // Configuration
    Map<String, Object> config = new HashMap<String, Object>();
    config.put("riserType", riserType);
    config.put("waterDepth_m", waterDepth);
    config.put("topAngle_deg", topAngle);
    config.put("departureAngle_deg", departureAngle);
    result.put("configuration", config);

    // Environmental conditions
    Map<String, Object> environment = new HashMap<String, Object>();
    environment.put("currentVelocity_m_s", currentVelocity);
    environment.put("seabedCurrentVelocity_m_s", seabedCurrentVelocity);
    environment.put("significantWaveHeight_m", significantWaveHeight);
    environment.put("peakWavePeriod_s", peakWavePeriod);
    environment.put("platformHeaveAmplitude_m", platformHeaveAmplitude);
    environment.put("platformHeavePeriod_s", platformHeavePeriod);
    environment.put("seabedFriction", seabedFriction);
    result.put("environmentalConditions", environment);

    // Tension analysis
    Map<String, Object> tension = new HashMap<String, Object>();
    tension.put("topTension_kN", topTension);
    tension.put("minTopTension_kN", minTopTension);
    tension.put("maxTopTension_kN", maxTopTension);
    tension.put("bottomTension_kN", bottomTension);
    tension.put("catenaryParameter_m", catenaryParameter);
    tension.put("strokeRequirement_m", strokeRequirement);
    result.put("tensionAnalysis", tension);

    // Touchdown analysis
    Map<String, Object> touchdown = new HashMap<String, Object>();
    touchdown.put("touchdownPointStress_MPa", touchdownPointStress);
    touchdown.put("touchdownCurvatureRadius_m", touchdownCurvatureRadius);
    touchdown.put("touchdownZoneLength_m", touchdownZoneLength);
    touchdown.put("touchdownBendingMoment_kN_m", touchdownBendingMoment);
    result.put("touchdownAnalysis", touchdown);

    // VIV analysis
    Map<String, Object> viv = new HashMap<String, Object>();
    viv.put("vortexSheddingFrequency_Hz", vortexSheddingFrequency);
    viv.put("naturalFrequency_Hz", naturalFrequency);
    viv.put("frequencyRatio",
        naturalFrequency > 0 ? vortexSheddingFrequency / naturalFrequency : 0.0);
    viv.put("vivAmplitude_A_D", vivAmplitude);
    viv.put("vivStressRange_MPa", vivStressRange);
    viv.put("vivLockIn", vivLockIn);
    viv.put("vivFatigueDamagePerYear", vivFatigueDamage);
    result.put("vivAnalysis", viv);

    // Dynamic stress
    Map<String, Object> dynamic = new HashMap<String, Object>();
    dynamic.put("waveInducedStress_MPa", waveInducedStress);
    dynamic.put("heaveInducedStress_MPa", heaveInducedStress);
    dynamic.put("combinedDynamicStress_MPa", combinedDynamicStress);
    result.put("dynamicStressAnalysis", dynamic);

    // Fatigue analysis
    Map<String, Object> fatigue = new HashMap<String, Object>();
    fatigue.put("riserFatigueLife_years", riserFatigueLife);
    fatigue.put("totalFatigueDamageRatePerYear", totalFatigueDamageRate);
    fatigue.put("vivFatigueDamagePerYear", vivFatigueDamage);
    fatigue.put("waveFatigueDamagePerYear", waveFatigueDamage);
    result.put("fatigueAnalysis", fatigue);

    // Utilization checks
    Map<String, Object> utilization = new HashMap<String, Object>();
    utilization.put("collapseUtilization", collapseUtilization);
    utilization.put("maxStressUtilization", maxStressUtilization);
    utilization.put("collapseAcceptable", collapseUtilization < 1.0);
    utilization.put("stressAcceptable", maxStressUtilization < 1.0);
    result.put("utilizationChecks", utilization);

    return result;
  }

  /**
   * Get riser-specific results as JSON.
   *
   * @return JSON string
   */
  public String toRiserJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toRiserMap());
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> result = super.toMap();
    result.put("riserDesign", toRiserMap());
    return result;
  }
}
