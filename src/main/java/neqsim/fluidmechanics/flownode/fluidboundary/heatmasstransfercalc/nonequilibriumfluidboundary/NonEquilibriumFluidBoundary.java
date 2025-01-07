package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract NonEquilibriumFluidBoundary class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class NonEquilibriumFluidBoundary
    extends neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundary {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NonEquilibriumFluidBoundary.class);

  protected int neq = 0;
  protected Matrix dx;
  protected Matrix Jac;
  protected Matrix fvec;
  protected Matrix uMassTrans;
  protected Matrix uMassTransold;
  protected Matrix Xgij;

  public double[][] molFractionDifference;

  /**
   * <p>
   * Constructor for NonEquilibriumFluidBoundary.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public NonEquilibriumFluidBoundary(SystemInterface system) {
    super(system);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfComponents();
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);
    massTransferCoefficientMatrix[0] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    massTransferCoefficientMatrix[1] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    totalMassTransferCoefficientMatrix[0] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    totalMassTransferCoefficientMatrix[1] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    molFractionDifference =
        new double[2][getBulkSystem().getPhases()[0].getNumberOfComponents() - 1];
    // interphaseSystem = bulkSystem.clone();
    // interphaseOps = new ThermodynamicOperations(interphaseSystem);
    // interphaseOps.TPflash();
    // // interphaseOps.displayResult();
  }

  /**
   * <p>
   * Constructor for NonEquilibriumFluidBoundary.
   * </p>
   *
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public NonEquilibriumFluidBoundary(FlowNodeInterface flowNode) {
    super(flowNode);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfComponents();
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);
    massTransferCoefficientMatrix[0] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    massTransferCoefficientMatrix[1] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    totalMassTransferCoefficientMatrix[0] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    totalMassTransferCoefficientMatrix[1] =
        new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
            getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    // interphaseSystem = bulkSystem.clone();
    molFractionDifference =
        new double[2][getBulkSystem().getPhases()[0].getNumberOfComponents() - 1];
    // interphaseOps = new ThermodynamicOperations(interphaseSystem);
    // interphaseOps.TPflash();
    // // interphaseOps.displayResult();
  }

  /** {@inheritDoc} */
  @Override
  public NonEquilibriumFluidBoundary clone() {
    NonEquilibriumFluidBoundary clonedSystem = null;

    try {
      clonedSystem = (NonEquilibriumFluidBoundary) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    return clonedSystem;
  }

  /**
   * <p>
   * setfvecMassTrans.
   * </p>
   */
  public void setfvecMassTrans() {
    double sumx = 0;
    double sumy = 0;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      fvec.set(i, 0,
          Math.log((interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[0].getComponent(i).getx()))
              - Math.log((interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  * interphaseSystem.getPhases()[1].getComponent(i).getx())));
      sumx += interphaseSystem.getPhases()[0].getComponent(i).getx();
      sumy += interphaseSystem.getPhases()[1].getComponent(i).getx();
    }
    fvec.set(bulkSystem.getPhases()[0].getNumberOfComponents() - 1, 0, 1.0 - sumx);
    fvec.set(bulkSystem.getPhases()[0].getNumberOfComponents(), 0, 1.0 - sumy);

    for (int i = bulkSystem.getPhases()[0].getNumberOfComponents() + 1; i < (2
        * bulkSystem.getPhases()[0].getNumberOfComponents()); i++) {
      fvec.set(i, 0,
          (totalMassTransferCoefficientMatrix[1].get(
              i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1),
              i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1))
              * (bulkSystem.getPhases()[0].getComponents()[i
                  - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)].getx()
                  - interphaseSystem.getPhases()[0].getComponents()[i
                      - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)].getx())
              + (totalMassTransferCoefficientMatrix[0].get(
                  i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1),
                  i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1))
                  * (bulkSystem.getPhases()[1].getComponents()[i
                      - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)].getx()
                      - interphaseSystem.getPhases()[1].getComponents()[i
                          - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)].getx()))));
    }
  }

  /**
   * <p>
   * setfvecMassTrans2.
   * </p>
   */
  public void setfvecMassTrans2() {
    double sumx = 0.0;
    double sumy = 0.0;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      fvec.set(i, 0,
          Math.log((interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[0].getComponent(i).getx()))
              - Math.log((interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  * interphaseSystem.getPhases()[1].getComponent(i).getx())));
      sumx += interphaseSystem.getPhases()[1].getComponent(i).getx();
      sumy += interphaseSystem.getPhases()[0].getComponent(i).getx();
    }

    fvec.set(bulkSystem.getPhases()[0].getNumberOfComponents(), 0, 1.0 - sumx);
    fvec.set(bulkSystem.getPhases()[0].getNumberOfComponents() + 1, 0, 1.0 - sumy);

    Matrix dx = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfComponents() - 1);
    Matrix dy = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfComponents() - 1);
    Matrix x = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfComponents() - 1);
    Matrix y = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfComponents() - 1);

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      dy.set(0, i, (bulkSystem.getPhases()[0].getComponent(i).getx()
          - interphaseSystem.getPhases()[0].getComponent(i).getx()));
      dx.set(0, i, (bulkSystem.getPhases()[1].getComponent(i).getx()
          - interphaseSystem.getPhases()[1].getComponent(i).getx()));
      y.set(0, i, (bulkSystem.getPhases()[0].getComponent(i).getx()));
      x.set(0, i, (bulkSystem.getPhases()[1].getComponent(i).getx()));
    }

    Matrix fluxX = totalMassTransferCoefficientMatrix[1].times(dx.transpose())
        .times(bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
            / bulkSystem.getPhases()[1].getMolarMass());
    Matrix fluxY = totalMassTransferCoefficientMatrix[0].times(dy.transpose())
        .times(bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
            / bulkSystem.getPhases()[0].getMolarMass());

    // fluxX.print(10,10);
    // fluxY.print(10,10);
    // totalMassTransferCoefficientMatrix[0].print(10,10);

    // System.out.println("n flux");
    // nFlux.getMatrix(0,bulkSystem.getPhases()[1].getNumberOfComponents()-2,0,0).print(10,10);
    // System.out.println("j gas flux");
    // fluxY.print(10,10);
    // System.out.println("j gliq flux");
    // fluxX.print(10,10);
    // System.out.println("yn gas flux");
    // y.transpose().times(totalFlux).print(10,10);
    // System.out.println("xn gas flux");
    // x.transpose().times(totalFlux).print(10,10);

    Matrix errX = nFlux.getMatrix(0, bulkSystem.getPhases()[1].getNumberOfComponents() - 2, 0, 0)
        .plus(fluxX).minus(x.transpose().times(totalFlux));
    Matrix errY = nFlux.getMatrix(0, bulkSystem.getPhases()[0].getNumberOfComponents() - 2, 0, 0)
        .minus(fluxY).minus(y.transpose().times(totalFlux));

    for (int i = bulkSystem.getPhases()[0].getNumberOfComponents()
        + 2; i < (2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 1); i++) {
      fvec.set(i, 0, errX.get((i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 2)), 0));
      fvec.set((i + (bulkSystem.getPhases()[0].getNumberOfComponents() - 1)), 0,
          errY.get((i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 2)), 0));
    }

    /*
     * System.out.println("fvec"); fvec.print(30,30); errX.print(10,10); errY.print(10,10);
     */
    // fluxX.print(10,10);
    // fluxY.print(10,10);
    // Matrix fluxY = totalMassTransferCoefficientMatrix[1]
  }

  /**
   * <p>
   * setJacMassTrans.
   * </p>
   */
  public void setJacMassTrans() {
    double dij = 0;
    double tempJ = 0;
    Jac.timesEquals(0.0);

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[0].getComponent(i).getx()
            + interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);

        // tempJ=
        // dij*interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()+interphaseSystem.getPhases()[0].getComponent(i).getx()*interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);
        Jac.set(i, j, tempJ);
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents() - 1, i, -1.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents() - 1,
          bulkSystem.getPhases()[0].getNumberOfComponents() + i, 0.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents(), i, 0.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents(),
          bulkSystem.getPhases()[0].getNumberOfComponents() + i, -1.0);
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[1].getComponent(i).getx()
            + interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);

        // tempJ=
        // dij*interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()+interphaseSystem.getPhases()[1].getComponent(i).getx()*interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);
        Jac.set(i, j + bulkSystem.getPhases()[0].getNumberOfComponents(), -tempJ);
      }
    }

    // this must be changed.....

    for (int i = bulkSystem.getPhases()[0].getNumberOfComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == (j + bulkSystem.getPhases()[0].getNumberOfComponents()) ? 1.0 : 0.0;
        tempJ = -dij * (totalMassTransferCoefficientMatrix[1].get(
            i - bulkSystem.getPhases()[0].getNumberOfComponents(),
            i - bulkSystem.getPhases()[0].getNumberOfComponents()));
        Jac.set(i + 1, j, tempJ);
      }
    }

    for (int i = bulkSystem.getPhases()[0].getNumberOfComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      for (int j = bulkSystem.getPhases()[0].getNumberOfComponents(); j < 2
          * bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0;
        tempJ = -dij * (totalMassTransferCoefficientMatrix[0].get(
            i - bulkSystem.getPhases()[0].getNumberOfComponents(),
            i - bulkSystem.getPhases()[0].getNumberOfComponents()));
        Jac.set(i + 1, j, tempJ);
      }
    }
  }

  /**
   * <p>
   * setJacMassTrans2.
   * </p>
   */
  public void setJacMassTrans2() {
    double dij = 0;
    double tempJ = 0;
    Jac.timesEquals(0.0);

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[0].getComponent(i).getx()
            + interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);
        Jac.set(i, j, tempJ);
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents(), i, 0.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents(),
          bulkSystem.getPhases()[0].getNumberOfComponents() + i, -1.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents() + 1, i, -1.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents() + 1,
          bulkSystem.getPhases()[0].getNumberOfComponents() + i, 0.0);
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[1].getComponent(i).getx()
            + interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);
        Jac.set(i, j + bulkSystem.getPhases()[0].getNumberOfComponents(), -tempJ);
      }
    }

    // this must be changed.....

    for (int i = bulkSystem.getPhases()[0].getNumberOfComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == (j + bulkSystem.getPhases()[0].getNumberOfComponents()) ? 1.0 : 0.0;
        // tempJ =
        // -dij*(totalMassTransferCoefficientMatrix[1].get(i-bulkSystem.getPhases()[0].getNumberOfComponents(),
        // i-bulkSystem.getPhases()[0].getNumberOfComponents()));
        // tempJ = dij *
        // totalMassTransferCoefficientMatrix[0].getRowSum(i-bulkSystem.getPhases()[0].getNumberOfComponents());
        // Jac.set(i+2,j+2*bulkSystem.getPhases()[0].getNumberOfComponents()-1, -
        // interphaseSystem.getPhases()[0].getComponents()[i-bulkSystem.getPhases()[0].getNumberOfComponents()].getx());
        if (j != bulkSystem.getPhases()[0].getNumberOfComponents() - 1) {
          Jac.set(i + 2, j + bulkSystem.getPhases()[0].getNumberOfComponents(),
              -totalMassTransferCoefficientMatrix[1]
                  .get(i - bulkSystem.getPhases()[0].getNumberOfComponents(), j)
                  * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[1].getMolarMass()); // tempJ);
        }
        Jac.set(i + 2, j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents(),
            dij - bulkSystem.getPhases()[1].getComponents()[i
                - bulkSystem.getPhases()[0].getNumberOfComponents()].getx());
      }
    }

    for (int i = bulkSystem.getPhases()[0].getNumberOfComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      for (int j = bulkSystem.getPhases()[0].getNumberOfComponents(); j < 2
          * bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0;
        // tempJ =
        // -dij*(totalMassTransferCoefficientMatrix[1].get(i-bulkSystem.getPhases()[0].getNumberOfComponents(),
        // i-bulkSystem.getPhases()[0].getNumberOfComponents()));
        // tempJ = dij *
        // totalMassTransferCoefficientMatrix[1].getRowSum(i-bulkSystem.getPhases()[0].getNumberOfComponents()*1.0/bulkSystem.getPhases()[1].getMolarVolume());
        if (j != 2 * bulkSystem.getPhases()[0].getNumberOfComponents() - 1) {
          Jac.set(i + 1 + bulkSystem.getPhases()[0].getNumberOfComponents(),
              j - bulkSystem.getPhases()[0].getNumberOfComponents(),
              totalMassTransferCoefficientMatrix[0].get(
                  i - bulkSystem.getPhases()[0].getNumberOfComponents(),
                  j - bulkSystem.getPhases()[0].getNumberOfComponents())
                  * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[0].getMolarMass()); // tempJ);
        }
        Jac.set(i + 1 + bulkSystem.getPhases()[0].getNumberOfComponents(),
            j + bulkSystem.getPhases()[0].getNumberOfComponents(), dij - bulkSystem.getPhases()[0]
                .getComponents()[i - bulkSystem.getPhases()[0].getNumberOfComponents()].getx());
      }
    }
    // System.out.println("jac");
    // Jac.print(10,10);
    // totalMassTransferCoefficientMatrix[0].print(20,20);
    // totalMassTransferCoefficientMatrix[1].print(20,20);
  }

  /**
   * <p>
   * Setter for the field <code>uMassTrans</code>.
   * </p>
   */
  public void setuMassTrans() {
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      uMassTrans.set(i, 0, interphaseSystem.getPhases()[0].getComponent(i).getx());
      uMassTrans.set(i + bulkSystem.getPhases()[0].getNumberOfComponents(), 0,
          interphaseSystem.getPhases()[1].getComponent(i).getx());
    }

    for (int i = 2 * bulkSystem.getPhases()[0].getNumberOfComponents(); i < 3
        * bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      uMassTrans.set(i, 0, nFlux.get(i - 2 * bulkSystem.getPhases()[0].getNumberOfComponents(), 0));
    }
  }

  /**
   * <p>
   * updateMassTrans.
   * </p>
   */
  public void updateMassTrans() {
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      interphaseSystem.getPhases()[0].getComponent(i).setx(uMassTrans.get(i, 0));
      interphaseSystem.getPhases()[1].getComponent(i)
          .setx(uMassTrans.get(i + bulkSystem.getPhases()[0].getNumberOfComponents(), 0));
    }

    for (int i = 2 * bulkSystem.getPhases()[0].getNumberOfComponents(); i < 3
        * bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      nFlux.set(i - 2 * bulkSystem.getPhases()[0].getNumberOfComponents(), 0, uMassTrans.get(i, 0));
    }
  }

  /**
   * <p>
   * calcMolFractionDifference.
   * </p>
   */
  public void calcMolFractionDifference() {
    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents() - 1; i++) {
      molFractionDifference[0][i] = bulkSystem.getPhases()[0].getComponent(i).getx()
          - interphaseSystem.getPhases()[0].getComponent(i).getx();
      molFractionDifference[1][i] = bulkSystem.getPhases()[1].getComponent(i).getx()
          - interphaseSystem.getPhases()[1].getComponent(i).getx();
    }
  }

  /**
   * <p>
   * calcHeatTransferCoefficients.
   * </p>
   *
   * @param phaseNum a int
   */
  public void calcHeatTransferCoefficients(int phaseNum) {
    prandtlNumber[phaseNum] = getBulkSystem().getPhase(phaseNum).getCp()
        / getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
        * getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getViscosity()
        / getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    // System.out.println("prandtlNumber " + prandtlNumber[phase] + " interface " +
    // flowNode.getInterphaseTransportCoefficient().calcInterphaseHeatTransferCoefficient(phase,
    // prandtlNumber[phase], flowNode));
    heatTransferCoefficient[phaseNum] = flowNode.getInterphaseTransportCoefficient()
        .calcInterphaseHeatTransferCoefficient(phaseNum, prandtlNumber[phaseNum], flowNode);
  }

  /**
   * <p>
   * calcHeatTransferCorrection.
   * </p>
   *
   * @param phaseNum a int
   */
  public void calcHeatTransferCorrection(int phaseNum) {
    double temp = 1.0e-20;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      if (Math.abs(nFlux.get(i, 0)) < 1e-20) {
        temp += 0.0;
      } else {
        temp += nFlux.get(i, 0) * getBulkSystem().getPhase(phaseNum).getCp()
            / getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
            / heatTransferCoefficient[phaseNum]; // bulkSystem.getPhases()[0].getComponent(i).getNumberOfMolesInPhase()*
      }
    }
    // System.out.println("temp eat " + temp);
    // System.out.println("temo " + temp);
    if (Math.abs(temp) > 1e-10) {
      heatTransferCorrection[phaseNum] = temp / (Math.exp(temp) - 1.0);
    } else {
      heatTransferCorrection[phaseNum] = 1.0;
    }
    // System.out.println("heat corr. " + heatTransferCorrection[phase]);
  }

  /** {@inheritDoc} */
  @Override
  public void initMassTransferCalc() {
    super.initMassTransferCalc();
  }

  /** {@inheritDoc} */
  @Override
  public void initHeatTransferCalc() {
    super.initHeatTransferCalc();
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    calcMolFractionDifference();
    super.init();
  }

  /** {@inheritDoc} */
  @Override
  public void heatTransSolve() {
    double f = 0;
    double df = 0;
    double dhtot = 0.0;
    int iter = 0;
    do {
      iter++;
      dhtot = 0.0;
      for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
        dhtot += nFlux.get(i, 0) * (bulkSystem.getPhases()[0].getComponent(i)
            .getEnthalpy(bulkSystem.getPhases()[0].getTemperature())
            / bulkSystem.getPhases()[0].getComponent(i).getNumberOfMolesInPhase()
            - bulkSystem.getPhases()[1].getComponent(i)
                .getEnthalpy(bulkSystem.getPhases()[1].getTemperature())
                / bulkSystem.getPhases()[1].getComponent(i).getNumberOfMolesInPhase());
      }
      // System.out.println("dhtot " + dhtot + " heat coef " +
      // heatTransferCoefficient[0]);
      f = heatTransferCoefficient[0] * heatTransferCorrection[0]
          * (bulkSystem.getPhases()[0].getTemperature() - interphaseSystem.getTemperature())
          + heatTransferCoefficient[1] * heatTransferCorrection[1]
              * (bulkSystem.getPhases()[1].getTemperature() - interphaseSystem.getTemperature())
          + dhtot;

      df = -heatTransferCoefficient[0] * heatTransferCorrection[0]
          - heatTransferCoefficient[1] * heatTransferCorrection[1];
      // System.out.println("f " + f + " df " + df);
      // if(interphaseSystem.getTemperature() - f/df>0.0)
      // interphaseSystem.setTemperature(interphaseSystem.getTemperature() - f/df);
      interphaseSystem.setTemperature(interphaseSystem.getTemperature() - f / df);
      // System.out.println("new temp " + interphaseSystem.getTemperature());
    } while (Math.abs(f) > 1e-6 && iter < 100);

    interphaseHeatFlux[0] = -heatTransferCoefficient[0] * heatTransferCorrection[0]
        * (bulkSystem.getPhases()[0].getTemperature() - interphaseSystem.getTemperature());
    interphaseHeatFlux[1] = -heatTransferCoefficient[1] * heatTransferCorrection[1]
        * (bulkSystem.getPhases()[1].getTemperature() - interphaseSystem.getTemperature());
    // System.out.println("heat flox here " + getInterphaseHeatFlux(0) + " heatflux2
    // " + getInterphaseHeatFlux(1));
  }

  /** {@inheritDoc} */
  @Override
  public void massTransSolve() {
    int iter = 1;
    double err = 1.0e10;
    // double oldErr = 0.0;
    double factor = 10.0;
    // if(bulkSystem.isChemicalSystem()) factor=100.0;
    setuMassTrans();
    do {
      // oldErr = err;
      iter++;
      init();
      setfvecMassTrans2();
      setJacMassTrans2();
      dx = Jac.solve(fvec);
      if (!Double.valueOf(dx.norm2()).isNaN()) {
        uMassTrans.minusEquals(dx.times(iter / (iter + factor)));
        err = Math.abs(dx.norm2() / uMassTrans.norm2());
        updateMassTrans();
        calcFluxes();
      } else {
        System.out.println("dx: NaN in mass trans calc");
        err = 10;
      }
      // System.out.println("err " + err);
    } while ((err > 1.e-4 && iter < 100) || iter < 5);
    calcFluxes();
  }

  /** {@inheritDoc} */
  @Override
  public double[] calcFluxes() {
    double sum = 0.0;
    // System.out.println("starter...");
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfComponents(); i++) {
      sum += nFlux.get(i, 0);
    }

    totalFlux = sum; // tot.get(0,0);
    // System.out.println("total flux " + totalFlux);
    return nFlux.transpose().getArray()[0];
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    // interphaseSystem = bulkSystem.clone();
    initInterphaseSystem();
    init();
    int iterOuter = 0;
    int iterInner = 0;
    double totalFluxOld = totalFlux;

    double heatFlux = 0;
    double oldHeatFlux = 0.0;
    if (heatTransferCalc) {
      this.heatTransSolve();
      heatFlux = this.getInterphaseHeatFlux(0);
    }

    do {
      iterInner++;
      iterOuter = 0;
      oldHeatFlux = heatFlux;
      do {
        iterOuter++;
        totalFluxOld = totalFlux;

        if (massTransferCalc) {
          massTransSolve();
        }
      } while (Math.abs((totalFluxOld - totalFlux) / totalFlux) > 1e-6 && iterOuter < 50);

      if (heatTransferCalc) {
        this.heatTransSolve();
        heatFlux = this.getInterphaseHeatFlux(0);
      }
    } while (Math.abs((oldHeatFlux - heatFlux) / heatFlux) > 1e-6 && heatTransferCalc
        && iterInner < 50);
    init();
    // System.out.println("iterInner " +iterInner + " temp gas " +
    // interphaseSystem.getTemperature(0)+ " temp liq " +
    // interphaseSystem.getTemperature(1));
  }
}
