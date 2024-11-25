package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * WATcalc class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WATcalc extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WATcalc.class);

  /**
   * <p>
   * Constructor for WATcalc.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public WATcalc(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    double sumx = 0.0;
    // system.setHydrateCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    int iter = 0;
    double funkOld = 0.0;
    double deltaT = 1.0;
    double[] Ksolid = new double[system.getPhase(0).getNumberOfComponents()];
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      system.getPhase(PhaseType.SOLIDCOMPLEX).getComponent(i).setx(1.0);
      Ksolid[i] = 1.0;
    }
    do {
      iter++;
      ops.TPflash();

      sumx = 0.0;
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        system.getPhase(PhaseType.SOLIDCOMPLEX).getComponent(i)
            .setx(Ksolid[i] * system.getPhase(0).getComponent(i).getx());
        Ksolid[i] = system.getPhase(0).getComponent(i).getFugacityCoefficient()
            / system.getPhase(PhaseType.SOLIDCOMPLEX).getComponent(i)
                .fugcoef(system.getPhase(PhaseType.SOLIDCOMPLEX));
        sumx += Ksolid[i] * system.getPhase(0).getComponent(i).getx();
      }
      double funk = sumx - 1.0;
      double dfunkdt = (funk - funkOld) / deltaT;
      funkOld = funk;
      double dT = -funk / dfunkdt;
      double oldTemp = system.getTemperature();
      if (iter > 1) {
        system.setTemperature(system.getTemperature() + dT * iter * 1.0 / (5.0 + iter));
      } else {
        system.setTemperature(system.getTemperature() - 0.1);
      }
      deltaT = system.getTemperature() - oldTemp;
      // logger.info("sumx " + sumx + " deltaT "+ deltaT + " dT "+dT + " temperature "
      // + system.getTemperature());
    } while (Math.abs(sumx - 1.0) > 1e-8 && iter < 100);
    // logger.info("sumx " + sumx);

    system.setNumberOfPhases(system.getNumberOfPhases() + 1);
    system.setPhaseIndex(system.getNumberOfPhases() - 1, 5);
    system.setBeta(system.getNumberOfPhases() - 1, 1e-10);
    system.init(3);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
