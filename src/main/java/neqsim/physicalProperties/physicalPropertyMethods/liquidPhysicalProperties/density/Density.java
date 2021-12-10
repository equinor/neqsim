/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Density
        extends neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.LiquidPhysicalPropertyMethod
        implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Density.class);

    /** Creates new Density */
    public Density() {
    }

    public Density(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        this.liquidPhase = liquidPhase;
    }

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
     * Returns the density of the phase. Unit: kg/m^3
     */
    @Override
	public double calcDensity() {

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
        return 1.0 / (liquidPhase.getPhase().getMolarVolume() - tempVar) * liquidPhase.getPhase().getMolarMass()
                * 1.0e5;
    }
}
