/*
 * Copyright 2016-17, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.sampling;

import static com.yahoo.sketches.sampling.PreambleUtil.FAMILY_BYTE;
import static com.yahoo.sketches.sampling.PreambleUtil.PREAMBLE_LONGS_BYTE;
import static com.yahoo.sketches.sampling.PreambleUtil.RESERVOIR_SIZE_INT;
import static com.yahoo.sketches.sampling.PreambleUtil.RESERVOIR_SIZE_SHORT;
import static com.yahoo.sketches.sampling.PreambleUtil.SER_VER_BYTE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.Test;

import com.yahoo.memory.Memory;
import com.yahoo.memory.NativeMemory;
import com.yahoo.sketches.Family;
import com.yahoo.sketches.SketchesArgumentException;

public class ReservoirLongsUnionTest {
  @Test
  public void checkEmptyUnion() {
    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(1024);
    final byte[] unionBytes = rlu.toByteArray();

    // will intentionally break if changing empty union serialization
    assertEquals(unionBytes.length, 8);

    println(rlu.toString());
  }

  @Test
  public void checkInstantiation() {
    final int n = 100;
    final int k = 25;

    // create empty unions
    ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(k);
    assertNull(rlu.getResult());
    rlu.update(5);
    assertNotNull(rlu.getResult());

    // pass in a sketch, as both an object and memory
    final ReservoirLongsSketch rls = ReservoirLongsSketch.getInstance(k);
    for (int i = 0; i < n; ++i) {
      rls.update(i);
    }

    rlu = ReservoirLongsUnion.getInstance(rls.getK());
    rlu.update(rls);
    assertNotNull(rlu.getResult());

    final byte[] sketchBytes = rls.toByteArray();
    final Memory mem = new NativeMemory(sketchBytes);
    rlu = ReservoirLongsUnion.getInstance(rls.getK());
    rlu.update(mem);
    assertNotNull(rlu.getResult());

    println(rlu.toString());
  }

  @Test
  public void checkNullUpdate() {
    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(1024);
    assertNull(rlu.getResult());

    // null sketch
    rlu.update((ReservoirLongsSketch) null);
    assertNull(rlu.getResult());

    // null memory
    rlu.update((Memory) null);
    assertNull(rlu.getResult());

    // valid input
    rlu.update(5L);
    assertNotNull(rlu.getResult());
  }

  @Test
  public void checkSerialization() {
    final int n = 100;
    final int k = 25;

    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(k);
    for (int i = 0; i < n; ++i) {
      rlu.update(i);
    }

    final byte[] unionBytes = rlu.toByteArray();
    final Memory mem = new NativeMemory(unionBytes);

    final ReservoirLongsUnion rebuiltUnion = ReservoirLongsUnion.getInstance(mem);
    validateUnionEquality(rlu, rebuiltUnion);
  }

  @Test
  public void checkVersionConversionWithEmptyGadget() {
    final int k = 32768;
    final short encK = ReservoirSize.computeSize(k);

    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(k);
    final byte[] unionBytesOrig = rlu.toByteArray();

    // get a new byte[], manually revert to v1, then reconstruct
    final byte[] unionBytes = rlu.toByteArray();
    final Memory unionMem = new NativeMemory(unionBytes);

    unionMem.putByte(SER_VER_BYTE, (byte) 1);
    unionMem.putInt(RESERVOIR_SIZE_INT, 0); // zero out all 4 bytes
    unionMem.putShort(RESERVOIR_SIZE_SHORT, encK);
    println(PreambleUtil.preambleToString(unionMem));

    final ReservoirLongsUnion rebuilt = ReservoirLongsUnion.getInstance(unionMem);
    final byte[] rebuiltBytes = rebuilt.toByteArray();

    assertEquals(unionBytesOrig.length, rebuiltBytes.length);
    for (int i = 0; i < unionBytesOrig.length; ++i) {
      assertEquals(unionBytesOrig[i], rebuiltBytes[i]);
    }
  }

  @Test
  public void checkVersionConversionWithGadget() {
    final long n = 32;
    final int k = 256;
    final short encK = ReservoirSize.computeSize(k);

    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(k);
    for (long i = 0; i < n; ++i) {
      rlu.update(i);
    }
    final byte[] unionBytesOrig = rlu.toByteArray();

    // get a new byte[], manually revert to v1, then reconstruct
    final byte[] unionBytes = rlu.toByteArray();
    final Memory unionMem = new NativeMemory(unionBytes);

    unionMem.putByte(SER_VER_BYTE, (byte) 1);
    unionMem.putInt(RESERVOIR_SIZE_INT, 0); // zero out all 4 bytes
    unionMem.putShort(RESERVOIR_SIZE_SHORT, encK);

    // force gadget header to v1, too
    final int offset = Family.RESERVOIR_UNION.getMaxPreLongs() << 3;
    unionMem.putByte(offset + SER_VER_BYTE, (byte) 1);
    unionMem.putInt(offset + RESERVOIR_SIZE_INT, 0); // zero out all 4 bytes
    unionMem.putShort(offset + RESERVOIR_SIZE_SHORT, encK);

    final ReservoirLongsUnion rebuilt = ReservoirLongsUnion.getInstance(unionMem);
    final byte[] rebuiltBytes = rebuilt.toByteArray();

    assertEquals(unionBytesOrig.length, rebuiltBytes.length);
    for (int i = 0; i < unionBytesOrig.length; ++i) {
      assertEquals(unionBytesOrig[i], rebuiltBytes[i]);
    }
  }

  //@SuppressWarnings("null") // this is the point of the test
  @Test(expectedExceptions = java.lang.NullPointerException.class)
  public void checkNullMemoryInstantiation() {
    ReservoirLongsUnion.getInstance(null);
  }

  @Test
  public void checkDownsampledUpdate() {
    final int bigK = 1024;
    final int smallK = 256;
    final int n = 2048;
    final ReservoirLongsSketch sketch1 = getBasicSketch(n, smallK);
    final ReservoirLongsSketch sketch2 = getBasicSketch(n, bigK);

    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(smallK);
    assertEquals(rlu.getMaxK(), smallK);

    rlu.update(sketch1);
    assertNotNull(rlu.getResult());
    assertEquals(rlu.getResult().getK(), smallK);

    rlu.update(sketch2);
    assertEquals(rlu.getResult().getK(), smallK);
    assertEquals(rlu.getResult().getNumSamples(), smallK);
  }

  @Test
  public void checkNewGadget() {
    final int maxK = 1024;
    final int bigK = 1536;
    final int smallK = 128;

    // downsample input sketch, use as gadget (exact mode, but irrelevant here)
    final ReservoirLongsSketch bigKSketch = getBasicSketch(maxK / 2, bigK);
    final byte[] bigKBytes = bigKSketch.toByteArray();
    final Memory bigKMem = new NativeMemory(bigKBytes);

    ReservoirLongsUnion riu = ReservoirLongsUnion.getInstance(maxK);
    riu.update(bigKMem);
    assertNotNull(riu.getResult());
    assertEquals(riu.getResult().getK(), maxK);
    assertEquals(riu.getResult().getN(), maxK / 2);

    // sketch k < maxK but in sampling mode
    final ReservoirLongsSketch smallKSketch = getBasicSketch(maxK, smallK);
    final byte[] smallKBytes = smallKSketch.toByteArray();
    final Memory smallKMem = new NativeMemory(smallKBytes);

    riu = ReservoirLongsUnion.getInstance(maxK);
    riu.update(smallKMem);
    assertNotNull(riu.getResult());
    assertTrue(riu.getResult().getK() < maxK);
    assertEquals(riu.getResult().getK(), smallK);
    assertEquals(riu.getResult().getN(), maxK);

    // sketch k < maxK and in exact mode
    final ReservoirLongsSketch smallKExactSketch = getBasicSketch(smallK, smallK);
    final byte[] smallKExactBytes = smallKExactSketch.toByteArray();
    final Memory smallKExactMem = new NativeMemory(smallKExactBytes);

    riu = ReservoirLongsUnion.getInstance(maxK);
    riu.update(smallKExactMem);
    assertNotNull(riu.getResult());
    assertEquals(riu.getResult().getK(), maxK);
    assertEquals(riu.getResult().getN(), smallK);
  }


  @Test
  public void checkStandardMergeNoCopy() {
    final int k = 1024;
    final int n1 = 256;
    final int n2 = 256;
    final ReservoirLongsSketch sketch1 = getBasicSketch(n1, k);
    final ReservoirLongsSketch sketch2 = getBasicSketch(n2, k);

    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(k);
    rlu.update(sketch1);
    rlu.update(sketch2);

    assertNotNull(rlu.getResult());
    assertEquals(rlu.getResult().getK(), k);
    assertEquals(rlu.getResult().getN(), n1 + n2);
    assertEquals(rlu.getResult().getNumSamples(), n1 + n2);

    // creating from Memory should avoid a copy
    final int n3 = 2048;
    final ReservoirLongsSketch sketch3 = getBasicSketch(n3, k);
    final byte[] sketch3Bytes = sketch3.toByteArray();
    final Memory mem = new NativeMemory(sketch3Bytes);
    rlu.update(mem);

    assertEquals(rlu.getResult().getK(), k);
    assertEquals(rlu.getResult().getN(), n1 + n2 + n3);
    assertEquals(rlu.getResult().getNumSamples(), k);
  }

  @Test
  public void checkStandardMergeWithCopy() {
    // this will check the other code route to a standard merge,
    // but will copy sketch2 to be non-destructive.
    final int k = 1024;
    final int n1 = 768;
    final int n2 = 2048;
    final ReservoirLongsSketch sketch1 = getBasicSketch(n1, k);
    final ReservoirLongsSketch sketch2 = getBasicSketch(n2, k);

    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(k);
    rlu.update(sketch1);
    rlu.update(sketch2);
    rlu.update(10);

    assertNotNull(rlu.getResult());
    assertEquals(rlu.getResult().getK(), k);
    assertEquals(rlu.getResult().getN(), n1 + n2 + 1);
    assertEquals(rlu.getResult().getNumSamples(), k);
  }

  @Test
  public void checkWeightedMerge() {
    final int k = 1024;
    final int n1 = 16384;
    final int n2 = 2048;
    final ReservoirLongsSketch sketch1 = getBasicSketch(n1, k);
    final ReservoirLongsSketch sketch2 = getBasicSketch(n2, k);

    ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(k);
    rlu.update(sketch1);
    rlu.update(sketch2);

    assertNotNull(rlu.getResult());
    assertEquals(rlu.getResult().getK(), k);
    assertEquals(rlu.getResult().getN(), n1 + n2);
    assertEquals(rlu.getResult().getNumSamples(), k);

    // now merge into the sketch for updating -- results should match
    rlu = ReservoirLongsUnion.getInstance(k);
    rlu.update(sketch2);
    rlu.update(sketch1);

    assertNotNull(rlu.getResult());
    assertEquals(rlu.getResult().getK(), k);
    assertEquals(rlu.getResult().getN(), n1 + n2);
    assertEquals(rlu.getResult().getNumSamples(), k);
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void checkBadPreLongs() {
    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(1024);
    final Memory mem = new NativeMemory(rlu.toByteArray());
    mem.putByte(PREAMBLE_LONGS_BYTE, (byte) 0); // corrupt the preLongs count

    ReservoirLongsUnion.getInstance(mem);
    fail();
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void checkBadSerVer() {
    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(1024);
    final Memory mem = new NativeMemory(rlu.toByteArray());
    mem.putByte(SER_VER_BYTE, (byte) 0); // corrupt the serialization version

    ReservoirLongsUnion.getInstance(mem);
    fail();
  }

  @Test(expectedExceptions = SketchesArgumentException.class)
  public void checkBadFamily() {
    final ReservoirLongsUnion rlu = ReservoirLongsUnion.getInstance(1024);
    final Memory mem = new NativeMemory(rlu.toByteArray());
    mem.putByte(FAMILY_BYTE, (byte) 0); // corrupt the family ID

    ReservoirLongsUnion.getInstance(mem);
    fail();
  }

  private static void validateUnionEquality(final ReservoirLongsUnion rlu1,
                                            final ReservoirLongsUnion rlu2) {
    assertEquals(rlu1.getMaxK(), rlu2.getMaxK());

    ReservoirLongsSketchTest.validateReservoirEquality(rlu1.getResult(), rlu2.getResult());
  }

  private static ReservoirLongsSketch getBasicSketch(final int n, final int k) {
    final ReservoirLongsSketch rls = ReservoirLongsSketch.getInstance(k);

    for (int i = 0; i < n; ++i) {
      rls.update(i);
    }

    return rls;
  }

  /**
   * Wrapper around System.out.println() allowing a simple way to disable logging in tests
   *
   * @param msg The message to print
   */
  private static void println(final String msg) {
    //System.out.println(msg);
  }
}
