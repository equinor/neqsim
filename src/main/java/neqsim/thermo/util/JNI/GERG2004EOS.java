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
 * GERG2004EOS.java
 *
 * Created on 19. september 2006, 12:18
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package neqsim.thermo.util.JNI;

/**
 *
 * @author ESOL
 */
public class GERG2004EOS {

    private static final long serialVersionUID = 1000;

    public static native double POTDX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20);

    public static native double ZOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double HOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double SOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double CPOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double WOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double RJTOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double GOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double UOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double AOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double[] SFUGOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double[] SPHIOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double CVOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    public static native double[] SALLOTPX(double c1, double c2, double c3, double c4, double c5, double c6, double c7,
            double c8, double c9, double c10, double c11, double c12, double c13, double c14, double c15, double c16,
            double c17, double c18, double c19, double c20, int IPHASE);

    /** Creates a new instance of GERG2004EOS */

    public String[] nameList = { "methane", "nitrogen", "CO2", "ethane", "propane", "n-butane", "i-butane", "n-pentane",
            "i-pentane", "n-hexane", "n-heptane", "n-octane", "hydrogen", "oxygen", "CO", "water", "helium", "argon" };

    public GERG2004EOS() {
        System.loadLibrary("test2");
    }

    public static void main(String args[]) {
        GERG2004EOS gergEOS = new GERG2004EOS();
        double c1 = 298.0, c2 = 0.1, c3 = 0.90, c4 = 0.1, c5 = 0, c6 = 0, c7 = 0, c8 = 0, c9 = 0, c10 = 0, c11 = 0,
                c12 = 0, c13 = 0, c14 = 0, c15 = 0, c16 = 0, c17 = 0, c18 = 0, c19 = 0, c20 = 0;
        // double a2 = gergEOS.POTDX (c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12,
        // c13, c14, c15, c16, c17, c18, c19, c20);
        double a4 = GERG2004EOS.SALLOTPX(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17,
                c18, c19, c20, 2)[3];
        double a2 = GERG2004EOS.ZOTPX(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18,
                c19, c20, 2);
        double[] a3 = new double[18];
        // a3 = gergEOS.SPHIOTPX(c1, c2,
        // c3,c4,c5,c6,c7,c8,c9,c10,c11,c12,c13,c14,c15,c16,c17,c18,c19,c20,-2);
        // System.out.println("potdx " + a);

    }

    public String[] getNameList() {
        return nameList;
    }
}
