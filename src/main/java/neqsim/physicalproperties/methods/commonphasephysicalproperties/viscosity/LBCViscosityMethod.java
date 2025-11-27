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
    double volumeMixRooted = 0.0;
    double epsilonMixSum = 0.0;
    for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
      double criticalVolume = phase.getPhase().getComponent(i).getCriticalVolume();
      // Book correlation requires critical volume in cm3/mol. Component data may be stored in m3/mol
      // for TBP/plus fractions, so convert when needed before applying the cubic mixing rule.
      if (criticalVolume < 1.0) {
        criticalVolume *= 1.0e6; // convert from m3/mol to cm3/mol
      }
      volumeMixRooted += phase.getPhase().getComponent(i).getx() * Math.cbrt(criticalVolume);

      double molarMass = phase.getPhase().getComponent(i).getMolarMass() * 1000.0;
      double tc = phase.getPhase().getComponent(i).getTC();
      double pc = phase.getPhase().getComponent(i).getPC();
      double TR = phase.getPhase().getTemperature() / tc;
      temp2 = Math.pow(tc, 1.0 / 6.0)
          / (Math.pow(molarMass, 1.0 / 2.0) * Math.pow(pc, 2.0 / 3.0));
      epsilonMixSum += phase.getPhase().getComponent(i).getx() * Math.pow(temp2, 6.0);
      temp = TR < 1.5 ? 34.0e-5 * 1.0 / temp2 * Math.pow(TR, 0.94)
          : 17.78e-5 * 1.0 / temp2 * Math.pow(4.58 * TR - 1.67, 5.0 / 8.0);

      temp3 += phase.getPhase().getComponent(i).getx() * temp
          * Math.pow(molarMass, 1.0 / 2.0);
      temp4 += phase.getPhase().getComponent(i).getx() * Math.pow(molarMass, 1.0 / 2.0);

    }

    lowPresVisc = temp3 / temp4;
    // logger.info("LP visc " + lowPresVisc);
    double pseudoCriticalVolume = Math.pow(volumeMixRooted, 3.0); // cm3/mol
    critDens = 1.0 / pseudoCriticalVolume; // mol/cm3
    double epsilonMix = Math.pow(epsilonMixSum, 1.0 / 6.0);
    double reducedDensity = phase.getPhase().getPhysicalProperties().getDensity()
        / phase.getPhase().getMolarMass() / critDens / 1000000.0;
    // System.out.println("reduced density " + reducedDensity);
    double poly = a[0] + a[1] * reducedDensity + a[2] * Math.pow(reducedDensity, 2.0)
        + a[3] * Math.pow(reducedDensity, 3.0) + a[4] * Math.pow(reducedDensity, 4.0);
    double denseContribution = (Math.pow(poly, 4.0) - 1.0e-4) / epsilonMix;
    double viscositycP = denseContribution + lowPresVisc;
    return viscositycP / 1.0e3;
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    return 0;
  }
}
