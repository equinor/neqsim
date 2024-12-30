package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * <p>
 * TPflash class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TPflash extends Flash {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(TPflash.class);

  SystemInterface clonedSystem;
  double betaTolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
  double presdiff = 1.0;

  /**
   * <p>
   * Constructor for TPflash.
   * </p>
   */
  public TPflash() {}

  /**
   * <p>
   * Constructor for TPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TPflash(SystemInterface system) {
    this.system = system;
    lnOldOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldoldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
  }

  /**
   * <p>
   * Constructor for TPflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public TPflash(SystemInterface system, boolean checkForSolids) {
    this(system);
    solidCheck = checkForSolids;
  }

  /**
   * <p>
   * sucsSubs.
   * </p>
   */
  public void sucsSubs() {
    deviation = 0;

    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
        Kold = system.getPhase(0).getComponent(i).getK();
        system.getPhase(0).getComponent(i).setK(1.0e-40);
        system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
      } else {
        Kold = system.getPhase(0).getComponent(i).getK();
        system.getPhase(0).getComponent(i)
            .setK(system.getPhase(1).getComponent(i).getFugacityCoefficient()
                / system.getPhase(0).getComponent(i).getFugacityCoefficient() * presdiff);
        if (Double.isNaN(system.getPhase(0).getComponent(i).getK())) {
          system.getPhase(0).getComponent(i).setK(Kold);
          system.init(1);
        }
        system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
        deviation += Math.abs(Math.log(system.getPhase(0).getComponent(i).getK()) - Math.log(Kold));
      }
    }

    double oldBeta = system.getBeta();

    RachfordRice rachfordRice = new RachfordRice();
    try {
      // system.setBeta(rachfordRice.calcBetaS(system));
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (IsNaNException ex) {
      logger.warn("Not able to calculate beta. Value is NaN");
      system.setBeta(oldBeta);
    } catch (TooManyIterationsException ex) {
      logger.warn("Not able to calculate beta, calculation is not converging.");
      system.setBeta(oldBeta);
    }
    if (system.getBeta() > 1.0 - betaTolerance) {
      system.setBeta(1.0 - betaTolerance);
    }
    if (system.getBeta() < betaTolerance) {
      system.setBeta(betaTolerance);
    }
    system.calc_x_y();
    system.init(1);
  }

  /**
   * <p>
   * accselerateSucsSubs.
   * </p>
   */
  public void accselerateSucsSubs() {
    double prod1 = 0.0;
    double prod2 = 0.0;
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      prod1 += oldDeltalnK[i] * oldoldDeltalnK[i];
      prod2 += oldoldDeltalnK[i] * oldoldDeltalnK[i];
    }

    double lambda = prod1 / prod2;

    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      // lnK[i] = lnK[i] + lambda*lambda*oldoldDeltalnK[i]/(1.0-lambda); // byttet +
      // til -
      lnK[i] += lambda / (1.0 - lambda) * deltalnK[i];
      system.getPhase(0).getComponent(i).setK(Math.exp(lnK[i]));
      system.getPhase(1).getComponent(i).setK(Math.exp(lnK[i]));
    }
    double oldBeta = system.getBeta();
    RachfordRice rachfordRice = new RachfordRice();
    try {
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (Exception ex) {
      system.setBeta(rachfordRice.getBeta()[0]);
      if (system.getBeta() > 1.0 - betaTolerance || system.getBeta() < betaTolerance) {
        system.setBeta(oldBeta);
      }
      // logger.info("temperature " + system.getTemperature() + " pressure " +
      // system.getPressure());
      logger.error(ex.getMessage(), ex);
    }

    system.calc_x_y();
    system.init(1);
    // sucsSubs();
  }

  /**
   * <p>
   * setNewK.
   * </p>
   */
  public void setNewK() {
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      lnOldOldOldK[i] = lnOldOldK[i];
      lnOldOldK[i] = lnOldK[i];
      lnOldK[i] = lnK[i];
      lnK[i] = Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient())
          - Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient());

      oldoldDeltalnK[i] = lnOldOldK[i] - lnOldOldOldK[i];
      oldDeltalnK[i] = lnOldK[i] - lnOldOldK[i];
      deltalnK[i] = lnK[i] - lnOldK[i];
    }
  }

  /**
   * <p>
   * resetK.
   * </p>
   */
  public void resetK() {
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      lnK[i] = lnOldK[i];
      system.getPhase(0).getComponent(i).setK(Math.exp(lnK[i]));
      system.getPhase(1).getComponent(i).setK(Math.exp(lnK[i]));
    }
    try {
      RachfordRice rachfordRice = new RachfordRice();
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
      system.calc_x_y();
      system.init(1);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.isForcePhaseTypes() && system.getMaxNumberOfPhases() == 1) {
      system.setNumberOfPhases(1);
      return;
    }

    findLowestGibbsPhaseIsChecked = false;
    int minGibbsPhase = 0;
    double minimumGibbsEnergy = 0;

    system.init(0);
    system.init(1);

    if ((system.getPhase(0).getGibbsEnergy()
        * (1.0 - Math.signum(system.getPhase(0).getGibbsEnergy()) * 1e-8)) < system.getPhase(1)
            .getGibbsEnergy()) {
      minGibbsPhase = 0;
    } else {
      minGibbsPhase = 1;
    }
    // logger.debug("minimum gibbs phase " + minGibbsPhase);
    minimumGibbsEnergy = system.getPhase(minGibbsPhase).getGibbsEnergy();

    if (system.getPhase(0).getNumberOfComponents() == 1 || system.getMaxNumberOfPhases() == 1) {
      system.setNumberOfPhases(1);
      if (minGibbsPhase == 0) {
        system.setPhaseIndex(0, 0);
      } else {
        system.setPhaseIndex(0, 1);
      }
      if (solidCheck) {
        ThermodynamicOperations operation = new ThermodynamicOperations(system);
        operation.TPSolidflash();
      }
      return;
    }

    minGibsPhaseLogZ = new double[system.getPhase(0).getNumberOfComponents()];
    minGibsLogFugCoef = new double[system.getPhase(0).getNumberOfComponents()];

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(minGibbsPhase).getComponent(i).getz() > 1e-50) {
        minGibsPhaseLogZ[i] = Math.log(system.getPhase(minGibbsPhase).getComponent(i).getz());
      }
      minGibsLogFugCoef[i] =
          system.getPhase(minGibbsPhase).getComponent(i).getLogFugacityCoefficient();
    }

    presdiff = system.getPhase(1).getPressure() / system.getPhase(0).getPressure();
    if (Math.abs(system.getPhase(0).getPressure() - system.getPhase(1).getPressure()) > 1e-12) {
      for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        system.getPhase(0).getComponent(i)
            .setK(system.getPhase(0).getComponent(i).getK() * presdiff);
        system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
      }
    }

    if (system.isChemicalSystem()) {
      system.getChemicalReactionOperations().solveChemEq(1, 0);
      system.getChemicalReactionOperations().solveChemEq(1, 1);
    }

    // Calculates phase fractions and initial composition based on Wilson K-factors
    try {
      RachfordRice rachfordRice = new RachfordRice();
      system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    system.calc_x_y();
    system.init(1);
    // If phase fraction using Wilson K factor returns pure gas or pure liquid, we
    // try with another K value guess based on calculated fugacities.
    // This solves some problems when we have high volumes of water and heavy
    // hydrocarbons returning only one liquid phase (and this phase desolves all
    // gas)
    if (system.getBeta() > (1.0 - betaTolerance * 1.1)
        || system.getBeta() < (betaTolerance * 1.1)) {
      system.setBeta(0.5);
      sucsSubs();
    }

    // Performs three iterations of successive substitution
    for (int k = 0; k < 3; k++) {
      if (system.getBeta() < (1.0 - betaTolerance * 1.1)
          && system.getBeta() > (betaTolerance * 1.1)) {
        sucsSubs();
        if ((system.getGibbsEnergy() - minimumGibbsEnergy)
            / Math.abs(minimumGibbsEnergy) < -1e-12) {
          break;
        }
      }
    }

    // System.out.println("beta " + system.getBeta());

    int totiter = 0;
    double tpdx = 1.0;
    double tpdy = 1.0;
    double dgonRT = 1.0;
    boolean passedTests = false;
    if (system.getBeta() > (1.0 - 1.1 * betaTolerance)
        || system.getBeta() < (1.1 * betaTolerance)) {
      tpdx = 1.0;
      tpdy = 1.0;
      dgonRT = 1.0;
    } else if (system.getGibbsEnergy() < (minimumGibbsEnergy * (1.0 - 1.0e-12))) {
      tpdx = -1.0;
      tpdy = -1.0;
      dgonRT = -1.0;
    } else {
      for (i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (system.getComponent(i).getz() > 1e-50) {
          tpdy += system.getPhase(0).getComponent(i).getx()
              * (Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient())
                  + Math.log(system.getPhase(0).getComponent(i).getx()) - minGibsPhaseLogZ[i]
                  - minGibsLogFugCoef[i]);
          tpdx += system.getPhase(1).getComponent(i).getx()
              * (Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient())
                  + Math.log(system.getPhase(1).getComponent(i).getx()) - minGibsPhaseLogZ[i]
                  - minGibsLogFugCoef[i]);
        }
      }

      dgonRT = system.getPhase(0).getBeta() * tpdy + (1.0 - system.getPhase(0).getBeta()) * tpdx;

      if (dgonRT > 0) {
        if (tpdx < 0) {
          for (i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            system.getPhase(0).getComponent(i)
                .setK(Math.exp(Math.log(system.getPhase(1).getComponent(i).getFugacityCoefficient())
                    - minGibsLogFugCoef[i]) * presdiff);
            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
          }
        } else if (tpdy < 0) {
          for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            system.getPhase(0).getComponent(i)
                .setK(Math
                    .exp(minGibsLogFugCoef[i]
                        - Math.log(system.getPhase(0).getComponent(i).getFugacityCoefficient()))
                    * presdiff);
            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
          }
        } else {
          passedTests = true;
        }
      }
    }

    if (passedTests || (dgonRT > 0 && tpdx > 0 && tpdy > 0) || Double.isNaN(system.getBeta())) {
      if (system.checkStability() && stabilityCheck()) {
        if (system.doMultiPhaseCheck()) {
          // logger.info("one phase flash is stable - checking multiphase flash....");
          TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
          operation.run();
        }
        if (solidCheck) {
          this.solidPhaseFlash();
        }
        if (system.isMultiphaseWaxCheck()) {
          TPmultiflashWAX operation = new TPmultiflashWAX(system, true);
          operation.run();
        }

        system.orderByDensity();
        system.init(1);
        // commented out by Even Solbraa 6/2-2012k
        // system.init(3);
        return;
      }
    }

    setNewK();

    gibbsEnergy = system.getGibbsEnergy();
    gibbsEnergyOld = gibbsEnergy;

    // Checks if gas or oil is the most stable phase
    double gasgib = system.getPhase(0).getGibbsEnergy();
    system.setPhaseType(0, PhaseType.byValue(0));
    system.init(1, 0);
    double liqgib = system.getPhase(0).getGibbsEnergy();

    if (gasgib * (1.0 - Math.signum(gasgib) * 1e-8) < liqgib) {
      system.setPhaseType(0, PhaseType.byValue(1));
    }
    system.init(1);

    int accelerateInterval = 7;
    int newtonLimit = 20;
    int timeFromLastGibbsFail = 0;

    double chemdev = 0;
    double oldChemDiff = 1.0;
    double diffChem = 1.0;
    do {
      iterations = 0;
      do {
        iterations++;

        if (iterations < newtonLimit || system.isChemicalSystem()
            || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (timeFromLastGibbsFail > 6 && (iterations % accelerateInterval) == 0
              && !(system.isChemicalSystem() || system.doSolidPhaseCheck())) {
            accselerateSucsSubs();
          } else {
            sucsSubs();
          }
        } else if (iterations >= newtonLimit && Math
            .abs(system.getPhase(0).getPressure() - system.getPhase(1).getPressure()) < 1e-5) {
          if (iterations == newtonLimit) {
            secondOrderSolver = new SysNewtonRhapsonTPflash(system, 2,
                system.getPhases()[0].getNumberOfComponents());
          }
          try {
            deviation = secondOrderSolver.solve();
          } catch (Exception ex) {
            sucsSubs();
          }
        } else {
          sucsSubs();
        }

        gibbsEnergyOld = gibbsEnergy;
        gibbsEnergy = system.getGibbsEnergy();

        if (((gibbsEnergy - gibbsEnergyOld) / Math.abs(gibbsEnergyOld) > 1e-8
            || system.getBeta() < betaTolerance * 1.01
            || system.getBeta() > (1 - betaTolerance * 1.01)) && !system.isChemicalSystem()
            && timeFromLastGibbsFail > 0) {
          resetK();
          timeFromLastGibbsFail = 0;
          // logger.info("gibbs decrease " + (gibbsEnergy - gibbsEnergyOld) /
          // Math.abs(gibbsEnergyOld));
          // setNewK();
          // logger.info("reset K..");
        } else {
          timeFromLastGibbsFail++;
          setNewK();
        }
        // logger.info("iterations " + iterations + " error " + deviation);
      } while ((deviation > 1e-10) && (iterations < maxNumberOfIterations));
      // logger.info("iterations " + iterations + " error " + deviation);
      if (system.isChemicalSystem()) {
        oldChemDiff = chemdev;
        chemdev = 0.0;

        double[] xchem = new double[system.getPhase(0).getNumberOfComponents()];

        for (int phaseNum = 1; phaseNum < system.getNumberOfPhases(); phaseNum++) {
          for (i = 0; i < system.getPhase(phaseNum).getNumberOfComponents(); i++) {
            xchem[i] = system.getPhase(phaseNum).getComponent(i).getx();
          }

          system.init(1);
          system.getChemicalReactionOperations().solveChemEq(phaseNum, 1);

          for (i = 0; i < system.getPhase(phaseNum).getNumberOfComponents(); i++) {
            chemdev +=
                Math.abs(xchem[i] - system.getPhase(phaseNum).getComponent(i).getx()) / xchem[i];
          }
        }
        diffChem = Math.abs(oldChemDiff - chemdev);
      }
      // logger.info("chemdev: " + chemdev + " iter: " + totiter);
      totiter++;
    } while ((diffChem > 1e-6 && chemdev > 1e-6 && totiter < 300)
        || (system.isChemicalSystem() && totiter < 2));
    if (system.isChemicalSystem()) {
      sucsSubs();
    }
    if (system.doMultiPhaseCheck()) {
      TPmultiflash operation = new TPmultiflash(system, system.doSolidPhaseCheck());
      operation.run();
    } else {
      // Checks if gas or oil is the most stable phase
      try {
        if (system.getPhase(0).getType() == PhaseType.GAS) {
          gasgib = system.getPhase(0).getGibbsEnergy();
          system.setPhaseType(0, PhaseType.byValue(0));

          system.init(1, 0);
          liqgib = system.getPhase(0).getGibbsEnergy();
        } else {
          liqgib = system.getPhase(0).getGibbsEnergy();
          system.setPhaseType(0, PhaseType.byValue(1));
          system.init(1, 0);
          gasgib = system.getPhase(0).getGibbsEnergy();
        }
        if (gasgib * (1.0 - Math.signum(gasgib) * 1e-8) < liqgib) {
          system.setPhaseType(0, PhaseType.byValue(1));
        }
      } catch (Exception e) {
        system.setPhaseType(0, PhaseType.byValue(1));
      }

      system.init(1);
    }

    if (solidCheck) {
      this.solidPhaseFlash();
    }
    if (system.isMultiphaseWaxCheck()) {
      TPmultiflashWAX operation = new TPmultiflashWAX(system, true);
      operation.run();
    }

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getBeta(i) < betaTolerance * 1.01) {
        system.removePhase(i);
      }
    }
    // system.initPhysicalProperties("density");
    system.orderByDensity();
    system.init(1);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
