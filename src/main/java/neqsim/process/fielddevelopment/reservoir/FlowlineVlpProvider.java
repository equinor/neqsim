package neqsim.process.fielddevelopment.reservoir;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Lift model of a flowline, riser or any chain of pipe segments between two network nodes, for network branch tables
 * and for live network back-pressure calculations.
 *
 * <p>
 * Each coordinate (surface rate, outlet pressure, water ratio, gas ratio) is recombined to a compositional stream by
 * the {@link VfpFluidFactory} and sent through a chain of {@link PipeBeggsAndBrills} segments. The inlet pressure that
 * delivers the requested outlet pressure is found with {@link ForwardVlpProvider}, so the value returned by
 * {@link #bottomHolePressure(double, double, double, double, double)} is the pressure at the inlet node of the branch.
 * A failed pipe march (pressure collapsing below zero) is treated as a collapsed profile so that the inverse solve
 * moves to a higher inlet pressure.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see TubingVlpProvider
 * @see ForwardVlpProvider
 */
public class FlowlineVlpProvider implements VlpProvider {
  private static final Logger logger = LogManager.getLogger(FlowlineVlpProvider.class);
  private final VfpFluidFactory fluidFactory;
  private final List<double[]> segments = new ArrayList<double[]>();
  private final ForwardVlpProvider inverse;
  private double roughnessM = 4.5e-5;
  private double inletTemperatureC = 60.0;
  private double ambientTemperatureC = 5.0;
  private double heatTransferCoefficient = Double.NaN;
  private int incrementsPerSegment = 10;
  private int pipeRuns = 0;
  private int failures = 0;
  private double lastOutletTemperatureC = Double.NaN;

  /**
   * Creates a provider without segments; add at least one with {@link #addSegment(double, double, double)}.
   *
   * @param fluidFactory creates the stream for each coordinate, on the rate basis of the table or network
   */
  public FlowlineVlpProvider(VfpFluidFactory fluidFactory) {
    this.fluidFactory = fluidFactory;
    this.inverse = new ForwardVlpProvider(new ForwardVlpProvider.ForwardPressureModel() {
      @Override
      public double outletPressure(double rate, double inletPressure, double waterRatio, double gasRatio, double alq) {
        return forward(rate, inletPressure, waterRatio, gasRatio, alq);
      }
    });
    this.inverse.setCollapsedProfileDrop(50.0);
    this.inverse.setInletBounds(1.0, 1000.0);
  }

  /**
   * Adds a pipe segment at the downstream end of the chain.
   *
   * @param lengthM segment length in m
   * @param elevationChangeM outlet elevation minus inlet elevation in m (positive for upward flow)
   * @param innerDiameterM inner diameter in m
   * @return this provider
   * @throws IllegalArgumentException if the length or diameter is not positive
   */
  public FlowlineVlpProvider addSegment(double lengthM, double elevationChangeM, double innerDiameterM) {
    if (lengthM <= 0.0 || innerDiameterM <= 0.0) {
      throw new IllegalArgumentException("Segment length and diameter must be positive");
    }
    segments.add(new double[] {lengthM, elevationChangeM, innerDiameterM});
    return this;
  }

  /**
   * Sets the wall roughness of all segments.
   *
   * @param roughnessM absolute roughness in m
   * @return this provider
   */
  public FlowlineVlpProvider setRoughness(double roughnessM) {
    this.roughnessM = roughnessM;
    return this;
  }

  /**
   * Sets the fluid temperature at the inlet node.
   *
   * @param temperatureC inlet temperature in C
   * @return this provider
   */
  public FlowlineVlpProvider setInletTemperature(double temperatureC) {
    this.inletTemperatureC = temperatureC;
    return this;
  }

  /**
   * Sets heat exchange with the surroundings. Without this call the chain is adiabatic.
   *
   * @param ambientTemperatureC ambient (seabed) temperature in C
   * @param overallHeatTransferCoefficient overall U-value in W/(m2 K), referred to the inner diameter
   * @return this provider
   */
  public FlowlineVlpProvider setAmbient(double ambientTemperatureC, double overallHeatTransferCoefficient) {
    this.ambientTemperatureC = ambientTemperatureC;
    this.heatTransferCoefficient = overallHeatTransferCoefficient;
    return this;
  }

  /**
   * Sets the number of calculation increments in every segment.
   *
   * @param increments number of increments (at least 1)
   * @return this provider
   */
  public FlowlineVlpProvider setIncrementsPerSegment(int increments) {
    this.incrementsPerSegment = Math.max(1, increments);
    return this;
  }

  /**
   * Sets the pressure tolerance of the inlet pressure solve.
   *
   * @param bar absolute tolerance in bar
   * @return this provider
   */
  public FlowlineVlpProvider setTolerance(double bar) {
    inverse.setTolerance(bar);
    return this;
  }

  /**
   * Sets the search bounds of the inlet pressure solve.
   *
   * @param minBar lower bound in bara
   * @param maxBar upper bound in bara
   * @return this provider
   */
  public FlowlineVlpProvider setInletBounds(double minBar, double maxBar) {
    inverse.setInletBounds(minBar, maxBar);
    return this;
  }

  /**
   * Number of pipe-chain runs used so far (several per coordinate because of the inverse solve).
   *
   * @return pipe-chain run count
   */
  public int getPipeRuns() {
    return pipeRuns;
  }

  /**
   * Number of pipe-chain runs that failed or collapsed.
   *
   * @return failure count
   */
  public int getFailures() {
    return failures;
  }

  /**
   * Outlet temperature of the most recent successful pipe-chain run.
   *
   * @return outlet temperature in C, NaN before the first successful run
   */
  public double getLastOutletTemperatureC() {
    return lastOutletTemperatureC;
  }

  /**
   * Runs the pipe chain from the inlet node.
   *
   * @param rate surface rate in Sm3/day on the fluid factory's rate basis
   * @param inletPressure inlet node pressure in bara
   * @param waterRatio water ratio on the fluid factory's basis
   * @param gasRatio gas ratio on the fluid factory's basis
   * @param alq artificial lift quantity in Sm3/day
   * @return outlet pressure in bara, NaN if the march fails
   * @throws IllegalStateException if no segment has been added
   */
  public double forward(double rate, double inletPressure, double waterRatio, double gasRatio, double alq) {
    if (segments.isEmpty()) {
      throw new IllegalStateException("Add at least one segment before using the flowline model");
    }
    pipeRuns++;
    try {
      SystemInterface fluid = fluidFactory.createWellStream(rate, waterRatio, gasRatio, alq);
      Stream feed = new Stream("flowline inlet", fluid);
      feed.setTemperature(inletTemperatureC, "C");
      feed.setPressure(inletPressure, "bara");
      feed.run();
      StreamInterface current = feed;
      for (int i = 0; i < segments.size(); i++) {
        double[] segment = segments.get(i);
        PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("segment " + (i + 1), current);
        pipe.setLength(segment[0]);
        pipe.setElevation(segment[1]);
        pipe.setDiameter(segment[2]);
        pipe.setPipeWallRoughness(roughnessM);
        pipe.setNumberOfIncrements(incrementsPerSegment);
        if (Double.isNaN(heatTransferCoefficient)) {
          pipe.setHeatTransferMode(PipeBeggsAndBrills.HeatTransferMode.ADIABATIC);
        } else {
          pipe.setConstantSurfaceTemperature(ambientTemperatureC, "C");
          pipe.setHeatTransferCoefficient(heatTransferCoefficient);
        }
        pipe.run();
        current = pipe.getOutletStream();
        double p = current.getPressure("bara");
        if (Double.isNaN(p) || Double.isInfinite(p) || p <= 0.0) {
          failures++;
          return Double.NaN;
        }
      }
      lastOutletTemperatureC = current.getTemperature("C");
      return current.getPressure("bara");
    } catch (RuntimeException ex) {
      failures++;
      logger.debug("Flowline point rate {} inlet {} failed: {}", rate, inletPressure, ex.toString());
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double bottomHolePressure(double rate, double outletPressure, double waterRatio, double gasRatio, double alq) {
    return inverse.bottomHolePressure(rate, outletPressure, waterRatio, gasRatio, alq);
  }
}
