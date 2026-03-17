package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calculator for well casing and tubing mechanical design.
 *
 * <p>
 * Provides engineering calculations for subsea well tubular design per:
 * </p>
 * <ul>
 * <li>API 5CT / ISO 11960 - Casing and Tubing grades and properties</li>
 * <li>API Bull 5C3 - Formulas for burst, collapse, and tension</li>
 * <li>NORSOK D-010 - Well integrity design factors</li>
 * <li>API RP 90 - Annular casing pressure management</li>
 * </ul>
 *
 * <h2>Casing Design Loads</h2>
 * <ul>
 * <li><b>Burst</b> - Internal pressure exceeds external (kick, displacement to gas)</li>
 * <li><b>Collapse</b> - External pressure exceeds internal (cement, lost returns)</li>
 * <li><b>Tension</b> - Axial load from casing weight plus running/landing loads</li>
 * </ul>
 *
 * <h2>Design Factors (NORSOK D-010 / API)</h2>
 *
 * <table>
 * <caption>Minimum design factors for casing design</caption>
 * <tr>
 * <th>Load Case</th>
 * <th>Factor</th>
 * </tr>
 * <tr>
 * <td>Burst</td>
 * <td>1.10</td>
 * </tr>
 * <tr>
 * <td>Collapse</td>
 * <td>1.00</td>
 * </tr>
 * <tr>
 * <td>Tension</td>
 * <td>1.60</td>
 * </tr>
 * <tr>
 * <td>Triaxial (VME)</td>
 * <td>1.25</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 * @see WellMechanicalDesign
 */
