package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Root-cause-analysis helper for locating plausible elemental-sulfur formation mechanisms in a
 * process.
 *
 * <p>The analysis is intentionally mechanistic and screening-oriented. It does not claim a
 * universal kinetic rate for sulfur chemistry. Instead, each process location is evaluated from
 * its local temperature, pressure, H2S/O2/water availability, residence-time proxy and configured
 * surface state. This makes the result suitable for RCA work where the objective is to rank where a
 * reaction could have occurred and explain why.</p>
 *
 * <p>Three mechanisms are currently scored: homogeneous H2S/O2 oxidation, heterogeneous H2S/O2
 * oxidation on reactive surfaces, and oxidation of historical FeS scale during oxygen ingress.
 * Surface-specific kinetic models can later replace the screening factors without changing the RCA
 * result contract.</p>
 */
public class SulfurRcaAnalysis implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Surface state used when evaluating a process location. */
  public static class SurfaceState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private String material = "carbon steel";
    private double wettedFraction = 0.0;
    private double reactiveIronOxideFraction = 0.0;
    private double ironSulfideCoverageFraction = 0.0;
    private double relativeSurfaceArea = 1.0;
    private double wallTemperatureC = Double.NaN;

    public SurfaceState setMaterial(String material) {
      this.material = material;
      return this;
    }

    public SurfaceState setWettedFraction(double value) {
      this.wettedFraction = clamp01(value);
      return this;
    }

    public SurfaceState setReactiveIronOxideFraction(double value) {
      this.reactiveIronOxideFraction = clamp01(value);
      return this;
    }

    public SurfaceState setIronSulfideCoverageFraction(double value) {
      this.ironSulfideCoverageFraction = clamp01(value);
      return this;
    }

    public SurfaceState setRelativeSurfaceArea(double value) {
      this.relativeSurfaceArea = Math.max(0.0, value);
      return this;
    }

    public SurfaceState setWallTemperatureC(double value) {
      this.wallTemperatureC = value;
      return this;
    }
  }

  /** Ranked RCA result for one process location. */
  public static class LocationResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    public final String location;
    public final double temperatureC;
    public final double pressureBara;
    public final double h2sMoleFraction;
    public final double oxygenMoleFraction;
    public final double waterMoleFraction;
    public final double homogeneousOxidationScore;
    public final double surfaceOxidationScore;
    public final double ironSulfideOxidationScore;
    public final double overallScore;
    public final String dominantMechanism;
    public final List<String> contributingFactors;

    private LocationResult(String location, double temperatureC, double pressureBara,
        double h2sMoleFraction, double oxygenMoleFraction, double waterMoleFraction,
        double homogeneousOxidationScore, double surfaceOxidationScore,
        double ironSulfideOxidationScore, List<String> contributingFactors) {
      this.location = location;
      this.temperatureC = temperatureC;
      this.pressureBara = pressureBara;
      this.h2sMoleFraction = h2sMoleFraction;
      this.oxygenMoleFraction = oxygenMoleFraction;
      this.waterMoleFraction = waterMoleFraction;
      this.homogeneousOxidationScore = homogeneousOxidationScore;
      this.surfaceOxidationScore = surfaceOxidationScore;
      this.ironSulfideOxidationScore = ironSulfideOxidationScore;
      this.overallScore = Math.max(homogeneousOxidationScore,
          Math.max(surfaceOxidationScore, ironSulfideOxidationScore));
      if (ironSulfideOxidationScore == overallScore) {
        this.dominantMechanism = "FeS oxidation during oxygen ingress";
      } else if (surfaceOxidationScore == overallScore) {
        this.dominantMechanism = "heterogeneous H2S oxidation on reactive surface";
      } else {
        this.dominantMechanism = "homogeneous H2S/O2 oxidation";
      }
      this.contributingFactors = Collections.unmodifiableList(contributingFactors);
    }
  }

  private final ProcessSystem processSystem;
  private final Map<String, SurfaceState> surfaceStates = new LinkedHashMap<>();
  private final List<LocationResult> results = new ArrayList<>();
  private double defaultResidenceTimeSeconds = 1.0;

  public SulfurRcaAnalysis(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
  }

  public void setDefaultResidenceTimeSeconds(double seconds) {
    defaultResidenceTimeSeconds = Math.max(0.0, seconds);
  }

  public void setSurfaceState(String equipmentName, SurfaceState state) {
    if (equipmentName == null || state == null) {
      throw new IllegalArgumentException("equipmentName and state must be non-null");
    }
    surfaceStates.put(equipmentName, state);
  }

  /** Run the RCA screening on all process equipment with an accessible outlet or inlet stream. */
  public void run() {
    results.clear();
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      StreamInterface stream = extractRepresentativeStream(equipment);
      if (stream == null || stream.getThermoSystem() == null) {
        continue;
      }
      results.add(analyse(equipment.getName(), stream.getThermoSystem(),
          surfaceStates.getOrDefault(equipment.getName(), new SurfaceState())));
    }
    results.sort(Comparator.comparingDouble((LocationResult r) -> r.overallScore).reversed());
  }

  public List<LocationResult> getResults() {
    return Collections.unmodifiableList(results);
  }

  public LocationResult getHighestRiskLocation() {
    return results.isEmpty() ? null : results.get(0);
  }

  private LocationResult analyse(String name, SystemInterface system, SurfaceState surface) {
    double temperatureC = system.getTemperature() - 273.15;
    double pressureBara = system.getPressure();
    double h2s = moleFraction(system, "H2S");
    double oxygen = Math.max(moleFraction(system, "oxygen"), moleFraction(system, "O2"));
    double water = moleFraction(system, "water");
    double wallTemperatureC = Double.isFinite(surface.wallTemperatureC)
        ? surface.wallTemperatureC : temperatureC;

    // Smooth screening windows. They intentionally rank opportunity rather than predict an
    // absolute rate. This keeps RCA useful before a literature/calibrated kinetic set is selected.
    double reactantAvailability = availability(h2s, 1.0e-6) * availability(oxygen, 1.0e-6);
    double homogeneousTemperature = gaussianWindow(temperatureC, 180.0, 120.0);
    double homogeneous = reactantAvailability * homogeneousTemperature
        * residenceFactor(defaultResidenceTimeSeconds);

    double wetting = Math.max(surface.wettedFraction, availability(water, 1.0e-4) * 0.5);
    double reactiveSurface = clamp01(surface.reactiveIronOxideFraction
        + 0.5 * surface.ironSulfideCoverageFraction);
    double surfaceTemperature = gaussianWindow(wallTemperatureC, 60.0, 80.0);
    double heterogeneous = reactantAvailability * surfaceTemperature * (0.25 + 0.75 * wetting)
        * reactiveSurface * surface.relativeSurfaceArea;

    double fesOxidation = availability(oxygen, 1.0e-6)
        * surface.ironSulfideCoverageFraction * (0.2 + 0.8 * wetting)
        * gaussianWindow(wallTemperatureC, 40.0, 70.0) * surface.relativeSurfaceArea;

    List<String> factors = new ArrayList<>();
    if (h2s > 1.0e-6) factors.add("H2S present");
    if (oxygen > 1.0e-6) factors.add("oxygen present");
    if (wetting > 0.2) factors.add("wet/reactive wall");
    if (surface.ironSulfideCoverageFraction > 0.1) factors.add("historical FeS scale available");
    if (surface.reactiveIronOxideFraction > 0.1) factors.add("reactive iron-oxide surface available");
    if (Math.abs(wallTemperatureC - temperatureC) > 5.0) factors.add("wall temperature differs from bulk fluid");
    if (surface.relativeSurfaceArea > 1.5) factors.add("elevated reactive surface area");
    factors.add("surface material: " + surface.material);

    return new LocationResult(name, temperatureC, pressureBara, h2s, oxygen, water,
        homogeneous, heterogeneous, fesOxidation, factors);
  }

  private static StreamInterface extractRepresentativeStream(ProcessEquipmentInterface equipment) {
    try {
      Object value = equipment.getClass().getMethod("getOutletStream").invoke(equipment);
      if (value instanceof StreamInterface) return (StreamInterface) value;
    } catch (Exception ignored) {
      // Try inlet below.
    }
    try {
      Object value = equipment.getClass().getMethod("getInletStream").invoke(equipment);
      if (value instanceof StreamInterface) return (StreamInterface) value;
    } catch (Exception ignored) {
      // Equipment has no stream-like public accessor.
    }
    return null;
  }

  private static double moleFraction(SystemInterface system, String componentName) {
    try {
      return Math.max(0.0, system.getComponent(componentName).getz());
    } catch (Exception ex) {
      return 0.0;
    }
  }

  private static double availability(double value, double scale) {
    if (value <= 0.0) return 0.0;
    return clamp01(value / (value + scale));
  }

  private static double residenceFactor(double seconds) {
    return clamp01(seconds / (seconds + 10.0));
  }

  private static double gaussianWindow(double value, double optimum, double width) {
    double x = (value - optimum) / Math.max(width, 1.0e-9);
    return Math.exp(-0.5 * x * x);
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
