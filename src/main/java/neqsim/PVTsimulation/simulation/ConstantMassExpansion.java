/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.PVTsimulation.simulation;

import neqsim.PVTsimulation.util.parameterfitting.CMEFunction;
import java.util.ArrayList;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author esol
 */
public class ConstantMassExpansion extends BasePVTsimulation {

    private static final long serialVersionUID = 1000;

    double[] relativeVolume = null;
    double[] totalVolume = null;
    private double[] liquidRelativeVolume = null;
    private double[] Zgas = null, Yfactor = null, isoThermalCompressibility = null;
    boolean saturationConditionFound = false;
    private double saturationIsoThermalCompressibility = 0.0;
    double[] temperatures = null;

    public ConstantMassExpansion(SystemInterface tempSystem) {
        super(tempSystem);
    }

    public void calcSaturationConditions() {

        do {
            getThermoSystem().setPressure(getThermoSystem().getPressure() + 10.0);
            thermoOps.TPflash();
        } while (getThermoSystem().getNumberOfPhases() > 1);
        double minPres = getThermoSystem().getPressure() - 10.0;
        double maxPres = getThermoSystem().getPressure();
        do {
            getThermoSystem().setPressure((minPres + maxPres) / 2.0);
            thermoOps.TPflash();
            if (getThermoSystem().getNumberOfPhases() > 1) {
                minPres = getThermoSystem().getPressure();
            } else {
                maxPres = getThermoSystem().getPressure();
            }
        } while (Math.abs(maxPres - minPres) > 1e-5);
        /* try {
         thermoOps.dewPointPressureFlash();
         } catch (Exception e) {
         e.printStackTrace();
         }*/
        saturationVolume = getThermoSystem().getVolume();
        saturationPressure = getThermoSystem().getPressure();
        Zsaturation = getThermoSystem().getZ();
        saturationConditionFound = true;
    }

    public double getSaturationPressure() {
        return saturationPressure;
    }

