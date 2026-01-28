package neqsim.process.mechanicaldesign.splitter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for splitter equipment.
 * 
 * <p>
 * Handles design calculations for flow splitters, distribution headers, and manifolds including
 * header sizing, branch connections, and pressure drop calculations.
 * </p>
 *
 * @author AGAS
 * @version $Id: $Id
 */
public class SplitterMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Header internal diameter [m]. */
  private double headerDiameter = 0.0;

  /** Header wall thickness [mm]. */
  private double headerWallThickness = 0.0;

  /** Header length [m]. */
  private double headerLength = 0.0;

  /** Branch connection diameter [m]. */
  private double branchDiameter = 0.0;

  /** Distribution manifold volume [m3]. */
  private double manifoldVolume = 0.0;

  /** Design velocity in header [m/s]. */
  private double designVelocity = 15.0;

  /** Maximum allowable velocity [m/s]. */
  private double maxAllowableVelocity = 30.0;

  /** Total pressure drop [bar]. */
  private double totalPressureDrop = 0.0;

  /** Splitter type (tee, wye, header, manifold). */
  private String splitterType = "header";

  /** Number of outlet branches. */
  private int numberOfBranches = 0;

  /** Material grade for construction. */
  private String materialGrade = "A106-B";

  /** Design standard code. */
  private String designStandardCode = "ASME-B31.3";

  /** Split ratios for each branch. */
  private double[] splitRatios;

  /**
   * Constructor for SplitterMechanicalDesign.
   *
   * @param equipment the process equipment for this design
   */
  public SplitterMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();
    // Load company-specific design parameters from database
    // e.g., velocity limits, pressure drop limits, material specs
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (!(getProcessEquipment() instanceof Splitter)) {
      return;
    }
    Splitter splitter = (Splitter) getProcessEquipment();

    // Get number of outlet streams
    numberOfBranches = splitter.getSplitNumber();
    if (numberOfBranches == 0) {
      return;
    }

    // Get inlet flow
    StreamInterface inletStream = splitter.getInletStream();
    if (inletStream == null || inletStream.getFluid() == null) {
      return;
    }

    double totalVolumeFlow = inletStream.getFlowRate("m3/hr");
    if (totalVolumeFlow <= 0) {
      return;
    }

    // Store split ratios
    splitRatios = splitter.getSplitFactors();

    // Find maximum branch flow for sizing
    double maxBranchVolumeFlow = 0.0;
    for (double ratio : splitRatios) {
      double branchFlow = totalVolumeFlow * ratio;
      maxBranchVolumeFlow = Math.max(maxBranchVolumeFlow, branchFlow);
    }

    // Size header based on inlet flow and design velocity
    // Q = v * A => A = Q / v => D = sqrt(4 * A / pi)
    double volumeFlowM3s = totalVolumeFlow / 3600.0; // m3/hr to m3/s
    double headerArea = volumeFlowM3s / designVelocity;
    headerDiameter = Math.sqrt(4.0 * headerArea / Math.PI);

    // Round up to nearest standard pipe size
    headerDiameter = roundToStandardPipeSize(headerDiameter);

    // Size branch connections based on max branch flow
    double branchVolumeFlowM3s = maxBranchVolumeFlow / 3600.0;
    double branchArea = branchVolumeFlowM3s / designVelocity;
    branchDiameter = Math.sqrt(4.0 * branchArea / Math.PI);
    branchDiameter = roundToStandardPipeSize(branchDiameter);

    // Calculate header length based on number of branches and spacing
    double branchSpacing = Math.max(2.0 * branchDiameter, 0.5); // min 2D spacing or 0.5m
    headerLength = (numberOfBranches + 1) * branchSpacing;

    // Calculate wall thickness based on pressure vessel code
    double designPressure = getMaxOperationPressure() * 1.1; // 10% margin
    double designTemperature = getMaxOperationTemperature();

    // ASME B31.3 wall thickness for pipe: t = PD / (2SE + 2YP)
    double S = getTensileStrength() * 0.4; // Allowable stress (40% of tensile)
    double E = getJointEfficiency();
    double Y = 0.4; // Temperature coefficient

    double tMin = (designPressure * headerDiameter * 1000) / (2.0 * S + 2.0 * Y * designPressure);
    tMin += getCorrosionAllowance();
    headerWallThickness = Math.max(tMin, 3.0); // Minimum 3mm

    // Calculate manifold volume
    manifoldVolume = Math.PI * Math.pow(headerDiameter / 2.0, 2) * headerLength;

    // Estimate total pressure drop (simplified)
    // Using K-factor method for splitting losses
    double kSplitting = 0.5; // Typical splitting loss coefficient
    double density = inletStream.getFluid().getDensity("kg/m3");
    double velocity = volumeFlowM3s / (Math.PI * Math.pow(headerDiameter / 2.0, 2));
    totalPressureDrop = kSplitting * 0.5 * density * velocity * velocity / 1e5; // bar

    // Update outer diameter
    outerDiameter = headerDiameter + 2.0 * headerWallThickness / 1000.0;
    innerDiameter = headerDiameter;
    setWallThickness(headerWallThickness);
  }

  /**
   * Rounds diameter to nearest standard pipe size.
   *
   * @param diameter diameter in meters
   * @return nearest standard pipe diameter in meters
   */
  private double roundToStandardPipeSize(double diameter) {
    // Standard pipe sizes in meters (NPS in inches converted)
    double[] standardSizes = {0.0127, // 0.5"
        0.0254, // 1"
        0.0381, // 1.5"
        0.0508, // 2"
        0.0635, // 2.5"
        0.0762, // 3"
        0.1016, // 4"
        0.1270, // 5"
        0.1524, // 6"
        0.2032, // 8"
        0.2540, // 10"
        0.3048, // 12"
        0.3556, // 14"
        0.4064, // 16"
        0.4572, // 18"
        0.5080, // 20"
        0.6096, // 24"
        0.7620, // 30"
        0.9144, // 36"
        1.0668, // 42"
        1.2192 // 48"
    };

    for (double stdSize : standardSizes) {
      if (stdSize >= diameter) {
        return stdSize;
      }
    }
    return standardSizes[standardSizes.length - 1];
  }

  /**
   * Gets the header diameter.
   *
   * @return header diameter in meters
   */
  public double getHeaderDiameter() {
    return headerDiameter;
  }

  /**
   * Sets the header diameter.
   *
   * @param diameter header diameter in meters
   */
  public void setHeaderDiameter(double diameter) {
    this.headerDiameter = diameter;
  }

  /**
   * Gets the header wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getHeaderWallThickness() {
    return headerWallThickness;
  }

  /**
   * Gets the header length.
   *
   * @return header length in meters
   */
  public double getHeaderLength() {
    return headerLength;
  }

  /**
   * Sets the header length.
   *
   * @param length header length in meters
   */
  public void setHeaderLength(double length) {
    this.headerLength = length;
  }

  /**
   * Gets the branch connection diameter.
   *
   * @return branch diameter in meters
   */
  public double getBranchDiameter() {
    return branchDiameter;
  }

  /**
   * Sets the branch connection diameter.
   *
   * @param diameter branch diameter in meters
   */
  public void setBranchDiameter(double diameter) {
    this.branchDiameter = diameter;
  }

  /**
   * Gets the manifold volume.
   *
   * @return manifold volume in m3
   */
  public double getManifoldVolume() {
    return manifoldVolume;
  }

  /**
   * Gets the design velocity.
   *
   * @return design velocity in m/s
   */
  public double getDesignVelocity() {
    return designVelocity;
  }

  /**
   * Sets the design velocity.
   *
   * @param velocity design velocity in m/s
   */
  public void setDesignVelocity(double velocity) {
    this.designVelocity = velocity;
  }

  /**
   * Gets the maximum allowable velocity.
   *
   * @return max allowable velocity in m/s
   */
  public double getMaxAllowableVelocity() {
    return maxAllowableVelocity;
  }

  /**
   * Sets the maximum allowable velocity.
   *
   * @param velocity max velocity in m/s
   */
  public void setMaxAllowableVelocity(double velocity) {
    this.maxAllowableVelocity = velocity;
  }

  /**
   * Gets the total pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getTotalPressureDrop() {
    return totalPressureDrop;
  }

  /**
   * Gets the splitter type.
   *
   * @return splitter type (tee, wye, header, manifold)
   */
  public String getSplitterType() {
    return splitterType;
  }

  /**
   * Sets the splitter type.
   *
   * @param type splitter type (tee, wye, header, manifold)
   */
  public void setSplitterType(String type) {
    this.splitterType = type;
  }

  /**
   * Gets the number of outlet branches.
   *
   * @return number of branches
   */
  public int getNumberOfBranches() {
    return numberOfBranches;
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

  /**
   * Gets the split ratios.
   *
   * @return array of split ratios for each branch
   */
  public double[] getSplitRatios() {
    return splitRatios;
  }

  // ============================================================================
  // Cost Estimation Methods
  // ============================================================================

  /**
   * Calculate equipment weight for cost estimation.
   *
   * <p>
   * Calculates header weight, branch weight, and total piping weight based on dimensions and wall
   * thickness.
   * </p>
   */
  public void calculateWeights() {
    if (headerDiameter <= 0 || headerLength <= 0) {
      return;
    }

    // Steel density (kg/m3)
    double steelDensity = 7850.0;

    // Header weight
    double headerOD = headerDiameter + 2.0 * headerWallThickness / 1000.0;
    double headerCrossSectionArea =
        Math.PI / 4.0 * (Math.pow(headerOD, 2) - Math.pow(headerDiameter, 2));
    double headerWeight = headerCrossSectionArea * headerLength * steelDensity;

    // Branch weight (simplified - assume same wall thickness)
    double branchLength = 0.5; // Assume 0.5m per branch
    double branchOD = branchDiameter + 2.0 * headerWallThickness / 1000.0;
    double branchCrossSectionArea =
        Math.PI / 4.0 * (Math.pow(branchOD, 2) - Math.pow(branchDiameter, 2));
    double branchWeight = branchCrossSectionArea * branchLength * steelDensity * numberOfBranches;

    // Set weights
    weigthVesselShell = headerWeight;
    weightPiping = branchWeight;
    setWeightTotal(headerWeight + branchWeight);
  }

  /**
   * Calculate cost for splitter equipment.
   *
   * @return estimated cost in USD
   */
  public double calculateSplitterCost() {
    if (headerDiameter <= 0) {
      return 0.0;
    }

    // Calculate weights first
    calculateWeights();

    // Use piping cost correlation
    double pipeCostPerMeter =
        getCostEstimate().getCostCalculator().calcPipingCost(headerDiameter, 1.0, 40);

    // Header cost
    double headerCost = pipeCostPerMeter * headerLength;

    // Branch costs (assume 0.5m per branch)
    double branchCostPerMeter =
        getCostEstimate().getCostCalculator().calcPipingCost(branchDiameter, 1.0, 40);
    double branchCost = branchCostPerMeter * 0.5 * numberOfBranches;

    // Fittings and fabrication (50% of pipe cost)
    double fittingsCost = (headerCost + branchCost) * 0.5;

    return headerCost + branchCost + fittingsCost;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    MechanicalDesignResponse baseResponse = new MechanicalDesignResponse(this);
    JsonObject jsonObj = JsonParser.parseString(baseResponse.toJson()).getAsJsonObject();

    // Add splitter-specific design data
    jsonObj.addProperty("splitterType", splitterType);
    jsonObj.addProperty("numberOfBranches", numberOfBranches);
    jsonObj.addProperty("headerDiameter_m", headerDiameter);
    jsonObj.addProperty("headerDiameter_inches", headerDiameter / 0.0254);
    jsonObj.addProperty("headerWallThickness_mm", headerWallThickness);
    jsonObj.addProperty("headerLength_m", headerLength);
    jsonObj.addProperty("branchDiameter_m", branchDiameter);
    jsonObj.addProperty("branchDiameter_inches", branchDiameter / 0.0254);
    jsonObj.addProperty("manifoldVolume_m3", manifoldVolume);
    jsonObj.addProperty("designVelocity_m_s", designVelocity);
    jsonObj.addProperty("maxAllowableVelocity_m_s", maxAllowableVelocity);
    jsonObj.addProperty("totalPressureDrop_bar", totalPressureDrop);
    jsonObj.addProperty("materialGrade", materialGrade);
    jsonObj.addProperty("designStandardCode", designStandardCode);

    // Add split ratios if available
    if (splitRatios != null && splitRatios.length > 0) {
      com.google.gson.JsonArray ratiosArray = new com.google.gson.JsonArray();
      for (double ratio : splitRatios) {
        ratiosArray.add(ratio);
      }
      jsonObj.add("splitRatios", ratiosArray);
    }

    // Add cost estimation data
    calculateWeights();
    JsonObject costData = new JsonObject();
    costData.addProperty("headerWeight_kg", weigthVesselShell);
    costData.addProperty("branchWeight_kg", weightPiping);
    costData.addProperty("totalWeight_kg", getWeightTotal());
    costData.addProperty("estimatedCost_USD", calculateSplitterCost());
    jsonObj.add("costEstimation", costData);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(jsonObj);
  }
}

