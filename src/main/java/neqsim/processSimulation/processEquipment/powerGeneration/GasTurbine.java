package neqsim.processSimulation.processEquipment.powerGeneration;

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
import neqsim.thermo.system.SystemSrkEos;

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

    public GasTurbine(String name) {
        super(name);
        // needs to be changed to gas tubing mechanical design
        SystemInterface airThermoSystem = neqsim.thermo.Fluid.create("air");
        airThermoSystem.addComponent("CO2", 0.0);
        airThermoSystem.addComponent("water", 0.0);
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

    public CompressorMechanicalDesign getMechanicalDesign() {
        return new CompressorMechanicalDesign(this);
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
        this.inStream = inletStream;
        try {
            this.outStream = inletStream.clone();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        // System.out.println("compressor running..");
        double heatOfCombustion = inStream.LCV() * inStream.getFlowRate("mole/sec");
        thermoSystem = inStream.getThermoSystem().clone();
        airStream.setFlowRate(thermoSystem.getFlowRate("mole/sec") * airGasRatio, "mole/sec");
        airStream.setPressure(1.01325);
        airStream.run();
        airCompressor.setInletStream(airStream);
        airCompressor.setOutletPressure(combustionpressure);
        airCompressor.run();
        compressorPower = airCompressor.getPower();
        StreamInterface outStreamAir = airCompressor.getOutletStream().clone();
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

        locHeater.getOutletStream().getFluid().addComponent("CO2", moleMethane);
        locHeater.getOutletStream().getFluid().addComponent("water", moleMethane * 2.0);
        locHeater.getOutletStream().getFluid().addComponent("methane", -moleMethane);
        locHeater.getOutletStream().getFluid().addComponent("oxygen", -moleMethane * 2.0);
        locHeater.getOutletStream().getFluid().init(3);
        // locHeater.getOutStream().run();
        locHeater.displayResult();

        Expander expander = new Expander("expander", locHeater.getOutletStream());
        expander.setOutletPressure(1.01325);
        expander.run();

        Cooler cooler1 = new Cooler("cooler1", expander.getOutletStream());
        cooler1.setOutTemperature(288.15);
        cooler1.run();

        expanderPower = expander.getPower();

        power = expanderPower - compressorPower;
        setHeat(cooler1.getDuty());
    }

    /**
     * <p>
     * runTransient.
     * </p>
     */
    public void runTransient() {}

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        // test code;....
        neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(298.15, 1.0);

        testSystem.addComponent("methane", 1.0);

        Stream gasstream = new Stream("well stream", testSystem);
        gasstream.setFlowRate(1.0, "MSm3/day");
        gasstream.setTemperature(50.0, "C");
        gasstream.setPressure(2.0, "bara");
        gasstream.run();
        GasTurbine gasturb = new GasTurbine(gasstream);

        gasstream.run();
        gasturb.run();

        System.out.println("power generated " + gasturb.getPower() / 1.0e6);
        System.out.println("heat generated " + gasturb.getHeat() / 1.0e6);
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
     * Setter for the field <code>heat</code>.
     * </p>
     *
     * @param heat a double
     */
    public void setHeat(double heat) {
        this.heat = heat;
    }
}
