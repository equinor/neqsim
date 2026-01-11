package neqsim.process.mechanicaldesign.pipeline;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.pipeline.Riser;

/**
 * Mechanical design class for risers.
 *
 * <p>
 * This class extends PipelineMechanicalDesign to provide riser-specific mechanical design
 * calculations including:
 * </p>
 * <ul>
 * <li>Top tension calculation for catenary and TTR</li>
 * <li>Touchdown point stress analysis</li>
 * <li>VIV (Vortex-Induced Vibration) response estimation</li>
 * <li>Dynamic stress factors for wave and current loading</li>
 * <li>Fatigue life estimation for cyclic loading</li>
 * <li>Heave motion response</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>DNV-OS-F201 - Dynamic Risers</li>
 * <li>DNV-RP-F204 - Riser Fatigue</li>
 * <li>DNV-RP-C203 - Fatigue Design of Offshore Structures</li>
 * <li>API RP 2RD - Design of Risers for Floating Production Systems</li>
 * <li>API RP 17B - Flexible Pipe</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Riser riser = new Riser("Production Riser", inletStream);
 * riser.setRiserType(RiserType.STEEL_CATENARY_RISER);
 * riser.setWaterDepth(800.0);
 * riser.run();
 *
 * RiserMechanicalDesign design = (RiserMechanicalDesign) riser.getMechanicalDesign();
 * design.setMaxOperationPressure(100.0);
 * design.setMaterialGrade("X65");
 * design.setDesignStandardCode("DNV-OS-F201");
 * design.readDesignSpecifications();
 * design.calcDesign();
 *
 * // Get riser-specific results
 * RiserMechanicalDesignCalculator calc = design.getRiserCalculator();
 * double topTension = calc.getTopTension();
 * double touchdownStress = calc.getTouchdownPointStress();
 * double vivAmplitude = calc.getVIVAmplitude();
 * double fatigueLife = calc.getRiserFatigueLife();
 * }</pre>
 *
 * @author ASMF
 * @version 1.0
 * @see Riser
 * @see RiserMechanicalDesignCalculator
 */
