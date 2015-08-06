package com.yahoo.sketches.hll;


import com.yahoo.sketches.Util;
import com.yahoo.sketches.hash.MurmurHash3;
import com.yahoo.sketches.memory.Memory;

public class Preamble
{

  public static final byte DEFAULT_PREAMBLE_SIZE = 1;
  public static final byte DEFAULT_PREAMBLE_VERSION = 8;
  public static final byte DEFAULT_PREAMBLE_FAMILY_ID = 6;
  public static final byte PERAMBLE_SIZE_BYTES = 8;

  public static final int[] AUX_SIZE = new int[] { 1, 4, 4, 4, 4, 4, 4, 8, 8, 8, 16, 16, 32, 32, 64, 128, 256,
      512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144 };


  private byte preambleSize;
  private byte version;
  private byte familyId;
  private byte logConfigK;
  private byte flags;
  private short seedHash;

  public Preamble(byte preambleSize, byte version, byte familyId, byte logConfigK, byte flags, short seedHash) {
    this.preambleSize = preambleSize;
    this.version = version;
    this.familyId = familyId;
    this.logConfigK = logConfigK;
    this.flags = flags;
    this.seedHash = seedHash;
  }

  public static Preamble fromMemory(Memory memory) {
    int offset = 0;
    Builder builder = new Builder()
        .setPreambleSize(memory.getByte(offset++))
        .setVersion(memory.getByte(offset++))
        .setFamilyId(memory.getByte(offset++))
        .setLogConfigK(memory.getByte(offset++))
        // Invert the ++ in order to skip over the unused byte.  A bunch of bits are wasted
        // instead of packing the preamble so that the semantics of the various parts of the
        // preamble can be aligned across different sketches.
        .setFlags(memory.getByte(++offset));

    short leftSide = (short) (memory.getByte(offset++) << 8);
    short rightSide = memory.getByte(offset++);
    short seedHash = (short) (leftSide | rightSide);
    return builder.setSeedHash(seedHash).build();
  }

  /**
   * Computes and checks the 16-bit seed hash from the given long seed.
   * The seed hash may not be zero in order to maintain compatibility with older serialized
   * versions that did not have this concept.
   *
   * @param seed the given seed.
   * @return the seed hash.
   */
  private static short computeSeedHash(long seed) {
    long[] seedArr = {seed};
    short seedHash = (short) ((MurmurHash3.hash(seedArr, 0L)[0]) & 0xFFFFL);
    if (seedHash == 0) {
      throw new IllegalArgumentException(
          "The given seed: " + seed + " produced a seedHash of zero. " +
              "You must choose a different seed.");
    }
    return seedHash;
  }


  public static Preamble createSharedPreamble(int logConfigK) {
    if (logConfigK > 255) {
      throw new IllegalArgumentException("logConfigK is greater than a byte, make it smaller");
    }

    byte flags = new PreambleFlags.Builder()
        .setBigEndian(false)
        .setReadOnly(true)
        .setEmpty(true)
        .setSharedPreambleMode(true)
        .setSparseMode(true)
        .setUnionMode(true)
        .setEightBytePadding(false)
        .build();

    short seedHash = computeSeedHash(Util.DEFAULT_UPDATE_SEED);
    Preamble preamble = new Builder()
        .setLogConfigK((byte) logConfigK)
        .setFlags(flags)
        .setSeedHash(seedHash)
        .build();

    return preamble;
  }

  public byte[] toByteArray() {
    byte[] retVal = new byte[getPreambleSize() << 3];
    intoByteArray(retVal, 0);
    return retVal;
  }

  public int intoByteArray(byte[] bytes, int offset) {
    bytes[offset++] = getPreambleSize();
    bytes[offset++] = getVersion();
    bytes[offset++] = getFamilyId();
    bytes[offset++] = getLogConfigK();
    bytes[offset++] = 0; // unused
    bytes[offset++] = getFlags();

    short seedHash = getSeedHash();
    bytes[offset++] = (byte) (seedHash >>> 8);
    bytes[offset++] = (byte) (seedHash & Byte.MIN_VALUE);
    return offset;
  }

  public byte getPreambleSize() {
    return preambleSize;
  }

  public byte getVersion() {
    return version;
  }

  public byte getFamilyId() {
    return familyId;
  }

  public byte getLogConfigK() {
    return logConfigK;
  }

  public int getConfigK() {
    return 1 << logConfigK;
  }

  public int getMaxAuxSize() {
    return AUX_SIZE[logConfigK] << 2;
  }

  public byte getFlags() {
    return flags;
  }

  public short getSeedHash() {
    return seedHash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Preamble preamble = (Preamble) o;

    if (familyId != preamble.familyId) return false;
    if (flags != preamble.flags) return false;
    if (logConfigK != preamble.logConfigK) return false;
    if (preambleSize != preamble.preambleSize) return false;
    if (seedHash != preamble.seedHash) return false;
    if (version != preamble.version) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) preambleSize;
    result = 31 * result + (int) version;
    result = 31 * result + (int) familyId;
    result = 31 * result + (int) logConfigK;
    result = 31 * result + (int) flags;
    result = 31 * result + (int) seedHash;
    return result;
  }

  public static class Builder {
    private byte preambleSize = Preamble.DEFAULT_PREAMBLE_SIZE;
    private byte version = Preamble.DEFAULT_PREAMBLE_VERSION;
    private byte familyId = Preamble.DEFAULT_PREAMBLE_FAMILY_ID;
    private byte logConfigK;
    private byte flags;
    private short seedHash;

    public Builder setPreambleSize(byte preambleSize) {
      this.preambleSize = preambleSize;
      return this;
    }

    public Builder setVersion(byte version) {
      this.version = version;
      return this;
    }

    public Builder setFamilyId(byte familyId) {
      this.familyId = familyId;
      return this;
    }

    public Builder setLogConfigK(byte logConfigK) {
      this.logConfigK = logConfigK;
      return this;
    }

    public Builder setFlags(byte flags) {
      this.flags = flags;
      return this;
    }

    public Builder setSeedHash(short seedHash) {
      this.seedHash = seedHash;
      return this;
    }

    public Preamble build() {
      return new Preamble(preambleSize, version, familyId, logConfigK, flags, seedHash);
    }

  }

}
