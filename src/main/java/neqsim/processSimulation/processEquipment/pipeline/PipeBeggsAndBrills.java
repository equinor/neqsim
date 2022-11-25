package neqsim.processSimulation.processEquipment.pipeline;

import java.util.UUID;
import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * https://www.ihsenergy.ca/support/documentation_ca/Piper/2018_1/Content/HTML_Files/Reference%
 * 20materials/Pressure_loss_correlations/c-te-pressure.htm
 *
 */

/*
 * For multiphase flow, many of the published correlations are applicable for "vertical flow" only,
 * while others apply for "horizontal flow" only. Few correlations apply to the whole spectrum of
 * flow situations that may be encountered in oil and gas operations, namely uphill, downhill,
 * horizontal, inclined and vertical flow. The Beggs and Brill (1973) correlation, is one of the few
 * published correlations capable of handling all these flow directions. It was developed using
 * 1" and 1-1/2" sections of pipe that could be inclined at any angle from the horizontal.
 * 
 * The Beggs and Brill multiphase correlation deals with both the friction pressure loss and the
 * hydrostatic pressure difference. First the appropriate flow regime for the particular combination
 * of gas and liquid rates (Segregated, Intermittent or Distributed) is determined. The liquid
 * holdup, and hence, the in-situ density of the gas-liquid mixture is then calculated according to
 * the appropriate flow regime, to obtain the hydrostatic pressure difference. A two-phase friction
 * factor is calculated based on the "input" gas-liquid ratio and the Fanning friction factor. From
 * this the friction pressure loss is calculated using "input" gas-liquid mixture properties. A more
 * detailed discussion of each step is given in the following documentation.
 * 
 * If only a single-phase fluid is flowing, the Beggs and Brill multiphase correlation devolves to
 * the Fanning Gas or Fanning Liquid correlation.
 */
