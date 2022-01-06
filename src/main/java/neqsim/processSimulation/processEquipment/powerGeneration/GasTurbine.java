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
import neqsim.thermo.system.SystemSrkEos;

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
        // needs to be changed to gas tubing mechanical design
        mechanicalDesign = new CompressorMechanicalDesign(this);
        SystemInterface airThermoSystem = neqsim.thermo.Fluid.create("air");
        airThermoSystem.addComponent("CO2", 0.0);
        airThermoSystem.addComponent("water", 0.0);
        airThermoSystem.createDatabase(true);
        // airThermoSystem.display();
        airStream = new Stream(airThermoSystem);
        airStream.setPressure(1.01325);
        airStream.setTemperature(288.15, "K");
        airCompressor = new Compressor(airStream);
    }

    /**
     * <p>
     * Constructor for GasTurbine.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
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
        this();
        this.name = name;
        setInletStream(inletStream);
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
        // System.out.println("compressor running..");
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
        Heater locHeater = new Heater(outStreamAir);
        locHeater.setEnergyInput(heatOfCombustion);
        locHeater.run();

        locHeater.getOutStream().getFluid().addComponent("CO2", moleMethane);
        locHeater.getOutStream().getFluid().addComponent("water", moleMethane * 2.0);
        locHeater.getOutStream().getFluid().addComponent("methane", -moleMethane);
        locHeater.getOutStream().getFluid().addComponent("oxygen", -moleMethane * 2.0);
        locHeater.getOutStream().getFluid().init(3);
        // locHeater.getOutStream().run();
        locHeater.displayResult();

        Expander expander = new Expander(locHeater.getOutStream());
        expander.setOutletPressure(1.01325);
        expander.run();

        Cooler cooler1 = new Cooler(expander.getOutStream());
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

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        // TODO Auto-generated method stub
        return false;
    }
}
