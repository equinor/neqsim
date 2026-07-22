/*
 * MixerInterface.java
 *
 * Created on 21. august 2001, 22:28
 */

package neqsim.process.equipment.mixer;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Interface for processEquipment with multiple inlet streams and a single outlet stream.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface MixerInterface extends ProcessEquipmentInterface {
  /**
   * addStream.
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addStream(StreamInterface newStream);

  /**
   * Getter for outlet stream object.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutletStream();

  /**
   * Getter for outlet stream object.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @deprecated use {@link #getOutletStream} instead
   */
  @Deprecated
  public default StreamInterface getOutStream() {
    return getOutletStream();
  }

  /**
   * replaceStream.
   *
   * @param i a int
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void replaceStream(int i, StreamInterface newStream);

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem();

  /**
   * removeInputStream.
   *
   * @param i a int
   */
  public void removeInputStream(int i);
}
