package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

/**
 * Verifies that component clones preserve independently owned fugacity derivative caches.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ComponentDerivativeCacheCloneTest {
  /** Keeps every cached entry and prevents either clone from overwriting the other's derivatives. */
  @Test
  void clonePreservesFullDerivativeCachesWithoutSharingStorage() {
    ComponentSrk source = new ComponentSrk("methane", 1.0, 1.0, 0);
    for (int component = 0; component < source.dfugdn.length; component++) {
      source.dfugdn[component] = component + 0.25;
      source.dfugdx[component] = -component - 0.75;
    }
    ComponentSrk copy = source.clone();
    assertNotSame(source.dfugdn, copy.dfugdn);
    assertNotSame(source.dfugdx, copy.dfugdx);
    assertArrayEquals(source.dfugdn, copy.dfugdn, 0.0);
    assertArrayEquals(source.dfugdx, copy.dfugdx, 0.0);
    copy.dfugdn[0] = -10.0;
    copy.dfugdx[copy.dfugdx.length - 1] = 10.0;
    assertEquals(0.25, source.dfugdn[0], 0.0);
    assertEquals(-source.dfugdx.length + 0.25, source.dfugdx[source.dfugdx.length - 1], 0.0);
    source.dfugdn[source.dfugdn.length - 1] = -20.0;
    source.dfugdx[0] = 20.0;
    assertEquals(copy.dfugdn.length - 0.75, copy.dfugdn[copy.dfugdn.length - 1], 0.0);
    assertEquals(-0.75, copy.dfugdx[0], 0.0);
  }
}
