package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel;
import neqsim.thermo.system.SystemInterface;

/**
 * Abstract ReactiveFluidBoundary class.
 *
 * <p>
 * Reactive Newton state uses primitive arrays. Temporary Jama adapters retain the legacy linear-solve and vector-norm
 * behavior. Serialized instances containing the former Jama working fields are not compatible with this layout.
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
  protected double[] dx;
  protected double[][] Jac;
  protected double[] fvec;
  protected double[] uMassTrans;
  protected double[] uMassTransold;
  protected double[][] Xgij;

  public double[][] molFractionDifference;

  /**
   * Constructor for ReactiveFluidBoundary.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ReactiveFluidBoundary(SystemInterface system) {
    super(system);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents();
    Jac = new double[neq][neq];
    fvec = new double[neq];
    dx = new double[neq];
    Xgij = new double[neq][4];
    uMassTransold = uMassTrans.clone();

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
   * Constructor for ReactiveFluidBoundary.
   *
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public ReactiveFluidBoundary(FlowNodeInterface flowNode) {
    super(flowNode);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents();
    Jac = new double[neq][neq];
    fvec = new double[neq];
    dx = new double[neq];
    Xgij = new double[neq][4];
    uMassTransold = uMassTrans.clone();

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
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setfvecMassTrans() {
    double sumx = 0;
    double sumy = 0;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      fvec[i] = Math
          .log((interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[0].getComponent(i).getx()))
          - Math.log((interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[1].getComponent(i).getx()));
      sumx += interphaseSystem.getPhases()[0].getComponent(i).getx();
      sumy += interphaseSystem.getPhases()[1].getComponent(i).getx();
    }
    fvec[bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1] = 1 - sumx;
    fvec[bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = 1 - sumy;

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1; i < (2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents()); i++) {
      int component = i - (bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1);
      fvec[i] = totalMassTransferCoefficientMatrix[1][component][component]
          * (bulkSystem.getPhase(0).getComponent(component).getx()
              - interphaseSystem.getPhase(0).getComponent(component).getx())
          + totalMassTransferCoefficientMatrix[0][component][component]
              * (bulkSystem.getPhase(1).getComponent(component).getx()
                  - interphaseSystem.getPhase(1).getComponent(component).getx());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setfvecMassTrans2() {
    double sumx = 0.0;
    double sumy = 0.0;
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      fvec[i] = Math
          .log((interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[0].getComponent(i).getx()))
          - Math.log((interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()
              * interphaseSystem.getPhases()[1].getComponent(i).getx()));
      sumx += interphaseSystem.getPhases()[1].getComponent(i).getx();
      sumy += interphaseSystem.getPhases()[0].getComponent(i).getx();
    }

    fvec[bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = 1.0 - sumx;
    fvec[bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1] = 1.0 - sumy;

    int componentCount = bulkSystem.getPhases()[0].getNumberOfMolecularComponents();
    double densityX = bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[1].getMolarMass();
    double densityY = bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[0].getMolarMass();
    for (int componentIndex = 0; componentIndex < componentCount - 1; componentIndex++) {
      double fluxX = 0.0;
      double fluxY = 0.0;
      double xFraction = bulkSystem.getPhases()[1].getComponent(componentIndex).getx();
      double yFraction = bulkSystem.getPhases()[0].getComponent(componentIndex).getx();
      for (int differenceIndex = 0; differenceIndex < componentCount - 1; differenceIndex++) {
        double dx = bulkSystem.getPhases()[1].getComponent(differenceIndex).getx()
            - interphaseSystem.getPhases()[1].getComponent(differenceIndex).getx();
        double dy = bulkSystem.getPhases()[0].getComponent(differenceIndex).getx()
            - interphaseSystem.getPhases()[0].getComponent(differenceIndex).getx();
        fluxX += totalMassTransferCoefficientMatrix[1][componentIndex][differenceIndex] * dx;
        fluxY += totalMassTransferCoefficientMatrix[0][componentIndex][differenceIndex] * dy;
      }
      fluxX *= densityX;
      fluxY *= densityY;
      double errX = nFlux.get(componentIndex, 0) + fluxX - xFraction * totalFlux;
      double errY = nFlux.get(componentIndex, 0) - fluxY - yFraction * totalFlux;
      fvec[componentCount + 2 + componentIndex] = errX;
      fvec[2 * componentCount + 1 + componentIndex] = errY;
    }

    /*
     * System.out.println("fvec"); fvec.print(30,30); errX.print(10,10); errY.print(10,10);
     */
  }

  /** {@inheritDoc} */
  @Override
  public void setJacMassTrans() {
    double dij = 0;
    double tempJ = 0;
    for (double[] row : Jac) {
      for (int column = 0; column < row.length; column++) {
        row[column] *= 0.0;
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[0].getComponent(i).getx()
            + interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);

        // tempJ=
        // dij*interphaseSystem.getPhases()[0].getComponent(i).getFugacityCoefficient()+interphaseSystem.getPhases()[0].getComponent(i).getx()*interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);
        Jac[i][j] = tempJ;
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1][i] = -1.0;
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1][bulkSystem.getPhases()[0]
          .getNumberOfMolecularComponents() + i] = 0.0;
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][i] = 0.0;
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][bulkSystem.getPhases()[0]
          .getNumberOfMolecularComponents() + i] = -1.0;
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[1].getComponent(i).getx()
            + interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);

        // tempJ=
        // dij*interphaseSystem.getPhases()[1].getComponent(i).getFugacityCoefficient()+interphaseSystem.getPhases()[1].getComponent(i).getx()*interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);
        Jac[i][j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = -tempJ;
      }
    }

    // this must be changed.....

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == (j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()) ? 1.0 : 0.0;
        tempJ = -dij
            * totalMassTransferCoefficientMatrix[1][i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][i
                - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()];
        Jac[i + 1][j] = tempJ;
      }
    }

    for (int i = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents() - 1; i++) {
      for (int j = bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j < 2
          * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0;
        tempJ = -dij
            * totalMassTransferCoefficientMatrix[0][i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][i
                - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()];
        Jac[i + 1][j] = tempJ;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setJacMassTrans2() {
    double dij = 0;
    double tempJ = 0;
    for (double[] row : Jac) {
      for (int column = 0; column < row.length; column++) {
        row[column] *= 0.0;
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[0].getComponent(i).getx()
            + interphaseSystem.getPhases()[0].getComponent(i).getdfugdx(j);
        Jac[i][j] = tempJ;
      }
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][i] = 0.0;
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][bulkSystem.getPhases()[0]
          .getNumberOfMolecularComponents() + i] = -1.0;
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1][i] = -1.0;
      Jac[bulkSystem.getPhases()[0].getNumberOfMolecularComponents() + 1][bulkSystem.getPhases()[0]
          .getNumberOfMolecularComponents() + i] = 0.0;
    }

    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta
        tempJ = dij * 1.0 / interphaseSystem.getPhases()[1].getComponent(i).getx()
            + interphaseSystem.getPhases()[1].getComponent(i).getdfugdx(j);
        Jac[i][j + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = -tempJ;
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
          Jac[i + 2][j
              + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = -totalMassTransferCoefficientMatrix[1][i
                  - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][j]
                  * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[1].getMolarMass();
        }
        Jac[i + 2][j + 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = dij
            - bulkSystem.getPhases()[1].getComponents()[i - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()]
                .getx();
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
          Jac[i + 1 + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][j
              - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = totalMassTransferCoefficientMatrix[0][i
                  - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][j
                      - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()]
                  * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[0].getMolarMass();
        }
        Jac[i + 1 + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()][j
            + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = dij
                - bulkSystem.getPhases()[0].getComponents()[i
                    - bulkSystem.getPhases()[0].getNumberOfMolecularComponents()].getx();
      }
    }
    // System.out.println("jac");
    // Jac.print(10,10);
    // totalMassTransferCoefficientMatrix[0].print(20,20);
  }

  /** {@inheritDoc} */
  @Override
  public void setuMassTrans() {
    ensureReactiveState();
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      // System.out.println("i");
      uMassTrans[i] = interphaseSystem.getPhases()[0].getComponent(i).getx();
      uMassTrans[i + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()] = interphaseSystem.getPhases()[1]
          .getComponent(i).getx();
    }

    for (int i = 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 3
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      uMassTrans[i] = nFlux.get(i - 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0);
    }
  }

  /** Initializes subclass state when invoked before the reactive constructor body has run. */
  private void ensureReactiveState() {
    if (uMassTrans == null) {
      int componentCount = bulkSystem.getPhase(0).getNumberOfMolecularComponents();
      uMassTrans = new double[3 * componentCount];
      uMassTransold = new double[uMassTrans.length];
      molFractionDifference = new double[2][componentCount - 1];
    }
  }

  /** {@inheritDoc} */
  @Override
  public void updateMassTrans() {
    for (int i = 0; i < bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      interphaseSystem.getPhases()[0].getComponent(i).setx(uMassTrans[i]);
      interphaseSystem.getPhases()[1].getComponent(i)
          .setx(uMassTrans[i + bulkSystem.getPhases()[0].getNumberOfMolecularComponents()]);
    }

    for (int i = 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i < 3
        * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(); i++) {
      nFlux.set(i - 2 * bulkSystem.getPhases()[0].getNumberOfMolecularComponents(), 0, uMassTrans[i]);
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
        dhtot += nFlux.get(i, 0)
            * (bulkSystem.getPhases()[0].getComponent(i).getEnthalpy(bulkSystem.getPhases()[0].getTemperature())
                - bulkSystem.getPhases()[1].getComponent(i).getEnthalpy(bulkSystem.getPhases()[1].getTemperature()));
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
      dx = solveReactiveJacobianStep();
      applyReactiveNewtonStep(dx);
      updateMassTrans();
      calcFluxes();
      // uMassTrans.print(30,30);
      logger.debug("Reactive boundary Newton step: {}", Arrays.toString(dx));
      // System.out.println("err " + Math.abs(dx.norm2()/uMassTrans.norm2()));
    } while (Math.abs(reactiveVectorNorm(dx) / reactiveVectorNorm(uMassTrans)) > 1.e-10 && iter < 50);
    // System.out.println("iter " + iter);

    calcFluxes2();

    // uMassTrans.print(30,30);
  }

  /**
   * Solves the array-backed reactive Jacobian using the existing numerical backend.
   *
   * @return independent Newton update vector
   */
  protected double[] solveReactiveJacobianStep() {
    return new Matrix(Jac).solve(new Matrix(fvec, fvec.length)).getColumnPackedCopy();
  }

  /**
   * Applies the existing fixed reactive Newton damping to the primary array state.
   *
   * @param step Newton update vector
   */
  protected void applyReactiveNewtonStep(double[] step) {
    for (int equation = 0; equation < uMassTrans.length; equation++) {
      uMassTrans[equation] -= step[equation] * 0.1;
    }
  }

  /**
   * Evaluates the legacy column-vector norm without retaining Matrix storage.
   *
   * @param values vector values
   * @return Euclidean norm calculated by the legacy backend
   */
  private static double reactiveVectorNorm(double[] values) {
    return new Matrix(values, values.length).norm2();
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
    return nFlux.getData().clone();
  }

  /**
   * calcFluxes2.
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
    return nFlux.getData().clone();
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
