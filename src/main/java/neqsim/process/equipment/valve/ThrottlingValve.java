package neqsim.process.equipment.valve;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.util.monitor.ValveResponse;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ThrottlingValve class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ThrottlingValve extends TwoPortEquipment implements ValveInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
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
  private double deltaPressure = 0.0;

  /**
   * * Constructor for ThrottlingValve.
   *
   * @param name name of valve
   */
  public ThrottlingValve(String name) {
    super(name);
    setCalculateSteadyState(true);
  }

  /**
   * <p>
   * Constructor for ThrottlingValve.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public ThrottlingValve(String name, StreamInterface inletStream) {
    this(name);
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
  private double adjustCv(double Cv, double percentValveOpening) {
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
  public double liquidValveMassFlow(double P1, double P2, double rho, double Cv, double Fp,
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
   * Calculates the percent valve opening given the mass flow rate through a valve, upstream
   * pressure (P1), downstream pressure (P2), fluid density (rho), flow coefficient (Cv), and piping
   * geometry factor (Fp).
   *
   * @param massFlowRate The mass flow rate through the valve in kg/hr.
   * @param P1 The upstream pressure in bar.
   * @param P2 The downstream pressure in bar.
   * @param rho The density of the fluid in kilograms per cubic meter (kg/m³).
   * @param Cv The flow coefficient of the valve in US gallons per minute (USG/min).
   * @param Fp The piping geometry factor (dimensionless).
   * @return The percent valve opening.
   */
  public double calcPercentValveOpeningLiquid(double massFlowRate, double P1, double P2, double rho,
      double Cv, double Fp) {
    // Equation unit conversion constant
    final double N1 = 0.0865;

    // Convert pressures from bar to Pascals directly in the code
    double P1Pa = P1 * 100000;
    double P2Pa = P2 * 100000;

    // Calculate pressure difference and clip to be non-negative
    double deltaP = Math.max(P1Pa - P2Pa, 0);

    // Calculate the denominator part of the equation
    double denominator = Cv * N1 * Fp * Math.sqrt(deltaP * rho);

    // Calculate percent valve opening
    double percentValveOpening = (massFlowRate / denominator) * 100;

    return percentValveOpening;
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
  public double liquidValvePout(double P1, double m, double rho, double Cv, double Fp,
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
  public double liquidValveCv(double P1, double P2, double rho, double m, double Fp,
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
   * Calculate the mass flow rate through a throttling valve.
   *
   * @param Pus Upstream pressure (in bar).
   * @param Pds Downstream pressure (in bar).
   * @param rhous Upstream density (in kg/m^3).
   * @param Cv Flow coefficient for the valve.
   * @param percentValveOpening Percentage of valve opening (0-100%).
   * @param isGasService Flag indicating if the service is gas (true) or liquid (false).
   * @return The calculated mass flow rate (in kg/s).
   */
  public double calcmassflow(double Pus, double Pds, double rhous, double Cv,
      double percentValveOpening, boolean isGasService) {

    double Cl = 30.0; // Define Cl value
    double sineFactor;

    if (isGasService) {
      // Gas service calculations
      sineFactor = Math.sin(Math.toRadians(3417 / Cl));
      double Cg = Cv * Cl; // Convert Cv to Cg
      // Calculate the mass flow rate using Cg
      double massFlowRate = 0.0457 * Math.sqrt(Pus * 100.0 * rhous) * sineFactor
          * Math.sqrt((Pus - Pds) / Pus) * Cg * percentValveOpening / 100.0;
      return massFlowRate;
    } else {
      // Liquid service calculations
      sineFactor = Math.sin(Math.toRadians(3417 / Cl));
      // Calculate the mass flow rate using Cv
      double massFlowRate = 0.0457 * Math.sqrt(Pus * 100.0 * rhous) * sineFactor
          * Math.sqrt((Pus - Pds) / Pus) * Cv * percentValveOpening / 100.0;
      return massFlowRate;
    }
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

    // Calculate the term that involves the mass flow rate, Cv, and percent valve
    // opening
    double flowTerm = (massFlowRate / (0.0457 * Math.sqrt(Pus * 100.0 * rhous) * sineFactor * Cv
        * (percentValveOpening / 100.0)));

    // Square the flowTerm to eliminate the square root
    double flowTermSquared = flowTerm * flowTerm;

    // Calculate Pds
    double Pds = Pus * (1 - flowTermSquared);

    return Pds;
  }

  /**
   * Calculates the valve flow coefficient (Cv) for a throttling valve.
   *
   * @param Pus Upstream pressure (in bar).
   * @param Pds Downstream pressure (in bar).
   * @param rhous Upstream fluid density (in kg/m³).
   * @param massFlowRate Mass flow rate through the valve (in kg/h).
   * @param percentValveOpening Percentage of valve opening (0-100%).
   * @return The calculated valve flow coefficient (Cv).
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
    double term = massFlowRate / (0.0457 * Math.sqrt(Pus * 100 * rhous) * sineFactor * Cv);

    // Calculate the percent valve opening
    double percentValveOpening = term / Math.sqrt(1 - (Pds / Pus)) * 100.0;

    return percentValveOpening;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("valve running..");
    // outStream.setSpecification(inletStream.getSpecification());
    if (getInletStream().getThermoSystem() != null) {
      thermoSystem = getInletStream().getThermoSystem().clone();
    } else {
      logger.error("Inlet stream thermo system is null");
      return;
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    double enthalpy = thermoSystem.getEnthalpy();
    inStream.getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double outp = 0.0;
    if (pressure == 0) {
      pressure = inStream.getThermoSystem().getPressure();
    }

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
    if (deltaPressure != 0) {
      thermoSystem.setPressure(thermoSystem.getPressure(pressureUnit) - deltaPressure,
          pressureUnit);
      setOutletPressure(thermoSystem.getPressure());
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
        || thermoSystem.getNumberOfMoles() < 1e-12 || pressure == 0) {
      thermoOps.TPflash();
    } else {
      thermoOps.PHflash(enthalpy, 0);
    }
    outStream.setThermoSystem(thermoSystem);
    // System.out.println("Total volume flow " +
    // Uncomment the line below to get the volume of the outlet stream's
    // thermodynamic system
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
    if (gasValve) {
      percentValveOpening = calcPercentValveOpening(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          inStream.getFlowRate("kg/hr"));
    } else {
      percentValveOpening = calcPercentValveOpeningLiquid(inStream.getFlowRate("kg/hr"),
          inStream.getThermoSystem().getPressure(), outStream.getThermoSystem().getPressure(),
          inStream.getFluid().getDensity("kg/m3"), Cv, Fp);
    }

    if (gasValve) {
      molarFlow = calcmassflow(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          percentValveOpening, true) / 3600.0 / inStream.getFluid().getMolarMass("kg/mol");
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
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    // inletStream.run(id);

    outStream.setThermoSystem(thermoSystem.clone());
    outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
    outStream.getThermoSystem().init(3);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  /**
   * This annotation is used to exclude the method from Jacoco code coverage reports.
   */
  @ExcludeFromJacocoGeneratedReport
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
    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    outStream.setThermoSystem(thermoSystem);

    if (gasValve) {
      molarFlow = calcmassflow(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          percentValveOpening, true) / 3600.0 / inStream.getFluid().getMolarMass("kg/mol");
    } else {
      molarFlow = liquidValveMassFlow(inStream.getThermoSystem().getPressure(),
          outStream.getThermoSystem().getPressure(), inStream.getFluid().getDensity("kg/m3"), Cv,
          Fp, percentValveOpening) / 3600.0 / inStream.getFluid().getMolarMass("kg/mol");
    }

    try {
      inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getThermoSystem().init(1);
      inStream.run(id);
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    try {
      outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      outStream.getThermoSystem().init(1);
      outStream.run(id);
    } catch (Exception ex) {
      logger.error(ex.getMessage());
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

  /**
   * <p>
   * setMinimumValveOpening.
   * </p>
   *
   * @param minopen a double
   */
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
  public double getCv(String unit) {
    if (unit.equals("US")) {
      return Cv / 54.9;
    } else if (unit.equalsIgnoreCase("SI") || unit.isEmpty()) {
      return Cv;
    } else {
      logger.warn("Invalid unit specified for getCv. Returning SI value.");
      return Cv;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setCv(double cv) {
    this.Cv = cv;
    valveCvSet = true;
  }

  /** {@inheritDoc} */
  @Override
  public void setCv(double cv, String unit) {
    if (unit.equals("US")) {
      this.Cv = cv * 54.9;
    } else {
      this.Cv = cv;
    }
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

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    valveMechanicalDesign = new ValveMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public ValveMechanicalDesign getMechanicalDesign() {
    return valveMechanicalDesign;
  }

  /**
   * <p>
   * setIsCalcOutPressure.
   * </p>
   *
   * @param isSetPres a boolean
   */
  public void setIsCalcOutPressure(boolean isSetPres) {
    isCalcPressure = isSetPres;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new ValveResponse(this));
  }

  /**
   * <p>
   * isGasValve.
   * </p>
   *
   * @return a boolean
   */
  public boolean isGasValve() {
    return gasValve;
  }

  /**
   * <p>
   * Setter for the field <code>gasValve</code>.
   * </p>
   *
   * @param gasValve a boolean
   */
  public void setGasValve(boolean gasValve) {
    this.gasValve = gasValve;
  }

  /**
   * <p>
   * getFp.
   * </p>
   *
   * @return a double
   */
  public double getFp() {
    return Fp;
  }

  /**
   * <p>
   * setFp.
   * </p>
   *
   * @param fp a double
   */
  public void setFp(double fp) {
    Fp = fp;
  }

  public double getDeltaPressure() {
    return deltaPressure;
  }

  public void setDeltaPressure(double deltaPressure, String unit) {
    this.deltaPressure = deltaPressure;
    this.pressureUnit = unit;
  }
}
