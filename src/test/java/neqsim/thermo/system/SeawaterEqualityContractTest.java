package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Equality and hashed-collection regressions for bulk seawater systems.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class SeawaterEqualityContractTest {
  /** Equal deep copies remain valid lookup keys without using their phases' identity hashes. */
  @Test
  void equalDeepCopiesHaveEqualHashesAndFindCollectionEntries() {
    SystemSeawater original = new SystemSeawater(298.15, 3.0, 35.0);
    original.setTotalFlowRate(2.0, "kg/sec");
    new ThermodynamicOperations(original).TPflash();
    original.initProperties();
    SystemSeawater copy = original.clone();
    assertEquals(original, copy);
    assertEquals(original.hashCode(), copy.hashCode());
    Set<SystemSeawater> systems = new HashSet<SystemSeawater>();
    systems.add(original);
    assertTrue(systems.contains(copy));
    Map<SystemSeawater, String> labels = new HashMap<SystemSeawater, String>();
    labels.put(original, "utility");
    assertEquals("utility", labels.get(copy));
    copy.setTemperature(308.15);
    assertFalse(original.equals(copy));
    assertFalse(copy.equals(original));
  }
}
