/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.characterization;

/**
 *
 * @author ESOL
 */
public interface PlusFractionModelInterface {

    public boolean hasPlusFraction();

    public void characterizePlusFraction(TBPModelInterface model);

    public int getFirstTBPFractionNumber();

    public int getFirstPlusFractionNumber();

    public double getMaxPlusMolarMass();

    public String getName();

    public int getLastPlusFractionNumber();

    public int getPlusComponentNumber();

    public double getNumberOfPlusPseudocomponents();

    public double getMPlus();

    public double getZPlus();

    public double getDensPlus();

    public double[] getZ();

    public double[] getM();

    public double[] getDens();
}
