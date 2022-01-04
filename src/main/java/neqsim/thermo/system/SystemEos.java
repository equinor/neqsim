/*
 * System_EOS.java
 *
 * Created on 8. april 2000, 22:55
 */

package neqsim.thermo.system;

/**
 *
 * @author Even Solbraa
 * @version
 */
abstract class SystemEos extends neqsim.thermo.system.SystemThermo {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for SystemEos.</p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemEos(double T, double P) {
        super(T, P);
    }

    /**
     * <p>
     * Constructor for SystemEos.
     * </p>
     */
    public SystemEos() {
        super();
    }

}
