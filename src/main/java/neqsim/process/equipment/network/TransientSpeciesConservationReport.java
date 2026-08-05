package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/** Immutable component-inventory diagnostics for one transient network location and timestep. */
public final class TransientSpeciesConservationReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Location represented by a report. */
  public enum LocationType {
    /** One finite-volume pipe edge. */
    EDGE,
    /** One conservative mixing junction. */
    JUNCTION,
    /** The complete network, including every edge inventory. */
    NETWORK
  }

  private final String locationName;
  private final LocationType locationType;
  private final double elapsedTimeSeconds;
  private final String[] componentNames;
  private final double[][] massFractionProfile;
  private final double[] initialInventoryKg;
  private final double[] finalInventoryKg;
  private final double[] inletBoundaryMassKg;
  private final double[] outletBoundaryMassKg;
  private final double[] inventoryResidualKg;
  private final double[] relativeInventoryResidual;
  private final double maximumRelativeInventoryResidual;
  private final double minimumMassFraction;
  private final double maximumMassFraction;
  private final double maximumMassFractionSumError;
  private final boolean converged;
  private final String message;

  TransientSpeciesConservationReport(String locationName, LocationType locationType, double elapsedTimeSeconds,
      String[] componentNames, double[][] massFractionProfile, double[] initialInventoryKg, double[] finalInventoryKg,
      double[] inletBoundaryMassKg, double[] outletBoundaryMassKg, double[] inventoryResidualKg,
      double[] relativeInventoryResidual, double maximumRelativeInventoryResidual, double minimumMassFraction,
      double maximumMassFraction, double maximumMassFractionSumError, boolean converged, String message) {
    this.locationName = locationName;
    this.locationType = locationType;
    this.elapsedTimeSeconds = elapsedTimeSeconds;
    this.componentNames = copy(componentNames);
    this.massFractionProfile = copy(massFractionProfile);
    this.initialInventoryKg = copy(initialInventoryKg);
    this.finalInventoryKg = copy(finalInventoryKg);
    this.inletBoundaryMassKg = copy(inletBoundaryMassKg);
    this.outletBoundaryMassKg = copy(outletBoundaryMassKg);
    this.inventoryResidualKg = copy(inventoryResidualKg);
    this.relativeInventoryResidual = copy(relativeInventoryResidual);
    this.maximumRelativeInventoryResidual = maximumRelativeInventoryResidual;
    this.minimumMassFraction = minimumMassFraction;
    this.maximumMassFraction = maximumMassFraction;
    this.maximumMassFractionSumError = maximumMassFractionSumError;
    this.converged = converged;
    this.message = message;
  }

  /** @return edge, junction, or network name */
  public String getLocationName() {
    return locationName;
  }

  /** @return represented location type */
  public LocationType getLocationType() {
    return locationType;
  }

  /** @return accepted-step end time in seconds */
  public double getElapsedTimeSeconds() {
    return elapsedTimeSeconds;
  }

  /** @return defensive copy of component names */
  public String[] getComponentNames() {
    return copy(componentNames);
  }

  /** @return defensive component-by-cell mass-fraction profile, empty for non-edge reports */
  public double[][] getMassFractionProfile() {
    return copy(massFractionProfile);
  }

  /** @return component inventories at the beginning of the run in kg */
  public double[] getInitialInventoryKg() {
    return copy(initialInventoryKg);
  }

  /** @return component inventories at this accepted time in kg */
  public double[] getFinalInventoryKg() {
    return copy(finalInventoryKg);
  }

  /** @return cumulative integrated inlet component masses through this accepted time in kg */
  public double[] getInletBoundaryMassKg() {
    return copy(inletBoundaryMassKg);
  }

  /** @return cumulative integrated outlet component masses through this accepted time in kg */
  public double[] getOutletBoundaryMassKg() {
    return copy(outletBoundaryMassKg);
  }

  /** @return final minus initial minus inlet plus outlet component residuals in kg */
  public double[] getInventoryResidualKg() {
    return copy(inventoryResidualKg);
  }

  /** @return component residuals divided by their conservative balance scale */
  public double[] getRelativeInventoryResidual() {
    return copy(relativeInventoryResidual);
  }

  /** @return maximum absolute relative component residual */
  public double getMaximumRelativeInventoryResidual() {
    return maximumRelativeInventoryResidual;
  }

  /** @return minimum mass fraction, or NaN for reports without a composition profile */
  public double getMinimumMassFraction() {
    return minimumMassFraction;
  }

  /** @return maximum mass fraction, or NaN for reports without a composition profile */
  public double getMaximumMassFraction() {
    return maximumMassFraction;
  }

  /** @return maximum cell-wise absolute mass-fraction sum error */
  public double getMaximumMassFractionSumError() {
    return maximumMassFractionSumError;
  }

  /** @return true when balance and boundedness criteria passed */
  public boolean isConverged() {
    return converged;
  }

  /** @return diagnostic summary */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize this immutable report for Python/JPype capture.
   *
   * @return stable, pretty-printed JSON
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(Double.class, finiteDoubleSerializer);
    gsonBuilder.registerTypeAdapter(Double.TYPE, finiteDoubleSerializer);
    gsonBuilder.serializeNulls();
    gsonBuilder.setPrettyPrinting();
    return gsonBuilder.create().toJson(this);
  }

  private static double[] copy(double[] values) {
    return values == null ? new double[0] : Arrays.copyOf(values, values.length);
  }

  private static String[] copy(String[] values) {
    return values == null ? new String[0] : Arrays.copyOf(values, values.length);
  }

  private static double[][] copy(double[][] values) {
    if (values == null) {
      return new double[0][0];
    }
    double[][] result = new double[values.length][];
    for (int index = 0; index < values.length; index++) {
      result[index] = copy(values[index]);
    }
    return result;
  }
}
