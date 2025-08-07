package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;

/**
 * An interface for defining the flow characteristic of a valve. This allows for implementing
 * different valve behaviors like linear, equal percentage, etc.
 */
public interface ValveCharacteristic extends Serializable {

  public double getActualKv(double Kv, double percentOpening);

  public double getOpeningFactor(double percentOpening);
}
