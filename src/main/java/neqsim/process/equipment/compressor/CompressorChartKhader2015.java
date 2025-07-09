package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * * CompressorChartKader2015 is a class that implements the compressor chart calculations based on
 * the Kader 2015 method. It extends the CompressorChartAlternativeMapLookupExtrapolate class and
 * provides methods to set compressor curves based on speed, flow, head, and efficiency values. See:
 * https://github.com/EvenSol/NeqSim-Colab/discussions/12
 */
public class CompressorChartKhader2015 extends CompressorChartAlternativeMapLookupExtrapolate {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorChartKhader2015.class);
  SystemInterface fluid = null;
  double impellerOuterDiameter = 1.0;

  /**
   * Constructs a CompressorChartKader2015 object with the specified fluid and impeller diameter.
   *
   * @param fluid the working fluid for the compressor
   * @param impellerdiam the outer diameter of the impeller
   */
  public CompressorChartKhader2015(SystemInterface fluid, double impellerdiam) {
    super();
    this.fluid = fluid;
    this.impellerOuterDiameter = impellerdiam;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Sets the compressor curves based on the provided chart conditions, speed, flow, head,
   * flowPolytrpicEfficiency and polytropic efficiency values.
   * </p>
   */
  @Override
  /**
   * {@inheritDoc}
   *
   * <p>
   * Sets the compressor curves based on the provided chart conditions, speed, flow, head,
   * flowPolytrpicEfficiency and polytropic efficiency values.
   * </p>
   *
   * <p>
   * <b>Mathematical background (see Kader 2015):</b><br>
   * The method normalizes compressor map data using the following relations:
   * <ul>
   * <li><b>Corrected Head Factor:</b> H<sub>corr</sub> = H / c<sub>s</sub><sup>2</sup></li>
   * <li><b>Corrected Flow Factor:</b> Q<sub>corr</sub> = Q / (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Corrected Flow Factor for Efficiency:</b> Q<sub>corr,eff</sub> = Q<sub>eff</sub> /
   * (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Polytropic Efficiency:</b> &eta;<sub>p</sub> = &eta;<sub>p</sub></li>
   * <li><b>Machine Mach Number:</b> Ma = N D / c<sub>s</sub></li>
   * </ul>
   * where:
   * <ul>
   * <li>H = head</li>
   * <li>Q = flow</li>
   * <li>Q<sub>eff</sub> = flow for efficiency</li>
   * <li>N = speed</li>
   * <li>D = impeller outer diameter</li>
   * <li>c<sub>s</sub> = sound speed of the fluid</li>
   * </ul>
   * These dimensionless numbers allow for comparison and extrapolation of compressor performance
   * across different conditions, as described in Kader (2015) and the referenced NeqSim discussion.
   * </p>
   */
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] flowPolyEff, double[][] polyEff) {

    double fluidSoundSpeed = createDefaultFluid(chartConditions);

    double[] machNumberCorrectedHeadFactor = new double[speed.length];
    double[] machNumberCorrectedFlowFactor = new double[speed.length];
    double[] machNumberCorrectedFlowFactorEfficiency = new double[speed.length];
    double[] polEff = new double[speed.length];

    for (int i = 0; i < speed.length; i++) {
      for (int j = 0; j < flow[i].length; j++) {
        machNumberCorrectedHeadFactor[j] = head[i][j] / fluidSoundSpeed / fluidSoundSpeed;
        machNumberCorrectedFlowFactor[j] =
            flow[i][j] / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
        machNumberCorrectedFlowFactorEfficiency[j] =
            flowPolyEff[i][j] / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
        polEff[j] = polyEff[i][j];
      }
      double machineMachNumber = speed[i] * impellerOuterDiameter / fluidSoundSpeed;

      CompressorCurve curve = new CompressorCurve(machineMachNumber, machNumberCorrectedFlowFactor,
          machNumberCorrectedHeadFactor, machNumberCorrectedFlowFactorEfficiency, polEff);
      chartValues.add(curve);
      chartSpeeds.add(speed[i]);
    }

    setUseCompressorChart(true);
  }

  /**
   * Creates and initializes a default fluid for compressor chart calculations.
   *
   * @param chartConditions array with temperature and pressure
   * @return the sound speed of the fluid
   */
  private double createDefaultFluid(double[] chartConditions) {
    // Set moles so that the molecular weight matches chartConditions[3] (if provided), by varying
    // propane
    double methaneFrac = 0.90;
    double ethaneFrac = 0.05;
    double propaneFrac = 0.05;
    double targetMolWeight = (chartConditions.length > 3) ? chartConditions[3] : -1.0;

    // Molar masses [g/mol]
    double mwMethane = 16.043;
    double mwEthane = 30.07;
    double mwPropane = 44.097;

    if (targetMolWeight > 0.0) {
      // Solve for propaneFrac so that total mole fraction = 1.0 and molecular weight matches target
      // MW = x1*mw1 + x2*mw2 + x3*mw3
      // x1 = methaneFrac, x2 = ethaneFrac, x3 = 1 - x1 - x2
      // targetMolWeight = x1*mw1 + x2*mw2 + (1-x1-x2)*mw3
      // => x3 = (targetMolWeight - x1*mw1 - x2*mw2) / (mw3 - mw1 - mw2)
      double x1 = methaneFrac;
      double x2 = ethaneFrac;
      double numerator = targetMolWeight - x1 * mwMethane - x2 * mwEthane;
      double denominator = mwPropane - mwMethane - mwEthane;
      double x3 = numerator / denominator;
      // Clamp x3 to [0,1], adjust x1 and x2 if needed
      if (x3 < 0.0)
        x3 = 0.0;
      if (x3 > 1.0)
        x3 = 1.0;
      double sum = x1 + x2 + x3;
      // Normalize if sum != 1.0
      x1 /= sum;
      x2 /= sum;
      x3 /= sum;
      methaneFrac = x1;
      ethaneFrac = x2;
      propaneFrac = x3;
    }

    fluid = new neqsim.thermo.system.SystemPrEos(273.15 + 20.0, 1.0e5);
    fluid.addComponent("methane", methaneFrac);
    fluid.addComponent("ethane", ethaneFrac);
    fluid.addComponent("propane", propaneFrac);
    fluid.init(0);
    fluid.setTemperature(chartConditions[0]);
    fluid.setPressure(chartConditions[1]);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.initThermoProperties();
    return fluid.getSoundSpeed();
  }


  /**
   * Calculates the polytropic head for a given flow and speed.
   *
   * <p>
   * The method first converts the input flow and speed to dimensionless numbers using the sound
   * speed and impeller diameter:
   * <ul>
   * <li><b>Corrected Flow Factor:</b> Q<sub>corr</sub> = Q / (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Machine Mach Number:</b> Ma = N D / c<sub>s</sub></li>
   * </ul>
   * It then interpolates/extrapolates the polytropic head from the reference compressor curves in
   * this dimensionless space, and finally converts the result back to physical units by multiplying
   * with c<sub>s</sub><sup>2</sup>.
   * </p>
   *
   * @param flow volumetric flow rate
   * @param speed rotational speed
   * @return polytropic head in physical units
   */
  @Override
  public double getPolytropicHead(double flow, double speed) {
    double fluidSoundSpeed = fluid.getSoundSpeed();
    double machNumberCorrectedFlowFactor =
        flow / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
    double machineMachNumber = speed * impellerOuterDiameter / fluidSoundSpeed;
    return super.getPolytropicHead(machNumberCorrectedFlowFactor, machineMachNumber)
        * fluidSoundSpeed * fluidSoundSpeed;
  }


  /**
   * Calculates the polytropic efficiency for a given flow and speed.
   *
   * <p>
   * The method first converts the input flow and speed to dimensionless numbers using the sound
   * speed and impeller diameter:
   * <ul>
   * <li><b>Corrected Flow Factor:</b> Q<sub>corr</sub> = Q / (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Machine Mach Number:</b> Ma = N D / c<sub>s</sub></li>
   * </ul>
   * It then interpolates/extrapolates the polytropic efficiency from the reference compressor
   * curves in this dimensionless space.
   * </p>
   *
   * @param flow volumetric flow rate
   * @param speed rotational speed
   * @return polytropic efficiency (dimensionless)
   */
  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    double fluidSoundSpeed = fluid.getSoundSpeed();
    double machNumberCorrectedFlowFactor =
        flow / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
    double machineMachNumber = speed * impellerOuterDiameter / fluidSoundSpeed;
    return super.getPolytropicEfficiency(machNumberCorrectedFlowFactor, machineMachNumber);
  }
}
