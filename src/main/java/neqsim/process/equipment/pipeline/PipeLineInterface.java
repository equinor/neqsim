/*
 * PipeLineInterface.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.process.equipment.pipeline;

import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.SimulationInterface;
import neqsim.process.equipment.TwoPortInterface;

/**
 * Common interface for all pipeline simulation models.
 *
 * <p>
 * This interface defines the standard methods that all pipeline models must implement, providing a
 * unified API for pipeline simulation regardless of the underlying physical model (single-phase,
 * two-phase, Beggs-Brill, two-fluid, etc.).
 * </p>
 *
 * <h2>Pipeline Model Categories</h2>
 * <ul>
 * <li><b>Simple models</b>: {@link AdiabaticPipe}, {@link SimpleTPoutPipeline}</li>
 * <li><b>Empirical correlations</b>: {@link PipeBeggsAndBrills}</li>
 * <li><b>Mechanistic models</b>: {@link TwoFluidPipe}, {@link OnePhasePipeLine},
 * {@link TwoPhasePipeLine}</li>
 * <li><b>Specialized models</b>: {@link WaterHammerPipe}, {@link TubingPerformance}</li>
 * </ul>
 *
 * <h2>Common Usage Pattern</h2>
 *
 * <pre>{@code
 * // All pipeline models share these common methods
 * PipeLineInterface pipe = new PipeBeggsAndBrills("pipeline", inletStream);
 * pipe.setLength(5000.0); // 5 km
 * pipe.setDiameter(0.2); // 200 mm
 * pipe.setElevation(100.0); // 100 m rise
 * pipe.setPipeWallRoughness(4.6e-5); // 46 microns
 * pipe.run();
 *
 * double pressureDrop = pipe.getPressureDrop();
 * double outletPressure = pipe.getOutletPressure("bara");
 * String flowRegime = pipe.getFlowRegime();
 * }</pre>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public interface PipeLineInterface extends SimulationInterface, TwoPortInterface {

  // ============================================================================
  // GEOMETRY METHODS - Common to all pipeline models
  // ============================================================================

  /**
   * Set the total pipe length.
   *
   * @param length the pipe length in meters
   */
  public void setLength(double length);

  /**
   * Get the total pipe length.
   *
   * @return the pipe length in meters
   */
  public double getLength();

  /**
   * Set the pipe inner diameter.
   *
   * @param diameter the inner diameter in meters
   */
  public void setDiameter(double diameter);

  /**
   * Get the pipe inner diameter.
   *
   * @return the inner diameter in meters
   */
  public double getDiameter();

  /**
   * Set the pipe wall roughness.
   *
   * @param roughness the wall roughness in meters
   */
  public void setPipeWallRoughness(double roughness);

  /**
   * Get the pipe wall roughness.
   *
   * @return the wall roughness in meters
   */
  public double getPipeWallRoughness();

  /**
   * Set the elevation change from inlet to outlet.
   *
   * @param elevation elevation change in meters (positive = uphill)
   */
  public void setElevation(double elevation);

  /**
   * Get the elevation change from inlet to outlet.
   *
   * @return elevation change in meters
   */
  public double getElevation();

  /**
   * Set the inlet elevation above reference.
   *
   * @param inletElevation inlet elevation in meters
   */
  public void setInletElevation(double inletElevation);

  /**
   * Get the inlet elevation above reference.
   *
   * @return inlet elevation in meters
   */
  public double getInletElevation();

  /**
   * Set the outlet elevation above reference.
   *
   * @param outletElevation outlet elevation in meters
   */
  public void setOutletElevation(double outletElevation);

  /**
   * Get the outlet elevation above reference.
   *
   * @return outlet elevation in meters
   */
  public double getOutletElevation();

  // ============================================================================
  // SEGMENTED GEOMETRY - For multi-leg pipeline models
  // ============================================================================

  /**
   * Set number of pipe legs/segments.
   *
   * @param number number of legs
   */
  public void setNumberOfLegs(int number);

  /**
   * Get number of pipe legs/segments.
   *
   * @return number of legs
   */
  public int getNumberOfLegs();

  /**
   * Set the height profile along the pipe.
   *
   * @param heights array of heights at each leg boundary (length = numberOfLegs + 1)
   */
  public void setHeightProfile(double[] heights);

  /**
   * Set the position of each leg along the pipe.
   *
   * @param positions array of positions at each leg boundary in meters
   */
  public void setLegPositions(double[] positions);

  /**
   * Set pipe diameters for each segment.
   *
   * @param diameters array of diameters for each leg boundary
   */
  public void setPipeDiameters(double[] diameters);

  /**
   * Set wall roughness for each segment.
   *
   * @param roughness array of roughness values for each leg
   */
  public void setPipeWallRoughness(double[] roughness);

  /**
   * Set outer temperatures for heat transfer calculations.
   *
   * @param outerTemperatures array of outer temperatures in Kelvin for each leg
   */
  public void setOuterTemperatures(double[] outerTemperatures);

  /**
   * Set number of computational nodes in each leg.
   *
   * @param number number of nodes per leg
   */
  public void setNumberOfNodesInLeg(int number);

  /**
   * Set number of computational increments/segments.
   *
   * @param numberOfIncrements number of increments for pressure drop calculation
   */
  public void setNumberOfIncrements(int numberOfIncrements);

  /**
   * Get number of computational increments/segments.
   *
   * @return number of increments
   */
  public int getNumberOfIncrements();

  // ============================================================================
  // CALCULATION MODE METHODS
  // ============================================================================

  /**
   * Set the outlet pressure for flow rate calculation mode.
   *
   * <p>
   * When outlet pressure is specified, the pipeline model calculates the required flow rate to
   * achieve the target outlet pressure.
   * </p>
   *
   * @param pressure outlet pressure in bara
   */
  public void setOutPressure(double pressure);

  /**
   * Set the outlet temperature.
   *
   * @param temperature outlet temperature in Kelvin
   */
  public void setOutTemperature(double temperature);

  // ============================================================================
  // RESULTS METHODS - Common output from all pipeline models
  // ============================================================================

  /**
   * Get the total pressure drop across the pipeline.
   *
   * @return pressure drop in bar
   */
  public double getPressureDrop();

  /**
   * Get the outlet pressure.
   *
   * @param unit pressure unit (e.g., "bara", "barg", "Pa", "MPa")
   * @return outlet pressure in specified unit
   */
  public double getOutletPressure(String unit);

  /**
   * Get the outlet temperature.
   *
   * @param unit temperature unit (e.g., "K", "C")
   * @return outlet temperature in specified unit
   */
  public double getOutletTemperature(String unit);

  /**
   * Get the flow velocity in the pipe.
   *
   * @return velocity in m/s
   */
  public double getVelocity();

  /**
   * Get the superficial velocity for a specific phase.
   *
   * @param phaseNumber phase index (0=gas, 1=liquid)
   * @return superficial velocity in m/s
   */
  public double getSuperficialVelocity(int phaseNumber);

  /**
   * Get the determined flow regime.
   *
   * @return flow regime as string (e.g., "stratified", "slug", "annular", "dispersed bubble")
   */
  public String getFlowRegime();

  /**
   * Get the liquid holdup fraction.
   *
   * @return liquid holdup as fraction (0-1)
   */
  public double getLiquidHoldup();

  /**
   * Get the Reynolds number for the flow.
   *
   * @return Reynolds number (dimensionless)
   */
  public double getReynoldsNumber();

  /**
   * Get the friction factor.
   *
   * @return Darcy friction factor (dimensionless)
   */
  public double getFrictionFactor();

  // ============================================================================
  // PROFILE METHODS - For detailed results along pipe length
  // ============================================================================

  /**
   * Get the pressure profile along the pipe.
   *
   * @return array of pressures in bar at each increment
   */
  public double[] getPressureProfile();

  /**
   * Get the temperature profile along the pipe.
   *
   * @return array of temperatures in Kelvin at each increment
   */
  public double[] getTemperatureProfile();

  /**
   * Get the liquid holdup profile along the pipe.
   *
   * @return array of liquid holdup values at each increment
   */
  public double[] getLiquidHoldupProfile();

  // ============================================================================
  // HEAT TRANSFER METHODS
  // ============================================================================

  /**
   * Set the overall heat transfer coefficient.
   *
   * @param coefficient heat transfer coefficient in W/(m²·K)
   */
  public void setHeatTransferCoefficient(double coefficient);

  /**
   * Get the overall heat transfer coefficient.
   *
   * @return heat transfer coefficient in W/(m²·K)
   */
  public double getHeatTransferCoefficient();

  /**
   * Set the constant surface temperature for heat transfer.
   *
   * @param temperature surface temperature in Kelvin
   */
  public void setConstantSurfaceTemperature(double temperature);

  /**
   * Check if the pipe is operating in adiabatic mode.
   *
   * @return true if adiabatic (no heat transfer)
   */
  public boolean isAdiabatic();

  /**
   * Set whether the pipe operates in adiabatic mode.
   *
   * @param adiabatic true for no heat transfer
   */
  public void setAdiabatic(boolean adiabatic);

  // ============================================================================
  // ADVANCED CONFIGURATION - For specific pipeline models
  // ============================================================================

  /**
   * Set output file name for detailed results.
   *
   * @param name output file name
   */
  public void setOutputFileName(String name);

  /**
   * Set initial flow pattern for simulation initialization.
   *
   * @param flowPattern initial flow pattern (e.g., "stratified", "slug")
   */
  public void setInitialFlowPattern(String flowPattern);

  /**
   * Get the underlying flow system (for advanced models).
   *
   * @return flow system interface or null if not applicable
   */
  public FlowSystemInterface getPipe();

  /**
   * Set pipe specification from database.
   *
   * @param nominalDiameter nominal diameter in mm or inches
   * @param specification pipe specification code (e.g., "API 5L", "ANSI B36.10")
   */
  public void setPipeSpecification(double nominalDiameter, String specification);

  /**
   * Get the pipe wall thickness.
   *
   * @return wall thickness in meters
   */
  public double getWallThickness();

  /**
   * Set the pipe wall thickness.
   *
   * @param thickness wall thickness in meters
   */
  public void setWallThickness(double thickness);

  /**
   * Get the angle of the pipe from horizontal.
   *
   * @return angle in degrees (0 = horizontal, 90 = vertical upward)
   */
  public double getAngle();

  /**
   * Set the angle of the pipe from horizontal.
   *
   * @param angle angle in degrees (0 = horizontal, 90 = vertical upward)
   */
  public void setAngle(double angle);

  // ============================================================================
  // MECHANICAL BUILDUP - Pipe Wall Construction
  // ============================================================================

  /**
   * Set the pipe material.
   *
   * @param material pipe material name (e.g., "carbon steel", "stainless steel", "duplex")
   */
  public void setPipeMaterial(String material);

  /**
   * Get the pipe material.
   *
   * @return pipe material name
   */
  public String getPipeMaterial();

  /**
   * Set the pipe schedule.
   *
   * @param schedule pipe schedule (e.g., "40", "80", "STD", "XS")
   */
  public void setPipeSchedule(String schedule);

  /**
   * Get the pipe schedule.
   *
   * @return pipe schedule
   */
  public String getPipeSchedule();

  /**
   * Set the insulation thickness.
   *
   * @param thickness insulation thickness in meters
   */
  public void setInsulationThickness(double thickness);

  /**
   * Get the insulation thickness.
   *
   * @return insulation thickness in meters
   */
  public double getInsulationThickness();

  /**
   * Set the insulation thermal conductivity.
   *
   * @param conductivity thermal conductivity in W/(m·K)
   */
  public void setInsulationConductivity(double conductivity);

  /**
   * Get the insulation thermal conductivity.
   *
   * @return thermal conductivity in W/(m·K)
   */
  public double getInsulationConductivity();

  /**
   * Set the insulation type/material.
   *
   * @param insulationType insulation type (e.g., "polyurethane", "mineral wool", "aerogel")
   */
  public void setInsulationType(String insulationType);

  /**
   * Get the insulation type/material.
   *
   * @return insulation type
   */
  public String getInsulationType();

  /**
   * Set the coating thickness.
   *
   * @param thickness coating thickness in meters
   */
  public void setCoatingThickness(double thickness);

  /**
   * Get the coating thickness.
   *
   * @return coating thickness in meters
   */
  public double getCoatingThickness();

  /**
   * Set the coating thermal conductivity.
   *
   * @param conductivity thermal conductivity in W/(m·K)
   */
  public void setCoatingConductivity(double conductivity);

  /**
   * Get the coating thermal conductivity.
   *
   * @return thermal conductivity in W/(m·K)
   */
  public double getCoatingConductivity();

  /**
   * Set the pipe wall thermal conductivity.
   *
   * @param conductivity thermal conductivity in W/(m·K)
   */
  public void setPipeWallConductivity(double conductivity);

  /**
   * Get the pipe wall thermal conductivity.
   *
   * @return thermal conductivity in W/(m·K)
   */
  public double getPipeWallConductivity();

  // ============================================================================
  // OUTER HEAT TRANSFER - External Conditions
  // ============================================================================

  /**
   * Set the outer (external) heat transfer coefficient.
   *
   * <p>
   * This is the convective heat transfer coefficient between the pipe outer surface (or insulation
   * outer surface if insulated) and the surrounding environment.
   * </p>
   *
   * <p>
   * Typical values:
   * </p>
   * <ul>
   * <li>Still air: 5-25 W/(m²·K)</li>
   * <li>Flowing air: 10-100 W/(m²·K)</li>
   * <li>Buried in soil: 1-5 W/(m²·K)</li>
   * <li>Still water: 100-500 W/(m²·K)</li>
   * <li>Flowing seawater: 500-2000 W/(m²·K)</li>
   * </ul>
   *
   * @param coefficient outer heat transfer coefficient in W/(m²·K)
   */
  public void setOuterHeatTransferCoefficient(double coefficient);

  /**
   * Get the outer (external) heat transfer coefficient.
   *
   * @return outer heat transfer coefficient in W/(m²·K)
   */
  public double getOuterHeatTransferCoefficient();

  /**
   * Set the inner (fluid-side) heat transfer coefficient.
   *
   * <p>
   * This is the convective heat transfer coefficient between the fluid and the pipe inner wall. If
   * not set, it can be calculated automatically based on flow conditions.
   * </p>
   *
   * @param coefficient inner heat transfer coefficient in W/(m²·K)
   */
  public void setInnerHeatTransferCoefficient(double coefficient);

  /**
   * Get the inner (fluid-side) heat transfer coefficient.
   *
   * @return inner heat transfer coefficient in W/(m²·K)
   */
  public double getInnerHeatTransferCoefficient();

  /**
   * Set outer heat transfer coefficients for each pipe segment.
   *
   * @param coefficients array of outer heat transfer coefficients in W/(m²·K)
   */
  public void setOuterHeatTransferCoefficients(double[] coefficients);

  /**
   * Set inner (wall) heat transfer coefficients for each pipe segment.
   *
   * @param coefficients array of wall heat transfer coefficients in W/(m²·K)
   */
  public void setWallHeatTransferCoefficients(double[] coefficients);

  /**
   * Set ambient/surrounding temperatures for each pipe segment.
   *
   * @param temperatures array of ambient temperatures in Kelvin
   */
  public void setAmbientTemperatures(double[] temperatures);

  /**
   * Set the ambient temperature.
   *
   * @param temperature ambient temperature in Kelvin
   */
  public void setAmbientTemperature(double temperature);

  /**
   * Get the ambient temperature.
   *
   * @return ambient temperature in Kelvin
   */
  public double getAmbientTemperature();

  /**
   * Calculate the overall heat transfer coefficient based on pipe buildup.
   *
   * <p>
   * This method calculates the overall U-value considering:
   * </p>
   * <ul>
   * <li>Inner fluid-side convection</li>
   * <li>Pipe wall conduction</li>
   * <li>Coating conduction (if present)</li>
   * <li>Insulation conduction (if present)</li>
   * <li>Outer convection</li>
   * </ul>
   *
   * <p>
   * The calculation uses the resistance analogy for cylindrical geometries.
   * </p>
   *
   * @return overall heat transfer coefficient in W/(m²·K) based on inner diameter
   */
  public double calculateOverallHeatTransferCoefficient();

  // ============================================================================
  // BURIAL CONDITIONS - For buried pipelines
  // ============================================================================

  /**
   * Set the burial depth below ground surface.
   *
   * @param depth burial depth in meters
   */
  public void setBurialDepth(double depth);

  /**
   * Get the burial depth.
   *
   * @return burial depth in meters
   */
  public double getBurialDepth();

  /**
   * Set the soil thermal conductivity.
   *
   * @param conductivity soil thermal conductivity in W/(m·K)
   */
  public void setSoilConductivity(double conductivity);

  /**
   * Get the soil thermal conductivity.
   *
   * @return soil thermal conductivity in W/(m·K)
   */
  public double getSoilConductivity();

  /**
   * Check if the pipe is buried.
   *
   * @return true if pipe is buried
   */
  public boolean isBuried();

  /**
   * Set whether the pipe is buried.
   *
   * @param buried true if pipe is buried
   */
  public void setBuried(boolean buried);

  // ============================================================================
  // MECHANICAL DESIGN - Standards-based design calculations
  // ============================================================================

  /**
   * Get the mechanical design calculator for this pipeline.
   *
   * <p>
   * The calculator provides standards-based calculations for:
   * </p>
   * <ul>
   * <li>Wall thickness calculation per ASME B31.3/B31.4/B31.8 or DNV-OS-F101</li>
   * <li>Maximum Allowable Operating Pressure (MAOP)</li>
   * <li>Stress analysis (hoop, longitudinal, von Mises)</li>
   * <li>Test pressure calculation</li>
   * </ul>
   *
   * @return mechanical design calculator
   */
  public neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator getMechanicalDesignCalculator();

  /**
   * Set design pressure for mechanical design calculations.
   *
   * @param pressure design pressure in MPa
   */
  public void setDesignPressure(double pressure);

  /**
   * Get design pressure.
   *
   * @return design pressure in MPa
   */
  public double getDesignPressure();

  /**
   * Set design pressure with unit.
   *
   * @param pressure design pressure value
   * @param unit pressure unit ("MPa", "bar", "bara", "psi")
   */
  public void setDesignPressure(double pressure, String unit);

  /**
   * Set maximum design temperature for mechanical design.
   *
   * @param temperature design temperature in Celsius
   */
  public void setDesignTemperature(double temperature);

  /**
   * Get maximum design temperature.
   *
   * @return design temperature in Celsius
   */
  public double getDesignTemperature();

  /**
   * Set the pipe material grade per API 5L.
   *
   * @param grade material grade (e.g., "X42", "X52", "X65", "X70", "X80")
   */
  public void setMaterialGrade(String grade);

  /**
   * Get the pipe material grade.
   *
   * @return material grade
   */
  public String getMaterialGrade();

  /**
   * Set the design code for wall thickness and pressure calculations.
   *
   * @param code design code (e.g., "ASME_B31_8", "ASME_B31_4", "ASME_B31_3", "DNV_OS_F101")
   */
  public void setDesignCode(String code);

  /**
   * Get the design code.
   *
   * @return design code
   */
  public String getDesignCode();

  /**
   * Set the location class per ASME B31.8.
   *
   * <p>
   * Location classes:
   * </p>
   * <ul>
   * <li>Class 1: Offshore and remote areas (F=0.72)</li>
   * <li>Class 2: Fringe areas (F=0.60)</li>
   * <li>Class 3: Suburban areas (F=0.50)</li>
   * <li>Class 4: Urban areas (F=0.40)</li>
   * </ul>
   *
   * @param locationClass location class 1-4
   */
  public void setLocationClass(int locationClass);

  /**
   * Get the location class.
   *
   * @return location class 1-4
   */
  public int getLocationClass();

  /**
   * Set the corrosion allowance.
   *
   * @param allowance corrosion allowance in meters
   */
  public void setCorrosionAllowance(double allowance);

  /**
   * Get the corrosion allowance.
   *
   * @return corrosion allowance in meters
   */
  public double getCorrosionAllowance();

  /**
   * Calculate minimum required wall thickness based on design code.
   *
   * @return minimum wall thickness in meters
   */
  public double calculateMinimumWallThickness();

  /**
   * Calculate Maximum Allowable Operating Pressure (MAOP).
   *
   * @return MAOP in MPa
   */
  public double calculateMAOP();

  /**
   * Get MAOP in specified unit.
   *
   * @param unit pressure unit ("MPa", "bar", "psi")
   * @return MAOP in specified unit
   */
  public double getMAOP(String unit);

  /**
   * Calculate hydrostatic test pressure.
   *
   * @return test pressure in MPa
   */
  public double calculateTestPressure();

  /**
   * Calculate hoop stress at operating pressure.
   *
   * @return hoop stress in MPa
   */
  public double calculateHoopStress();

  /**
   * Calculate von Mises equivalent stress.
   *
   * @param deltaT temperature change from installation in Celsius
   * @return von Mises stress in MPa
   */
  public double calculateVonMisesStress(double deltaT);

  /**
   * Check if the mechanical design is within allowable stress limits.
   *
   * @return true if design stress is below allowable limit
   */
  public boolean isMechanicalDesignSafe();

  /**
   * Generate a mechanical design report.
   *
   * @return formatted design report string
   */
  public String generateMechanicalDesignReport();
}
