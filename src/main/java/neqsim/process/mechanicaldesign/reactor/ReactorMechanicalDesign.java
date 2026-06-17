package neqsim.process.mechanicaldesign.reactor;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for reactor vessels per ASME VIII Div 1.
 *
 * <p>
 * Covers pressure vessel sizing for fixed-bed, CSTR, and packed-bed reactor vessels. Includes
 * catalyst loading calculation, bed pressure drop estimation (Ergun equation), internal
 * distribution plate design, and weight/cost estimation. Applicable to reactors modeled with
 * {@link GibbsReactor} or similar equipment classes.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ReactorMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // ============================================================================
  // Design Parameters
  // ============================================================================

  /** Reactor type: "FIXED_BED", "CSTR", "PACKED_BED", "FLUIDIZED_BED". */
  private String reactorType = "FIXED_BED";

  /** Vessel inner diameter in meters (computed). */
  private double vesselDiameter = 0.0;

  /** Vessel tan-tan length in meters (computed). */
  private double vesselLength = 0.0;

  /** Shell wall thickness in mm (computed). */
  private double shellThickness = 0.0;

  /** Head wall thickness in mm (computed). */
  private double headThickness = 0.0;

  /** Design pressure in bara. */
  private double designPressureBara = 0.0;

  /** Design temperature in Celsius. */
  private double designTemperatureC = 0.0;

  /** Design pressure margin factor. */
  private double designPressureMargin = 1.10;

  /** Design temperature margin above max operating in Celsius. */
  private double designTemperatureMarginC = 25.0;

  /** Catalyst volume in m3. */
  private double catalystVolume = 0.0;

  /** Catalyst mass in kg. */
  private double catalystMass = 0.0;

  /** Catalyst bulk density in kg/m3 (default alumina-based). */
  private double catalystBulkDensity = 800.0;

  /** Catalyst particle diameter in mm. */
  private double catalystParticleDiameter = 3.0;

  /** Catalyst bed void fraction. */
  private double bedVoidFraction = 0.40;

  /** Bed pressure drop in bar (computed, Ergun equation). */
  private double bedPressureDrop = 0.0;

  /** Minimum L/D ratio for vessel. */
  private double minLDRatio = 2.0;

  /** Maximum L/D ratio for vessel. */
  private double maxLDRatio = 8.0;

  /** Gas hourly space velocity in 1/hr. */
  private double ghsv = 5000.0;

  /** Liquid hourly space velocity in 1/hr (for liquid-phase reactors). */
  private double lhsv = 1.0;

  /** Vessel empty weight in kg (computed). */
  private double emptyVesselWeight = 0.0;

  /** Total equipped weight in kg (computed). */
  private double totalEquippedWeight = 0.0;

  /** Allowable stress for vessel material in MPa. */
  private double allowableStressMPa = 137.9; // SA-516-70

  /** Number of catalyst beds. */
  private int numberOfBeds = 1;

  /** Distribution plate thickness in mm. */
  private double distributionPlateThickness = 12.0;

  /**
   * Constructor for ReactorMechanicalDesign.
   *
   * @param equipment the reactor equipment
   */
  public ReactorMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    ProcessEquipmentInterface reactor = getProcessEquipment();

    // Get inlet stream via TwoPortInterface
    StreamInterface inletStream = null;
    if (reactor instanceof TwoPortInterface) {
      inletStream = ((TwoPortInterface) reactor).getInletStream();
    }
    if (inletStream == null || inletStream.getThermoSystem() == null) {
      return;
    }

    double operatingPressure = Math.max(inletStream.getPressure("bara"), getMaxOperationPressure());
    double operatingTemperatureC = Math.max(inletStream.getTemperature("C"),
        getMaxOperationTemperature() > 0 ? getMaxOperationTemperature() - 273.15 : 0);
    double feedFlowM3s = inletStream.getFlowRate("m3/hr") / 3600.0;
    double gasDensity = inletStream.getThermoSystem().getDensity("kg/m3");
    double gasViscosity = 1.5e-5; // Pa*s default

    // === Design Conditions ===
    designPressureBara = operatingPressure * designPressureMargin;
    designTemperatureC = operatingTemperatureC + designTemperatureMarginC;

    // === Catalyst Volume from GHSV ===
    double feedFlowM3hr = feedFlowM3s * 3600.0;
    if ("CSTR".equals(reactorType)) {
      catalystVolume = feedFlowM3hr / Math.max(lhsv, 0.01);
    } else {
      catalystVolume = feedFlowM3hr / Math.max(ghsv, 1.0);
    }
    catalystVolume = Math.max(catalystVolume, 0.01);
    catalystMass = catalystVolume * catalystBulkDensity;

    // === Vessel Sizing ===
    // Total vessel volume: catalyst volume / bed fill fraction (~70%)
    double bedFillFraction = 0.70;
    double totalVolume = catalystVolume / bedFillFraction;

    // Optimize L/D for target ratio
    double targetLD = 4.0;
    vesselDiameter = Math.pow(4.0 * totalVolume / (Math.PI * targetLD), 1.0 / 3.0);
    vesselDiameter = Math.max(vesselDiameter, 0.5);
    vesselLength = targetLD * vesselDiameter;

    // Check L/D limits
    double ldRatio = vesselLength / vesselDiameter;
    if (ldRatio < minLDRatio) {
      vesselLength = vesselDiameter * minLDRatio;
    } else if (ldRatio > maxLDRatio) {
      vesselDiameter = vesselLength / maxLDRatio;
    }

    // === Bed Pressure Drop (Ergun Equation) ===
    // dP/dL = (150 * mu * (1-eps)^2 * v) / (dp^2 * eps^3)
    // + (1.75 * rho * (1-eps) * v^2) / (dp * eps^3)
    double bedLength = catalystVolume / (Math.PI * vesselDiameter * vesselDiameter / 4.0);
    double velocity = feedFlowM3s / (Math.PI * vesselDiameter * vesselDiameter / 4.0);
    double dp = catalystParticleDiameter / 1000.0; // mm to m
    double eps = bedVoidFraction;

    double viscousTerm =
        150.0 * gasViscosity * Math.pow(1.0 - eps, 2) * velocity / (dp * dp * Math.pow(eps, 3));
    double inertialTerm =
        1.75 * gasDensity * (1.0 - eps) * velocity * velocity / (dp * Math.pow(eps, 3));
    double dPdL = viscousTerm + inertialTerm; // Pa/m
    bedPressureDrop = dPdL * bedLength / 1.0e5; // bar

    // === Shell Wall Thickness (ASME VIII Div 1) ===
    double pMPa = designPressureBara * 0.1;
    double rM = vesselDiameter / 2.0;
    double sMPa = allowableStressMPa;
    double e = getJointEfficiency();
    double caMm = getCorrosionAllowance();

    shellThickness = (pMPa * rM * 1000.0) / (sMPa * e - 0.6 * pMPa) + caMm;
    shellThickness = Math.max(shellThickness, 6.0);

    // Head (2:1 ellipsoidal)
    double dM = vesselDiameter;
    headThickness = (pMPa * dM * 1000.0) / (2.0 * sMPa * e - 0.2 * pMPa) + caMm;
    headThickness = Math.max(headThickness, shellThickness);

    // === Weight Estimation ===
    double steelDensity = 7850.0;
    double shellArea = Math.PI * vesselDiameter * vesselLength;
    double headArea = 2.0 * Math.PI * Math.pow(vesselDiameter / 2.0, 2) * 1.084;
    emptyVesselWeight = (shellArea + headArea) * (shellThickness / 1000.0) * steelDensity;

    // Internals: distribution plates, support grids
    double internalsWeight = numberOfBeds * Math.PI * vesselDiameter * vesselDiameter / 4.0
        * (distributionPlateThickness / 1000.0) * steelDensity * 2.0; // top + bottom per bed
    double nozzleWeight = emptyVesselWeight * 0.10;

    totalEquippedWeight = emptyVesselWeight + catalystMass + internalsWeight + nozzleWeight;

    // === Set base class fields ===
    innerDiameter = vesselDiameter;
    outerDiameter = vesselDiameter + 2.0 * shellThickness / 1000.0;
    wallThickness = shellThickness;
    tantanLength = vesselLength;
    setWeightTotal(totalEquippedWeight);
    weigthInternals = internalsWeight + catalystMass;
    weightVessel = emptyVesselWeight;
    weightNozzle = nozzleWeight;
  }

  /**
   * Gets the reactor type.
   *
   * @return reactor type string
   */
  public String getReactorType() {
    return reactorType;
  }

  /**
   * Sets the reactor type.
   *
   * @param reactorType "FIXED_BED", "CSTR", "PACKED_BED", or "FLUIDIZED_BED"
   */
  public void setReactorType(String reactorType) {
    this.reactorType = reactorType;
  }

  /**
   * Gets the catalyst volume.
   *
   * @return volume in m3
   */
  public double getCatalystVolume() {
    return catalystVolume;
  }

  /**
   * Gets the catalyst mass.
   *
   * @return mass in kg
   */
  public double getCatalystMass() {
    return catalystMass;
  }

  /**
   * Gets the bed pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getBedPressureDrop() {
    return bedPressureDrop;
  }

  /**
   * Sets the catalyst bulk density.
   *
   * @param density density in kg/m3
   */
  public void setCatalystBulkDensity(double density) {
    this.catalystBulkDensity = density;
  }

  /**
   * Sets the catalyst particle diameter.
   *
   * @param diameter diameter in mm
   */
  public void setCatalystParticleDiameter(double diameter) {
    this.catalystParticleDiameter = diameter;
  }

  /**
   * Sets the bed void fraction.
   *
   * @param voidFraction void fraction (0-1, typical 0.35-0.45)
   */
  public void setBedVoidFraction(double voidFraction) {
    this.bedVoidFraction = voidFraction;
  }

  /**
   * Sets the gas hourly space velocity.
   *
   * @param ghsv GHSV in 1/hr
   */
  public void setGHSV(double ghsv) {
    this.ghsv = ghsv;
  }

  /**
   * Sets the liquid hourly space velocity.
   *
   * @param lhsv LHSV in 1/hr
   */
  public void setLHSV(double lhsv) {
    this.lhsv = lhsv;
  }

  /**
   * Sets the number of catalyst beds.
   *
   * @param beds number of beds
   */
  public void setNumberOfBeds(int beds) {
    this.numberOfBeds = beds;
  }

  /**
   * Gets the number of catalyst beds.
   *
   * @return number of beds
   */
  public int getNumberOfBeds() {
    return numberOfBeds;
  }

  /**
   * Gets the vessel diameter.
   *
   * @return diameter in meters
   */
  public double getVesselDiameter() {
    return vesselDiameter;
  }

  /**
   * Gets the vessel length.
   *
   * @return length in meters
   */
  public double getVesselLength() {
    return vesselLength;
  }

  /**
   * Gets the shell wall thickness.
   *
   * @return thickness in mm
   */
  public double getShellThickness() {
    return shellThickness;
  }

  /**
   * Gets the head thickness.
   *
   * @return thickness in mm
   */
  public double getHeadThickness() {
    return headThickness;
  }

  /**
   * Gets the empty vessel weight.
   *
   * @return weight in kg
   */
  public double getEmptyVesselWeight() {
    return emptyVesselWeight;
  }

  /**
   * Gets the total equipped weight.
   *
   * @return weight in kg
   */
  public double getTotalEquippedWeight() {
    return totalEquippedWeight;
  }

  /**
   * Gets the design pressure.
   *
   * @return design pressure in bara
   */
  public double getDesignPressureBara() {
    return designPressureBara;
  }

  /**
   * Gets the design temperature.
   *
   * @return design temperature in Celsius
   */
  public double getDesignTemperatureC() {
    return designTemperatureC;
  }
}
