/*
 * Copyright 2015-16, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.quantiles;

import com.yahoo.memory.Memory;

/**
 * For building a new DoublesSketch Union operation.
 *
 * @author Lee Rhodes
 */
public class DoublesUnionBuilder {
  private int bMaxK = PreambleUtil.DEFAULT_K;
  private Memory bMem = null;

  /**
   * Constructor for a new DoublesUnionBuilder. The default configuration is
   * <ul>
   * <li>k: 128. This produces a normalized rank error of about 1.7%</li>
   * <li>Memory: null</li>
   * </ul>
   */
  public DoublesUnionBuilder() {}

  /**
   * Sets the parameter <i>masK</i> that determines the maximum size of the sketch that
   * results from a union and its accuracy.
   * @param maxK determines the accuracy and size of the union and is a maximum value.
   * The effective <i>k</i> can be smaller due to unions with smaller <i>k</i> sketches.
   * It is recommended that <i>maxK</i> be a power of 2 to enable unioning of sketches with
   * different values of <i>k</i>.
   * @return this builder
   */
  public DoublesUnionBuilder setMaxK(final int maxK) {
    Util.checkK(maxK);
    bMaxK = maxK;
    return this;
  }

  /**
   * Deprecated in favor of a clearer method naming. Please use {@link #setMaxK(int)}.
   * @param maxK See {@link #setMaxK(int)}.
   * @return this builder
   * @deprecated please use {@link #setMaxK(int)}, which more accurately describes its function.
   */
  @Deprecated
  public DoublesUnionBuilder setK(final int maxK) {
    return setMaxK(maxK);
  }

  /**
   * Specifies the Memory to be initialized for a new off-heap version of the union.
   * @param mem the given Memory.
   * @return this builder
   */
  public DoublesUnionBuilder initMemory(final Memory mem) {
    bMem = mem;
    return this;
  }

  /**
   * Gets the current configured value of <i>maxK</i>
   * @return the current configured value of <i>maxK</i>
   */
  public int getMaxK() {
    return bMaxK;
  }

  /**
   * Gets the configured Memory to be initialized by the union for off-heap use.
   * @return the configured Memory.
   */
  public Memory getMemory() {
    return bMem;
  }

  /**
   * Returns a new empty Union object with the current configuration of this Builder.
   * @return a Union object
   */
  public DoublesUnion build() {
    return (bMem == null) ? DoublesUnionImpl.heapInstance(bMaxK)
        : DoublesUnionImpl.directInstance(bMaxK, bMem);
  }

  /**
   * Returns a Heap Union object that has been initialized with the data from the given sketch.
   * @param sketch A DoublesSketch to be used as a source of data only and will not be modified.
   * @return a DoublesUnion object
   */
  public static DoublesUnion heapify(final DoublesSketch sketch) {
    return DoublesUnionImpl.heapifyInstance(sketch);
  }

  /**
   * Returns a Heap Union object that has been initialized with the data from the given memory
   * image of a sketch.
   *
   * @param srcMem A memory image of a DoublesSketch to be used as a source of data,
   * but will not be modified.
   * @return a Union object
   */
  public static DoublesUnion heapify(final Memory srcMem) {
    return DoublesUnionImpl.heapifyInstance(srcMem);
  }

  /**
   * Returns a Union object that wraps off-heap data of the given memory image of
   * a sketch. The data structures of the Union remain off-heap.
   *
   * @param mem A memory region to be used as the data structure for the sketch
   * and will be modified.
   * @return a Union object
   */
  public static DoublesUnion wrap(final Memory mem) {
    return DoublesUnionImpl.wrapInstance(mem);
  }

  /**
   * Returns a Heap DoublesUnion object that has been initialized with the data from the given
   * sketch.
   *
   * @param sketch A DoublesSketch to be used as a source of data only and will not be modified.
   * @return a DoublesUnion object
   * @deprecated changed name to {@link #heapify(DoublesSketch)} to more accuately reflect
   * its intent
   */
  @Deprecated
  public static DoublesUnion build(final DoublesSketch sketch) {
    return DoublesUnionImpl.heapifyInstance(sketch);
  }

  /**
   * Returns a heap Union object that has been initialized with the data from the given Memory
   * image of a DoublesSketch. A reference to this Memory image is not retained.
   *
   * @param srcMem a Memory image of a DoublesSketch
   * @return a Union object
   * @deprecated changed name to {@link #heapify(Memory)} to more accuately reflect its intent
   */
  @Deprecated
  public static DoublesUnion build(final Memory srcMem) {
    return DoublesUnionImpl.heapifyInstance(srcMem);
  }

  /**
   * Returns a Union object that has been initialized with the data from the given sketch.
   *
   * @param sketch A DoublesSketch to be used as a source of data, but will not be modified.
   * @return a Union object
   * @deprecated this is a duplicate of {@link #heapify(DoublesSketch)} and no longer needed.
   */
  @Deprecated
  public static DoublesUnion copyBuild(final DoublesSketch sketch) {
    return DoublesUnionImpl.heapifyInstance(DoublesUtil.copyToHeap(sketch));
  }

}
