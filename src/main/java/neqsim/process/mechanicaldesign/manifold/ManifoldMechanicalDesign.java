package neqsim.process.mechanicaldesign.manifold;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldLocation;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator.ManifoldType;

/**
 * Mechanical design class for manifolds.
 *
 * <p>
 * This class bridges the Manifold equipment with mechanical design calculations for:
 * </p>
 * <ul>
 * <li>Topside manifolds on offshore platforms</li>
 * <li>Onshore manifolds in process facilities</li>
 * <li>Subsea manifolds on seabed</li>
 * </ul>
 *
 * <p>
 * Design includes wall thickness, velocity limits, branch reinforcement, support design, and
 * vibration analysis per applicable codes.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public class ManifoldMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Calculator for mechanical design. */
  private ManifoldMechanicalDesignCalculator calculator;

  /** Data source for design parameters. */
  private ManifoldMechanicalDesignDataSource dataSource;

  /** Design standard code. */
  private String designStandardCode = "ASME-B31.3";

  /** Manifold location. */
  private ManifoldLocation location = ManifoldLocation.TOPSIDE;

  /** Manifold type. */
  private ManifoldType manifoldType = ManifoldType.PRODUCTION;

  /** Material grade. */
  private String materialGrade = "A106-B";

  /** Number of inlets. */
  private int numberOfInlets = 1;

  /** Number of outlets. */
  private int numberOfOutlets = 2;

  /** Header outer diameter in meters. */
  private double headerDiameter = 0.3048;

  /** Branch outer diameter in meters. */
  private double branchDiameter = 0.1524;

  /** Water depth for subsea in meters. */
  private double waterDepth = 0.0;

  /**
   * Constructor for ManifoldMechanicalDesign.
   *
   * @param equipment the manifold equipment
   */
  public ManifoldMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.calculator = new ManifoldMechanicalDesignCalculator();
    this.dataSource = new ManifoldMechanicalDesignDataSource();
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Configure calculator from equipment
    Manifold manifold = (Manifold) getProcessEquipment();

    calculator.setLocation(location);
    calculator.setManifoldType(manifoldType);
    calculator.setHeaderOuterDiameter(headerDiameter);
    calculator.setBranchOuterDiameter(branchDiameter);
    calculator.setNumberOfInlets(numberOfInlets);
    calculator.setNumberOfOutlets(numberOfOutlets);
    calculator.setMaterialGrade(materialGrade);
    calculator.setDesignPressure(getMaxOperationPressure() * 1.1);
    calculator.setDesignTemperature(getMaxOperationTemperature() - 273.15);
    calculator.setWaterDepth(waterDepth);

    // Get flow properties from mixed stream if available
    if (manifold.getMixedStream() != null && manifold.getMixedStream().getFluid() != null) {
      try {
        double density = manifold.getMixedStream().getFluid().getDensity("kg/m3");
        double massFlow = manifold.getMixedStream().getFluid().getFlowRate("kg/sec");
        double liquidFrac = manifold.getMixedStream().getFluid().getVolumeFraction(0);

        calculator.setMixtureDensity(density > 0 ? density : 100.0);
        calculator.setMassFlowRate(massFlow > 0 ? massFlow : 10.0);
        calculator.setLiquidFraction(liquidFrac);
      } catch (Exception e) {
        // Use defaults
        calculator.setMixtureDensity(100.0);
        calculator.setMassFlowRate(10.0);
      }
    }

    // Load database parameters
    dataSource.loadIntoCalculator(calculator, getCompanySpecificDesignStandards(),
        designStandardCode, "Manifold");
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    readDesignSpecifications();

    // Perform design verification
    boolean passed = calculator.performDesignVerification();

    // Update base class properties
    setWallThickness(calculator.getHeaderWallThickness());
    setInnerDiameter(headerDiameter - 2 * calculator.getHeaderWallThickness());
    setOuterDiameter(headerDiameter);
    setWeightTotal(calculator.getTotalDryWeight());
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    try {
      // Get base response
      MechanicalDesignResponse response = new MechanicalDesignResponse(this);
      JsonObject jsonObj = JsonParser.parseString(response.toJson()).getAsJsonObject();

      // Add manifold-specific fields
      jsonObj.addProperty("designStandardCode", designStandardCode);
      jsonObj.addProperty("materialGrade", materialGrade);
      jsonObj.addProperty("manifoldLocation", location.name());
      jsonObj.addProperty("manifoldType", manifoldType.name());
      jsonObj.addProperty("numberOfInlets", numberOfInlets);
      jsonObj.addProperty("numberOfOutlets", numberOfOutlets);
      jsonObj.addProperty("headerDiameter_m", headerDiameter);
      jsonObj.addProperty("branchDiameter_m", branchDiameter);

      // Add calculator results
      JsonObject calcObj = JsonParser.parseString(calculator.toJson()).getAsJsonObject();
      jsonObj.add("designCalculations", calcObj);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(jsonObj);
    } catch (Exception e) {
      return "{\"error\": \"" + e.getMessage() + "\"}";
    }
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Get the calculator.
   *
   * @return the calculator
   */
  public ManifoldMechanicalDesignCalculator getCalculator() {
    return calculator;
  }

  /**
   * Get the design standard code.
   *
   * @return the design standard code
   */
  public String getDesignStandardCode() {
    return designStandardCode;
  }

  /**
   * Set the design standard code.
   *
   * @param designStandardCode the design standard code
   */
  public void setDesignStandardCode(String designStandardCode) {
    this.designStandardCode = designStandardCode;
  }

  /**
   * Get the manifold location.
   *
   * @return the location
   */
  public ManifoldLocation getLocation() {
    return location;
  }

  /**
   * Set the manifold location.
   *
   * @param location the location
   */
  public void setLocation(ManifoldLocation location) {
    this.location = location;
  }

  /**
   * Get the manifold type.
   *
   * @return the manifold type
   */
  public ManifoldType getManifoldType() {
    return manifoldType;
  }

  /**
   * Set the manifold type.
   *
   * @param manifoldType the manifold type
   */
  public void setManifoldType(ManifoldType manifoldType) {
    this.manifoldType = manifoldType;
  }

  /**
   * Get the material grade.
   *
   * @return the material grade
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Set the material grade.
   *
   * @param materialGrade the material grade
   */
  public void setMaterialGrade(String materialGrade) {
    this.materialGrade = materialGrade;
  }

  /**
   * Get number of inlets.
   *
   * @return number of inlets
   */
  public int getNumberOfInlets() {
    return numberOfInlets;
  }

  /**
   * Set number of inlets.
   *
   * @param numberOfInlets number of inlets
   */
  public void setNumberOfInlets(int numberOfInlets) {
    this.numberOfInlets = numberOfInlets;
  }

  /**
   * Get number of outlets.
   *
   * @return number of outlets
   */
  public int getNumberOfOutlets() {
    return numberOfOutlets;
  }

  /**
   * Set number of outlets.
   *
   * @param numberOfOutlets number of outlets
   */
  public void setNumberOfOutlets(int numberOfOutlets) {
    this.numberOfOutlets = numberOfOutlets;
  }

  /**
   * Get header diameter.
   *
   * @return header diameter in meters
   */
  public double getHeaderDiameter() {
    return headerDiameter;
  }

  /**
   * Set header diameter.
   *
   * @param headerDiameter header diameter in meters
   */
  public void setHeaderDiameter(double headerDiameter) {
    this.headerDiameter = headerDiameter;
  }

  /**
   * Get branch diameter.
   *
   * @return branch diameter in meters
   */
  public double getBranchDiameter() {
    return branchDiameter;
  }

  /**
   * Set branch diameter.
   *
   * @param branchDiameter branch diameter in meters
   */
  public void setBranchDiameter(double branchDiameter) {
    this.branchDiameter = branchDiameter;
  }

  /**
   * Get water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth water depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }
}
