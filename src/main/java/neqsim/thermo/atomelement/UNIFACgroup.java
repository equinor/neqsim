package neqsim.thermo.atomelement;

import java.util.Arrays;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.phase.PhaseGEUnifac;

/**
 * <p>
 * UNIFACgroup class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class UNIFACgroup implements ThermodynamicConstantsInterface, Comparable<UNIFACgroup> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(UNIFACgroup.class);

  double R = 0.0;
  double Q = 0.0;
  int n = 0;
  double xComp = 0.0;
  double QComp = 0.0;
  double QMix = 0.0;
  public double[] QMixdN = null; // , xMixdN = null;
  double[] lnGammaMixdn = new double[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  double lnGammaComp = 0.0;
  double lnGammaMix = 0.0;
  double lnGammaCompdT = 0.0;
  double lnGammaMixdT = 0.0;
  private double lnGammaCompdTdT = 0.0;
  private double lnGammaMixdTdT = 0.0;
  int groupIndex = 0;
  String groupName = "";
  int mainGroup = 0;
  int subGroup = 0;

  /**
   * <p>
   * getQMixdN.
   * </p>
   *
   * @return the QMixdN
   */
  public double[] getQMixdN() {
    return QMixdN;
  }

  /**
   * <p>
   * setQMixdN.
   * </p>
   *
   * @param QMixdN the QMixdN to set
   */
  public void setQMixdN(double[] QMixdN) {
    this.QMixdN = QMixdN;
  }

  /**
   * <p>
   * Constructor for UNIFACgroup.
   * </p>
   */
  public UNIFACgroup() {}

  /**
   * <p>
   * Constructor for UNIFACgroup.
   * </p>
   *
   * @param groupNumber a int
   * @param temp a int
   */
  public UNIFACgroup(int groupNumber, int temp) {
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet = database
            .getResultSet(("SELECT * FROM unifacgroupparam WHERE Secondary=" + groupNumber + ""));
        dataSet.next();
        dataSet.getClob("name");
      } catch (Exception ex) {
        dataSet.close();
        dataSet = database
            .getResultSet(("SELECT * FROM unifacgroupparam WHERE Secondary=" + groupNumber + ""));
        dataSet.next();
      }
      n = temp;
      R = Double.parseDouble(dataSet.getString("VolumeR"));
      Q = Double.parseDouble(dataSet.getString("SurfAreaQ"));
      mainGroup = Integer.parseInt(dataSet.getString("Main"));
      subGroup = Integer.parseInt(dataSet.getString("Secondary"));
      groupName = dataSet.getString("Name");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * Getter for property R.
   *
   * @return Value of property R.
   */
  public double getR() {
    return R;
  }

  /**
   * Setter for property R.
   *
   * @param R New value of property R.
   */
  public void setR(double R) {
    this.R = R;
  }

  /**
   * Getter for property Q.
   *
   * @return Value of property Q.
   */
  public double getQ() {
    return Q;
  }

  /**
   * Setter for property Q.
   *
   * @param Q New value of property Q.
   */
  public void setQ(double Q) {
    this.Q = Q;
  }

  /**
   * Getter for property n.
   *
   * @return Value of property n.
   */
  public int getN() {
    return n;
  }

  /**
   * Setter for property n.
   *
   * @param n New value of property n.
   */
  public void setN(int n) {
    this.n = n;
  }

  /**
   * Getter for property mainGroup.
   *
   * @return Value of property mainGroup.
   */
  public int getMainGroup() {
    return mainGroup;
  }

  /**
   * Setter for property mainGroup.
   *
   * @param mainGroup New value of property mainGroup.
   */
  public void setMainGroup(int mainGroup) {
    this.mainGroup = mainGroup;
  }

  /**
   * Getter for property subGroup.
   *
   * @return Value of property subGroup.
   */
  public int getSubGroup() {
    return subGroup;
  }

  /**
   * Setter for property subGroup.
   *
   * @param subGroup New value of property subGroup.
   */
  public void setSubGroup(int subGroup) {
    this.subGroup = subGroup;
  }

  /**
   * Getter for property groupName.
   *
   * @return Value of property groupName.
   */
  public java.lang.String getGroupName() {
    return groupName;
  }

  /**
   * Setter for property groupName.
   *
   * @param groupName New value of property groupName.
   */
  public void setGroupName(java.lang.String groupName) {
    this.groupName = groupName;
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(UNIFACgroup o) {
    if (o.getSubGroup() < getSubGroup()) {
      return 1;
    } else if (o.getSubGroup() == getSubGroup()) {
      return 0;
    } else {
      return -1;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(QMixdN);
    result = prime * result + Arrays.hashCode(lnGammaMixdn);
    result = prime * result + Objects.hash(Q, QComp, QMix, R, groupIndex, groupName, lnGammaComp,
        lnGammaCompdT, lnGammaCompdTdT, lnGammaMix, lnGammaMixdT, lnGammaMixdTdT, mainGroup, n,
        subGroup, xComp);
    return result;
  }

  /** {@inheritDoc} */
  // @Override
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    UNIFACgroup other = (UNIFACgroup) obj;
    return subGroup == other.subGroup;
    /*
     * Double.doubleToLongBits(Q) == Double.doubleToLongBits(other.Q) &&
     * Double.doubleToLongBits(QComp) == Double.doubleToLongBits(other.QComp) &&
     * Double.doubleToLongBits(QMix) == Double.doubleToLongBits(other.QMix) && Arrays.equals(QMixdN,
     * other.QMixdN) && Double.doubleToLongBits(R) == Double.doubleToLongBits(other.R) && groupIndex
     * == other.groupIndex && Objects.equals(groupName, other.groupName) &&
     * Double.doubleToLongBits(lnGammaComp) == Double .doubleToLongBits(other.lnGammaComp) &&
     * Double.doubleToLongBits(lnGammaCompdT) == Double .doubleToLongBits(other.lnGammaCompdT) &&
     * Double.doubleToLongBits(lnGammaCompdTdT) == Double .doubleToLongBits(other.lnGammaCompdTdT)
     * && Double.doubleToLongBits(lnGammaMix) == Double.doubleToLongBits(other.lnGammaMix) &&
     * Double.doubleToLongBits(lnGammaMixdT) == Double .doubleToLongBits(other.lnGammaMixdT) &&
     * Double.doubleToLongBits(lnGammaMixdTdT) == Double .doubleToLongBits(other.lnGammaMixdTdT) &&
     * Arrays.equals(lnGammaMixdn, other.lnGammaMixdn) && mainGroup == other.mainGroup && n ==
     * other.n && subGroup == other.subGroup && Double.doubleToLongBits(xComp) ==
     * Double.doubleToLongBits(other.xComp);
     */
  }

  /**
   * Getter for property xComp.
   *
   * @param component a {@link neqsim.thermo.component.ComponentGEUnifac} object
   * @return Value of property xComp.
   */
  public double calcXComp(ComponentGEUnifac component) {
    double temp = 0.0;
    for (int i = 0; i < component.getNumberOfUNIFACgroups(); i++) {
      temp += component.getUnifacGroup(i).getN();
    }
    xComp = getN() / temp;
    // System.out.println("xcomp " + xComp);
    return xComp;
  }
  /*
   * public double calcXMix(PhaseGEUnifac phase) { double temp = 0.0, temp2 = 0.0, tempVal = 0.0;
   *
   * for (int j = 0; j < phase.getNumberOfComponents(); j++) { for (int i = 0; i <
   * ((ComponentGEUnifac) phase.getComponent(j)).getNumberOfUNIFACgroups(); i++) { tempVal =
   * phase.getComponent(j).getNumberOfMolesInPhase() * ((ComponentGEUnifac)
   * phase.getComponent(j)).getUnifacGroup(i).getN(); temp += tempVal; if (((ComponentGEUnifac)
   * phase.getComponent(j)).getUnifacGroup(i).getSubGroup() == subGroup) { temp2 += tempVal; } } }
   * xMix = temp2 / temp; return xMix; }
   */

  /**
   * <p>
   * calcQComp.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentGEUnifac} object
   * @return a double
   */
  public double calcQComp(ComponentGEUnifac component) {
    double temp = 0.0;
    for (int i = 0; i < component.getNumberOfUNIFACgroups(); i++) {
      temp += component.getUnifacGroup(i).getXComp() * component.getUnifacGroup(i).getQ();
    }
    QComp = getQ() * getXComp() / temp;
    if (getXComp() == 0) {
      QComp = 0.0;
    }
    // System.out.println("qcomp " + xComp);
    return QComp;
  }

  /**
   * <p>
   * calcQMix.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseGEUnifac} object
   * @return a double
   */
  public double calcQMix(PhaseGEUnifac phase) {
    ComponentGEUnifac component;
    double temp = 0.0;
    double temp2 = 0.0;
    double tempVar;
    double numberOfMoles;
    UNIFACgroup unifacGroup;
    int numberOfGrups = 0;
    for (int j = 0; j < phase.getNumberOfComponents(); j++) {
      component = ((ComponentGEUnifac) phase.getComponent(j));
      numberOfMoles = component.getNumberOfMolesInPhase();
      numberOfGrups = component.getNumberOfUNIFACgroups();
      for (int i = 0; i < numberOfGrups; i++) {
        unifacGroup = component.getUnifacGroup(i);
        tempVar = numberOfMoles * unifacGroup.getN() * unifacGroup.getQ();
        temp += tempVar;
        if (unifacGroup.getSubGroup() == subGroup) {
          temp2 += tempVar;
        }
      }
    }
    // System.out.println("xmix " + xMix);
    QMix = temp2 / temp;
    return QMix;
  }

  /**
   * <p>
   * calcQMixdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseGEUnifac} object
   * @return an array of type double
   */
  public double[] calcQMixdN(PhaseGEUnifac phase) {
    setQMixdN(new double[phase.getNumberOfComponents()]);
    ComponentGEUnifac component;
    UNIFACgroup unifacGroup;
    // calcXMixdN(phase);
    double temp;
    double temp2;
    double tempVar;
    double tempdn;
    double temp2dn;
    double tempVardn = 0.0;
    for (int k = 0; k < phase.getNumberOfComponents(); k++) {
      temp = 0.0;
      temp2 = 0.0;
      tempdn = 0.0;
      temp2dn = 0.0;
      for (int j = 0; j < phase.getNumberOfComponents(); j++) {
        component = ((ComponentGEUnifac) phase.getComponent(j));
        for (int i = 0; i < component.getNumberOfUNIFACgroups(); i++) {
          unifacGroup = component.getUnifacGroup(i);
          tempVar = component.getNumberOfMolesInPhase() * component.getUnifacGroup(i).getN()
              * component.getUnifacGroup(i).getQ();
          temp += tempVar;
          if (k == j) {
            tempVardn = unifacGroup.getN() * unifacGroup.getQ();
            tempdn += tempVardn;
          }
          if (unifacGroup.getSubGroup() == subGroup) {
            temp2 += tempVar;
            if (k == j) {
              temp2dn += tempVardn;
            }
          }
        }
      }
      getQMixdN()[k] = (temp2dn * temp - temp2 * tempdn) / (temp * temp);
    }
    return getQMixdN();
  }

  /**
   * <p>
   * getQMixdN.
   * </p>
   *
   * @param comp a int
   * @return a double
   */
  public double getQMixdN(int comp) {
    return QMixdN[comp];
  }
  /*
   * public double getXMixdN(int comp) { return xMixdN[comp]; }
   *
   * public void setXMixdN(double[] xMixdN) { this.xMixdN = xMixdN; }
   */

  /**
   * <p>
   * Getter for the field <code>xComp</code>.
   * </p>
   *
   * @return a double
   */
  public double getXComp() {
    return xComp;
  }

  /**
   * Setter for property xComp.
   *
   * @param xComp New value of property xComp.
   */
  public void setXComp(double xComp) {
    this.xComp = xComp;
  }

  /**
   * Getter for property QComp.
   *
   * @return Value of property QComp.
   */
  public double getQComp() {
    return QComp;
  }

  /**
   * Setter for property QComp.
   *
   * @param QComp New value of property QComp.
   */
  public void setQComp(double QComp) {
    this.QComp = QComp;
  }

  /**
   * Getter for property QMix.
   *
   * @return Value of property QMix.
   */
  public double getQMix() {
    return QMix;
  }

  /**
   * Setter for property QMix.
   *
   * @param QMix New value of property QMix.
   */
  public void setQMix(double QMix) {
    this.QMix = QMix;
  }

  /**
   * Getter for property groupIndex.
   *
   * @return Value of property groupIndex.
   */
  public int getGroupIndex() {
    return groupIndex;
  }

  /**
   * Setter for property groupIndex.
   *
   * @param groupIndex New value of property groupIndex.
   */
  public void setGroupIndex(int groupIndex) {
    this.groupIndex = groupIndex;
  }

  /**
   * Getter for property lnGammaComp.
   *
   * @return Value of property lnGammaComp.
   */
  public double getLnGammaComp() {
    return lnGammaComp;
  }

  /**
   * Setter for property lnGammaComp.
   *
   * @param lnGammaComp New value of property lnGammaComp.
   */
  public void setLnGammaComp(double lnGammaComp) {
    this.lnGammaComp = lnGammaComp;
  }

  /**
   * Getter for property lnGammaMix.
   *
   * @return Value of property lnGammaMix.
   */
  public double getLnGammaMix() {
    return lnGammaMix;
  }

  /**
   * <p>
   * Getter for the field <code>lnGammaMixdn</code>.
   * </p>
   *
   * @param compNumb a int
   * @return a double
   */
  public double getLnGammaMixdn(int compNumb) {
    return lnGammaMixdn[compNumb];
  }

  /**
   * <p>
   * Setter for the field <code>lnGammaMixdn</code>.
   * </p>
   *
   * @param lnGammaMixdn1 a double
   * @param compNumb a int
   */
  public void setLnGammaMixdn(double lnGammaMixdn1, int compNumb) {
    lnGammaMixdn[compNumb] = lnGammaMixdn1;
  }

  /**
   * Setter for property lnGammaMix.
   *
   * @param lnGammaMix New value of property lnGammaMix.
   */
  public void setLnGammaMix(double lnGammaMix) {
    this.lnGammaMix = lnGammaMix;
  }

  /**
   * Getter for property lnGammaCompdT.
   *
   * @return Value of property lnGammaCompdT.
   */
  public double getLnGammaCompdT() {
    return lnGammaCompdT;
  }

  /**
   * Setter for property lnGammaCompdT.
   *
   * @param lnGammaCompdT New value of property lnGammaCompdT.
   */
  public void setLnGammaCompdT(double lnGammaCompdT) {
    this.lnGammaCompdT = lnGammaCompdT;
  }

  /**
   * Getter for property lnGammaMixdT.
   *
   * @return Value of property lnGammaMixdT.
   */
  public double getLnGammaMixdT() {
    return lnGammaMixdT;
  }

  /**
   * Setter for property lnGammaMixdT.
   *
   * @param lnGammaMixdT New value of property lnGammaMixdT.
   */
  public void setLnGammaMixdT(double lnGammaMixdT) {
    this.lnGammaMixdT = lnGammaMixdT;
  }

  /**
   * <p>
   * Getter for the field <code>lnGammaCompdTdT</code>.
   * </p>
   *
   * @return the lnGammaCompdTdT
   */
  public double getLnGammaCompdTdT() {
    return lnGammaCompdTdT;
  }

  /**
   * <p>
   * Setter for the field <code>lnGammaCompdTdT</code>.
   * </p>
   *
   * @param lnGammaCompdTdT the lnGammaCompdTdT to set
   */
  public void setLnGammaCompdTdT(double lnGammaCompdTdT) {
    this.lnGammaCompdTdT = lnGammaCompdTdT;
  }

  /**
   * <p>
   * Getter for the field <code>lnGammaMixdTdT</code>.
   * </p>
   *
   * @return the lnGammaMixdTdT
   */
  public double getLnGammaMixdTdT() {
    return lnGammaMixdTdT;
  }

  /**
   * <p>
   * Setter for the field <code>lnGammaMixdTdT</code>.
   * </p>
   *
   * @param lnGammaMixdTdT the lnGammaMixdTdT to set
   */
  public void setLnGammaMixdTdT(double lnGammaMixdTdT) {
    this.lnGammaMixdTdT = lnGammaMixdTdT;
  }
}
