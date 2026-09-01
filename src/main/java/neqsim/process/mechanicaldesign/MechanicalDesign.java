package neqsim.process.mechanicaldesign;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.data.DatabaseMechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.data.MechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.designstandards.AdsorptionDehydrationDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.CompressorDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.DesignStandard;
import neqsim.process.mechanicaldesign.designstandards.GasScrubberDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.JointEfficiencyPlateStandard;
import neqsim.process.mechanicaldesign.designstandards.MaterialPipeDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.MaterialPlateDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PipelineDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PressureVesselDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.StandardRegistry;
import neqsim.process.mechanicaldesign.designstandards.StandardSelection;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/**
 * MechanicalDesign class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class MechanicalDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(MechanicalDesign.class);

  /**
   * Getter for the field <code>materialPipeDesignStandard</code>.
   *
   * @return the materialPipeDesignStandard
   */
  public MaterialPipeDesignStandard getMaterialPipeDesignStandard() {
    return materialPipeDesignStandard;
  }

  /**
   * Setter for the field <code>materialPipeDesignStandard</code>.
   *
   * @param materialPipeDesignStandard the materialPipeDesignStandard to set
   */
  public void setMaterialPipeDesignStandard(MaterialPipeDesignStandard materialPipeDesignStandard) {
    this.materialPipeDesignStandard = materialPipeDesignStandard;
  }

  /**
   * getMaterialDesignStandard.
   *
   * @return the materialDesignStandard
   */
  public MaterialPlateDesignStandard getMaterialDesignStandard() {
    return materialPlateDesignStandard;
  }

  /**
   * setMaterialDesignStandard.
   *
   * @param materialDesignStandard the materialDesignStandard to set
   */
  public void setMaterialDesignStandard(MaterialPlateDesignStandard materialDesignStandard) {
    this.materialPlateDesignStandard = materialDesignStandard;
  }

  private boolean hasSetCompanySpecificDesignStandards = false;
  private double maxOperationPressure = 100.0;
  private double minOperationPressure = 1.0;
  private double maxOperationTemperature = 100.0;
  private double minOperationTemperature = 1.0;
  public double maxDesignVolumeFlow = 0.0;
  public double minDesignVolumeFLow = 0.0;
  public double maxDesignGassVolumeFlow = 0.0;
  public double minDesignGassVolumeFLow = 0.0;
  public double maxDesignOilVolumeFlow = 0.0;
  public double minDesignOilFLow = 0.0;
  public double maxDesignWaterVolumeFlow = 0.0;
  public double minDesignWaterVolumeFLow = 0.0;
  public double maxDesignPower = 0.0;
  public double minDesignPower = 0.0;
  public double maxDesignDuty = 0.0;
  public double minDesignDuty = 0.0;
  /** Maximum design Cv for valves. */
  public double maxDesignCv = 0.0;
  /** Maximum design velocity in m/s for pipes. */
  public double maxDesignVelocity = 0.0;
  /** Maximum design pressure drop in bara. */
  public double maxDesignPressureDrop = 0.0;

  public void setMaxDesignPower(double maxDesignPower) {
    this.maxDesignPower = maxDesignPower;
  }

  public void setMinDesignPower(double minDesignPower) {
    this.minDesignPower = minDesignPower;
  }

  public void setMaxDesignDuty(double maxDesignDuty) {
    this.maxDesignDuty = maxDesignDuty;
  }

  public void setMinDesignDuty(double minDesignDuty) {
    this.minDesignDuty = minDesignDuty;
  }

  /**
   * Gets the maximum design power in kW.
   *
   * @return maximum design power in kW
   */
  public double getPower() {
    return maxDesignPower;
  }

  /**
   * Gets the maximum design duty in kW.
   *
   * @return maximum design duty in kW
   */
  public double getDuty() {
    return maxDesignDuty;
  }

  /**
   * Gets the heat transfer area in m2.
   *
   * @return heat transfer area in m2
   */
  public double getHeatTransferArea() {
    // Override in heat exchanger subclass
    return 0.0;
  }

  /**
   * Sets the maximum design Cv for valves.
   *
   * @param maxDesignCv maximum Cv value
   */
  public void setMaxDesignCv(double maxDesignCv) {
    this.maxDesignCv = maxDesignCv;
  }

  /**
   * Gets the maximum design Cv for valves.
   *
   * @return maximum Cv value
   */
  public double getMaxDesignCv() {
    return maxDesignCv;
  }

  /**
   * Sets the maximum design velocity in m/s.
   *
   * @param maxDesignVelocity maximum velocity in m/s
   */
  public void setMaxDesignVelocity(double maxDesignVelocity) {
    this.maxDesignVelocity = maxDesignVelocity;
  }

  /**
   * Gets the maximum design velocity in m/s.
   *
   * @return maximum velocity in m/s
   */
  public double getMaxDesignVelocity() {
    return maxDesignVelocity;
  }

  /**
   * Sets the maximum design pressure drop in bara.
   *
   * @param maxDesignPressureDrop maximum pressure drop in bara
   */
  public void setMaxDesignPressureDrop(double maxDesignPressureDrop) {
    this.maxDesignPressureDrop = maxDesignPressureDrop;
  }

  /**
   * Gets the maximum design pressure drop in bara.
   *
   * @return maximum pressure drop in bara
   */
  public double getMaxDesignPressureDrop() {
    return maxDesignPressureDrop;
  }

  // ============================================================
  // Design-limit utilization (universal "via mechanical design")
  // ============================================================

  /**
   * Builds capacity constraints from the mechanical-design limits that have been explicitly set on this design.
   *
   * <p>
   * A constraint is created for every {@code maxDesign*} limit that has been set to a positive value <em>and</em> for
   * which a finite current operating value is currently available. Each constraint uses
   * {@code dataSource = "mechanicalDesign"}, constraint type {@link CapacityConstraint.ConstraintType#DESIGN}, a
   * warning threshold of 0.9, and a live value supplier so the utilization tracks the simulation. This is the single,
   * equipment-agnostic place that maps mechanical-design limits to capacity utilization.
   * </p>
   *
   * <p>
   * The following metrics are derived universally from the attached equipment's inlet/outlet streams and therefore work
   * for every equipment type:
   * </p>
   * <ul>
   * <li>total inlet volumetric flow vs {@code maxDesignVolumeFlow} (actual m3/hr)</li>
   * <li>pressure drop |Pin - Pout| vs {@code maxDesignPressureDrop} (bara)</li>
   * </ul>
   *
   * <p>
   * The remaining metrics (phase volumetric flows, power, duty, Cv, velocity) are read through the protected
   * {@code getOperating*} hooks. These return {@link Double#NaN} by default and are overridden by equipment-specific
   * mechanical-design subclasses (e.g. compressor power, pipe velocity) so that the matching unit convention is used.
   * </p>
   *
   * @return a list of capacity constraints (possibly empty, never {@code null})
   */
  public List<CapacityConstraint> getDesignCapacityConstraints() {
    List<CapacityConstraint> constraints = new ArrayList<CapacityConstraint>();
    addDesignConstraint(constraints, "design volume flow", "m3/hr", maxDesignVolumeFlow, getOperatingVolumeFlow(),
        new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return getOperatingVolumeFlow();
          }
        });
    addDesignConstraint(constraints, "design gas volume flow", "m3/hr", maxDesignGassVolumeFlow,
        getOperatingGasVolumeFlow(), new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return getOperatingGasVolumeFlow();
          }
        });
    addDesignConstraint(constraints, "design oil volume flow", "m3/hr", maxDesignOilVolumeFlow,
        getOperatingOilVolumeFlow(), new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return getOperatingOilVolumeFlow();
          }
        });
    addDesignConstraint(constraints, "design water volume flow", "m3/hr", maxDesignWaterVolumeFlow,
        getOperatingWaterVolumeFlow(), new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return getOperatingWaterVolumeFlow();
          }
        });
    addDesignConstraint(constraints, "design power", "kW", maxDesignPower, getOperatingPower(), new DoubleSupplier() {
      @Override
      public double getAsDouble() {
        return getOperatingPower();
      }
    });
    addDesignConstraint(constraints, "design duty", "kW", maxDesignDuty, getOperatingDuty(), new DoubleSupplier() {
      @Override
      public double getAsDouble() {
        return getOperatingDuty();
      }
    });
    addDesignConstraint(constraints, "design Cv", "", maxDesignCv, getOperatingCv(), new DoubleSupplier() {
      @Override
      public double getAsDouble() {
        return getOperatingCv();
      }
    });
    addDesignConstraint(constraints, "design velocity", "m/s", maxDesignVelocity, getOperatingVelocity(),
        new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return getOperatingVelocity();
          }
        });
    addDesignConstraint(constraints, "design pressure drop", "bara", maxDesignPressureDrop, getOperatingPressureDrop(),
        new DoubleSupplier() {
          @Override
          public double getAsDouble() {
            return getOperatingPressureDrop();
          }
        });
    return constraints;
  }

  /**
   * Adds a single design-derived capacity constraint when the limit is set and the current value is finite.
   *
   * @param constraints the list to add to
   * @param name the constraint name (e.g. "design power")
   * @param unit the unit of measurement (e.g. "kW")
   * @param designLimit the design limit value; only used when greater than zero
   * @param currentValue the current operating value evaluated once for the set/finite check
   * @param supplier a live supplier used to track the current value during simulation
   */
  private void addDesignConstraint(List<CapacityConstraint> constraints, String name, String unit, double designLimit,
      double currentValue, DoubleSupplier supplier) {
    if (designLimit > 0.0 && Double.isFinite(currentValue)) {
      CapacityConstraint constraint = new CapacityConstraint(name, unit, CapacityConstraint.ConstraintType.DESIGN)
          .setDesignValue(designLimit).setWarningThreshold(0.9).setDataSource("mechanicalDesign")
          .setDescription("Derived from mechanical design limit").setValueSupplier(supplier)
          .setCurrentValue(currentValue);
      constraint.setEnabled(true);
      constraints.add(constraint);
    }
  }

  /**
   * Computes equipment utilization from the explicitly set mechanical-design limits.
   *
   * <p>
   * Utilization is the current operating value divided by the design limit (1.0 == 100% of design). Only metrics with a
   * positive design limit and a finite current operating value are returned. See
   * {@link #getDesignCapacityConstraints()} for the set of supported metrics.
   * </p>
   *
   * @return a map from metric name to utilization fraction (possibly empty, never {@code null})
   */
  public Map<String, Double> getDesignUtilization() {
    Map<String, Double> utilization = new LinkedHashMap<String, Double>();
    for (CapacityConstraint constraint : getDesignCapacityConstraints()) {
      double value = constraint.getUtilization();
      if (!Double.isNaN(value)) {
        utilization.put(constraint.getName(), value);
      }
    }
    return utilization;
  }

  /**
   * Returns the highest utilization across all mechanical-design-derived metrics.
   *
   * @return the maximum utilization fraction (1.0 == 100% of design), or 0.0 when no design limit is set or no
   * operating value is available
   */
  public double getMaxDesignUtilization() {
    double max = 0.0;
    for (Double value : getDesignUtilization().values()) {
      if (value != null && !Double.isNaN(value) && value > max) {
        max = value;
      }
    }
    return max;
  }

  /**
   * Returns the pressure drop across the attached equipment in bara.
   *
   * <p>
   * Computed universally as |Pin - Pout| from the first inlet and first outlet stream. Returns {@link Double#NaN} when
   * the equipment, an inlet, or an outlet stream is unavailable.
   * </p>
   *
   * @return pressure drop in bara, or {@link Double#NaN} if it cannot be determined
   */
  protected double getOperatingPressureDrop() {
    if (processEquipment == null) {
      return Double.NaN;
    }
    try {
      List<StreamInterface> inlets = processEquipment.getInletStreams();
      List<StreamInterface> outlets = processEquipment.getOutletStreams();
      if (inlets.isEmpty() || outlets.isEmpty()) {
        return Double.NaN;
      }
      double pin = inlets.get(0).getPressure("bara");
      double pout = outlets.get(0).getPressure("bara");
      if (Double.isNaN(pin) || Double.isNaN(pout)) {
        return Double.NaN;
      }
      return Math.abs(pin - pout);
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Returns the total actual inlet volumetric flow in m3/hr.
   *
   * <p>
   * Computed universally as the sum of the actual volumetric flow of all inlet streams. Returns {@link Double#NaN} when
   * the equipment has no inlet streams or the flow cannot be determined.
   * </p>
   *
   * @return total inlet volumetric flow in m3/hr, or {@link Double#NaN} if it cannot be determined
   */
  protected double getOperatingVolumeFlow() {
    if (processEquipment == null) {
      return Double.NaN;
    }
    try {
      List<StreamInterface> inlets = processEquipment.getInletStreams();
      if (inlets.isEmpty()) {
        return Double.NaN;
      }
      double total = 0.0;
      boolean any = false;
      for (StreamInterface stream : inlets) {
        double flow = stream.getFlowRate("m3/hr");
        if (!Double.isNaN(flow)) {
          total += flow;
          any = true;
        }
      }
      return any ? total : Double.NaN;
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Returns the actual gas-phase volumetric flow in m3/hr.
   *
   * <p>
   * Default implementation returns {@link Double#NaN}. Equipment-specific mechanical-design subclasses (e.g.
   * separators) override this to supply the gas volumetric flow in the unit that matches
   * {@code maxDesignGassVolumeFlow}.
   * </p>
   *
   * @return gas volumetric flow in m3/hr, or {@link Double#NaN} if not applicable
   */
  protected double getOperatingGasVolumeFlow() {
    return Double.NaN;
  }

  /**
   * Returns the actual oil-phase volumetric flow in m3/hr.
   *
   * <p>
   * Default implementation returns {@link Double#NaN}. Override in equipment-specific subclasses.
   * </p>
   *
   * @return oil volumetric flow in m3/hr, or {@link Double#NaN} if not applicable
   */
  protected double getOperatingOilVolumeFlow() {
    return Double.NaN;
  }

  /**
   * Returns the actual water-phase volumetric flow in m3/hr.
   *
   * <p>
   * Default implementation returns {@link Double#NaN}. Override in equipment-specific subclasses.
   * </p>
   *
   * @return water volumetric flow in m3/hr, or {@link Double#NaN} if not applicable
   */
  protected double getOperatingWaterVolumeFlow() {
    return Double.NaN;
  }

  /**
   * Returns the operating shaft power in kW.
   *
   * <p>
   * Default implementation returns {@link Double#NaN}. Equipment-specific mechanical-design subclasses for rotating
   * equipment (compressors, pumps, expanders) override this to supply the operating power.
   * </p>
   *
   * @return operating power in kW, or {@link Double#NaN} if not applicable
   */
  protected double getOperatingPower() {
    return Double.NaN;
  }

  /**
   * Returns the operating thermal duty in kW.
   *
   * <p>
   * Default implementation returns {@link Double#NaN}. Equipment-specific mechanical-design subclasses for thermal
   * equipment (heaters, coolers, heat exchangers) override this to supply the operating duty.
   * </p>
   *
   * @return operating duty in kW, or {@link Double#NaN} if not applicable
   */
  protected double getOperatingDuty() {
    return Double.NaN;
  }

  /**
   * Returns the operating valve flow coefficient (Cv).
   *
   * <p>
   * Default implementation returns {@link Double#NaN}. Valve mechanical-design subclasses override this to supply the
   * operating Cv.
   * </p>
   *
   * @return operating Cv, or {@link Double#NaN} if not applicable
   */
  protected double getOperatingCv() {
    return Double.NaN;
  }

  /**
   * Returns the operating fluid velocity in m/s.
   *
   * <p>
   * Default implementation returns {@link Double#NaN}. Pipe and pipeline mechanical-design subclasses override this to
   * supply the operating velocity.
   * </p>
   *
   * @return operating velocity in m/s, or {@link Double#NaN} if not applicable
   */
  protected double getOperatingVelocity() {
    return Double.NaN;
  }

  private String companySpecificDesignStandards = "Statoil";
  private ProcessEquipmentInterface processEquipment = null;
  // private String pressureVesselDesignStandard = "ASME - Pressure Vessel Code";
  // private String pipingDesignStandard="Statoil";
  // private String valveDesignStandard="Statoil";
  private double tensileStrength = 483; // MPa
  private double jointEfficiency = 1.0; // fully radiographed
  private MaterialPlateDesignStandard materialPlateDesignStandard = new MaterialPlateDesignStandard();
  private MaterialPipeDesignStandard materialPipeDesignStandard = new MaterialPipeDesignStandard();
  private String construtionMaterial = "steel";
  private double corrosionAllowance = 0.0; // mm
  private double pressureMarginFactor = 0.1;
  private DesignLimitData designLimitData = DesignLimitData.EMPTY;
  private MechanicalDesignMarginResult lastMarginResult = MechanicalDesignMarginResult.EMPTY;
  private transient List<MechanicalDesignDataSource> designDataSources = new ArrayList<>();
  public double innerDiameter = 0.0;
  public double outerDiameter = 0.0;
  /** Wall thickness in mm. */
  public double wallThickness = 0.0;
  public double tantanLength = 0.0; // tantan is same as seamtoseam length
  private double weightTotal = 0.0;
  private double volumeTotal = 2.0;
  public double weigthInternals = 0.0;
  public double weightNozzle = 0.0;
  public double weightPiping = 0.0;
  public double weightElectroInstrument = 0.0;
  public double weightStructualSteel = 0.0;
  public double weightVessel = 0.0;
  public double weigthVesselShell = 0.0;
  public double moduleHeight = 0.0;
  public double moduleWidth = 0;
  public double moduleLength = 0.0;
  public Hashtable<String, DesignStandard> designStandard = new Hashtable<String, DesignStandard>();
  public UnitCostEstimateBaseClass costEstimate = null;
  double defaultLiquidDensity = 1000.0;
  double defaultLiquidViscosity = 0.001012;

  /**
   * Constructor for MechanicalDesign.
   *
   * @param processEquipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public MechanicalDesign(ProcessEquipmentInterface processEquipment) {
    this.processEquipment = processEquipment;
    costEstimate = new UnitCostEstimateBaseClass(this);
    initMechanicalDesign();
  }

  /** Initialize design data using configured data sources. */
  public void initMechanicalDesign() {
    loadDesignLimits();
  }

  private void loadDesignLimits() {
    String equipmentType = resolveEquipmentType();
    if (equipmentType.isEmpty()) {
      designLimitData = DesignLimitData.EMPTY;
      return;
    }

    String companyIdentifier = Objects.toString(companySpecificDesignStandards, "");
    DesignLimitData loadedData = null;
    for (MechanicalDesignDataSource dataSource : getActiveDesignDataSources()) {
      Optional<DesignLimitData> candidate = dataSource.getDesignLimits(equipmentType, companyIdentifier);
      if (candidate.isPresent()) {
        loadedData = candidate.get();
        break;
      }
    }

    if (loadedData == null) {
      designLimitData = DesignLimitData.EMPTY;
      return;
    }

    designLimitData = loadedData;

    if (!Double.isNaN(loadedData.getCorrosionAllowance())) {
      corrosionAllowance = loadedData.getCorrosionAllowance();
    }
    if (!Double.isNaN(loadedData.getJointEfficiency())) {
      jointEfficiency = loadedData.getJointEfficiency();
    }
  }

  private List<MechanicalDesignDataSource> getActiveDesignDataSources() {
    if (designDataSources.isEmpty()) {
      List<MechanicalDesignDataSource> defaults = new ArrayList<>();
      defaults.add(new DatabaseMechanicalDesignDataSource());
      return defaults;
    }
    return new ArrayList<>(designDataSources);
  }

  private String resolveEquipmentType() {
    if (processEquipment == null) {
      return "";
    }
    return processEquipment.getClass().getSimpleName();
  }

  /**
   * Configure a single data source to load design limits from.
   *
   * @param dataSource data source to use, {@code null} clears existing sources.
   */
  public void setDesignDataSource(MechanicalDesignDataSource dataSource) {
    if (dataSource == null) {
      designDataSources = new ArrayList<>();
    } else {
      designDataSources = new ArrayList<>();
      designDataSources.add(dataSource);
    }
    initMechanicalDesign();
  }

  /**
   * Configure the list of data sources to use when loading design limits.
   *
   * @param dataSources ordered list of data sources.
   */
  public void setDesignDataSources(List<MechanicalDesignDataSource> dataSources) {
    if (dataSources == null) {
      designDataSources = new ArrayList<>();
    } else {
      designDataSources = new ArrayList<>(dataSources);
    }
    initMechanicalDesign();
  }

  /**
   * Add an additional data source used when loading design limits.
   *
   * @param dataSource the data source to add
   */
  public void addDesignDataSource(MechanicalDesignDataSource dataSource) {
    if (dataSource == null) {
      return;
    }
    designDataSources.add(dataSource);
    initMechanicalDesign();
  }

  /**
   * Get the immutable list of configured data sources.
   *
   * @return list of data sources.
   */
  public List<MechanicalDesignDataSource> getDesignDataSources() {
    return Collections.unmodifiableList(designDataSources);
  }

  public DesignLimitData getDesignLimitData() {
    return designLimitData;
  }

  public double getDesignMaxPressureLimit() {
    return designLimitData.getMaxPressure();
  }

  /**
   * Gets the maximum pressure limit in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted maximum pressure limit
   */
  public double getDesignMaxPressureLimit(String unit) {
    return new PressureUnit(getDesignMaxPressureLimit(), "bara").getValue(unit);
  }

  public double getDesignMinPressureLimit() {
    return designLimitData.getMinPressure();
  }

  /**
   * Gets the minimum pressure limit in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted minimum pressure limit
   */
  public double getDesignMinPressureLimit(String unit) {
    return new PressureUnit(getDesignMinPressureLimit(), "bara").getValue(unit);
  }

  public double getDesignMaxTemperatureLimit() {
    return designLimitData.getMaxTemperature();
  }

  /**
   * Gets the maximum temperature limit in an explicit temperature unit.
   *
   * @param unit requested temperature unit
   * @return converted maximum temperature limit
   */
  public double getDesignMaxTemperatureLimit(String unit) {
    return new TemperatureUnit(getDesignMaxTemperatureLimit(), "K").getValue(unit);
  }

  public double getDesignMinTemperatureLimit() {
    return designLimitData.getMinTemperature();
  }

  /**
   * Gets the minimum temperature limit in an explicit temperature unit.
   *
   * @param unit requested temperature unit
   * @return converted minimum temperature limit
   */
  public double getDesignMinTemperatureLimit(String unit) {
    return new TemperatureUnit(getDesignMinTemperatureLimit(), "K").getValue(unit);
  }

  public double getDesignCorrosionAllowance() {
    return designLimitData.getCorrosionAllowance();
  }

  public double getDesignJointEfficiency() {
    return designLimitData.getJointEfficiency();
  }

  /**
   * Validate the current operating envelope against design limits.
   *
   * @return computed margin result.
   */
  public MechanicalDesignMarginResult validateOperatingEnvelope() {
    return validateOperatingEnvelope(maxOperationPressure, minOperationPressure, maxOperationTemperature,
        minOperationTemperature, corrosionAllowance, jointEfficiency);
  }

  /**
   * Validate a specific operating envelope against design limits.
   *
   * @param operatingMaxPressure maximum operating pressure.
   * @param operatingMinPressure minimum operating pressure.
   * @param operatingMaxTemperature maximum operating temperature.
   * @param operatingMinTemperature minimum operating temperature.
   * @param operatingCorrosionAllowance corrosion allowance used in operation.
   * @param operatingJointEfficiency joint efficiency achieved in operation.
   * @return computed margin result.
   */
  public MechanicalDesignMarginResult validateOperatingEnvelope(double operatingMaxPressure,
      double operatingMinPressure, double operatingMaxTemperature, double operatingMinTemperature,
      double operatingCorrosionAllowance, double operatingJointEfficiency) {
    double maxPressureMargin = marginToUpperLimit(designLimitData.getMaxPressure(), operatingMaxPressure);
    double minPressureMargin = marginFromLowerLimit(operatingMinPressure, designLimitData.getMinPressure());
    double maxTemperatureMargin = marginToUpperLimit(designLimitData.getMaxTemperature(), operatingMaxTemperature);
    double minTemperatureMargin = marginFromLowerLimit(operatingMinTemperature, designLimitData.getMinTemperature());
    double corrosionMargin = marginFromLowerLimit(operatingCorrosionAllowance, designLimitData.getCorrosionAllowance());
    double jointMargin = marginFromLowerLimit(operatingJointEfficiency, designLimitData.getJointEfficiency());

    lastMarginResult = new MechanicalDesignMarginResult(maxPressureMargin, minPressureMargin, maxTemperatureMargin,
        minTemperatureMargin, corrosionMargin, jointMargin);
    return lastMarginResult;
  }

  public MechanicalDesignMarginResult getLastMarginResult() {
    return lastMarginResult;
  }

  private double marginToUpperLimit(double limit, double value) {
    if (Double.isNaN(limit) || Double.isNaN(value)) {
      return Double.NaN;
    }
    return limit - value;
  }

  private double marginFromLowerLimit(double value, double limit) {
    if (Double.isNaN(limit) || Double.isNaN(value)) {
      return Double.NaN;
    }
    return value - limit;
  }

  /**
   * Getter for the field <code>maxOperationPressure</code>.
   *
   * @return maximum operating pressure in bara
   */
  public double getMaxOperationPressure() {
    return maxOperationPressure;
  }

  /**
   * Gets the maximum operating pressure in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted maximum operating pressure
   */
  public double getMaxOperationPressure(String unit) {
    return new PressureUnit(maxOperationPressure, "bara").getValue(unit);
  }

  /**
   * getMaxDesignPressure.
   *
   * @return a double
   */
  public double getMaxDesignPressure() {
    return getMaxOperationPressure() * (1.0 + pressureMarginFactor);
  }

  /**
   * Gets the maximum design pressure in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted maximum design pressure
   */
  public double getMaxDesignPressure(String unit) {
    return new PressureUnit(getMaxDesignPressure(), "bara").getValue(unit);
  }

  /**
   * getMinDesignPressure.
   *
   * @return a double
   */
  public double getMinDesignPressure() {
    return getMinOperationPressure() * (1.0 - pressureMarginFactor);
  }

  /**
   * Gets the minimum design pressure in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted minimum design pressure
   */
  public double getMinDesignPressure(String unit) {
    return new PressureUnit(getMinDesignPressure(), "bara").getValue(unit);
  }

  /**
   * readDesignSpecifications.
   */
  public void readDesignSpecifications() {
  }

  /**
   * Setter for the field <code>maxOperationPressure</code>.
   *
   * @param maxPressure maximum operating pressure in bara
   */
  public void setMaxOperationPressure(double maxPressure) {
    this.maxOperationPressure = maxPressure;
  }

  /**
   * Sets the maximum operating pressure using an explicit absolute or gauge pressure unit.
   *
   * @param maxPressure maximum operating pressure
   * @param unit pressure unit supported by {@link PressureUnit}
   */
  public void setMaxOperationPressure(double maxPressure, String unit) {
    this.maxOperationPressure = new PressureUnit(maxPressure, unit).getValue("bara");
  }

  /**
   * Getter for the field <code>minOperationPressure</code>.
   *
   * @return minimum operating pressure in bara
   */
  public double getMinOperationPressure() {
    return minOperationPressure;
  }

  /**
   * Gets the minimum operating pressure in an explicit pressure unit.
   *
   * @param unit requested pressure unit
   * @return converted minimum operating pressure
   */
  public double getMinOperationPressure(String unit) {
    return new PressureUnit(minOperationPressure, "bara").getValue(unit);
  }

  /**
   * Setter for the field <code>minOperationPressure</code>.
   *
   * @param minPressure minimum operating pressure in bara
   */
  public void setMinOperationPressure(double minPressure) {
    this.minOperationPressure = minPressure;
  }

  /**
   * Sets the minimum operating pressure using an explicit absolute or gauge pressure unit.
   *
   * @param minPressure minimum operating pressure
   * @param unit pressure unit supported by {@link PressureUnit}
   */
  public void setMinOperationPressure(double minPressure, String unit) {
    this.minOperationPressure = new PressureUnit(minPressure, unit).getValue("bara");
  }

  /**
   * Getter for the field <code>maxOperationTemperature</code>.
   *
   * @return maximum operating temperature in kelvin
   */
  public double getMaxOperationTemperature() {
    return maxOperationTemperature;
  }

  /**
   * Gets the maximum operating temperature in an explicit temperature unit.
   *
   * @param unit requested temperature unit
   * @return converted maximum operating temperature
   */
  public double getMaxOperationTemperature(String unit) {
    return new TemperatureUnit(maxOperationTemperature, "K").getValue(unit);
  }

  /**
   * Setter for the field <code>maxOperationTemperature</code>.
   *
   * @param maxTemperature maximum operating temperature in kelvin
   */
  public void setMaxOperationTemperature(double maxTemperature) {
    this.maxOperationTemperature = maxTemperature;
  }

  /**
   * Sets the maximum operating temperature using an explicit temperature unit.
   *
   * @param maxTemperature maximum operating temperature
   * @param unit temperature unit supported by {@link TemperatureUnit}
   */
  public void setMaxOperationTemperature(double maxTemperature, String unit) {
    this.maxOperationTemperature = new TemperatureUnit(maxTemperature, unit).getValue("K");
  }

  /**
   * Getter for the field <code>minOperationTemperature</code>.
   *
   * @return minimum operating temperature in kelvin
   */
  public double getMinOperationTemperature() {
    return minOperationTemperature;
  }

  /**
   * Gets the minimum operating temperature in an explicit temperature unit.
   *
   * @param unit requested temperature unit
   * @return converted minimum operating temperature
   */
  public double getMinOperationTemperature(String unit) {
    return new TemperatureUnit(minOperationTemperature, "K").getValue(unit);
  }

  /**
   * Setter for the field <code>minOperationTemperature</code>.
   *
   * @param minTemperature minimum operating temperature in kelvin
   */
  public void setMinOperationTemperature(double minTemperature) {
    this.minOperationTemperature = minTemperature;
  }

  /**
   * Sets the minimum operating temperature using an explicit temperature unit.
   *
   * @param minTemperature minimum operating temperature
   * @param unit temperature unit supported by {@link TemperatureUnit}
   */
  public void setMinOperationTemperature(double minTemperature, String unit) {
    this.minOperationTemperature = new TemperatureUnit(minTemperature, unit).getValue("K");
  }

  /**
   * Getter for the field <code>processEquipment</code>.
   *
   * @return the processEquipment
   */
  public ProcessEquipmentInterface getProcessEquipment() {
    return processEquipment;
  }

  /**
   * Setter for the field <code>processEquipment</code>.
   *
   * @param processEquipment the processEquipment to set
   */
  public void setProcessEquipment(ProcessEquipmentInterface processEquipment) {
    this.processEquipment = processEquipment;
  }

  /**
   * calcDesign.
   */
  public void calcDesign() {
    // System.out.println("reading design parameters for: " + processEquipment.getName());
    if (!hasSetCompanySpecificDesignStandards) {
      setCompanySpecificDesignStandards("default");
    }
    readDesignSpecifications();
  }

  /**
   * setDesign.
   */
  public void setDesign() {
    // System.out.println("reading design parameters for: " + processEquipment.getName());
    readDesignSpecifications();
  }

  /**
   * Getter for the field <code>tensileStrength</code>.
   *
   * @return the tensileStrength
   */
  public double getTensileStrength() {
    return tensileStrength;
  }

  /**
   * Setter for the field <code>tensileStrength</code>.
   *
   * @param tensileStrength the tensileStrength to set
   */
  public void setTensileStrength(double tensileStrength) {
    this.tensileStrength = tensileStrength;
  }

  /**
   * Getter for the field <code>construtionMaterial</code>.
   *
   * @return the construtionMaterial
   */
  public String getConstrutionMaterial() {
    return construtionMaterial;
  }

  /**
   * Setter for the field <code>construtionMaterial</code>.
   *
   * @param construtionMaterial the construtionMaterial to set
   */
  public void setConstrutionMaterial(String construtionMaterial) {
    this.construtionMaterial = construtionMaterial;
  }

  /**
   * Getter for the field <code>jointEfficiency</code>.
   *
   * @return the jointEfficiency
   */
  public double getJointEfficiency() {
    return jointEfficiency;
  }

  /**
   * getMaxAllowableStress.
   *
   * @return a double
   */
  public double getMaxAllowableStress() {
    return tensileStrength / 3.5;
  }

  /**
   * Setter for the field <code>jointEfficiency</code>.
   *
   * @param jointEfficiency the jointEfficiency to set
   */
  public void setJointEfficiency(double jointEfficiency) {
    this.jointEfficiency = jointEfficiency;
  }

  /**
   * Getter for the field <code>corrosionAllowance</code>.
   *
   * @return the corrosionAllowance
   */
  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  /**
   * Setter for the field <code>corrosionAllowance</code>.
   *
   * @param corrosionAllowance the corrosionAllowance to set
   */
  public void setCorrosionAllowance(double corrosionAllowance) {
    this.corrosionAllowance = corrosionAllowance;
  }

  /**
   * Getter for the field <code>pressureMarginFactor</code>.
   *
   * @return the pressureMarginFactor
   */
  public double getPressureMarginFactor() {
    return pressureMarginFactor;
  }

  /**
   * Setter for the field <code>pressureMarginFactor</code>.
   *
   * @param pressureMarginFactor the pressureMarginFactor to set
   */
  public void setPressureMarginFactor(double pressureMarginFactor) {
    this.pressureMarginFactor = pressureMarginFactor;
  }

  /**
   * Getter for the field <code>outerDiameter</code>.
   *
   * @return a double
   */
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /**
   * Getter for the field <code>companySpecificDesignStandards</code>.
   *
   * @return the companySpecificDesignStandards
   */
  public String getCompanySpecificDesignStandards() {
    return companySpecificDesignStandards;
  }

  /**
   * Setter for the field <code>companySpecificDesignStandards</code>.
   *
   * @param companySpecificDesignStandards the companySpecificDesignStandards to set
   */
  public void setCompanySpecificDesignStandards(String companySpecificDesignStandards) {
    this.companySpecificDesignStandards = companySpecificDesignStandards == null ? "" : companySpecificDesignStandards;

    if (this.companySpecificDesignStandards.equals("StatoilTR")) {
      getDesignStandard().put("pressure vessel design code",
          new PressureVesselDesignStandard("ASME - Pressure Vessel Code", this));
      getDesignStandard().put("separator process design", new SeparatorDesignStandard("StatoilTR", this));
      getDesignStandard().put("gas scrubber process design", new GasScrubberDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("adsorption dehydration process design",
          new AdsorptionDehydrationDesignStandard("", this));
      getDesignStandard().put("pipeline design codes", new PipelineDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("compressor design codes", new CompressorDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("material plate design codes", new MaterialPlateDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("plate Joint Efficiency design codes",
          new JointEfficiencyPlateStandard("Statoil_TR1414", this));
      getDesignStandard().put("material pipe design codes", new MaterialPipeDesignStandard("Statoil_TR1414", this));

      // pressureVesselDesignStandard = "ASME - Pressure Vessel Code";
      // setPipingDesignStandard("TR1945_Statoil");
      // setValveDesignStandard("TR1903_Statoil");
    } else {
      logger.debug("using default mechanical design standards...no design standard {}",
          this.companySpecificDesignStandards);
      getDesignStandard().put("pressure vessel design code",
          new PressureVesselDesignStandard("ASME - Pressure Vessel Code", this));
      getDesignStandard().put("separator process design", new SeparatorDesignStandard("StatoilTR", this));
      getDesignStandard().put("gas scrubber process design", new GasScrubberDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("adsorption dehydration process design",
          new AdsorptionDehydrationDesignStandard("", this));
      getDesignStandard().put("pipeline design codes", new PipelineDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("compressor design codes", new CompressorDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("material plate design codes", new MaterialPlateDesignStandard("Statoil_TR1414", this));
      getDesignStandard().put("plate Joint Efficiency design codes",
          new JointEfficiencyPlateStandard("Statoil_TR1414", this));
      getDesignStandard().put("material pipe design codes", new MaterialPipeDesignStandard("Statoil_TR1414", this));
    }
    hasSetCompanySpecificDesignStandards = true;
    initMechanicalDesign();
  }

  /**
   * Set a design standard using an international standard type.
   *
   * <p>
   * This method allows setting design standards based on international standards like NORSOK, ASME, API, DNV, ISO,
   * ASTM, etc. The standard is placed in the appropriate category based on its type.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * equipment.getMechanicalDesign().setDesignStandard(StandardType.ASME_VIII_DIV1);
   * equipment.getMechanicalDesign().setDesignStandard(StandardType.NORSOK_P_001);
   * </pre>
   *
   * @param standardType the international standard type to use
   * @throws IllegalArgumentException if standardType is null
   */
  public void setDesignStandard(StandardType standardType) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    DesignStandard standard = StandardRegistry.createStandard(standardType, this);
    String category = standardType.getDesignStandardCategory();
    getDesignStandard().put(category, standard);
    hasSetCompanySpecificDesignStandards = true;
  }

  /**
   * Set a design standard using an international standard type with a specific version.
   *
   * @param standardType the international standard type to use
   * @param version the specific version of the standard (e.g., "2021", "Rev 6")
   * @throws IllegalArgumentException if standardType is null
   */
  public void setDesignStandard(StandardType standardType, String version) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    DesignStandard standard = StandardRegistry.createStandard(standardType, version, this);
    String category = standardType.getDesignStandardCategory();
    getDesignStandard().put(category, standard);
    hasSetCompanySpecificDesignStandards = true;
  }

  /**
   * Set a design standard from an explicit typed selection.
   *
   * <p>
   * Use {@link StandardSelection#strict(StandardType)} for fail-closed selection. The existing
   * {@link #setDesignStandard(StandardType)} overload remains legacy compatible.
   * </p>
   *
   * @param selection typed edition and selection behavior
   * @throws neqsim.process.mechanicaldesign.designstandards.StandardSelectionException if a strict selection cannot be
   * honored
   */
  public void setDesignStandard(StandardSelection selection) {
    DesignStandard standard = StandardRegistry.createStandard(selection, this);
    String category = selection.getStandardType().getDesignStandardCategory();
    getDesignStandard().put(category, standard);
    hasSetCompanySpecificDesignStandards = true;
  }

  /**
   * Set multiple design standards using international standard types.
   *
   * <p>
   * Each standard is placed in its appropriate category. If multiple standards have the same category, the last one in
   * the list takes precedence.
   * </p>
   *
   * @param standardTypes the list of standard types to apply
   */
  public void setDesignStandards(List<StandardType> standardTypes) {
    if (standardTypes == null) {
      return;
    }
    for (StandardType type : standardTypes) {
      if (type != null) {
        setDesignStandard(type);
      }
    }
  }

  /**
   * Get a list of applicable international standards for this equipment type.
   *
   * @return list of applicable StandardType values
   */
  public List<StandardType> getApplicableStandards() {
    String equipmentType = resolveEquipmentType();
    return StandardRegistry.getApplicableStandards(equipmentType);
  }

  /**
   * Get recommended standards for this equipment type organized by category.
   *
   * @return map of category name to list of applicable standards
   */
  public java.util.Map<String, List<StandardType>> getRecommendedStandards() {
    String equipmentType = resolveEquipmentType();
    return StandardRegistry.getRecommendedStandards(equipmentType);
  }

  /**
   * Set a design standard for a specific category.
   *
   * <p>
   * This method allows direct assignment of a DesignStandard instance to a specific design category. It is used
   * internally by the TorgManager and can be used for custom standard configurations.
   * </p>
   *
   * @param category the design category (e.g., "pressure vessel design code")
   * @param standard the DesignStandard instance to set
   */
  public void setDesignStandard(String category, DesignStandard standard) {
    if (category == null || category.isEmpty()) {
      throw new IllegalArgumentException("category cannot be null or empty");
    }
    if (standard == null) {
      throw new IllegalArgumentException("standard cannot be null");
    }
    getDesignStandard().put(category, standard);
    hasSetCompanySpecificDesignStandards = true;
  }

  /**
   * Getter for the field <code>innerDiameter</code>.
   *
   * @return the innerDiameter
   */
  public double getInnerDiameter() {
    return innerDiameter;
  }

  /**
   * Setter for the field <code>innerDiameter</code>.
   *
   * @param innerDiameter the innerDiameter to set
   */
  public void setInnerDiameter(double innerDiameter) {
    this.innerDiameter = innerDiameter;
  }

  /**
   * Setter for the field <code>outerDiameter</code>.
   *
   * @param outerDiameter the outerDiameter to set
   */
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }

  /**
   * Getter for the field <code>wallThickness</code>.
   *
   * @return the wallThickness
   */
  public double getWallThickness() {
    return wallThickness;
  }

  /**
   * Setter for the field <code>wallThickness</code>.
   *
   * @param wallThickness the wallThickness to set
   */
  public void setWallThickness(double wallThickness) {
    this.wallThickness = wallThickness;
  }

  /**
   * Getter for the field <code>tantanLength</code>.
   *
   * @return the tantanLength
   */
  public double getTantanLength() {
    return tantanLength;
  }

  /**
   * Setter for the field <code>tantanLength</code>.
   *
   * @param tantanLength the tantanLength to set
   */
  public void setTantanLength(double tantanLength) {
    this.tantanLength = tantanLength;
  }

  /**
   * Getter for the field <code>weightTotal</code>.
   *
   * @return the weightTotal
   */
  public double getWeightTotal() {
    return weightTotal;
  }

  /**
   * Setter for the field <code>weightTotal</code>.
   *
   * @param weightTotal the weightTotal to set
   */
  public void setWeightTotal(double weightTotal) {
    this.weightTotal = weightTotal;
  }

  /**
   * Getter for the field <code>weigthInternals</code>.
   *
   * @return the wigthInternals
   */
  public double getWeigthInternals() {
    return weigthInternals;
  }

  /**
   * Setter for the field <code>weigthInternals</code>.
   *
   * @param weigthInternals the weigthInternals to set
   */
  public void setWeigthInternals(double weigthInternals) {
    this.weigthInternals = weigthInternals;
  }

  /**
   * Getter for the field <code>weightVessel</code>.
   *
   * @return the weightShell
   */
  public double getWeightVessel() {
    return weightVessel;
  }

  /**
   * Setter for the field <code>weightVessel</code>.
   *
   * @param weightVessel the weightShell to set
   */
  public void setWeightVessel(double weightVessel) {
    this.weightVessel = weightVessel;
  }

  /**
   * Getter for the field <code>weightNozzle</code>.
   *
   * @return the weightNozzle
   */
  public double getWeightNozzle() {
    return weightNozzle;
  }

  /**
   * Setter for the field <code>weightNozzle</code>.
   *
   * @param weightNozzle the weightNozzle to set
   */
  public void setWeightNozzle(double weightNozzle) {
    this.weightNozzle = weightNozzle;
  }

  /**
   * Getter for the field <code>weightPiping</code>.
   *
   * @return the weightPiping
   */
  public double getWeightPiping() {
    return weightPiping;
  }

  /**
   * Setter for the field <code>weightPiping</code>.
   *
   * @param weightPiping the weightPiping to set
   */
  public void setWeightPiping(double weightPiping) {
    this.weightPiping = weightPiping;
  }

  /**
   * Getter for the field <code>weightElectroInstrument</code>.
   *
   * @return the weightElectroInstrument
   */
  public double getWeightElectroInstrument() {
    return weightElectroInstrument;
  }

  /**
   * Setter for the field <code>weightElectroInstrument</code>.
   *
   * @param weightElectroInstrument the weightElectroInstrument to set
   */
  public void setWeightElectroInstrument(double weightElectroInstrument) {
    this.weightElectroInstrument = weightElectroInstrument;
  }

  /**
   * Getter for the field <code>weightStructualSteel</code>.
   *
   * @return the weightStructualSteel
   */
  public double getWeightStructualSteel() {
    return weightStructualSteel;
  }

  /**
   * Setter for the field <code>weightStructualSteel</code>.
   *
   * @param weightStructualSteel the weightStructualSteel to set
   */
  public void setWeightStructualSteel(double weightStructualSteel) {
    this.weightStructualSteel = weightStructualSteel;
  }

  /**
   * Getter for the field <code>weigthVesselShell</code>.
   *
   * @return the weigthVesselShell
   */
  public double getWeigthVesselShell() {
    return weigthVesselShell;
  }

  /**
   * Setter for the field <code>weigthVesselShell</code>.
   *
   * @param weigthVesselShell the weigthVesselShell to set
   */
  public void setWeigthVesselShell(double weigthVesselShell) {
    this.weigthVesselShell = weigthVesselShell;
  }

  /**
   * Getter for the field <code>moduleHeight</code>.
   *
   * @return the moduleHeight
   */
  public double getModuleHeight() {
    return moduleHeight;
  }

  /**
   * Setter for the field <code>moduleHeight</code>.
   *
   * @param moduleHeight the moduleHeight to set
   */
  public void setModuleHeight(double moduleHeight) {
    this.moduleHeight = moduleHeight;
  }

  /**
   * Getter for the field <code>moduleWidth</code>.
   *
   * @return the moduleWidth
   */
  public double getModuleWidth() {
    return moduleWidth;
  }

  /**
   * Setter for the field <code>moduleWidth</code>.
   *
   * @param moduleWidth the moduleWidth to set
   */
  public void setModuleWidth(double moduleWidth) {
    this.moduleWidth = moduleWidth;
  }

  /**
   * Getter for the field <code>moduleLength</code>.
   *
   * @return the moduleLength
   */
  public double getModuleLength() {
    return moduleLength;
  }

  /**
   * Setter for the field <code>moduleLength</code>.
   *
   * @param moduleLength the moduleLength to set
   */
  public void setModuleLength(double moduleLength) {
    this.moduleLength = moduleLength;
  }

  /**
   * Getter for the field <code>designStandard</code>.
   *
   * @return the designStandard
   */
  public Hashtable<String, DesignStandard> getDesignStandard() {
    return designStandard;
  }

  /**
   * Setter for the field <code>designStandard</code>.
   *
   * @param designStandard the designStandard to set
   */
  public void setDesignStandard(Hashtable<String, DesignStandard> designStandard) {
    this.designStandard = designStandard;
  }

  /**
   * Check if this mechanical design has any design standards assigned.
   *
   * @return true if at least one design standard is assigned
   */
  public boolean hasDesignStandard() {
    return designStandard != null && !designStandard.isEmpty();
  }

  /**
   * Getter for the field <code>maxDesignVolumeFlow</code>.
   *
   * @return the maxDesignVolumeFlow
   */
  public double getMaxDesignVolumeFlow() {
    return maxDesignVolumeFlow;
  }

  /**
   * Setter for the field <code>maxDesignVolumeFlow</code>.
   *
   * @param maxDesignVolumeFlow the maxDesignVolumeFlow to set
   */
  public void setMaxDesignVolumeFlow(double maxDesignVolumeFlow) {
    this.maxDesignVolumeFlow = maxDesignVolumeFlow;
  }

  /**
   * Getter for the field <code>minDesignVolumeFLow</code>.
   *
   * @return the minDesignVolumeFLow
   */
  public double getMinDesignVolumeFLow() {
    return minDesignVolumeFLow;
  }

  /**
   * Setter for the field <code>minDesignVolumeFLow</code>.
   *
   * @param minDesignVolumeFLow the minDesignVolumeFLow to set
   */
  public void setMinDesignVolumeFLow(double minDesignVolumeFLow) {
    this.minDesignVolumeFLow = minDesignVolumeFLow;
  }

  /**
   * Getter for the field <code>maxDesignGassVolumeFlow</code>.
   *
   * @return the maxDesignGassVolumeFlow
   */
  public double getMaxDesignGassVolumeFlow() {
    return maxDesignGassVolumeFlow;
  }

  /**
   * Setter for the field <code>maxDesignGassVolumeFlow</code>.
   *
   * @param maxDesignGassVolumeFlow the maxDesignGassVolumeFlow to set
   */
  public void setMaxDesignGassVolumeFlow(double maxDesignGassVolumeFlow) {
    this.maxDesignGassVolumeFlow = maxDesignGassVolumeFlow;
  }

  /**
   * Getter for the field <code>minDesignGassVolumeFLow</code>.
   *
   * @return the minDesignGassVolumeFLow
   */
  public double getMinDesignGassVolumeFLow() {
    return minDesignGassVolumeFLow;
  }

  /**
   * Setter for the field <code>minDesignGassVolumeFLow</code>.
   *
   * @param minDesignGassVolumeFLow the minDesignGassVolumeFLow to set
   */
  public void setMinDesignGassVolumeFLow(double minDesignGassVolumeFLow) {
    this.minDesignGassVolumeFLow = minDesignGassVolumeFLow;
  }

  /**
   * Getter for the field <code>maxDesignOilVolumeFlow</code>.
   *
   * @return the maxDesignOilVolumeFlow
   */
  public double getMaxDesignOilVolumeFlow() {
    return maxDesignOilVolumeFlow;
  }

  /**
   * Setter for the field <code>maxDesignOilVolumeFlow</code>.
   *
   * @param maxDesignOilVolumeFlow the maxDesignOilVolumeFlow to set
   */
  public void setMaxDesignOilVolumeFlow(double maxDesignOilVolumeFlow) {
    this.maxDesignOilVolumeFlow = maxDesignOilVolumeFlow;
  }

  /**
   * Getter for the field <code>minDesignOilFLow</code>.
   *
   * @return the minDesignOilFLow
   */
  public double getMinDesignOilFLow() {
    return minDesignOilFLow;
  }

  /**
   * Setter for the field <code>minDesignOilFLow</code>.
   *
   * @param minDesignOilFLow the minDesignOilFLow to set
   */
  public void setMinDesignOilFLow(double minDesignOilFLow) {
    this.minDesignOilFLow = minDesignOilFLow;
  }

  /**
   * Getter for the field <code>maxDesignWaterVolumeFlow</code>.
   *
   * @return the maxDesignWaterVolumeFlow
   */
  public double getMaxDesignWaterVolumeFlow() {
    return maxDesignWaterVolumeFlow;
  }

  /**
   * Setter for the field <code>maxDesignWaterVolumeFlow</code>.
   *
   * @param maxDesignWaterVolumeFlow the maxDesignWaterVolumeFlow to set
   */
  public void setMaxDesignWaterVolumeFlow(double maxDesignWaterVolumeFlow) {
    this.maxDesignWaterVolumeFlow = maxDesignWaterVolumeFlow;
  }

  /**
   * Getter for the field <code>minDesignWaterVolumeFLow</code>.
   *
   * @return the minDesignWaterVolumeFLow
   */
  public double getMinDesignWaterVolumeFLow() {
    return minDesignWaterVolumeFLow;
  }

  /**
   * Setter for the field <code>minDesignWaterVolumeFLow</code>.
   *
   * @param minDesignWaterVolumeFLow the minDesignWaterVolumeFLow to set
   */
  public void setMinDesignWaterVolumeFLow(double minDesignWaterVolumeFLow) {
    this.minDesignWaterVolumeFLow = minDesignWaterVolumeFLow;
  }

  /**
   * displayResults.
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[][] table = new String[3][3]; // createTable(getProcessEquipment().getName());
    table[1][0] = getProcessEquipment().getName();
    table[1][1] = Double.toString(getWeightTotal());
    table[1][2] = Double.toString(getVolumeTotal());
    String[] names = { "", "Volume", "Weight" };
    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.setSize(800, 600); // pack();
    // dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * Getter for the field <code>volumeTotal</code>.
   *
   * @return the volumeTotal
   */
  public double getVolumeTotal() {
    return volumeTotal;
  }

  /**
   * Setter for the field <code>volumeTotal</code>.
   *
   * @param volumeTotal the total equipment volume in m3 (must be non-negative)
   */
  public void setVolumeTotal(double volumeTotal) {
    this.volumeTotal = volumeTotal;
  }

  /**
   * isHasSetCompanySpecificDesignStandards.
   *
   * @return the hasSetCompanySpecificDesignStandards
   */
  public boolean isHasSetCompanySpecificDesignStandards() {
    return hasSetCompanySpecificDesignStandards;
  }

  /**
   * Setter for the field <code>hasSetCompanySpecificDesignStandards</code>.
   *
   * @param hasSetCompanySpecificDesignStandards the hasSetCompanySpecificDesignStandards to set
   */
  public void setHasSetCompanySpecificDesignStandards(boolean hasSetCompanySpecificDesignStandards) {
    this.hasSetCompanySpecificDesignStandards = hasSetCompanySpecificDesignStandards;
  }

  /**
   * Getter for the field <code>costEstimate</code>.
   *
   * @return the costEstimate
   */
  public UnitCostEstimateBaseClass getCostEstimate() {
    return costEstimate;
  }

  /**
   * Setter for the field <code>defaultLiquidDensity</code>.
   *
   * @param defaultLiqDens a double
   */
  public void setDefaultLiquidDensity(double defaultLiqDens) {
    this.defaultLiquidDensity = defaultLiqDens;
  }

  /**
   * Getter for the field <code>defaultLiquidDensity</code>.
   *
   * @return a double
   */
  public double getDefaultLiquidDensity() {
    return defaultLiquidDensity;
  }

  /**
   * Setter for the field <code>defaultLiquidViscosity</code>.
   *
   * @param defaultLiqVisc a double
   */
  public void setDefaultLiquidViscosity(double defaultLiqVisc) {
    this.defaultLiquidViscosity = defaultLiqVisc;
  }

  /**
   * Getter for the field <code>defaultLiquidViscosity</code>.
   *
   * @return a double
   */
  public double getDefaultLiquidViscosity() {
    return defaultLiquidViscosity;
  }

  /**
   * Export mechanical design data to JSON format.
   *
   * <p>
   * This method creates a {@link MechanicalDesignResponse} object and serializes it to JSON using Gson. The JSON
   * includes equipment identification, weight breakdown, design conditions, dimensions, and materials information.
   * </p>
   *
   * <p>
   * Usage example:
   * </p>
   *
   * <pre>
   * {@code
   * MechanicalDesign mecDesign = separator.getMechanicalDesign();
   * mecDesign.calcDesign();
   * String json = mecDesign.toJson();
   * }
   * </pre>
   *
   * @return JSON string representation of the mechanical design
   */
  public String toJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    return response.toJson();
  }

  /**
   * Export mechanical design data to compact JSON format (no pretty printing).
   *
   * @return compact JSON string
   */
  public String toCompactJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    return response.toCompactJson();
  }

  /**
   * Get the mechanical design response object.
   *
   * <p>
   * This method returns a {@link MechanicalDesignResponse} object that can be further customized or combined with other
   * data before serialization.
   * </p>
   *
   * @return MechanicalDesignResponse object
   */
  public MechanicalDesignResponse getResponse() {
    return new MechanicalDesignResponse(this);
  }

  // ============================================================================
  // Cost Estimation Methods
  // ============================================================================

  /**
   * Calculate cost estimates for this equipment.
   *
   * <p>
   * This method calculates purchased equipment cost, bare module cost, total module cost, and grass roots cost based on
   * the equipment weight and design parameters.
   * </p>
   *
   * <p>
   * Usage example:
   * </p>
   *
   * <pre>
   * {@code
   * MechanicalDesign mecDesign = separator.getMechanicalDesign();
   * mecDesign.calcDesign();
   * mecDesign.calculateCostEstimate();
   * double totalCost = mecDesign.getTotalModuleCost();
   * }
   * </pre>
   */
  public void calculateCostEstimate() {
    if (costEstimate != null) {
      costEstimate.calculateCostEstimate();
    }
  }

  /**
   * Get the purchased equipment cost.
   *
   * @return purchased equipment cost in USD
   */
  public double getPurchasedEquipmentCost() {
    if (costEstimate == null) {
      return 0.0;
    }
    return costEstimate.getPurchasedEquipmentCost();
  }

  /**
   * Get the bare module cost.
   *
   * <p>
   * Bare module cost includes the purchased equipment cost plus installation, piping, instrumentation, and other direct
   * costs.
   * </p>
   *
   * @return bare module cost in USD
   */
  public double getBareModuleCost() {
    if (costEstimate == null) {
      return 0.0;
    }
    return costEstimate.getBareModuleCost();
  }

  /**
   * Get the total module cost.
   *
   * <p>
   * Total module cost includes bare module cost plus contingency and engineering fees.
   * </p>
   *
   * @return total module cost in USD
   */
  public double getTotalModuleCost() {
    if (costEstimate == null) {
      return 0.0;
    }
    return costEstimate.getTotalModuleCost();
  }

  /**
   * Get the grass roots cost.
   *
   * <p>
   * Grass roots cost is the total cost for a new facility including site development, buildings, and offsites.
   * </p>
   *
   * @return grass roots cost in USD
   */
  public double getGrassRootsCost() {
    if (costEstimate == null) {
      return 0.0;
    }
    return costEstimate.getGrassRootsCost();
  }

  /**
   * Get the installation labor man-hours.
   *
   * @return installation man-hours
   */
  public double getInstallationManHours() {
    if (costEstimate == null) {
      return 0.0;
    }
    return costEstimate.getInstallationManHours();
  }

  /**
   * Generate bill of materials for this equipment.
   *
   * @return list of BOM items as maps
   */
  public java.util.List<java.util.Map<String, Object>> generateBillOfMaterials() {
    if (costEstimate == null) {
      return new ArrayList<>();
    }
    return costEstimate.generateBillOfMaterials();
  }

  /**
   * Set the material of construction for cost estimation.
   *
   * @param material material name (e.g., "Carbon Steel", "SS316", "Monel")
   */
  public void setCostEstimateMaterial(String material) {
    if (costEstimate != null) {
      costEstimate.setMaterialOfConstruction(material);
    }
  }

  /**
   * Set the location factor for cost estimation.
   *
   * @param factor location factor (1.0 = US Gulf Coast)
   */
  public void setCostEstimateLocationFactor(double factor) {
    if (costEstimate != null) {
      costEstimate.setLocationFactor(factor);
    }
  }

  /**
   * Set the current CEPCI for cost escalation.
   *
   * @param cepci Chemical Engineering Plant Cost Index
   */
  public void setCostEstimateCepci(double cepci) {
    if (costEstimate != null) {
      costEstimate.setCurrentCepci(cepci);
    }
  }

  /**
   * Export cost estimate to JSON format.
   *
   * @return JSON string of cost estimate
   */
  public String costEstimateToJson() {
    if (costEstimate == null) {
      return "{}";
    }
    return costEstimate.toJson();
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(companySpecificDesignStandards, construtionMaterial, corrosionAllowance, costEstimate,
        designStandard, hasSetCompanySpecificDesignStandards, innerDiameter, jointEfficiency,
        materialPipeDesignStandard, materialPlateDesignStandard, maxDesignGassVolumeFlow, maxDesignOilVolumeFlow,
        maxDesignVolumeFlow, maxDesignWaterVolumeFlow, maxOperationPressure, maxOperationTemperature,
        minDesignGassVolumeFLow, minDesignOilFLow, minDesignVolumeFLow, minDesignWaterVolumeFLow, minOperationPressure,
        minOperationTemperature, moduleHeight, moduleLength, moduleWidth, outerDiameter, pressureMarginFactor,
        processEquipment, tantanLength, tensileStrength, volumeTotal, wallThickness, weightElectroInstrument,
        weightNozzle, weightPiping, weightStructualSteel, weightTotal, weightVessel, weigthInternals,
        weigthVesselShell);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    MechanicalDesign other = (MechanicalDesign) obj;
    return Objects.equals(companySpecificDesignStandards, other.companySpecificDesignStandards)
        && Objects.equals(construtionMaterial, other.construtionMaterial)
        && Double.doubleToLongBits(corrosionAllowance) == Double.doubleToLongBits(other.corrosionAllowance)
        && Objects.equals(costEstimate, other.costEstimate) && Objects.equals(designStandard, other.designStandard)
        && hasSetCompanySpecificDesignStandards == other.hasSetCompanySpecificDesignStandards
        && Double.doubleToLongBits(innerDiameter) == Double.doubleToLongBits(other.innerDiameter)
        && Double.doubleToLongBits(jointEfficiency) == Double.doubleToLongBits(other.jointEfficiency)
        && Objects.equals(materialPipeDesignStandard, other.materialPipeDesignStandard)
        && Objects.equals(materialPlateDesignStandard, other.materialPlateDesignStandard)
        && Double.doubleToLongBits(maxDesignGassVolumeFlow) == Double.doubleToLongBits(other.maxDesignGassVolumeFlow)
        && Double.doubleToLongBits(maxDesignOilVolumeFlow) == Double.doubleToLongBits(other.maxDesignOilVolumeFlow)
        && Double.doubleToLongBits(maxDesignVolumeFlow) == Double.doubleToLongBits(other.maxDesignVolumeFlow)
        && Double.doubleToLongBits(maxDesignWaterVolumeFlow) == Double.doubleToLongBits(other.maxDesignWaterVolumeFlow)
        && Double.doubleToLongBits(maxOperationPressure) == Double.doubleToLongBits(other.maxOperationPressure)
        && Double.doubleToLongBits(maxOperationTemperature) == Double.doubleToLongBits(other.maxOperationTemperature)
        && Double.doubleToLongBits(minDesignGassVolumeFLow) == Double.doubleToLongBits(other.minDesignGassVolumeFLow)
        && Double.doubleToLongBits(minDesignOilFLow) == Double.doubleToLongBits(other.minDesignOilFLow)
        && Double.doubleToLongBits(minDesignVolumeFLow) == Double.doubleToLongBits(other.minDesignVolumeFLow)
        && Double.doubleToLongBits(minDesignWaterVolumeFLow) == Double.doubleToLongBits(other.minDesignWaterVolumeFLow)
        && Double.doubleToLongBits(minOperationPressure) == Double.doubleToLongBits(other.minOperationPressure)
        && Double.doubleToLongBits(minOperationTemperature) == Double.doubleToLongBits(other.minOperationTemperature)
        && Double.doubleToLongBits(moduleHeight) == Double.doubleToLongBits(other.moduleHeight)
        && Double.doubleToLongBits(moduleLength) == Double.doubleToLongBits(other.moduleLength)
        && Double.doubleToLongBits(moduleWidth) == Double.doubleToLongBits(other.moduleWidth)
        && Double.doubleToLongBits(outerDiameter) == Double.doubleToLongBits(other.outerDiameter)
        && Double.doubleToLongBits(pressureMarginFactor) == Double.doubleToLongBits(other.pressureMarginFactor)
        && Objects.equals(processEquipment, other.processEquipment)
        && Double.doubleToLongBits(tantanLength) == Double.doubleToLongBits(other.tantanLength)
        && Double.doubleToLongBits(tensileStrength) == Double.doubleToLongBits(other.tensileStrength)
        && Double.doubleToLongBits(volumeTotal) == Double.doubleToLongBits(other.volumeTotal)
        && Double.doubleToLongBits(wallThickness) == Double.doubleToLongBits(other.wallThickness)
        && Double.doubleToLongBits(weightElectroInstrument) == Double.doubleToLongBits(other.weightElectroInstrument)
        && Double.doubleToLongBits(weightNozzle) == Double.doubleToLongBits(other.weightNozzle)
        && Double.doubleToLongBits(weightPiping) == Double.doubleToLongBits(other.weightPiping)
        && Double.doubleToLongBits(weightStructualSteel) == Double.doubleToLongBits(other.weightStructualSteel)
        && Double.doubleToLongBits(weightTotal) == Double.doubleToLongBits(other.weightTotal)
        && Double.doubleToLongBits(weightVessel) == Double.doubleToLongBits(other.weightVessel)
        && Double.doubleToLongBits(weigthInternals) == Double.doubleToLongBits(other.weigthInternals)
        && Double.doubleToLongBits(weigthVesselShell) == Double.doubleToLongBits(other.weigthVesselShell);
  }
}
