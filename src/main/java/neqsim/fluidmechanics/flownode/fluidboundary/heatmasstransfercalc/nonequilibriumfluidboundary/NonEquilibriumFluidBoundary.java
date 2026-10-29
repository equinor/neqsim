package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.decomposition.lu.LUDecompositionAlt_DDRM;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Abstract NonEquilibriumFluidBoundary class.
 *
 * <p>
 * Newton working matrices use EJML storage. Subclasses using these protected fields must use {@link DMatrixRMaj};
 * serialized instances containing the former Jama working matrices are not compatible with this representation.
 * Coefficient data use primitive arrays; deprecated exported EJML views retain their live behavior.
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class NonEquilibriumFluidBoundary
    extends neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundary {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NonEquilibriumFluidBoundary.class);

  protected int neq = 0;
  protected DMatrixRMaj dx;
  protected DMatrixRMaj Jac;
  protected DMatrixRMaj fvec;
  protected DMatrixRMaj uMassTrans;
  protected DMatrixRMaj uMassTransold;
  protected DMatrixRMaj Xgij;

  public double[][] molFractionDifference;

  /**
   * Constructor for NonEquilibriumFluidBoundary.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public NonEquilibriumFluidBoundary(SystemInterface system) {
    super(system);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfComponents();
    Jac = new DMatrixRMaj(neq, neq);
    fvec = new DMatrixRMaj(neq, 1);
    int independentComponents = getBulkSystem().getPhases()[0].getNumberOfComponents() - 1;
    setMassTransferCoefficientArray(0, new double[independentComponents][independentComponents]);
    setMassTransferCoefficientArray(1, new double[independentComponents][independentComponents]);
    totalMassTransferCoefficientMatrix[0] = new double[independentComponents][independentComponents];
    totalMassTransferCoefficientMatrix[1] = new double[independentComponents][independentComponents];
    molFractionDifference = new double[2][getBulkSystem().getPhases()[0].getNumberOfComponents() - 1];
    // interphaseSystem = bulkSystem.clone();
    // interphaseOps = new ThermodynamicOperations(interphaseSystem);
    // interphaseOps.TPflash();
    // // interphaseOps.displayResult();
  }

  /**
   * Constructor for NonEquilibriumFluidBoundary.
   *
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public NonEquilibriumFluidBoundary(FlowNodeInterface flowNode) {
    super(flowNode);
    neq = 3 * bulkSystem.getPhases()[0].getNumberOfComponents();
    Jac = new DMatrixRMaj(neq, neq);
    fvec = new DMatrixRMaj(neq, 1);
    int independentComponents = getBulkSystem().getPhases()[0].getNumberOfComponents() - 1;
    setMassTransferCoefficientArray(0, new double[independentComponents][independentComponents]);
    setMassTransferCoefficientArray(1, new double[independentComponents][independentComponents]);
    totalMassTransferCoefficientMatrix[0] = new double[independentComponents][independentComponents];
    totalMassTransferCoefficientMatrix[1] = new double[independentComponents][independentComponents];
    // interphaseSystem = bulkSystem.clone();
    molFractionDifference = new double[2][getBulkSystem().getPhases()[0].getNumberOfComponents() - 1];
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
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }

  /**
   * setfvecMassTrans.
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
      fvec.set(i, 0, (totalMassTransferCoefficientMatrix[1][i
          - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)][i
              - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)]
          * (bulkSystem.getPhases()[0].getComponents()[i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)]
              .getx()
              - interphaseSystem.getPhases()[0].getComponents()[i
                  - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)].getx())
          + (totalMassTransferCoefficientMatrix[0][i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)][i
              - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)]
              * (bulkSystem.getPhases()[1].getComponents()[i - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)]
                  .getx()
                  - interphaseSystem.getPhases()[1].getComponents()[i
                      - (bulkSystem.getPhases()[0].getNumberOfComponents() + 1)].getx()))));
    }
  }

  /**
   * setfvecMassTrans2.
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

    int componentCount = bulkSystem.getPhases()[0].getNumberOfComponents();
    double densityX = bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[1].getMolarMass();
    double densityY = bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[0].getMolarMass();
    for (int componentIndex = 0; componentIndex < componentCount - 1; componentIndex++) {
      double fluxX = 0.0;
      double fluxY = 0.0;
      double x = bulkSystem.getPhases()[1].getComponent(componentIndex).getx();
      double y = bulkSystem.getPhases()[0].getComponent(componentIndex).getx();
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
      double errX = nFlux.get(componentIndex, 0) + fluxX - x * totalFlux;
      double errY = nFlux.get(componentIndex, 0) - fluxY - y * totalFlux;
      fvec.set(componentCount + 2 + componentIndex, 0, errX);
      fvec.set(2 * componentCount + 1 + componentIndex, 0, errY);
    }
  }

  /**
   * setJacMassTrans.
   */
  public void setJacMassTrans() {
    double dij = 0;
    double tempJ = 0;
    CommonOps_DDRM.scale(0.0, Jac);

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
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents(), bulkSystem.getPhases()[0].getNumberOfComponents() + i,
          -1.0);
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
        tempJ = -dij * totalMassTransferCoefficientMatrix[1][i - bulkSystem.getPhases()[0].getNumberOfComponents()][i
            - bulkSystem.getPhases()[0].getNumberOfComponents()];
        Jac.set(i + 1, j, tempJ);
      }
    }

    for (int i = bulkSystem.getPhases()[0].getNumberOfComponents(); i < 2
        * bulkSystem.getPhases()[0].getNumberOfComponents() - 1; i++) {
      for (int j = bulkSystem.getPhases()[0].getNumberOfComponents(); j < 2
          * bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        dij = i == j ? 1.0 : 0.0;
        tempJ = -dij * totalMassTransferCoefficientMatrix[0][i - bulkSystem.getPhases()[0].getNumberOfComponents()][i
            - bulkSystem.getPhases()[0].getNumberOfComponents()];
        Jac.set(i + 1, j, tempJ);
      }
    }
  }

  /**
   * setJacMassTrans2.
   */
  public void setJacMassTrans2() {
    double dij = 0;
    double tempJ = 0;
    CommonOps_DDRM.scale(0.0, Jac);

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
      Jac.set(bulkSystem.getPhases()[0].getNumberOfComponents(), bulkSystem.getPhases()[0].getNumberOfComponents() + i,
          -1.0);
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
              -totalMassTransferCoefficientMatrix[1][i - bulkSystem.getPhases()[0].getNumberOfComponents()][j]
                  * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[1].getMolarMass()); // tempJ);
        }
        Jac.set(i + 2, j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents(), dij
            - bulkSystem.getPhases()[1].getComponents()[i - bulkSystem.getPhases()[0].getNumberOfComponents()].getx());
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
              totalMassTransferCoefficientMatrix[0][i - bulkSystem.getPhases()[0].getNumberOfComponents()][j
                  - bulkSystem.getPhases()[0].getNumberOfComponents()]
                  * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
                  / bulkSystem.getPhases()[0].getMolarMass()); // tempJ);
        }
        Jac.set(i + 1 + bulkSystem.getPhases()[0].getNumberOfComponents(),
            j + bulkSystem.getPhases()[0].getNumberOfComponents(),
            dij - bulkSystem.getPhases()[0].getComponents()[i - bulkSystem.getPhases()[0].getNumberOfComponents()]
                .getx());
      }
    }
    // System.out.println("jac");
    // Jac.print(10,10);
    // totalMassTransferCoefficientMatrix[0].print(20,20);
    // totalMassTransferCoefficientMatrix[1].print(20,20);
  }

  /**
   * Setter for the field <code>uMassTrans</code>.
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
   * updateMassTrans.
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
   * calcMolFractionDifference.
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
   * calcHeatTransferCoefficients.
   *
   * @param phaseNum a int
   */
  public void calcHeatTransferCoefficients(int phaseNum) {
    // Pr = Cp_mass * mu / k, where Cp_mass = Cp_total / (n * M)
    prandtlNumber[phaseNum] = getBulkSystem().getPhase(phaseNum).getCp()
        / getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
        / getBulkSystem().getPhase(phaseNum).getMolarMass()
        * getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getViscosity()
        / getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    // System.out.println("prandtlNumber " + prandtlNumber[phase] + " interface " +
    // flowNode.getInterphaseTransportCoefficient().calcInterphaseHeatTransferCoefficient(phase,
    // prandtlNumber[phase], flowNode));
    heatTransferCoefficient[phaseNum] = flowNode.getInterphaseTransportCoefficient()
        .calcInterphaseHeatTransferCoefficient(phaseNum, prandtlNumber[phaseNum], flowNode);
  }

  /**
   * calcHeatTransferCorrection.
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
            / getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase() / heatTransferCoefficient[phaseNum]; // bulkSystem.getPhases()[0].getComponent(i).getNumberOfMolesInPhase()*
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
        dhtot += nFlux.get(i, 0)
            * (bulkSystem.getPhases()[0].getComponent(i).getEnthalpy(bulkSystem.getPhases()[0].getTemperature())
                / bulkSystem.getPhases()[0].getComponent(i).getNumberOfMolesInPhase()
                - bulkSystem.getPhases()[1].getComponent(i).getEnthalpy(bulkSystem.getPhases()[1].getTemperature())
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
      // Guard against zero derivative to prevent NaN/Infinity temperature
      if (Math.abs(df) < 1e-30) {
        break;
      }
      double step = f / df;
      // Clamp step to avoid extreme temperature jumps
      double maxStep = 50.0; // max 50 K per iteration
      if (step > maxStep) {
        step = maxStep;
      } else if (step < -maxStep) {
        step = -maxStep;
      }
      double newTemp = interphaseSystem.getTemperature() - step;
      // Ensure temperature stays positive (Kelvin)
      if (newTemp < 1.0) {
        newTemp = 1.0;
      }
      interphaseSystem.setTemperature(newTemp);
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
    int maxNaNCount = 5; // Maximum consecutive NaN results before giving up
    int nanCount = 0;
    double err = 1.0e10;
    // double oldErr = 0.0;
    double factor = 10.0;
    // if(bulkSystem.isChemicalSystem()) factor=100.0;
    long tStart = System.currentTimeMillis();
    setuMassTrans();
    do {
      // oldErr = err;
      iter++;
      init();
      setfvecMassTrans2();
      setJacMassTrans2();

      // Check for singular or ill-conditioned Jacobian
      try {
        dx = solveMassTransferStep();
      } catch (RuntimeException e) {
        // Matrix solve failed - likely singular matrix
        logger.debug("Jacobian solve failed in mass transfer calculation: {}", e.getMessage());
        nanCount++;
        if (nanCount >= maxNaNCount) {
          logger.debug("Mass transfer solver terminated after {} consecutive failures", nanCount);
          break;
        }
        continue;
      }

      if (dx != null && !Double.valueOf(massTransferColumnNorm(dx)).isNaN()) {
        nanCount = 0; // Reset counter on successful solve
        double uMassTransNorm = massTransferColumnNorm(uMassTrans);
        if (uMassTransNorm > 1e-20) {
          DMatrixRMaj dampedStep = dx.copy();
          CommonOps_DDRM.scale(iter / (iter + factor), dampedStep);
          CommonOps_DDRM.subtractEquals(uMassTrans, dampedStep);
          err = Math.abs(massTransferColumnNorm(dx) / uMassTransNorm);
        } else {
          // Avoid division by zero
          DMatrixRMaj dampedStep = dx.copy();
          CommonOps_DDRM.scale(iter / (iter + factor), dampedStep);
          CommonOps_DDRM.subtractEquals(uMassTrans, dampedStep);
          err = Math.abs(massTransferColumnNorm(dx));
        }
        updateMassTrans();
        calcFluxes();
      } else {
        nanCount++;
        if (nanCount == 1) {
          // Only log on first occurrence to reduce noise
          logger.debug("NaN detected in mass transfer calculation at iteration {}", iter);
        }
        if (nanCount >= maxNaNCount) {
          logger.debug("Mass transfer solver terminated after {} consecutive NaN results", nanCount);
          break;
        }
        err = 10;
      }
      // System.out.println("err " + err);
    } while ((err > 1.e-4 && iter < 100) || iter < 5);
    calcFluxes();
  }

  /**
   * Solves the existing nonreactive film equations and interface heat balance together.
   *
   * <p>
   * The segregated temperature/flux fixed point can amplify roundoff even from an equilibrium state. This Newton update
   * includes temperature, checks every residual before taking a step, and accepts only finite positive-composition
   * states with decreasing residual. No flux is overwritten to enforce equilibrium. Bulk inventories are never changed
   * here.
   *
   * @throws IllegalStateException if transport, thermodynamics or convergence is invalid
   */
  protected final void solveCoupledMassHeatTransfer() {
    SystemInterface savedInterface = interphaseSystem.clone();
    DMatrixRMaj savedFlux = nFlux.copy();
    double savedTotalFlux = totalFlux;
    double[] savedHeatFlux = interphaseHeatFlux.clone();
    try {
      initInterphaseSystem();
      setuMassTrans();
      double[] state = new double[neq + 1];
      System.arraycopy(uMassTrans.getData(), 0, state, 0, neq);
      state[neq] = interphaseSystem.getTemperature();
      double[] residual = evaluateCoupledFilm(state);
      for (int iteration = 0; iteration < 50; iteration++) {
        double norm = coupledFilmResidualNorm(residual);
        if (norm <= 1.0) {
          return;
        }
        DMatrixRMaj jacobian = new DMatrixRMaj(neq + 1, neq + 1);
        for (int column = 0; column <= neq; column++) {
          double step = coupledFilmDifferenceStep(state, column);
          double[] plus = state.clone();
          double[] minus = state.clone();
          plus[column] += step;
          minus[column] -= step;
          double[] plusResidual = evaluateCoupledFilm(plus);
          double[] minusResidual = evaluateCoupledFilm(minus);
          for (int row = 0; row <= neq; row++) {
            jacobian.set(row, column, (plusResidual[row] - minusResidual[row]) / (2.0 * step));
          }
        }
        DMatrixRMaj rightHandSide = new DMatrixRMaj(neq + 1, 1, true, residual.clone());
        for (int row = 0; row <= neq; row++) {
          double scale = 0.0;
          for (int column = 0; column <= neq; column++) {
            scale = Math.max(scale, Math.abs(jacobian.get(row, column)));
          }
          if (!(scale > 0.0) || !Double.isFinite(scale)) {
            throw new IllegalStateException("Invalid coupled film Jacobian row " + row);
          }
          for (int column = 0; column <= neq; column++) {
            jacobian.set(row, column, jacobian.get(row, column) / scale);
          }
          rightHandSide.set(row, 0, rightHandSide.get(row, 0) / scale);
        }
        DMatrixRMaj update = new DMatrixRMaj(neq + 1, 1);
        if (!CommonOps_DDRM.solve(jacobian, rightHandSide, update)) {
          throw new IllegalStateException("Singular coupled film Jacobian");
        }
        boolean accepted = false;
        for (double fraction = 1.0; fraction >= 1.0e-8; fraction *= 0.5) {
          double[] candidate = state.clone();
          for (int column = 0; column <= neq; column++) {
            candidate[column] -= fraction * update.get(column, 0);
          }
          if (!isCoupledFilmStateAdmissible(candidate)) {
            continue;
          }
          double[] candidateResidual;
          try {
            candidateResidual = evaluateCoupledFilm(candidate);
          } catch (RuntimeException invalidCandidate) {
            continue;
          }
          if (coupledFilmResidualNorm(candidateResidual) < norm) {
            state = candidate;
            residual = candidateResidual;
            accepted = true;
            break;
          }
        }
        if (!accepted) {
          throw new IllegalStateException("Coupled film line search failed; scaled residual=" + norm);
        }
      }
      throw new IllegalStateException(
          "Coupled film did not converge; scaled residual=" + coupledFilmResidualNorm(residual));
    } catch (RuntimeException failure) {
      interphaseSystem = savedInterface;
      nFlux = savedFlux;
      totalFlux = savedTotalFlux;
      interphaseHeatFlux = savedHeatFlux;
      throw failure;
    }
  }

  /**
   * Selects a centered-difference interval, keeping both composition probes positive.
   *
   * @param state interface compositions, component molar fluxes and temperature
   * @param column variable to perturb
   * @return finite-difference interval in that variable's units
   */
  protected double coupledFilmDifferenceStep(double[] state, int column) {
    if (column == neq) {
      return 1.0e-4;
    }
    if (column < 2 * bulkSystem.getNumberOfComponents()) {
      return Math.min(Math.max(Math.abs(state[column]) * 1.0e-5, 1.0e-9), state[column] * 0.25);
    }
    return Math.max(Math.abs(state[column]) * 1.0e-5, 1.0e-8);
  }

  /**
   * Tests the domain before thermodynamic recovery; no composition or temperature is clipped.
   *
   * @param state candidate interface state
   * @return true for finite fluxes, strictly positive fractions below one and positive temperature
   */
  private boolean isCoupledFilmStateAdmissible(double[] state) {
    for (double value : state) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    for (int component = 0; component < 2 * bulkSystem.getNumberOfComponents(); component++) {
      if (!(state[component] > 0.0 && state[component] < 1.0)) {
        return false;
      }
    }
    return state[neq] > 0.0;
  }

  /**
   * Evaluates the actual corrected film residuals and one interface energy balance.
   *
   * @param state interface mole fractions, molar fluxes in mol/(m2 s), temperature in K
   * @return fugacity, normalization, film-flux and heat residuals (last entry in W/m2)
   * @throws IllegalStateException if the candidate has invalid thermodynamics or transport
   */
  private double[] evaluateCoupledFilm(double[] state) {
    if (!isCoupledFilmStateAdmissible(state)) {
      throw new IllegalStateException("Inadmissible coupled film state");
    }
    for (int variable = 0; variable < neq; variable++) {
      uMassTrans.set(variable, 0, state[variable]);
    }
    updateMassTrans();
    calcFluxes();
    interphaseSystem.setTemperature(state[neq]);
    init();
    setfvecMassTrans2();
    double[] residual = new double[neq + 1];
    System.arraycopy(fvec.getData(), 0, residual, 0, neq);
    double transferredEnthalpy = 0.0;
    for (int component = 0; component < bulkSystem.getNumberOfComponents(); component++) {
      double gasTemperature = bulkSystem.getPhase(0).getTemperature();
      double liquidTemperature = bulkSystem.getPhase(1).getTemperature();
      double gasEnthalpy = bulkSystem.getPhase(0).getComponent(component).getHID(gasTemperature)
          + bulkSystem.getPhase(0).getComponent(component).getHresTP(gasTemperature);
      double liquidEnthalpy = bulkSystem.getPhase(1).getComponent(component).getHID(liquidTemperature)
          + bulkSystem.getPhase(1).getComponent(component).getHresTP(liquidTemperature);
      transferredEnthalpy += nFlux.get(component, 0) * (gasEnthalpy - liquidEnthalpy);
    }
    for (int phase = 0; phase < 2; phase++) {
      double conductance = heatTransferCoefficient[phase] * heatTransferCorrection[phase];
      if (!(conductance > 0.0) || !Double.isFinite(conductance)) {
        throw new IllegalStateException("Invalid interfacial heat conductance for phase " + phase);
      }
      interphaseHeatFlux[phase] = -conductance * (bulkSystem.getPhase(phase).getTemperature() - state[neq]);
    }
    residual[neq] = interphaseHeatFlux[0] + interphaseHeatFlux[1] - transferredEnthalpy;
    if (!Double.isFinite(coupledFilmResidualNorm(residual))) {
      throw new IllegalStateException("Nonfinite coupled film residual");
    }
    return residual;
  }

  /**
   * Scales every equation by its absolute convergence requirement rather than flux changes.
   *
   * @param residual complete coupled residual vector
   * @return maximum normalized residual, or positive infinity for a nonfinite entry
   */
  private double coupledFilmResidualNorm(double[] residual) {
    int components = bulkSystem.getNumberOfComponents();
    double norm = 0.0;
    for (int row = 0; row < residual.length; row++) {
      if (!Double.isFinite(residual[row])) {
        return Double.POSITIVE_INFINITY;
      }
      double tolerance = row < components ? 1.0e-10 : row < components + 2 ? 1.0e-12 : row < neq ? 1.0e-10 : 1.0e-6;
      norm = Math.max(norm, Math.abs(residual[row]) / tolerance);
    }
    return norm;
  }

  /**
   * Solves the Newton system with EJML factors and the established column-update substitution order.
   *
   * @return Newton step, including non-finite entries for the existing recovery branch to inspect
   * @throws RuntimeException if factorization fails or the system has an exactly zero pivot
   */
  private DMatrixRMaj solveMassTransferStep() {
    double[][] coefficients = new double[neq][neq];
    for (int row = 0; row < neq; row++) {
      for (int column = 0; column < neq; column++) {
        coefficients[row][column] = Jac.get(row, column);
      }
    }
    return new DMatrixRMaj(neq, 1, true, solveFilmLinearSystem(coefficients, fvec.getData()));
  }

  /**
   * Solves a film Jacobian using EJML LU while preserving column-ordered triangular updates.
   *
   * <p>
   * Film Jacobians can be extremely ill-conditioned. Row-ordered substitution can change their Newton trajectory; this
   * implementation retains the previous arithmetic order without retaining a Jama dependency. Non-finite results remain
   * available to the existing caller recovery logic, and only exactly zero pivots are rejected.
   *
   * @param coefficients square coefficient data
   * @param rightHandSide right-hand-side vector
   * @return independent solution vector
   * @throws RuntimeException if factorization fails or a pivot is exactly zero
   */
  protected static double[] solveFilmLinearSystem(double[][] coefficients, double[] rightHandSide) {
    int dimension = coefficients.length;
    LUDecompositionAlt_DDRM decomposition = new LUDecompositionAlt_DDRM();
    if (!decomposition.decompose(new DMatrixRMaj(coefficients))) {
      throw new IllegalStateException("Film Jacobian LU factorization failed");
    }
    DMatrixRMaj factors = decomposition.getLU();
    int[] permutation = decomposition.getPivot();
    double[] solution = new double[dimension];
    for (int row = 0; row < dimension; row++) {
      if (factors.get(row, row) == 0.0) {
        throw new IllegalStateException("Film Jacobian is singular");
      }
      solution[row] = rightHandSide[permutation[row]];
    }
    for (int diagonal = 0; diagonal < dimension; diagonal++) {
      for (int row = diagonal + 1; row < dimension; row++) {
        solution[row] -= solution[diagonal] * factors.get(row, diagonal);
      }
    }
    for (int diagonal = dimension - 1; diagonal >= 0; diagonal--) {
      solution[diagonal] /= factors.get(diagonal, diagonal);
      for (int row = 0; row < diagonal; row++) {
        solution[row] -= solution[diagonal] * factors.get(row, diagonal);
      }
    }
    return solution;
  }

  /**
   * Measures a solution column without treating an all-NaN vector as a zero update.
   *
   * @param column Newton residual or state column
   * @return Euclidean norm, or NaN if any entry is NaN
   */
  private static double massTransferColumnNorm(DMatrixRMaj column) {
    for (int row = 0; row < column.getNumRows(); row++) {
      if (Double.isNaN(column.get(row, 0))) {
        return Double.NaN;
      }
    }
    return NormOps_DDRM.normP2(column);
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
    return nFlux.getData().clone();
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
      } while (Math.abs(totalFlux) > 1e-30 && Math.abs((totalFluxOld - totalFlux) / totalFlux) > 1e-6
          && iterOuter < 50);

      if (heatTransferCalc) {
        this.heatTransSolve();
        heatFlux = this.getInterphaseHeatFlux(0);
      }
    } while (Math.abs(heatFlux) > 1e-30 && Math.abs((oldHeatFlux - heatFlux) / heatFlux) > 1e-6 && heatTransferCalc
        && iterInner < 50);
    init();
  }
}
