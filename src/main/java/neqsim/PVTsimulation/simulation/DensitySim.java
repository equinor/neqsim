/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.PVTsimulation.simulation;

import java.util.ArrayList;
import neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompViscosity.linearLiquidModel.ViscosityFunction;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author esol
 */
public class DensitySim extends BasePVTsimulation {

    private static final long serialVersionUID = 1000;

    double[] temperature = null;

    double[] pressure = null;
    private double[] waxFraction = null;
    private double[] gasDensity;
    private double[] oilDensity;
    private double[] aqueousDensity;

    public DensitySim(SystemInterface tempSystem) {
        super(tempSystem);
        temperature = new double[1];
        pressure = new double[1];
        temperature[0] = tempSystem.getTemperature();
        pressure[0] = tempSystem.getPressure();
    }

    public void setTemperaturesAndPressures(double[] temperature, double[] pressure) {

        this.pressure = pressure;
        this.temperature = temperature;
        experimentalData = new double[temperature.length][1];

    }

    public void runTuning() {
        ArrayList sampleList = new ArrayList();

        try {
            System.out.println("adding....");

            for (int i = 0; i < experimentalData[0].length; i++) {
                ViscosityFunction function = new ViscosityFunction();
                double guess[] = { 1.0 };// getThermoSystem().getPhase(0).getComponent(0).getCriticalViscosity()};
                function.setInitialGuess(guess);

                SystemInterface tempSystem = getThermoSystem();// (SystemInterface) getThermoSystem().clone();

                tempSystem.setTemperature(temperature[i]);
                tempSystem.setPressure(pressure[i]);
                thermoOps.TPflash();
                // tempSystem.display();
                double sample1[] = { temperature[i] };
                double viscosity = experimentalData[0][i];
                double standardDeviation1[] = { 1.5 };
                SampleValue sample = new SampleValue(viscosity, viscosity / 50.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(tempSystem);
                sampleList.add(sample);

            }
        } catch (Exception e) {
            System.out.println("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);

        optimizer = new LevenbergMarquardt();
        optimizer.setMaxNumberOfIterations(20);

        optimizer.setSampleSet(sampleSet);
        optimizer.solve();
        runCalc();
        optimizer.displayCurveFit();
    }

    public void runCalc() {
        gasDensity = new double[pressure.length];
        oilDensity = new double[pressure.length];
        aqueousDensity = new double[pressure.length];

        for (int i = 0; i < pressure.length; i++) {
            // thermoOps.setSystem(getThermoSystem());
            getThermoSystem().setPressure(pressure[i]);
            getThermoSystem().setTemperature(temperature[i]);
            thermoOps.TPflash();
            getThermoSystem().initPhysicalProperties();
            waxFraction[i] = 0.0;

            if (getThermoSystem().hasPhaseType("gas")) {
                gasDensity[i] = getThermoSystem().getPhase("gas").getPhysicalProperties().getViscosity();
            }
            if (getThermoSystem().hasPhaseType("oil")) {
                oilDensity[i] = getThermoSystem().getPhase("oil").getPhysicalProperties().getViscosity();
            }
            if (getThermoSystem().hasPhaseType("aqueous")) {
                aqueousDensity[i] = getThermoSystem().getPhase("aqueous").getPhysicalProperties().getViscosity();
            }
        }
    }

    public static void main(String[] args) {
        SystemInterface tempSystem = new SystemSrkEos(298.0, 10.0);
        // tempSystem.addComponent("n-heptane", 6.78);

        // tempSystem.addTBPfraction("C19", 10.13, 270.0 / 1000.0, 0.814);
        tempSystem.addPlusFraction("C20", 10.62, 100.0 / 1000.0, 0.73);

        // tempSystem.getCharacterization().characterisePlusFraction();
        // tempSystem.getWaxModel().addTBPWax();
        tempSystem.createDatabase(true);
        tempSystem.setMixingRule(2);
        // tempSystem.addSolidComplexPhase("wax");
        // tempSystem.setMultiphaseWaxCheck(true);
        // tempSystem.setMultiPhaseCheck(true);
        tempSystem.init(0);
        tempSystem.init(1);

        DensitySim sepSim = new DensitySim(tempSystem);
        double[] temps = { 300.15, 293.15, 283.15, 273.15, 264.15 };
        double[] pres = { 5, 5, 5, 5.0, 5.0 };
        sepSim.setTemperaturesAndPressures(temps, pres);
        sepSim.runCalc();

        double[][] expData = { { 2e-4, 3e-4, 4e-4, 5e-4, 6e-4 }, };
        sepSim.setExperimentalData(expData);
        sepSim.runTuning();
//sepSim.runCalc();
        double a = sepSim.getGasDensity()[0];
        double a2 = sepSim.getOilDensity()[0];
        sepSim.getThermoSystem().display();
        // sepSim.tuneModel(exptemperatures, exppressures, expwaxFrations);
    }

    /**
     * @return the waxFraction
     */
    public double[] getWaxFraction() {
        return waxFraction;
    }

    /**
     * @return the gasViscosity
     */
    public double[] getGasDensity() {
        return gasDensity;
    }

    /**
     * @return the oilViscosity
     */
    public double[] getOilDensity() {
        return oilDensity;
    }

    /**
     * @return the aqueousViscosity
     */
    public double[] getAqueousDensity() {
        return aqueousDensity;
    }

}
