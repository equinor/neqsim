package neqsim.processSimulation.processEquipment.pump;



import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.UUID;




import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Pump class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Pump extends TwoPortEquipment implements PumpInterface {
  private static final long serialVersionUID = 1000;

  SystemInterface thermoSystem;
  double dH = 0.0;
  double pressure = 0.0;
  private double molarFlow = 10.0;
  private double speed = 1000.0;

  private double outTemperature = 298.15;
  private boolean useOutTemperature = false;
  private boolean calculateAsCompressor = true;
  public double isentropicEfficiency = 1.0;
  public boolean powerSet = false;
  private String pressureUnit = "bara";
  //private PumpChart pumpChart = new PumpChart();

  /**
   * <p>
   * Constructor for Pump.
   * </p>
   */
  @Deprecated
  public Pump() {
    super("Pump");
  }

  /**
   * <p>
   * Constructor for Pump.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public Pump(StreamInterface inletStream) {
    this();
    setInletStream(inletStream);
  }

  /**
   * Constructor for Pump.
   *
   * @param name name of pump
   */
  public Pump(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Pump.
   * </p>
   *
   * @param name name of pump
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public Pump(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    this.inStream = stream;
    this.outStream = stream.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.pressure = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnergy() {
    return dH;
  }

  /** {@inheritDoc} */
  @Override
  public double getPower() {
    return dH;
  }

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPower(String unit) {
    if (unit.equals("W")) {
      return dH;
    } else if (unit.equals("kW")) {
      return dH / 1000.0;
    } else if (unit.equals("MW")) {
      return dH / 1.0e6;
    }
    return dH;
  }

  /**
   * <p>
   * getDuty.
   * </p>
   *
   * @return a double
   */
  public double getDuty() {
    return dH;
  }

  /**
   * <p>
   * calculateAsCompressor.
   * </p>
   *
   * @param setPumpCalcType a boolean
   */
  public void calculateAsCompressor(boolean setPumpCalcType) {
    calculateAsCompressor = setPumpCalcType;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("pump running..");
    inStream.getThermoSystem().init(3);
    double hinn = inStream.getThermoSystem().getEnthalpy();
    double entropy = inStream.getThermoSystem().getEntropy();

    if (useOutTemperature) {
      thermoSystem = inStream.getThermoSystem().clone();
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
      // thermoSystem.setTotalNumberOfMoles(molarFlow);
      thermoSystem.setTemperature(outTemperature);
      thermoSystem.setPressure(pressure, pressureUnit);
      thermoOps.TPflash();
      thermoSystem.init(3);
    } else {
      /*
      if (!pumpChart.isUsePumpChart() && calculateAsCompressor) {
        thermoSystem = inStream.getThermoSystem().clone();
        thermoSystem.setPressure(pressure, pressureUnit);
        // System.out.println("entropy inn.." + entropy);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.PSflash(entropy);
        // double densOutIdeal = getThermoSystem().getDensity();
        if (!powerSet) {
          dH = (getThermoSystem().getEnthalpy() - hinn) / isentropicEfficiency;
        }
        double hout = hinn + dH;
        isentropicEfficiency = (getThermoSystem().getEnthalpy() - hinn) / dH;
        dH = hout - hinn;
        thermoOps = new ThermodynamicOperations(getThermoSystem());
        thermoOps.PHflash(hout, 0);
      } else if (pumpChart.isUsePumpChart()) {
        thermoSystem = inStream.getThermoSystem().clone();
        double pumpHead = 0.0;
        pumpHead = getPumpChart().getHead(thermoSystem.getFlowRate("m3/hr"), getSpeed());
        isentropicEfficiency =
            getPumpChart().getEfficiency(thermoSystem.getFlowRate("m3/hr"), getSpeed());
        double deltaP = pumpHead * 1000.0 * ThermodynamicConstantsInterface.gravity / 1.0E5;
        thermoSystem = inStream.getThermoSystem().clone();
        thermoSystem.setPressure(inStream.getPressure() + deltaP);
        double dH = thermoSystem.getFlowRate("kg/sec") / thermoSystem.getDensity("kg/m3")
            * (thermoSystem.getPressure("Pa") - inStream.getThermoSystem().getPressure("Pa"))
            / (isentropicEfficiency / 100.0);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
        double hout = hinn + dH;
        thermoOps.PHflash(hout, 0);
        thermoSystem.init(3);
      } else {
        thermoSystem = inStream.getThermoSystem().clone();
        thermoSystem.setPressure(pressure, pressureUnit);
        double dH = thermoSystem.getFlowRate("kg/sec") / thermoSystem.getDensity("kg/m3")
            * (thermoSystem.getPressure("Pa") - inStream.getThermoSystem().getPressure("Pa"))
            / isentropicEfficiency;
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
        double hout = hinn + dH;
        thermoOps.PHflash(hout, 0);
        thermoSystem.init(3);
      }
      */
    }

    // double entropy= inletStream.getThermoSystem().getEntropy();
    // thermoSystem.setPressure(pressure);
    // System.out.println("entropy inn.." + entropy);
    // thermoOps.PSflash(entropy);
    dH = thermoSystem.getEnthalpy() - hinn;
    outStream.setThermoSystem(thermoSystem);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    // outStream.run(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}

  /**
   * <p>
   * Getter for the field <code>molarFlow</code>.
   * </p>
   *
   * @return a double
   */
  public double getMolarFlow() {
    return molarFlow;
  }

  /**
   * <p>
   * Setter for the field <code>molarFlow</code>.
   * </p>
   *
   * @param molarFlow a double
   */
  public void setMolarFlow(double molarFlow) {
    this.molarFlow = molarFlow;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /**
   * <p>
   * Getter for the field <code>isentropicEfficiency</code>.
   * </p>
   *
   * @return the isentropicEfficiency
   */
  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  /**
   * <p>
   * Setter for the field <code>isentropicEfficiency</code>.
   * </p>
   *
   * @param isentropicEfficiency the isentropicEfficiency to set
   */
  public void setIsentropicEfficiency(double isentropicEfficiency) {
    this.isentropicEfficiency = isentropicEfficiency;
  }

  /**
   * <p>
   * Getter for the field <code>outTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getOutTemperature() {
    if (useOutTemperature) {
      return outTemperature;
    } else {
      return getThermoSystem().getTemperature();
    }
  }

  /**
   * <p>
   * Setter for the field <code>outTemperature</code>.
   * </p>
   *
   * @param outTemperature a double
   */
  public void setOutTemperature(double outTemperature) {
    useOutTemperature = true;
    this.outTemperature = outTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return outStream.getThermoSystem().getEntropy(unit)
        - inStream.getThermoSystem().getEntropy(unit);
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
    setOutletPressure(pressure);
    pressureUnit = unit;
  }

  /**
   * <p>
   * Setter for the field <code>speed</code>.
   * </p>
   *
   * @param speed a double
   */
  public void setSpeed(double speed) {
    this.speed = speed;
  }

  /**
   * <p>
   * Getter for the field <code>speed</code>.
   * </p>
   *
   * @return a double
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * <p>
   * Getter for the field <code>pumpChart</code>.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.processEquipment.pump.PumpChart} object
   */
  /*
  public PumpChart getPumpChart() {
    return pumpChart;
  }
  */
}
