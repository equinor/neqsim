package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionLoad;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionProfileSeries;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityPlanner;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityResult;
import neqsim.process.fielddevelopment.tieback.capacity.TieInPeriodResult;

/** Allocates shared facility capacity between existing host and new satellite production. */
public final class FacilityCapacityAllocator {

  /** Allocates a calendar year's potential rates using the configured holdback and priority policy. */
  public AllocationResult allocate(FacilityLifecycleStrategy strategy, int year,
      FacilityProductionRate satellitePotential) {
    if (strategy == null || satellitePotential == null) {
      throw new IllegalArgumentException("strategy and satellite potential are required");
    }
    FacilityCapacity capacity = strategy.getCapacity(year);
    FacilityProductionRate hostPotential = strategy.getHostProduction(year);
    FacilityProductionRate hostRequested = hostPotential.scale(1.0 - strategy.getHostHoldbackFraction());
    FacilityProductionRate satelliteRequested = satellitePotential.scale(1.0 - strategy.getSatelliteHoldbackFraction());
    FacilityProductionRate hostAllocated;
    FacilityProductionRate satelliteAllocated;

    if (strategy.getDevelopmentMode() == FacilityLifecycleStrategy.DevelopmentMode.BROWNFIELD_TIEBACK) {
      TieInPeriodResult period = runCanonicalTieInAllocation(strategy, year, capacity, hostRequested,
          satelliteRequested);
      hostAllocated = fromProductionLoad(period.getAcceptedBase());
      satelliteAllocated = fromProductionLoad(period.getAcceptedSatellite());
    } else {
      hostAllocated = FacilityProductionRate.zero();
      satelliteAllocated = satelliteRequested.scale(capacity.scaleToFit(satelliteRequested));
    }

    FacilityProductionRate total = hostAllocated.plus(satelliteAllocated);
    FacilityProductionRate totalRequested = hostRequested.plus(satelliteRequested);
    return new AllocationResult(year, capacity, hostPotential, hostRequested, hostAllocated, satellitePotential,
        satelliteRequested, satelliteAllocated, capacity.getPrimaryConstraint(total),
        capacity.getMaximumUtilization(total), capacity.getPrimaryConstraint(totalRequested),
        capacity.getMaximumUtilization(totalRequested));
  }

  private TieInPeriodResult runCanonicalTieInAllocation(FacilityLifecycleStrategy strategy, int year,
      FacilityCapacity capacity, FacilityProductionRate hostRequested, FacilityProductionRate satelliteRequested) {
    HostFacility allocationHost = HostFacility.builder(strategy.getHostFacility().getName() + " annual envelope")
        .oilCapacity(toOilBopd(capacity.getOilSm3PerDay())).gasCapacity(toGasMSm3d(capacity.getGasSm3PerDay()))
        .waterCapacity(finiteOrZero(capacity.getWaterSm3PerDay()))
        .liquidCapacity(finiteOrZero(capacity.getLiquidSm3PerDay())).build();
    ProductionProfileSeries hostProfile = new ProductionProfileSeries("base host")
        .add(toProductionLoad("base host", year, hostRequested));
    ProductionProfileSeries satelliteProfile = new ProductionProfileSeries("satellite")
        .add(toProductionLoad("satellite", year, satelliteRequested));
    TieInCapacityResult result = new TieInCapacityPlanner(allocationHost).setHostProductionProfile(hostProfile)
        .setSatelliteProductionProfile(satelliteProfile).setAllocationPolicy(strategy.getAllocationPolicy())
        .setHoldbackPolicy(strategy.getHoldbackPolicy()).run();
    return result.getPeriodResults().get(0);
  }

  private ProductionLoad toProductionLoad(String name, int year, FacilityProductionRate rates) {
    return new ProductionLoad(name, year, rates.getGasSm3PerDay() / 1.0e6,
        rates.getOilSm3PerDay() / ProductionLoad.BARREL_TO_M3, rates.getWaterSm3PerDay(), rates.getLiquidSm3PerDay());
  }

