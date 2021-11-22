package neqsim.processSimulation.processEquipment.absorber;

import neqsim.processSimulation.mechanicalDesign.absorber.AbsorberMechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class SimpleAbsorber extends Separator implements AbsorberInterface {
    private static final long serialVersionUID = 1000;

    boolean setTemperature = false;
    Stream[] outStream;
    Stream[] inStream;
    SystemInterface system;
    protected double temperatureOut = 0, dT = 0.0;
    private int numberOfStages = 5;
    private double numberOfTheoreticalStages = 3.0;
    double absorptionEfficiency = 0.5;
    private double HTU = 0.85;
    private double NTU = 2.0;
    private double stageEfficiency = 0.25;
    private double fsFactor = 0.0;

    public SimpleAbsorber() {
        mechanicalDesign = new AbsorberMechanicalDesign(this);
    }

    public SimpleAbsorber(Stream inStream1) {
        mechanicalDesign = new AbsorberMechanicalDesign(this);

        outStream = new Stream[2];
        inStream = new Stream[2];
        this.inStream[0] = inStream1;
        this.inStream[1] = inStream1;
        outStream[0] = (Stream) inStream1.clone();
        outStream[1] = (Stream) inStream1.clone();

        SystemInterface systemOut1 = (SystemInterface) inStream1.getThermoSystem().clone();
        outStream[0].setThermoSystem(systemOut1);

        double molCO2 =
                inStream1.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfmoles();
        System.out.println("mol CO2 " + molCO2);
        SystemInterface systemOut0 = (SystemInterface) inStream1.getThermoSystem().clone();
        systemOut0.init(0);
        systemOut0.addComponent("MDEA", molCO2 * absorptionEfficiency);
        systemOut0.addComponent("water", molCO2 * absorptionEfficiency * 10.0);
        systemOut0.chemicalReactionInit();
        systemOut0.createDatabase(true);
        systemOut0.setMixingRule(4);
        outStream[1].setThermoSystem(systemOut0);
        outStream[1].run();
    }

    @Override
    public void setName(String name) {
        // outStream[0].setName(name + "_Sout1");
        // outStream[1].setName(name + "_Sout2");
        super.setName(name);
    }

    public void setdT(double dT) {
        this.dT = dT;
    }

    public Stream getOutStream() {
        return outStream[0];
    }

    public Stream getOutStream(int i) {
        return outStream[i];
    }

    public Stream getSolventInStream() {
        return inStream[0];
    }

    public Stream getInStream(int i) {
        return inStream[i];
    }

    public void setOutTemperature(double temperature) {
        this.temperatureOut = temperature;
    }

    public void getOutTemperature(int i) {
        outStream[i].getThermoSystem().getTemperature();
    }

    public void getInTemperature(int i) {
        inStream[i].getThermoSystem().getTemperature();
    }

    @Override
    public void run() {
        SystemInterface systemOut1 = (SystemInterface) inStream[1].getThermoSystem().clone();
        outStream[0].setThermoSystem(systemOut1);
        outStream[0].run();

        outStream[1].run();

        double error = 1e5;
        error = absorptionEfficiency - (outStream[1].getThermoSystem().getPhase(1)
                .getComponent("CO2").getNumberOfMolesInPhase()
                + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-")
                        .getNumberOfMolesInPhase())
                / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA")
                        .getNumberOfMolesInPhase()
                        + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+")
                                .getNumberOfMolesInPhase());
        int iter = 0;
        do {
            iter++;
            double factor = (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA")
                    .getNumberOfMolesInPhase()
                    + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+")
                            .getNumberOfMolesInPhase());
            // outStream[1].getThermoSystem().addComponent("CO2",(20.0-outStream[1].getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfMolesInPhase()),0);
            outStream[1].getThermoSystem().addComponent("MDEA", -error * factor);
            outStream[1].getThermoSystem().addComponent("water", -error * 10.0 * factor);
            outStream[1].run();
            error = absorptionEfficiency - ((outStream[1].getThermoSystem().getPhase(1)
                    .getComponent("CO2").getNumberOfMolesInPhase()
                    + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-")
                            .getNumberOfMolesInPhase())
                    / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA")
                            .getNumberOfMolesInPhase()
                            + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+")
                                    .getNumberOfMolesInPhase()));

            System.out.println("error " + error);
        } while (Math.abs(error) > 1e-4 && iter < 30
                && outStream[1].getThermoSystem().getPhase(1).getBeta() > 0
                && outStream[0].getThermoSystem().getPhase(1).getBeta() > 0);
    }

    @Override
    public void displayResult() {
        outStream[0].displayResult();
        outStream[1].displayResult();
    }

    public void runTransient() {}

    @Override
    public void setAproachToEquilibrium(double eff) {
        this.absorptionEfficiency = eff;
    }

    public double getNumberOfTheoreticalStages() {
        return numberOfTheoreticalStages;
    }

    public void setNumberOfTheoreticalStages(double numberOfTheoreticalStages) {
        this.numberOfTheoreticalStages = numberOfTheoreticalStages;
    }

    public int getNumberOfStages() {
        return numberOfStages;
    }

    public void setNumberOfStages(int numberOfStages) {
        this.numberOfStages = numberOfStages;
    }

    public double getStageEfficiency() {
        return stageEfficiency;
    }

    public void setStageEfficiency(double stageEfficiency) {
        this.stageEfficiency = stageEfficiency;
    }

    public double getHTU() {
        return HTU;
    }

    public void setHTU(double HTU) {
        this.HTU = HTU;
    }

    public double getNTU() {
        return NTU;
    }

    public void setNTU(double NTU) {
        this.NTU = NTU;
    }

    public double getFsFactor() {
        double intArea = 3.14 * getInternalDiameter() * getInternalDiameter() / 4.0;
        return getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea
                * Math.sqrt(getGasOutStream().getThermoSystem().getDensity("kg/m3"));
    }

    public double getWettingRate() {
        double intArea = 3.14 * getInternalDiameter() * getInternalDiameter() / 4.0;
        return getLiquidOutStream().getThermoSystem().getFlowRate("m3/hr") / intArea;
    }
}
