/*
 * Copyright 2015-16, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.hll;

import com.yahoo.sketches.SketchesArgumentException;

/**
 * @author Kevin Lang
 */
final class OnHeapHashFields implements Fields {
  private final Preamble preamble;
  private final FieldsFactory denseFactory;
  private final int switchToDenseSize;

  private final OnHeapHash hasher;

  private int growthBound;

  public OnHeapHashFields(final Preamble preamble, final int startSize, final int switchToDenseSize,
      final FieldsFactory denseFactory) {
    this.preamble = preamble;
    this.denseFactory = denseFactory;
    this.hasher = new OnHeapHash(startSize);
    this.switchToDenseSize = switchToDenseSize;

    this.growthBound = 3 * (startSize >>> 2);
  }

  @Override
  public Preamble getPreamble() {
    return preamble;
  }

  @Override
  public Fields updateBucket(final int key, final byte val, final UpdateCallback callback) {
    hasher.updateBucket(key, val, callback);

    if (hasher.getNumElements() >= growthBound) {
      final int[] fields = hasher.getFields();
      this.growthBound = 3 * (fields.length >>> 2);
      if (fields.length == switchToDenseSize) {
        final Fields retVal = denseFactory.make(preamble);
        final BucketIterator iter = getBucketIterator();
        while (iter.next()) {
          retVal.updateBucket(iter.getKey(), iter.getValue(), NOOP_CB);
        }
        return retVal;
      } else {
        hasher.resetFields(fields.length << 1);
        hasher.boostrap(fields);
      }
    }

    return this;
  }

  @Override
  public int intoByteArray(final byte[] array, final int offset) {
    final int numBytesNeeded = numBytesToSerialize();
    if (array.length - offset < numBytesNeeded) {
      throw new SketchesArgumentException(
          String.format("array too small[%,d] < [%,d]", array.length - offset, numBytesNeeded)
      );
    }

    array[offset] = Fields.HASH_SPARSE_VERSION;
    return hasher.intoByteArray(array, offset + 1);
  }

  @Override
  public int numBytesToSerialize() {
    return 1 + hasher.numBytesToSerialize();
  }

  @Override
  public Fields toCompact() {
    return OnHeapImmutableCompactFields.fromFields(this);
  }

  @Override
  public BucketIterator getBucketIterator() {
    return hasher.getBucketIterator();
  }

  @Override
  public Fields unionInto(final Fields recipient, final UpdateCallback cb) {
    return recipient.unionBucketIterator(getBucketIterator(), cb);
  }

  @Override
  public Fields unionBucketIterator(final BucketIterator iter, final UpdateCallback callback) {
    return HllUtils.unionBucketIterator(this, iter, callback);
  }

  @Override
  public Fields unionCompressedAndExceptions(final byte[] compressed, final int minVal,
      final OnHeapHash exceptions, final UpdateCallback cb) {
    return unionBucketIterator(
        CompressedBucketUtils.getBucketIterator(compressed, minVal, exceptions), cb);
  }
}
