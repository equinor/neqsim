package com.equinor.neqsim.spotbugs;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.SignatureParser;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;

/**
 * Finds typical NeqSim fluid-state mistakes that have local evidence in a single method.
 *
 * <ul>
 * <li>{@code NEQSIM_STALE_PROPERTY_READ}: a fluid is modified or TP-flashed in the method (the flash
 * is linked to the fluid through {@code new ThermodynamicOperations(fluid)}) and a caloric or
 * physical property of the same fluid is read before it is initialised again.</li>
 * <li>{@code NEQSIM_SHARED_FLUID_MUTATION}: a state-changing call is made directly on the fluid
 * returned by {@code getThermoSystem()} or {@code getFluid()}, which changes the fluid of the
 * stream or unit instead of a clone.</li>
 * <li>{@code NEQSIM_MISSING_MIXING_RULE}: a fluid created in the method is flashed or initialised
 * before {@code setMixingRule(...)} has been called on it.</li>
 * </ul>
 *
 * <p>
 * All state is forgotten at jump targets and when a variable is overwritten, so the checks only
 * report straight-line code where the problem is certain.
 * </p>
 */
public class StaleFluidStateDetector extends OpcodeStackDetector {
  /** Fluid modified or flashed, then read without initialisation. */
  static final String STALE_BUG = "NEQSIM_STALE_PROPERTY_READ";

  /** State-changing call on the fluid owned by a stream or unit. */
  static final String SHARED_BUG = "NEQSIM_SHARED_FLUID_MUTATION";

  /** Flash or init before a mixing rule is set. */
  static final String MIXING_BUG = "NEQSIM_MISSING_MIXING_RULE";

  private static final int DENSITY = 1;
  private static final int VISCOSITY = 2;
  private static final int CONDUCTIVITY = 4;
  private static final int FULL_ONLY = 8;
  private static final int ALL = DENSITY | VISCOSITY | CONDUCTIVITY | FULL_ONLY;

  private static final String PHASE_PACKAGE = "neqsim/thermo/phase/";
  private static final String SYSTEM_PACKAGE = "neqsim/thermo/system/";
  private static final String PHYSICAL_PACKAGE = "neqsim/physicalproperties/";
  private static final String OPERATIONS_CLASS = "neqsim/thermodynamicoperations/ThermodynamicOperations";

  private static final Set<String> CALORIC_GETTERS = new HashSet<>(Arrays.asList("getEnthalpy",
      "getEntropy", "getCp", "getCv", "getGibbsEnergy", "getInternalEnergy", "getHelmholtzEnergy",
      "getGamma", "getGamma2", "getKappa", "getJouleThomsonCoefficient", "getSoundSpeed"));

  private static final Map<String, Integer> PHYSICAL_GETTERS = new HashMap<>();

  static {
    PHYSICAL_GETTERS.put("getViscosity", VISCOSITY);
    PHYSICAL_GETTERS.put("getKinematicViscosity", VISCOSITY | DENSITY);
    PHYSICAL_GETTERS.put("getThermalConductivity", CONDUCTIVITY);
    PHYSICAL_GETTERS.put("getConductivity", CONDUCTIVITY);
    PHYSICAL_GETTERS.put("getDiffusionCoefficient", FULL_ONLY);
    PHYSICAL_GETTERS.put("getEffectiveDiffusionCoefficient", FULL_ONLY);
    PHYSICAL_GETTERS.put("getSurfaceTension", FULL_ONLY);
    PHYSICAL_GETTERS.put("getInterfacialTension", FULL_ONLY);
  }

  /** Calls that change the state of a fluid and invalidate derived properties. */
  private static final Set<String> MUTATORS = new HashSet<>(Arrays.asList("setTemperature",
      "setPressure", "setMolarComposition", "setMolarCompositionPlus", "setMolarCompositionOfPlusFluid",
      "setTotalFlowRate", "setTotalNumberOfMoles", "addComponent", "addFluid", "removeComponent",
      "setMixingRule", "setPhaseType"));

