package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.adsorber.SimpleAdsorber;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 * <p>
 * AdsorptionDehydrationlModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class AdsorptionDehydrationlModule extends ProcessModuleBaseClass {
    private static final long serialVersionUID = 1000;

    protected StreamInterface gasStreamToAdsorber = null, gasStreamFromAdsorber = null;
    protected SimpleAdsorber[] adsorber = null;
    double regenerationCycleTime = 1.0, waterDewPontTemperature = 273.15 - 10.0, designFlow = 1.0,
            designAdsorptionTemperature = 298.0, designRegenerationTemperature = 440.0,
            designAdsorptionPressure = 60.0;
    int numberOfAdorptionBeds = 3;
    double adsorberInternalDiameter = 1.0;
    double adsorbentFillingHeight = 3.0;

    public AdsorptionDehydrationlModule(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void addInputStream(String streamName, StreamInterface stream) {
        if (streamName.equals("gasStreamToAdsorber")) {
            this.gasStreamToAdsorber = stream;
        }
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutputStream(String streamName) {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        if (streamName.equals("gasStreamFromAdsorber")) {
            return this.gasStreamFromAdsorber;
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ProcessEquipmentInterface getUnit(String unitName) {
        if (unitName.equals("adorber_0")) {
            return adsorber[0];
        } else if (unitName.equals("adorber_1")) {
            return adsorber[1];
        } else if (unitName.equals("adorber_2")) {
            return adsorber[2];
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (!isInitializedModule) {
            initializeModule();
        }
        gasStreamToAdsorber.run();

        getOperations().run();

        // gasStreamFromAdsorber = (Stream) inletSeparator.getGasOutStream().clone();
        // gasStreamFromAdsorber.getThermoSystem().addComponent("water",
        // -gasStreamFromAdsorber.getThermoSystem().getPhase(0).getComponent("water").getNumberOfMolesInPhase()
        // * 0.99);
        // gasStreamFromAdsorber.getThermoSystem().addComponent("TEG", 1e-10);
        // gasStreamFromAdsorber.getThermoSystem().init(1);
    }

    /** {@inheritDoc} */
    @Override
    public void initializeStreams() {
        isInitializedStreams = true;
        try {
            adsorber = new SimpleAdsorber[numberOfAdorptionBeds];
            for (int i = 0; i < numberOfAdorptionBeds; i++) {
                adsorber[i] = new SimpleAdsorber("SimpleAdsorber_" + i + 1, gasStreamToAdsorber);
            }
            this.gasStreamFromAdsorber = (Stream) this.gasStreamToAdsorber.clone();
            this.gasStreamFromAdsorber.setName("Stream from Adsorber");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void initializeModule() {
        isInitializedModule = true;

        getOperations().add(gasStreamToAdsorber);
    }

    /** {@inheritDoc} */
    @Override
    public void setSpecification(String specificationName, double value) {
        if (specificationName.equals("water dew point temperature")) {
            waterDewPontTemperature = value;
        }
        if (specificationName.equals("designFlow")) {
            designFlow = value;
        }
        if (specificationName.equals("designAdsorptionTemperature")) {
            designAdsorptionTemperature = value;
        }
        if (specificationName.equals("designRegenerationTemperature")) {
            designRegenerationTemperature = value;
        }
        if (specificationName.equals("designAdsorptionPressure")) {
            designAdsorptionPressure = value;
        }

        if (specificationName.equals("regenerationCycleTime")) {
            regenerationCycleTime = value;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void calcDesign() {
        Stream tempStream = (Stream) gasStreamToAdsorber.clone();
        tempStream.getThermoSystem().setPressure(designAdsorptionPressure);
        tempStream.getThermoSystem().setTemperature(designAdsorptionTemperature);
        tempStream.run();
        tempStream.getThermoSystem().initPhysicalProperties();
        double gasDensity =
                tempStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();

        double gasVelocity = 67.0 / Math.sqrt(gasDensity);

        double qa = designFlow / (numberOfAdorptionBeds - 1.0) / 1440.0
                * (1.01325 / designAdsorptionPressure) * (designAdsorptionTemperature / 288.15)
                * tempStream.getThermoSystem().getPhase(0).getZ();
        adsorberInternalDiameter = Math.sqrt(4.0 * qa / Math.PI / gasVelocity);

        double waterLoadingCycle = regenerationCycleTime * designFlow * 42.29489667
                * tempStream.getThermoSystem().getPhase(0).getComponent("water").getx()
                * tempStream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass();// 360.0;
                                                                                                // //
                                                                                                // kg/cycle
                                                                                                // this
                                                                                                // needs
                                                                                                // to
                                                                                                // be
                                                                                                // calculated
        double usefulDesiccantCapacity = 10.0; // 10%
        double bulkDensityDesiccant = 750.0; // 10%

        adsorbentFillingHeight = 400.0 * waterLoadingCycle / (Math.PI * usefulDesiccantCapacity
                * bulkDensityDesiccant * adsorberInternalDiameter * adsorberInternalDiameter);

        // double lenghtDiameterRatio = adsorbentFillingHeight / adsorberInternalDiameter;
        // design is done here
    }

    /** {@inheritDoc} */
    @Override
    public void setDesign() {
        for (int i = 0; i < numberOfAdorptionBeds; i++) {
            adsorber[i].getMechanicalDesign().setInnerDiameter(adsorberInternalDiameter);
            adsorber[i].getMechanicalDesign().setTantanLength(adsorbentFillingHeight * 1.5);
        }
        // set design is done here
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
                new neqsim.thermo.system.SystemSrkEos((273.15 + 30.0), 10.0);

        testSystem.addComponent("methane", 1.0);
        testSystem.addComponent("water", 1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream inletStream = new Stream("inletStream", testSystem);
        Separator separator = new Separator("Separator 1", inletStream);

        neqsim.processSimulation.processSystem.processModules.AdsorptionDehydrationlModule adsorptionPlant =
                new neqsim.processSimulation.processSystem.processModules.AdsorptionDehydrationlModule("absPlant");
        adsorptionPlant.addInputStream("gasStreamToAdsorber", separator.getGasOutStream());
        adsorptionPlant.setSpecification("water dew point temperature", 273.15 - 100.0);
        adsorptionPlant.setSpecification("designFlow", 20.0e6); // MSm^3/day
        adsorptionPlant.setSpecification("designAdsorptionTemperature", 273.15 + 30);
        adsorptionPlant.setSpecification("designRegenerationTemperature", 273.15 + 250.0);
        adsorptionPlant.setSpecification("designAdsorptionPressure", 60.0);
        adsorptionPlant.setSpecification("regenerationCycleTime", 1.0); // days per cycle
        adsorptionPlant.setSpecification("maxDesignPressure", 100.0);
        adsorptionPlant.setSpecification("maxDesignTemperature", 100.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(inletStream);
        operations.add(separator);
        operations.add(adsorptionPlant);

        operations.run();

        adsorptionPlant.calcDesign();

        // TEGplant.getOutputStream("gasStreamFromAdsorber").displayResult();
    }
}
