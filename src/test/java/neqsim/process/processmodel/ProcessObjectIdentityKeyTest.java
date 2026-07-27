package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Guards the identity-key contract for process objects.
 *
 * <p>
 * Registries, caches and graph-traversal sets keyed on a process, a unit operation or a stream must keep working across
 * a {@code run()}. {@link IdentityHashMap} is used for those internally because it is insulated from any equality
 * semantics the process classes may adopt. These tests lock in that the identity-keyed pattern survives a run, and that
 * the section traversal helpers - which use identity-based visited sets - keep working.
 * </p>
 *
 * <p>
 * The complementary {@link ProcessEqualityIdentityTest} covers the equality contract of the types themselves.
 * </p>
 */
public class ProcessObjectIdentityKeyTest extends neqsim.NeqSimTest {

  private static SystemInterface makeGas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1000.0, "kg/hr");
    return fluid;
  }

  private static ProcessSystem buildProcess() {
    Stream feed = new Stream("feed", makeGas());
    Heater cooler = new Heater("cooler", feed);
    cooler.setOutTemperature(280.0);
    Separator sep = new Separator("sep", cooler.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.setName("identity key area");
    process.add(feed);
    process.add(cooler);
    process.add(sep);
    return process;
  }

  /**
   * An {@link IdentityHashMap} keyed on a {@link ProcessSystem} still resolves after the model has run, even though the
   * value-based hash of the process has changed in the meantime.
   */
  @Test
  public void identityMapKeyedOnProcessSurvivesRun() {
    ProcessSystem process = buildProcess();

    Map<ProcessSystem, String> registry = new IdentityHashMap<ProcessSystem, String>();
    registry.put(process, "registered");

    process.run();

    assertEquals("registered", registry.get(process),
        "IdentityHashMap keyed on a ProcessSystem must still resolve after run()");
    assertEquals(1, registry.size(), "no duplicate entry may be created for the same process instance");
  }

  /**
   * The same guarantee for unit operations and streams, which back the equipment caches and graph traversals.
   */
  @Test
  public void identityMapKeyedOnEquipmentAndStreamSurvivesRun() {
    ProcessSystem process = buildProcess();
    ProcessEquipmentInterface cooler = process.getUnit("cooler");
    StreamInterface feed = (StreamInterface) process.getUnit("feed");
    assertNotNull(cooler);
    assertNotNull(feed);

    Map<ProcessEquipmentInterface, String> unitCache = new IdentityHashMap<ProcessEquipmentInterface, String>();
    Map<StreamInterface, String> streamCache = new IdentityHashMap<StreamInterface, String>();
    Set<ProcessEquipmentInterface> visited = Collections
        .newSetFromMap(new IdentityHashMap<ProcessEquipmentInterface, Boolean>());
    unitCache.put(cooler, "cached role");
    streamCache.put(feed, "cached phase");
    visited.add(cooler);

    process.run();

    assertEquals("cached role", unitCache.get(cooler), "equipment cache must still resolve after run()");
    assertEquals("cached phase", streamCache.get(feed), "stream cache must still resolve after run()");
    assertEquals(true, visited.contains(cooler), "identity traversal set must still contain the unit after run()");
    assertSame(cooler, process.getUnit("cooler"), "getUnit must keep returning the same instance");
  }

  /**
   * The section traversal helpers use identity-based visited sets. A deactivate/activate round trip must lock and
   * unlock the same number of units, before and after a run.
   */
  @Test
  public void sectionTraversalRoundTripIsStableAcrossRun() {
    ProcessSystem process = buildProcess();
    process.run();

    int locked = process.deactivateSection("cooler");
    assertEquals(2, locked, "cooler and the downstream separator must be locked");
    assertEquals(true, process.getUnit("cooler").isLockedInactive());
    assertEquals(true, process.getUnit("sep").isLockedInactive());
    assertEquals(false, process.getUnit("feed").isLockedInactive(), "upstream feed must stay active");

    int unlocked = process.activateSection("cooler");
    assertEquals(locked, unlocked, "activateSection must reach exactly the units deactivateSection locked");
    assertEquals(false, process.getUnit("cooler").isLockedInactive());
    assertEquals(false, process.getUnit("sep").isLockedInactive());
  }
}
