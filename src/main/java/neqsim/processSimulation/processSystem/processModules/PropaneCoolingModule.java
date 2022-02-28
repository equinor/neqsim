package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.compressor.CompressorInterface;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 * <p>
 * PropaneCoolingModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PropaneCoolingModule extends ProcessModuleBaseClass {

    public PropaneCoolingModule(String name) {
        super(name);
    }
    /**
     * <p>
     * Setter for the field <code>condenserTemperature</code>.
     * </p>
     *
     * @param condenserTemperature the condenserTemperature to set
     */
    public void setCondenserTemperature(double condenserTemperature) {
        this.condenserTemperature = condenserTemperature;
    }

    /**
     * <p>
     * Setter for the field <code>vaporizerTemperature</code>.
     * </p>
     *
     * @param vaporizerTemperature the vaporizerTemperature to set
     */
    public void setVaporizerTemperature(double vaporizerTemperature) {
        this.vaporizerTemperature = vaporizerTemperature;
    }

    private static final long serialVersionUID = 1000;

    StreamInterface refrigerantStream;
    private double condenserTemperature = 273.15 + 30.0; // Kelvin
    private double vaporizerTemperature = 273.15 - 40.0; // Kelvin

    /** {@inheritDoc} */
    @Override
    public void addInputStream(String streamName, StreamInterface stream) {
        if (streamName.equals("refrigerant")) {
            this.refrigerantStream = stream;
        }
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutputStream(String streamName) {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        if (streamName.equals("refrigerant")) {
            return this.refrigerantStream;
        } else if (streamName.equals("refrigerant...")) {
            return this.refrigerantStream;
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void initializeModule() {
        isInitializedModule = true;

        refrigerantStream.getThermoSystem().setTemperature(condenserTemperature);
        ((Stream) refrigerantStream).setSpecification("bubT");
        refrigerantStream.run();

        ThrottlingValve JTvalve = new ThrottlingValve("JTvalve", refrigerantStream);

        Cooler cooler = new Cooler("propane evaporator", JTvalve.getOutStream());
        cooler.setPressureDrop(0.35);
        cooler.setSpecification("out stream");

        Stream stream_2 = new Stream("stream_2", cooler.getOutStream());
        stream_2.setSpecification("dewT");
        stream_2.getThermoSystem().setTemperature(vaporizerTemperature);
        stream_2.run();

        cooler.setOutStream(stream_2);
        JTvalve.setOutletPressure(stream_2.getPressure());

        Compressor compressor1 = new Compressor("propane compressor", stream_2);
        // compressor1.setIsentropicEfficiency(0.75);
        // compressor1.setPower(180000);
        compressor1.setOutletPressure(refrigerantStream.getPressure());

        Heater condenser = new Heater("propane condenser", compressor1.getOutStream());
        condenser.setPressureDrop(0.07);
        condenser.setSpecification("out stream");
        condenser.setOutStream((Stream) refrigerantStream);

        System.out.println("adding operations....");
        getOperations().add(refrigerantStream);
        getOperations().add(JTvalve);
        getOperations().add(cooler);
        getOperations().add(stream_2);
        getOperations().add(compressor1);
        getOperations().add(condenser);
        System.out.println("finished adding operations....");
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (!isInitializedModule) {
            initializeModule();
        }
        System.out.println("running model....");
        getOperations().run();

        // gasExitStream = secondStageAfterCooler.getOutStream();
    }

    /** {@inheritDoc} */
    @Override
    public void initializeStreams() {
        isInitializedStreams = true;
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
        getOperations().runTransient();
    }

    /** {@inheritDoc} */
    @Override
    public void calcDesign() {
        // design is done here
    }

    /** {@inheritDoc} */
    @Override
    public void setDesign() {
        // set design is done here
    }

    /** {@inheritDoc} */
    @Override
    public void setSpecification(String specificationName, double value) {
        if (specificationName.equals("vaporizerTemperature")) {
            setVaporizerTemperature(value);
        } else if (specificationName.equals("condenserTemperature")) {
            setCondenserTemperature(value);
        }
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos(273.15 - 20, 1);
        testSystem.addComponent("propane", 0.30);
        testSystem.createDatabase(true);

        Stream porpane = new Stream("porpane", testSystem);
        PropaneCoolingModule propaneModule = new PropaneCoolingModule("propaneModule");
        propaneModule.setCondenserTemperature(273.15 + 30);
        propaneModule.setVaporizerTemperature(273.15 - 40);

        propaneModule.addInputStream("refrigerant", porpane);
        propaneModule.run();

        double compressorWork =
                ((CompressorInterface) propaneModule.getOperations().getUnit("propane compressor"))
                        .getEnergy();

        double evaporatorDuty =
                ((Cooler) propaneModule.getOperations().getUnit("propane evaporator"))
                        .getEnergyInput();
        double evaporatorPressure =
                ((Cooler) propaneModule.getOperations().getUnit("propane evaporator"))
                        .getOutStream().getPressure();
        double evaporatorTemperature =
                ((Cooler) propaneModule.getOperations().getUnit("propane evaporator"))
                        .getOutStream().getTemperature();

        double condenserDuty = ((Heater) propaneModule.getOperations().getUnit("propane condenser"))
                .getEnergyInput();
        double condenserPressure =
                ((Heater) propaneModule.getOperations().getUnit("propane condenser")).getOutStream()
                        .getPressure();
        double condenserTemperature =
                ((Heater) propaneModule.getOperations().getUnit("propane condenser")).getOutStream()
                        .getTemperature();

        System.out.println("Compressor work " + compressorWork + " W");

        System.out.println("evaporator duty " + evaporatorDuty + " W");
        System.out.println("evaporator temperature " + (evaporatorTemperature - 273.15) + " C");
        System.out.println("evaporator pressure " + evaporatorPressure + " bara");

        System.out.println("condenser duty " + condenserDuty + " W");
        System.out.println("condenser temperature " + (condenserTemperature - 273.15) + " C");
        System.out.println("condenser pressure " + condenserPressure + " bara");
        // ((Cooler) propaneModule.getOperations().getUnit("propane
        // evaporator")).getInStream().displayResult();
        // ((Cooler) propaneModule.getOperations().getUnit("propane
        // evaporator")).getOutStream().displayResult();

        // TT ((CompressorInterface) propaneModule.getOperations().getUnit("propane
        // compressor")).displayResult();
        // ((CompressorInterface) propaneModule.getOperations().getUnit("propane
        // compressor")).getOutStream().displayResult();
        // ((Heater) propaneModule.getOperations().getUnit("propane
        // condenser")).getInStream().displayResult();
        // ((Heater) propaneModule.getOperations().getUnit("propane
        // condenser")).getOutStream().displayResult();
    }
}
