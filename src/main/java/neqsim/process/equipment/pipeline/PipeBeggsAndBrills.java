package neqsim.process.equipment.pipeline;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.PipeBeggsBrillsResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Pipeline simulation using Beggs and Brill empirical correlations for multiphase flow.
 *
 * <p>
 * This class implements the Beggs and Brill (1973) correlation for pressure drop and liquid holdup
 * prediction in multiphase pipeline flow. It supports both single-phase and multiphase (gas-liquid)
 * flow in horizontal, inclined, and vertical pipes.
 * </p>
 *
 * <h2>Energy Equation</h2>
 * <p>
 * The energy balance includes three optional components:
 * <ul>
 * <li><b>Wall heat transfer</b> - Heat exchange with surroundings using LMTD method</li>
 * <li><b>Joule-Thomson effect</b> - Temperature change due to gas expansion (cooling)</li>
 * <li><b>Friction heating</b> - Viscous dissipation adding energy to the fluid</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * 
 * <pre>{@code
 * PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipeline", feedStream);
 * pipe.setDiameter(0.1524); // 6 inch
 * pipe.setLength(5000.0); // 5 km
 * pipe.setElevation(0.0); // horizontal
 *
 * // Enable enhanced energy equation
 * pipe.setConstantSurfaceTemperature(288.15, "K");
 * pipe.setHeatTransferCoefficient(25.0); // W/(m²·K)
 * pipe.setIncludeJouleThomsonEffect(true);
 * pipe.setJouleThomsonCoefficient(3.5e-6); // K/Pa
 * pipe.setIncludeFrictionHeating(true);
 *
 * pipe.run();
 * }</pre>
 *
 * @author Even Solbraa, Sviatoslav Eroshkin
 * @version $Id: $Id
 * @see Pipeline
 */
public class PipeBeggsAndBrills extends Pipeline {
  private static final long serialVersionUID = 1001;

  /** Flow regimes available in Beggs and Brill correlations. */
  public enum FlowRegime {
    SEGREGATED, INTERMITTENT, DISTRIBUTED, TRANSITION, SINGLE_PHASE, UNKNOWN
  }

  /** Calculation modes for pipeline simulation. */
  public enum CalculationMode {
    /** Calculate outlet pressure from inlet conditions and flow rate (default). */
    CALCULATE_OUTLET_PRESSURE,
    /** Calculate flow rate from inlet and specified outlet pressure. */
    CALCULATE_FLOW_RATE
  }

  int iteration;

  private double nominalDiameter;

  private Boolean PipeSpecSet = false;

  // Inlet pressure of the pipeline (initialization)
  private double inletPressure = Double.NaN;

  private double totalPressureDrop = 0;

  // Outlet properties initialization [K] and [bar]
  protected double temperatureOut = 270;
  protected double pressureOut = 0.0;

  // Calculation mode and specified outlet pressure
  private CalculationMode calculationMode = CalculationMode.CALCULATE_OUTLET_PRESSURE;
  private double specifiedOutletPressure = Double.NaN;
  private String specifiedOutletPressureUnit = "bara";
  private int maxFlowIterations = 50;
  private double flowConvergenceTolerance = 1e-4;

  // Unit for maximum flow
  String maxflowunit = "kg/hr";

  // Inside diameter of the pipe [m]
  private double insideDiameter = Double.NaN;

  // Thickness diameter of the pipe [m]
  private double pipeThickness = Double.NaN;

  // Roughness of the pipe wall [m]
  private double pipeWallRoughness = 1e-5;

  // Flag to run isothermal calculations
  private boolean runIsothermal = true;

  // Flow pattern of the fluid in the pipe
  private FlowRegime regime;

  // Volume fraction of liquid in the input mixture
  private double inputVolumeFractionLiquid;

  // Froude number of the mixture
  private double mixtureFroudeNumber;

  // Specification of the pipe
  private String pipeSpecification = "LD201";

  // Ref. Beggs and Brills
  private double A;

  // Area of the pipe [m2]
  private double area;

  // Superficial gas velocity in the pipe [m/s]
  private double supGasVel;

  // Superficial liquid velocity in the pipe [m/s]
  private double supLiquidVel;

  // Density of the mixture [kg/m3]
  private double mixtureDensity;

  // Hydrostatic pressure drop in the pipe [bar]
  private double hydrostaticPressureDrop;

  // Holdup ref. Beggs and Brills
  private double El = 0;

  // Superficial mixture velocity in the pipe [m/s]
  private double supMixVel;

  // Frictional pressure loss in the pipe [bar]
  private double frictionPressureLoss;

  // Total pressure drop in the pipe [bar]
  private double pressureDrop;

  // Number of pipe increments for calculations
  private int numberOfIncrements = 5;

  // Length of the pipe [m]
  private double totalLength = Double.NaN;

  // Elevation of the pipe [m]
  private double totalElevation = Double.NaN;

  // Angle of the pipe [degrees]
  private double angle = Double.NaN;

  // Density of the liquid in the mixture in case of water and oil phases present together
  private double mixtureLiquidDensity;

  // Viscosity of the liquid in the mixture in case of water and oil phases present together
  private double mixtureLiquidViscosity;

  // Mass fraction of oil in the mixture in case of water and oil phases present together
  private double mixtureOilMassFraction;

  // Volume fraction of oil in the mixture in case of water and oil phases present together
  private double mixtureOilVolumeFraction;

  private double cumulativeLength;

  private double cumulativeElevation;

  // For segment calculation
  double length;
  double elevation;

  // Results initialization (for each segment)

  private List<Double> pressureProfile;
  private List<Double> temperatureProfile;
  private List<Double> pressureDropProfile;
  private List<FlowRegime> flowRegimeProfile;

  private List<Double> liquidSuperficialVelocityProfile;
  private List<Double> gasSuperficialVelocityProfile;
  private List<Double> mixtureSuperficialVelocityProfile;

  private List<Double> mixtureViscosityProfile;
  private List<Double> mixtureDensityProfile;
  private List<Double> liquidDensityProfile;

  private List<Double> liquidHoldupProfile;
  private List<Double> mixtureReynoldsNumber;

  private List<Double> lengthProfile;
  private List<Double> elevationProfile;
  private List<Integer> incrementsProfile;

  private boolean transientInitialized = false;
  private List<Double> transientPressureProfile;
  private List<Double> transientTemperatureProfile;
  private List<Double> transientMassFlowProfile;
  private List<Double> transientVelocityProfile;
  private List<Double> transientDensityProfile;
  private double segmentLengthMeters = Double.NaN;
  private double crossSectionArea = Double.NaN;

  private static final double MIN_TRANSIT_VELOCITY = 1.0e-3;
  private static final double MIN_DENSITY = 1.0e-6;

  // Flag to run isothermal calculations
  private boolean runAdiabatic = true;
  private boolean runConstantSurfaceTemperature = false;

  private double constantSurfaceTemperature;

  private double heatTransferCoefficient;

  private String heatTransferCoefficientMethod = "Estimated";

  // Joule-Thomson effect: temperature change during gas expansion
  // When enabled, JT coefficient is calculated from gas phase thermodynamics
  private boolean includeJouleThomsonEffect = false;

  // Friction heating parameters for viscous dissipation
  // When enabled, friction pressure losses are converted to thermal energy in the fluid
  private boolean includeFrictionHeating = false;

  // Heat transfer parameters
  double Tmi; // medium temperature
  double Tmo; // outlet temperature
  double Ts; // wall temperature
  double error; // error in heat transfer
  double iterationT; // iteration in heat transfer
  double dTlm; // log mean temperature difference
  double cp; // heat capacity
  double q1; // heat transfer
  double q2;
  double ReNoSlip;
  double S = 0;
  double rhoNoSlip = 0;
  double muNoSlip = 0;
  double thermalConductivity;
  double Pr; // Prandtl number
  double frictionFactor;
  double frictionTwoPhase;
  double Nu;
  double criticalPressure;
  double hmax;
  double X;

  /**
   * Constructor for PipeBeggsAndBrills.
   *
   * @param name name of pipe
   */
  public PipeBeggsAndBrills(String name) {
    super(name);
  }

