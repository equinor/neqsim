/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */
package neqsim.processSimulation.processEquipment.adsorber;

import neqsim.processSimulation.mechanicalDesign.adsorber.AdsorberMechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class SimpleAdsorber extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface {

    private static final long serialVersionUID = 1000;

    ThermodynamicOperations testOps;
    boolean setTemperature = false;
    String name = new String();
    StreamInterface[] outStream;
    StreamInterface[] inStream;
    SystemInterface system;
    protected double temperatureOut = 0, dT = 0.0;
    private int numberOfStages = 5;
    private double numberOfTheoreticalStages = 3.0;
    double absorptionEfficiency = 0.5;
    private double HTU = 0.85;
    private double NTU = 2.0;
    private double stageEfficiency = 0.25;

    /**
     * Creates new Heater
     */
    public SimpleAdsorber() {
        mechanicalDesign = new AdsorberMechanicalDesign(this);
    }

    public SimpleAdsorber(StreamInterface inStream1) {
        mechanicalDesign = new AdsorberMechanicalDesign(this);

        outStream = new Stream[2];
        inStream = new Stream[2];
        this.inStream[0] = inStream1;
        this.inStream[1] = inStream1;
        outStream[0] = (Stream) inStream1.clone();
        outStream[1] = (Stream) inStream1.clone();


        SystemInterface systemOut1 = (SystemInterface) inStream1.getThermoSystem().clone();
        outStream[0].setThermoSystem(systemOut1);

        double molCO2 = inStream1.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfmoles();
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

    public void setName(String name) {
        outStream[0].setName(name + "_Sout1");
        outStream[1].setName(name + "_Sout2");
        this.name = name;
    }

    public void setdT(double dT) {
        this.dT = dT;
    }

    public StreamInterface getOutStream(int i) {
        return outStream[i];
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

    public void run() {
        SystemInterface systemOut1 = (SystemInterface) inStream[1].getThermoSystem().clone();
        outStream[0].setThermoSystem(systemOut1);
        outStream[0].run();

        outStream[1].run();

        double error = 1e5;
        error = absorptionEfficiency - (outStream[1].getThermoSystem().getPhase(1).getComponent("CO2").getNumberOfMolesInPhase() + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-").getNumberOfMolesInPhase())
                / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase() + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+").getNumberOfMolesInPhase());
        int iter = 0;
        do {
            iter++;
            double factor = (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase() + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+").getNumberOfMolesInPhase());
            //outStream[1].getThermoSystem().addComponent("CO2",(20.0-outStream[1].getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfMolesInPhase()),0);
            outStream[1].getThermoSystem().addComponent("MDEA", -error * factor);
            outStream[1].getThermoSystem().addComponent("water", -error * 10.0 * factor);
            outStream[1].run();
            error = absorptionEfficiency - ((outStream[1].getThermoSystem().getPhase(1).getComponent("CO2").getNumberOfMolesInPhase() + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-").getNumberOfMolesInPhase())
                    / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase() + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+").getNumberOfMolesInPhase()));

            System.out.println("error " + error);
        } while (Math.abs(error) > 1e-4 && iter < 30 && outStream[1].getThermoSystem().getPhase(1).getBeta() > 0 && outStream[0].getThermoSystem().getPhase(1).getBeta() > 0);

    }

    public void displayResult() {
        outStream[0].displayResult();
        outStream[1].displayResult();
    }

    public String getName() {
        return name;
    }

    public void runTransient() {
    }

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
}
