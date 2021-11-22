/*
 * GasLiquidSurfaceTension.java
 *
 * Created on 13. august 2001, 13:14
 */
package neqsim.physicalProperties.interfaceProperties.surfaceTension;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public class FirozabadiRamleyInterfaceTension extends SurfaceTension {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new GasLiquidSurfaceTension
     */
    public FirozabadiRamleyInterfaceTension() {}

    public FirozabadiRamleyInterfaceTension(SystemInterface system) {
        super(system);
    }

    /**
     * Calculates the pure component surfacetension using the Macleod/Sugden method
     */
    @Override
    public double calcPureComponentSurfaceTension(int componentNumber) {
        return 1.0e-3
                * Math.pow(
                        system.getPhases()[0].getComponents()[componentNumber]
                                .getParachorParameter()
                                * 1.0e-6
                                * (system.getPhases()[1].getPhysicalProperties().getDensity()
                                        / system.getPhases()[1].getMolarMass()
                                        * system.getPhases()[1].getComponents()[componentNumber]
                                                .getx()
                                        - system.getPhases()[0].getPhysicalProperties().getDensity()
                                                / system.getPhases()[0].getMolarMass()
                                                * system.getPhases()[0]
                                                        .getComponents()[componentNumber].getx()),
                        4.0);
    }

    /**
     * Calculates the surfacetension using the Firozabadi Ramley (1988) method for mixtures Units:
     * N/m
     */
    @Override
    public double calcSurfaceTension(int interface1, int interface2) {
        double temp = 0;
        if (system.getNumberOfPhases() < 2) {
            return 0.0;
        }

        double deltaDens = Math.abs(system.getPhase(interface2).getPhysicalProperties().getDensity()
                - system.getPhase(interface1).getPhysicalProperties().getDensity());
        double Tr = system.getPhase(interface1).getTemperature()
                / system.getPhase(interface1).getPseudoCriticalTemperature();
        // System.out.println("deltaDens " + deltaDens + " Tr " + Tr + " phasetyaae " +
        // system.getPhase(interface1).getPhaseTypeName());
        double a1 = 0.0, b1 = 0.0;
        if (deltaDens / 1000.0 < 0.2) {
            a1 = 2.2062;
            b1 = -0.94716;
        } else if (deltaDens / 1000.0 < 0.5) {
            a1 = 2.915;
            b1 = -0.76852;
        } else {
            a1 = 3.3858;
            b1 = -0.62590;
        }

        double temp1 = a1 * Math.pow(deltaDens / 1000.0, b1 + 1.0) / Math.pow(Tr, 0.3125);
        return Math.pow(temp1, 4.0) / 1000.0;
    }
}
