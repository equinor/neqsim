/*
 * DoubleCloneable.java
 *
 * Created on 3. juni 2001, 20:19
 */

package neqsim.util.util;

/**
 * @author esol
 * @version
 */
public class DoubleCloneable implements Cloneable {
    private static final long serialVersionUID = 1000;

    double doubleValue;

    /** Creates new DoubleCloneable */
    public DoubleCloneable() {}

    public DoubleCloneable(double val) {
        this.doubleValue = val;
    }

    @Override
    public DoubleCloneable clone() {
        DoubleCloneable clonedSystem = null;
        try {
            clonedSystem = (DoubleCloneable) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    public void set(double val) {
        doubleValue = val;
    }

    public double doubleValue() {
        return doubleValue;
    }
}
