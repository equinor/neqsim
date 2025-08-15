/*
 * Costald.java
 *
 * Created on 13. July 2022
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Costald Density Calculation class for liquids.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Costald extends LiquidPhysicalPropertyMethod implements DensityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Costald.class);

  /**
   * <p>
   * Constructor for Costald.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Costald(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Costald clone() {
    Costald properties = null;

    try {
      properties = (Costald) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /**
   * {@inheritDoc} Densities of compressed liquid mixtures. Unit: kg/m^3
   */
  @Override
  public double calcDensity() {
    // Densities of compressed liquid mixture. Method book "The properties of Gases and Liquids"
    // Bruce E. Poling 5.26
    // double tempVar = 0.0;
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

    double A; // PolynomialA
    double B; // PolynomialB

    // The constants of Aalto et al. (1996);
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
        ascFactMix += Math.sqrt(liquidPhase.getPhase().getComponent(i).getAcentricFactor())
            * (liquidPhase.getPhase().getComponent(i).getx());

        criticalVolumeMix1 += liquidPhase.getPhase().getComponent(i).getCriticalVolume()
            * liquidPhase.getPhase().getComponent(i).getx();
        criticalVolumeMix2 +=
            Math.pow(liquidPhase.getPhase().getComponent(i).getCriticalVolume(), 0.6666)
                * liquidPhase.getPhase().getComponent(i).getx();
        criticalVolumeMix3 +=
            Math.pow(liquidPhase.getPhase().getComponent(i).getCriticalVolume(), 0.33333)
                * liquidPhase.getPhase().getComponent(i).getx();
        criticalTemperature1 += Math
            .sqrt(liquidPhase.getPhase().getComponent(i).getTC()
                * liquidPhase.getPhase().getComponent(i).getCriticalVolume())
            * liquidPhase.getPhase().getComponent(i).getx();

        /*
         * tempVar += liquidPhase.getPhase().getComponent(i).getx()
         * (liquidPhase.getPhase().getComponent(i).getVolumeCorrection() +
         * liquidPhase.getPhase().getComponent(i).getVolumeCorrectionT()
         * (liquidPhase.getPhase().getTemperature() - 288.15));
         */
      }
    }
    ascFactMix = Math.pow(ascFactMix, 2);
    criticalVolumeMix = 0.25 * (criticalVolumeMix1 + 3 * criticalVolumeMix2 * criticalVolumeMix3);
    criticalTemperatureMix = Math.pow(criticalTemperature1, 2) / (criticalVolumeMix);
    pressureMixPseudocritical =
        (0.291 - 0.08 * ascFactMix) * 83.14 * criticalTemperatureMix / (criticalVolumeMix);
    pressurePseudoReduced0 = 6.13144
        - 6.30662 / (liquidPhase.getPhase().getTemperature() / criticalTemperatureMix)
        - 1.55663 * Math.log(liquidPhase.getPhase().getTemperature() / criticalTemperatureMix)
        + 0.17518 * Math.pow(liquidPhase.getPhase().getTemperature() / criticalTemperatureMix, 6);
    pressurePseudoReduced1 = 2.99938
        - 3.08508 / (liquidPhase.getPhase().getTemperature() / criticalTemperatureMix)
        + 1.26573 * Math.log(liquidPhase.getPhase().getTemperature() / criticalTemperatureMix)
        + 0.08560 * Math.pow(liquidPhase.getPhase().getTemperature() / criticalTemperatureMix, 6);
    pressurePseudoReducedVapour =
        Math.exp(pressurePseudoReduced0 + ascFactMix * pressurePseudoReduced1);
    A = a0 + a1 * (liquidPhase.getPhase().getTemperature() / criticalTemperatureMix)
        + a2 * Math.pow((liquidPhase.getPhase().getTemperature() / criticalTemperatureMix), 3)
        + a3 * Math.pow((liquidPhase.getPhase().getTemperature() / criticalTemperatureMix), 6)
        + a4 / (liquidPhase.getPhase().getTemperature() / criticalTemperatureMix);
    // System.out.println("density correction tempvar " + tempVar);
    B = b0 + b1 * ascFactMix;
    volumeLiquidMolarSaturated = criticalVolumeMix * Math.pow((0.29056 - 0.08775 * ascFactMix), Math
        .pow(1 - (liquidPhase.getPhase().getTemperature() / criticalTemperatureMix), 0.28571429));

    return 1 / (volumeLiquidMolarSaturated
        * (A + Math.pow(C,
            Math.pow((D - (liquidPhase.getPhase().getTemperature() / criticalTemperatureMix)), B))
            * ((liquidPhase.getPhase().getPressure() / pressureMixPseudocritical)
                - pressurePseudoReducedVapour))
        / (A + C * ((liquidPhase.getPhase().getPressure() / pressureMixPseudocritical)
            - pressurePseudoReducedVapour))
        * 1e-6 / liquidPhase.getPhase().getMolarMass());
  }
}
