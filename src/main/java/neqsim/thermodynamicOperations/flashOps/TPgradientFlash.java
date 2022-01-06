/*
 * TPgradientFlash.java
 *
 * Created on 8. mars 2001, 10:56
 */
package neqsim.thermodynamicOperations.flashOps;

import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TPgradientFlash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class TPgradientFlash extends Flash {
    private static final long serialVersionUID = 1000;

    SystemInterface localSystem = null, tempSystem = null;
    double temperature = 0.0, height = 0.0, deltaHeight = 0.0;
    double deltaT = 0.0;
    Matrix Jac;
    Matrix fvec;
    Matrix u;
    Matrix dx;
    Matrix uold;

    /**
     * <p>
     * Constructor for TPgradientFlash.
     * </p>
     */
    public TPgradientFlash() {}

    /**
     * <p>
     * Constructor for TPgradientFlash.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param height a double
     * @param temperature a double
     */
    public TPgradientFlash(SystemInterface system, double height, double temperature) {
        this.system = system;
        this.temperature = temperature;
        this.height = height;
        Jac = new Matrix(system.getPhase(0).getNumberOfComponents(),
                system.getPhase(0).getNumberOfComponents());
        fvec = new Matrix(system.getPhase(0).getNumberOfComponents(), 1);
    }

    /**
     * <p>
     * Setter for the field <code>fvec</code>.
     * </p>
     */
    public void setfvec() {
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            fvec.set(i, 0, Math
                    .log(localSystem.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                            * localSystem.getPhases()[0].getComponents()[i].getx()
                            * localSystem.getPressure())
                    - Math.log(tempSystem.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                            * tempSystem.getPhases()[0].getComponents()[i].getx()
                            * tempSystem.getPressure())
                    - tempSystem.getPhases()[0].getComponents()[i].getMolarMass()
                            * neqsim.thermo.ThermodynamicConstantsInterface.gravity * deltaHeight
                            / neqsim.thermo.ThermodynamicConstantsInterface.R
                            / tempSystem.getPhase(0).getTemperature()
                    + tempSystem.getPhases()[0].getComponents()[i].getMolarMass()
                            * (tempSystem.getPhases()[0].getEnthalpy()
                                    / tempSystem.getPhases()[0].getNumberOfMolesInPhase()
                                    / tempSystem.getPhase(0).getMolarMass()
                                    - tempSystem.getPhases()[0].getComponents()[i]
                                            .getEnthalpy(tempSystem.getPhase(0).getTemperature())
                                            / tempSystem.getPhases()[0].getComponent(i)
                                                    .getNumberOfMolesInPhase()
                                            / tempSystem.getPhase(0).getComponent(i).getMolarMass())
                            * deltaT / tempSystem.getPhase(0).getTemperature()
                            / neqsim.thermo.ThermodynamicConstantsInterface.R
                            / tempSystem.getPhase(0).getTemperature());
        }
    }

    /**
     * <p>
     * setJac.
     * </p>
     */
    public void setJac() {
        Jac.timesEquals(0.0);
        double dij = 0.0;

        double tempJ = 0.0;

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
                dij = i == j ? 1.0 : 0.0;// Kroneckers delta
                tempJ = 1.0
                        / (localSystem.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                                * localSystem.getPhases()[0].getComponents()[i].getx()
                                * localSystem.getPressure())
                        * (localSystem.getPhases()[0].getComponents()[i].getFugasityCoeffisient()
                                * dij * localSystem.getPressure()
                                + localSystem.getPhases()[0].getComponents()[i].getdfugdx(j)
                                        * localSystem.getPhases()[0].getComponents()[i].getx()
                                        * localSystem.getPressure());
                Jac.set(i, j, tempJ);
            }
        }
    }

    /**
     * <p>
     * setNewX.
     * </p>
     */
    public void setNewX() {
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            localSystem.getPhase(0).getComponent(i)
                    .setx(localSystem.getPhase(0).getComponent(i).getx() - 0.5 * dx.get(i, 0));
        }
        localSystem.getPhase(0).normalize();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        tempSystem = system.clone();
        tempSystem.init(0);
        tempSystem.init(3);

        localSystem = system.clone();
        // localSystem.setPressure(height*9.81*height);

        deltaT = (temperature - system.getTemperature()) / 20.0;
        deltaHeight = height / 20.0;

        for (int i = 0; i < 20; i++) {
            localSystem.setTemperature(localSystem.getTemperature() + deltaT);
            for (int o = 0; o < 15; o++) {
                localSystem.init(3);
                setfvec();
                setJac();
                dx = Jac.solve(fvec);
                dx.print(10, 10);
                setNewX();
            }
            // localSystem.display();

            tempSystem = localSystem.clone();
            tempSystem.init(3);
        }
    }

    /** {@inheritDoc} */
    @Override
    public SystemInterface getThermoSystem() {
        return localSystem;
    }

    /** {@inheritDoc} */
    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
