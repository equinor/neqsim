package neqsim.processSimulation.processEquipment.powerGeneration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.mechanicalDesign.compressor.CompressorMechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
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
public class GasTurbine extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Compressor.class);

    public SystemInterface thermoSystem;
    public StreamInterface airStream;
    public Compressor airCompressor;
    public StreamInterface inletStream;
    public StreamInterface outStream;
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

    public GasTurbine(String name) {
        super(name);
        // needs to be changed to gas tubing mechanical design
        SystemInterface airThermoSystem = neqsim.thermo.Fluid.create("combustion air");
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
        this(name);
        setInletStream(inletStream);
    }

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
     * <p>
     * Setter for the field <code>inletStream</code>.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;
        try {
            this.outStream = inletStream.clone();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        double heatOfCombustion = inletStream.LCV() * inletStream.getFlowRate("mole/sec");
        thermoSystem = inletStream.getThermoSystem().clone();
        airStream.setFlowRate(thermoSystem.getFlowRate("mole/sec") * airGasRatio, "mole/sec");
        airStream.setPressure(1.01325);
        airStream.run();

        airCompressor.setInletStream(airStream);
        airCompressor.setOutletPressure(combustionpressure);
        airCompressor.run();
        compressorPower = airCompressor.getPower();
        StreamInterface outStreamAir = airCompressor.getOutStream().clone();
        outStreamAir.getFluid().addFluid(thermoSystem);
        // outStreamAir.getFluid().setTemperature(800.0);
        // outStreamAir.getFluid().createDatabase(true);
        double moleMethane = outStreamAir.getFluid().getComponent("methane").getNumberOfmoles();
        // double moleEthane = outStreamAir.getFluid().getComponent("ethane").getNumberOfmoles();
        // double molePropane = outStreamAir.getFluid().getComponent("propane").getNumberOfmoles();

        outStreamAir.run();
        Heater locHeater = new Heater("locHeater", outStreamAir);
        locHeater.setEnergyInput(heatOfCombustion);
        locHeater.run();

        locHeater.getOutStream().getFluid().addComponent("CO2", moleMethane);
        locHeater.getOutStream().getFluid().addComponent("water", moleMethane * 2.0);
        locHeater.getOutStream().getFluid().addComponent("methane", -moleMethane);
        locHeater.getOutStream().getFluid().addComponent("oxygen", -moleMethane * 2.0);

        // todo: Init fails because there is less than moleMethane of oxygen
        locHeater.getOutStream().getFluid().init(3);
        // locHeater.getOutStream().run();
        locHeater.displayResult();

        Expander expander = new Expander("expander", locHeater.getOutStream());
        expander.setOutletPressure(1.01325);
        expander.run();

        Cooler cooler1 = new Cooler("cooler1", expander.getOutStream());
        cooler1.setOutTemperature(288.15);
        cooler1.run();

        expanderPower = expander.getPower();

        power = expanderPower - compressorPower;
        this.heat = cooler1.getDuty();
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
    }
}
