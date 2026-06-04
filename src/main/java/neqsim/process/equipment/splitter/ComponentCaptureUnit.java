package neqsim.process.equipment.splitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Selective component capture unit for screening separation trains.
 *
 * <p>
 * The unit creates two outlet streams from one inlet stream: a captured stream containing a
 * selected fraction of one named component, and a treated stream containing the remaining gas or
 * liquid. It is intentionally simple and deterministic so route builders can represent CO2 capture,
 * water drying, mercury guard beds, or other high-level component removal steps before a detailed
 * absorber, membrane, or molecular-sieve package is available.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ComponentCaptureUnit extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Inlet stream to split. */
  private StreamInterface inletStream;

  /** Captured component stream. */
  private StreamInterface capturedStream;

  /** Treated outlet stream. */
  private StreamInterface treatedStream;

  /** Component selected for capture. */
  private String componentName = "CO2";

  /** Fraction of selected component routed to the captured stream. */
  private double captureFraction = 0.90;

  /** Captured component mole flow in the stream basis. */
  private double capturedComponentMoleFlow = 0.0;

  /** Treated component mole flow in the stream basis. */
  private double treatedComponentMoleFlow = 0.0;

  /**
   * Constructs a component capture unit without inlet stream.
   *
   * @param name unit operation name
   */
  public ComponentCaptureUnit(String name) {
    super(name);
  }

  /**
   * Constructs a component capture unit with inlet stream.
   *
   * @param name unit operation name
   * @param inletStream inlet stream
   */
  public ComponentCaptureUnit(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * Sets the inlet stream and initializes outlet stream placeholders.
   *
   * @param inletStream inlet stream
   */
  public void setInletStream(StreamInterface inletStream) {
    if (inletStream == null || inletStream.getThermoSystem() == null) {
      throw new IllegalArgumentException("inletStream with a thermo system is required");
    }
    this.inletStream = inletStream;
    capturedStream =
        new Stream(getName() + " captured " + componentName, inletStream.getThermoSystem().clone());
    treatedStream = new Stream(getName() + " treated", inletStream.getThermoSystem().clone());
  }

  /**
   * Gets the inlet stream.
   *
   * @return inlet stream
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Sets the component selected for capture.
   *
   * @param componentName component name in the thermodynamic system
   */
  public void setComponentName(String componentName) {
    if (componentName == null || componentName.trim().isEmpty()) {
      throw new IllegalArgumentException("componentName cannot be null or empty");
    }
    this.componentName = componentName;
  }

  /**
   * Gets the selected capture component.
   *
   * @return component name
   */
  public String getComponentName() {
    return componentName;
  }

  /**
   * Sets the selected-component capture fraction.
   *
   * @param captureFraction capture fraction between zero and one
   */
  public void setCaptureFraction(double captureFraction) {
    if (!Double.isFinite(captureFraction) || captureFraction < 0.0 || captureFraction > 1.0) {
      throw new IllegalArgumentException("captureFraction must be finite and between zero and one");
    }
    this.captureFraction = captureFraction;
  }

  /**
   * Gets the selected-component capture fraction.
   *
   * @return capture fraction
   */
  public double getCaptureFraction() {
    return captureFraction;
  }

  /**
   * Gets the captured component outlet stream.
   *
   * @return captured stream
   */
  public StreamInterface getCapturedStream() {
    return capturedStream;
  }

  /**
   * Gets the treated outlet stream.
   *
   * @return treated stream
   */
  public StreamInterface getTreatedStream() {
    return treatedStream;
  }

  /**
   * Gets captured selected-component mole flow.
   *
   * @return captured component mole flow in the stream basis
   */
  public double getCapturedComponentMoleFlow() {
    return capturedComponentMoleFlow;
  }

  /**
   * Gets treated selected-component mole flow.
   *
   * @return selected component mole flow remaining in treated stream
   */
  public double getTreatedComponentMoleFlow() {
    return treatedComponentMoleFlow;
  }

  /**
   * Gets the actual removal fraction from the latest run.
   *
   * @return selected-component removal fraction, or zero when no selected component is present
   */
  public double getActualCaptureFraction() {
    double total = capturedComponentMoleFlow + treatedComponentMoleFlow;
    return total > 0.0 ? capturedComponentMoleFlow / total : 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (inletStream != null) {
      streams.add(inletStream);
    }
    return streams;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (capturedStream != null) {
      streams.add(capturedStream);
    }
    if (treatedStream != null) {
      streams.add(treatedStream);
    }
    return streams;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inletStream == null) {
      throw new IllegalStateException("ComponentCaptureUnit requires an inlet stream");
    }
    SplitResult captured = createSplitSystem(true);
    SplitResult treated = createSplitSystem(false);
    capturedStream.setThermoSystem(captured.system);
    treatedStream.setThermoSystem(treated.system);
    initializeStream(capturedStream, captured.totalMoles, id);
    initializeStream(treatedStream, treated.totalMoles, id);
    capturedComponentMoleFlow = captured.selectedComponentMoles;
    treatedComponentMoleFlow = treated.selectedComponentMoles;
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = inletStream == null ? 0.0 : inletStream.getThermoSystem().getFlowRate(unit);
    double capturedFlow =
        capturedStream == null ? 0.0 : capturedStream.getThermoSystem().getFlowRate(unit);
    double treatedFlow =
        treatedStream == null ? 0.0 : treatedStream.getThermoSystem().getFlowRate(unit);
    return capturedFlow + treatedFlow - inletFlow;
  }

  /**
   * Gets a compact result map.
   *
   * @return ordered result map for reporting
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("componentName", componentName);
    results.put("captureFraction", captureFraction);
    results.put("actualCaptureFraction", getActualCaptureFraction());
    results.put("capturedComponentMoleFlow", capturedComponentMoleFlow);
    results.put("treatedComponentMoleFlow", treatedComponentMoleFlow);
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(getResults());
  }

  /**
   * Creates either the captured or treated thermodynamic system.
   *
   * @param captured true for captured stream, false for treated stream
   * @return split result containing system and flow bookkeeping
   */
  private SplitResult createSplitSystem(boolean captured) {
    SystemInterface feed = inletStream.getThermoSystem();
    SystemInterface system = feed.clone();
    system.setEmptyFluid();
    double totalMoles = 0.0;
    double selectedMoles = 0.0;
    for (int componentIndex = 0; componentIndex < feed.getNumberOfComponents(); componentIndex++) {
      String feedComponentName = feed.getComponent(componentIndex).getComponentName();
      double inletMoles = Math.max(0.0, feed.getComponent(componentIndex).getNumberOfmoles());
      double routedMoles = getRoutedMoles(feedComponentName, inletMoles, captured);
      if (routedMoles > 0.0) {
        system.addComponent(componentIndex, routedMoles);
        totalMoles += routedMoles;
        if (isSelectedComponent(feedComponentName)) {
          selectedMoles += routedMoles;
        }
      }
    }
    return new SplitResult(system, totalMoles, selectedMoles);
  }

  /**
   * Calculates routed moles for one component and one outlet.
   *
   * @param feedComponentName feed component name
   * @param inletMoles inlet component moles
   * @param captured true when routing to captured outlet
   * @return moles routed to the selected outlet
   */
  private double getRoutedMoles(String feedComponentName, double inletMoles, boolean captured) {
    if (isSelectedComponent(feedComponentName)) {
      return captured ? inletMoles * captureFraction : inletMoles * (1.0 - captureFraction);
    }
    return captured ? 0.0 : inletMoles;
  }

  /**
   * Checks whether a feed component matches the selected capture component.
   *
   * @param feedComponentName component name from feed
   * @return true if the component is selected for capture
   */
  private boolean isSelectedComponent(String feedComponentName) {
    return feedComponentName != null && feedComponentName.equalsIgnoreCase(componentName);
  }

  /**
   * Initializes a split outlet stream when it contains material.
   *
   * @param stream split outlet stream
   * @param totalMoles total moles routed to the stream
   * @param id calculation identifier
   */
  private void initializeStream(StreamInterface stream, double totalMoles, UUID id) {
    if (totalMoles > 0.0) {
      stream.getThermoSystem().init(0);
      try {
        new ThermodynamicOperations(stream.getThermoSystem()).TPflash();
      } catch (Exception ex) {
        stream.getThermoSystem().init(3);
      }
    }
    stream.run(id);
  }

  /**
   * Immutable split calculation result.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static final class SplitResult {
    /** Split thermodynamic system. */
    private final SystemInterface system;

    /** Total moles routed to this split. */
    private final double totalMoles;

    /** Moles of selected component routed to this split. */
    private final double selectedComponentMoles;

    /**
     * Creates a split result.
     *
     * @param system split thermodynamic system
     * @param totalMoles total moles routed to this split
     * @param selectedComponentMoles selected component moles routed to this split
     */
    private SplitResult(SystemInterface system, double totalMoles, double selectedComponentMoles) {
      this.system = system;
      this.totalMoles = totalMoles;
      this.selectedComponentMoles = selectedComponentMoles;
    }
  }
}
