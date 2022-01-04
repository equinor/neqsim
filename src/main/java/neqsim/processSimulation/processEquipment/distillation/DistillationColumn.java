package neqsim.processSimulation.processEquipment.distillation;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.MixerInterface;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * DistillationColumn class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class DistillationColumn extends ProcessEquipmentBaseClass implements DistillationInterface {
    private static final long serialVersionUID = 1000;
    private boolean doInitializion = true;
    boolean hasReboiler = false, hasCondenser = false;
    protected ArrayList<SimpleTray> trays = new ArrayList<SimpleTray>(0);
    double condenserCoolingDuty = 10.0;
    private double reboilerTemperature = 273.15;
    private double condeserTemperature = 270.15;
    double topTrayPressure = -1.0, bottomTrayPressure = -1.0;
    int numberOfTrays = 1;
    private int feedTrayNumber = 1;
    StreamInterface stream_3 = new Stream(), gasOutStream = new Stream(),
            liquidOutStream = new Stream(), feedStream = null;
    boolean stream_3isset = false;
    private double internalDiameter = 1.0;
    neqsim.processSimulation.processSystem.ProcessSystem distoperations;
    Heater heater;
    Separator separator2;

    /**
     * <p>
     * Constructor for DistillationColumn.
     * </p>
     *
     * @param numberOfTraysLocal a int
     * @param hasReboiler a boolean
     * @param hasCondenser a boolean
     */
    public DistillationColumn(int numberOfTraysLocal, boolean hasReboiler, boolean hasCondenser) {
        this.hasReboiler = hasReboiler;
        this.hasCondenser = hasCondenser;
        distoperations = new neqsim.processSimulation.processSystem.ProcessSystem();
        this.numberOfTrays = numberOfTraysLocal;
        if (hasReboiler) {
            trays.add(new Reboiler());
            this.numberOfTrays++;
        }
        for (int i = 0; i < numberOfTraysLocal; i++) {
            trays.add(new SimpleTray());
        }
        if (hasCondenser) {
            trays.add(new Condenser());
            this.numberOfTrays++;
        }
        for (int i = 0; i < this.numberOfTrays; i++) {
            distoperations.add(trays.get(i));
        }
    }

    /**
     * <p>
     * addFeedStream.
     * </p>
     *
     * @param inputStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     * @param feedTrayNumber a int
     */
    public void addFeedStream(StreamInterface inputStream, int feedTrayNumber) {
        feedStream = inputStream;
        getTray(feedTrayNumber).addStream(inputStream);
        setFeedTrayNumber(feedTrayNumber);
        double moles = inputStream.getThermoSystem().getTotalNumberOfMoles();
        gasOutStream.setThermoSystem((SystemInterface) inputStream.getThermoSystem().clone());
        gasOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);
        liquidOutStream.setThermoSystem((SystemInterface) inputStream.getThermoSystem().clone());
        liquidOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);
    }

    /**
     * <p>
     * init.
     * </p>
     */
    public void init() {
        if (!isDoInitializion()) {
            return;
        }
        setDoInitializion(false);
        ((Runnable) trays.get(feedTrayNumber)).run();

        if (getTray(feedTrayNumber).getStream(0).getFluid().getNumberOfPhases() == 1) {
            for (int i = 0; i < numberOfTrays; i++) {
                if (getTray(i).getNumberOfInputStreams() > 0 && i != feedTrayNumber) {
                    getTray(feedTrayNumber).addStream(trays.get(i).getStream(0));
                    ((Runnable) trays.get(feedTrayNumber)).run();
                    if (getTray(feedTrayNumber).getStream(0).getFluid().getNumberOfPhases() > 1)
                        break;
                }
            }
        }
        ((MixerInterface) trays.get(numberOfTrays - 1))
                .addStream(trays.get(feedTrayNumber).getGasOutStream());
        ((Mixer) trays.get(numberOfTrays - 1)).getStream(0).getThermoSystem()
                .setTotalNumberOfMoles(((Mixer) trays.get(numberOfTrays - 1)).getStream(0)
                        .getThermoSystem().getTotalNumberOfMoles() * (1.0e-6));
        ((MixerInterface) trays.get(0)).addStream(trays.get(feedTrayNumber).getLiquidOutStream());
        int streamNumbReboil = ((SimpleTray) trays.get(0)).getNumberOfInputStreams() - 1;
        ((Mixer) trays.get(0)).getStream(streamNumbReboil).getThermoSystem()
                .setTotalNumberOfMoles(((Mixer) trays.get(0)).getStream(streamNumbReboil)
                        .getThermoSystem().getTotalNumberOfMoles() * (1.0e-6));

        // ((Runnable) trays.get(numberOfTrays - 1)).run();
        ((Runnable) trays.get(0)).run();

        condeserTemperature =
                ((MixerInterface) trays.get(numberOfTrays - 1)).getThermoSystem().getTemperature();
        reboilerTemperature = ((MixerInterface) trays.get(0)).getThermoSystem().getTemperature();

        // double deltaTemp = (reboilerTemperature - condeserTemperature) / (numberOfTrays * 1.0);
        double feedTrayTemperature =
                getTray(getFeedTrayNumber()).getThermoSystem().getTemperature();

        double deltaTempCondeser = (feedTrayTemperature - condeserTemperature)
                / (numberOfTrays * 1.0 - feedTrayNumber - 1);
        double deltaTempReboiler =
                (reboilerTemperature - feedTrayTemperature) / (feedTrayNumber * 1.0);

        double delta = 0;
        for (int i = feedTrayNumber + 1; i < numberOfTrays; i++) {
            delta += deltaTempCondeser;
            ((Mixer) trays.get(i)).setTemperature(
                    getTray(getFeedTrayNumber()).getThermoSystem().getTemperature() - delta);
        }
        delta = 0;
        for (int i = feedTrayNumber - 1; i >= 0; i--) {
            delta += deltaTempReboiler;
            ((Mixer) trays.get(i)).setTemperature(
                    getTray(getFeedTrayNumber()).getThermoSystem().getTemperature() + delta);
        }

        for (int i = 1; i < numberOfTrays - 1; i++) {
            ((MixerInterface) trays.get(i)).addStream(trays.get(i - 1).getGasOutStream());
            trays.get(i).init();
            ((Runnable) trays.get(i)).run();
        }

        ((MixerInterface) trays.get(numberOfTrays - 1)).replaceStream(0,
                trays.get(numberOfTrays - 2).getGasOutStream());
        trays.get(numberOfTrays - 1).init();
        ((Runnable) trays.get(numberOfTrays - 1)).run();

        for (int i = numberOfTrays - 2; i >= 1; i--) {
            ((MixerInterface) trays.get(i)).addStream(trays.get(i + 1).getLiquidOutStream());
            trays.get(i).init();
            ((Runnable) trays.get(i)).run();
        }
        int streamNumb = ((SimpleTray) trays.get(0)).getNumberOfInputStreams() - 1;
        ((MixerInterface) trays.get(0)).replaceStream(streamNumb,
                trays.get(1).getLiquidOutStream());
        trays.get(0).init();
        ((Runnable) trays.get(0)).run();

    }

    /**
     * <p>
     * Getter for the field <code>gasOutStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getGasOutStream() {
        return gasOutStream;
    }

    /**
     * <p>
     * Getter for the field <code>liquidOutStream</code>.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getLiquidOutStream() {
        return liquidOutStream;
    }

    /**
     * <p>
     * getTray.
     * </p>
     *
     * @param trayNumber a int
     * @return a {@link neqsim.processSimulation.processEquipment.distillation.SimpleTray} object
     */
    public SimpleTray getTray(int trayNumber) {
        return trays.get(trayNumber);
    }

    /** {@inheritDoc} */
    @Override
    public void setNumberOfTrays(int number) {
        int oldNumberOfTrays = numberOfTrays;

        int tempNumberOfTrays = number;

        if (hasReboiler) {
            tempNumberOfTrays++;
        }
        if (hasCondenser) {
            tempNumberOfTrays++;
        }

        int change = tempNumberOfTrays - oldNumberOfTrays;

        if (change > 0) {
            for (int i = 0; i < change; i++) {
                trays.add(1, new SimpleTray());
            }
        } else if (change < 0) {
            for (int i = 0; i > change; i--) {
                trays.remove(1);
            }
        }
        numberOfTrays = tempNumberOfTrays;

        setDoInitializion(true);
        init();
    }

    /**
     * <p>
     * setTopCondeserDuty.
     * </p>
     *
     * @param duty a double
     */
    public void setTopCondeserDuty(double duty) {
        condenserCoolingDuty = duty;
    }

    /**
     * <p>
     * setTopPressure.
     * </p>
     *
     * @param topPressure a double
     */
    public void setTopPressure(double topPressure) {
        topTrayPressure = topPressure;
    }

    /**
     * <p>
     * setBottomPressure.
     * </p>
     *
     * @param bottomPressure a double
     */
    public void setBottomPressure(double bottomPressure) {
        bottomTrayPressure = bottomPressure;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        double dp = 0.0;
        if (bottomTrayPressure < 0) {
            bottomTrayPressure = getTray(feedTrayNumber).getStream(0).getPressure();
        }
        if (topTrayPressure < 0) {
            topTrayPressure = getTray(feedTrayNumber).getStream(0).getPressure();
        }
        if (numberOfTrays > 1) {
            dp = (bottomTrayPressure - topTrayPressure) / (numberOfTrays - 1.0);
        }
        for (int i = 0; i < numberOfTrays; i++) {
            trays.get(i).setPressure(bottomTrayPressure - i * dp);
        }
        getTray(feedTrayNumber).getStream(0)
                .setThermoSystem((SystemInterface) feedStream.getThermoSystem().clone());

        if (numberOfTrays == 1) {
            ((Runnable) trays.get(0)).run();
            gasOutStream.setThermoSystem(
                    (SystemInterface) trays.get(0).getGasOutStream().getThermoSystem().clone());
            liquidOutStream.setThermoSystem(
                    (SystemInterface) trays.get(0).getLiquidOutStream().getThermoSystem().clone());
            return;
        }

        if (isDoInitializion()) {
            this.init();
        }
        double err = 1.0e10, errOld;
        int iter = 0;
        double[] oldtemps = new double[numberOfTrays];
        ((Runnable) trays.get(feedTrayNumber)).run();

        do {
            iter++;
            errOld = err;
            err = 0.0;
            for (int i = 0; i < numberOfTrays; i++) {
                oldtemps[i] = ((MixerInterface) trays.get(i)).getThermoSystem().getTemperature();
            }

            for (int i = feedTrayNumber; i > 1; i--) {
                ((Mixer) trays.get(i - 1)).replaceStream(1, trays.get(i).getLiquidOutStream());
                trays.get(i - 1).setPressure(bottomTrayPressure - (i - 1) * dp);
                ((Runnable) trays.get(i - 1)).run();
            }
            int streamNumb = ((SimpleTray) trays.get(0)).getNumberOfInputStreams() - 1;
            ((Mixer) trays.get(0)).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
            ((Runnable) trays.get(0)).run();

            for (int i = 1; i <= numberOfTrays - 1; i++) {
                int replaceStream = 0;
                if (i == feedTrayNumber)
                    replaceStream = 1;
                ((Mixer) trays.get(i)).replaceStream(replaceStream,
                        trays.get(i - 1).getGasOutStream());
                ((Runnable) trays.get(i)).run();
            }

            for (int i = numberOfTrays - 2; i == feedTrayNumber; i--) {
                int replaceStream = 1;
                if (i == feedTrayNumber)
                    replaceStream = 2;
                if (i == feedTrayNumber && i == 0)
                    replaceStream = 1;
                ((Mixer) trays.get(i)).replaceStream(replaceStream,
                        trays.get(i + 1).getLiquidOutStream());
                ((Runnable) trays.get(i)).run();
            }
            for (int i = 0; i < numberOfTrays; i++) {
                err += Math.abs(oldtemps[i]
                        - ((MixerInterface) trays.get(i)).getThermoSystem().getTemperature());
            }
            System.out.println("error iter " + err + " iteration " + iter);
            // massBalanceCheck();
        } while (err > 1e-4 && err < errOld && iter < 10);// && !massBalanceCheck());

        // massBalanceCheck();
        gasOutStream.setThermoSystem((SystemInterface) trays.get(numberOfTrays - 1)
                .getGasOutStream().getThermoSystem().clone());
        liquidOutStream.setThermoSystem(
                (SystemInterface) trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        distoperations.displayResult();
    }

    /**
     * <p>
     * massBalanceCheck.
     * </p>
     *
     * @return a boolean
     */
    public boolean massBalanceCheck() {
        double[] massInput = new double[numberOfTrays];
        double[] massOutput = new double[numberOfTrays];
        double[] massBalance = new double[numberOfTrays];
        System.out.println("water in feed "
                + feedStream.getFluid().getPhase(0).getComponent("water").getNumberOfmoles());
        System.out.println("water in strip gas feed " + trays.get(0).getStream(0).getFluid()
                .getPhase(0).getComponent("water").getNumberOfmoles());

        for (int i = 0; i < numberOfTrays; i++) {
            int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
            for (int j = 0; j < numberOfInputStreams; j++) {
                massInput[i] += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
            }
            massOutput[i] += trays.get(i).getGasOutStream().getFlowRate("kg/hr");
            massOutput[i] += trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
            massBalance[i] = massInput[i] - massOutput[i];
            System.out.println("tray " + i + " number of input streams " + numberOfInputStreams
                    + " massinput " + massInput[i] + " massoutput " + massOutput[i]
                    + " massbalance " + massBalance[i] + " gasout "
                    + trays.get(i).getGasOutStream().getFlowRate("kg/hr") + " liquidout "
                    + trays.get(i).getLiquidOutStream().getFlowRate("kg/hr") + " pressure "
                    + trays.get(i).getGasOutStream().getPressure() + " temperature "
                    + trays.get(i).getGasOutStream().getTemperature("C"));

            System.out
                    .println("tray " + i + " number of input streams " + numberOfInputStreams
                            + " water in gasout "
                            + trays.get(i).getGasOutStream().getFluid().getPhase(0)
                                    .getComponent("water").getNumberOfmoles()
                            + " water in liquidout "
                            + trays.get(i).getLiquidOutStream().getFluid().getPhase(0)
                                    .getComponent("water").getNumberOfmoles()
                            + " pressure " + trays.get(i).getGasOutStream().getPressure()
                            + " temperature " + trays.get(i).getGasOutStream().getTemperature("C"));

        }

        double massError = 0.0;
        for (int i = 0; i < numberOfTrays; i++) {
            massError += Math.abs(massBalance[i]);
        }
        if (massError > 1e-6)
            return false;
        else
            return true;
    }

    /**
     * <p>
     * energyBalanceCheck.
     * </p>
     */
    public void energyBalanceCheck() {
        double[] energyInput = new double[numberOfTrays];
        double[] energyOutput = new double[numberOfTrays];
        double[] energyBalance = new double[numberOfTrays];
        for (int i = 0; i < numberOfTrays; i++) {
            int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
            for (int j = 0; j < numberOfInputStreams; j++) {
                energyInput[i] += trays.get(i).getStream(j).getFluid().getEnthalpy();
            }
            energyOutput[i] += trays.get(i).getGasOutStream().getFluid().getEnthalpy();
            energyOutput[i] += trays.get(i).getLiquidOutStream().getFluid().getEnthalpy();
            energyBalance[i] = energyInput[i] - energyOutput[i];
            System.out.println("tray " + i + " number of input streams " + numberOfInputStreams
                    + " energyinput " + energyInput[i] + " energyoutput " + energyOutput[i]
                    + " energybalance " + energyBalance[i] + " gasout "
                    + trays.get(i).getGasOutStream().getFlowRate("kg/hr") + " liquidout "
                    + trays.get(i).getLiquidOutStream().getFlowRate("kg/hr") + " pressure "
                    + trays.get(i).getGasOutStream().getPressure() + " temperature "
                    + trays.get(i).getGasOutStream().getTemperature("C"));
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
                new neqsim.thermo.system.SystemSrkEos((273.15 - 0.0), 15.000);
        // testSystem.addComponent("methane", 10.00);
        testSystem.addComponent("ethane", 10.0);
        testSystem.addComponent("CO2", 10.0);
        testSystem.addComponent("propane", 20.0);
        // testSystem.addComponent("i-butane", 5.0);
        // testSystem.addComponent("n-hexane", 15.0);
        // testSystem.addComponent("n-heptane", 30.0);
        // testSystem.addComponent("n-octane", 4.0);
        // testSystem.addComponent("n-nonane", 3.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        ops.TPflash();
        testSystem.display();
        Stream stream_1 = new Stream("Stream1", testSystem);

        DistillationColumn column = new DistillationColumn(5, true, true);
        column.addFeedStream(stream_1, 3);
        // column.getReboiler().setHeatInput(520000.0);
        ((Reboiler) column.getReboiler()).setRefluxRatio(0.5);
        // column.getCondenser().setHeatInput(-70000.0);
        // ((Condenser) column.getCondenser()).setRefluxRatio(0.2);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(column);
        operations.run();

        column.displayResult();
        System.out.println("reboiler duty" + ((Reboiler) column.getReboiler()).getDuty());
        System.out.println("condeser duty" + ((Condenser) column.getCondenser()).getDuty());

    }

    /**
     * <p>
     * getReboiler.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.distillation.SimpleTray} object
     */
    public SimpleTray getReboiler() {
        return trays.get(0);
    }

    /**
     * <p>
     * getCondenser.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.distillation.SimpleTray} object
     */
    public SimpleTray getCondenser() {
        return trays.get(trays.size() - 1);
    }

    /**
     * <p>
     * Getter for the field <code>reboilerTemperature</code>.
     * </p>
     *
     * @return the reboilerTemperature
     */
    public double getReboilerTemperature() {
        return reboilerTemperature;
    }

    /**
     * <p>
     * Setter for the field <code>reboilerTemperature</code>.
     * </p>
     *
     * @param reboilerTemperature the reboilerTemperature to set
     */
    public void setReboilerTemperature(double reboilerTemperature) {
        this.reboilerTemperature = reboilerTemperature;
    }

    /**
     * <p>
     * getCondenserTemperature.
     * </p>
     *
     * @return the condeserTemperature
     */
    public double getCondenserTemperature() {
        return condeserTemperature;
    }

    /**
     * <p>
     * setCondenserTemperature.
     * </p>
     *
     * @param condeserTemperature the condeserTemperature to set
     */
    public void setCondenserTemperature(double condeserTemperature) {
        this.condeserTemperature = condeserTemperature;
    }

    /**
     * <p>
     * Getter for the field <code>feedTrayNumber</code>.
     * </p>
     *
     * @return the feedTrayNumber
     */
    public int getFeedTrayNumber() {
        return feedTrayNumber;
    }

    /**
     * <p>
     * Setter for the field <code>feedTrayNumber</code>.
     * </p>
     *
     * @param feedTrayNumber the feedTrayNumber to set
     */
    public void setFeedTrayNumber(int feedTrayNumber) {
        this.feedTrayNumber = feedTrayNumber;
    }

    /**
     * <p>
     * isDoInitializion.
     * </p>
     *
     * @return a boolean
     */
    public boolean isDoInitializion() {
        return doInitializion;
    }

    /**
     * <p>
     * Setter for the field <code>doInitializion</code>.
     * </p>
     *
     * @param doInitializion a boolean
     */
    public void setDoInitializion(boolean doInitializion) {
        this.doInitializion = doInitializion;
    }

    /**
     * <p>
     * getFsFactor.
     * </p>
     *
     * @return a double
     */
    public double getFsFactor() {
        double intArea = 3.14 * getInternalDiameter() * getInternalDiameter() / 4.0;
        return getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea
                * Math.sqrt(getGasOutStream().getThermoSystem().getDensity("kg/m3"));
    }

    /**
     * <p>
     * Getter for the field <code>internalDiameter</code>.
     * </p>
     *
     * @return a double
     */
    public double getInternalDiameter() {
        return internalDiameter;
    }

    /**
     * <p>
     * Setter for the field <code>internalDiameter</code>.
     * </p>
     *
     * @param internalDiameter a double
     */
    public void setInternalDiameter(double internalDiameter) {
        this.internalDiameter = internalDiameter;
    }
}
