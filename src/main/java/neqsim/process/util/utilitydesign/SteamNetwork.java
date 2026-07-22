package neqsim.process.util.utilitydesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening-level multi-pressure steam network balancer.
 *
 * <p>
 * Manages a cascade of steam headers (for example HP / MP / LP) with a steam demand and an optional local generation
 * (waste-heat boilers, let-down turbine exhaust) at each level. Steam is supplied to the highest-pressure header by a
 * fired {@link Boiler} and cascades down through let-down stations to satisfy the lower-pressure header demands; local
 * generation at a header reduces the let-down required from above. The network sizes the boiler make-up generation and
 * a {@link Deaerator} for the boiler feedwater. It is deterministic and intended for early-stage utility screening.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * SteamNetwork net = new SteamNetwork("Steam System");
 * net.addLevel("HP", 41.0, 252.0);
 * net.addLevel("MP", 11.0, 184.0);
 * net.addLevel("LP", 4.5, 148.0);
 * net.addDemand("MP", 8000.0); // kg/h
 * net.addDemand("LP", 5000.0);
 * net.setLocalGeneration("LP", 2000.0); // waste-heat steam
 * net.calculate();
 * double boilerSteam = net.getBoilerGenerationKgh();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SteamNetwork implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Equipment name. */
  private String name = "Steam Network";

  /** Steam enthalpy rise from feedwater to generated steam [kJ/kg], default 2200. */
  private double steamEnthalpyRiseKJperKg = 2200.0;

  /** Condensate return fraction (the rest is made up with fresh feedwater), default 0.8. */
  private double condensateReturnFraction = 0.8;

  /** Steam headers, kept sorted highest-pressure first after each add. */
  private final List<SteamHeader> headers = new ArrayList<SteamHeader>();

  /** Embedded boiler sizing the top-header make-up generation. */
  private final Boiler boiler = new Boiler("Steam Network Boiler");

  /** Embedded deaerator sizing the boiler feedwater treatment. */
  private final Deaerator deaerator = new Deaerator("Steam Network Deaerator");

  // Results
  private double boilerGenerationKgh = 0.0;
  private double totalDemandKgh = 0.0;
  private double totalLocalGenerationKgh = 0.0;
  private boolean calculated = false;

  /**
   * A single steam header (pressure level) in the network.
   */
  public static class SteamHeader implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Header name. */
    public final String name;

    /** Header pressure [bara]. */
    public final double pressureBara;

    /** Saturation temperature [°C]. */
    public final double saturationTempC;

    /** Steam demand at this header [kg/h]. */
    public double demandKgh = 0.0;

    /** Local steam generation at this header [kg/h]. */
    public double localGenerationKgh = 0.0;

    /** Steam entering this header from the level above (or boiler for the top) [kg/h]. */
    public double inflowFromAboveKgh = 0.0;

    /** Steam let down from this header to the level below [kg/h]. */
    public double letdownToBelowKgh = 0.0;

    /**
     * Creates a steam header.
     *
     * @param name header name
     * @param pressureBara header pressure in bara
     * @param saturationTempC saturation temperature in °C
     */
    public SteamHeader(String name, double pressureBara, double saturationTempC) {
      this.name = name;
      this.pressureBara = pressureBara;
      this.saturationTempC = saturationTempC;
    }
  }

  /**
   * Creates a steam network with the default name.
   */
  public SteamNetwork() {
  }

  /**
   * Creates a named steam network.
   *
   * @param name network name
   */
  public SteamNetwork(String name) {
    this.name = name;
  }

  /**
   * Adds a steam header. Headers are automatically ordered by decreasing pressure.
   *
   * @param name header name
   * @param pressureBara header pressure in bara
   * @param saturationTempC saturation temperature in °C
   */
  public void addLevel(String name, double pressureBara, double saturationTempC) {
    headers.add(new SteamHeader(name, pressureBara, saturationTempC));
    Collections.sort(headers, new Comparator<SteamHeader>() {
      @Override
      public int compare(SteamHeader a, SteamHeader b) {
        return Double.compare(b.pressureBara, a.pressureBara);
      }
    });
    calculated = false;
  }

  private SteamHeader findHeader(String levelName) {
    for (SteamHeader h : headers) {
      if (h.name.equals(levelName)) {
        return h;
      }
    }
    throw new IllegalArgumentException("unknown steam header: " + levelName);
  }

  /**
   * Adds a steam demand to a header.
   *
   * @param levelName header name
   * @param demandKgh steam demand in kg/h (must be non-negative)
   */
  public void addDemand(String levelName, double demandKgh) {
    if (demandKgh < 0.0) {
      throw new IllegalArgumentException("demand must be non-negative, got " + demandKgh);
    }
    findHeader(levelName).demandKgh += demandKgh;
    calculated = false;
  }

  /**
   * Sets the local steam generation at a header (replacing any previous value).
   *
   * @param levelName header name
   * @param generationKgh local generation in kg/h (must be non-negative)
   */
  public void setLocalGeneration(String levelName, double generationKgh) {
    if (generationKgh < 0.0) {
      throw new IllegalArgumentException("generation must be non-negative, got " + generationKgh);
    }
    findHeader(levelName).localGenerationKgh = generationKgh;
    calculated = false;
  }

  /**
   * Balances the steam network, sizing the make-up boiler generation and the deaerator.
   */
  public void calculate() {
    totalDemandKgh = 0.0;
    totalLocalGenerationKgh = 0.0;
    for (SteamHeader h : headers) {
      totalDemandKgh += h.demandKgh;
      totalLocalGenerationKgh += h.localGenerationKgh;
      h.inflowFromAboveKgh = 0.0;
      h.letdownToBelowKgh = 0.0;
    }

    // Bottom-up cascade: the steam a header must receive from above equals its own net deficit
    // plus the steam the header below needs (which passes through it as let-down).
    double needFromBelowReturn = 0.0;
    for (int i = headers.size() - 1; i >= 0; i--) {
      SteamHeader h = headers.get(i);
      h.letdownToBelowKgh = needFromBelowReturn;
      double required = h.demandKgh + h.letdownToBelowKgh - h.localGenerationKgh;
      h.inflowFromAboveKgh = Math.max(0.0, required);
      needFromBelowReturn = h.inflowFromAboveKgh;
    }

    boilerGenerationKgh = headers.isEmpty() ? 0.0 : headers.get(0).inflowFromAboveKgh;

    // Size the make-up boiler from the generation expressed as a thermal duty.
    double boilerDutyKW = boilerGenerationKgh * steamEnthalpyRiseKJperKg / 3600.0;
    boiler.setSteamEnthalpyRiseKJperKg(steamEnthalpyRiseKJperKg);
    boiler.addSteamDuty("Steam Network make-up", boilerDutyKW);
    boiler.calculate();

    // Size the deaerator for the boiler feedwater (fresh make-up portion needs deaeration).
    double feedwater = boiler.getFeedwaterFlowKgh();
    deaerator.setFeedwaterFlowKgh(feedwater);
    deaerator.calculate();

    calculated = true;
  }

  private void ensureCalculated() {
    if (!calculated) {
      calculate();
    }
  }

  /**
   * Builds an ordered results map suitable for JSON serialization.
   *
   * @return ordered results map
   */
  public Map<String, Object> toResultsMap() {
    ensureCalculated();
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("name", name);
    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("steamEnthalpyRise_kJ_per_kg", steamEnthalpyRiseKJperKg);
    basis.put("condensateReturnFraction", condensateReturnFraction);
    root.put("designBasis", basis);
    List<Map<String, Object>> levels = new ArrayList<Map<String, Object>>();
    for (SteamHeader h : headers) {
      Map<String, Object> level = new LinkedHashMap<String, Object>();
      level.put("name", h.name);
      level.put("pressure_bara", h.pressureBara);
      level.put("saturationTemp_C", h.saturationTempC);
      level.put("demand_kg_per_h", h.demandKgh);
      level.put("localGeneration_kg_per_h", h.localGenerationKgh);
      level.put("inflowFromAbove_kg_per_h", h.inflowFromAboveKgh);
      level.put("letdownToBelow_kg_per_h", h.letdownToBelowKgh);
      levels.add(level);
    }
    root.put("headers", levels);
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("totalDemand_kg_per_h", totalDemandKgh);
    results.put("totalLocalGeneration_kg_per_h", totalLocalGenerationKgh);
    results.put("boilerGeneration_kg_per_h", boilerGenerationKgh);
    root.put("results", results);
    root.put("boiler", boiler.toResultsMap());
    root.put("deaerator", deaerator.toResultsMap());
    return root;
  }

  /**
   * Serializes the steam network results to pretty-printed JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toResultsMap());
  }

  // ==========================================================================
  // Setters
  // ==========================================================================

  /**
   * Sets the steam enthalpy rise from feedwater to generated steam.
   *
   * @param value enthalpy rise in kJ/kg
   */
  public void setSteamEnthalpyRiseKJperKg(double value) {
    this.steamEnthalpyRiseKJperKg = value;
    calculated = false;
  }

  /**
   * Sets the condensate return fraction.
   *
   * @param value condensate return as a fraction of generated steam
   */
  public void setCondensateReturnFraction(double value) {
    this.condensateReturnFraction = value;
    calculated = false;
  }

  // ==========================================================================
  // Result getters
  // ==========================================================================

  /**
   * Gets the network name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the make-up steam generation required from the boiler.
   *
   * @return boiler generation in kg/h
   */
  public double getBoilerGenerationKgh() {
    ensureCalculated();
    return boilerGenerationKgh;
  }

  /**
   * Gets the total steam demand across all headers.
   *
   * @return total demand in kg/h
   */
  public double getTotalDemandKgh() {
    ensureCalculated();
    return totalDemandKgh;
  }

  /**
   * Gets the total local steam generation across all headers.
   *
   * @return total local generation in kg/h
   */
  public double getTotalLocalGenerationKgh() {
    ensureCalculated();
    return totalLocalGenerationKgh;
  }

  /**
   * Gets the number of steam headers.
   *
   * @return header count
   */
  public int getHeaderCount() {
    return headers.size();
  }

  /**
   * Gets the embedded make-up boiler.
   *
   * @return boiler
   */
  public Boiler getBoiler() {
    ensureCalculated();
    return boiler;
  }

  /**
   * Gets the embedded deaerator.
   *
   * @return deaerator
   */
  public Deaerator getDeaerator() {
    ensureCalculated();
    return deaerator;
  }
}
