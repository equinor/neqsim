package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * LBCViscosityMethod class.
 * </p>
 *
 * @author esol
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class LBCViscosityMethod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LBCViscosityMethod.class);

  double[] a = {0.10230, 0.023364, 0.058533, -0.040758, 0.0093324};

  /**
   * <p>
   * Constructor for LBCViscosityMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public LBCViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    double lowPresVisc = 0.0;
    double temp = 0.0;
    double temp2 = 0.0;
    double temp3 = 0.0;
    double temp4 = 0.0;
    double critDens = 0.0;
    double par1 = 0.0;
    double par2 = 0.0;
    double par3 = 0.0;
    double par4 = 0.0;
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      par1 += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getTC();
      par2 += phase.getPhase().getComponent(i).getx()
          * phase.getPhase().getComponent(i).getMolarMass() * 1000.0;
      par3 += phase.getPhase().getComponent(i).getx() * phase.getPhase().getComponent(i).getPC();
      par4 += phase.getPhase().getComponent(i).getx()
          * phase.getPhase().getComponent(i).getCriticalVolume();
      double TR = phase.getPhase().getTemperature() / phase.getPhase().getComponent(i).getTC();
      temp2 = Math.pow(phase.getPhase().getComponent(i).getTC(), 1.0 / 6.0)
          / (Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1000.0, 1.0 / 2.0)
              * Math.pow(phase.getPhase().getComponent(i).getPC(), 2.0 / 3.0));
      temp = TR < 1.5 ? 34.0e-5 * 1.0 / temp2 * Math.pow(TR, 0.94)
          : 17.78e-5 * 1.0 / temp2 * Math.pow(4.58 * TR - 1.67, 5.0 / 8.0);

      temp3 += phase.getPhase().getComponent(i).getx() * temp
          * Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1000.0, 1.0 / 2.0);
      temp4 += phase.getPhase().getComponent(i).getx()
          * Math.pow(phase.getPhase().getComponent(i).getMolarMass() * 1000.0, 1.0 / 2.0);
    }

    lowPresVisc = temp3 / temp4;
    // logger.info("LP visc " + lowPresVisc);
    critDens = 1.0 / par4; // mol/cm3
    double eps =
        Math.pow(par1, 1.0 / 6.0) * Math.pow(par2, -1.0 / 2.0) * Math.pow(par3, -2.0 / 3.0);
    double reducedDensity = phase.getPhase().getPhysicalProperties().getDensity()
        / phase.getPhase().getMolarMass() / critDens / 1000000.0;
    // System.out.println("reduced density " + reducedDensity);
    double numb = a[0] + a[1] * reducedDensity + a[2] * Math.pow(reducedDensity, 2.0)
        + a[3] * Math.pow(reducedDensity, 3.0) + a[4] * Math.pow(reducedDensity, 4.0);
    double viscosity = (-Math.pow(10.0, -4.0) + Math.pow(numb, 4.0)) / eps + lowPresVisc;
    viscosity /= 1.0e3;
    // System.out.println("visc " + viscosity);
    return viscosity;
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return 0;
  }
}
