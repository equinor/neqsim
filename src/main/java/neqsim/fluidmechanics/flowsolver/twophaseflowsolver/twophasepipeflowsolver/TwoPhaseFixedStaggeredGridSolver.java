package neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.mathlib.generalmath.TDMAsolve;

/**
 * <p>
 * TwoPhaseFixedStaggeredGridSolver class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TwoPhaseFixedStaggeredGridSolver extends TwoPhasePipeFlowSolver
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TwoPhaseFixedStaggeredGridSolver.class);
  Matrix diffMatrix;
  double[][] dn;
  int iter = 0;
  Matrix[] diff4Matrix;
  double[][][] xNew;
  protected double[][] oldMass;
  protected double[][] oldComp;
  protected double[][] oldDensity;
  protected double[][] oldVelocity;
  protected double[][][] oldComposition;
  protected double[][] oldInternalEnergy;
  protected double[][] oldImpuls;
  protected double[][] oldEnergy;

  /**
   * <p>
   * Constructor for TwoPhaseFixedStaggeredGridSolver.
   * </p>
   */
  public TwoPhaseFixedStaggeredGridSolver() {}

  /**
   * <p>
   * Constructor for TwoPhaseFixedStaggeredGridSolver.
   * </p>
   *
   * @param pipe a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   * @param length a double
   * @param nodes a int
   */
  public TwoPhaseFixedStaggeredGridSolver(FlowSystemInterface pipe, double length, int nodes) {
    super(pipe, length, nodes);
  }

  /**
   * <p>
   * Constructor for TwoPhaseFixedStaggeredGridSolver.
   * </p>
   *
   * @param pipe a {@link neqsim.fluidmechanics.flowsystem.FlowSystemInterface} object
   * @param length a double
   * @param nodes a int
   * @param dynamic a boolean
   */
  public TwoPhaseFixedStaggeredGridSolver(FlowSystemInterface pipe, double length, int nodes,
      boolean dynamic) {
    super(pipe, length, nodes);
    this.dynamic = dynamic;
    oldMass = new double[2][nodes];
    oldComp = new double[2][nodes];
    oldImpuls = new double[2][nodes];
    diff4Matrix =
        new Matrix[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    oldEnergy = new double[2][nodes];
    oldVelocity = new double[2][nodes];
    oldDensity = new double[2][nodes];
    oldInternalEnergy = new double[2][nodes];
    oldComposition = new double[2][pipe.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][nodes];
    numberOfVelocityNodes = nodes;
  }

  /** {@inheritDoc} */
  @Override
  public TwoPhaseFixedStaggeredGridSolver clone() {
    TwoPhaseFixedStaggeredGridSolver clonedSystem = null;
    try {
      clonedSystem = (TwoPhaseFixedStaggeredGridSolver) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    return clonedSystem;
  }

  /**
   * <p>
   * initProfiles.
   * </p>
   */
  public void initProfiles() {
    pipe.getNode(0).getBulkSystem().initBeta();
    pipe.getNode(0).getBulkSystem().init_x_y();
    pipe.getNode(0).initFlowCalc();
    pipe.getNode(0).calcFluxes();

    int numComponents = pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents();

    for (int i = 1; i < numberOfNodes - 1; i++) {
      // Copy mole state from previous node (i-1) to current node (i)
      // This propagates the accumulated mass transfer through the pipe
      for (int phase = 0; phase < 2; phase++) {
        for (int comp = 0; comp < numComponents; comp++) {
          double prevMoles = pipe.getNode(i - 1).getBulkSystem().getPhase(phase).getComponent(comp)
              .getNumberOfMolesInPhase();
          double currMoles = pipe.getNode(i).getBulkSystem().getPhase(phase).getComponent(comp)
              .getNumberOfMolesInPhase();
          double delta = prevMoles - currMoles;
          if (Math.abs(delta) > 1e-20) {
            pipe.getNode(i).getBulkSystem().getPhases()[phase].addMoles(comp, delta);
          }
        }
      }

      // Re-initialize beta and x,y based on updated moles
      pipe.getNode(i).getBulkSystem().initBeta();
      pipe.getNode(i).getBulkSystem().init_x_y();
      pipe.getNode(i).getBulkSystem().init(3);
      pipe.getNode(i).initFlowCalc();
      pipe.getNode(i).calcFluxes();

      double liquidHeatRate = pipe.getNode(i).getFluidBoundary().getInterphaseHeatFlux(1)
          * pipe.getNode(i).getInterphaseContactArea()
          * (pipe.getNode(i).getGeometry().getNodeLength() / pipe.getNode(i).getVelocity(1));
      double gasHeatRate = -pipe.getNode(i).getFluidBoundary().getInterphaseHeatFlux(0)
          * pipe.getNode(i).getInterphaseContactArea()
          * (pipe.getNode(i).getGeometry().getNodeLength() / pipe.getNode(i).getVelocity(0));

      double liquid_dT = liquidHeatRate / pipe.getNode(i).getBulkSystem().getPhase(1).getCp();
      double gas_dT = gasHeatRate / pipe.getNode(i).getBulkSystem().getPhase(0).getCp();

      pipe.getNode(i + 1).getBulkSystem().getPhase(0)
          .setTemperature(pipe.getNode(i).getBulkSystem().getPhase(0).getTemperature() + gas_dT);
      pipe.getNode(i + 1).getBulkSystem().getPhase(1)
          .setTemperature(pipe.getNode(i).getBulkSystem().getPhase(1).getTemperature() + liquid_dT);

      // Calculate and apply mass transfer from this node
      for (int componentNumber = 0; componentNumber < numComponents; componentNumber++) {
        double transferToLiquid =
            pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(componentNumber)
                * pipe.getNode(i).getInterphaseContactArea();

        // Limit transfer to 50% of available moles per node
        if (transferToLiquid > 0.0) {
          double availableInGas = pipe.getNode(i).getBulkSystem().getPhase(0)
              .getComponent(componentNumber).getNumberOfMolesInPhase();
          transferToLiquid = Math.min(transferToLiquid, 0.5 * Math.max(0.0, availableInGas));
        } else if (transferToLiquid < 0.0) {
          // Negative flux = evaporation from liquid to gas
          // This can happen when driving force reverses (gas depleted, liquid saturated)
          // For steady-state dissolution modeling, skip evaporation to prevent re-equilibration
          transferToLiquid = 0.0;
        }

        // Apply mass transfer: gas loses, liquid gains
        pipe.getNode(i).getBulkSystem().getPhases()[0].addMoles(componentNumber, -transferToLiquid);
        pipe.getNode(i).getBulkSystem().getPhases()[1].addMoles(componentNumber, transferToLiquid);
      }

      // Re-init after applying transfer so phaseFraction reflects new state
      pipe.getNode(i).getBulkSystem().initBeta();
      pipe.getNode(i).getBulkSystem().init_x_y();
      pipe.getNode(i).initFlowCalc();
    }

    // Handle last node - copy from previous node
    int lastNode = numberOfNodes - 1;
    for (int phase = 0; phase < 2; phase++) {
      for (int comp = 0; comp < numComponents; comp++) {
        double prevMoles = pipe.getNode(lastNode - 1).getBulkSystem().getPhase(phase)
            .getComponent(comp).getNumberOfMolesInPhase();
        double currMoles = pipe.getNode(lastNode).getBulkSystem().getPhase(phase).getComponent(comp)
            .getNumberOfMolesInPhase();
        double delta = prevMoles - currMoles;
        if (Math.abs(delta) > 1e-20) {
          pipe.getNode(lastNode).getBulkSystem().getPhases()[phase].addMoles(comp, delta);
        }
      }
    }

    pipe.getNode(lastNode).getBulkSystem().initBeta();
    pipe.getNode(lastNode).getBulkSystem().init_x_y();
    pipe.getNode(lastNode).getBulkSystem().init(3);
    pipe.getNode(lastNode).initFlowCalc();
    pipe.getNode(lastNode).calcFluxes();
  }

  /**
   * <p>
   * initMatrix.
   * </p>
   */
  public void initMatrix() {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).init();
      double enthalpy0 = pipe.getNode(i).getBulkSystem().getPhases()[0].getEnthalpy()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
      double enthalpy1 = pipe.getNode(i).getBulkSystem().getPhases()[1].getEnthalpy()
          / pipe.getNode(i).getBulkSystem().getPhases()[1].getNumberOfMolesInPhase()
          / pipe.getNode(i).getBulkSystem().getPhases()[1].getMolarMass();

      solMatrix[0].set(i, 0, pipe.getNode(i).getVelocityIn(0).doubleValue());
      solMatrix[1].set(i, 0, pipe.getNode(i).getVelocityIn(1).doubleValue());

      sol3Matrix[0].set(i, 0, enthalpy0);
      sol3Matrix[1].set(i, 0, enthalpy1);

      solPhaseConsMatrix[0].set(i, 0,
          pipe.getNode(i).getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity());
      solPhaseConsMatrix[1].set(i, 0, pipe.getNode(i).getPhaseFraction(1));

      for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
        for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0]
            .getNumberOfComponents(); j++) {
          solMolFracMatrix[phaseNum][j].set(i, 0,
              pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getComponent(j).getx()
                  * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getComponent(j)
                      .getMolarMass()
                  / pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getMolarMass());
        }
      }
    }
  }

  /**
   * <p>
   * initPressure.
   * </p>
   *
   * @param phaseNum a int
   */
  public void initPressure(int phaseNum) {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).init();
      pipe.getNode(i).getBulkSystem()
          .setPressure(0.8 * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getdPdrho()
              * diffMatrix.get(i, 0) * 1e-5 + pipe.getNode(i).getBulkSystem().getPressure());
      pipe.getNode(i).init();
    }
  }

  /**
   * <p>
   * initVelocity.
   * </p>
   *
   * @param phase a int
   */
  public void initVelocity(int phase) {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).setVelocityIn(phase, pipe.getNode(i).getVelocityIn(phase).doubleValue() + 0.8
          * (solMatrix[phase].get(i, 0) - pipe.getNode(i).getVelocityIn(phase).doubleValue()));
    }

    for (int i = 0; i < numberOfNodes; i++) {
      double meanVelocity = pipe.getNode(i).getVelocityIn(phase).doubleValue();
      pipe.getNode(i).setVelocity(phase, meanVelocity);
      pipe.getNode(i).init();
    }
  }

  /**
   * <p>
   * initTemperature.
   * </p>
   *
   * @param phaseNum a int
   */
  public void initTemperature(int phaseNum) {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).init();
      pipe.getNode(i).getBulkSystem()
          .setTemperature(
              pipe.getNode(i).getBulkSystem().getTemperature(phaseNum) + 0.8 * diffMatrix.get(i, 0)
                  / (pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getCp()
                      / pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
                      / pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getMolarMass()),
              phaseNum);
      pipe.getNode(i).init();
    }
  }

  /**
   * <p>
   * initPhaseFraction.
   * </p>
   *
   * @param phase a int
   */
  public void initPhaseFraction(int phase) {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).setPhaseFraction(phase,
          pipe.getNode(i).getPhaseFraction(phase) + 0.8 * diffMatrix.get(i, 0));
      pipe.getNode(i).setPhaseFraction(0, 1.0 - pipe.getNode(i).getPhaseFraction(phase));
      pipe.getNode(i).init();
    }
  }

  /**
   * <p>
   * initComposition.
   * </p>
   *
   * @param phaseNum a int
   * @param comp a int
   */
  public void initComposition(int phaseNum, int comp) {
    for (int j = 0; j < numberOfNodes; j++) {
      if ((pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp].getx()
          + diffMatrix.get(j, 0) * pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getMolarMass()
              / pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp]
                  .getMolarMass()) > 1.0) {
        pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp].setx(1.0 - 1e-30);
      } else if (pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp].getx()
          + diffMatrix.get(j, 0) * pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getMolarMass()
              / pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp]
                  .getMolarMass() < 0.0) {
        pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp].setx(1e-30);
      } else {
        pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp]
            .setx(pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp].getx()
                + diffMatrix.get(j, 0)
                    * pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getMolarMass()
                    / pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[comp]
                        .getMolarMass());
        // pipe.getNode(j).getBulkSystem().getPhases()[0].getComponent(p).getx()
        // + 0.5*diff4Matrix[p].get(j,0));
      }

      double xSum = 0.0;
      for (int i = 0; i < pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getNumberOfComponents()
          - 1; i++) {
        xSum += pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponent(i).getx();
      }

      pipe.getNode(j).getBulkSystem().getPhase(phaseNum).getComponents()[pipe.getNode(j)
          .getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1].setx(1.0 - xSum);
      pipe.getNode(j).init();
    }
  }

  /**
   * <p>
   * setMassConservationMatrix.
   * </p>
   *
   * @param phaseNum a int
   */
  public void setMassConservationMatrix(int phaseNum) {
    if (!dynamic) {
      double SU = 0;
      a[0] = 0;
      b[0] = 1.0;
      c[0] = 0;
      SU = pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity();
      r[0] = SU;
    } else {
      // double Ae = pipe.getNode(0).getArea(phase);
      // double Aw = pipe.getNode(0).getArea(phase);
      // double Fw = pipe.getNode(0).getVelocityIn(phase).doubleValue() * Aw;
      // double Fe = oldVelocity[phase][0] * Ae;
      // System.out.println("new- old : " +
      // (pipe.getNode(0).getVelocityIn().doubleValue() - oldVelocity[0]));
      oldMass[phaseNum][0] = 1.0 / timeStep * pipe.getNode(0).getGeometry().getArea()
          * pipe.getNode(0).getGeometry().getNodeLength();

      a[0] = 0.0; // Math.max(Fw,0);
      c[0] = 1.0; // Math.max(-Fe,0);
      b[0] = 1.0; // a[0] + c[0] + (Fe - Fw) + oldMass[0];
      r[0] = 0.0; // oldMass[0]*oldDensity[0];

      // setter ligningen paa rett form
      a[0] = -a[0];
      c[0] = -c[0];
    }

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getArea(phaseNum);
      double Aw = pipe.getNode(i - 1).getArea(phaseNum);
      double Fe = pipe.getNode(i).getVelocityOut(phaseNum).doubleValue() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn(phaseNum).doubleValue() * Aw;

      if (dynamic) {
        oldMass[phaseNum][i] = 1.0 / timeStep * pipe.getNode(i).getArea(phaseNum)
            * pipe.getNode(i).getGeometry().getNodeLength();
      } else {
        oldMass[phaseNum][i] = 0.0;
      }

      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) + oldMass[phaseNum][i];
      r[i] = oldMass[phaseNum][i] * oldDensity[phaseNum][i];

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    // double Ae = pipe.getNode(i).getArea(phase);
    // double Aw = pipe.getNode(i - 1).getArea(phase);

    // double Fe = pipe.getNode(i).getVelocity(phase) * Ae;
    // double Fw = pipe.getNode(i).getVelocityIn(phase).doubleValue() * Aw;

    if (dynamic) {
      oldMass[phaseNum][i] = 1.0 / timeStep * pipe.getNode(i).getArea(phaseNum)
          * pipe.getNode(i).getGeometry().getNodeLength();
    } else {
      oldMass[phaseNum][i] = 0.0;
    }

    a[i] = 1; // Math.max(Fw,0);
    c[i] = 0; // Math.max(-Fe,0);
    b[i] = 1; // a[i] + c[i] + (Fe - Fw) + oldMass[phase][i];
    r[i] = 0; // oldMass[phase][i]*oldDensity[phase][i];
    // setter ligningen paa rett form
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * <p>
   * setPhaseFractionMatrix.
   * </p>
   *
   * @param phaseNum a int
   */
  public void setPhaseFractionMatrix(int phaseNum) {
    if (!dynamic) {
      double SU = 0;
      a[0] = 0;
      b[0] = 1.0;
      c[0] = 0;
      SU = pipe.getNode(0).getPhaseFraction(phaseNum);
      r[0] = SU;
    } else {
      // double Ae = pipe.getNode(0).getGeometry().getArea();
      // double Aw = pipe.getNode(0).getGeometry().getArea();
      // double Fw = pipe.getNode(0).getVelocityIn(phase).doubleValue() * Aw;
      // double Fe = oldVelocity[phase][0] * Ae;
      // System.out.println("new- old : " +
      // (pipe.getNode(0).getVelocityIn().doubleValue() - oldVelocity[0]));
      oldMass[phaseNum][0] = 1.0 / timeStep * pipe.getNode(0).getGeometry().getArea()
          * pipe.getNode(0).getGeometry().getNodeLength();

      a[0] = 0.0; // Math.max(Fw,0);
      c[0] = 1.0; // Math.max(-Fe,0);
      b[0] = 1.0; // a[0] + c[0] + (Fe - Fw) + oldMass[0];
      r[0] = 0.0; // oldMass[0]*oldDensity[0];

      // setter ligningen paa rett form
      a[0] = -a[0];
      c[0] = -c[0];
    }

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();
      double Fe = pipe.getNode(i).getVelocityOut(phaseNum).doubleValue() * Ae
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity();
      double Fw = pipe.getNode(i).getVelocityIn(phaseNum).doubleValue() * Aw * pipe.getNode(i - 1)
          .getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity();

      if (dynamic) {
        oldMass[phaseNum][i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
            * pipe.getNode(i).getGeometry().getNodeLength();
      } else {
        oldMass[phaseNum][i] = 0.0;
      }

      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) + oldMass[phaseNum][i];
      r[i] = oldMass[phaseNum][i] * oldDensity[phaseNum][i];

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();

    double Fe = pipe.getNode(i).getVelocity(phaseNum) * Ae
        * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity();
    double Fw = pipe.getNode(i).getVelocityIn(phaseNum).doubleValue() * Aw * pipe.getNode(i - 1)
        .getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity();

    if (dynamic) {
      oldMass[phaseNum][i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
          * pipe.getNode(i).getGeometry().getNodeLength();
    } else {
      oldMass[phaseNum][i] = 0.0;
    }

    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    b[i] = a[i] + c[i] + (Fe - Fw) + oldMass[phaseNum][i];
    r[i] = oldMass[phaseNum][i] * oldDensity[phaseNum][i];
    // setter ligningen paa rett form
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * <p>
   * setImpulsMatrixTDMA.
   * </p>
   *
   * @param phaseNum a int
   */
  public void setImpulsMatrixTDMA(int phaseNum) {
    double sign = (phaseNum == 0) ? 1.0 : -1.0;
    double SU = 0.0;
    double SP = 0.0;
    double Fw = 0.0;

    double Fe = 0.0;
    pipe.getNode(0).initFlowCalc();
    pipe.getNode(0).init();
    pipe.getNode(0).setVelocityIn(phaseNum, pipe.getNode(0).getVelocity(phaseNum));

    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;

    r[0] = pipe.getNode(0).getVelocityIn(phaseNum).doubleValue();

    a[1] = 0;
    b[1] = 1.0;
    c[1] = 0;

    r[1] = pipe.getNode(0).getVelocityIn(phaseNum).doubleValue();

    for (int i = 2; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getArea(phaseNum);
      double Aw = pipe.getNode(i - 1).getArea(phaseNum);
      double Amean = pipe.getNode(i - 1).getArea(phaseNum);
      double meanFrik = pipe.getNode(i - 1).getWallFrictionFactor(phaseNum);
      double meanDensity = pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum)
          .getPhysicalProperties().getDensity();
      double oldMeanDensity = oldDensity[phaseNum][i];
      double meanVelocity = pipe.getNode(i - 1).getVelocity(phaseNum);
      double vertposchange = pipe.getNode(i).getVerticalPositionOfNode()
          - pipe.getNode(i - 1).getVerticalPositionOfNode();
      double nodeLength = pipe.getNode(i - 1).getGeometry().getNodeLength();
      double interfaceFricition = pipe.getNode(i - 1).getInterPhaseFrictionFactor();
      // System.out.println(" dif: " +
      // (-pipe.getNode(i-1).getWallContactLength(phase)
      // * nodeLength* meanDensity *
      // meanFrik*Math.abs(meanVelocity)*meanVelocity/8.0
      // - pipe.getNode(i-1).getInterphaseContactLength(0)*nodeLength* meanDensity
      // *
      // interfaceFricition*Math.abs(pipe.getNode(i).getVelocity(0) -
      // pipe.getNode(i).getVelocity(1))*(pipe.getNode(i).getVelocity(0) -
      // pipe.getNode(i).getVelocity(1))/8.0*sign));
      SU = -Amean
          * (pipe.getNode(i).getBulkSystem().getPressure()
              - pipe.getNode(i - 1).getBulkSystem().getPressure())
          * 1e5
          + Amean * gravity * meanDensity * vertposchange
          + pipe.getNode(i - 1).getWallContactLength(phaseNum) * nodeLength * meanDensity * meanFrik
              * Math.abs(meanVelocity) * meanVelocity / 8.0
          - pipe.getNode(i - 1).getInterphaseContactLength(0) * nodeLength * meanDensity
              * interfaceFricition
              * Math.abs(pipe.getNode(i).getVelocity(0) - pipe.getNode(i).getVelocity(1))
              * (pipe.getNode(i).getVelocity(0) - pipe.getNode(i).getVelocity(1)) / 8.0 * sign;
      // System.out.println("su " + SU);
      SP = -pipe.getNode(i - 1).getWallContactLength(phaseNum) * nodeLength * meanDensity * meanFrik
          * meanVelocity / 4.0;
      Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
          .getDensity() * pipe.getNode(i - 1).getVelocity(phaseNum);
      Fe = Ae
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * pipe.getNode(i).getVelocity(phaseNum);

      if (dynamic) {
        oldImpuls[phaseNum][i] = 1.0 / timeStep * oldMeanDensity * nodeLength * Amean;
      } else {
        oldImpuls[phaseNum][i] = 0.0;
      }

      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0); // - Fe/2.0;
      b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldImpuls[phaseNum][i];
      r[i] = SU + oldImpuls[phaseNum][i] * oldVelocity[phaseNum][i];
      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    double Ae = pipe.getNode(i - 1).getArea(phaseNum);
    double Aw = pipe.getNode(i - 1).getArea(phaseNum);
    double Amean = pipe.getNode(i - 1).getArea(phaseNum);
    // double meanDiameter = pipe.getNode(i - 1).getGeometry().getDiameter();
    double meanFrik = pipe.getNode(i - 1).getWallFrictionFactor(phaseNum);
    double meanDensity =
        pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity();
    double oldMeanDensity = oldDensity[phaseNum][i];
    double meanVelocity = pipe.getNode(i - 1).getVelocity(phaseNum);
    double vertposchange = pipe.getNode(i).getVerticalPositionOfNode()
        - pipe.getNode(i - 1).getVerticalPositionOfNode();
    double nodeLength = pipe.getNode(i - 1).getGeometry().getNodeLength();
    double interfaceFricition = pipe.getNode(i - 1).getInterPhaseFrictionFactor();

    SU = -Amean
        * (pipe.getNode(i).getBulkSystem().getPressure()
            - pipe.getNode(i - 1).getBulkSystem().getPressure())
        * 1e5
        - Amean * gravity * meanDensity * vertposchange
        + pipe.getNode(i - 1).getWallContactLength(phaseNum) * nodeLength * meanDensity * meanFrik
            * Math.abs(meanVelocity) * meanVelocity / 8.0
        - pipe.getNode(i - 1).getInterphaseContactLength(0) * nodeLength * meanDensity
            * interfaceFricition
            * Math.abs(pipe.getNode(i).getVelocity(0) - pipe.getNode(i).getVelocity(1))
            * (pipe.getNode(i).getVelocity(0) - pipe.getNode(i).getVelocity(1)) / 8.0 * sign;
    SP = -pipe.getNode(i - 1).getWallContactLength(phaseNum) * nodeLength * meanDensity * meanFrik
        * meanVelocity / 4.0;
    Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
        .getDensity() * pipe.getNode(i).getVelocityIn(phaseNum).doubleValue();
    Fe = Ae
        * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
        * pipe.getNode(i).getVelocity(phaseNum);

    if (dynamic) {
      oldImpuls[phaseNum][i] = 1.0 / timeStep * oldMeanDensity * nodeLength * Amean;
    } else {
      oldImpuls[phaseNum][i] = 0.0;
    }

    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    // if(dynamic){c[i] = - Fe/2.0; a[i] = Fw/2.0; }
    b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldImpuls[phaseNum][i];
    r[i] = SU + oldImpuls[phaseNum][i] * oldVelocity[phaseNum][i];

    // setter ligningen paa rett form
    a[numberOfNodes - 1] = -a[numberOfNodes - 1];
    c[numberOfNodes - 1] = -c[numberOfNodes - 1];
  }

  /**
   * <p>
   * setEnergyMatrixTDMA.
   * </p>
   * 
   * <p>
   * This method implements the energy conservation equation for two-phase pipe flow. The energy
   * equation includes:
   * </p>
   * <ul>
   * <li>Convective enthalpy transport</li>
   * <li>Wall heat transfer (with proper heat transfer coefficient)</li>
   * <li>Interphase heat transfer</li>
   * <li>Joule-Thomson effect (temperature change due to pressure drop)</li>
   * <li>Latent heat from phase change (evaporation/condensation)</li>
   * <li>Gravitational potential energy changes</li>
   * </ul>
   *
   * @param phaseNum a int
   */
  public void setEnergyMatrixTDMA(int phaseNum) {
    double sign = (phaseNum == 0) ? 1.0 : -1.0;

    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    double SU = pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getEnthalpy()
        / pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
        / pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getMolarMass();
    r[0] = SU;

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double fe = pipe.getNode(i + 1).getGeometry().getNodeLength()
          / (pipe.getNode(i).getGeometry().getNodeLength()
              + pipe.getNode(i + 1).getGeometry().getNodeLength());
      double fw = pipe.getNode(i - 1).getGeometry().getNodeLength()
          / (pipe.getNode(i).getGeometry().getNodeLength()
              + pipe.getNode(i - 1).getGeometry().getNodeLength());
      double Ae = pipe.getNode(i).getArea(phaseNum);
      double Aw = pipe.getNode(i - 1).getArea(phaseNum);
      double vertposchange = (1 - fe)
          * (pipe.getNode(i + 1).getVerticalPositionOfNode()
              - pipe.getNode(i).getVerticalPositionOfNode())
          + (1 - fw) * (pipe.getNode(i).getVerticalPositionOfNode()
              - pipe.getNode(i - 1).getVerticalPositionOfNode());

      // Get proper wall heat transfer coefficient and surrounding temperature
      double wallHeatTransferCoeff = pipe.getNode(i).calcTotalHeatTransferCoefficient(phaseNum);
      double surroundingTemp =
          pipe.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature();
      double phaseTemp = pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getTemperature();
      double nodeLength = pipe.getNode(i).getGeometry().getNodeLength();
      double diameter = pipe.getNode(i).getGeometry().getDiameter();

      // Calculate Joule-Thomson effect: dT = μ_JT * dP
      // Pressure gradient (Pa/m): dP/dx = (P[i+1] - P[i-1]) / (2 * nodeLength)
      double dPdx = (pipe.getNode(i + 1).getBulkSystem().getPressure()
          - pipe.getNode(i - 1).getBulkSystem().getPressure()) * 1e5
          / (pipe.getNode(i + 1).getGeometry().getNodeLength()
              + pipe.getNode(i - 1).getGeometry().getNodeLength());
      double jouleThomsonCoeff =
          pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getJouleThomsonCoefficient();
      // Joule-Thomson contribution to energy (J/kg): ρ * v * μ_JT * dP/dx * A * dx
      double jouleThomsonEnergy = pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity()
          * pipe.getNode(i).getVelocity(phaseNum) * jouleThomsonCoeff * dPdx
          * pipe.getNode(i).getArea(phaseNum) * nodeLength;

      // Calculate latent heat from phase change (evaporation/condensation)
      // This is based on the interphase mass transfer rate and enthalpy of vaporization
      double latentHeatEnergy = 0.0;
      if (pipe.getNode(i).getFluidBoundary() != null) {
        // Sum latent heat contribution from all components
        for (int comp = 0; comp < pipe.getNode(i).getBulkSystem().getPhase(phaseNum)
            .getNumberOfComponents(); comp++) {
          double molarFlux = pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(comp);
          // Enthalpy difference between gas and liquid phase for this component
          double gasEnthalpy = pipe.getNode(i).getBulkSystem().getPhase(0).getComponent(comp)
              .getEnthalpy(pipe.getNode(i).getBulkSystem().getPhase(0).getTemperature())
              / pipe.getNode(i).getBulkSystem().getPhase(0).getComponent(comp)
                  .getNumberOfMolesInPhase();
          double liquidEnthalpy = pipe.getNode(i).getBulkSystem().getPhase(1).getComponent(comp)
              .getEnthalpy(pipe.getNode(i).getBulkSystem().getPhase(1).getTemperature())
              / pipe.getNode(i).getBulkSystem().getPhase(1).getComponent(comp)
                  .getNumberOfMolesInPhase();
          double enthalpyOfVaporization = gasEnthalpy - liquidEnthalpy;

          // Latent heat = mass flux * enthalpy of vaporization * contact area * residence time
          double contactLength = pipe.getNode(i).getInterphaseContactLength(phaseNum);
          double residenceTime = nodeLength / Math.max(pipe.getNode(i).getVelocity(phaseNum), 1e-6);
          latentHeatEnergy += sign * molarFlux * enthalpyOfVaporization * contactLength * nodeLength
              * residenceTime;
        }
      }

      // Wall heat transfer: Q = U * A * (T_wall - T_fluid)
      // Using proper heat transfer coefficient and surrounding temperature
      double wallHeatFlux = pipe.getNode(i).getWallContactLength(phaseNum)
          / pipe.getNode(i).getGeometry().getCircumference() * pipe.getNode(i).getArea(phaseNum)
          * 4.0 * wallHeatTransferCoeff * (surroundingTemp - phaseTemp) / diameter * nodeLength;

      // Interphase heat transfer
      double interphaseHeatFlux =
          sign * pipe.getNode(i).getFluidBoundary().getInterphaseHeatFlux(phaseNum) * nodeLength
              * pipe.getNode(i).getInterphaseContactLength(phaseNum)
              * (nodeLength / pipe.getNode(i).getVelocity(phaseNum));

      // Potential energy change (elevation work)
      double potentialEnergy = -pipe.getNode(i).getArea(phaseNum) * gravity
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity()
          * pipe.getNode(i).getVelocity(phaseNum) * vertposchange;

      // Total source term
      SU = potentialEnergy + wallHeatFlux + interphaseHeatFlux + jouleThomsonEnergy
          + latentHeatEnergy;
      double SP = 0;

      double Fw =
          Aw * pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
              .getDensity() * pipe.getNode(i).getVelocityIn(phaseNum).doubleValue();
      double Fe = Ae
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * pipe.getNode(i).getVelocityOut(phaseNum).doubleValue();

      if (dynamic) {
        oldEnergy[phaseNum][i] = 1.0 / timeStep * oldDensity[phaseNum][i]
            * pipe.getNode(i).getGeometry().getNodeLength() * pipe.getNode(i).getArea(phaseNum);
      } else {
        oldEnergy[phaseNum][i] = 0.0;
      }

      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldEnergy[phaseNum][i];
      r[i] = SU + oldEnergy[phaseNum][i] * oldInternalEnergy[phaseNum][i];

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;

    double fw = pipe.getNode(i - 1).getGeometry().getNodeLength()
        / (pipe.getNode(i).getGeometry().getNodeLength()
            + pipe.getNode(i - 1).getGeometry().getNodeLength());
    double Ae = pipe.getNode(i).getArea(phaseNum);
    double Aw = pipe.getNode(i - 1).getArea(phaseNum);
    double vertposchange = (1 - fw) * (pipe.getNode(i).getVerticalPositionOfNode()
        - pipe.getNode(i - 1).getVerticalPositionOfNode());

    // Get proper wall heat transfer coefficient and surrounding temperature for last node
    double wallHeatTransferCoeffLast = pipe.getNode(i).calcTotalHeatTransferCoefficient(phaseNum);
    double surroundingTempLast =
        pipe.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature();
    double phaseTempLast = pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getTemperature();
    double nodeLengthLast = pipe.getNode(i).getGeometry().getNodeLength();
    double diameterLast = pipe.getNode(i).getGeometry().getDiameter();

    // Calculate Joule-Thomson effect for last node (use backward difference)
    double dPdxLast = (pipe.getNode(i).getBulkSystem().getPressure()
        - pipe.getNode(i - 1).getBulkSystem().getPressure()) * 1e5
        / pipe.getNode(i - 1).getGeometry().getNodeLength();
    double jouleThomsonCoeffLast =
        pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getJouleThomsonCoefficient();
    double jouleThomsonEnergyLast = pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity()
        * pipe.getNode(i).getVelocity(phaseNum) * jouleThomsonCoeffLast * dPdxLast
        * pipe.getNode(i).getArea(phaseNum) * nodeLengthLast;

    // Calculate latent heat from phase change for last node
    double latentHeatEnergyLast = 0.0;
    if (pipe.getNode(i).getFluidBoundary() != null) {
      for (int comp = 0; comp < pipe.getNode(i).getBulkSystem().getPhase(phaseNum)
          .getNumberOfComponents(); comp++) {
        double molarFluxLast = pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(comp);
        double gasEnthalpyLast = pipe.getNode(i).getBulkSystem().getPhase(0).getComponent(comp)
            .getEnthalpy(pipe.getNode(i).getBulkSystem().getPhase(0).getTemperature())
            / pipe.getNode(i).getBulkSystem().getPhase(0).getComponent(comp)
                .getNumberOfMolesInPhase();
        double liquidEnthalpyLast = pipe.getNode(i).getBulkSystem().getPhase(1).getComponent(comp)
            .getEnthalpy(pipe.getNode(i).getBulkSystem().getPhase(1).getTemperature())
            / pipe.getNode(i).getBulkSystem().getPhase(1).getComponent(comp)
                .getNumberOfMolesInPhase();
        double enthalpyOfVaporizationLast = gasEnthalpyLast - liquidEnthalpyLast;
        double contactLengthLast = pipe.getNode(i).getInterphaseContactLength(phaseNum);
        double residenceTimeLast =
            nodeLengthLast / Math.max(pipe.getNode(i).getVelocity(phaseNum), 1e-6);
        latentHeatEnergyLast += sign * molarFluxLast * enthalpyOfVaporizationLast
            * contactLengthLast * nodeLengthLast * residenceTimeLast;
      }
    }

    // Wall heat transfer with proper coefficient for last node
    double wallHeatFluxLast = pipe.getNode(i).getWallContactLength(phaseNum)
        / pipe.getNode(i).getGeometry().getCircumference() * pipe.getNode(i).getArea(phaseNum) * 4.0
        * wallHeatTransferCoeffLast * (surroundingTempLast - phaseTempLast) / diameterLast
        * nodeLengthLast;

    // Interphase heat transfer for last node
    double interphaseHeatFluxLast =
        sign * pipe.getNode(i).getFluidBoundary().getInterphaseHeatFlux(phaseNum) * nodeLengthLast
            * pipe.getNode(i).getInterphaseContactLength(phaseNum)
            * (nodeLengthLast / pipe.getNode(i).getVelocity(phaseNum));

    // Potential energy change for last node
    double potentialEnergyLast = -pipe.getNode(i).getArea(phaseNum) * gravity
        * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity()
        * pipe.getNode(i).getVelocity(phaseNum) * vertposchange;

    // Total source term for last node
    SU = potentialEnergyLast + wallHeatFluxLast + interphaseHeatFluxLast + jouleThomsonEnergyLast
        + latentHeatEnergyLast;
    double SP = 0;

    double Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
        .getDensity() * pipe.getNode(i).getVelocityIn(phaseNum).doubleValue();
    double Fe =
        Ae * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
            * pipe.getNode(i).getVelocity(phaseNum);

    if (dynamic) {
      oldEnergy[phaseNum][i] = 1.0 / timeStep * oldDensity[phaseNum][i]
          * pipe.getNode(i).getGeometry().getNodeLength() * pipe.getNode(i).getArea(phaseNum);
    } else {
      oldEnergy[phaseNum][i] = 0.0;
    }

    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldEnergy[phaseNum][i];
    r[i] = SU + oldEnergy[phaseNum][i] * oldInternalEnergy[phaseNum][i];
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * <p>
   * setComponentConservationMatrix2.
   * </p>
   *
   * @param phaseNum a int
   * @param componentNumber a int
   */
  public void setComponentConservationMatrix2(int phaseNum, int componentNumber) {
    double SU = 0;
    double sign = (phaseNum == 0) ? 1.0 : 1.0;
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    SU = pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getComponents()[componentNumber].getx();
    // System.out.println("phase x0: "
    // +pipe.getNode(0).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getx());
    // System.out.println("phase x1: "
    // +pipe.getNode(0).getBulkSystem().getPhases()[1].getComponents()[componentNumber].getx());
    r[0] = SU;

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double fe = pipe.getNode(i + 1).getGeometry().getNodeLength()
          / (pipe.getNode(i).getGeometry().getNodeLength()
              + pipe.getNode(i + 1).getGeometry().getNodeLength());
      double fw = pipe.getNode(i - 1).getGeometry().getNodeLength()
          / (pipe.getNode(i).getGeometry().getNodeLength()
              + pipe.getNode(i - 1).getGeometry().getNodeLength());
      double Ae = 1.0 / ((1.0 - fe) / pipe.getNode(i).getArea(phaseNum)
          + fe / pipe.getNode(i + 1).getArea(phaseNum));
      double Aw = 1.0 / ((1.0 - fw) / pipe.getNode(i).getArea(phaseNum)
          + fw / pipe.getNode(i - 1).getArea(phaseNum));

      double Fe = pipe.getNode(i).getVelocityOut(phaseNum).doubleValue()
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getComponents()[componentNumber]
              .getMolarMass()
          / pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getMolarMass() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn(phaseNum).doubleValue()
          * pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
              .getDensity()
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getComponents()[componentNumber]
              .getMolarMass()
          / pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getMolarMass() * Aw;

      // System.out.println("vel: " +
      // pipe.getNode(i).getVelocityOut(phase).doubleValue() + " fe " + Fe);
      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0); // - Fe/2.0;
      b[i] = a[i] + c[i] + (Fe - Fw)
          - sign * pipe.getNode(i).getArea(phaseNum)
              * pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(componentNumber)
              / pipe.getNode(i).getVelocity() * pipe.getNode(i).getGeometry().getNodeLength();
      r[i] = 0;
      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    a[numberOfNodes - 1] = -1.0; // -1.0;
    b[numberOfNodes - 1] = 1.0;
    c[numberOfNodes - 1] = 0;
    SU = pipe.getNode(numberOfNodes - 2).getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
        .getDensity()
        * pipe.getNode(numberOfNodes - 2).getVelocityIn(phaseNum).doubleValue()
        * pipe.getNode(numberOfNodes - 2).getBulkSystem().getPhase(phaseNum)
            .getComponents()[componentNumber].getx()
        / (pipe.getNode(numberOfNodes - 1).getBulkSystem().getPhase(phaseNum)
            .getPhysicalProperties().getDensity()
            * pipe.getNode(numberOfNodes - 1).getVelocityIn(phaseNum).doubleValue());
    r[numberOfNodes - 1] = 0; // SU;
  }

  /**
   * <p>
   * setComponentConservationMatrix.
   * </p>
   *
   * @param phaseNum a int
   * @param componentNumber a int
   */
  public void setComponentConservationMatrix(int phaseNum, int componentNumber) {
    double sign = (phaseNum == 0) ? -1.0 : 1.0;
    double SU = 0;
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    SU = pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getComponents()[componentNumber].getx()
        * pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getComponents()[componentNumber]
            .getMolarMass()
        / pipe.getNode(0).getBulkSystem().getPhase(phaseNum).getMolarMass();
    r[0] = SU;

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getArea(phaseNum);
      double Aw = pipe.getNode(i - 1).getArea(phaseNum);

      double Fe = pipe.getNode(i).getVelocityOut(phaseNum).doubleValue()
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn(phaseNum).doubleValue()
          * pipe.getNode(i - 1).getBulkSystem().getPhase(phaseNum).getDensity() * Aw;
      // System.out.println("vel: " +
      // pipe.getNode(i).getVelocityOut(phase).doubleValue() + " fe " + Fe);
      if (dynamic) {
        oldComp[phaseNum][i] = 1.0 / timeStep * pipe.getNode(i).getArea(phaseNum)
            * pipe.getNode(i).getGeometry().getNodeLength()
            * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity();
      } else {
        oldComp[phaseNum][i] = 0.0;
      }

      SU = +sign * pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(componentNumber)
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getComponents()[componentNumber]
              .getMolarMass()
          * pipe.getNode(i).getGeometry().getNodeLength()
          * pipe.getNode(i).getInterphaseContactLength(phaseNum)
          * (pipe.getNode(i).getGeometry().getNodeLength() / pipe.getNode(i).getVelocity(phaseNum));
      // double SP = 0;
      // -pipe.getNode(i).getGeometry().getArea() * 4.0 * 12.0 /
      // (pipe.getNode(i).getGeometry().getDiameter()) *
      // pipe.getNode(i).getGeometry().getNodeLength();

      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[phaseNum][i];
      r[i] = SU + oldComp[phaseNum][i] * oldComposition[phaseNum][componentNumber][i];

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    // double fw =
    // pipe.getNode(i-1).getGeometry().getNodeLength() /
    // (pipe.getNode(i).getGeometry().getNodeLength() +
    // pipe.getNode(i-1).getGeometry().getNodeLength());
    // double Ae = pipe.getNode(i).getArea(phaseNum);
    // double Aw = pipe.getNode(i - 1).getArea(phaseNum);

    // double Fe = pipe.getNode(i).getVelocity(phaseNum) *
    // pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity() * Ae;
    // double Fw = pipe.getNode(i).getVelocityIn(phaseNum).doubleValue() * pipe.getNode(i -
    // 1).getBulkSystem().getPhase(phaseNum).getDensity() * Aw;

    SU = +sign * pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(componentNumber)
        * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getComponents()[componentNumber]
            .getMolarMass()
        * pipe.getNode(i).getGeometry().getNodeLength()
        * pipe.getNode(i).getInterphaseContactLength(phaseNum)
        * (pipe.getNode(i).getGeometry().getNodeLength() / pipe.getNode(i).getVelocity(phaseNum));
    // double SP = 0;
    // -pipe.getNode(i).getGeometry().getArea() * 4.0*12.0 /
    // (pipe.getNode(i).getGeometry().getDiameter())*pipe.getNode(i).getGeometry().getNodeLength();

    if (dynamic) {
      oldComp[phaseNum][i] = 1.0 / timeStep * pipe.getNode(i).getArea(phaseNum)
          * pipe.getNode(i).getGeometry().getNodeLength()
          * pipe.getNode(i).getBulkSystem().getPhase(phaseNum).getDensity();
    } else {
      oldComp[phaseNum][i] = 0.0;
    }

    a[i] = 1.0; // Math.max(Fw,0);
    c[i] = 0.0; // Math.max(-Fe,0);
    b[i] = 1.0; // a[i] + c[i] + (Fe - Fw) + oldComp[phase][i];
    r[i] = 0.0; // SU + oldComp[phase][i]*oldComposition[phase][componentNumber][i];
    // setter ligningen paa rett form
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * <p>
   * initFinalResults.
   * </p>
   *
   * @param phase a int
   */
  public void initFinalResults(int phase) {
    for (int i = 0; i < numberOfNodes; i++) {
      oldVelocity[phase][i] = pipe.getNode(i).getVelocityIn().doubleValue();
      oldDensity[phase][i] =
          pipe.getNode(i).getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity();
      oldInternalEnergy[phase][i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getEnthalpy()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();

      for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0]
          .getNumberOfComponents(); j++) {
        oldComposition[phase][j][i] = xNew[phase][j][i];
        // pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getx() *
        // pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getMolarMass() /
        // pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
      }
    }
  }

  /**
   * <p>
   * calcFluxes.
   * </p>
   */
  public void calcFluxes() {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).calcFluxes();
    }
  }

  /**
   * <p>
   * initNodes.
   * </p>
   */
  public void initNodes() {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).initFlowCalc();
      pipe.getNode(i).init();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void solveTDMA() {
    double[] d;
    int iter = 0;
    int iterTop = 0;
    double maxDiff = 1e10;
    // double maxDiffOld = 1e10;
    double diff = 0;
    System.out.println("starting...:");
    initProfiles();
    dn = new double[numberOfNodes][pipe.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents()];
    xNew = new double[2][pipe.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][numberOfNodes];
    initMatrix();

    do {
      // maxDiffOld = maxDiff;
      maxDiff = 0;
      iterTop++;

      iter = 0;
      if (this.solverType >= 5) {
        for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
          do {
            iter++;
            setImpulsMatrixTDMA(phaseNum);
            Matrix solOld = solMatrix[phaseNum].copy();
            d = TDMAsolve.solve(a, b, c, r);
            solMatrix[phaseNum] = new Matrix(d, 1).transpose();
            solMatrix[phaseNum].print(10, 10);
            diffMatrix = solMatrix[phaseNum].minus(solOld);
            // System.out.println("diff impuls: "+
            // diffMatrix.norm2()/solMatrix[phase].norm2());
            diff = Math.abs(diffMatrix.norm1() / solMatrix[phaseNum].norm1());
            if (diff > maxDiff) {
              maxDiff = diff;
            }
            initVelocity(phaseNum);
          } while (diff > 1e-10 && iter < 100);
        }
      }

      iter = 0;
      if (this.solverType >= 5) {
        for (int phaseNum = 1; phaseNum < 2; phaseNum++) {
          do {
            iter++;
            setPhaseFractionMatrix(phaseNum);
            Matrix solOld = solPhaseConsMatrix[phaseNum].copy();
            d = TDMAsolve.solve(a, b, c, r);
            solPhaseConsMatrix[phaseNum] = new Matrix(d, 1).transpose();
            // solPhaseConsMatrix[phase].print(10,10);
            diffMatrix = solPhaseConsMatrix[phaseNum].minus(solOld);
            // System.out.println("diff phase frac: "+
            // diffMatrix.norm2()/solPhaseConsMatrix[phase].norm2());
            diff = Math.abs(diffMatrix.norm1() / solPhaseConsMatrix[phaseNum].norm1());
            if (diff > maxDiff) {
              maxDiff = diff;
            }
            initPhaseFraction(phaseNum);
          } while (diff > 1e-15 && iter < 100);
        }

        int phase = 0;
        do {
          iter++;
          setMassConservationMatrix(phase);
          Matrix solOld = solPhaseConsMatrix[phase].copy();
          d = TDMAsolve.solve(a, b, c, r);
          solPhaseConsMatrix[phase] = new Matrix(d, 1).transpose();
          // solPhaseConsMatrix[phase].print(10,10);
          diffMatrix = solPhaseConsMatrix[phase].minus(solOld);
          // System.out.println("diff mass: "+
          // diffMatrix.norm2()/solPhaseConsMatrix[phase].norm2());
          diff = Math.abs(diffMatrix.norm1() / solPhaseConsMatrix[phase].norm1());
          if (diff > maxDiff) {
            maxDiff = diff;
          }
          initPressure(phase);
        } while (diff > 1e-15 && iter < 100);
      }

      if (this.solverType >= 5) {
        for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
          iter = 0;
          do {
            iter++;
            Matrix sol3Old = sol3Matrix[phaseNum].copy();
            setEnergyMatrixTDMA(phaseNum);
            d = TDMAsolve.solve(a, b, c, r);
            sol3Matrix[phaseNum] = new Matrix(d, 1).transpose();
            diffMatrix = sol3Matrix[phaseNum].minus(sol3Old);
            // System.out.println("diff energy: " +
            // diffMatrix.norm2()/sol3Matrix[phase].norm2());
            // diffMatrix.print(10,10);
            diff = Math.abs(diffMatrix.norm1() / sol3Matrix[phaseNum].norm1());
            if (diff > maxDiff) {
              maxDiff = diff;
            }
            initTemperature(phaseNum);
          } while (diff > 1e-15 && iter < 100);
        }
      }

      if (this.solverType >= 5) {
        double compDiff = 0.0;
        int compIter = 0;
        do {
          calcFluxes();
          compIter++;
          for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
            iter = 0;
            for (int p = 0; p < pipe.getNode(0).getBulkSystem().getPhases()[0]
                .getNumberOfComponents() - 1; p++) {
              do {
                iter++;
                setComponentConservationMatrix(phaseNum, p);
                Matrix solOld = solMolFracMatrix[phaseNum][p].copy();
                xNew[phaseNum][p] = TDMAsolve.solve(a, b, c, r);
                solMolFracMatrix[phaseNum][p] = new Matrix(xNew[phaseNum][p], 1).transpose();
                diffMatrix = solMolFracMatrix[phaseNum][p].minus(solOld);
                diff = Math.abs(diffMatrix.norm2() / solMolFracMatrix[phaseNum][p].norm2());
                if (diff > maxDiff) {
                  maxDiff = diff;
                }
                if (diff > compDiff) {
                  compDiff = diff;
                }
                System.out.println(
                    "diff molfrac: " + diffMatrix.norm2() / solMolFracMatrix[phaseNum][p].norm2());
                // Matrix dmat = new Matrix(xNew[phase][p], 1);
                // dmat.print(10,10);
                initComposition(phaseNum, p);
              } while (diff > 1e-12 && iter < 10);
            }
          }
        } while (compDiff > 1e-10 && compIter < 10);
        initNodes();
      }

      // initVelocity();
      // this.setVelocities();*/
      System.out.println("iter: " + iterTop + " maxdiff " + maxDiff);
    } while (Math.abs(maxDiff) > 1e-7 && iterTop < 1); // diffMatrix.norm2()/sol2Matrix.norm2())>0.1);

    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      for (int p = 0; p < pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()
          - 1; p++) {
        Matrix dmat = new Matrix(xNew[phaseNum][p], 1);
        dmat.print(10, 10);
      }
    }
  }
}
