package neqsim.process.equipment.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Transient wellbore model for simulating shutdown cooling and depressurization. Models the thermal
 * equilibration of a wellbore after injection stops, accounting for radial heat conduction to the
 * formation (geothermal gradient) and the resulting phase behaviour changes.
 *
 * <p>
 * The model divides the wellbore into vertical segments and computes the temperature profile as the
 * fluid cools toward the formation temperature at each depth. During cooling, flash calculations
 * determine if two-phase conditions develop and track impurity enrichment in the gas phase.
 * </p>
 *
 * <p>
 * Usage example:
 *
 * <pre>
 * TransientWellbore well = new TransientWellbore("InjWell", feedStream);
 * well.setWellDepth(1300.0);
 * well.setTubingDiameter(0.1571);
 * well.setFormationTemperature(277.15, 316.15); // 4C at top, 43C at bottom
 * well.setShutdownCoolingRate(5.0); // K/hr exponential decay rate
 * well.setNumberOfSegments(20);
 * well.runShutdownSimulation(24.0, 1.0); // 24 hours, 1-hour steps
 * List&lt;double[]&gt; profiles = well.getTemperatureProfiles();
 * </pre>
 *
 * @author neqsim
 * @version 1.0
 */
public class TransientWellbore extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Well measured depth in meters. */
  private double wellDepth = 1000.0;

  /** Tubing inner diameter in meters. */
  private double tubingDiameter = 0.1571;

  /** Formation temperature at wellhead (top) in Kelvin. */
  private double formationTempTop = 277.15;

  /** Formation temperature at bottom-hole in Kelvin. */
  private double formationTempBottom = 316.15;

  /** Number of vertical segments for discretization. */
  private int numberOfSegments = 20;

  /** Cooling time constant for exponential decay toward formation temperature in hours. */
  private double coolingTimeConstant = 5.0;

  /** Overall radial heat transfer coefficient in W/(m2.K). */
  private double radialHeatTransferCoeff = 25.0;

  /** Depressurization rate in bar per hour (0 = no depressurization). */
  private double depressurizationRate = 0.0;

  /**
   * Time step results: list of snapshots, each containing depth vs (T, P, phases, compositions).
   */
  private final List<TransientSnapshot> snapshots = new ArrayList<>();

  /**
   * Constructor for TransientWellbore.
   *
   * @param name the equipment name
   */
  public TransientWellbore(String name) {
    super(name);
  }

  /**
   * Constructor for TransientWellbore with inlet stream.
   *
   * @param name the equipment name
   * @param inletStream the inlet (feed) stream defining initial fluid conditions
   */
  public TransientWellbore(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * Sets the well measured depth.
   *
   * @param depth the well depth in meters
   */
  public void setWellDepth(double depth) {
    this.wellDepth = depth;
  }

  /**
   * Gets the well depth.
   *
   * @return the well depth in meters
   */
  public double getWellDepth() {
    return wellDepth;
  }

  /**
   * Sets the tubing inner diameter.
   *
   * @param diameter the inner diameter in meters
   */
  public void setTubingDiameter(double diameter) {
    this.tubingDiameter = diameter;
  }

  /**
   * Gets the tubing inner diameter.
   *
   * @return the inner diameter in meters
   */
  public double getTubingDiameter() {
    return tubingDiameter;
  }

  /**
   * Sets the formation temperature at the wellhead and bottom-hole. A linear geothermal gradient is
   * assumed between these two points.
   *
   * @param topTempKelvin formation temperature at the wellhead in Kelvin
   * @param bottomTempKelvin formation temperature at the bottom-hole in Kelvin
   */
  public void setFormationTemperature(double topTempKelvin, double bottomTempKelvin) {
    this.formationTempTop = topTempKelvin;
    this.formationTempBottom = bottomTempKelvin;
  }

  /**
   * Sets the exponential cooling time constant. The fluid temperature at each segment evolves as:
   * T(t) = T_formation + (T_initial - T_formation) * exp(-t / tau). Smaller values mean faster
   * cooling.
   *
   * @param timeConstantHours the cooling time constant in hours
   */
  public void setShutdownCoolingRate(double timeConstantHours) {
    this.coolingTimeConstant = timeConstantHours;
  }

  /**
   * Gets the cooling time constant.
   *
   * @return the cooling time constant in hours
   */
  public double getShutdownCoolingRate() {
    return coolingTimeConstant;
  }

  /**
   * Sets the radial heat transfer coefficient for heat exchange between wellbore fluid and
   * formation.
   *
   * @param coefficient the overall heat transfer coefficient in W/(m2.K)
   */
  public void setRadialHeatTransferCoefficient(double coefficient) {
    this.radialHeatTransferCoeff = coefficient;
  }

  /**
   * Gets the radial heat transfer coefficient.
   *
   * @return the coefficient in W/(m2.K)
   */
  public double getRadialHeatTransferCoefficient() {
    return radialHeatTransferCoeff;
  }

  /**
   * Sets the wellhead depressurization rate during shutdown.
   *
   * @param rateBarPerHour the pressure decrease rate in bar per hour (0 = constant pressure)
   */
  public void setDepressurizationRate(double rateBarPerHour) {
    this.depressurizationRate = rateBarPerHour;
  }

  /**
   * Gets the depressurization rate.
   *
   * @return the rate in bar per hour
   */
  public double getDepressurizationRate() {
    return depressurizationRate;
  }

  /**
   * Sets the number of vertical segments.
   *
   * @param segments the number of segments
   */
  public void setNumberOfSegments(int segments) {
    this.numberOfSegments = segments;
  }

  /**
   * Gets the number of segments.
   *
   * @return the number of segments
   */
  public int getNumberOfSegments() {
    return numberOfSegments;
  }

  /**
   * Runs the shutdown transient simulation. Starting from the initial operating conditions in the
   * inlet stream, the wellbore cools toward the formation temperature over the specified duration.
   *
   * @param totalTimeHours the total simulation duration in hours
   * @param timeStepHours the time step size in hours
   */
  public void runShutdownSimulation(double totalTimeHours, double timeStepHours) {
    snapshots.clear();

    SystemInterface baseFluid = inStream.getThermoSystem().clone();
    double initialWHP = baseFluid.getPressure();
    double segmentLength = wellDepth / numberOfSegments;

    // Calculate hydrostatic pressure profile (approximate from initial density)
    ThermodynamicOperations ops = new ThermodynamicOperations(baseFluid);
    ops.TPflash();
    baseFluid.initProperties();
    double density = baseFluid.getDensity("kg/m3");

    // Initialize temperature profile from operating conditions
    // Assume linear T profile from inlet stream T at top to BHT at bottom
    double topT = baseFluid.getTemperature();
    double geothermalGrad = (formationTempBottom - formationTempTop) / wellDepth;
    double operatingBHT = topT + geothermalGrad * wellDepth * 0.5; // approximate

    double[] initialTemps = new double[numberOfSegments + 1];
    double[] depths = new double[numberOfSegments + 1];
    double[] pressures = new double[numberOfSegments + 1];

    for (int i = 0; i <= numberOfSegments; i++) {
      double depth = i * segmentLength;
      depths[i] = depth;
      initialTemps[i] = topT + (operatingBHT - topT) * depth / wellDepth;
      pressures[i] = initialWHP + density * 9.81 * depth / 1.0e5; // approximate hydrostatic
    }

    // Record initial state
    snapshots.add(createSnapshot(0.0, depths, initialTemps, pressures, baseFluid));

    // Time-stepping loop
    double[] currentTemps = initialTemps.clone();
    for (double t = timeStepHours; t <= totalTimeHours + 1e-9; t += timeStepHours) {
      // Calculate current WHP (with depressurization)
      double currentWHP = Math.max(1.0, initialWHP - depressurizationRate * t);

      // Update each segment temperature
      double[] newTemps = new double[numberOfSegments + 1];
      double[] newPressures = new double[numberOfSegments + 1];

      for (int i = 0; i <= numberOfSegments; i++) {
        double depth = depths[i];
        double formationT = formationTempTop + geothermalGrad * depth;

        // Exponential decay toward formation temperature
        double decayFactor = Math.exp(-timeStepHours / coolingTimeConstant);
        newTemps[i] = formationT + (currentTemps[i] - formationT) * decayFactor;

        // Hydrostatic pressure at this depth
        newPressures[i] = currentWHP + density * 9.81 * depth / 1.0e5;
      }

      // Create snapshot with flash at each segment
      snapshots.add(createSnapshot(t, depths, newTemps, newPressures, baseFluid));
      currentTemps = newTemps;
    }
  }

  /**
   * Creates a snapshot of the wellbore state at a given time by performing flash calculations at
   * each segment.
   *
   * @param timeHours the simulation time in hours
   * @param depths the depth array
   * @param temperatures the temperature array in Kelvin
   * @param pressures the pressure array in bara
   * @param baseFluid the base fluid for cloning
   * @return a TransientSnapshot with all segment data
   */
  private TransientSnapshot createSnapshot(double timeHours, double[] depths, double[] temperatures,
      double[] pressures, SystemInterface baseFluid) {
    TransientSnapshot snap = new TransientSnapshot(timeHours, numberOfSegments + 1);

    for (int i = 0; i <= numberOfSegments; i++) {
      snap.depths[i] = depths[i];
      snap.temperatures[i] = temperatures[i];
      snap.pressures[i] = pressures[i];

      // Flash at each segment
      SystemInterface segFluid = baseFluid.clone();
      segFluid.setTemperature(temperatures[i]);
      segFluid.setPressure(Math.max(1.0, pressures[i]));

      ThermodynamicOperations segOps = new ThermodynamicOperations(segFluid);
      try {
        segOps.TPflash();
        segFluid.initProperties();
        snap.numberOfPhases[i] = segFluid.getNumberOfPhases();

        if (segFluid.hasPhaseType("gas") && segFluid.getNumberOfPhases() > 1) {
          int gasIdx = segFluid.getPhaseNumberOfPhase("gas");
          snap.gasFraction[i] = segFluid.getBeta(gasIdx);
          // Track light impurity composition in gas phase
          for (int c = 0; c < segFluid.getPhase("gas").getNumberOfComponents(); c++) {
            String compName = segFluid.getPhase("gas").getComponent(c).getComponentName();
            double yi = segFluid.getPhase("gas").getComponent(c).getx();
            snap.addGasComposition(i, compName, yi);
          }
        } else {
          snap.gasFraction[i] = segFluid.hasPhaseType("gas") ? 1.0 : 0.0;
        }
      } catch (Exception e) {
        snap.numberOfPhases[i] = 1;
        snap.gasFraction[i] = 0.0;
      }
    }
    return snap;
  }

  /**
   * Gets all snapshots from the shutdown simulation.
   *
   * @return a list of TransientSnapshot objects, one per time step
   */
  public List<TransientSnapshot> getSnapshots() {
    return snapshots;
  }

  /**
   * Gets the temperature profiles as a list of double arrays (one per time step).
   *
   * @return list of temperature arrays in Kelvin
   */
  public List<double[]> getTemperatureProfiles() {
    List<double[]> profiles = new ArrayList<>();
    for (TransientSnapshot snap : snapshots) {
      profiles.add(snap.temperatures.clone());
    }
    return profiles;
  }

  /**
   * Gets the time points of each snapshot.
   *
   * @return array of time values in hours
   */
  public double[] getTimePoints() {
    double[] times = new double[snapshots.size()];
    for (int i = 0; i < snapshots.size(); i++) {
      times[i] = snapshots.get(i).timeHours;
    }
    return times;
  }

  /**
   * Returns the maximum H2 concentration in the gas phase across all segments and time steps.
   *
   * @param componentName the component to check (e.g., "hydrogen")
   * @return the maximum gas phase mole fraction, or 0 if no two-phase conditions
   */
  public double getMaxGasPhaseConcentration(String componentName) {
    double maxConc = 0.0;
    for (TransientSnapshot snap : snapshots) {
      for (int i = 0; i <= numberOfSegments; i++) {
        Map<String, Double> gasComp = snap.gasCompositions.get(i);
        if (gasComp != null) {
          Double yi = gasComp.get(componentName);
          if (yi != null && yi > maxConc) {
            maxConc = yi;
          }
        }
      }
    }
    return maxConc;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Default run performs a 24-hour shutdown simulation with 1-hour steps
    runShutdownSimulation(24.0, 1.0);

    // Set outlet stream to the wellhead conditions at the final time step
    if (!snapshots.isEmpty()) {
      TransientSnapshot lastSnap = snapshots.get(snapshots.size() - 1);
      SystemInterface outFluid = inStream.getThermoSystem().clone();
      outFluid.setTemperature(lastSnap.temperatures[0]); // wellhead
      outFluid.setPressure(Math.max(1.0, lastSnap.pressures[0])); // wellhead
      ThermodynamicOperations ops = new ThermodynamicOperations(outFluid);
      ops.TPflash();
      outFluid.initProperties();
      outStream.setThermoSystem(outFluid);
      outStream.setCalculationIdentifier(id);
    }
  }

  /**
   * A snapshot of the wellbore state at a single time step. Contains depth-dependent profiles of
   * temperature, pressure, phase state, and gas phase compositions.
   */
  public static class TransientSnapshot implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** Simulation time in hours. */
    public final double timeHours;

    /** Depth array in meters (from top = 0 to bottom = wellDepth). */
    public final double[] depths;

    /** Temperature array in Kelvin at each depth. */
    public final double[] temperatures;

    /** Pressure array in bara at each depth. */
    public final double[] pressures;

    /** Number of phases at each depth. */
    public final int[] numberOfPhases;

    /** Gas phase mole fraction at each depth (0 = all liquid, 1 = all gas). */
    public final double[] gasFraction;

    /** Gas phase composition at each depth: segment index to (component name to mole fraction). */
    public final Map<Integer, Map<String, Double>> gasCompositions;

    /**
     * Constructor for TransientSnapshot.
     *
     * @param timeHours the simulation time
     * @param size the number of depth points
     */
    public TransientSnapshot(double timeHours, int size) {
      this.timeHours = timeHours;
      this.depths = new double[size];
      this.temperatures = new double[size];
      this.pressures = new double[size];
      this.numberOfPhases = new int[size];
      this.gasFraction = new double[size];
      this.gasCompositions = new LinkedHashMap<>();
    }

    /**
     * Adds gas phase composition data for a segment.
     *
     * @param segmentIndex the segment index
     * @param componentName the component name
     * @param moleFraction the mole fraction in the gas phase
     */
    public void addGasComposition(int segmentIndex, String componentName, double moleFraction) {
      Map<String, Double> compMap = gasCompositions.get(segmentIndex);
      if (compMap == null) {
        compMap = new LinkedHashMap<>();
        gasCompositions.put(segmentIndex, compMap);
      }
      compMap.put(componentName, moleFraction);
    }
  }
}
