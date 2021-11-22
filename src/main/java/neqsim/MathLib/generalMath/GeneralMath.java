package neqsim.MathLib.generalMath;

/**
 * @author Even Solbraa
 * @version
 */
public class GeneralMath implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    /** Creates new newtonRhapson */
    public GeneralMath() {}

    public static double log10(double var) {
        return Math.log(var) / Math.log(10);
    }
}
