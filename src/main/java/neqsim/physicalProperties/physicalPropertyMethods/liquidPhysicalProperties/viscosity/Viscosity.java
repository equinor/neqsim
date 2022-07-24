package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity;

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
    neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.LiquidPhysicalPropertyMethod
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
   * @param liquidPhase a
   *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *        object
   */
  public Viscosity(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
    super(liquidPhase);
    pureComponentViscosity = new double[liquidPhase.getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public Viscosity clone() {
    Viscosity properties = null;

    try {
      properties = (Viscosity) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    double tempVar = 0;
    double tempVar2 = 0;
    double viscosity = 0;
    this.calcPureComponentViscosity();

    // method og Grunberg and Nissan [87]
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      tempVar += liquidPhase.getPhase().getWtFrac(i) * Math.log(pureComponentViscosity[i]);
      // tempVar += liquidPhase.getPhase().getComponents()[i].getx() *
      // Math.log(pureComponentViscosity[i]);
    }
    tempVar2 = 0;
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      double wigthFraci = liquidPhase.getPhase().getWtFrac(i);
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        double wigthFracj = liquidPhase.getPhase().getWtFrac(j);
        if (i != j) {
          tempVar2 += wigthFraci * wigthFracj * liquidPhase.getMixingRule().getViscosityGij(i, j);
          // System.out.println("gij " + liquidPhase.getMixingRule().getViscosityGij(i,
          // j));
        }

        // if(i!=j) tempVar2 +=
        // liquidPhase.getPhase().getComponents()[i].getx()*liquidPhase.getPhase().getComponents()[j].getx()*liquidPhase.getMixingRule().getViscosityGij(i,j);
      }
    }
    viscosity = Math.exp(tempVar + tempVar2) / 1.0e3; // N-sek/m2
    return viscosity;
  }

  /**
   * <p>
   * calcPureComponentViscosity.
   * </p>
   */
  public void calcPureComponentViscosity() {
    pureComponentViscosity = new double[liquidPhase.getPhase().getNumberOfComponents()];
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      if (liquidPhase.getPhase().getTemperature() > liquidPhase.getPhase().getComponents()[i]
          .getTC()) {
        pureComponentViscosity[i] = 5.0e-1;
      } else if (liquidPhase.getPhase().getComponents()[i].getLiquidViscosityModel() == 1) {
        pureComponentViscosity[i] =
            liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(0)
                * Math.pow(liquidPhase.getPhase().getTemperature(),
                    liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(1));
      } else if (liquidPhase.getPhase().getComponents()[i].getLiquidViscosityModel() == 2) {
        pureComponentViscosity[i] =
            Math.exp(liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(0)
                + liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(1)
                    / liquidPhase.getPhase().getTemperature());
      } else if (liquidPhase.getPhase().getComponents()[i].getLiquidViscosityModel() == 3) {
        pureComponentViscosity[i] =
            Math.exp(liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(0)
                + liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(1)
                    / liquidPhase.getPhase().getTemperature()
                + liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(2)
                    * liquidPhase.getPhase().getTemperature()
                + liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(3)
                    * Math.pow(liquidPhase.getPhase().getTemperature(), 2));
      } else if (liquidPhase.getPhase().getComponents()[i].getLiquidViscosityModel() == 4) {
        pureComponentViscosity[i] = Math.pow(10, liquidPhase.getPhase().getComponents()[i]
            .getLiquidViscosityParameter(0)
            * (1.0 / liquidPhase.getPhase().getTemperature()
                - 1.0 / liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(1))); // liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(2)*liquidPhase.getPhase().getTemperature()+liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(3)/liquidPhase.getPhase().getTemperature()+liquidPhase.getPhase().getComponents()[i].getLiquidViscosityParameter(3)*Math.pow(liquidPhase.getPhase().getTemperature(),2));
      } else {
        // System.out.println("no pure component viscosity model defined for component "
        // + liquidPhase.getPhase().getComponents()[i].getComponentName());
        pureComponentViscosity[i] = 7.0e-1;
      }
      pureComponentViscosity[i] *= ((getViscosityPressureCorrection(i) + 1.0) / 2.0);
      // System.out.println("pure comp viscosity " + pureComponentViscosity[i] + "
      // pressure cor " + getViscosityPressureCorrection(i));
    }
  }

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
    double TR =
        liquidPhase.getPhase().getTemperature() / liquidPhase.getPhase().getComponent(i).getTC();
    if (TR > 1) {
      return 1.0;
    }
    double deltaPr = (liquidPhase.getPhase().getPressure() - 0.0)
        / liquidPhase.getPhase().getComponent(i).getPC();
    double A = 0.9991 - (4.674 * 1e-4 / (1.0523 * Math.pow(TR, -0.03877) - 1.0513));
    double D = (0.3257 / Math.pow((1.0039 - Math.pow(TR, 2.573)), 0.2906)) - 0.2086;
    double C = -0.07921 + 2.1616 * TR - 13.4040 * TR * TR + 44.1706 * Math.pow(TR, 3)
        - 84.8291 * Math.pow(TR, 4) + 96.1209 * Math.pow(TR, 5) - 59.8127 * Math.pow(TR, 6)
        + 15.6719 * Math.pow(TR, 7);
    return (1.0 + D * Math.pow(deltaPr / 2.118, A))
        / (1.0 + C * liquidPhase.getPhase().getComponent(i).getAcentricFactor() * deltaPr);
  }
}
