package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ojalgo.array.Array1D;
import org.ojalgo.matrix.decomposition.Eigenvalue;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.Primitive64Store;
import org.ojalgo.scalar.ComplexNumber;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.math.LinearAlgebraOps;

/**
 * <p>
 * Critical-point flash calculation using the Heidemann and Khalil criterion.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class CriticalPointFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CriticalPointFlash.class);

  private Primitive64Store Mmatrix = null;
  int numberOfComponents;
  double Vc0;
  double Tc0;

  /**
   * <p>
   * Constructor for CriticalPointFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public CriticalPointFlash(SystemInterface system) {
    this.system = system;
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Mmatrix = Primitive64Store.FACTORY.make(numberOfComponents, numberOfComponents);
  }

  /**
   * <p>
   * Builds the scaled Q matrix used in the Heidemann &amp; Khalil (1980) critical-point criterion.
   * </p>
   *
   * <p>
   * The element \(Q_{ij} = \sqrt{z_i z_j}\, \partial \ln f_i / \partial n_j\) is the Hessian of the reduced Helmholtz
   * energy with respect to mole numbers at constant temperature and volume. It is theoretically symmetric (a Maxwell
   * relation), so the matrix is explicitly symmetrized after assembly to remove the small numerical asymmetry
   * introduced by the constant-pressure to constant-volume derivative conversion. Symmetrization guarantees real
   * eigenvalues and non-null eigenvectors from the eigenvalue decomposition.
   * </p>
   */
  public void calcMmatrix() {
    double dij = 0;
    double tempJ = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
	dij = i == j ? 1.0 : 0.0; // Kroneckers delta

	tempJ = (dij / system.getPhase(0).getComponent(i).getNumberOfMolesInPhase()
	    - 1.0 / system.getPhase(0).getNumberOfMolesInPhase()
	    + (system.getPhase(0).getComponent(i).getdfugdn(j) + system.getPhase(0).getComponent(i).getdfugdp()
		* system.getPhase(0).getComponent(j).getVoli() * system.getPhase(0).getdPdVTn() * -1.0));

        Mmatrix.set(i, j, Math.sqrt(
            system.getPhase(0).getComponent(i).getz() * system.getPhase(0).getComponent(j).getz())
            * tempJ);
      }
    }
    LinearAlgebraOps.symmetriseMmatrix(Mmatrix);
  }

  /**
   * <p>
   * Returns the eigenvector associated with the eigenvalue of {@link #Mmatrix} that is closest to
   * zero.
   * </p>
   *
   * <p>
   * At a mixture critical point the smallest eigenvalue of the Q matrix vanishes, and the
   * corresponding eigenvector defines the direction of the critical composition perturbation
   * (Heidemann &amp; Khalil, 1980). Selecting the eigenvector by smallest eigenvalue magnitude
   * &mdash; rather than by a fixed index &mdash; ensures the correct critical direction is used and
   * avoids returning a null (complex) eigenvector.
   * </p>
   *
   * @return the eigenvector of the smallest-magnitude eigenvalue, or {@code null} if no eigenvector
   *         is available
   */
  public double[] getCriticalEigenVector() {
    Eigenvalue<Double> evd = Eigenvalue.PRIMITIVE.make(numberOfComponents, true);
    if (!evd.decompose(Mmatrix.copy())) {
      return null;
    }

    Array1D<ComplexNumber> eigenvalues = evd.getEigenvalues();
    MatrixStore<Double> eigenvectors = evd.getV();
    int n = (int) eigenvalues.count();
    int bestIndex = -1;
    double smallestMagnitude = Double.POSITIVE_INFINITY;

    for (int idx = 0; idx < n; idx++) {
      ComplexNumber eigenvalue = eigenvalues.get(idx);
      double magnitude = Math.abs(eigenvalue.getReal());
      if (magnitude < smallestMagnitude) {
        smallestMagnitude = magnitude;
        bestIndex = idx;
      }
    }
    if (bestIndex < 0) {
      return null;
    }

    double[] vector = new double[numberOfComponents];
    for (int row = 0; row < numberOfComponents; row++) {
      vector[row] = eigenvectors.doubleValue(row, bestIndex);
    }
    return vector;
  }

  /**
   * Calculates the quadratic form $v^T M v$ for a vector and matrix.
   *
   * @param vector perturbation direction vector
   * @param matrix matrix used in the quadratic form
   * @return scalar value of $v^T M v$
   */
  private double quadraticForm(double[] vector, Primitive64Store matrix) {
    double result = 0.0;
    for (int i = 0; i < vector.length; i++) {
      double rowSum = 0.0;
      for (int j = 0; j < vector.length; j++) {
        rowSum += matrix.doubleValue(i, j) * vector[j];
      }
      result += vector[i] * rowSum;
    }
    return result;
  }

  /**
   * <p>
   * calcdpd.
   * </p>
   *
   * @return a double
   */
  public double calcdpd() {
    final double[] oldz = system.getMolarRate();
    double[] eigenVector = getCriticalEigenVector();
    if (eigenVector == null) {
      return Double.NaN;
    }

    double[] newz1 = new double[numberOfComponents];
    double[] newz2 = new double[numberOfComponents];
    double sperturb = 1e-3;
    for (int ii = 0; ii < numberOfComponents; ii++) {
      newz1[ii] = system.getPhase(0).getComponent(ii).getz()
          + sperturb * eigenVector[ii] * Math.sqrt(system.getPhase(0).getComponent(ii).getz());
      newz2[ii] = system.getPhase(0).getComponent(ii).getz()
          - sperturb * eigenVector[ii] * Math.sqrt(system.getPhase(0).getComponent(ii).getz());
    }

    system.setMolarComposition(newz1);
    system.init(3);
    calcMmatrix();
    final double perturb1 = quadraticForm(eigenVector, Mmatrix);

    system.setMolarComposition(newz2);
    system.init(3);
    calcMmatrix();
    double perturb2 = quadraticForm(eigenVector, Mmatrix);

    system.setMolarComposition(oldz);
    system.init(3);

    double dtpddsss = (perturb1 + perturb2) / (sperturb * sperturb);
    return dtpddsss;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    system.init(0);
    system.setTotalNumberOfMoles(1.0);
    system.init(3);

    system.setNumberOfPhases(1);

    Tc0 = system.getPhase(0).getPseudoCriticalTemperature();
    Vc0 = 4 * system.getPhase(0).getB() / system.getPhase(0).getNumberOfMolesInPhase();

    system.setUseTVasIndependentVariables(true);
    system.setNumberOfPhases(1);
    system.setTemperature(Tc0);
    system.getPhase(0).setTotalVolume(Vc0 * system.getTotalNumberOfMoles());
    system.init(3);
    system.init(3);
    // system.display();

    for (int k = 0; k < 13; k++) {
      double dT = 0.1;
      calcMmatrix();
      double[] eigenVector = getCriticalEigenVector();
      if (eigenVector == null) {
        break;
      }
      double evalMatrix = quadraticForm(eigenVector, Mmatrix);
      // Heidemann & Khalil (1980): the temperature is adjusted to drive the smallest eigenvalue of
      // the symmetric Q matrix to zero. The Rayleigh quotient eigenVector' Q eigenVector equals
      // that
      // smallest eigenvalue (the eigenvector is unit-normalised). This is far better scaled than
      // the
      // determinant (a product of all eigenvalues) and avoids the spurious roots the determinant
      // has.
      double detM = evalMatrix;
      int iter = 0;
      system.setTemperature(system.getTemperature() + dT);

      do {
        system.init(3);
        iter++;
        double olddetM = detM;
        calcMmatrix();
        eigenVector = getCriticalEigenVector();
        if (eigenVector == null) {
          break;
        }
        evalMatrix = quadraticForm(eigenVector, Mmatrix);
        detM = evalMatrix;
        double ddetdT = (detM - olddetM) / dT;
        if (ddetdT == 0.0 || Double.isNaN(ddetdT)) {
          break;
        }
        dT = -detM / ddetdT;
        // Limit the Newton step so the search does not jump into a non-physical region where the
        // EOS returns NaN properties (which would make the Q matrix non-decomposable).
        if (Math.abs(dT) > 5.0) {
          dT = Math.signum(dT) * 5.0;
        }
        double oldTemp = system.getTemperature();
        system.setTemperature(oldTemp + dT);
        logger.info("Temperature " + oldTemp + " dT " + dT + " evalMatrix " + evalMatrix);
      } while (Math.abs(dT) > 1e-8 && iter < 112);

      double dVc = Vc0 / 100.0;
      system.init(3);
      double valstart = calcdpd();
      if (Double.isNaN(valstart)) {
        break;
      }
      iter = 0;
      system.getPhase(0).setTotalVolume(system.getPhase(0).getTotalVolume() + dVc);
      double dVOld = 1111110;
      do {
        double oldVal = valstart;
        system.init(3);
        iter++;
        valstart = calcdpd();
        if (Double.isNaN(valstart)) {
          break;
        }
        double ddetdV = (valstart - oldVal) / dVc;
        if (ddetdV == 0.0 || Double.isNaN(ddetdV)) {
          break;
        }
        dVOld = dVc;
        dVc = -valstart / ddetdV;
        system.getPhase(0).setTotalVolume(system.getPhase(0).getVolume() + 0.5 * dVc);
        logger.info("Volume " + system.getPhase(0).getVolume() + " dVc " + dVc + " tddpp "
            + valstart + " pressure " + system.getPressure());
      } while (Math.abs(dVc) > 1e-5 && iter < 112 && (Math.abs(dVc) < Math.abs(dVOld) || iter < 3));
    }
    system.setUseTVasIndependentVariables(false);
    system.init(3);
  }
}

