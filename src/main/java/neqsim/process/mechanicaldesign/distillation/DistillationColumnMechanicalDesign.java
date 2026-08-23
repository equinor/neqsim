package neqsim.process.mechanicaldesign.distillation;

import java.util.List;
import java.util.function.DoubleSupplier;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.costestimation.column.ColumnCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.PackedColumn;
import neqsim.process.equipment.distillation.internals.ColumnInternalsDesigner;
import neqsim.process.equipment.distillation.internals.PackingHydraulicsCalculator;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternal;
import neqsim.process.mechanicaldesign.separator.internals.InternalOperatingWindow;

/**
 * Mechanical design class for distillation columns.
 *
 * <p>
 * Handles design calculations for tray-based and packed distillation columns including:
 * </p>
 * <ul>
 * <li>Column vessel sizing (diameter, height, wall thickness)</li>
 * <li>Tray hydraulics (weir loading, flooding, pressure drop)</li>
 * <li>Internals design (trays, structured or random packing, and outlet demisters)</li>
 * <li>Capacity utilization, bottleneck identification, and retrofit screening</li>
 * <li>Reboiler and condenser duty requirements</li>
 * </ul>
 *
 * @author AGAS
 * @version $Id: $Id
 */
public class DistillationColumnMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // Column vessel dimensions
  /** Column internal diameter [m]. */
  private double columnDiameter = 0.0;

  /** Column tangent-to-tangent height [m]. */
  private double columnHeight = 0.0;

  /** Column wall thickness [mm]. */
  private double columnWallThickness = 0.0;

  /** Number of theoretical trays. */
  private int numberOfTrays = 0;

  /** Actual number of trays (accounting for efficiency). */
  private int actualTrays = 0;

  /** Overall tray efficiency. */
  private double trayEfficiency = 0.65;

  // Tray hydraulics parameters
  /** Tray spacing [m]. */
  private double traySpacing = 0.6;

  /** Weir height [m]. */
  private double weirHeight = 0.05;

  /** Weir length [m]. */
  private double weirLength = 0.0;

  /** Downcomer area fraction. */
  private double downcomberAreaFraction = 0.1;

  /** Active area fraction. */
  private double activeAreaFraction = 0.85;

  /** Hole area fraction (for sieve trays). */
  private double holeAreaFraction = 0.1;

  /** Hole diameter [mm] (for sieve trays). */
  private double holeDiameter = 12.7;

  // Performance parameters
  /** Flooding factor (0-1, design typically 0.80-0.85). */
  private double floodingFactor = 0.0;

  /** Maximum flooding factor allowed. */
  private double maxFloodingFactor = 0.85;

  /** Weir liquid loading [m3/hr per m of weir]. */
  private double weirLoading = 0.0;

  /** Maximum weir loading [m3/hr per m]. */
  private double maxWeirLoading = 90.0;

  /** Tray pressure drop [mbar/tray]. */
  private double trayPressureDrop = 0.0;

  /** Total column pressure drop [bar]. */
  private double totalPressureDrop = 0.0;

  // Duties
  /** Reboiler duty [kW]. */
  private double reboilerDuty = 0.0;

  /** Condenser duty [kW]. */
  private double condenserDuty = 0.0;

  // Internals type
  /** Tray type (sieve, valve, bubble-cap). */
  private String trayType = "sieve";

  /** Material grade for column shell. */
  private String materialGrade = "SA-516-70";

  /** Design standard code. */
  private String designStandardCode = "ASME-VIII-Div1";

  /** Tray material. */
  private String trayMaterial = "SS316L";

  // Shared gas-liquid contactor internals
  /** Contactor internals type: auto, packed, sieve, valve, or bubble-cap. */
  private String contactorInternalsType = "auto";

  /** Packing preset for packed contactors. */
  private String packingPreset = "Mellapak-250Y";

  /** Whether the selected packing is structured. */
  private boolean structuredPacking = true;

  /** Packed bed height [m]. */
  private double packedHeight = 5.0;

  /** Packed or tray design flood fraction. */
  private double contactorDesignFloodFraction = 0.70;

  /** Relative hydraulic capacity factor for the selected packing. */
  private double packingHydraulicCapacityFactor = 1.0;

  /** Fixed internal diameter for rating or retrofit studies [m], or -1 for sizing. */
  private double columnDiameterOverride = -1.0;

  /** Whether an outlet demister is included in the capacity analysis. */
  private boolean outletDemisterEnabled = false;

  /** Outlet demister type. */
  private String outletDemisterType = "wire_mesh";

  /** Outlet demister database subtype. */
  private String outletDemisterSubType = "Standard Knitted";

  /** Fraction of column cross-sectional area open to the demister. */
  private double outletDemisterAreaFraction = 1.0;

  /** Maximum permitted total contactor pressure drop [bar]. */
  private double maxContactorPressureDropBar = 0.5;

  /** Most recent detailed column internals calculation. */
  private transient ColumnInternalsDesigner contactorInternalsDesigner;

  /** Most recent outlet demister model. */
  private transient DemistingInternal outletDemister;

  /** Most recent outlet demister operating window. */
  private transient InternalOperatingWindow outletDemisterOperatingWindow;

  /** Most recent combined contactor capacity result. */
  private ContactorCapacityResult contactorCapacityResult;

  /**
   * Constructor for DistillationColumnMechanicalDesign.
   *
   * @param equipment the process equipment for this design
   */
  public DistillationColumnMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new ColumnCostEstimate(this);
  }

  /**
   * Optimize the tray configuration on annualized mechanical-design cost.
   *
   * <p>
   * This is the mechanical-design entry point for column optimization. It delegates the rigorous tray-count/feed-tray
   * search to the column, then evaluates CAPEX and utility OPEX using this design object's tray efficiency and the
   * column cost-estimation correlations.
   * </p>
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product, false for the bottom product
   * @param maxTrays the maximum total tray count to try including reboiler/condenser if present
   * @return economic optimization result with process, mechanical design, and cost metrics
   * @throws IllegalStateException if the associated equipment is not a distillation column
   */
  public DistillationColumn.EconomicTrayOptimizationResult optimizeEconomicTrayConfiguration(double productSpec,
      String componentName, boolean isTopProduct, int maxTrays) {
    return getColumnForEconomicOptimization().findEconomicOptimalTrayConfiguration(productSpec, componentName,
        isTopProduct, maxTrays, 0.15, 8000.0, 25.0, 0.03, trayEfficiency);
  }

  /**
   * Optimize the tray configuration on annualized cost using supplied economics and ratios.
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product, false for the bottom product
   * @param maxTrays the maximum total tray count to try including reboiler/condenser if present
   * @param condenserRefluxRatios optional condenser reflux-ratio candidates to evaluate
   * @param reboilerRatios optional reboiler boilup/reflux-ratio candidates to evaluate
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @return economic optimization result with process, mechanical design, and cost metrics
   * @throws IllegalStateException if the associated equipment is not a distillation column
   */
  public DistillationColumn.EconomicTrayOptimizationResult optimizeEconomicTrayConfiguration(double productSpec,
      String componentName, boolean isTopProduct, int maxTrays, double[] condenserRefluxRatios, double[] reboilerRatios,
      double capitalChargeFactor, double operatingHoursPerYear, double steamCostPerTonne,
      double coolingWaterCostPerM3) {
    return getColumnForEconomicOptimization().findEconomicOptimalTrayConfiguration(productSpec, componentName,
        isTopProduct, maxTrays, condenserRefluxRatios, reboilerRatios, capitalChargeFactor, operatingHoursPerYear,
        steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency);
  }

  /**
   * Get the associated column for economic optimization.
   *
   * @return associated distillation column
   * @throws IllegalStateException if the associated equipment is not a distillation column
   */
  private DistillationColumn getColumnForEconomicOptimization() {
    if (!(getProcessEquipment() instanceof DistillationColumn)) {
      throw new IllegalStateException("Economic tray optimization requires a DistillationColumn.");
    }
    return (DistillationColumn) getProcessEquipment();
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();
    // Load company-specific design parameters from database
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (!(getProcessEquipment() instanceof DistillationColumn)) {
      return;
    }
    DistillationColumn column = (DistillationColumn) getProcessEquipment();

    // Get number of theoretical trays from the trays list
    numberOfTrays = column.getTrays().size();
    if (numberOfTrays <= 0) {
      return;
    }

    // Calculate actual trays from efficiency
    actualTrays = (int) Math.ceil(numberOfTrays / trayEfficiency);

    // Get vapor and liquid flow rates from top tray
    double vaporMolarFlow = 0.0;
    double liquidMolarFlow = 0.0;
    double vaporDensity = 1.0;
    double liquidDensity = 800.0;
    double vaporMW = 20.0;
    double liquidMW = 100.0;

    try {
      // Get top tray conditions for sizing
      if (column.getTray(0) != null && column.getTray(0).getGasOutStream() != null) {
        vaporMolarFlow = column.getTray(0).getGasOutStream().getFlowRate("mol/hr");
        if (column.getTray(0).getGasOutStream().getFluid() != null) {
          vaporDensity = column.getTray(0).getGasOutStream().getFluid().getDensity("kg/m3");
          vaporMW = column.getTray(0).getGasOutStream().getFluid().getMolarMass() * 1000;
        }
      }
      if (column.getTray(0) != null && column.getTray(0).getLiquidOutStream() != null) {
        liquidMolarFlow = column.getTray(0).getLiquidOutStream().getFlowRate("mol/hr");
        if (column.getTray(0).getLiquidOutStream().getFluid() != null) {
          liquidDensity = column.getTray(0).getLiquidOutStream().getFluid().getDensity("kg/m3");
          liquidMW = column.getTray(0).getLiquidOutStream().getFluid().getMolarMass() * 1000;
        }
      }
    } catch (Exception ex) {
      // Use defaults if trays not accessible
    }

    try {
      if (vaporMolarFlow <= 0.0 && column.getGasOutStream() != null && column.getGasOutStream().getFluid() != null) {
        vaporMolarFlow = column.getGasOutStream().getFlowRate("mol/hr");
        vaporDensity = column.getGasOutStream().getFluid().getDensity("kg/m3");
        vaporMW = column.getGasOutStream().getFluid().getMolarMass() * 1000;
      }
      if (liquidMolarFlow <= 0.0 && column.getLiquidOutStream() != null
          && column.getLiquidOutStream().getFluid() != null) {
        liquidMolarFlow = column.getLiquidOutStream().getFlowRate("mol/hr");
        liquidDensity = column.getLiquidOutStream().getFluid().getDensity("kg/m3");
        liquidMW = column.getLiquidOutStream().getFluid().getMolarMass() * 1000;
      }
    } catch (Exception ex) {
      // Keep tray-based or default sizing inputs.
    }

    // Calculate mass flow rates
    double vaporMassFlow = vaporMolarFlow * vaporMW / 1000.0; // kg/hr
    double liquidMassFlow = liquidMolarFlow * liquidMW / 1000.0; // kg/hr

    // Calculate volumetric flow rates
    double vaporVolumeFlow = vaporMassFlow / vaporDensity; // m3/hr
    double liquidVolumeFlow = liquidMassFlow / liquidDensity; // m3/hr

    // Calculate column diameter using Souders-Brown correlation
    // U_flood = K * sqrt((rho_L - rho_V) / rho_V)
    // where K is typically 0.05-0.15 for sieve trays
    double kFactor = 0.1; // m/s
    if ("valve".equals(trayType)) {
      kFactor = 0.12;
    } else if ("bubble-cap".equals(trayType)) {
      kFactor = 0.08;
    }

    double uFlood = kFactor * Math.sqrt((liquidDensity - vaporDensity) / vaporDensity);
    if (Double.isNaN(uFlood) || Double.isInfinite(uFlood) || uFlood <= 0.0) {
      uFlood = kFactor;
    }
    double uDesign = uFlood * maxFloodingFactor;

    // Calculate required vapor area
    double vaporVolumeFlowM3s = vaporVolumeFlow / 3600.0;
    if (vaporVolumeFlowM3s <= 0.0 || Double.isNaN(vaporVolumeFlowM3s) || Double.isInfinite(vaporVolumeFlowM3s)
        || uDesign <= 0.0) {
      columnDiameter = roundToStandardDiameter(0.5);
    } else {
      double requiredVaporArea = vaporVolumeFlowM3s / uDesign;

      // Account for downcomer area
      double totalArea = requiredVaporArea / (1.0 - downcomberAreaFraction);
      columnDiameter = Math.sqrt(4.0 * totalArea / Math.PI);

      // Round up to standard vessel diameter
      columnDiameter = roundToStandardDiameter(columnDiameter);
    }

    // Recalculate actual flooding factor
    double actualArea = Math.PI * Math.pow(columnDiameter / 2.0, 2);
    double actualVaporArea = actualArea * (1.0 - downcomberAreaFraction);
    double actualVelocity = vaporVolumeFlowM3s / actualVaporArea;
    floodingFactor = actualVelocity / uFlood;

    // Calculate weir length (typically 0.6-0.8 of diameter)
    weirLength = columnDiameter * 0.7;

    // Calculate weir loading
    weirLoading = liquidVolumeFlow / weirLength;

    // Calculate column height
    // Height = (trays * spacing) + (top section) + (bottom section) + heads
    double traySection = actualTrays * traySpacing;
    double topSection = 1.0; // m - vapor disengagement
    double bottomSection = 2.0; // m - liquid holdup
    double heads = 0.5; // m - each head
    columnHeight = traySection + topSection + bottomSection + 2.0 * heads;

    // Calculate wall thickness using pressure vessel code
    columnWallThickness = calculateRequiredWallThickness(columnDiameter);

    // Estimate tray pressure drop
    // Simplified: dry tray + liquid head
    double dryTrayDp = 5.0; // mbar typical for sieve tray
    double liquidHeadDp = weirHeight * liquidDensity * 9.81 / 100; // mbar
    trayPressureDrop = dryTrayDp + liquidHeadDp;
    totalPressureDrop = trayPressureDrop * actualTrays / 1000; // bar

    // Get duties from column if available
    try {
      if (column.getReboiler() != null) {
        reboilerDuty = column.getReboiler().getDuty() / 1000; // kW
      }
      if (column.getCondenser() != null) {
        condenserDuty = Math.abs(column.getCondenser().getDuty() / 1000); // kW
      }
    } catch (Exception ex) {
      // Duties not available
    }

    // Update inherited fields
    innerDiameter = columnDiameter;
    outerDiameter = columnDiameter + 2.0 * columnWallThickness / 1000.0;
    setWallThickness(columnWallThickness);

    calculateContactorCapacity(column);
    columnWallThickness = calculateRequiredWallThickness(columnDiameter);
    outerDiameter = columnDiameter + 2.0 * columnWallThickness / 1000.0;
    setWallThickness(columnWallThickness);
    column.applyMechanicalDesignCapacityConstraints();
  }

  /**
   * Calculate required cylindrical shell thickness using the existing ASME UG-27 design basis.
   *
   * @param diameter column internal diameter [m]
   * @return required wall thickness [mm], including corrosion allowance
   */
  private double calculateRequiredWallThickness(double diameter) {
    double designPressure = getMaxOperationPressure() * 1.1;
    double allowableStress = getTensileStrength() * 0.4;
    double denominator = allowableStress * getJointEfficiency() - 0.6 * designPressure;
    double calculatedThickness = denominator > 0.0 ? designPressure * diameter * 500.0 / denominator : 0.0;
    calculatedThickness += getCorrosionAllowance();
    return Math.max(calculatedThickness, 6.0);
  }

  /**
   * Calculate the integrated tray or packing, demister, and pressure-drop capacity result.
   *
   * @param column converged column to evaluate
   * @return integrated contactor capacity result
   */
  private ContactorCapacityResult calculateContactorCapacity(DistillationColumn column) {
    boolean equipmentDefinesPacking = column instanceof PackedColumn && "auto".equalsIgnoreCase(contactorInternalsType);
    String activeInternalsType = resolveInternalsType(column);
    String activePackingPreset = packingPreset;
    boolean activeStructuredPacking = structuredPacking;
    double activePackedHeight = packedHeight;
    double activeDesignFloodFraction = contactorDesignFloodFraction;
    double activePackingCapacityFactor = packingHydraulicCapacityFactor;

    if (equipmentDefinesPacking) {
      PackedColumn packedColumn = (PackedColumn) column;
      activePackingPreset = packedColumn.getPackingType();
      activeStructuredPacking = packedColumn.isStructuredPacking();
      activePackedHeight = packedColumn.getPackedHeight();
      activeDesignFloodFraction = packedColumn.getDesignFloodFraction();
      activePackingCapacityFactor = packedColumn.getPackingHydraulicCapacityFactor();
    }

    contactorInternalsDesigner = new ColumnInternalsDesigner(column);
    contactorInternalsDesigner.setInternalsType(activeInternalsType);
    contactorInternalsDesigner.setTraySpacing(traySpacing);
    contactorInternalsDesigner.setWeirHeight(weirHeight);
    contactorInternalsDesigner.setHoleDiameter(holeDiameter);
    contactorInternalsDesigner.setHoleAreaFraction(holeAreaFraction);
    contactorInternalsDesigner.setDowncommerAreaFraction(downcomberAreaFraction);
    contactorInternalsDesigner.setDesignFloodFraction(activeDesignFloodFraction);
    contactorInternalsDesigner.setPackingPreset(activePackingPreset);
    contactorInternalsDesigner.setStructuredPacking(activeStructuredPacking);
    contactorInternalsDesigner.setPackedHeight(activePackedHeight);
    contactorInternalsDesigner.setPackingHydraulicCapacityFactor(activePackingCapacityFactor);

    double ratingDiameter = resolveRatingDiameter(column, activeInternalsType);
    if (ratingDiameter > 0.0) {
      contactorInternalsDesigner.setColumnDiameterOverride(ratingDiameter);
    }
    contactorInternalsDesigner.calculate();

    double calculatedDiameter = contactorInternalsDesigner.getRequiredDiameter();
    if (calculatedDiameter > 0.0) {
      columnDiameter = calculatedDiameter;
      innerDiameter = calculatedDiameter;
      outerDiameter = calculatedDiameter + 2.0 * columnWallThickness / 1000.0;
    }
    if ("packed".equalsIgnoreCase(activeInternalsType)) {
      columnHeight = activePackedHeight + 4.0;
    }

    PackingHydraulicsCalculator packing = contactorInternalsDesigner.getPackingResult();
    double percentFlood = contactorInternalsDesigner.getMaxPercentFlood();
    double floodingUtilization = activeDesignFloodFraction > 0.0 ? percentFlood / (100.0 * activeDesignFloodFraction)
        : 0.0;
    boolean wettingOk = packing == null || packing.isWettingOk();
    double fsFactor = calculateFsFactor(column, columnDiameter);
    if (fsFactor <= 0.0 && packing != null) {
      fsFactor = packing.getFsFactor();
    }
    double gasFlowKgPerHour = getGasFlowKgPerHour(column);
    if (fsFactor > 0.0 && floodingUtilization > 0.0) {
      column.setMaxAllowableFsFactor(fsFactor / floodingUtilization);
    }

    double demisterOperatingK = 0.0;
    double demisterMaximumK = 0.0;
    double demisterUtilization = 0.0;
    double demisterPressureDropPa = 0.0;
    String activeDemisterType = "";
    String activeDemisterSubType = "";
    outletDemister = null;
    outletDemisterOperatingWindow = null;
    if (outletDemisterEnabled && columnDiameter > 0.0) {
      outletDemister = DemistingInternal.fromDatabase(outletDemisterType, outletDemisterSubType);
      outletDemister.setName("column outlet demister");
      activeDemisterType = outletDemister.getType();
      activeDemisterSubType = outletDemister.getSubType();
      double area = Math.PI * columnDiameter * columnDiameter / 4.0 * outletDemisterAreaFraction;
      outletDemister.setArea(area);
      double gasDensity = getGasDensity(column);
      double liquidDensity = getLiquidDensity(column, gasDensity);
      double gasVelocity = getGasVolumeFlowM3PerSecond(column) / Math.max(area, 1.0e-12);
      if (gasDensity > 0.0 && liquidDensity > gasDensity) {
        demisterOperatingK = gasVelocity * Math.sqrt(gasDensity / (liquidDensity - gasDensity));
        demisterMaximumK = outletDemister.getMaxKFactor();
        outletDemisterOperatingWindow = outletDemister.getOperatingWindow(demisterOperatingK);
        demisterUtilization = outletDemisterOperatingWindow.getUtilization();
        demisterPressureDropPa = outletDemister.calcPressureDrop(gasVelocity, gasDensity);
      }
    }

    double internalsPressureDropPa = contactorInternalsDesigner.getTotalPressureDrop();
    totalPressureDrop = (internalsPressureDropPa + demisterPressureDropPa) / 1.0e5;
    double pressureDropUtilization = maxContactorPressureDropBar > 0.0 ? totalPressureDrop / maxContactorPressureDropBar
        : 0.0;

    boolean hydraulicsAvailable = columnDiameter > 0.0 && percentFlood > 0.0;
    double overallUtilization = floodingUtilization;
    String bottleneck = "column internals flooding";
    if (outletDemisterEnabled && demisterUtilization > overallUtilization) {
      overallUtilization = demisterUtilization;
      bottleneck = "outlet demister";
    }
    if (pressureDropUtilization > overallUtilization) {
      overallUtilization = pressureDropUtilization;
      bottleneck = "contactor pressure drop";
    }
    if (!wettingOk && overallUtilization < 1.0) {
      bottleneck = "packing minimum wetting";
    }
    if (!hydraulicsAvailable) {
      bottleneck = "hydraulic data unavailable";
    }

    double floodingHeadroom = floodingUtilization > 0.0 ? 1.0 / floodingUtilization : Double.POSITIVE_INFINITY;
    double demisterHeadroom = outletDemisterEnabled && demisterUtilization > 0.0 ? 1.0 / demisterUtilization
        : Double.POSITIVE_INFINITY;
    double pressureDropHeadroom = pressureDropUtilization > 0.0 ? Math.sqrt(1.0 / pressureDropUtilization)
        : Double.POSITIVE_INFINITY;
    double gasCapacityMultiplier = Math.min(floodingHeadroom, Math.min(demisterHeadroom, pressureDropHeadroom));
    if (!Double.isFinite(gasCapacityMultiplier)) {
      gasCapacityMultiplier = 0.0;
    }
    boolean designOk = hydraulicsAvailable && wettingOk && floodingUtilization <= 1.0
        && (!outletDemisterEnabled || demisterUtilization <= 1.0) && pressureDropUtilization <= 1.0;

    contactorCapacityResult = new ContactorCapacityResult(activeInternalsType,
        packing == null ? "" : packing.getPackingName(), activePackingCapacityFactor, columnDiameter, gasFlowKgPerHour,
        fsFactor, percentFlood, floodingUtilization, wettingOk, activeDemisterType, activeDemisterSubType,
        demisterOperatingK, demisterMaximumK, demisterUtilization, totalPressureDrop, pressureDropUtilization,
        overallUtilization, bottleneck, gasCapacityMultiplier, gasFlowKgPerHour * gasCapacityMultiplier, designOk);
    return contactorCapacityResult;
  }

  /**
   * Resolve the active internals type, using packed internals automatically for {@link PackedColumn} equipment.
   *
   * @param column column being evaluated
   * @return packed or tray internals type
   */
  private String resolveInternalsType(DistillationColumn column) {
    if (!"auto".equalsIgnoreCase(contactorInternalsType)) {
      return contactorInternalsType;
    }
    return column instanceof PackedColumn ? "packed" : trayType;
  }

  /**
   * Resolve the fixed diameter used for an equipment rating.
   *
   * @param column column being evaluated
   * @param activeInternalsType active internals type
   * @return fixed rating diameter [m], or -1 for automatic packed-column sizing
   */
  private double resolveRatingDiameter(DistillationColumn column, String activeInternalsType) {
    if (columnDiameterOverride > 0.0) {
      return columnDiameterOverride;
    }
    if (column instanceof PackedColumn && column.getInternalDiameter() > 0.0) {
      return column.getInternalDiameter();
    }
    return "packed".equalsIgnoreCase(activeInternalsType) ? -1.0 : columnDiameter;
  }

  /**
   * Calculate Fs from the live top gas flow and the supplied diameter.
   *
   * @param column column being evaluated
   * @param diameter internal diameter [m]
   * @return Fs [m/s sqrt(kg/m3)]
   */
  private double calculateFsFactor(DistillationColumn column, double diameter) {
    if (diameter <= 0.0) {
      return 0.0;
    }
    double area = Math.PI * diameter * diameter / 4.0;
    return getGasVolumeFlowM3PerSecond(column) / area * Math.sqrt(Math.max(getGasDensity(column), 0.0));
  }

  /**
   * Get current top gas mass flow.
   *
   * @param column column being evaluated
   * @return gas mass flow [kg/hr], or zero if unavailable
   */
  private double getGasFlowKgPerHour(DistillationColumn column) {
    try {
      return column.getGasOutStream() == null ? 0.0 : column.getGasOutStream().getFlowRate("kg/hr");
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Get current top gas volumetric flow.
   *
   * @param column column being evaluated
   * @return gas volumetric flow [m3/s], or zero if unavailable
   */
  private double getGasVolumeFlowM3PerSecond(DistillationColumn column) {
    try {
      return column.getGasOutStream() == null ? 0.0 : column.getGasOutStream().getFlowRate("m3/sec");
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Get current top gas density.
   *
   * @param column column being evaluated
   * @return gas density [kg/m3], or a conservative default if unavailable
   */
  private double getGasDensity(DistillationColumn column) {
    try {
      return column.getGasOutStream().getFluid().getDensity("kg/m3");
    } catch (Exception ex) {
      return 1.0;
    }
  }

  /**
   * Get current solvent or condensed-liquid density.
   *
   * @param column column being evaluated
   * @param gasDensity gas density [kg/m3]
   * @return liquid density [kg/m3], or a conservative default if unavailable
   */
  private double getLiquidDensity(DistillationColumn column, double gasDensity) {
    try {
      double density = column.getLiquidOutStream().getFluid().getDensity("kg/m3");
      return density > gasDensity ? density : Math.max(800.0, gasDensity + 1.0);
    } catch (Exception ex) {
      return Math.max(800.0, gasDensity + 1.0);
    }
  }

  /**
   * Rounds diameter to nearest standard vessel diameter.
   *
   * @param diameter diameter in meters
   * @return nearest standard vessel diameter in meters
   */
  private double roundToStandardDiameter(double diameter) {
    // Standard vessel diameters in meters
    double[] standardSizes = { 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.4, 1.5, 1.6, 1.8, 2.0, 2.2, 2.4, 2.6, 2.8, 3.0,
        3.2, 3.4, 3.6, 3.8, 4.0, 4.5, 5.0, 5.5, 6.0, 7.0, 8.0, 9.0, 10.0 };

    for (double stdSize : standardSizes) {
      if (stdSize >= diameter) {
        return stdSize;
      }
    }
    return Math.ceil(diameter);
  }

  // Getters and setters

  /**
   * Gets the column diameter.
   *
   * @return column diameter in meters
   */
  public double getColumnDiameter() {
    return columnDiameter;
  }

  /**
   * Sets the column diameter.
   *
   * @param diameter column diameter in meters
   */
  public void setColumnDiameter(double diameter) {
    this.columnDiameter = diameter;
  }

  /**
   * Gets the column height.
   *
   * @return column height in meters
   */
  public double getColumnHeight() {
    return columnHeight;
  }

  /**
   * Sets the column height.
   *
   * @param height column height in meters
   */
  public void setColumnHeight(double height) {
    this.columnHeight = height;
  }

  /**
   * Gets the column wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getColumnWallThickness() {
    return columnWallThickness;
  }

  /**
   * Gets the number of theoretical trays.
   *
   * @return number of theoretical trays
   */
  public int getNumberOfTrays() {
    return numberOfTrays;
  }

  /**
   * Gets the actual number of trays.
   *
   * @return actual number of trays
   */
  public int getActualTrays() {
    return actualTrays;
  }

  /**
   * Gets the tray efficiency.
   *
   * @return tray efficiency (0-1)
   */
  public double getTrayEfficiency() {
    return trayEfficiency;
  }

  /**
   * Sets the tray efficiency.
   *
   * @param efficiency tray efficiency (0-1)
   */
  public void setTrayEfficiency(double efficiency) {
    this.trayEfficiency = efficiency;
  }

  /**
   * Gets the tray spacing.
   *
   * @return tray spacing in meters
   */
  public double getTraySpacing() {
    return traySpacing;
  }

  /**
   * Sets the tray spacing.
   *
   * @param spacing tray spacing in meters
   */
  public void setTraySpacing(double spacing) {
    this.traySpacing = spacing;
  }

  /**
   * Gets the flooding factor.
   *
   * @return flooding factor (0-1)
   */
  public double getFloodingFactor() {
    return floodingFactor;
  }

  /**
   * Gets the maximum flooding factor.
   *
   * @return maximum flooding factor
   */
  public double getMaxFloodingFactor() {
    return maxFloodingFactor;
  }

  /**
   * Sets the maximum flooding factor.
   *
   * @param factor maximum flooding factor (typically 0.80-0.85)
   */
  public void setMaxFloodingFactor(double factor) {
    this.maxFloodingFactor = factor;
  }

  /**
   * Gets the weir loading.
   *
   * @return weir loading in m3/hr per m
   */
  public double getWeirLoading() {
    return weirLoading;
  }

  /**
   * Gets the tray pressure drop.
   *
   * @return tray pressure drop in mbar/tray
   */
  public double getTrayPressureDrop() {
    return trayPressureDrop;
  }

  /**
   * Gets the total column pressure drop.
   *
   * @return total pressure drop in bar
   */
  public double getTotalPressureDrop() {
    return totalPressureDrop;
  }

  /**
   * Gets the reboiler duty.
   *
   * @return reboiler duty in kW
   */
  public double getReboilerDuty() {
    return reboilerDuty;
  }

  /**
   * Gets the condenser duty.
   *
   * @return condenser duty in kW
   */
  public double getCondenserDuty() {
    return condenserDuty;
  }

  /**
   * Gets the tray type.
   *
   * @return tray type (sieve, valve, bubble-cap)
   */
  public String getTrayType() {
    return trayType;
  }

  /**
   * Sets the tray type.
   *
   * @param type tray type (sieve, valve, bubble-cap)
   */
  public void setTrayType(String type) {
    this.trayType = type;
  }

  /**
   * Set the contactor internals type used by the integrated hydraulic rating.
   *
   * @param type auto, packed, sieve, valve, or bubble-cap
   * @throws IllegalArgumentException if the type is unsupported
   */
  public void setContactorInternalsType(String type) {
    if (type == null || !("auto".equalsIgnoreCase(type) || "packed".equalsIgnoreCase(type)
        || "sieve".equalsIgnoreCase(type) || "valve".equalsIgnoreCase(type) || "bubble-cap".equalsIgnoreCase(type))) {
      throw new IllegalArgumentException("contactor internals type must be auto, packed, sieve, valve, or bubble-cap");
    }
    this.contactorInternalsType = type;
  }

  /**
   * Get the configured contactor internals type.
   *
   * @return auto, packed, or a tray type
   */
  public String getContactorInternalsType() {
    return contactorInternalsType;
  }

  /**
   * Configure packed contactor internals.
   *
   * @param preset registered packing preset
   * @param structured true for structured packing
   * @param height packed bed height [m]
   * @param designFloodFraction design fraction of packing flood
   * @param hydraulicCapacityFactor relative vendor-supported hydraulic capacity factor
   * @throws IllegalArgumentException if a numeric input is outside its valid range
   */
  public void configurePackedInternals(String preset, boolean structured, double height, double designFloodFraction,
      double hydraulicCapacityFactor) {
    if (preset == null || preset.trim().isEmpty()) {
      throw new IllegalArgumentException("packing preset must be specified");
    }
    if (!Double.isFinite(height) || height <= 0.0) {
      throw new IllegalArgumentException("packed height must be positive and finite");
    }
    if (!Double.isFinite(designFloodFraction) || designFloodFraction <= 0.0 || designFloodFraction >= 1.0) {
      throw new IllegalArgumentException("design flood fraction must be between zero and one");
    }
    if (!Double.isFinite(hydraulicCapacityFactor) || hydraulicCapacityFactor <= 0.0) {
      throw new IllegalArgumentException("packing hydraulic capacity factor must be positive and finite");
    }
    contactorInternalsType = "packed";
    packingPreset = preset;
    structuredPacking = structured;
    packedHeight = height;
    contactorDesignFloodFraction = designFloodFraction;
    packingHydraulicCapacityFactor = hydraulicCapacityFactor;
  }

  /**
   * Set a fixed column diameter for rating and retrofit calculations.
   *
   * @param diameter internal diameter [m], or a non-positive value to return to automatic sizing
   * @throws IllegalArgumentException if the value is not finite
   */
  public void setColumnDiameterOverride(double diameter) {
    if (!Double.isFinite(diameter)) {
      throw new IllegalArgumentException("column diameter override must be finite");
    }
    columnDiameterOverride = diameter > 0.0 ? diameter : -1.0;
  }

  /**
   * Get the fixed rating diameter override.
   *
   * @return internal diameter [m], or -1 when automatic sizing is active
   */
  public double getColumnDiameterOverride() {
    return columnDiameterOverride;
  }

  /**
   * Configure an outlet mist eliminator using the separator-internals design database.
   *
   * @param demisterType wire_mesh, vane_pack, or cyclone
   * @param subType database subtype such as Standard Knitted or Low Pressure Drop
   */
  public void configureOutletDemister(String demisterType, String subType) {
    if (demisterType == null || demisterType.trim().isEmpty()) {
      throw new IllegalArgumentException("demister type must be specified");
    }
    outletDemisterEnabled = true;
    outletDemisterType = demisterType;
    outletDemisterSubType = subType == null ? "" : subType;
  }

  /**
   * Disable the outlet demister in the integrated capacity analysis.
   */
  public void disableOutletDemister() {
    outletDemisterEnabled = false;
  }

  /**
   * Set the fraction of column cross-sectional area available for gas flow through the outlet demister.
   *
   * @param fraction open area fraction in the interval zero to one
   * @throws IllegalArgumentException if the fraction is invalid
   */
  public void setOutletDemisterAreaFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("outlet demister area fraction must be above zero and no greater than one");
    }
    outletDemisterAreaFraction = fraction;
  }

  /**
   * Set the maximum permitted total contactor pressure drop.
   *
   * @param pressureDropBar maximum pressure drop [bar]
   * @throws IllegalArgumentException if the limit is not positive and finite
   */
  public void setMaxContactorPressureDropBar(double pressureDropBar) {
    if (!Double.isFinite(pressureDropBar) || pressureDropBar <= 0.0) {
      throw new IllegalArgumentException("maximum contactor pressure drop must be positive and finite");
    }
    maxContactorPressureDropBar = pressureDropBar;
  }

  /**
   * Get the maximum permitted total contactor pressure drop.
   *
   * @return maximum pressure drop [bar]
   */
  public double getMaxContactorPressureDropBar() {
    return maxContactorPressureDropBar;
  }

  /**
   * Get the most recent integrated contactor capacity result.
   *
   * @return capacity result, or null before {@link #calcDesign()}
   */
  public ContactorCapacityResult getContactorCapacityResult() {
    return contactorCapacityResult;
  }

  /**
   * Get the detailed tray or packing calculation from the most recent design.
   *
   * @return column internals designer, or null before calculation
   */
  public ColumnInternalsDesigner getContactorInternalsDesigner() {
    return contactorInternalsDesigner;
  }

  /**
   * Get the most recent outlet demister operating window.
   *
   * @return demister operating window, or null when the demister is disabled or not calculated
   */
  public InternalOperatingWindow getOutletDemisterOperatingWindow() {
    return outletDemisterOperatingWindow;
  }

  /**
   * Compare the current baseline with candidate packed internals at the same vessel diameter and process conditions.
   *
   * <p>
   * The candidate packing factor is explicit so manufacturer claims can be screened without embedding a universal
   * capacity uplift in the packing name. The returned uplift is controlled by the minimum headroom of packing flood,
   * demister K-factor, and total pressure drop.
   * </p>
   *
   * @param candidatePackingPreset registered candidate packing preset
   * @param candidateStructured true for structured candidate packing
   * @param candidatePackingCapacityFactor relative candidate hydraulic capacity factor
   * @param candidateDemisterType candidate demister type
   * @param candidateDemisterSubType candidate demister database subtype
   * @return baseline-versus-candidate capacity comparison
   */
  public ContactorCapacityComparison comparePackedInternals(String candidatePackingPreset, boolean candidateStructured,
      double candidatePackingCapacityFactor, String candidateDemisterType, String candidateDemisterSubType) {
    DistillationColumn column = getColumnForEconomicOptimization();
    if (contactorCapacityResult == null) {
      calcDesign();
    }
    ContactorCapacityResult baseline = contactorCapacityResult;

    String savedInternalsType = contactorInternalsType;
    String savedPackingPreset = packingPreset;
    boolean savedStructuredPacking = structuredPacking;
    double savedCapacityFactor = packingHydraulicCapacityFactor;
    double savedDiameterOverride = columnDiameterOverride;
    boolean savedDemisterEnabled = outletDemisterEnabled;
    String savedDemisterType = outletDemisterType;
    String savedDemisterSubType = outletDemisterSubType;

    ContactorCapacityResult candidate;
    try {
      contactorInternalsType = "packed";
      packingPreset = candidatePackingPreset;
      structuredPacking = candidateStructured;
      packingHydraulicCapacityFactor = candidatePackingCapacityFactor;
      columnDiameterOverride = baseline.getColumnDiameter();
      configureOutletDemister(candidateDemisterType, candidateDemisterSubType);
      candidate = calculateContactorCapacity(column);
    } finally {
      contactorInternalsType = savedInternalsType;
      packingPreset = savedPackingPreset;
      structuredPacking = savedStructuredPacking;
      packingHydraulicCapacityFactor = savedCapacityFactor;
      columnDiameterOverride = savedDiameterOverride;
      outletDemisterEnabled = savedDemisterEnabled;
      outletDemisterType = savedDemisterType;
      outletDemisterSubType = savedDemisterSubType;
      calculateContactorCapacity(column);
    }

    return new ContactorCapacityComparison(baseline, candidate);
  }

  /** {@inheritDoc} */
  @Override
  public List<CapacityConstraint> getDesignCapacityConstraints() {
    List<CapacityConstraint> constraints = super.getDesignCapacityConstraints();
    if (contactorCapacityResult == null) {
      return constraints;
    }
    constraints.add(createContactorConstraint("column internals flooding",
        contactorCapacityResult.getFloodingUtilization(), new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return contactorCapacityResult == null ? 0.0 : contactorCapacityResult.getFloodingUtilization();
          }
        }));
    if (outletDemisterEnabled) {
      constraints.add(createContactorConstraint("outlet demister", contactorCapacityResult.getDemisterUtilization(),
          new DoubleSupplier() {
            @Override
            public double getAsDouble() {
              return contactorCapacityResult == null ? 0.0 : contactorCapacityResult.getDemisterUtilization();
            }
          }));
    }
    constraints.add(createContactorConstraint("contactor pressure drop",
        contactorCapacityResult.getPressureDropUtilization(), new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return contactorCapacityResult == null ? 0.0 : contactorCapacityResult.getPressureDropUtilization();
          }
        }));
    return constraints;
  }

  /**
   * Create a normalized mechanical-design capacity constraint.
   *
   * @param name constraint name
   * @param utilization current utilization fraction
   * @param supplier live result supplier
   * @return enabled hard capacity constraint with a design value of one
   */
  private CapacityConstraint createContactorConstraint(String name, double utilization, DoubleSupplier supplier) {
    return new CapacityConstraint(name, "fraction", CapacityConstraint.ConstraintType.HARD).setDesignValue(1.0)
        .setMaxValue(1.0).setWarningThreshold(0.9).setSeverity(CapacityConstraint.ConstraintSeverity.HARD)
        .setDescription("Integrated column-internals mechanical-design limit").setDataSource("mechanicalDesign")
        .setValueSupplier(supplier).setCurrentValue(utilization).setEnabled(true);
  }

  /**
   * Gets the material grade.
   *
   * @return material grade
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Sets the material grade.
   *
   * @param grade material grade
   */
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
  }

  /**
   * Gets the design standard code.
   *
   * @return design standard code
   */
  public String getDesignStandardCode() {
    return designStandardCode;
  }

  /**
   * Sets the design standard code.
   *
   * @param code design standard code
   */
  public void setDesignStandardCode(String code) {
    this.designStandardCode = code;
  }

  // ============================================================================
  // Cost Estimation Methods
  // ============================================================================

  /**
   * Calculate equipment weight for cost estimation.
   *
   * <p>
   * Calculates column shell weight, head weight, tray weight, and total weight based on dimensions and wall thickness.
   * </p>
   */
  public void calculateWeights() {
    if (columnDiameter <= 0 || columnHeight <= 0) {
      return;
    }

    // Steel density (kg/m3)
    double steelDensity = 7850.0;

    // Column shell weight
    double outerDiam = columnDiameter + 2.0 * columnWallThickness / 1000.0;
    double shellCrossSectionArea = Math.PI / 4.0 * (Math.pow(outerDiam, 2) - Math.pow(columnDiameter, 2));
    double shellWeight = shellCrossSectionArea * columnHeight * steelDensity;

    // Head weight (2:1 ellipsoidal, approximate as 15% of shell weight)
    double headsWeight = shellWeight * 0.15 * 2;

    // Tray weight (approximate: 50-100 kg/m2 of tray area)
    double trayArea = Math.PI / 4.0 * Math.pow(columnDiameter, 2);
    double trayWeight = trayArea * 75.0 * actualTrays; // 75 kg/m2 average

    // Set weights
    weigthVesselShell = shellWeight + headsWeight;
    weigthInternals = trayWeight;
    setWeightTotal(weigthVesselShell + trayWeight);
  }

  /**
   * Calculate cost for distillation column.
   *
   * @return estimated cost in USD
   */
  public double calculateColumnCost() {
    if (columnDiameter <= 0 || columnHeight <= 0) {
      return 0.0;
    }

    // Calculate weights first
    calculateWeights();

    neqsim.process.costestimation.CostEstimationCalculator calc = getCostEstimate().getCostCalculator();

    // Column shell cost
    double shellCost = calc.calcVerticalVesselCost(weigthVesselShell);

    // Tray cost based on type
    double trayCost;
    if ("valve".equalsIgnoreCase(trayType)) {
      trayCost = calc.calcValveTraysCost(columnDiameter, actualTrays);
    } else if ("bubble-cap".equalsIgnoreCase(trayType)) {
      trayCost = calc.calcBubbleCapTraysCost(columnDiameter, actualTrays);
    } else {
      trayCost = calc.calcSieveTraysCost(columnDiameter, actualTrays);
    }

    // Apply material factor for shell
    double materialFactor = calc.getMaterialFactor();
    shellCost *= materialFactor;

    // Tray material factor (SS316L typical for trays)
    double trayMaterialFactor = 2.3; // SS316L
    trayCost *= trayMaterialFactor;

    return shellCost + trayCost;
  }

  /**
   * Calculate reboiler cost estimate.
   *
   * @return reboiler cost in USD
   */
  public double calculateReboilerCost() {
    if (reboilerDuty <= 0) {
      return 0.0;
    }
    // Estimate area from duty: Q = U * A * LMTD
    // Assume U = 1000 W/m2K, LMTD = 30 K
    double estimatedArea = reboilerDuty * 1000 / (1000 * 30);
    return getCostEstimate().getCostCalculator().calcShellTubeHeatExchangerCost(estimatedArea);
  }

  /**
   * Calculate condenser cost estimate.
   *
   * @return condenser cost in USD
   */
  public double calculateCondenserCost() {
    if (condenserDuty <= 0) {
      return 0.0;
    }
    // Estimate area from duty: Q = U * A * LMTD
    // Assume U = 800 W/m2K, LMTD = 20 K (air cooler or water cooler)
    double estimatedArea = condenserDuty * 1000 / (800 * 20);
    return getCostEstimate().getCostCalculator().calcAirCoolerCost(estimatedArea);
  }

  /**
   * Calculate total column system cost including reboiler and condenser.
   *
   * @return total system cost in USD
   */
  public double calculateTotalSystemCost() {
    return calculateColumnCost() + calculateReboilerCost() + calculateCondenserCost();
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    MechanicalDesignResponse baseResponse = new MechanicalDesignResponse(this);
    JsonObject jsonObj = JsonParser.parseString(baseResponse.toJson()).getAsJsonObject();

    // Add column vessel design
    JsonObject vessel = new JsonObject();
    vessel.addProperty("columnDiameter_m", columnDiameter);
    vessel.addProperty("columnHeight_m", columnHeight);
    vessel.addProperty("columnWallThickness_mm", columnWallThickness);
    vessel.addProperty("materialGrade", materialGrade);
    vessel.addProperty("designStandardCode", designStandardCode);
    jsonObj.add("vesselDesign", vessel);

    // Add tray design
    JsonObject trays = new JsonObject();
    trays.addProperty("numberOfTheoreticalTrays", numberOfTrays);
    trays.addProperty("actualTrays", actualTrays);
    trays.addProperty("trayEfficiency", trayEfficiency);
    trays.addProperty("trayType", trayType);
    trays.addProperty("trayMaterial", trayMaterial);
    trays.addProperty("traySpacing_m", traySpacing);
    trays.addProperty("weirHeight_m", weirHeight);
    trays.addProperty("weirLength_m", weirLength);
    trays.addProperty("downcomberAreaFraction", downcomberAreaFraction);
    trays.addProperty("activeAreaFraction", activeAreaFraction);
    jsonObj.add("trayDesign", trays);

    // Add hydraulics
    JsonObject hydraulics = new JsonObject();
    hydraulics.addProperty("floodingFactor", floodingFactor);
    hydraulics.addProperty("maxFloodingFactor", maxFloodingFactor);
    hydraulics.addProperty("weirLoading_m3hr_m", weirLoading);
    hydraulics.addProperty("maxWeirLoading_m3hr_m", maxWeirLoading);
    hydraulics.addProperty("trayPressureDrop_mbar", trayPressureDrop);
    hydraulics.addProperty("totalPressureDrop_bar", totalPressureDrop);
    jsonObj.add("hydraulics", hydraulics);

    if (contactorCapacityResult != null) {
      JsonObject contactorCapacity = JsonParser.parseString(contactorCapacityResult.toJson()).getAsJsonObject();
      jsonObj.add("contactorCapacity", contactorCapacity);
    }

    // Add duties
    JsonObject duties = new JsonObject();
    duties.addProperty("reboilerDuty_kW", reboilerDuty);
    duties.addProperty("condenserDuty_kW", condenserDuty);
    jsonObj.add("duties", duties);

    // Add cost estimation
    calculateWeights();
    JsonObject costData = new JsonObject();
    costData.addProperty("shellWeight_kg", weigthVesselShell);
    costData.addProperty("trayWeight_kg", weigthInternals);
    costData.addProperty("totalWeight_kg", getWeightTotal());
    costData.addProperty("columnCost_USD", calculateColumnCost());
    costData.addProperty("reboilerCost_USD", calculateReboilerCost());
    costData.addProperty("condenserCost_USD", calculateCondenserCost());
    costData.addProperty("totalSystemCost_USD", calculateTotalSystemCost());
    jsonObj.add("costEstimation", costData);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(jsonObj);
  }
}
