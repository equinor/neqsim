/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.viscosity;

import static java.lang.Double.NaN;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Viscosity class.
 * </p>
 *
 * @author Even Solbraa
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class Viscosity extends
        neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Viscosity.class);

    public double[] pureComponentViscosity;

    /**
     * <p>
     * Constructor for Viscosity.
     * </p>
     */
    public Viscosity() {}

    /**
     * <p>
     * Constructor for Viscosity.
     * </p>
     *
     * @param solidPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Viscosity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
    }

    /** {@inheritDoc} */
    @Override
    public Viscosity clone() {
        Viscosity properties = null;

        try {
            properties = (Viscosity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    /** {@inheritDoc} */
    @Override
    public double calcViscosity() {
        double viscosity = NaN;
        return viscosity;
    }

    /**
     * <p>
     * calcPureComponentViscosity.
     * </p>
     */
    public void calcPureComponentViscosity() {}

    /** {@inheritDoc} */
    @Override
    public double getPureComponentViscosity(int i) {
        return pureComponentViscosity[i];
    }

    /**
     * <p>
     * getViscosityPressureCorrection.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getViscosityPressureCorrection(int i) {
        return 0.0;
    }
}
