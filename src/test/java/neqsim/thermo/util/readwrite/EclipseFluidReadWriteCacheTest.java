package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.thermo.system.SystemInterface;

/**
 * Tests for the parsed-fluid cache in {@link EclipseFluidReadWrite}.
 *
 * <p>
 * Parsing an E300 file is roughly three orders of magnitude more expensive than cloning the resulting fluid, so reads
 * of an unchanged file are served from a cache. These tests verify that every read still hands the caller an
 * independent, mutable fluid.
 * </p>
 *
 * @author ESOL
 */
class EclipseFluidReadWriteCacheTest extends neqsim.NeqSimTest {
  private final String fluidFile = new File("src/test/java/neqsim/thermo/util/readwrite").getAbsolutePath()
      + "/fluid1.e300";

  @BeforeEach
  public void setUpCache() {
    EclipseFluidReadWrite.pseudoName = "";
    EclipseFluidReadWrite.setUseCache(true);
    EclipseFluidReadWrite.clearCache();
  }

  @AfterEach
  public void resetCache() {
    EclipseFluidReadWrite.pseudoName = "";
    EclipseFluidReadWrite.setUseCache(true);
    EclipseFluidReadWrite.setMaxCacheSize(EclipseFluidReadWrite.DEFAULT_MAX_CACHE_SIZE);
    EclipseFluidReadWrite.clearCache();
  }

  @Test
  public void testCachedReadReturnsIndependentEquivalentFluid() {
    SystemInterface first = EclipseFluidReadWrite.read(fluidFile);
    SystemInterface second = EclipseFluidReadWrite.read(fluidFile);

    assertNotSame(first, second);
    assertEquals(first.getNumberOfComponents(), second.getNumberOfComponents());
    double[] firstComposition = first.getMolarComposition();
    double[] secondComposition = second.getMolarComposition();
    assertEquals(firstComposition.length, secondComposition.length);
    for (int i = 0; i < firstComposition.length; i++) {
      assertEquals(firstComposition[i], secondComposition[i], 1e-12);
    }

    // Mutating a returned fluid must not corrupt the cached template.
    second.setTemperature(350.0);
    second.setPressure(123.0);
    SystemInterface third = EclipseFluidReadWrite.read(fluidFile);
    assertEquals(first.getTemperature(), third.getTemperature(), 1e-9);
    assertEquals(first.getPressure(), third.getPressure(), 1e-9);
  }

  @Test
  public void testCacheCanBeDisabled() {
    EclipseFluidReadWrite.setUseCache(false);
    SystemInterface uncached = EclipseFluidReadWrite.read(fluidFile);
    assertTrue(uncached.getNumberOfComponents() > 0);

    EclipseFluidReadWrite.setUseCache(true);
    SystemInterface cached = EclipseFluidReadWrite.read(fluidFile);
    assertEquals(uncached.getNumberOfComponents(), cached.getNumberOfComponents());
    assertNotSame(uncached, cached);
  }

  @Test
  public void testCacheIsBoundedAndStillCorrectAfterEviction() {
    assertEquals(EclipseFluidReadWrite.DEFAULT_MAX_CACHE_SIZE, EclipseFluidReadWrite.getMaxCacheSize());

    SystemInterface reference = EclipseFluidReadWrite.read(fluidFile);

    // Shrink the cache to a single entry, then push the entry out by reading the same file under a
    // different pseudo-name prefix (which is part of the cache key). The next read of the original
    // key must re-parse and still produce the same fluid.
    EclipseFluidReadWrite.setMaxCacheSize(1);
    EclipseFluidReadWrite.pseudoName = "PSEUDO_";
    EclipseFluidReadWrite.read(fluidFile);
    EclipseFluidReadWrite.pseudoName = "";

    SystemInterface afterEviction = EclipseFluidReadWrite.read(fluidFile);
    assertEquals(reference.getNumberOfComponents(), afterEviction.getNumberOfComponents());
    double[] referenceComposition = reference.getMolarComposition();
    double[] evictedComposition = afterEviction.getMolarComposition();
    for (int i = 0; i < referenceComposition.length; i++) {
      assertEquals(referenceComposition[i], evictedComposition[i], 1e-12);
    }

    EclipseFluidReadWrite.setMaxCacheSize(EclipseFluidReadWrite.DEFAULT_MAX_CACHE_SIZE);
  }

  @Test
  public void testMaxCacheSizeMustBePositive() {
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        EclipseFluidReadWrite.setMaxCacheSize(0);
      }
    });
  }
}
