package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * KrishnaStandartFilmModel class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class KrishnaStandartFilmModel extends
    neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.NonEquilibriumFluidBoundary
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(KrishnaStandartFilmModel.class);
  Matrix phiMatrix;
  Matrix redPhiMatrix;
  Matrix redCorrectionMatrix;
  Matrix betaMatrix;

  /**
   * <p>
   * Constructor for KrishnaStandartFilmModel.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public KrishnaStandartFilmModel(SystemInterface system) {
    super(system);
    binaryMassTransferCoefficient = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    binarySchmidtNumber = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    uMassTrans = new Matrix(neq, 1);
    Xgij = new Matrix(neq, 4);
    this.setuMassTrans();
    uMassTransold = uMassTrans.copy();
  }

  /**
   * <p>
   * Constructor for KrishnaStandartFilmModel.
   * </p>
   *
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public KrishnaStandartFilmModel(FlowNodeInterface flowNode) {
    super(flowNode);
    binaryMassTransferCoefficient = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    binarySchmidtNumber = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    uMassTrans = new Matrix(neq, 1);
    Xgij = new Matrix(neq, 4);
    this.setuMassTrans();
    uMassTransold = uMassTrans.copy();
    phiMatrix = new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1,
        getBulkSystem().getPhases()[0].getNumberOfComponents() - 1);
    redCorrectionMatrix = new Matrix(getBulkSystem().getPhases()[0].getNumberOfComponents() - 1, 1);
  }

  /** {@inheritDoc} */
  @Override
  public KrishnaStandartFilmModel clone() {
    KrishnaStandartFilmModel clonedSystem = null;

    try {
      clonedSystem = (KrishnaStandartFilmModel) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    return clonedSystem;
  }

  /**
   * <p>
   * calcBinarySchmidtNumbers.
   * </p>
   *
   * @param phaseNum a int
   * @return a double
   */
  public double calcBinarySchmidtNumbers(int phaseNum) {
    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); i++) {
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        binarySchmidtNumber[phaseNum][i][j] =
            getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
                / getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
                    .getDiffusionCoefficient(i, j);
        // System.out.println("i j " + i +" j " + j);
        // System.out.println("phase " + phase + " diff" +
        // getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDiffusionCoefficient(i,j));
        // System.out.println("phase " + phase + " visk" +
        // getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity());
      }
    }
    return 1;
  }

  /**
   * <p>
   * calcBinaryMassTransferCoefficients.
   * </p>
   *
   * @param phaseNum a int
   * @return a double
   */
  public double calcBinaryMassTransferCoefficients(int phaseNum) {
    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); i++) {
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        binaryMassTransferCoefficient[phaseNum][i][j] =
            flowNode.getInterphaseTransportCoefficient().calcInterphaseMassTransferCoefficient(
                phaseNum, binarySchmidtNumber[phaseNum][i][j], flowNode);
      }
    }
    return 1;
  }

  /**
   * <p>
   * calcMassTransferCoefficients.
   * </p>
   *
   * @param phaseNum a int
   * @return a double
   */
  public double calcMassTransferCoefficients(int phaseNum) {
    int n = getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1;

    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      double tempVar = 0;
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        if (i != j) {
          tempVar += getBulkSystem().getPhase(phaseNum).getComponent(j).getx()
              / binaryMassTransferCoefficient[phaseNum][i][j];
        }
        if (j < n) {
          massTransferCoefficientMatrix[phaseNum].set(i, j,
              -getBulkSystem().getPhase(phaseNum).getComponent(i).getx()
                  * (1.0 / binaryMassTransferCoefficient[phaseNum][i][j]
                      - 1.0 / binaryMassTransferCoefficient[phaseNum][i][n]));
        }
      }
      massTransferCoefficientMatrix[phaseNum].set(i, i,
          tempVar + getBulkSystem().getPhase(phaseNum).getComponent(i).getx()
              / binaryMassTransferCoefficient[phaseNum][i][n]);
    }
    massTransferCoefficientMatrix[phaseNum] = massTransferCoefficientMatrix[phaseNum].inverse();
    return 1;
  }

  /**
   * <p>
   * calcPhiMatrix.
   * </p>
   *
   * @param phaseNum a int
   */
  public void calcPhiMatrix(int phaseNum) {
    int n = getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1;

    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      double tempVar = 0;
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        if (i != j || i == n) {
          tempVar +=
              nFlux.get(i, 0) / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
                  * binaryMassTransferCoefficient[phaseNum][i][j]);
        }
        if (j < n) {
          phiMatrix.set(i, j,
              -nFlux.get(i, 0) * (1.0
                  / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
                      * binaryMassTransferCoefficient[phaseNum][i][j])
                  - 1.0 / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
                      * binaryMassTransferCoefficient[phaseNum][i][n])));
        }
      }
      phiMatrix.set(i, i,
          tempVar + nFlux.get(i, 0)
              / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
                  * binaryMassTransferCoefficient[phaseNum][i][n]));
    }
  }

  /**
   * <p>
   * calcRedPhiMatrix.
   * </p>
   *
   * @param phase a int
   */
  public void calcRedPhiMatrix(int phase) {
    redPhiMatrix = new Matrix(phiMatrix.eig().getRealEigenvalues(), 1);
  }

  /**
   * <p>
   * calcRedCorrectionMatrix.
   * </p>
   *
   * @param phaseNum a int
   */
  public void calcRedCorrectionMatrix(int phaseNum) {
    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      redCorrectionMatrix.set(i, 0, (redPhiMatrix.get(0, i) * Math.exp(redPhiMatrix.get(0, i)))
          / (Math.exp(redPhiMatrix.get(0, i)) - (1.0 - 1e-15)));
    }
  }

  /**
   * <p>
   * calcCorrectionMatrix.
   * </p>
   *
   * @param phaseNum a int
   */
  public void calcCorrectionMatrix(int phaseNum) {
    Matrix modalPhiMatrix = phiMatrix.eig().getV();
    Matrix diagonalRedCorrectionMatrix =
        new Matrix(getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1,
            getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1);
    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      diagonalRedCorrectionMatrix.set(i, i, redCorrectionMatrix.get(i, 0));
    }
    rateCorrectionMatrix[phaseNum] =
        modalPhiMatrix.times(diagonalRedCorrectionMatrix.times(modalPhiMatrix.inverse()));
  }

  /**
   * <p>
   * calcTotalMassTransferCoefficientMatrix.
   * </p>
   *
   * @param phase a int
   */
  public void calcTotalMassTransferCoefficientMatrix(int phase) {
    totalMassTransferCoefficientMatrix[phase] = massTransferCoefficientMatrix[phase];
    // System.out.println("before phase: " + phase);
    // totalMassTransferCoefficientMatrix[phase].print(10,10);
    // System.out.println("eqcorr " + useThermodynamicCorrections(phase));
    // System.out.println("fluxcorr " + useFiniteFluxCorrection(phase));
    if (Math.abs(totalFlux) > 1e-30) {
      if (useFiniteFluxCorrection(phase) && useThermodynamicCorrections(phase)) {
        totalMassTransferCoefficientMatrix[phase] = rateCorrectionMatrix[phase]
            .times(nonIdealCorrections[phase].times(massTransferCoefficientMatrix[phase]));
      } else if (useFiniteFluxCorrection(phase)) {
        totalMassTransferCoefficientMatrix[phase] =
            rateCorrectionMatrix[phase].times(massTransferCoefficientMatrix[phase]);
      } else if (useThermodynamicCorrections(phase)) {
        totalMassTransferCoefficientMatrix[phase] =
            massTransferCoefficientMatrix[phase].times(nonIdealCorrections[phase]);
      } else {
        totalMassTransferCoefficientMatrix[phase] = massTransferCoefficientMatrix[phase];
      }
    } else {
      totalMassTransferCoefficientMatrix[phase] = massTransferCoefficientMatrix[phase];
    }
    // System.out.println("phase: " + phase);
    // totalMassTransferCoefficientMatrix[phase].print(10,10);
  }

  /**
   * <p>
   * initCorrections.
   * </p>
   *
   * @param phase a int
   */
  public void initCorrections(int phase) {
    calcPhiMatrix(phase);
    // phiMatrix.print(10,10);
    calcRedPhiMatrix(phase);
    // redPhiMatrix.print(10,10);
    calcRedCorrectionMatrix(phase);
    // redCorrectionMatrix.print(10,10);
    calcCorrectionMatrix(phase);
    // System.out.println("corr mat: " + phase);
    // rateCorrectionMatrix[phase].print(10,10);
  }

  /** {@inheritDoc} */
  @Override
  public void initMassTransferCalc() {
    super.initMassTransferCalc();
    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      this.calcBinarySchmidtNumbers(phaseNum);
      this.calcBinaryMassTransferCoefficients(phaseNum);
      this.calcMassTransferCoefficients(phaseNum);
      this.initCorrections(phaseNum);
      this.calcNonIdealCorrections(phaseNum);
      // this.calcFluxTypeCorrectionMatrix(phase,0);
      this.calcTotalMassTransferCoefficientMatrix(phaseNum);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initHeatTransferCalc() {
    super.initHeatTransferCalc();
    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      this.calcHeatTransferCoefficients(phaseNum);
      this.calcHeatTransferCorrection(phaseNum);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    super.init();
    if (massTransferCalc) {
      this.initMassTransferCalc();
    }
    if (heatTransferCalc) {
      this.initHeatTransferCalc();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    super.solve();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("Starter.....");
    SystemSrkEos testSystem = new SystemSrkEos(295.3, 3.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    PipeData pipe1 = new PipeData(0.1, 0.025);

    testSystem.addComponent("methane", 0.0071152181, 0);
    testSystem.addComponent("ethane", 0.0071152181, 0);
    testSystem.addComponent("water", 0.00362204876, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initPhysicalProperties();
    FlowNodeInterface test = new AnnularFlow(testSystem, pipe1);
    test.initFlowCalc();
    test.init();

    KrishnaStandartFilmModel test2 = new KrishnaStandartFilmModel(test);
    test2.solve();
    // test2.initCorrections(1);
    test2.calcFluxes();
    test2.getInterphaseSystem().display();
    test2.display("");
  }
}
