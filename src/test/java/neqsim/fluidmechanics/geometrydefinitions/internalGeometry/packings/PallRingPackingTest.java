package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PallRingPackingTest {
  static PallRingPacking prp;

  @BeforeAll
  static void setUp() {
    prp = new PallRingPacking();
  }

  @Test
  void testGetSize() {
    double origSize = prp.getSize();
    Assertions.assertEquals(origSize, prp.getSize());
    double newSize = origSize + 1;
    prp.setSize(newSize);
    Assertions.assertEquals(newSize, prp.getSize());
  }

  @Test
  void testGetSurfaceAreaPrVolume() {
    double origSize = prp.getSurfaceAreaPrVolume();
    Assertions.assertEquals(origSize, prp.getSurfaceAreaPrVolume());
  }

  @Test
  void testGetVoidFractionPacking() {
    double origSize = prp.getVoidFractionPacking();
    Assertions.assertEquals(origSize, prp.getVoidFractionPacking());
    double newSize = origSize + 1;
    prp.setVoidFractionPacking(newSize);
    Assertions.assertEquals(newSize, prp.getVoidFractionPacking());
  }

  @Test
  void testGetName() {
    String origName = prp.getName();
    Assertions.assertEquals(origName, prp.getName());
    String newName = origName + "_1";
    prp.setName(newName);
    Assertions.assertEquals(newName, prp.getName());
  }
}
