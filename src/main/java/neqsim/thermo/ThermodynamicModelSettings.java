/*
 * ThermodynamicConstantsInterface.java
 *
 * Created on 3. juni 2000, 19:07
 */

package neqsim.thermo;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public interface ThermodynamicModelSettings extends java.io.Serializable {
    double phaseFractionMinimumLimit = 1e-12;
    static final int MAX_NUMBER_OF_COMPONENTS = 100;
}