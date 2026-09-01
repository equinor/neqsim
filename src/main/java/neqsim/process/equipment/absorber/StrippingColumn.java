package neqsim.process.equipment.absorber;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Rigorous counter-current equilibrium-tray stripper based on the distillation MESH solver.
 *
 * <p>
 * The stripping gas enters tray zero and the rich liquid enters the highest numbered tray. Tray numbers are zero based
 * from bottom to top. The class reuses {@link AbsorptionColumn} because absorption and stripping use the same
 * counter-current equilibrium-stage equations; the thermodynamic driving force determines the direction of component
 * transfer.
 * </p>
 *
 * <p>
 * Overall, per-tray, per-component, and per-tray/per-component Murphree vapor efficiencies are inherited. The gas and
 * liquid outlet streams remain ordinary NeqSim streams, and all inherited convergence, MESH residual, material-balance,
 * energy-diagnostic, warm-start, and solver-selection APIs remain available.
 * </p>
 *
 * @author esolbraa
 * @version $Id: $Id
 */
public class StrippingColumn extends AbsorptionColumn {
  private static final long serialVersionUID = 1000L;

  /**
   * Create a counter-current tray stripper without a condenser or reboiler.
   *
   * @param name equipment name
   * @param numberOfTrays number of actual trays
   */
  public StrippingColumn(String name, int numberOfTrays) {
    super(name, numberOfTrays);
  }

  /**
   * Add stripping gas to the bottom tray.
   *
   * @param stream stripping-gas feed
   */
  public void addStrippingGasStream(StreamInterface stream) {
    addGasInStream(stream);
  }

  /**
   * Add rich liquid to the top tray.
   *
   * @param stream rich-liquid feed
   */
  public void addRichLiquidStream(StreamInterface stream) {
    addSolventInStream(stream);
  }

  /**
   * Get the stripping-gas feed.
   *
   * @return stripping-gas inlet stream, or {@code null} before assignment
   */
  public StreamInterface getStrippingGasStream() {
    return getGasInStream();
  }

  /**
   * Get the rich-liquid feed.
   *
   * @return rich-liquid inlet stream, or {@code null} before assignment
   */
  public StreamInterface getRichLiquidStream() {
    return getSolventInStream();
  }

  /**
   * Get the overhead gas product.
   *
   * @return overhead gas stream
   */
  public StreamInterface getOverheadGasStream() {
    return getGasOutStream();
  }

  /**
   * Get the lean liquid product.
   *
   * @return lean liquid stream
   */
  public StreamInterface getLeanLiquidStream() {
    return getLiquidOutStream();
  }
}
