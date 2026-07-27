package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Locks in identity equality for {@link ProcessSystem} and process equipment.
 *
 * <p>
 * These types used to implement value-based {@code equals}/{@code hashCode} over state that {@code run()} rewrites, so
 * their hash changed while the model solved. That silently broke every {@code HashMap}/{@code HashSet} keyed on them.
 * They now inherit identity semantics from {@link Object}, which makes the hash stable for the lifetime of the object.
 * Value comparison of two flowsheets is done with
 * {@link neqsim.process.processmodel.lifecycle.ProcessModelState#compare} instead.
 * </p>
 */
public class ProcessEqualityIdentityTest extends neqsim.NeqSimTest {

  private static SystemInterface makeGas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.02);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1000.0, "kg/hr");
    return fluid;
  }

  private static ProcessSystem buildProcess(String name) {
    Stream feed = new Stream("feed", makeGas());
    Separator sep = new Separator("sep", feed);
    Compressor comp = new Compressor("comp", sep.getGasOutStream());
    comp.setOutletPressure(80.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.setName(name);
    process.add(feed);
    process.add(sep);
    process.add(comp);
    return process;
  }

  /**
   * Two independently built but identically configured process systems are distinct objects and must not compare equal.
   */
  @Test
  public void identicallyConfiguredProcessSystemsAreNotEqual() {
    ProcessSystem a = buildProcess("plant");
    ProcessSystem b = buildProcess("plant");

    assertNotEquals(a, b, "two distinct ProcessSystem instances must not compare equal");
    assertEquals(a, a, "a ProcessSystem must equal itself");
  }

  /**
   * The same must hold for equipment: two identically configured units are different objects.
   */
  @Test
  public void identicallyConfiguredEquipmentIsNotEqual() {
    Separator sepA = new Separator("sep", new Stream("feed", makeGas()));
    Separator sepB = new Separator("sep", new Stream("feed", makeGas()));
    Compressor compA = new Compressor("comp", new Stream("feed", makeGas()));
    Compressor compB = new Compressor("comp", new Stream("feed", makeGas()));
    Mixer mixA = new Mixer("mix");
    Mixer mixB = new Mixer("mix");

    assertNotEquals(sepA, sepB, "distinct separators must not compare equal");
    assertNotEquals(compA, compB, "distinct compressors must not compare equal");
    assertNotEquals(mixA, mixB, "distinct mixers must not compare equal");
    assertEquals(sepA, sepA, "a separator must equal itself");
  }

  /**
   * The core guarantee: the hash of a process system and of its equipment must not change when the model is solved, so
   * hash-based collections keyed on them keep working.
   */
  @Test
  public void hashCodeIsStableAcrossRun() {
    ProcessSystem process = buildProcess("plant");
    Separator sep = (Separator) process.getUnit("sep");
    Compressor comp = (Compressor) process.getUnit("comp");

    int processHashBefore = process.hashCode();
    int sepHashBefore = sep.hashCode();
    int compHashBefore = comp.hashCode();

    process.run();

    assertEquals(processHashBefore, process.hashCode(), "ProcessSystem hash must survive run()");
    assertEquals(sepHashBefore, sep.hashCode(), "Separator hash must survive run()");
    assertEquals(compHashBefore, comp.hashCode(), "Compressor hash must survive run()");
  }

  /**
   * The practical consequence: a plain {@code HashMap}/{@code HashSet} keyed on a process system or a unit still
   * resolves after the model has been solved.
   */
  @Test
  public void hashCollectionsKeyedOnProcessObjectsSurviveRun() {
    ProcessSystem process = buildProcess("plant");
    Separator sep = (Separator) process.getUnit("sep");

    Map<ProcessSystem, String> registry = new HashMap<ProcessSystem, String>();
    Map<Separator, String> unitCache = new HashMap<Separator, String>();
    Set<Object> seen = new HashSet<Object>();
    registry.put(process, "registered");
    unitCache.put(sep, "cached");
    seen.add(process);
    seen.add(sep);

    process.run();

    assertEquals("registered", registry.get(process), "HashMap keyed on a ProcessSystem must still resolve");
    assertEquals("cached", unitCache.get(sep), "HashMap keyed on equipment must still resolve");
    assertTrue(seen.contains(process), "HashSet must still contain the process after run()");
    assertTrue(seen.contains(sep), "HashSet must still contain the unit after run()");
    assertEquals(1, registry.size(), "no duplicate entry may be created for the same instance");
  }
}
