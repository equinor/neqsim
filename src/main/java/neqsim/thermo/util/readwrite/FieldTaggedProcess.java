package neqsim.thermo.util.readwrite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Creates isolated process copies with a common, field-labelled Eclipse component slate.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class FieldTaggedProcess {
  /** Prevents construction of this utility class. */
  private FieldTaggedProcess() {
  }

  /**
   * Normalizes Eclipse pseudo-component decoration without removing origin labels.
   *
   * @param name component name
   * @return normalized base name
   */
  private static String key(String name) {
    return name.toLowerCase(Locale.ROOT).replace("_pc", "").replace("*", "");
  }

  /**
   * Collects equipment fluids without entering their thermodynamic property-model internals.
   *
   * @param object current process object
   * @param visited already visited identities
   * @param fluids equipment-owned thermodynamic systems
   */
  private static void collect(Object object, Set<Object> visited, Set<SystemInterface> fluids) {
    if (object == null || !visited.add(object)) {
      return;
    }
    if (object instanceof SystemInterface) {
      fluids.add((SystemInterface) object);
      return;
    }
    Class<?> type = object.getClass();
    if (type.isArray() && !type.getComponentType().isPrimitive()) {
      for (int index = 0; index < Array.getLength(object); index++) {
        collect(Array.get(object, index), visited, fluids);
      }
    } else if (object instanceof Iterable<?>) {
      for (Object child : (Iterable<?>) object) {
        collect(child, visited, fluids);
      }
    } else if (object instanceof Map<?, ?>) {
      for (Object child : ((Map<?, ?>) object).values()) {
        collect(child, visited, fluids);
      }
    } else if (type.getName().startsWith("neqsim.process.")
        || type.getName().startsWith("neqsim.thermodynamicoperations.")) {
      for (Class<?> owner = type; owner != null
          && owner.getName().startsWith("neqsim."); owner = owner.getSuperclass()) {
        for (Field field : owner.getDeclaredFields()) {
          if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
              || field.getType().isPrimitive()) {
            continue;
          }
          try {
            field.setAccessible(true);
            collect(field.get(object), visited, fluids);
          } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Cannot inspect process field " + field.getName(), exception);
          }
        }
      }
    }
  }

  /**
   * Evaluates a tagged copy with the normal process run step after invalidating bulk-flow caches that cannot observe
   * origin changes.
   *
   * @param process isolated process copy, never the live source
   */
  public static void runPass(ProcessSystem process) {
    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    collect(process, visited, Collections.newSetFromMap(new IdentityHashMap<SystemInterface, Boolean>()));
    for (Object object : visited) {
      Class<?> type = object.getClass();
      if (!type.getName().startsWith("neqsim.process.equipment.")) {
        continue;
      }
      for (Class<?> owner = type; owner != null
          && owner.getName().startsWith("neqsim."); owner = owner.getSuperclass()) {
        for (Field field : owner.getDeclaredFields()) {
          String name = field.getName();
          if (field.getType() == double.class && ("lastEnthalpy".equals(name) || "lastFlowRate".equals(name)
              || "lastPressure".equals(name) || "lastTemperature".equals(name))) {
            try {
              field.setAccessible(true);
              field.setDouble(object, Double.NaN);
            } catch (IllegalAccessException exception) {
              throw new IllegalArgumentException("Cannot invalidate equipment cache " + name, exception);
            }
          }
        }
      }
    }
    UUID identifier = UUID.randomUUID();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!unit.isLockedInactive()) {
        unit.isActive(true);
      }
    }
    process.run_step(identifier);
  }

  /**
   * Maps a fluid into one labelled block, preserving its phase inventory and thermodynamic state.
   *
   * @param source original fluid
   * @param base unlabelled Eclipse reference
   * @param tagged identical reference with complete labelled blocks
   * @param block destination block index
   * @return independent labelled fluid
   */
  public static SystemInterface tagFluid(SystemInterface source, SystemInterface base, SystemInterface tagged,
      int block) {
    int count = base.getNumberOfComponents();
    int total = tagged.getNumberOfComponents();
    if (count == 0 || total % count != 0 || block < 0 || block >= total / count) {
      throw new IllegalArgumentException("Invalid labelled component blocks");
    }
    Map<String, Integer> indices = new HashMap<String, Integer>();
    for (int index = 0; index < count; index++) {
      if (indices.put(key(base.getComponent(index).getComponentName()), index) != null) {
        throw new IllegalArgumentException("Ambiguous Eclipse component mapping");
      }
    }
    double[] composition = new double[total];
    int[] mapping = new int[source.getNumberOfComponents()];
    double sum = 0.0;
    for (int index = 0; index < source.getNumberOfComponents(); index++) {
      String name = source.getComponent(index).getComponentName();
      Integer target = indices.get(key(name));
      double fraction = source.getComponent(index).getz();
      mapping[index] = target == null ? -1 : target;
      if (!Double.isFinite(fraction) || fraction < 0.0 || (target == null && fraction > 1e-15)) {
        throw new IllegalArgumentException("Unsupported or invalid component: " + name);
      }
      if (target == null) {
        continue;
      }
      ComponentEos actual = (ComponentEos) source.getComponent(index);
      ComponentEos expected = (ComponentEos) base.getComponent(target);
      if (fraction > 1e-15) {
        double[] observed = {actual.getMolarMass(), actual.getTC(), actual.getPC(), actual.calca(), actual.calcb(),
            actual.getAcentricFactor(), actual.getVolumeCorrectionConst()};
        double[] reference = {expected.getMolarMass(), expected.getTC(), expected.getPC(), expected.calca(),
            expected.calcb(), expected.getAcentricFactor(), expected.getVolumeCorrectionConst()};
        for (int property = 0; property < observed.length; property++) {
          if (!Double.isFinite(observed[property]) || Math.abs(observed[property] - reference[property]) > 1e-8
              * Math.max(1.0, Math.abs(reference[property]))) {
            throw new IllegalArgumentException("Eclipse EOS mismatch for " + name + " property " + property);
          }
        }
      }
      composition[block * count + target] += fraction;
      sum += fraction;
    }
    for (int first = 0; first < mapping.length; first++) {
      for (int second = 0; second < mapping.length; second++) {
        if (mapping[first] < 0 || mapping[second] < 0) {
          continue;
        }
        double observed = ((PhaseEos) source.getPhase(0)).getMixingRule().getBinaryInteractionParameter(first, second);
        double expected = ((PhaseEos) base.getPhase(0)).getMixingRule().getBinaryInteractionParameter(mapping[first],
            mapping[second]);
        if (!Double.isFinite(observed) || Math.abs(observed - expected) > 1e-9) {
          throw new IllegalArgumentException("Eclipse binary interaction mismatch");
        }
      }
    }
    if (sum <= 0.0) {
      composition[block * count] = 1.0;
    }
    SystemInterface result = tagged.clone();
    result.setMolarComposition(composition);
    result.setTemperature(source.getTemperature());
    result.setPressure(source.getPressure());
    result.setTotalFlowRate(source.getFlowRate("mole/hr"), "mole/hr");
    result.init(0);
    result.setMultiPhaseCheck(source.doMultiPhaseCheck());
    result.allowPhaseShift(source.allowPhaseShift());
    result.setNumberOfPhases(source.getNumberOfPhases());
    for (int phase = 0; phase < result.getMaxNumberOfPhases(); phase++) {
      result.setPhaseIndex(phase, phase);
    }
    for (int phase = 0; phase < source.getNumberOfPhases(); phase++) {
      result.setPhaseType(phase, source.getPhase(phase).getType());
      result.setBeta(phase, source.getBeta(phase));
      for (int index = 0; index < total; index++) {
        result.getPhase(phase).getComponent(index).setx(0.0);
      }
      for (int index = 0; index < mapping.length; index++) {
        if (mapping[index] >= 0) {
          int target = block * count + mapping[index];
          result.getPhase(phase).getComponent(target)
              .setx(result.getPhase(phase).getComponent(target).getx() + source.getPhase(phase).getComponent(index).getx());
        }
      }
    }
    result.init(3);
    return result;
  }

  /**
   * Copies all serializable internal fluids into block zero without mutating the original graph.
   *
   * @param process source process
   * @param base common unlabelled Eclipse reference
   * @param tagged common labelled Eclipse reference
   * @return deeply isolated process with labelled internal fluids
   * @throws IOException if serialization or component mapping fails
   * @throws ClassNotFoundException if a process class cannot be restored
   */
  public static ProcessSystem copy(ProcessSystem process, final SystemInterface base, final SystemInterface tagged)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final Set<SystemInterface> targets = Collections.newSetFromMap(new IdentityHashMap<SystemInterface, Boolean>());
    collect(process, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), targets);
    final Set<SystemInterface> replacements = Collections
        .newSetFromMap(new IdentityHashMap<SystemInterface, Boolean>());
    replacements.add(tagged);
    try (ObjectOutputStream output = new ObjectOutputStream(bytes) {
      {
        enableReplaceObject(true);
      }

      /** {@inheritDoc} */
      @Override
      protected Object replaceObject(Object object) throws IOException {
        if (object instanceof SystemInterface && targets.contains(object) && !replacements.contains(object)) {
          SystemInterface replacement = tagFluid((SystemInterface) object, base, tagged, 0);
          replacements.add(replacement);
          return replacement;
        }
        return object;
      }
    }) {
      output.writeObject(process);
    }
    ProcessSystem result;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      result = (ProcessSystem) input.readObject();
    }
    for (ProcessEquipmentInterface unit : result.getUnitOperations()) {
      if (unit instanceof ComponentSplitter) {
        ComponentSplitter splitter = (ComponentSplitter) unit;
        double[] factors = splitter.getSplitFactors();
        if (factors.length != base.getNumberOfComponents()) {
          throw new IllegalArgumentException("Component splitter does not use the common Eclipse order");
        }
        double[] expanded = new double[tagged.getNumberOfComponents()];
        for (int index = 0; index < expanded.length; index++) {
          expanded[index] = factors[index % factors.length];
        }
        splitter.setSplitFactors(expanded);
      }
    }
    return result;
  }
}