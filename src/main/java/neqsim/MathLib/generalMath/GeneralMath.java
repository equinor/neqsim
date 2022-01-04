package neqsim.MathLib.generalMath;

/**
 * <p>
 * GeneralMath class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GeneralMath implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for GeneralMath.
     * </p>
     */
    public GeneralMath() {}

    /**
     * <p>
     * log10.
     * </p>
     *
     * @param var a double
     * @return a double
     */
    public static double log10(double var) {
        return Math.log(var) / Math.log(10);
    }
}
