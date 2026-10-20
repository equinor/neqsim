package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.mathlib.linearalgebra.ApacheCommonsMathLinearAlgebra;
import neqsim.mathlib.linearalgebra.RealBlockEigenDecomposition;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Krishna-Standart film calculations with array-backed coefficient, correction and eigen data.
 *
 * <p>
 * Commons Math supplies paired eigen data, and EJML evaluates the modal correction on primitive-array state. Serialized
 * instances containing the former Jama eigen-data fields are not compatible with this layout.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class KrishnaStandartFilmModel extends
    neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.NonEquilibriumFluidBoundary
    implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(KrishnaStandartFilmModel.class);
  private static final ApacheCommonsMathLinearAlgebra ALGEBRA = new ApacheCommonsMathLinearAlgebra();
  private transient boolean complexSpectrumWarningLogged;
  double[][] phiMatrix;
  double[] redPhiMatrix;
  double[] redCorrectionMatrix;
  double[][] betaMatrix;

  /**
   * Constructor for KrishnaStandartFilmModel.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public KrishnaStandartFilmModel(SystemInterface system) {
    super(system);
    binaryMassTransferCoefficient = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    binarySchmidtNumber = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    uMassTrans = new DMatrixRMaj(neq, 1);
    Xgij = new DMatrixRMaj(neq, 4);
    this.setuMassTrans();
    uMassTransold = uMassTrans.copy();
    int independentComponents = getBulkSystem().getPhases()[0].getNumberOfComponents() - 1;
    phiMatrix = new double[independentComponents][independentComponents];
    redCorrectionMatrix = new double[independentComponents];
  }

  /**
   * Constructor for KrishnaStandartFilmModel.
   *
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public KrishnaStandartFilmModel(FlowNodeInterface flowNode) {
    super(flowNode);
    binaryMassTransferCoefficient = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    binarySchmidtNumber = new double[2][getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    uMassTrans = new DMatrixRMaj(neq, 1);
    Xgij = new DMatrixRMaj(neq, 4);
    this.setuMassTrans();
    uMassTransold = uMassTrans.copy();
    int independentComponents = getBulkSystem().getPhases()[0].getNumberOfComponents() - 1;
    phiMatrix = new double[independentComponents][independentComponents];
    redCorrectionMatrix = new double[independentComponents];
  }

  /** {@inheritDoc} */
  @Override
  public KrishnaStandartFilmModel clone() {
    KrishnaStandartFilmModel clonedSystem = null;

    try {
      clonedSystem = (KrishnaStandartFilmModel) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }

  /**
   * calcBinarySchmidtNumbers.
   *
   * @param phaseNum a int
   * @return a double
   */
  public double calcBinarySchmidtNumbers(int phaseNum) {
    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); i++) {
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        double diffCoeff = getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getDiffusionCoefficient(i, j);
        if (Double.isNaN(diffCoeff) || Double.isInfinite(diffCoeff) || Math.abs(diffCoeff) < 1e-30) {
          binarySchmidtNumber[phaseNum][i][j] = 1.0;
        } else {
          binarySchmidtNumber[phaseNum][i][j] = getBulkSystem().getPhase(phaseNum).getPhysicalProperties()
              .getKinematicViscosity() / diffCoeff;
        }
        if (Double.isNaN(binarySchmidtNumber[phaseNum][i][j])
            || Double.isInfinite(binarySchmidtNumber[phaseNum][i][j])) {
          binarySchmidtNumber[phaseNum][i][j] = 1.0;
        }
      }
    }
    return 1;
  }

  /**
   * calcBinaryMassTransferCoefficients.
   *
   * @param phaseNum a int
   * @return a double
   */
  public double calcBinaryMassTransferCoefficients(int phaseNum) {
    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); i++) {
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        binaryMassTransferCoefficient[phaseNum][i][j] = flowNode.getInterphaseTransportCoefficient()
            .calcInterphaseMassTransferCoefficient(phaseNum, binarySchmidtNumber[phaseNum][i][j], flowNode);
      }
    }
    return 1;
  }

  /**
   * Calculates the mass transfer coefficients for the specified phase.
   *
   * <p>
   * Coefficients are calculated based on the binary mass transfer coefficients and the composition of the phase.
   * </p>
   *
   * @param phaseNum a int
   * @return a double
   */
  public double calcMassTransferCoefficients(int phaseNum) {
    int n = getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1;
    DMatrixRMaj resistanceMatrix = new DMatrixRMaj(n, n);

    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      double tempVar = 0;
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        if (i != j) {
          tempVar += getBulkSystem().getPhase(phaseNum).getComponent(j).getx()
              / binaryMassTransferCoefficient[phaseNum][i][j];
        }
        if (j < n) {
          resistanceMatrix.set(i, j,
              -getBulkSystem().getPhase(phaseNum).getComponent(i).getx()
                  * (1.0 / binaryMassTransferCoefficient[phaseNum][i][j]
                      - 1.0 / binaryMassTransferCoefficient[phaseNum][i][n]));
        }
      }
      resistanceMatrix.set(i, i, tempVar
          + getBulkSystem().getPhase(phaseNum).getComponent(i).getx() / binaryMassTransferCoefficient[phaseNum][i][n]);
    }
    LinearSolverDense<DMatrixRMaj> linearSolver = LinearSolverFactory_DDRM.lu(n);
    if (!linearSolver.setA(resistanceMatrix)) {
      throw new IllegalStateException("Mass-transfer resistance matrix is singular for phase " + phaseNum);
    }
    DMatrixRMaj inverseMatrix = new DMatrixRMaj(n, n);
    linearSolver.invert(inverseMatrix);
    boolean invalidInverse = false;
    for (int row = 0; row < n; row++) {
      for (int column = 0; column < n; column++) {
        if (Double.isNaN(inverseMatrix.get(row, column)) || Double.isInfinite(inverseMatrix.get(row, column))) {
          invalidInverse = true;
        }
      }
    }
    if (invalidInverse) {
      int dim = getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1;
      double[][] identity = new double[dim][dim];
      for (int index = 0; index < dim; index++) {
        identity[index][index] = 1.0;
      }
      setMassTransferCoefficientArray(phaseNum, identity);
    } else {
      setMassTransferCoefficientArray(phaseNum, toArray(inverseMatrix));
    }
    return 1;
  }

  /**
   * calcPhiMatrix.
   *
   * @param phaseNum a int
   */
  public void calcPhiMatrix(int phaseNum) {
    int n = getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1;

    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      double tempVar = 0;
      for (int j = 0; j < getBulkSystem().getPhase(phaseNum).getNumberOfComponents(); j++) {
        if (i != j) {
          tempVar += nFlux.get(j, 0) / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
              * binaryMassTransferCoefficient[phaseNum][i][j]);
        }
        if (j < n) {
          phiMatrix[i][j] = -nFlux.get(i, 0) * (1.0
              / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
                  * binaryMassTransferCoefficient[phaseNum][i][j])
              - 1.0 / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
                  * binaryMassTransferCoefficient[phaseNum][i][n]));
        }
      }
      phiMatrix[i][i] = tempVar + nFlux.get(i, 0) / (1.0 / (getBulkSystem().getPhase(phaseNum).getMolarVolume() * 1e-5)
          * binaryMassTransferCoefficient[phaseNum][i][n]);
    }
  }

  /**
   * calcRedPhiMatrix.
   *
   * @param phase a int
   */
  public void calcRedPhiMatrix(int phase) {
    double scale = phiMatrixScale();
    redPhiMatrix = decomposePhiMatrix(scale).getRealParts();
    for (int component = 0; component < redPhiMatrix.length; component++) {
      redPhiMatrix[component] *= scale;
    }
  }

  /**
   * Finds a nonzero scale for the finite-flux eigensystem.
   *
   * @return largest absolute entry, or one for the zero matrix
   */
  private double phiMatrixScale() {
    double scale = 0.0;
    for (double[] row : phiMatrix) {
      for (double value : row) {
        scale = Math.max(scale, Math.abs(value));
      }
    }
    return scale == 0.0 ? 1.0 : scale;
  }

  /**
   * Decomposes a scaled copy to keep the Schur calculation independent of the flux magnitude.
   *
   * <p>
   * The historical film correlation applies its scalar correction to real eigenvalue parts in the real block basis.
   * Preserve that approximation for conjugate pairs; this is not the full complex matrix function.
   *
   * @param scale largest absolute finite-flux matrix entry, or one for the zero matrix
   * @return eigensystem of the normalized matrix
   */
  private RealBlockEigenDecomposition decomposePhiMatrix(double scale) {
    double[][] normalized = new double[phiMatrix.length][phiMatrix.length];
    for (int row = 0; row < phiMatrix.length; row++) {
      for (int column = 0; column < phiMatrix.length; column++) {
        normalized[row][column] = phiMatrix[row][column] / scale;
      }
    }
    RealBlockEigenDecomposition result = ALGEBRA.realBlockEigenDecomposition(normalized);
    if (result.hasComplexEigenvalues() && !complexSpectrumWarningLogged) {
      logger.warn("Finite-flux matrix has complex conjugate eigenvalues; retaining the historical real-part "
          + "film-correction approximation (not a full complex matrix function).");
      complexSpectrumWarningLogged = true;
    }
    return result;
  }

  /**
   * calcRedCorrectionMatrix.
   *
   * @param phaseNum a int
   */
  public void calcRedCorrectionMatrix(int phaseNum) {
    for (int i = 0; i < getBulkSystem().getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      double z = redPhiMatrix[i];
      double correction;
      if (Math.abs(z) < 1e-6) {
        // Analytic continuation of z * exp(z) / (exp(z) - 1), including z = 0.
        correction = 1.0 + z / 2.0 + z * z / 12.0;
      } else if (z > 0.0) {
        correction = z / -Math.expm1(-z);
      } else {
        correction = z * Math.exp(z) / Math.expm1(z);
      }
      redCorrectionMatrix[i] = correction;
    }
  }

  /**
   * calcCorrectionMatrix.
   *
   * @param phaseNum a int
   */
  public void calcCorrectionMatrix(int phaseNum) {
    RealBlockEigenDecomposition eigen = decomposePhiMatrix(phiMatrixScale());
    DMatrixRMaj modalPhiMatrix = new DMatrixRMaj(eigen.getVectors());
    int dimension = modalPhiMatrix.getNumRows();
    DMatrixRMaj diagonalCorrection = new DMatrixRMaj(dimension, dimension);
    for (int component = 0; component < dimension; component++) {
      diagonalCorrection.set(component, component, redCorrectionMatrix[component]);
    }
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(dimension);
    if (!solver.setA(modalPhiMatrix.copy())) {
      throw new IllegalStateException("Finite-flux eigenvector matrix is singular for phase " + phaseNum);
    }
    DMatrixRMaj inverse = new DMatrixRMaj(dimension, dimension);
    solver.invert(inverse);
    DMatrixRMaj scaledInverse = new DMatrixRMaj(dimension, dimension);
    CommonOps_DDRM.mult(diagonalCorrection, inverse, scaledInverse);
    DMatrixRMaj correction = new DMatrixRMaj(dimension, dimension);
    CommonOps_DDRM.mult(modalPhiMatrix, scaledInverse, correction);
    rateCorrectionMatrix[phaseNum] = toArray(correction);
  }

  /**
   * Composes the coefficient matrix for column-vector composition differences: Xi * K * Gamma, where K is the inverse
   * resistance matrix. Disabled corrections act as identity matrices. The total coefficient array is independent of K
   * so reactive enhancement cannot change the unenhanced coefficients.
   *
   * @param phase a int
   */
  public void calcTotalMassTransferCoefficientArray(int phase) {
    DMatrixRMaj corrected = new DMatrixRMaj(getMassTransferCoefficientArray(phase));
    if (useThermodynamicCorrections(phase)) {
      DMatrixRMaj thermodynamicCorrection = new DMatrixRMaj(nonIdealCorrections[phase]);
      DMatrixRMaj product = new DMatrixRMaj(corrected.getNumRows(), thermodynamicCorrection.getNumCols());
      CommonOps_DDRM.mult(corrected, thermodynamicCorrection, product);
      corrected = product;
    }
    if (useFiniteFluxCorrection(phase)) {
      DMatrixRMaj rateCorrection = new DMatrixRMaj(rateCorrectionMatrix[phase]);
      DMatrixRMaj product = new DMatrixRMaj(rateCorrection.getNumRows(), corrected.getNumCols());
      CommonOps_DDRM.mult(rateCorrection, corrected, product);
      corrected = product;
    }
    totalMassTransferCoefficientMatrix[phase] = toArray(corrected);
  }

  /**
   * Copies an EJML matrix to primitive row-major coefficient arrays.
   *
   * @param matrix EJML coefficient matrix
   * @return independent coefficient arrays
   */
  private static double[][] toArray(DMatrixRMaj matrix) {
    double[][] values = new double[matrix.getNumRows()][matrix.getNumCols()];
    for (int row = 0; row < matrix.getNumRows(); row++) {
      System.arraycopy(matrix.getData(), row * matrix.getNumCols(), values[row], 0, matrix.getNumCols());
    }
    return values;
  }

  /**
   * initCorrections.
   *
   * @param phase a int
   */
  public void initCorrections(int phase) {
    calcPhiMatrix(phase);
    if (hasNaN(phiMatrix)) {
      int n = getBulkSystem().getPhase(phase).getNumberOfComponents() - 1;
      rateCorrectionMatrix[phase] = new double[n][n];
      for (int component = 0; component < n; component++) {
        rateCorrectionMatrix[phase][component][component] = 1.0;
      }
      return;
    }
    calcRedPhiMatrix(phase);
    calcRedCorrectionMatrix(phase);
    calcCorrectionMatrix(phase);
  }

  /**
   * Checks whether a matrix contains any NaN or Infinite values.
   *
   * @param values the matrix data to check
   * @return true if any element is NaN or Infinite
   */
  private boolean hasNaN(double[][] values) {
    for (double[] row : values) {
      for (double value : row) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
          return true;
        }
      }
    }
    return false;
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
      this.calcTotalMassTransferCoefficientArray(phaseNum);
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
   * main.
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
