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
public class ThrottlingValve extends TwoPortEquipment
    implements ValveInterface, neqsim.process.equipment.capacity.CapacityConstrainedEquipment,
    neqsim.process.design.AutoSizeable {
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

  /** Flag indicating if valve has been auto-sized. */
  private boolean autoSized = false;

  /** Maximum design AIV (Acoustic-Induced Vibration) power level in kW. */
  private double maxDesignAIV = 10.0;

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
   * @param name        a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface}
   *                    object
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
  public double getCapacityDuty() {
    return getOutStream().getFlowRate("m3/hr");
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return getMechanicalDesign().maxDesignVolumeFlow;
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
   * @param unit     a {@link java.lang.String} object
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
   * @param unit     a {@link java.lang.String} object
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
   * Calculates molar flow for a gas based on IEC 60534 standards. This method
   * accounts for choked
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
   * @param Kv                  Flow coefficient SI (for 100% opening)
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

    double inletMolarFlow = getInletStream().getThermoSystem().getFlowRate("mole/sec");
    if (isNegligibleFlow(inletMolarFlow)) {
      applyZeroFlowState(id);
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

    double inletMolarFlow = inStream.getThermoSystem().getFlowRate("mole/sec");
    if (isNegligibleFlow(inletMolarFlow)) {
      applyZeroFlowState(id);
      return;
    }

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

      if (molarFlow > 0.0) {
        inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
        inStream.getThermoSystem().initProperties();
      }
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
      if (molarFlow <= 0.0) {
        applyZeroFlowState(id);
        return;
      }
    } else {
      applyZeroFlowState(id);
      return;
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

  private static final double minimumMolarFlow = 1e-12;

  private double ensureValidMolarFlow(double flow) {
    if (Double.isFinite(flow)) {
      if (flow > minimumMolarFlow) {
        return flow;
      }
      return 0.0;
    }
    return 0.0;
  }

  private boolean isNegligibleFlow(double flow) {
    return !Double.isFinite(flow) || Math.abs(flow) <= minimumMolarFlow;
  }

  private void applyZeroFlowState(UUID id) {
    molarFlow = 0.0;
    double targetPressure = pressure;
    if (!(targetPressure > 0.0)) {
      try {
        targetPressure = getOutletStream().getThermoSystem().getPressure(pressureUnit);
      } catch (Exception ex) {
        targetPressure = getInletStream().getThermoSystem().getPressure(pressureUnit);
      }
    }

    try {
      // Preserve inlet composition by keeping component mole fractions
      // before setting total moles to zero
      thermoSystem.setTotalNumberOfMoles(0.0);
      thermoSystem.init(0);
    } catch (Exception ex) {
      logger.debug("Unable to initialize zero-flow state: {}", ex.getMessage());
    }

    thermoSystem.setPressure(targetPressure, pressureUnit);
    outStream.setThermoSystem(thermoSystem);
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
    double controllerRequest = requestedValveOpening;
    if (hasController && getController().isActive()) {
      getController().runTransient(this.percentValveOpening, dt, id);
      controllerRequest = getController().getResponse();
    }
    controllerRequest = clampValveOpening(controllerRequest);
    setTargetPercentValveOpening(controllerRequest);
    percentValveOpening = clampValveOpening(applyTravelDynamics(percentValveOpening, requestedValveOpening, dt));
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
        double travelTime = delta >= 0.0 ? getEffectiveOpeningTravelTime() : getEffectiveClosingTravelTime();
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

  /**
   * Gets the maximum valve opening in percent.
   *
   * @return maximum valve opening percentage (0 to 100)
   */
  public double getMaximumValveOpening() {
    return maxValveOpening;
  }

  /**
   * Gets the minimum valve opening in percent.
   *
   * @return minimum valve opening percentage (0 to 100)
   */
  public double getMinimumValveOpening() {
    return minValveOpening;
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
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new ValveResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    ValveResponse res = new ValveResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
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
   * @param unit          a {@link java.lang.String} object
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

  // ============================================================================
  // Acoustic-Induced Vibration (AIV) Calculations
  // ============================================================================

  /**
   * Calculate Acoustic-Induced Vibration (AIV) power level.
   *
   * <p>
   * AIV is most significant at control valves where high pressure drops occur.
   * The acoustic power
   * generated is a function of mass flow rate, pressure drop, and temperature.
   * This method
   * implements the Energy Institute Guidelines formula for AIV screening.
   * </p>
   *
   * <p>
   * AIV Power Level interpretation (per Energy Institute Guidelines):
   * </p>
   * <ul>
   * <li>&lt; 1 kW: Low risk - no special measures required</li>
   * <li>1 - 10 kW: Medium risk - screening required</li>
   * <li>10 - 25 kW: High risk - detailed assessment required</li>
   * <li>&gt; 25 kW: Very high risk - design modifications needed</li>
   * </ul>
   *
   * @return AIV acoustic power level in kW
   */
  public double calculateAIV() {
    if (inStream == null || outStream == null) {
      return 0.0;
    }

    double p1 = inStream.getPressure("Pa"); // Upstream pressure in Pa
    double p2 = outStream.getPressure("Pa"); // Downstream pressure in Pa

    if (p1 <= 0 || p2 <= 0 || p1 <= p2) {
      return 0.0; // No pressure drop or invalid pressures
    }

    double tempK = inStream.getTemperature("K");
    double mdot = inStream.getFlowRate("kg/sec"); // Mass flow rate in kg/s

    // Pressure ratio for acoustic power calculation
    double pressureRatio = (p1 - p2) / p1;
    if (pressureRatio > 1.0) {
      pressureRatio = 1.0;
    }

    // Energy Institute formula for acoustic power level
    // W_acoustic = 3.2e-9 * mdot * P1 * (dP/P1)^3.6 * (T/273.15)^0.8
    double acousticPowerWatts = 3.2e-9 * mdot * p1 * Math.pow(pressureRatio, 3.6) * Math.pow(tempK / 273.15, 0.8);

    return acousticPowerWatts / 1000.0; // Return in kW
  }

  /**
   * Calculate AIV Likelihood of Failure based on acoustic power and downstream
   * pipe geometry.
   *
   * <p>
   * AIV LOF interpretation:
   * </p>
   * <ul>
   * <li>&lt; 0.3: Low risk</li>
   * <li>0.3 - 0.5: Medium risk - monitoring recommended</li>
   * <li>0.5 - 0.7: High risk - detailed assessment required</li>
   * <li>&gt; 0.7: Very high risk - design changes needed</li>
   * </ul>
   *
   * @param downstreamDiameter  downstream pipe diameter in meters
   * @param downstreamThickness downstream pipe wall thickness in meters
   * @return AIV likelihood of failure (0.0-1.0)
   */
  public double calculateAIVLikelihoodOfFailure(double downstreamDiameter,
      double downstreamThickness) {
    double acousticPowerKW = calculateAIV();

    double externalDiameter = downstreamDiameter + 2 * downstreamThickness;
    double dtRatio = externalDiameter / downstreamThickness;

    // Screening parameter: acoustic power * (D/t)^2
    double screeningParam = acousticPowerKW * 1000 * Math.pow(dtRatio, 2); // Convert to W

    // LOF based on screening parameter thresholds
    if (screeningParam < 1e4) {
      return 0.1;
    } else if (screeningParam < 1e5) {
      return 0.3;
    } else if (screeningParam < 1e6) {
      return 0.6;
    } else {
      return 0.9;
    }
  }

  /**
   * Set maximum design AIV power level for capacity constraints.
   *
   * @param aivKW maximum AIV power level in kW
   */
  public void setMaxDesignAIV(double aivKW) {
    this.maxDesignAIV = aivKW;
  }

  /**
   * Get maximum design AIV power level.
   *
   * @return maximum AIV power level in kW
   */
  public double getMaxDesignAIV() {
    return maxDesignAIV;
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /** Storage for capacity constraints. */
  private final java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> capacityConstraints = new java.util.LinkedHashMap<>();

  /**
   * Initializes default capacity constraints for the valve.
   *
   * <p>
   * NOTE: All constraints are disabled by default for backwards compatibility.
   * Enable specific
   * constraints when valve capacity analysis is needed (e.g., when Cv has been
   * sized).
   * </p>
   */
  protected void initializeCapacityConstraints() {
    // Cv utilization constraint (HARD limit) - disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("cvUtilization",
        "", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(getMechanicalDesign().maxDesignCv).setWarningThreshold(0.9)
        .setDescription("Cv utilization vs design maximum").setValueSupplier(() -> getCv())
        .setEnabled(false));

    // Volume flow constraint (DESIGN limit) - disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("volumeFlow",
        "m3/hr", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN)
        .setDesignValue(getMechanicalDesign().maxDesignVolumeFlow).setWarningThreshold(0.9)
        .setDescription("Volume flow vs design maximum").setValueSupplier(
            () -> getOutStream() != null ? getOutStream().getFlowRate("m3/hr") : 0.0)
        .setEnabled(false));

    // Valve opening constraint (SOFT limit) - disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("valveOpening",
        "%", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
        .setDesignValue(maxValveOpening).setWarningThreshold(0.9)
        .setDescription("Valve opening percentage")
        .setValueSupplier(() -> percentValveOpening)
        .setEnabled(false));

    // AIV (Acoustic-Induced Vibration) constraint - critical for control valves -
    // disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("AIV", "kW",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
        .setDesignValue(maxDesignAIV).setMaxValue(50.0).setWarningThreshold(0.4)
        .setDescription("AIV acoustic power (<1kW=low, 1-10kW=medium, >25kW=very high risk)")
        .setValueSupplier(() -> calculateAIV())
        .setEnabled(false));
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    if (capacityConstraints.isEmpty()) {
      initializeCapacityConstraints();
    }
    return java.util.Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      if (c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(
      neqsim.process.equipment.capacity.CapacityConstraint constraint) {
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    capacityConstraints.clear();
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /**
   * Default design opening percentage for valve sizing (50% for good control
   * range).
   */
  private static final double DESIGN_OPENING_PERCENT = 50.0;

  /** Minimum design Cv when flow is zero or very low. */
  private static final double MIN_DEFAULT_CV = 1.0;

  /**
   * Auto-sizes the valve based on current flow conditions.
   *
   * <p>
   * This method calculates the required Cv value so that the valve operates at
   * approximately 50%
   * opening at the current flow rate. This provides good control range - the
   * valve can open further
   * for higher flows or close for lower flows.
   * </p>
   *
   * <p>
   * Design philosophy:
   * </p>
   * <ul>
   * <li>At normal flow, valve should be at ~50% opening (design point)</li>
   * <li>Maximum Cv (100% opening) = Cv needed at 50% opening * 2
   * (approximately)</li>
   * <li>This gives control range from ~25% to 100% opening for typical flow
   * variations</li>
   * <li>For zero flow valves (bypass, emergency), uses minimum default Cv or
   * estimates from
   * connected equipment</li>
   * </ul>
   *
   * @param safetyFactor safety factor to apply (e.g., 1.2 for 20% margin)
   */
  @Override
  public void autoSize(double safetyFactor) {
    autoSize(safetyFactor, DESIGN_OPENING_PERCENT);
  }

  /**
   * Auto-sizes the valve based on current flow conditions with specified design
   * opening.
   *
   * @param safetyFactor         safety factor to apply (e.g., 1.2 for 20% margin)
   * @param designOpeningPercent the target valve opening percentage at design
   *                             flow (typically 50%)
   */
  public void autoSize(double safetyFactor, double designOpeningPercent) {
    if (getInletStream() == null) {
      throw new IllegalStateException("Cannot auto-size valve without inlet stream");
    }

    // Run the valve first to establish operating conditions
    run();

    // Check if we have meaningful flow
    double flowRate = getInletStream().getFlowRate("kg/hr");
    boolean hasFlow = flowRate > 1e-6; // More than 1 mg/hr

    double designCv;

    if (hasFlow) {
      // Calculate Cv at 100% opening for current flow
      getMechanicalDesign().calcDesign();
      double calculatedCv = getMechanicalDesign().getValveCvMax();

      if (calculatedCv <= 0 || Double.isNaN(calculatedCv)) {
        // Fallback if calculation fails
        calculatedCv = estimateCvFromFlow(flowRate);
      }

      // The calculated Cv is for 100% opening
      // To have the valve at designOpeningPercent at current flow,
      // we need to size the valve Cv larger
      // For equal-percentage characteristic: Cv_actual = Cv_100 * R^((opening-1))
      // Simplified: Cv needed at 100% ≈ Cv at design opening / opening factor
      double openingFactor = getMechanicalDesign().getValveCharacterizationMethod()
          .getOpeningFactor(designOpeningPercent);

      // designCv is the Cv at 100% opening such that at current flow,
      // the valve operates at designOpeningPercent
      designCv = calculatedCv / openingFactor;

      // Apply safety factor
      designCv = designCv * safetyFactor;

      logger.info("Valve {} auto-sized: flow={} kg/hr, Cv@100%={}, designOpening={}%, designCv={}",
          getName(), flowRate, calculatedCv, designOpeningPercent, designCv);
    } else {
      // Zero flow valve - estimate based on connected equipment or use default
      designCv = estimateCvForZeroFlowValve(safetyFactor);
      logger.info("Valve {} auto-sized for zero flow: using default/estimated Cv={}", getName(),
          designCv);
    }

    // Ensure minimum Cv
    designCv = Math.max(designCv, MIN_DEFAULT_CV);

    // Set the Cv on the valve (this is what controls valve sizing)
    setCv(designCv);

    // Also set maxDesignCv for capacity constraint tracking
    getMechanicalDesign().setMaxDesignCv(designCv);

    // Set the valve opening to the design point for meaningful utilization
    if (hasFlow) {
      setPercentValveOpening(designOpeningPercent);
    }

    // Set volume flow design value
    if (getOutStream() != null) {
      double volumeFlow = getOutStream().getFlowRate("m3/hr");
      if (volumeFlow > 0) {
        getMechanicalDesign().maxDesignVolumeFlow = volumeFlow * safetyFactor;
      }
    }

    // Clear and reinitialize capacity constraints with new design values
    capacityConstraints.clear();
    initializeCapacityConstraints();

    autoSized = true;
  }

  /**
   * Estimates Cv from flow rate using simplified correlation.
   *
   * <p>
   * This is a rough estimate when the standard calculation fails. Uses typical
   * valve sizing rules
   * of thumb.
   * </p>
   *
   * @param flowRateKghr mass flow rate in kg/hr
   * @return estimated Cv value
   */
  private double estimateCvFromFlow(double flowRateKghr) {
    // Rule of thumb: For liquids, Cv ≈ Q (gpm) for water at 1 psi drop
    // For gases, Cv ≈ Q (scfh) / 1360 at critical flow
    // Use a conservative estimate based on mass flow
    if (isGasValve()) {
      // Gas: assume typical conditions, rough estimate
      // Cv ≈ mass flow (kg/hr) / 50 (very rough rule of thumb)
      return Math.max(flowRateKghr / 50.0, MIN_DEFAULT_CV);
    } else {
      // Liquid: assume water-like, rough estimate
      // Cv ≈ volume flow (m3/hr) * 0.865 for water at 1 bar drop
      double volumeFlow = flowRateKghr / 1000.0; // Approximate for water-like
      return Math.max(volumeFlow * 0.865, MIN_DEFAULT_CV);
    }
  }

  /**
   * Estimates Cv for a valve with zero or negligible flow.
   *
   * <p>
   * For valves like bypass valves, emergency relief valves, or startup valves
   * that normally have no
   * flow, this method estimates an appropriate Cv based on:
   * </p>
   * <ul>
   * <li>Connected stream design conditions (if available)</li>
   * <li>Pressure drop across the valve</li>
   * <li>Default minimum values based on valve type</li>
   * </ul>
   *
   * @param safetyFactor safety factor to apply
   * @return estimated design Cv
   */
  private double estimateCvForZeroFlowValve(double safetyFactor) {
    double estimatedCv = MIN_DEFAULT_CV;

    // Try to estimate from pressure conditions
    if (getInletStream() != null && getOutStream() != null) {
      double p1 = getInletStream().getPressure("bara");
      double p2 = getOutStream().getPressure("bara");
      double dp = Math.abs(p1 - p2);

      if (dp > 0.1) {
        // There's a pressure drop defined - estimate Cv for potential flow
        // Use a conservative estimate based on typical design flow for the pipe size
        // Assume the valve might need to pass up to 10% of typical process flow
        // For now, use pressure-based scaling
        if (isGasValve()) {
          // For gas, Cv roughly scales with sqrt(P1^2 - P2^2) / P1
          // Use conservative estimate: Cv = 10 * sqrt(dp) for small valves
          estimatedCv = 10.0 * Math.sqrt(dp);
        } else {
          // For liquid, Cv roughly scales with sqrt(dp)
          estimatedCv = 5.0 * Math.sqrt(dp);
        }
      }
    }

    // Apply safety factor and ensure minimum
    estimatedCv = estimatedCv * safetyFactor;
    return Math.max(estimatedCv, MIN_DEFAULT_CV * safetyFactor);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2); // Default 20% safety factor
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String company, String trDocument) {
    // For now, use default safety factor
    // In the future, look up company-specific sizing requirements
    double safetyFactor = 1.2;

    // Company-specific safety factors could be loaded from database
    if ("Equinor".equalsIgnoreCase(company)) {
      safetyFactor = 1.25; // Example: Equinor requires 25% margin
    }

    autoSize(safetyFactor);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder report = new StringBuilder();
    report.append("Valve Sizing Report for: ").append(getName()).append("\n");
    report.append("=========================================\n");

    if (!autoSized) {
      report.append("Valve has not been auto-sized.\n");
      return report.toString();
    }

    report.append("Auto-sized: Yes\n");

    // Operating conditions
    if (getInletStream() != null) {
      report.append("\nOperating Conditions:\n");
      report.append(String.format("  Inlet Pressure: %.2f bara\n", getInletPressure()));
      report.append(String.format("  Outlet Pressure: %.2f bara\n", getOutletPressure()));
      report.append(String.format("  Pressure Drop: %.2f bar\n", getInletPressure() - pressure));
      report.append(
          String.format("  Flow Rate: %.2f kg/hr\n", getInletStream().getFlowRate("kg/hr")));
      report.append(String.format("  Valve Opening: %.1f%%\n", percentValveOpening));
    }

    // Valve sizing results
    report.append("\nSizing Results:\n");
    report.append(String.format("  Cv (max): %.2f\n", getMechanicalDesign().getValveCvMax()));
    report.append(
        String.format("  Nominal Size: %.0f inch\n", getMechanicalDesign().getNominalSizeInches()));
    report
        .append(String.format("  ANSI Class: %d\n", getMechanicalDesign().getAnsiPressureClass()));
    report
        .append(String.format("  Face-to-Face: %.1f mm\n", getMechanicalDesign().getFaceToFace()));

    // Weight
    report.append("\nWeight Estimate:\n");
    report.append(String.format("  Body Weight: %.1f kg\n", getMechanicalDesign().getBodyWeight()));
    report.append(
        String.format("  Actuator Weight: %.1f kg\n", getMechanicalDesign().getActuatorWeight()));

    return report.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    java.util.Map<String, Object> reportData = new java.util.LinkedHashMap<String, Object>();

    reportData.put("equipmentName", getName());
    reportData.put("equipmentType", "ThrottlingValve");
    reportData.put("autoSized", autoSized);

    if (autoSized && getInletStream() != null) {
      // Operating conditions
      java.util.Map<String, Object> operating = new java.util.LinkedHashMap<String, Object>();
      operating.put("inletPressure_bara", getInletPressure());
      operating.put("outletPressure_bara", getOutletPressure());
      operating.put("pressureDrop_bar", getInletPressure() - pressure);
      operating.put("flowRate_kghr", getInletStream().getFlowRate("kg/hr"));
      operating.put("valveOpening_percent", percentValveOpening);
      reportData.put("operatingConditions", operating);

      // Sizing results
      java.util.Map<String, Object> sizing = new java.util.LinkedHashMap<String, Object>();
      sizing.put("cvMax", getMechanicalDesign().getValveCvMax());
      sizing.put("nominalSize_inch", getMechanicalDesign().getNominalSizeInches());
      sizing.put("ansiClass", getMechanicalDesign().getAnsiPressureClass());
      sizing.put("faceToFace_mm", getMechanicalDesign().getFaceToFace());
      reportData.put("sizingResults", sizing);

      // Weight
      java.util.Map<String, Object> weight = new java.util.LinkedHashMap<String, Object>();
      weight.put("bodyWeight_kg", getMechanicalDesign().getBodyWeight());
      weight.put("actuatorWeight_kg", getMechanicalDesign().getActuatorWeight());
      reportData.put("weightEstimate", weight);
    }

    return new GsonBuilder().setPrettyPrinting().create().toJson(reportData);
  }
}
