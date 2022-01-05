/*
 * SurfaceTension.java
 *
 * Created on 13. august 2001, 13:14
 */
package neqsim.physicalProperties.interfaceProperties.surfaceTension;

import neqsim.physicalProperties.interfaceProperties.InterfaceProperties;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SurfaceTension class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SurfaceTension extends InterfaceProperties implements SurfaceTensionInterface {
    private static final long serialVersionUID = 1000;

    protected SystemInterface system;

    /**
     * <p>
     * Constructor for SurfaceTension.
     * </p>
     */
    public SurfaceTension() {}

    /**
     * <p>
     * Constructor for SurfaceTension.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SurfaceTension(SystemInterface system) {
        this.system = system;
    }

    /**
     * <p>
     * calcPureComponentSurfaceTension.
     * </p>
     *
     * @param componentNumber a int
     * @return a double
     */
    public double calcPureComponentSurfaceTension(int componentNumber) {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcSurfaceTension(int int1, int int2) {
        return 0.0;
    }

    /**
     * <p>
     * getComponentWithHighestBoilingpoint.
     * </p>
     *
     * @return a int
     */
    public int getComponentWithHighestBoilingpoint() {
        int compNumb = 0;
        double boilPoint = -273.15;
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getNormalBoilingPoint() > boilPoint) {
                compNumb = i;
                boilPoint = system.getPhase(0).getComponent(i).getNormalBoilingPoint();
            }
        }
        return compNumb;
    }
}
