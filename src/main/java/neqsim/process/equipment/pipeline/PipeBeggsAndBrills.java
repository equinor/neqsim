package neqsim.process.equipment.pipeline;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * PipeBeggsAndBrills class.
 * </p>
 *
 * @author Even Solbraa , Sviatoslav Eroshkin
 * @version $Id: $Id
 */
public class PipeBeggsAndBrills extends Pipeline {
  private static final long serialVersionUID = 1001;

  int iteration;

  private double nominalDiameter;

  private Boolean PipeSpecSet = false;

  // Inlet pressure of the pipeline (initialization)
  private double inletPressure = Double.NaN;

  private double totalPressureDrop = 0;

  // Outlet properties initialization [K] and [bar]
  protected double temperatureOut = 270;
  protected double pressureOut = 0.0;

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
  private String regime;

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
  private List<String> flowRegimeProfile;

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
   * @return a {@link java.lang.String} object
   */
  public String calcFlowRegime() {
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
        regime = "Single Phase";
      } else {
        supLiquidVel = system.getPhase(1).getFlowRate("ft3/sec") / area;
        supMixVel = supLiquidVel;
        inputVolumeFractionLiquid = 1.0;
        regime = "Single Phase";
      }
    }

    liquidSuperficialVelocityProfile.add(supLiquidVel / 3.2808399); // to meters
    gasSuperficialVelocityProfile.add(supGasVel / 3.2808399);
    mixtureSuperficialVelocityProfile.add(supMixVel / 3.2808399);

    double L1 = 316 * Math.pow(inputVolumeFractionLiquid, 0.302);
    double L2 = 0.0009252 * Math.pow(inputVolumeFractionLiquid, -2.4684);
    double L3 = 0.1 * Math.pow(inputVolumeFractionLiquid, -1.4516);
    double L4 = 0.5 * Math.pow(inputVolumeFractionLiquid, -6.738);

    if (regime != "Single Phase") {
      if ((inputVolumeFractionLiquid < 0.01 && mixtureFroudeNumber < L1)
          || (inputVolumeFractionLiquid >= 0.01 && mixtureFroudeNumber < L2)) {
        regime = "SEGREGATED";
      } else if ((inputVolumeFractionLiquid < 0.4 && inputVolumeFractionLiquid >= 0.01
          && mixtureFroudeNumber <= L1 && mixtureFroudeNumber > L3)
          || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber <= L4
              && mixtureFroudeNumber > L3)) {
        regime = "INTERMITTENT";
      } else if ((inputVolumeFractionLiquid < 0.4 && mixtureFroudeNumber >= L4)
          || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber > L4)) {
        regime = "DISTRIBUTED";
      } else if (mixtureFroudeNumber > L2 && mixtureFroudeNumber < L3) {
        regime = "TRANSITION";
      } else if (inputVolumeFractionLiquid < 0.1 || inputVolumeFractionLiquid > 0.9) {
        regime = "INTERMITTENT";
      } else if (mixtureFroudeNumber > 110) {
        regime = "INTERMITTENT";
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

    if (regime == "SEGREGATED") {
      El = 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868);
    } else if (regime == "INTERMITTENT") {
      El = 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
          / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (regime == "DISTRIBUTED") {
      El = 1.065 * Math.pow(inputVolumeFractionLiquid, 0.5824)
          / (Math.pow(mixtureFroudeNumber, 0.0609));
    } else if (regime == "TRANSITION") {
      El = A * 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868)
          + B * 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
              / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (regime == "Single Phase") {
      if (inputVolumeFractionLiquid < 0.1) {
        El = inputVolumeFractionLiquid;
      } else {
        El = 1.0 - inputVolumeFractionLiquid;
      }
    }

    if (regime != "Single Phase") {
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
        if (regime == "SEGREGATED") {
          betta = (1 - inputVolumeFractionLiquid)
              * Math.log(0.011 * Math.pow(Nvl, 3.539) / (Math.pow(inputVolumeFractionLiquid, 3.768)
                  * Math.pow(mixtureFroudeNumber, 1.614)));
        } else if (regime == "INTERMITTENT") {
          betta = (1 - inputVolumeFractionLiquid)
              * Math.log(2.96 * Math.pow(inputVolumeFractionLiquid, 0.305)
                  * Math.pow(mixtureFroudeNumber, 0.0978) / (Math.pow(Nvl, 0.4473)));
        } else if (regime == "DISTRIBUTED") {
          betta = 0;
        }
      } else {
        betta = (1 - inputVolumeFractionLiquid)
            * Math.log(4.70 * Math.pow(Nvl, 0.1244) / (Math.pow(inputVolumeFractionLiquid, 0.3692)
                * Math.pow(mixtureFroudeNumber, 0.5056)));
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
      if (system.hasPhaseType("gas")) {
        mixtureDensity = system.getPhase(0).getDensity("lb/ft3");
      } else {
        mixtureDensity = system.getPhase(1).getDensity("lb/ft3");
      }
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
      if (regime != "Single Phase") {
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
      if (system.hasPhaseType("gas")) {
        rhoNoSlip = (system.getPhase(0).getDensity("lb/ft3"));
        muNoSlip = (system.getPhase(0).getViscosity("cP"));
        liquidDensityProfile.add(0.0);
      } else {
        rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3"));
        muNoSlip = (system.getPhase(1).getViscosity("cP"));
        liquidDensityProfile.add(rhoNoSlip * 16.01846);
      }
    }

    mixtureViscosityProfile.add(muNoSlip);
    mixtureDensityProfile.add(rhoNoSlip * 16.01846);

    double ReNoSlip =
        rhoNoSlip * supMixVel * insideDiameter * (16 / (3.28 * 3.28)) / (0.001 * muNoSlip);

    mixtureReynoldsNumber.add(ReNoSlip);

    double E = pipeWallRoughness / insideDiameter;

    // Haaland equation
    double frictionFactor = Math.pow(1 / (-1.8 * Math.log10((E / 3.7) + (6.9 / ReNoSlip))), 2);
    double frictionTwoPhase = frictionFactor * Math.exp(S);

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
    regime = "unknown";
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
    iteration = 0;

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
        testOps.PHflash(enthalpyInlet);
      } else {
        testOps.TPflash();
      }
      system.initProperties();
      temperatureProfile.add(system.getTemperature());
    }
    totalPressureDrop = pipeInletPressure - system.getPressure();
    calcPressureDrop(); // to initialize final parameters
    lengthProfile.add(cumulativeLength);
    elevationProfile.add(cumulativeElevation);
    incrementsProfile.add(getNumberOfIncrements());

    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * runTransient.
   * </p>
   */
  @Override
  public void runTransient(double dt, UUID id) {
    run(id);
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
   * @return a {@link java.lang.String} object
   */
  public String getFlowRegime() {
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
  public List<String> getFlowRegimeProfile() {
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
  public String getSegmentFlowRegime(int index) {
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
    if (index >= 0 && index <= liquidSuperficialVelocityProfile.size()) {
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
    if (index >= 0 && index <= gasSuperficialVelocityProfile.size()) {
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
    if (index >= 0 && index <= mixtureSuperficialVelocityProfile.size()) {
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
    if (index >= 0 && index <= mixtureViscosityProfile.size()) {
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
    if (index >= 0 && index <= mixtureDensityProfile.size()) {
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
    if (index >= 0 && index <= liquidDensityProfile.size()) {
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
    if (index >= 0 && index <= liquidHoldupProfile.size()) {
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
    if (index >= 0 && index <= mixtureReynoldsNumber.size()) {
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
    if (index >= 0 && index <= lengthProfile.size()) {
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
    if (index >= 0 && index <= elevationProfile.size()) {
      return elevationProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }
}
