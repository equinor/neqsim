/*
 * Test.java
 *
 * Created on 22. januar 2001, 22:59
 */
package neqsim.PVTsimulation.util.parameterfitting;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class CMEFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;

    double molarMass = 0.0;
    double saturationVolume = 0, saturationPressure = 0;
    double Zsaturation = 0;

    /**
     * Creates new Test
     */
    public CMEFunction() {
        params = new double[3];
    }

    public void calcSaturationConditions(SystemInterface system) {

        do {
            system.setPressure(system.getPressure() + 10.0);
            thermoOps.TPflash();
        } while (system.getNumberOfPhases() > 1);
        double minPres = system.getPressure() - 10.0;
        double maxPres = system.getPressure();
        do {
            system.setPressure((minPres + maxPres) / 2.0);
            thermoOps.TPflash();
            if (system.getNumberOfPhases() > 1) {
                minPres = system.getPressure();
            } else {
                maxPres = system.getPressure();
            }
        } while (Math.abs(maxPres - minPres) > 1e-5);
        /*
         * try { thermoOps.dewPointPressureFlash(); } catch (Exception e) {
         * e.printStackTrace(); }
         */
        saturationVolume = system.getVolume();
        saturationPressure = system.getPressure();
        Zsaturation = system.getZ();
    }

    @Override
	public double calcValue(double[] dependentValues) {
        int plusNumber = 0;
        molarMass = params[0];
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
                plusNumber = i;
            }
        }
        SystemInterface tempSystem = (SystemInterface) system.clone();
        tempSystem.resetCharacterisation();
        tempSystem.createDatabase(true);
        tempSystem.setMixingRule(system.getMixingRule());

        tempSystem.getPhase(0).getComponent(plusNumber).setMolarMass(molarMass);
        tempSystem.getPhase(1).getComponent(plusNumber).setMolarMass(molarMass);
        tempSystem.getCharacterization().characterisePlusFraction();
        tempSystem.createDatabase(true);
        tempSystem.setMixingRule(system.getMixingRule());
        tempSystem.init(0);
        tempSystem.init(1);
        thermoOps.setSystem(tempSystem);
        calcSaturationConditions(tempSystem);
        tempSystem.setTemperature(dependentValues[0]);
        tempSystem.setPressure(dependentValues[1]);
        // thermoOps.setSystem(tempSystem);
        thermoOps.TPflash();

        double totalVolume = tempSystem.getVolume();
        // system.display();
        return totalVolume / saturationVolume; // %wax
    }

    @Override
	public void setFittingParams(int i, double value) {
        params[i] = value;
        int plusNumber = 0;
        for (int ii = 0; ii < system.getPhase(0).getNumberOfComponents(); ii++) {
            if (system.getPhase(0).getComponent(ii).isIsPlusFraction()) {
                plusNumber = ii;
            }
        }
        system.getPhase(0).getComponent(plusNumber).setMolarMass(value);
        system.getPhase(1).getComponent(plusNumber).setMolarMass(value);
        // system.get
    }
}
