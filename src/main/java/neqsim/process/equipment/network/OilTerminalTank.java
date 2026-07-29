package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Terminal tank/cavern with explicit perfect-mixing or segregated operation.
 */
public class OilTerminalTank implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Inventory mixing model. */
  public enum MixingMode {
    /** Receipts join a documented perfectly mixed inventory. */
    PERFECT_MIXED,
    /** Parcel identity remains segregated and withdrawals are FIFO. */
    SEGREGATED
  }

  private final String name;
  private final double capacityKg;
  private final double heelKg;
  private final double maximumReceiptKgS;
  private final double maximumWithdrawalKgS;
  private final MixingMode mixingMode;
  private boolean available = true;
  private final List<TankLot> lots = new ArrayList<TankLot>();
  private int currentPeriod = -1;
  private double receivedThisPeriodKg = 0.0;
  private double withdrawnThisPeriodKg = 0.0;

  /**
   * Create a tank.
   *
   * @param name name
   * @param capacityKg capacity mass
   * @param heelKg minimum heel
   * @param maximumReceiptKgS receipt limit
   * @param maximumWithdrawalKgS withdrawal limit
   * @param mixingMode mixing mode
   */
  public OilTerminalTank(String name, double capacityKg, double heelKg, double maximumReceiptKgS,
      double maximumWithdrawalKgS, MixingMode mixingMode) {
    if (!(capacityKg > 0.0) || heelKg < 0.0 || heelKg >= capacityKg) {
      throw new IllegalArgumentException("Tank capacity and heel are invalid");
    }
    this.name = name;
    this.capacityKg = capacityKg;
    this.heelKg = heelKg;
    this.maximumReceiptKgS = maximumReceiptKgS;
    this.maximumWithdrawalKgS = maximumWithdrawalKgS;
    this.mixingMode = mixingMode;
  }

  /**
   * Start a period and reset rate counters.
   *
   * @param periodIndex period
   */
  public void beginPeriod(int periodIndex) {
    if (periodIndex != currentPeriod) {
      currentPeriod = periodIndex;
      receivedThisPeriodKg = 0.0;
      withdrawnThisPeriodKg = 0.0;
    }
  }

  /**
   * Add opening inventory without applying a period receipt-rate limit.
   *
   * @param parcel opening parcel
   */
  public void addOpeningInventory(CrudeParcel parcel) {
    if (getMassKg() + parcel.getMassKg() > capacityKg + 1.0e-9) {
      throw new IllegalStateException("Opening inventory exceeds capacity for " + name);
    }
    lots.add(new TankLot(parcel, parcel.getMassKg()));
  }

  /**
   * Receive a parcel.
   *
   * @param parcel parcel
   * @param durationSeconds period duration
   */
  public void receive(CrudeParcel parcel, double durationSeconds) {
    requireAvailable();
    double newReceipt = receivedThisPeriodKg + parcel.getMassKg();
    if (maximumReceiptKgS > 0.0 && newReceipt > maximumReceiptKgS * durationSeconds + 1.0e-9) {
      throw new IllegalStateException("Tank receipt-rate limit exceeded for " + name);
    }
    if (getMassKg() + parcel.getMassKg() > capacityKg + 1.0e-9) {
      throw new IllegalStateException("Tank capacity/ullage exceeded for " + name);
    }
    lots.add(new TankLot(parcel, parcel.getMassKg()));
    receivedThisPeriodKg = newReceipt;
  }

  /**
   * Withdraw a parcel.
   *
   * @param parcelId output parcel identifier
   * @param massKg requested mass
   * @param periodIndex period
   * @param route route
   * @param durationSeconds period duration
   * @return withdrawn parcel
   */
  public CrudeParcel withdraw(String parcelId, double massKg, int periodIndex, String route, double durationSeconds) {
    requireAvailable();
    if (!(massKg > 0.0) || getMassKg() - massKg < heelKg - 1.0e-9) {
      throw new IllegalStateException("Withdrawal violates inventory heel for " + name);
    }
    double newWithdrawal = withdrawnThisPeriodKg + massKg;
    if (maximumWithdrawalKgS > 0.0 && newWithdrawal > maximumWithdrawalKgS * durationSeconds + 1.0e-9) {
      throw new IllegalStateException("Tank withdrawal-rate limit exceeded for " + name);
    }

    CrudeParcel output;
    if (mixingMode == MixingMode.PERFECT_MIXED) {
      output = withdrawPerfectlyMixed(parcelId, massKg, periodIndex, route);
    } else {
      output = withdrawSegregated(parcelId, massKg, periodIndex, route);
    }
    withdrawnThisPeriodKg = newWithdrawal;
    return output;
  }

  private CrudeParcel withdrawPerfectlyMixed(String parcelId, double massKg, int periodIndex, String route) {
    double openingMass = getMassKg();
    List<CrudeParcel> inventoryParcels = new ArrayList<CrudeParcel>();
    for (TankLot lot : lots) {
      inventoryParcels.add(new CrudeParcel(lot.parcel.getId(), lot.massKg, lot.parcel.getAssay(),
          lot.parcel.getEntryPeriod(), lot.parcel.getRoute(), lot.parcel.getProvenance()));
    }
    CrudeBlendResult blend = CrudeAssay.blend(name + " mixed inventory", inventoryParcels);
    double remainingFraction = (openingMass - massKg) / openingMass;
    for (TankLot lot : lots) {
      lot.massKg *= remainingFraction;
    }
    removeEmptyLots();
    return new CrudeParcel(parcelId, massKg, blend.getAssay(), periodIndex, route,
        "Perfectly mixed withdrawal from " + name);
  }

  private CrudeParcel withdrawSegregated(String parcelId, double massKg, int periodIndex, String route) {
    TankLot first = lots.get(0);
    if (first.massKg + 1.0e-9 < massKg) {
      throw new IllegalStateException("Segregated withdrawal would cross a parcel boundary in " + name
          + "; request a smaller parcel or explicitly blend");
    }
    first.massKg -= massKg;
    CrudeAssay assay = first.parcel.getAssay();
    removeEmptyLots();
    return new CrudeParcel(parcelId, massKg, assay, periodIndex, route, "Segregated FIFO withdrawal from " + name);
  }

  private void removeEmptyLots() {
    Iterator<TankLot> iterator = lots.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().massKg <= 1.0e-9) {
        iterator.remove();
      }
    }
  }

  /** @return current inventory mass */
  public double getMassKg() {
    double mass = 0.0;
    for (TankLot lot : lots) {
      mass += lot.massKg;
    }
    return mass;
  }

  /**
   * Snapshot inventory and component/parcel identity.
   *
   * @return state
   */
  public TankInventoryState snapshot() {
    Map<String, Double> componentMass = new LinkedHashMap<String, Double>();
    Map<String, Double> parcelMass = new LinkedHashMap<String, Double>();
    for (TankLot lot : lots) {
      parcelMass.put(lot.parcel.getId(), lot.massKg);
      SystemInterface fluid = lot.parcel.getAssay().getFluid();
      double averageMolarMass = fluid.getMolarMass();
      double[] composition = fluid.getMolarComposition();
      for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
        ComponentInterface component = fluid.getPhase(0).getComponent(index);
        double mass = lot.massKg * composition[index] * component.getMolarMass() / averageMolarMass;
        Double existing = componentMass.get(component.getComponentName());
        componentMass.put(component.getComponentName(), (existing == null ? 0.0 : existing) + mass);
      }
    }
    return new TankInventoryState(name, getMassKg(), capacityKg, heelKg, available, mixingMode.name(), componentMass,
        parcelMass);
  }

  /** @return tank name */
  public String getName() {
    return name;
  }

  /** @return capacity in kg */
  public double getCapacityKg() {
    return capacityKg;
  }

  /** @return heel in kg */
  public double getHeelKg() {
    return heelKg;
  }

  /** @return mixing mode */
  public MixingMode getMixingMode() {
    return mixingMode;
  }

  /** @return availability */
  public boolean isAvailable() {
    return available;
  }

  /** @param value availability */
  public void setAvailable(boolean value) {
    available = value;
  }

  /**
   * Create an independent operational copy.
   *
   * @return copied tank and inventory
   */
  public OilTerminalTank copy() {
    OilTerminalTank copied = new OilTerminalTank(name, capacityKg, heelKg, maximumReceiptKgS, maximumWithdrawalKgS,
        mixingMode);
    copied.available = available;
    for (TankLot lot : lots) {
      copied.lots.add(new TankLot(lot.parcel, lot.massKg));
    }
    return copied;
  }

  private void requireAvailable() {
    if (!available) {
      throw new IllegalStateException("Tank is unavailable: " + name);
    }
  }

  private static final class TankLot implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final CrudeParcel parcel;
    private double massKg;

    private TankLot(CrudeParcel parcel, double massKg) {
      this.parcel = parcel;
      this.massKg = massKg;
    }
  }
}
