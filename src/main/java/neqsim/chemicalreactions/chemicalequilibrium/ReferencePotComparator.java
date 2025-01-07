/*
 * ReferencePotComperator.java
 *
 * Created on 11. april 2001, 20:21
 */

package neqsim.chemicalreactions.chemicalequilibrium;

import java.util.Comparator;
import neqsim.thermo.component.ComponentInterface;

/**
 * <p>
 * ReferencePotComparator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ReferencePotComparator
    implements Comparator<ComponentInterface>, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** {@inheritDoc} */
  @Override
  public int compare(ComponentInterface o1, ComponentInterface o2) {
    double v1 = o1.getReferencePotential();
    double v2 = o2.getReferencePotential();

    int ans = v1 >= v2 ? 1 : 0;

    return ans;
  }
}