  private FacilityProductionRate fromProductionLoad(ProductionLoad load) {
    return new FacilityProductionRate(load.getOilRateBopd() * ProductionLoad.BARREL_TO_M3,
        load.getGasRateMSm3d() * 1.0e6, load.getWaterRateM3d());
  }

  private double toOilBopd(double oilSm3PerDay) {
    return Double.isFinite(oilSm3PerDay) ? oilSm3PerDay / ProductionLoad.BARREL_TO_M3 : 0.0;
  }

  private double toGasMSm3d(double gasSm3PerDay) {
    return Double.isFinite(gasSm3PerDay) ? gasSm3PerDay / 1.0e6 : 0.0;
  }

  private double finiteOrZero(double value) {
    return Double.isFinite(value) ? value : 0.0;
  }

  /** Immutable allocation and nameplate-utilization result. */
  public static final class AllocationResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int year;
    private final FacilityCapacity capacity;
    private final FacilityProductionRate hostPotential;
    private final FacilityProductionRate hostRequested;
    private final FacilityProductionRate hostAllocated;
    private final FacilityProductionRate satellitePotential;
    private final FacilityProductionRate satelliteRequested;
    private final FacilityProductionRate satelliteAllocated;
    private final String primaryBottleneck;
    private final double maximumUtilization;
    private final String requestedPrimaryBottleneck;
    private final double requestedMaximumUtilization;

    private AllocationResult(int year, FacilityCapacity capacity, FacilityProductionRate hostPotential,
        FacilityProductionRate hostRequested, FacilityProductionRate hostAllocated,
        FacilityProductionRate satellitePotential, FacilityProductionRate satelliteRequested,
        FacilityProductionRate satelliteAllocated, String primaryBottleneck, double maximumUtilization,
        String requestedPrimaryBottleneck, double requestedMaximumUtilization) {
      this.year = year;
      this.capacity = capacity;
      this.hostPotential = hostPotential;
      this.hostRequested = hostRequested;
      this.hostAllocated = hostAllocated;
      this.satellitePotential = satellitePotential;
      this.satelliteRequested = satelliteRequested;
      this.satelliteAllocated = satelliteAllocated;
      this.primaryBottleneck = primaryBottleneck;
      this.maximumUtilization = maximumUtilization;
      this.requestedPrimaryBottleneck = requestedPrimaryBottleneck;
      this.requestedMaximumUtilization = requestedMaximumUtilization;
    }

    /** Returns allocation calendar year. */
    public int getYear() {
      return year;
    }

    /** Returns the active facility capacity envelope. */
    public FacilityCapacity getCapacity() {
      return capacity;
    }

    /** Returns host production potential before holdback. */
    public FacilityProductionRate getHostPotential() {
      return hostPotential;
    }

    /** Returns host production requested after holdback. */
    public FacilityProductionRate getHostRequested() {
      return hostRequested;
    }

    /** Returns host production admitted to the facility. */
    public FacilityProductionRate getHostAllocated() {
      return hostAllocated;
    }

    /** Returns satellite production potential before holdback. */
    public FacilityProductionRate getSatellitePotential() {
      return satellitePotential;
    }

    /** Returns satellite production requested after holdback. */
    public FacilityProductionRate getSatelliteRequested() {
      return satelliteRequested;
    }

    /** Returns satellite production admitted to the facility. */
    public FacilityProductionRate getSatelliteAllocated() {
      return satelliteAllocated;
    }

    /** Returns the most utilized nameplate constraint. */
    public String getPrimaryBottleneck() {
      return primaryBottleneck;
    }

    /** Returns maximum nameplate utilization as a fraction. */
    public double getMaximumUtilization() {
      return maximumUtilization;
    }

    /** Returns the nameplate constraint at requested rates before capacity allocation. */
    public String getRequestedPrimaryBottleneck() {
      return requestedPrimaryBottleneck;
    }

    /** Returns maximum nameplate utilization at requested rates before capacity allocation. */
    public double getRequestedMaximumUtilization() {
      return requestedMaximumUtilization;
    }
  }
}
