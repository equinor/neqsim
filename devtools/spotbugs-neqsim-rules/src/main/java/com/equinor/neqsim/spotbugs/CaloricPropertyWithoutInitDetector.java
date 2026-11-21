package com.equinor.neqsim.spotbugs;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.SignatureParser;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantString;

/**
 * Reports methods that read a caloric or physical property of a NeqSim phase or fluid before
 * anything in the same method has initialised it.
 *
 * <p>
 * Caloric properties (enthalpy, entropy, Cp, Cv and so on) are built from temperature and pressure
 * derivatives that are only valid after {@code init(2)} or higher; a TP flash leaves the phases at
 * init level 1. Physical properties (density, viscosity, thermal conductivity, diffusion
 * coefficients, interfacial tension) need the physical property models to be initialised. When only
 * one of density, viscosity or conductivity is needed, {@code initPhysicalProperties("density")}
 * (or the {@code PhysicalPropertyType} overload) is the fastest correct call and is accepted for
 * that property only. Diffusion coefficients and interfacial tension have no targeted
 * initialisation and need {@code initPhysicalProperties()}, {@code initProperties()} or
 * {@code calcInterfaceProperties()}. The check is a linear scan of the method bytecode.
 * </p>
 *
 * <ul>
 * <li>Caloric guard: an {@code init*} call (except {@code initPhysicalProperties}) on a phase or
 * system, any {@code ThermodynamicOperations} call, or a {@code run}/{@code runTransient} call.</li>
 * <li>Physical guard: see above, or a {@code run}/{@code runTransient} call.</li>
 * </ul>
 *
 * <p>
 * Classes inside {@code neqsim.thermo}, {@code neqsim.thermodynamicoperations} and
 * {@code neqsim.physicalproperties} implement the properties and are not checked.
 * </p>
 */
public class CaloricPropertyWithoutInitDetector extends OpcodeStackDetector {
  /** Bug pattern for caloric properties read without init(2) or higher. */
  static final String CALORIC_BUG = "NEQSIM_CALORIC_WITHOUT_INIT";

  /** Bug pattern for physical properties read without physical property initialisation. */
  static final String PHYSICAL_BUG = "NEQSIM_PHYSICAL_WITHOUT_INIT";

  /** Bug pattern for an init call that repeats an earlier identical call on the same fluid. */
  static final String REDUNDANT_BUG = "NEQSIM_REDUNDANT_INIT";

  private static final int DENSITY = 1;
  private static final int VISCOSITY = 2;
  private static final int CONDUCTIVITY = 4;
  /** Properties that can only be initialised by the full physical property call. */
  private static final int FULL_ONLY = 8;
  private static final int ALL = DENSITY | VISCOSITY | CONDUCTIVITY | FULL_ONLY;

  private static final String PHASE_PACKAGE = "neqsim/thermo/phase/";
  private static final String SYSTEM_PACKAGE = "neqsim/thermo/system/";
  private static final String PHYSICAL_PACKAGE = "neqsim/physicalproperties/";
  private static final String OPERATIONS_PACKAGE = "neqsim/thermodynamicoperations/";

  private static final Set<String> CALORIC_GETTERS = new HashSet<>(Arrays.asList("getEnthalpy",
      "getEntropy", "getCp", "getCv", "getGibbsEnergy", "getInternalEnergy", "getHelmholtzEnergy",
      "getGamma", "getGamma2", "getKappa", "getJouleThomsonCoefficient", "getSoundSpeed"));

  /** Physical property getter and the initialised properties it needs. */
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

  private final BugReporter bugReporter;
  private boolean caloricInitialised;
  private int physicalInitialised;
  private boolean caloricReported;
  private boolean physicalReported;
  private boolean redundantReported;
  /** True for client classes; the property implementations themselves are only checked for repeats. */
  private boolean propertyChecks = true;
  /** Last init call per fluid: key is receiver, method name and signature, value the arguments. */
  private final Map<String, String> lastInit = new HashMap<>();
  private String lastStringConstant = "";
  private String lastStaticField = "";

  /**
   * Creates the detector.
   *
   * @param bugReporter reporter supplied by SpotBugs
   */
  public CaloricPropertyWithoutInitDetector(BugReporter bugReporter) {
    this.bugReporter = bugReporter;
  }

  @Override
  public void visitClassContext(ClassContext classContext) {
    String name = classContext.getJavaClass().getClassName();
    propertyChecks = isChecked(name);
    if (name.startsWith("neqsim.")) {
      super.visitClassContext(classContext);
    }
  }

  /**
   * Tells whether a class is checked. Classes that implement the properties are skipped.
   *
   * @param dottedClassName fully qualified class name
   * @return true if the class belongs to NeqSim outside the property implementations
   */
  static boolean isChecked(String dottedClassName) {
    return dottedClassName.startsWith("neqsim.") && !dottedClassName.startsWith("neqsim.thermo.")
        && !dottedClassName.startsWith("neqsim.thermodynamicoperations.")
        && !dottedClassName.startsWith("neqsim.physicalproperties.");
  }

  @Override
  public void visit(Code obj) {
    caloricInitialised = false;
    physicalInitialised = 0;
    caloricReported = false;
    physicalReported = false;
    redundantReported = false;
    lastInit.clear();
    lastStaticField = "";
    super.visit(obj);
  }

