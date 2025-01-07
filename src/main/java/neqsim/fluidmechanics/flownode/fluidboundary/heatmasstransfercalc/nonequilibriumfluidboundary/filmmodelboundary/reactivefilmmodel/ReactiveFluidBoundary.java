package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract ReactiveFluidBoundary class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class ReactiveFluidBoundary extends KrishnaStandartFilmModel {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ReactiveFluidBoundary.class);
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

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
   * Constructor for ReactiveFluidBoundary.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ReactiveFluidBoundary(SystemInterface system) {
    super(system);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents();
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);

    // massTransferCoefficientMatrix[0] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // massTransferCoefficientMatrix[1] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // totalMassTransferCoefficientMatrix[0] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // totalMassTransferCoefficientMatrix[1] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // molFractionDifference = new
    // double[2][getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1];
  }

  /**
   * <p>
   * Constructor for ReactiveFluidBoundary.
   * </p>
   *
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public ReactiveFluidBoundary(FlowNodeInterface flowNode) {
    super(flowNode);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents();
    Jac = new Matrix(neq, neq);
    fvec = new Matrix(neq, 1);

    // massTransferCoefficientMatrix[0] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // massTransferCoefficientMatrix[1] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // totalMassTransferCoefficientMatrix[0] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // totalMassTransferCoefficientMatrix[1] = new
    // Matrix(getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1,getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1);
    // interphaseSystem = bulkSystem.clone();
    // molFractionDifference = new
    // double[2][getBulkSystem().getPhases()[0].getNumberOfMolecularComponents()-1];
  }

  /** {@inheritDoc} */
  @Override
  public ReactiveFluidBoundary clone() {
    ReactiveFluidBoundary clonedSystem = null;

    try {
      clonedSystem = (ReactiveFluidBoundary) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setfvecMassTrans() {
    double sumx = 0;
    double sumy = 0;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      fvec.set(i, 0,
          Math.log((interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[0].getComponent(i).getx()))
              - Math.log((interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  * interphaseSystem.getPhases()[1].getComponent(i).getx())));
      sumx += interphaseSystem.getPhases()[0].getComponent(i).getx();
      sumy += interphaseSystem.getPhases()[1].getComponent(i).getx();
    }
    fvec.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1, 0, 1 - sumx);
    fvec.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0, 1 - sumy);

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1; i < (2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents()); i++) {
      fvec.set(i, 0,
          (totalMassTransferCoefficientMatrix[1].get(
              i - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1),
              i - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1))
              * (bulkSystem.getPhases()[0].getComponents()[i
                  - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1)].getx()
                  - interphaseSystem.getPhases()[0].getComponents()[i
                      - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1)].getx())
              + (totalMassTransferCoefficientMatrix[0].get(
                  i - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1),
                  i - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1))
                  * (bulkSystem.getPhases()[1].getComponents()[i
                      - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1)].getx()
                      - interphaseSystem.getPhases()[1].getComponents()[i
                          - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1)]
                              .getx()))));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setfvecMassTrans2() {
    double sumx = 0.0;
    double sumy = 0.0;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      fvec.set(i, 0,
          Math.log((interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[0].getComponent(i).getx()))
              - Math.log((interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()
                  * interphaseSystem.getPhases()[1].getComponent(i).getx())));
      sumx += interphaseSystem.getPhases()[1].getComponent(i).getx();
      sumy += interphaseSystem.getPhases()[0].getComponent(i).getx();
    }

    fvec.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0, 1.0 - sumx);
    fvec.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1, 0, 1.0 - sumy);

    Matrix dx = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1);
    Matrix dy = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1);
    Matrix x = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1);
    Matrix y = new Matrix(1, bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1);

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
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
    // nFlux.getMatrix(0,bulkSystem.getPhases()[1].getNumberOfMolecularComponents()-2,0,0).print(10,10);
    // System.out.println("j gas flux");
    // fluxY.print(10,10);
    // System.out.println("j gliq flux");
    // fluxX.print(10,10);
    // System.out.println("yn gas flux");
    // y.transpose().times(totalFlux).print(10,10);
    // System.out.println("xn gas flux");
    // x.transpose().times(totalFlux).print(10,10);

    Matrix errX =
        nFlux.getMatrix(0, bulkSystem.getPhases()[1].getNumberOfMolecularComponents() - 2, 0, 0)
            .plus(fluxX).minus(x.transpose().times(totalFlux));
    Matrix errY =
        nFlux.getMatrix(0, bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 2, 0, 0)
            .minus(fluxY).minus(y.transpose().times(totalFlux));

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents()
        + 2; i < (2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1); i++) {
      fvec.set(i, 0,
          errX.get((i - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 2)), 0));
      fvec.set((i + (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1)), 0,
          errY.get((i - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 2)), 0));
    }

    /*
     * System.out.println("fvec"); fvec.print(30,30); errX.print(10,10); errY.print(10,10);
     */
    // fluxX.print(10,10);
    // fluxY.print(10,10);
    // Matrix fluxY = totalMassTransferCoefficientMatrix[1]
  }

  /** {@inheritDoc} */
  @Override
  public void setJacMassTrans() {
    double dij = 0;
    double tempJ = 0;
    Jac.timesEquals(0.0);

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[0].getComponent(i).getx()
            + interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);

        // tempJ=
        // dij*interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()+interphaseSystem.getPhases()[0].getComponent(i).getx()*interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);
        Jac.set(i, j, tempJ);
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1, i, -1.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1,
          bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + i, 0.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), i, 0.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
          bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + i, -1.0);
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[1].getComponent(i).getx()
            + interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);

        // tempJ=
        // dij*interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()+interphaseSystem.getPhases()[1].getComponent(i).getx()*interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);
        Jac.set(i, j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), -tempJ);
      }
    }

    // this must be changed.....

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == (j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()) ? 1.0 : 0.0;
        tempJ = -dij * (totalMassTransferCoefficientMatrix[1].get(
            i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
            i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()));
        Jac.set(i + 1, j, tempJ);
      }
    }

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j < 2
          * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0;
        tempJ = -dij * (totalMassTransferCoefficientMatrix[0].get(
            i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
            i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()));
        Jac.set(i + 1, j, tempJ);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setJacMassTrans2() {
    double dij = 0;
    double tempJ = 0;
    Jac.timesEquals(0.0);

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[0].getComponent(i).getx()
            + interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);
        Jac.set(i, j, tempJ);
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), i, 0.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
          bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + i, -1.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1, i, -1.0);
      Jac.set(bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1,
          bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + i, 0.0);
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[1].getComponent(i).getx()
            + interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);
        Jac.set(i, j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), -tempJ);
      }
    }

    // this must be changed.....

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == (j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()) ? 1.0 : 0.0;
        // tempJ =
        // -dij*(totalMassTransferCoefficientMatrix[1].get(i-bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
        // i-bulkSystem.getPhases()[0].getNumberOfMolecularComponents()));
        // tempJ = dij *
        // totalMassTransferCoefficientMatrix[0].getRowSum(i-bulkSystem.getPhases()[0].getNumberOfMolecularComponents());
        // Jac.set(i+2,j+2*bulkSystem.getPhases()[0].getNumberOfMolecularComponents()-1,
        // -
        // interphaseSystem.getPhases()[0].getComponents()[i-bulkSystem.getPhases()[0].getNumberOfMolecularComponents()].getx());
        if (j != bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1) {
          Jac.set(i + 2, j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
              -totalMassTransferCoefficientMatrix[1]
                  .get(i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), j)
                  * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[1].getMolarMass()); // tempJ);
        }
        Jac.set(i + 2, j + 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
            dij - bulkSystem.getPhases()[1].getComponents()[i
                - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()].getx());
      }
    }

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j < 2
          * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0;
        // tempJ =
        // -dij*(totalMassTransferCoefficientMatrix[1].get(i-bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
        // i-bulkSystem.getPhases()[0].getNumberOfMolecularComponents()));
        // tempJ = dij *
        // totalMassTransferCoefficientMatrix[1].getRowSum(i-bulkSystem.getPhases()[0].getNumberOfMolecularComponents()*1.0/bulkSystem.getPhases()[1].getMolarVolume());
        if (j != 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1) {
          Jac.set(i + 1 + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
              j - bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
              totalMassTransferCoefficientMatrix[0].get(
                  i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
                  j - bulkSystem.getPhases()[0].getNumberOfMolecularComponents())
                  * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[0].getMolarMass()); // tempJ);
        }
        Jac.set(i + 1 + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
            j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(),
            dij - bulkSystem.getPhases()[0].getComponents()[i
                - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()].getx());
      }
    }
    // System.out.println("jac");
    // Jac.print(10,10);
    // totalMassTransferCoefficientMatrix[0].print(20,20);
  }

  /** {@inheritDoc} */
  @Override
  public void setuMassTrans() {
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      // System.out.println("i");
      uMassTrans.set(i, 0, interphaseSystem.getPhases()[0].getComponent(i).getx());
      uMassTrans.set(i + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0,
          interphaseSystem.getPhases()[1].getComponent(i).getx());
    }

    for (int i = 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 3
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      uMassTrans.set(i, 0,
          nFlux.get(i - 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void updateMassTrans() {
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      interphaseSystem.getPhases()[0].getComponent(i).setx(uMassTrans.get(i, 0));
      interphaseSystem.getPhases()[1].getComponent(i)
          .setx(uMassTrans.get(i + bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0));
    }

    for (int i = 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 3
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      nFlux.set(i - 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0,
          uMassTrans.get(i, 0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcMolFractionDifference() {
    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      molFractionDifference[0][i] = bulkSystem.getPhases()[0].getComponent(i).getx()
          - interphaseSystem.getPhases()[0].getComponent(i).getx();
      molFractionDifference[1][i] = bulkSystem.getPhases()[1].getComponent(i).getx()
          - interphaseSystem.getPhases()[1].getComponent(i).getx();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcHeatTransferCoefficients(int phaseNum) {
    prandtlNumber[phaseNum] = getBulkSystem().getPhase(phaseNum).getCp()
        * getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getViscosity()
        / getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    heatTransferCoefficient[phaseNum] = flowNode.getInterphaseTransportCoefficient()
        .calcInterphaseHeatTransferCoefficient(phaseNum, prandtlNumber[phaseNum], flowNode);
  }

  /** {@inheritDoc} */
  @Override
  public void calcHeatTransferCorrection(int phaseNum) {
    double temp = 0;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      temp += bulkSystem.getPhases()[0].getComponent(i).getNumberOfMolesInPhase()
          * getBulkSystem().getPhase(phaseNum).getCp() / heatTransferCoefficient[phaseNum];
    }
    heatTransferCorrection[phaseNum] = temp;
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
    do {
      dhtot = 0.0;
      for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
        dhtot += nFlux.get(i, 0) * (bulkSystem.getPhases()[0].getComponent(i)
            .getEnthalpy(bulkSystem.getPhases()[0].getTemperature())
            - bulkSystem.getPhases()[1].getComponent(i)
                .getEnthalpy(bulkSystem.getPhases()[1].getTemperature()));
      }

      f = heatTransferCoefficient[0] * heatTransferCorrection[0]
          * (bulkSystem.getPhases()[0].getTemperature() - interphaseSystem.getTemperature())
          + heatTransferCoefficient[1] * heatTransferCorrection[1]
              * (bulkSystem.getPhases()[1].getTemperature() - interphaseSystem.getTemperature())
          + dhtot;

      df = -heatTransferCoefficient[0] * heatTransferCorrection[0]
          - heatTransferCoefficient[1] * heatTransferCorrection[1];
      interphaseSystem.setTemperature(interphaseSystem.getTemperature() - f / df);

      // System.out.println("f " + f);
      // System.out.println("int temp " + interphaseSystem.getTemperature());
      // System.out.println("gas temp " + bulkSystem.getPhases()[0].getTemperature());
      // System.out.println("liq temp " + bulkSystem.getPhases()[1].getTemperature());
    } while (Math.abs(f) > 1e-10);
  }

  /** {@inheritDoc} */
  @Override
  public void massTransSolve() {
    int iter = 0;
    setuMassTrans();

    do {
      iter++;
      init();
      setfvecMassTrans2();
      setJacMassTrans2();
      dx = Jac.solve(fvec);
      uMassTrans.minusEquals(dx.times(0.1));
      updateMassTrans();
      calcFluxes();
      // uMassTrans.print(30,30);
      dx.print(30, 30);
      // System.out.println("err " + Math.abs(dx.norm2()/uMassTrans.norm2()));
    } while (Math.abs(dx.norm2() / uMassTrans.norm2()) > 1.e-10 && iter < 50);
    // System.out.println("iter " + iter);

    calcFluxes2();

    // uMassTrans.print(30,30);
  }

  /** {@inheritDoc} */
  @Override
  public double[] calcFluxes() {
    double sum = 0.0;
    // System.out.println("starter...");
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      sum += nFlux.get(i, 0);
    }

    totalFlux = sum; // tot.get(0,0);
    return nFlux.transpose().getArray()[0];
  }

  /**
   * <p>
   * calcFluxes2.
   * </p>
   *
   * @return an array of type double
   */
  public double[] calcFluxes2() {
    double sum = 0.0;
    // System.out.println("starter...");
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      sum += nFlux.get(i, 0);
      // System.out.println("n " + nFlux.get(i,0) );
    }

    totalFlux = sum; // tot.get(0,0);
    return nFlux.transpose().getArray()[0];
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    int iterOuter = 0;
    double totalFluxOld = totalFlux;
    do {
      iterOuter++;
      totalFluxOld = totalFlux;
      if (massTransferCalc) {
        massTransSolve();
      }

      if (heatTransferCalc) {
        // System.out.println("heat ");
        this.heatTransSolve();
      }
      // System.out.println("flux err: " + Math.abs(totalFluxOld-totalFlux));
    } while (Math.abs((totalFluxOld - totalFlux) / totalFlux) > 1e-10 && iterOuter < 55);

    // System.out.println("iterOuter " +iterOuter);
    init();
  }
}
