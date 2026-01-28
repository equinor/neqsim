package neqsim.process.mechanicaldesign.distillation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for distillation columns.
 * 
 * <p>
 * Handles design calculations for tray-based and packed distillation columns including:
 * </p>
 * <ul>
 * <li>Column vessel sizing (diameter, height, wall thickness)</li>
 * <li>Tray hydraulics (weir loading, flooding, pressure drop)</li>
 * <li>Internals design (tray spacing, downcomer area)</li>
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

  /**
   * Constructor for DistillationColumnMechanicalDesign.
   *
   * @param equipment the process equipment for this design
   */
  public DistillationColumnMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
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
    double uDesign = uFlood * maxFloodingFactor;

    // Calculate required vapor area
    double vaporVolumeFlowM3s = vaporVolumeFlow / 3600.0;
    double requiredVaporArea = vaporVolumeFlowM3s / uDesign;

    // Account for downcomer area
    double totalArea = requiredVaporArea / (1.0 - downcomberAreaFraction);
    columnDiameter = Math.sqrt(4.0 * totalArea / Math.PI);

    // Round up to standard vessel diameter
    columnDiameter = roundToStandardDiameter(columnDiameter);

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
    double designPressure = getMaxOperationPressure() * 1.1;
    double S = getTensileStrength() * 0.4; // Allowable stress
    double E = getJointEfficiency();

    // UG-27 for cylindrical shells: t = PR / (SE - 0.6P)
    double tShell = (designPressure * columnDiameter * 500) / (S * E - 0.6 * designPressure);
    tShell += getCorrosionAllowance();
    columnWallThickness = Math.max(tShell, 6.0); // Minimum 6mm

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
  }

  /**
   * Rounds diameter to nearest standard vessel diameter.
   *
   * @param diameter diameter in meters
   * @return nearest standard vessel diameter in meters
   */
  private double roundToStandardDiameter(double diameter) {
    // Standard vessel diameters in meters
    double[] standardSizes = {0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.4, 1.5, 1.6, 1.8, 2.0, 2.2,
        2.4, 2.6, 2.8, 3.0, 3.2, 3.4, 3.6, 3.8, 4.0, 4.5, 5.0, 5.5, 6.0, 7.0, 8.0, 9.0, 10.0};

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
   * Calculates column shell weight, head weight, tray weight, and total weight based on dimensions
   * and wall thickness.
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
    double shellCrossSectionArea =
        Math.PI / 4.0 * (Math.pow(outerDiam, 2) - Math.pow(columnDiameter, 2));
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

    neqsim.process.costestimation.CostEstimationCalculator calc =
        getCostEstimate().getCostCalculator();

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

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(jsonObj);
  }
}

