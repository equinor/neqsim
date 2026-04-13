package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;
import java.util.ArrayList;
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

  /** Whether flooding was detected in the mist eliminator. */
  private boolean mistEliminatorFlooded = false;

  /** DSD after inlet device transformation (intermediate result). */
  private DropletSizeDistribution postInletDeviceDSD;

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
      return;
    }

    // --- Standard calculation (original path) ---
    calculateStandard(gasDensity, oilDensity, waterDensity, gasViscosity, oilViscosity,
        waterViscosity, gasVelocity, vesselDiameter, vesselLength, orientation,
        liquidLevelFraction);
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
      calcGasBubbleCarryUnder(gasDensity, liquidDensity, liquidViscosity, vesselDiameter,
          vesselLength, orientation, liquidLevelFraction);
    }

    // --- Liquid-liquid separation (three-phase) ---
    if (oilDensity > 0 && waterDensity > 0) {
      calcLiquidLiquidSeparation(oilDensity, waterDensity, oilViscosity, waterViscosity,
          vesselDiameter, vesselLength, orientation, liquidLevelFraction);
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
      // Three-phase: assume equal oil/water split (simplification)
      double oilFraction = 0.5;
      geometryCalc.calculateThreePhase(vesselGasFlow, vesselLiquidFlow * oilFraction,
          vesselLiquidFlow * (1.0 - oilFraction), oilFraction);
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

      GradeEfficiencyCurve gravityCurve = GradeEfficiencyCurve.gravity(gravityCutDiameter);
      gravitySectionEfficiency = gravityCurve.calcOverallEfficiency(gravityInletDSD);

      // ============================================================
      // STAGE 5: Mist eliminator (with flooding check)
      // ============================================================
      if (mistEliminatorCurve != null && !mistEliminatorFlooded) {
        mistEliminatorEfficiency = mistEliminatorCurve.calcOverallEfficiency(gravityInletDSD);
        overallGasLiquidEfficiency =
            calcCombinedEfficiency(gravityInletDSD, gravityCurve, mistEliminatorCurve);
      } else if (mistEliminatorCurve != null && mistEliminatorFlooded) {
        // Flooded mist eliminator — efficiency severely degraded
        mistEliminatorEfficiency = 0.0;
        overallGasLiquidEfficiency = gravitySectionEfficiency;
      } else {
        overallGasLiquidEfficiency = gravitySectionEfficiency;
      }
    } else if (mistEliminatorCurve != null && gravityInletDSD != null && !mistEliminatorFlooded) {
      mistEliminatorEfficiency = mistEliminatorCurve.calcOverallEfficiency(gravityInletDSD);
      overallGasLiquidEfficiency = mistEliminatorEfficiency;
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
  private void calcGasBubbleCarryUnder(double gasDensity, double liquidDensity,
      double liquidViscosity, double diameter, double length, String orientation,
      double liquidLevelFrac) {

    double liquidHeight;
    if ("vertical".equalsIgnoreCase(orientation)) {
      liquidHeight = length * liquidLevelFrac;
    } else {
      liquidHeight = diameter * liquidLevelFrac;
    }

    // Liquid residence time from geometry
    double liquidVolume = Math.PI * diameter * diameter / 4.0 * liquidHeight;
    // Estimate a typical liquid velocity (assume same approach)
    double liquidResidenceTime = liquidHeight > 0 ? 120.0 : 0.0; // Default 120 s if not computed

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
}
