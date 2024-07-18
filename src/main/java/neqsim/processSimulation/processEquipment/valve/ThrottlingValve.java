package neqsim.processSimulation.processEquipment.valve;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.processSimulation.mechanicalDesign.valve.ValveMechanicalDesign;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.util.monitor.ValveResponse;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ThrottlingValve class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ThrottlingValve extends TwoPortEquipment implements ValveInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(ThrottlingValve.class);
  SystemInterface thermoSystem;

  private boolean valveCvSet = false;

  private boolean isoThermal = false;

  double pressure = 0.0;
  private double Cv;
  private double maxMolarFlow = 1000.0;
  private double minMolarFlow = 0.0;
  private double maxValveOpening = 100.0;
  private double minValveOpening = 0.0;
  private double percentValveOpening = 100.0;
  double molarFlow = 0.0;
  private String pressureUnit = "bara";
  private boolean acceptNegativeDP = true;
  ValveMechanicalDesign valveMechanicalDesign;
  boolean isCalcPressure = false;
  private boolean gasValve = true;
  private double Fp = 1.0;

  /**
   * <p>
   * Constructor for ThrottlingValve.
   * </p>
   */
  public ThrottlingValve() {
    this("ThrottlingValve");
    setCalculateSteadyState(false);
  }

  /**
   * <p>
   * Constructor for ThrottlingValve.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public ThrottlingValve(StreamInterface inletStream) {
    this("ThrottlingValve", inletStream);
  }

  /**
   * * Constructor for ThrottlingValve.
   *
   * @param name name of valve
   */
  public ThrottlingValve(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for ThrottlingValve.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public ThrottlingValve(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * <p>
   * getDeltaPressure.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getDeltaPressure(String unit) {
    return inStream.getFluid().getPressure(unit) - thermoSystem.getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    super.setInletStream(stream);
    StreamInterface outStream = stream.clone();
    outStream.setName("outStream");
    super.setOutletStream(outStream);
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletPressure() {
    return this.pressure;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /** {@inheritDoc} */
  @Override
  public double getInletPressure() {
    return getInletStream().getThermoSystem().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {
    setOutletPressure(pressure);
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setPressure(double pressure, String unit) {
    setOutletPressure(pressure, unit);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.pressure = pressure;
    getOutletStream().getThermoSystem().setPressure(pressure, pressureUnit);
  }

  /**
   * <p>
   * setOutletPressure.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setOutletPressure(double pressure, String unit) {
    pressureUnit = unit;
    this.pressure = pressure;
    getOutletStream().getThermoSystem().setPressure(pressure, pressureUnit);
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    if (getInletStream().getThermoSystem().getTemperature() == thermoSystem.getTemperature()
        && getInletStream().getThermoSystem().getPressure() == thermoSystem.getPressure()
        && getInletStream().getThermoSystem().getFlowRate("kg/hr") == thermoSystem
            .getFlowRate("kg/hr")
        && getOutletPressure() == getOutletStream().getPressure()) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * Adjusts the flow coefficient (Cv) based on the percentage valve opening.
   *
   * @param Cv Flow coefficient in US gallons per minute (USG/min).
   * @param percentValveOpening Percentage valve opening (0 to 100).
   * @return Adjusted flow coefficient (Cv) in US gallons per minute (USG/min).
   */
  private static double adjustCv(double Cv, double percentValveOpening) {
    return Cv * (percentValveOpening / 100);
  }

  /**
   * Calculates the mass flow rate through a control valve for a liquid based on the given
   * parameters.
   *
   * @param P1 Upstream pressure in bar.
   * @param P2 Downstream pressure in bar.
   * @param rho Density of the fluid in kilograms per cubic meter (kg/m³).
   * @param Cv Flow coefficient in US gallons per minute (USG/min).
   * @param Fp Piping geometry factor (dimensionless).
   * @param percentValveOpening Percentage valve opening (0 to 100).
   * @return Mass flow rate in kilograms per hour (kg/h).
   */
  public static double liquidValveMassFlow(double P1, double P2, double rho, double Cv, double Fp,
      double percentValveOpening) {
    // Equation unit conversion constant
    final double N1 = 0.0865;

    // Convert pressures from bar to Pascals directly in the code
    double P1Pa = P1 * 100000;
    double P2Pa = P2 * 100000;

    // Adjust Cv based on the percentage valve opening
    double adjustedCv = adjustCv(Cv, percentValveOpening);

    // Clip Cv value to be non-negative
    double clippedCv = Math.max(adjustedCv, 0);
    // Calculate pressure difference and clip to be non-negative
    double deltaP = Math.max(P1Pa - P2Pa, 0);
    // Calculate mass flow rate
    double massFlowRate = clippedCv * N1 * Fp * Math.sqrt(deltaP * rho);

    return massFlowRate;
  }

  /**
   * Calculates the downstream pressure (P2) through a control valve for a liquid based on the given
   * parameters.
   *
   * @param P1 Upstream pressure in bar.
   * @param m Mass flow rate in kilograms per hour (kg/h).
   * @param rho Density of the fluid in kilograms per cubic meter (kg/m³).
   * @param Cv Flow coefficient in US gallons per minute (USG/min).
   * @param Fp Piping geometry factor (dimensionless).
   * @param percentValveOpening Percentage valve opening (0 to 100).
   * @return Downstream pressure in bar.
   */
  public static double liquidValvePout(double P1, double m, double rho, double Cv, double Fp,
      double percentValveOpening) {
    // Equation unit conversion constant
    final double N1 = 0.0865;

    // Convert upstream pressure from bar to Pascals directly in the code
    double P1Pa = P1 * 100000;

    // Adjust Cv based on the percentage valve opening
    double adjustedCv = adjustCv(Cv, percentValveOpening);

    // Clip Cv value to be non-negative
    double clippedCv = Math.max(adjustedCv, 0);
    // Calculate deltaP from mass flow rate
    double deltaP = Math.pow(m / (clippedCv * N1 * Fp), 2) / rho;
    // Calculate downstream pressure
    double P2Pa = P1Pa - deltaP;

    // Ensure downstream pressure is non-negative
    P2Pa = Math.max(P2Pa, 0);

    // Convert downstream pressure from Pascals to bar directly in the code
    return P2Pa / 100000;
  }

  /**
   * Calculates the flow coefficient (Cv) of a control valve for a liquid based on the given
   * parameters.
   *
   * @param P1 Upstream pressure in bar.
   * @param P2 Downstream pressure in bar.
   * @param rho Density of the fluid in kilograms per cubic meter (kg/m³).
   * @param m Mass flow rate in kilograms per hour (kg/h).
   * @param Fp Piping geometry factor (dimensionless).
   * @param percentValveOpening Percentage valve opening (0 to 100).
   * @return Flow coefficient (Cv) in US gallons per minute (USG/min).
   */
  public static double liquidValveCv(double P1, double P2, double rho, double m, double Fp,
      double percentValveOpening) {
    // Equation unit conversion constant
    final double N1 = 0.0865;

    // Convert pressures from bar to Pascals directly in the code
    double P1Pa = P1 * 100000;
    double P2Pa = P2 * 100000;

    // Calculate pressure difference and clip to be non-negative
    double deltaP = Math.max(P1Pa - P2Pa, 0);
    // Calculate flow coefficient
    double Cv = m / (N1 * Fp * Math.sqrt(deltaP * rho));

    // Adjust Cv based on the percentage valve opening
    return Cv / (percentValveOpening / 100);
  }



  /**
   * Calculates the mass flow rate through a valve given the upstream pressure (Pus), downstream
   * pressure (Pds), fluid density (rhous), and flow coefficient (Cv).
   *
   * The calculation is based on the formula for mass flow through a valve.
   *
   * @param Pus The upstream pressure (Pus) in bara.
   * @param Pds The downstream pressure (Pds) in bara.
   * @param rhous The density of the fluid upstream of the valve in kg/m^3.
   * @param Cv The flow coefficient of the valve.
   * @param percentValveOpening Opening of valve in %
   * @return The mass flow rate through the valve in kg/hr.
   */
  public double calcmassflow(double Pus, double Pds, double rhous, double Cv,
      double percentValveOpening) {

    // Sine of 3417 / 30.0
    double sineFactor = Math.sin(3417 / 30.0);

    // Calculate the mass flow rate
    double massFlowRate = 0.0457 * Math.sqrt(Pus * 100.0 * rhous) * sineFactor
        * Math.sqrt((Pus - Pds) / Pus) * Cv * percentValveOpening / 100.0;
    return massFlowRate;
  }

  /**
   * Calculates the downstream pressure (Pds) of a valve given the upstream pressure (Pus), fluid
   * density (rhous), flow coefficient (Cv), mass flow rate, and the percent valve opening.
   *
   * The calculation is based on the formula for mass flow through a valve, rearranged to solve for
   * Pds.
   *
   * @param Pus The upstream pressure (Pus) in bara.
   * @param rhous The density of the fluid upstream of the valve in kg/m^3.
   * @param Cv The flow coefficient of the valve.
   * @param massFlowRate The mass flow rate through the valve in kg/hr.
   * @param percentValveOpening Opening of valve in %
   * @return The downstream pressure (Pds) in bara.
   */
  public double calcValvePout(double Pus, double rhous, double Cv, double massFlowRate,
      double percentValveOpening) {
    // Sine of 3417 / 30.0
    double sineFactor = Math.sin(3417 / 30.0);

    // Calculate the term that involves the mass flow rate, Cv, and percent valve opening
    double flowTerm = (massFlowRate / (0.0457 * Math.sqrt(Pus * 100.0 * rhous) * sineFactor * Cv
        * (percentValveOpening / 100.0)));

    // Square the flowTerm to eliminate the square root
    double flowTermSquared = flowTerm * flowTerm;

    // Calculate Pds
    double Pds = Pus * (1 - flowTermSquared);

    return Pds;
  }


  /**
   * Calculates the flow coefficient (Cv) of a valve given the upstream pressure (Pus), downstream
   * pressure (Pds), fluid density (rhous), mass flow rate, and the percent valve opening.
   *
   * The calculation is based on the formula for mass flow through a valve, rearranged to solve for
   * Cv.
   *
   * @param Pus The upstream pressure (Pus) in bara.
   * @param Pds The downstream pressure (Pds) in bara.
   * @param rhous The density of the fluid upstream of the valve in kg/m^3.
   * @param massFlowRate The mass flow rate through the valve in kg/hr.
   * @param percentValveOpening Opening of valve in %
   * @return The flow coefficient (Cv) of the valve.
   */
  public double calcCv(double Pus, double Pds, double rhous, double massFlowRate,
      double percentValveOpening) {
    // Sine of 3417 / 30.0
    double sineFactor = Math.sin(3417 / 30.0);

    // Calculate Cv
    double Cv = massFlowRate / (0.0457 * Math.sqrt(Pus * 100.0 * rhous) * sineFactor
        * Math.sqrt((Pus - Pds) / Pus) * percentValveOpening / 100.0);

    return Cv;
  }

  /**
   * Calculates the percent valve opening given the upstream pressure (Pus), downstream pressure
   * (Pds), fluid density (rhous), flow coefficient (Cv), and mass flow rate.
   *
   * The calculation is based on the formula for mass flow through a valve, rearranged to solve for
   * percent valve opening.
   *
   * @param Pus The upstream pressure (Pus) in bara.
   * @param Pds The downstream pressure (Pds) in bara.
   * @param rhous The density of the fluid upstream of the valve in kg/m^3.
   * @param Cv The flow coefficient of the valve.
   * @param massFlowRate The mass flow rate through the valve in kg/hr.
   * @return The percent valve opening.
   */
  public double calcPercentValveOpening(double Pus, double Pds, double rhous, double Cv,
      double massFlowRate) {
    // Sine of 3417 / 30.0
    double sineFactor = Math.sin(3417 / 30.0);

    // Calculate the term that involves the mass flow rate, Pus, rhous, and Cv
    double term = massFlowRate / (0.0457 * Math.sqrt(Pus * rhous) * sineFactor * Cv);

    // Calculate the percent valve opening
    double percentValveOpening = term / Math.sqrt(1 - (Pds / Pus)) * 100.0;

    return percentValveOpening;
  }



  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("valve running..");
    // outStream.setSpecification(inletStream.getSpecification());
    thermoSystem = getInletStream().getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    double enthalpy = thermoSystem.getEnthalpy();
    inStream.getThermoSystem().initPhysicalProperties("density");
    double outp = 0.0;

    if (inStream.getThermoSystem().hasPhaseType(PhaseType.GAS)
        && inStream.getThermoSystem().getVolumeFraction(0) > 0.9) {
      setGasValve(true);
    } else {
      setGasValve(false);
    }

    if (valveCvSet && isCalcPressure) {
      if (gasValve) {
        outp = calcValvePout(inStream.getThermoSystem().getPressure(),
            inStream.getThermoSystem().getDensity("kg/m3"), Cv, inStream.getFlowRate("kg/hr"),
            percentValveOpening);
      } else {
        outp =
            liquidValvePout(inStream.getThermoSystem().getPressure(), inStream.getFlowRate("kg/hr"),
                inStream.getThermoSystem().getDensity("kg/m3"), Cv, Fp, percentValveOpening);
      }
      setOutletPressure(outp);
    }

    if ((thermoSystem.getPressure(pressureUnit) - pressure) < 0) {
      if (isAcceptNegativeDP()) {
        thermoSystem.setPressure(pressure, pressureUnit);
      }
    } else {
      thermoSystem.setPressure(pressure, pressureUnit);
    }

    if (getSpecification().equals("out stream")) {
      thermoSystem.setPressure(outStream.getPressure(), pressureUnit);
    }
    // System.out.println("enthalpy inn.." + enthalpy);
    // thermoOps.PHflash(enthalpy, 0);
    if (isIsoThermal() || Math.abs(pressure - inStream.getThermoSystem().getPressure()) < 1e-6
        || thermoSystem.getNumberOfMoles() < 1e-12) {
      thermoOps.TPflash();
    } else {
      thermoOps.PHflash(enthalpy, 0);
    }
    outStream.setThermoSystem(thermoSystem);
    // System.out.println("Total volume flow " +
    // outStream.getThermoSystem().getVolume());
    // System.out.println("density valve " +
    // inletStream.getThermoSystem().getDensity());

    if (!valveCvSet) {
      // If valve CV is not set, calculate it from inletstream flow, percent opening
      // and
      // differential pressure over valve.
      if (gasValve) {
        Cv = calcCv(inStream.getThermoSystem().getPressure(),
            outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"),
            inStream.getFlowRate("kg/hr"), percentValveOpening);
      } else {
        Cv = liquidValveCv(inStream.getThermoSystem().getPressure(),
            outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"),
            inStream.getFlowRate("kg/hr"), Fp, percentValveOpening);
      }
      valveCvSet = true;
    }

    percentValveOpening = calcPercentValveOpening(inStream.getThermoSystem().getPressure(),
        outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
        inStream.getFlowRate("kg/hr"));

    if (gasValve) {
      molarFlow = calcmassflow(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          percentValveOpening) / 3600.0 / inStream.getFluid().getMolarMass("kg/mol");
    } else {
      molarFlow = liquidValveMassFlow(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          Fp, percentValveOpening) / 3600.0 / inStream.getFluid().getMolarMass("kg/mol");
    }

    if (Math.abs(pressure - inStream.getThermoSystem().getPressure()) < 1e-6) {
      molarFlow = inStream.getThermoSystem().getTotalNumberOfMoles();
    }

    try {
      inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getThermoSystem().init(3);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    // inletStream.run(id);

    outStream.setThermoSystem(thermoSystem.clone());
    outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
    outStream.getThermoSystem().init(3);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    thermoSystem.display(getName());
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return thermoSystem.getResultTable();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }

    runController(dt, id);

    thermoSystem = inStream.getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);

    double enthalpy = thermoSystem.getEnthalpy();
    thermoSystem.setPressure(getOutletStream().getThermoSystem().getPressure());
    // System.out.println("enthalpy inn.." + enthalpy);
    if (isIsoThermal()) {
      thermoOps.TPflash();
    } else {
      thermoOps.PHflash(enthalpy, 0);
    }
    thermoSystem.initPhysicalProperties("density");
    outStream.setThermoSystem(thermoSystem);

    if (gasValve) {
      molarFlow = calcmassflow(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          percentValveOpening) / 3600.0 / inStream.getFluid().getMolarMass("kg/mol");
    } else {
      molarFlow = liquidValveMassFlow(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          Fp, percentValveOpening) / 3600.0 / inStream.getFluid().getMolarMass("kg/mol");
    }

    try {
      inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getThermoSystem().init(1);
      inStream.run(id);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    try {
      outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      outStream.getThermoSystem().init(1);
      outStream.run(id);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * runController.
   * </p>
   *
   * @param dt a double
   * @param id Calculation identifier
   */
  public void runController(double dt, UUID id) {
    if (hasController && getController().isActive()) {
      getController().runTransient(this.percentValveOpening, dt, id);
      this.percentValveOpening = getController().getResponse();
      if (this.percentValveOpening > maxValveOpening) {
        this.percentValveOpening = maxValveOpening;
      }
      if (this.percentValveOpening < minValveOpening) {
        this.percentValveOpening = minValveOpening;
      }
      // System.out.println("valve opening " + this.percentValveOpening + " %");
    }
    setCalculationIdentifier(id);
  }

  public void setMinimumValveOpening(double minopen) {
    minValveOpening = minopen;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return Cv;
  }

  /** {@inheritDoc} */
  @Override
  public void setCv(double cv) {
    this.Cv = cv;
    valveCvSet = true;
  }

  /** {@inheritDoc} */
  @Override
  public double getPercentValveOpening() {
    return percentValveOpening;
  }

  /** {@inheritDoc} */
  @Override
  public void setPercentValveOpening(double percentValveOpening) {
    this.percentValveOpening = percentValveOpening;
  }

  /**
   * <p>
   * isValveCvSet.
   * </p>
   *
   * @return a boolean
   */
  public boolean isValveCvSet() {
    return valveCvSet;
  }

  /**
   * <p>
   * Setter for the field <code>valveCvSet</code>.
   * </p>
   *
   * @param valveCvSet a boolean
   */
  public void setValveCvSet(boolean valveCvSet) {
    this.valveCvSet = valveCvSet;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIsoThermal() {
    return isoThermal;
  }

  /** {@inheritDoc} */
  @Override
  public void setIsoThermal(boolean isoThermal) {
    this.isoThermal = isoThermal;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    outStream.getThermoSystem().init(3);
    inStream.getThermoSystem().init(3);
    return outStream.getThermoSystem().getEntropy(unit)
        - inStream.getThermoSystem().getEntropy(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    outStream.getThermoSystem().init(3);
    inStream.getThermoSystem().init(3);
    return outStream.getThermoSystem().getExergy(surroundingTemperature, unit)
        - inStream.getThermoSystem().getExergy(surroundingTemperature, unit);
  }

  /**
   * <p>
   * isAcceptNegativeDP.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAcceptNegativeDP() {
    return acceptNegativeDP;
  }

  /**
   * <p>
   * Setter for the field <code>acceptNegativeDP</code>.
   * </p>
   *
   * @param acceptNegativeDP a boolean
   */
  public void setAcceptNegativeDP(boolean acceptNegativeDP) {
    this.acceptNegativeDP = acceptNegativeDP;
  }

  @Override
  public void initMechanicalDesign() {
    valveMechanicalDesign = new ValveMechanicalDesign(this);
  }

  /**
   * {@inheritDoc}
   *
   * @return a {@link neqsim.processSimulation.mechanicalDesign.valve.ValveMechanicalDesign} object
   */
  @Override
  public ValveMechanicalDesign getMechanicalDesign() {
    return valveMechanicalDesign;
  }

  public void setIsCalcOutPressure(boolean isSetPres) {
    isCalcPressure = isSetPres;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new ValveResponse(this));
  }

  public boolean isGasValve() {
    return gasValve;
  }

  public void setGasValve(boolean gasValve) {
    this.gasValve = gasValve;
  }

  public double getFp() {
    return Fp;
  }

  public void setFp(double fp) {
    Fp = fp;
  }
}
