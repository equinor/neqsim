package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.Complex_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.system.SystemInterface;

/**
 * CriticalPointFlash class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class CriticalPointFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CriticalPointFlash.class);

  SimpleMatrix Mmatrix = null;
  SimpleMatrix Nmatrix = null;
  SimpleMatrix fmatrix = null;
  int numberOfComponents;
  double Vc0;
  double Tc0;

  /**
   * Constructor for CriticalPointFlash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public CriticalPointFlash(SystemInterface system) {
    this.system = system;
    // clonedsystem = system.clone();
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Mmatrix = new SimpleMatrix(numberOfComponents, numberOfComponents);
    Nmatrix = new SimpleMatrix(numberOfComponents, numberOfComponents);
    fmatrix = new SimpleMatrix(numberOfComponents, 1);
  }

  /**
   * Builds the scaled Q matrix used in the Heidemann &amp; Khalil (1980) critical-point criterion.
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

        Mmatrix.set(i, j,
            Math.sqrt(system.getPhase(0).getComponent(i).getz() * system.getPhase(0).getComponent(j).getz()) * tempJ);
      }
    }
    // Q is theoretically symmetric; symmetrize to guarantee real eigenvalues/eigenvectors.
    Mmatrix = Mmatrix.plus(Mmatrix.transpose()).scale(0.5);
  }

  /**
   * Returns the eigenvector associated with the eigenvalue of {@link #Mmatrix} that is closest to zero.
   *
   * <p>
   * At a mixture critical point the smallest eigenvalue of the Q matrix vanishes, and the corresponding eigenvector
   * defines the direction of the critical composition perturbation (Heidemann &amp; Khalil, 1980). Selecting the
   * eigenvector by smallest eigenvalue magnitude &mdash; rather than by a fixed index &mdash; ensures the correct
   * critical direction is used and avoids returning a null (complex) eigenvector.
   * </p>
   *
   * @return the eigenvector of the smallest-magnitude eigenvalue, or {@code null} if no real eigenvector is available
   */
  public SimpleMatrix getCriticalEigenVector() {
    // Use the dedicated symmetric eigen decomposition. The Q matrix is symmetrized in
    // calcMmatrix(), so a symmetric solver is guaranteed to return real eigenvalues and
    // non-null (real) eigenvectors, unlike the generic SimpleMatrix.eig() which can flag
    // near-zero imaginary parts as complex and then return null eigenvectors.
    EigenDecomposition_F64<DMatrixRMaj> evd = DecompositionFactory_DDRM.eig(numberOfComponents, true, true);
    if (!evd.decompose(Mmatrix.getMatrix().copy())) {
      return null;
    }
    int n = evd.getNumberOfEigenvalues();
    int bestIndex = -1;
    double smallestMagnitude = Double.POSITIVE_INFINITY;
    for (int idx = 0; idx < n; idx++) {
      if (evd.getEigenVector(idx) == null) {
        continue;
      }
      Complex_F64 eigenvalue = evd.getEigenvalue(idx);
      double magnitude = Math.abs(eigenvalue.getReal());
      if (magnitude < smallestMagnitude) {
        smallestMagnitude = magnitude;
        bestIndex = idx;
      }
    }
    if (bestIndex < 0) {
      return null;
    }
    return SimpleMatrix.wrap(evd.getEigenVector(bestIndex));
  }

  /**
   * calcdpd.
   *
   * @return a double
   */
  public double calcdpd() {
    double[] oldz = system.getMolarRate();
    SimpleMatrix eigenVector = getCriticalEigenVector();
    if (eigenVector == null) {
      return Double.NaN;
    }

    double[] newz1 = new double[numberOfComponents];
    double[] newz2 = new double[numberOfComponents];
    double sperturb = 1e-3;
    for (int ii = 0; ii < numberOfComponents; ii++) {
      newz1[ii] = system.getPhase(0).getComponent(ii).getz()
          + sperturb * eigenVector.get(ii) * Math.sqrt(system.getPhase(0).getComponent(ii).getz());
      newz2[ii] = system.getPhase(0).getComponent(ii).getz()
          - sperturb * eigenVector.get(ii) * Math.sqrt(system.getPhase(0).getComponent(ii).getz());
    }

    system.setMolarComposition(newz1);
    system.init(3);
    calcMmatrix();
    // eigenVector = Mmatrix.eig().getEigenVector(0);
    SimpleMatrix evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
    double perturb1 = evalMatrix.get(0, 0);

    system.setMolarComposition(newz2);
    system.init(3);
    calcMmatrix();
    // eigenVector = Mmatrix.eig().getEigenVector(0);
    evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
    double perturb2 = evalMatrix.get(0, 0);

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
      double detM;
      double olddetM;
      double ddetdT;
      double dT = 0.1;
      calcMmatrix();
      SimpleMatrix eigenVector = getCriticalEigenVector();
      if (eigenVector == null) {
        break;
      }
      SimpleMatrix evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
      // Heidemann & Khalil (1980): the temperature is adjusted to drive the smallest eigenvalue of
      // the symmetric Q matrix to zero. The Rayleigh quotient eigenVector' Q eigenVector equals
      // that
      // smallest eigenvalue (the eigenvector is unit-normalised). This is far better scaled than
      // the
      // determinant (a product of all eigenvalues) and avoids the spurious roots the determinant
      // has.
      detM = evalMatrix.get(0, 0);
      int iter = 0;
      system.setTemperature(system.getTemperature() + dT);

      do {
        system.init(3);
        iter++;
        olddetM = detM;
        calcMmatrix();
        eigenVector = getCriticalEigenVector();
        if (eigenVector == null) {
          break;
        }
        evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
        detM = evalMatrix.get(0, 0);
        ddetdT = (detM - olddetM) / dT;
        if (ddetdT == 0.0 || Double.isNaN(ddetdT)) {
          break;
        }
        dT = -detM / ddetdT;
        // Limit the Newton step so the search does not jump into a non-physical region where
        // the
        // EOS returns NaN properties (which would make the Q matrix non-decomposable).
        if (Math.abs(dT) > 5.0) {
          dT = Math.signum(dT) * 5.0;
        }
        double oldTemp = system.getTemperature();
        system.setTemperature(oldTemp + dT);
        logger.info("Temperature " + oldTemp + " dT " + dT + " evalMatrix " + evalMatrix.get(0, 0));
      } while (Math.abs(dT) > 1e-8 && iter < 112);

      double dVc = Vc0 / 100.0;
      double ddetdV;
      double oldVal;
      system.init(3);
      double valstart = calcdpd();
      if (Double.isNaN(valstart)) {
        break;
      }
      iter = 0;
      system.getPhase(0).setTotalVolume(system.getPhase(0).getTotalVolume() + dVc);
      double dVOld = 1111110;
      do {
        oldVal = valstart;
        system.init(3);
        iter++;
        valstart = calcdpd();
        if (Double.isNaN(valstart)) {
          break;
        }
        ddetdV = (valstart - oldVal) / dVc;
        if (ddetdV == 0.0 || Double.isNaN(ddetdV)) {
          break;
        }
        dVOld = dVc;
        dVc = -valstart / ddetdV;
        system.getPhase(0).setTotalVolume(system.getPhase(0).getVolume() + 0.5 * dVc);
        logger.info("Volume " + system.getPhase(0).getVolume() + " dVc " + dVc + " tddpp " + valstart + " pressure "
            + system.getPressure());
      } while (Math.abs(dVc) > 1e-5 && iter < 112 && (Math.abs(dVc) < Math.abs(dVOld) || iter < 3));
    }
    system.setUseTVasIndependentVariables(false);
    system.init(3);
  }
}
