package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 * <p>
 * GlycolDehydrationlModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class GlycolDehydrationlModule extends ProcessModuleBaseClass {
    private static final long serialVersionUID = 1000;

    protected StreamInterface gasStreamToAbsorber = null, strippingGas = null,
            gasStreamFromAbsorber = null, gasFromStripper = null, leanTEGStreamToAbsorber = null;
    protected SimpleTEGAbsorber absorbtionColumn = null;
    // protected DistillationColumn stripperColumn = null;
    protected Separator stripperColumn = null;
    Heater reboiler = null;
    protected Pump HPpump = null;
    protected Separator glycolFlashDrum = null, waterSeparator = null;
    protected ThrottlingValve valveHP = null, valveMP = null;
    Cooler heatExchanger1 = null, heatExchanger2 = null, heatExchanger3 = null;
    double waterDewPontSpecification = 273.15 - 10.0;
    double numberOfTheoreticalEquilibriumStages = 2;
    private double flashPressure = 5.0;
    double designStandardGasFlowRate = 20.0, maxAbsorberDesignPressure = 70.0;
    double designGasFeedTemperature = 273.15 + 30.0;
    double leanGlycolMolarFraction = 0.95, leanGlycolwtFraction = 0.99,
            leanGlycolMolarFlowRate = 1.0, maxglycolFlowRate = 1;
    String glycolTypeName = "TEG";
    double reboilerTemperature = 273.15 + 204.0, regenerationPressure = 1.4;

    public GlycolDehydrationlModule() {}

    /** {@inheritDoc} */
    @Override
    public void addInputStream(String streamName, StreamInterface stream) {
        if (streamName.equals("gasStreamToAbsorber")) {
            this.gasStreamToAbsorber = stream;
            this.strippingGas = (StreamInterface) stream.clone();
        }
        if (streamName.equals("strippingGas")) {
            this.strippingGas = stream;
        }
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutputStream(String streamName) {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        if (streamName.equals("gasStreamFromAbsorber")) {
            return this.gasStreamFromAbsorber;
        }
        if (streamName.equals("liquidFromStripper")) {
            return this.stripperColumn.getLiquidOutStream();
        }
        if (streamName.equals("liquidFromStripper")) {
            return this.stripperColumn.getLiquidOutStream();
        }
        if (streamName.equals("condenserStripper")) {
            return this.stripperColumn.getGasOutStream();
        } else {
            return null;
        }
    }

    /**
     * <p>
     * solveAbsorptionFactor.
     * </p>
     *
     * @param Ea a double
     * @return a double
     */
    public double solveAbsorptionFactor(double Ea) {
        double A = 7.0, Aold = 7.0;
        double error = 1.0, errorOld = 1.0;
        int iter = 0;
        do {
            iter++;
            errorOld = error;
            error = (Math.pow(A, numberOfTheoreticalEquilibriumStages + 1.0) - A)
                    / (Math.pow(A, numberOfTheoreticalEquilibriumStages + 1.0) - 1) - Ea;

            double dErrordA = (error - errorOld) / (A - Aold);
            Aold = A;
            if (iter > 2) {
                A -= error / dErrordA;
            } else {
                A += error;
            }
        } while (Math.abs(error) > 1e-6 && iter < 100);
        return A;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        if (!isInitializedModule) {
            initializeModule();
        }
        if (!isCalcDesign()) {
            calcDesign();
        }
        getOperations().run();
    }

    /** {@inheritDoc} */
    @Override
    public void initializeStreams() {
        isInitializedStreams = true;
        try {
            this.gasStreamFromAbsorber = (StreamInterface) this.gasStreamToAbsorber.clone();
            this.gasStreamFromAbsorber.setName("Stream from TEG Absorber");

            this.gasFromStripper = (StreamInterface) this.gasStreamToAbsorber.clone();
            this.gasFromStripper.setName("Gas stream from Stripper");

            this.leanTEGStreamToAbsorber = (StreamInterface) this.gasStreamToAbsorber.clone();
            this.leanTEGStreamToAbsorber.setName("lean TEG to absorber");

            this.leanTEGStreamToAbsorber.getThermoSystem().removeMoles();
            this.leanTEGStreamToAbsorber.getThermoSystem().addComponent("water",
                    leanGlycolMolarFlowRate * (1.0 - leanGlycolMolarFraction));
            this.leanTEGStreamToAbsorber.getThermoSystem().addComponent("TEG",
                    leanGlycolMolarFlowRate * leanGlycolMolarFraction);
            this.leanTEGStreamToAbsorber.getThermoSystem().setTotalFlowRate(maxglycolFlowRate,
                    "kg/hr");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void initializeModule() {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        isInitializedModule = true;

        absorbtionColumn = new SimpleTEGAbsorber();
        absorbtionColumn.addGasInStream(gasStreamToAbsorber);
        absorbtionColumn.addSolventInStream(leanTEGStreamToAbsorber);

        valveHP = new ThrottlingValve(absorbtionColumn.getLiquidOutStream());
        valveHP.setOutletPressure(flashPressure);

        glycolFlashDrum = new Separator("flash drum", valveHP.getOutStream());

        valveMP = new ThrottlingValve(glycolFlashDrum.getLiquidOutStream());
        valveMP.setOutletPressure(regenerationPressure);

        /*
         * stripperColumn = new DistillationColumn(5, true, false);
         * stripperColumn.addFeedStream(valveMP.getOutStream(), 3);
         * stripperColumn.setCondenserTemperature(273.15 + 80.0); ((Reboiler)
         * stripperColumn.getReboiler()).setRefluxRatio(11.7);
         * 
         */

        Heater reboiler = new Heater(valveMP.getOutStream());
        reboiler.setName("reboiler");
        reboiler.setOutTemperature(reboilerTemperature);

        strippingGas.setTemperature(reboilerTemperature, "K");
        strippingGas.setPressure(regenerationPressure, "bara");
        stripperColumn = new Separator(reboiler.getOutStream());
        stripperColumn.addStream(strippingGas);

        heatExchanger1 = new Cooler(stripperColumn.getLiquidOutStream());
        heatExchanger1.setOutTemperature(100.0);

        HPpump = new Pump(heatExchanger1.getOutStream());
        HPpump.setName("HP lean TEG pump");
        HPpump.setOutletPressure(gasStreamToAbsorber.getPressure());

        heatExchanger2 = new Cooler(HPpump.getOutStream());
        heatExchanger2.setOutTemperature(273.15 + 40.0);

        heatExchanger3 = new Cooler(stripperColumn.getGasOutStream());
        heatExchanger3.setOutTemperature(273.15 + 30.0);

        waterSeparator = new Separator("watersep", heatExchanger3.getOutStream());

        // leanTEGStreamToAbsorber = heatExchanger2.getOutStream();
        // getOperations().add(gasStreamToAbsorber);
        getOperations().add(leanTEGStreamToAbsorber);
        getOperations().add(absorbtionColumn);
        // getOperations().add(gasStreamFromAbsorber);
        getOperations().add(valveHP);
        getOperations().add(glycolFlashDrum);
        getOperations().add(valveMP);
        getOperations().add(reboiler);
        getOperations().add(stripperColumn);

        getOperations().add(heatExchanger1);
        getOperations().add(HPpump);
        getOperations().add(heatExchanger2);
        getOperations().add(heatExchanger3);
        getOperations().add(waterSeparator);

        // operations.add(leanTEGStreamToAbsorber);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
        getOperations().runTransient();
    }

    /** {@inheritDoc} */
    @Override
    public void setProperty(String specificationName, double value, String unit) {
        if (unit == "") {
            setProperty(specificationName, value);
        } else {
            setProperty(specificationName, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setProperty(String specificationName, double value) {
        if (specificationName.equals("water dew point specification")) {
            waterDewPontSpecification = value;
        }
        if (specificationName.equals("number of theoretical stages")) {
            numberOfTheoreticalEquilibriumStages = value;
        }
        if (specificationName.equals("designStandardGasFlowRate")) {
            designStandardGasFlowRate = value;
        }
        if (specificationName.equals("designGasFeedTemperature")) {
            designGasFeedTemperature = value;
        }
        if (specificationName.equals("maxAbsorberDesignPressure")) {
            maxAbsorberDesignPressure = value;
        }
        if (specificationName.equals("maxglycolFlowRate")) {
            maxglycolFlowRate = value;
        }
        if (specificationName.equals("flashPressure")) {
            flashPressure = value;
        }
        if (specificationName.equals("reboilerTemperature")) {
            reboilerTemperature = value;
        }
        if (specificationName.equals("regenerationPressure")) {
            regenerationPressure = value;
        }
    }

    /**
     * <p>
     * calcGlycolConcentration.
     * </p>
     *
     * @param y0 a double
     * @return a double
     */
    public double calcGlycolConcentration(double y0) {
        Stream tempStream = (Stream) this.gasStreamToAbsorber.clone();
        tempStream.run();

        double dn = 1.0 * tempStream.getThermoSystem().getPhase(0).getComponent("water")
                .getNumberOfMolesInPhase();
        double error = 1.0, oldError = 0.0;
        double oldNumberOfMoles = 0.0, numberOfMoles = 0.0;
        int iter = 0;
        do {
            iter++;
            oldNumberOfMoles = numberOfMoles;
            tempStream.getThermoSystem().addComponent("TEG", dn);
            tempStream.run();
            numberOfMoles =
                    tempStream.getThermoSystem().getPhase(0).getComponent("TEG").getNumberOfmoles();
            oldError = error;
            error = (tempStream.getThermoSystem().getPhase(0).getComponent("water").getx() - y0);// /
                                                                                                 // y0;

            double derrordn = (error - oldError) / (numberOfMoles - oldNumberOfMoles);
            if (iter < 2) {
                dn = error;
            } else {
                dn = -error / derrordn;
            }
            System.out.println("error " + error);
        } while (Math.abs(error) > 1e-8 && iter < 100);
        leanGlycolMolarFraction =
                tempStream.getThermoSystem().getPhase(1).getComponent("TEG").getx();
        leanGlycolwtFraction = tempStream.getThermoSystem().getPhase(1).getComponent("TEG").getx()
                * tempStream.getThermoSystem().getPhase(1).getComponent("TEG").getMolarMass()
                / tempStream.getThermoSystem().getPhase(1).getMolarMass();

        return leanGlycolwtFraction;
    }

    /**
     * <p>
     * calcKglycol.
     * </p>
     *
     * @return a double
     */
    public double calcKglycol() {
        Stream tempStream = (Stream) this.gasStreamToAbsorber.clone();
        tempStream.getThermoSystem().addComponent("TEG", 5.0 * tempStream.getThermoSystem()
                .getPhase(0).getComponent("water").getNumberOfMolesInPhase());
        tempStream.run();
        double activityCoefficientTEG =
                tempStream.getThermoSystem().getPhase(1).getActivityCoefficient(tempStream
                        .getThermoSystem().getPhase(1).getComponent("water").getComponentNumber());
        double K = tempStream.getThermoSystem().getPhase(0).getComponent("water").getx()
                / (tempStream.getThermoSystem().getPhase(1).getComponent("water").getx()
                        * activityCoefficientTEG);

        return K;
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        gasStreamFromAbsorber.getThermoSystem().display();
        leanTEGStreamToAbsorber.displayResult();
        glycolFlashDrum.displayResult();
        heatExchanger2.displayResult();
        waterSeparator.displayResult();
    }

    /** {@inheritDoc} */
    @Override
    public void calcDesign() {
        setIsCalcDesign(true);
        double yN = gasStreamToAbsorber.getThermoSystem().getPhase(0).getComponent("water").getx();

        // Estimates K value
        double K = calcKglycol();// gasStreamToAbsorber.getThermoSystem().getPhase(1).getComponent("water").getFugasityCoefficient()
                                 // /
                                 // gasStreamToAbsorber.getThermoSystem().getPhase(0).getComponent("water").getFugasityCoefficient();
        gasStreamFromAbsorber = (StreamInterface) gasStreamToAbsorber.clone();
        // gasStreamFromAbsorber.getThermoSystem().addComponent("water", 1.0);
        gasStreamFromAbsorber.getThermoSystem().setTemperature(waterDewPontSpecification);
        gasStreamFromAbsorber.run();
        double y1 =
                gasStreamFromAbsorber.getThermoSystem().getPhase(0).getComponent("water").getx();
        gasStreamFromAbsorber.getThermoSystem().setTemperature(waterDewPontSpecification - 10.0);
        gasStreamFromAbsorber.run();
        double y0 =
                gasStreamFromAbsorber.getThermoSystem().getPhase(0).getComponent("water").getx();
        gasStreamFromAbsorber.run();
        calcGlycolConcentration(y0);

        double Ea = (yN - y1) / (yN - y0);

        double absorptionFactor = solveAbsorptionFactor(Ea);
        leanGlycolMolarFlowRate =
                absorptionFactor * K * designStandardGasFlowRate * 42.28981 / 24.0 / 3600; // kg
                                                                                           // TEG/hr
        // gasStreamFromAbsorber.displayResult();
        // double kgWater =
        // gasStreamToAbsorber.getThermoSystem().getPhase(0).getComponent("water").getx()
        // * designStandardGasFlowRate*1e6 * 42.28981 / 24.0 /3600 *
        // gasStreamFromAbsorber.getThermoSystem().getPhase(0).getComponent("water").getMolarMass();
        // double kgTEGperkgWater = leanGlycolMolarFlowRate / kgWater;

        gasStreamFromAbsorber.getThermoSystem().removePhase(1);
        gasStreamFromAbsorber.getThermoSystem().setTemperature(designGasFeedTemperature);
        gasStreamFromAbsorber.run();
        /*
         * absorbtionColumn.setNumberOfTheoreticalStages( numberOfTheoreticalEquilibriumStages);
         * absorbtionColumn.getMechanicalDesign().setMaxDesignGassVolumeFlow(
         * designStandardGasFlowRate);
         * absorbtionColumn.getMechanicalDesign().setMaxDesignVolumeFlow(
         * designStandardGasFlowRate);
         * absorbtionColumn.getMechanicalDesign().setMaxOperationPressure(
         * maxAbsorberDesignPressure); absorbtionColumn.getMechanicalDesign().calcDesign();
         */
        this.leanTEGStreamToAbsorber.getThermoSystem().removeMoles();
        this.leanTEGStreamToAbsorber.getThermoSystem().addComponent("methane", 1e-15);
        this.leanTEGStreamToAbsorber.getThermoSystem().addComponent("water",
                leanGlycolMolarFlowRate * (1.0 - leanGlycolMolarFraction));
        this.leanTEGStreamToAbsorber.getThermoSystem().addComponent("TEG",
                leanGlycolMolarFlowRate * leanGlycolMolarFraction);
        this.leanTEGStreamToAbsorber.run();
    }

    /** {@inheritDoc} */
    @Override
    public void setDesign() {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 40.0), 1.0);
        testSystem.addComponent("water", leanGlycolMolarFlowRate * (1.0 - leanGlycolMolarFraction));
        testSystem.addComponent("TEG", leanGlycolMolarFlowRate * leanGlycolMolarFraction);
        testSystem.setMixingRule(9);
        this.leanTEGStreamToAbsorber.setThermoSystem(testSystem);
        this.leanTEGStreamToAbsorber.run();
        // this.leanTEGStreamToAbsorber.displayResult();

        absorbtionColumn.getMechanicalDesign().setDesign();
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
                new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 40.0), 70.0);

        testSystem.addComponent("methane", 100.0);
        testSystem.addComponent("water", 0.1);
        testSystem.addComponent("TEG", 0.0);

        testSystem.createDatabase(true);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setMixingRule(10);
        testSystem.setTotalFlowRate(5, "MSm^3/day");

        Stream gasinletStream = new Stream(testSystem);

        neqsim.thermo.system.SystemInterface strippingGasSystem =
                new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 40.0), 70.0);

        strippingGasSystem.addComponent("methane", 1.0);
        strippingGasSystem.addComponent("water", 0);
        strippingGasSystem.addComponent("TEG", 0);

        strippingGasSystem.createDatabase(true);
        strippingGasSystem.setMultiPhaseCheck(true);
        strippingGasSystem.setMixingRule(10);
        strippingGasSystem.setTotalFlowRate(0.005, "MSm^3/day");

        Stream strippingGasStream = new Stream(strippingGasSystem);

        StreamSaturatorUtil saturator = new StreamSaturatorUtil(gasinletStream);

        Separator separator = new Separator("Separator 1", saturator.getOutStream());

        neqsim.processSimulation.processSystem.processModules.GlycolDehydrationlModule TEGplant =
                new neqsim.processSimulation.processSystem.processModules.GlycolDehydrationlModule();
        TEGplant.addInputStream("gasStreamToAbsorber", saturator.getOutStream());
        TEGplant.addInputStream("strippingGas", strippingGasStream);
        TEGplant.setSpecification("water dew point specification", 273.15 - 10.0);
        TEGplant.setSpecification("number of theoretical stages", 1.5);
        TEGplant.setSpecification("maxAbsorberDesignPressure", 70.0);
        TEGplant.setSpecification("designStandardGasFlowRate", 5.0e6);
        TEGplant.setSpecification("maxglycolFlowRate", 10.0);
        TEGplant.setSpecification("designGasFeedTemperature", 273.15 + 30);
        TEGplant.setSpecification("flashPressure", 5.0);
        TEGplant.setSpecification("regenerationPressure", 1.21325);
        TEGplant.setSpecification("reboilerTemperature", 273.15 + 205.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(gasinletStream);
        operations.add(saturator);
        // operations.add(separator);
        operations.add(TEGplant);

        operations.run();
        saturator.getOutStream().displayResult();
        separator.getThermoSystem().display();
        ((ProcessEquipmentBaseClass) operations.getUnit("reboiler")).run();
        // separator.getGasOutStream().displayResult();
        // TEGplant.displayResult();
        // TEGplant.calcDesign();
        // TEGplant.setDesign();
        TEGplant.getOutputStream("gasStreamFromAbsorber").displayResult();

        TEGplant.getOutputStream("liquidFromStripper").run();
        TEGplant.getOutputStream("liquidFromStripper").displayResult();
        System.out.println("wt TEG " + TEGplant.getOutputStream("liquidFromStripper").getFluid()
                .getPhase(0).getWtFrac("TEG"));
        System.out.println("reboiler duty "
                + ((Heater) operations.getUnit("reboiler")).getDuty() / 1000.0 + " kW");
        System.out.println("Lean TEG flow "
                + ((Pump) operations.getUnit("HP lean TEG pump")).getFluid().getFlowRate("kg/hr")
                + " kg/hr");
        System.out.println("Lean TEG pump power "
                + ((Pump) operations.getUnit("HP lean TEG pump")).getPower() / 1000.0 + " kW");

        ((Separator) operations.getUnit("flash drum")).displayResult();
        ((Separator) operations.getUnit("watersep")).displayResult();
        ((ProcessEquipmentBaseClass) operations.getUnit("reboiler")).run();
        ((ProcessEquipmentBaseClass) operations.getUnit("reboiler")).displayResult();
        // TEGplant.getOutputStream("condenserStripper").displayResult();
    }

    /**
     * <p>
     * Getter for the field <code>flashPressure</code>.
     * </p>
     *
     * @return a double
     */
    public double getFlashPressure() {
        return flashPressure;
    }

    /**
     * <p>
     * Setter for the field <code>flashPressure</code>.
     * </p>
     *
     * @param flashPressure a double
     */
    public void setFlashPressure(double flashPressure) {
        this.flashPressure = flashPressure;
    }
}
