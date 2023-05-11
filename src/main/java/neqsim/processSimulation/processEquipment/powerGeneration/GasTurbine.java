package neqsim.processSimulation.processEquipment.powerGeneration;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.mechanicalDesign.compressor.CompressorMechanicalDesign;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.expander.Expander;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GasTurbine class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class GasTurbine extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Compressor.class);

  public SystemInterface thermoSystem;
  public StreamInterface airStream;
  public Compressor airCompressor;
  public double combustionpressure = 2.5;
  double airGasRatio = 2.8;
  double expanderPower = 0.0;
  double compressorPower = 0.0;
  private double heat = 0.0;

  public double power = 0.0;

  /**
   * <p>
   * Constructor for GasTurbine.
   * </p>
   */
  public GasTurbine() {
    this("GasTurbine");
  }

  /**
   * <p>
   * Constructor for GasTurbine.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public GasTurbine(String name) {
    super(name);
    // needs to be changed to gas tubing mechanical design
    SystemInterface airThermoSystem = new neqsim.thermo.Fluid().create("combustion air");
    airThermoSystem.createDatabase(true);
    // airThermoSystem.display();
    airStream = new Stream("airStream", airThermoSystem);
    airStream.setPressure(1.01325);
    airStream.setTemperature(288.15, "K");
    airCompressor = new Compressor("airCompressor", airStream);
  }

  /**
   * <p>
   * Constructor for GasTurbine.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public GasTurbine(StreamInterface inletStream) {
    this();
    setInletStream(inletStream);
  }

  /**
   * <p>
   * Constructor for GasTurbine.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public GasTurbine(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * {@inheritDoc}
   *
   * @return a
   *         {@link neqsim.processSimulation.mechanicalDesign.compressor.CompressorMechanicalDesign}
   *         object
   */
  @Override
  public CompressorMechanicalDesign getMechanicalDesign() {
    return new CompressorMechanicalDesign(this);
  }

  /**
   * <p>
   * Getter for the field <code>heat</code>.
   * </p>
   *
   * @return a double
   */
  public double getHeat() {
    return heat;
  }

  /**
   * <p>
   * Getter for the field <code>power</code>.
   * </p>
   *
   * @return a double
   */
  public double getPower() {
    return power;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;
    try {
      this.outStream = inletStream.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    thermoSystem = inStream.getThermoSystem().clone();
    airStream.setFlowRate(thermoSystem.getFlowRate("mole/sec") * airGasRatio, "mole/sec");
    airStream.setPressure(1.01325);
    airStream.run(id);

    airCompressor.setInletStream(airStream);
    airCompressor.setOutletPressure(combustionpressure);
    airCompressor.run(id);
    compressorPower = airCompressor.getPower();
    StreamInterface outStreamAir = airCompressor.getOutletStream().clone();
    outStreamAir.getFluid().addFluid(thermoSystem);
    // outStreamAir.getFluid().setTemperature(800.0);
    // outStreamAir.getFluid().createDatabase(true);

    outStreamAir.run(id);

    double heatOfCombustion = inStream.LCV() * inStream.getFlowRate("mole/sec");
    Heater locHeater = new Heater("locHeater", outStreamAir);
    locHeater.setEnergyInput(heatOfCombustion);
    locHeater.run(id);

    double moleMethane = outStreamAir.getFluid().getComponent("methane").getNumberOfmoles();
    // double moleEthane = outStreamAir.getFluid().getComponent("ethane").getNumberOfmoles();
    // double molePropane = outStreamAir.getFluid().getComponent("propane").getNumberOfmoles();
    locHeater.getOutletStream().getFluid().addComponent("CO2", moleMethane);
    locHeater.getOutletStream().getFluid().addComponent("water", moleMethane * 2.0);
    locHeater.getOutletStream().getFluid().addComponent("methane", -moleMethane);
    locHeater.getOutletStream().getFluid().addComponent("oxygen", -moleMethane * 2.0);

    // todo: Init fails because there is less than moleMethane of oxygen
    locHeater.getOutletStream().getFluid().init(3);
    // locHeater.getOutStream().run(id);
    locHeater.displayResult();

    Expander expander = new Expander("expander", locHeater.getOutletStream());
    expander.setOutletPressure(1.01325);
    expander.run(id);

    Cooler cooler1 = new Cooler("cooler1", expander.getOutletStream());
    cooler1.setOutTemperature(288.15);
    cooler1.run(id);

    expanderPower = expander.getPower();

    power = expanderPower - compressorPower;
    this.heat = cooler1.getDuty();
    setCalculationIdentifier(id);
  }
}
