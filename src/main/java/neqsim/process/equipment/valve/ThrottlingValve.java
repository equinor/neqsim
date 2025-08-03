package neqsim.process.equipment.valve;

import java.util.Map;
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
  private boolean allowChoked = false;
  private boolean allowLaminar = true;
  private double xt = 0.6; // critical pressure drop ratio for choked flow

  /**
   * * Constructor for ThrottlingValve.
   *
   * @param name name of valve
   */
  public ThrottlingValve(String name) {
    super(name);
    setCalculateSteadyState(true);
    initMechanicalDesign();

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
    valveMechanicalDesign = new ValveMechanicalDesign(this);
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
   * <p>
   * calcCv.
   * </p>
   *
   */
  public void calcCv() {
    // Map<String, Object> result = getMechanicalDesign().calcValveSize();
    // this.Cv = (double) result.get("Cv");
    Map<String, Object> design = getMechanicalDesign().calcValveSize();
    this.Cv = (double) design.get("Kv");
  }

  /**
   * Calculates molar flow for a gas based on IEC 60534 standards. This method accounts for choked
   * (critical) flow.
   *
   * @param kvAdjusted The Kv value adjusted for valve opening.
   * @return Molar flow in mole/sec.
   */
  public double calculateMolarFlow(double kvAdjusted) {
    double flow_m3_sec = getMechanicalDesign().getValveSizingMethod()
        .calculateFlowRateFromValveOpening(Cv, percentValveOpening, inStream, outStream);

    return flow_m3_sec * inStream.getFluid().getDensity("kg/m3")
        / inStream.getFluid().getMolarMass("kg/mol");
  }

  public double calculateOutletPressure(double CvAdjusted) {
    // If calculateOutletPressure is not defined, use calculateOutletPressureFromCv if available
    return getMechanicalDesign().getValveSizingMethod().findOutletPressureForFixedCv(Cv,
        percentValveOpening, inStream);
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


  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (getInletStream().getThermoSystem() != null) {
      thermoSystem = getInletStream().getThermoSystem().clone();
    } else {
      logger.error("Inlet stream thermo system is null");
      return;
    }
    thermoSystem.initProperties();

    if (thermoSystem.hasPhaseType(PhaseType.GAS) && thermoSystem.getVolumeFraction(0) > 0.5) {
      setGasValve(true);
    } else {
      setGasValve(false);
    }

    if (!valveCvSet) {
      calcCv();
      valveCvSet = true;
    }

    inStream.getThermoSystem().initProperties();
    double enthalpy = thermoSystem.getEnthalpy();

    double outPres = getOutletStream().getThermoSystem().getPressure();
    double molarFlowStart = getInletStream().getThermoSystem().getFlowRate("mole/sec");
    // first estimate of flow from current outlet pressure
    // Calculate molar flow rate for gas directly here (without calling calculateMolarFlowRateGas)
    double CvAdjusted = adjustCv(Cv, percentValveOpening);
    double inletPressure = inStream.getThermoSystem().getPressure("bara");
    double outletPressure = outStream.getThermoSystem().getPressure("bara");

    double deltaP = Math.max(inletPressure - outletPressure, 0.0);
    if (deltaP > 0.0 && !isCalcPressure) {
      molarFlow = calculateMolarFlow(CvAdjusted);
    } else {
      molarFlow = 0.0;
    }

    // update outlet pressure if required
    if (valveCvSet && isCalcPressure) {
      outPres = calculateOutletPressure(CvAdjusted);
      setOutletPressure(outPres);
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

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    if (isIsoThermal() || Math.abs(pressure - inStream.getThermoSystem().getPressure()) < 1e-6
        || thermoSystem.getTotalNumberOfMoles() < 1e-12 || pressure == 0) {
      thermoSystem.setPressure(outPres, pressureUnit);
      thermoOps.TPflash();
    } else {
      thermoOps.PHflash(enthalpy, 0);
    }
    outStream.setThermoSystem(thermoSystem);

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
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

    inStream.getFluid().initProperties();
    thermoSystem = inStream.getThermoSystem().clone();
    thermoSystem.init(3);
    double enthalpy = thermoSystem.getEnthalpy();

    double outPres = getOutletStream().getThermoSystem().getPressure();
    pressure = outPres;
    double deltaP = Math.max(inStream.getPressure() - outPres, 0.0);

    if ((thermoSystem.getPressure(pressureUnit) - outPres) < 0) {
      if (isAcceptNegativeDP()) {
        thermoSystem.setPressure(outPres, pressureUnit);
      }
    } else {
      thermoSystem.setPressure(outPres, pressureUnit);
    }

    if (getSpecification().equals("out stream")) {
      thermoSystem.setPressure(outStream.getPressure(), pressureUnit);
    }
    double adjustCv = adjustCv(Cv, percentValveOpening);
    if (deltaP > 0.0 && !isCalcPressure) {
      molarFlow = calculateMolarFlow(adjustCv);

      // inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getThermoSystem().setTotalFlowRate(molarFlow, "mole/sec");
      inStream.getThermoSystem().init(1);
      inStream.run(id);
    }
    // update outlet pressure if required
    if (valveCvSet && isCalcPressure) {
      inStream.getFluid().initProperties();
      outPres = calculateOutletPressure(adjustCv);
      thermoSystem.setPressure(outPres);
      setOutletPressure(outPres);
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    if (isIsoThermal()) {
      thermoOps.TPflash();
    } else {
      thermoOps.PHflash(enthalpy, 0);
    }
    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    outStream.setThermoSystem(thermoSystem);


    if (deltaP > 0.0) {
      molarFlow = calculateMolarFlow(adjustCv);
    } else {
      molarFlow = 0.0;
    }

    try {
      // inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getThermoSystem().setTotalFlowRate(molarFlow, "mole/sec");
      inStream.getThermoSystem().init(1);
      inStream.run(id);
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    try {
      // outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      outStream.getThermoSystem().setTotalFlowRate(molarFlow, "mole/sec");
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
      return Cv * 1.156;
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
      this.Cv = cv / 1.156;
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

  /**
   * <p>
   * Getter for the field <code>deltaPressure</code>.
   * </p>
   *
   * @return a double
   */
  public double getDeltaPressure() {
    return deltaPressure;
  }

  /**
   * <p>
   * Setter for the field <code>deltaPressure</code>.
   * </p>
   *
   * @param deltaPressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setDeltaPressure(double deltaPressure, String unit) {
    this.deltaPressure = deltaPressure;
    this.pressureUnit = unit;
  }

  public boolean isAllowChoked() {
    return allowChoked;
  }

  public void setAllowChoked(boolean allowChoked) {
    this.allowChoked = allowChoked;
  }

  public boolean isAllowLaminar() {
    return allowLaminar;
  }

  public void setAllowLaminar(boolean allowLaminar) {
    this.allowLaminar = allowLaminar;
  }
}
