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
    implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DensityInterface {
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
   * {@inheritDoc} Densities of compressed liquid mixtures. Unit: kg/m^3
   */
  @Override
  public double calcDensity() {
    // Densitites of compressed liquid mixture. Method book "The properties of Gases and Liquids" Bruce E. Poling
    //5.26 
    double tempVar = 0.0;
    double ascFactMix = 0.0;
    double criticalVolumeMix1 = 0.0;
    double criticalVolumeMix2 = 0.0;
    double criticalVolumeMix3 = 0.0;
    double criticalVolumeMix = 0.0;
    double criticalTemperature1 = 0.0;
    double criticalTemperatureMix = 0.0;
    double pressureMixPseudocritical = 0.0;
    double pressurePseudoReduced0 = 0.0;
    double pressurePseudoReduced1 = 0.0;
    double pressurePseudoReducedVapour = 0.0;
    double volumeLiquidMolarSaturated = 0.0;

    double A; //PolynomialA
    double B; //PolynomialB

    //The constants of Aalto et al. (1996); 
    double a0 = -170.335;
    double a1 = -28.578;
    double a2 = 124.809;
    double a3 = -55.5393;
    double a4 = 130.01;
    double b0 = 0.164813;
    double b1 = -0.0914427;
    double C = Math.exp(1);
    double D = 1.00588;

    if (liquidPhase.getPhase().useVolumeCorrection()) {
      for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
        ascFactMix += Math.sqrt(liquidPhase.getPhase().getComponents()[i].getAcentricFactor())
        *(liquidPhase.getPhase().getComponents()[i].getx());

        criticalVolumeMix1 += liquidPhase.getPhase().getComponents()[i].getCriticalVolume()
        *liquidPhase.getPhase().getComponents()[i].getx();
        criticalVolumeMix2 += Math.pow(liquidPhase.getPhase().getComponents()[i].getCriticalVolume(),0.6666)
        *liquidPhase.getPhase().getComponents()[i].getx();
        criticalVolumeMix3 += Math.pow(liquidPhase.getPhase().getComponents()[i].getCriticalVolume(),0.33333)
        *liquidPhase.getPhase().getComponents()[i].getx();
        criticalTemperature1 += Math.sqrt(liquidPhase.getPhase().getComponents()[i].getTC()
        *liquidPhase.getPhase().getComponents()[i].getCriticalVolume())
        *liquidPhase.getPhase().getComponents()[i].getx();

        tempVar += liquidPhase.getPhase().getComponents()[i].getx()
            * (liquidPhase.getPhase().getComponents()[i].getVolumeCorrection()
                + liquidPhase.getPhase().getComponents()[i].getVolumeCorrectionT()
                    * (liquidPhase.getPhase().getTemperature() - 288.15));
      }
    }
    ascFactMix = Math.pow(ascFactMix,2);
    criticalVolumeMix = 0.25*(criticalVolumeMix1+3*criticalVolumeMix2*criticalVolumeMix3);
    criticalTemperatureMix = Math.pow(criticalTemperature1,2)/(criticalVolumeMix);
    pressureMixPseudocritical = (0.291 - 0.08*ascFactMix)*83.14*criticalTemperatureMix/(criticalVolumeMix);
    pressurePseudoReduced0 = 6.13144 - 6.30662/(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix)
    - 1.55663*Math.log(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix) 
    + 0.17518*Math.pow(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix,6);
    pressurePseudoReduced1 = 2.99938 - 3.08508/(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix)
    + 1.26573*Math.log(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix) 
    + 0.08560*Math.pow(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix,6);
    pressurePseudoReducedVapour = Math.exp(pressurePseudoReduced0 + ascFactMix*pressurePseudoReduced1);
    A = a0 + a1*(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix)  
    + a2*Math.pow((liquidPhase.getPhase().getTemperature()/criticalTemperatureMix),3) 
    + a3*Math.pow((liquidPhase.getPhase().getTemperature()/criticalTemperatureMix),6) 
    + a4/(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix);
    // System.out.println("density correction tempvar " + tempVar);
    B = b0+ b1*ascFactMix;
    volumeLiquidMolarSaturated = criticalVolumeMix*Math.pow((0.29056 - 0.08775*ascFactMix), 
    Math.pow(1 -(liquidPhase.getPhase().getTemperature()/criticalTemperatureMix),0.28571429));

    return 1/(volumeLiquidMolarSaturated*(A 
    + Math.pow(C,Math.pow((D - (liquidPhase.getPhase().getTemperature()/criticalTemperatureMix)),B))
    *((liquidPhase.getPhase().getPressure()/pressureMixPseudocritical) - pressurePseudoReducedVapour))
    /(A + C*((liquidPhase.getPhase().getPressure()/pressureMixPseudocritical) - pressurePseudoReducedVapour))*1e-6
    /liquidPhase.getPhase().getMolarMass());
  }
}
