package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.CommonPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Abstract class for Viscosity property.
 *
 * @author Even Solbraa
 */
public abstract class Viscosity extends CommonPhysicalPropertyMethod implements ViscosityInterface {
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
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Viscosity(PhysicalProperties phase) {
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
      if (phase.getPhase().getTemperature() > phase.getPhase().getComponent(i).getTC()) {
        pureComponentViscosity[i] = 5.0e-1;
      } else if (phase.getPhase().getComponent(i).getLiquidViscosityModel() == 1) {
        pureComponentViscosity[i] = phase.getPhase().getComponent(i).getLiquidViscosityParameter(0)
            * Math.pow(phase.getPhase().getTemperature(),
                phase.getPhase().getComponent(i).getLiquidViscosityParameter(1));
      } else if (phase.getPhase().getComponent(i).getLiquidViscosityModel() == 2) {
      } else if (phase.getPhase().getComponent(i).getLiquidViscosityModel() == 3) {
        pureComponentViscosity[i] =
            Math.exp(phase.getPhase().getComponent(i).getLiquidViscosityParameter(0)
                + phase.getPhase().getComponent(i).getLiquidViscosityParameter(1)
                    / phase.getPhase().getTemperature()
                + phase.getPhase().getComponent(i).getLiquidViscosityParameter(2)
                    * phase.getPhase().getTemperature()
                + phase.getPhase().getComponent(i).getLiquidViscosityParameter(3)
                    * Math.pow(phase.getPhase().getTemperature(), 2));
      } else if (phase.getPhase().getComponent(i).getLiquidViscosityModel() == 4) {
        pureComponentViscosity[i] = Math.pow(10,
            phase.getPhase().getComponent(i).getLiquidViscosityParameter(0)
                * (1.0 / phase.getPhase().getTemperature()
                    - 1.0 / phase.getPhase().getComponent(i).getLiquidViscosityParameter(1)));
        // phase.getPhase().getComponent(i).getLiquidViscosityParameter(2) *
        // phase.getPhase().getTemperature()+phase.getPhase().getComponent(i).getLiquidViscosityParameter(3)
        // /
        // phase.getPhase().getTemperature()+phase.getPhase().getComponent(i).getLiquidViscosityParameter(3)
        // * Math.pow(phase.getPhase().getTemperature(),2));
      } else {
        // System.out.println("no pure component viscosity model defined for component "
        // + phase.getPhase().getComponent(i).getComponentName());
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
