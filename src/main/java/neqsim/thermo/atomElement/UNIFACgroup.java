/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Element.java
 *
 * Created on 4. februar 2001, 22:11
 */
package neqsim.thermo.atomElement;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.phase.PhaseGEUnifac;
import org.apache.logging.log4j.*;

/**
 * @author  Even Solbraa
 * @version
 */
public class UNIFACgroup implements ThermodynamicConstantsInterface, Comparable {

    /**
     * @return the QMixdN
     */
    public double[] getQMixdN() {
        return QMixdN;
    }

    /**
     * @param QMixdN the QMixdN to set
     */
    public void setQMixdN(double[] QMixdN) {
        this.QMixdN = QMixdN;
    }

    private static final long serialVersionUID = 1000;
    double R = 0.0;
    double Q = 0.0;
    int n = 0;
    double xComp = 0.0;
    double QComp = 0.0, QMix = 0.0;
    public double[] QMixdN = null;// , xMixdN = null;
    double[] lnGammaMixdn = new double[MAX_NUMBER_OF_COMPONENTS];
    double lnGammaComp = 0.0, lnGammaMix = 0.0;
    double lnGammaCompdT = 0.0, lnGammaMixdT = 0.0;
    private double lnGammaCompdTdT = 0.0;
    private double lnGammaMixdTdT = 0.0;
    int groupIndex = 0;
    String groupName = "";
    int mainGroup = 0;
    int subGroup = 0;
    static Logger logger = LogManager.getLogger(UNIFACgroup.class);

    /**
     * Creates new Element
     */
    public UNIFACgroup() {
    }

    public UNIFACgroup(int groupNumber, int temp) {

        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        try {
            java.sql.ResultSet dataSet = null;
            try {
                dataSet = database.getResultSet(("SELECT * FROM unifacgroupparam WHERE Secondary=" + groupNumber + ""));
                dataSet.next();
                dataSet.getClob("name");
            } catch (Exception e) {
                dataSet.close();
                dataSet = database.getResultSet(("SELECT * FROM unifacgroupparam WHERE Secondary=" + groupNumber + ""));
                dataSet.next();
            }
            n = temp;
            R = Double.parseDouble(dataSet.getString("VolumeR"));
            Q = Double.parseDouble(dataSet.getString("SurfAreaQ"));
            mainGroup = Integer.parseInt(dataSet.getString("Main"));
            subGroup = Integer.parseInt(dataSet.getString("Secondary"));
            groupName = dataSet.getString("Name");
            dataSet.close();
            database.getConnection().close();
        } catch (Exception e) {
            try {
                database.getConnection().close();
            } catch (Exception ex) {
                logger.error(ex);
            }
            String err = e.toString();
            logger.error(err);
            // System.out.println(err);
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

    /**
     * Compares this object with the specified object for order. Returns a negative
     * integer, zero, or a positive integer as this object is less than, equal to,
     * or greater than the specified object.
     * <p>
     *
     * In the foregoing description, the notation
     * <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
     * <i>signum</i> function, which is defined to return one of <tt>-1</tt>,
     * <tt>0</tt>, or <tt>1</tt> according to whether the value of <i>expression</i>
     * is negative, zero or positive.
     * 
     * The implementor must ensure <tt>sgn(x.compareTo(y)) ==
     * -sgn(y.compareTo(x))</tt> for all <tt>x</tt> and <tt>y</tt>. (This implies
     * that <tt>x.compareTo(y)</tt> must throw an exception iff
     * <tt>y.compareTo(x)</tt> throws an exception.)
     * <p>
     *
     * The implementor must also ensure that the relation is transitive:
     * <tt>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</tt> implies
     * <tt>x.compareTo(z)&gt;0</tt>.
     * <p>
     *
     * Finally, the implementer must ensure that <tt>x.compareTo(y)==0</tt> implies
     * that <tt>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</tt>, for all <tt>z</tt>.
     * <p>
     *
     * It is strongly recommended, but <i>not</i> strictly required that
     * <tt>(x.compareTo(y)==0) == (x.equals(y))</tt>. Generally speaking, any class
     * that implements the <tt>Comparable</tt> interface and violates this condition
     * should clearly indicate this fact. The recommended language is "Note: this
     * class has a natural ordering that is inconsistent with equals."
     *
     * @param  o                  the Object to be compared.
     * @return                    a negative integer, zero, or a positive integer as
     *                            this object is less than, equal to, or greater
     *                            than the specified object.
     * @throws ClassCastException if the specified object's type prevents it from
     *                            being compared to this Object.
     */
    @Override
    public boolean equals(Object o) {
        return ((UNIFACgroup) o).getSubGroup() == getSubGroup();
    }

    @Override
    public int compareTo(java.lang.Object o) {
        if (((UNIFACgroup) o).getSubGroup() < getSubGroup()) {
            return 1;
        } else if (((UNIFACgroup) o).getSubGroup() == getSubGroup()) {
            return 0;
        } else {
            return -1;
        }
    }

    /**
     * Getter for property xComp.
     *
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
     * public double calcXMix(PhaseGEUnifac phase) { double temp = 0.0, temp2 = 0.0,
     * tempVal = 0.0;
     * 
     * for (int j = 0; j < phase.getNumberOfComponents(); j++) { for (int i = 0; i <
     * ((ComponentGEUnifac) phase.getComponent(j)).getNumberOfUNIFACgroups(); i++) {
     * tempVal = phase.getComponent(j).getNumberOfMolesInPhase() *
     * ((ComponentGEUnifac) phase.getComponent(j)).getUnifacGroup(i).getN(); temp +=
     * tempVal; if (((ComponentGEUnifac)
     * phase.getComponent(j)).getUnifacGroup(i).getSubGroup() == subGroup) { temp2
     * += tempVal; } } } xMix = temp2 / temp; return xMix; }
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

    public double calcQMix(PhaseGEUnifac phase) {
        ComponentGEUnifac component;
        double temp = 0.0, temp2 = 0.0, tempVar, numberOfMoles;
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

    public double[] calcQMixdN(PhaseGEUnifac phase) {
        setQMixdN(new double[phase.getNumberOfComponents()]);
        ComponentGEUnifac component;
        UNIFACgroup unifacGroup;
        // calcXMixdN(phase);
        double temp, temp2, tempVar, tempdn, temp2dn, tempVardn = 0.0;
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

    public double getQMixdN(int comp) {
        return QMixdN[comp];
    }
    /*
     * public double getXMixdN(int comp) { return xMixdN[comp]; }
     * 
     * public void setXMixdN(double[] xMixdN) { this.xMixdN = xMixdN; }
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
     * Getter for property xMix.
     *
     * @return Value of property xMix.
     */
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

    public double getLnGammaMixdn(int compNumb) {
        return lnGammaMixdn[compNumb];
    }

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
     * @return the lnGammaCompdTdT
     */
    public double getLnGammaCompdTdT() {
        return lnGammaCompdTdT;
    }

    /**
     * @param lnGammaCompdTdT the lnGammaCompdTdT to set
     */
    public void setLnGammaCompdTdT(double lnGammaCompdTdT) {
        this.lnGammaCompdTdT = lnGammaCompdTdT;
    }

    /**
     * @return the lnGammaMixdTdT
     */
    public double getLnGammaMixdTdT() {
        return lnGammaMixdTdT;
    }

    /**
     * @param lnGammaMixdTdT the lnGammaMixdTdT to set
     */
    public void setLnGammaMixdTdT(double lnGammaMixdTdT) {
        this.lnGammaMixdTdT = lnGammaMixdTdT;
    }
}
