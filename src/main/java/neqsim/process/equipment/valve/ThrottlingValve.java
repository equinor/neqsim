package neqsim.process.equipment.valve;

import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.valve.ControlValveSizing;
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

  public void calcCv(SystemInterface fluid) {
    double xT = .137;
    double D1 = 8 * 0.0254;
    double D2 = 8 * 0.0254;
    double d = D1;
    double FL = 1.0;
    double FD = 1.0;

    if (fluid.hasPhaseType(PhaseType.GAS)) {
      Map<String, Object> result = neqsim.process.mechanicaldesign.valve.ControlValveSizing
          .sizeControlValveGas(fluid.getTemperature("K"), fluid.getMolarMass("gr/mol"),
              fluid.getViscosity("kg/msec"), fluid.getGamma2(), fluid.getZ(),
              getInletPressure() * 1e5, getOutletPressure() * 1e5, fluid.getFlowRate("Sm3/sec"), D1,
              D2, d, FL, FD, xT, true, true, true);
      this.Cv = (double) result.get("Cv");

    } else {
      Map<String, Object> result = neqsim.process.mechanicaldesign.valve.ControlValveSizing
          .sizeControlValveLiquid(fluid.getDensity("kg/m3"), 1.0 * 1e5,
              fluid.getPhase(0).getPseudoCriticalPressure() * 1e5, fluid.getViscosity("kg/msec"),
              getInletPressure() * 1e5, getOutletPressure() * 1e5,
              fluid.getFlowRate("kg/sec") / fluid.getDensity("kg/m3"), null, null, null, 1.0, 1.0,
              true, true, true);
      this.Cv = (double) result.get("Cv");
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

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {

    if (getInletStream().getThermoSystem() != null) {
      thermoSystem = getInletStream().getThermoSystem().clone();
    } else {
      logger.error("Inlet stream thermo system is null");
      return;
    }
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

        outp = ControlValveSizing.findOutletPressureForFixedCvGas(inStream.getTemperature(),
            inStream.getFluid().getMolarMass("gr/mol"), inStream.getFluid().getViscosity("kg/msec"),
            inStream.getFluid().getGamma2(), inStream.getFluid().getZ(),
            inStream.getThermoSystem().getPressure("Pa"), inStream.getFlowRate("Sm3/sec"), Cv,
            0.137, true) / 1e5;

      } else

      {
        outp = ControlValveSizing.findOutletPressureForFixedCvLiquid(
            inStream.getFluid().getDensity("kg/m3"), 1e5,
            inStream.getFluid().getPhase(0).getPseudoCriticalPressure() * 1e5,
            inStream.getFluid().getViscosity("kg/msec"),
            inStream.getThermoSystem().getPressure("Pa"),
            inStream.getFlowRate("kg/sec") / inStream.getFluid().getDensity("kg/m3"), Cv, 1.0, 1.0,
            true, true) / 1e5;
      }

      setOutletPressure(outp);
    }
    if (deltaPressure != 0)

    {
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

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    if (isIsoThermal() || Math.abs(pressure - inStream.getThermoSystem().getPressure()) < 1e-6
        || thermoSystem.getTotalNumberOfMoles() < 1e-12 || pressure == 0) {
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
      calcCv(thermoSystem);
      valveCvSet = true;
    }


    if (isGasValve()) {
      double xT = .137;
      double Q = inStream.getFlowRate("Sm3/sec");
      percentValveOpening = ControlValveSizing.calculateValveOpeningFromFlowRateGas(Q, Cv,
          getInletStream().getTemperature(), getInletStream().getFluid().getMolarMass("gr/mol"),
          getInletStream().getFluid().getViscosity("kg/msec"),
          getInletStream().getFluid().getGamma2(), getInletStream().getFluid().getZ(),
          inStream.getThermoSystem().getPressure("Pa"),
          outStream.getThermoSystem().getPressure("Pa"), 1.0, xT, true);
    } else {
      double Q = inStream.getFlowRate("kg/sec") / inStream.getFluid().getDensity("kg/m3");
      percentValveOpening = ControlValveSizing.calculateValveOpeningFromFlowRateLiquid(Q, Cv,
          inStream.getThermoSystem().getDensity("kg/m3"), 1.0e5,
          inStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
          inStream.getThermoSystem().getViscosity("kg/msec"),
          inStream.getThermoSystem().getPressure("Pa"),
          outStream.getThermoSystem().getPressure("Pa"), 1.0, 1.0, true);
    }
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
      molarFlow = ControlValveSizing.calculateFlowRateFromCvAndValveOpeningGas(Cv,
          percentValveOpening, inStream.getThermoSystem().getTemperature(),
          inStream.getFluid().getMolarMass("gr/mol"), inStream.getFluid().getViscosity("kg/msec"),
          inStream.getFluid().getGamma2(), inStream.getFluid().getZ(),
          inStream.getThermoSystem().getPressure() * 1e5,
          outStream.getThermoSystem().getPressure() * 1e5, 1.0, 0.137, true) * 101325.0 / 8.314
          / 288.15;
    } else {
      double oldFLow = inStream.getFlowRate("mole/sec");
      molarFlow = ControlValveSizing.calculateFlowRateFromValveOpeningLiquid(percentValveOpening,
          Cv, inStream.getThermoSystem().getDensity("kg/m3"), 1.0e5,
          inStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
          inStream.getThermoSystem().getViscosity("kg/msec"),
          inStream.getThermoSystem().getPressure("Pa"),
          outStream.getThermoSystem().getPressure("Pa"), 1.0, 1.0, true)
          * inStream.getThermoSystem().getDensity("kg/m3")
          / inStream.getThermoSystem().getMolarMass("kg/mol");
    }

    try {
      inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getFluid().init(1);
      inStream.run(id);
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    try {
      outStream.getFluid().setTotalNumberOfMoles(molarFlow);
      outStream.getFluid().init(1);
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
  public void setCg(double cg) {
    double Cl = 30.0;
    this.setCv(cg / Cl);
  }

  /** {@inheritDoc} */
  @Override
  public double getCg() {
    double Cl = 30.0;
    return getCv() * Cl;
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
