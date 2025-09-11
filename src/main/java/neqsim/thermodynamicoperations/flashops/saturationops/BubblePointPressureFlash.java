package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * bubblePointPressureFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class BubblePointPressureFlash extends ConstantDutyPressureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubblePointPressureFlash.class);

  /**
   * <p>
   * Constructor for bubblePointPressureFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public BubblePointPressureFlash(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.getPhase(0).getNumberOfComponents() == 1) {
      var comp = system.getPhase(0).getComponent(0);
      if (system.getTemperature() >= comp.getTC()) {
        throw new IllegalStateException("System is supercritical");
      }
      double pGuess = comp.getAntoineVaporPressure(system.getTemperature());
      if (Double.isNaN(pGuess) || pGuess <= 0 || pGuess < comp.getTriplePointPressure()
          || pGuess > comp.getPC()) {
        double tTrip = comp.getTriplePointTemperature();
        double tCrit = comp.getTC();
        double pTrip = comp.getTriplePointPressure();
        double pCrit = comp.getPC();
        double frac = (system.getTemperature() - tTrip) / (tCrit - tTrip);
        pGuess = pTrip + frac * (pCrit - pTrip);
      }
      system.setPressure(pGuess);
    }

    int iterations = 0;
    int maxNumberOfIterations = 500;
    double yold = 0;
    double ytotal = 1;
    // double deriv = 0, funk = 0;
    boolean chemSolved = true;
    // logger.info("starting");
    // system.setPressure(1.0);
    system.init(0);
    system.setNumberOfPhases(2);
    system.setBeta(1, 1.0 - 1e-10);
    system.setBeta(0, 1e-10);

    double oldPres = 0;
    double oldChemPres = 1;
    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(1, 0);
      system.getChemicalReactionOperations().solveChemEq(1, 1);
    }

    for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
      system.getPhases()[1].getComponent(i).setx(system.getPhases()[0].getComponent(i).getz());
      if (system.getPhases()[0].getComponent(i).getIonicCharge() != 0) {
        system.getPhases()[0].getComponent(i).setx(1e-40);
      } else {
        system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
            * system.getPhases()[1].getComponent(i).getz());
      }
    }

    ytotal = 0.0;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      ytotal += system.getPhases()[0].getComponent(i).getx();
    }

    double ktot = 0.0;
    int chemIter = 0;

    chemLoop: do {
      chemIter++;
      oldChemPres = system.getPressure();
      iterations = 0;
      do {
        iterations++;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          system.getPhases()[0].getComponent(i)
              .setx(system.getPhases()[0].getComponent(i).getx() / ytotal);
        }
        system.init(3);
        oldPres = system.getPressure();
        ktot = 0.0;
        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
          do {
            yold = system.getPhases()[0].getComponent(i).getx();
            if (!Double.isNaN(
                Math.exp(Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient())
                    - Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient())))) {
              if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                  || system.getPhase(0).getComponent(i).isIsIon()) {
                system.getPhases()[0].getComponent(i).setK(1e-40);
              } else {
                system.getPhases()[0].getComponent(i).setK(Math.exp(
                    Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient()) - Math
                        .log(system.getPhases()[0].getComponent(i).getFugacityCoefficient())));
              }
            }
            system.getPhases()[1].getComponent(i)
                .setK(system.getPhases()[0].getComponent(i).getK());
            system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
                * system.getPhases()[1].getComponent(i).getz());
            // logger.info("y err " +
            // Math.abs(system.getPhases()[0].getComponent(i).getx()-yold));
          } while (Math.abs(system.getPhases()[0].getComponent(i).getx() - yold) / yold > 1e-8);
          ktot += Math.abs(system.getPhases()[1].getComponent(i).getK() - 1.0);
        }
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          if (!Double.isNaN(system.getPhases()[0].getComponent(i).getK())) {
            system.getPhases()[0].getComponent(i).setx(system.getPhases()[0].getComponent(i).getK()
                * system.getPhases()[1].getComponent(i).getz());
          } else {
            system.init(0);
            logger.error("k err. : nan");
          }
        }

        ytotal = 0.0;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          ytotal += system.getPhases()[0].getComponent(i).getx();
        }
        // zlogger.info("ytot " + ytotal + " pres " + system.getPressure());

        // system.getPhase(0).normalize();

        if (ytotal > 1.5) {
          ytotal = 1.5;
        }
        if (ytotal < 0.5) {
          ytotal = 0.5;
        }
        system.setPressure(system.getPressure() * ytotal);
        // + 0.5*(ytotal*system.getPressure()-system.getPressure()));
        if (system.getPressure() < 0) {
          system.setPressure(oldChemPres / 2.0);
          continue chemLoop;
        }
        if (system.getPressure() > 5 * oldChemPres) {
          system.setPressure(oldChemPres * 5);
          continue chemLoop;
        }
        // logger.info("iter in bub calc " + iterations + " pres " +
        // system.getPressure()+ " ytot " + ytotal + " chem iter " + chemIter);
      } while (((((Math.abs(ytotal - 1.0)) > 1e-7)
          || Math.abs(oldPres - system.getPressure()) / oldPres > 1e-6)
          && (iterations < maxNumberOfIterations)) || iterations < 5);

      if (system.isChemicalSystem()) { // && (iterations%3)==0 && iterations<50){
        chemSolved = system.getChemicalReactionOperations().solveChemEq(1, 1);
        system.setBeta(1, 1.0 - 1e-10);
        system.setBeta(0, 1e-10);
      }
      // logger.info("iter in bub calc " + iterations + " pres " +
      // system.getPressure()+ " chem iter " + chemIter);
    } while ((Math.abs(oldChemPres - system.getPressure()) / oldChemPres > 1e-6 || chemIter < 2
        || !chemSolved) && chemIter < 20);
    // if(system.getPressure()>300) system.setPressure(300.0);
    // logger.info("iter in bub calc " + iterations + " pres " +
    // system.getPressure()+ " chem iter " + chemIter);
    // logger.info("iter " + iterations + " XTOT " +ytotal + " ktot " +ktot);
    system.init(1);
    if (Math.abs(ytotal - 1.0) > 1e-4
        || ktot < 1e-3 && system.getPhase(0).getNumberOfComponents() > 1) {
      logger.info("ytot " + Math.abs(ytotal - 1.0));
      setSuperCritical(true);
    }
    if (isSuperCritical()) {
      // throw new IllegalStateException("System is supercritical");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
