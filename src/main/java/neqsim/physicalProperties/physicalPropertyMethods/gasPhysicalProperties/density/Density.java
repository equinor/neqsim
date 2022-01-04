/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod;

/**
 * <p>
 * Density class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Density extends GasPhysicalPropertyMethod implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Density.class);

    /**
     * <p>Constructor for Density.</p>
     */
    public Density() {}

    /**
     * <p>
     * Constructor for Density.
     * </p>
     *
     * @param gasPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Density(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        this.gasPhase = gasPhase;
    }

    /** {@inheritDoc} */
    @Override
    public Density clone() {
        Density properties = null;

        try {
            properties = (Density) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the density of the phase. Unit: kg/m^3
     */
    @Override
    public double calcDensity() {

        double tempVar = 0;
        if (gasPhase.getPhase().useVolumeCorrection()) {
            for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
                tempVar += gasPhase.getPhase().getComponents()[i].getx()
                        * gasPhase.getPhase().getComponents()[i].getVolumeCorrection();
            }
        }
        return 1.0 / (gasPhase.getPhase().getMolarVolume() - tempVar)
                * gasPhase.getPhase().getMolarMass() * 1e5;
    }
}
