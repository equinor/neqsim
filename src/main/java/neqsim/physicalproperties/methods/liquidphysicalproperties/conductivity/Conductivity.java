/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Conductivity class for liquids.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Conductivity extends LiquidPhysicalPropertyMethod implements ConductivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Conductivity.class);

  double conductivity = 0;
  public double[] pureComponentConductivity;

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Conductivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
    pureComponentConductivity = new double[liquidPhase.getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public Conductivity clone() {
    Conductivity properties = null;

    try {
      properties = (Conductivity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    calcPureComponentConductivity();

    conductivity = 0.0;

    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        /*
         * tempVar2 = Math .pow(1.0 + Math .sqrt(pureComponentConductivity[i] /
         * pureComponentConductivity[j])
         * Math.pow(liquidPhase.getPhase().getComponent(j).getMolarMass() /
         * liquidPhase.getPhase().getComponent(i).getMolarMass(), 0.25), 2.0) / Math.pow(8.0 (1.0 +
         * liquidPhase.getPhase().getComponent(i).getMolarMass() /
         * liquidPhase.getPhase().getComponent(j).getMolarMass()), 0.5);
         */
      }
      double wigthFraci = liquidPhase.getPhase().getWtFrac(i);
      conductivity += wigthFraci * pureComponentConductivity[i]; // tempVar;
      // conductivity = conductivity +
      // liquidPhase.getPhase().getComponent(i).getx() *
      // pureComponentConductivity[i]; ///tempVar;
    }

    return conductivity;
  }

  /**
   * <p>
   * calcPureComponentConductivity.
   * </p>
   */
  public void calcPureComponentConductivity() {
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      // pure component conductivity
      pureComponentConductivity[i] =
          liquidPhase.getPhase().getComponent(i).getLiquidConductivityParameter(0)
              + liquidPhase.getPhase().getComponent(i).getLiquidConductivityParameter(1)
                  * liquidPhase.getPhase().getTemperature()
              + liquidPhase.getPhase().getComponent(i).getLiquidConductivityParameter(2)
                  * Math.pow(liquidPhase.getPhase().getTemperature(), 2.0);
      if (pureComponentConductivity[i] < 0) {
        pureComponentConductivity[i] = 1e-10;
      }
    }
  }
}
