/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.PVTsimulation.simulation;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author ESOL
 */
public class SwellingTest extends BasePVTsimulation {

    private static final long serialVersionUID = 1000;

    double[] gasInjected = null;
    private double[] pressures = null;
    private double[] relativeOilVolume = null;
    SystemInterface injectionGas;

    public SwellingTest(SystemInterface tempSystem) {
        super(tempSystem);
    }

    public void setInjectionGas(SystemInterface injectionGas) {
        this.injectionGas = injectionGas;
    }

    public void setCummulativeMolePercentGasInjected(double[] gasInjected) {
        this.gasInjected = gasInjected;
        setPressures(new double[gasInjected.length]);
        setRelativeOilVolume(new double[gasInjected.length]);
    }

    public void runCalc() {
        double oldInjected = 0.0;
        thermoOps.TPflash();
        double orginalOilVolume = getThermoSystem().getVolume();
        double oilMoles = getThermoSystem().getTotalNumberOfMoles();

        for (int i = 0; i < getPressures().length; i++) {
            if (gasInjected[i] > 1e-10) {
                injectionGas.setTotalFlowRate(oilMoles * (gasInjected[i] - oldInjected), "mol/sec");
                injectionGas.init(0);
                injectionGas.init(1);
                oldInjected = gasInjected[i];

                getThermoSystem().init(0);
                getThermoSystem().addFluid(injectionGas);
            }
            getThermoSystem().init(0);
            if (i == 0) {
                getThermoSystem().setPressure(10.0);
            }
            getThermoSystem().setTemperature(temperature);
            try {
                thermoOps.bubblePointPressureFlash(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            pressures[i] =  getThermoSystem().getPressure();
            getRelativeOilVolume()[i] = getThermoSystem().getVolume()/orginalOilVolume;
        }
        
           for (int i = 0; i < getPressures().length; i++) {
               System.out.println("pressure " + getPressures()[i] + " relativeOil volume " + getRelativeOilVolume()[i]);
           }

    }

    public static void main(String[] args) {

        SystemInterface oilSystem = new SystemSrkEos(298.0, 50);
        oilSystem.addComponent("propane", 0.01);
        oilSystem.addTBPfraction("C10", 100.0, 145.0/1000.0, 0.82);
         oilSystem.addTBPfraction("C12", 120.0, 175.0/1000.0, 0.85);
        oilSystem.createDatabase(true);
        oilSystem.setMixingRule(2);

        SystemInterface gasSystem = new SystemSrkEos(298.0, 50);
        gasSystem.addComponent("methane", 110.0);
        gasSystem.addComponent("ethane", 10.0);
        gasSystem.createDatabase(true);
        gasSystem.setMixingRule(2);

        SwellingTest test = new SwellingTest(oilSystem);
        test.setInjectionGas(gasSystem);
        test.setTemperature(298.15);
        test.setCummulativeMolePercentGasInjected(new double[]{0.0, 0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.4, 0.5,1.0,1.3,2.0});
        test.runCalc();
        
        test.getThermoSystem().display();
    }

    /**
     * @return the relativeOilVolume
     */
    public double[] getRelativeOilVolume() {
        return relativeOilVolume;
    }

    /**
     * @param relativeOilVolume the relativeOilVolume to set
     */
    public void setRelativeOilVolume(double[] relativeOilVolume) {
        this.relativeOilVolume = relativeOilVolume;
    }

    /**
     * @return the pressures
     */
    @Override
    public double[] getPressures() {
        return pressures;
    }

    /**
     * @param pressures the pressures to set
     */
    @Override
    public void setPressures(double[] pressures) {
        this.pressures = pressures;
    }
}