/**
 * <p>
 * AdiabaticTwoPhasePipe class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PipeBeggsAndBrills extends Pipeline {

  private static final long serialVersionUID = 1001;

  double inletPressure = 0;

  boolean setTemperature = false;

  boolean setPressureOut = false;

  protected double temperatureOut = 270;

  protected double pressureOut = 0.0;

  private double pressureOutLimit = 0.0;

  double length = 100.0;

  double flowLimit = 1e20;

  String maxflowunit = "kg/hr";
  double insideDiameter = 0.1;
  double velocity = 1.0;
  double pipeWallRoughness = 1e-5;
  private double inletElevation = 0;
  private double outletElevation = 0;
  double dH = 0.0;
  private String flowPattern = "unknown";
  double inputVolumeFractionLiquid;
  double mixtureFroudeNumber;
  String pipeSpecification = "AP02";
  double A;
  double area;
  double supGasVel;
  double supLiquidVel;
  double elevation = 0;
  double angle;
  boolean setPipeElevation;
  double mixtureDensity;
  double hydrostaticPressureDrop;
  double El = 0;
  double supMixVel;
  double frictionPressureLoss;
  double pressureDrop;
  int numberOfIncrements = 1;

  /**
   * <p>
   * Constructor for AdiabaticTwoPhasePipe.
   * </p>
   */
  @Deprecated
  public PipeBeggsAndBrills() {}

  /**
   * <p>
   * Constructor for AdiabaticTwoPhasePipe.
   * </p>
   *
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public PipeBeggsAndBrills(StreamInterface inStream) {
    this("PipeBeggsAndBrills", inStream);
  }

  /**
   * Constructor for AdiabaticTwoPhasePipe.
   *
   * @param name name of pipe
   */
  public PipeBeggsAndBrills(String name) {
    super(name);
  }

  /**
   * Constructor for AdiabaticTwoPhasePipe.
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
   * @param nominalDiameter a double
   * @param pipeSec a {@link java.lang.String} object
   */
  public void setPipeSpecification(double nominalDiameter, String pipeSec) {
    pipeSpecification = pipeSec;
    insideDiameter = nominalDiameter / 1000.0;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return outStream.getThermoSystem();
  }

  /**
   * <p>
   * setOutTemperature.
   * </p>
   *
   * @param temperature a double
   */
  public void setOutTemperature(double temperature) {
    setTemperature = true;
    this.temperatureOut = temperature;
  }

  /**
   * <p>Setter for the field <code>elevation</code>.</p>
   *
   * @param elevation a double
   */
  public void setElevation(double elevation) {
    setPipeElevation = true;
    this.elevation = elevation;
  }

  /**
   * <p>Setter for the field <code>angle</code>.</p>
   *
   * @param angle a double
   */
  public void setAngle(double angle) {
    this.angle = angle;
  }

  /**
   * <p>Setter for the field <code>numberOfIncrements</code>.</p>
   *
   * @param numberOfIncrements a int
   */
  public void setNumberOfIncrements(int numberOfIncrements) {
    this.numberOfIncrements = numberOfIncrements;
  }

  /**
   * <p>
   * setOutPressure.
   * </p>
   *
   * @param pressure a double
   */
  public void setOutPressure(double pressure) {
    setPressureOut = true;
    this.pressureOut = pressure;
  }

  /**
   * <p>convertSystemUnitToImperial.</p>
   */
  public void convertSystemUnitToImperial() {
    insideDiameter = insideDiameter * 3.2808399;
    angle = 0.01745329 * angle;
    elevation = elevation * 3.2808399;
    length = length * 3.2808399;
    pipeWallRoughness = pipeWallRoughness * 3.2808399;
  }

  /**
   * <p>convertSystemUnitToMetric.</p>
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
   * <p>calcFlowRegime.</p>
   *
   * @return a {@link java.lang.String} object
   */
  public String calcFlowRegime() {

    // Calc input volume fraction
    area = (Math.PI / 4.0) * Math.pow(insideDiameter, 2.0);
    if (system.getNumberOfPhases() != 1){
      supLiquidVel = system.getPhase(1).getFlowRate("ft3/sec") / area;
      supGasVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
      supMixVel = supLiquidVel + supGasVel;
      mixtureFroudeNumber = Math.pow(supMixVel, 2) / (32.174 * insideDiameter);
      inputVolumeFractionLiquid = supLiquidVel / supMixVel;
      /*
      * Unlike the Gray or the Hagedorn and Brown correlations, the Beggs and Brill correlation
      * requires that a flow pattern be determined. Since the original flow pattern map was created,
      * it has been modified. We have used this modified flow pattern map for our calculations. The
      * transition lines for the modified correlation are defined as follows
      */
    }
      else{
        if (system.hasPhaseType("gas")){
          supGasVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
          supMixVel = supGasVel;
          inputVolumeFractionLiquid = 0.0;
          flowPattern = "Single Phase";
        }
        else{
          supLiquidVel = system.getPhase(1).getFlowRate("ft3/sec") / area;
          supMixVel = supLiquidVel;
          inputVolumeFractionLiquid = 1.0;
          flowPattern = "Single Phase";
        }
        System.out.println("Only one phase in the pipe");
      }



      double L1 = 316 * Math.pow(inputVolumeFractionLiquid, 0.302);
      double L2 = 0.0009252 * Math.pow(inputVolumeFractionLiquid, -2.4684);
      double L3 = 0.1 * Math.pow(inputVolumeFractionLiquid, -1.4516);
      double L4 = 0.5 * Math.pow(inputVolumeFractionLiquid, -6.738);

      if (flowPattern != "Single Phase"){
         // The flow type can then be readily determined either from a representative flow pattern map or
      // according to the following conditions
        if ((inputVolumeFractionLiquid < 0.01 && mixtureFroudeNumber < L1)
        || (inputVolumeFractionLiquid >= 0.01 && mixtureFroudeNumber < L2)) {
      flowPattern = "SEGREGATED";
    } else if ((inputVolumeFractionLiquid < 0.4 && inputVolumeFractionLiquid >= 0.01
        && mixtureFroudeNumber <= L1 && mixtureFroudeNumber > L3)
        || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber <= L4
            && mixtureFroudeNumber > L3)) {
      flowPattern = "INTERMITTENT";
    } else if ((inputVolumeFractionLiquid < 0.4 && mixtureFroudeNumber >= L4)
        || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber > L4)) {
      flowPattern = "DISTRIBUTED";
    } else if (mixtureFroudeNumber > L2 && mixtureFroudeNumber < L3) {
      flowPattern = "TRANSITION";
    } else if (inputVolumeFractionLiquid < 0.1 || inputVolumeFractionLiquid > 0.9) {
      flowPattern = "Single Phase";
    } else {
      logger.debug("Flow regime is not found");
  }


      }
     
      A = (L3 - mixtureFroudeNumber) / (L3 - L2);

    return flowPattern;
  }


  /**
   * <p>calcHydrostaticPressureDifference.</p>
   *
   * @return a double
   */
  public double calcHydrostaticPressureDifference() {

    /*
     * Once the flow type has been determined then the liquid holdup can be calculated. Beggs and
     * Brill divided the liquid holdup calculation into two parts. First the liquid holdup for
     * horizontal flow, EL(0), is determined, and then this holdup is modified for inclined flow.
     * EL(0) must be ≥ CL and therefore when EL(0) is smaller than CL, EL(0) is assigned a value of
     * CL. There is a separate calculation of liquid holdup (EL(0)) for each flow type
     */
    double B = 1 - A;

    double BThetta;

    if (flowPattern == "SEGREGATED") {
      El = 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868);
    } else if (flowPattern == "INTERMITTENT") {
      El = 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
          / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (flowPattern == "DISTRIBUTED") {
      El = 1.065 * Math.pow(inputVolumeFractionLiquid, 0.5824)
          / (Math.pow(mixtureFroudeNumber, 0.0609));
    } else if (flowPattern == "TRANSITION") {
      El = A * 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868)
          + B * 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
              / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (flowPattern == "Single Phase") {
      if (inputVolumeFractionLiquid < 0.1) {
        El = inputVolumeFractionLiquid;
      } else {
        El = 1.0 - inputVolumeFractionLiquid;
      }
    }

    if (flowPattern != "Single Phase") {
      // Oil surface tension
      double SG = system.getPhase(1).getDensity("lb/ft3") / (1000 * 0.0624279606);

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
        if (flowPattern == "SEGREGATED") {
          betta = (1 - inputVolumeFractionLiquid)
              * Math.log(0.011 * Math.pow(Nvl, 3.539) / (Math.pow(inputVolumeFractionLiquid, 3.768)
                  * Math.pow(mixtureFroudeNumber, 1.614)));
        } else if (flowPattern == "INTERMITTENT") {
          betta = (1 - inputVolumeFractionLiquid)
              * Math.log(2.96 * Math.pow(inputVolumeFractionLiquid, 0.305)
                  * Math.pow(mixtureFroudeNumber, 0.0978) / (Math.pow(Nvl, 0.4473)));
        } else if (flowPattern == "DISTRIBUTED") {
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

      mixtureDensity =
        system.getPhase(1).getDensity("lb/ft3") * El + system.getPhase(0).getDensity() * (1 - El);
      }
      else{
      if (system.hasPhaseType("gas")){
        mixtureDensity =
        system.getPhase(0).getDensity("lb/ft3");
      }
      else{
        mixtureDensity =
        system.getPhase(1).getDensity("lb/ft3");
      }
    }
    
      
      
    hydrostaticPressureDrop = mixtureDensity * 32.2 * elevation;

    return hydrostaticPressureDrop;
  }

  /**
   * <p>calcFrictionPressureLoss.</p>
   *
   * @return a double
   */
  public double calcFrictionPressureLoss() {
    double S = 0;

    double rhoNoSlip =0;
  
    double muNoSlip = 0;

    if (system.getNumberOfPhases() != 1) {
      if (flowPattern != "Single Phase"){
        double y = Math.log(inputVolumeFractionLiquid / (Math.pow(El, 2)));
        S = y / (-0.0523 + 3.18 * y - 0.872 * Math.pow(y, 2.0) + 0.01853 * Math.pow(y, 4));
      rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3")) * inputVolumeFractionLiquid
      + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
      muNoSlip = system.getPhase(1).getViscosity("cP") * inputVolumeFractionLiquid
      + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
      }
      else{
        rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3")) * inputVolumeFractionLiquid
        + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
        muNoSlip = system.getPhase(1).getViscosity("cP") * inputVolumeFractionLiquid
        + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
      }
      }

    else{
      if (system.hasPhaseType("gas")){
        rhoNoSlip = (system.getPhase(0).getDensity("lb/ft3"));
        muNoSlip = (system.getPhase(0).getViscosity("cP"));
      }
      else{
        rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3"));
        muNoSlip = (system.getPhase(1).getViscosity("cP"));
      }
    }

    double ReNoSlip = rhoNoSlip * supMixVel * insideDiameter*(16/(3.28*3.28)) / (0.001*muNoSlip);

    double E = pipeWallRoughness / insideDiameter;

    // Haaland equation
    double frictionFactor = Math.pow(1 / (-1.8 * Math.log10((E / 3.7) + (6.9 / ReNoSlip))), 2); 
    double frictionTwoPhase = frictionFactor * Math.exp(S);

    frictionPressureLoss = frictionTwoPhase * Math.pow(supMixVel, 2) * rhoNoSlip * (length)
        / (2*insideDiameter);
    return frictionPressureLoss;
  }

  /**
   * <p>calcPressureDrop.</p>
   *
   * @return a double
   */
  public double calcPressureDrop() {
    convertSystemUnitToImperial();
    calcFlowRegime();
    hydrostaticPressureDrop = calcHydrostaticPressureDifference();
    frictionPressureLoss = calcFrictionPressureLoss();
    pressureDrop = (hydrostaticPressureDrop + frictionPressureLoss);
    convertSystemUnitToMetric();
    return pressureDrop;
  }


  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    length = length / numberOfIncrements;
    system = inStream.getThermoSystem().clone();
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    system.initProperties();
    double enthalpyInlet = system.getEnthalpy();
    for (int i = 1; i <= numberOfIncrements; i++) {
      System.out.println(system.getNumberOfPhases());  
      System.out.println(system.hasPhaseType("gas"));
      System.out.println(system.hasPhaseType("oil")); 
      System.out.println(system.getFlowRate("kg/hr"));
      System.out.println(system.getComponent("methane").getz());
      //System.out.println(system.getComponent("n-heptane").getz());
      inletPressure = system.getPressure();
      pressureDrop = calcPressureDrop();


      pressureOut = inletPressure - pressureDrop;
      system.setPressure(pressureOut);
      testOps.PHflash(enthalpyInlet);
      system.initProperties();
      System.out.println(system.getTemperature("C"));
    }
    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
    System.out.println(pressureOut);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    system.display();
  }

  /**
   * <p>getSuperficialVelocity.</p>
   *
   * @return a double
   */
  public double getSuperficialVelocity() {
    return getInletStream().getThermoSystem().getFlowRate("kg/sec")
        / getInletStream().getThermoSystem().getDensity("kg/m3")
        / (Math.PI / 4.0 * Math.pow(insideDiameter, 2.0));
  }

  /** {@inheritDoc} */
  @Override
  public FlowSystemInterface getPipe() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {}

  /**
   * <p>
   * Getter for the field <code>length</code>.
   * </p>
   *
   * @return the length
   */
  public double getLength() {
    return length;
  }

  /**
   * <p>
   * Setter for the field <code>length</code>.
   * </p>
   *
   * @param length the length to set
   */
  public void setLength(double length) {
    this.length = length;
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
   * <p>getFlowRegime.</p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getFlowRegime() {
    return flowPattern;
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
   * Getter for the field <code>inletElevation</code>.
   * </p>
   *
   * @return the inletElevation
   */
  public double getInletElevation() {
    return inletElevation;
  }

  /**
   * <p>Getter for the field <code>pressureDrop</code>.</p>
   *
   * @return a double
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * <p>
   * Setter for the field <code>inletElevation</code>.
   * </p>
   *
   * @param inletElevation the inletElevation to set
   */
  public void setInletElevation(double inletElevation) {
    this.inletElevation = inletElevation;
  }

  /**
   * <p>
   * Getter for the field <code>outletElevation</code>.
   * </p>
   *
   * @return the outletElevation
   */
  public double getOutletElevation() {
    return outletElevation;
  }

  /**
   * <p>
   * Setter for the field <code>outletElevation</code>.
   * </p>
   *
   * @param outletElevation the outletElevation to set
   */
  public void setOutletElevation(double outletElevation) {
    this.outletElevation = outletElevation;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param name an array of {@link java.lang.String} objects
   */
  public static void main(String[] name) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
    testSystem.addComponent("methane", 5, "MSm^3/day");
    testSystem.addComponent("n-heptane", 5, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);

    Stream stream_1 = new Stream("Stream1", testSystem);

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(stream_1);
    pipe.setDiameter(1.017112);
    pipe.setPipeWallRoughness(5e-6);
    pipe.setLength(10000);
    pipe.setElevation(0.0);
    pipe.setAngle(0);
    pipe.setNumberOfIncrements(2);



    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.run();


  }

  /**
   * <p>
   * Getter for the field <code>pressureOutLimit</code>.
   * </p>
   *
   * @return a double
   */
  public double getPressureOutLimit() {
    return pressureOutLimit;
  }

  /**
   * <p>
   * Setter for the field <code>pressureOutLimit</code>.
   * </p>
   *
   * @param pressureOutLimit a double
   */
  public void setPressureOutLimit(double pressureOutLimit) {
    this.pressureOutLimit = pressureOutLimit;
  }

  /**
   * <p>
   * Setter for the field <code>flowLimit</code>.
   * </p>
   *
   * @param flowLimit a double
   * @param unit a {@link java.lang.String} object
   */
  public void setFlowLimit(double flowLimit, String unit) {
    this.flowLimit = flowLimit;
    maxflowunit = unit;
  }
}
