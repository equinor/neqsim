/*
 * Density.java
 *
 * Created on 24. januar 2001, 19:49
 */

package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Density
        extends neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
        implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Density.class);

    /** Creates new Density */
    public Density() {
    }

    public Density(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        this.solidPhase = liquidPhase;
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
        if (solidPhase.getPhase().useVolumeCorrection()) {
            for (int i = 0; i < solidPhase.getPhase().getNumberOfComponents(); i++) {
                tempVar += solidPhase.getPhase().getComponents()[i].getx()
                        * (solidPhase.getPhase().getComponents()[i].getVolumeCorrection()
                                + solidPhase.getPhase().getComponents()[i].getVolumeCorrectionT()
                                        * (solidPhase.getPhase().getTemperature() - 288.15));
            }
        }
        // System.out.println("density correction tempvar " + tempVar);
        return 1.0 / (solidPhase.getPhase().getMolarVolume() - tempVar) * solidPhase.getPhase().getMolarMass() * 1e5;
    }
}
