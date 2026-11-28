package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.mathlib.linearalgebra.LinearAlgebraOperations;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Temperature-pressure flash with fluid phases and one pure solid phase.
 *
 * <p>
 * The component solid-check flags select the allowed solids. Simultaneous precipitation of multiple selected components
 * is unsupported because the equilibrium equations and phase storage represent one pure solid.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SolidFlash1 extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SolidFlash1.class);
  private static final LinearAlgebraOperations ALGEBRA = new EjmlLinearAlgebra();
  private static final double SINGLE_FLUID_FUGACITY_TOLERANCE = 1.0e-10;

  // SystemInterface clonedSystem;
  boolean multiPhaseTest = false;
  double[] dQdbeta;
  double[][] Qmatrix;
  double[] E;
  double Q = 0;
  int solidsNumber = 0;
  int solidIndex = 0;
  double totalSolidFrac = 0.0;
  int[] FluidPhaseActiveDescriptors; // 1 = active; 0 = inactive

  /**
   * Constructor for SolidFlash1.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SolidFlash1(SystemInterface system) {
    super(system);
  }

  /**
   * calcMultiPhaseBeta.
   */
  public void calcMultiPhaseBeta() {
  }

  /**
   * setXY.
   */
  public void setXY() {
    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i != solidIndex) {
          system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz() / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient());
        } else {
          system.getPhase(k).getComponent(i)
              .setx(system.getPhase(PhaseType.SOLID).getComponent(i).getFugacityCoefficient()
                  / system.getPhase(k).getComponent(i).getFugacityCoefficient());
        }
      }
    }
  }

  /**
   * checkX.
   */
  public void checkX() {
    for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
      double x = 0.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        x += system.getPhase(k).getComponent(i).getx();
      }
      // logger.info("x tot " + x + " PHASE " + k);
      if (x < 1.0 - 1e-6) {
        // logger.info("removing phase " + k);
        system.setBeta(system.getNumberOfPhases() - 2, system.getBeta(system.getNumberOfPhases() - 1));
        system.setBeta(0, 1.0 - system.getBeta(system.getNumberOfPhases() - 1));
        system.setNumberOfPhases(system.getNumberOfPhases() - 1);
        system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
        system.init(1);
        calcE();
        setXY();
        return;
      }
    }
  }

  /**
   * calcE.
   */
  public void calcE() {
    E = new double[system.getPhases()[0].getNumberOfComponents()];

    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      E[i] = 0.0;
      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        E[i] += system.getBeta(k) / system.getPhase(k).getComponent(i).getFugacityCoefficient();
      }
    }
  }

  /**
   * calcQ.
   *
   * @return a double
   */
  public double calcQ() {
    Q = 0;
    double betaTotal = 0;
    dQdbeta = new double[system.getNumberOfPhases() - solidsNumber];
    Qmatrix = new double[system.getNumberOfPhases() - solidsNumber][system.getNumberOfPhases() - solidsNumber];

    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      betaTotal += system.getBeta(k);
    }

    Q = betaTotal;
    for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
      if (i != solidIndex) {
        Q -= Math.log(E[i]) * system.getPhase(0).getComponent(i).getz();
      }
    }
    for (int i = 0; i < solidsNumber; i++) {
      Q += system.getPhase(0).getComponent(solidIndex).getz()
          * (1 - Math.log(system.getPhase(0).getComponent(solidIndex).getz()
              / system.getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()));
      for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
        Q -= system.getBeta(j) * system.getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()
            / system.getPhase(j).getComponent(solidIndex).getFugacityCoefficient();
      }
    }
    return Q;
  }

  /**
   * calcQbeta.
   */
  public void calcQbeta() {
    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      dQdbeta[k] = 1.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i == solidIndex) {
          dQdbeta[k] -= system.getPhase(PhaseType.SOLID).getComponents()[solidIndex].getFugacityCoefficient()
              / system.getPhase(k).getComponent(solidIndex).getFugacityCoefficient();
        } else {
          dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient();
        }
      }
    }
  }

  /**
   * calcGradientAndHesian.
   */
  public void calcGradientAndHesian() {
    Qmatrix = new double[system.getNumberOfPhases() - solidsNumber][system.getNumberOfPhases() - solidsNumber];
    dQdbeta = new double[system.getNumberOfPhases() - solidsNumber];

    for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
      dQdbeta[k] = 1.0;
      for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
        if (i == solidIndex) {
          dQdbeta[k] -= system.getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()
              / system.getPhase(k).getComponent(solidIndex).getFugacityCoefficient();
        } else {
          dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() / E[i]
              / system.getPhase(k).getComponent(i).getFugacityCoefficient();
        }
      }
    }

    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      Qmatrix[i][i] = 0;
      for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
        for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
          if (k != solidIndex) {
            Qmatrix[i][j] += system.getPhase(0).getComponent(k).getz()
                / (E[k] * E[k] * system.getPhase(j).getComponent(k).getFugacityCoefficient()
                    * system.getPhase(i).getComponent(k).getFugacityCoefficient());
          }
        }
      }
    }

    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      if (FluidPhaseActiveDescriptors[i] == 0) {
        dQdbeta[i] = 0.0;
        for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
          Qmatrix[i][j] = 0.0;
        }
        Qmatrix[i][i] = 1.0;
      }
    }
    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      if (FluidPhaseActiveDescriptors[i] == 1) {
        Qmatrix[i][i] += 10e-15;
      }
    }
  }

  /**
   * solveBeta.
   */
  public void solveBeta() {
    double[] oldBeta = new double[system.getNumberOfPhases() - solidsNumber];
    // double newBeta[] = new double[system.getNumberOfPhases() - solidsNumber];
    int iter = 0;
    double[] ans = new double[system.getNumberOfPhases() - solidsNumber];
    double stepNorm;
    double Qold = 0;
    double betaReductionFactor = 1.0;
    double Qnew = 0;
    do {
      // system.init(1);
      calcE();
      Qold = calcQ();
      calcGradientAndHesian();
      oldBeta = new double[system.getNumberOfPhases() - solidsNumber];
      // newBeta = new double[system.getNumberOfPhases() - solidsNumber];
      iter++;
      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        oldBeta[k] = system.getBeta(k);
      }

      double[] betaMatrix = oldBeta.clone();

      try {
        ans = ALGEBRA.solve(Qmatrix, dQdbeta);
      } catch (Exception ex) {
        logger.debug("Solid-fraction solve failed; retaining the previous step", ex);
      }
      // dQdBM.print(10, 10);
      // logger.info("BetaStep: ");
      // ans.print(30, 30);

      betaReductionFactor = 1.0;
      // logger.info("Oldbeta befor update");
      // betaMatrix.print(10, 10);
      double[] betaMatrixTemp = ALGEBRA.subtract(betaMatrix, ALGEBRA.scale(ans, betaReductionFactor));
      // logger.info("Beta before multiplying reduction Factoer");
      // betaMatrixTemp.print(10, 2);

      double minBetaTem = 1000000;
      int minBetaTemIndex = 0;

      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        if (betaMatrixTemp[k] * FluidPhaseActiveDescriptors[k] < minBetaTem) {
          minBetaTem = betaMatrixTemp[k];
          minBetaTemIndex = k;
        }
      }

      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        if ((minBetaTem < -1.0E-10)) {
          betaReductionFactor = 1.0 + betaMatrixTemp[minBetaTemIndex] / ans[minBetaTemIndex];
        }
      }
      // logger.info("Reduction Factor " + betaReductionFactor);
      betaMatrixTemp = ALGEBRA.subtract(betaMatrix, ALGEBRA.scale(ans, betaReductionFactor));

      // logger.info("Beta after multiplying reduction Factoer");
      // betaMatrixTemp.print(10, 2);

      double[] betaMatrixOld = betaMatrix.clone();
      betaMatrix = betaMatrixTemp.clone();
      boolean deactivatedPhase = false;
      for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
        double currBeta = betaMatrix[k];
        if (currBeta < phaseFractionMinimumLimit) {
          system.setBeta(k, phaseFractionMinimumLimit);
          // This used to be verified with abs(betamatrix.get(i,0)) < 1.0e-10
          FluidPhaseActiveDescriptors[k] = 0;
          deactivatedPhase = true;
        } else if (currBeta > (1.0 - phaseFractionMinimumLimit)) {
          system.setBeta(k, 1.0 - phaseFractionMinimumLimit);
        } else {
          system.setBeta(k, currBeta);
        }
      }

      Qnew = calcQ();

      // logger.info("Qold = " + Qold);
      // logger.info("Qnew = " + Qnew);
      if (Qnew > Qold + 1.0e-10 && !deactivatedPhase) {
        // logger.info("Qnew > Qold...............................");
        int iter2 = 0;
        do {
          iter2++;
          betaReductionFactor /= 2.0;
          betaMatrixTemp = ALGEBRA.subtract(betaMatrixOld, ALGEBRA.scale(ans, betaReductionFactor));
          betaMatrix = betaMatrixTemp;
          for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
            system.setBeta(i, betaMatrix[i]);
            if (Math.abs(system.getBeta(i)) < 1.0e-10) {
              FluidPhaseActiveDescriptors[i] = 0;
            } else {
              FluidPhaseActiveDescriptors[i] = 1;
            }
          }
          Qnew = calcQ();
        } while (Qnew > Qold + 1.0e-10 && iter2 < 5);
      }
      stepNorm = 0.0;
      for (double value : ans) {
        stepNorm += Math.abs(value);
      }
    } while ((Math.abs(stepNorm) > 1e-7 && iter < 100) || iter < 2);
  }

  /**
   * checkGibbs.
   */
  public void checkGibbs() {
    double gibbs1 = 0;
    double gibbs2 = 0;
    for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
      system.setPhaseType(i, PhaseType.LIQUID);
      system.init(1);
      gibbs1 = system.getPhase(i).getGibbsEnergy();
      system.setPhaseType(i, PhaseType.GAS);
      system.init(1);
      gibbs2 = system.getPhase(i).getGibbsEnergy();
      if (gibbs1 < gibbs2) {
        system.setPhaseType(i, PhaseType.LIQUID);
      } else {
        system.setPhaseType(i, PhaseType.GAS);
      }
      system.init(1);
    }
  }

  /**
   * calcSolidBeta.
   *
   * @return a double
   */
  public double calcSolidBeta() {
    double tempVar = system.getPhase(0).getComponents()[solidIndex].getz();
    // double beta = 1.0;
    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
      if (FluidPhaseActiveDescriptors[i] == 1) {
        tempVar -= system.getBeta(i)
            * system.getPhase(PhaseType.SOLID).getComponent(solidIndex).getFugacityCoefficient()
            / system.getPhase(i).getComponent(solidIndex).getFugacityCoefficient();
        // beta -= system.getBeta(i);
      }
    }
    if (tempVar > 0 && tempVar < 1.0) {
      system.setBeta(system.getNumberOfPhases() - 1, tempVar);
      // logger.info("Solid PhaseFraction " + tempVar);
    }
    return tempVar;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    int iter = 0;
    solidsNumber = 0;
    totalSolidFrac = 0.0;

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    if (!system.doSolidPhaseCheck()) {
      system.setSolidPhaseCheck(true);
    }
    boolean[] selectedSolids = new boolean[system.getNumberOfComponents()];
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      selectedSolids[i] = system.getPhase(0).getComponent(i).doSolidCheck();
    }
    // Nested multiphase refinements also inspect the system flag, so the operation flag alone is insufficient.
    system.setSolidPhaseCheck(false);
    try {
      ops.TPflash(false);
    } finally {
      system.setSolidPhaseCheck(true);
      // Newly activated phase buffers must inherit the same selection as the feed phase.
      for (PhaseInterface phase : system.getPhases()) {
        if (phase != null) {
          for (int i = 0; i < selectedSolids.length; i++) {
            phase.getComponent(i).setSolidCheck(selectedSolids[i]);
          }
        }
      }
    }
    // system.display();
    FluidPhaseActiveDescriptors = new int[system.getNumberOfPhases()];
    for (int i = 0; i < FluidPhaseActiveDescriptors.length; i++) {
      FluidPhaseActiveDescriptors[i] = 1;
    }

    if (checkAndAddSolidPhase() == 0) {
      system.init(1);
      return;
    }
    if (system.getPhase(0).getNumberOfComponents() == 1) {
      system.init(1);
      if (system.getPhase(0).getFugacity(0) > system.getPhase(PhaseType.SOLID).getFugacity(0)) {
        system.setPhaseIndex(0, 3);
      } else {
      }
      system.setBeta(0, 1.0);
      system.setNumberOfPhases(1);
      system.init(1);
      return;
    }
    if (system.getNumberOfPhases() - solidsNumber == 1) {
      if (removeIncipientSolidAtFluidBoundary()) {
        solidsNumber = 0;
        totalSolidFrac = 0.0;
        system.init(1);
        return;
      }
      solveSingleFluidPhase();
      system.init(1);
      return;
    }
    // if (system.getPhase(0).getNumberOfComponents() <= 2) {
    // solvebeta1();
    // }else{
    double oldBeta = 0.0;
    double beta = 0.0;
    do {
      oldBeta = beta;
      iter++;
      this.solveBeta();
      calcE();
      setXY();
      system.init(1);
      calcQbeta();

      beta = calcSolidBeta();
      /*
       * for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) { if (FluidPhaseActiveDescriptors[i] == 0)
       * { logger.info("dQdB " + i + " " + dQdbeta[i]); if (dQdbeta[i] < 0) { FluidPhaseActiveDescriptors[i] = 1; } } }
       */
    } while (Math.abs((beta - oldBeta) / beta) > 1e-6 && iter < 20);

    boolean retainedCarrierPhase = retainCarrierPhaseIfAllDeactivated();
    for (int i = system.getNumberOfPhases() - solidsNumber - 1; i >= 0; i--) {
      if (FluidPhaseActiveDescriptors[i] == 0) {
        int removedPhaseIndex = system.getPhaseIndex(i);
        system.deleteFluidPhase(i);
        // Keep the removed buffer addressable by phaseToSystem and subsequent flashes.
        system.setPhaseIndex(system.getNumberOfPhases(), removedPhaseIndex);
      }
    }
    if (retainedCarrierPhase && system.getNumberOfPhases() - solidsNumber == 1) {
      solveSingleFluidPhase();
    }
    system.init(1);
  }

  /**
   * Retains the original fluid-only state at an incipient solid boundary.
   *
   * <p>
   * A fluid-only flash can predict a positive solid amount from roundoff at saturation, for example after complete
   * capture of precipitated sulfur. A solid below the phase-fraction resolution is unnecessary only if the original
   * fluid root also satisfies the existing fugacity tolerance. Do not apply this test to a retained trial root after
   * the multiphase beta solve.
   * </p>
   *
   * @return true if an incipient solid was removed without changing the overall inventory
   */
  private boolean removeIncipientSolidAtFluidBoundary() {
    if (system.getBeta(1) > phaseFractionMinimumLimit) {
      return false;
    }
    PhaseInterface fluid = system.getPhase(0);
    PhaseInterface solid = system.getPhase(1);
    double feedFraction = fluid.getComponent(solidIndex).getz();
    double solidCoefficient = solid.getComponent(solidIndex).fugcoef(solid);
    double residual = Math
        .log(feedFraction * fluid.getComponent(solidIndex).getFugacityCoefficient() / solidCoefficient);
    if (!Double.isFinite(residual) || Math.abs(residual) > SINGLE_FLUID_FUGACITY_TOLERANCE) {
      return false;
    }
    system.setBeta(1, 0.0);
    system.removePhaseKeepTotalComposition(1);
    system.setBeta(0, 1.0);
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      fluid.getComponent(component).setx(fluid.getComponent(component).getz());
    }
    return true;
  }

  /**
   * Retains one fluid storage root when the general beta solve deactivates every carrier phase.
   *
   * <p>
   * The retained root is the one with the largest trial beta. The one-fluid bracketed solve then replaces its trial
   * composition and beta with the component-conservative equilibrium solution.
   * </p>
   *
   * @return true when a carrier phase had to be retained
   */
  private boolean retainCarrierPhaseIfAllDeactivated() {
    int retainedPhase = -1;
    double largestBeta = Double.NEGATIVE_INFINITY;
    for (int phase = 0; phase < system.getNumberOfPhases() - solidsNumber; phase++) {
      if (FluidPhaseActiveDescriptors[phase] == 1) {
        return false;
      }
      if (system.getBeta(phase) > largestBeta) {
        largestBeta = system.getBeta(phase);
        retainedPhase = phase;
      }
    }
    if (retainedPhase < 0) {
      throw new IllegalStateException("Solid equilibrium has no fluid carrier phase");
    }
    FluidPhaseActiveDescriptors[retainedPhase] = 1;
    return true;
  }

  /**
   * Solves one fluid phase in equilibrium with one selected pure solid.
   *
   * <p>
   * The general multiphase beta Newton system becomes rank deficient when only one fluid phase remains. In that case
   * the pure-solid fugacity equality fixes the selected component mole fraction in the fluid, while the non-solid feed
   * inventory fixes the fluid beta. Solving those two relations directly prevents the last carrier phase from being
   * deactivated and preserves every non-solid component.
   * </p>
   *
   * @throws IllegalStateException if the one-fluid-phase equilibrium is nonphysical or does not converge
   */
  private void solveSingleFluidPhase() {
    final int maxIterations = 100;
    final double compositionTolerance = 1.0e-12;
    double solidFeedFraction = system.getPhase(0).getComponent(solidIndex).getz();
    double lowerMoleFraction = Math.max(phaseFractionMinimumLimit, compositionTolerance);
    double upperMargin = Math.max(compositionTolerance, 100.0 * phaseFractionMinimumLimit * (1.0 - solidFeedFraction));
    double upperMoleFraction = solidFeedFraction - upperMargin;
    if (upperMoleFraction <= lowerMoleFraction) {
      throw new IllegalStateException("Selected solid inventory is below the one-fluid equilibrium resolution");
    }
    double lowerResidual = setSingleFluidState(lowerMoleFraction, solidFeedFraction);
    double upperResidual = setSingleFluidState(upperMoleFraction, solidFeedFraction);
    if (!Double.isFinite(lowerResidual) || !Double.isFinite(upperResidual) || lowerResidual * upperResidual > 0.0) {
      throw new IllegalStateException("Single-fluid solid equilibrium is not bracketed");
    }
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      double middleMoleFraction = 0.5 * (lowerMoleFraction + upperMoleFraction);
      double middleResidual = setSingleFluidState(middleMoleFraction, solidFeedFraction);
      if (!Double.isFinite(middleResidual)) {
        throw new IllegalStateException("Nonfinite single-fluid solid fugacity residual");
      }
      if (Math.abs(middleResidual) <= SINGLE_FLUID_FUGACITY_TOLERANCE) {
        return;
      }
      if (upperMoleFraction - lowerMoleFraction <= compositionTolerance) {
        throw new IllegalStateException("Single-fluid solid fugacity root is discontinuous");
      }
      if (lowerResidual * middleResidual <= 0.0) {
        upperMoleFraction = middleMoleFraction;
      } else {
        lowerMoleFraction = middleMoleFraction;
        lowerResidual = middleResidual;
      }
    }
    throw new IllegalStateException("Single-fluid solid equilibrium did not converge");
  }

  /**
   * Applies a component-conservative one-fluid/one-solid trial and returns its fugacity residual.
   *
   * @param solidFluidMoleFraction selected-solid component mole fraction in the fluid phase
   * @param solidFeedFraction selected-solid component overall mole fraction
   * @return logarithmic selected-component fugacity residual
   */
  private double setSingleFluidState(double solidFluidMoleFraction, double solidFeedFraction) {
    double fluidBeta = (1.0 - solidFeedFraction) / (1.0 - solidFluidMoleFraction);
    if (!Double.isFinite(fluidBeta) || fluidBeta <= phaseFractionMinimumLimit
        || fluidBeta >= 1.0 - phaseFractionMinimumLimit) {
      throw new IllegalStateException("Invalid single-fluid solid equilibrium phase fraction");
    }
    system.setBeta(0, fluidBeta);
    system.setBeta(1, 1.0 - fluidBeta);
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      double moleFraction = component == solidIndex ? solidFluidMoleFraction
          : system.getPhase(0).getComponent(component).getz() / fluidBeta;
      system.getPhase(0).getComponent(component).setx(moleFraction);
    }
    system.init(1);
    double solidFugacityCoefficient = system.getPhase(PhaseType.SOLID).getComponent(solidIndex)
        .getFugacityCoefficient();
    double fluidFugacityCoefficient = system.getPhase(0).getComponent(solidIndex).getFugacityCoefficient();
    return Math.log(solidFluidMoleFraction * fluidFugacityCoefficient / solidFugacityCoefficient);
  }

  /**
   * Check the selected components after a fluid-only flash and add a single stable pure solid.
   *
   * @return the number of solid phases added, zero or one
   * @throws UnsupportedOperationException if multiple selected components are predicted to precipitate
   */
  public int checkAndAddSolidPhase() {
    int candidateIndex = -1;
    double candidateFraction = 0.0;
    for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
      if (!system.getPhase(0).getComponent(k).doSolidCheck()
          || system.getTemperature() > system.getPhase(0).getComponent(k).getTriplePointTemperature()) {
        continue;
      }
      // Each stability trial uses a normalized pure-component reference phase.
      // Keep non-candidate solid components at trace fractions during fugacity evaluation.
      for (int j = 0; j < system.getNumberOfComponents(); j++) {
        system.getPhases()[3].getComponent(j).setx(j == k ? 1.0 : 1e-50);
      }
      double fraction = system.getPhase(0).getComponent(k).getz();
      double solidFugacityCoefficient = system.getPhases()[3].getComponent(k).fugcoef(system.getPhases()[3]);
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        fraction -= system.getBeta(i) * solidFugacityCoefficient
            / system.getPhase(i).getComponent(k).getFugacityCoefficient();
      }
      if (fraction > 1e-20) {
        if (candidateIndex >= 0) {
          throw new UnsupportedOperationException("TPSolidflash does not support multiple solid phases: "
              + system.getPhase(0).getComponent(candidateIndex).getName() + " and "
              + system.getPhase(0).getComponent(k).getName() + ". Select a single solid component.");
        }
        candidateIndex = k;
        candidateFraction = fraction;
      }
    }
    if (candidateIndex < 0) {
      return 0;
    }

    solidIndex = candidateIndex;
    solidsNumber = 1;
    totalSolidFrac = candidateFraction;
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      system.getPhases()[3].getComponent(i).setz(system.getPhase(0).getComponent(i).getz());
      system.getPhases()[3].getComponent(i).setx(i == solidIndex ? 1.0 : 1e-50);
    }
    system.setNumberOfPhases(system.getNumberOfPhases() + 1);
    system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
    system.setBeta(system.getNumberOfPhases() - 1, candidateFraction);
    return solidsNumber;
  }

  /**
   * solvebeta1.
   *
   * @return a double
   */
  public double solvebeta1() {
    // double numberOfMolesFreeze =
    // system.getPhase(0).getComponent(solidIndex).getNumberOfmoles();
    double solidCandidate = 0;
    int iter = 0;
    double dn = -0.01;
    double solidCandidateOld = 0;
    do {
      solidCandidateOld = solidCandidate;
      system.addComponent(system.getPhase(0).getComponent(solidIndex).getComponentName(), dn);
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      // system.init(0);
      system.setSolidPhaseCheck(false);
      ops.TPflash();
      // system.setSolidPhaseCheck(true);

      iter++;
      solidCandidate = system.getPhase(0).getComponents()[solidIndex].getz();
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        solidCandidate -= system.getPhase(PhaseType.SOLID).getComponent(solidIndex).fugcoef(
            system.getPhase(PhaseType.SOLID)) / system.getPhase(i).getComponent(solidIndex).getFugacityCoefficient();
      }
      double dsoliddn = (solidCandidate - solidCandidateOld) / dn;
      dn = -0.5 * solidCandidate / dsoliddn;
      logger.info("solid cand " + solidCandidate);
    } while (solidCandidate > 1e-5 && iter < 50);

    return 1.0;
  }
}
