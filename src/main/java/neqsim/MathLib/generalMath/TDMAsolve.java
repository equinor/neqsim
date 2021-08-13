/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * TDMAsolve.java
 *
 * Created on 4. desember 2000, 22:34
 */

package neqsim.MathLib.generalMath;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TDMAsolve implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    /** Creates new TDMAsolve */
    public TDMAsolve() {}

    public static double[] solve(double a[], double b[], double c[], double r[]) {

        int length = a.length;
        double[] u = new double[length];
        double bet = 0;
        double gam[] = new double[length];

        bet = b[0];
        u[0] = r[0] / bet;

        for (int j = 1; j < length; j++) {
            gam[j] = c[j - 1] / bet;
            bet = b[j] - a[j] * gam[j];
            u[j] = (r[j] - a[j] * u[j - 1]) / bet;
        }

        for (int j = (length - 2); j >= 0; j--) {
            u[j] -= gam[j + 1] * u[j + 1];
        }
        return u;
    }

}
