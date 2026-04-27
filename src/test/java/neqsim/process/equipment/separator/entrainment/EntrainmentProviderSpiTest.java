package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;

/**
 * Contract tests for the entrainment-provider SPI.
 *
 * <p>
 * These tests verify the public-side wiring only — that the built-in
 * 7-stage provider is discovered through {@link java.util.ServiceLoader},
 * that lookup of an unknown id throws a clear error, and that
 * {@link Separator#setEntrainmentProvider(String)} stores the resolved
 * provider. They do not exercise any private plug-in (which lives in a
 * separate, internal repo).
 *
 * @author NeqSim
 * @version 1.0
 */
public class EntrainmentProviderSpiTest {

  /**
   * The built-in provider must be registered via {@code META-INF/services}
   * and discoverable by id.
   */
  @Test
  public void builtInProviderIsRegistered() {
    EnhancedEntrainmentProvider p =
        EntrainmentProviderRegistry.find(BuiltInSevenStageProvider.ID);
    assertNotNull(p, "built-in provider must be registered");
    assertEquals(BuiltInSevenStageProvider.ID, p.getId());
    assertEquals(BuiltInSevenStageProvider.VERSION, p.getVersion());
  }

  /**
   * Looking up an unknown id must fail loudly with an
   * {@link IllegalStateException}, never silently fall back.
   */
  @Test
  public void unknownProviderThrowsClearError() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        new org.junit.jupiter.api.function.Executable() {
          @Override
          public void execute() {
            EntrainmentProviderRegistry.find("definitely-not-registered-v9");
          }
        });
    assertTrue(ex.getMessage().contains("definitely-not-registered-v9"),
        "error must echo the requested id");
    assertTrue(ex.getMessage().contains(BuiltInSevenStageProvider.ID),
        "error must list available providers including the built-in");
  }

  /**
   * Null id must be rejected with {@link IllegalArgumentException}.
   */
  @Test
  public void nullIdRejected() {
    assertThrows(IllegalArgumentException.class,
        new org.junit.jupiter.api.function.Executable() {
          @Override
          public void execute() {
            EntrainmentProviderRegistry.find(null);
          }
        });
  }

  /**
   * Setting and getting a provider on a {@link Separator} round-trips
   * through the registry and stores the resolved instance.
   */
  @Test
  public void separatorRoundTripsProviderSelection() {
    Separator sep = new Separator("test-sep");
    assertNull(sep.getEntrainmentProvider(),
        "no provider selected by default");

    sep.setEntrainmentProvider(BuiltInSevenStageProvider.ID);
    EnhancedEntrainmentProvider active = sep.getEntrainmentProvider();
    assertNotNull(active);
    assertEquals(BuiltInSevenStageProvider.ID, active.getId());

    // Same instance returned by registry (ServiceLoader caches per-loader).
    EnhancedEntrainmentProvider direct =
        EntrainmentProviderRegistry.find(BuiltInSevenStageProvider.ID);
    assertSame(active, direct, "registry must return the cached instance");

    sep.setEntrainmentProvider(null);
    assertNull(sep.getEntrainmentProvider(), "null clears the selection");
  }

  /**
   * The built-in provider always reports applicability OK (it is a
   * predictive model, not an empirical regression with an envelope).
   */
  @Test
  public void builtInProviderIsAlwaysApplicable() {
    Separator sep = new Separator("test-sep");
    EnhancedEntrainmentProvider p =
        EntrainmentProviderRegistry.find(BuiltInSevenStageProvider.ID);
    EntrainmentApplicability a = p.checkApplicability(sep);
    assertNotNull(a);
    assertTrue(a.isApplicable());
    assertEquals(0, a.getDiagnostics().size());
  }

  /**
   * The {@link EntrainmentResult} contract — provider id and version are
   * stamped, numerical fields default to NaN until the calculator wiring
   * is finalised.
   */
  @Test
  public void builtInComputeReturnsTaggedResult() {
    Separator sep = new Separator("test-sep");
    EnhancedEntrainmentProvider p =
        EntrainmentProviderRegistry.find(BuiltInSevenStageProvider.ID);
    EntrainmentResult r = p.compute(sep);
    assertNotNull(r);
    assertEquals(BuiltInSevenStageProvider.ID, r.getProviderId());
    assertEquals(BuiltInSevenStageProvider.VERSION, r.getProviderVersion());
  }
}
