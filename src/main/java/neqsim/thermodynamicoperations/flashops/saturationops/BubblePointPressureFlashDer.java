package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * bubblePointPressureFlashDer class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class BubblePointPressureFlashDer extends ConstantDutyPressureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubblePointPressureFlashDer.class);

  /**
   * <p>
   * Constructor for bubblePointPressureFlashDer.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public BubblePointPressureFlashDer(SystemInterface system) {
    super(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.getPhase(0).getNumberOfComponents() == 1
        && system.getTemperature() > system.getPhase(0).getComponent(0).getTC()) {
      setSuperCritical(true);
    }
    int iterations = 0;
    int maxNumberOfIterations = 500;
    double yold = 0;
    double ytotal = 1;
    double deriv = 0;
    double funk = 0;
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
      // (1,1) changed to (1,0) by Neeraj. Also change at line 125
      system.getChemicalReactionOperations().solveChemEq(1, 0);
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

    do {
      chemIter++;
      oldChemPres = system.getPressure();
      iterations = 0;
      do {
        iterations++;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          system.getPhases()[0].getComponent(i)
              .setx(system.getPhases()[0].getComponent(i).getx() / ytotal);
        }
        system.init(1);
        oldPres = system.getPressure();
        ktot = 0.0;
        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
          do {
            yold = system.getPhases()[0].getComponent(i).getx();
            if (!Double.isNaN(
                Math.exp(Math.log(system.getPhases()[1].getComponent(i).getFugacityCoefficient())
                    - Math.log(system.getPhases()[0].getComponent(i).getFugacityCoefficient())))) {
              if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
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
            logger.error("K error : nan");
          }
        }

        ytotal = 0.0;
        deriv = 0.0;
        funk = 0.0;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
          ytotal += system.getPhases()[0].getComponent(i).getx();
          // Following lines added by Neeraj
          funk += system.getPhases()[1].getComponent(i).getx()
              * system.getPhases()[1].getComponent(i).getK();
          deriv += system.getPhases()[1].getComponent(i).getx()
              * system.getPhases()[1].getComponent(i).getK()
              * (system.getPhases()[1].getComponent(i).logfugcoefdP(this.system.getPhase(1))
                  - system.getPhases()[0].getComponent(i).logfugcoefdP(this.system.getPhase(0)));
        }
        // logger.info("ytot " + ytotal + " pres " + system.getPressure());

        // system.getPhase(0).normalize();
        // logger.info("deriv
        // "+system.getPhases()[0].getComponents()[3].logfugcoefdP(this.system.getPhase(0)));
        // logger.info("ytot "+ytotal);
        if (ytotal > 1.5) {
          ytotal = 1.5;
        }
        if (ytotal < 0.5) {
          ytotal = 0.5;
        }

        // Following line added by Neeraj
        system.setPressure(system.getPressure() - (funk - 1) / deriv);
        // Following line commented out by Neeraj
        // system.setPressure(system.getPressure()*ytotal); // +
        // 0.5*(ytotal*system.getPressure()-system.getPressure()));
        if (system.getPressure() < 0) {
          system.setPressure(oldChemPres / 2.0);
          run();
          return;
        }
        if (system.getPressure() > 5 * oldChemPres) {
          system.setPressure(oldChemPres * 5);
          run();
          return;
        }
        // logger.info("iter in bub calc " + iterations + " pres " +
        // system.getPressure()+ " ytot " + ytotal + " chem iter " + chemIter);
      } while (((((Math.abs(ytotal - 1.0)) > 1e-7)
          || Math.abs(oldPres - system.getPressure()) / oldPres > 1e-6)
          && (iterations < maxNumberOfIterations)) || iterations < 5);

      if (system.isChemicalSystem()) { // && (iterations%3)==0 && iterations<50){
        // Should also change -- Neeraj
        chemSolved = system.getChemicalReactionOperations().solveChemEq(1, 0);
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
      // Print command added by Neeraj
      logger.info("Supercritical vapor phase !!");
      setSuperCritical(true);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}
}
