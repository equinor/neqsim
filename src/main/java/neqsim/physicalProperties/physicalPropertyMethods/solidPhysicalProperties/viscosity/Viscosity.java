/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.viscosity;

import org.apache.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class Viscosity extends neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(Viscosity.class);

    public double[] pureComponentViscosity;

    /**
     * Creates new Viscosity class
     */
    public Viscosity() {
    }

    public Viscosity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
    }

    public Object clone() {
        Viscosity properties = null;

        try {
            properties = (Viscosity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    public double calcViscosity() {

        double viscosity = 0.0;
        return viscosity;
    }

    public void calcPureComponentViscosity() {

    }

    public double getPureComponentViscosity(int i) {
        return pureComponentViscosity[i];
    }

    public double getViscosityPressureCorrection(int i) {
        return 0.0;
    }
}