  /** Subset of mutators that is a clear mistake when applied to a stream's own fluid. */
  private static final Set<String> SHARED_MUTATORS = new HashSet<>(Arrays.asList("setTemperature",
      "setPressure", "setMolarComposition", "setTotalFlowRate", "addComponent", "setMixingRule"));

  private static final Set<String> FLUID_GETTERS =
      new HashSet<>(Arrays.asList("getThermoSystem", "getFluid"));

  private static final Set<String> INPUT_GETTERS = new HashSet<>(
      Arrays.asList("getInStream", "getInletStream", "getFeedStream", "getInletStreams"));

  /** State of one tracked fluid. */
  private static final class FluidState {
    boolean caloricStale;
    int physical;
    String cause = "";
  }

  private final BugReporter bugReporter;
  private final Map<String, FluidState> states = new HashMap<>();
  private final Map<Integer, String> opsToFluid = new HashMap<>();
  private final Set<Integer> needsMixingRule = new HashSet<>();
  /** Program counters of fluid getter calls, mapped to whether the stream is an input. */
  private final Map<Integer, Boolean> inputFluidAt = new HashMap<>();
  private String pendingOpsFluid;
  private boolean pendingNewFluid;
  private boolean pendingFresh;
  private String lastStringConstant = "";
  private String lastStaticField = "";
  private boolean staleReported;
  private boolean sharedReported;
  private boolean mixingReported;

  /**
   * Creates the detector.
   *
   * @param bugReporter reporter supplied by SpotBugs
   */
  public StaleFluidStateDetector(BugReporter bugReporter) {
    this.bugReporter = bugReporter;
  }

  @Override
  public void visitClassContext(ClassContext classContext) {
    if (CaloricPropertyWithoutInitDetector.isChecked(classContext.getJavaClass().getClassName())) {
      super.visitClassContext(classContext);
    }
  }

  @Override
  public void visit(Code obj) {
    states.clear();
    opsToFluid.clear();
    needsMixingRule.clear();
    inputFluidAt.clear();
    pendingOpsFluid = null;
    pendingNewFluid = false;
    pendingFresh = false;
    staleReported = false;
    sharedReported = false;
    mixingReported = false;
    lastStringConstant = "";
    lastStaticField = "";
    super.visit(obj);
  }

  @Override
  public void sawOpcode(int seen) {
    boolean consume = pendingFresh;
    pendingFresh = false;
    if (stack.isJumpTarget(getPC())) {
      states.clear();
      needsMixingRule.clear();
    }
    if (isRegisterStore()) {
      handleStore(consume);
      return;
    }
    if (seen == Const.LDC || seen == Const.LDC_W) {
      if (getConstantRefOperand() instanceof org.apache.bcel.classfile.ConstantString) {
        lastStringConstant = getStringConstantOperand();
      }
      return;
    }
    if (seen == Const.GETSTATIC) {
      lastStaticField = getNameConstantOperand();
      return;
    }
    if (seen == Const.PUTFIELD || seen == Const.PUTSTATIC) {
      String field = "f" + getXFieldOperand();
      states.remove(field);
      return;
    }
    if (seen == Const.INVOKEVIRTUAL || seen == Const.INVOKEINTERFACE || seen == Const.INVOKESPECIAL) {
      handleInvoke(seen);
    } else if (seen == Const.INVOKESTATIC) {
      int parameters = new SignatureParser(getSigConstantOperand()).getNumParameters();
      for (int i = 0; i < parameters && i < stack.getStackDepth(); i++) {
        String key = keyOf(stack.getStackItem(i));
        if (key != null) {
          states.remove(key);
        }
      }
      if (getClassConstantOperand().equals(getClassName().replace('.', '/'))) {
        states.clear();
      }
    }
  }

  private void handleStore(boolean consume) {
    int register = getRegisterOperand();
    states.remove("r" + register);
    needsMixingRule.remove(register);
    opsToFluid.remove(register);
    if (consume && pendingOpsFluid != null) {
      opsToFluid.put(register, pendingOpsFluid);
    }
    if (consume && pendingNewFluid) {
      needsMixingRule.add(register);
    }
    pendingOpsFluid = null;
    pendingNewFluid = false;
  }

