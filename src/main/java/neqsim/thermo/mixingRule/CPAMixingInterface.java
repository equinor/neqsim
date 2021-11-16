/*
 * CPAMixingInterface.java
 *
 * Created on 26. februar 2001, 19:38
 */
package neqsim.thermo.mixingRule;

import neqsim.thermo.phase.PhaseInterface;

/**
 * @author Even Solbraa
 * @version
 */
public interface CPAMixingInterface extends java.io.Serializable {
        // public double calcXi(int siteNumber, int compnumb, PhaseInterface phase,
        // double temperature, double pressure, int numbcomp);

        public double calcDeltadT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
                        PhaseInterface phase, double temperature, double pressure, int numbcomp);

        public double calcDeltadTdV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
                        PhaseInterface phase, double temperature, double pressure, int numbcomp);

        public double calcDeltadTdT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
                        PhaseInterface phase, double temperature, double pressure, int numbcomp);

        public double calcXi(int[][][] assosScheme, int[][][][] assosScheme2, int siteNumber,
                        int compnumb, PhaseInterface phase, double temperature, double pressure,
                        int numbcomp);

        public double calcDelta(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
                        PhaseInterface phase, double temperature, double pressure, int numbcomp);

        public double calcDeltadN(int derivativeComp, int siteNumber1, int siteNumber2,
                        int compnumb1, int compnumb2, PhaseInterface phase, double temperature,
                        double pressure, int numbcomp);

        public double calcDeltadV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
                        PhaseInterface phase, double temperature, double pressure, int numbcomp);

        public double calcDeltaNog(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
                        PhaseInterface phase, double temperature, double pressure, int numbcomp);
}
