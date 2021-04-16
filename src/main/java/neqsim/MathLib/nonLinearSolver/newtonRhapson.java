/*
 * newtonRhapson.java
 *
 * Created on 15. juli 2000, 17:43
 */

package neqsim.MathLib.nonLinearSolver;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class newtonRhapson extends Object implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    int order;
    double[] polyConstants;
    double funkVal = 0, derivVal = 0, dubDerivVal = 0;
    private int maxIterations = 500, times = 0;
    double xNew = 0, xNew2, x = 0;

    /** Creates new newtonRhapson */
    public newtonRhapson() {
    }

    public void setOrder(int o) {
        order = o;
        polyConstants = new double[order + 1];
    }

    public void setConstants(double[] constants) {
        System.arraycopy(constants, 0, polyConstants, 0, constants.length);
    }

    public double funkValue(double x) {

        funkVal = 0;

        for (int i = 0; i < polyConstants.length; i++) {
            funkVal += polyConstants[i] * Math.pow(x, order - i);
        }
        return funkVal;
    }

    public double derivValue(double x) {

        derivVal = 0;

        for (int i = 0; i < polyConstants.length - 1; i++) {
            derivVal += (order - i) * polyConstants[i] * Math.pow(x, order - 1 - i);
        }

        return derivVal;
    }

    public double dubDerivValue(double x) {

        dubDerivVal = 0;

        for (int i = 0; i < polyConstants.length - 2; i++) {
            dubDerivVal += (order - 1 - i) * (order - i) * polyConstants[i] * Math.pow(x, order - 2 - i);
        }

        return dubDerivVal;
    }

    public double solve(double xin) {

        int iterations = 0;

        times++;

        x = xin;
        xNew = x;
        xNew2 = x;

        do {
            // System.out.println("x " + xNew);
            iterations++;

            // System.out.println("Z : " + x);
            if (Math.abs(funkValue(xNew)) < Math.abs(funkValue(xNew2))) {
                x = xNew;
            } else {
                x = xNew2;
            }
            if (derivValue(x) * derivValue(x) - 2.0 * funkValue(x) * dubDerivValue(x) > 0) {
                xNew = x - derivValue(x) / dubDerivValue(x)
                        + Math.sqrt(derivValue(x) * derivValue(x) - 2 * funkValue(x) * dubDerivValue(x))
                                / dubDerivValue(x);
                xNew2 = x - derivValue(x) / dubDerivValue(x)
                        - Math.sqrt(derivValue(x) * derivValue(x) - 2 * funkValue(x) * dubDerivValue(x))
                                / dubDerivValue(x);
            } else {
                // System.out.println("using first order newton-rhapson...........");
                xNew = x - funkValue(x) / derivValue(x);
                xNew2 = xNew;
            }

            if (xNew < 0) {
                xNew = 0;
                // System.out.println("x++...........");
            }
            if (xNew > 1.5) {
                xNew = 1;
                // System.out.println("x--...........");
            }
        } while (Math.abs(funkValue(x)) > 1e-10 && iterations <= maxIterations);

        if (iterations == maxIterations) {
            System.out.println("Too many iterations...");
        }
        // System.out.println("iterations in newton-rhapson = " + iterations );

        return xNew;

    }

    public double solve1order(double xin) {

        int iterations = 0;

        times++;

        x = xin;
        xNew = x;
        xNew2 = x;

        do {
            iterations++;
            x = xNew;
            xNew = x - funkValue(x) / derivValue(x);
            xNew2 = xNew;

        } while (Math.abs(funkValue(x)) > 1e-10 && iterations <= maxIterations);

        if (iterations == maxIterations) {
            System.out.println("Too many iterations...");
        }
        // System.out.println("iterations in newton-rhapson = " + iterations );

        return xNew;

    }

    public static void main(String args[]) {

        newtonRhapson test = new newtonRhapson();
        test.setOrder(3);

        double[] constants = new double[] { -0.003058, -0.01806, -0.266, -0.2999 };
        test.setConstants(constants);

        System.out.println("val : " + test.funkValue(-0.0));
        System.out.println("val : " + test.dubDerivValue(-0.3));
        System.out.println("val : " + test.derivValue(-0.3));
        // System.out.println("val : " + test.solve(-0.3));

    }
}
