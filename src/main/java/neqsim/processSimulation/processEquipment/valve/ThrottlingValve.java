package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
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

  protected String name = new String();
  private boolean valveCvSet = false, isoThermal = false;
  SystemInterface thermoSystem;
  double pressure = 0.0;
  private double Cv = 1.0;
  private double maxMolarFlow = 1000.0;
  private double minMolarFlow = 0.0;
  private double percentValveOpening = 100.0;
  double molarFlow = 0.0;
  private String pressureUnit = "bara";
  private boolean acceptNegativeDP = true;

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
  public void run() {
    // System.out.println("valve running..");
    // outStream.setSpecification(inletStream.getSpecification());
    thermoSystem = getInletStream().getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    double enthalpy = thermoSystem.getEnthalpy();
    if ((thermoSystem.getPressure(pressureUnit) - pressure) < 0) {
      if (isAcceptNegativeDP())
        thermoSystem.setPressure(pressure, pressureUnit);
    } else {
      thermoSystem.setPressure(pressure, pressureUnit);
    }

    if (getSpecification().equals("out stream")) {
      thermoSystem.setPressure(outStream.getPressure(), pressureUnit);
    }
    // System.out.println("enthalpy inn.." + enthalpy);
    // thermoOps.PHflash(enthalpy, 0);
    if (isIsoThermal() || Math.abs(pressure - inStream.getThermoSystem().getPressure()) < 1e-6) {
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
      Cv = inStream.getThermoSystem().getTotalNumberOfMoles() / (getPercentValveOpening() / 100.0
          * Math.sqrt(
              (inStream.getThermoSystem().getPressure() - outStream.getThermoSystem().getPressure())
                  / thermoSystem.getDensity()));
    }
    molarFlow = getCv() * getPercentValveOpening() / 100.0
        * Math.sqrt(
            (inStream.getThermoSystem().getPressure() - outStream.getThermoSystem().getPressure())
                / thermoSystem.getDensity());
    if (Math.abs(pressure - inStream.getThermoSystem().getPressure()) < 1e-6) {
      molarFlow = inStream.getThermoSystem().getTotalNumberOfMoles();
    }
    // System.out.println("molar flow " + molarFlow);

    inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
    inStream.getThermoSystem().init(3);
    // inletStream.run();

    outStream.setThermoSystem(thermoSystem.clone());
    outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
    outStream.getThermoSystem().init(3);
    // outStream.run();
    // Cv =
    // inletStream.getThermoSystem().getTotalNumberOfMoles()/Math.sqrt(inletStream.getPressure()-outStream.getPressure());
    // molarFlow = inletStream.getThermoSystem().getTotalNumberOfMoles();
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
  public void runTransient(double dt) {
    if (getCalculateSteadyState()) {
      run();
      return;
    }

    runController(dt);

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
    // if(getPercentValveOpening()<99){
    molarFlow = getCv() * getPercentValveOpening() / 100.0
        * Math.sqrt(
            (inStream.getThermoSystem().getPressure() - outStream.getThermoSystem().getPressure())
                / thermoSystem.getDensity());
    // System.out.println("molar flow " + molarFlow);
    // System.out.println("Cv " + getCv());
    // System.out.println("density " + inletStream.getThermoSystem().getDensity());

    // 8 } else {
    // molarFlow=inletStream.getThermoSystem().getTotalNumberOfMoles();
    // }

    inStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
    inStream.getThermoSystem().init(1);
    inStream.run();

    outStream.getThermoSystem().setTotalNumberOfMoles(molarFlow);
    outStream.getThermoSystem().init(1);
    outStream.run();

    // System.out.println("delta p valve " +
    // (inletStream.getThermoSystem().getPressure() -
    // outStream.getThermoSystem().getPressure()));
    // System.out.println("total molar flow out " + molarFlow);
    // System.out.println("Total volume flow " +
    // outStream.getThermoSystem().getVolume());
  }

  /**
   * <p>
   * runController.
   * </p>
   *
   * @param dt a double
   */
  public void runController(double dt) {
    if (hasController) {
      getController().runTransient(this.percentValveOpening, dt);
      this.percentValveOpening = getController().getResponse();
      if (this.percentValveOpening > 100) {
        this.percentValveOpening = 100;
      }
      if (this.percentValveOpening < 0) {
        this.percentValveOpening = 1e-10;
      }
      // System.out.println("valve opening " + this.percentValveOpening + " %");
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return Cv;
  }

  /** {@inheritDoc} */
  @Override
  public void setCv(double Cv) {
    this.Cv = Cv;
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
}
