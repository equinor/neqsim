package neqsim.process.equipment.tank;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.TankResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Tank class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Tank extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Tank.class);

  SystemInterface thermoSystem;
  SystemInterface gasSystem;
  SystemInterface waterSystem;
  SystemInterface liquidSystem;
  SystemInterface thermoSystemCloned;
  Stream gasOutStream;
  Stream liquidOutStream;
  private int numberOfInputStreams = 0;
  Mixer inletStreamMixer = new Mixer("Separator Inlet Stream Mixer");
  private double efficiency = 1.0;
  private double liquidCarryoverFraction = 0.0;
  private double gasCarryunderFraction = 0.0;
  private double volume = 136000.0;
  double steelWallTemperature = 298.15;
  double steelWallMass = 1840.0 * 1000.0;
  double steelWallArea = 15613.0;
  double heatTransferNumber = 5.0;
  double steelCp = 450.0;

  double separatorLength = 40.0;
  double separatorDiameter = 60.0;
  double liquidVolume = 235.0;
  double gasVolume = 15.0;

  private double liquidLevel = liquidVolume / (liquidVolume + gasVolume);

  /**
   * Constructor for Tank.
   *
   * @param name name of tank
   */
  public Tank(String name) {
    super(name);
    setCalculateSteadyState(true);
  }

  /**
   * <p>
   * Constructor for Tank.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Tank(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * <p>
   * setInletStream.
   * </p>
   *
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setInletStream(StreamInterface inletStream) {
    inletStreamMixer.addStream(inletStream);
    thermoSystem = inletStream.getThermoSystem().clone();
    gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
    gasOutStream = new Stream("gasOutStream", gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    liquidOutStream = new Stream("liquidOutStream", liquidSystem);
  }

  /**
   * <p>
   * addStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addStream(StreamInterface newStream) {
    if (numberOfInputStreams == 0) {
      setInletStream(newStream);
    } else {
      inletStreamMixer.addStream(newStream);
    }
    numberOfInputStreams++;
  }

  /**
   * <p>
   * Getter for the field <code>liquidOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * <p>
   * getGas.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGas() {
    return getGasOutStream();
  }

  /**
   * <p>
   * getLiquid.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getLiquid() {
    return getLiquidOutStream();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the following properties:
   * </p>
   * <ul>
   * <li>steelWallTemperature</li>
   * <li>gasOutStream</li>
   * <li>liquidOutStream</li>
   * <li><code>thermoSystem</code> including properties</li>
   * <li>liquidLevel</li>
   * <li>liquidVolume</li>
   * <li>gasVolume</li>
   * </ul>
   */
  @Override
  public void run(UUID id) {
    inletStreamMixer.run(id);
    SystemInterface thermoSystem2 = inletStreamMixer.getOutletStream().getThermoSystem().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem2);
    ops.VUflash(thermoSystem2.getVolume(), thermoSystem2.getInternalEnergy());
    logger.info("Volume " + thermoSystem2.getVolume() + " internalEnergy "
        + thermoSystem2.getInternalEnergy());
    steelWallTemperature = thermoSystem2.getTemperature();
    if (thermoSystem2.hasPhaseType("gas")) {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2, "gas");
    } else {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2.getEmptySystemClone(), "gas");
    }
    if (thermoSystem2.hasPhaseType("oil")) {
      liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "oil");
    } else {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2.getEmptySystemClone(), "oil");
    }

    thermoSystem = thermoSystem2.clone();
    thermoSystem.setTotalNumberOfMoles(1.0e-10);
    thermoSystem.init(1);
    logger.info("number of phases " + thermoSystem.getNumberOfPhases());
    for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
      double relFact = gasVolume / (thermoSystem.getPhase(j).getVolume() * 1.0e-5);
      if (j == 1) {
        relFact = liquidVolume / (thermoSystem.getPhase(j).getVolume() * 1.0e-5);
      }
      for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
        thermoSystem.addComponent(thermoSystem.getPhase(j).getComponent(i).getComponentName(),
            relFact * thermoSystem.getPhase(j).getComponent(i).getNumberOfMolesInPhase(), j);
      }
    }
    if (thermoSystem2.getNumberOfPhases() == 2) {
      thermoSystem.setBeta(gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
          / (gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
              + liquidVolume / thermoSystem2.getPhase(1).getMolarVolume()));
    } else {
      thermoSystem.setBeta(1.0 - 1e-10);
    }
    thermoSystem.init(3);
    logger.info("moles in separator " + thermoSystem.getNumberOfMoles());
    double volume1 = thermoSystem.getVolume();
    logger.info("volume1 bef " + volume1);
    logger.info("beta " + thermoSystem.getBeta());

    if (thermoSystem2.getNumberOfPhases() == 2) {
      liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
    } else {
      liquidLevel = 1e-10;
    }
    liquidVolume =
        getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter * separatorLength;
    gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
        * separatorLength;
    logger.info("moles out" + liquidOutStream.getThermoSystem().getTotalNumberOfMoles());

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    thermoSystem.display();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }

    inletStreamMixer.run(id);

    System.out.println("moles out" + liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
    // double inMoles =
    // inletStreamMixer.getOutStream().getThermoSystem().getTotalNumberOfMoles();
    // double gasoutMoles = gasOutStream.getThermoSystem().getNumberOfMoles();
    // double liqoutMoles = liquidOutStream.getThermoSystem().getNumberOfMoles();
    thermoSystem.init(3);
    gasOutStream.getThermoSystem().init(3);
    liquidOutStream.getThermoSystem().init(3);
    inletStreamMixer.getOutletStream().getThermoSystem().init(3);
    double volume1 = thermoSystem.getVolume();
    System.out.println("volume1 " + volume1);
    double deltaEnergy = inletStreamMixer.getOutletStream().getThermoSystem().getEnthalpy()
        - gasOutStream.getThermoSystem().getEnthalpy()
        - liquidOutStream.getThermoSystem().getEnthalpy();
    System.out.println("enthalph delta " + deltaEnergy);
    double wallHeatTransfer = heatTransferNumber * steelWallArea
        * (steelWallTemperature - thermoSystem.getTemperature()) * dt;
    System.out.println("delta temp " + (steelWallTemperature - thermoSystem.getTemperature()));
    steelWallTemperature -= wallHeatTransfer / (steelCp * steelWallMass);
    System.out.println("wall Temperature " + steelWallTemperature);

    double newEnergy = thermoSystem.getInternalEnergy() + dt * deltaEnergy + wallHeatTransfer;

    System.out.println("energy cooling " + dt * deltaEnergy);
    System.out.println("energy heating " + wallHeatTransfer / dt + " kW");

    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      double dn = 0.0;
      for (int k = 0; k < inletStreamMixer.getOutletStream().getThermoSystem()
          .getNumberOfPhases(); k++) {
        dn += inletStreamMixer.getOutletStream().getThermoSystem().getPhase(k).getComponent(i)
            .getNumberOfMolesInPhase();
      }
      dn = dn - gasOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase()
          - liquidOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      System.out.println("dn " + dn);
      thermoSystem.addComponent(inletStreamMixer.getOutletStream().getThermoSystem().getPhase(0)
          .getComponent(i).getComponentName(), dn * dt);
    }
    System.out.println("liquid level " + liquidLevel);
    liquidVolume =
        getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter * separatorLength;
    gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
        * separatorLength;

    System.out.println("total moles " + thermoSystem.getTotalNumberOfMoles());

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.VUflash(volume1, newEnergy);

    setOutComposition(thermoSystem);
    setTempPres(thermoSystem.getTemperature(), thermoSystem.getPressure());

    if (thermoSystem.hasPhaseType("oil")) {
      liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
    } else {
      liquidLevel = 1e-10;
    }
    System.out.println("liquid level " + liquidLevel);
    liquidVolume =
        getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter * separatorLength;
    gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
        * separatorLength;
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * setOutComposition.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setOutComposition(SystemInterface thermoSystem) {
    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      if (thermoSystem.hasPhaseType("gas")) {
        getGasOutStream().getThermoSystem().getPhase(0).getComponent(i).setx(thermoSystem
            .getPhase(thermoSystem.getPhaseNumberOfPhase("gas")).getComponent(i).getx());
      }
      if (thermoSystem.hasPhaseType("oil")) {
        getLiquidOutStream().getThermoSystem().getPhase(0).getComponent(i).setx(thermoSystem
            .getPhase(thermoSystem.getPhaseNumberOfPhase("oil")).getComponent(i).getx());
      }
    }
  }

  /**
   * <p>
   * setTempPres.
   * </p>
   *
   * @param temp a double
   * @param pres a double
   */
  public void setTempPres(double temp, double pres) {
    gasOutStream.getThermoSystem().setTemperature(temp);
    liquidOutStream.getThermoSystem().setTemperature(temp);

    inletStreamMixer.setPressure(pres);
    gasOutStream.getThermoSystem().setPressure(pres);
    liquidOutStream.getThermoSystem().setPressure(pres);

    UUID id = UUID.randomUUID();
    inletStreamMixer.run(id);
    gasOutStream.run(id);
    liquidOutStream.run(id);
  }

  /**
   * <p>
   * Getter for the field <code>efficiency</code>.
   * </p>
   *
   * @return a double
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * <p>
   * Setter for the field <code>efficiency</code>.
   * </p>
   *
   * @param efficiency a double
   */
  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  /**
   * <p>
   * Getter for the field <code>liquidCarryoverFraction</code>.
   * </p>
   *
   * @return a double
   */
  public double getLiquidCarryoverFraction() {
    return liquidCarryoverFraction;
  }

  /**
   * <p>
   * Setter for the field <code>liquidCarryoverFraction</code>.
   * </p>
   *
   * @param liquidCarryoverFraction a double
   */
  public void setLiquidCarryoverFraction(double liquidCarryoverFraction) {
    this.liquidCarryoverFraction = liquidCarryoverFraction;
  }

  /**
   * <p>
   * Getter for the field <code>gasCarryunderFraction</code>.
   * </p>
   *
   * @return a double
   */
  public double getGasCarryunderFraction() {
    return gasCarryunderFraction;
  }

  /**
   * <p>
   * Setter for the field <code>gasCarryunderFraction</code>.
   * </p>
   *
   * @param gasCarryunderFraction a double
   */
  public void setGasCarryunderFraction(double gasCarryunderFraction) {
    this.gasCarryunderFraction = gasCarryunderFraction;
  }

  /**
   * <p>
   * Getter for the field <code>liquidLevel</code>.
   * </p>
   *
   * @return a double
   */
  public double getLiquidLevel() {
    return liquidLevel;
  }

  /**
   * <p>
   * Getter for the field <code>volume</code>.
   * </p>
   *
   * @return a double
   */
  public double getVolume() {
    return volume;
  }

  /**
   * <p>
   * Setter for the field <code>volume</code>.
   * </p>
   *
   * @param volume a double
   */
  public void setVolume(double volume) {
    this.volume = volume;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getThermoSystem();
      inletFlow += inletStreamMixer.getStream(i).getThermoSystem().getFlowRate(unit);
    }
    double outletFlow = getGasOutStream().getThermoSystem().getFlowRate(unit)
        + getLiquidOutStream().getThermoSystem().getFlowRate(unit);
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new TankResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    TankResponse res = new TankResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
  }
}