  private void handleInvoke(int seen) {
    String owner = getClassConstantOperand();
    String name = getNameConstantOperand();
    String signature = getSigConstantOperand();
    int parameters = new SignatureParser(signature).getNumParameters();
    if (stack.getStackDepth() <= parameters) {
      return;
    }
    OpcodeStack.Item receiver = stack.getStackItem(parameters);
    boolean fluidOwner = owner.startsWith(PHASE_PACKAGE) || owner.startsWith(SYSTEM_PACKAGE);
    if (seen == Const.INVOKESPECIAL && "<init>".equals(name)) {
      if (OPERATIONS_CLASS.equals(owner) && parameters >= 1) {
        pendingOpsFluid = keyOf(stack.getStackItem(parameters - 1));
        pendingFresh = pendingOpsFluid != null;
      } else if (owner.startsWith(SYSTEM_PACKAGE) && owner.substring(SYSTEM_PACKAGE.length()).startsWith("System")) {
        pendingNewFluid = true;
        pendingFresh = true;
      }
      return;
    }
    if (OPERATIONS_CLASS.equals(owner)) {
      handleOperation(name, receiver);
      return;
    }
    if (FLUID_GETTERS.contains(name) && !owner.startsWith(SYSTEM_PACKAGE)) {
      inputFluidAt.put(getPC(), isInputStream(receiver));
    }
    if (!fluidOwner && !owner.startsWith(PHYSICAL_PACKAGE)) {
      forgetPassedFluids(parameters, owner, receiver);
      return;
    }
    String key = keyOf(receiver);
    if (fluidOwner && SHARED_MUTATORS.contains(name) && returnsOwnedFluid(receiver) && !sharedReported) {
      sharedReported = true;
      report(SHARED_BUG, name);
    }
    if (fluidOwner && name.startsWith("init")) {
      handleInit(name, signature, key);
      return;
    }
    if (fluidOwner && name.startsWith("setMixingRule") && key != null && registerOf(key) >= 0) {
      needsMixingRule.remove(registerOf(key));
    }
    if (fluidOwner && MUTATORS.contains(name) && key != null) {
      FluidState state = state(key);
      state.caloricStale = true;
      state.physical = 0;
      state.cause = name;
      return;
    }
    handleRead(name, receiver, key);
  }

  private void handleOperation(String name, OpcodeStack.Item receiver) {
    String fluid = receiver.getRegisterNumber() >= 0 ? opsToFluid.get(receiver.getRegisterNumber()) : null;
    if (fluid == null) {
      return;
    }
    if (needsMixingRule.contains(registerOf(fluid)) && !mixingReported && !name.startsWith("calc")) {
      mixingReported = true;
      report(MIXING_BUG, name);
    }
    if (name.endsWith("flash") || name.contains("flash")) {
      FluidState state = state(fluid);
      state.caloricStale = name.startsWith("TPflash");
      state.physical = 0;
      state.cause = name;
    }
  }

  private void handleInit(String name, String signature, String key) {
    if (key == null) {
      return;
    }
    if (needsMixingRule.contains(registerOf(key)) && !mixingReported && "init".equals(name)
        && "(I)V".equals(signature)) {
      Object constant = stack.getStackItem(0).getConstant();
      if (constant instanceof Integer && ((Integer) constant) >= 1) {
        mixingReported = true;
        report(MIXING_BUG, name);
      }
    }
    FluidState state = state(key);
    if ("init".equals(name) && "(I)V".equals(signature)) {
      Object constant = stack.getStackItem(0).getConstant();
      if (constant instanceof Integer) {
        state.caloricStale = ((Integer) constant) < 2;
        if (state.caloricStale) {
          state.cause = "init(" + constant + ")";
        }
        return;
      }
    }
    if ("initProperties".equals(name)) {
      state.caloricStale = false;
      state.physical = ALL;
    } else if (name.startsWith("initPhysicalProperties")) {
      state.physical |= physicalMask(signature);
    } else {
      state.caloricStale = false;
    }
  }

