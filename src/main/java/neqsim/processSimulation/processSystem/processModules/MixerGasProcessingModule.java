package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.pump.PumpInterface;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 * <p>
 * MixerGasProcessingModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class MixerGasProcessingModule extends ProcessModuleBaseClass {
    private static final long serialVersionUID = 1000;

    StreamInterface feedStream, gasExitStream, oilExitStream, glycolFeedStream, glycolExitStream;
    Separator glycolScrubber;
    Separator inletSeparator;
    double inletSepTemperature = 50.00; // bar'
    double gasScrubberTemperature = 30.00, firstStageOutPressure = 110.0,
            glycolScrubberTemperature = 20.0, secondStageOutPressure = 200.0; // bar
    double glycolInjectionRate = 10.0, exportGasTemperature = 273.15 + 30.0,
            liquidPumpPressure = 150.0; // m^3/hr
    Compressor secondStageCompressor;
    Pump oilPump;
    Cooler secondStageAfterCooler;

    public MixerGasProcessingModule(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void addInputStream(String streamName, StreamInterface stream) {
        if (streamName.equals("feed stream")) {
            this.feedStream = stream;
        }
        if (streamName.equals("glycol feed stream")) {
            this.glycolFeedStream = stream;
        }
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutputStream(String streamName) {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        if (streamName.equals("gas exit stream")) {
            return this.gasExitStream;
        } else if (streamName.equals("oil exit stream")) {
            return this.oilExitStream;
        } else if (streamName.equals("glycol exit stream")) {
            return this.glycolExitStream;
        } else if (streamName.equals("glycol feed stream")) {
            return this.glycolFeedStream;
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void initializeModule() {
        isInitializedModule = true;
        // double inletPressure = feedStream.getPressure();

        Cooler inletCooler = new Cooler("inlet well stream cooler", feedStream);
        inletCooler.setOutTemperature(inletSepTemperature + 273.15);

        inletSeparator = new Separator("Inlet separator", inletCooler.getOutStream());

        Cooler gasCooler = new Cooler("separator gas cooler", inletSeparator.getGasOutStream());
        gasCooler.setOutTemperature(gasScrubberTemperature + 273.15);

        oilPump = new Pump("liquid pump", inletSeparator.getLiquidOutStream());
        oilPump.setOutletPressure(liquidPumpPressure);

        Separator gasScrubber =
                new Separator("HC dew point control scrubber", gasCooler.getOutStream());

        Recycle HPliquidRecycle = new Recycle("Resycle");
        double tolerance = 1e-2;
        HPliquidRecycle.setTolerance(tolerance);
        HPliquidRecycle.addStream(gasScrubber.getLiquidOutStream());
        inletSeparator.addStream(HPliquidRecycle.getOutStream());

        Compressor firstStageCompressor =
                new Compressor("1st stage compressor", gasScrubber.getGasOutStream());
        firstStageCompressor.setOutletPressure(firstStageOutPressure);

        glycolFeedStream.getThermoSystem().setPressure(firstStageOutPressure);

        Mixer glycolMixer = new Mixer("glycol injection mixer");
        glycolMixer.addStream(firstStageCompressor.getOutletStream());
        glycolMixer.addStream(glycolFeedStream);

        Cooler mixerAfterCooler =
                new Cooler("glycol mixer after cooler", glycolMixer.getOutStream());
        mixerAfterCooler.setOutTemperature(glycolScrubberTemperature + 273.15);

        glycolScrubber =
                new Separator("Water dew point control scrubber", mixerAfterCooler.getOutStream());

        secondStageCompressor =
                new Compressor("2nd stage compressor", glycolScrubber.getGasOutStream());
        secondStageCompressor.setOutletPressure(secondStageOutPressure);

        secondStageAfterCooler =
            new Cooler("second stage after cooler", secondStageCompressor.getOutletStream());
        secondStageAfterCooler.setOutTemperature(exportGasTemperature + 273.15);

        getOperations().add(inletCooler);
        getOperations().add(inletSeparator);
        getOperations().add(gasCooler);
        getOperations().add(oilPump);
        getOperations().add(gasScrubber);
        getOperations().add(HPliquidRecycle);
        getOperations().add(firstStageCompressor);
        getOperations().add(glycolMixer);
        getOperations().add(mixerAfterCooler);
        getOperations().add(glycolScrubber);
        getOperations().add(secondStageCompressor);
        getOperations().add(secondStageAfterCooler);
    }

    /** {@inheritDoc} */
    @Override
    public void initializeStreams() {
        isInitializedStreams = true;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (!isInitializedModule) {
            initializeModule();
        }
        getOperations().run();

        gasExitStream = secondStageAfterCooler.getOutStream();
        oilExitStream = oilPump.getOutStream();
        glycolExitStream = glycolScrubber.getLiquidOutStream();
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
        if (specificationName.equals("inlet separation temperature")) {
            inletSepTemperature = value;
        } else if (specificationName.equals("gas scrubber temperature")) {
            gasScrubberTemperature = value;
        } else if (specificationName.equals("first stage out pressure")) {
            firstStageOutPressure = value;
        } else if (specificationName.equals("glycol scrubber temperature")) {
            glycolScrubberTemperature = value;
        } else if (specificationName.equals("second stage out pressure")) {
            secondStageOutPressure = value;
        } else if (specificationName.equals("export gas temperature")) {
            exportGasTemperature = value;
        } else if (specificationName.equals("liquid pump out pressure")) {
            liquidPumpPressure = value;
        }
    }

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 50, 65);

        testSystem.addComponent("methane", 50);
        testSystem.addComponent("propane", 0.15);
        testSystem.addComponent("nC10", 2);
        testSystem.addComponent("water", 5);
        testSystem.addComponent("TEG", 5e-9);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.setMultiPhaseCheck(true);

        neqsim.thermo.system.SystemInterface glycolTestSystem =
                new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 15, 50);
        glycolTestSystem.addComponent("methane", 0);
        glycolTestSystem.addComponent("propane", 0);
        glycolTestSystem.addComponent("nC10", 0);
        glycolTestSystem.addComponent("water", 1e-4);
        glycolTestSystem.addComponent("TEG", 0.8);

        glycolTestSystem.createDatabase(true);
        glycolTestSystem.setMixingRule(10);
        glycolTestSystem.setMultiPhaseCheck(true);
        glycolTestSystem.init(0);
        Stream wellStream = new Stream("Well stream", testSystem);
        wellStream.getThermoSystem().setTotalFlowRate(5.0, "MSm^3/day");

        Stream glycolFeedStream = new Stream("Glycol feed stream", glycolTestSystem);
        glycolFeedStream.getThermoSystem().setTotalFlowRate(4.0 * 1e3, "kg/hr");

        MixerGasProcessingModule separationModule = new MixerGasProcessingModule("GasMixer");
        separationModule.addInputStream("feed stream", wellStream);
        separationModule.addInputStream("glycol feed stream", glycolFeedStream);
        separationModule.setSpecification("inlet separation temperature", 55.0);
        separationModule.setSpecification("gas scrubber temperature", 35.0);
        separationModule.setSpecification("glycol scrubber temperature", 25.0);
        separationModule.setSpecification("first stage out pressure", 110.0);
        separationModule.setSpecification("second stage out pressure", 200.0);
        separationModule.setSpecification("export gas temperature", 30.0);
        separationModule.setSpecification("liquid pump out pressure", 150.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(wellStream);
        operations.add(glycolFeedStream);
        operations.add(separationModule);
        operations.run();
        // glycolFeedStream.displayResult();
        // separationModule.getOutputStream("gas exit stream").displayResult();
        // separationModule.getOutputStream("oil exit stream").displayResult();
        // separationModule.getOutputStream("liquid pump").displayResult();
        double en = ((PumpInterface) separationModule.getOperations().getUnit("liquid pump"))
                .getPower();
        // separationModule.getOutputStream("glycol feed stream").displayResult();
        // separationModule.getOutputStream("glycol exit stream").displayResult();

        // ((Separator) operations.getUnit("Water dew point control
        // scrubber")).displayResult();
    }
}
