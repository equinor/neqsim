package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.mixingrule.CPAMixingRuleHandler;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * <p>
 * PhaseSrkCPA_proceduralMatrices class.
 * </p>
 *
 * @author Even Solbraa
 * @version Modified to use procedural oriented ejml matrices by Marlene Lund
 */
public class PhaseSrkCPA_proceduralMatrices extends PhaseSrkEos implements PhaseCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseSrkCPA_proceduralMatrices.class);

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
  private double gcpav = 0.0;
  int cpaon = 1;
  int oldTotalNumberOfAccociationSites = 0;
  private int totalNumberOfAccociationSites = 0;
  int[][][] selfAccociationScheme = null;
  int[][][][] crossAccociationScheme = null;
  int[] moleculeNumber = null;
  int[] assSiteNumber = null;
  private double[][] gvector = null;
  private double[][] delta = null;
  private double[][] deltaNog = null;
  private double[][] deltadT = null;
  private double[][] deltadTdT = null;
  private double[][][] Klkni = null;
  private DMatrixRMaj KlkTVMatrix = null;
  private DMatrixRMaj KlkTTMatrix = null;
  private DMatrixRMaj KlkTMatrix = null;
  private DMatrixRMaj udotTimesmMatrix = null;
  private DMatrixRMaj mVector = null;
  private DMatrixRMaj udotMatrix = null;
  private DMatrixRMaj uMatrix = null;
  private DMatrixRMaj QMatksiksiksi = null;
  private DMatrixRMaj KlkVVVMatrix = null;
  private DMatrixRMaj KlkVVMatrix = null;
  private DMatrixRMaj udotTimesmiMatrix = null;
  private DMatrixRMaj ksiMatrix = null;
  private DMatrixRMaj KlkMatrix = null;
  private DMatrixRMaj hessianMatrix = null;
  private DMatrixRMaj hessianInvers = null;
  private DMatrixRMaj KlkVMatrix = null;
  DMatrixRMaj corr2Matrix = null;
  DMatrixRMaj corr3Matrix = null;
  DMatrixRMaj corr4Matrix = null;

  /**
   * <p>
   * Constructor for PhaseSrkCPA_proceduralMatrices.
   * </p>
   */
  public PhaseSrkCPA_proceduralMatrices() {}

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPA clone() {
    PhaseSrkCPA clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    // clonedPhase.cpaSelect = (CPAMixing) cpaSelect.clone();
    // clonedPhase.cpamix = (CPAMixingInterface) cpamix.clone();
    // clonedPhase.cpamix = cpaSelect.getMixingRule(1, this);

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    if (initType == 0) {
      setTotalNumberOfAccociationSites(0);
      selfAccociationScheme = new int[numberOfComponents][0][0];
      crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
      for (int i = 0; i < numberOfComponents; i++) {
        if (getComponent(i).getNumberOfmoles() < 1e-50) {
          getComponent(i).setNumberOfAssociationSites(0);
        } else {
          getComponent(i)
              .setNumberOfAssociationSites(getComponent(i).getOrginalNumberOfAssociationSites());
          setTotalNumberOfAccociationSites(
              getTotalNumberOfAccociationSites() + getComponent(i).getNumberOfAssociationSites());
          selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
          for (int j = 0; j < numberOfComponents; j++) {
            crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
          }
        }
      }

      // had to remove if below - dont understand why.. Even
      // if (getTotalNumberOfAccociationSites() != oldTotalNumberOfAccociationSites) {
      mVector = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      KlkMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVVMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVVVMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      hessianMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTTMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTVMatrix =
          new DMatrixRMaj(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      corr2Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      corr3Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      corr4Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      Klkni =
          new double[numberOfComponents][getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      ksiMatrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      uMatrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      udotMatrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      moleculeNumber = new int[getTotalNumberOfAccociationSites()];
      assSiteNumber = new int[getTotalNumberOfAccociationSites()];
      gvector = new double[getTotalNumberOfAccociationSites()][1];
      udotTimesmMatrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      delta = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltaNog = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltadT = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltadTdT =
          new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      QMatksiksiksi = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
      // }
      udotTimesmiMatrix =
          new DMatrixRMaj(getNumberOfComponents(), getTotalNumberOfAccociationSites());

      oldTotalNumberOfAccociationSites = getTotalNumberOfAccociationSites();

      int temp = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          moleculeNumber[temp + j] = i;
          assSiteNumber[temp + j] = j;
        }
        temp += getComponent(i).getNumberOfAssociationSites();
      }
    }
    if (cpamix == null) {
      // NB! Hardcoded mixing rule type
      cpamix = cpaSelect.getMixingRule(1, this);
    }

    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType > 0 && isConstantPhaseVolume()) {
      calcDelta();
      solveX();
      super.init(totalNumberOfMoles, numberOfComponents, 1, pt, beta);
      gcpa = calc_g();
      // lngcpa = Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
    }

    if (initType > 0) {
      hcpatot = calc_hCPA();
    }

    if (initType > 1) {
      initCPAMatrix(initType);
      // hcpatotdT = calc_hCPAdT();
      // super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }
  }

  /**
   * <p>
   * initCPAMatrix.
   * </p>
   *
   * @param type a int
   */
  public void initCPAMatrix(int type) {
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
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        uMatrix.set(temp + j, 0,
            Math.log(ksiMatrix.get(temp + j, 0)) - ksiMatrix.get(temp + j, 0) + 1.0);
        gvector[temp + j][0] = mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0);
      }
      temp += getComponent(i).getNumberOfAssociationSites();
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
          deltadT[i][j] = cpamix.calcDeltadT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
              moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
          deltadT[j][i] = deltadT[i][j];

          deltadTdT[i][j] =
              cpamix.calcDeltadTdT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
                  moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
          deltadTdT[j][i] = deltadTdT[i][j];
        }
      }
    }

    double gdv1 = getGcpav() - 1.0 / getTotalVolume();
    double gdv2 = gdv1 * gdv1;
    double gdv3 = gdv2 * gdv1;
    double totVol = getTotalVolume();
    // double Klk = 0.0;
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        KlkVMatrix.set(i, j, KlkMatrix.get(i, j) * gdv1);
        KlkVMatrix.set(j, i, KlkVMatrix.get(i, j));

        KlkVVMatrix.set(i, j, KlkMatrix.get(i, j) * gdv2
            + KlkMatrix.get(i, j) * (gcpavv + 1.0 / getTotalVolume() / getTotalVolume()));
        KlkVVMatrix.set(j, i, KlkVVMatrix.get(i, j));

        KlkVVVMatrix.set(i, j,
            KlkMatrix.get(i, j) * gdv3
                + 3.0 * KlkMatrix.get(i, j) * (getGcpav() - 1.0 / getTotalVolume())
                    * (gcpavv + 1.0 / (totVol * totVol))
                + KlkMatrix.get(i, j) * (gcpavvv - 2.0 / (totVol * totVol * totVol)));
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

            KlkTVMatrix.set(i, j, KlkMatrix.get(i, j) * tempVar * (gcpav - 1.0 / getTotalVolume()));
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
              Klkni[p][i][j] = KlkMatrix.get(i, j)
                  * (t1 + t2 + ((ComponentSrkCPA) getComponent(p)).calc_lngi(this));
              Klkni[p][j][i] = Klkni[p][i][j];
            }
          }
        }
      }
      QMatksiksiksi.set(i, 0, 2.0 * mVector.get(i, 0)
          / (ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0)));
    }

    DMatrixRMaj ksiMatrixTranspose = new DMatrixRMaj();
    CommonOps_DDRM.transpose(ksiMatrix, ksiMatrixTranspose);

    // dXdV
    DMatrixRMaj KlkVMatrixksi = new DMatrixRMaj();
    CommonOps_DDRM.mult(KlkVMatrix, ksiMatrix, KlkVMatrixksi);

    DMatrixRMaj XV = new DMatrixRMaj();
    DMatrixRMaj XVtranspose = new DMatrixRMaj();
    CommonOps_DDRM.mult(hessianInvers, KlkVMatrixksi, XV);
    CommonOps_DDRM.transpose(XV, XVtranspose);

    DMatrixRMaj QCPA = new DMatrixRMaj();
    DMatrixRMaj mVectorTranspose = new DMatrixRMaj();
    DMatrixRMaj ksiMatrixudotMatrix = new DMatrixRMaj();
    DMatrixRMaj ksiMatrixudotMatrixScaled = new DMatrixRMaj();
    DMatrixRMaj uMatrixMinusksiMatrixudotMatrixScaled = new DMatrixRMaj();
    DMatrixRMaj ksiMatrixudotMatrixScaledNegative = new DMatrixRMaj();
    CommonOps_DDRM.transpose(mVector, mVectorTranspose);
    CommonOps_DDRM.elementMult(ksiMatrix, udotMatrix, ksiMatrixudotMatrix);
    CommonOps_DDRM.scale(0.5, ksiMatrixudotMatrix, ksiMatrixudotMatrixScaled);
    CommonOps_DDRM.scale(-1.0, ksiMatrixudotMatrixScaled, ksiMatrixudotMatrixScaledNegative);
    CommonOps_DDRM.add(uMatrix, ksiMatrixudotMatrixScaledNegative,
        uMatrixMinusksiMatrixudotMatrixScaled);
    CommonOps_DDRM.mult(mVectorTranspose, uMatrixMinusksiMatrixudotMatrixScaled, QCPA);
    FCPA = QCPA.get(0, 0);

    DMatrixRMaj tempMatrix = new DMatrixRMaj();
    CommonOps_DDRM.mult(ksiMatrixTranspose, KlkVMatrixksi, tempMatrix);
    CommonOps_DDRM.scale(-0.5, tempMatrix);
    dFCPAdV = tempMatrix.get(0, 0);

    DMatrixRMaj KlkVVMatrixTimesKsi = new DMatrixRMaj();
    DMatrixRMaj KlkVMatrixksiTranspose = new DMatrixRMaj();
    DMatrixRMaj KlkVMatrixksiXV = new DMatrixRMaj();
    DMatrixRMaj ksiKlkVVksi = new DMatrixRMaj();
    DMatrixRMaj tempMatrixVV = new DMatrixRMaj();
    CommonOps_DDRM.mult(KlkVVMatrix, ksiMatrix, KlkVVMatrixTimesKsi);
    CommonOps_DDRM.transpose(KlkVMatrixksi, KlkVMatrixksiTranspose);
    CommonOps_DDRM.mult(KlkVMatrixksiTranspose, XV, KlkVMatrixksiXV);
    CommonOps_DDRM.mult(ksiMatrixTranspose, KlkVVMatrixTimesKsi, ksiKlkVVksi);
    CommonOps_DDRM.scale(-0.5, ksiKlkVVksi);
    CommonOps_DDRM.scale(-1.0, KlkVMatrixksiXV);
    CommonOps_DDRM.add(ksiKlkVVksi, KlkVMatrixksiXV, tempMatrixVV);
    dFCPAdVdV = tempMatrixVV.get(0, 0);

    DMatrixRMaj QVVV = new DMatrixRMaj();
    DMatrixRMaj QVVksi = new DMatrixRMaj();
    DMatrixRMaj QksiVksi = new DMatrixRMaj();
    DMatrixRMaj KlkVVVMatrixksi = new DMatrixRMaj();
    CommonOps_DDRM.mult(KlkVVVMatrix, ksiMatrix, KlkVVVMatrixksi);
    CommonOps_DDRM.mult(ksiMatrixTranspose, KlkVVVMatrixksi, QVVV);
    CommonOps_DDRM.scale(-0.5, QVVV);
    CommonOps_DDRM.scale(-1.0, KlkVVMatrixTimesKsi, QVVksi);
    CommonOps_DDRM.scale(-1.0, KlkVMatrix, QksiVksi);

    DMatrixRMaj mat1 = new DMatrixRMaj();
    DMatrixRMaj mat2 = new DMatrixRMaj();
    DMatrixRMaj mat4 = new DMatrixRMaj();
    DMatrixRMaj QVVksiTranspose = new DMatrixRMaj();
    DMatrixRMaj QksiVksiXV = new DMatrixRMaj();
    DMatrixRMaj XVtransposeQMatksiksiksiXVtranspose = new DMatrixRMaj();
    DMatrixRMaj QMatksiksiksiXVtranspose = new DMatrixRMaj();

    CommonOps_DDRM.transpose(QVVksi, QVVksiTranspose);
    CommonOps_DDRM.mult(QVVksiTranspose, XV, mat1);
    CommonOps_DDRM.scale(3.0, mat1);
    CommonOps_DDRM.mult(QksiVksi, XV, QksiVksiXV);
    CommonOps_DDRM.mult(XVtranspose, QksiVksiXV, mat2);
    CommonOps_DDRM.scale(3.0, mat2);
    CommonOps_DDRM.mult(QMatksiksiksi, XVtranspose, QMatksiksiksiXVtranspose);
    CommonOps_DDRM.mult(XVtranspose, QMatksiksiksiXVtranspose, XVtransposeQMatksiksiksiXVtranspose);
    CommonOps_DDRM.mult(XVtransposeQMatksiksiksiXVtranspose, XV, mat4);

    DMatrixRMaj dFCPAdVdVdVMatrix = new DMatrixRMaj();
    dFCPAdVdVdVMatrix.setTo(QVVV);
    CommonOps_DDRM.addEquals(dFCPAdVdVdVMatrix, mat1);
    CommonOps_DDRM.addEquals(dFCPAdVdVdVMatrix, mat2);
    CommonOps_DDRM.addEquals(dFCPAdVdVdVMatrix, mat2);
    CommonOps_DDRM.addEquals(dFCPAdVdVdVMatrix, mat4);
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
    DMatrixRMaj KlkTMatrixTimesKsi = new DMatrixRMaj();
    CommonOps_DDRM.mult(KlkTMatrix, ksiMatrix, KlkTMatrixTimesKsi);

    // dQdT
    DMatrixRMaj tempMatrix2 = new DMatrixRMaj();
    CommonOps_DDRM.mult(ksiMatrixTranspose, KlkTMatrixTimesKsi, tempMatrix2);
    CommonOps_DDRM.scale(-0.5, tempMatrix2);
    dFCPAdT = tempMatrix2.get(0, 0);

    // SimpleMatrix KlkTVMatrix = new SimpleMatrix(KlkdTdV);
    // SimpleMatrix tempMatrixTV =
    // ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5).minus(KlkTMatrixTImesKsi.transpose().mult(XV));
    // dFCPAdTdV = tempMatrixTV.get(0, 0);
    // dXdT
    DMatrixRMaj XT = new DMatrixRMaj();
    CommonOps_DDRM.mult(hessianInvers, KlkTMatrixTimesKsi, XT);

    // dQdTdT
    DMatrixRMaj tempMatrixTT = new DMatrixRMaj();
    DMatrixRMaj KlkTMatrixTimesKsiTranspose = new DMatrixRMaj();
    DMatrixRMaj KlkTMatrixTimesKsiTransposeXT = new DMatrixRMaj();
    DMatrixRMaj KlkTTMatrixksi = new DMatrixRMaj();
    DMatrixRMaj ksiMatrixTransposeKlkTTMatrixksi = new DMatrixRMaj();
    CommonOps_DDRM.transpose(KlkTMatrixTimesKsi, KlkTMatrixTimesKsiTranspose);
    CommonOps_DDRM.mult(KlkTMatrixTimesKsiTranspose, XT, KlkTMatrixTimesKsiTransposeXT);
    CommonOps_DDRM.mult(KlkTTMatrix, ksiMatrix, KlkTTMatrixksi);
    CommonOps_DDRM.mult(ksiMatrixTranspose, KlkTTMatrixksi, ksiMatrixTransposeKlkTTMatrixksi);
    CommonOps_DDRM.scale(-0.5, ksiMatrixTransposeKlkTTMatrixksi);
    CommonOps_DDRM.scale(-1.0, KlkTMatrixTimesKsiTransposeXT);
    CommonOps_DDRM.add(ksiMatrixTransposeKlkTTMatrixksi, KlkTMatrixTimesKsiTransposeXT,
        tempMatrixTT);
    // SimpleMatrix tempMatrixTT =
    // ksiMatrixTranspose.mult(KlkTTMatrix.mult(ksiMatrix)).scale(-0.5).minus(KlkTMatrixTImesKsi.transpose().mult(XT));
    dFCPAdTdT = tempMatrixTT.get(0, 0);

    DMatrixRMaj tempMatrixTV = new DMatrixRMaj();
    DMatrixRMaj KlkTMatrixTimesKsiTransposeXV = new DMatrixRMaj();
    DMatrixRMaj KlkTVMatrixksi = new DMatrixRMaj();
    DMatrixRMaj ksiMatrixTransposeKlkTVMatrixksi = new DMatrixRMaj();
    CommonOps_DDRM.transpose(KlkTMatrixTimesKsi, KlkTMatrixTimesKsiTranspose);
    CommonOps_DDRM.mult(KlkTMatrixTimesKsiTranspose, XV, KlkTMatrixTimesKsiTransposeXV);
    CommonOps_DDRM.mult(KlkTVMatrix, ksiMatrix, KlkTVMatrixksi);
    CommonOps_DDRM.mult(ksiMatrixTranspose, KlkTVMatrixksi, ksiMatrixTransposeKlkTTMatrixksi);
    CommonOps_DDRM.scale(-0.5, ksiMatrixTransposeKlkTVMatrixksi);
    CommonOps_DDRM.scale(-1.0, KlkTMatrixTimesKsiTransposeXV);
    CommonOps_DDRM.add(ksiMatrixTransposeKlkTVMatrixksi, KlkTMatrixTimesKsiTransposeXV,
        tempMatrixTV);
    // SimpleMatrix tempMatrixTV =
    // ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5).minus(KlkTMatrixTImesKsi.transpose().mult(XV));
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
      DMatrixRMaj KiMatrix = new DMatrixRMaj(Klkni[p]);
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
      DMatrixRMaj tempMatrix4 = new DMatrixRMaj();
      CommonOps_DDRM.mult(KiMatrix, ksiMatrix, tempMatrix4);
      // udotTimesmiMatrix.getMatrix(assSites, assSites, 0,
      // totalNumberOfAccociationSites - 1).print(10, 10);
      DMatrixRMaj tempMatrix5 = new DMatrixRMaj();
      DMatrixRMaj extractedVector = new DMatrixRMaj();
      DMatrixRMaj extractedVectorTranspose = new DMatrixRMaj();
      CommonOps_DDRM.extractRow(udotTimesmiMatrix, p, extractedVector);
      CommonOps_DDRM.transpose(extractedVector, extractedVectorTranspose);
      CommonOps_DDRM.changeSign(tempMatrix4);
      CommonOps_DDRM.add(extractedVectorTranspose, tempMatrix4, tempMatrix5);

      // tempMki[0] = mki[p];
      // Matrix amatrix = new Matrix(croeneckerProduct(tempMki,
      // udotMatrix.getArray()));
      // System.out.println("aMatrix ");
      // amatrix.transpose().print(10, 10);
      // System.out.println("temp4 matrix");
      // tempMatrix4.print(10, 10);
      // Matrix tempMatrix5 = amatrix.minus(tempMatrix4);

      DMatrixRMaj tempMatrix6 = new DMatrixRMaj();
      CommonOps_DDRM.mult(hessianInvers, tempMatrix5, tempMatrix6); // .scale(-1.0);
      // System.out.println("dXdni");
      // tempMatrix4.print(10, 10);
      // tempMatrix5.print(10, 10);
      // System.out.println("dXdn ");
      // tempMatrix6.print(10, 10);
      int temp2 = 0;
      for (int compp = 0; compp < numberOfComponents; compp++) {
        for (int kk = 0; kk < getComponent(compp).getNumberOfAssociationSites(); kk++) {
          ((ComponentCPAInterface) getComponent(compp)).setXsitedni(kk, p,
              -1.0 * tempMatrix6.get(temp2 + kk, 0));
        }
        temp2 += getComponent(compp).getNumberOfAssociationSites();
      }
      // assSites += getComponent(p).getNumberOfAssociationSites();
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
   * <p>
   * calcDelta.
   * </p>
   */
  public void calcDelta() {
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        deltaNog[i][j] = cpamix.calcDeltaNog(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
            moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
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
   * <p>
   * FCPA.
   * </p>
   *
   * @return a double
   */
  public double FCPA() {
    /*
     * double tot = 0.0; double ans = 0.0; for (int i = 0; i < numberOfComponents; i++) { tot = 0.0;
     * for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) { double xai =
     * ((ComponentSrkCPA) getComponent(i)).getXsite()[j]; tot += (Math.log(xai) - 1.0 / 2.0 * xai +
     * 1.0 / 2.0); } ans += getComponent(i).getNumberOfMolesInPhase() * tot; } return ans;
     */
    return FCPA;
  }

  /**
   * <p>
   * dFCPAdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdV() {
    // return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav())
    // * hcpatot;
    return dFCPAdV;
  }

  /**
   * <p>
   * dFCPAdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdVdV() {
    // double sum1 = -1.0 / 2.0 * gcpavv * hcpatot;
    // return -1.0 / getTotalVolume() * dFCPAdV() + sum1;
    return dFCPAdVdV;
  }

  /**
   * <p>
   * dFCPAdVdVdV.
   * </p>
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
   * <p>
   * dFCPAdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdT() {
    /*
     * double tot = 0.0; double ans = 0.0; for (int i = 0; i < numberOfComponents; i++) { tot = 0.0;
     * for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) { double xai =
     * ((ComponentSrkCPA) getComponent(i)).getXsite()[j]; double xaidT = ((ComponentSrkCPA)
     * getComponent(i)).getXsitedT()[j]; tot += 1.0 / xai * xaidT - 0.5 * xaidT; // - 1.0 / 2.0 *
     * xai + 1.0 / 2.0); } ans += getComponent(i).getNumberOfMolesInPhase() * tot; }
     * System.out.println("dFCPAdT1  " + ans + " dfcpa2 " +dFCPAdT); return ans;
     */
    return dFCPAdT;
  }

  /**
   * <p>
   * dFCPAdTdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdTdT() {
    return dFCPAdTdT;
  }

  /**
   * <p>
   * dFCPAdTdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdTdV() {
    // System.out.println("dFCPAdTdV1 " + dFCPAdTdV + " dFCPAdTdV " +( 1.0 / (2.0 *
    // getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav()) * hcpatotdT));
    return dFCPAdTdV;
    /*
     * if (totalNumberOfAccociationSites > 0) { return 1.0 / (2.0 * getTotalVolume()) * (1.0 -
     * getTotalVolume() * getGcpav()) * hcpatotdT; } else { return 0; }
     */
  }

  // calc_hCPA, calc_g, calc_lngV, calc_lngVV, calc_lngVVV methods are now provided by
  // PhaseCPAInterface default implementation

  /**
   * <p>
   * calcXsitedV.
   * </p>
   */
  public void calcXsitedV() {
    if (getTotalNumberOfAccociationSites() > 0) {
      initCPAMatrix(1);
    }
  }

  /**
   * <p>
   * solveX.
   * </p>
   *
   * @return a boolean
   */
  public boolean solveX() {
    if (getTotalNumberOfAccociationSites() == 0) {
      return true;
    }

    boolean solvedX = solveX2(5);
    if (solvedX) {
      // return true;
    }

    DMatrixRMaj mat1 = KlkMatrix;
    DMatrixRMaj mat2 = ksiMatrix;
    // second order method not working correctly and not used t the moment b ecause of numerical
    // stability
    int temp = 0;
    int iter = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        mVector.set(temp + j, 0, getComponent(i).getNumberOfMolesInPhase());
      }
      temp += getComponent(i).getNumberOfAssociationSites();
    }
    double Klk = 0.0;
    double totvolume = getTotalVolume();
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        Klk = mVector.get(i, 0) * mVector.get(j, 0) / totvolume * delta[i][j];
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
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          ksiMatrix.set(temp + j, 0, ((ComponentSrkCPA) getComponent(i)).getXsite()[j]);
          // ksiMatrix.getMatrix().unsafe_set(temp + j, 0,
          // ksiMatrix.getMatrix().unsafe_get(temp + j, 0));
          udotMatrix.set(temp + j, 0, 1.0 / ksiMatrix.get(temp + j, 0) - 1.0);
          udotTimesmMatrix.set(temp + j, 0, mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0));
        }
        temp += getComponent(i).getNumberOfAssociationSites();
      }

      for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
        for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
          int krondelt = 0;
          if (i == j) {
            krondelt = 1;
          }
          hessianMatrix.set(i, j,
              -mVector.get(i, 0) / (ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0)) * krondelt
                  - KlkMatrix.get(i, j));
          hessianMatrix.set(j, i, hessianMatrix.get(i, j));
        }
      }

      // ksiMatrix = new SimpleMatrix(ksi);
      // SimpleMatrix hessianMatrix = new SimpleMatrix(hessian);
      try {
        CommonOps_DDRM.invert(hessianMatrix, hessianInvers);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        return false;
      }

      CommonOps_DDRM.mult(mat1, mat2, corr2Matrix);
      CommonOps_DDRM.subtract(udotTimesmMatrix, corr2Matrix, corr3Matrix);
      CommonOps_DDRM.mult(hessianInvers, corr3Matrix, corr4Matrix);
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
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          double newX = ksiMatrix.get(temp + j, 0) - corr4Matrix.unsafe_get((temp + j), 0);
          if (newX < 0) {
            newX = 1e-10;
            solved = false;
          }
          ((ComponentCPAInterface) getComponent(i)).setXsite(j, newX);
        }
        temp += getComponent(i).getNumberOfAssociationSites();
      }
      // System.out.println("corrmatrix error " );
      // System.out.println("error " + corrMatrix.norm1());
    } while ((NormOps_DDRM.normF(corr4Matrix) > 1e-12 || !solved) && iter < 100);

    // System.out.println("iter " + iter + " error " + NormOps.normF(corr4Matrix));
    // // corrMatrix.print(10, 10);
    // ksiMatrix.print(10, 10);
    return true;
  }

  // solveX2 method is now provided by PhaseCPAInterface default implementation

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
   * <p>
   * calcRootVolFinder.
   * </p>
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

      int solveXIter = 0;
      do {
        solveXIter++;
      } while (!solveX() && solveXIter < 50);

      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);

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
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
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

    double h;
    double BonVold;
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
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      double dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      double dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      double d1 = -h / dh;
      double d2 = -dh / dhh;
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
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10 || Math.abs(h) > 1e-12)
        && iterations < 100);

    if (Math.abs(h) > 1e-12) {
      // System.out.println("h failed " + "Z" + Z + " iterations " + iterations + "
      // BonV " + BonV);
      // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    }
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
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    return getMolarVolume();
  }

  /**
   * <p>
   * molarVolumeChangePhase.
   * </p>
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
  public double molarVolumeChangePhase(double pressure, double temperature, double A, double B,
      PhaseType pt) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    // double BonV = pt == 1 ? 2.0 / (2.0 + temperature /
    // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
    // temperature * R);
    double BonV = calcRootVolFinder(pt);
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

      solveX();

      initCPAMatrix(1);
      double BonV2 = BonV * BonV;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;
      // System.out.println("d1" + d1 + " d2 " + d2 + " d1 / d2 " + (d1 / d2));
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
     * if (Math.abs(h) > 1e-8) { if (pt == 0) { molarVolume(pressure, temperature, A, B, 1); } else
     * { molarVolume(pressure, temperature, A, B, 0); } return getMolarVolume(); }
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
      throw new neqsim.util.exception.IsNaNException(this, "molarVolumeChangePhase",
          "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }

    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume2(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
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
      dErrdV = -R * temperature * dFdVdV
          - R * temperature * numberOfMolesInPhase / Math.pow(getVolume(), 2.0);

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
   * <p>
   * Setter for the field <code>gcpav</code>.
   * </p>
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
  public int[] getMoleculeNumber() {
    return moleculeNumber;
  }

  /** {@inheritDoc} */
  @Override
  public int[] getAssSiteNumber() {
    return assSiteNumber;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getCpaDelta() {
    return delta;
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

  // getCrossAssosiationScheme method is now provided by PhaseCPAInterface default implementation

  /** {@inheritDoc} */
  @Override
  public int[][][] getSelfAccociationScheme() {
    return selfAccociationScheme;
  }

  /** {@inheritDoc} */
  @Override
  public int[][][][] getCrossAccociationScheme() {
    return crossAccociationScheme;
  }

  /**
   * <p>
   * croeneckerProduct.
   * </p>
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
}
