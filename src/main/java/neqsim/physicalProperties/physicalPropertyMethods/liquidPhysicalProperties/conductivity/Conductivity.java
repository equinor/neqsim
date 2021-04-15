/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.conductivity;

import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Conductivity
        extends neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.LiquidPhysicalPropertyMethod
        implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Conductivity.class);

    double conductivity = 0;
    public double[] pureComponentConductivity;

    /**
     * Creates new Conductivity
     */
    public Conductivity() {
    }

    public Conductivity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super(liquidPhase);
        pureComponentConductivity = new double[liquidPhase.getPhase().getNumberOfComponents()];
    }

    public Object clone() {
        Conductivity properties = null;

        try {
            properties = (Conductivity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    public double calcConductivity() {
        double tempVar = 0, tempVar2 = 0;

        calcPureComponentConductivity();

        conductivity = 0.0;

        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            tempVar = 0;
            for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
                tempVar2 = Math
                        .pow(1.0 + Math.sqrt(pureComponentConductivity[i] / pureComponentConductivity[j])
                                * Math.pow(liquidPhase.getPhase().getComponents()[j].getMolarMass()
                                        / liquidPhase.getPhase().getComponents()[i].getMolarMass(), 0.25),
                                2.0)
                        / Math.pow(8.0 * (1.0 + liquidPhase.getPhase().getComponents()[i].getMolarMass()
                                / liquidPhase.getPhase().getComponents()[j].getMolarMass()), 0.5);
                tempVar += liquidPhase.getPhase().getComponents()[j].getx() * tempVar2;
            }
            double wigthFraci = liquidPhase.getPhase().getWtFrac(i);
            conductivity += wigthFraci * pureComponentConductivity[i];/// tempVar;
            // conductivity = conductivity +
            // liquidPhase.getPhase().getComponents()[i].getx() *
            // pureComponentConductivity[i];///tempVar;
        }

        return conductivity;
    }

    public void calcPureComponentConductivity() {
        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            // pure component conductivity
            pureComponentConductivity[i] = liquidPhase.getPhase().getComponents()[i].getLiquidConductivityParameter(0)
                    + liquidPhase.getPhase().getComponents()[i].getLiquidConductivityParameter(1)
                            * liquidPhase.getPhase().getTemperature()
                    + liquidPhase.getPhase().getComponents()[i].getLiquidConductivityParameter(2)
                            * Math.pow(liquidPhase.getPhase().getTemperature(), 2.0);
            if (pureComponentConductivity[i] < 0) {
                pureComponentConductivity[i] = 1e-10;
            }
        }
    }
}