public class WellDesignCalculator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ============ Well Geometry ============
  /** Measured depth in meters. */
  private double measuredDepth = 3800.0;

  /** True vertical depth in meters. */
  private double trueVerticalDepth = 3200.0;

  /** Water depth in meters. */
  private double waterDepth = 350.0;

  // ============ Pressure / Temperature ============
  /** Max wellhead pressure in bara. */
  private double maxWellheadPressure = 345.0;

  /** Reservoir pressure in bara. */
  private double reservoirPressure = 400.0;

  /** Reservoir temperature in Celsius. */
  private double reservoirTemperature = 100.0;

  /** Max bottomhole temperature in Celsius. */
  private double maxBottomholeTemperature = 120.0;

  // ============ Casing Program ============
  /** Conductor OD in inches. */
  private double conductorOD = 30.0;

  /** Conductor depth in meters MD. */
  private double conductorDepth = 100.0;

  /** Surface casing OD in inches. */
  private double surfaceCasingOD = 20.0;

  /** Surface casing depth in meters MD. */
  private double surfaceCasingDepth = 800.0;

  /** Intermediate casing OD in inches. */
  private double intermediateCasingOD = 13.375;

  /** Intermediate casing depth in meters MD. */
  private double intermediateCasingDepth = 2500.0;

  /** Production casing OD in inches. */
  private double productionCasingOD = 9.625;

  /** Production casing depth in meters MD. */
  private double productionCasingDepth = 3800.0;

  /** Production liner OD in inches. */
  private double productionLinerOD = 7.0;

  /** Production liner depth in meters MD. */
  private double productionLinerDepth = 0.0;

  // ============ Tubing ============
  /** Tubing OD in inches. */
  private double tubingOD = 5.5;

  /** Tubing weight in lb/ft. */
  private double tubingWeight = 23.0;

  /** Tubing grade. */
  private String tubingGrade = "L80";

  // ============ Calculated Results — Casing ============
  /** Production casing wall thickness in mm. */
  private double productionCasingWallThickness = 0.0;

  /** Intermediate casing wall thickness in mm. */
  private double intermediateCasingWallThickness = 0.0;

  /** Surface casing wall thickness in mm. */
  private double surfaceCasingWallThickness = 0.0;

  /** Production casing burst design factor. */
  private double productionCasingBurstDF = 0.0;

  /** Production casing collapse design factor. */
  private double productionCasingCollapseDF = 0.0;

  /** Production casing tension design factor. */
  private double productionCasingTensionDF = 0.0;

  /** Tubing wall thickness in mm. */
  private double tubingWallThickness = 0.0;

  /** Tubing burst design factor. */
  private double tubingBurstDF = 0.0;

  // ============ Weights ============
  /** Total casing weight in tonnes. */
  private double totalCasingWeight = 0.0;

  /** Total tubing weight in tonnes. */
  private double totalTubingWeight = 0.0;

  /** Total cement volume in m3. */
  private double totalCementVolume = 0.0;

  /** Total cuttings volume in m3. */
  private double totalCuttingsVolume = 0.0;

  /** Production casing VME (triaxial) design factor. */
  private double productionCasingVME_DF = 0.0;

  /** Temperature derating factor applied to production casing (0-1). */
  private double temperatureDeratingFactor = 1.0;

  // ============ Design Factors (NORSOK D-010) ============
  /** Minimum burst design factor. */
  private static final double MIN_BURST_DF = 1.10;

  /** Minimum collapse design factor. */
  private static final double MIN_COLLAPSE_DF = 1.00;

  /** Minimum tension design factor. */
  private static final double MIN_TENSION_DF = 1.60;

  /** Minimum triaxial (von Mises Equivalent) design factor per NORSOK D-010. */
  private static final double MIN_VME_DF = 1.25;

  /** Pore pressure gradient (bar/m) — typical hydrostatic. */
  private static final double PORE_PRESSURE_GRADIENT = 0.1013;

  /** Fracture pressure gradient (bar/m) — typical. */
  private static final double FRAC_GRADIENT = 0.17;

  /** Steel density kg/m3. */
  private static final double STEEL_DENSITY = 7850.0;

  /** Seawater density kg/m3. */
  private static final double SEAWATER_DENSITY = 1025.0;

  /** Cement slurry density kg/m3. */
  private static final double CEMENT_DENSITY = 1900.0;

  /**
   * Default constructor.
   */
  public WellDesignCalculator() {}

  /**
   * Calculate casing design for all strings.
   *
   * <p>
   * Performs burst, collapse, and tension checks per API Bull 5C3 / NORSOK D-010.
   * </p>
   */
  public void calculateCasingDesign() {
    calculateProductionCasing();
    calculateIntermediateCasing();
    calculateSurfaceCasing();
  }

  /**
   * Calculate production casing design.
   */
  private void calculateProductionCasing() {
    // Get SMYS for typical production casing grade (P110)
    double smys = getCasingGradeSMYS("P110"); // 758 MPa

    // ---- Burst Load ----
    // Worst case: full reservoir pressure at surface (gas-filled)
    double burstPressure = reservoirPressure; // bara at surface
    // External pressure at shoe (mud hydrostatic)
    double externalAtShoe = PORE_PRESSURE_GRADIENT * productionCasingDepth;
    // Net burst at surface
    double netBurstSurface = maxWellheadPressure;

    // Barlow burst formula: P_burst = 2 * SMYS * t / OD
    // Required: P_rated >= DF * P_design
    double requiredBurstRating = MIN_BURST_DF * netBurstSurface; // bara

    // OD in mm
    double odMm = productionCasingOD * 25.4;
    // Required wall thickness for burst: t = P * OD / (2 * SMYS)
    // Convert bara to MPa: 1 bara = 0.1 MPa
    double requiredBurstThickness = (requiredBurstRating * 0.1 * odMm) / (2.0 * smys);

    // ---- Collapse Load ----
    // Worst case: empty string opposite full mud/cement column
    double collapsePressure = PORE_PRESSURE_GRADIENT * productionCasingDepth;
    double requiredCollapseRating = MIN_COLLAPSE_DF * collapsePressure;

    // Simplified collapse (yield-strength collapse for D/t < 15):
    // P_collapse = 2 * SMYS * ((D/t - 1) / (D/t)^2)
    // For required thickness: approximate iteratively
    double requiredCollapseThickness =
        estimateCollapseThickness(odMm, requiredCollapseRating * 0.1, smys);

    // ---- Take maximum ----
    productionCasingWallThickness = Math.max(requiredBurstThickness, requiredCollapseThickness);
    // API minimum wall thickness for 9-5/8" casing ~ 8.9 mm (API 5CT catalog)
    productionCasingWallThickness = Math.max(productionCasingWallThickness, 8.9);

    // Calculate actual design factors with selected thickness
    double actualBurstRating = (2.0 * smys * productionCasingWallThickness) / (odMm * 0.1);
    productionCasingBurstDF = netBurstSurface > 0 ? actualBurstRating / netBurstSurface : 99.0;

    double dt = odMm / productionCasingWallThickness;
    double actualCollapseRating = 2.0 * smys * ((dt - 1.0) / (dt * dt)) / 0.1;
    productionCasingCollapseDF =
        collapsePressure > 0 ? actualCollapseRating / collapsePressure : 99.0;

    // ---- Tension ----
    // Weight in air
    double casingWeightKg = calculatePipeWeight(productionCasingOD, productionCasingWallThickness,
        productionCasingDepth);
    double tensionLoad = casingWeightKg * 9.81 / 1000.0; // kN

    // Yield strength for tension = SMYS * cross-section area
    double crossSection =
        Math.PI / 4.0 * (Math.pow(odMm, 2) - Math.pow(odMm - 2 * productionCasingWallThickness, 2));
    double yieldTension = smys * crossSection / 1000.0; // kN

    productionCasingTensionDF = tensionLoad > 0 ? yieldTension / tensionLoad : 99.0;

    // ---- VME triaxial check (NORSOK D-010 / API TR 5C3) ----
    // von Mises Equivalent stress combines hoop, axial, and radial stresses
    double derated = applyTemperatureDerating(smys, maxBottomholeTemperature);
    temperatureDeratingFactor = derated / smys;

    double hoopStress = netBurstSurface * 0.1 * odMm / (2.0 * productionCasingWallThickness);
    double axialStress = tensionLoad * 1000.0 / crossSection; // kN to N, area in mm2 gives MPa
    double radialStress = -netBurstSurface * 0.1 / 2.0; // approximation

    double vmeStress = Math.sqrt(0.5 * (Math.pow(hoopStress - axialStress, 2)
        + Math.pow(axialStress - radialStress, 2) + Math.pow(radialStress - hoopStress, 2)));
    productionCasingVME_DF = vmeStress > 0 ? derated / vmeStress : 99.0;
  }

  /**
   * Calculate intermediate casing design.
   */
  private void calculateIntermediateCasing() {
    if (intermediateCasingDepth <= 0) {
      return;
    }

    double smys = getCasingGradeSMYS("N80"); // 552 MPa
    double odMm = intermediateCasingOD * 25.4;

    // Burst: kick tolerance scenario
    double burstPressure = FRAC_GRADIENT * intermediateCasingDepth;
    double requiredBurstRating = MIN_BURST_DF * burstPressure;
    double burstThickness = (requiredBurstRating * 0.1 * odMm) / (2.0 * smys);

    // Collapse: lost returns with partial evacuation
    double collapsePressure = PORE_PRESSURE_GRADIENT * intermediateCasingDepth;
    double collapseThickness = estimateCollapseThickness(odMm, collapsePressure * 0.1, smys);

    intermediateCasingWallThickness = Math.max(burstThickness, collapseThickness);
    intermediateCasingWallThickness = Math.max(intermediateCasingWallThickness, 10.5);
  }

  /**
   * Calculate surface casing design.
   */
  private void calculateSurfaceCasing() {
    double smys = getCasingGradeSMYS("K55"); // 379 MPa
    double odMm = surfaceCasingOD * 25.4;

    // Burst: kick scenario
    double burstPressure = FRAC_GRADIENT * surfaceCasingDepth;
    double requiredBurstRating = MIN_BURST_DF * burstPressure;
    double burstThickness = (requiredBurstRating * 0.1 * odMm) / (2.0 * smys);

    // Minimum for 20" casing ~ 12.7 mm
    surfaceCasingWallThickness = Math.max(burstThickness, 12.7);
  }

  /**
   * Calculate tubing design.
   */
  public void calculateTubingDesign() {
    double smys = getCasingGradeSMYS(tubingGrade);
    double odMm = tubingOD * 25.4;

    // Tubing burst: SITP (shut-in tubing pressure)
    double tubeDesignPressure = maxWellheadPressure;
    double requiredBurstRating = MIN_BURST_DF * tubeDesignPressure;

    tubingWallThickness = (requiredBurstRating * 0.1 * odMm) / (2.0 * smys);
    // Minimum from API 5CT catalog for 5.5" tubing ~ 6.4 mm
    tubingWallThickness = Math.max(tubingWallThickness, 6.4);

    // Actual burst rating
    double actualRating = (2.0 * smys * tubingWallThickness) / (odMm * 0.1);
    tubingBurstDF = tubeDesignPressure > 0 ? actualRating / tubeDesignPressure : 99.0;
  }

  /**
   * Calculate total weights for all strings.
   */
  public void calculateWeights() {
    totalCasingWeight = 0.0;
    totalTubingWeight = 0.0;

    // Conductor
    double conductorWeight = calculatePipeWeight(conductorOD, 25.4, conductorDepth); // 1" wall
                                                                                     // typical
    totalCasingWeight += conductorWeight / 1000.0; // kg to tonnes

    // Surface casing
    double surfaceWeight =
        calculatePipeWeight(surfaceCasingOD, surfaceCasingWallThickness, surfaceCasingDepth);
    totalCasingWeight += surfaceWeight / 1000.0;

    // Intermediate casing
    if (intermediateCasingDepth > 0) {
      double intWeight = calculatePipeWeight(intermediateCasingOD, intermediateCasingWallThickness,
          intermediateCasingDepth);
      totalCasingWeight += intWeight / 1000.0;
    }

    // Production casing
    double prodWeight = calculatePipeWeight(productionCasingOD, productionCasingWallThickness,
        productionCasingDepth);
    totalCasingWeight += prodWeight / 1000.0;

    // Production liner
    if (productionLinerDepth > 0) {
      double linerLength = productionLinerDepth - productionCasingDepth;
      if (linerLength > 0) {
        double linerWeight = calculatePipeWeight(productionLinerOD, 8.0, linerLength);
        totalCasingWeight += linerWeight / 1000.0;
      }
    }

    // Tubing (from wellhead to production packer, roughly production casing depth)
    double tubingLength = productionCasingDepth - waterDepth;
    if (tubingLength > 0) {
      // Tubing weight from lb/ft
      totalTubingWeight = tubingWeight * 0.4536 * tubingLength / 0.3048 / 1000.0;
    }
  }

  /**
   * Calculate cement volumes for all annuli.
   */
  public void calculateCementVolumes() {
    totalCementVolume = 0.0;
    totalCuttingsVolume = 0.0;

    // Surface casing cement (full column, wellhead to shoe)
    totalCementVolume += calculateAnnularVolume(surfaceCasingOD, surfaceCasingWallThickness,
        conductorOD, 25.4, surfaceCasingDepth);

    // Intermediate cement (typically from shoe up to overlap with surface casing)
    if (intermediateCasingDepth > 0) {
      double cementLength = intermediateCasingDepth - surfaceCasingDepth + 200; // 200m overlap
      totalCementVolume +=
          calculateAnnularVolume(intermediateCasingOD, intermediateCasingWallThickness,
              surfaceCasingOD, surfaceCasingWallThickness, cementLength);
    }

    // Production casing cement (shoe to 200m above previous shoe)
    double prodCementLength = productionCasingDepth - intermediateCasingDepth + 200;
    totalCementVolume += calculateAnnularVolume(productionCasingOD, productionCasingWallThickness,
        intermediateCasingOD, intermediateCasingWallThickness, prodCementLength);

    // Drill cuttings: total hole volume minus casing volume
    totalCuttingsVolume = calculateHoleVolume();
  }

  /**
   * Calculate pipe weight in kg.
   *
   * @param outerDiameterInches OD in inches
   * @param wallThicknessMm wall thickness in mm
   * @param lengthM length in meters
   * @return weight in kg
   */
  private double calculatePipeWeight(double outerDiameterInches, double wallThicknessMm,
      double lengthM) {
    double odM = outerDiameterInches * 0.0254;
    double tM = wallThicknessMm / 1000.0;
    double idM = odM - 2.0 * tM;
    double crossSection = Math.PI / 4.0 * (odM * odM - idM * idM);
    return crossSection * lengthM * STEEL_DENSITY;
  }

  /**
   * Calculate annular volume between two concentric casings.
   *
   * @param outerOD outer casing OD in inches
   * @param outerWT outer casing wall thickness in mm
   * @param innerOD inner casing OD in inches
   * @param innerWT inner casing wall thickness in mm (unused for hole wall)
   * @param lengthM length in meters
   * @return volume in m3
   */
  private double calculateAnnularVolume(double outerOD, double outerWT, double innerOD,
      double innerWT, double lengthM) {
    // Hole ID = bit size ≈ outer casing OD + clearance
    double holeID = outerOD * 0.0254 + 0.05; // ~2" clearance
    double casingOD = innerOD * 0.0254;
    // Annular volume between hole and casing
    double annularArea = Math.PI / 4.0 * (holeID * holeID - casingOD * casingOD);
    // Use casing OD for cement in annulus
    return Math.abs(annularArea * lengthM);
  }

  /**
   * Calculate total drilled hole volume.
   *
   * @return hole volume in m3
   */
  private double calculateHoleVolume() {
    double volume = 0.0;
    // Each section drilled with different bit size
    // Conductor section: 36" hole
    double bit1 = 36.0 * 0.0254;
    volume += Math.PI / 4.0 * bit1 * bit1 * conductorDepth;

    // Surface section: ~26" hole
    double bit2 = 26.0 * 0.0254;
    volume += Math.PI / 4.0 * bit2 * bit2 * (surfaceCasingDepth - conductorDepth);

    // Intermediate section: ~17.5" hole
    if (intermediateCasingDepth > 0) {
      double bit3 = 17.5 * 0.0254;
      volume += Math.PI / 4.0 * bit3 * bit3 * (intermediateCasingDepth - surfaceCasingDepth);
    }

    // Production section: ~12.25" hole
    double bit4 = 12.25 * 0.0254;
    volume += Math.PI / 4.0 * bit4 * bit4
        * (productionCasingDepth - Math.max(intermediateCasingDepth, surfaceCasingDepth));

    return volume;
  }

  /**
   * Estimate required wall thickness for collapse resistance.
   *
   * <p>
   * Uses the API 5C3 yield-strength collapse formula for an initial estimate.
   * </p>
   *
   * @param odMm outer diameter in mm
   * @param collapsePressureMPa collapse pressure in MPa
   * @param smysMPa SMYS in MPa
   * @return required wall thickness in mm
   */
  private double estimateCollapseThickness(double odMm, double collapsePressureMPa,
      double smysMPa) {
    // Iterative: starting from thin wall, increase until collapse rating > required
    for (double t = 5.0; t < odMm / 2.0; t += 0.5) {
      double dt = odMm / t;
      // Yield-strength collapse (API 5C3 simplified)
      double collapseRating = 2.0 * smysMPa * ((dt - 1.0) / (dt * dt));
      if (collapseRating >= collapsePressureMPa * MIN_COLLAPSE_DF) {
        return t;
      }
    }
    return odMm / 4.0; // Conservative fallback
  }

  /**
   * Apply API 5CT temperature derating to yield strength.
   *
   * <p>
   * Per API 5CT / ISO 11960, yield strength is derated at elevated temperatures. The derating
   * factors are from API TR 5C3 Table D.1 (typical carbon/low-alloy).
   * </p>
   *
   * @param smysMPa SMYS at ambient temperature in MPa
   * @param temperatureC design temperature in Celsius
   * @return derated yield strength in MPa
   */
  private double applyTemperatureDerating(double smysMPa, double temperatureC) {
    // API 5CT / API TR 5C3 temperature derating factors
    // Below 100 degC: no derating
    if (temperatureC <= 100.0) {
      return smysMPa;
    }
    // Linear interpolation of typical derating from API TR 5C3 Table D.1:
    // 100C -> 1.00, 150C -> 0.97, 200C -> 0.93, 250C -> 0.87, 300C -> 0.80
    double factor;
    if (temperatureC <= 150.0) {
      factor = 1.0 - 0.03 * (temperatureC - 100.0) / 50.0;
    } else if (temperatureC <= 200.0) {
      factor = 0.97 - 0.04 * (temperatureC - 150.0) / 50.0;
    } else if (temperatureC <= 250.0) {
      factor = 0.93 - 0.06 * (temperatureC - 200.0) / 50.0;
    } else if (temperatureC <= 300.0) {
      factor = 0.87 - 0.07 * (temperatureC - 250.0) / 50.0;
    } else {
      factor = 0.80; // Beyond 300C, use minimum factor
    }
    return smysMPa * factor;
  }

  /**
   * Get SMTS (Specified Minimum Tensile Strength) for a casing grade per API 5CT / ISO 11960.
   *
   * @param grade casing grade string (e.g., "L80", "P110", "K55")
   * @return SMTS in MPa
   */
  public double getCasingGradeSMTS(String grade) {
    // API 5CT Table C.6 / ISO 11960 tensile strength values
    if ("H40".equals(grade)) {
      return 414.0;
    } else if ("J55".equals(grade) || "K55".equals(grade)) {
      return 517.0;
    } else if ("N80".equals(grade) || "L80".equals(grade)) {
      return 689.0;
    } else if ("C90".equals(grade)) {
      return 689.0;
    } else if ("C95".equals(grade) || "T95".equals(grade)) {
      return 724.0;
    } else if ("P110".equals(grade)) {
      return 862.0;
    } else if ("Q125".equals(grade)) {
      return 931.0;
    } else if ("13Cr".equals(grade) || "13CR".equals(grade)) {
      return 689.0;
    } else if ("S13Cr".equals(grade) || "Super13Cr".equals(grade)) {
      return 724.0;
    } else if ("25Cr".equals(grade)) {
      return 862.0;
    }
    return 689.0; // Default to L80/N80 SMTS
  }

  /**
   * Get SMYS for a casing grade per API 5CT / ISO 11960.
   *
   * @param grade casing grade string (e.g., "L80", "P110", "K55")
   * @return SMYS in MPa
   */
  public double getCasingGradeSMYS(String grade) {
    // API 5CT casing grades and minimum yield strengths
    if ("H40".equals(grade)) {
      return 276.0;
    } else if ("J55".equals(grade) || "K55".equals(grade)) {
      return 379.0;
    } else if ("N80".equals(grade) || "L80".equals(grade)) {
      return 552.0;
    } else if ("C90".equals(grade)) {
      return 621.0;
    } else if ("C95".equals(grade) || "T95".equals(grade)) {
      return 655.0;
    } else if ("P110".equals(grade)) {
      return 758.0;
    } else if ("Q125".equals(grade)) {
      return 862.0;
    } else if ("13Cr".equals(grade) || "13CR".equals(grade)) {
      return 552.0; // Similar to L80 for 13Cr-L80
    } else if ("S13Cr".equals(grade) || "Super13Cr".equals(grade)) {
      return 621.0;
    } else if ("25Cr".equals(grade)) {
      return 758.0;
    }
    return 552.0; // Default to L80/N80
  }

  /**
   * Get design results as a Map for JSON export.
   *
   * @return map of all results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    Map<String, Object> casingDesign = new LinkedHashMap<String, Object>();
    casingDesign.put("productionCasingWallThicknessMm", productionCasingWallThickness);
    casingDesign.put("intermediateCasingWallThicknessMm", intermediateCasingWallThickness);
    casingDesign.put("surfaceCasingWallThicknessMm", surfaceCasingWallThickness);
    casingDesign.put("productionCasingBurstDF", productionCasingBurstDF);
    casingDesign.put("productionCasingCollapseDF", productionCasingCollapseDF);
    casingDesign.put("productionCasingTensionDF", productionCasingTensionDF);
    casingDesign.put("productionCasingVME_DF", productionCasingVME_DF);
    casingDesign.put("temperatureDeratingFactor", temperatureDeratingFactor);
    result.put("casingDesign", casingDesign);

    Map<String, Object> tubingDesign = new LinkedHashMap<String, Object>();
    tubingDesign.put("tubingWallThicknessMm", tubingWallThickness);
    tubingDesign.put("tubingBurstDF", tubingBurstDF);
    result.put("tubingDesign", tubingDesign);

    Map<String, Object> weights = new LinkedHashMap<String, Object>();
    weights.put("totalCasingWeightTonnes", totalCasingWeight);
    weights.put("totalTubingWeightTonnes", totalTubingWeight);
    weights.put("totalCementVolumeM3", totalCementVolume);
    weights.put("totalCuttingsVolumeM3", totalCuttingsVolume);
    result.put("weights", weights);

    return result;
  }

  // ============ Setters ============

  /**
   * Set measured depth.
   *
   * @param measuredDepth measured depth in meters
   */
  public void setMeasuredDepth(double measuredDepth) {
    this.measuredDepth = measuredDepth;
  }

  /**
   * Set true vertical depth.
   *
   * @param trueVerticalDepth TVD in meters
   */
  public void setTrueVerticalDepth(double trueVerticalDepth) {
    this.trueVerticalDepth = trueVerticalDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth water depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }

  /**
   * Set maximum wellhead pressure.
   *
   * @param maxWellheadPressure max wellhead pressure in bara
   */
  public void setMaxWellheadPressure(double maxWellheadPressure) {
    this.maxWellheadPressure = maxWellheadPressure;
  }

  /**
   * Set reservoir pressure.
   *
   * @param reservoirPressure reservoir pressure in bara
   */
  public void setReservoirPressure(double reservoirPressure) {
    this.reservoirPressure = reservoirPressure;
  }

  /**
   * Set reservoir temperature.
   *
   * @param reservoirTemperature reservoir temperature in Celsius
   */
  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Set max bottomhole temperature.
   *
   * @param maxBottomholeTemperature max BHT in Celsius
   */
  public void setMaxBottomholeTemperature(double maxBottomholeTemperature) {
    this.maxBottomholeTemperature = maxBottomholeTemperature;
  }

  /**
   * Set conductor casing properties.
   *
   * @param od outer diameter in inches
   * @param depth setting depth in meters MD
   */
  public void setConductorCasing(double od, double depth) {
    this.conductorOD = od;
    this.conductorDepth = depth;
  }

  /**
   * Set surface casing properties.
   *
   * @param od outer diameter in inches
   * @param depth setting depth in meters MD
   */
  public void setSurfaceCasing(double od, double depth) {
    this.surfaceCasingOD = od;
    this.surfaceCasingDepth = depth;
  }

  /**
   * Set intermediate casing properties.
   *
   * @param od outer diameter in inches
   * @param depth setting depth in meters MD
   */
  public void setIntermediateCasing(double od, double depth) {
    this.intermediateCasingOD = od;
    this.intermediateCasingDepth = depth;
  }

  /**
   * Set production casing properties.
   *
   * @param od outer diameter in inches
   * @param depth setting depth in meters MD
   */
  public void setProductionCasing(double od, double depth) {
    this.productionCasingOD = od;
    this.productionCasingDepth = depth;
  }

  /**
   * Set production liner properties.
   *
   * @param od outer diameter in inches
   * @param depth total depth in meters MD
   */
  public void setProductionLiner(double od, double depth) {
    this.productionLinerOD = od;
    this.productionLinerDepth = depth;
  }

  /**
   * Set tubing properties.
   *
   * @param od outer diameter in inches
   * @param weight weight in lb/ft
   * @param grade API 5CT grade string
   */
  public void setTubing(double od, double weight, String grade) {
    this.tubingOD = od;
    this.tubingWeight = weight;
    this.tubingGrade = grade;
  }

  // ============ Getters ============

  /**
   * Get production casing wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getProductionCasingWallThickness() {
    return productionCasingWallThickness;
  }

  /**
   * Get intermediate casing wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getIntermediateCasingWallThickness() {
    return intermediateCasingWallThickness;
  }

  /**
   * Get surface casing wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getSurfaceCasingWallThickness() {
    return surfaceCasingWallThickness;
  }

  /**
   * Get tubing wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getTubingWallThickness() {
    return tubingWallThickness;
  }

  /**
   * Get production casing burst design factor.
   *
   * @return burst DF
   */
  public double getProductionCasingBurstDF() {
    return productionCasingBurstDF;
  }

  /**
   * Get production casing collapse design factor.
   *
   * @return collapse DF
   */
  public double getProductionCasingCollapseDF() {
    return productionCasingCollapseDF;
  }

  /**
   * Get production casing tension design factor.
   *
   * @return tension DF
   */
  public double getProductionCasingTensionDF() {
    return productionCasingTensionDF;
  }

  /**
   * Get tubing burst design factor.
   *
   * @return burst DF
   */
  public double getTubingBurstDF() {
    return tubingBurstDF;
  }

  /**
   * Get total casing weight.
   *
   * @return weight in tonnes
   */
  public double getTotalCasingWeight() {
    return totalCasingWeight;
  }

  /**
   * Get total tubing weight.
   *
   * @return weight in tonnes
   */
  public double getTotalTubingWeight() {
    return totalTubingWeight;
  }

  /**
   * Get total cement volume.
   *
   * @return volume in m3
   */
  public double getTotalCementVolume() {
    return totalCementVolume;
  }

  /**
   * Get total cuttings volume.
   *
   * @return volume in m3
   */
  public double getTotalCuttingsVolume() {
    return totalCuttingsVolume;
  }

  /**
   * Get production casing VME (triaxial) design factor per NORSOK D-010.
   *
   * <p>
   * The von Mises Equivalent combines hoop, axial, and radial stresses. Must be &gt;= 1.25 per
   * NORSOK D-010 Table 18.
   * </p>
   *
   * @return VME design factor
   */
  public double getProductionCasingVME_DF() {
    return productionCasingVME_DF;
  }

  /**
   * Get the temperature derating factor applied to production casing SMYS.
   *
   * <p>
   * Per API 5CT / API TR 5C3 Table D.1, yield strength is derated at elevated temperatures. Factor
   * of 1.0 means no derating (&lt;= 100 degC).
   * </p>
   *
   * @return derating factor (0 to 1)
   */
  public double getTemperatureDeratingFactor() {
    return temperatureDeratingFactor;
  }
}
