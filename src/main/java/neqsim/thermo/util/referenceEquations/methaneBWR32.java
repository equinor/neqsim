/*
 * Copyright 2019 ESOL.
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
package neqsim.thermo.util.referenceEquations;

/**
 *
 * @author ESOL
 */
public class methaneBWR32 {
	private static final long serialVersionUID = 1000;
    double gamma = 0.0096;
    double[] N = new double[]{-1.84E-02,
        1.051016206,
        1.61E+01,
        8.48E+02,
        -4.27E+04,
        7.66E-04,
        -4.84E-01,
        8.52E+01,
        -1.66E+04,
        -3.75E-05,
        2.86E-02,
        -2.868528597,
        1.19E-04,
        -8.53E-03,
        3.836506384,
        2.50E-05,
        5.80E-06,
        -7.16E-03,
        1.26E-04,
        2.22E+04,
        -1.48E+06,
        5.05E+01,
        1.64E+06,
        2.13E-01,
        3.78E+01,
        -1.19E-05,
        -3.16E+01,
        -4.10E-06,
        1.49E-03,
        3.15E-09,
        -2.17E-06,
        2.40E-05};

    public double calcPressure(double molDens, double temp) {
        double pres = 0.0;
        double R = 0.08205616;
        pres = molDens * R * temp
                + molDens * molDens * (N[0] * temp + N[1] * Math.sqrt(temp) + N[2] + N[3] / temp + N[4] / temp / temp)
                + Math.pow(molDens, 3.0) * (N[5] * temp + N[6] + N[7] / temp + N[8] / temp / temp)
                + Math.pow(molDens, 4.0) * (N[9] * temp + N[10] + N[11] / temp) + Math.pow(molDens, 5.0) * N[12]
                + Math.pow(molDens, 6.0) * (N[13] / temp + N[14] / temp / temp) + Math.pow(molDens, 7.0) * N[15] / temp
                + Math.pow(molDens, 8.0) * (N[16] / temp + N[17] / temp / temp) + Math.pow(molDens, 9.0) * N[18] / temp / temp
                + Math.pow(molDens, 3.0) * (N[19] / temp / temp + N[20] / Math.pow(temp, 3.0)) * Math.exp(-gamma * Math.pow(molDens, 2.0))
                + Math.pow(molDens, 5.0) * (N[21] / temp / temp + N[22] / Math.pow(temp, 4.0)) * Math.exp(-gamma * Math.pow(molDens, 2.0))
                + Math.pow(molDens, 7.0) * (N[23] / temp / temp + N[24] / Math.pow(temp, 3.0)) * Math.exp(-gamma * Math.pow(molDens, 2.0))
                + Math.pow(molDens, 9.0) * (N[25] / temp / temp + N[26] / Math.pow(temp, 4.0)) * Math.exp(-gamma * Math.pow(molDens, 2.0))
                + Math.pow(molDens, 11.0) * (N[27] / temp / temp + N[28] / Math.pow(temp, 3.0)) * Math.exp(-gamma * Math.pow(molDens, 2.0))
                + Math.pow(molDens, 13.0) * (N[29] / temp / temp + N[30] / Math.pow(temp, 3.0) + N[31] / Math.pow(temp, 4.0)) * Math.exp(-gamma * Math.pow(molDens, 2.0));

        return pres + 0.01325;
    }

    public double molDens(double temp, double pres, boolean gas) {
        double guessMolDens = 0.04;
        if(!gas) guessMolDens = 1.0;
        double error = 0.0, oldError = 1.0;
        double molVol = 1.0, oldMolVol = 1.0, derrordmolvol = 0.0;
        int iter = 0;
        do {
            iter++;
            if (iter < 4) {
                guessMolDens -= error / 1000.0;
            } else {
                guessMolDens -= error / derrordmolvol;
            }

            molVol = guessMolDens;
            error = calcPressure(guessMolDens, temp) - pres;
            derrordmolvol = (error - oldError) / (molVol - oldMolVol);

            System.out.println("error " + error);
            oldError = error;
            oldMolVol = molVol;
        } while (Math.abs(error) > 1e-10);
        return guessMolDens;
    }

    public static void main(String args[]) {
        methaneBWR32 testMet = new methaneBWR32();
        double pres = testMet.calcPressure(0.04, 298.0);
        // System.out.println("pressure " + pres);
        System.out.println("molDens " + testMet.molDens(90.15, 1.0, false));
        //for(int i = 0;i<300;i++){
        //      pres = testMet.calcPressure(0.01+i/100.0, 100.0);
        // System.out.println("pressure " + pres);

    }

}
