/*
 * ThermodynamicConstantsInterface.java
 *
 * Created on 3. juni 2000, 19:07
 */

package neqsim.thermo;

/**
 * <p>ThermodynamicModelSettings interface.</p>
 *
 * @author Even Solbraa
 */
public interface ThermodynamicModelSettings extends java.io.Serializable {
    /** Constant <code>phaseFractionMinimumLimit=1e-12</code> */
    double phaseFractionMinimumLimit = 1e-12;
    /** Constant <code>MAX_NUMBER_OF_COMPONENTS=100</code> */
    static final int MAX_NUMBER_OF_COMPONENTS = 100;
}
