/*
 * TPmultiflash.java
 *
 * Created on 2. oktober 2000, 22:26
 */

package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TPmultiflash class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TPmultiflash extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPmultiflash.class);

  // SystemInterface clonedSystem;
  boolean multiPhaseTest = false;
  double[][] dQdbeta;
  double[][] Qmatrix;
  double[] Erow;
  double Q = 0;
  boolean doStabilityAnalysis = true;
  boolean removePhase = false;
  boolean checkOneRemove = false;
  boolean secondTime = false;
  boolean aqueousPhaseSeedAttempted = false;
  boolean postFlashStabilityChecked = false;

  double[] multTerm;
  double[] multTerm2;

  /**
   * <p>
   * Constructor for TPmultiflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TPmultiflash(SystemInterface system) {
    super(system);
    Erow = new double[system.getPhase(0).getNumberOfComponents()];
  }

  /**
   * <p>
   * Constructor for TPmultiflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public TPmultiflash(SystemInterface system, boolean checkForSolids) {
    super(system, checkForSolids);
    Erow = new double[system.getPhase(0).getNumberOfComponents()];
    multTerm = new double[system.getPhase(0).getNumberOfComponents()];
    multTerm2 = new double[system.getPhase(0).getNumberOfComponents()];
  }

  /**
   * <p>
   * calcMultiPhaseBeta.
   * </p>
   */
  public void calcMultiPhaseBeta() {}

  /**
   * <p>
   * setDoubleArrays.
   * </p>
   */
  public void setDoubleArrays() {
    dQdbeta = new double[system.getNumberOfPhases()][1];
    Qmatrix = new double[system.getNumberOfPhases()][system.getNumberOfPhases()];
  }

  /**
   * <p>
   * setXY.
   * </p>
   */
  public void setXY() {
    // Check for ions directly - ions must be handled specially regardless of whether
    // chemical reactions are defined. Ions can only exist in aqueous phases.
    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      boolean isAqueous = system.getPhase(k).getType() == PhaseType.AQUEOUS;

      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
          // Check for ions - ions can only exist in aqueous phases
          // This check must happen regardless of isChemicalSystem() status
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            // Ions only exist in aqueous phases, near-zero in gas/oil
            if (isAqueous) {
              // In aqueous phase, calculate ion x from moles
              double totalMoles = system.getPhase(k).getNumberOfMolesInPhase();
              if (totalMoles > 1e-100) {
                system.getPhase(k).getComponent(i)
                    .setx(system.getPhase(k).getComponent(i).getNumberOfmoles() / totalMoles);
              } else {
                system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz());
              }
            } else {
              // No ions in gas or oil phases
              system.getPhase(k).getComponent(i).setx(1e-50);
            }
          } else {
            // Non-ionic components: normal flash calculation
            // Bound fugacity coefficient to avoid numerical overflow
            double phi = system.getPhase(k).getComponent(i).getFugacityCoefficient();
            if (phi < 1e-100) {
              phi = 1e-100;
            } else if (phi > 1e100) {
              phi = 1e100;
            }
            double newX = system.getPhase(0).getComponent(i).getz() / Erow[i] / phi;
            if (!Double.isFinite(newX) || newX <= 0.0) {
              newX = Math.max(system.getPhase(0).getComponent(i).getz(), 1.0e-30);
            }
            system.getPhase(k).getComponent(i).setx(newX);
          }
        }
      }

      system.getPhase(k).normalize();
    }
  }

  /**
   * <p>
   * calcE.
   * </p>
   */
  public void calcE() {
    // E = new double[system.getPhase(0).getNumberOfComponents()];
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      Erow[i] = 0.0;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        double phi = system.getPhase(k).getComponent(i).getFugacityCoefficient();
        // Bound fugacity coefficient to avoid numerical overflow in E calculation.
        // Extremely small phi values (< 1e-100) occur due to severe non-ideality
        // (e.g., ions with hydrocarbons having kij=1.0) and cause overflow in beta/phi.
        // Extremely large phi values (> 1e100) are also non-physical for typical systems.
        if (phi < 1e-100) {
          phi = 1e-100;
        } else if (phi > 1e100) {
          phi = 1e100;
        }
        Erow[i] += system.getPhase(k).getBeta() / phi;
      }
      if (Erow[i] < 1e-100) {
        Erow[i] = 1e-100;
      }
      if (Erow[i] > 1e100) {
        Erow[i] = 1e100;
      }
      if (Double.isNaN(Erow[i])) {
        logger.error("Erow is NaN for component " + system.getPhase(0).getComponent(i).getName());
        Erow[i] = 1e-100;
      }
    }
  }

  /**
   * <p>
   * calcQ.
   * </p>
   *
   * @return a double
   */
  public double calcQ() {
    /*
     * double betaTotal = 0; for (int k = 0; k < system.getNumberOfPhases(); k++) { betaTotal +=
     * system.getPhase(k).getBeta(); } Q = betaTotal;
     */
    this.calcE();
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { Q -= Math.log(E[i]) *
     * system.getPhase(0).getComponent(i).getz(); }
     */

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      multTerm[i] = system.getPhase(0).getComponent(i).getz() / Erow[i];
      multTerm2[i] = system.getPhase(0).getComponent(i).getz() / (Erow[i] * Erow[i]);
    }

    for (int k = 0; k < system.getNumberOfPhases(); k++) {
      dQdbeta[k][0] = 1.0;
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        // Bound fugacity coefficient to avoid numerical overflow
        double phi = system.getPhase(k).getComponent(i).getFugacityCoefficient();
        if (phi < 1e-100) {
          phi = 1e-100;
        } else if (phi > 1e100) {
          phi = 1e100;
        }
        dQdbeta[k][0] -= multTerm[i] / phi;
      }
    }

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      for (int j = 0; j < system.getNumberOfPhases(); j++) {
        Qmatrix[i][j] = 0.0;
        for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
          // Bound fugacity coefficients to avoid numerical overflow
          double phiI = system.getPhase(i).getComponent(k).getFugacityCoefficient();
          double phiJ = system.getPhase(j).getComponent(k).getFugacityCoefficient();
          if (phiI < 1e-100) {
            phiI = 1e-100;
          } else if (phiI > 1e100) {
            phiI = 1e100;
          }
          if (phiJ < 1e-100) {
            phiJ = 1e-100;
          } else if (phiJ > 1e100) {
            phiJ = 1e100;
          }
          Qmatrix[i][j] += multTerm2[k] / (phiI * phiJ);
        }
        if (i == j) {
          Qmatrix[i][j] += 1.0e-3;
        }
      }
    }
    return Q;
  }

  /**
   * <p>
   * solveBeta.
   * </p>
   *
   * @return a double
   */
  public double solveBeta() {
    SimpleMatrix betaMatrix = new SimpleMatrix(1, system.getNumberOfPhases());
    SimpleMatrix ans = null;
    double err = 1.0;
    int iter = 1;
    do {
      iter++;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        betaMatrix.set(0, k, system.getPhase(k).getBeta());
      }

      calcQ();
      SimpleMatrix dQM = new SimpleMatrix(dQdbeta);
      SimpleMatrix dQdBM = new SimpleMatrix(Qmatrix);
      try {
        ans = dQdBM.solve(dQM).transpose();
      } catch (Exception ex) {
        logger.error(ex.getMessage());
        break;
      }
      betaMatrix = betaMatrix.minus(ans.scale(iter / (iter + 3.0)));
      removePhase = false;
      for (int k = 0; k < system.getNumberOfPhases(); k++) {
        double currBeta = betaMatrix.get(0, k);
        if (currBeta < phaseFractionMinimumLimit) {
          system.setBeta(k, phaseFractionMinimumLimit);
          if (checkOneRemove) {
            if (system.getPhase(k).getType() == PhaseType.GAS) {
              system.setPhaseType(k, PhaseType.LIQUID);
            }
            checkOneRemove = false;
            removePhase = true;
          }
          checkOneRemove = true;
        } else if (currBeta > (1.0 - phaseFractionMinimumLimit)) {
          system.setBeta(k, 1.0 - phaseFractionMinimumLimit);
        } else {
          system.setBeta(k, currBeta);
        }
      }
      system.normalizeBeta();
      system.init(1);
      calcE();
      setXY();
      system.init(1);
      err = ans.normF();
    } while ((err > 1e-12 && iter < 50) || iter < 3);
    // logger.info("iterations " + iter);
    return err;
  }

  /** {@inheritDoc} */
  @Override
  public void stabilityAnalysis() {
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];
    tm = new double[system.getPhase(0).getNumberOfComponents()];

    double[] alpha = null;
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    clonedSystem.add(system.clone());
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { if
     * (system.getPhase(0).getComponent(i).getx() < 1e-100) { clonedSystem.add(null); continue; }
     * double numb = 0; clonedSystem.add(system.clone());
     *
     * // (clonedSystem.get(i)).init(0); commented out sept 2005, Even S. for (int j = 0; j <
     * system.getPhase(0).getNumberOfComponents(); j++) { numb = i == j ? 1.0 : 1.0e-12; // set to 0
     * by Even Solbraa 23.01.2013 - chaged back to 1.0e-12 27.04.13 if
     * (system.getPhase(0).getComponent(j).getz() < 1e-100) { numb = 0; } (
     * clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb); } if
     * (system.getPhase(0).getComponent(i).getIonicCharge() == 0) { ( clonedSystem.get(i)).init(1);
     * } }
     */

    lowestGibbsEnergyPhase = 0;
    /*
     * // logger.info("low gibbs phase " + lowestGibbsEnergyPhase); for (int k = 0; k <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null)) { sumw[k] += (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx(); } } }
     *
     * for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for
     * (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null) && system.getPhase(0).getComponent(k).getx() > 1e-100) { (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).setx((
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[k]); } logger.info("x: " + (
     * clonedSystem.get(k)).getPhase(0).getComponent(i).getx()); } if
     * (system.getPhase(0).getComponent(k).getx() > 1e-100) { d[k] =
     * Math.log(system.getPhase(0).getComponent(k).getx()) +
     * system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
     * if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents
     * ()[k].getIonicCharge()!=0) d[k]=0; } //logger.info("dk: " + d[k]); }
     */

    // Calculate reference fugacities d[k] = ln(x_k) + ln(phi_k) for feed phase
    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
            + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
        // if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponent(k).getIonicCharge()!=0)
        // d[k]=0;
      }
    }

    // Initialize logWi array (will be overwritten for each pure component trial)
    for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
      if (system.getPhase(0).getComponent(j).getz() > 1e-100) {
        logWi[j] = 0.0;
      } else {
        logWi[j] = -10000.0;
      }
    }

    int hydrocarbonTestCompNumb = 0;
    int lightTestCompNumb = 0;
    double Mmax = 0;
    double Mmin = 1e10;
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) > Mmax) {
          Mmax = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) < Mmin) {
          Mmin = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
      }
    }
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }
    // boolean checkdForHCmix = false;

    // OPTIMIZATION: Check if system has ions - if so, we know an aqueous phase must exist
    // and we can skip water as a trial component (water-rich phase is guaranteed stable)
    boolean systemHasIons = false;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
          && system.getPhase(0).getComponent(i).getz() > 1e-20) {
        systemHasIons = true;
        break;
      }
    }

    for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
              && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)) {
        continue;
      }

      // OPTIMIZATION: Skip water and MEG as trial components when ions are present
      // Ions force existence of an aqueous phase, so water-rich trial is redundant
      if (systemHasIons) {
        String compName = minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName();
        if (compName.equals("water") || compName.equals("MEG") || compName.equals("TEG")
            || compName.equals("DEG") || compName.equals("methanol")
            || compName.equals("ethanol")) {
          // Mark as stable (tm > 0 means stable)
          tm[j] = 1.0;
          continue;
        }
      }

      double nomb = 0.0;
      for (int cc = 0; cc < system.getPhase(0).getNumberOfComponents(); cc++) {
        // Pure component trial phase: component j = 1.0, others = trace
        // OPTIMIZATION: Set ions to exactly 0 to skip expensive electrolyte calculations
        // Ions don't participate in vapor-liquid equilibrium, so their trial phase
        // contribution is not needed for stability analysis
        if (system.getPhase(0).getComponent(cc).getIonicCharge() != 0) {
          nomb = 0.0;
        } else {
          nomb = cc == j ? 1.0 : 1.0e-12;
        }
        if (system.getPhase(0).getComponent(cc).getz() < 1e-100) {
          nomb = 0.0;
        }

        // Initialize logWi to match pure component trial phase (Michelsen's algorithm)
        // For pure component j trial: Wi[j] = 1.0, Wi[others] = trace
        // So logWi[j] = 0, logWi[others] = log(1e-12) ≈ -27.6
        if (system.getPhase(0).getComponent(cc).getz() > 1e-100) {
          logWi[cc] = Math.log(Math.max(nomb, 1e-100));
        } else {
          logWi[cc] = -10000.0;
        }

        if (clonedSystem.get(0).isPhase(1)) {
          try {
            clonedSystem.get(0).getPhase(1).getComponent(cc).setx(nomb);
            /*
             * if (system.getPhase(1).getType() == PhaseType.AQUEOUS && !checkdForHCmix) {
             * clonedSystem.get(0).getPhase(1).getComponent(cc)
             * .setx(clonedSystem.get(0).getPhase(0).getComponent(cc).getK() /
             * clonedSystem.get(0).getPhase(0).getComponent(cc).getx()); } else {
             * clonedSystem.get(0).getPhase(1).getComponent(cc).setx(nomb); }
             */
          } catch (Exception ex) {
            logger.warn(ex.getMessage());
          }
        }
      }

      // if (system.getPhase(1).getType() == PhaseType.AQUEOUS && !checkdForHCmix) {
      // checkdForHCmix = true;
      // }

      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName().equals("water")
      // && minimumGibbsEnergySystem.isChemicalSystem()) continue;
      // logger.info("STAB CHECK COMP " +
      // system.getPhase(0).getComponent(j).getComponentName());
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
      int iter = 0;
      double errOld = 1.0e100;
      boolean useaccsubst = true;
      int maxsucssubiter = 150;
      int maxiter = 200;
      do {
        errOld = err;
        iter++;
        err = 0;

        if (iter <= maxsucssubiter || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (iter % 7 == 0 && useaccsubst) {
            double vec1 = 0.0;

            double vec2 = 0.0;
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            double lambda = prod1 / prod2;
            // logger.info("lambda " + lambda);
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              logWi[i] += lambda / (1.0 - lambda) * deltalogWi[i];
              err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          } else {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            clonedSystem.get(0).init(1, 1);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              // oldlogw[i] = logWi[i];
              if (!Double.isInfinite(
                  clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                logWi[i] = d[i]
                    - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
              useaccsubst = true;
            }
            if (iter > 2 && err > errOld) {
              useaccsubst = false;
            }
          }
        } else {
          SimpleMatrix f = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(), 1);
          SimpleMatrix df = null;
          SimpleMatrix identitytimesConst = null;
          // if (!secondOrderStabilityAnalysis) {
          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          clonedSystem.get(0).init(3, 1);
          alpha = new double[clonedSystem.get(0).getPhases()[0].getNumberOfComponents()];
          df = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(),
              system.getPhases()[0].getNumberOfComponents());
          identitytimesConst = SimpleMatrix.identity(system.getPhases()[0].getNumberOfComponents());
          // ,
          // system.getPhases()[0].getNumberOfComponents());
          // secondOrderStabilityAnalysis = true;
          // }

          for (int i = 0; i < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
            }
            for (int k = 0; k < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              } else {
                df.set(i, k, 0);
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              }
            }
          }

          // f.print(10, 10);
          // df.print(10, 10);
          SimpleMatrix dx = null;
          try {
            // Check if the determinant is close to zero
            double determinant = df.determinant();
            if (Math.abs(determinant) < 1e-10) {
              logger.warn("Matrix is nearly singular. Determinant: " + determinant);
              // Add a small regularization term to stabilize the solution
              dx = df.plus(identitytimesConst.scale(1e-6)).solve(f).negative();
            } else {
              dx = df.plus(identitytimesConst).solve(f).negative();
            }
          } catch (Exception e) {
            logger.error("Error solving matrix equation: " + e.getMessage());
            logger.debug("Attempting fallback with scaled regularization...");
            try {
              // Fallback: Add a larger regularization term and retry
              dx = df.plus(identitytimesConst.scale(0.2)).solve(f).negative();
            } catch (Exception ex) {
              logger.error("Fallback matrix solve failed: " + ex.getMessage());
              logger.debug("Attempting pseudo-inverse fallback...");
              try {
                DMatrixRMaj pinv = new DMatrixRMaj(df.numCols(), df.numRows());
                CommonOps_DDRM.pinv(df.getDDRM(), pinv);
                DMatrixRMaj result = new DMatrixRMaj(df.numCols(), 1);
                CommonOps_DDRM.mult(pinv, f.getDDRM(), result);
                dx = SimpleMatrix.wrap(result).negative();
                logger.warn("Used pseudo-inverse matrix solve.");
              } catch (Exception ex2) {
                logger.error("Pseudo-inverse fallback failed: " + ex2.getMessage());
                logger.warn("Setting dx to zero matrix as a last resort.");
                dx = new SimpleMatrix(f.numRows(), f.numCols());
              }
            }
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            double alphaNew = alpha[i] + dx.get(i, 0);
            Wi[j][i] = Math.pow(alphaNew / 2.0, 2.0);
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              logWi[i] = Math.log(Wi[j][i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -1000.0;
            }
            err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
          }

          // logger.info("err newton " + err);
        }
        // logger.info("err: " + err);

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(safeExp(logWi[i]));
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
      } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);
      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= safeExp(logWi[i]);
        }
        x[j][i] = clonedSystem.get(0).getPhase(1).getComponent(i).getx();
        // logger.info("txji: " + x[j][i]);

        xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
      }
      if (iter >= maxiter) {
        // logger.info("iter > maxiter multiphase stability ");
        // logger.info("error " + Math.abs(err));
        // logger.info("tm: " + tm[j]);
      }

      if (Math.abs(xTrivialCheck0) < 1e-4 || Math.abs(xTrivialCheck1) < 1e-4) {
        tm[j] = 10.0;
      }

      if (tm[j] < -1e-8) {
        break;
      }
    }

    int unstabcomp = 0;
    for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
      if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
        system.addPhase();
        unstabcomp = k;
        for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
          system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
        }
        system.getPhases()[system.getNumberOfPhases() - 1].normalize();
        multiPhaseTest = true;
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(0).getComponent(unstabcomp).getz());
        system.init(1);
        system.normalizeBeta();

        // logger.info("STABILITY ANALYSIS: ");
        // logger.info("tm1: " + k + " "+ tm[k]);
        // system.display();
        return;
      }
    }

    system.normalizeBeta();
    // logger.info("STABILITY ANALYSIS: ");
    // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);
    // system.display();
  }

  /**
   * Enhanced stability analysis that uses Wilson K-values for initial guesses and tests multiple
   * trial phase compositions. This method is more robust for detecting liquid-liquid equilibria and
   * three-phase systems (e.g., CO2/H2S/hydrocarbon mixtures).
   *
   * <p>
   * Key improvements over basic stabilityAnalysis():
   * </p>
   * <ul>
   * <li>Uses Wilson K-value correlation for vapor-liquid equilibrium (VLE) detection</li>
   * <li>Tests vapor-like trial (K), liquid-like trial (1/K), and LLE-specific trial phases</li>
   * <li>LLE trial uses acentric factor-based perturbation (polarity proxy) since Wilson K-values
   * are derived from vapor pressure correlations and may not capture activity coefficient-driven
   * liquid-liquid splits</li>
   * <li>Does not skip non-hydrocarbon components (important for CO2, H2S systems)</li>
   * <li>Tests stability against all existing phases, not just phase 0</li>
   * <li>Includes Wegstein acceleration for faster convergence</li>
   * </ul>
   */
  public void stabilityAnalysisEnhanced() {
    int numComponents = system.getPhase(0).getNumberOfComponents();
    double[] logWi = new double[numComponents];
    double[] Wi = new double[numComponents];
    double[] oldlogw = new double[numComponents];
    double[] oldoldlogw = new double[numComponents];
    double[] deltalogWi = new double[numComponents];
    double[] oldDeltalogWi = new double[numComponents];
    double[] x = new double[numComponents];
    tm = new double[numComponents];

    // Initialize all tm values to stable (positive)
    for (int i = 0; i < numComponents; i++) {
      tm[i] = 10.0;
    }

    // Clone system once - reuse for all tests
    SystemInterface clonedSystem = system.clone();

    // Calculate Wilson K-values for each component once
    // K = (Pc/P) * exp(5.373 * (1 + omega) * (1 - Tc/T))
    double[] wilsonK = new double[numComponents];
    double[] logWilsonK = new double[numComponents];
    double tempK = system.getTemperature();
    double presBar = system.getPressure();

    // Pre-calculate which components are valid (z > threshold and not ionic)
    boolean[] validComponent = new boolean[numComponents];
    int validCount = 0;
    for (int j = 0; j < numComponents; j++) {
      double z = system.getPhase(0).getComponent(j).getz();
      boolean isIon = system.getPhase(0).getComponent(j).getIonicCharge() != 0;
      validComponent[j] = z > 1e-100 && !isIon;
      if (validComponent[j]) {
        validCount++;
        double tc = system.getPhase(0).getComponent(j).getTC();
        double pc = system.getPhase(0).getComponent(j).getPC();
        double omega = system.getPhase(0).getComponent(j).getAcentricFactor();
        double kVal = (pc / presBar) * Math.exp(5.373 * (1.0 + omega) * (1.0 - tc / tempK));
        wilsonK[j] = Math.max(kVal, 1e-20);
        logWilsonK[j] = Math.log(wilsonK[j]);
      } else {
        wilsonK[j] = 1.0;
        logWilsonK[j] = 0.0;
      }
    }

    // Early exit if no valid components
    if (validCount == 0) {
      system.normalizeBeta();
      return;
    }

    int numPhases = system.getNumberOfPhases();

    // Pre-calculate reference fugacities for all phases
    double[][] dRef = new double[numPhases][numComponents];
    for (int refPhase = 0; refPhase < numPhases; refPhase++) {
      for (int k = 0; k < numComponents; k++) {
        double xk = system.getPhase(refPhase).getComponent(k).getx();
        if (xk > 1e-100) {
          dRef[refPhase][k] =
              Math.log(xk) + system.getPhase(refPhase).getComponent(k).getLogFugacityCoefficient();
        }
      }
    }

    // Test stability for EACH existing phase as reference phase
    for (int refPhase = 0; refPhase < numPhases; refPhase++) {
      double[] d = dRef[refPhase];

      // Test with three different initial guesses:
      // trialType = 1: Vapor-like trial phase (use Wilson K directly) - for VLE gas detection
      // trialType = -1: Liquid-like trial phase (use 1/Wilson K) - for VLE liquid detection
      // trialType = 0: LLE trial (composition perturbation) - for liquid-liquid equilibrium
      // Wilson K-values are based on vapor pressure and work well for VLE,
      // but LLE is driven by activity coefficient differences (polarity, H-bonding),
      // so we use a different initialization strategy for LLE detection.
      for (int trialType = 1; trialType >= -1; trialType--) {
        // Initialize logWi based on trial type
        for (int j = 0; j < numComponents; j++) {
          if (!validComponent[j]) {
            logWi[j] = -10000.0;
            Wi[j] = 0.0;
          } else if (trialType == 1) {
            // Vapor-like: use Wilson K (volatile components enriched)
            logWi[j] = logWilsonK[j];
            Wi[j] = Math.exp(logWi[j]);
          } else if (trialType == -1) {
            // Liquid-like: use 1/K (heavy components enriched)
            logWi[j] = -logWilsonK[j];
            Wi[j] = Math.exp(logWi[j]);
          } else {
            // LLE trial (trialType == 0): perturb based on polarity/activity
            // Use component properties to create polar vs non-polar split
            // Components with high acentric factor or polar nature go one way
            double omega = system.getPhase(0).getComponent(j).getAcentricFactor();
            double z = system.getPhase(0).getComponent(j).getz();
            // Alternate enrichment based on acentric factor (proxy for polarity)
            // Higher omega -> more polar/associating -> enrich in one liquid phase
            double perturbFactor = (omega > 0.15) ? 2.0 : 0.5;
            Wi[j] = z * perturbFactor;
            logWi[j] = Math.log(Math.max(Wi[j], 1e-100));
          }
          oldlogw[j] = logWi[j];
          oldoldlogw[j] = logWi[j];
          deltalogWi[j] = 0.0;
          oldDeltalogWi[j] = 0.0;
        }

        // Set initial trial phase composition
        for (int cc = 0; cc < numComponents; cc++) {
          if (clonedSystem.isPhase(1)) {
            clonedSystem.getPhase(1).getComponent(cc).setx(validComponent[cc] ? Wi[cc] : 1e-50);
          }
        }

        // Successive substitution iterations with acceleration
        int iter = 0;
        double err = 1.0e10;
        double errOld = 1.0e100;
        int maxiter = 150; // Reduced from 200 - Wilson init converges faster
        boolean useAcceleration = true;

        do {
          errOld = err;
          iter++;
          err = 0;

          // Store old values for acceleration
          for (int i = 0; i < numComponents; i++) {
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldDeltalogWi[i] = deltalogWi[i];
          }

          clonedSystem.init(1, 1);

          // Update logWi from fugacity coefficients
          for (int i = 0; i < numComponents; i++) {
            if (validComponent[i]) {
              double logFugCoeff =
                  clonedSystem.getPhase(1).getComponent(i).getLogFugacityCoefficient();
              if (!Double.isInfinite(logFugCoeff)) {
                logWi[i] = d[i] - logFugCoeff;
              }
            }
            deltalogWi[i] = logWi[i] - oldlogw[i];
            err += Math.abs(deltalogWi[i]);
            Wi[i] = safeExp(logWi[i]);
          }

          // Wegstein/GDEM acceleration every 7th iteration
          if (iter % 7 == 0 && iter > 7 && useAcceleration && err < errOld) {
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (int i = 0; i < numComponents; i++) {
              if (validComponent[i]) {
                double vec1 = deltalogWi[i] * oldDeltalogWi[i];
                double vec2 = oldDeltalogWi[i] * oldDeltalogWi[i];
                prod1 += vec1;
                prod2 += vec2;
              }
            }
            if (prod2 > 1e-20) {
              double lambda = prod1 / prod2;
              if (lambda > 0.0 && lambda < 1.0) {
                double accelFactor = lambda / (1.0 - lambda);
                for (int i = 0; i < numComponents; i++) {
                  if (validComponent[i]) {
                    logWi[i] += accelFactor * deltalogWi[i];
                    Wi[i] = safeExp(logWi[i]);
                  }
                }
              }
            }
          }

          // Disable acceleration if error is increasing
          if (iter > 2 && err > errOld) {
            useAcceleration = false;
          }

          // Update trial phase compositions
          for (int i = 0; i < numComponents; i++) {
            clonedSystem.getPhase(1).getComponent(i).setx(validComponent[i] ? Wi[i] : 1e-50);
          }
        } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);

        // Calculate tangent plane distance
        double tmVal = 1.0;
        for (int i = 0; i < numComponents; i++) {
          if (validComponent[i]) {
            tmVal -= Wi[i];
          }
          x[i] = clonedSystem.getPhase(1).getComponent(i).getx();
        }

        // Check for trivial solution (trial phase same as any existing phase)
        boolean isTrivial = false;
        for (int existingPhase = 0; existingPhase < numPhases; existingPhase++) {
          double xTrivialCheck = 0.0;
          for (int i = 0; i < numComponents; i++) {
            xTrivialCheck += Math.abs(x[i] - system.getPhase(existingPhase).getComponent(i).getx());
          }
          if (xTrivialCheck < 1e-4) {
            isTrivial = true;
            break;
          }
        }

        // If unstable and non-trivial, add new phase and return
        if (!isTrivial && tmVal < -1e-8) {
          system.addPhase();
          int newPhaseIdx = system.getNumberOfPhases() - 1;
          for (int i = 0; i < numComponents; i++) {
            system.getPhase(newPhaseIdx).getComponent(i).setx(x[i]);
          }
          system.getPhases()[newPhaseIdx].normalize();
          multiPhaseTest = true;

          // Set initial beta based on dominant component
          int dominantComp = 0;
          double maxX = 0;
          for (int i = 0; i < numComponents; i++) {
            if (x[i] > maxX) {
              maxX = x[i];
              dominantComp = i;
            }
          }
          system.setBeta(newPhaseIdx, system.getPhase(0).getComponent(dominantComp).getz());
          system.init(1);
          system.normalizeBeta();
          return;
        }
      }
    }

    system.normalizeBeta();
  }

  /**
   * <p>
   * stabilityAnalysis3.
   * </p>
   */
  public void stabilityAnalysis3() {
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] sumw = new double[system.getPhase(0).getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];
    tm = new double[system.getPhase(0).getNumberOfComponents()];

    double[] alpha = null;
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    clonedSystem.add(system.clone());
    /*
     * for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) { if
     * (system.getPhase(0).getComponent(i).getx() < 1e-100) { clonedSystem.add(null); continue; }
     * double numb = 0; clonedSystem.add(system.clone());
     *
     * // (clonedSystem.get(i)).init(0); commented out sept 2005, Even S. for (int j = 0; j <
     * system.getPhase(0).getNumberOfComponents(); j++) { numb = i == j ? 1.0 : 1.0e-12; // set to 0
     * by Even Solbraa 23.01.2013 - chaged back to 1.0e-12 27.04.13 if
     * (system.getPhase(0).getComponent(j).getz() < 1e-100) { numb = 0; } (
     * clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb); } if
     * (system.getPhase(0).getComponent(i).getIonicCharge() == 0) { ( clonedSystem.get(i)).init(1);
     * } }
     */

    lowestGibbsEnergyPhase = 0;
    /*
     * // logger.info("low gibbs phase " + lowestGibbsEnergyPhase); for (int k = 0; k <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for (int i = 0; i <
     * minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null)) { sumw[k] += (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx(); } } }
     *
     * for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) { for
     * (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) { if (!((
     * clonedSystem.get(k)) == null) && system.getPhase(0).getComponent(k).getx() > 1e-100) { (
     * clonedSystem.get(k)).getPhase(1).getComponent(i).setx((
     * clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[k]); } logger.info("x: " + (
     * clonedSystem.get(k)).getPhase(0).getComponent(i).getx()); } if
     * (system.getPhase(0).getComponent(k).getx() > 1e-100) { d[k] =
     * Math.log(system.getPhase(0).getComponent(k).getx()) +
     * system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
     * if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents
     * ()[k].getIonicCharge()!=0) d[k]=0; } //logger.info("dk: " + d[k]); }
     */
    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
            + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
        // if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponent(k).getIonicCharge()!=0)
        // d[k]=0;
      }
    }

    for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
      if (system.getPhase(0).getComponent(j).getz() > 1e-100) {
        logWi[j] = 1.0;
      } else {
        logWi[j] = -10000.0;
      }
    }

    int hydrocarbonTestCompNumb = 0;
    int lightTestCompNumb = 0;
    double Mmax = 0;
    double Mmin = 1e10;
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) > Mmax) {
          Mmax = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) < Mmin) {
          Mmin = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
      }
    }

    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }

    // OPTIMIZATION: Check if system has ions - if so, we know an aqueous phase must exist
    // and we can skip water as a trial component (water-rich phase is guaranteed stable)
    boolean systemHasIons = false;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
          && system.getPhase(0).getComponent(i).getz() > 1e-20) {
        systemHasIons = true;
        break;
      }
    }

    for (int j = 0; j < system.getNumberOfComponents(); j++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
              && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)) {
        continue;
      }

      // OPTIMIZATION: Skip water and MEG as trial components when ions are present
      // Ions force existence of an aqueous phase, so water-rich trial is redundant
      if (systemHasIons) {
        String compName = minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName();
        if (compName.equals("water") || compName.equals("MEG") || compName.equals("TEG")
            || compName.equals("DEG") || compName.equals("methanol")
            || compName.equals("ethanol")) {
          // Mark as stable (tm > 0 means stable)
          tm[j] = 1.0;
          continue;
        }
      }

      double nomb = 0.0;
      for (int cc = 0; cc < system.getPhase(0).getNumberOfComponents(); cc++) {
        // OPTIMIZATION: Set ions to exactly 0 to skip expensive electrolyte calculations
        if (system.getPhase(0).getComponent(cc).getIonicCharge() != 0) {
          nomb = 0.0;
        } else {
          nomb = cc == j ? 1.0 : 1.0e-12;
        }
        if (system.getPhase(0).getComponent(cc).getz() < 1e-100) {
          nomb = 0.0;
        }

        if (clonedSystem.get(0).isPhase(1)) {
          try {
            clonedSystem.get(0).getPhase(1).getComponent(cc).setx(nomb);
          } catch (Exception ex) {
            logger.warn(ex.getMessage());
          }
        }
      }
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName().equals("water")
      // && minimumGibbsEnergySystem.isChemicalSystem()) continue;
      // logger.info("STAB CHECK COMP " +
      // system.getPhase(0).getComponent(j).getComponentName());
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
      int iter = 0;
      double errOld = 1.0e100;
      boolean useaccsubst = true;
      int maxsucssubiter = 150;
      int maxiter = 200;
      do {
        errOld = err;
        iter++;
        err = 0;

        if (iter <= maxsucssubiter || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (iter % 7 == 0 && useaccsubst) {
            double vec1 = 0.0;

            double vec2 = 0.0;
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            double lambda = prod1 / prod2;
            // logger.info("lambda " + lambda);
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              logWi[i] += lambda / (1.0 - lambda) * deltalogWi[i];
              err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          } else {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            clonedSystem.get(0).init(1, 1);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              // oldlogw[i] = logWi[i];
              if (!Double.isInfinite(
                  clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                logWi[i] = d[i]
                    - clonedSystem.get(0).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if (clonedSystem.get(0).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
              useaccsubst = true;
            }
            if (iter > 2 && err > errOld) {
              useaccsubst = false;
            }
          }
        } else {
          SimpleMatrix f = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(), 1);
          SimpleMatrix df = null;
          SimpleMatrix identitytimesConst = null;
          // if (!secondOrderStabilityAnalysis) {
          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          clonedSystem.get(0).init(3, 1);
          alpha = new double[clonedSystem.get(0).getPhases()[0].getNumberOfComponents()];
          df = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(),
              system.getPhases()[0].getNumberOfComponents());
          identitytimesConst = SimpleMatrix.identity(system.getPhases()[0].getNumberOfComponents());
          // ,
          // system.getPhases()[0].getNumberOfComponents());
          // secondOrderStabilityAnalysis = true;
          // }

          for (int i = 0; i < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + clonedSystem.get(0).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
            }
            for (int k = 0; k < clonedSystem.get(0).getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * clonedSystem.get(0).getPhases()[1].getComponent(i).getdfugdn(k));
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              } else {
                df.set(i, k, 0);
                // * clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              }
            }
          }

          // f.print(10, 10);
          // df.print(10, 10);
          SimpleMatrix dx = null;
          try {
            // Check if the determinant is close to zero
            double determinant = df.determinant();
            if (Math.abs(determinant) < 1e-10) {
              logger.warn("Matrix is nearly singular. Determinant: " + determinant);
              // Add a small regularization term to stabilize the solution
              dx = df.plus(identitytimesConst.scale(1e-6)).solve(f).negative();
            } else {
              dx = df.plus(identitytimesConst).solve(f).negative();
            }
          } catch (Exception e) {
            logger.error("Error solving matrix equation: " + e.getMessage());
            logger.debug("Attempting fallback with scaled regularization...");
            try {
              // Fallback: Add a larger regularization term and retry
              dx = df.plus(identitytimesConst.scale(0.5)).solve(f).negative();
            } catch (Exception ex) {
              logger.error("Fallback matrix solve failed: " + ex.getMessage());
              logger.debug("Attempting pseudo-inverse fallback...");
              try {
                DMatrixRMaj pinv = new DMatrixRMaj(df.numCols(), df.numRows());
                CommonOps_DDRM.pinv(df.getDDRM(), pinv);
                DMatrixRMaj result = new DMatrixRMaj(df.numCols(), 1);
                CommonOps_DDRM.mult(pinv, f.getDDRM(), result);
                dx = SimpleMatrix.wrap(result).negative();
                logger.warn("Used pseudo-inverse matrix solve.");
              } catch (Exception ex2) {
                logger.error("Pseudo-inverse fallback failed: " + ex2.getMessage());
                logger.warn("Setting dx to zero matrix as a last resort.");
                dx = new SimpleMatrix(f.numRows(), f.numCols());
              }
            }
          }

          // dx.print(10, 10);

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            double alphaNew = alpha[i] + dx.get(i, 0);
            Wi[j][i] = Math.pow(alphaNew / 2.0, 2.0);
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              logWi[i] = Math.log(Wi[j][i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -1000.0;
            }
            err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
          }

          // logger.info("err newton " + err);
        }
        // logger.info("err: " + err);
        sumw[j] = 0;

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          sumw[j] += safeExp(logWi[i]);
        }

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(safeExp(logWi[i]) / sumw[j]);
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            clonedSystem.get(0).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
      } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < maxiter);
      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= safeExp(logWi[i]);
        }
        x[j][i] = clonedSystem.get(0).getPhase(1).getComponent(i).getx();
        // logger.info("txji: " + x[j][i]);

        xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
      }
      if (iter >= maxiter - 1) {
        // logger.info("iter > maxiter multiphase stability ");
        // logger.info("error " + Math.abs(err));
        // logger.info("tm: " + tm[j]);
      }

      if (Math.abs(xTrivialCheck0) < 1e-4 || Math.abs(xTrivialCheck1) < 1e-4) {
        tm[j] = 10.0;
      }

      if (tm[j] < -1e-8) {
        break;
      }
    }

    int unstabcomp = 0;
    for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
      if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
        system.addPhase();
        unstabcomp = k;
        for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
          system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
        }
        system.getPhases()[system.getNumberOfPhases() - 1].normalize();
        multiPhaseTest = true;
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(0).getComponent(unstabcomp).getz());
        system.init(1);
        system.normalizeBeta();

        // logger.info("STABILITY ANALYSIS: ");
        // logger.info("tm1: " + k + " "+ tm[k]);
        // system.display();
        return;
      }
    }

    system.normalizeBeta();
    // logger.info("STABILITY ANALYSIS: ");
    // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);
    // system.display();
  }

  /**
   * <p>
   * stabilityAnalysis2.
   * </p>
   */
  public void stabilityAnalysis2() {
    double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];

    double[] deltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldDeltalogWi = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] sumw = new double[system.getPhase(0).getNumberOfComponents()];
    double err = 0;
    double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
    double[] oldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] oldoldoldlogw = new double[system.getPhases()[0].getNumberOfComponents()];
    double[] d = new double[system.getPhase(0).getNumberOfComponents()];
    double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
        .getNumberOfComponents()];
    tm = new double[system.getPhase(0).getNumberOfComponents()];

    double[] alpha = null;
    // SystemInterface minimumGibbsEnergySystem;
    ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);
    // if (minimumGibbsEnergySystem == null) {
    // minimumGibbsEnergySystem = system.clone();
    // }
    minimumGibbsEnergySystem = system;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getx() < 1e-100) {
        clonedSystem.add(null);
        continue;
      }
      double numb = 0;
      clonedSystem.add(system.clone());
      // (clonedSystem.get(i)).init(0); commented out sept 2005, Even
      // S.
      for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
        numb = i == j ? 1.0 : 1.0e-12;
        if (system.getPhase(0).getComponent(j).getz() < 1e-100) {
          numb = 0;
        }
        (clonedSystem.get(i)).getPhase(1).getComponent(j).setx(numb);
      }
      if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
        (clonedSystem.get(i)).init(1);
      }
    }

    lowestGibbsEnergyPhase = 0;

    // logger.info("low gibbs phase " + lowestGibbsEnergyPhase);
    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
        if (!((clonedSystem.get(k)) == null)) {
          sumw[k] += (clonedSystem.get(k)).getPhase(1).getComponent(i).getx();
        }
      }
    }

    for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
      for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
        if (!((clonedSystem.get(k)) == null)
            && system.getPhase(0).getComponent(k).getx() > 1e-100) {
          (clonedSystem.get(k)).getPhase(1).getComponent(i)
              .setx((clonedSystem.get(k)).getPhase(1).getComponent(i).getx() / sumw[k]);
        }
        // logger.info("x: " + (
        // clonedSystem.get(k)).getPhase(0).getComponent(i).getx());
      }
      if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
            + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
        // if(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponent(k).getIonicCharge()!=0)
        // d[k]=0;
      }
      // logger.info("dk: " + d[k]);
    }

    for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
      if (system.getPhase(0).getComponent(j).getz() > 1e-100) {
        logWi[j] = 1.0;
      } else {
        logWi[j] = -10000.0;
      }
    }

    int hydrocarbonTestCompNumb = 0;
    int lightTestCompNumb = 0;
    double Mmax = 0;
    double Mmin = 1e10;
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) > Mmax) {
          Mmax = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
        if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) < Mmin) {
          Mmin = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
        }
      }
    }
    for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmax) < 1e-5) {
          hydrocarbonTestCompNumb = i;
          // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
        }
      }

      if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()
          && minimumGibbsEnergySystem.getPhase(0).getComponent(i).getz() > 1e-50) {
        if (Math.abs(
            (minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) - Mmin) < 1e-5) {
          lightTestCompNumb = i;
          // logger.info("CHECKING light component " + lightTestCompNumb);
        }
      }
    }

    for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
      if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
          || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
              && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)) {
        continue;
      }
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).getName().equals("water")
      // && minimumGibbsEnergySystem.isChemicalSystem()) continue;
      // logger.info("STAB CHECK COMP " +
      // system.getPhase(0).getComponent(j).getComponentName());
      // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
      int iter = 0;
      double errOld = 1.0e100;
      do {
        errOld = err;
        iter++;
        err = 0;

        if (iter <= 150 || !system.isImplementedCompositionDeriativesofFugacity()) {
          if (iter % 7 == 0) {
            double vec1 = 0.0;

            double vec2 = 0.0;
            double prod1 = 0.0;
            double prod2 = 0.0;
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              vec1 = oldDeltalogWi[i] * oldoldDeltalogWi[i];
              vec2 = Math.pow(oldoldDeltalogWi[i], 2.0);
              prod1 += vec1 * vec2;
              prod2 += vec2 * vec2;
            }

            double lambda = prod1 / prod2;
            // logger.info("lambda " + lambda);
            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              logWi[i] += lambda / (1.0 - lambda) * deltalogWi[i];
              err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          } else {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              oldoldoldlogw[i] = oldoldlogw[i];
              oldoldlogw[i] = oldlogw[i];
              oldlogw[i] = logWi[i];
              oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
              oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
            }
            (clonedSystem.get(j)).init(1, 1);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              // oldlogw[i] = logWi[i];
              if (!Double.isInfinite(
                  (clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient())
                  && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                logWi[i] = d[i]
                    - (clonedSystem.get(j)).getPhase(1).getComponent(i).getLogFugacityCoefficient();
                if ((clonedSystem.get(j)).getPhase(1).getComponent(i).getIonicCharge() != 0) {
                  logWi[i] = -1000.0;
                }
              }
              deltalogWi[i] = logWi[i] - oldlogw[i];
              err += Math.abs(logWi[i] - oldlogw[i]);
              Wi[j][i] = safeExp(logWi[i]);
            }
          }
        } else {
          SimpleMatrix f = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(), 1);
          SimpleMatrix df = null;
          SimpleMatrix identitytimesConst = null;
          // if (!secondOrderStabilityAnalysis) {
          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            oldoldoldlogw[i] = oldoldlogw[i];
            oldoldlogw[i] = oldlogw[i];
            oldlogw[i] = logWi[i];
            oldoldDeltalogWi[i] = oldoldlogw[i] - oldoldoldlogw[i];
            oldDeltalogWi[i] = oldlogw[i] - oldoldlogw[i];
          }
          (clonedSystem.get(j)).init(3, 1);
          alpha = new double[(clonedSystem.get(j)).getPhases()[0].getNumberOfComponents()];
          df = new SimpleMatrix(system.getPhases()[0].getNumberOfComponents(),
              system.getPhases()[0].getNumberOfComponents());
          identitytimesConst = SimpleMatrix.identity(system.getPhases()[0].getNumberOfComponents());
          // , system.getPhases()[0].getNumberOfComponents());
          // secondOrderStabilityAnalysis = true;
          // }

          for (int i = 0; i < (clonedSystem.get(j)).getPhases()[0].getNumberOfComponents(); i++) {
            alpha[i] = 2.0 * Math.sqrt(Wi[j][i]);
          }

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              f.set(i, 0, Math.sqrt(Wi[j][i]) * (Math.log(Wi[j][i])
                  + (clonedSystem.get(j)).getPhases()[1].getComponent(i).getLogFugacityCoefficient()
                  - d[i]));
            }
            for (int k = 0; k < (clonedSystem.get(j)).getPhases()[0].getNumberOfComponents(); k++) {
              double kronDelt = (i == k) ? 1.0 : 0.0;
              if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                df.set(i, k, kronDelt + Math.sqrt(Wi[j][k] * Wi[j][i])
                    * (clonedSystem.get(j)).getPhases()[1].getComponent(i).getdfugdn(k));
                // *
                // clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              } else {
                df.set(i, k, 0);
                // *
                // clonedSystem.getPhases()[j].getNumberOfMolesInPhase());
              }
            }
          }
          // f.print(10, 10);
          // df.print(10, 10);
          SimpleMatrix dx = null;
          try {
            // Check if the determinant is close to zero
            double determinant = df.determinant();
            if (Math.abs(determinant) < 1e-10) {
              logger.warn("Matrix is nearly singular. Determinant: " + determinant);
              // Add a small regularization term to stabilize the solution
              dx = df.plus(identitytimesConst.scale(1e-6)).solve(f).negative();
            } else {
              dx = df.plus(identitytimesConst).solve(f).negative();
            }
          } catch (Exception e) {
            logger.error("Error solving matrix equation: " + e.getMessage());
            logger.debug("Attempting fallback with scaled regularization...");
            try {
              // Fallback: Add a larger regularization term and retry
              dx = df.plus(identitytimesConst.scale(0.5)).solve(f).negative();
            } catch (Exception ex) {
              logger.error("Fallback matrix solve failed: " + ex.getMessage());
              throw new RuntimeException("Matrix solve failed after fallback attempts", ex);
            }
          }

          // dx.print(10, 10);

          for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            double alphaNew = alpha[i] + dx.get(i, 0);
            Wi[j][i] = Math.pow(alphaNew / 2.0, 2.0);
            if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
              logWi[i] = Math.log(Wi[j][i]);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                || system.getPhase(0).getComponent(i).isIsIon()) {
              logWi[i] = -1000.0;
            }
            err += Math.abs((logWi[i] - oldlogw[i]) / oldlogw[i]);
          }

          // logger.info("err newton " + err);
        }
        // logger.info("err: " + err);
        sumw[j] = 0;

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          sumw[j] += safeExp(logWi[i]);
        }

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
          if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
            (clonedSystem.get(j)).getPhase(1).getComponent(i).setx(safeExp(logWi[i]) / sumw[j]);
          }
          if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
              || system.getPhase(0).getComponent(i).isIsIon()) {
            (clonedSystem.get(j)).getPhase(1).getComponent(i).setx(1e-50);
          }
        }
      } while ((Math.abs(err) > 1e-9 || err > errOld) && iter < 200);
      if (iter > 198) {
        // System.out.println("too many iterations....." + err + " temperature "
        // + system.getTemperature("C") + " C " + system.getPressure("bara") + " bara");
        throw new RuntimeException(
            new neqsim.util.exception.TooManyIterationsException(this, "stabilityAnalysis2", 200));
      }
      // logger.info("err: " + err + " ITER " + iter);
      double xTrivialCheck0 = 0.0;
      double xTrivialCheck1 = 0.0;

      tm[j] = 1.0;

      for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getx() > 1e-100) {
          tm[j] -= safeExp(logWi[i]);
        }
        x[j][i] = (clonedSystem.get(j)).getPhase(1).getComponent(i).getx();
        // logger.info("txji: " + x[j][i]);

        xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
        xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
      }
      if (iter >= 199) {
        logger.info("iter > maxiter multiphase stability ");
        logger.info("error " + Math.abs(err));
        logger.info("tm: " + tm[j]);
      }

      if (Math.abs(xTrivialCheck0) < 1e-6 || Math.abs(xTrivialCheck1) < 1e-6) {
        tm[j] = 10.0;
      }

      if (tm[j] < -1e-8) {
        break;
      }
    }

    int unstabcomp = 0;
    for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
      if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
        system.addPhase();
        unstabcomp = k;
        for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
          system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
        }
        system.getPhases()[system.getNumberOfPhases() - 1].normalize();
        multiPhaseTest = true;
        system.setBeta(system.getNumberOfPhases() - 1,
            system.getPhase(0).getComponent(unstabcomp).getz());
        system.init(1);
        system.normalizeBeta();

        // logger.info("STABILITY ANALYSIS: ");
        // logger.info("tm1: " + k + " "+ tm[k]);
        // system.display();
        return;
      }
    }
    system.normalizeBeta();
    // logger.info("STABILITY ANALYSIS: ");
    // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);
    // system.display();
  }

  private boolean seedAdditionalPhaseFromFeed() {
    if (!system.doMultiPhaseCheck()) {
      return false;
    }
    if (system.getNumberOfPhases() >= 3) {
      return false;
    }
    boolean hasAqueous = false;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseType type = system.getPhase(phase).getType();
      if (type == PhaseType.GAS && system.getPhase(phase).getBeta() > 1.0e-6) {
        return false;
      }
      if (type == PhaseType.AQUEOUS) {
        hasAqueous = true;
      }
    }
    if (!hasAqueous) {
      return false;
    }
    double waterZ = 0.0;
    try {
      waterZ = system.getComponent("water").getz();
    } catch (Exception ex) {
      for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
        if ("water".equals(system.getPhase(0).getComponent(comp).getComponentName())) {
          waterZ = system.getPhase(0).getComponent(comp).getz();
          break;
        }
      }
    }
    if (waterZ < 1.0e-4) {
      return false;
    }
    boolean hasHydrocarbon = false;
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      if (system.getPhase(0).getComponent(comp).isHydrocarbon()
          && system.getPhase(0).getComponent(comp).getz() > 1.0e-4) {
        hasHydrocarbon = true;
        break;
      }
    }
    if (!hasHydrocarbon) {
      return false;
    }
    system.addPhase();
    int phaseIndex = system.getNumberOfPhases() - 1;
    system.setPhaseType(phaseIndex, PhaseType.GAS);
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      double z = system.getPhase(0).getComponent(comp).getz();
      system.getPhase(phaseIndex).getComponent(comp).setx(z > 0 ? z : 1.0e-16);
    }
    system.getPhases()[phaseIndex].normalize();
    double initialBeta = Math.max(1.0e-3, 1000.0 * phaseFractionMinimumLimit);
    system.setBeta(phaseIndex, initialBeta);
    system.normalizeBeta();
    system.init(1);
    return true;
  }

  /**
   * Ensures only one aqueous phase exists in the system. The aqueous phase is the one with the
   * highest aqueous component content (water, MEG, TEG, DEG, methanol, ethanol, and ions). Other
   * liquid phases are reclassified as OIL by moving their aqueous components (water, glycols, ions)
   * to the true aqueous phase and keeping hydrocarbons in the oil phase. This method applies to
   * systems with ions (where ions must be confined to the aqueous phase) or chemical systems.
   */
  private void ensureSingleAqueousPhase() {
    // Only needed for systems with ions or chemical systems - skip for simple molecular systems
    if ((!system.isChemicalSystem() && !system.hasIons()) || system.getNumberOfPhases() < 2) {
      return;
    }

    // Count how many non-gas phases are classified as AQUEOUS
    int aqueousCount = 0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == PhaseType.AQUEOUS) {
        aqueousCount++;
      }
    }

    if (aqueousCount <= 1) {
      return; // Already have at most one aqueous phase
    }

    // Find the phase with highest aqueous component content - this will be the true aqueous phase
    int bestAqueousPhase = -1;
    double maxAqueousContent = 0.0;

    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (system.getPhase(phase).getType() == PhaseType.GAS) {
        continue;
      }

      double aqueousContent = 0.0;
      for (int comp = 0; comp < system.getPhase(phase).getNumberOfComponents(); comp++) {
        ComponentInterface component = system.getPhase(phase).getComponent(comp);
        String name = component.getComponentName().toLowerCase();
        // Count water, glycols, alcohols, and ions as aqueous components
        if (name.equals("water") || name.equals("meg") || name.equals("teg") || name.equals("deg")
            || name.equals("methanol") || name.equals("ethanol") || component.getIonicCharge() != 0
            || component.isIsIon()) {
          aqueousContent += component.getx();
        }
      }

      if (aqueousContent > maxAqueousContent) {
        maxAqueousContent = aqueousContent;
        bestAqueousPhase = phase;
      }
    }

    if (bestAqueousPhase < 0) {
      return;
    }

    // For phases that are AQUEOUS but not the best aqueous phase:
    // Move hydrocarbons to dominate, set aqueous components and ions to trace
    // This will cause init() to reclassify them as OIL
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (phase == bestAqueousPhase || system.getPhase(phase).getType() == PhaseType.GAS) {
        continue;
      }

      if (system.getPhase(phase).getType() == PhaseType.AQUEOUS) {
        // This phase should become OIL - adjust compositions
        // Set ions and most aqueous components to trace amounts
        for (int comp = 0; comp < system.getPhase(phase).getNumberOfComponents(); comp++) {
          ComponentInterface component = system.getPhase(phase).getComponent(comp);
          String name = component.getComponentName().toLowerCase();

          if (component.getIonicCharge() != 0 || component.isIsIon()) {
            // Ions only in aqueous phase
            component.setx(1e-50);
          } else if (name.equals("water")) {
            // Reduce water significantly but keep trace for solubility
            component.setx(Math.min(component.getx() * 0.01, 1e-4));
          } else if (name.equals("meg") || name.equals("teg") || name.equals("deg")
              || name.equals("methanol") || name.equals("ethanol")) {
            // Reduce glycols/alcohols
            component.setx(Math.min(component.getx() * 0.1, 1e-3));
          }
          // Hydrocarbons keep their current x values
        }
        system.getPhase(phase).normalize();
      }
    }

    // Reinitialize - phase types will be recalculated based on new compositions
    system.init(1);
  }

  private boolean seedHydrocarbonLiquidFromFeed() {
    if (!system.doMultiPhaseCheck()) {
      return false;
    }
    if (system.getNumberOfPhases() >= 3 || system.hasPhaseType(PhaseType.OIL)
        || !system.hasPhaseType(PhaseType.AQUEOUS)) {
      return false;
    }

    double waterZ = 0.0;
    try {
      waterZ = system.getComponent("water").getz();
    } catch (Exception ex) {
      for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
        if ("water".equals(system.getPhase(0).getComponent(comp).getComponentName())) {
          waterZ = system.getPhase(0).getComponent(comp).getz();
          break;
        }
      }
    }

    if (waterZ < 1.0e-6) {
      return false;
    }

    double heavyHydrocarbonTotal = 0.0;
    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      ComponentInterface component = system.getPhase(0).getComponent(comp);
      if (component.isHydrocarbon() && component.getz() > 1.0e-6
          && component.getMolarMass() > 0.045) {
        heavyHydrocarbonTotal += component.getz();
      }
    }
    // Seed oil phase if there's significant heavy hydrocarbon content
    // For electrolyte/chemical systems, allow seeding even when water > hydrocarbons
    // because oil-water separation is physically expected
    boolean shouldSeedOil = heavyHydrocarbonTotal >= 5.0e-3;
    if (!system.isChemicalSystem()) {
      // For non-chemical systems, also require hydrocarbons > water
      shouldSeedOil = shouldSeedOil && heavyHydrocarbonTotal > waterZ;
    }
    if (!shouldSeedOil) {
      return false;
    }

    system.addPhase();;
    int phaseIndex = system.getNumberOfPhases() - 1;
    system.setPhaseType(phaseIndex, PhaseType.OIL);

    for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
      ComponentInterface component = system.getPhase(0).getComponent(comp);
      double z = component.getz();
      double x = 1.0e-16;
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        x = 1.0e-16;
      } else if (component.isHydrocarbon()) {
        if (component.getMolarMass() > 0.045) {
          x = Math.max(z, 1.0e-12);
        } else {
          x = Math.min(z * 1.0e-2, 1.0e-8);
        }
      } else if ("water".equalsIgnoreCase(component.getComponentName())) {
        x = Math.min(z * 1.0e-2, 1.0e-8);
      }
      system.getPhase(phaseIndex).getComponent(comp).setx(x);
    }

    system.getPhases()[phaseIndex].normalize();
    double initialBeta = Math.max(1.0e-5, 10.0 * phaseFractionMinimumLimit);
    system.setBeta(phaseIndex, initialBeta);
    system.normalizeBeta();
    system.init(1);
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int aqueousPhaseNumber = 0;
    // logger.info("Starting multiphase-flash....");

    // For systems with ions, temporarily remove ions before stability analysis
    // This allows proper oil-water-gas phase separation without ion interference
    // Ions will be restored to aqueous phase(s) after stability analysis
    // Note: This must be done for ANY system with ions, not just chemical reaction systems
    double[] ionicZ = null;
    boolean hasIons = system.hasIons();

    // Store ion compositions and temporarily remove them for stability analysis
    if (hasIons) {
      ionicZ = new double[system.getPhase(0).getNumberOfComponents()];
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
            || system.getPhase(0).getComponent(i).isIsIon()) {
          ionicZ[i] = system.getPhase(0).getComponent(i).getz();
          // Temporarily set ion z to near-zero for stability analysis
          for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
            system.getPhase(phase).getComponent(i).setz(1e-100);
          }
        }
      }
      system.init(1);
    }

    // system.setNumberOfPhases(system.getNumberOfPhases()+1);
    if (doStabilityAnalysis) {
      stabilityAnalysis();
      // If enhanced stability check is enabled and standard analysis didn't find additional
      // phases, try enhanced version which uses Wilson K-value initial guesses and tests both
      // vapor-like and liquid-like trial phases for more robust detection of liquid-liquid
      // equilibria (e.g., sour gas, CO2 systems)
      if (system.doEnhancedMultiPhaseCheck() && !multiPhaseTest && system.getNumberOfPhases() < 3) {
        stabilityAnalysisEnhanced();
      }
    }
    if (!multiPhaseTest && seedAdditionalPhaseFromFeed()) {
      multiPhaseTest = true;
      doStabilityAnalysis = false;
    }
    if (seedHydrocarbonLiquidFromFeed()) {
      multiPhaseTest = true;
      doStabilityAnalysis = false;
    }
    // system.orderByDensity();
    doStabilityAnalysis = true;

    // Debug: Check phases after stability analysis (before ion restoration)
    if (hasIons) {
      logger.debug("After stability analysis (ions removed): {} phases",
          system.getNumberOfPhases());
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        logger.debug("  Phase {} type: {}", phase, system.getPhase(phase).getType());
      }
    }

    // Restore ions to aqueous phase(s) after stability analysis
    if (hasIons && ionicZ != null) {
      aqueousPhaseNumber =
          system.hasPhaseType(PhaseType.AQUEOUS) ? system.getPhaseNumberOfPhase("aqueous") : -1;
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if ((system.getPhase(0).getComponent(i).getIonicCharge() != 0
            || system.getPhase(0).getComponent(i).isIsIon()) && ionicZ[i] > 1e-100) {
          // Restore z values
          for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
            system.getPhase(phase).getComponent(i).setz(ionicZ[i]);
            // Set ions only in aqueous phase, near-zero in others
            if (system.getPhase(phase).getType() == PhaseType.AQUEOUS) {
              system.getPhase(phase).getComponent(i).setx(ionicZ[i]);
            } else {
              system.getPhase(phase).getComponent(i).setx(1e-50);
            }
          }
        }
      }
      // Normalize aqueous phase and reinitialize
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        system.getPhase(phase).normalize();
      }
      system.init(1);
    }

    // system.init(1);
    // system.display();
    aqueousPhaseNumber =
        system.hasPhaseType(PhaseType.AQUEOUS) ? system.getPhaseNumberOfPhase("aqueous") : -1;
    if (system.isChemicalSystem() && aqueousPhaseNumber >= 0) {
      system.getChemicalReactionOperations().solveChemEq(aqueousPhaseNumber, 0);
      system.getChemicalReactionOperations().solveChemEq(aqueousPhaseNumber, 1);
    }

    int iterations = 0;
    if (multiPhaseTest) { // && !system.isChemicalSystem()) {
      double diff = 1.0e10;

      double oldDiff = 1.0e10;
      double chemdev = 0;
      int iterOut = 0;
      double maxerr = 1e-12;

      do {
        iterOut++;
        if (system.isChemicalSystem() && system.hasPhaseType(PhaseType.AQUEOUS)) {
          int currentAqueousPhase = system.getPhaseNumberOfPhase("aqueous");
          if (currentAqueousPhase != aqueousPhaseNumber) {
            aqueousPhaseNumber = currentAqueousPhase;
            system.getChemicalReactionOperations().solveChemEq(aqueousPhaseNumber, 0);
          }

          if (aqueousPhaseNumber >= 0 && aqueousPhaseNumber < system.getNumberOfPhases()) {
            chemdev = 0.0;
            double[] xchem =
                new double[system.getPhase(aqueousPhaseNumber).getNumberOfComponents()];

            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              xchem[i] = system.getPhase(aqueousPhaseNumber).getComponent(i).getx();
            }

            system.init(1);
            system.getChemicalReactionOperations().solveChemEq(aqueousPhaseNumber, 1);

            for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
              chemdev +=
                  Math.abs(xchem[i] - system.getPhase(aqueousPhaseNumber).getComponent(i).getx());
            }
          }
        }
        setDoubleArrays();
        iterations = 0;
        do {
          iterations++;
          // oldBeta = system.getBeta(system.getNumberOfPhases() - 1);
          // system.init(1);
          oldDiff = diff;
          diff = this.solveBeta();
          // diff = Math.abs((system.getBeta(system.getNumberOfPhases() - 1) - oldBeta) /
          // oldBeta);
          // System.out.println("diff multiphase " + diff);
          if (iterations % 50 == 0) {
            maxerr *= 100.0;
          }
        } while (diff > maxerr && !removePhase && (diff < oldDiff || iterations < 50)
            && iterations < 200);
        // this.solveBeta(true);
        if (iterations >= 199) {
          logger.error("error in multiphase flash..did not solve in 200 iterations");
          logger.error("diff " + diff + " temperaure " + system.getTemperature("C") + " pressure "
              + system.getPressure("bara"));
          diff = this.solveBeta();
        }
      } while ((Math.abs(chemdev) > 1e-10 && iterOut < 100)
          || (iterOut < 3 && system.isChemicalSystem() && system.hasPhaseType(PhaseType.AQUEOUS)));

      // After flash converges, check for additional phases (three-phase detection)
      // This is particularly important for systems like CO2/H2S/hydrocarbon mixtures
      // that may exhibit vapor-liquid-liquid equilibrium
      if (system.doMultiPhaseCheck() && system.getNumberOfPhases() >= 2
          && system.getNumberOfPhases() < 3 && !postFlashStabilityChecked) {
        postFlashStabilityChecked = true;
        int oldNumPhases = system.getNumberOfPhases();
        stabilityAnalysisEnhanced();
        if (system.getNumberOfPhases() > oldNumPhases) {
          // Found a third phase - re-run the flash calculation
          multiPhaseTest = true;
          doStabilityAnalysis = false;
          run();
        }
      }

      // Check if water is present and if an aqueous phase should be seeded
      // Only try to seed aqueous phase once per flash operation (not on recursive calls)
      if (system.hasComponent("water") && !aqueousPhaseSeedAttempted && system.doMultiPhaseCheck()
          && !system.hasPhaseType(PhaseType.AQUEOUS)) {
        aqueousPhaseSeedAttempted = true;
        double waterZ = 0.0;
        int waterComponentIndex = -1;
        try {
          waterZ = system.getComponent("water").getz();
          waterComponentIndex = system.getComponent("water").getComponentNumber();
        } catch (Exception ex) {
          for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
            if ("water".equals(system.getPhase(0).getComponent(comp).getComponentName())) {
              waterZ = system.getPhase(0).getComponent(comp).getz();
              waterComponentIndex = comp;
              break;
            }
          }
        }

        // If water content is significant (> 1e-6), seed an aqueous phase.
        // Limit total active phases to a maximum of 3 (e.g. gas, liquid, aqueous) to avoid
        // indexing beyond what downstream algorithms expect. Do not create a new aqueous
        // phase if one already exists.
        if (waterZ > 1.0e-6 && waterComponentIndex >= 0 && system.getNumberOfPhases() < 3
            && !system.hasPhaseType(PhaseType.AQUEOUS)) {
          system.addPhase();
          int aquPhaseIndex = system.getNumberOfPhases() - 1;
          system.setPhaseType(aquPhaseIndex, PhaseType.AQUEOUS);

          // Initialize aqueous phase with water and trace amounts of other components
          for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
            double x = 1.0e-16;
            if (comp == waterComponentIndex) {
              // Concentrate water in aqueous phase
              x = Math.max(waterZ, 1.0e-12);
            } else if (!system.getPhase(0).getComponent(comp).isHydrocarbon()
                && !system.getPhase(0).getComponent(comp).isInert()) {
              // Other aqueous components get trace amounts
              x = Math.min(system.getPhase(0).getComponent(comp).getz() * 1.0e-2, 1.0e-8);
            }
            system.getPhase(aquPhaseIndex).getComponent(comp).setx(x);
          }

          system.getPhases()[aquPhaseIndex].normalize();
          double initialBeta = Math.max(1.0e-5, 10.0 * phaseFractionMinimumLimit);
          system.setBeta(aquPhaseIndex, initialBeta);
          system.normalizeBeta();
          system.init(1);
          multiPhaseTest = true;
          doStabilityAnalysis = false;
        }
      }

      // For electrolyte systems: ensure only one aqueous phase - the one with most aqueous content
      // Other phases classified as AQUEOUS should be reclassified as OIL with ions removed
      // Also applies to systems with ions even without chemical reactions
      ensureSingleAqueousPhase();

      boolean hasRemovedPhase = false;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        if (system.getBeta(i) < 1.1 * phaseFractionMinimumLimit) {
          system.removePhaseKeepTotalComposition(i);
          doStabilityAnalysis = false;
          hasRemovedPhase = true;
        }
      }

      boolean trivialSolution = false;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        for (int j = i + 1; j < system.getNumberOfPhases(); j++) {
          if (Math
              .abs(system.getPhase(i).getDensity() - system.getPhase(j).getDensity()) < 1.1e-5) {
            trivialSolution = true;
            break;
          }
        }
        if (trivialSolution) {
          break;
        }
      }

      if (trivialSolution && !hasRemovedPhase) {
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
          for (int j = i + 1; j < system.getNumberOfPhases(); j++) {
            if (Math
                .abs(system.getPhase(i).getDensity() - system.getPhase(j).getDensity()) < 1.1e-5) {
              system.removePhaseKeepTotalComposition(j);
              doStabilityAnalysis = false;
              hasRemovedPhase = true;
            }
          }
        }
      }

      /*
       * for (int i = 0; i < system.getNumberOfPhases()-1; i++) { if
       * (Math.abs(system.getPhase(i).getDensity()-system.getPhase(i+1).getDensity())< 1e-6 &&
       * !hasRemovedPhase) { system.removePhase(i+1); doStabilityAnalysis=false; hasRemovedPhase =
       * true; } }
       */
      if (hasRemovedPhase && !secondTime) {
        secondTime = true;
        stabilityAnalysis3();
        run();
      }

      /*
       * if (!secondTime) { secondTime = true; doStabilityAnalysis = false; run(); }
       */
    }
  }
}
