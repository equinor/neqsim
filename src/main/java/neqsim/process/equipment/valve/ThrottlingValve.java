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
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
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

  private boolean valveKvSet = false;

  private boolean isoThermal = false;

  double pressure = 0.0;
  private double Kv;
  private double maxMolarFlow = 1000.0;
  private double minMolarFlow = 0.0;
  private double maxValveOpening = 100.0;
  private double minValveOpening = 0.0;
  private double percentValveOpening = 100.0;
  private double requestedValveOpening = percentValveOpening;
  private double valveTravelTimeSec = 0.0;
  private double valveOpeningTravelTimeSec = Double.NaN;
  private double valveClosingTravelTimeSec = Double.NaN;
  private double valveTimeConstantSec = 0.0;
  private ValveTravelModel travelModel = ValveTravelModel.NONE;
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
    initMechanicalDesign();
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
   * calcKv.
   */
  public void calcKv() {
    Map<String, Object> design = getMechanicalDesign().calcValveSize();
    setKv((Double) design.get("Kv"));
  }

  /**
   * Calculates molar flow for a gas based on IEC 60534 standards. This method accounts for choked
   * (critical) flow.
   *
   * @return Molar flow in mole/sec.
   */
  public double calculateMolarFlow() {
    double flow_m3_sec = getMechanicalDesign().getValveSizingMethod()
        .calculateFlowRateFromValveOpening(adjustKv(Kv, percentValveOpening), inStream, outStream);

    return flow_m3_sec * inStream.getFluid().getDensity("kg/m3")
        / inStream.getFluid().getMolarMass("kg/mol");
  }

  /**
   * Calculates the outlet pressure based on the adjusted Kv value.
   *
   * @param KvAdjusted the adjusted flow coefficient (Kv)
   * @return the calculated outlet pressure
   */
  public double calculateOutletPressure(double KvAdjusted) {
    return getMechanicalDesign().getValveSizingMethod().findOutletPressureForFixedKv(KvAdjusted,
        inStream) / 1.0e5;
  }

  /**
   * Adjusts the flow coefficient (Kv) based on the percentage valve opening.
   *
   * @param Kv Flow coefficient SI (for 100% opening)
   * @param percentValveOpening Percentage valve opening (0 to 100).
   * @return Adjusted flow coefficient (Kv)
   */
  private double adjustKv(double Kv, double percentValveOpening) {
    return getMechanicalDesign().getValveCharacterizationMethod().getActualKv(Kv,
        percentValveOpening);
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

    if (!valveKvSet) {
      calcKv();
      valveKvSet = true;
    }
    inStream.getThermoSystem().initProperties();
    double enthalpy = thermoSystem.getEnthalpy();

    double outPres = getOutletStream().getThermoSystem().getPressure();
    double molarFlowStart = getInletStream().getThermoSystem().getFlowRate("mole/sec");
    // first estimate of flow from current outlet pressure
    // Calculate molar flow rate for gas directly here (without calling
    // calculateMolarFlowRateGas)
    double inletPressure = inStream.getThermoSystem().getPressure("bara");
    double outletPressure = outStream.getThermoSystem().getPressure("bara");

    double deltaP = Math.max(inletPressure - outletPressure, 0.0);
    if (deltaP > 0.0 && !isCalcPressure) {
      molarFlow = calculateMolarFlow();
    } else {
      molarFlow = 0.0;
    }

    // update outlet pressure if required
    if (valveKvSet && isCalcPressure) {
      outPres = calculateOutletPressure(adjustKv(Kv, percentValveOpening));
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
    thermoSystem.init(2);
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
    double adjustKv = adjustKv(Kv, percentValveOpening);
    if (deltaP > 0.0 && !isCalcPressure) {
      molarFlow = ensureValidMolarFlow(calculateMolarFlow());

      inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getThermoSystem().initProperties();
    }
    // update outlet pressure if required
    if (valveKvSet && isCalcPressure) {
      inStream.getFluid().initProperties();
      outPres = calculateOutletPressure(adjustKv);
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
      molarFlow = ensureValidMolarFlow(calculateMolarFlow());
    } else {
      molarFlow = minimumMolarFlow;
    }

    try {
      inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      inStream.getThermoSystem().initProperties();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    try {
      outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
      outStream.getThermoSystem().initProperties();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    setCalculationIdentifier(id);
  }

  private static final double minimumMolarFlow = 1e-100;

  private double ensureValidMolarFlow(double flow) {
    if (Double.isFinite(flow) && flow > minimumMolarFlow) {
      return flow;
    }
    return minimumMolarFlow;
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
    double controllerRequest = requestedValveOpening;
    if (hasController && getController().isActive()) {
      getController().runTransient(this.percentValveOpening, dt, id);
      controllerRequest = getController().getResponse();
    }
    controllerRequest = clampValveOpening(controllerRequest);
    setTargetPercentValveOpening(controllerRequest);
    percentValveOpening = clampValveOpening(
        applyTravelDynamics(percentValveOpening, requestedValveOpening, dt));
    setCalculationIdentifier(id);
  }

  private double clampValveOpening(double value) {
    return Math.min(maxValveOpening, Math.max(minValveOpening, value));
  }

  private double applyTravelDynamics(double current, double target, double dt) {
    if (travelModel == null || travelModel == ValveTravelModel.NONE) {
      return target;
    }

    double effectiveDt = Math.max(0.0, dt);
    switch (travelModel) {
      case LINEAR_RATE_LIMIT:
        double delta = target - current;
        if (Math.abs(delta) < 1e-12 || effectiveDt <= 0.0) {
          return target;
        }
        double travelTime = delta >= 0.0 ? getEffectiveOpeningTravelTime()
            : getEffectiveClosingTravelTime();
        if (travelTime <= 0.0) {
          return target;
        }
        double maxRate = 100.0 / travelTime;
        double maxChange = maxRate * effectiveDt;
        if (Math.abs(delta) <= maxChange) {
          return target;
        }
        return current + Math.copySign(maxChange, delta);
      case FIRST_ORDER_LAG:
        double tau = valveTimeConstantSec > 0.0 ? valveTimeConstantSec : valveTravelTimeSec;
        if (tau <= 0.0 || effectiveDt <= 0.0) {
          return target;
        }
        double alpha = 1.0 - Math.exp(-effectiveDt / tau);
        return current + alpha * (target - current);
      default:
        return target;
    }
  }

  private double getEffectiveOpeningTravelTime() {
    if (!Double.isNaN(valveOpeningTravelTimeSec)) {
      return Math.max(0.0, valveOpeningTravelTimeSec);
    }
    return Math.max(0.0, valveTravelTimeSec);
  }

  private double getEffectiveClosingTravelTime() {
    if (!Double.isNaN(valveClosingTravelTimeSec)) {
      return Math.max(0.0, valveClosingTravelTimeSec);
    }
    return Math.max(0.0, valveTravelTimeSec);
  }

  /**
   * <p>
   * setMinimumValveOpening.
   * </p>
   *
   * @param minopen a double
   */
  public void setMinimumValveOpening(double minopen) {
    minValveOpening = Math.max(0.0, Math.min(maxValveOpening, minopen));
    percentValveOpening = clampValveOpening(percentValveOpening);
    requestedValveOpening = clampValveOpening(requestedValveOpening);
  }

  /**
   * Sets the maximum valve opening in percent.
   *
   * @param maxopen a double representing the maximum permitted opening
   */
  public void setMaximumValveOpening(double maxopen) {
    maxValveOpening = Math.max(minValveOpening, Math.min(100.0, maxopen));
    percentValveOpening = clampValveOpening(percentValveOpening);
    requestedValveOpening = clampValveOpening(requestedValveOpening);
  }

  /** {@inheritDoc} */
  @Override
  public double getKv() {
    return Kv;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return getCv("US");
  }

  /** {@inheritDoc} */
  @Override
  public double getCv(String unit) {
    if (unit.equals("US")) {
      return Kv * 1.156;
    } else if (unit.equalsIgnoreCase("SI") || unit.isEmpty()) {
      return Kv;
    } else {
      logger.warn("Invalid unit specified for getKv. Returning SI value.");
      return Kv;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setCv(double Cv) {
    setCv(Cv, "US");
  }

  /** {@inheritDoc} */
  @Override
  public void setKv(double Kv) {
    setCv(Kv, "SI");
  }

  /** {@inheritDoc} */
  @Override
  public void setCv(double cv, String unit) {
    if (unit.equals("US")) {
      this.Kv = cv / 1.156;
    } else {
      this.Kv = cv;
    }
    valveKvSet = true;
  }

  /** {@inheritDoc} */
  @Override
  public void setTravelTime(double travelTimeSec) {
    valveTravelTimeSec = Math.max(0.0, travelTimeSec);
  }

  /** {@inheritDoc} */
  @Override
  public double getTravelTime() {
    return Math.max(0.0, valveTravelTimeSec);
  }

  /** {@inheritDoc} */
  @Override
  public void setOpeningTravelTime(double travelTimeSec) {
    valveOpeningTravelTimeSec = travelTimeSec > 0.0 ? travelTimeSec : Double.NaN;
  }

  /** {@inheritDoc} */
  @Override
  public double getOpeningTravelTime() {
    return getEffectiveOpeningTravelTime();
  }

  /** {@inheritDoc} */
  @Override
  public void setClosingTravelTime(double travelTimeSec) {
    valveClosingTravelTimeSec = travelTimeSec > 0.0 ? travelTimeSec : Double.NaN;
  }

  /** {@inheritDoc} */
  @Override
  public double getClosingTravelTime() {
    return getEffectiveClosingTravelTime();
  }

  /** {@inheritDoc} */
  @Override
  public void setTravelTimeConstant(double timeConstantSec) {
    valveTimeConstantSec = Math.max(0.0, timeConstantSec);
  }

  /** {@inheritDoc} */
  @Override
  public double getTravelTimeConstant() {
    return valveTimeConstantSec;
  }

  /** {@inheritDoc} */
  @Override
  public void setTravelModel(ValveTravelModel travelModel) {
    if (travelModel == null) {
      this.travelModel = ValveTravelModel.NONE;
    } else {
      this.travelModel = travelModel;
    }
  }

  /** {@inheritDoc} */
  @Override
  public ValveTravelModel getTravelModel() {
    return travelModel;
  }

  /** {@inheritDoc} */
  @Override
  public double getCg() {
    double Cl = 1360.0;
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
    double clamped = clampValveOpening(percentValveOpening);
    this.percentValveOpening = clamped;
    this.requestedValveOpening = clamped;
  }

  /** {@inheritDoc} */
  @Override
  public double getTargetPercentValveOpening() {
    return requestedValveOpening;
  }

  /** {@inheritDoc} */
  @Override
  public void setTargetPercentValveOpening(double percentValveOpening) {
    this.requestedValveOpening = clampValveOpening(percentValveOpening);
  }

  /**
   * <p>
   * isValveKvSet.
   * </p>
   *
   * @return a boolean
   */
  public boolean isValveKvSet() {
    return valveKvSet;
  }

  /**
   * <p>
   * Setter for the field <code>valveKvSet</code>.
   * </p>
   *
   * @param valveKvSet a boolean
   */
  public void setValveKvSet(boolean valveKvSet) {
    this.valveKvSet = valveKvSet;
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

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    ValveResponse res = new ValveResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
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

  /**
   * <p>
   * isAllowChoked.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAllowChoked() {
    return allowChoked;
  }

  /**
   * <p>
   * Setter for the field <code>allowChoked</code>.
   * </p>
   *
   * @param allowChoked a boolean
   */
  public void setAllowChoked(boolean allowChoked) {
    this.allowChoked = allowChoked;
  }

  /**
   * <p>
   * isAllowLaminar.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAllowLaminar() {
    return allowLaminar;
  }

  /**
   * <p>
   * Setter for the field <code>allowLaminar</code>.
   * </p>
   *
   * @param allowLaminar a boolean
   */
  public void setAllowLaminar(boolean allowLaminar) {
    this.allowLaminar = allowLaminar;
  }
}
