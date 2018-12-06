/*
 * Unit.java
 *
 * Created on 25. januar 2002, 20:20
 */

package neqsim.util.unit;

/**
 *
 * @author  esol
 * @version 
 */
public interface Unit {
    double getSIvalue();
    double getValue(String tounit);
    double getValue(double val, String fromunit, String tounit);
}

