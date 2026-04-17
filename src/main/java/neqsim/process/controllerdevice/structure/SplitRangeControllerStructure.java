package neqsim.process.controllerdevice.structure;

import neqsim.process.controllerdevice.ControllerDeviceInterface;

/**
 * Split-range control structure where a single controller drives two or more final control elements
 * (e.g. valves). Each element is assigned a sub-range of the controller output (0-100%). For
 * example, valve A may operate from 0-50% while valve B operates from 50-100%.
 *
 * <p>
 * After {@link #runTransient(double)} the individual outputs can be queried with
 * {@link #getOutput(int)}. The {@link #getOutput()} method returns the raw controller output.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SplitRangeControllerStructure implements ControlStructureInterface {
  private static final long serialVersionUID = 1000;

  private final ControllerDeviceInterface controller;
  private final double[] rangeLow;
  private final double[] rangeHigh;
  private final double[] elementOutputs;
  private double rawOutput = 0.0;
  private boolean isActive = true;

  /**
   * Create a split-range structure with equal-width ranges for the specified number of elements.
   * For two elements: element 0 gets 0-50%, element 1 gets 50-100%.
   *
   * @param controller the single controller driving the split-range
   * @param numberOfElements number of final control elements
   */
  public SplitRangeControllerStructure(ControllerDeviceInterface controller, int numberOfElements) {
    if (numberOfElements < 2) {
      throw new IllegalArgumentException("Split-range requires at least 2 elements.");
    }
    this.controller = controller;
    this.rangeLow = new double[numberOfElements];
    this.rangeHigh = new double[numberOfElements];
    this.elementOutputs = new double[numberOfElements];
    double width = 100.0 / numberOfElements;
    for (int i = 0; i < numberOfElements; i++) {
      rangeLow[i] = i * width;
      rangeHigh[i] = (i + 1) * width;
    }
  }

  /**
   * Create a split-range structure with custom ranges for each element.
   *
   * @param controller the single controller driving the split-range
   * @param rangeLow array of lower bounds (0-100%) for each element
   * @param rangeHigh array of upper bounds (0-100%) for each element
   */
  public SplitRangeControllerStructure(ControllerDeviceInterface controller, double[] rangeLow,
      double[] rangeHigh) {
    if (rangeLow.length != rangeHigh.length || rangeLow.length < 2) {
      throw new IllegalArgumentException(
          "rangeLow and rangeHigh must have equal length and at least 2 elements.");
    }
    this.controller = controller;
    this.rangeLow = rangeLow.clone();
    this.rangeHigh = rangeHigh.clone();
    this.elementOutputs = new double[rangeLow.length];
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    if (!isActive) {
      return;
    }
    controller.runTransient(controller.getResponse(), dt);
    rawOutput = controller.getResponse();

    for (int i = 0; i < elementOutputs.length; i++) {
      if (rawOutput <= rangeLow[i]) {
        elementOutputs[i] = 0.0;
      } else if (rawOutput >= rangeHigh[i]) {
        elementOutputs[i] = 100.0;
      } else {
        double span = rangeHigh[i] - rangeLow[i];
        elementOutputs[i] = (rawOutput - rangeLow[i]) / span * 100.0;
      }
    }
  }

  /**
   * Get the scaled output for a specific element after split-range mapping.
   *
   * @param elementIndex zero-based index of the final control element
   * @return output for the element scaled 0-100%
   */
  public double getOutput(int elementIndex) {
    if (elementIndex < 0 || elementIndex >= elementOutputs.length) {
      throw new IndexOutOfBoundsException("Element index out of range: " + elementIndex);
    }
    return elementOutputs[elementIndex];
  }

  /**
   * Get the number of final control elements in this split-range structure.
   *
   * @return number of elements
   */
  public int getNumberOfElements() {
    return elementOutputs.length;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutput() {
    return rawOutput;
  }

  /** {@inheritDoc} */
  @Override
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return isActive;
  }
}