  private void handleRead(String name, OpcodeStack.Item receiver, String key) {
    FluidState state = key == null ? singleTrackedFluid(receiver) : states.get(key);
    if (state == null) {
      return;
    }
    Integer needed = PHYSICAL_GETTERS.get(name);
    boolean stale = CALORIC_GETTERS.contains(name) && state.caloricStale
        || needed != null && (state.physical & needed) != needed && !state.cause.isEmpty();
    if (stale && !staleReported) {
      staleReported = true;
      report(STALE_BUG, name + " after " + state.cause);
    }
  }

  /**
   * A phase read as fluid.getPhase(i).getX() has no key of its own; use the only tracked fluid.
   *
   * @param receiver stack item the property is read from
   * @return the state of the single tracked fluid, or null
   */
  private FluidState singleTrackedFluid(OpcodeStack.Item receiver) {
    XMethod source = receiver.getReturnValueOf();
    if (source == null || !source.getName().startsWith("getPhase") || states.size() != 1) {
      return null;
    }
    return states.values().iterator().next();
  }

  private boolean returnsOwnedFluid(OpcodeStack.Item receiver) {
    XMethod source = receiver.getReturnValueOf();
    if (source == null || !FLUID_GETTERS.contains(source.getName())) {
      return false;
    }
    Boolean input = inputFluidAt.get(receiver.getPC());
    return input != null && input;
  }

  /**
   * Tells whether a stream is an input of the method: a parameter or an inlet getter result.
   *
   * @param stream stack item the fluid getter is called on
   * @return true if changing its fluid changes something the caller owns
   */
  private boolean isInputStream(OpcodeStack.Item stream) {
    int register = stream.getRegisterNumber();
    if (register >= 0) {
      int first = getMethod().isStatic() ? 0 : 1;
      int slots = first;
      for (org.apache.bcel.generic.Type type : getMethod().getArgumentTypes()) {
        slots += type.getSize();
      }
      return register >= first && register < slots;
    }
    XMethod source = stream.getReturnValueOf();
    return source != null && INPUT_GETTERS.contains(source.getName());
  }

  /**
   * Forgets fluids that are handed to code the detector cannot see.
   *
   * @param parameters number of parameters of the call
   * @param owner class of the called method
   * @param receiver stack item the method is called on
   */
  private void forgetPassedFluids(int parameters, String owner, OpcodeStack.Item receiver) {
    for (int i = 0; i < parameters; i++) {
      String key = keyOf(stack.getStackItem(i));
      if (key != null) {
        states.remove(key);
      }
    }
    if (owner.equals(getClassName().replace('.', '/')) || receiver.getRegisterNumber() == 0) {
      states.clear();
    }
  }

  private FluidState state(String key) {
    return states.computeIfAbsent(key, k -> new FluidState());
  }

  private static int registerOf(String key) {
    return key.startsWith("r") ? Integer.parseInt(key.substring(1)) : -1;
  }

  private static String keyOf(OpcodeStack.Item item) {
    if (item.getXField() != null) {
      return "f" + item.getXField();
    }
    if (item.getRegisterNumber() >= 0) {
      return "r" + item.getRegisterNumber();
    }
    return null;
  }

  private int physicalMask(String signature) {
    String key;
    if (signature.startsWith("(Ljava/lang/String;")) {
      key = lastStringConstant;
    } else if (signature.contains("PhysicalPropertyType")) {
      key = lastStaticField;
    } else {
      return ALL;
    }
    switch (key.toUpperCase(java.util.Locale.ROOT)) {
      case "DENSITY":
      case "MASS_DENSITY":
        return DENSITY;
      case "VISCOSITY":
      case "DYNAMIC_VISCOSITY":
        return VISCOSITY;
      case "CONDUCTIVITY":
      case "THERMAL_CONDUCTIVITY":
        return CONDUCTIVITY;
      default:
        return ALL;
    }
  }

  private void report(String bugType, String detail) {
    bugReporter.reportBug(new BugInstance(this, bugType, NORMAL_PRIORITY).addClassAndMethod(this)
        .addString(detail).addSourceLine(this));
  }
}
