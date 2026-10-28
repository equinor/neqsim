package neqsim.process.safety.release;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable cylindrical-vessel geometry for fail-closed phase-selected inventory withdrawal.
 *
 * <p>
 * The model supports one equilibrium gas phase above at most one equilibrium liquid phase. It calculates liquid level
 * and gas/liquid interfacial area directly from the live phase volumes. The opening elevation is then compared with the
 * interface and a caller-declared exclusion band. An opening inside that band is rejected because entrainment, waves
 * and interface crossing are unresolved. This class does not predict carry-over, slip, level control or finite-rate
 * phase transfer.
 * </p>
 */
public final class ReleaseInventoryPhaseGeometry implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double GEOMETRY_TOLERANCE = 1.0e-8;

  /** Supported cylindrical-vessel axis orientation. */
  public enum Orientation {
    /** Cylinder axis is vertical; level is measured along the cylinder length. */
    VERTICAL,
    /** Cylinder axis is horizontal; level is measured across the circular diameter. */
    HORIZONTAL
  }

  /** Result of assessing one live inventory state against the configured opening. */
  public static final class Assessment implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double liquidLevelM;
    private final double interfacialAreaM2;
    private final PhaseType phaseAtOpening;
    private final boolean interfaceBandClear;

    private Assessment(double liquidLevelM, double interfacialAreaM2, PhaseType phaseAtOpening,
        boolean interfaceBandClear) {
      this.liquidLevelM = liquidLevelM;
      this.interfacialAreaM2 = interfacialAreaM2;
      this.phaseAtOpening = phaseAtOpening;
      this.interfaceBandClear = interfaceBandClear;
    }

    /** @return live liquid level measured from the vessel bottom in m */
    public double getLiquidLevelM() {
      return liquidLevelM;
    }

    /** @return live gas/liquid interfacial area in m2, or zero for a single phase */
    public double getInterfacialAreaM2() {
      return interfacialAreaM2;
    }

    /** @return native phase at the opening, or {@code null} inside the unresolved interface band */
    public PhaseType getPhaseAtOpening() {
      return phaseAtOpening;
    }

    /** @return whether the opening is outside the unresolved interface/entrainment band */
    public boolean isInterfaceBandClear() {
      return interfaceBandClear;
    }
  }

  private final Orientation orientation;
  private final double diameterM;
  private final double lengthM;
  private final double openingElevationM;
  private final double interfaceExclusionHalfBandM;

  /**
   * Creates explicit cylindrical inventory geometry.
   *
   * @param orientation cylinder-axis orientation
   * @param diameterM internal cylinder diameter in m
   * @param lengthM internal tangent-to-tangent cylinder length in m
   * @param openingElevationM opening centre elevation from the vessel bottom in m
   * @param interfaceExclusionHalfBandM unresolved distance on each side of the interface in m
   */
  public ReleaseInventoryPhaseGeometry(Orientation orientation, double diameterM, double lengthM,
      double openingElevationM, double interfaceExclusionHalfBandM) {
    if (orientation == null) {
      throw new IllegalArgumentException("INVENTORY_GEOMETRY_ORIENTATION_REQUIRED");
    }
    this.orientation = orientation;
    this.diameterM = ReleaseFlowRequest.positive(diameterM, "inventoryGeometryDiameterM");
    this.lengthM = ReleaseFlowRequest.positive(lengthM, "inventoryGeometryLengthM");
    this.interfaceExclusionHalfBandM = ReleaseFlowRequest.nonnegative(interfaceExclusionHalfBandM,
        "inventoryInterfaceExclusionHalfBandM");
    double maximumElevation = maximumElevationM();
    if (!Double.isFinite(openingElevationM) || openingElevationM < 0.0 || openingElevationM > maximumElevation) {
      throw new IllegalArgumentException("INVENTORY_OPENING_ELEVATION_INVALID");
    }
    if (interfaceExclusionHalfBandM > maximumElevation) {
      throw new IllegalArgumentException("INVENTORY_INTERFACE_EXCLUSION_BAND_INVALID");
    }
    this.openingElevationM = openingElevationM;
  }

  /**
   * Verifies that this cylinder capacity matches the inventory capacity.
   *
   * @param inventoryVolumeM3 configured inventory capacity in m3
   */
  void requireCapacity(double inventoryVolumeM3) {
    double capacity = getCapacityM3();
    if (Math.abs(capacity - inventoryVolumeM3) > GEOMETRY_TOLERANCE * Math.max(capacity, inventoryVolumeM3)) {
      throw new IllegalArgumentException(
          "INVENTORY_GEOMETRY_CAPACITY_MISMATCH: geometry=" + capacity + ", inventory=" + inventoryVolumeM3);
    }
  }

  /**
   * Assesses a live equilibrium inventory without changing it.
   *
   * @param fluid initialized live inventory
   * @return immutable level, area and phase assessment
   */
  public Assessment assess(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("INVENTORY_GEOMETRY_FLUID_REQUIRED");
    }
    double gasVolume = 0.0;
    double liquidVolume = 0.0;
    PhaseType liquidType = null;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseType type = fluid.getPhase(phase).getType();
      double volume = fluid.getPhase(phase).getVolume("m3");
      if (!Double.isFinite(volume) || volume < 0.0) {
        throw new IllegalStateException("INVENTORY_GEOMETRY_PHASE_VOLUME_INVALID: " + type);
      }
      if (type == PhaseType.GAS) {
        gasVolume += volume;
      } else if (type == PhaseType.OIL || type == PhaseType.LIQUID || type == PhaseType.AQUEOUS) {
        if (liquidType != null && liquidType != type && volume > GEOMETRY_TOLERANCE * getCapacityM3()) {
          throw new IllegalStateException("INVENTORY_GEOMETRY_MULTIPLE_LIQUIDS_UNSUPPORTED");
        }
        if (volume > GEOMETRY_TOLERANCE * getCapacityM3()) {
          liquidType = type;
        }
        liquidVolume += volume;
      } else {
        throw new IllegalStateException("INVENTORY_GEOMETRY_PHASE_UNSUPPORTED: " + type);
      }
    }
    double totalVolume = gasVolume + liquidVolume;
    if (Math.abs(totalVolume - getCapacityM3()) > 1.0e-6 * getCapacityM3()) {
      throw new IllegalStateException(
          "INVENTORY_GEOMETRY_VOLUME_CLOSURE_FAILED: phases=" + totalVolume + ", capacity=" + getCapacityM3());
    }
    if (liquidVolume <= GEOMETRY_TOLERANCE * getCapacityM3()) {
      return new Assessment(0.0, 0.0, PhaseType.GAS, true);
    }
    if (gasVolume <= GEOMETRY_TOLERANCE * getCapacityM3()) {
      return new Assessment(maximumElevationM(), 0.0, liquidType, true);
    }
    double level = orientation == Orientation.VERTICAL ? liquidVolume / circularAreaM2()
        : horizontalLevelM(liquidVolume / lengthM);
    double interfaceArea = orientation == Orientation.VERTICAL ? circularAreaM2()
        : horizontalInterfaceChordM(level) * lengthM;
    double distance = openingElevationM - level;
    if (Math.abs(distance) <= interfaceExclusionHalfBandM) {
      return new Assessment(level, interfaceArea, null, false);
    }
    return new Assessment(level, interfaceArea, distance > 0.0 ? PhaseType.GAS : liquidType, true);
  }

  private double horizontalLevelM(double occupiedAreaM2) {
    double lower = 0.0;
    double upper = diameterM;
    for (int i = 0; i < 80; i++) {
      double middle = 0.5 * (lower + upper);
      if (horizontalSegmentAreaM2(middle) < occupiedAreaM2) {
        lower = middle;
      } else {
        upper = middle;
      }
    }
    return 0.5 * (lower + upper);
  }

  private double horizontalSegmentAreaM2(double heightM) {
    double radius = 0.5 * diameterM;
    double distanceFromCenter = radius - heightM;
    double root = Math.sqrt(Math.max(0.0, radius * radius - distanceFromCenter * distanceFromCenter));
    return radius * radius * Math.acos(distanceFromCenter / radius) - distanceFromCenter * root;
  }

  private double horizontalInterfaceChordM(double levelM) {
    double radius = 0.5 * diameterM;
    double distanceFromCenter = radius - levelM;
    return 2.0 * Math.sqrt(Math.max(0.0, radius * radius - distanceFromCenter * distanceFromCenter));
  }

  private double circularAreaM2() {
    return Math.PI * diameterM * diameterM / 4.0;
  }

  private double maximumElevationM() {
    return orientation == Orientation.VERTICAL ? lengthM : diameterM;
  }

  /** @return cylinder-axis orientation */
  public Orientation getOrientation() {
    return orientation;
  }

  /** @return internal cylinder diameter in m */
  public double getDiameterM() {
    return diameterM;
  }

  /** @return internal cylinder length in m */
  public double getLengthM() {
    return lengthM;
  }

  /** @return cylinder capacity in m3 */
  public double getCapacityM3() {
    return circularAreaM2() * lengthM;
  }

  /** @return opening centre elevation from the vessel bottom in m */
  public double getOpeningElevationM() {
    return openingElevationM;
  }

  /** @return unresolved interface/entrainment half-band in m */
  public double getInterfaceExclusionHalfBandM() {
    return interfaceExclusionHalfBandM;
  }
}
