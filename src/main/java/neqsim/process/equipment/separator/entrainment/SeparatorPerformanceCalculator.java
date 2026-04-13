package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Calculates detailed separation performance for a separator or scrubber using droplet size
 * distributions and grade efficiency curves.
 *
 * <p>
 * This calculator provides an optional, physics-based alternative to the simple entrainment
 * fractions in the base {@code Separator} class. When enabled, it computes entrainment from
 * first-principles using:
 * </p>
 *
 * <ol>
 * <li><b>Inlet droplet size distribution</b> (Rosin-Rammler, log-normal, or from Hinze
 * correlation)</li>
 * <li><b>Gravity separation section</b> — cut diameter from vessel geometry and gas/liquid
 * residence time</li>
 * <li><b>Mist eliminator grade efficiency</b> — wire mesh, vane pack, cyclone, or custom</li>
 * <li><b>Liquid-liquid separation</b> (three-phase) — oil/water droplet settling with plate pack
 * option</li>
 * </ol>
 *
 * <p>
 * The overall entrainment is the fraction of the inlet DSD that passes through all separation
 * stages without being captured. For three-phase separators, separate calculations are performed
 * for gas-oil, gas-water, oil-in-water, and water-in-oil entrainment.
 * </p>
 *
 * <p>
 * <b>Usage pattern:</b>
 * </p>
 *
 * <pre>
 * SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();
 *
 * // Configure inlet DSD (e.g., from pipe flow)
 * calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.6));
 *
 * // Configure mist eliminator
 * calc.setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());
 *
 * // Run calculation
 * calc.calculate(gasDensity, oilDensity, waterDensity, gasViscosity, oilViscosity, waterViscosity,
 *     gasVelocity, separatorDiameter, separatorLength, orientation, liquidLevelFraction);
 *
 * // Get results
 * double oilInGas = calc.getOilInGasFraction();
 * double waterInGas = calc.getWaterInGasFraction();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class SeparatorPerformanceCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Gravitational acceleration [m/s2]. */
  private static final double G = 9.81;

  // -- Configuration --

  /** Inlet droplet size distribution for liquid-in-gas. */
  private DropletSizeDistribution gasLiquidDSD;

  /** Inlet droplet size distribution for oil-in-water (three-phase). */
  private DropletSizeDistribution oilInWaterDSD;

  /** Inlet droplet size distribution for water-in-oil (three-phase). */
  private DropletSizeDistribution waterInOilDSD;

  /** Inlet droplet size distribution for gas bubbles in liquid. */
  private DropletSizeDistribution gasBubbleDSD;

  /** Grade efficiency curve for the mist eliminator (gas section). */
  private GradeEfficiencyCurve mistEliminatorCurve;

  /** Grade efficiency curve for oil-water coalescer (if present). */
  private GradeEfficiencyCurve oilWaterCoalescerCurve;

  /** Whether to include gravity pre-separation before mist eliminator. */
  private boolean includeGravitySection = true;

  // -- Enhanced framework (flow regime, inlet device, geometry, database) --

  /** Flow regime calculator for the inlet pipe. */
  private MultiphaseFlowRegime flowRegimeCalc;

  /** Inlet device model. */
  private InletDeviceModel inletDeviceModel;

  /** Separator geometry calculator. */
  private SeparatorGeometryCalculator geometryCalc;

  /** Whether to use the enhanced calculation chain (flow regime + inlet device + geometry). */
  private boolean useEnhancedCalculation = false;

  /** Inlet pipe diameter [m] for flow regime prediction. */
  private double inletPipeDiameter = 0.2;

  /** Gas-liquid interfacial tension [N/m] for DSD generation. */
  private double surfaceTension = 0.025;

  /** Oil-water interfacial tension [N/m] for liquid-liquid DSD estimation. */
  private double oilWaterInterfacialTension = 0.030;

  /** Liquid-liquid residence time override [s]. Zero means use geometry-calculated value. */
  private double liquidLiquidResidenceTimeOverride = 0.0;

  /**
   * Oil volume fraction in the liquid phase [0-1] for three-phase calculations.
   * Defaults to 0.5 (equal oil/water split). Set via {@link #setOilVolumeFraction(double)}
   * when the actual oil/water split is known from the thermodynamic system.
   */
  private double oilVolumeFraction = 0.5;

  /** Whether to apply turbulent diffusion correction to the gravity cut diameter. */
  private boolean applyTurbulenceCorrection = true;

  /** Whether flooding was detected in the mist eliminator. */
  private boolean mistEliminatorFlooded = false;

  /** DSD after inlet device transformation (intermediate result). */
  private DropletSizeDistribution postInletDeviceDSD;

  /** API 12J compliance result (populated after calculate() if geometry is known). */
  private transient DropletSettlingCalculator.ApiComplianceResult apiComplianceResult;

  // -- Results --

  /** Calculated oil-in-gas entrainment fraction (volume basis). */
  private double oilInGasFraction = 0.0;

  /** Calculated water-in-gas entrainment fraction (volume basis). */
  private double waterInGasFraction = 0.0;

  /** Calculated gas-in-oil carry-under fraction (volume basis). */
  private double gasInOilFraction = 0.0;

  /** Calculated gas-in-water carry-under fraction (volume basis). */
  private double gasInWaterFraction = 0.0;

  /** Calculated oil-in-water fraction (volume basis). */
  private double oilInWaterFraction = 0.0;

  /** Calculated water-in-oil fraction (volume basis). */
  private double waterInOilFraction = 0.0;

  /** Gravity section efficiency for gas-liquid separation. */
  private double gravitySectionEfficiency = 0.0;

  /** Mist eliminator efficiency. */
  private double mistEliminatorEfficiency = 0.0;

  /** Overall gas-liquid separation efficiency. */
  private double overallGasLiquidEfficiency = 0.0;

  /** Gravity section cut diameter [m]. */
  private double gravityCutDiameter = 0.0;

  /** Liquid-liquid gravity section efficiency. */
  private double liquidLiquidGravityEfficiency = 0.0;

  /** K-factor (Souders-Brown) at operating conditions. */
  private double kFactor = 0.0;

  /** K-factor utilization (operating K / design K). */
  private double kFactorUtilization = 0.0;

  /** Calibration multiplier for liquid-in-gas carryover fractions. */
  private double liquidInGasCalibrationFactor = 1.0;

  /** Calibration multiplier for gas carry-under fractions (gas in liquid). */
  private double gasCarryUnderCalibrationFactor = 1.0;

  /** Calibration multiplier for liquid-liquid cross-contamination fractions. */
  private double liquidLiquidCalibrationFactor = 1.0;

  /** Inlet device bulk separation efficiency. */
  private double inletDeviceBulkEfficiency = 0.0;

  /** Flow regime at separator inlet. */
  private MultiphaseFlowRegime.FlowRegime inletFlowRegime;

  /**
   * Creates a new SeparatorPerformanceCalculator with default settings.
   */
  public SeparatorPerformanceCalculator() {
    // Defaults: no DSD set, user must configure before calculating
  }

  /**
   * Performs the full separation performance calculation.
   *
   * <p>
   * This method calculates entrainment fractions for all relevant phase pairs based on the
   * configured droplet size distributions, grade efficiency curves, and separator geometry.
   * </p>
   *
   * @param gasDensity gas phase density [kg/m3]
   * @param oilDensity oil phase density [kg/m3], 0 if no oil
   * @param waterDensity water phase density [kg/m3], 0 if no water
   * @param gasViscosity gas phase dynamic viscosity [Pa.s]
   * @param oilViscosity oil phase dynamic viscosity [Pa.s]
   * @param waterViscosity water phase dynamic viscosity [Pa.s]
   * @param gasVelocity superficial gas velocity in gas section [m/s]
   * @param vesselDiameter internal diameter of separator [m]
   * @param vesselLength length of separator [m]
   * @param orientation "horizontal" or "vertical"
   * @param liquidLevelFraction fraction of vessel cross-section occupied by liquid (horizontal) or
   *        fraction of height (vertical) [0-1]
   */
  public void calculate(double gasDensity, double oilDensity, double waterDensity,
      double gasViscosity, double oilViscosity, double waterViscosity, double gasVelocity,
      double vesselDiameter, double vesselLength, String orientation, double liquidLevelFraction) {

    // Reset results
    resetResults();

    // --- Enhanced calculation chain (if enabled) ---
    if (useEnhancedCalculation) {
        calculateEnhanced(gasDensity, oilDensity, waterDensity, gasViscosity, oilViscosity,
          waterViscosity, gasVelocity, vesselDiameter, vesselLength, orientation,
          liquidLevelFraction);
        applyCalibrationFactors();
      return;
    }

    // --- Standard calculation (original path) ---
    calculateStandard(gasDensity, oilDensity, waterDensity, gasViscosity, oilViscosity,
        waterViscosity, gasVelocity, vesselDiameter, vesselLength, orientation,
        liquidLevelFraction);
    applyCalibrationFactors();
  }

  /**
   * Applies user-defined calibration multipliers to calculated entrainment fractions.
   *
   * <p>
   * This preserves the mechanistic model structure while allowing reconciliation to plant/vendor
   * test data. All calibrated values are clamped to [0, 1].
   * </p>
   */
  private void applyCalibrationFactors() {
    oilInGasFraction = clamp01(oilInGasFraction * liquidInGasCalibrationFactor);
    waterInGasFraction = clamp01(waterInGasFraction * liquidInGasCalibrationFactor);
    gasInOilFraction = clamp01(gasInOilFraction * gasCarryUnderCalibrationFactor);
    gasInWaterFraction = clamp01(gasInWaterFraction * gasCarryUnderCalibrationFactor);
    oilInWaterFraction = clamp01(oilInWaterFraction * liquidLiquidCalibrationFactor);
    waterInOilFraction = clamp01(waterInOilFraction * liquidLiquidCalibrationFactor);
  }

  /**
   * Clamps a value to the physical [0, 1] range.
   *
   * @param value raw value
   * @return clamped value in [0, 1]
   */
  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  /**
   * Standard calculation path (original implementation).
   *
   * @param gasDensity gas density [kg/m3]
   * @param oilDensity oil density [kg/m3]
   * @param waterDensity water density [kg/m3]
   * @param gasViscosity gas viscosity [Pa.s]
   * @param oilViscosity oil viscosity [Pa.s]
   * @param waterViscosity water viscosity [Pa.s]
   * @param gasVelocity gas velocity [m/s]
   * @param vesselDiameter vessel diameter [m]
   * @param vesselLength vessel length [m]
   * @param orientation vessel orientation
   * @param liquidLevelFraction liquid level fraction
   */
  private void calculateStandard(double gasDensity, double oilDensity, double waterDensity,
      double gasViscosity, double oilViscosity, double waterViscosity, double gasVelocity,
      double vesselDiameter, double vesselLength, String orientation, double liquidLevelFraction) {

    // --- Gas-liquid separation ---
    if (gasLiquidDSD != null && (oilDensity > 0 || waterDensity > 0)) {
      double liquidDensity = (oilDensity > 0) ? oilDensity : waterDensity;
      double liquidViscosity = (oilDensity > 0) ? oilViscosity : waterViscosity;

      calcGasLiquidSeparation(gasDensity, liquidDensity, gasViscosity, gasVelocity, vesselDiameter,
          vesselLength, orientation, liquidLevelFraction);

      double totalEntrainmentFraction = 1.0 - overallGasLiquidEfficiency;

      // Split entrainment between oil and water based on relative liquid volumes
      if (oilDensity > 0 && waterDensity > 0) {
        // Three-phase: split proportionally (simplified — in reality, feed composition matters)
        oilInGasFraction = totalEntrainmentFraction;
        waterInGasFraction = totalEntrainmentFraction;
      } else if (oilDensity > 0) {
        oilInGasFraction = totalEntrainmentFraction;
      } else {
        waterInGasFraction = totalEntrainmentFraction;
      }
    }

    // --- Gas bubble carry-under in liquid ---
    if (gasBubbleDSD != null && (oilDensity > 0 || waterDensity > 0)) {
      double liquidDensity = (oilDensity > 0) ? oilDensity : waterDensity;
      double liquidViscosity = (oilDensity > 0) ? oilViscosity : waterViscosity;
      calcGasBubbleCarryUnder(gasDensity, liquidDensity, liquidViscosity, gasVelocity,
          vesselDiameter, vesselLength, orientation, liquidLevelFraction);
    }

    // --- Liquid-liquid separation (three-phase) ---
    if (oilDensity > 0 && waterDensity > 0) {
      calcLiquidLiquidSeparation(oilDensity, waterDensity, oilViscosity, waterViscosity,
          vesselDiameter, vesselLength, orientation, liquidLevelFraction);
    }

    // --- Build API 12J compliance if enough info is available ---
    if (gravityCutDiameter > 0) {
      double liqResTime = estimateLiquidResidenceTime(vesselDiameter, vesselLength, orientation,
          liquidLevelFraction, gasVelocity);
      boolean hasMe = (mistEliminatorCurve != null);
      boolean isThreePhase = (oilDensity > 0 && waterDensity > 0);
      apiComplianceResult = DropletSettlingCalculator.checkApi12JCompliance(gravityCutDiameter,
          kFactor, hasMe, liqResTime, orientation, isThreePhase);
    }
  }

  /**
   * Enhanced calculation chain integrating flow regime prediction, inlet device modeling, detailed
   * vessel geometry, and database-driven internals performance.
   *
   * <p>
   * Calculation sequence:
   * </p>
   * <ol>
   * <li>Predict inlet pipe flow regime and generate regime-specific DSD</li>
   * <li>Model inlet device: bulk separation, DSD transformation, pressure drop</li>
   * <li>Calculate vessel geometry: gas/liquid areas, settling heights, K-factor</li>
   * <li>Gravity section: droplet settling with proper residence time from geometry</li>
   * <li>Mist eliminator: grade efficiency with K-factor flooding check</li>
   * <li>Liquid-liquid separation with geometry-based residence time (three-phase)</li>
   * </ol>
   *
   * @param gasDensity gas phase density [kg/m3]
   * @param oilDensity oil phase density [kg/m3], 0 if no oil
   * @param waterDensity water phase density [kg/m3], 0 if no water
   * @param gasViscosity gas phase dynamic viscosity [Pa.s]
   * @param oilViscosity oil phase dynamic viscosity [Pa.s]
   * @param waterViscosity water phase dynamic viscosity [Pa.s]
   * @param gasVelocity superficial gas velocity in gas section [m/s]
   * @param vesselDiameter internal diameter of separator [m]
   * @param vesselLength length of separator [m]
   * @param orientation "horizontal" or "vertical"
   * @param liquidLevelFraction fraction of vessel cross-section occupied by liquid [0-1]
   */
  private void calculateEnhanced(double gasDensity, double oilDensity, double waterDensity,
      double gasViscosity, double oilViscosity, double waterViscosity, double gasVelocity,
      double vesselDiameter, double vesselLength, String orientation, double liquidLevelFraction) {

    double liquidDensity = (oilDensity > 0) ? oilDensity : waterDensity;
    double liquidViscosity = (oilDensity > 0) ? oilViscosity : waterViscosity;
    boolean isVertical = "vertical".equalsIgnoreCase(orientation);

    // ============================================================
    // STAGE 1: Flow regime prediction and inlet DSD generation
    // ============================================================
    if (flowRegimeCalc == null) {
      flowRegimeCalc = new MultiphaseFlowRegime();
    }

    // Estimate superficial liquid velocity from gas velocity and liquid fraction
    double superficialLiquidVelocity =
        gasVelocity * liquidLevelFraction / (1.0 - liquidLevelFraction + 1e-10);
    if (superficialLiquidVelocity < 0.001) {
      superficialLiquidVelocity = 0.01; // minimum to avoid degenerate regime
    }

    // Configure and run flow regime prediction
    flowRegimeCalc.setGasDensity(gasDensity);
    flowRegimeCalc.setLiquidDensity(liquidDensity);
    flowRegimeCalc.setGasViscosity(gasViscosity);
    flowRegimeCalc.setLiquidViscosity(liquidViscosity);
    flowRegimeCalc.setSurfaceTension(surfaceTension);
    flowRegimeCalc.setPipeDiameter(inletPipeDiameter);
    flowRegimeCalc.setGasSuperficialVelocity(gasVelocity);
    flowRegimeCalc.setLiquidSuperficialVelocity(superficialLiquidVelocity);
    flowRegimeCalc.setPipeOrientation(isVertical ? "vertical" : "horizontal");
    flowRegimeCalc.predict();
    inletFlowRegime = flowRegimeCalc.getPredictedRegime();

    // Generate regime-specific DSD if none explicitly set
    DropletSizeDistribution workingDSD = gasLiquidDSD;
    if (workingDSD == null) {
      workingDSD = flowRegimeCalc.getGeneratedDSD();
    }

    // ============================================================
    // STAGE 2: Inlet device modeling
    // ============================================================
    if (inletDeviceModel == null) {
      inletDeviceModel = new InletDeviceModel(InletDeviceModel.InletDeviceType.HALF_PIPE);
    }

    // Estimate actual volume flows from superficial velocities and pipe area
    double pipeArea = Math.PI * inletPipeDiameter * inletPipeDiameter / 4.0;
    double gasVolumeFlow = gasVelocity * pipeArea;
    double liquidVolumeFlow = superficialLiquidVelocity * pipeArea;

    inletDeviceModel.setInletNozzleDiameter(inletPipeDiameter);
    if (workingDSD != null) {
      inletDeviceModel.calculate(workingDSD, gasDensity, liquidDensity, gasVolumeFlow,
          liquidVolumeFlow, surfaceTension);
    }

    inletDeviceBulkEfficiency = inletDeviceModel.getBulkSeparationEfficiency();
    postInletDeviceDSD = inletDeviceModel.getDownstreamDSD();

    // Use post-inlet-device DSD for downstream stages
    DropletSizeDistribution gravityInletDSD =
        (postInletDeviceDSD != null) ? postInletDeviceDSD : workingDSD;

    // ============================================================
    // STAGE 3: Vessel geometry calculation
    // ============================================================
    if (geometryCalc == null) {
      geometryCalc = new SeparatorGeometryCalculator();
    }

    geometryCalc.setOrientation(isVertical ? "vertical" : "horizontal");
    geometryCalc.setInternalDiameter(vesselDiameter);
    geometryCalc.setTangentToTangentLength(vesselLength);
    geometryCalc.setNormalLiquidLevel(liquidLevelFraction);

    // Estimate actual vessel flows from vessel geometry
    double vesselArea = Math.PI * vesselDiameter * vesselDiameter / 4.0;
    double vesselGasFlow = gasVelocity * vesselArea * (1.0 - liquidLevelFraction);
    double vesselLiquidFlow = superficialLiquidVelocity * vesselArea * liquidLevelFraction;

    if (oilDensity > 0 && waterDensity > 0) {
      // Three-phase: use configured oil volume fraction (default 0.5; set via setOilVolumeFraction)
      double oilFrac = Math.max(0.01, Math.min(0.99, oilVolumeFraction));
      geometryCalc.calculateThreePhase(vesselGasFlow, vesselLiquidFlow * oilFrac,
          vesselLiquidFlow * (1.0 - oilFrac), oilFrac);
    } else {
      geometryCalc.calculate(vesselGasFlow, vesselLiquidFlow);
    }

    // K-factor from Souders-Brown
    kFactor = SeparatorGeometryCalculator.calcKFactor(gasVelocity, gasDensity, liquidDensity);
    double designKFactor = 0.0;
    if (mistEliminatorCurve != null) {
      // Use database design K-factor for the mist eliminator type
      SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
      String meType = mistEliminatorCurve.getType().name();
      List<SeparatorInternalsDatabase.InternalsRecord> records = db.findByType(meType);
      if (!records.isEmpty()) {
        designKFactor = records.get(0).maxKFactor;
      } else {
        designKFactor = 0.107; // GPSA default for wire mesh
      }
    }
    kFactorUtilization = (designKFactor > 0) ? kFactor / designKFactor : 0.0;
    mistEliminatorFlooded = kFactorUtilization > 1.0;

    // ============================================================
    // STAGE 4: Gravity section separation
    // ============================================================
    double effectiveSettlingHeight = geometryCalc.getEffectiveGasSettlingHeight();
    double gasResidenceTime = geometryCalc.getGasResidenceTime();

    if (includeGravitySection && gravityInletDSD != null) {
      gravityCutDiameter = DropletSettlingCalculator.calcCriticalDiameter(effectiveSettlingHeight,
          gasResidenceTime, gasDensity, liquidDensity, gasViscosity);

      // Apply turbulence correction to cut diameter (Csanady 1963 / Koenders 2015)
      double effectiveCutDiam = gravityCutDiameter;
      if (applyTurbulenceCorrection) {
        effectiveCutDiam = DropletSettlingCalculator.calcTurbulenceCorrectedCutDiameter(
            gravityCutDiameter, gasVelocity, effectiveSettlingHeight, kFactor, designKFactor,
            gasDensity, liquidDensity, gasViscosity);
      }

      GradeEfficiencyCurve gravityCurve = GradeEfficiencyCurve.gravity(effectiveCutDiam);
      gravitySectionEfficiency = gravityCurve.calcOverallEfficiency(gravityInletDSD);

      // ============================================================
      // STAGE 5: Mist eliminator (with flooding check)
      // ============================================================
      if (mistEliminatorCurve != null && !mistEliminatorFlooded) {
        mistEliminatorEfficiency = mistEliminatorCurve.calcOverallEfficiency(gravityInletDSD);
        overallGasLiquidEfficiency =
            calcCombinedEfficiency(gravityInletDSD, gravityCurve, mistEliminatorCurve);
      } else if (mistEliminatorCurve != null && mistEliminatorFlooded) {
        // Partially flooded: efficiency degrades linearly with excess K-factor.
        // At K_util = 1.0 (onset of flooding): efficiency = 50% of normal.
        // At K_util >= 2.0: efficiency = 0 (complete flooding / re-entrainment).
        // Based on: Fabian, P., et al. (1993). GPSA Engineering Data Book, Sec 7.
        double floodPenalty = Math.max(0.0, 1.0 - (kFactorUtilization - 1.0));
        double normalMeEff = mistEliminatorCurve.calcOverallEfficiency(gravityInletDSD);
        mistEliminatorEfficiency = normalMeEff * floodPenalty * 0.5;
        overallGasLiquidEfficiency = gravitySectionEfficiency
            + (1.0 - gravitySectionEfficiency) * mistEliminatorEfficiency;
      } else {
        overallGasLiquidEfficiency = gravitySectionEfficiency;
      }
    } else if (mistEliminatorCurve != null && gravityInletDSD != null && !mistEliminatorFlooded) {
      mistEliminatorEfficiency = mistEliminatorCurve.calcOverallEfficiency(gravityInletDSD);
      overallGasLiquidEfficiency = mistEliminatorEfficiency;
    }

    // --- API 12J compliance check ---
    if (gravityCutDiameter > 0) {
      double liqResTime = geometryCalc.getLiquidResidenceTime();
      if (liqResTime <= 0) {
        liqResTime = estimateLiquidResidenceTime(vesselDiameter, vesselLength, orientation,
            liquidLevelFraction, gasVelocity);
      }
      boolean hasMe = (mistEliminatorCurve != null);
      boolean isThreePhase = (oilDensity > 0 && waterDensity > 0);
      apiComplianceResult = DropletSettlingCalculator.checkApi12JCompliance(gravityCutDiameter,
          kFactor, hasMe, liqResTime, orientation, isThreePhase);
    }

    // Apply inlet device bulk efficiency (pre-gravity removal)
    // Total liquid removed = bulk + (1-bulk) * downstream_efficiency
    double combinedEfficiency =
        inletDeviceBulkEfficiency + (1.0 - inletDeviceBulkEfficiency) * overallGasLiquidEfficiency;
    overallGasLiquidEfficiency = Math.min(1.0, combinedEfficiency);

    // Set entrainment fractions
    double totalEntrainmentFraction = 1.0 - overallGasLiquidEfficiency;
    if (oilDensity > 0 && waterDensity > 0) {
      oilInGasFraction = totalEntrainmentFraction;
      waterInGasFraction = totalEntrainmentFraction;
    } else if (oilDensity > 0) {
      oilInGasFraction = totalEntrainmentFraction;
    } else {
      waterInGasFraction = totalEntrainmentFraction;
    }

    // ============================================================
    // STAGE 6: Gas bubble carry-under
    // ============================================================
    if (gasBubbleDSD != null && liquidDensity > 0) {
      double liquidHeight = geometryCalc.getEffectiveLiquidSettlingHeight();
      double liquidResidenceTime = geometryCalc.getLiquidResidenceTime();
      if (liquidResidenceTime <= 0) {
        liquidResidenceTime = 120.0;
      }

      double bubbleCutDiameter = DropletSettlingCalculator.calcCriticalDiameter(liquidHeight,
          liquidResidenceTime, liquidDensity, gasDensity, liquidViscosity);
      GradeEfficiencyCurve bubbleGravity = GradeEfficiencyCurve.gravity(bubbleCutDiameter);
      double bubbleEff = bubbleGravity.calcOverallEfficiency(gasBubbleDSD);
      gasInOilFraction = 1.0 - bubbleEff;
      gasInWaterFraction = gasInOilFraction;
    }

    // ============================================================
    // STAGE 7: Liquid-liquid separation (three-phase)
    // ============================================================
    if (oilDensity > 0 && waterDensity > 0) {
      double llResidenceTime =
          (liquidLiquidResidenceTimeOverride > 0) ? liquidLiquidResidenceTimeOverride
              : geometryCalc.getLiquidResidenceTime();
      if (llResidenceTime <= 0) {
        llResidenceTime = 300.0;
      }
      double oilPadHeight = geometryCalc.getOilPadThickness();
      if (oilPadHeight <= 0) {
        oilPadHeight = vesselDiameter * liquidLevelFraction / 2.0;
      }

      // Water droplets settling in oil
      if (waterInOilDSD != null) {
        double dCut = DropletSettlingCalculator.calcCriticalDiameter(oilPadHeight, llResidenceTime,
            oilDensity, waterDensity, oilViscosity);
        GradeEfficiencyCurve waterInOilGravity = GradeEfficiencyCurve.gravity(dCut);
        double gravEff = waterInOilGravity.calcOverallEfficiency(waterInOilDSD);
        if (oilWaterCoalescerCurve != null) {
          double coalEff = oilWaterCoalescerCurve.calcOverallEfficiency(waterInOilDSD);
          waterInOilFraction = (1.0 - gravEff) * (1.0 - coalEff);
        } else {
          waterInOilFraction = 1.0 - gravEff;
        }
      }

      // Oil droplets rising in water
      if (oilInWaterDSD != null) {
        double waterLayerHeight = geometryCalc.getWaterLayerHeight();
        if (waterLayerHeight <= 0) {
          waterLayerHeight = oilPadHeight;
        }
        double dCut = DropletSettlingCalculator.calcCriticalDiameter(waterLayerHeight,
            llResidenceTime, waterDensity, oilDensity, waterViscosity);
        GradeEfficiencyCurve oilInWaterGravity = GradeEfficiencyCurve.gravity(dCut);
        double gravEff = oilInWaterGravity.calcOverallEfficiency(oilInWaterDSD);
        if (oilWaterCoalescerCurve != null) {
          double coalEff = oilWaterCoalescerCurve.calcOverallEfficiency(oilInWaterDSD);
          oilInWaterFraction = (1.0 - gravEff) * (1.0 - coalEff);
        } else {
          oilInWaterFraction = 1.0 - gravEff;
        }
        liquidLiquidGravityEfficiency = gravEff;
      }
    }
  }

  /**
   * Calculates gas-liquid separation efficiency combining gravity section and mist eliminator.
   *
   * @param gasDensity gas density [kg/m3]
   * @param liquidDensity liquid density [kg/m3]
   * @param gasViscosity gas viscosity [Pa.s]
   * @param gasVelocity superficial gas velocity [m/s]
   * @param diameter vessel diameter [m]
   * @param length vessel length [m]
   * @param orientation vessel orientation
   * @param liquidLevelFrac liquid level fraction
   */
  private void calcGasLiquidSeparation(double gasDensity, double liquidDensity, double gasViscosity,
      double gasVelocity, double diameter, double length, String orientation,
      double liquidLevelFrac) {

    // Calculate gravity section
    double gasAreaFraction = 1.0 - liquidLevelFrac;
    double availableHeight;
    double gasResidenceTime;

    if ("vertical".equalsIgnoreCase(orientation)) {
      availableHeight = length * gasAreaFraction;
      double gasArea = Math.PI * diameter * diameter / 4.0 * gasAreaFraction;
      gasResidenceTime = (gasArea > 0 && gasVelocity > 0) ? length / gasVelocity : 0.0;
    } else {
      // Horizontal: droplets must settle across the gas cap height
      availableHeight = diameter * (1.0 - liquidLevelFrac);
      double gasArea = Math.PI * diameter * diameter / 4.0 * gasAreaFraction;
      gasResidenceTime = (gasVelocity > 0) ? length / gasVelocity : 0.0;
    }

    // Calculate gravity cut diameter
    gravityCutDiameter = DropletSettlingCalculator.calcCriticalDiameter(availableHeight,
        gasResidenceTime, gasDensity, liquidDensity, gasViscosity);

    // Create gravity grade efficiency
    GradeEfficiencyCurve gravityCurve = GradeEfficiencyCurve.gravity(gravityCutDiameter);

    if (includeGravitySection) {
      gravitySectionEfficiency = gravityCurve.calcOverallEfficiency(gasLiquidDSD);
    }

    // Mist eliminator efficiency
    if (mistEliminatorCurve != null) {
      mistEliminatorEfficiency = mistEliminatorCurve.calcOverallEfficiency(gasLiquidDSD);
    }

    // Combined efficiency: gravity removes some droplets, mist eliminator catches the rest
    // The DSD reaching the mist eliminator is the "penetration" through gravity
    if (includeGravitySection && mistEliminatorCurve != null) {
      overallGasLiquidEfficiency =
          calcCombinedEfficiency(gasLiquidDSD, gravityCurve, mistEliminatorCurve);
    } else if (includeGravitySection) {
      overallGasLiquidEfficiency = gravitySectionEfficiency;
    } else if (mistEliminatorCurve != null) {
      overallGasLiquidEfficiency = mistEliminatorEfficiency;
    }
  }

  /**
   * Calculates combined efficiency of two stages in series (gravity + mist eliminator).
   *
   * <p>
   * For each size class, the combined efficiency is:
   * </p>
   *
   * $$ \eta_{combined}(d) = 1 - (1 - \eta_1(d)) \cdot (1 - \eta_2(d)) $$
   *
   * @param dsd droplet size distribution
   * @param stage1 first stage (gravity)
   * @param stage2 second stage (mist eliminator)
   * @return combined overall efficiency
   */
  private double calcCombinedEfficiency(DropletSizeDistribution dsd, GradeEfficiencyCurve stage1,
      GradeEfficiencyCurve stage2) {
    double[][] classes = dsd.getDiscreteClasses();
    double totalEfficiency = 0.0;
    for (double[] cls : classes) {
      double midDiameter = cls[1];
      double volumeFraction = cls[2];
      double eta1 = stage1.getEfficiency(midDiameter);
      double eta2 = stage2.getEfficiency(midDiameter);
      double combinedEta = 1.0 - (1.0 - eta1) * (1.0 - eta2);
      totalEfficiency += combinedEta * volumeFraction;
    }
    return Math.max(0.0, Math.min(1.0, totalEfficiency));
  }

  /**
   * Calculates gas bubble carry-under in the liquid phase.
   *
   * @param gasDensity gas density [kg/m3]
   * @param liquidDensity liquid density [kg/m3]
   * @param liquidViscosity liquid viscosity [Pa.s]
   * @param diameter vessel diameter [m]
   * @param length vessel length [m]
   * @param orientation vessel orientation
   * @param liquidLevelFrac liquid level fraction
   */
  /**
   * Estimates the liquid residence time from vessel geometry and gas velocity.
   *
   * <p>
   * Uses mass continuity to derive the superficial liquid velocity from the superficial gas
   * velocity and the phase fraction, then divides vessel length by that velocity.
   * </p>
   *
   * @param diameter vessel internal diameter [m]
   * @param length vessel tangent-to-tangent length [m]
   * @param orientation "horizontal" or "vertical"
   * @param liquidLevelFrac fraction of cross-section occupied by liquid [0-1]
   * @param gasVelocity superficial gas velocity [m/s]
   * @return estimated liquid residence time [s]
   */
  private static double estimateLiquidResidenceTime(double diameter, double length,
      String orientation, double liquidLevelFrac, double gasVelocity) {
    // Superficial liquid velocity via continuity (same volumetric flow density approach)
    double gasFrac = Math.max(0.01, 1.0 - liquidLevelFrac);
    double liqFrac = Math.max(0.01, liquidLevelFrac);
    double superficialLiquidVelocity = gasVelocity * gasFrac / liqFrac;
    // Liquid residence time = vessel length / liquid velocity
    return length / Math.max(superficialLiquidVelocity, 0.0001);
  }

  private void calcGasBubbleCarryUnder(double gasDensity, double liquidDensity,
      double liquidViscosity, double gasVelocity, double diameter, double length,
      String orientation, double liquidLevelFrac) {

    double liquidHeight;
    if ("vertical".equalsIgnoreCase(orientation)) {
      liquidHeight = length * liquidLevelFrac;
    } else {
      liquidHeight = diameter * liquidLevelFrac;
    }

    // Compute liquid residence time from vessel geometry and continuity (not hardcoded)
    double liquidResidenceTime = estimateLiquidResidenceTime(diameter, length, orientation,
        liquidLevelFrac, gasVelocity);
    if (liquidHeight <= 0) {
      liquidResidenceTime = 0.0;
    }

    double bubbleCutDiameter = DropletSettlingCalculator.calcCriticalDiameter(liquidHeight,
        liquidResidenceTime, liquidDensity, gasDensity, liquidViscosity);

    GradeEfficiencyCurve bubbleGravity = GradeEfficiencyCurve.gravity(bubbleCutDiameter);
    double bubbleEfficiency = bubbleGravity.calcOverallEfficiency(gasBubbleDSD);

    gasInOilFraction = 1.0 - bubbleEfficiency;
    gasInWaterFraction = gasInOilFraction; // Simplified: same for both liquid phases
  }

  /**
   * Calculates liquid-liquid separation (oil-water and water-oil).
   *
   * @param oilDensity oil density [kg/m3]
   * @param waterDensity water density [kg/m3]
   * @param oilViscosity oil viscosity [Pa.s]
   * @param waterViscosity water viscosity [Pa.s]
   * @param diameter vessel diameter [m]
   * @param length vessel length [m]
   * @param orientation vessel orientation
   * @param liquidLevelFrac total liquid level fraction
   */
  private void calcLiquidLiquidSeparation(double oilDensity, double waterDensity,
      double oilViscosity, double waterViscosity, double diameter, double length,
      String orientation, double liquidLevelFrac) {

    double liquidHeight;
    if ("vertical".equalsIgnoreCase(orientation)) {
      liquidHeight = length * liquidLevelFrac;
    } else {
      liquidHeight = diameter * liquidLevelFrac;
    }

    // Assume oil-water interface is at mid-liquid level
    double settlingHeight = liquidHeight / 2.0;
    // Default residence time for liquid-liquid separation
    double liquidResidenceTime = 300.0; // 5 minutes default

    // Water droplets settling in oil (water-in-oil)
    if (waterInOilDSD != null) {
      double dCut = DropletSettlingCalculator.calcCriticalDiameter(settlingHeight,
          liquidResidenceTime, oilDensity, waterDensity, oilViscosity);

      GradeEfficiencyCurve gravityCurve = GradeEfficiencyCurve.gravity(dCut);
      double gravityEff = gravityCurve.calcOverallEfficiency(waterInOilDSD);

      if (oilWaterCoalescerCurve != null) {
        double coalescerEff = oilWaterCoalescerCurve.calcOverallEfficiency(waterInOilDSD);
        waterInOilFraction = (1.0 - gravityEff) * (1.0 - coalescerEff);
      } else {
        waterInOilFraction = 1.0 - gravityEff;
      }
    }

    // Oil droplets rising in water (oil-in-water)
    if (oilInWaterDSD != null) {
      double dCut = DropletSettlingCalculator.calcCriticalDiameter(settlingHeight,
          liquidResidenceTime, waterDensity, oilDensity, waterViscosity);

      GradeEfficiencyCurve gravityCurve = GradeEfficiencyCurve.gravity(dCut);
      double gravityEff = gravityCurve.calcOverallEfficiency(oilInWaterDSD);

      if (oilWaterCoalescerCurve != null) {
        double coalescerEff = oilWaterCoalescerCurve.calcOverallEfficiency(oilInWaterDSD);
        oilInWaterFraction = (1.0 - gravityEff) * (1.0 - coalescerEff);
      } else {
        oilInWaterFraction = 1.0 - gravityEff;
      }
      liquidLiquidGravityEfficiency = gravityEff;
    }
  }

  /**
   * Resets all calculated results to zero.
   */
  private void resetResults() {
    oilInGasFraction = 0.0;
    waterInGasFraction = 0.0;
    gasInOilFraction = 0.0;
    gasInWaterFraction = 0.0;
    oilInWaterFraction = 0.0;
    waterInOilFraction = 0.0;
    gravitySectionEfficiency = 0.0;
    mistEliminatorEfficiency = 0.0;
    overallGasLiquidEfficiency = 0.0;
    gravityCutDiameter = 0.0;
    liquidLiquidGravityEfficiency = 0.0;
    kFactor = 0.0;
    kFactorUtilization = 0.0;
    inletDeviceBulkEfficiency = 0.0;
    inletFlowRegime = null;
    mistEliminatorFlooded = false;
    postInletDeviceDSD = null;
  }

  /**
   * Returns a JSON string with full performance calculation results.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("overallGasLiquidEfficiency", overallGasLiquidEfficiency);
    result.put("gravitySectionEfficiency", gravitySectionEfficiency);
    result.put("mistEliminatorEfficiency", mistEliminatorEfficiency);
    result.put("gravityCutDiameter_um", gravityCutDiameter * 1e6);
    result.put("oilInGasFraction", oilInGasFraction);
    result.put("waterInGasFraction", waterInGasFraction);
    result.put("gasInOilFraction", gasInOilFraction);
    result.put("gasInWaterFraction", gasInWaterFraction);
    result.put("oilInWaterFraction", oilInWaterFraction);
    result.put("waterInOilFraction", waterInOilFraction);
    result.put("liquidLiquidGravityEfficiency", liquidLiquidGravityEfficiency);
    result.put("liquidInGasCalibrationFactor", liquidInGasCalibrationFactor);
    result.put("gasCarryUnderCalibrationFactor", gasCarryUnderCalibrationFactor);
    result.put("liquidLiquidCalibrationFactor", liquidLiquidCalibrationFactor);

    if (gasLiquidDSD != null) {
      Map<String, Object> dsdInfo = new LinkedHashMap<String, Object>();
      dsdInfo.put("type", gasLiquidDSD.getType().name());
      dsdInfo.put("d50_um", gasLiquidDSD.getD50() * 1e6);
      dsdInfo.put("d32_um", gasLiquidDSD.getSauterMeanDiameter() * 1e6);
      result.put("inletDSD", dsdInfo);
    }
    if (mistEliminatorCurve != null) {
      Map<String, Object> meInfo = new LinkedHashMap<String, Object>();
      meInfo.put("type", mistEliminatorCurve.getType().name());
      meInfo.put("d50_um", mistEliminatorCurve.getCutDiameter() * 1e6);
      meInfo.put("maxEfficiency", mistEliminatorCurve.getMaxEfficiency());
      result.put("mistEliminator", meInfo);
    }

    // Enhanced calculation results
    if (useEnhancedCalculation) {
      result.put("enhancedCalculation", true);
      result.put("kFactor", kFactor);
      result.put("kFactorUtilization", kFactorUtilization);
      result.put("mistEliminatorFlooded", mistEliminatorFlooded);
      result.put("inletDeviceBulkEfficiency", inletDeviceBulkEfficiency);
      if (inletFlowRegime != null) {
        result.put("inletFlowRegime", inletFlowRegime.name());
      }
      if (postInletDeviceDSD != null) {
        Map<String, Object> pidDsd = new LinkedHashMap<String, Object>();
        pidDsd.put("type", postInletDeviceDSD.getType().name());
        pidDsd.put("d50_um", postInletDeviceDSD.getD50() * 1e6);
        pidDsd.put("d32_um", postInletDeviceDSD.getSauterMeanDiameter() * 1e6);
        result.put("postInletDeviceDSD", pidDsd);
      }
      if (inletDeviceModel != null) {
        Map<String, Object> idInfo = new LinkedHashMap<String, Object>();
        idInfo.put("type", inletDeviceModel.getDeviceType().name());
        idInfo.put("nozzleVelocity_m_s", inletDeviceModel.getNozzleVelocity());
        idInfo.put("momentumFlux_Pa", inletDeviceModel.getMomentumFlux());
        idInfo.put("pressureDrop_Pa", inletDeviceModel.getPressureDrop());
        result.put("inletDevice", idInfo);
      }
      if (geometryCalc != null) {
        Map<String, Object> geomInfo = new LinkedHashMap<String, Object>();
        geomInfo.put("gasResidenceTime_s", geometryCalc.getGasResidenceTime());
        geomInfo.put("liquidResidenceTime_s", geometryCalc.getLiquidResidenceTime());
        geomInfo.put("effectiveSettlingHeight_m", geometryCalc.getEffectiveGasSettlingHeight());
        geomInfo.put("gasArea_m2", geometryCalc.getGasArea());
        geomInfo.put("liquidArea_m2", geometryCalc.getLiquidArea());
        result.put("vesselGeometry", geomInfo);
      }
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }

  // ----- Getters for results -----

  /**
   * Gets the calculated oil-in-gas entrainment fraction (volume basis).
   *
   * @return oilInGasFraction [0-1]
   */
  public double getOilInGasFraction() {
    return oilInGasFraction;
  }

  /**
   * Gets the calculated water-in-gas entrainment fraction (volume basis).
   *
   * @return waterInGasFraction [0-1]
   */
  public double getWaterInGasFraction() {
    return waterInGasFraction;
  }

  /**
   * Gets the calculated gas-in-oil carry-under fraction (volume basis).
   *
   * @return gasInOilFraction [0-1]
   */
  public double getGasInOilFraction() {
    return gasInOilFraction;
  }

  /**
   * Gets the calculated gas-in-water carry-under fraction (volume basis).
   *
   * @return gasInWaterFraction [0-1]
   */
  public double getGasInWaterFraction() {
    return gasInWaterFraction;
  }

  /**
   * Gets the calculated oil-in-water fraction (volume basis).
   *
   * @return oilInWaterFraction [0-1]
   */
  public double getOilInWaterFraction() {
    return oilInWaterFraction;
  }

  /**
   * Gets the calculated water-in-oil fraction (volume basis).
   *
   * @return waterInOilFraction [0-1]
   */
  public double getWaterInOilFraction() {
    return waterInOilFraction;
  }

  /**
   * Gets the gravity section efficiency for gas-liquid separation.
   *
   * @return gravitySectionEfficiency [0-1]
   */
  public double getGravitySectionEfficiency() {
    return gravitySectionEfficiency;
  }

  /**
   * Gets the mist eliminator efficiency.
   *
   * @return mistEliminatorEfficiency [0-1]
   */
  public double getMistEliminatorEfficiency() {
    return mistEliminatorEfficiency;
  }

  /**
   * Gets the overall gas-liquid separation efficiency.
   *
   * @return overallGasLiquidEfficiency [0-1]
   */
  public double getOverallGasLiquidEfficiency() {
    return overallGasLiquidEfficiency;
  }

  /**
   * Gets the gravity section cut diameter [m].
   *
   * @return gravityCutDiameter [m]
   */
  public double getGravityCutDiameter() {
    return gravityCutDiameter;
  }

  /**
   * Gets the liquid-liquid gravity section efficiency.
   *
   * @return liquidLiquidGravityEfficiency [0-1]
   */
  public double getLiquidLiquidGravityEfficiency() {
    return liquidLiquidGravityEfficiency;
  }

  // ----- Setters for configuration -----

  /**
   * Sets the inlet droplet size distribution for liquid-in-gas separation.
   *
   * @param dsd droplet size distribution
   */
  public void setGasLiquidDSD(DropletSizeDistribution dsd) {
    this.gasLiquidDSD = dsd;
  }

  /**
   * Gets the gas-liquid droplet size distribution.
   *
   * @return dsd or null if not set
   */
  public DropletSizeDistribution getGasLiquidDSD() {
    return gasLiquidDSD;
  }

  /**
   * Sets the inlet droplet size distribution for oil-in-water droplets.
   *
   * @param dsd droplet size distribution
   */
  public void setOilInWaterDSD(DropletSizeDistribution dsd) {
    this.oilInWaterDSD = dsd;
  }

  /**
   * Sets the inlet droplet size distribution for water-in-oil droplets.
   *
   * @param dsd droplet size distribution
   */
  public void setWaterInOilDSD(DropletSizeDistribution dsd) {
    this.waterInOilDSD = dsd;
  }

  /**
   * Sets the inlet droplet size distribution for gas bubbles entrained in liquid.
   *
   * @param dsd bubble size distribution
   */
  public void setGasBubbleDSD(DropletSizeDistribution dsd) {
    this.gasBubbleDSD = dsd;
  }

  /**
   * Sets the grade efficiency curve for the mist eliminator.
   *
   * @param curve grade efficiency curve
   */
  public void setMistEliminatorCurve(GradeEfficiencyCurve curve) {
    this.mistEliminatorCurve = curve;
  }

  /**
   * Gets the mist eliminator grade efficiency curve.
   *
   * @return curve or null if not set
   */
  public GradeEfficiencyCurve getMistEliminatorCurve() {
    return mistEliminatorCurve;
  }

  /**
   * Sets the grade efficiency curve for a liquid-liquid coalescer (plate pack, etc.).
   *
   * @param curve grade efficiency curve
   */
  public void setOilWaterCoalescerCurve(GradeEfficiencyCurve curve) {
    this.oilWaterCoalescerCurve = curve;
  }

  /**
   * Sets whether to include gravity pre-separation before the mist eliminator.
   *
   * @param include true to include gravity section (default true)
   */
  public void setIncludeGravitySection(boolean include) {
    this.includeGravitySection = include;
  }

  /**
   * Gets whether gravity pre-separation is included.
   *
   * @return true if gravity section is included
   */
  public boolean isIncludeGravitySection() {
    return includeGravitySection;
  }

  /**
   * Sets the liquid-liquid residence time override [s].
   *
   * @param seconds residence time [s]
   */
  public void setLiquidLiquidResidenceTime(double seconds) {
    this.liquidLiquidResidenceTimeOverride = seconds;
  }

  /**
   * Sets calibration multiplier for liquid-in-gas carryover
   * (oil-in-gas and water-in-gas fractions).
   *
   * @param factor calibration factor (&gt;= 0), where 1.0 means no calibration
   */
  public void setLiquidInGasCalibrationFactor(double factor) {
    this.liquidInGasCalibrationFactor = Math.max(0.0, factor);
  }

  /**
   * Gets calibration multiplier for liquid-in-gas carryover.
   *
   * @return calibration factor
   */
  public double getLiquidInGasCalibrationFactor() {
    return liquidInGasCalibrationFactor;
  }

  /**
   * Sets calibration multiplier for gas carry-under (gas-in-oil and gas-in-water).
   *
   * @param factor calibration factor (&gt;= 0), where 1.0 means no calibration
   */
  public void setGasCarryUnderCalibrationFactor(double factor) {
    this.gasCarryUnderCalibrationFactor = Math.max(0.0, factor);
  }

  /**
   * Gets calibration multiplier for gas carry-under.
   *
   * @return calibration factor
   */
  public double getGasCarryUnderCalibrationFactor() {
    return gasCarryUnderCalibrationFactor;
  }

  /**
   * Sets calibration multiplier for liquid-liquid cross-contamination
   * (oil-in-water and water-in-oil fractions).
   *
   * @param factor calibration factor (&gt;= 0), where 1.0 means no calibration
   */
  public void setLiquidLiquidCalibrationFactor(double factor) {
    this.liquidLiquidCalibrationFactor = Math.max(0.0, factor);
  }

  /**
   * Gets calibration multiplier for liquid-liquid cross-contamination.
   *
   * @return calibration factor
   */
  public double getLiquidLiquidCalibrationFactor() {
    return liquidLiquidCalibrationFactor;
  }

  /**
   * Result object for one-point calibration of entrainment factors.
   */
  public static class CalibrationSummary implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1002L;

    /** Previous liquid-in-gas factor. */
    public final double previousLiquidInGasFactor;
    /** Previous gas carry-under factor. */
    public final double previousGasCarryUnderFactor;
    /** Previous liquid-liquid factor. */
    public final double previousLiquidLiquidFactor;

    /** New liquid-in-gas factor. */
    public final double newLiquidInGasFactor;
    /** New gas carry-under factor. */
    public final double newGasCarryUnderFactor;
    /** New liquid-liquid factor. */
    public final double newLiquidLiquidFactor;

    /** Number of usable points in liquid-in-gas group. */
    public final int liquidInGasPointsUsed;
    /** Number of usable points in gas carry-under group. */
    public final int gasCarryUnderPointsUsed;
    /** Number of usable points in liquid-liquid group. */
    public final int liquidLiquidPointsUsed;

    /**
     * Constructs a calibration summary.
     *
     * @param prevLig previous liquid-in-gas factor
     * @param prevGcu previous gas carry-under factor
     * @param prevLiqLiq previous liquid-liquid factor
     * @param newLig new liquid-in-gas factor
     * @param newGcu new gas carry-under factor
     * @param newLiqLiq new liquid-liquid factor
     * @param ligPoints number of liquid-in-gas points used
     * @param gcuPoints number of gas carry-under points used
     * @param liqLiqPoints number of liquid-liquid points used
     */
    public CalibrationSummary(double prevLig, double prevGcu, double prevLiqLiq, double newLig,
        double newGcu, double newLiqLiq, int ligPoints, int gcuPoints, int liqLiqPoints) {
      this.previousLiquidInGasFactor = prevLig;
      this.previousGasCarryUnderFactor = prevGcu;
      this.previousLiquidLiquidFactor = prevLiqLiq;
      this.newLiquidInGasFactor = newLig;
      this.newGasCarryUnderFactor = newGcu;
      this.newLiquidLiquidFactor = newLiqLiq;
      this.liquidInGasPointsUsed = ligPoints;
      this.gasCarryUnderPointsUsed = gcuPoints;
      this.liquidLiquidPointsUsed = liqLiqPoints;
    }
  }

  /**
   * One field/vendor calibration case with modeled and measured entrainment fractions.
   */
  public static class CalibrationCase implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1003L;

    /** Optional case identifier. */
    public final String caseId;

    /** Modeled fractions. */
    public final double modeledOilInGas;
    public final double modeledWaterInGas;
    public final double modeledGasInOil;
    public final double modeledGasInWater;
    public final double modeledOilInWater;
    public final double modeledWaterInOil;

    /** Measured fractions. */
    public final double measuredOilInGas;
    public final double measuredWaterInGas;
    public final double measuredGasInOil;
    public final double measuredGasInWater;
    public final double measuredOilInWater;
    public final double measuredWaterInOil;

    /**
     * Constructs a calibration case.
     *
     * @param caseIdArg case identifier
     * @param moig modeled oil-in-gas
     * @param mwig modeled water-in-gas
     * @param mgio modeled gas-in-oil
     * @param mgiw modeled gas-in-water
     * @param moiw modeled oil-in-water
     * @param mwio modeled water-in-oil
     * @param eoig measured oil-in-gas
     * @param ewig measured water-in-gas
     * @param egio measured gas-in-oil
     * @param egiw measured gas-in-water
     * @param eoiw measured oil-in-water
     * @param ewio measured water-in-oil
     */
    public CalibrationCase(String caseIdArg, double moig, double mwig, double mgio, double mgiw,
        double moiw, double mwio, double eoig, double ewig, double egio, double egiw,
        double eoiw, double ewio) {
      this.caseId = caseIdArg;
      this.modeledOilInGas = moig;
      this.modeledWaterInGas = mwig;
      this.modeledGasInOil = mgio;
      this.modeledGasInWater = mgiw;
      this.modeledOilInWater = moiw;
      this.modeledWaterInOil = mwio;
      this.measuredOilInGas = eoig;
      this.measuredWaterInGas = ewig;
      this.measuredGasInOil = egio;
      this.measuredGasInWater = egiw;
      this.measuredOilInWater = eoiw;
      this.measuredWaterInOil = ewio;
    }
  }

  /**
   * Summary for batch calibration against multiple cases.
   */
  public static class BatchCalibrationSummary extends CalibrationSummary {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1004L;

    /** Number of calibration cases processed. */
    public final int casesProcessed;

    /** Mean absolute percentage error before calibration [-]. */
    public final double mapeBefore;

    /** Mean absolute percentage error after calibration [-]. */
    public final double mapeAfter;

    /**
     * Constructs a batch calibration summary.
     *
     * @param base base summary fields
     * @param cases number of cases processed
     * @param before mean absolute percentage error before calibration
     * @param after mean absolute percentage error after calibration
     */
    public BatchCalibrationSummary(CalibrationSummary base, int cases, double before,
        double after) {
      super(base.previousLiquidInGasFactor, base.previousGasCarryUnderFactor,
          base.previousLiquidLiquidFactor, base.newLiquidInGasFactor,
          base.newGasCarryUnderFactor, base.newLiquidLiquidFactor, base.liquidInGasPointsUsed,
          base.gasCarryUnderPointsUsed, base.liquidLiquidPointsUsed);
      this.casesProcessed = cases;
      this.mapeBefore = before;
      this.mapeAfter = after;
    }
  }

  /**
   * Loads calibration cases from a CSV file.
   *
   * <p>
   * Required headers (case-insensitive):
   * </p>
   * <ul>
   * <li>modeled_oil_in_gas, modeled_water_in_gas, modeled_gas_in_oil,
   * modeled_gas_in_water, modeled_oil_in_water, modeled_water_in_oil</li>
   * <li>measured_oil_in_gas, measured_water_in_gas, measured_gas_in_oil,
   * measured_gas_in_water, measured_oil_in_water, measured_water_in_oil</li>
   * </ul>
   *
   * <p>
   * Optional header: case_id.
   * </p>
   *
   * @param filePath path to CSV file
   * @return list of calibration cases
   * @throws IOException if reading fails or required columns are missing
   */
  public static List<CalibrationCase> loadCalibrationCasesFromCsv(String filePath)
      throws IOException {
    List<CalibrationCase> cases = new ArrayList<CalibrationCase>();

    BufferedReader reader = new BufferedReader(new FileReader(filePath));
    try {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new IOException("Calibration CSV is empty: " + filePath);
      }

      String[] headers = headerLine.split(",");
      Map<String, Integer> idx = new HashMap<String, Integer>();
      for (int i = 0; i < headers.length; i++) {
        idx.put(headers[i].trim().toLowerCase(), Integer.valueOf(i));
      }

      String[] required = new String[] {"modeled_oil_in_gas", "modeled_water_in_gas",
          "modeled_gas_in_oil", "modeled_gas_in_water", "modeled_oil_in_water",
          "modeled_water_in_oil", "measured_oil_in_gas", "measured_water_in_gas",
          "measured_gas_in_oil", "measured_gas_in_water", "measured_oil_in_water",
          "measured_water_in_oil"};
      for (int i = 0; i < required.length; i++) {
        if (!idx.containsKey(required[i])) {
          throw new IOException("Missing required CSV column: " + required[i]);
        }
      }

      String line;
      int row = 1;
      while ((line = reader.readLine()) != null) {
        row++;
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
          continue;
        }
        String[] cols = line.split(",", -1);

        String caseId = getString(cols, idx, "case_id", "row-" + row);
        CalibrationCase c = new CalibrationCase(caseId,
            getDouble(cols, idx, "modeled_oil_in_gas", row),
            getDouble(cols, idx, "modeled_water_in_gas", row),
            getDouble(cols, idx, "modeled_gas_in_oil", row),
            getDouble(cols, idx, "modeled_gas_in_water", row),
            getDouble(cols, idx, "modeled_oil_in_water", row),
            getDouble(cols, idx, "modeled_water_in_oil", row),
            getDouble(cols, idx, "measured_oil_in_gas", row),
            getDouble(cols, idx, "measured_water_in_gas", row),
            getDouble(cols, idx, "measured_gas_in_oil", row),
            getDouble(cols, idx, "measured_gas_in_water", row),
            getDouble(cols, idx, "measured_oil_in_water", row),
            getDouble(cols, idx, "measured_water_in_oil", row));
        cases.add(c);
      }
    } finally {
      reader.close();
    }
    return cases;
  }

  /**
   * Calibrates factors from grouped measurements (three values only).
   *
   * <p>
   * Groups are interpreted as:
   * </p>
   * <ul>
   * <li>liquid-in-gas: average of oil-in-gas and water-in-gas model values</li>
   * <li>gas carry-under: average of gas-in-oil and gas-in-water model values</li>
   * <li>liquid-liquid: average of oil-in-water and water-in-oil model values</li>
   * </ul>
   *
   * @param measuredLiquidInGas measured grouped liquid-in-gas
   * @param measuredGasCarryUnder measured grouped gas carry-under
   * @param measuredLiquidLiquid measured grouped liquid-liquid cross-contamination
   * @param modelFloor minimum model value for ratio fitting
   * @return calibration summary
   */
  public CalibrationSummary calibrateFromGroupedMeasurements(double measuredLiquidInGas,
      double measuredGasCarryUnder, double measuredLiquidLiquid, double modelFloor) {
    double prevLig = liquidInGasCalibrationFactor;
    double prevGcu = gasCarryUnderCalibrationFactor;
    double prevLiqLiq = liquidLiquidCalibrationFactor;

    double ligModel = averagePositive(oilInGasFraction, waterInGasFraction, modelFloor);
    double gcuModel = averagePositive(gasInOilFraction, gasInWaterFraction, modelFloor);
    double liqLiqModel = averagePositive(oilInWaterFraction, waterInOilFraction, modelFloor);

    int ligPoints = (ligModel > 0.0) ? 1 : 0;
    int gcuPoints = (gcuModel > 0.0) ? 1 : 0;
    int liqLiqPoints = (liqLiqModel > 0.0) ? 1 : 0;

    if (ligPoints > 0) {
      setLiquidInGasCalibrationFactor(
          safeRatio(measuredLiquidInGas, ligModel, Math.max(0.0, modelFloor)));
    }
    if (gcuPoints > 0) {
      setGasCarryUnderCalibrationFactor(
          safeRatio(measuredGasCarryUnder, gcuModel, Math.max(0.0, modelFloor)));
    }
    if (liqLiqPoints > 0) {
      setLiquidLiquidCalibrationFactor(
          safeRatio(measuredLiquidLiquid, liqLiqModel, Math.max(0.0, modelFloor)));
    }

    return new CalibrationSummary(prevLig, prevGcu, prevLiqLiq, liquidInGasCalibrationFactor,
        gasCarryUnderCalibrationFactor, liquidLiquidCalibrationFactor, ligPoints, gcuPoints,
        liqLiqPoints);
  }

  /**
   * Calibrates factors from a list of modeled/measured cases.
   *
   * @param cases calibration cases
   * @param modelFloor minimum model value for ratio fitting
   * @return batch calibration summary with pre/post error metrics
   */
  public BatchCalibrationSummary calibrateFromCaseLibrary(List<CalibrationCase> cases,
      double modelFloor) {
    if (cases == null || cases.isEmpty()) {
      CalibrationSummary base = new CalibrationSummary(liquidInGasCalibrationFactor,
          gasCarryUnderCalibrationFactor, liquidLiquidCalibrationFactor,
          liquidInGasCalibrationFactor, gasCarryUnderCalibrationFactor,
          liquidLiquidCalibrationFactor, 0, 0, 0);
      return new BatchCalibrationSummary(base, 0, 0.0, 0.0);
    }

    double prevLig = liquidInGasCalibrationFactor;
    double prevGcu = gasCarryUnderCalibrationFactor;
    double prevLiqLiq = liquidLiquidCalibrationFactor;

    List<Double> ligRatios = new ArrayList<Double>();
    List<Double> gcuRatios = new ArrayList<Double>();
    List<Double> liqLiqRatios = new ArrayList<Double>();

    double beforeErrSum = 0.0;
    int beforeErrCount = 0;

    for (int i = 0; i < cases.size(); i++) {
      CalibrationCase c = cases.get(i);

      addRatio(ligRatios, safeRatio(c.measuredOilInGas, c.modeledOilInGas, modelFloor));
      addRatio(ligRatios, safeRatio(c.measuredWaterInGas, c.modeledWaterInGas, modelFloor));
      addRatio(gcuRatios, safeRatio(c.measuredGasInOil, c.modeledGasInOil, modelFloor));
      addRatio(gcuRatios, safeRatio(c.measuredGasInWater, c.modeledGasInWater, modelFloor));
      addRatio(liqLiqRatios, safeRatio(c.measuredOilInWater, c.modeledOilInWater, modelFloor));
      addRatio(liqLiqRatios, safeRatio(c.measuredWaterInOil, c.modeledWaterInOil, modelFloor));

      beforeErrSum += pairMape(c.modeledOilInGas, c.measuredOilInGas, modelFloor);
      beforeErrSum += pairMape(c.modeledWaterInGas, c.measuredWaterInGas, modelFloor);
      beforeErrSum += pairMape(c.modeledGasInOil, c.measuredGasInOil, modelFloor);
      beforeErrSum += pairMape(c.modeledGasInWater, c.measuredGasInWater, modelFloor);
      beforeErrSum += pairMape(c.modeledOilInWater, c.measuredOilInWater, modelFloor);
      beforeErrSum += pairMape(c.modeledWaterInOil, c.measuredWaterInOil, modelFloor);
      beforeErrCount += 6;
    }

    if (!ligRatios.isEmpty()) {
      setLiquidInGasCalibrationFactor(meanList(ligRatios));
    }
    if (!gcuRatios.isEmpty()) {
      setGasCarryUnderCalibrationFactor(meanList(gcuRatios));
    }
    if (!liqLiqRatios.isEmpty()) {
      setLiquidLiquidCalibrationFactor(meanList(liqLiqRatios));
    }

    double afterErrSum = 0.0;
    int afterErrCount = 0;
    for (int i = 0; i < cases.size(); i++) {
      CalibrationCase c = cases.get(i);
      afterErrSum += pairMape(clamp01(c.modeledOilInGas * liquidInGasCalibrationFactor),
          c.measuredOilInGas, modelFloor);
      afterErrSum += pairMape(clamp01(c.modeledWaterInGas * liquidInGasCalibrationFactor),
          c.measuredWaterInGas, modelFloor);
      afterErrSum += pairMape(clamp01(c.modeledGasInOil * gasCarryUnderCalibrationFactor),
          c.measuredGasInOil, modelFloor);
      afterErrSum += pairMape(clamp01(c.modeledGasInWater * gasCarryUnderCalibrationFactor),
          c.measuredGasInWater, modelFloor);
      afterErrSum += pairMape(clamp01(c.modeledOilInWater * liquidLiquidCalibrationFactor),
          c.measuredOilInWater, modelFloor);
      afterErrSum += pairMape(clamp01(c.modeledWaterInOil * liquidLiquidCalibrationFactor),
          c.measuredWaterInOil, modelFloor);
      afterErrCount += 6;
    }

    CalibrationSummary base = new CalibrationSummary(prevLig, prevGcu, prevLiqLiq,
        liquidInGasCalibrationFactor, gasCarryUnderCalibrationFactor,
        liquidLiquidCalibrationFactor, ligRatios.size(), gcuRatios.size(), liqLiqRatios.size());

    double mapeBefore = beforeErrCount > 0 ? beforeErrSum / beforeErrCount : 0.0;
    double mapeAfter = afterErrCount > 0 ? afterErrSum / afterErrCount : 0.0;
    return new BatchCalibrationSummary(base, cases.size(), mapeBefore, mapeAfter);
  }

  /**
   * Builds a JSON calibration report with factors, aggregate error metrics, and per-case residuals.
   *
   * @param cases calibration cases used in fitting
   * @param summary batch calibration summary returned by {@link #calibrateFromCaseLibrary}
   * @param modelFloor minimum floor used for percentage error calculations
   * @return pretty-printed JSON calibration report
   */
  public String buildBatchCalibrationReportJson(List<CalibrationCase> cases,
      BatchCalibrationSummary summary, double modelFloor) {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("casesProcessed", summary != null ? summary.casesProcessed : 0);
    report.put("mapeBefore", summary != null ? summary.mapeBefore : 0.0);
    report.put("mapeAfter", summary != null ? summary.mapeAfter : 0.0);

    report.put("previousLiquidInGasFactor",
        summary != null ? summary.previousLiquidInGasFactor : liquidInGasCalibrationFactor);
    report.put("previousGasCarryUnderFactor",
        summary != null ? summary.previousGasCarryUnderFactor : gasCarryUnderCalibrationFactor);
    report.put("previousLiquidLiquidFactor",
        summary != null ? summary.previousLiquidLiquidFactor : liquidLiquidCalibrationFactor);

    report.put("newLiquidInGasFactor", liquidInGasCalibrationFactor);
    report.put("newGasCarryUnderFactor", gasCarryUnderCalibrationFactor);
    report.put("newLiquidLiquidFactor", liquidLiquidCalibrationFactor);

    List<Map<String, Object>> residuals = new ArrayList<Map<String, Object>>();
    if (cases != null) {
      for (int i = 0; i < cases.size(); i++) {
        CalibrationCase c = cases.get(i);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("caseId", c.caseId);

        r.put("modeledOilInGas", c.modeledOilInGas);
        r.put("modeledWaterInGas", c.modeledWaterInGas);
        r.put("modeledGasInOil", c.modeledGasInOil);
        r.put("modeledGasInWater", c.modeledGasInWater);
        r.put("modeledOilInWater", c.modeledOilInWater);
        r.put("modeledWaterInOil", c.modeledWaterInOil);

        r.put("measuredOilInGas", c.measuredOilInGas);
        r.put("measuredWaterInGas", c.measuredWaterInGas);
        r.put("measuredGasInOil", c.measuredGasInOil);
        r.put("measuredGasInWater", c.measuredGasInWater);
        r.put("measuredOilInWater", c.measuredOilInWater);
        r.put("measuredWaterInOil", c.measuredWaterInOil);

        double calOilInGas = clamp01(c.modeledOilInGas * liquidInGasCalibrationFactor);
        double calWaterInGas = clamp01(c.modeledWaterInGas * liquidInGasCalibrationFactor);
        double calGasInOil = clamp01(c.modeledGasInOil * gasCarryUnderCalibrationFactor);
        double calGasInWater = clamp01(c.modeledGasInWater * gasCarryUnderCalibrationFactor);
        double calOilInWater = clamp01(c.modeledOilInWater * liquidLiquidCalibrationFactor);
        double calWaterInOil = clamp01(c.modeledWaterInOil * liquidLiquidCalibrationFactor);

        r.put("calibratedOilInGas", calOilInGas);
        r.put("calibratedWaterInGas", calWaterInGas);
        r.put("calibratedGasInOil", calGasInOil);
        r.put("calibratedGasInWater", calGasInWater);
        r.put("calibratedOilInWater", calOilInWater);
        r.put("calibratedWaterInOil", calWaterInOil);

        double mapeBefore = (pairMape(c.modeledOilInGas, c.measuredOilInGas, modelFloor)
            + pairMape(c.modeledWaterInGas, c.measuredWaterInGas, modelFloor)
            + pairMape(c.modeledGasInOil, c.measuredGasInOil, modelFloor)
            + pairMape(c.modeledGasInWater, c.measuredGasInWater, modelFloor)
            + pairMape(c.modeledOilInWater, c.measuredOilInWater, modelFloor)
            + pairMape(c.modeledWaterInOil, c.measuredWaterInOil, modelFloor)) / 6.0;
        double mapeAfter =
            (pairMape(calOilInGas, c.measuredOilInGas, modelFloor)
                + pairMape(calWaterInGas, c.measuredWaterInGas, modelFloor)
                + pairMape(calGasInOil, c.measuredGasInOil, modelFloor)
                + pairMape(calGasInWater, c.measuredGasInWater, modelFloor)
                + pairMape(calOilInWater, c.measuredOilInWater, modelFloor)
                + pairMape(calWaterInOil, c.measuredWaterInOil, modelFloor)) / 6.0;

        r.put("mapeBefore", mapeBefore);
        r.put("mapeAfter", mapeAfter);
        residuals.add(r);
      }
    }
    report.put("caseResiduals", residuals);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(report);
  }

  /**
   * Saves batch calibration report JSON to disk.
   *
   * @param filePath output file path
   * @param cases calibration cases used in fitting
   * @param summary batch calibration summary
   * @param modelFloor minimum floor used for percentage error calculations
   * @throws IOException if file writing fails
   */
  public void saveBatchCalibrationReportJson(String filePath, List<CalibrationCase> cases,
      BatchCalibrationSummary summary, double modelFloor) throws IOException {
    String json = buildBatchCalibrationReportJson(cases, summary, modelFloor);
    FileWriter writer = new FileWriter(filePath);
    try {
      writer.write(json);
    } finally {
      writer.close();
    }
  }

  /**
   * Parses a numeric CSV column value.
   *
   * @param cols CSV columns
   * @param idx header index map
   * @param key column key
   * @param row row number (for diagnostics)
   * @return parsed double value
   * @throws IOException if parsing fails
   */
  private static double getDouble(String[] cols, Map<String, Integer> idx, String key, int row)
      throws IOException {
    Integer iObj = idx.get(key);
    if (iObj == null) {
      throw new IOException("Missing column " + key + " at row " + row);
    }
    int i = iObj.intValue();
    if (i >= cols.length) {
      throw new IOException("Missing value for column " + key + " at row " + row);
    }
    try {
      return Double.parseDouble(cols[i].trim());
    } catch (NumberFormatException e) {
      throw new IOException("Invalid number in column " + key + " at row " + row + ": "
          + cols[i], e);
    }
  }

  /**
   * Parses an optional string CSV column value.
   *
   * @param cols CSV columns
   * @param idx header index map
   * @param key column key
   * @param defaultValue default value if column missing/blank
   * @return parsed string
   */
  private static String getString(String[] cols, Map<String, Integer> idx, String key,
      String defaultValue) {
    Integer iObj = idx.get(key);
    if (iObj == null) {
      return defaultValue;
    }
    int i = iObj.intValue();
    if (i >= cols.length) {
      return defaultValue;
    }
    String val = cols[i].trim();
    return val.isEmpty() ? defaultValue : val;
  }

  /**
   * Adds finite ratio values to a list.
   *
   * @param list target list
   * @param ratio ratio value
   */
  private static void addRatio(List<Double> list, double ratio) {
    if (!Double.isNaN(ratio) && !Double.isInfinite(ratio)) {
      list.add(Double.valueOf(ratio));
    }
  }

  /**
   * Returns arithmetic mean of a list of doubles.
   *
   * @param values values list
   * @return mean value
   */
  private static double meanList(List<Double> values) {
    if (values == null || values.isEmpty()) {
      return 1.0;
    }
    double sum = 0.0;
    for (int i = 0; i < values.size(); i++) {
      sum += values.get(i).doubleValue();
    }
    return sum / values.size();
  }

  /**
   * Computes absolute percentage error for one modeled/measured pair.
   *
   * @param modeled modeled value
   * @param measured measured value
   * @param floor small floor to avoid division by zero
   * @return absolute percentage error
   */
  private static double pairMape(double modeled, double measured, double floor) {
    double denom = Math.max(Math.max(0.0, measured), Math.max(1e-12, floor));
    return Math.abs(modeled - Math.max(0.0, measured)) / denom;
  }

  /**
   * Returns average of positive values above a floor, or 0 if no values qualify.
   *
   * @param a value a
   * @param b value b
   * @param floor minimum value threshold
   * @return average or 0
   */
  private static double averagePositive(double a, double b, double floor) {
    double f = Math.max(0.0, floor);
    boolean aOk = a > f;
    boolean bOk = b > f;
    if (aOk && bOk) {
      return 0.5 * (a + b);
    }
    if (aOk) {
      return a;
    }
    if (bOk) {
      return b;
    }
    return 0.0;
  }

  /**
   * Auto-calibrates entrainment multipliers from one measured benchmark point.
   *
   * <p>
   * This method uses the current calculated fractions as model values and computes group-wise
   * multipliers from measured/model ratios:
   * </p>
   * <ul>
   * <li>Liquid-in-gas: oil-in-gas and water-in-gas</li>
   * <li>Gas carry-under: gas-in-oil and gas-in-water</li>
   * <li>Liquid-liquid: oil-in-water and water-in-oil</li>
   * </ul>
   *
   * <p>
   * Ratios are only formed for model values above {@code modelFloor}. The group multiplier is the
   * arithmetic mean of valid ratios. If no valid ratios exist in a group, the existing factor is
   * retained.
   * </p>
   *
   * @param measuredOilInGas measured oil-in-gas fraction [0-1]
   * @param measuredWaterInGas measured water-in-gas fraction [0-1]
   * @param measuredGasInOil measured gas-in-oil fraction [0-1]
   * @param measuredGasInWater measured gas-in-water fraction [0-1]
   * @param measuredOilInWater measured oil-in-water fraction [0-1]
   * @param measuredWaterInOil measured water-in-oil fraction [0-1]
   * @param modelFloor minimum modeled fraction to use in ratio fitting (e.g. 1e-9)
   * @return calibration summary containing old/new factors and data-point usage
   */
  public CalibrationSummary calibrateFromMeasuredFractions(double measuredOilInGas,
      double measuredWaterInGas, double measuredGasInOil, double measuredGasInWater,
      double measuredOilInWater, double measuredWaterInOil, double modelFloor) {

    double prevLig = liquidInGasCalibrationFactor;
    double prevGcu = gasCarryUnderCalibrationFactor;
    double prevLiqLiq = liquidLiquidCalibrationFactor;

    double[] ligRatios = new double[] {safeRatio(measuredOilInGas, oilInGasFraction, modelFloor),
        safeRatio(measuredWaterInGas, waterInGasFraction, modelFloor)};
    double[] gcuRatios =
        new double[] {safeRatio(measuredGasInOil, gasInOilFraction, modelFloor),
            safeRatio(measuredGasInWater, gasInWaterFraction, modelFloor)};
    double[] liqLiqRatios =
        new double[] {safeRatio(measuredOilInWater, oilInWaterFraction, modelFloor),
            safeRatio(measuredWaterInOil, waterInOilFraction, modelFloor)};

    int ligPoints = countFinite(ligRatios);
    int gcuPoints = countFinite(gcuRatios);
    int liqLiqPoints = countFinite(liqLiqRatios);

    if (ligPoints > 0) {
      setLiquidInGasCalibrationFactor(averageFinite(ligRatios, prevLig));
    }
    if (gcuPoints > 0) {
      setGasCarryUnderCalibrationFactor(averageFinite(gcuRatios, prevGcu));
    }
    if (liqLiqPoints > 0) {
      setLiquidLiquidCalibrationFactor(averageFinite(liqLiqRatios, prevLiqLiq));
    }

    return new CalibrationSummary(prevLig, prevGcu, prevLiqLiq, liquidInGasCalibrationFactor,
        gasCarryUnderCalibrationFactor, liquidLiquidCalibrationFactor, ligPoints, gcuPoints,
        liqLiqPoints);
  }

  /**
   * Computes measured/model ratio for calibration if modeled value exceeds floor.
   *
   * @param measured measured fraction
   * @param modeled modeled fraction
   * @param floor minimum modeled value for ratio use
   * @return ratio, or NaN if modeled value is below floor
   */
  private static double safeRatio(double measured, double modeled, double floor) {
    double m = Math.max(0.0, measured);
    if (modeled <= Math.max(0.0, floor)) {
      return Double.NaN;
    }
    return m / modeled;
  }

  /**
   * Counts finite values in an array.
   *
   * @param values input values
   * @return number of finite values
   */
  private static int countFinite(double[] values) {
    int count = 0;
    for (int i = 0; i < values.length; i++) {
      if (!Double.isNaN(values[i]) && !Double.isInfinite(values[i])) {
        count++;
      }
    }
    return count;
  }

  /**
   * Calculates the arithmetic mean of finite values, or returns a default value.
   *
   * @param values input values
   * @param defaultValue fallback if no finite values are present
   * @return arithmetic mean of finite values or defaultValue
   */
  private static double averageFinite(double[] values, double defaultValue) {
    double sum = 0.0;
    int count = 0;
    for (int i = 0; i < values.length; i++) {
      if (!Double.isNaN(values[i]) && !Double.isInfinite(values[i])) {
        sum += values[i];
        count++;
      }
    }
    if (count == 0) {
      return defaultValue;
    }
    return sum / count;
  }

  // ----- Enhanced framework getters/setters -----

  /**
   * Enables or disables the enhanced calculation chain (flow regime, inlet device, geometry).
   *
   * @param use true to enable enhanced calculation
   */
  public void setUseEnhancedCalculation(boolean use) {
    this.useEnhancedCalculation = use;
  }

  /**
   * Returns whether enhanced calculation is enabled.
   *
   * @return true if enhanced calculation is enabled
   */
  public boolean isUseEnhancedCalculation() {
    return useEnhancedCalculation;
  }

  /**
   * Sets the inlet pipe diameter for flow regime prediction.
   *
   * @param diameter inlet pipe diameter [m]
   */
  public void setInletPipeDiameter(double diameter) {
    this.inletPipeDiameter = diameter;
  }

  /**
   * Gets the inlet pipe diameter.
   *
   * @return inlet pipe diameter [m]
   */
  public double getInletPipeDiameter() {
    return inletPipeDiameter;
  }

  /**
   * Sets the gas-liquid interfacial tension for DSD generation.
   *
   * @param sigma interfacial tension [N/m]
   */
  public void setSurfaceTension(double sigma) {
    this.surfaceTension = sigma;
  }

  /**
   * Gets the gas-liquid interfacial tension.
   *
   * @return interfacial tension [N/m]
   */
  public double getSurfaceTension() {
    return surfaceTension;
  }

  /**
   * Sets the oil-water interfacial tension for liquid-liquid DSD.
   *
   * @param sigma interfacial tension [N/m]
   */
  public void setOilWaterInterfacialTension(double sigma) {
    this.oilWaterInterfacialTension = sigma;
  }

  /**
   * Gets the oil-water interfacial tension.
   *
   * @return interfacial tension [N/m]
   */
  public double getOilWaterInterfacialTension() {
    return oilWaterInterfacialTension;
  }

  /**
   * Sets the inlet device model.
   *
   * @param model inlet device model
   */
  public void setInletDeviceModel(InletDeviceModel model) {
    this.inletDeviceModel = model;
  }

  /**
   * Gets the inlet device model.
   *
   * @return inlet device model or null
   */
  public InletDeviceModel getInletDeviceModel() {
    return inletDeviceModel;
  }

  /**
   * Sets the flow regime calculator.
   *
   * @param calc flow regime calculator
   */
  public void setFlowRegimeCalculator(MultiphaseFlowRegime calc) {
    this.flowRegimeCalc = calc;
  }

  /**
   * Gets the flow regime calculator.
   *
   * @return flow regime calculator or null
   */
  public MultiphaseFlowRegime getFlowRegimeCalculator() {
    return flowRegimeCalc;
  }

  /**
   * Sets the separator geometry calculator.
   *
   * @param calc geometry calculator
   */
  public void setGeometryCalculator(SeparatorGeometryCalculator calc) {
    this.geometryCalc = calc;
  }

  /**
   * Gets the separator geometry calculator.
   *
   * @return geometry calculator or null
   */
  public SeparatorGeometryCalculator getGeometryCalculator() {
    return geometryCalc;
  }

  /**
   * Gets the K-factor (Souders-Brown) at operating conditions.
   *
   * @return kFactor [m/s]
   */
  public double getKFactor() {
    return kFactor;
  }

  /**
   * Gets the K-factor utilization (operating K / design K).
   *
   * @return kFactorUtilization [0-...], values above 1.0 indicate flooding
   */
  public double getKFactorUtilization() {
    return kFactorUtilization;
  }

  /**
   * Gets the inlet device bulk separation efficiency.
   *
   * @return inletDeviceBulkEfficiency [0-1]
   */
  public double getInletDeviceBulkEfficiency() {
    return inletDeviceBulkEfficiency;
  }

  /**
   * Gets the predicted inlet flow regime.
   *
   * @return flow regime enum or null if not calculated
   */
  public MultiphaseFlowRegime.FlowRegime getInletFlowRegime() {
    return inletFlowRegime;
  }

  /**
   * Returns whether the mist eliminator is flooded (K-factor exceeds design).
   *
   * @return true if flooded
   */
  public boolean isMistEliminatorFlooded() {
    return mistEliminatorFlooded;
  }

  /**
   * Gets the DSD after inlet device transformation.
   *
   * @return transformed DSD or null if not calculated
   */
  public DropletSizeDistribution getPostInletDeviceDSD() {
    return postInletDeviceDSD;
  }

  /**
   * Sets the oil volume fraction in the liquid phase for three-phase calculations.
   *
   * <p>
   * This fraction is used when splitting the liquid flow between the oil and water phases
   * in the vessel geometry calculation. The default of 0.5 (50/50 split) should be
   * overridden by the {@code Separator.run()} method using the actual phase volumes
   * from the thermodynamic system.
   * </p>
   *
   * @param fraction oil volume fraction [0-1]
   */
  public void setOilVolumeFraction(double fraction) {
    this.oilVolumeFraction = Math.max(0.01, Math.min(0.99, fraction));
  }

  /**
   * Gets the oil volume fraction used in three-phase calculations.
   *
   * @return oil volume fraction [0-1]
   */
  public double getOilVolumeFraction() {
    return oilVolumeFraction;
  }

  /**
   * Enables or disables the turbulent diffusion correction on the gravity cut diameter.
   *
   * <p>
   * When enabled (default), the Csanady (1963) turbulent dispersion correction increases
   * the effective cut diameter to account for turbulence in the separator vessel, giving
   * a more accurate (less optimistic) prediction at higher gas loads. Disable only for
   * quiescent-flow validation cases.
   * </p>
   *
   * @param apply true to apply correction (default), false to use pure gravity settling
   */
  public void setApplyTurbulenceCorrection(boolean apply) {
    this.applyTurbulenceCorrection = apply;
  }

  /**
   * Returns whether the turbulent diffusion correction is active.
   *
   * @return true if turbulence correction is applied
   */
  public boolean isApplyTurbulenceCorrection() {
    return applyTurbulenceCorrection;
  }

  /**
   * Returns the API 12J compliance check result.
   *
   * <p>
   * Populated automatically after {@link #calculate} when a gravity cut diameter is
   * available. Returns {@code null} before the first calculation or when insufficient
   * geometry data is available.
   * </p>
   *
   * @return API 12J compliance result, or {@code null} if not yet computed
   */
  public DropletSettlingCalculator.ApiComplianceResult getApiComplianceResult() {
    return apiComplianceResult;
  }

  /**
   * Generates a default Rosin-Rammler DSD for liquid-liquid droplets using the Hinze (1955)
   * maximum stable droplet breakup criterion, adapted for oil-water interfacial tension and
   * turbulent energy input at the separator inlet.
   *
   * <p>
   * The maximum stable droplet diameter in turbulent flow (Hinze, 1955):
   * $d_{max} = C \cdot (\sigma / \rho_c)^{3/5} \cdot \epsilon^{-2/5}$
   * where $\epsilon$ is the turbulent energy dissipation rate estimated from inlet
   * momentum: $\epsilon \approx u_n^3 / D_{nozzle}$.
   * </p>
   *
   * <p>
   * The median diameter is set to $d_{max} / 4$ and a Rosin-Rammler spread of 1.8
   * is used, consistent with oil-water emulsion data (Luo and Svendsen, 1996).
   * </p>
   *
   * <p>
   * References:
   * </p>
   * <ul>
   * <li>Hinze, J.O. (1955). Fundamentals of the hydrodynamic mechanism of splitting in
   * dispersion processes. <i>AIChE J.</i>, 1(3), 289-295.</li>
   * <li>Luo, H., Svendsen, H.F. (1996). Theoretical model for drop and bubble breakup in
   * turbulent dispersions. <i>AIChE J.</i>, 42(5), 1225-1233.</li>
   * </ul>
   *
   * @param interfacialTension oil-water interfacial tension [N/m]
   * @param continuousDensity continuous phase density [kg/m3]
   * @param nozzleVelocity inlet nozzle velocity [m/s]
   * @param nozzleDiameter inlet nozzle diameter [m]
   * @return Rosin-Rammler DSD representing the initial liquid-liquid droplet population
   */
  public static DropletSizeDistribution generateLiquidLiquidDSD(double interfacialTension,
      double continuousDensity, double nozzleVelocity, double nozzleDiameter) {

    if (nozzleVelocity <= 0 || nozzleDiameter <= 0 || continuousDensity <= 0) {
      // Fallback: typical oil-water DSD in a gravity separator, d50 ~ 100 um
      return DropletSizeDistribution.rosinRammler(100e-6, 1.8);
    }

    // Turbulent energy dissipation at nozzle (Kolmogorov length-scale estimate)
    double epsilon = nozzleVelocity * nozzleVelocity * nozzleVelocity / nozzleDiameter;

    // Hinze maximum stable droplet diameter: d_max = C * (sigma/rho)^(3/5) * eps^(-2/5)
    // C = 0.725 (Kolmogorov-inertial regime constant, dimensionless We_crit = 1.17)
    double hinzeC = 0.725;
    double sigmaOverRho = interfacialTension / continuousDensity;
    double dMax = hinzeC * Math.pow(sigmaOverRho, 0.6) * Math.pow(epsilon, -0.4);

    // Median diameter ~ dMax / 4 (representative of entrained population)
    double d50 = dMax / 4.0;

    // Clamp to physically reasonable range: 10 um - 5 mm
    d50 = Math.max(10e-6, Math.min(5e-3, d50));

    // Rosin-Rammler spread = 1.8 (typical oil-water emulsion, Luo-Svendsen 1996)
    return DropletSizeDistribution.rosinRammler(d50 * 1.6, 1.8); // d0 = d50 / 0.63^(1/n)
  }
}
