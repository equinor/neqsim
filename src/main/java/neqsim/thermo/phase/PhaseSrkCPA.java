package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.mixingrule.CPAMixingRuleHandler;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * PhaseSrkCPA class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseSrkCPA extends PhaseSrkEos implements PhaseCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseSrkCPA.class);

  public CPAMixingRuleHandler cpaSelect = new CPAMixingRuleHandler();
  public CPAMixingRulesInterface cpamix;
  double gcpavv = 0.0;
  double gcpavvv = 0.0;
  double gcpa = 0.0;
  double hcpatot = 1.0;
  double FCPA = 0.0;
  double dFCPAdTdV;
  double dFCPAdTdT = 0.0;
  double dFCPAdT = 0;
  double dFCPAdV = 0;
  double dFCPAdVdV = 0.0;
  double dFCPAdVdVdV = 0.0;
  double gcpav = 0.0;
  protected double[] dFdNtemp = {0, 0};
  int cpaon = 1;
  int oldTotalNumberOfAccociationSites = 0;
  int totalNumberOfAccociationSites = 0;
  int[][][] selfAccociationScheme = null;
  int[][][][] crossAccociationScheme = null;
  int[] activeAccosComp = null; // new int[100];
  private double[] lngi;
  int[] moleculeNumber = null;
  int[] assSiteNumber = null;
  private double[][] gvector = null;
  protected double[][] delta = null;
  protected double[][] deltaNog = null;
  private double[][] deltadT = null;
  private double[][] deltadTdT = null;
  double[][][] Klkni = null;
  private SimpleMatrix KlkTVMatrix = null;
  private SimpleMatrix KlkTTMatrix = null;
  private SimpleMatrix KlkTMatrix = null;
  private SimpleMatrix udotTimesmMatrix = null;
  private SimpleMatrix mVector = null;
  private SimpleMatrix udotMatrix = null;
  private SimpleMatrix uMatrix = null;
  private SimpleMatrix QMatksiksiksi = null;
  private SimpleMatrix KlkVVVMatrix = null;
  private SimpleMatrix KlkVVMatrix = null;
  private SimpleMatrix udotTimesmiMatrix = null;
  private SimpleMatrix ksiMatrix = null;
  private SimpleMatrix KlkMatrix = null;
  private SimpleMatrix hessianMatrix = null;
  private SimpleMatrix hessianInvers = null;
  /**
   * Row-major LU factors (partial pivoting) of {@code hessianMatrix}. Reused for every right-hand side instead of an
   * explicit inverse. The association Hessian is small (one row per association site), so the factorization works on a
   * primitive array and allocates nothing in the molar-volume iteration.
   */
  private transient double[] hessianLU = null;
  /** Row interchanges of {@code hessianLU}: row {@code k} was swapped with row {@code hessianPivot[k]}. */
  private transient int[] hessianPivot = null;
  /** Scratch vectors of the association solver, one entry per association site. */
  private transient double[] siteWorkA = null;
  private transient double[] siteWorkB = null;
  /** Per-site scratch of the successive-substitution sweep: owning component, site fraction and moles. */
  private transient ComponentSrkCPA[] siteComps = null;
  private transient double[] siteX = null;
  private transient double[] siteMoles = null;
  private SimpleMatrix KlkVMatrix = null;
  private DMatrixRMaj corr2Matrix = null;
  private DMatrixRMaj corr3Matrix = null;
  private DMatrixRMaj corr4Matrix = null;

  /**
   * Constructor for PhaseSrkCPA.
   */
  public PhaseSrkCPA() {
    thermoPropertyModelName = "SRK-CPA-EoS";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPA clone() {
    PhaseSrkCPA clonedPhase = (PhaseSrkCPA) super.clone();
    if (clonedPhase == null) {
      throw new IllegalStateException("Cannot clone CPA phase: base phase clone failed");
    }
    clonedPhase.dFdNtemp = dFdNtemp == null ? null : dFdNtemp.clone();
    clonedPhase.activeAccosComp = activeAccosComp == null ? null : activeAccosComp.clone();
    clonedPhase.lngi = lngi == null ? null : lngi.clone();
    clonedPhase.moleculeNumber = moleculeNumber == null ? null : moleculeNumber.clone();
    clonedPhase.assSiteNumber = assSiteNumber == null ? null : assSiteNumber.clone();
    clonedPhase.selfAccociationScheme = copyArray(selfAccociationScheme);
    clonedPhase.crossAccociationScheme = copyArray(crossAccociationScheme);
    clonedPhase.gvector = copyArray(gvector);
    clonedPhase.delta = copyArray(delta);
    clonedPhase.deltaNog = copyArray(deltaNog);
    clonedPhase.deltadT = copyArray(deltadT);
    clonedPhase.deltadTdT = copyArray(deltadTdT);
    clonedPhase.Klkni = copyArray(Klkni);
    clonedPhase.KlkTVMatrix = KlkTVMatrix == null ? null : KlkTVMatrix.copy();
    clonedPhase.KlkTTMatrix = KlkTTMatrix == null ? null : KlkTTMatrix.copy();
    clonedPhase.KlkTMatrix = KlkTMatrix == null ? null : KlkTMatrix.copy();
    clonedPhase.udotTimesmMatrix = udotTimesmMatrix == null ? null : udotTimesmMatrix.copy();
    clonedPhase.mVector = mVector == null ? null : mVector.copy();
    clonedPhase.udotMatrix = udotMatrix == null ? null : udotMatrix.copy();
    clonedPhase.uMatrix = uMatrix == null ? null : uMatrix.copy();
    clonedPhase.QMatksiksiksi = QMatksiksiksi == null ? null : QMatksiksiksi.copy();
    clonedPhase.KlkVVVMatrix = KlkVVVMatrix == null ? null : KlkVVVMatrix.copy();
    clonedPhase.KlkVVMatrix = KlkVVMatrix == null ? null : KlkVVMatrix.copy();
    clonedPhase.udotTimesmiMatrix = udotTimesmiMatrix == null ? null : udotTimesmiMatrix.copy();
    clonedPhase.ksiMatrix = ksiMatrix == null ? null : ksiMatrix.copy();
    clonedPhase.KlkMatrix = KlkMatrix == null ? null : KlkMatrix.copy();
    clonedPhase.hessianMatrix = hessianMatrix == null ? null : hessianMatrix.copy();
    clonedPhase.hessianInvers = hessianInvers == null ? null : hessianInvers.copy();
    clonedPhase.KlkVMatrix = KlkVMatrix == null ? null : KlkVMatrix.copy();
    clonedPhase.corr2Matrix = corr2Matrix == null ? null : corr2Matrix.copy();
    clonedPhase.corr3Matrix = corr3Matrix == null ? null : corr3Matrix.copy();
    clonedPhase.corr4Matrix = corr4Matrix == null ? null : corr4Matrix.copy();
    // Factorizations own mutable scratch and cannot be shared. Rebuild lazily from the copied
    // Hessian if a derivative is requested before the clone next solves its association sites.
    clonedPhase.hessianLU = null;
    clonedPhase.hessianPivot = null;
    clonedPhase.siteWorkA = null;
    clonedPhase.siteWorkB = null;
    clonedPhase.siteComps = null;
    clonedPhase.siteX = null;
    clonedPhase.siteMoles = null;
    // Keep the configured CPA mixing-rule parameters shared, as in the EOS clone contract.
    // Concurrent mixing-rule reconfiguration is not supported.

    return clonedPhase;
  }

  /**
   * Copies an owned association matrix without sharing rows.
   *
   * @param source source matrix, possibly null
   * @return independent matrix with the same values and shape, or null
   */
  private static double[][] copyArray(double[][] source) {
    if (source == null) {
      return null;
    }
    double[][] result = source.clone();
    for (int i = 0; i < source.length; i++) {
      result[i] = source[i] == null ? null : source[i].clone();
    }
    return result;
  }

  /**
   * Copies an owned association tensor without sharing nested rows.
   *
   * @param source source tensor, possibly null
   * @return independent tensor with the same values and shape, or null
   */
  private static double[][][] copyArray(double[][][] source) {
    if (source == null) {
      return null;
    }
    double[][][] result = source.clone();
    for (int i = 0; i < source.length; i++) {
      result[i] = copyArray(source[i]);
    }
    return result;
  }

  /**
   * Copies an owned association matrix without sharing rows.
   *
   * @param source source matrix, possibly null
   * @return independent matrix with the same values and shape, or null
   */
  private static int[][] copyArray(int[][] source) {
    if (source == null) {
      return null;
    }
    int[][] result = source.clone();
    for (int i = 0; i < source.length; i++) {
      result[i] = source[i] == null ? null : source[i].clone();
    }
    return result;
  }

  /**
   * Copies an owned association tensor without sharing nested rows.
   *
   * @param source source tensor, possibly null
   * @return independent tensor with the same values and shape, or null
   */
  private static int[][][] copyArray(int[][][] source) {
    if (source == null) {
      return null;
    }
    int[][][] result = source.clone();
    for (int i = 0; i < source.length; i++) {
      result[i] = copyArray(source[i]);
    }
    return result;
  }

  /**
   * Copies an owned association tensor without sharing nested rows.
   *
   * @param source source tensor, possibly null
   * @return independent tensor with the same values and shape, or null
   */
  private static int[][][][] copyArray(int[][][][] source) {
    if (source == null) {
      return null;
    }
    int[][][][] result = source.clone();
    for (int i = 0; i < source.length; i++) {
      result[i] = copyArray(source[i]);
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    boolean changedAssosiationStatus = false;

    if (initType == 0) {
      activeAccosComp = new int[numberOfComponents];
      for (int i = 0; i < numberOfComponents; i++) {
        if (PhaseCPAInterface.hasNegligibleAssociation(componentArray[i], totalNumberOfMoles)) {
          componentArray[i].setNumberOfAssociationSites(0);
          if (activeAccosComp[i] == 1) {
            activeAccosComp[i] = 0;
            changedAssosiationStatus = true;
          }
        } else {
          if (activeAccosComp[i] == 0) {
            changedAssosiationStatus = true;
            activeAccosComp[i] = 1;
          }
        }
      }

      if (changedAssosiationStatus || lngi == null) {
        setTotalNumberOfAccociationSites(0);
        selfAccociationScheme = new int[numberOfComponents][0][0];
        crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
        for (int i = 0; i < numberOfComponents; i++) {
          if (PhaseCPAInterface.hasNegligibleAssociation(componentArray[i], totalNumberOfMoles)) {
            componentArray[i].setNumberOfAssociationSites(0);
          } else {
            componentArray[i].setNumberOfAssociationSites(componentArray[i].getOrginalNumberOfAssociationSites());
            setTotalNumberOfAccociationSites(
                getTotalNumberOfAccociationSites() + componentArray[i].getNumberOfAssociationSites());
            selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
            for (int j = 0; j < numberOfComponents; j++) {
              crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
            }
          }
        }
      }

      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          ((ComponentSrkCPA) componentArray[i]).setXsite(j, 1.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedV(j, 0.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedVdV(j, 0.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedVdVdV(j, 0.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedT(j, 0.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedTdV(j, 0.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedTdT(j, 0.0);
        }
      }

      if (changedAssosiationStatus || lngi == null || mVector == null) {
        lngi = new double[numberOfComponents];
        mVector = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        KlkMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        KlkVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        KlkVVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        KlkVVVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        hessianMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        KlkTMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        KlkTTMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        KlkTVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
        corr2Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
        corr3Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
        corr4Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
        Klkni = new double[numberOfComponents][getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        ksiMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        uMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        udotMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        moleculeNumber = new int[getTotalNumberOfAccociationSites()];
        assSiteNumber = new int[getTotalNumberOfAccociationSites()];
        gvector = new double[getTotalNumberOfAccociationSites()][1];
        udotTimesmMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        delta = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltaNog = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltadT = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltadTdT = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        QMatksiksiksi = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        udotTimesmiMatrix = new SimpleMatrix(numberOfComponents, getTotalNumberOfAccociationSites());

        oldTotalNumberOfAccociationSites = getTotalNumberOfAccociationSites();

        int temp = 0;
        for (int i = 0; i < numberOfComponents; i++) {
          for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
            moleculeNumber[temp + j] = i;
            assSiteNumber[temp + j] = j;
          }
          temp += componentArray[i].getNumberOfAssociationSites();
        }
      }
    }

    if (cpamix == null) {
      // NB! Hardcoded mixing rule type
      cpamix = cpaSelect.getMixingRule(1, this);
    }
    if (initType > 0) {
      calcDelta();
    }

    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    if (initType > 0 && isConstantPhaseVolume()) {
      solveX();
      super.init(totalNumberOfMoles, numberOfComponents, 1, pt, beta);
      gcpa = calc_g();
      gcpav = calc_lngV();
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
    }

    if (initType > 0) {
      hcpatot = calc_hCPA();
    }

    if (initType > 1) {
      initCPAMatrix(initType);
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
  }

  /**
   * initCPAMatrix.
   *
   * @param type a int
   */
  public void initCPAMatrix(int type) {
    if (totalNumberOfAccociationSites == 0) {
      FCPA = 0.0;
      dFCPAdTdV = 0.0;
      dFCPAdTdT = 0.0;
      dFCPAdT = 0;
      dFCPAdV = 0;
      dFCPAdVdV = 0.0;
      dFCPAdVdVdV = 0.0;
      return;
    }

    if (type == 1) {
      // Only the volume derivatives are requested (molar-volume iteration).
      initCPAVolumeDerivatives();
      FCPA = calcFCPAfromSites();
      return;
    }

    int temp = 0;
    double tempVar1;
    double tempVar2;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        tempVar1 = ksiMatrix.get(temp + j, 0);
        tempVar2 = udotMatrix.get(temp + j, 0);
        uMatrix.set(temp + j, 0, Math.log(tempVar1) - tempVar1 + 1.0);
        gvector[temp + j][0] = mVector.get(temp + j, 0) * tempVar2;

        if (moleculeNumber[temp + j] == i) {
          udotTimesmiMatrix.set(i, temp + j, tempVar2);
        } else {
          udotTimesmiMatrix.set(i, temp + j, 0.0);
        }
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }

    if (type > 2) {
      for (int p = 0; p < numberOfComponents; p++) {
        lngi[p] = ((ComponentSrkCPA) componentArray[p]).calc_lngi(this);
      }
    }

    for (int i = 0; i < totalNumberOfAccociationSites; i++) {
      for (int j = i; j < totalNumberOfAccociationSites; j++) {
        delta[i][j] = deltaNog[i][j] * gcpa;
        delta[j][i] = delta[i][j];
        if (type > 1) {
          deltadT[i][j] = cpamix.calcDeltadT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i], moleculeNumber[j],
              this, getTemperature(), getPressure(), numberOfComponents);
          deltadT[j][i] = deltadT[i][j];

          deltadTdT[i][j] = cpamix.calcDeltadTdT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
              moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
          deltadTdT[j][i] = deltadTdT[i][j];
        }
      }
    }

    double totalVolume = getTotalVolume();
    double totalVolume2 = totalVolume * totalVolume;
    double totalVolume3 = totalVolume2 * totalVolume;
    double gdv1 = getGcpav() - 1.0 / totalVolume;
    double gdv2 = gdv1 * gdv1;
    double gdv3 = gdv2 * gdv1;
    double Klk = 0.0;
    double tempVar;
    double tempKsiRead = 0.0;
    for (int i = 0; i < totalNumberOfAccociationSites; i++) {
      for (int j = i; j < totalNumberOfAccociationSites; j++) {
        Klk = KlkMatrix.get(i, j);
        tempVar = Klk * gdv1;
        KlkVMatrix.set(i, j, tempVar);
        KlkVMatrix.set(j, i, tempVar);

        tempVar = Klk * gdv2 + Klk * (gcpavv + 1.0 / totalVolume2);
        KlkVVMatrix.set(i, j, tempVar);
        KlkVVMatrix.set(j, i, tempVar);

        tempVar = Klk * gdv3 + 3.0 * Klk * (gcpav - 1.0 / totalVolume) * (gcpavv + 1.0 / (totalVolume2))
            + Klk * (gcpavvv - 2.0 / (totalVolume3));
        KlkVVVMatrix.set(i, j, tempVar);
        KlkVVVMatrix.set(j, i, tempVar);

        if (type > 1) {
          tempVar = deltadT[i][j] / delta[i][j];

          if (Math.abs(tempVar) > 1e-50) {
            double tempVardT = deltadTdT[i][j] / delta[i][j]
                - (deltadT[i][j] * deltadT[i][j]) / (delta[i][j] * delta[i][j]);

            tempVar2 = Klk * tempVar;
            KlkTMatrix.set(i, j, tempVar2);
            KlkTMatrix.set(j, i, tempVar2);

            tempVar2 = Klk * tempVar * (gcpav - 1.0 / totalVolume);
            KlkTVMatrix.set(i, j, tempVar2);
            KlkTVMatrix.set(j, i, tempVar2);

            tempVar2 = Klk * (tempVar * tempVar + tempVardT);
            KlkTTMatrix.set(i, j, tempVar2);
            KlkTTMatrix.set(j, i, tempVar2);
          }

          if (type > 2) {
            for (int p = 0; p < numberOfComponents; p++) {
              double t1 = 0.0;
              double t2 = 0.0;
              if (moleculeNumber[i] == p) {
                t1 = 1.0 / mVector.get(i, 0);
              }
              if (moleculeNumber[j] == p) {
                t2 = 1.0 / mVector.get(j, 0);
              }
              Klkni[p][i][j] = Klk * (t1 + t2 + lngi[p]); // ((ComponentSrkCPA)
              // getComponent(p)).calc_lngi(this));
              Klkni[p][j][i] = Klkni[p][i][j];
            }
          }
        }
      }
      tempKsiRead = ksiMatrix.get(i, 0);
      QMatksiksiksi.set(i, 0, 2.0 * mVector.get(i, 0) / (tempKsiRead * tempKsiRead * tempKsiRead));
    }

    SimpleMatrix ksiMatrixTranspose = ksiMatrix.transpose();

    // dXdV
    SimpleMatrix KlkVMatrixksi = KlkVMatrix.mult(ksiMatrix);
    SimpleMatrix XV = applyHessianInv(KlkVMatrixksi);
    SimpleMatrix XVtranspose = XV.transpose();

    FCPA = mVector.transpose().mult(uMatrix.minus(ksiMatrix.elementMult(udotMatrix).scale(0.5))).get(0, 0); // QCPA.get(0,
    // 0);
    // //*0.5;

    dFCPAdV = ksiMatrixTranspose.mult(KlkVMatrixksi).get(0, 0) * (-0.5);
    SimpleMatrix KlkVVMatrixTImesKsi = KlkVVMatrix.mult(ksiMatrix);
    dFCPAdVdV = ksiMatrixTranspose.mult(KlkVVMatrixTImesKsi).scale(-0.5).minus(KlkVMatrixksi.transpose().mult(XV))
        .get(0, 0);

    SimpleMatrix QVVV = ksiMatrixTranspose.mult(KlkVVVMatrix.mult(ksiMatrix)); // .scale(-0.5);
    SimpleMatrix QVVksi = KlkVVMatrixTImesKsi.scale(-1.0);
    SimpleMatrix QksiVksi = KlkVMatrix.scale(-1.0);

    dFCPAdVdVdV = -0.5 * QVVV.get(0, 0) + QVVksi.transpose().mult(XV).get(0, 0) * 3.0
        + XVtranspose.mult(QksiVksi.mult(XV)).get(0, 0) * 3.0
        + thirdOrderSiteTerm(QMatksiksiksi.getDDRM().data, XV.getDDRM().data, totalNumberOfAccociationSites);

    // Differentiate the stationary association equations twice and three times
    // with respect to native EOS volume. Each derivative is another right-hand
    // side for the already-factorized association Hessian.
    SimpleMatrix XVV = applyHessianInv(
        KlkVVMatrixTImesKsi.plus(KlkVMatrix.mult(XV).scale(2.0)).minus(QMatksiksiksi.elementMult(XV.elementMult(XV))));
    SimpleMatrix QMatksiksiksiksi = QMatksiksiksi.elementDiv(ksiMatrix).scale(-3.0);
    SimpleMatrix XVVV = applyHessianInv(KlkVVVMatrix.mult(ksiMatrix).plus(KlkVVMatrix.mult(XV).scale(3.0))
        .plus(KlkVMatrix.mult(XVV).scale(3.0)).minus(QMatksiksiksi.elementMult(XV.elementMult(XVV)).scale(3.0))
        .minus(QMatksiksiksiksi.elementMult(XV.elementMult(XV).elementMult(XV))));

    temp = 0;
    for (int p = 0; p < numberOfComponents; p++) {
      for (int kk = 0; kk < getComponent(p).getNumberOfAssociationSites(); kk++) {
        ((ComponentCPAInterface) getComponent(p)).setXsitedV(kk, XV.get(temp + kk, 0));
        ((ComponentSrkCPA) getComponent(p)).setXsitedVdV(kk, XVV.get(temp + kk, 0));
        ((ComponentSrkCPA) getComponent(p)).setXsitedVdVdV(kk, XVVV.get(temp + kk, 0));
      }
      temp += getComponent(p).getNumberOfAssociationSites();
    }

    // KlkTMatrix = new SimpleMatrix(KlkdT);
    SimpleMatrix KlkTMatrixTImesKsi = KlkTMatrix.mult(ksiMatrix);
    // dQdT
    SimpleMatrix tempMatrix2 = ksiMatrixTranspose.mult(KlkTMatrixTImesKsi); // .scale(-0.5);
    dFCPAdT = tempMatrix2.get(0, 0) * (-0.5);

    // SimpleMatrix KlkTVMatrix = new SimpleMatrix(KlkdTdV);
    // SimpleMatrix tempMatrixTV =
    // ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5).minus(KlkTMatrixTImesKsi.transpose().mult(XV));
    // dFCPAdTdV = tempMatrixTV.get(0, 0);
    // dXdT
    SimpleMatrix XT = applyHessianInv(KlkTMatrixTImesKsi);
    // Differentiate the stationary association equations once more. The same
    // Hessian applies; only the right-hand side changes. QMatksiksiksi stores
    // the diagonal third derivative 2*m/X^3 of the association Helmholtz term.
    SimpleMatrix XTT = applyHessianInv(KlkTTMatrix.mult(ksiMatrix).plus(KlkTMatrix.mult(XT).scale(2.0))
        .minus(QMatksiksiksi.elementMult(XT.elementMult(XT))));
    SimpleMatrix XTV = applyHessianInv(KlkTVMatrix.mult(ksiMatrix).plus(KlkTMatrix.mult(XV)).plus(KlkVMatrix.mult(XT))
        .minus(QMatksiksiksi.elementMult(XT.elementMult(XV))));
    // dQdTdT
    SimpleMatrix tempMatrixTT = ksiMatrixTranspose.mult(KlkTTMatrix.mult(ksiMatrix)).scale(-0.5)
        .minus(KlkTMatrixTImesKsi.transpose().mult(XT));
    dFCPAdTdT = tempMatrixTT.get(0, 0);

    SimpleMatrix tempMatrixTV = ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5)
        .minus(KlkTMatrixTImesKsi.transpose().mult(XV));
    dFCPAdTdV = tempMatrixTV.get(0, 0);

    temp = 0;
    for (int p = 0; p < numberOfComponents; p++) {
      for (int kk = 0; kk < getComponent(p).getNumberOfAssociationSites(); kk++) {
        ((ComponentCPAInterface) getComponent(p)).setXsitedT(kk, XT.get(temp + kk, 0));
        ((ComponentSrkCPA) getComponent(p)).setXsitedTdV(kk, XTV.get(temp + kk, 0));
        ((ComponentCPAInterface) getComponent(p)).setXsitedTdT(kk, XTT.get(temp + kk, 0));
      }
      temp += getComponent(p).getNumberOfAssociationSites();
    }

    if (type == 2) {
      return;
    }

    // int assSites = 0;
    // if(true) return;
    for (int p = 0; p < numberOfComponents; p++) {
      SimpleMatrix KiMatrix = new SimpleMatrix(Klkni[p]);
      // KiMatrix.print(10,10);
      // Matrix dQdniMatrix =
      // (ksiMatrix.transpose().times(KiMatrix.times(ksiMatrix)).times(-0.5)); // this
      // methods misses one part of ....
      // dQdniMatrix.print(10,10);
      // KiMatrix.print(10, 10);
      // miMatrix.getMatrix(assSites, assSites, 0, totalNumberOfAccociationSites -
      // 1).print(10, 10);
      // Matrix tempMatrix20 = miMatrix.getMatrix(assSites, assSites, 0,
      // totalNumberOfAccociationSites -
      // 1).times(uMatrix).minus(ksiMatrix.transpose().times(KiMatrix.times(ksiMatrix)).times(-0.5));
      //
      // ksiMatrix.transpose().times(KlkTMatrix.times(ksiMatrix)).times(-0.5);
      // System.out.println("dQdn ");
      // tempMatrix20.print(10, 10);
      SimpleMatrix tempMatrix4 = KiMatrix.mult(ksiMatrix);
      // udotTimesmiMatrix.getMatrix(assSites, assSites, 0,
      // totalNumberOfAccociationSites - 1).print(10, 10);
      SimpleMatrix tempMatrix5 = udotTimesmiMatrix.extractVector(true, p).transpose().minus(tempMatrix4);
      // tempMki[0] = mki[p];
      // Matrix amatrix = new Matrix(croeneckerProduct(tempMki,
      // udotMatrix.getArray()));
      // System.out.println("aMatrix ");
      // amatrix.transpose().print(10, 10);
      // System.out.println("temp4 matrix");
      // tempMatrix4.print(10, 10);
      // Matrix tempMatrix5 = amatrix.minus(tempMatrix4);
      SimpleMatrix tempMatrix6 = applyHessianInv(tempMatrix5); // .scale(-1.0);
      // System.out.println("dXdni");
      // tempMatrix4.print(10, 10);
      // tempMatrix5.print(10, 10);
      // System.out.println("dXdn ");
      // tempMatrix6.print(10, 10);
      int temp2 = 0;
      for (int compp = 0; compp < numberOfComponents; compp++) {
        for (int kk = 0; kk < getComponent(compp).getNumberOfAssociationSites(); kk++) {
          ((ComponentCPAInterface) getComponent(compp)).setXsitedni(kk, p, -1.0 * tempMatrix6.get(temp2 + kk, 0));
        }
        temp2 += getComponent(compp).getNumberOfAssociationSites();
      }
      // assSites += getComponent(p).getNumberOfAssociationSites();
    }
  }

  /**
   * Limit a large proposed change of B/V in the molar-volume iteration.
   *
   * <p>
   * The step keeps the proposed direction and is never shorter than one tenth of the proposal, which is what the
   * iteration always took. When that would move B/V by less than ten percent, the step is extended to ten percent of
   * the current B/V (still shorter than the proposal). A proposal of 15 percent therefore advances 10 percent instead
   * of 1.5 percent, which removes the long sequence of creeping iterations from a distant starting value.
   * </p>
   *
   * @param step proposed change of B/V
   * @param bonV current B/V
   * @return change of B/V to apply
   */
  private static double dampedVolumeStep(double step, double bonV) {
    double damped = 0.1 * step;
    double floor = 0.1 * Math.abs(bonV);
    if (Math.abs(damped) < floor && Math.abs(step) > floor) {
      return Math.copySign(floor, step);
    }
    return damped;
  }

  /**
   * Refresh the association strengths for the current radial distribution function value. {@code deltaNog} depends on
   * temperature only; the volume dependence enters through {@code gcpa}.
   */
  private void updateDeltaWithG() {
    final int n = totalNumberOfAccociationSites;
    final double g = gcpa;
    for (int i = 0; i < n; i++) {
      final double[] deltaRow = delta[i];
      final double[] nogRow = deltaNog[i];
      for (int j = 0; j < n; j++) {
        deltaRow[j] = nogRow[j] * g;
      }
    }
  }

  /**
   * Third-order term of the total volume derivative of the association Helmholtz function: the sum over sites of
   * {@code d3Q/dX_i^3 * (dX_i/dV)^3}. The third derivative of Q with respect to site fractions is diagonal.
   *
   * @param qXXX diagonal third derivative {@code 2 m_i / X_i^3}
   * @param xV site-fraction volume derivatives
   * @param n number of association sites
   * @return the contracted third-order term
   */
  private static double thirdOrderSiteTerm(double[] qXXX, double[] xV, int n) {
    double sum = 0.0;
    for (int i = 0; i < n; i++) {
      final double x = xV[i];
      sum += qXXX[i] * x * x * x;
    }
    return sum;
  }

  /**
   * Association Helmholtz contribution from the site fractions held after the last {@link #solveX()}.
   *
   * @return reduced association Helmholtz energy F_CPA
   */
  private double calcFCPAfromSites() {
    final int n = totalNumberOfAccociationSites;
    final double[] m = mVector.getDDRM().data;
    final double[] ksi = ksiMatrix.getDDRM().data;
    final double[] udot = udotMatrix.getDDRM().data;
    double sum = 0.0;
    for (int i = 0; i < n; i++) {
      final double x = ksi[i];
      sum += m[i] * ((Math.log(x) - x + 1.0) - 0.5 * x * udot[i]);
    }
    return sum;
  }

  /**
   * First, second and third volume derivative of the association Helmholtz function at converged site fractions.
   *
   * <p>
   * Every volume derivative of the association matrix K is a scalar multiple of K, because K = m m' g(V) Delta0 / V.
   * One product K X and one back-substitution with the factored Hessian therefore give all three derivatives without
   * forming the derivative matrices. Used inside the molar-volume iteration, where it allocates nothing.
   * </p>
   */
  private void initCPAVolumeDerivatives() {
    final int n = totalNumberOfAccociationSites;
    updateDeltaWithG();
    if (!hasHessianFactorization(n) && hessianInvers != null) {
      // State produced by the legacy explicit-inverse solver: no factorization to reuse.
      factorHessian(hessianMatrix.getDDRM().data, n);
    } else {
      ensureHessianFactorized();
    }
    if (siteWorkA == null || siteWorkA.length != n) {
      siteWorkA = new double[n];
      siteWorkB = new double[n];
    }
    final double[] m = mVector.getDDRM().data;
    final double[] ksi = ksiMatrix.getDDRM().data;
    final double[] klk = KlkMatrix.getDDRM().data;
    final double[] kx = siteWorkA;
    final double[] xV = siteWorkB;

    final double totalVolume = getTotalVolume();
    final double invV = 1.0 / totalVolume;
    final double gdv1 = getGcpav() - invV;
    final double gvvTerm = gcpavv + invV * invV;
    final double c2 = gdv1 * gdv1 + gvvTerm;
    final double c3 = gdv1 * gdv1 * gdv1 + 3.0 * gdv1 * gvvTerm + (gcpavvv - 2.0 * invV * invV * invV);

    double xKx = 0.0;
    for (int i = 0; i < n; i++) {
      final int in = i * n;
      double sum = 0.0;
      for (int j = 0; j < n; j++) {
        sum += klk[in + j] * ksi[j];
      }
      kx[i] = sum;
      xKx += ksi[i] * sum;
      xV[i] = gdv1 * sum;
    }
    luSolve(hessianLU, hessianPivot, n, xV);

    double kxXV = 0.0;
    double xVKxV = 0.0;
    for (int i = 0; i < n; i++) {
      final int in = i * n;
      double sum = 0.0;
      for (int j = 0; j < n; j++) {
        sum += klk[in + j] * xV[j];
      }
      final double x = ksi[i];
      kxXV += kx[i] * xV[i];
      xVKxV += xV[i] * sum;
      // K X is no longer needed for this site; reuse the slot for d3Q/dX^3 = 2 m / X^3.
      kx[i] = 2.0 * m[i] / (x * x * x);
    }
    final double third = thirdOrderSiteTerm(kx, xV, n);

    dFCPAdV = -0.5 * gdv1 * xKx;
    dFCPAdVdV = -0.5 * c2 * xKx - gdv1 * kxXV;
    dFCPAdVdVdV = -0.5 * c3 * xKx - 3.0 * c2 * kxXV - 3.0 * gdv1 * xVKxV + third;
  }

  /**
   * Calculates mixed amount-temperature, amount-volume, and second-amount derivatives of association-site fractions.
   *
   * <p>
   * This operation is intentionally explicit because the complete amount Hessian requires
   * {@code O(numberOfComponents^2)} association-Hessian solves and storage. Callers must first initialize this phase
   * with derivative level one or higher. This operation refreshes the phase at level three before using its first
   * derivatives. Ordinary CPA initialization does not allocate or calculate these tensors.
   *
   * <p>
   * The derivatives use native EOS volume as the independent volume coordinate, consistently with
   * {@link ComponentSrkCPA#getXsitedV()} and the other CPA site derivatives.
   *
   * @throws IllegalStateException if the phase has not been initialized at derivative level one or higher
   */
  public void calcAssociationSiteCompositionDerivatives() {
    int siteCount = getTotalNumberOfAccociationSites();
    if (siteCount == 0) {
      return;
    }
    if (getInitType() < 1 || Klkni == null || KlkTMatrix == null || KlkVMatrix == null || ksiMatrix == null
        || QMatksiksiksi == null || hessianMatrix == null) {
      throw new IllegalStateException(
          "Initialize the CPA phase with derivative level one or higher before calculating mixed site derivatives");
    }

    // Matrix allocation at level zero does not establish derivative availability. Refresh only on this explicit
    // path, including component covolume derivatives, so ordinary flashes need no additional state tracking.
    init(numberOfMolesInPhase / beta, numberOfComponents, 3, getType(), beta);

    for (int component = 0; component < numberOfComponents; component++) {
      ((ComponentSrkCPA) getComponent(component)).allocateAssociationSiteCompositionDerivatives(numberOfComponents);
    }

    SimpleMatrix xTemperature = associationSiteDerivativeVector(DerivativeCoordinate.TEMPERATURE, 0);
    SimpleMatrix xVolume = associationSiteDerivativeVector(DerivativeCoordinate.VOLUME, 0);
    SimpleMatrix[] xAmount = new SimpleMatrix[numberOfComponents];
    SimpleMatrix[] kAmount = new SimpleMatrix[numberOfComponents];
    for (int component = 0; component < numberOfComponents; component++) {
      xAmount[component] = associationSiteDerivativeVector(DerivativeCoordinate.AMOUNT, component);
      kAmount[component] = new SimpleMatrix(Klkni[component]);

      SimpleMatrix inverseSiteSquared = amountIncidenceVector(component);
      SimpleMatrix amountTemperature = applyHessianInv(
          amountTemperatureMatrix(component).mult(ksiMatrix).plus(kAmount[component].mult(xTemperature))
              .plus(KlkTMatrix.mult(xAmount[component])).plus(inverseSiteSquared.elementMult(xTemperature))
              .minus(QMatksiksiksi.elementMult(xAmount[component].elementMult(xTemperature))));
      SimpleMatrix amountVolume = applyHessianInv(
          amountVolumeMatrix(component).mult(ksiMatrix).plus(kAmount[component].mult(xVolume))
              .plus(KlkVMatrix.mult(xAmount[component])).plus(inverseSiteSquared.elementMult(xVolume))
              .minus(QMatksiksiksi.elementMult(xAmount[component].elementMult(xVolume))));
      storeMixedSiteDerivative(component, amountTemperature, true);
      storeMixedSiteDerivative(component, amountVolume, false);
    }

    for (int first = 0; first < numberOfComponents; first++) {
      SimpleMatrix firstIncidence = amountIncidenceVector(first);
      for (int second = first; second < numberOfComponents; second++) {
        SimpleMatrix secondIncidence = amountIncidenceVector(second);
        SimpleMatrix amountAmount = applyHessianInv(amountAmountMatrix(first, second).mult(ksiMatrix)
            .plus(kAmount[first].mult(xAmount[second])).plus(kAmount[second].mult(xAmount[first]))
            .plus(firstIncidence.elementMult(xAmount[second])).plus(secondIncidence.elementMult(xAmount[first]))
            .minus(QMatksiksiksi.elementMult(xAmount[first].elementMult(xAmount[second]))));
        storeSecondAmountSiteDerivative(first, second, amountAmount);
      }
    }
  }

  /** Coordinates whose first site derivatives are already part of level-three CPA initialization. */
  private enum DerivativeCoordinate {
    TEMPERATURE, VOLUME, AMOUNT
  }

  /**
   * Collects a first association-site derivative in global site order.
   *
   * @param coordinate derivative coordinate
   * @param amountComponent amount component when {@code coordinate} is {@code AMOUNT}
   * @return global site derivative vector
   */
  private SimpleMatrix associationSiteDerivativeVector(DerivativeCoordinate coordinate, int amountComponent) {
    SimpleMatrix result = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
    int globalSite = 0;
    for (int component = 0; component < numberOfComponents; component++) {
      ComponentSrkCPA cpaComponent = (ComponentSrkCPA) getComponent(component);
      for (int site = 0; site < cpaComponent.getNumberOfAssociationSites(); site++) {
        double value;
        if (coordinate == DerivativeCoordinate.TEMPERATURE) {
          value = cpaComponent.getXsitedT()[site];
        } else if (coordinate == DerivativeCoordinate.VOLUME) {
          value = cpaComponent.getXsitedV()[site];
        } else {
          value = cpaComponent.getXsitedni(site, amountComponent);
        }
        result.set(globalSite++, 0, value);
      }
    }
    return result;
  }

  /**
   * Builds the diagonal contribution {@code m_ni / X^2} in global site order.
   *
   * @param amountComponent differentiated component
   * @return incidence vector divided by squared site fractions
   */
  private SimpleMatrix amountIncidenceVector(int amountComponent) {
    SimpleMatrix result = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
    for (int site = 0; site < getTotalNumberOfAccociationSites(); site++) {
      if (moleculeNumber[site] == amountComponent) {
        double x = ksiMatrix.get(site, 0);
        result.set(site, 0, 1.0 / (x * x));
      }
    }
    return result;
  }

  /**
   * Builds {@code d K_ni / dT}.
   *
   * @param amountComponent differentiated component
   * @return mixed matrix derivative
   */
  private SimpleMatrix amountTemperatureMatrix(int amountComponent) {
    SimpleMatrix result = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
    for (int row = 0; row < getTotalNumberOfAccociationSites(); row++) {
      for (int column = 0; column < getTotalNumberOfAccociationSites(); column++) {
        result.set(row, column, KlkTMatrix.get(row, column) * amountMatrixFactor(amountComponent, row, column));
      }
    }
    return result;
  }

  /**
   * Builds {@code d K_ni / dV} at fixed amounts.
   *
   * @param amountComponent differentiated component
   * @return mixed matrix derivative
   */
  private SimpleMatrix amountVolumeMatrix(int amountComponent) {
    SimpleMatrix result = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
    double lngidV = ((ComponentSrkCPA) getComponent(amountComponent)).calc_lngidV(this);
    for (int row = 0; row < getTotalNumberOfAccociationSites(); row++) {
      for (int column = 0; column < getTotalNumberOfAccociationSites(); column++) {
        result.set(row, column, KlkVMatrix.get(row, column) * amountMatrixFactor(amountComponent, row, column)
            + KlkMatrix.get(row, column) * lngidV);
      }
    }
    return result;
  }

  /**
   * Builds {@code d2 K / (dn_i dn_j)} at fixed temperature and native EOS volume.
   *
   * @param first first amount component
   * @param second second amount component
   * @return second amount matrix derivative
   */
  private SimpleMatrix amountAmountMatrix(int first, int second) {
    SimpleMatrix result = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
    double lngij = ((ComponentSrkCPA) getComponent(first)).calc_lngij(second, this);
    for (int row = 0; row < getTotalNumberOfAccociationSites(); row++) {
      for (int column = 0; column < getTotalNumberOfAccociationSites(); column++) {
        double factor = amountMatrixFactor(first, row, column) * amountMatrixFactor(second, row, column) + lngij;
        if (moleculeNumber[row] == first && moleculeNumber[row] == second) {
          double amount = mVector.get(row, 0);
          factor -= 1.0 / (amount * amount);
        }
        if (moleculeNumber[column] == first && moleculeNumber[column] == second) {
          double amount = mVector.get(column, 0);
          factor -= 1.0 / (amount * amount);
        }
        result.set(row, column, KlkMatrix.get(row, column) * factor);
      }
    }
    return result;
  }

  /**
   * Returns the logarithmic amount factor multiplying one association matrix element.
   *
   * @param amountComponent differentiated component
   * @param row global row site
   * @param column global column site
   * @return logarithmic amount derivative factor
   */
  private double amountMatrixFactor(int amountComponent, int row, int column) {
    double factor = lngi[amountComponent];
    if (moleculeNumber[row] == amountComponent) {
      factor += 1.0 / mVector.get(row, 0);
    }
    if (moleculeNumber[column] == amountComponent) {
      factor += 1.0 / mVector.get(column, 0);
    }
    return factor;
  }

  /**
   * Stores one mixed site derivative in component-local site order.
   *
   * @param amountComponent differentiated amount component
   * @param values values in global site order
   * @param temperature whether the other coordinate is temperature rather than volume
   */
  private void storeMixedSiteDerivative(int amountComponent, SimpleMatrix values, boolean temperature) {
    int globalSite = 0;
    for (int component = 0; component < numberOfComponents; component++) {
      ComponentSrkCPA cpaComponent = (ComponentSrkCPA) getComponent(component);
      for (int site = 0; site < cpaComponent.getNumberOfAssociationSites(); site++) {
        if (temperature) {
          cpaComponent.setXsitednidT(site, amountComponent, values.get(globalSite, 0));
        } else {
          cpaComponent.setXsitednidV(site, amountComponent, values.get(globalSite, 0));
        }
        globalSite++;
      }
    }
  }

  /**
   * Stores a symmetric second-amount site derivative in component-local site order.
   *
   * @param first first amount component
   * @param second second amount component
   * @param values values in global site order
   */
  private void storeSecondAmountSiteDerivative(int first, int second, SimpleMatrix values) {
    int globalSite = 0;
    for (int component = 0; component < numberOfComponents; component++) {
      ComponentSrkCPA cpaComponent = (ComponentSrkCPA) getComponent(component);
      for (int site = 0; site < cpaComponent.getNumberOfAssociationSites(); site++) {
        double value = values.get(globalSite++, 0);
        cpaComponent.setXsitednidnj(site, first, second, value);
        cpaComponent.setXsitednidnj(site, second, first, value);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    // NB! Sets EOS mixing rule in parent class
    super.setMixingRule(mr);
    // NB! Ignores input mr, uses CPA
    cpamix = cpaSelect.getMixingRule(1, this);
  }

  /**
   * calcDelta.
   */
  public void calcDelta() {
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        deltaNog[i][j] = cpamix.calcDeltaNog(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i], moleculeNumber[j],
            this, getTemperature(), getPressure(), numberOfComponents);
        deltaNog[j][i] = deltaNog[i][j];
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSrkCPA(name, moles, molesInPhase, compNumber, this);
    for (int i = 0; i < numberOfComponents; i++) {
      if (componentArray[i] instanceof ComponentSrkCPA) {
        ((ComponentSrkCPA) componentArray[i]).resizeXsitedni(numberOfComponents);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    return super.getF() + cpaon * FCPA();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return super.dFdT() + cpaon * dFCPAdT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return super.dFdTdV() + cpaon * dFCPAdTdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    double dv2 = dFCPAdV();
    return super.dFdV() + cpaon * dv2;
  }

  // @Override
  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return super.dFdVdV() + cpaon * dFCPAdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return super.dFdVdVdV() + cpaon * dFCPAdVdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return super.dFdTdT() + cpaon * dFCPAdTdT();
  }

  /**
   * FCPA.
   *
   * @return a double
   */
  public double FCPA() {
    /*
     * double tot = 0.0; double ans = 0.0; for (int i = 0; i < numberOfComponents; i++) { tot = 0.0; for (int j = 0; j <
     * componentArray[i].getNumberOfAssociationSites(); j++) { double xai = ((ComponentSrkCPA)
     * componentArray[i]).getXsite()[j]; tot += (Math.log(xai) - 1.0 / 2.0 * xai + 1.0 / 2.0); } ans +=
     * componentArray[i].getNumberOfMolesInPhase() * tot; } return ans;
     */
    return FCPA;
  }

  /**
   * dFCPAdV.
   *
   * @return a double
   */
  public double dFCPAdV() {
    // return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav())
    // * hcpatot;
    return dFCPAdV;
  }

  /**
   * dFCPAdVdV.
   *
   * @return a double
   */
  public double dFCPAdVdV() {
    // double sum1 = -1.0 / 2.0 * gcpavv * hcpatot;
    // return -1.0 / getTotalVolume() * dFCPAdV() + sum1;
    return dFCPAdVdV;
  }

  /**
   * dFCPAdVdVdV.
   *
   * @return a double
   */
  public double dFCPAdVdVdV() {
    return dFCPAdVdVdV;
    // return -1.0 / getTotalVolume() * dFCPAdVdV() + 1.0 /
    // Math.pow(getTotalVolume(), 2.0) * dFCPAdV() - hcpatot / (2.0 *
    // Math.pow(getTotalVolume(), 2.0)) * (-getGcpav() - getTotalVolume() * gcpavv)
    // + hcpatot / (2.0 * getTotalVolume()) * (-gcpavv - getTotalVolume() * gcpavvv
    // - gcpavv);
  }

  /**
   * dFCPAdT.
   *
   * @return a double
   */
  public double dFCPAdT() {
    /*
     * double tot = 0.0; double ans = 0.0; for (int i = 0; i < numberOfComponents; i++) { tot = 0.0; for (int j = 0; j <
     * componentArray[i].getNumberOfAssociationSites(); j++) { double xai = ((ComponentSrkCPA)
     * componentArray[i]).getXsite()[j]; double xaidT = ((ComponentSrkCPA) componentArray[i]).getXsitedT()[j]; tot +=
     * 1.0 / xai * xaidT - 0.5 * xaidT; // - 1.0 / 2.0 * xai + 1.0 / 2.0); } ans +=
     * componentArray[i].getNumberOfMolesInPhase() * tot; } System.out.println("dFCPAdT1  " + ans + " dfcpa2 "
     * +dFCPAdT); return ans;
     */
    return dFCPAdT;
  }

  /**
   * dFCPAdTdT.
   *
   * @return a double
   */
  public double dFCPAdTdT() {
    return dFCPAdTdT;
  }

  /**
   * dFCPAdTdV.
   *
   * @return a double
   */
  public double dFCPAdTdV() {
    // System.out.println("dFCPAdTdV1 " + dFCPAdTdV + " dFCPAdTdV " +( 1.0 / (2.0 *
    // getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav()) * hcpatotdT));
    return dFCPAdTdV;
    /*
     * if (totalNumberOfAccociationSites > 0) { return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() *
     * getGcpav()) * hcpatotdT; } else { return 0; }
     */
  }

  /**
   * Calculate and return dFdNtemp. NB! Does not set field <code>dFdNtemp</code>.
   *
   * @return double[]
   */
  protected double[] calcdFdNtemp() {
    double tot1 = 0.0;
    double tot2 = 0.0;
    double tot3 = 0.0;
    double tot4 = 0.0;
    // double temp, temp2;
    for (int k = 0; k < getNumberOfComponents(); k++) {
      tot2 = 0.0;
      tot3 = 0.0;
      // temp = ((ComponentSrkCPA) getComponent(k)).calc_lngi(this);
      // temp2 = ((ComponentSrkCPA) getComponent(k)).calc_lngidV(this);
      for (int i = 0; i < getComponent(k).getNumberOfAssociationSites(); i++) {
        tot2 -= 1.0 * ((ComponentSrkCPA) getComponent(k)).getXsitedV()[i];
        tot3 += (1.0 - ((ComponentSrkCPA) getComponent(k)).getXsite()[i]) * 1.0;
      }
      tot1 += 1.0 / 2.0 * tot2 * getComponent(k).getNumberOfMolesInPhase();
      tot4 += 0.5 * getComponent(k).getNumberOfMolesInPhase() * tot3;
    }
    return new double[] {-tot1, -tot4};
  }

  /**
   * calcXsitedV.
   */
  public void calcXsitedV() {
    if (getTotalNumberOfAccociationSites() > 0) {
      initCPAMatrix(1);
    }
  }

  /**
   * Apply the inverse of the current Hessian to a right-hand side. If an LU factorization is available (cached by
   * {@link #solveX}), it is used to back-solve; otherwise, the explicit {@code hessianInvers} matrix is used when
   * present. A clone retains the Hessian values but owns no factorization, so its first derivative request rebuilds a
   * private LU factorization without changing the cached Hessian or association state.
   *
   * @param rhs right-hand side
   * @return {@code H^{-1} * rhs} as a SimpleMatrix
   */
  private SimpleMatrix applyHessianInv(SimpleMatrix rhs) {
    final int n = totalNumberOfAccociationSites;
    if (!hasHessianFactorization(n) && hessianInvers != null) {
      return hessianInvers.mult(rhs);
    }
    ensureHessianFactorized();
    DMatrixRMaj rhsMat = rhs.getDDRM();
    DMatrixRMaj out = rhsMat.copy();
    final double[] data = out.data;
    final int cols = out.numCols;
    if (cols == 1) {
      luSolve(hessianLU, hessianPivot, n, data);
    } else {
      final double[] column = new double[n];
      for (int c = 0; c < cols; c++) {
        for (int r = 0; r < n; r++) {
          column[r] = data[r * cols + c];
        }
        luSolve(hessianLU, hessianPivot, n, column);
        for (int r = 0; r < n; r++) {
          data[r * cols + c] = column[r];
        }
      }
    }
    return SimpleMatrix.wrap(out);
  }

  /**
   * Factor {@code hessianMatrix} unless a factorization for the current number of sites is already held. A clone keeps
   * the Hessian values but no factorization, so its first derivative request lands here.
   */
  private void ensureHessianFactorized() {
    final int n = totalNumberOfAccociationSites;
    if (hasHessianFactorization(n)) {
      return;
    }
    factorHessian(hessianMatrix.getDDRM().data, n);
  }

  /**
   * Tells whether LU factors for the given number of association sites are held. A clone or a deserialized phase holds
   * none until its first factorization.
   *
   * @param n number of association sites
   * @return true if {@code hessianLU} can be used for back-substitution
   */
  private boolean hasHessianFactorization(int n) {
    return hessianLU != null && hessianPivot != null && hessianLU.length == n * n;
  }

  /**
   * Copy and LU-factor the association Hessian.
   *
   * @param hessian row-major Hessian, left untouched
   * @param n number of association sites
   */
  private void factorHessian(double[] hessian, int n) {
    if (hessianLU == null || hessianLU.length != n * n) {
      hessianLU = new double[n * n];
      hessianPivot = new int[n];
    }
    System.arraycopy(hessian, 0, hessianLU, 0, n * n);
    luFactor(hessianLU, hessianPivot, n);
  }

  /**
   * In-place LU factorization with partial pivoting of a small dense row-major matrix.
   *
   * @param a matrix on entry; unit-lower and upper factors on exit
   * @param pivot receives the row interchanged with row k at step k
   * @param n matrix dimension
   */
  static void luFactor(double[] a, int[] pivot, int n) {
    for (int k = 0; k < n; k++) {
      final int kn = k * n;
      int p = k;
      double max = Math.abs(a[kn + k]);
      for (int i = k + 1; i < n; i++) {
        final double v = Math.abs(a[i * n + k]);
        if (v > max) {
          max = v;
          p = i;
        }
      }
      pivot[k] = p;
      if (p != k) {
        final int pn = p * n;
        for (int j = 0; j < n; j++) {
          final double t = a[kn + j];
          a[kn + j] = a[pn + j];
          a[pn + j] = t;
        }
      }
      final double diag = a[kn + k];
      if (diag == 0.0) {
        continue;
      }
      final double inv = 1.0 / diag;
      for (int i = k + 1; i < n; i++) {
        final int in = i * n;
        final double f = a[in + k] * inv;
        a[in + k] = f;
        if (f != 0.0) {
          for (int j = k + 1; j < n; j++) {
            a[in + j] -= f * a[kn + j];
          }
        }
      }
    }
  }

  /**
   * Solve with factors from {@link #luFactor}, overwriting the right-hand side with the solution.
   *
   * @param lu factors
   * @param pivot row interchanges
   * @param n matrix dimension
   * @param b right-hand side on entry, solution on exit
   */
  static void luSolve(double[] lu, int[] pivot, int n, double[] b) {
    for (int k = 0; k < n; k++) {
      final int p = pivot[k];
      if (p != k) {
        final double t = b[k];
        b[k] = b[p];
        b[p] = t;
      }
    }
    for (int i = 1; i < n; i++) {
      final int in = i * n;
      double sum = b[i];
      for (int j = 0; j < i; j++) {
        sum -= lu[in + j] * b[j];
      }
      b[i] = sum;
    }
    for (int i = n - 1; i >= 0; i--) {
      final int in = i * n;
      double sum = b[i];
      for (int j = i + 1; j < n; j++) {
        sum -= lu[in + j] * b[j];
      }
      b[i] = sum / lu[in + i];
    }
  }

  /**
   * solveX.
   *
   * @return a boolean
   */
  public boolean solveX() {
    final int n = totalNumberOfAccociationSites;
    if (n == 0) {
      return true;
    }

    boolean solvedX = solveX2(15);

    // The matrices are tiny (one row per association site), so work directly on their row-major
    // backing arrays. This keeps the SimpleMatrix fields current for the derivative code without
    // per-element accessor calls or temporaries.
    final double[] m = mVector.getDDRM().data;
    final double[] ksi = ksiMatrix.getDDRM().data;
    final double[] udot = udotMatrix.getDDRM().data;
    final double[] udotTimesm = udotTimesmMatrix.getDDRM().data;
    final double[] klk = KlkMatrix.getDDRM().data;
    final double[] hessian = hessianMatrix.getDDRM().data;
    if (siteWorkA == null || siteWorkA.length != n) {
      siteWorkA = new double[n];
      siteWorkB = new double[n];
    }
    final double[] corr = siteWorkA;

    int iter = 0;
    loadSiteScratch(n);
    final ComponentSrkCPA[] comps = siteComps;
    final int[] siteIdx = assSiteNumber;
    System.arraycopy(siteMoles, 0, m, 0, n);

    final double totvolume = getTotalVolume();
    for (int i = 0; i < n; i++) {
      final double mi = m[i];
      final double[] deltaRow = delta[i];
      for (int j = i; j < n; j++) {
        final double Klk = mi * m[j] / totvolume * deltaRow[j];
        klk[i * n + j] = Klk;
        klk[j * n + i] = Klk;
      }
    }
    boolean solved = true;
    double norm;
    do {
      solved = true;
      iter++;
      for (int k = 0; k < n; k++) {
        final double x = comps[k].getXsite()[siteIdx[k]];
        final double u = 1.0 / x - 1.0;
        ksi[k] = x;
        udot[k] = u;
        udotTimesm[k] = m[k] * u;
      }

      for (int i = 0; i < n; i++) {
        final int in = i * n;
        final double x = ksi[i];
        hessian[in + i] = -m[i] / (x * x) - klk[in + i];
        for (int j = i + 1; j < n; j++) {
          final double v = -klk[in + j];
          hessian[in + j] = v;
          hessian[j * n + i] = v;
        }
      }

      // Factor a copy so hessianMatrix stays readable by the derivative code.
      factorHessian(hessian, n);
      hessianInvers = null;
      if (solvedX) {
        return true;
      }

      // Newton step on the stationarity conditions of the association Q-function.
      norm = 0.0;
      for (int i = 0; i < n; i++) {
        final int in = i * n;
        double sum = 0.0;
        for (int j = 0; j < n; j++) {
          sum += klk[in + j] * ksi[j];
        }
        corr[i] = udotTimesm[i] - sum;
      }
      luSolve(hessianLU, hessianPivot, n, corr);
      for (int i = 0; i < n; i++) {
        norm += corr[i] * corr[i];
      }
      norm = Math.sqrt(norm);

      for (int k = 0; k < n; k++) {
        double newX = ksi[k] - corr[k];
        if (newX < 0) {
          newX = 1e-10;
          solved = false;
        }
        comps[k].setXsite(siteIdx[k], newX);
      }
    } while ((norm > 1e-12 || !solved) && iter < 100);

    return true;
  }

  /**
   * Fill the per-site scratch (owning component, current site fraction, moles) from the components.
   *
   * @param n number of association sites
   */
  private void loadSiteScratch(int n) {
    if (siteComps == null || siteComps.length != n) {
      siteComps = new ComponentSrkCPA[n];
      siteX = new double[n];
      siteMoles = new double[n];
    }
    for (int k = 0; k < n; k++) {
      ComponentSrkCPA c = (ComponentSrkCPA) componentArray[moleculeNumber[k]];
      siteComps[k] = c;
      siteX[k] = c.getXsite()[assSiteNumber[k]];
      siteMoles[k] = c.getNumberOfMolesInPhase();
    }
  }

  /**
   * solveX2.
   *
   * @param maxIter a int
   * @return a boolean
   */
  public boolean solveX2(int maxIter) {
    final int n = totalNumberOfAccociationSites;
    if (n == 0) {
      return true;
    }
    final double totalVolume = getTotalVolume();
    final double invV = 1.0 / totalVolume;

    // Cache into primitive arrays to eliminate O(N^2) virtual calls, casts, and array
    // dereferences from the inner loop. Preserves Gauss-Seidel semantics: the site value
    // written in outer iteration i is visible to sites i+1..n-1 within the same sweep.
    loadSiteScratch(n);
    final ComponentSrkCPA[] comps = siteComps;
    final double[] xArr = siteX;
    final double[] nMoles = siteMoles;
    final int[] siteIdx = assSiteNumber;

    double err = 0.0;
    int iter = 0;
    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < n; i++) {
        final double old = xArr[i];
        final double[] deltaRow = delta[i];
        double sum = 0.0;
        for (int j = 0; j < n; j++) {
          sum += nMoles[j] * deltaRow[j] * xArr[j];
        }
        double neeval = 1.0 / (1.0 + invV * sum);
        xArr[i] = neeval;
        err += Math.abs((old - neeval) / neeval);
      }
    } while (Math.abs(err) > 1e-12 && iter < maxIter);

    // Write back once, after convergence (or max iterations).
    for (int k = 0; k < n; k++) {
      comps[k].setXsite(siteIdx[k], xArr[k]);
    }

    if (Math.abs(err) < 1e-12) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Getter for the field <code>dFdNtemp</code>. Set value by calling function molarVolume.
   *
   * @return the dFdNtemp
   */
  public double[] getdFdNtemp() {
    return dFdNtemp;
  }

  /** {@inheritDoc} */
  @Override
  public double getHcpatot() {
    return hcpatot;
  }

  /**
   * Setter for property hcpatot.
   *
   * @param hcpatot New value of property hcpatot.
   */
  public void setHcpatot(double hcpatot) {
    this.hcpatot = hcpatot;
  }

  /** {@inheritDoc} */
  @Override
  public double getGcpa() {
    return gcpa;
  }

  /**
   * calcRootVolFinder.
   *
   * @param pt the PhaseType of the phase
   * @return a double
   */
  public double calcRootVolFinder(PhaseType pt) {
    double solvedBonVHigh = 0.0;
    double solvedBonVlow = 1.0;
    double oldh = 1;
    // double[][] matrix = new double[2][2000];
    double BonV = 1.0 - 1e-10;
    try {
      // molarVolume(pressure, temperature, A, B, pt);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 1;
    // double Dtemp = 0, dh = 0, gvvv = 0, fvvv = 0, dhh = 0;
    // double d1 = 0, d2 = 0;
    Btemp = getB();
    // Dtemp = getA();
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    for (int i = 0; i < 2000; i++) {
      BonVold = BonV;
      BonV = 1.0 - (i + 1e-6) * 1.0 / 2000.0;
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      gcpa = calc_g();
      // lngcpa =
      // Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      int solveXAttempts = 0;
      while (!solveX() && solveXAttempts < 50) {
        solveXAttempts++;
      }
      if (solveXAttempts >= 50) {
        // solveX failed to converge, skip this BonV value
        oldh = h;
        continue;
      }

      h = BonV - Btemp / numberOfMolesInPhase * dFdV() - pressure * Btemp / (numberOfMolesInPhase * R * temperature);

      if (Math.signum(h) * Math.signum(oldh) < 0 && i > 2) {
        if (solvedBonVlow < 1e-3) {
          solvedBonVlow = (BonV + BonVold) / 2.0;
          if (pt == PhaseType.GAS) {
            break;
          }
        } else {
          solvedBonVHigh = (BonV + BonVold) / 2.0;
          if (pt == PhaseType.LIQUID) {
            break;
          }
        }
      }
      solvedBonVHigh = (BonV + BonVold) / 2.0;
      oldh = h;
      // matrix[0][i] = BonV;
      // matrix[1][i] = h;
    }
    if (solvedBonVlow < 1e-3) {
      solvedBonVlow = solvedBonVHigh;
    }
    // dataPresentation.filehandling.TextFile file = new
    // dataPresentation.filehandling.TextFile();
    // file.setValues(matrix);
    // file.setOutputFileName("D:/temp/temp2.txt");
    // file.createFile();
    if (pt == PhaseType.GAS) {
      return solvedBonVlow;
    } else {
      return solvedBonVHigh;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    double BonV = pt == PhaseType.GAS ? pressure * getB() / (numberOfMolesInPhase * temperature * R)
        : 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    BonV = Math.max(1.0e-8, Math.min(1.0 - 1.0e-8, BonV));
    double BonVold;
    double BonV2;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    double d1 = 0;
    double d2 = 0;
    double Btemp = getB();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 300;
    do {
      iterations++;
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(1.0 / Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }

      // lngcpa =
      // Math.log(gcpa);
      gcpav = calc_lngV();
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      if (totalNumberOfAccociationSites > 0) {
        // The association strength follows the radial distribution function at the current
        // volume. Refresh it before solving the site fractions so that the residual and its
        // volume derivatives belong to the same state in every iteration.
        updateDeltaWithG();
        solveX();
      }

      initCPAMatrix(1);

      BonV2 = BonV * BonV;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV() - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;
      // System.out.println("h " + h + " iter " + iterations + " " + d1 + " d2 " + d2
      // + " d1 / d2 " + (d1 / d2));
      if (Double.isNaN(d1) || Double.isNaN(d2)) {
        return molarVolumeChangePhase(pressure, temperature, A, B, pt);
      } else if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += 0.5 * d1;
      } else if (d1 > d2) {
        return molarVolumeChangePhase(pressure, temperature, A, B, pt);
        // BonV += d2;
        // double hnew = h + d2 * dh;
        // if (Math.abs(hnew) > Math.abs(h)) {
        // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
        // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
        // temperature * R);
        // }
      } else {
        BonV += 0.5 * d1;
      }
      if (Math.abs((BonV - BonVold) / BonV) > 0.1) {
        BonV = BonVold + dampedVolumeStep(BonV - BonVold, BonVold);
      }
      if (Double.isNaN(BonV)) {
        return molarVolumeChangePhase(pressure, temperature, A, B, pt);
      }
      if (BonV < 0) {
        if (iterations < 10) {
          // System.out.println(iterations + " BonV " + BonV);
          BonV = (BonVold + BonV) / 2.0;
        } else {
          return molarVolumeChangePhase(pressure, temperature, A, B, pt);
        }
      }

      if (BonV >= 1.0) {
        if (iterations < 10) {
          BonV = (BonVold + BonV) / 2.0;
        } else {
          return molarVolumeChangePhase(pressure, temperature, A, B, pt);
        }
      }
      /*
       * if (BonV > 0.9999) { if (iterations < 10) { BonV = (BonVold + BonV) / 2.0; } else { // BonV =
       * calcRootVolFinder(pt); // BonV = molarVolumeChangePhase(pressure, temperature, A, B, pt); // BonV = 0.9999; //
       * BonV = pt == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature()) : pressure * getB() /
       * (numberOfMolesInPhase * temperature * R); } } else if (BonV < 0) { if (iterations < 10) { BonV =
       * Math.abs(BonVold + BonV) / 2.0; } else { // BonV = calcRootVolFinder(pt); // return
       * molarVolumeChangePhase(pressure, temperature, A, B, pt); // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
       * getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature * R); } }
       */
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      // h is converged when it is below the absolute tolerance, or when its Newton correction is
      // below the resolution of B/V. Close to B/V = 1 the slope dh is 1e4-1e6, so rounding alone
      // keeps |h| above 1e-12 and the absolute test can never be met.
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10
        || (Math.abs(h) > 1e-12 && Math.abs(d1) > 1.0e-13 * Math.abs(BonV))) && iterations < maxIterations);

    // System.out.println("h failed " + h + " Z" + Z + " iterations " + iterations +
    // " BonV " + BonV);
    // if (Math.abs(h) > 1e-12) {
    // System.out.println("h failed " + h + " Z" + Z + " iterations " + iterations +
    // " BonV " + BonV);
    // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    // }
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + getTotalVolume() + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
    // " +dh + " B " + Btemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    dFdNtemp = calcdFdNtemp();
    return getMolarVolume();
  }

  /**
   * molarVolumeChangePhase.
   *
   * @param pressure a double
   * @param temperature a double
   * @param A a double
   * @param B a double
   * @param pt the PhaseType of the phase
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double molarVolumeChangePhase(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    double BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
        : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    // double BonV = calcRootVolFinder(pt);
    // double BonVInit = BonV;
    if (BonV < 0) {
      BonV = 1.0e-8;
    }

    if (BonV >= 1.0) {
      BonV = 0.9999;
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    // double fvvv = 0, gvvv = 0;
    double d1 = 0;
    double d2 = 0;
    Btemp = getB();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    calcDelta();

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;

    do {
      iterations++;
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(1.0 / Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }

      // lngcpa =
      // Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      if (totalNumberOfAccociationSites > 0) {
        updateDeltaWithG();
      }
      solveX();

      initCPAMatrix(1);
      double BonV2 = BonV * BonV;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV() - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;
      // System.out.println("d1" + d1 + " d2 " + d2 + " d1 / d2 " + (d1 / d2));

      if (Double.isNaN(d1) || Double.isNaN(d2)) {
        BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
      } else if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += 0.5 * d1;
      } else if (d1 > d2) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      } else {
        BonV += 0.5 * d1;
      }
      if (Math.abs((BonV - BonVold) / BonVold) > 0.1) {
        BonV = BonVold + 0.1 * (BonV - BonVold);
      }

      if (Double.isNaN(BonV)) {
        BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
      }

      if (BonV > 1.1) {
        if (iterations < 3) {
          BonV = (BonVold + BonV) / 2.0;
        } else {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      if (BonV < 0) {
        if (iterations < 3) {
          BonV = Math.abs(BonVold + BonV) / 2.0;
        } else {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);

      // System.out.println("Z " + Z + "h " + h + " BONV " + (Math.abs((BonV -
      // BonVold) / BonV)));
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10) && iterations < 100);

    /*
     * if (Math.abs(h) > 1e-8) { if (pt == 0) { molarVolume(pressure, temperature, A, B, 1); } else {
     * molarVolume(pressure, temperature, A, B, 0); } return getMolarVolume(); }
     */
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + getTotalVolume() + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh + " B
    // " + Btemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolumeChangePhase", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }

    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume2(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    Z = pt == PhaseType.LIQUID ? 1.0 : 1.0e-5;
    setMolarVolume(Z * R * temperature / pressure);
    // super.molarVolume(pressure,temperature, A, B, phase);
    int iterations = 0;
    double err = 0.0;
    double dErrdV = 0.0;
    double deltaV = 0;

    do {
      iterations = iterations + 1;
      A = calcA(this, temperature, pressure, numberOfComponents);
      B = calcB(this, temperature, pressure, numberOfComponents);

      double dFdV = dFdV();
      double dFdVdV = dFdVdV();
      // double dFdVdVdV = dFdVdVdV();
      // double factor1 = 1.0e0, factor2 = 1.0e0;
      err = -R * temperature * dFdV + R * temperature / getMolarVolume() - pressure;

      // System.out.println("pressure " + -R * temperature * dFdV + " " + R *
      // temperature / getMolarVolume());
      // -pressure;
      dErrdV = -R * temperature * dFdVdV - R * temperature * numberOfMolesInPhase / Math.pow(getVolume(), 2.0);

      // System.out.println("errdV " + dErrdV);
      // System.out.println("err " + err);
      deltaV = -err / dErrdV;

      setMolarVolume(getMolarVolume() + deltaV / numberOfMolesInPhase);

      Z = pressure * getMolarVolume() / (R * temperature);
      if (Z < 0) {
        Z = 1e-6;
        setMolarVolume(Z * R * temperature / pressure);
      }
      // System.out.println("Z " + Z);
    } while (Math.abs(err) > 1.0e-8 || iterations < 100);
    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double getGcpav() {
    return gcpav;
  }

  /**
   * Setter for the field <code>gcpav</code>.
   *
   * @param gcpav a double
   */
  public void setGcpav(double gcpav) {
    this.gcpav = gcpav;
  }

  /** {@inheritDoc} */
  @Override
  public CPAMixingRulesInterface getCpaMixingRule() {
    return cpamix;
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    gcpa = calc_g();
    // lngcpa =
    // Math.log(gcpa);
    setGcpav(calc_lngV());
    gcpavv = calc_lngVV();
    gcpavvv = calc_lngVVV();
    solveX();
    hcpatot = calc_hCPA();

    initCPAMatrix(1);
    return super.calcPressure();
  }

  /** {@inheritDoc} */
  @Override
  public int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2) {
    if (comp1 == comp2) {
      return selfAccociationScheme[comp1][site1][site2];
    } else {
      return crossAccociationScheme[comp1][comp2][site1][site2];
    }
  }

  /**
   * croeneckerProduct.
   *
   * @param a an array of type double
   * @param b an array of type double
   * @return an array of type double
   */
  public double[][] croeneckerProduct(double[][] a, double[][] b) {
    int aLength = a.length;
    int aCols = a[0].length;
    int bLength = b.length;
    int bCols = b[0].length;
    double[][] result = new double[aLength * bLength][(aCols) * (bCols)];
    for (int z = 0; z < aLength; z++) {
      for (int i = 0; i < aCols; i++) {
        for (int j = 0; j < bLength; j++) {
          for (int k = 0; k < bCols; k++) {
            result[j + (z * bLength)][k + (i * bCols)] = a[z][i] * b[j][k];
          }
        }
      }
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public int getTotalNumberOfAccociationSites() {
    return totalNumberOfAccociationSites;
  }

  /** {@inheritDoc} */
  @Override
  public void setTotalNumberOfAccociationSites(int totalNumberOfAccociationSites) {
    this.totalNumberOfAccociationSites = totalNumberOfAccociationSites;
  }

  /**
   * initOld2.
   *
   * @param totalNumberOfMoles a double
   * @param numberOfComponents a int
   * @param type a int
   * @param pt the PhaseType of the phase
   * @param beta a double
   */
  public void initOld2(double totalNumberOfMoles, int numberOfComponents, int type, PhaseType pt, double beta) {
    // type = 0 start init, type = 1 gi nye betingelser
    if (type == 0) {
      setTotalNumberOfAccociationSites(0);
      selfAccociationScheme = new int[numberOfComponents][0][0];
      crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
      for (int i = 0; i < numberOfComponents; i++) {
        if (PhaseCPAInterface.hasNegligibleAssociation(componentArray[i], totalNumberOfMoles)) {
          componentArray[i].setNumberOfAssociationSites(0);
        } else {
          componentArray[i].setNumberOfAssociationSites(componentArray[i].getOrginalNumberOfAssociationSites());
          setTotalNumberOfAccociationSites(
              getTotalNumberOfAccociationSites() + componentArray[i].getNumberOfAssociationSites());
          selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
          for (int j = 0; j < numberOfComponents; j++) {
            crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
          }
        }
      }

      // had to remove if below - dont understand why.. Even
      // if (getTotalNumberOfAccociationSites() != oldTotalNumberOfAccociationSites) {
      mVector = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      KlkMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVVVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      hessianMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTTMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      corr2Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      corr3Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      corr4Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      Klkni = new double[numberOfComponents][getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      ksiMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      uMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      udotMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      moleculeNumber = new int[getTotalNumberOfAccociationSites()];
      assSiteNumber = new int[getTotalNumberOfAccociationSites()];
      gvector = new double[getTotalNumberOfAccociationSites()][1];
      udotTimesmMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      delta = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltaNog = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltadT = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltadTdT = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      QMatksiksiksi = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      // }
      udotTimesmiMatrix = new SimpleMatrix(getNumberOfComponents(), getTotalNumberOfAccociationSites());

      oldTotalNumberOfAccociationSites = getTotalNumberOfAccociationSites();

      int temp = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          moleculeNumber[temp + j] = i;
          assSiteNumber[temp + j] = j;
        }
        temp += componentArray[i].getNumberOfAssociationSites();
      }
    }
    if (cpamix == null) {
      // NB! Hardcoded mixing rule type
      cpamix = cpaSelect.getMixingRule(1, this);
    }

    super.init(totalNumberOfMoles, numberOfComponents, type, pt, beta);
    if (type > 0 && isConstantPhaseVolume()) {
      calcDelta();
      solveX();
      super.init(totalNumberOfMoles, numberOfComponents, 1, pt, beta);
      gcpa = calc_g();
      // lngcpa = Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
    }

    if (type > 0) {
      hcpatot = calc_hCPA();
    }

    if (type > 1) {
      initCPAMatrix(type);
      // hcpatotdT = calc_hCPAdT();
      // super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }
  }

  /**
   * initCPAMatrixOld.
   *
   * @param type a int
   */
  public void initCPAMatrixOld(int type) {
    if (getTotalNumberOfAccociationSites() == 0) {
      FCPA = 0.0;
      dFCPAdTdV = 0.0;
      dFCPAdTdT = 0.0;
      dFCPAdT = 0;
      dFCPAdV = 0;
      dFCPAdVdV = 0.0;
      dFCPAdVdVdV = 0.0;

      return;
    }

    int temp = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        uMatrix.set(temp + j, 0, Math.log(ksiMatrix.get(temp + j, 0)) - ksiMatrix.get(temp + j, 0) + 1.0);
        gvector[temp + j][0] = mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0);
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }
    for (int i = 0; i < getNumberOfComponents(); i++) {
      for (int j = 0; j < getTotalNumberOfAccociationSites(); j++) {
        if (moleculeNumber[j] == i) {
          udotTimesmiMatrix.set(i, j, udotMatrix.get(j, 0));
        } else {
          udotTimesmiMatrix.set(i, j, 0.0);
        }
      }
    }

    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        delta[i][j] = deltaNog[i][j] * getGcpa();
        delta[j][i] = delta[i][j];
        if (type > 1) {
          deltadT[i][j] = cpamix.calcDeltadT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i], moleculeNumber[j],
              this, getTemperature(), getPressure(), numberOfComponents);
          deltadT[j][i] = deltadT[i][j];

          deltadTdT[i][j] = cpamix.calcDeltadTdT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
              moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
          deltadTdT[j][i] = deltadTdT[i][j];
        }
      }
    }

    double totalVolume = getTotalVolume();
    double totalVolume2 = totalVolume * totalVolume;
    double totalVolume3 = totalVolume2 * totalVolume;
    double gdv1 = getGcpav() - 1.0 / totalVolume;
    double gdv2 = gdv1 * gdv1;
    double gdv3 = gdv2 * gdv1;
    // double Klk = 0.0;
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        KlkVMatrix.set(i, j, KlkMatrix.get(i, j) * gdv1);
        KlkVMatrix.set(j, i, KlkVMatrix.get(i, j));

        KlkVVMatrix.set(i, j,
            KlkMatrix.get(i, j) * gdv2 + KlkMatrix.get(i, j) * (gcpavv + 1.0 / totalVolume / totalVolume));
        KlkVVMatrix.set(j, i, KlkVVMatrix.get(i, j));

        KlkVVVMatrix.set(i, j,
            KlkMatrix.get(i, j) * gdv3
                + 3.0 * KlkMatrix.get(i, j) * (getGcpav() - 1.0 / totalVolume) * (gcpavv + 1.0 / (totalVolume2))
                + KlkMatrix.get(i, j) * (gcpavvv - 2.0 / (totalVolume3)));
        KlkVVVMatrix.set(j, i, KlkVVVMatrix.get(i, j));

        if (type > 1) {
          double tempVar = deltadT[i][j] / delta[i][j];
          double tempVardT = deltadTdT[i][j] / delta[i][j]
              - (deltadT[i][j] * deltadT[i][j]) / (delta[i][j] * delta[i][j]);

          if (!Double.isNaN(tempVar)) {
            // KlkdT[i][j] = KlkMatrix.getMatrix().unsafe_get(i, j) * tempVar;
            // KlkdT[j][i] = KlkdT[i][j];

            KlkTMatrix.set(i, j, KlkMatrix.get(i, j) * tempVar);
            KlkTMatrix.set(j, i, KlkTMatrix.get(i, j));

            KlkTVMatrix.set(i, j, KlkMatrix.get(i, j) * tempVar * (gcpav - 1.0 / totalVolume));
            KlkTVMatrix.set(j, i, KlkTVMatrix.get(i, j));

            KlkTTMatrix.set(i, j, KlkMatrix.get(i, j) * (tempVar * tempVar + tempVardT));
            KlkTTMatrix.set(j, i, KlkTTMatrix.get(i, j));
          }

          if (type > 2) {
            for (int p = 0; p < numberOfComponents; p++) {
              double t1 = 0.0;
              double t2 = 0.0;
              if (moleculeNumber[i] == p) {
                t1 = 1.0 / mVector.get(i, 0);
              }
              if (moleculeNumber[j] == p) {
                t2 = 1.0 / mVector.get(j, 0);
              }
              Klkni[p][i][j] = KlkMatrix.get(i, j) * (t1 + t2 + ((ComponentSrkCPA) getComponent(p)).calc_lngi(this));
              Klkni[p][j][i] = Klkni[p][i][j];
            }
          }
        }
      }
      QMatksiksiksi.set(i, 0,
          2.0 * mVector.get(i, 0) / (ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0)));
    }

    SimpleMatrix ksiMatrixTranspose = ksiMatrix.transpose();

    // dXdV
    SimpleMatrix KlkVMatrixksi = KlkVMatrix.mult(ksiMatrix);
    SimpleMatrix XV = hessianInvers.mult(KlkVMatrixksi);
    SimpleMatrix XVtranspose = XV.transpose();

    SimpleMatrix QCPA = mVector.transpose().mult(uMatrix.minus(ksiMatrix.elementMult(udotMatrix).scale(0.5)));
    FCPA = QCPA.get(0, 0);

    SimpleMatrix tempMatrix = ksiMatrixTranspose.mult(KlkVMatrixksi).scale(-0.5);
    dFCPAdV = tempMatrix.get(0, 0);
    SimpleMatrix KlkVVMatrixTImesKsi = KlkVVMatrix.mult(ksiMatrix);
    SimpleMatrix tempMatrixVV = ksiMatrixTranspose.mult(KlkVVMatrixTImesKsi).scale(-0.5)
        .minus(KlkVMatrixksi.transpose().mult(XV));
    dFCPAdVdV = tempMatrixVV.get(0, 0);

    SimpleMatrix QVVV = ksiMatrixTranspose.mult(KlkVVVMatrix.mult(ksiMatrix)).scale(-0.5);
    SimpleMatrix QVVksi = KlkVVMatrixTImesKsi.scale(-1.0);
    SimpleMatrix QksiVksi = KlkVMatrix.scale(-1.0);

    SimpleMatrix mat1 = QVVksi.transpose().mult(XV).scale(3.0);
    SimpleMatrix mat2 = XVtranspose.mult(QksiVksi.mult(XV)).scale(3.0);
    SimpleMatrix mat4 = XVtranspose.mult(QMatksiksiksi.mult(XVtranspose)).mult(XV);

    SimpleMatrix dFCPAdVdVdVMatrix = QVVV.plus(mat1).plus(mat2).plus(mat2).plus(mat4);
    dFCPAdVdVdV = dFCPAdVdVdVMatrix.get(0, 0);
    temp = 0;

    if (type == 1) {
      return;
    }
    for (int p = 0; p < numberOfComponents; p++) {
      for (int kk = 0; kk < getComponent(p).getNumberOfAssociationSites(); kk++) {
        ((ComponentCPAInterface) getComponent(p)).setXsitedV(kk, XV.get(temp + kk, 0));
      }
      temp += getComponent(p).getNumberOfAssociationSites();
    }

    // KlkTMatrix = new SimpleMatrix(KlkdT);
    SimpleMatrix KlkTMatrixTImesKsi = KlkTMatrix.mult(ksiMatrix);
    // dQdT
    SimpleMatrix tempMatrix2 = ksiMatrixTranspose.mult(KlkTMatrixTImesKsi).scale(-0.5);
    dFCPAdT = tempMatrix2.get(0, 0);

    // SimpleMatrix KlkTVMatrix = new SimpleMatrix(KlkdTdV);
    // SimpleMatrix tempMatrixTV =
    // ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5).minus(KlkTMatrixTImesKsi.transpose().mult(XV));
    // dFCPAdTdV = tempMatrixTV.get(0, 0);
    // dXdT
    SimpleMatrix XT = hessianInvers.mult(KlkTMatrixTImesKsi);
    // dQdTdT
    SimpleMatrix tempMatrixTT = ksiMatrixTranspose.mult(KlkTTMatrix.mult(ksiMatrix)).scale(-0.5)
        .minus(KlkTMatrixTImesKsi.transpose().mult(XT));
    dFCPAdTdT = tempMatrixTT.get(0, 0);

    SimpleMatrix tempMatrixTV = ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5)
        .minus(KlkTMatrixTImesKsi.transpose().mult(XV));
    dFCPAdTdV = tempMatrixTV.get(0, 0);

    temp = 0;
    for (int p = 0; p < numberOfComponents; p++) {
      for (int kk = 0; kk < getComponent(p).getNumberOfAssociationSites(); kk++) {
        ((ComponentCPAInterface) getComponent(p)).setXsitedT(kk, XT.get(temp + kk, 0));
      }
      temp += getComponent(p).getNumberOfAssociationSites();
    }

    if (type == 2) {
      return;
    }

    // int assSites = 0;
    // if(true) return;
    for (int p = 0; p < numberOfComponents; p++) {
      SimpleMatrix KiMatrix = new SimpleMatrix(Klkni[p]);
      // KiMatrix.print(10,10);
      // Matrix dQdniMatrix =
      // (ksiMatrix.transpose().times(KiMatrix.times(ksiMatrix)).times(-0.5)); // this
      // methods misses one part of ....
      // dQdniMatrix.print(10,10);
      // KiMatrix.print(10, 10);
      // miMatrix.getMatrix(assSites, assSites, 0, totalNumberOfAccociationSites -
      // 1).print(10, 10);
      // Matrix tempMatrix20 = miMatrix.getMatrix(assSites, assSites, 0,
      // totalNumberOfAccociationSites -
      // 1).times(uMatrix).minus(ksiMatrix.transpose().times(KiMatrix.times(ksiMatrix)).times(-0.5));
      //
      // ksiMatrix.transpose().times(KlkTMatrix.times(ksiMatrix)).times(-0.5);
      // System.out.println("dQdn ");
      // tempMatrix20.print(10, 10);
      SimpleMatrix tempMatrix4 = KiMatrix.mult(ksiMatrix);
      // udotTimesmiMatrix.getMatrix(assSites, assSites, 0,
      // totalNumberOfAccociationSites - 1).print(10, 10);
      SimpleMatrix tempMatrix5 = udotTimesmiMatrix.extractVector(true, p).transpose().minus(tempMatrix4);
      // tempMki[0] = mki[p];
      // Matrix amatrix = new Matrix(croeneckerProduct(tempMki,
      // udotMatrix.getArray()));
      // System.out.println("aMatrix ");
      // amatrix.transpose().print(10, 10);
      // System.out.println("temp4 matrix");
      // tempMatrix4.print(10, 10);
      // Matrix tempMatrix5 = amatrix.minus(tempMatrix4);
      SimpleMatrix tempMatrix6 = hessianInvers.mult(tempMatrix5); // .scale(-1.0);
      // System.out.println("dXdni");
      // tempMatrix4.print(10, 10);
      // tempMatrix5.print(10, 10);
      // System.out.println("dXdn ");
      // tempMatrix6.print(10, 10);
      int temp2 = 0;
      for (int compp = 0; compp < numberOfComponents; compp++) {
        for (int kk = 0; kk < getComponent(compp).getNumberOfAssociationSites(); kk++) {
          ((ComponentCPAInterface) getComponent(compp)).setXsitedni(kk, p, -1.0 * tempMatrix6.get(temp2 + kk, 0));
        }
        temp2 += getComponent(compp).getNumberOfAssociationSites();
      }
      // assSites += getComponent(p).getNumberOfAssociationSites();
    }
  }

  /**
   * solveXOld.
   *
   * @return a boolean
   */
  public boolean solveXOld() {
    if (getTotalNumberOfAccociationSites() == 0) {
      return true;
    }

    boolean solvedX = solveX2(5);
    if (solvedX) {
      // return true;
    }

    DMatrixRMaj mat1 = KlkMatrix.getMatrix();
    DMatrixRMaj mat2 = ksiMatrix.getMatrix();
    // second order method not working correctly and not used t the moment b ecause of numerical
    // stability
    int temp = 0;
    int iter = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        mVector.set(temp + j, 0, componentArray[i].getNumberOfMolesInPhase());
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }
    double Klk = 0.0;
    double totalVolume = getTotalVolume();
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        Klk = mVector.get(i, 0) * mVector.get(j, 0) / totalVolume * delta[i][j];
        KlkMatrix.set(i, j, Klk);
        KlkMatrix.set(j, i, Klk);
      }
    }
    boolean solved = true;
    // SimpleMatrix corrMatrix = null;
    do {
      solved = true;
      iter++;
      temp = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          ksiMatrix.set(temp + j, 0, ((ComponentSrkCPA) componentArray[i]).getXsite()[j]);
          // ksiMatrix.getMatrix().unsafe_set(temp + j, 0,
          // ksiMatrix.getMatrix().unsafe_get(temp + j, 0));
          udotMatrix.set(temp + j, 0, 1.0 / ksiMatrix.get(temp + j, 0) - 1.0);
          udotTimesmMatrix.set(temp + j, 0, mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0));
        }
        temp += componentArray[i].getNumberOfAssociationSites();
      }

      for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
        for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
          int krondelt = 0;
          if (i == j) {
            krondelt = 1;
          }
          hessianMatrix.set(i, j,
              -mVector.get(i, 0) / (ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0)) * krondelt - KlkMatrix.get(i, j));
          hessianMatrix.set(j, i, hessianMatrix.get(i, j));
        }
      }

      // ksiMatrix = new SimpleMatrix(ksi);
      // SimpleMatrix hessianMatrix = new SimpleMatrix(hessian);
      try {
        hessianInvers = hessianMatrix.invert();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        return false;
      }

      CommonOps_DDRM.mult(mat1, mat2, corr2Matrix);
      CommonOps_DDRM.subtract(udotTimesmMatrix.getDDRM(), corr2Matrix, corr3Matrix);
      CommonOps_DDRM.mult(hessianInvers.getDDRM(), corr3Matrix, corr4Matrix);
      // SimpleMatrix gMatrix = udotTimesmMatrix.minus(KlkMatrix.mult(ksiMatrix));
      // corrMatrix =
      // hessianInvers.mult(udotTimesmMatrix.minus(KlkMatrix.mult(ksiMatrix)));
      // //.scale(-1.0);
      temp = 0;
      // System.out.println("print SimpleMatrix ...");
      // corrMatrix.print(10, 10);
      // SimpleMatrix simp = new SimpleMatrix(corr4Matrix);
      // System.out.println("print CommonOps ...");
      // simp.print(10,10);
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          double newX = ksiMatrix.get(temp + j, 0) - corr4Matrix.unsafe_get((temp + j), 0);
          if (newX < 0) {
            newX = 1e-10;
            solved = false;
          }
          ((ComponentCPAInterface) componentArray[i]).setXsite(j, newX);
        }
        temp += componentArray[i].getNumberOfAssociationSites();
      }
      // System.out.println("corrmatrix error " );
      // System.out.println("error " + corrMatrix.norm1());
    } while ((NormOps_DDRM.normF(corr4Matrix) > 1e-12 || !solved) && iter < 100);

    // System.out.println("iter " + iter + " error " + NormOps.normF(corr4Matrix));
    // // corrMatrix.print(10, 10);
    // ksiMatrix.print(10, 10);
    return true;
  }

  /**
   * solveX2Old.
   *
   * @param maxIter a int
   * @return a boolean
   */
  public boolean solveX2Old(int maxIter) {
    double err = .0;
    int iter = 0;
    // if (delta == null) {
    // initCPAMatrix(1);
    double old = 0.0;
    double neeval = 0.0;
    double totalVolume = getTotalVolume();
    // }
    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
        old = ((ComponentSrkCPA) getComponent(moleculeNumber[i])).getXsite()[assSiteNumber[i]];
        neeval = 0;
        for (int j = 0; j < getTotalNumberOfAccociationSites(); j++) {
          neeval += getComponent(moleculeNumber[j]).getNumberOfMolesInPhase() * delta[i][j]
              * ((ComponentSrkCPA) getComponent(moleculeNumber[j])).getXsite()[assSiteNumber[j]];
        }
        neeval = 1.0 / (1.0 + 1.0 / totalVolume * neeval);
        ((ComponentCPAInterface) getComponent(moleculeNumber[i])).setXsite(assSiteNumber[i], neeval);
        err += Math.abs((old - neeval) / neeval);
      }
    } while (Math.abs(err) > 1e-10 && iter < maxIter);
    // System.out.println("iter " + iter);
    // if (Math.abs(err)
    // < 1e-12) {
    // return true;
    // } else {
    // System.out.println("did not solve for Xi in iterations: " + iter);
    // System.out.println("error: " + err);
    return false;
  }

  /**
   * molarVolumeOld.
   *
   * @param pressure a double
   * @param temperature a double
   * @param A a double
   * @param B a double
   * @param pt the PhaseType of the phase
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double molarVolumeOld(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    double BonV = pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
        : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    // if (pressure > 1000) {
    // BonV = 0.9999;
    // }

    // double calcRooBonVtVolFinder = calcRootVolFinder(pt);
    // BonV = calcRooBonVtVolFinder;
    // double BonVInit = BonV;
    if (BonV < 0) {
      BonV = 1.0e-8;
    }

    if (BonV >= 1.0) {
      BonV = 0.9999;
    }
    double BonVold;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    double d1 = 0;
    double d2 = 0;
    double Btemp = getB();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    calcDelta();

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;

    do {
      iterations++;
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(1.0 / Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }

      // lngcpa =
      // Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      if (getTotalNumberOfAccociationSites() > 0) {
        solveX();
      }

      initCPAMatrix(1);
      double BonV2 = BonV * BonV;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV() - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;
      // System.out.println("h " + h + " iter " + iterations + " " + d1 + " d2 " + d2
      // + " d1 / d2 " + (d1 / d2));
      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += 0.5 * d1;
      } else if (d1 > d2) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      } else {
        BonV += 0.5 * d1;
      }
      if (Math.abs((BonV - BonVold) / BonVold) > 0.1) {
        BonV = BonVold + 0.1 * (BonV - BonVold);
      }

      if (BonV > 0.9999) {
        if (iterations < 3) {
          BonV = (BonVold + BonV) / 2.0;
        } else {
          // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
          // BonV = 0.9999;
          // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
          // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
          // temperature * R);
        }
      } else if (BonV < 0) {
        if (iterations < 3) {
          BonV = Math.abs(BonVold + BonV) / 2.0;
        } else {
          // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
          // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
          // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
          // temperature * R);
        }
      }
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10 || Math.abs(h) > 1e-12) && iterations < 100);

    if (Math.abs(h) > 1e-12) {
      // System.out.println("h failed " + "Z" + Z + " iterations " + iterations + "
      // BonV " + BonV);
      // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    }
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + totalVolume + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh + " B
    // " + Btemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());

    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolumeOld", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    return getMolarVolume();
  }
}
