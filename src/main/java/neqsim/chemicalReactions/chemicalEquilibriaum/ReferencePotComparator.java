

/*
 * ReferencePotComperator.java
 *
 * Created on 11. april 2001, 20:21
 */

package neqsim.chemicalReactions.chemicalEquilibriaum;

import java.util.Comparator;
import neqsim.thermo.component.ComponentInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ReferencePotComparator implements Comparator, java.io.Serializable {
    private static final long serialVersionUID = 1000;

    @Override
    public int compare(Object o1, Object o2) {
        double v1 = ((ComponentInterface) o1).getReferencePotential();
        double v2 = ((ComponentInterface) o2).getReferencePotential();

        int ans = v1 >= v2 ? 1 : 0;

        return ans;
    }
}
