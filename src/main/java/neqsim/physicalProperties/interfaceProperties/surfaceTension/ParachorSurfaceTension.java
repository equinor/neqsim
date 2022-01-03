/*
 * GasLiquidSurfaceTension.java
 *
 * Created on 13. august 2001, 13:14
 */
package neqsim.physicalProperties.interfaceProperties.surfaceTension;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>ParachorSurfaceTension class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ParachorSurfaceTension extends SurfaceTension {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new GasLiquidSurfaceTension
     */
    public ParachorSurfaceTension() {
    }

    /**
     * <p>Constructor for ParachorSurfaceTension.</p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public ParachorSurfaceTension(SystemInterface system) {
        super(system);
    }

	/**
	 * {@inheritDoc}
	 *
	 * Calculates the pure component surfacetension using the Macleod/Sugden method
	 */
    @Override
	public double calcPureComponentSurfaceTension(int componentNumber) {
        return 1.0e-3 * Math.pow(system.getPhases()[0].getComponents()[componentNumber].getParachorParameter() * 1.0e-6
                * (system.getPhases()[1].getPhysicalProperties().getDensity() / system.getPhases()[1].getMolarMass()
                        * system.getPhases()[1].getComponents()[componentNumber].getx()
                        - system.getPhases()[0].getPhysicalProperties().getDensity()
                                / system.getPhases()[0].getMolarMass()
                                * system.getPhases()[0].getComponents()[componentNumber].getx()),
                4.0);
    }

	/**
	 * {@inheritDoc}
	 *
	 * Calculates the surfacetension using the Macleod/Sugden method for mixtures
	 * Units: N/m
	 */
    @Override
	public double calcSurfaceTension(int interface1, int interface2) {
        double temp = 0;
        if (system.getNumberOfPhases() < 2) {
            return 0.0;
        }
        // if(interface1>=2 || interface2>=2) return 0.0;
        // return 0.0;
        try {
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                // System.out.println("density1 parachor " +
                // system.getPhase(interface1).getPhysicalProperties().getDensity());
                // System.out.println("density2 parachor " +
                // system.getPhase(interface2).getPhysicalProperties().getDensity());
                temp += system.getPhase(interface1).getComponent(i).getParachorParameter() * 1.0e-6
                        * (system.getPhase(interface2).getPhysicalProperties().getDensity()
                                / system.getPhase(interface2).getMolarMass()
                                * system.getPhase(interface2).getComponent(i).getx()
                                - system.getPhase(interface1).getPhysicalProperties().getDensity()
                                        / system.getPhase(interface1).getMolarMass()
                                        * system.getPhase(interface1).getComponent(i).getx());
            }
        } catch (Exception e) {
            // e.printStackTrace();
            temp = 0.0;
        }
        return Math.pow(temp, 4.0) / 1000.0;
    }
}
