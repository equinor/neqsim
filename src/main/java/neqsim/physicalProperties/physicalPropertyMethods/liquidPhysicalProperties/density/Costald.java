/*
 * Costald.java
 *
 * Created on 13. July 2022
 */
package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Costald Density Calculation class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Costald extends
        neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.LiquidPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Density.class);

    /**
     * <p>
     * Constructor for Density.
     * </p>
     */
    public Costald() {}

    /**
     * <p>
     * Constructor for Costald.
     * </p>
     *
     * @param liquidPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Costald(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        this.liquidPhase = liquidPhase;
    }

    /** {@inheritDoc} */
    @Override
    public Costald clone() {
        Costald properties = null;

        try {
            properties = (Costald) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    /**
     * {@inheritDoc}
     * NB! Still not implemented
     * Returns the density of the phase using the Oostald method. Unit: kg/m^3
     */
    @Override
    public double calcDensity() {
      //This class need to be changed to the Costald method
        double tempVar = 0.0;
        if (liquidPhase.getPhase().useVolumeCorrection()) {
            for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
                tempVar += liquidPhase.getPhase().getComponents()[i].getx()
                        * (liquidPhase.getPhase().getComponents()[i].getVolumeCorrection()
                                + liquidPhase.getPhase().getComponents()[i].getVolumeCorrectionT()
                                        * (liquidPhase.getPhase().getTemperature() - 288.15));
            }
        }
        // System.out.println("density correction tempvar " + tempVar);
        return 1.0 / (liquidPhase.getPhase().getMolarVolume() - tempVar)
                * liquidPhase.getPhase().getMolarMass() * 1.0e5;
    }
}
