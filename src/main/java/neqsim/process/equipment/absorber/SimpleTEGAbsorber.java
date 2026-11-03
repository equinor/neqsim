package neqsim.process.equipment.absorber;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * SimpleTEGAbsorber class.
 *
 * <p>
 * The gas and solvent feeds may have different component slates. The absorber combines their complete component
 * inventories before flashing and preserves the transferred water in its phase-specific outlet streams.
 * </p>
 *
 * <p>
 * The contactor equilibrium flash allows three phases. When a hydrocarbon liquid separates from the glycol, the
 * glycol-rich phase sets the water K-value and leaves as rich solvent, and the hydrocarbon liquid is routed to the
 * outlet chosen with {@link #setHydrocarbonLiquidRouting(HydrocarbonLiquidRouting)} (gas outlet by default) so that the
 * component balance closes.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SimpleTEGAbsorber extends SimpleAbsorber {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SimpleTEGAbsorber.class);

  protected ArrayList<StreamInterface> streams = new ArrayList<StreamInterface>(0);
  protected double pressure = 0;
  protected int numberOfInputStreams = 0;
  protected StreamInterface mixedStream;
  protected StreamInterface gasInStream;
  protected StreamInterface solventInStream;
  private StreamInterface gasOutStream;
  private StreamInterface solventOutStream;
  protected StreamInterface outStream;
  private double kwater = 1e-4;
  int solventStreamNumber = 0;
  private boolean isSetWaterInDryGas = false;
  private double waterInDryGas = 30e-6;
  private static final double COMPONENT_INVENTORY_RELATIVE_TOLERANCE = 1.0e-10;
  /** Minimum TEG weight fraction for the absorber liquid phase to be treated as glycol-rich. */
  public static final double GLYCOL_RICH_TEG_WEIGHT_FRACTION = 0.5;
  /** Kremser absorption factor A = L / (K_water G) of the last run. */
  private double absorptionFactor = 0.0;
  /** TEG weight fraction of the equilibrium liquid phase used for the water K-value in the last run. */
  private double equilibriumLiquidTEGWeightFraction = Double.NaN;
  /** Logical phase number of the gas phase in the mixed contactor fluid of the last run. */
  private int gasPhaseIndex = 0;
  /** Logical phase number of the glycol-rich (solvent) liquid phase in the mixed contactor fluid of the last run. */
  private int solventPhaseIndex = 1;
  /** Mole fraction of the contactor feed that formed a separate hydrocarbon liquid in the last run. */
  private double hydrocarbonLiquidMoleFraction = 0.0;
  /** Informational copy of the separate hydrocarbon liquid of the last run, or null when none formed. */
  private StreamInterface hydrocarbonLiquidOutStream = null;
  /** Outlet that receives a separate hydrocarbon liquid phase. */
  private HydrocarbonLiquidRouting hydrocarbonLiquidRouting = HydrocarbonLiquidRouting.GAS_OUTLET;
  /** Whether the contactor TP flash runs a phase-stability check for a separate hydrocarbon liquid. */
  private boolean hydrocarbonLiquidCheck = true;

  /** Outlet receiving a hydrocarbon liquid phase that separates from the glycol in the contactor. */
  public enum HydrocarbonLiquidRouting {
    /** Hydrocarbon liquid leaves with the treated gas as entrained or condensed hydrocarbon (default). */
    GAS_OUTLET,
    /** Hydrocarbon liquid leaves with the rich glycol, as when condensate is carried down the column. */
    RICH_SOLVENT_OUTLET
  }

  /** Raised when an absorber operation changes the conserved component inventory. */
  private static final class ComponentInventoryException extends IllegalStateException {
    private static final long serialVersionUID = 1000;

    private ComponentInventoryException(String message) {
      super(message);
    }
  }

  /** Immutable component-mole inventory used to guard absorber reinitialization steps. */
  private static final class ComponentInventory {
    private final Map<String, Double> componentMoles;
    private final double totalMoles;

    private ComponentInventory(Map<String, Double> componentMoles, double totalMoles) {
      this.componentMoles = componentMoles;
      this.totalMoles = totalMoles;
    }

    private static ComponentInventory capture(SystemInterface system) {
      Map<String, Double> componentMoles = new LinkedHashMap<String, Double>();
      double totalMoles = 0.0;
      PhaseInterface referencePhase = system.getPhase(0);
      for (int componentIndex = 0; componentIndex < referencePhase.getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = referencePhase.getComponent(componentIndex);
        double moles = component.getNumberOfmoles();
        componentMoles.put(component.getName(), moles);
        totalMoles += moles;
      }
      return new ComponentInventory(componentMoles, totalMoles);
    }

    private static ComponentInventory capturePhase(SystemInterface system, int phaseNumber) {
      Map<String, Double> componentMoles = new LinkedHashMap<String, Double>();
      double totalMoles = 0.0;
      PhaseInterface phase = system.getPhase(phaseNumber);
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = phase.getComponent(componentIndex);
        double moles = component.getNumberOfMolesInPhase();
        componentMoles.put(component.getName(), moles);
        totalMoles += moles;
      }
      return new ComponentInventory(componentMoles, totalMoles);
    }

    private void requireUnchanged(SystemInterface system, String step) {
      PhaseInterface referencePhase = system.getPhase(0);
      for (Map.Entry<String, Double> entry : componentMoles.entrySet()) {
        ComponentInterface component = referencePhase.getComponent(entry.getKey());
        double actual = component == null ? 0.0 : component.getNumberOfmoles();
        requireClose(entry.getValue(), actual, step, entry.getKey());
      }
      requireClose(totalMoles, system.getTotalNumberOfMoles(), step, "total moles");
    }

    private void requireActivePhaseSum(SystemInterface system, String step) {
      for (Map.Entry<String, Double> entry : componentMoles.entrySet()) {
        double actual = 0.0;
        for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
          ComponentInterface component = system.getPhase(phaseNumber).getComponent(entry.getKey());
          if (component != null) {
            actual += component.getNumberOfMolesInPhase();
          }
        }
        requireClose(entry.getValue(), actual, step, entry.getKey());
      }
    }

    /**
     * Checks without throwing whether the active phases of a system sum to this inventory.
     *
     * @param system system to check
     * @return true when every component's phase sum matches the inventory within the tolerance
     */
    private boolean isActivePhaseSumConsistent(SystemInterface system) {
      for (Map.Entry<String, Double> entry : componentMoles.entrySet()) {
        double expected = entry.getValue();
        double actual = activePhaseMoles(system, entry.getKey());
        double tolerance = COMPONENT_INVENTORY_RELATIVE_TOLERANCE * Math.max(1.0, Math.abs(expected));
        if (Double.isNaN(actual) || Double.isInfinite(actual) || Math.abs(expected - actual) > tolerance) {
          return false;
        }
      }
      return true;
    }

    private void requireCombinedActivePhaseSum(SystemInterface firstSystem, SystemInterface secondSystem, String step) {
      for (Map.Entry<String, Double> entry : componentMoles.entrySet()) {
        double actual = activePhaseMoles(firstSystem, entry.getKey()) + activePhaseMoles(secondSystem, entry.getKey());
        requireClose(entry.getValue(), actual, step, entry.getKey());
      }
      requireClose(totalMoles, firstSystem.getTotalNumberOfMoles() + secondSystem.getTotalNumberOfMoles(), step,
          "total moles");
    }

    private static double activePhaseMoles(SystemInterface system, String componentName) {
      double moles = 0.0;
      for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
        ComponentInterface component = system.getPhase(phaseNumber).getComponent(componentName);
        if (component != null) {
          moles += component.getNumberOfMolesInPhase();
        }
      }
      return moles;
    }

    private static void requireClose(double expected, double actual, String step, String componentName) {
      double tolerance = COMPONENT_INVENTORY_RELATIVE_TOLERANCE * Math.max(1.0, Math.abs(expected));
      if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
        throw new ComponentInventoryException("SimpleTEGAbsorber changed " + componentName + " inventory during " + step
            + ": expected " + expected + " mol, found " + actual + " mol");
      }
    }
  }

  private static void runInventoryCheckedInitialization(SystemInterface system, Runnable initialization, String step) {
    ComponentInventory inventory = ComponentInventory.capture(system);
    initialization.run();
    inventory.requireUnchanged(system, step);
  }

  /**
   * Extracts a logical phase while retaining that phase's backing implementation in every system slot.
   *
   * <p>
   * The {@code phaseToSystem(PhaseInterface)} overload mutates its receiver, so perform the extraction on a clone. This
   * preserves the legacy single-phase system structure without selecting a raw backing-array index.
   * </p>
   *
   * @param system source multiphase system
   * @param logicalPhaseNumber logical active-phase number to extract
   * @return independent single-phase system containing the selected logical phase
   */
  static SystemInterface extractLogicalPhase(SystemInterface system, int logicalPhaseNumber) {
    SystemInterface phaseSystem = system.clone();
    return phaseSystem.phaseToSystem(phaseSystem.getPhase(logicalPhaseNumber));
  }

  /**
   * Constructor for SimpleTEGAbsorber.
   *
   * @param name a {@link java.lang.String} object
   */
  public SimpleTEGAbsorber(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addStream(StreamInterface newStream) {
    streams.add(newStream);
    if (numberOfInputStreams == 0) {
      mixedStream = streams.get(0).clone(this.getName() + " mixed stream");
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      runInventoryCheckedInitialization(mixedStream.getThermoSystem(), () -> mixedStream.getThermoSystem().init(0),
          "initial mixed-stream init(0)");
      runInventoryCheckedInitialization(mixedStream.getThermoSystem(), () -> mixedStream.getThermoSystem().init(3),
          "initial mixed-stream init(3)");
    }

    numberOfInputStreams++;
  }

  /**
   * addGasInStream.
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addGasInStream(StreamInterface newStream) {
    // TODO: fail if gasInStream is not null?
    gasInStream = newStream;
    gasOutStream = newStream.clone();
    addStream(newStream);
  }

  /**
   * addSolventInStream.
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addSolventInStream(StreamInterface newStream) {
    // TODO: fail if solventInStream is not null?
    solventInStream = newStream;
    solventOutStream = newStream.clone();
    addStream(newStream);
    solventStreamNumber = streams.size() - 1;
  }

  /**
   * replaceSolventInStream.
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void replaceSolventInStream(StreamInterface newStream) {
    // TODO: fails if solventStreamNumber is 0, i.e. no solventinstream set?
    solventInStream = newStream;
    streams.set(solventStreamNumber, solventInStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * mixStream.
   */
  public void mixStream() {
    for (int k = 1; k < streams.size(); k++) {
      mixedStream.getThermoSystem().addFluid(streams.get(k).getThermoSystem());
    }
    runInventoryCheckedInitialization(mixedStream.getThermoSystem(), () -> mixedStream.getThermoSystem().init_x_y(),
        "mixed-stream init_x_y");
    runInventoryCheckedInitialization(mixedStream.getThermoSystem(), () -> mixedStream.getThermoSystem().initBeta(),
        "mixed-stream initBeta");
    runInventoryCheckedInitialization(mixedStream.getThermoSystem(), () -> mixedStream.getThermoSystem().init(2),
        "mixed-stream init(2)");
  }

  /**
   * guessTemperature.
   *
   * @return a double
   */
  public double guessTemperature() {
    double gtemp = 0;
    for (int k = 0; k < streams.size(); k++) {
      gtemp += streams.get(k).getThermoSystem().getTemperature() * streams.get(k).getThermoSystem().getNumberOfMoles()
          / mixedStream.getThermoSystem().getNumberOfMoles();
    }
    return gtemp;
  }

  /**
   * calcMixStreamEnthalpy.
   *
   * @return a double
   */
  public double calcMixStreamEnthalpy() {
    double enthalpy = 0;
    for (int k = 0; k < streams.size(); k++) {
      final SystemInterface streamSystem = streams.get(k).getThermoSystem();
      runInventoryCheckedInitialization(streamSystem, () -> streamSystem.init(3), "inlet enthalpy init(3)");
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      // System.out.println("total enthalpy k : " + (
      // ((StreamInterface) streams.get(k)).getThermoSystem()).getEnthalpy());
    }
    // System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutStream() {
    return mixedStream;
  }

  /**
   * getInStream.
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getInStream() {
    return gasInStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * Getter for the field <code>gasInStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGasInStream() {
    return gasInStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquidOutStream() {
    return solventOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getSolventInStream() {
    return solventInStream;
  }

  /**
   * calcEa.
   *
   * @return a double
   */
  public double calcEa() {
    double A = mixedStream.getThermoSystem().getPhase(solventPhaseIndex).getNumberOfMolesInPhase()
        / mixedStream.getThermoSystem().getPhase(gasPhaseIndex).getNumberOfMolesInPhase() / kwater;
    absorptionFactor = A;
    absorptionEfficiency = (Math.pow(A, getNumberOfTheoreticalStages() + 1) - A)
        / (Math.pow(A, getNumberOfTheoreticalStages() + 1) - 1.0);
    return absorptionEfficiency;
  }

  /**
   * calcY0.
   *
   * @return a double
   */
  public double calcY0() {
    // double fugacityWaterLiquid =
    // mixedStream.getThermoSystem().getPhase(1).getFugacity("water");
    // double xrel =
    // mixedStream.getFluid().getPhase(0).getComponent("water").getx()/solventInStream.getFluid().getPhase(0).getComponent("water").getx();
    // double y0 =
    // xrel*fugacityWaterLiquid/(mixedStream.getFluid().getPhase(0).getComponent("water").getFugacityCoefficient()*mixedStream.getFluid().getPressure());
    double fugCoefRef = mixedStream.getThermoSystem().getPhase(solventPhaseIndex).getComponent("water")
        .getFugacityCoefficient();
    double y0 = solventInStream.getFluid().getPhase(0).getComponent("water").getx() * fugCoefRef
        / (mixedStream.getThermoSystem().getPhase(gasPhaseIndex).getComponent("water").getFugacityCoefficient());
    return y0;
  }

  /**
   * calcNumberOfTheoreticalStages.
   *
   * @return a double
   */
  public double calcNumberOfTheoreticalStages() {
    setNumberOfTheoreticalStages(getStageEfficiency() * getNumberOfStages());
    return getNumberOfTheoreticalStages();
  }

  /**
   * calcNTU.
   *
   * @param y0 a double
   * @param y1 a double
   * @param yb a double
   * @param ymix a double
   * @return a double
   */
  public double calcNTU(double y0, double y1, double yb, double ymix) {
    return Math.log((yb - ymix) / (y1 - y0));
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    try {
      double y1 = 0.0;
      // double yN = gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx();
      final boolean outletMultiPhaseCheck = streams.get(0).getThermoSystem().doMultiPhaseCheck();
      final int outletMaxNumberOfPhases = streams.get(0).getThermoSystem().getMaxNumberOfPhases();
      mixedStream.setThermoSystem((streams.get(0).getThermoSystem().clone()));
      // The gas + lean glycol mixture can split into gas, condensed hydrocarbon and glycol. A two-phase flash of that
      // mixture converges to a metastable or non-material-balanced state, so the equilibrium flash allows three phases.
      mixedStream.getThermoSystem().setMultiPhaseCheck(hydrocarbonLiquidCheck);
      if (!hydrocarbonLiquidCheck) {
        mixedStream.getThermoSystem().setMaxNumberOfPhases(Math.max(2, outletMaxNumberOfPhases));
      }
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      runInventoryCheckedInitialization(mixedStream.getThermoSystem(), () -> mixedStream.getThermoSystem().init(0),
          "run mixed-stream init(0)");
      mixStream();
      ComponentInventory feedInventory = ComponentInventory.capture(mixedStream.getThermoSystem());
      double enthalpy = calcMixStreamEnthalpy();
      mixedStream.getThermoSystem().setTemperature(guessTemperature());
      ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
      testOps.TPflash();
      feedInventory.requireUnchanged(mixedStream.getThermoSystem(), "TP flash");
      feedInventory.requireActivePhaseSum(mixedStream.getThermoSystem(), "TP flash phase recombination");
      if (mixedStream.getThermoSystem().getNumberOfPhases() <= 2) {
        // No separate hydrocarbon liquid found (or the check is disabled): keep the faster two-phase PH flash, and
        // fall back to a three-phase flash only if the two-phase solution loses the material balance or lumps
        // hydrocarbon with the glycol (a third phase appears between the guess and the adiabatic mixing
        // temperature).
        SystemInterface threePhaseStart = mixedStream.getThermoSystem().clone();
        mixedStream.getThermoSystem().setMultiPhaseCheck(false);
        mixedStream.getThermoSystem().setMaxNumberOfPhases(Math.max(2, outletMaxNumberOfPhases));
        testOps.PHflash(enthalpy, 0);
        double twoPhaseLiquidTeg = mixedStream.getThermoSystem().getNumberOfPhases() > 1
            ? tegWeightFraction(mixedStream.getThermoSystem().getPhase(1))
            : Double.NaN;
        if (!feedInventory.isActivePhaseSumConsistent(mixedStream.getThermoSystem())
            || twoPhaseLiquidTeg < GLYCOL_RICH_TEG_WEIGHT_FRACTION) {
          logger.warn("SimpleTEGAbsorber '{}': two-phase PH flash lost the material balance or lumped hydrocarbon "
              + "with the glycol; repeating it with a three-phase flash.", getName());
          mixedStream.setThermoSystem(threePhaseStart);
          mixedStream.getThermoSystem().setMultiPhaseCheck(true);
          testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
          testOps.TPflash();
          testOps.PHflash(enthalpy, 0);
        }
      } else {
        testOps.PHflash(enthalpy, 0);
      }
      feedInventory.requireUnchanged(mixedStream.getThermoSystem(), "PH flash");
      feedInventory.requireActivePhaseSum(mixedStream.getThermoSystem(), "PH flash phase recombination");

      List<Integer> hydrocarbonLiquidPhases = classifyPhases(mixedStream.getThermoSystem());
      if (!hydrocarbonLiquidPhases.isEmpty()) {
        logger.warn(
            "SimpleTEGAbsorber '{}': a separate hydrocarbon liquid phase ({} mol% of the contactor feed) forms at {} C. "
                + "It is routed to the {} and excluded from the glycol phase used for the water K-value. "
                + "Check the gas/TEG temperature difference or remove condensate upstream.",
            getName(), String.format("%.4f", 100.0 * hydrocarbonLiquidMoleFraction),
            String.format("%.2f", mixedStream.getThermoSystem().getTemperature() - 273.15),
            hydrocarbonLiquidRouting == HydrocarbonLiquidRouting.GAS_OUTLET ? "gas outlet" : "rich solvent outlet");
      }

      double xWaterLiquid = mixedStream.getThermoSystem().getPhase(solventPhaseIndex).getComponent("water").getx();
      equilibriumLiquidTEGWeightFraction = tegWeightFraction(mixedStream.getThermoSystem().getPhase(solventPhaseIndex));
      if (!Double.isNaN(equilibriumLiquidTEGWeightFraction)
          && equilibriumLiquidTEGWeightFraction < GLYCOL_RICH_TEG_WEIGHT_FRACTION) {
        logger.warn(
            "SimpleTEGAbsorber '{}': the liquid phase used for the water K-value is not glycol-rich "
                + "(TEG weight fraction {}). Condensed hydrocarbon is lumped with the glycol, which overstates "
                + "K_water and understates water absorption. Check the gas/TEG ratio or remove condensate upstream.",
            getName(), String.format("%.3f", equilibriumLiquidTEGWeightFraction));
      }
      if (xWaterLiquid > 1.0e-10) {
        kwater = mixedStream.getThermoSystem().getPhase(gasPhaseIndex).getComponent("water").getx() / xWaterLiquid;
      } else {
        kwater = 1.0e-5;
      }

      calcNumberOfTheoreticalStages();
      // System.out.println("number of theoretical stages " +
      // getNumberOfTheoreticalStages());
      double absorptionEffiency = calcEa();

      double y0 = calcY0();
      if (isSetWaterInDryGas) {
        y1 = waterInDryGas;
        setNumberOfTheoreticalStages(2.0);
      } else {
        y1 = gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx()
            - absorptionEffiency * (gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx() - y0);
      }

      double yMean = mixedStream.getThermoSystem().getPhase(gasPhaseIndex).getComponent("water").getx();
      double molesWaterToMove = (yMean - y1)
          * mixedStream.getThermoSystem().getPhase(gasPhaseIndex).getNumberOfMolesInPhase();
      StreamInterface newMixedStream = mixedStream.clone();
      newMixedStream.getThermoSystem().addComponent("water", -molesWaterToMove, gasPhaseIndex);
      newMixedStream.getThermoSystem().addComponent("water", molesWaterToMove, solventPhaseIndex);
      feedInventory.requireUnchanged(newMixedStream.getThermoSystem(), "phase-to-phase water transfer");
      feedInventory.requireActivePhaseSum(newMixedStream.getThermoSystem(), "phase-to-phase water transfer");
      runInventoryCheckedInitialization(newMixedStream.getThermoSystem(),
          () -> newMixedStream.getThermoSystem().initBeta(), "water-transfer initBeta");
      runInventoryCheckedInitialization(newMixedStream.getThermoSystem(),
          () -> newMixedStream.getThermoSystem().init_x_y(), "water-transfer init_x_y");
      runInventoryCheckedInitialization(newMixedStream.getThermoSystem(),
          () -> newMixedStream.getThermoSystem().init(2), "water-transfer init(2)");
      feedInventory.requireActivePhaseSum(newMixedStream.getThermoSystem(), "water-transfer reinitialization");
      mixedStream = newMixedStream;
      mixedStream.setCalculationIdentifier(id);

      // stream.getThermoSystem().display();

      ComponentInventory gasInventory = ComponentInventory.capturePhase(mixedStream.getThermoSystem(), gasPhaseIndex);
      final SystemInterface gasTemp = extractLogicalPhase(mixedStream.getThermoSystem(), gasPhaseIndex);
      gasInventory.requireUnchanged(gasTemp, "gas phase extraction");

      ComponentInventory liquidInventory = ComponentInventory.capturePhase(mixedStream.getThermoSystem(),
          solventPhaseIndex);
      final SystemInterface liqTemp = extractLogicalPhase(mixedStream.getThermoSystem(), solventPhaseIndex);
      liquidInventory.requireUnchanged(liqTemp, "liquid phase extraction");

      SystemInterface hydrocarbonLiquid = null;
      for (int phaseNumber : hydrocarbonLiquidPhases) {
        ComponentInventory hcInventory = ComponentInventory.capturePhase(mixedStream.getThermoSystem(), phaseNumber);
        SystemInterface hcPhase = extractLogicalPhase(mixedStream.getThermoSystem(), phaseNumber);
        hcInventory.requireUnchanged(hcPhase, "hydrocarbon liquid phase extraction");
        if (hydrocarbonLiquid == null) {
          hydrocarbonLiquid = hcPhase;
        } else {
          hydrocarbonLiquid.addFluid(hcPhase);
        }
      }
      if (hydrocarbonLiquid != null) {
        final SystemInterface hcSystem = hydrocarbonLiquid;
        runInventoryCheckedInitialization(hcSystem, () -> hcSystem.init(2), "hydrocarbon liquid init(2)");
        hcSystem.setMultiPhaseCheck(outletMultiPhaseCheck);
        if (hydrocarbonLiquidOutStream == null) {
          hydrocarbonLiquidOutStream = gasOutStream.clone(getName() + " hydrocarbon liquid");
        }
        hydrocarbonLiquidOutStream.setThermoSystem(hcSystem.clone());
        if (hydrocarbonLiquidRouting == HydrocarbonLiquidRouting.GAS_OUTLET) {
          combineAndFlash(gasTemp, hcSystem, "gas outlet hydrocarbon recombination");
        } else {
          combineAndFlash(liqTemp, hcSystem, "rich solvent hydrocarbon recombination");
        }
      } else {
        hydrocarbonLiquidOutStream = null;
      }
      gasTemp.setMultiPhaseCheck(outletMultiPhaseCheck);
      liqTemp.setMultiPhaseCheck(outletMultiPhaseCheck);
      gasTemp.setMaxNumberOfPhases(Math.max(outletMaxNumberOfPhases, gasTemp.getNumberOfPhases()));
      liqTemp.setMaxNumberOfPhases(Math.max(outletMaxNumberOfPhases, liqTemp.getNumberOfPhases()));
      runInventoryCheckedInitialization(gasTemp, () -> gasTemp.init(2), "gas outlet init(2)");
      runInventoryCheckedInitialization(liqTemp, () -> liqTemp.init(2), "liquid outlet init(2)");
      feedInventory.requireCombinedActivePhaseSum(gasTemp, liqTemp, "combined outlet phase extraction");
      gasOutStream.setThermoSystem(gasTemp);
      solventOutStream.setThermoSystem(liqTemp);

      setNTU(calcNTU(y0, y1, gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx(), yMean));
      // System.out.println("NTU " + getNTU());

      // double Ks = 0.055;
      getSolventOutStream().getThermoSystem().initPhysicalProperties();
      getSolventOutStream().setCalculationIdentifier(id);
      getGasOutStream().getThermoSystem().initPhysicalProperties();
      getGasOutStream().setCalculationIdentifier(id);

      // double vtemp = Ks * Math.sqrt((getSolventOutStream().getThermoSystem().getPhase(0)
      // .getPhysicalProperties().getDensity() -
      // getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties()
      // .getDensity()) /
      // getSolventOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());

      // double d = Math.sqrt(4.0 * getGasOutStream().getMolarRate() *
      // getGasOutStream().getThermoSystem().getPhase(0).getMolarMass() /
      // getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties()
      // .getDensity()/ 3.14 / vtemp);
      // System.out.println("diameter " + d);
      setCalculationIdentifier(id);
    } catch (ComponentInventoryException ex) {
      throw ex;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // System.out.println("rich TEG from absorber " +
    // getSolventOutStream().getFlowRate("kg/hr"));
  }

  /**
   * Calculates the Souders-Brown gas load factor (K-factor) for the contactor.
   *
   * <p>
   * The gas load factor is defined as {@code Ks = Vs * sqrt(rho_gas / (rho_liquid - rho_gas))} where {@code Vs} is the
   * superficial gas velocity (m/s), {@code rho_gas} is the gas outlet density (kg/m3), and {@code rho_liquid} is the
   * rich TEG outlet density (kg/m3).
   * </p>
   *
   * @return gas load factor in m/s, or 0 if the liquid density does not exceed the gas density
   */
  @Override
  public double getGasLoadFactor() {
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    double vs = getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea;
    double gasDensity = getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    double liquidDensity = getSolventOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    if (liquidDensity <= gasDensity) {
      return 0.0;
    }
    return vs * Math.sqrt(gasDensity / (liquidDensity - gasDensity));
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    SystemInterface thermoSystem = mixedStream.getThermoSystem();
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    thermoSystem.initPhysicalProperties();
    String[][] table = new String[50][5];
    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);

    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = thermoSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] = nf.format(thermoSystem.getPhases()[i].getComponent(j).getx(), buf, test).toString();
        table[j + 1][4] = "[-]";
      }
      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] = nf
          .format(thermoSystem.getPhases()[i].getPhysicalProperties().getDensity(), buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[kg/m^3]";

      // Double.longValue(thermoSystem.getPhases()[i].getBeta());

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] = nf
          .format(thermoSystem.getPhases()[i].getBeta(), buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][i + 1] = nf
          .format(thermoSystem.getPhases()[i].getMolarMass() * 1000, buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][4] = "[kg/kmol]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Cp";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] = nf
          .format((thermoSystem.getPhases()[i].getCp() / (thermoSystem.getPhases()[i].getNumberOfMolesInPhase()
              * thermoSystem.getPhases()[i].getMolarMass() * 1000)), buf, test)
          .toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/kg*K]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Viscosity";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] = nf
          .format((thermoSystem.getPhases()[i].getPhysicalProperties().getViscosity()), buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[kg/m*sec]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Conductivity";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] = nf
          .format(thermoSystem.getPhases()[i].getPhysicalProperties().getConductivity(), buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[W/m*K]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] = Double
          .toString(thermoSystem.getPhases()[i].getPressure());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] = Double
          .toString(thermoSystem.getPhases()[i].getTemperature());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
      Double.toString(thermoSystem.getPhases()[i].getTemperature());

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Stream";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = getName();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
    }

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * Setter for the field <code>gasOutStream</code>.
   *
   * @param gasOutStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public void setGasOutStream(StreamInterface gasOutStream) {
    this.gasOutStream = gasOutStream;
  }

  /**
   * Getter for the field <code>solventOutStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getSolventOutStream() {
    return solventOutStream;
  }

  /**
   * Setter for the field <code>solventOutStream</code>.
   *
   * @param solventOutStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setSolventOutStream(StreamInterface solventOutStream) {
    this.solventOutStream = solventOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refTEGabsorberloc) {
    double yin = getGasInStream().getFluid().getPhase("gas").getComponent("water").getx();
    double yout = getGasOutStream().getFluid().getPhase("gas").getComponent("water").getx();
    double y0 = calcY0();
    double A = mixedStream.getThermoSystem().getPhase(1).getNumberOfMolesInPhase()
        / mixedStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase() / kwater;
    double N = Math.log(((A - 1.0) / A) * ((yin - y0) / (yout - y0)) + (1.0 / A)) / Math.log(A);
    setNumberOfTheoreticalStages(N);
  }

  /**
   * Setter for the field <code>waterInDryGas</code>.
   *
   * @param waterInDryGasInput water in dry gas
   */
  public void setWaterInDryGas(double waterInDryGasInput) {
    waterInDryGas = waterInDryGasInput;
    isSetWaterInDryGas = true;
  }

  /**
   * isSetWaterInDryGas.
   *
   * @param isSetwaterInDryGas a boolean
   */
  public void isSetWaterInDryGas(boolean isSetwaterInDryGas) {
    this.isSetWaterInDryGas = isSetwaterInDryGas;
  }

  /**
   * Calculates the Fs factor (gas capacity factor) for structured packing in the contactor.
   *
   * <p>
   * The Fs factor is defined as:
   * </p>
   *
   * <pre>
   * Fs = Vs * sqrt(rho_gas)
   * </pre>
   *
   * <p>
   * where Vs is the superficial gas velocity (m/s) and rho_gas is the gas density (kg/m3). The Fs factor is
   * proportional to the aerodynamic lift exerted by the gas on the liquid flowing down the packing. Typical maximum
   * design value for structured packing is 3.0 m/s*sqrt(kg/m3).
   * </p>
   *
   * @return Fs factor in m/s*sqrt(kg/m3), or 0 if streams are not initialized
   */
  @Override
  public double getFsFactor() {
    if (getGasOutStream() == null || getGasOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    if (intArea <= 0.0) {
      return 0.0;
    }
    double vs = getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea;
    double rhoGas = getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    return vs * Math.sqrt(rhoGas);
  }

  /**
   * Calculates the maximum allowable Fs factor for the contactor packing.
   *
   * <p>
   * For structured packing in glycol contactors, the maximum Fs factor is typically limited to 3.0 m/s*sqrt(kg/m3) to
   * ensure sufficient hydraulic packing capacity and robustness for protection of downstream equipment.
   * </p>
   *
   * @return maximum allowable Fs factor in m/s*sqrt(kg/m3)
   */
  public double getMaxAllowableFsFactor() {
    return 3.0;
  }

  /**
   * Checks whether the current Fs factor is within the design limit.
   *
   * @return true if Fs factor is within the maximum allowable limit
   */
  public boolean isFsFactorWithinDesignLimit() {
    return getFsFactor() <= getMaxAllowableFsFactor();
  }

  /**
   * Calculates the Fs factor utilization as a fraction of the maximum.
   *
   * @return utilization ratio (0.0-1.0+). Values above 1.0 indicate the design limit is exceeded.
   */
  public double getFsFactorUtilization() {
    double maxFs = getMaxAllowableFsFactor();
    if (maxFs <= 0.0) {
      return 0.0;
    }
    return getFsFactor() / maxFs;
  }

  /**
   * Calculates the minimum vessel internal diameter to meet the Fs factor limit at the current gas flow rate.
   *
   * <p>
   * From Fs = Vs * sqrt(rho_gas) and Vs = Q / A, the minimum diameter is:
   * </p>
   *
   * <pre>
   * D_min = sqrt(4 * Q * sqrt(rho_gas) / (pi * Fs_max))
   * </pre>
   *
   * @return minimum internal diameter in metres, or 0 if streams are not initialized
   */
  public double getMinimumDiameterForFsLimit() {
    if (getGasOutStream() == null || getGasOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double gasFlowM3s = getGasOutStream().getThermoSystem().getFlowRate("m3/sec");
    double rhoGas = getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    double maxFs = getMaxAllowableFsFactor();
    if (maxFs <= 0.0) {
      return 0.0;
    }
    return Math.sqrt(4.0 * gasFlowM3s * Math.sqrt(rhoGas) / (Math.PI * maxFs));
  }

  /**
   * Calculates the lean TEG equilibrium water dew point temperature at the contactor pressure.
   *
   * <p>
   * Returns the water dew point that the lean TEG can achieve in equilibrium. This is used to verify that the lean TEG
   * quality provides sufficient margin below the treated gas dew point specification.
   * </p>
   *
   * @return equilibrium water dew point in Kelvin, or 0 if solvent stream is not available
   */
  public double getLeanTEGEquilibriumWaterDewPoint() {
    if (solventInStream == null || solventInStream.getThermoSystem() == null) {
      return 0.0;
    }
    try {
      SystemInterface tempSystem = solventInStream.getThermoSystem().clone();
      tempSystem.setTemperature(273.15 + 20.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(tempSystem);
      ops.waterDewPointTemperatureFlash();
      return tempSystem.getTemperature();
    } catch (Exception ex) {
      logger.error("Failed to calculate lean TEG equilibrium water dew point", ex);
      return 0.0;
    }
  }

  /**
   * Checks if the lean TEG equilibrium water dew point is at least the specified margin below the target dew point.
   *
   * <p>
   * Industry practice requires that the equilibrium water dew point of the lean TEG be at least 10 degC below the
   * treated gas dew point specification.
   * </p>
   *
   * @param targetDewPointC treated gas water dew point specification in degrees Celsius
   * @param marginC required margin in degrees Celsius (typically 10)
   * @return true if the lean TEG equilibrium dew point has sufficient margin
   */
  public boolean hasAdequateTEGQualityMargin(double targetDewPointC, double marginC) {
    double eqDewPointK = getLeanTEGEquilibriumWaterDewPoint();
    if (eqDewPointK <= 0.0) {
      return false;
    }
    double eqDewPointC = eqDewPointK - 273.15;
    return eqDewPointC <= (targetDewPointC - marginC);
  }

  /**
   * Validates the TEG contactor design by checking Fs factor, gas load factor, and lean TEG quality. Returns a summary
   * string with all design checks.
   *
   * @return design validation summary string
   */
  public String validateContactorDesign() {
    StringBuilder sb = new StringBuilder();
    sb.append("TEG Contactor Design Validation\n");
    sb.append("================================\n");

    double fs = getFsFactor();
    double maxFs = getMaxAllowableFsFactor();
    sb.append(String.format("Fs factor: %.3f m/s*sqrt(kg/m3) (max: %.1f)\n", fs, maxFs));
    sb.append(String.format("Fs utilization: %.1f%%\n", getFsFactorUtilization() * 100.0));
    sb.append(String.format("Fs within limit: %s\n", isFsFactorWithinDesignLimit() ? "OK" : "EXCEEDED"));

    if (!isFsFactorWithinDesignLimit()) {
      sb.append(String.format("Minimum diameter needed: %.3f m\n", getMinimumDiameterForFsLimit()));
    }

    double gasLoadFactor = 0.0;
    if (getGasOutStream() != null && getGasOutStream().getThermoSystem() != null && getSolventOutStream() != null
        && getSolventOutStream().getThermoSystem() != null) {
      gasLoadFactor = getGasLoadFactor();
    }
    sb.append(String.format("Gas load factor (Ks): %.4f m/s\n", gasLoadFactor));

    sb.append(String.format("NTU: %.2f\n", getNTU()));
    sb.append(String.format("Number of theoretical stages: %.2f\n", getNumberOfTheoreticalStages()));

    return sb.toString();
  }

  /**
   * Returns the water K-value (y/x) used by the Kremser calculation in the last run.
   *
   * @return water K-value (-)
   */
  public double getKwater() {
    return kwater;
  }

  /**
   * Returns the Kremser absorption factor A = L / (K_water G) of the last run.
   *
   * <p>
   * Values close to or below 1 mean the glycol circulation cannot absorb the water load in the available theoretical
   * stages; the absorption efficiency then falls steeply.
   * </p>
   *
   * @return absorption factor (-), 0 before the first run
   */
  public double getAbsorptionFactor() {
    return absorptionFactor;
  }

  /**
   * Returns the TEG weight fraction of the equilibrium liquid phase that set the water K-value in the last run.
   *
   * @return TEG weight fraction (0-1), or NaN when the fluid has no TEG component or the absorber has not run
   */
  public double getEquilibriumLiquidTEGWeightFraction() {
    return equilibriumLiquidTEGWeightFraction;
  }

  /**
   * Checks whether the liquid phase used for the water K-value in the last run was glycol-rich.
   *
   * <p>
   * The absorber flashes the mixed gas and solvent with up to three phases and uses the most glycol-rich liquid for the
   * water K-value; a separate hydrocarbon liquid is reported by {@link #hasHydrocarbonLiquid()}. If the selected liquid
   * is still not glycol-rich (for example almost no glycol circulation), the water K-value is overstated and results
   * with this method returning false should not be used for dew point predictions.
   * </p>
   *
   * @return true when the TEG weight fraction is at least {@link #GLYCOL_RICH_TEG_WEIGHT_FRACTION} (or no TEG is
   * present to check)
   */
  public boolean isEquilibriumLiquidGlycolRich() {
    return Double.isNaN(equilibriumLiquidTEGWeightFraction)
        || equilibriumLiquidTEGWeightFraction >= GLYCOL_RICH_TEG_WEIGHT_FRACTION;
  }

  /**
   * TEG weight fraction of a phase.
   *
   * @param phase the phase to evaluate
   * @return TEG weight fraction (0-1), or NaN when the phase has no TEG component
   */
  private static double tegWeightFraction(PhaseInterface phase) {
    if (!phase.hasComponent("TEG")) {
      return Double.NaN;
    }
    try {
      return phase.getWtFrac("TEG");
    } catch (Exception ex) {
      return Double.NaN;
    }
  }

  /**
   * Weight fraction of the polar solvent components (water and glycols/methanol) in a phase.
   *
   * @param phase the phase to evaluate
   * @return combined weight fraction of water, TEG, DEG, MEG and methanol (0-1)
   */
  private static double polarWeightFraction(PhaseInterface phase) {
    String[] polarComponents = {"water", "TEG", "DEG", "MEG", "methanol"};
    double fraction = 0.0;
    for (String componentName : polarComponents) {
      if (phase.hasComponent(componentName)) {
        try {
          fraction += phase.getWtFrac(componentName);
        } catch (Exception ex) {
          logger.debug("Could not evaluate weight fraction of {}: {}", componentName, ex.getMessage());
        }
      }
    }
    return fraction;
  }

  /**
   * Identifies the gas phase, the glycol-rich solvent phase and any separate hydrocarbon liquid phases of the flashed
   * contactor fluid, and stores the phase numbers for the Kremser calculation.
   *
   * <p>
   * The gas phase is the first phase of gas type (phase 0 when none is typed as gas). The solvent phase is the other
   * phase with the largest TEG weight fraction (largest polar weight fraction when the fluid has no TEG). All remaining
   * phases are treated as hydrocarbon liquid.
   * </p>
   *
   * @param system flashed contactor fluid
   * @return logical phase numbers of the hydrocarbon liquid phases (empty when none formed)
   */
  private List<Integer> classifyPhases(SystemInterface system) {
    List<Integer> hydrocarbonPhases = new ArrayList<Integer>();
    int numberOfPhases = system.getNumberOfPhases();
    gasPhaseIndex = 0;
    solventPhaseIndex = 1;
    hydrocarbonLiquidMoleFraction = 0.0;
    if (numberOfPhases < 2) {
      return hydrocarbonPhases;
    }
    for (int phaseNumber = 0; phaseNumber < numberOfPhases; phaseNumber++) {
      if (system.getPhase(phaseNumber).getType() == PhaseType.GAS) {
        gasPhaseIndex = phaseNumber;
        break;
      }
    }
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int phaseNumber = 0; phaseNumber < numberOfPhases; phaseNumber++) {
      if (phaseNumber == gasPhaseIndex) {
        continue;
      }
      PhaseInterface phase = system.getPhase(phaseNumber);
      double score = tegWeightFraction(phase);
      if (Double.isNaN(score)) {
        score = polarWeightFraction(phase);
      }
      if (score > bestScore) {
        bestScore = score;
        solventPhaseIndex = phaseNumber;
      }
    }
    for (int phaseNumber = 0; phaseNumber < numberOfPhases; phaseNumber++) {
      if (phaseNumber != gasPhaseIndex && phaseNumber != solventPhaseIndex) {
        hydrocarbonPhases.add(phaseNumber);
        hydrocarbonLiquidMoleFraction += system.getBeta(phaseNumber);
      }
    }
    return hydrocarbonPhases;
  }

  /**
   * Adds the components of one outlet system to another and re-flashes the result at its temperature and pressure,
   * verifying that the combined component inventory is conserved.
   *
   * @param target system receiving the added fluid; modified in place
   * @param addition system whose components are added to the target
   * @param step description used in inventory error messages
   */
  private static void combineAndFlash(SystemInterface target, SystemInterface addition, String step) {
    target.addFluid(addition);
    ComponentInventory combinedInventory = ComponentInventory.capture(target);
    target.setMultiPhaseCheck(true);
    new ThermodynamicOperations(target).TPflash();
    combinedInventory.requireUnchanged(target, step);
    combinedInventory.requireActivePhaseSum(target, step);
  }

  /**
   * Checks whether a separate hydrocarbon liquid phase formed in the contactor in the last run.
   *
   * @return true when the equilibrium flash found a hydrocarbon liquid besides the gas and the glycol phase
   */
  public boolean hasHydrocarbonLiquid() {
    return hydrocarbonLiquidOutStream != null;
  }

  /**
   * Returns the mole fraction of the contactor feed (gas plus lean solvent) that formed a separate hydrocarbon liquid
   * in the last run.
   *
   * @return hydrocarbon liquid mole fraction (0-1), 0 when no hydrocarbon liquid formed
   */
  public double getHydrocarbonLiquidMoleFraction() {
    return hydrocarbonLiquidMoleFraction;
  }

  /**
   * Returns an informational copy of the separate hydrocarbon liquid of the last run.
   *
   * <p>
   * The hydrocarbon liquid is already included in the outlet selected by
   * {@link #setHydrocarbonLiquidRouting(HydrocarbonLiquidRouting)}; do not connect this stream to downstream equipment
   * in addition to that outlet, or the hydrocarbon would be counted twice.
   * </p>
   *
   * @return hydrocarbon liquid stream (composition before water transfer), or null when none formed
   */
  public StreamInterface getHydrocarbonLiquidOutStream() {
    return hydrocarbonLiquidOutStream;
  }

  /**
   * Returns the outlet that receives a separate hydrocarbon liquid phase.
   *
   * @return hydrocarbon liquid routing
   */
  public HydrocarbonLiquidRouting getHydrocarbonLiquidRouting() {
    return hydrocarbonLiquidRouting;
  }

  /**
   * Sets the outlet that receives a separate hydrocarbon liquid phase. The default is
   * {@link HydrocarbonLiquidRouting#GAS_OUTLET}.
   *
   * @param routing hydrocarbon liquid routing; null restores the default
   */
  public void setHydrocarbonLiquidRouting(HydrocarbonLiquidRouting routing) {
    this.hydrocarbonLiquidRouting = routing == null ? HydrocarbonLiquidRouting.GAS_OUTLET : routing;
  }

  /**
   * Checks whether the contactor flash tests for a separate hydrocarbon liquid phase.
   *
   * @return true when the phase-stability check is enabled (default)
   */
  public boolean isHydrocarbonLiquidCheck() {
    return hydrocarbonLiquidCheck;
  }

  /**
   * Enables or disables the phase-stability check for a separate hydrocarbon liquid in the contactor flash.
   *
   * <p>
   * The check (a multiphase TP flash) costs roughly 0.1 to 0.5 s per run for a CPA fluid when no hydrocarbon liquid
   * forms. Disabling it restores the faster two-phase flash for lean gases. A hydrocarbon liquid is then only detected
   * when the two-phase solution loses the material balance or the liquid is no longer glycol-rich, in which case the
   * absorber still repeats the flash with three phases; a small condensate split that the two-phase flash ignores is
   * not detected.
   * </p>
   *
   * @param check true to run the stability check (default), false for the faster two-phase flash
   */
  public void setHydrocarbonLiquidCheck(boolean check) {
    this.hydrocarbonLiquidCheck = check;
  }
}
