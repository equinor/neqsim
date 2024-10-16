package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RachigRingPackingTest {
  static RachigRingPacking rp;

  @BeforeAll
  static void setUp() {
    rp = new RachigRingPacking();
  }

  @Test
  void testGetSize() {
    double origSize = rp.getSize();
    Assertions.assertEquals(origSize, rp.getSize());
    double newSize = origSize + 1;
    rp.setSize(newSize);
    Assertions.assertEquals(newSize, rp.getSize());
  }

  @Test
  void testGetSurfaceAreaPrVolume() {
    double origSize = rp.getSurfaceAreaPrVolume();
    Assertions.assertEquals(origSize, rp.getSurfaceAreaPrVolume());
  }

  @Test
  void testGetVoidFractionPacking() {
    double origSize = rp.getVoidFractionPacking();
    Assertions.assertEquals(origSize, rp.getVoidFractionPacking());
    double newSize = origSize + 1;
    rp.setVoidFractionPacking(newSize);
    Assertions.assertEquals(newSize, rp.getVoidFractionPacking());
  }

  @Test
  void testGetName() {
    String origName = rp.getName();
    Assertions.assertEquals(origName, rp.getName());
    String newName = origName + "_1";
    rp.setName(newName);
    Assertions.assertEquals(newName, rp.getName());
  }
}