public class RiserMechanicalDesign extends PipelineMechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Riser-specific calculator. */
  private RiserMechanicalDesignCalculator riserCalculator;

  /** Reference to the riser equipment. */
  private Riser riser;

  /** Data source for loading design parameters from database. */
  private transient RiserMechanicalDesignDataSource dataSource;

  /**
   * Constructor with riser equipment.
   *
   * @param equipment the riser equipment
   */
  public RiserMechanicalDesign(Riser equipment) {
    super(equipment);
    this.riser = equipment;
    this.riserCalculator = new RiserMechanicalDesignCalculator();
    this.dataSource = new RiserMechanicalDesignDataSource();
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Read base pipeline specifications
    super.readDesignSpecifications();

    // Load riser-specific design parameters from database
    String company = getCompanySpecificDesignStandards();
    if (company == null || company.isEmpty()) {
      company = "default";
    }
    String designCode = getDesignStandardCode();
    if (designCode == null || designCode.isEmpty()) {
      designCode = "DNV-OS-F201";
    }

    // Load parameters from database into calculator
    if (dataSource == null) {
      dataSource = new RiserMechanicalDesignDataSource();
    }
    dataSource.loadIntoCalculator(riserCalculator, company, designCode);

    // Load VIV-specific parameters
    dataSource.loadVIVParameters(riserCalculator);

    // Load fatigue-specific parameters
    dataSource.loadFatigueParameters(riserCalculator);

    // Copy riser-specific parameters to calculator
    if (riser != null) {
      riserCalculator.setRiserType(riser.getRiserType().name());
      riserCalculator.setWaterDepth(riser.getWaterDepth());
      riserCalculator.setTopAngle(riser.getTopAngle());
      riserCalculator.setDepartureAngle(riser.getDepartureAngle());
      riserCalculator.setCurrentVelocity(riser.getCurrentVelocity());
      riserCalculator.setSeabedCurrentVelocity(riser.getSeabedCurrentVelocity());
      riserCalculator.setSignificantWaveHeight(riser.getSignificantWaveHeight());
      riserCalculator.setPeakWavePeriod(riser.getPeakWavePeriod());
      riserCalculator.setPlatformHeaveAmplitude(riser.getPlatformHeaveAmplitude());
      riserCalculator.setPlatformHeavePeriod(riser.getPlatformHeavePeriod());
      riserCalculator.setSeabedFriction(riser.getSeabedFriction());

      // TTR-specific
      if (riser.isTensionedType()) {
        riserCalculator.setAppliedTopTension(riser.getAppliedTopTension());
        riserCalculator.setTensionVariationFactor(riser.getTensionVariationFactor());
      }

      // Lazy-wave specific
      if (riser.hasBuoyancyModules()) {
        riserCalculator.setBuoyancyModuleDepth(riser.getBuoyancyModuleDepth());
        riserCalculator.setBuoyancyModuleLength(riser.getBuoyancyModuleLength());
        riserCalculator.setBuoyancyPerMeter(riser.getBuoyancyPerMeter());
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    // Run base pipeline design calculations
    super.calcDesign();

    // Perform riser-specific calculations
    if (riser != null) {
      // Copy pipe geometry from riser to calculator
      // Inner diameter from riser, calculate outer based on wall thickness
      double innerDiam = riser.getDiameter();
      double wallThick = getWallThickness();
      if (wallThick <= 0) {
        wallThick = 0.0127; // 0.5 inch default
      }
      riserCalculator.setOuterDiameter(innerDiam + 2.0 * wallThick);
      riserCalculator.setNominalWallThickness(wallThick);
      riserCalculator.setDesignPressure(getMaxOperationPressure() * 1.1);

      // Set pipeline length from riser geometry
      riserCalculator.setPipelineLength(riser.getLength());

      // Calculate riser-specific parameters
      if (riser.isCatenaryType()) {
        riserCalculator.calculateCatenaryTopTension();
        riserCalculator.calculateTouchdownPointStress();
        riserCalculator.calculateTouchdownZoneLength();
      } else if (riser.isTensionedType()) {
        riserCalculator.calculateTTRTension();
        riserCalculator.calculateStrokeRequirement();
      }

      // VIV analysis
      riserCalculator.calculateVIVResponse();
      riserCalculator.calculateVIVFatigueDamage();

      // Dynamic response
      riserCalculator.calculateWaveInducedStress();
      riserCalculator.calculateHeaveInducedStress();

      // Fatigue analysis
      riserCalculator.calculateRiserFatigueLife();

      // Collapse check for deep water
      if (riser.getWaterDepth() > 200) {
        riserCalculator.calculateExternalPressure(riser.getWaterDepth());
        riserCalculator.calculateCollapsePressure();
        riserCalculator.calculateCollapseUtilization();
      }
    }
  }

  /**
   * Get the riser-specific calculator.
   *
   * @return riser mechanical design calculator
   */
  public RiserMechanicalDesignCalculator getRiserCalculator() {
    return riserCalculator;
  }

  /**
   * Check if the riser design is acceptable.
   *
   * @return true if all design checks pass
   */
  public boolean isDesignAcceptable() {
    boolean acceptable = true;

    // Check stress utilization
    if (riserCalculator.getMaxStressUtilization() > 1.0) {
      acceptable = false;
    }

    // Check collapse utilization
    if (riserCalculator.getCollapseUtilization() > 1.0) {
      acceptable = false;
    }

    // Check fatigue life
    if (riserCalculator.getRiserFatigueLife() < getDesignLifeYears()) {
      acceptable = false;
    }

    // Check VIV
    if (riserCalculator.isVIVLockIn()) {
      acceptable = false;
    }

    return acceptable;
  }

  /**
   * Get design life in years.
   *
   * @return design life
   */
  public double getDesignLifeYears() {
    return 25.0; // Default 25 years
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    // Get base response from parent
    String parentJson = super.toJson();
    JsonObject jsonObj = JsonParser.parseString(parentJson).getAsJsonObject();

    // Add riser-specific calculations
    JsonObject riserCalc = JsonParser.parseString(riserCalculator.toRiserJson()).getAsJsonObject();
    jsonObj.add("riserDesignCalculations", riserCalc);

    // Add riser properties
    if (riser != null) {
      JsonObject riserProps = new JsonObject();
      riserProps.addProperty("riserType", riser.getRiserType().getDisplayName());
      riserProps.addProperty("waterDepth_m", riser.getWaterDepth());
      riserProps.addProperty("topAngle_deg", riser.getTopAngle());
      riserProps.addProperty("departureAngle_deg", riser.getDepartureAngle());
      riserProps.addProperty("riserLength_m", riser.getLength());
      riserProps.addProperty("isCatenaryType", riser.isCatenaryType());
      riserProps.addProperty("isTensionedType", riser.isTensionedType());
      riserProps.addProperty("hasBuoyancyModules", riser.hasBuoyancyModules());

      // Environmental conditions
      riserProps.addProperty("significantWaveHeight_m", riser.getSignificantWaveHeight());
      riserProps.addProperty("peakWavePeriod_s", riser.getPeakWavePeriod());
      riserProps.addProperty("currentVelocity_m_s", riser.getCurrentVelocity());
      riserProps.addProperty("seabedCurrentVelocity_m_s", riser.getSeabedCurrentVelocity());
      riserProps.addProperty("platformHeaveAmplitude_m", riser.getPlatformHeaveAmplitude());

      jsonObj.add("riserProperties", riserProps);
    }

    // Add design status
    jsonObj.addProperty("designAcceptable", isDesignAcceptable());
    jsonObj.addProperty("designLifeYears", getDesignLifeYears());

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(jsonObj);
  }
}
