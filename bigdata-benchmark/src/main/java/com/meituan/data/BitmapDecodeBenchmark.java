package com.meituan.data;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(jvmArgs = "-server")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BitmapDecodeBenchmark {

  private long[] bitmaps;

  private int[] result;

  @Param({"100000"})
  private int N;

  @Param({"1", "2", "4", "8", "16", "32"})
  private int density;

  @Setup
  public void setup() {
    System.out.println("setup state");
    if (density > 64) {
      throw new IllegalArgumentException("density should be less than 64, got: " + density);
    }

    Random r = new Random();

    bitmaps = new long[N];
    for (int k = 0; k < density * N; k++) {
      int bit = r.nextInt(64 * N);
      bitmaps[bit / 64] |= (1L << (bit % 64));
    }

    int card = 0;
    for (int k = 0; k < N; k++) {
      card += Long.bitCount(bitmaps[k]);
    }
    result = new int[card];
  }

  @Benchmark
  @Fork(jvmArgsAppend = "-XX:-UseCountLeadingZerosInstruction", value = 1)
  public int trailingZeros() {
    return BitmapDecodeUtils.decodeUsingTrailingZeros(bitmaps, result);
  }

  @Benchmark
  @Fork(jvmArgsAppend = "-XX:+UseCountLeadingZerosInstruction", value = 1)
  public int trailingZerosIntrinsics() {
    return BitmapDecodeUtils.decodeUsingTrailingZeros(bitmaps, result);
  }

  @Benchmark
  @Fork(jvmArgsAppend = "-XX:-UsePopCountInstruction", value = 1)
  public int bitCount() {
    return BitmapDecodeUtils.decodeUsingBitCount(bitmaps, result);
  }

  @Benchmark
  @Fork(jvmArgsAppend = "-XX:+UsePopCountInstruction", value = 1)
  public int bitCountIntrinsics() {
    return BitmapDecodeUtils.decodeUsingBitCount(bitmaps, result);
  }

  static class BitmapDecodeUtils {

    public static int decodeUsingTrailingZeros(long[] bitmaps, int[] result) {
      int pos = 0;
      for (int k = 0; k < bitmaps.length; k++) {
        long bitset = bitmaps[k];
        while (bitset != 0) {
          int ntz = Long.numberOfTrailingZeros(bitset);
          result[pos++] = 64 * k + ntz;
          bitset ^= (1L << ntz);
        }
      }
      return pos;
    }

    public static int decodeUsingBitCount(long[] bitmaps, int[] result) {
      int pos = 0;
      for (int k = 0; k < bitmaps.length; k++) {
        long bitset = bitmaps[k];
        while (bitset != 0) {
          long t = bitset & -bitset;  // set the smallest 1 bit
          result[pos++] = 64 * k + Long.bitCount(t - 1);
          bitset ^= t;
        }
      }
      return pos;
    }
  }

}