    public void runCalc() {
        saturationConditionFound = false;
        relativeVolume = new double[pressures.length];
        totalVolume = new double[pressures.length];
        liquidRelativeVolume = new double[pressures.length];
        Zgas = new double[pressures.length];
        Yfactor = new double[pressures.length];
        isoThermalCompressibility = new double[pressures.length];
        getThermoSystem().setTemperature(temperature);
        if (!saturationConditionFound) {
            calcSaturationConditions();
            try {
                thermoOps.TPflash();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            //       getThermoSystem().setPressure(400);
            //       thermoOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        saturationVolume = getThermoSystem().getVolume();
        saturationPressure = getThermoSystem().getPressure();
        Zsaturation = getThermoSystem().getZ();
        saturationIsoThermalCompressibility = -1.0 / getThermoSystem().getPhase(0).getVolume() / getThermoSystem().getPhase(0).getdPdVTn();

        for (int i = 0; i < pressures.length; i++) {
            //   getThermoSystem().init(0);
            getThermoSystem().setPressure(pressures[i]);
            try {
                thermoOps.TPflash();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //getThermoSystem().display();
            totalVolume[i] = getThermoSystem().getVolume();
            relativeVolume[i] = totalVolume[i] / saturationVolume;
            if (getThermoSystem().getNumberOfPhases() == 1) {
                Zgas[i] = getThermoSystem().getPhase(0).getZ();
                isoThermalCompressibility[i] = -1.0 / getThermoSystem().getPhase(0).getVolume() / getThermoSystem().getPhase(0).getdPdVTn();
            }
            if (getThermoSystem().getNumberOfPhases() > 1) {
                liquidRelativeVolume[i] = getThermoSystem().getPhase("oil").getVolume() / saturationVolume * 100;
                Yfactor[i] = ((saturationPressure - pressures[i]) / pressures[i]) / ((totalVolume[i] - saturationVolume) / saturationVolume);
            }
            System.out.println("pressure " + getThermoSystem().getPressure() + " relative volume " + relativeVolume[i] + " liquid rel vol " + liquidRelativeVolume[i] + " Zgas " + Zgas[i] + " Yfactor " + getYfactor()[i] + " isoCompfactor " + getIsoThermalCompressibility()[i]);
        }
        System.out.println("test finished");
    }

    public void runTuning() {
        ArrayList sampleList = new ArrayList();

        try {
            System.out.println("adding....");

            for (int i = 0; i < experimentalData[0].length; i++) {
                CMEFunction function = new CMEFunction();
                double[] guess = new double[]{getThermoSystem().getCharacterization().getPlusFractionModel().getMPlus() / 1000.0};
                function.setInitialGuess(guess);

                SystemInterface tempSystem = getThermoSystem();//(SystemInterface) getThermoSystem().clone();

                tempSystem.setTemperature(temperature);
                tempSystem.setPressure(pressures[i]);
                //thermoOps.TPflash();
                //tempSystem.display();
                double sample1[] = {temperature, pressures[i]};
                double relativeVolume = experimentalData[0][i];
                double standardDeviation1[] = {1.5};
                SampleValue sample = new SampleValue(relativeVolume, relativeVolume / 50.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(tempSystem);
                sampleList.add(sample);

            }
        } catch (Exception e) {
            System.out.println("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);

        optimizer = new LevenbergMarquardt();
        optimizer.setMaxNumberOfIterations(5);

        optimizer.setSampleSet(sampleSet);
        optimizer.solve();
        runCalc();
        //optim.displayCurveFit();
    }

    public static void main(String[] args) {

        SystemInterface tempSystem = new SystemSrkEos(313.0, 10.0);
        tempSystem.addComponent("nitrogen", 0.34);
        tempSystem.addComponent("CO2", 3.59);
        tempSystem.addComponent("methane", 67.42);
        tempSystem.addComponent("ethane", 9.02);
        tempSystem.addComponent("propane", 4.31);
        tempSystem.addComponent("i-butane", 0.93);
        tempSystem.addComponent("n-butane", 1.71);
        tempSystem.addComponent("i-pentane", 0.74);
        tempSystem.addComponent("n-pentane", 0.85);
        tempSystem.addComponent("n-hexane", 1.38);
        tempSystem.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912);
        tempSystem.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255);
        tempSystem.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454);
        tempSystem.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864);
        tempSystem.addPlusFraction("C11", 4.58, 256.2 / 1000.0, 0.8398);
        // tempSystem.getCharacterization().characterisePlusFraction();
        tempSystem.createDatabase(true);
        tempSystem.setMixingRule(2);
        tempSystem.init(0);
        tempSystem.init(1);
        /*
         tempSystem.addComponent("nitrogen", 0.6);
         tempSystem.addComponent("CO2", 3.34);
         tempSystem.addComponent("methane", 74.16);
         tempSystem.addComponent("ethane", 7.9);
         tempSystem.addComponent("propane", 4.15);
         tempSystem.addComponent("i-butane", 0.71);
         tempSystem.addComponent("n-butane", 0.71);
         tempSystem.addComponent("i-pentane", 0.66);
         tempSystem.addComponent("n-pentane", 0.66);
         tempSystem.addComponent("n-hexane", 0.81);
         //   tempSystem.addTBPfraction("C7", 1.2, 91.0 / 1000.0, 0.746);
         //  tempSystem.addTBPfraction("C8", 1.15, 104.0 / 1000.0, 0.770);
         //     tempSystem.addTBPfraction("C9", 5.15, 125.0 / 1000.0, 0.8);
         */

        ConstantMassExpansion CMEsim = new ConstantMassExpansion(tempSystem);
        //    CMEsim.runCalc();
        //double a = CMEsim.getSaturationPressure();

        CMEsim.setTemperaturesAndPressures(new double[]{313, 313, 313, 313}, new double[]{400, 300.0, 200.0, 100.0});
        double[][] expData = {{0.95, 0.99, 1.12, 1.9}};
        CMEsim.setExperimentalData(expData);
        //CMEsim.runTuning();
        CMEsim.runCalc();
    }

    /**
     * @return the relativeVolume
     */
    public double[] getRelativeVolume() {
        return relativeVolume;
    }

    /**
     * @return the liquidRelativeVolume
     */
    public double[] getLiquidRelativeVolume() {
        return liquidRelativeVolume;
    }

    /**
     * @return the Zgas
     */
    public double[] getZgas() {
        return Zgas;
    }

    /**
     * @return the Yfactor
     */
    public double[] getYfactor() {
        return Yfactor;
    }

    /**
     * @return the isoThermalCompressibility
     */
    public double[] getIsoThermalCompressibility() {
        return isoThermalCompressibility;
    }

    /**
     * @return the saturationIsoThermalCompressibility
     */
    public double getSaturationIsoThermalCompressibility() {
        return saturationIsoThermalCompressibility;
    }

    public void setTemperaturesAndPressures(double[] temperature, double[] pressure) {

        this.pressures = pressure;
        this.temperatures = temperature;
        experimentalData = new double[temperature.length][1];

    }

}
