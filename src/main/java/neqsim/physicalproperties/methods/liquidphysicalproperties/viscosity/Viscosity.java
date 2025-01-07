package neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Viscosity class.
 * </p>
 *
 * @author Even Solbraa
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class Viscosity extends LiquidPhysicalPropertyMethod implements ViscosityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Viscosity.class);

  public double[] pureComponentViscosity;

  /**
   * <p>
   * Constructor for Viscosity.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Viscosity(PhysicalProperties liquidPhase) {
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
    this.calcPureComponentViscosity();

    // method og Grunberg and Nissan [87]
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      tempVar += liquidPhase.getPhase().getWtFrac(i) * Math.log(pureComponentViscosity[i]);
      // tempVar += liquidPhase.getPhase().getComponent(i).getx() *
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
        // liquidPhase.getPhase().getComponent(i).getx()*liquidPhase.getPhase().getComponent(j).getx()*liquidPhase.getMixingRule().getViscosityGij(i,j);
      }
    }
    double viscosity = Math.exp(tempVar + tempVar2) / 1.0e3; // N-sek/m2
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
      if (liquidPhase.getPhase().getTemperature() > liquidPhase.getPhase().getComponent(i)
          .getTC()) {
        pureComponentViscosity[i] = 5.0e-1;
      } else if (liquidPhase.getPhase().getComponent(i).getLiquidViscosityModel() == 1) {
        pureComponentViscosity[i] =
            liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(0)
                * Math.pow(liquidPhase.getPhase().getTemperature(),
                    liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(1));
      } else if (liquidPhase.getPhase().getComponent(i).getLiquidViscosityModel() == 2) {
        pureComponentViscosity[i] =
            Math.exp(liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(0)
                + liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(1)
                    / liquidPhase.getPhase().getTemperature());
      } else if (liquidPhase.getPhase().getComponent(i).getLiquidViscosityModel() == 3) {
        pureComponentViscosity[i] =
            Math.exp(liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(0)
                + liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(1)
                    / liquidPhase.getPhase().getTemperature()
                + liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(2)
                    * liquidPhase.getPhase().getTemperature()
                + liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(3)
                    * Math.pow(liquidPhase.getPhase().getTemperature(), 2));
      } else if (liquidPhase.getPhase().getComponent(i).getLiquidViscosityModel() == 4) {
        pureComponentViscosity[i] = Math.pow(10,
            liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(0)
                * (1.0 / liquidPhase.getPhase().getTemperature()
                    - 1.0 / liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(1))); // liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(2)*liquidPhase.getPhase().getTemperature()+liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(3)/liquidPhase.getPhase().getTemperature()+liquidPhase.getPhase().getComponent(i).getLiquidViscosityParameter(3)*Math.pow(liquidPhase.getPhase().getTemperature(),2));
      } else {
        // System.out.println("no pure component viscosity model defined for component "
        // + liquidPhase.getPhase().getComponent(i).getComponentName());
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
