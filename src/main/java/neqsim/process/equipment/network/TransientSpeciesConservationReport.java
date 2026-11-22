package neqsim.process.equipment.network;

import static neqsim.util.util.ArrayUtils.copyArrayOrEmpty;
import static neqsim.util.util.ArrayUtils.copyMatrixArrayOrEmpty;

import java.io.Serializable;
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
    this.componentNames = copyArrayOrEmpty(componentNames);
    this.massFractionProfile = copyMatrixArrayOrEmpty(massFractionProfile);
    this.initialInventoryKg = copyArrayOrEmpty(initialInventoryKg);
    this.finalInventoryKg = copyArrayOrEmpty(finalInventoryKg);
    this.inletBoundaryMassKg = copyArrayOrEmpty(inletBoundaryMassKg);
    this.outletBoundaryMassKg = copyArrayOrEmpty(outletBoundaryMassKg);
    this.inventoryResidualKg = copyArrayOrEmpty(inventoryResidualKg);
    this.relativeInventoryResidual = copyArrayOrEmpty(relativeInventoryResidual);
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
    return copyArrayOrEmpty(componentNames);
  }

  /** @return defensive component-by-cell mass-fraction profile, empty for non-edge reports */
  public double[][] getMassFractionProfile() {
    return copyMatrixArrayOrEmpty(massFractionProfile);
  }

  /** @return component inventories at the beginning of the run in kg */
  public double[] getInitialInventoryKg() {
    return copyArrayOrEmpty(initialInventoryKg);
  }

  /** @return component inventories at this accepted time in kg */
  public double[] getFinalInventoryKg() {
    return copyArrayOrEmpty(finalInventoryKg);
  }

  /** @return cumulative integrated inlet component masses through this accepted time in kg */
  public double[] getInletBoundaryMassKg() {
    return copyArrayOrEmpty(inletBoundaryMassKg);
  }

  /** @return cumulative integrated outlet component masses through this accepted time in kg */
  public double[] getOutletBoundaryMassKg() {
    return copyArrayOrEmpty(outletBoundaryMassKg);
  }

  /** @return final minus initial minus inlet plus outlet component residuals in kg */
  public double[] getInventoryResidualKg() {
    return copyArrayOrEmpty(inventoryResidualKg);
  }

  /** @return component residuals divided by their conservative balance scale */
  public double[] getRelativeInventoryResidual() {
    return copyArrayOrEmpty(relativeInventoryResidual);
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

}
