package neqsim.process.equipment.powergeneration;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.thermo.ThermodynamicConstantsInterface;
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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
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
    airStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
    airStream.setTemperature(288.15, "K");
    airCompressor = new Compressor("airCompressor", airStream);
  }

  /**
   * <p>
   * Constructor for GasTurbine.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public GasTurbine(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
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
  @Override
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
    airStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
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
    // double moleEthane =
    // outStreamAir.getFluid().getComponent("ethane").getNumberOfmoles();
    // double molePropane =
    // outStreamAir.getFluid().getComponent("propane").getNumberOfmoles();
    locHeater.getOutletStream().getFluid().addComponent("CO2", moleMethane);
    locHeater.getOutletStream().getFluid().addComponent("water", moleMethane * 2.0);
    locHeater.getOutletStream().getFluid().addComponent("methane", -moleMethane);
    locHeater.getOutletStream().getFluid().addComponent("oxygen", -moleMethane * 2.0);

    // TODO: Init fails because there is less than moleMethane of oxygen
    locHeater.getOutletStream().getFluid().init(3);
    // locHeater.getOutStream().run(id);
    locHeater.displayResult();

    Expander expander = new Expander("expander", locHeater.getOutletStream());
    expander.setOutletPressure(ThermodynamicConstantsInterface.referencePressure);
    expander.run(id);

    Cooler cooler1 = new Cooler("cooler1", expander.getOutletStream());
    cooler1.setOutTemperature(288.15);
    cooler1.run(id);

    expanderPower = expander.getPower();

    power = expanderPower - compressorPower;
    this.heat = cooler1.getDuty();
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * Calculates ideal air fuel ratio [kg air/kg fuel].
   * </p>
   *
   * @return ideal air fuel ratio [kg air/kg fuel]
   */
  public double calcIdealAirFuelRatio() {
    thermoSystem = inStream.getThermoSystem().clone();
    double elementsH = 0.0;
    double elementsC = 0.0;
    double sumHC = 0.0;
    double molMassHC = 0.0;
    double wtFracHC = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      if (thermoSystem.getComponent(i).isHydrocarbon()) {
        sumHC += thermoSystem.getComponent(i).getz();
        molMassHC +=
            thermoSystem.getComponent(i).getz() * thermoSystem.getComponent(i).getMolarMass();
        elementsC += thermoSystem.getComponent(i).getz()
            * thermoSystem.getComponent(i).getElements().getNumberOfElements("C");
        elementsH += thermoSystem.getComponent(i).getz()
            * thermoSystem.getComponent(i).getElements().getNumberOfElements("H");
      }
    }

    if (sumHC < 1e-100) {
      return 0.0;
    } else {
      wtFracHC = molMassHC / thermoSystem.getMolarMass();
      molMassHC /= sumHC;
      elementsC /= sumHC;
      elementsH /= sumHC;
    }
    double A = elementsC + elementsH / 4;

    double AFR = A * (32.0 + 3.76 * 28.0) / 1000.0 / molMassHC * wtFracHC;
    return AFR;
  }
}
