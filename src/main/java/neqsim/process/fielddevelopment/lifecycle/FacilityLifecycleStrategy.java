package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.capacity.CapacityAllocationPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.HoldbackPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionLoad;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionProfileSeries;

/** Greenfield sizing or brownfield shared-capacity strategy used by a lifecycle concept. */
public final class FacilityLifecycleStrategy implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Development mode. */
  public enum DevelopmentMode {
    GREENFIELD,
    BROWNFIELD_TIEBACK
  }

  private final String name;
  private final DevelopmentMode developmentMode;
  private final CapacityAllocationPolicy allocationPolicy;
  private final HoldbackPolicy holdbackPolicy;
  private final HostFacility hostFacility;
  private final ProductionProfileSeries hostProductionProfile;
  private final FacilityProductionRate designRates;
  private final FacilityCapacity baseCapacity;
  private final NavigableMap<Integer, FacilityCapacity> capacitySchedule;
  private final double designMargin;
  private final double hostHoldbackFraction;
  private final double satelliteHoldbackFraction;
  private final double maximumDetailedProcessUtilization;
  private final boolean autoSizeDetailedProcess;
  private final boolean useDetailedProcessConstraints;

  private FacilityLifecycleStrategy(Builder builder) {
    name = builder.name;
    developmentMode = builder.developmentMode;
    allocationPolicy = builder.allocationPolicy;
    holdbackPolicy = builder.holdbackPolicy;
    hostFacility = builder.hostFacility;
    hostProductionProfile = builder.hostProductionProfile;
    designRates = builder.designRates;
    designMargin = builder.designMargin;
    hostHoldbackFraction = builder.hostHoldbackFraction;
    satelliteHoldbackFraction = builder.satelliteHoldbackFraction;
    maximumDetailedProcessUtilization = builder.maximumDetailedProcessUtilization;
    autoSizeDetailedProcess = builder.autoSizeDetailedProcess;
    useDetailedProcessConstraints = builder.useDetailedProcessConstraints;
    baseCapacity = builder.baseCapacity != null ? builder.baseCapacity
        : developmentMode == DevelopmentMode.GREENFIELD
            ? FacilityCapacity.fromDesignRates(designRates, builder.designPowerKw, designMargin)
            : FacilityCapacity.fromHostFacility(hostFacility, builder.designPowerKw);
    capacitySchedule = Collections.unmodifiableNavigableMap(
        new TreeMap<Integer, FacilityCapacity>(builder.capacitySchedule));
  }

  /** Creates a greenfield strategy whose nameplate is derived from design rates and margin. */
  public static Builder greenfield(String name, FacilityProductionRate designRates) {
    return new Builder(name, DevelopmentMode.GREENFIELD, designRates, null, null);
  }

  /** Creates a brownfield strategy using an existing host and its own production profile. */
  public static Builder tieback(String name, HostFacility host, ProductionProfileSeries hostProductionProfile) {
    return new Builder(name, DevelopmentMode.BROWNFIELD_TIEBACK, FacilityProductionRate.zero(), host,
        hostProductionProfile);
  }

  /** Returns the capacity available in a year, including any scheduled debottlenecking. */
  public FacilityCapacity getCapacity(int year) {
    Map.Entry<Integer, FacilityCapacity> entry = capacitySchedule.floorEntry(year);
    return entry == null ? baseCapacity : entry.getValue();
  }

  /** Returns the host potential profile, or zero rates for a greenfield. */
  public FacilityProductionRate getHostProduction(int year) {
    if (hostProductionProfile == null || hostProductionProfile.isEmpty()) {
      return FacilityProductionRate.zero();
    }
    ProductionLoad exact = hostProductionProfile.getLoadByYear(year);
    if (exact != null) {
      return fromProductionLoad(exact);
    }
    ProductionLoad lower = null;
    ProductionLoad upper = null;
    for (ProductionLoad load : hostProductionProfile.getLoads()) {
      if (load.getYear() < year && (lower == null || load.getYear() > lower.getYear())) {
        lower = load;
      }
      if (load.getYear() > year && (upper == null || load.getYear() < upper.getYear())) {
        upper = load;
      }
    }
    if (lower == null) {
      return fromProductionLoad(upper);
    }
    if (upper == null) {
      return fromProductionLoad(lower);
    }
    double fraction = (year - lower.getYear()) / (double) (upper.getYear() - lower.getYear());
    FacilityProductionRate first = fromProductionLoad(lower);
    FacilityProductionRate second = fromProductionLoad(upper);
    return new FacilityProductionRate(interpolate(first.getOilSm3PerDay(), second.getOilSm3PerDay(), fraction),
        interpolate(first.getGasSm3PerDay(), second.getGasSm3PerDay(), fraction),
        interpolate(first.getWaterSm3PerDay(), second.getWaterSm3PerDay(), fraction));
  }

  private FacilityProductionRate fromProductionLoad(ProductionLoad load) {
    return new FacilityProductionRate(load.getOilRateBopd() * ProductionLoad.BARREL_TO_M3,
        load.getGasRateMSm3d() * 1.0e6, load.getWaterRateM3d());
  }

  private double interpolate(double first, double second, double fraction) {
    return first + (second - first) * fraction;
  }

  /** Returns strategy name. */
  public String getName() {
    return name;
  }

  /** Returns greenfield or brownfield development mode. */
  public DevelopmentMode getDevelopmentMode() {
    return developmentMode;
  }

  /** Returns host/satellite capacity allocation policy. */
  public CapacityAllocationPolicy getAllocationPolicy() {
    return allocationPolicy;
  }

  /** Returns the canonical tie-in production holdback policy. */
  public HoldbackPolicy getHoldbackPolicy() {
    return holdbackPolicy;
  }

  /** Returns existing host facility, or null for a greenfield. */
  public HostFacility getHostFacility() {
    return hostFacility;
  }

  /** Returns simultaneous process design rates. */
  public FacilityProductionRate getDesignRates() {
    return designRates;
  }

  /** Returns facility capacity before scheduled debottlenecking. */
  public FacilityCapacity getBaseCapacity() {
    return baseCapacity;
  }

  /** Returns greenfield design margin. */
  public double getDesignMargin() {
    return designMargin;
  }

  /** Returns host pre-allocation holdback fraction. */
  public double getHostHoldbackFraction() {
    return hostHoldbackFraction;
  }

  /** Returns satellite pre-allocation holdback fraction. */
  public double getSatelliteHoldbackFraction() {
    return satelliteHoldbackFraction;
  }

  /** Returns permitted detailed-process utilization fraction. */
  public double getMaximumDetailedProcessUtilization() {
    return maximumDetailedProcessUtilization;
  }

  /** Returns whether detailed equipment is auto-sized at the design case. */
  public boolean isAutoSizeDetailedProcess() {
    return autoSizeDetailedProcess;
  }

  /** Returns whether detailed process/mechanical constraints are enforced each year. */
  public boolean isUseDetailedProcessConstraints() {
    return useDetailedProcessConstraints;
  }

  /** Builder for facility lifecycle strategies. */
  public static final class Builder {
    private final DevelopmentMode developmentMode;
    private final FacilityProductionRate designRates;
    private final HostFacility hostFacility;
    private final ProductionProfileSeries hostProductionProfile;
    private final NavigableMap<Integer, FacilityCapacity> capacitySchedule =
        new TreeMap<Integer, FacilityCapacity>();
    private String name;
    private CapacityAllocationPolicy allocationPolicy = CapacityAllocationPolicy.BASE_FIRST;
    private HoldbackPolicy holdbackPolicy = HoldbackPolicy.DEFER_TO_LATER_YEARS;
    private FacilityCapacity baseCapacity;
    private double designMargin = 1.15;
    private double designPowerKw;
    private double hostHoldbackFraction;
    private double satelliteHoldbackFraction;
    private double maximumDetailedProcessUtilization = 1.0;
    private boolean autoSizeDetailedProcess;
    private boolean useDetailedProcessConstraints = true;

    private Builder(String name, DevelopmentMode developmentMode, FacilityProductionRate designRates,
        HostFacility hostFacility, ProductionProfileSeries hostProductionProfile) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("facility strategy name is required");
      }
      if (developmentMode == DevelopmentMode.GREENFIELD && designRates == null) {
        throw new IllegalArgumentException("greenfield design rates are required");
      }
      if (developmentMode == DevelopmentMode.BROWNFIELD_TIEBACK
          && (hostFacility == null || hostProductionProfile == null)) {
        throw new IllegalArgumentException("tieback host and production profile are required");
      }
      this.name = name;
      this.developmentMode = developmentMode;
      this.designRates = designRates;
      this.hostFacility = hostFacility;
      this.hostProductionProfile = hostProductionProfile;
      autoSizeDetailedProcess = developmentMode == DevelopmentMode.GREENFIELD;
    }

    /** Selects canonical host/satellite nameplate-capacity allocation policy. */
    public Builder allocationPolicy(CapacityAllocationPolicy allocationPolicy) {
      if (allocationPolicy == null) {
        throw new IllegalArgumentException("allocationPolicy is required");
      }
      this.allocationPolicy = allocationPolicy;
      return this;
    }

    /** Selects whether constrained production is curtailed or deferred in tie-in planning. */
    public Builder holdbackPolicy(HoldbackPolicy holdbackPolicy) {
      if (holdbackPolicy == null) {
        throw new IllegalArgumentException("holdbackPolicy is required");
      }
      this.holdbackPolicy = holdbackPolicy;
      return this;
    }

    /** Sets greenfield design margin as a multiplier of at least one. */
    public Builder designMargin(double designMargin) {
      if (!Double.isFinite(designMargin) || designMargin < 1.0) {
        throw new IllegalArgumentException("designMargin must be finite and at least one");
      }
      this.designMargin = designMargin;
      return this;
    }

    /** Sets explicit facility power capacity in kW; zero derives or leaves it unconstrained. */
    public Builder designPowerKw(double designPowerKw) {
      this.designPowerKw = designPowerKw;
      return this;
    }

    /** Sets an explicit oil, gas, water, liquid and power nameplate envelope. */
    public Builder nameplateCapacity(FacilityCapacity capacity) {
      this.baseCapacity = capacity;
      return this;
    }

    /** Schedules a new capacity envelope from a calendar year, for example after debottlenecking. */
    public Builder capacityFromYear(int year, FacilityCapacity capacity) {
      if (capacity == null) {
        throw new IllegalArgumentException("capacity is required");
      }
      capacitySchedule.put(year, capacity);
      return this;
    }

    /** Applies pre-allocation host and satellite production holdback fractions. */
    public Builder holdback(double hostFraction, double satelliteFraction) {
      hostHoldbackFraction = fraction(hostFraction, "hostFraction");
      satelliteHoldbackFraction = fraction(satelliteFraction, "satelliteFraction");
      return this;
    }

    /** Sets the permitted detailed-process utilization in the interval (0, 1]. */
    public Builder maximumDetailedProcessUtilization(double utilization) {
      if (!Double.isFinite(utilization) || utilization <= 0.0 || utilization > 1.0) {
        throw new IllegalArgumentException("maximum detailed utilization must be in (0, 1]");
      }
      maximumDetailedProcessUtilization = utilization;
      return this;
    }

    /** Enables or disables detailed process auto-sizing at the design case. */
    public Builder autoSizeDetailedProcess(boolean autoSize) {
      autoSizeDetailedProcess = autoSize;
      return this;
    }

    /** Enables or disables detailed equipment and mechanical constraint enforcement. */
    public Builder useDetailedProcessConstraints(boolean useDetailedConstraints) {
      useDetailedProcessConstraints = useDetailedConstraints;
      return this;
    }

    /** Builds the immutable facility strategy. */
    public FacilityLifecycleStrategy build() {
      return new FacilityLifecycleStrategy(this);
    }

    private static double fraction(double value, String name) {
      if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
        throw new IllegalArgumentException(name + " must be in [0, 1)");
      }
      return value;
    }
  }
}
