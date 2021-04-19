/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.viscosity;

import static java.lang.Double.NaN;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class Viscosity
        extends neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
        implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Viscosity.class);

    public double[] pureComponentViscosity;

    /**
     * Creates new Viscosity class
     */
    public Viscosity() {
    }

    public Viscosity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
    }

    @Override
	public Object clone() {
        Viscosity properties = null;

        try {
            properties = (Viscosity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    @Override
	public double calcViscosity() {

        double viscosity = NaN;
        return viscosity;
    }

    public void calcPureComponentViscosity() {

    }

    @Override
	public double getPureComponentViscosity(int i) {
        return pureComponentViscosity[i];
    }

    public double getViscosityPressureCorrection(int i) {
        return 0.0;
    }
}
