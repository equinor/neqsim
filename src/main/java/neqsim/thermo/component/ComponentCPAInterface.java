/*
 * ComponentEosInterface.java
 *
 * Created on 4. juni 2000, 13:35
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public interface ComponentCPAInterface extends ComponentEosInterface {

    public double[] getXsite();


    public double[] getXsiteOld();

    public double[] getXsitedT();
     public double[] getXsitedTdT();
   public void setXsitedTdT(int i, double xsitedTdT);
    public void setXsitedT(int i, double xsitedT);

    public double dFCPAdXi(int site, PhaseInterface phase);

    public double[] getXsitedV();

    public double dFCPAdXidXj(int sitei, int sitej, int compj, PhaseInterface phase);

    public void setXsite(int i, double xsite);

    public void setXsiteOld(int i, double xsite);

    public void setXsitedV(int i, double xsite);

    public double dFCPAdNdXi(int site, PhaseInterface phase);

    public double dFCPAdVdXi(int site, PhaseInterface phase);
 public void setXsitedni(int xnumb, int compnumb, double val);
}