  @Override
  public void sawOpcode(int seen) {
    if (stack.isJumpTarget(getPC()) || isBranch(seen) || isRegisterStore() || seen == Const.PUTFIELD
        || seen == Const.PUTSTATIC) {
      lastInit.clear();
    }
    if (seen == Const.LDC || seen == Const.LDC_W) {
      if (getConstantRefOperand() instanceof ConstantString) {
        lastStringConstant = getStringConstantOperand();
      }
      return;
    }
    if (seen == Const.GETSTATIC) {
      lastStaticField = getNameConstantOperand();
      return;
    }
    if (seen != Const.INVOKEVIRTUAL && seen != Const.INVOKEINTERFACE
        && seen != Const.INVOKESPECIAL) {
      return;
    }
    String owner = getClassConstantOperand();
    String name = getNameConstantOperand();
    boolean thermo = owner.startsWith(PHASE_PACKAGE) || owner.startsWith(SYSTEM_PACKAGE);
    boolean physical = thermo || owner.startsWith(PHYSICAL_PACKAGE);
    boolean run = "run".equals(name) || "runTransient".equals(name);
    trackInit(owner, name, thermo, physical);
    if (!propertyChecks) {
      return;
    }
    if (run || owner.startsWith(OPERATIONS_PACKAGE)
        || thermo && name.startsWith("init") && !name.startsWith("initPhysical")) {
      caloricInitialised = true;
    }
    if (run || "initProperties".equals(name) || "calcInterfaceProperties".equals(name)) {
      physicalInitialised = ALL;
    } else if (name.startsWith("initPhysicalProperties")) {
      physicalInitialised |= initialisedByPhysicalCall();
    }
    if (thermo && CALORIC_GETTERS.contains(name) && !caloricInitialised && !caloricReported) {
      caloricReported = true;
      report(CALORIC_BUG, name);
      return;
    }
    Integer needed = PHYSICAL_GETTERS.get(name);
    if (physical && needed == null && "getDensity".equals(name)
        && owner.startsWith(PHYSICAL_PACKAGE)) {
      needed = DENSITY;
    }
    if (physical && needed != null && (physicalInitialised & needed) != needed
        && !physicalReported) {
      physicalReported = true;
      report(PHYSICAL_BUG, name);
    }
  }

  /**
   * Finds which properties the current initPhysicalProperties call initialises.
   *
   * @return bit mask of initialised properties
   */
  private int initialisedByPhysicalCall() {
    String signature = getSigConstantOperand();
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

  /**
   * Tracks init calls per fluid and reports one that repeats an identical earlier call.
   *
   * <p>
   * Only calls with a known receiver (local variable or field) and constant arguments are tracked.
   * Any call that is not a plain getter or an init call, any store, branch or jump target forgets
   * the tracked state, because the fluid may have changed.
   * </p>
   *
   * @param owner class of the invoked method
   * @param name invoked method name
   * @param thermo true if the owner is a phase or system type
   * @param physical true if the owner is a phase, system or physical property type
   */
  private void trackInit(String owner, String name, boolean thermo, boolean physical) {
    boolean flash = (OPERATIONS_PACKAGE + "ThermodynamicOperations").equals(owner) && "TPflash".equals(name)
        && getSigConstantOperand().startsWith("()");
    if (!(thermo && name.startsWith("init")) && !flash) {
      boolean getter = physical && (name.startsWith("get") || name.startsWith("is"));
      if (!getter) {
        lastInit.clear();
      }
      return;
    }
    String signature = getSigConstantOperand();
    int parameters = new SignatureParser(signature).getNumParameters();
    if (stack.getStackDepth() <= parameters) {
      lastInit.clear();
      return;
    }
    OpcodeStack.Item receiver = stack.getStackItem(parameters);
    String receiverKey = null;
    if (receiver.getXField() != null) {
      receiverKey = "f" + receiver.getXField();
    } else if (receiver.getRegisterNumber() >= 0) {
      receiverKey = "r" + receiver.getRegisterNumber();
    }
    StringBuilder arguments = new StringBuilder();
    for (int i = 0; i < parameters; i++) {
      Object constant = stack.getStackItem(i).getConstant();
      if (constant == null) {
        receiverKey = null;
      }
      arguments.append(',').append(constant);
    }
    if (receiverKey == null) {
      lastInit.clear();
      return;
    }
    String key = receiverKey + "|" + name + "|" + signature;
    if (arguments.toString().equals(lastInit.get(key))) {
      if (!redundantReported) {
        redundantReported = true;
        report(REDUNDANT_BUG, name);
      }
    }
    if ("init".equals(name)) {
      String prefix = receiverKey + "|init|";
      lastInit.keySet().removeIf(k -> k.startsWith(prefix));
    }
    // A flash and an init on the same fluid reset each other's "already done" state.
    boolean thisIsFlash = flash;
    lastInit.keySet().removeIf(k -> k.contains("|TPflash|") != thisIsFlash);
    lastInit.put(key, arguments.toString());
  }

  private void report(String bugType, String property) {
    bugReporter.reportBug(new BugInstance(this, bugType, NORMAL_PRIORITY).addClassAndMethod(this)
        .addString(property).addSourceLine(this));
  }
}
