package com.equinor.neqsim.eqn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.entrainment.EnhancedEntrainmentProvider;

/**
 * Smoke tests for the EQN π-number plug-in.
 *
 * <p>
 * Verifies that:
 * <ul>
 *   <li>the plug-in is discovered through {@link ServiceLoader}</li>
 *   <li>the bundled coefficient resource loads without error</li>
 *   <li>id and version match the public contract</li>
 * </ul>
 *
 * Numerical correctness of the π-number regression is verified in
 * separate fixture tests against a synthetic dataset (not included
 * here — those tests live in this private repo only).
 *
 * @author Equinor NeqSim team
 * @version 1.0.0
 */
public class EqnPiNumberProviderTest {

  /**
   * The plug-in must be discoverable via {@link ServiceLoader} when this
   * JAR is on the classpath.
   */
  @Test
  public void pluginIsDiscovered() {
    ServiceLoader<EnhancedEntrainmentProvider> loader =
        ServiceLoader.load(EnhancedEntrainmentProvider.class);
    boolean found = false;
    for (Iterator<EnhancedEntrainmentProvider> it = loader.iterator(); it.hasNext();) {
      EnhancedEntrainmentProvider p = it.next();
      if (EqnPiNumberProvider.ID.equals(p.getId())) {
        found = true;
        assertEquals(EqnPiNumberProvider.VERSION, p.getVersion());
        break;
      }
    }
    assertTrue(found, "EQN plug-in must be registered via META-INF/services");
  }

  /**
   * The constructor must successfully parse the bundled coefficient
   * resource.
   */
  @Test
  public void coefficientsLoad() {
    EqnPiNumberProvider p = new EqnPiNumberProvider();
    assertNotNull(p);
    assertEquals(EqnPiNumberProvider.ID, p.getId());
    assertEquals(EqnPiNumberProvider.VERSION, p.getVersion());
  }
}
