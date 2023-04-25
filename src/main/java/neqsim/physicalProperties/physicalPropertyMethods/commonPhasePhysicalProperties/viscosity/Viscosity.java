package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 */
abstract class Viscosity extends
    neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.CommonPhysicalPropertyMethod
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
   * @param phase a
   *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *        object
   */
  public Viscosity(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
    super(phase);
    pureComponentViscosity = new double[phase.getPhase().getNumberOfComponents()];
  }

  /**
   * <p>
   * calcPureComponentViscosity.
   * </p>
   */
  public void calcPureComponentViscosity() {
    pureComponentViscosity = new double[phase.getPhase().getNumberOfComponents()];
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      if (phase.getPhase().getTemperature() > phase.getPhase().getComponents()[i].getTC()) {
        pureComponentViscosity[i] = 5.0e-1;
      } else if (phase.getPhase().getComponents()[i].getLiquidViscosityModel() == 1) {
        pureComponentViscosity[i] =
            phase.getPhase().getComponents()[i].getLiquidViscosityParameter(0)
                * Math.pow(phase.getPhase().getTemperature(),
                    phase.getPhase().getComponents()[i].getLiquidViscosityParameter(1));
      } else if (phase.getPhase().getComponents()[i].getLiquidViscosityModel() == 2) {
      } else if (phase.getPhase().getComponents()[i].getLiquidViscosityModel() == 3) {
        pureComponentViscosity[i] =
            Math.exp(phase.getPhase().getComponents()[i].getLiquidViscosityParameter(0)
                + phase.getPhase().getComponents()[i].getLiquidViscosityParameter(1)
                    / phase.getPhase().getTemperature()
                + phase.getPhase().getComponents()[i].getLiquidViscosityParameter(2)
                    * phase.getPhase().getTemperature()
                + phase.getPhase().getComponents()[i].getLiquidViscosityParameter(3)
                    * Math.pow(phase.getPhase().getTemperature(), 2));
      } else if (phase.getPhase().getComponents()[i].getLiquidViscosityModel() == 4) {
        pureComponentViscosity[i] = Math.pow(10,
            phase.getPhase().getComponents()[i].getLiquidViscosityParameter(0)
                * (1.0 / phase.getPhase().getTemperature()
                    - 1.0 / phase.getPhase().getComponents()[i].getLiquidViscosityParameter(1))); // phase.getPhase().getComponents()[i].getLiquidViscosityParameter(2)*phase.getPhase().getTemperature()+phase.getPhase().getComponents()[i].getLiquidViscosityParameter(3)/phase.getPhase().getTemperature()+phase.getPhase().getComponents()[i].getLiquidViscosityParameter(3)*Math.pow(phase.getPhase().getTemperature(),2));
      } else {
        // System.out.println("no pure component viscosity model defined for component "
        // + phase.getPhase().getComponents()[i].getComponentName());
        pureComponentViscosity[i] = 7.0e-1;
      }
      pureComponentViscosity[i] *= ((getViscosityPressureCorrection(i) + 1.0) / 2.0);
      // System.out.println("pure comp viscosity " + pureComponentViscosity[i] + "
      // pressure cor " + getViscosityPressureCorrection(i));
    }
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
    double TR = phase.getPhase().getTemperature() / phase.getPhase().getComponent(i).getTC();
    if (TR > 1) {
      return 1.0;
    }
    double deltaPr =
        (phase.getPhase().getPressure() - 0.0) / phase.getPhase().getComponent(i).getPC();
    double A = 0.9991 - (4.674 * 1e-4 / (1.0523 * Math.pow(TR, -0.03877) - 1.0513));
    double D = (0.3257 / Math.pow((1.0039 - Math.pow(TR, 2.573)), 0.2906)) - 0.2086;
    double C = -0.07921 + 2.1616 * TR - 13.4040 * TR * TR + 44.1706 * Math.pow(TR, 3)
        - 84.8291 * Math.pow(TR, 4) + 96.1209 * Math.pow(TR, 5) - 59.8127 * Math.pow(TR, 6)
        + 15.6719 * Math.pow(TR, 7);
    return (1.0 + D * Math.pow(deltaPr / 2.118, A))
        / (1.0 + C * phase.getPhase().getComponent(i).getAcentricFactor() * deltaPr);
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return pureComponentViscosity[i];
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
}
