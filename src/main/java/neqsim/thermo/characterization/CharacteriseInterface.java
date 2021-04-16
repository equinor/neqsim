/*
 * Characterize.java
 *
 * Created on 17. juli 2003, 12:04
 */
package neqsim.thermo.characterization;

/**
 *
 * @author ESOL
 */
public interface CharacteriseInterface {

    public double[] PVTsimMolarMass = { 80.0, 96.0, 107, 121, 134, 147, 161, 175, 190, 206, 222, 237, 251, 263, 275,
            291, 305, 318, 331, 345, 359, 374, 388, 402, 416, 430, 444, 458, 472, 486, 500, 514, 528, 542, 556, 570,
            584, 598, 612, 626, 640, 654, 668, 682, 696, 710, 724, 738, 752, 766, 780, 794, 808, 822, 836, 850, 864,
            878, 892, 906, 920, 934, 948, 962, 976, 990, 1004, 1018, 1032, 1046, 1060, 1074, 1088, 1102, 1116 };

    public void solve();

    public void generatePlusFractions(int start, int end, double zplus, double Mplus);

    public void generateTBPFractions();

    public boolean groupTBPfractions();

    public boolean hasPlusFraction();

    public boolean isPseudocomponents();

    public void setPseudocomponents(boolean pseudocomponents);

    public void setNumberOfPseudocomponents(int numberOfPseudocomponents);

    public void addCharacterizedPlusFraction();

    public void removeTBPfraction();

    public void addHeavyEnd();

    public void addTBPFractions();

    public double[] getCoefs();

    public double getCoef(int i);

    public int getFirstPlusFractionNumber();

    public int getLastPlusFractionNumber();

    public void setZPlus(double zPlus);

    public double[] getPlusCoefs();

    public double getPlusCoefs(int i);

    public void setPlusCoefs(double[] plusCoefs);

    public double getDensPlus();

    public double getZPlus();

    public double getMPlus();

    public void setMPlus(double MPlus);

    public double getDensLastTBP();

    public void setCoefs(double[] coefs);

    public void setCoefs(double coef, int i);

    public void setDensLastTBP(double densLastTBP);
}