  /**
   * Constructor for PipeBeggsAndBrills.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public PipeBeggsAndBrills(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * <p>
   * Setter for the field <code>pipeSpecification</code>.
   * </p>
   *
   * @param nominalDiameter a double in inch
   * @param pipeSec a {@link java.lang.String} object
   */
  public void setPipeSpecification(double nominalDiameter, String pipeSec) {
    this.pipeSpecification = pipeSec;
    this.nominalDiameter = nominalDiameter;
    this.PipeSpecSet = true;

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet =
          database.getResultSet("SELECT * FROM pipedata where Size='" + nominalDiameter + "'");
      try {
        if (dataSet.next()) {
          this.pipeThickness = Double.parseDouble(dataSet.getString(pipeSpecification)) / 1000;
          this.insideDiameter =
              (Double.parseDouble(dataSet.getString("OD"))) / 1000 - 2 * this.pipeThickness;
        }
      } catch (NumberFormatException e) {
        logger.error(e.getMessage());
      } catch (SQLException e) {
        logger.error(e.getMessage());
      }
    } catch (SQLException e) {
      logger.error(e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return outStream.getThermoSystem();
  }

  /**
   * <p>
   * Setter for the field <code>elevation</code>.
   * </p>
   *
   * @param elevation a double
   */
  public void setElevation(double elevation) {
    this.totalElevation = elevation;
  }

  /**
   * <p>
   * Setter for the field <code>length</code>.
   * </p>
   *
   * @param length the length to set
   */
  public void setLength(double length) {
    this.totalLength = length;
  }

  /**
   * <p>
   * setDiameter.
   * </p>
   *
   * @param diameter the diameter to set
   */
  public void setDiameter(double diameter) {
    insideDiameter = diameter;
  }

  /**
   * <p>
   * setThickness.
   * </p>
   *
   * @param pipeThickness the thickness to set
   */
  public void setThickness(double pipeThickness) {
    this.pipeThickness = pipeThickness;
  }

  /**
   * <p>
   * getThickness.
   * </p>
   *
   * @return a double
   */
  public double getThickness() {
    return this.pipeThickness;
  }

  /**
   * <p>
   * Setter for the field <code>angle</code>.
   * </p>
   *
   * @param angle a double
   */
  public void setAngle(double angle) {
    this.angle = angle;
  }

  /**
   * <p>
   * Setter for the field <code>pipeWallRoughness</code>.
   * </p>
   *
   * @param pipeWallRoughness the pipeWallRoughness to set
   */
  public void setPipeWallRoughness(double pipeWallRoughness) {
    this.pipeWallRoughness = pipeWallRoughness;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfIncrements</code>.
   * </p>
   *
   * @param numberOfIncrements a int
   */
  public void setNumberOfIncrements(int numberOfIncrements) {
    this.numberOfIncrements = numberOfIncrements;
  }

  /**
   * <p>
   * Setter for the field <code>runIsothermal</code>.
   * </p>
   *
   * @param runIsothermal a boolean
   */
  public void setRunIsothermal(boolean runIsothermal) {
    this.runIsothermal = runIsothermal;
  }

  /**
   * <p>
   * Setter for the field <code>constantSurfaceTemperature</code>.
   * </p>
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   */
  public void setConstantSurfaceTemperature(double temperature, String unit) {
    if (unit.equals("K")) {
      this.constantSurfaceTemperature = temperature;
    } else if (unit.equals("C")) {
      this.constantSurfaceTemperature = temperature + 273.15;
    } else {
      throw new RuntimeException("unit not supported " + unit);
    }
    this.runIsothermal = false;
    this.runAdiabatic = false;
    this.runConstantSurfaceTemperature = true;
  }

  /**
   * <p>
   * Setter for the field <code>heatTransferCoefficient</code>.
   * </p>
   *
   * @param heatTransferCoefficient a double
   */
  public void setHeatTransferCoefficient(double heatTransferCoefficient) {
    this.heatTransferCoefficient = heatTransferCoefficient;
    this.heatTransferCoefficientMethod = "Defined";
  }

  /**
   * Sets the specified outlet pressure and switches to flow rate calculation mode. When outlet
   * pressure is specified, the run() method will iterate to find the flow rate that achieves the
   * specified outlet pressure.
   *
   * @param pressure the desired outlet pressure in bara
   */
  public void setOutletPressure(double pressure) {
    this.specifiedOutletPressure = pressure;
    this.specifiedOutletPressureUnit = "bara";
    this.calculationMode = CalculationMode.CALCULATE_FLOW_RATE;
  }

  /**
   * Sets the specified outlet pressure with unit and switches to flow rate calculation mode. When
   * outlet pressure is specified, the run() method will iterate to find the flow rate that achieves
   * the specified outlet pressure.
   *
   * @param pressure the desired outlet pressure
   * @param unit the pressure unit (e.g., "bara", "barg", "Pa", "MPa")
   */
  public void setOutletPressure(double pressure, String unit) {
    this.specifiedOutletPressure = pressure;
    this.specifiedOutletPressureUnit = unit;
    this.calculationMode = CalculationMode.CALCULATE_FLOW_RATE;
  }

  /**
   * Gets the specified outlet pressure.
   *
   * @return the specified outlet pressure in the unit set, or NaN if not specified
   */
  public double getSpecifiedOutletPressure() {
    return specifiedOutletPressure;
  }

  /**
   * Gets the specified outlet pressure unit.
   *
   * @return the pressure unit
   */
  public String getSpecifiedOutletPressureUnit() {
    return specifiedOutletPressureUnit;
  }

  /**
   * Sets the calculation mode for the pipeline.
   *
   * @param mode the calculation mode (CALCULATE_OUTLET_PRESSURE or CALCULATE_FLOW_RATE)
   */
  public void setCalculationMode(CalculationMode mode) {
    this.calculationMode = mode;
  }

  /**
   * Gets the current calculation mode.
   *
   * @return the calculation mode
   */
  public CalculationMode getCalculationMode() {
    return calculationMode;
  }

  /**
   * Sets the maximum number of iterations for flow rate calculation when outlet pressure is
   * specified.
   *
   * @param maxIterations the maximum number of iterations
   */
  public void setMaxFlowIterations(int maxIterations) {
    this.maxFlowIterations = maxIterations;
  }

  /**
   * Sets the convergence tolerance for flow rate calculation when outlet pressure is specified.
   *
   * @param tolerance the relative convergence tolerance (default 1e-4)
   */
  public void setFlowConvergenceTolerance(double tolerance) {
    this.flowConvergenceTolerance = tolerance;
  }

  /**
   * Converts the input values from the system measurement units to imperial units. Needed because
   * the main equations and coefficients are developed for imperial system
   * <p>
   * The conversions applied are:
   * </p>
   * <ul>
   * <li>Inside Diameter (m) - (feet): multiplied by 3.2808399</li>
   * <li>Angle (m) - (feet): multiplied by 0.01745329</li>
   * <li>Elevation (m) - (feet): multiplied by 3.2808399</li>
   * <li>Length (m) - (feet): multiplied by 3.2808399</li>
   * <li>Pipe Wall Roughness (m) - (feet): multiplied by 3.2808399</li>
   * </ul>
   */
  public void convertSystemUnitToImperial() {
    insideDiameter = insideDiameter * 3.2808399;
    angle = 0.01745329 * angle;
    elevation = elevation * 3.2808399;
    length = length * 3.2808399;
    pipeWallRoughness = pipeWallRoughness * 3.2808399;
  }

  /**
   * Converts the input values from imperial units to the system measurement units. Needed because
   * the main equations and coefficients are developed for imperial system
   * <p>
   * The conversions applied are the inverse of those in the {@link #convertSystemUnitToImperial()}
   * method:
   * </p>
   * <ul>
   * <li>Inside Diameter (ft - m): divided by 3.2808399</li>
   * <li>Angle (ft - m): divided by 0.01745329</li>
   * <li>Elevation (ft - m): divided by 3.2808399</li>
   * <li>Length (ft - m): divided by 3.2808399</li>
   * <li>Pipe Wall Roughness (ft - m): divided by 3.2808399</li>
   * <li>Pressure Drop (lb/inch) -(bar): multiplied by 1.48727E-05</li>
   * </ul>
   */
  public void convertSystemUnitToMetric() {
    insideDiameter = insideDiameter / 3.2808399;
    angle = angle / 0.01745329;
    elevation = elevation / 3.2808399;
    length = length / 3.2808399;
    pipeWallRoughness = pipeWallRoughness / 3.2808399;
    pressureDrop = pressureDrop * 1.48727E-05;
  }

  /**
   * <p>
   * calculateMissingValue.
   * </p>
   */
  public void calculateMissingValue() {
    if (Double.isNaN(totalLength)) {
      totalLength = calculateLength();
    } else if (Double.isNaN(totalElevation)) {
      totalElevation = calculateElevation();
    } else if (Double.isNaN(angle)) {
      angle = calculateAngle();
    }
    if (Math.abs(totalElevation) > Math.abs(totalLength)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "calcMissingValue",
              "elevation", "- cannot be higher than length of the pipe" + length));
    }
    if (Double.isNaN(totalElevation) || Double.isNaN(totalLength) || Double.isNaN(angle)
        || Double.isNaN(insideDiameter)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "calcMissingValue",
              "elevation or length or angle or inlet diameter", "cannot be null"));
    }
  }

  /**
   * Calculates the length based on the elevation and angle.
   *
   * @return the calculated length.
   */
  private double calculateLength() {
    return totalElevation / Math.sin(Math.toRadians(angle));
  }

  /**
   * Calculates the elevation based on the length and angle.
   *
   * @return the calculated elevation.
   */
  private double calculateElevation() {
    return totalLength * Math.sin(Math.toRadians(angle));
  }

  /**
   * Calculates the angle based on the length and elevation.
   *
   * @return the calculated angle.
   */
  private double calculateAngle() {
    return Math.toDegrees(Math.asin(totalElevation / totalLength));
  }

  /**
   * <p>
   * calcFlowRegime.
   * </p>
   *
   * @return the determined flow regime
   */
  public FlowRegime calcFlowRegime() {
    // Calc input volume fraction
    area = (Math.PI / 4.0) * Math.pow(insideDiameter, 2.0);
    if (system.getNumberOfPhases() != 1) {
      if (system.getNumberOfPhases() == 3) {
        supLiquidVel =
            (system.getPhase(1).getFlowRate("ft3/sec") + system.getPhase(2).getFlowRate("ft3/sec"))
                / area;
      } else {
        supLiquidVel = system.getPhase(1).getFlowRate("ft3/sec") / area;
      }

      supGasVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
      supMixVel = supLiquidVel + supGasVel;

      mixtureFroudeNumber = Math.pow(supMixVel, 2) / (32.174 * insideDiameter);
      inputVolumeFractionLiquid = supLiquidVel / supMixVel;
    } else {
      if (system.hasPhaseType("gas")) {
        supGasVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
        supMixVel = supGasVel;
        inputVolumeFractionLiquid = 0.0;
        regime = FlowRegime.SINGLE_PHASE;
      } else {
        // Single-phase liquid: only phase is at index 0
        supLiquidVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
        supMixVel = supLiquidVel;
        inputVolumeFractionLiquid = 1.0;
        regime = FlowRegime.SINGLE_PHASE;
      }
    }

    liquidSuperficialVelocityProfile.add(supLiquidVel / 3.2808399); // to meters
    gasSuperficialVelocityProfile.add(supGasVel / 3.2808399);
    mixtureSuperficialVelocityProfile.add(supMixVel / 3.2808399);

    double L1 = 316 * Math.pow(inputVolumeFractionLiquid, 0.302);
    double L2 = 0.0009252 * Math.pow(inputVolumeFractionLiquid, -2.4684);
    double L3 = 0.1 * Math.pow(inputVolumeFractionLiquid, -1.4516);
    double L4 = 0.5 * Math.pow(inputVolumeFractionLiquid, -6.738);

    if (regime != FlowRegime.SINGLE_PHASE) {
      if ((inputVolumeFractionLiquid < 0.01 && mixtureFroudeNumber < L1)
          || (inputVolumeFractionLiquid >= 0.01 && mixtureFroudeNumber < L2)) {
        regime = FlowRegime.SEGREGATED;
      } else if ((inputVolumeFractionLiquid < 0.4 && inputVolumeFractionLiquid >= 0.01
          && mixtureFroudeNumber <= L1 && mixtureFroudeNumber > L3)
          || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber <= L4
              && mixtureFroudeNumber > L3)) {
        regime = FlowRegime.INTERMITTENT;
      } else if ((inputVolumeFractionLiquid < 0.4 && mixtureFroudeNumber >= L4)
          || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber > L4)) {
        regime = FlowRegime.DISTRIBUTED;
      } else if (mixtureFroudeNumber > L2 && mixtureFroudeNumber < L3) {
        regime = FlowRegime.TRANSITION;
      } else if (inputVolumeFractionLiquid < 0.1 || inputVolumeFractionLiquid > 0.9) {
        regime = FlowRegime.INTERMITTENT;
      } else if (mixtureFroudeNumber > 110) {
        regime = FlowRegime.INTERMITTENT;
      } else {
        throw new RuntimeException(new neqsim.util.exception.InvalidOutputException(
            "PipeBeggsAndBrills", "run: calcFlowRegime", "FlowRegime", "Flow regime is not found"));
      }
    }

    A = (L3 - mixtureFroudeNumber) / (L3 - L2);

    flowRegimeProfile.add(regime);
    return regime;
  }

  /**
   * <p>
   * calcHydrostaticPressureDifference.
   * </p>
   *
   * @return a double
   */
  public double calcHydrostaticPressureDifference() {
    double B = 1 - A;

    double BThetta;

    if (regime == FlowRegime.SEGREGATED) {
      El = 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868);
    } else if (regime == FlowRegime.INTERMITTENT) {
      El = 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
          / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (regime == FlowRegime.DISTRIBUTED) {
      El = 1.065 * Math.pow(inputVolumeFractionLiquid, 0.5824)
          / (Math.pow(mixtureFroudeNumber, 0.0609));
    } else if (regime == FlowRegime.TRANSITION) {
      El = A * 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868)
          + B * 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
              / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (regime == FlowRegime.SINGLE_PHASE) {
      // For single-phase flow, liquid holdup equals liquid volume fraction
      // Gas: El = 0, Liquid: El = 1
      El = inputVolumeFractionLiquid;
    }

    if (regime != FlowRegime.SINGLE_PHASE) {
      double SG;
      if (system.getNumberOfPhases() == 3) {
        mixtureOilMassFraction = system.getPhase(1).getFlowRate("kg/hr")
            / (system.getPhase(1).getFlowRate("kg/hr") + system.getPhase(2).getFlowRate("kg/hr"));
        mixtureOilVolumeFraction = system.getPhase(1).getVolume()
            / (system.getPhase(1).getVolume() + system.getPhase(2).getVolume());

        mixtureLiquidViscosity = system.getPhase(1).getViscosity("cP") * mixtureOilVolumeFraction
            + (system.getPhase(2).getViscosity("cP")) * (1 - mixtureOilVolumeFraction);

        mixtureLiquidDensity = (system.getPhase(1).getDensity("lb/ft3") * mixtureOilMassFraction
            + system.getPhase(2).getDensity("lb/ft3") * (1 - mixtureOilMassFraction));

        SG = (mixtureLiquidDensity) / (1000 * 0.0624279606);
      } else {
        SG = system.getPhase(1).getDensity("lb/ft3") / (1000 * 0.0624279606);
      }

      double APIgrav = (141.5 / (SG)) - 131.0;
      double sigma68 = 39.0 - 0.2571 * APIgrav;
      double sigma100 = 37.5 - 0.2571 * APIgrav;
      double sigma;

      if (system.getTemperature("C") * (9.0 / 5.0) + 32.0 > 100.0) {
        sigma = sigma100;
      } else if (system.getTemperature("C") * (9.0 / 5.0) + 32.0 < 68.0) {
        sigma = sigma68;
      } else {
        sigma = sigma68 + (system.getTemperature("C") * (9.0 / 5.0) + 32.0 - 68.0)
            * (sigma100 - sigma68) / (100.0 - 68.0);
      }
      double pressureCorrection = 1.0 - 0.024 * Math.pow((system.getPressure("psi")), 0.45);
      sigma = sigma * pressureCorrection;
      double Nvl = 1.938 * supLiquidVel
          * Math.pow(system.getPhase(1).getDensity() * 0.0624279606 / (32.2 * sigma), 0.25);
      double betta = 0;

      if (elevation > 0) {
        if (regime == FlowRegime.SEGREGATED) {
          double logArg = 0.011 * Math.pow(Nvl, 3.539)
              / (Math.pow(inputVolumeFractionLiquid, 3.768) * Math.pow(mixtureFroudeNumber, 1.614));
          if (logArg > 0) {
            betta = (1 - inputVolumeFractionLiquid) * Math.log(logArg);
          }
        } else if (regime == FlowRegime.INTERMITTENT) {
          double logArg = 2.96 * Math.pow(inputVolumeFractionLiquid, 0.305)
              * Math.pow(mixtureFroudeNumber, 0.0978) / (Math.pow(Nvl, 0.4473));
          if (logArg > 0) {
            betta = (1 - inputVolumeFractionLiquid) * Math.log(logArg);
          }
        } else if (regime == FlowRegime.DISTRIBUTED) {
          betta = 0;
        }
      } else {
        double logArg = 4.70 * Math.pow(Nvl, 0.1244)
            / (Math.pow(inputVolumeFractionLiquid, 0.3692) * Math.pow(mixtureFroudeNumber, 0.5056));
        if (logArg > 0) {
          betta = (1 - inputVolumeFractionLiquid) * Math.log(logArg);
        }
      }
      betta = (betta > 0) ? betta : 0;
      BThetta = 1 + betta * (Math.sin(1.8 * angle * 0.01745329)
          - (1.0 / 3.0) * Math.pow(Math.sin(1.8 * angle * 0.01745329), 3.0));

      El = BThetta * El;
      if (system.getNumberOfPhases() == 3) {
        mixtureDensity =
            mixtureLiquidDensity * El + system.getPhase(0).getDensity("lb/ft3") * (1 - El);
      } else {
        mixtureDensity = system.getPhase(1).getDensity("lb/ft3") * El
            + system.getPhase(0).getDensity("lb/ft3") * (1 - El);
      }
    } else {
      // Single-phase: only phase is at index 0
      mixtureDensity = system.getPhase(0).getDensity("lb/ft3");
    }
    hydrostaticPressureDrop = mixtureDensity * 32.2 * elevation; // 32.2 - g

    liquidHoldupProfile.add(El);

    return hydrostaticPressureDrop;
  }

  /**
   * <p>
   * calcFrictionPressureLoss.
   * </p>
   *
   * @return a double
   */
  public double calcFrictionPressureLoss() {
    double S = 0;
    double rhoNoSlip = 0;
    double muNoSlip = 0;

    if (system.getNumberOfPhases() != 1) {
      if (regime != FlowRegime.SINGLE_PHASE) {
        double y = inputVolumeFractionLiquid / (Math.pow(El, 2));
        if (1 < y && y < 1.2) {
          S = Math.log(2.2 * y - 1.2);
        } else {
          S = Math.log(y) / (-0.0523 + 3.18 * Math.log(y) - 0.872 * Math.pow(Math.log(y), 2.0)
              + 0.01853 * Math.pow(Math.log(y), 4));
        }
        if (system.getNumberOfPhases() == 3) {
          rhoNoSlip = mixtureLiquidDensity * inputVolumeFractionLiquid
              + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
          muNoSlip = mixtureLiquidViscosity * inputVolumeFractionLiquid
              + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
          liquidDensityProfile.add(mixtureLiquidDensity * 16.01846);
        } else {
          rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3")) * inputVolumeFractionLiquid
              + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
          muNoSlip = system.getPhase(1).getViscosity("cP") * inputVolumeFractionLiquid
              + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
          liquidDensityProfile.add((system.getPhase(1).getDensity("lb/ft3")) * 16.01846);
        }
      } else {
        rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3")) * inputVolumeFractionLiquid
            + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
        muNoSlip = system.getPhase(1).getViscosity("cP") * inputVolumeFractionLiquid
            + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
        liquidDensityProfile.add((system.getPhase(1).getDensity("lb/ft3")) * 16.01846);
      }
    } else {
      // Single-phase: only phase is at index 0
      rhoNoSlip = (system.getPhase(0).getDensity("lb/ft3"));
      muNoSlip = (system.getPhase(0).getViscosity("cP"));
      if (system.hasPhaseType("gas")) {
        liquidDensityProfile.add(0.0);
      } else {
        liquidDensityProfile.add(rhoNoSlip * 16.01846);
      }
    }

    mixtureViscosityProfile.add(muNoSlip);
    mixtureDensityProfile.add(rhoNoSlip * 16.01846);

    ReNoSlip = rhoNoSlip * supMixVel * insideDiameter * (16 / (3.28 * 3.28)) / (0.001 * muNoSlip);

    mixtureReynoldsNumber.add(ReNoSlip);

    double E = pipeWallRoughness / insideDiameter;

    // Calculate friction factor with proper flow regime handling
    if (Math.abs(ReNoSlip) < 1e-10) {
      frictionFactor = 0.0;
    } else if (Math.abs(ReNoSlip) < 2300) {
      // Laminar flow
      frictionFactor = 64.0 / ReNoSlip;
    } else if (Math.abs(ReNoSlip) < 4000) {
      // Transition zone - interpolate between laminar and turbulent
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent =
          Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / 4000.0))), 2);
      frictionFactor = fLaminar + (fTurbulent - fLaminar) * (ReNoSlip - 2300.0) / 1700.0;
    } else {
      // Turbulent flow - Haaland equation
      // f = (1 / (-1.8 * log10((ε/D/3.7)^1.11 + 6.9/Re)))^2
      frictionFactor =
          Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / ReNoSlip))), 2);
    }
    frictionTwoPhase = frictionFactor * Math.exp(S);

    frictionPressureLoss =
        frictionTwoPhase * Math.pow(supMixVel, 2) * rhoNoSlip * (length) / (2 * insideDiameter);
    return frictionPressureLoss;
  }

  /**
   * <p>
   * calcPressureDrop.
   * </p>
   *
   * @return a double
   */
  public double calcPressureDrop() {
    convertSystemUnitToImperial();
    regime = FlowRegime.UNKNOWN;
    calcFlowRegime();
    hydrostaticPressureDrop = calcHydrostaticPressureDifference();
    frictionPressureLoss = calcFrictionPressureLoss();
    pressureDrop = (hydrostaticPressureDrop + frictionPressureLoss);
    convertSystemUnitToMetric();
    iteration = iteration + 1;
    return pressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Input validation
    if (insideDiameter <= 0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "insideDiameter", "must be positive, got: " + insideDiameter));
    }
    if (numberOfIncrements <= 0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "numberOfIncrements", "must be positive, got: " + numberOfIncrements));
    }

    if (calculationMode == CalculationMode.CALCULATE_FLOW_RATE) {
      runWithSpecifiedOutletPressure(id);
    } else {
      runWithSpecifiedFlowRate(id);
    }
  }

  /**
   * Run pipeline calculation with specified flow rate (calculate outlet pressure). This is the
   * default calculation mode.
   *
   * @param id calculation identifier
   */
  private void runWithSpecifiedFlowRate(UUID id) {
    iteration = 0;
    transientInitialized = false;

    pressureProfile = new ArrayList<>();
    temperatureProfile = new ArrayList<>();

    pressureDropProfile = new ArrayList<>();
    flowRegimeProfile = new ArrayList<>();

    liquidSuperficialVelocityProfile = new ArrayList<>();
    gasSuperficialVelocityProfile = new ArrayList<>();
    mixtureSuperficialVelocityProfile = new ArrayList<>();

    mixtureViscosityProfile = new ArrayList<>();
    mixtureDensityProfile = new ArrayList<>();
    liquidDensityProfile = new ArrayList<>();
    liquidHoldupProfile = new ArrayList<>();
    mixtureReynoldsNumber = new ArrayList<>();

    lengthProfile = new ArrayList<>();
    elevationProfile = new ArrayList<>();
    incrementsProfile = new ArrayList<>();

    calculateMissingValue();
    double enthalpyInlet = Double.NaN;
    length = totalLength / numberOfIncrements;
    elevation = totalElevation / numberOfIncrements;
    system = inStream.getThermoSystem().clone();
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    system.initProperties();

    if (!runIsothermal) {
      enthalpyInlet = system.getEnthalpy();
    }
    double pipeInletPressure = system.getPressure();
    cumulativeLength = 0.0;
    cumulativeElevation = 0.0;
    pressureProfile.add(system.getPressure()); // pressure at segment 0
    temperatureProfile.add(system.getTemperature()); // temperature at segment 0
    pressureDropProfile.add(0.0); // DP at segment 0
    for (int i = 1; i <= numberOfIncrements; i++) {
      lengthProfile.add(cumulativeLength);
      elevationProfile.add(cumulativeElevation);
      incrementsProfile.add(i - 1);

      cumulativeLength += length;
      cumulativeElevation += elevation;

      inletPressure = system.getPressure();
      pressureDrop = calcPressureDrop();
      pressureDropProfile.add(pressureDrop);
      pressureOut = inletPressure - pressureDrop;
      pressureProfile.add(pressureOut);

      if (pressureOut < 0) {
        throw new RuntimeException(new neqsim.util.exception.InvalidOutputException(
            "PipeBeggsAndBrills", "run: calcOutletPressure", "pressure out",
            "- Outlet pressure is negative" + pressureOut));
      }

      system.setPressure(pressureOut);
      if (!runIsothermal) {
        enthalpyInlet = calcHeatBalance(enthalpyInlet, system, testOps);
        // testOps.PHflash(enthalpyInlet);
        temperatureProfile.add(system.getTemperature());
      } else {
        testOps.TPflash();
      }
      system.initProperties();
    }
    totalPressureDrop = pipeInletPressure - system.getPressure();
    calcPressureDrop(); // to initialize final parameters
    lengthProfile.add(cumulativeLength);
    elevationProfile.add(cumulativeElevation);
    incrementsProfile.add(getNumberOfIncrements());
    system.initProperties();
    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
  }

  /**
   * Run pipeline calculation with specified outlet pressure (calculate flow rate). Uses bisection
   * method to find the flow rate that achieves the target outlet pressure.
   *
   * @param id calculation identifier
   */
  private void runWithSpecifiedOutletPressure(UUID id) {
    if (Double.isNaN(specifiedOutletPressure)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "specifiedOutletPressure", "must be set when using CALCULATE_FLOW_RATE mode"));
    }

    // Convert specified outlet pressure to bara
    double targetPressure = specifiedOutletPressure;
    if (!specifiedOutletPressureUnit.equals("bara")) {
      // Create a temporary system to convert pressure units
      SystemInterface tempSystem = inStream.getThermoSystem().clone();
      tempSystem.setPressure(specifiedOutletPressure, specifiedOutletPressureUnit);
      targetPressure = tempSystem.getPressure("bara");
    }

    double inletPressureBara = inStream.getThermoSystem().getPressure("bara");
    if (targetPressure >= inletPressureBara) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeBeggsAndBrills", "run", "specifiedOutletPressure",
          "must be less than inlet pressure (" + inletPressureBara + " bara)"));
    }
    if (targetPressure <= 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeBeggsAndBrills", "run", "specifiedOutletPressure", "must be positive"));
    }

    // Save original flow rate
    String flowUnit = "kg/hr";
    double originalFlowRate = inStream.getFlowRate(flowUnit);

    // Use bisection method to find flow rate
    // Start with a wide range
    double flowLow = 1.0; // Minimum 1 kg/hr
    double flowHigh = originalFlowRate * 100.0; // Up to 100x original

    // First, find a valid low flow rate (where outlet pressure > target)
    double pressureAtLowFlow = tryCalculatePressure(flowLow, flowUnit, id);
    if (pressureAtLowFlow < targetPressure) {
      // Even at minimum flow, can't achieve target pressure
      inStream.setFlowRate(originalFlowRate, flowUnit);
      inStream.run();
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeBeggsAndBrills", "run", "specifiedOutletPressure",
          "cannot be achieved - pressure drop too high even at minimum flow"));
    }

    // Find a valid high flow rate (where outlet pressure < target)
    // Start from a reasonable multiple and increase if needed
    flowHigh = originalFlowRate * 2.0;
    double pressureAtHighFlow = tryCalculatePressure(flowHigh, flowUnit, id);

    // If pressure is still too high, increase flow rate
    int boundSearchIter = 0;
    while (pressureAtHighFlow > targetPressure && boundSearchIter < 20) {
      flowHigh *= 2.0;
      pressureAtHighFlow = tryCalculatePressure(flowHigh, flowUnit, id);
      boundSearchIter++;
    }

    // If we couldn't find a high bound with positive pressure that gives low enough outlet pressure
    // it means we need very high flow (or it's infeasible)
    if (pressureAtHighFlow > targetPressure) {
      inStream.setFlowRate(originalFlowRate, flowUnit);
      inStream.run();
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "specifiedOutletPressure", "cannot be achieved - requires extremely high flow rate"));
    }

    // Bisection iteration
    double flowMid = 0;
    double pressureMid = 0;
    int iterCount = 0;

    while (iterCount < maxFlowIterations) {
      flowMid = (flowLow + flowHigh) / 2.0;
      pressureMid = tryCalculatePressure(flowMid, flowUnit, id);

      double relativeError = Math.abs(pressureMid - targetPressure) / targetPressure;

      if (relativeError < flowConvergenceTolerance) {
        // Converged
        break;
      }

      if (pressureMid > targetPressure) {
        // Need more pressure drop, increase flow
        flowLow = flowMid;
      } else {
        // Need less pressure drop, decrease flow
        flowHigh = flowMid;
      }

      // Check if bounds have converged
      if (Math.abs(flowHigh - flowLow) / flowMid < flowConvergenceTolerance) {
        break;
      }

      iterCount++;
    }

    // Final run with converged flow rate - already done in tryCalculatePressure
    // Just ensure the state is set correctly
    inStream.setFlowRate(flowMid, flowUnit);
    inStream.run();
    runWithSpecifiedFlowRate(id);
  }

  /**
   * Helper method to calculate outlet pressure for a given flow rate, handling exceptions when
   * pressure goes negative (indicating flow rate is too high).
   *
   * @param flowRate the flow rate to test
   * @param flowUnit the unit for flow rate
   * @param id calculation identifier
   * @return the outlet pressure, or a very low value if calculation fails (pressure went negative)
   */
  private double tryCalculatePressure(double flowRate, String flowUnit, UUID id) {
    inStream.setFlowRate(flowRate, flowUnit);
    inStream.run();
    try {
      runWithSpecifiedFlowRate(id);
      return getOutletPressure();
    } catch (RuntimeException e) {
      // If calculation fails (e.g., negative pressure), return very low pressure
      // This helps the bisection algorithm know this flow rate is too high
      return -1e6; // Return a very negative value to indicate "too high flow"
    }
  }

  /**
   * Calculates the Nusselt number using the Gnielinski correlation for turbulent pipe flow. Valid
   * for 0.5 &lt; Pr &lt; 2000 and 3000 &lt; Re &lt; 5E6.
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return the Nusselt number
   */
  private double calcGnielinskiNu(double Re, double Pr) {
    // Gnielinski correlation: Nu = (f/8)(Re-1000)Pr / (1 + 12.7*(f/8)^0.5*(Pr^(2/3)-1))
    // Uses the friction factor already calculated
    double f = frictionFactor;
    if (f <= 0) {
      // Fallback: use Petukhov friction factor approximation
      f = Math.pow(0.790 * Math.log(Re) - 1.64, -2);
    }
    return ((f / 8.0) * (Re - 1000.0) * Pr)
        / (1.0 + 12.7 * Math.pow(f / 8.0, 0.5) * (Math.pow(Pr, 2.0 / 3.0) - 1.0));
  }

  /**
   * Estimates the heat transfer coefficient for the given system.
   *
   * @param system the thermodynamic system for which the heat transfer coefficient is to be
   *        estimated
   * @return the estimated heat transfer coefficient
   */
  public double estimateHeatTransferCoefficent(SystemInterface system) {
    cp = system.getCp("J/kgK");
    thermalConductivity = system.getThermalConductivity();
    // Prandtl number: Pr = μ * Cp / k
    // viscosity in cP * 0.001 converts to Pa.s (kg/(m.s))
    Pr = 0.001 * system.getViscosity("cP") * cp / thermalConductivity;

    if (ReNoSlip < 2300) {
      // Laminar flow - constant Nusselt for fully developed pipe flow
      Nu = 3.66;
    } else if (ReNoSlip < 3000) {
      // Transition zone - interpolate between laminar and turbulent
      double NuLaminar = 3.66;
      double NuTurbulent = calcGnielinskiNu(3000, Pr);
      Nu = NuLaminar + (NuTurbulent - NuLaminar) * (ReNoSlip - 2300) / 700.0;
    } else {
      // Turbulent flow - Gnielinski correlation (valid for 0.5 < Pr < 2000, 3000 < Re < 5E6)
      Nu = calcGnielinskiNu(ReNoSlip, Pr);
    }
    heatTransferCoefficient = Nu * thermalConductivity / insideDiameter;

    if (system.getNumberOfPhases() > 1) {
      X = system.getPhase(0).getFlowRate("kg/sec") / system.getFlowRate("kg/sec");
      heatTransferCoefficient =
          (-31.469 * Math.pow(X, 2) + 31.469 * X + 0.007) * heatTransferCoefficient;
      // double Xtt =
      // (Math.pow(((1-X)/X),0.9)*Math.pow((system.getPhase(0).getDensity()/mixtureLiquidDensity),
      // 0.5)*
      // Math.pow(mixtureLiquidViscosity/(0.001*system.getPhase(0).getViscosity()), 0.1));
      // double E = 0.8897*(1/Xtt) + 1.0392;
      // double Retp = ReNoSlip*Math.pow(E, 1.25);
      // double S;
      // if (Retp < 1E5){
      // S = 0.9;
      // }
      // else if(Retp <= 1E6){
      // S = -5.6*1E-7*Retp + 0.9405;
      // }
      // else if(Retp < 1E7)
      // S = 1368.9*Math.pow(Retp, -0.601);
      // else{
      // S = 0.1;
      // }

      // double reducedPressure = system.getPressure()*100/criticalPressure;
      // double Fp = 1.8*Math.pow(reducedPressure, 0.17) + 4*Math.pow(reducedPressure, 1.2) +
      // 10*Math.pow(reducedPressure, 10);
      // double hb = 0.00417*Math.pow(criticalPressure, 0.69)*Math.pow(heatFlux/100, 0.7)*Fp;
      // heatTransferCoefficient = E*heatTransferCoefficient + S*hb;
    }

    return heatTransferCoefficient;
  }

  /**
   * Calculates the temperature difference between the outlet and inlet of the system.
   *
   * @param system the thermodynamic system for which the temperature difference is to be calculated
   * @return the temperature difference between the outlet and inlet
   */
  public double calcTemperatureDifference(SystemInterface system) {
    double cpLocal = system.getCp("J/kgK");
    double Tmi = system.getTemperature("C");
    double Ts = constantSurfaceTemperature - 273.15;

    // Handle case where surface temperature equals inlet temperature (no heat transfer)
    if (Math.abs(Ts - Tmi) < 0.01) {
      return 0.0;
    }

    // Set bounds correctly for both heating and cooling cases
    double TmoLower, TmoUpper;
    if (Ts > Tmi) {
      // Heating case: outlet temperature between inlet and surface
      TmoLower = Tmi;
      TmoUpper = Ts;
    } else {
      // Cooling case: outlet temperature between surface and inlet
      TmoLower = Ts;
      TmoUpper = Tmi;
    }

    double Tmo = (TmoLower + TmoUpper) / 2; // Initial guess
    double error = 999;
    double tolerance = 0.01; // Tolerance for convergence
    int maxIterations = 100; // Maximum number of iterations

    if (heatTransferCoefficientMethod.equals("Estimated")) {
      heatTransferCoefficient = estimateHeatTransferCoefficent(system);
    }

    // Protect against zero or negative heat transfer coefficient
    if (heatTransferCoefficient <= 0) {
      return 0.0;
    }

    for (int i = 0; i < maxIterations; i++) {
      // Log mean temperature difference (LMTD)
      // dTlm = ((Ts-Tmo) - (Ts-Tmi)) / ln((Ts-Tmo)/(Ts-Tmi))
      // Protect against log of negative or zero
      double dT1 = Ts - Tmi;
      double dT2 = Ts - Tmo;

      // Check for singularity conditions
      if (Math.abs(dT2) < 0.001) {
        // Tmo very close to Ts - reached maximum heat transfer
        break;
      }
      if (dT1 * dT2 <= 0) {
        // Signs differ - Tmo has crossed Ts, which shouldn't happen
        break;
      }

      double dTlm;
      if (Math.abs(dT1 - dT2) < 0.001) {
        // When dT1 ≈ dT2, use arithmetic mean to avoid 0/0
        dTlm = (dT1 + dT2) / 2.0;
      } else {
        dTlm = (dT1 - dT2) / Math.log(dT1 / dT2);
      }

      // Original formulation: find Tmo where h_given = h_calculated
      // h_calculated = m_dot * Cp * (Tmo - Tmi) / (A * dTlm)
      double heatTransferArea = Math.PI * insideDiameter * length * dTlm;
      error = heatTransferCoefficient
          - system.getFlowRate("kg/sec") * cpLocal * (Tmo - Tmi) / heatTransferArea;

      if (Math.abs(error) < tolerance) {
        break; // Converged
      }

      // Bisection update
      // error > 0 means h_given > h_calc, need more heat transfer (Tmo closer to Ts)
      // error < 0 means h_given < h_calc, need less heat transfer (Tmo closer to Tmi)
      if (error > 0) {
        TmoLower = Tmo; // Need higher Tmo (for heating case)
      } else {
        TmoUpper = Tmo; // Need lower Tmo (for heating case)
      }

      Tmo = (TmoLower + TmoUpper) / 2; // New guess
    }
    return Tmo - Tmi;
  }

  /**
   * Calculates the heat balance for the given system.
   *
   * <p>
   * This method calculates the enthalpy change due to:
   * <ul>
   * <li>Wall heat transfer (LMTD method) - when not adiabatic</li>
   * <li>Joule-Thomson effect - cooling/heating due to pressure change (calculated from
   * thermodynamics)</li>
   * <li>Friction heating - viscous dissipation</li>
   * </ul>
   *
   * <p>
   * The final PHflash operation determines the equilibrium state at the new enthalpy and pressure,
   * which inherently accounts for heat of vaporization/condensation in two-phase flow. Phase
   * changes (liquid evaporation or vapor condensation) are properly handled through the enthalpy
   * balance.
   * </p>
   *
   * @param enthalpy the initial enthalpy of the system
   * @param system the thermodynamic system for which the heat balance is to be calculated
   * @param testOps the thermodynamic operations to be performed
   * @return the calculated enthalpy after performing the heat balance
   */
  public double calcHeatBalance(double enthalpy, SystemInterface system,
      ThermodynamicOperations testOps) {
    double Cp = system.getCp("J/kgK");
    double massFlowRate = system.getFlowRate("kg/sec");

    // 1. Wall heat transfer (LMTD-based calculation)
    if (!runAdiabatic) {
      enthalpy = enthalpy + massFlowRate * Cp * calcTemperatureDifference(system);
    }

    // 2. Joule-Thomson effect: temperature change due to pressure drop
    // JT coefficient is calculated from gas phase thermodynamics
    // dH_JT = m_dot * Cp * μ_JT * dP (where dP is pressure drop, positive value)
    if (includeJouleThomsonEffect && system.hasPhaseType("gas")) {
      try {
        double jouleThomsonCoeff = system.getPhase("gas").getJouleThomsonCoefficient("K/Pa");
        if (!Double.isNaN(jouleThomsonCoeff) && !Double.isInfinite(jouleThomsonCoeff)
            && jouleThomsonCoeff > 0) {
          double pressureDropPa = pressureDrop * 1e5; // bar to Pa
          double dT_JT = -jouleThomsonCoeff * pressureDropPa; // Cooling for expansion
          enthalpy = enthalpy + massFlowRate * Cp * dT_JT;
        }
      } catch (Exception ex) {
        // Skip JT effect if calculation fails
      }
    }

    // 3. Friction heating: viscous dissipation adds energy to the fluid
    // Q_friction = dP_friction * volumetric_flow_rate
    if (includeFrictionHeating) {
      double frictionPressureDropPa = Math.abs(pressureDrop) * 1e5; // bar to Pa
      double volumetricFlowRate = massFlowRate / system.getDensity("kg/m3");
      double frictionHeat = frictionPressureDropPa * volumetricFlowRate;
      enthalpy = enthalpy + frictionHeat;
    }

    // PHflash finds equilibrium at new enthalpy - this inherently handles
    // heat of vaporization/condensation for two-phase flow
    testOps.PHflash(enthalpy);
    return enthalpy;
  }

  /**
   * Sets whether to include Joule-Thomson effect in energy calculations.
   *
   * <p>
   * The Joule-Thomson effect accounts for temperature change during gas expansion. For natural gas,
   * this typically results in cooling during pressure drop. The JT coefficient is automatically
   * calculated from the gas phase thermodynamics using NeqSim's rigorous equation of state,
   * providing accurate values for the actual fluid composition and conditions.
   * </p>
   *
   * <p>
   * Typical Joule-Thomson coefficients (calculated automatically):
   * <ul>
   * <li>Methane: ~4×10⁻⁶ K/Pa (0.4 K/bar)</li>
   * <li>Natural gas: 3-5×10⁻⁶ K/Pa</li>
   * <li>CO2: ~10⁻⁵ K/Pa (1 K/bar)</li>
   * </ul>
   *
   * @param include true to include JT effect, false otherwise
   */
  public void setIncludeJouleThomsonEffect(boolean include) {
    this.includeJouleThomsonEffect = include;
  }

  /**
   * Gets whether Joule-Thomson effect is included in energy calculations.
   *
   * <p>
   * When enabled, the energy equation accounts for temperature change due to gas expansion,
   * typically resulting in cooling for natural gas flows. The JT coefficient is automatically
   * calculated from the gas phase thermodynamics.
   * </p>
   *
   * @return true if JT effect is included in the energy balance
   * @see #setIncludeJouleThomsonEffect(boolean)
   */
  public boolean isIncludeJouleThomsonEffect() {
    return includeJouleThomsonEffect;
  }

  /**
   * Sets whether to include friction heating in energy calculations.
   *
   * <p>
   * Friction heating accounts for viscous dissipation, where mechanical energy lost to friction is
   * converted to thermal energy in the fluid. The heat added is calculated as: Q_friction =
   * ΔP_friction × Q_volumetric
   * </p>
   *
   * <p>
   * For typical pipeline conditions, friction heating is a small effect (typically 0.01-0.1 K per
   * bar of friction pressure drop) compared to wall heat transfer or Joule-Thomson cooling.
   * However, for high-velocity or long pipelines, it may become significant.
   * </p>
   *
   * @param include true to include friction heating, false otherwise
   */
  public void setIncludeFrictionHeating(boolean include) {
    this.includeFrictionHeating = include;
  }

  /**
   * Gets whether friction heating is included in energy calculations.
   *
   * <p>
   * When enabled, the energy equation accounts for viscous dissipation, where friction pressure
   * losses are converted to thermal energy in the fluid.
   * </p>
   *
   * @return true if friction heating is included in the energy balance
   * @see #setIncludeFrictionHeating(boolean)
   */
  public boolean isIncludeFrictionHeating() {
    return includeFrictionHeating;
  }

  private void initializeTransientState(UUID id) {
    run(id);

    transientPressureProfile = new ArrayList<>(pressureProfile);
    transientTemperatureProfile = new ArrayList<>(temperatureProfile);

    if (transientTemperatureProfile.size() < numberOfIncrements + 1) {
      double fallbackTemperature;
      if (!transientTemperatureProfile.isEmpty()) {
        fallbackTemperature =
            transientTemperatureProfile.get(transientTemperatureProfile.size() - 1);
      } else {
        fallbackTemperature = getInletStream().getThermoSystem().getTemperature();
      }
      while (transientTemperatureProfile.size() < numberOfIncrements + 1) {
        transientTemperatureProfile.add(fallbackTemperature);
      }
    }
    transientMassFlowProfile = new ArrayList<>();
    transientVelocityProfile = new ArrayList<>();
    transientDensityProfile = new ArrayList<>();

    crossSectionArea = (Math.PI / 4.0) * Math.pow(insideDiameter, 2.0);
    segmentLengthMeters = totalLength / Math.max(1, numberOfIncrements);

    SystemInterface inlet = getInletStream().getThermoSystem().clone();
    inlet.initProperties();
    double steadyFlow = inlet.getFlowRate("kg/sec");
    double steadyDensity = Math.max(MIN_DENSITY, inlet.getDensity("kg/m3"));
    double baseVelocity = steadyFlow / (steadyDensity * crossSectionArea);
    baseVelocity = Math.max(MIN_TRANSIT_VELOCITY, baseVelocity);

    for (int i = 0; i < transientPressureProfile.size(); i++) {
      transientMassFlowProfile.add(steadyFlow);
    }

    if (mixtureSuperficialVelocityProfile != null && !mixtureSuperficialVelocityProfile.isEmpty()) {
      for (int i = 0; i < numberOfIncrements; i++) {
        double velocityFeetPerSecond = mixtureSuperficialVelocityProfile
            .get(Math.min(i, mixtureSuperficialVelocityProfile.size() - 1));
        transientVelocityProfile
            .add(Math.max(MIN_TRANSIT_VELOCITY, velocityFeetPerSecond * 0.3048));
      }
    } else {
      for (int i = 0; i < numberOfIncrements; i++) {
        transientVelocityProfile.add(baseVelocity);
      }
    }

    if (mixtureDensityProfile != null && !mixtureDensityProfile.isEmpty()) {
      for (int i = 0; i < numberOfIncrements; i++) {
        transientDensityProfile.add(Math.max(MIN_DENSITY,
            mixtureDensityProfile.get(Math.min(i, mixtureDensityProfile.size() - 1))));
      }
    } else {
      for (int i = 0; i < numberOfIncrements; i++) {
        transientDensityProfile.add(Math.max(MIN_DENSITY, steadyDensity));
      }
    }

    transientInitialized = true;
  }

  private void ensureTransientState(UUID id) {
    if (!transientInitialized || transientPressureProfile == null
        || transientPressureProfile.size() != numberOfIncrements + 1
        || transientTemperatureProfile == null
        || transientTemperatureProfile.size() != numberOfIncrements + 1
        || transientMassFlowProfile == null
        || transientMassFlowProfile.size() != numberOfIncrements + 1
        || transientVelocityProfile == null || transientVelocityProfile.size() != numberOfIncrements
        || transientDensityProfile == null
        || transientDensityProfile.size() != numberOfIncrements) {
      initializeTransientState(id);
      return;
    }
    if (Double.isNaN(crossSectionArea)) {
      crossSectionArea = (Math.PI / 4.0) * Math.pow(insideDiameter, 2.0);
    }
    if (Double.isNaN(segmentLengthMeters) || segmentLengthMeters <= 0) {
      segmentLengthMeters = totalLength / Math.max(1, numberOfIncrements);
    }
  }

  /**
   * Calculates friction pressure drop for transient simulation. Uses simplified correlations that
   * don't depend on steady-state flow regime detection.
   *
   * @param velocity mixture velocity in m/s
   * @param density mixture density in kg/m3
   * @param viscosity mixture viscosity in Pa.s (not cP)
   * @param segmentLength length of segment in m
   * @return friction pressure drop in bar
   */
  private double calcTransientFrictionPressureDrop(double velocity, double density,
      double viscosity, double segmentLength) {
    if (velocity < MIN_TRANSIT_VELOCITY || density < MIN_DENSITY || viscosity <= 0) {
      return 0.0;
    }

    // Calculate Reynolds number
    double Re = density * Math.abs(velocity) * insideDiameter / viscosity;

    // Calculate friction factor
    double f;
    double E = pipeWallRoughness / insideDiameter;

    if (Re < 1e-10) {
      f = 0.0;
    } else if (Re < 2300) {
      // Laminar flow
      f = 64.0 / Re;
    } else if (Re < 4000) {
      // Transition zone
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent =
          Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / 4000.0))), 2);
      f = fLaminar + (fTurbulent - fLaminar) * (Re - 2300.0) / 1700.0;
    } else {
      // Turbulent flow - Haaland equation
      f = Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / Re))), 2);
    }

    // Darcy-Weisbach: ΔP = f * (L/D) * (ρv²/2)
    // Result in Pa, convert to bar
    double dpFrictionPa =
        f * (segmentLength / insideDiameter) * (density * velocity * velocity / 2.0);
    return dpFrictionPa / 1e5; // Convert Pa to bar
  }

  /**
   * Calculates hydrostatic pressure drop for transient simulation.
   *
   * @param density mixture density in kg/m3
   * @param elevationChange elevation change in m (positive = uphill)
   * @return hydrostatic pressure drop in bar
   */
  private double calcTransientHydrostaticPressureDrop(double density, double elevationChange) {
    // ΔP_hydro = ρ * g * Δh
    // Result in Pa, convert to bar
    double dpHydroPa = density * 9.81 * elevationChange;
    return dpHydroPa / 1e5; // Convert Pa to bar
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {

    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }
    ensureTransientState(id);

    SystemInterface inletSystem = getInletStream().getThermoSystem().clone();
    inletSystem.initProperties();

    double inletPressureBoundary = inletSystem.getPressure();
    double inletTemperatureBoundary = inletSystem.getTemperature();
    double inletMassFlowBoundary = inletSystem.getFlowRate("kg/sec");
    double inletDensityBoundary = Math.max(MIN_DENSITY, inletSystem.getDensity("kg/m3"));
    double inletViscosityBoundary = inletSystem.getViscosity("kg/msec"); // Pa.s

    double inletVelocity = inletMassFlowBoundary / (inletDensityBoundary * crossSectionArea);
    inletVelocity = Math.max(MIN_TRANSIT_VELOCITY, inletVelocity);

    // Segment elevation change (same for all segments)
    double segmentElevation = totalElevation / Math.max(1, numberOfIncrements);

    List<Double> updatedPressure = new ArrayList<>(transientPressureProfile);
    List<Double> updatedTemperature = new ArrayList<>(transientTemperatureProfile);
    List<Double> updatedMassFlow = new ArrayList<>(transientMassFlowProfile);
    List<Double> updatedVelocity = new ArrayList<>(transientVelocityProfile);
    List<Double> updatedDensity = new ArrayList<>(transientDensityProfile);

    // Store viscosity for friction calculations (estimate from density ratio)
    List<Double> segmentViscosities = new ArrayList<>();
    for (int i = 0; i < numberOfIncrements; i++) {
      // Simple viscosity estimate - scale with density ratio from inlet
      double densityRatio = transientDensityProfile.get(i) / inletDensityBoundary;
      segmentViscosities.add(inletViscosityBoundary * Math.pow(densityRatio, 0.5));
    }

    updatedPressure.set(0, inletPressureBoundary);
    updatedTemperature.set(0, inletTemperatureBoundary);
    updatedMassFlow.set(0, inletMassFlowBoundary);

    for (int segment = 0; segment < numberOfIncrements; segment++) {
      // Get upstream values - use already-updated values for processed segments
      double upstreamPressure = updatedPressure.get(segment);
      double upstreamTemperature = updatedTemperature.get(segment);
      double upstreamMassFlow = updatedMassFlow.get(segment);

      // Get current downstream values from previous state
      double downstreamPressure = transientPressureProfile.get(segment + 1);
      double downstreamTemperature = transientTemperatureProfile.get(segment + 1);
      double downstreamMassFlow = transientMassFlowProfile.get(segment + 1);

      // Get segment properties
      double segmentDensity = transientDensityProfile.get(segment);
      double segmentVelocity = transientVelocityProfile.get(segment);
      double segmentViscosity = segmentViscosities.get(segment);
      segmentVelocity = Math.max(MIN_TRANSIT_VELOCITY, segmentVelocity);

      // Calculate transit time and relaxation factor
      double tau = segmentLengthMeters / segmentVelocity;
      double relaxation = tau > 0.0 ? Math.min(1.0, dt / tau) : 1.0;

      // Calculate pressure losses for this segment
      double dpFriction = calcTransientFrictionPressureDrop(segmentVelocity, segmentDensity,
          segmentViscosity, segmentLengthMeters);
      double dpHydrostatic = calcTransientHydrostaticPressureDrop(segmentDensity, segmentElevation);
      double totalSegmentDp = dpFriction + dpHydrostatic;

      // Wave transport with pressure losses:
      // The advected pressure is reduced by friction and hydrostatic losses
      double advectedPressure = upstreamPressure - totalSegmentDp;

      // Apply relaxation for wave propagation
      double newDownstreamPressure =
          downstreamPressure + relaxation * (advectedPressure - downstreamPressure);

      // Ensure pressure doesn't go negative
      newDownstreamPressure = Math.max(0.1, newDownstreamPressure);
      updatedPressure.set(segment + 1, newDownstreamPressure);

      // Temperature propagation (advective transport, no heat losses in simplified model)
      updatedTemperature.set(segment + 1,
          downstreamTemperature + relaxation * (upstreamTemperature - downstreamTemperature));

      // Mass flow propagation - with mass conservation enforcement
      // For incompressible/weakly compressible flow, mass flow should be continuous
      double newMassFlow =
          downstreamMassFlow + relaxation * (upstreamMassFlow - downstreamMassFlow);
      updatedMassFlow.set(segment + 1, newMassFlow);

      // Update velocity based on updated mass flow and density
      double targetVelocity =
          newMassFlow / (Math.max(MIN_DENSITY, segmentDensity) * crossSectionArea);
      double relaxedVelocity = segmentVelocity + relaxation * (targetVelocity - segmentVelocity);
      updatedVelocity.set(segment, Math.max(MIN_TRANSIT_VELOCITY, relaxedVelocity));

      // Update density - use already-updated upstream density for consistency
      double upstreamDensity;
      if (segment == 0) {
        upstreamDensity = inletDensityBoundary;
      } else {
        upstreamDensity = updatedDensity.get(segment - 1); // Use already-updated value
      }
      double relaxedDensity = segmentDensity + relaxation * (upstreamDensity - segmentDensity);
      updatedDensity.set(segment, Math.max(MIN_DENSITY, relaxedDensity));
    }

    // Update transient profiles
    transientPressureProfile = updatedPressure;
    transientTemperatureProfile = updatedTemperature;
    transientMassFlowProfile = updatedMassFlow;
    transientVelocityProfile = updatedVelocity;
    transientDensityProfile = updatedDensity;

    // Update steady-state profiles for output
    pressureProfile = new ArrayList<>(transientPressureProfile);
    temperatureProfile = new ArrayList<>(transientTemperatureProfile);

    pressureDropProfile = new ArrayList<>();
    pressureDropProfile.add(0.0);
    for (int i = 0; i < numberOfIncrements; i++) {
      pressureDropProfile
          .add(transientPressureProfile.get(i) - transientPressureProfile.get(i + 1));
    }

    mixtureSuperficialVelocityProfile = new ArrayList<>();
    for (int i = 0; i < numberOfIncrements; i++) {
      mixtureSuperficialVelocityProfile.add(transientVelocityProfile.get(i) / 0.3048);
    }

    mixtureDensityProfile = new ArrayList<>(transientDensityProfile);

    double outletPressure = transientPressureProfile.get(transientPressureProfile.size() - 1);
    double outletTemperature =
        transientTemperatureProfile.get(transientTemperatureProfile.size() - 1);
    double outletMassFlow = transientMassFlowProfile.get(transientMassFlowProfile.size() - 1);

    SystemInterface outletSystem = system;
    if (outletSystem == null) {
      outletSystem = inletSystem.clone();
    } else {
      outletSystem = outletSystem.clone();
    }
    outletSystem.setPressure(outletPressure);
    outletSystem.setTemperature(outletTemperature);
    outletSystem.setTotalFlowRate(outletMassFlow, "kg/sec");
    outletSystem.setMolarComposition(inletSystem.getMolarComposition());
    outletSystem.initProperties();

    system = outletSystem;
    outStream.setThermoSystem(outletSystem);
    outStream.setCalculationIdentifier(id);

    totalPressureDrop = transientPressureProfile.get(0) - outletPressure;
    pressureOut = outletPressure;
    temperatureOut = outletTemperature;

    increaseTime(dt);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

  /**
   * <p>
   * getInletSuperficialVelocity.
   * </p>
   *
   * @return a double
   */
  public double getInletSuperficialVelocity() {
    return getInletStream().getThermoSystem().getFlowRate("kg/sec")
        / getInletStream().getThermoSystem().getDensity("kg/m3")
        / (Math.PI / 4.0 * Math.pow(insideDiameter, 2.0));
  }

  /**
   * Getter for the field <code>heatTransferCoefficient</code>.
   *
   * @return the heat transfer coefficient
   */
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /**
   * <p>
   * getOutletSuperficialVelocity.
   * </p>
   *
   * @return a double
   */
  public double getOutletSuperficialVelocity() {
    return getSegmentMixtureSuperficialVelocity(numberOfIncrements);
  }

  /**
   * <p>
   * getNumberOfIncrements.
   * </p>
   *
   * @return a double
   */
  public int getNumberOfIncrements() {
    return numberOfIncrements;
  }

  /**
   * <p>
   * Getter for the field <code>angle</code>.
   * </p>
   *
   * @return angle in degrees
   */
  public double getAngle() {
    return angle;
  }

  /**
   * <p>
   * Getter for the field <code>length</code>.
   * </p>
   *
   * @return total length of the pipe in m
   */
  public double getLength() {
    return cumulativeLength;
  }

  /**
   * <p>
   * Getter for the field <code>elevation</code>.
   * </p>
   *
   * @return total elevation of the pipe in m
   */
  public double getElevation() {
    return cumulativeElevation;
  }

  /**
   * <p>
   * getDiameter.
   * </p>
   *
   * @return the diameter
   */
  public double getDiameter() {
    return insideDiameter;
  }

  /**
   * <p>
   * getFlowRegime.
   * </p>
   *
   * @return flow regime
   */
  public FlowRegime getFlowRegime() {
    return regime;
  }

  /**
   * <p>
   * Getter for the field <code>LastSegmentPressureDrop</code>.
   * </p>
   *
   * @return pressure drop last segment
   */
  public double getLastSegmentPressureDrop() {
    return pressureDrop;
  }

  /**
   * <p>
   * Getter for the field <code>totalPressureDrop</code>.
   * </p>
   *
   * @return total pressure drop
   */
  public double getPressureDrop() {
    return totalPressureDrop;
  }

  /**
   * <p>
   * Getter for the field <code>PressureProfile</code>.
   * </p>
   *
   * @return a list double
   */
  public List<Double> getPressureProfile() {
    return new ArrayList<>(pressureProfile);
  }

  /**
   * <p>
   * getSegmentPressure.
   * </p>
   *
   * @param index segment number
   * @return segment pressure as double
   */
  public Double getSegmentPressure(int index) {
    if (index >= 0 && index < pressureProfile.size()) {
      return pressureProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * Get Pressure drop profile.
   *
   * @return ArrayList of pressure drop profile.
   */
  public List<Double> getPressureDropProfile() {
    return new ArrayList<>(pressureDropProfile);
  }

  /**
   * <p>
   * getSegmentPressureDrop.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentPressureDrop(int index) {
    if (index >= 0 && index < pressureDropProfile.size()) {
      return pressureDropProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * Getter for the field <code>temperatureProfile</code>.
   * </p>
   *
   * @return list of temperatures
   */
  public List<Double> getTemperatureProfile() {
    return new ArrayList<>(temperatureProfile);
  }

  /**
   * <p>
   * getSegmentTemperature.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentTemperature(int index) {
    if (index >= 0 && index < temperatureProfile.size()) {
      return temperatureProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * Getter for the field <code>flowRegimeProfile</code>.
   * </p>
   *
   * @return list of flow regime names
   */
  public List<FlowRegime> getFlowRegimeProfile() {
    return new ArrayList<>(flowRegimeProfile);
  }

  /**
   * <p>
   * getSegmentFlowRegime.
   * </p>
   *
   * @param index segment number
   * @return String
   */
  public FlowRegime getSegmentFlowRegime(int index) {
    if (index >= 0 && index < flowRegimeProfile.size()) {
      return flowRegimeProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * Getter for the field <code>liquidSuperficialVelocityProfile</code>.
   * </p>
   *
   * @return list of liquid superficial velocity profile
   */
  public List<Double> getLiquidSuperficialVelocityProfile() {
    return new ArrayList<>(liquidSuperficialVelocityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>gasSuperficialVelocityProfile</code>.
   * </p>
   *
   * @return list of gas superficial velocities
   */
  public List<Double> getGasSuperficialVelocityProfile() {
    return new ArrayList<>(gasSuperficialVelocityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureSuperficialVelocityProfile</code>.
   * </p>
   *
   * @return list of mixture superficial velocity profile
   */
  public List<Double> getMixtureSuperficialVelocityProfile() {
    return new ArrayList<>(mixtureSuperficialVelocityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureViscosityProfile</code>.
   * </p>
   *
   * @return list of mixture viscosity
   */
  public List<Double> getMixtureViscosityProfile() {
    return new ArrayList<>(mixtureViscosityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureDensityProfile</code>.
   * </p>
   *
   * @return list of density profile
   */
  public List<Double> getMixtureDensityProfile() {
    return new ArrayList<>(mixtureDensityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>liquidDensityProfile</code>.
   * </p>
   *
   * @return a {@link java.util.List} object
   */
  public List<Double> getLiquidDensityProfile() {
    return new ArrayList<>(liquidDensityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>liquidHoldupProfile</code>.
   * </p>
   *
   * @return list of hold-up
   */
  public List<Double> getLiquidHoldupProfile() {
    return new ArrayList<>(liquidHoldupProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureReynoldsNumber</code>.
   * </p>
   *
   * @return list of reynold numbers
   */
  public List<Double> getMixtureReynoldsNumber() {
    return new ArrayList<>(mixtureReynoldsNumber);
  }

  /**
   * <p>
   * Getter for the field <code>lengthProfile</code>.
   * </p>
   *
   * @return list of length profile
   */
  public List<Double> getLengthProfile() {
    return new ArrayList<>(lengthProfile);
  }

  /**
   * <p>
   * Getter for the field <code>incrementsProfile</code>.
   * </p>
   *
   * @return list of increments profile
   */
  public List<Integer> getIncrementsProfile() {
    return new ArrayList<>(incrementsProfile);
  }

  /**
   * <p>
   * Getter for the field <code>elevationProfile</code>.
   * </p>
   *
   * @return list of elevation profile
   */
  public List<Double> getElevationProfile() {
    return new ArrayList<>(elevationProfile);
  }

  /**
   * <p>
   * getSegmentLiquidSuperficialVelocity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLiquidSuperficialVelocity(int index) {
    if (index >= 0 && index < liquidSuperficialVelocityProfile.size()) {
      return liquidSuperficialVelocityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentGasSuperficialVelocity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentGasSuperficialVelocity(int index) {
    if (index >= 0 && index < gasSuperficialVelocityProfile.size()) {
      return gasSuperficialVelocityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureSuperficialVelocity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureSuperficialVelocity(int index) {
    if (index >= 0 && index < mixtureSuperficialVelocityProfile.size()) {
      return mixtureSuperficialVelocityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureViscosity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureViscosity(int index) {
    if (index >= 0 && index < mixtureViscosityProfile.size()) {
      return mixtureViscosityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureDensity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureDensity(int index) {
    if (index >= 0 && index < mixtureDensityProfile.size()) {
      return mixtureDensityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentLiquidDensity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLiquidDensity(int index) {
    if (index >= 0 && index < liquidDensityProfile.size()) {
      return liquidDensityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentLiquidHoldup.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLiquidHoldup(int index) {
    if (index >= 0 && index < liquidHoldupProfile.size()) {
      return liquidHoldupProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureReynoldsNumber.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureReynoldsNumber(int index) {
    if (index >= 0 && index < mixtureReynoldsNumber.size()) {
      return mixtureReynoldsNumber.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentLength.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLength(int index) {
    if (index >= 0 && index < lengthProfile.size()) {
      return lengthProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentElevation.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentElevation(int index) {
    if (index >= 0 && index < elevationProfile.size()) {
      return elevationProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new PipeBeggsBrillsResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    PipeBeggsBrillsResponse res = new PipeBeggsBrillsResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }
}
