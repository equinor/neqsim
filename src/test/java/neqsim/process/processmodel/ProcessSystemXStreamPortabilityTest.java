package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;

import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Guards XStream portability of a saved {@link ProcessSystem}.
 *
 * <p>
 * XStream ships no converter for a few JDK collection implementations and falls back to reflection over
 * {@code java.util}. Embedded hosts such as neqsim-python run without {@code --add-opens
 * java.base/java.util=ALL-UNNAMED}, so any such instance reachable through a non-transient field makes
 * {@code save_neqsim} fail with "No converter available" and writes a truncated file. The Maven Surefire configuration
 * does add those opens, which is exactly why a plain round-trip test cannot catch this — hence the reachability check
 * below.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class ProcessSystemXStreamPortabilityTest extends neqsim.NeqSimTest {

  /** JDK types XStream can only serialize by reflecting into {@code java.util}. */
  private static final Set<String> UNPORTABLE_TYPES = Collections.unmodifiableSet(new HashSet<String>(
      Arrays.asList("java.util.IdentityHashMap", "java.util.Collections$SetFromMap", "java.util.WeakHashMap")));

  /** Safety cap so a pathological graph cannot hang the build. */
  private static final int MAX_INSPECTED_OBJECTS = 2000000;

  /**
   * Builds a small converging process with one recycle loop.
   *
   * @return a process that has been run to convergence
   */
  private static ProcessSystem buildRecycleProcess() {
    ProcessSystem process = new ProcessSystem("xstream portability process");

    Stream feed = new Stream("feed", createGasFluid());
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    Stream recycleGas = new Stream("recycle gas", createGasFluid());
    recycleGas.setFlowRate(5000.0, "kg/hr");
    recycleGas.setTemperature(25.0, "C");
    recycleGas.setPressure(50.0, "bara");
    process.add(recycleGas);

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleGas);
    process.add(mixer);

    Cooler cooler = new Cooler("cooler", mixer.getOutletStream());
    cooler.setOutTemperature(15.0, "C");
    process.add(cooler);

    Separator separator = new Separator("separator", cooler.getOutletStream());
    process.add(separator);

    Splitter splitter = new Splitter("splitter", separator.getGasOutStream());
    splitter.setSplitFactors(new double[] { 0.9, 0.1 });
    process.add(splitter);

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(recycleGas);
    recycle.setTolerance(1e-4);
    process.add(recycle);

    process.run();
    return process;
  }

  /**
   * Creates a small two-component gas fluid.
   *
   * @return configured gas fluid
   */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** A run process must not retain a JDK collection XStream can only reach by reflection. */
  @Test
  void testRunProcessHoldsNoTypesXStreamCannotConvert() {
    assertNoUnportableTypeIsReachable(buildRecycleProcess());
  }

  /** The same guard must hold for a freshly constructed process, before any unit is added. */
  @Test
  void testEmptyProcessHoldsNoTypesXStreamCannotConvert() {
    assertNoUnportableTypeIsReachable(new ProcessSystem("empty process"));
  }

  /** A process with a recycle must survive an XStream round trip and stay runnable. */
  @Test
  void testProcessWithRecycleRoundTripsThroughXStream() {
    ProcessSystem process = buildRecycleProcess();

    XStream xstream = new XStream();
    xstream.addPermission(AnyTypePermission.ANY);
    String xml = xstream.toXML(process);
    assertTrue(xml.contains("neqsim.process.processmodel.ProcessSystem"), "XML must contain the process root");

    ProcessSystem restored = (ProcessSystem) xstream.fromXML(xml);
    assertNotNull(restored.getUnit("recycle"), "restored process must keep its recycle");
    restored.run();
  }

  /**
   * Walks every object reachable through non-static, non-transient fields and fails on the first unportable JDK
   * collection found, reporting the field path that owns it.
   *
   * @param root object graph entry point
   */
  private static void assertNoUnportableTypeIsReachable(Object root) {
    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    Deque<Object[]> queue = new ArrayDeque<Object[]>();
    queue.add(new Object[] { root, root.getClass().getSimpleName() });
    int inspected = 0;

    while (!queue.isEmpty() && inspected < MAX_INSPECTED_OBJECTS) {
      Object[] entry = queue.poll();
      Object node = entry[0];
      String path = (String) entry[1];
      if (node == null || isLeaf(node) || !visited.add(node)) {
        continue;
      }
      inspected++;

      Class<?> type = node.getClass();
      if (UNPORTABLE_TYPES.contains(type.getName())) {
        fail(type.getName() + " is reachable at " + path
            + ". XStream has no converter for it and cannot reflect into java.util on JDK 9+,"
            + " so saving the process fails outside Surefire's --add-opens. Mark the owning field transient.");
      }

      if (type.isArray()) {
        if (!type.getComponentType().isPrimitive()) {
          int length = Array.getLength(node);
          for (int i = 0; i < length; i++) {
            queue.add(new Object[] { Array.get(node, i), path + "[" + i + "]" });
          }
        }
        continue;
      }
      if (node instanceof Collection) {
        for (Object element : (Collection<?>) node) {
          queue.add(new Object[] { element, path + "/element" });
        }
        continue;
      }
      if (node instanceof Map) {
        for (Map.Entry<?, ?> mapEntry : ((Map<?, ?>) node).entrySet()) {
          queue.add(new Object[] { mapEntry.getKey(), path + "/key" });
          queue.add(new Object[] { mapEntry.getValue(), path + "/value" });
        }
        continue;
      }
      if (isPlatformType(type)) {
        continue;
      }

      for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
        for (Field field : current.getDeclaredFields()) {
          int modifiers = field.getModifiers();
          if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.getType().isPrimitive()) {
            continue;
          }
          Object value;
          try {
            field.setAccessible(true);
            value = field.get(node);
          } catch (Throwable inaccessible) {
            continue;
          }
          queue.add(new Object[] { value, path + "." + field.getName() });
        }
      }
    }
  }

  /**
   * Tests whether an object carries no further references worth following.
   *
   * @param node object to classify
   * @return true when the object is an immutable scalar
   */
  private static boolean isLeaf(Object node) {
    return node instanceof String || node instanceof Number || node instanceof Boolean || node instanceof Character
        || node instanceof Enum || node instanceof Class;
  }

  /**
   * Tests whether a type belongs to the JDK or JVM vendor namespaces, which cannot be reflected into on JDK 9+.
   *
   * @param type type to classify
   * @return true when the type must not be opened by reflection
   */
  private static boolean isPlatformType(Class<?> type) {
    String name = type.getName();
    return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.")
        || name.startsWith("com.sun.");
  }
}
