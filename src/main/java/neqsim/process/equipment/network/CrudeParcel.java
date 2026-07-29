package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Immutable crude parcel identity, quantity, timing, route, and provenance.
 */
public class CrudeParcel implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String id;
  private final double massKg;
  private final CrudeAssay assay;
  private final int entryPeriod;
  private final String route;
  private final String provenance;

  /**
   * Create a parcel.
   *
   * @param id parcel identifier
   * @param massKg parcel mass
   * @param assay assay identity
   * @param entryPeriod entry period
   * @param route route
   * @param provenance source
   */
  public CrudeParcel(String id, double massKg, CrudeAssay assay, int entryPeriod, String route, String provenance) {
    if (!(massKg > 0.0)) {
      throw new IllegalArgumentException("Parcel mass must be positive");
    }
    this.id = id;
    this.massKg = massKg;
    this.assay = assay;
    this.entryPeriod = entryPeriod;
    this.route = route;
    this.provenance = provenance;
  }

  /** @return parcel identifier */
  public String getId() {
    return id;
  }

  /** @return parcel mass in kg */
  public double getMassKg() {
    return massKg;
  }

  /** @return assay */
  public CrudeAssay getAssay() {
    return assay;
  }

  /** @return entry period */
  public int getEntryPeriod() {
    return entryPeriod;
  }

  /** @return route */
  public String getRoute() {
    return route;
  }

  /** @return provenance */
  public String getProvenance() {
    return provenance;
  }
}
