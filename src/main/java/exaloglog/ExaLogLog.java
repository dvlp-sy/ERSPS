package exaloglog;

import com.dynatrace.hash4j.util.PackedArray;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

import static exaloglog.DistinctCountUtil.unsignedLongToDouble;
import static exaloglog.MLBiasCorrectionConstants.ML_BIAS_CORRECTION_CONSTANTS;

/**
 * 32비트 레지스터를 사용하는 ExaLogLog 알고리즘의 구현체
 */
@Getter
public class ExaLogLog {
    // CORRECTION_BIT(레지스터에서 작은 카디널리티 보정을 위한 비트) + VALUE_BIT(레지스터의 최대 비트 수) = 32비트
    private static final int VALUE_BIT = 26; // 보정 비트를 제외한 레지스터의 최대 비트 수

    private static final int MIN_P = 2;
    private static final int MAX_T = VALUE_BIT - MIN_P;

    private final byte p;   // 레지스터 인덱스 (레지스터 수 m = 2^p)
    private final byte t;   // rho 값(k)을 압축하기 위한 비트 공간
    private final byte d;   // 현재 레지스터가 가진 최대 rho 값 이후에 나오는 trailing bits를 저장할 비트 공간 (저장 정밀도)

    private final byte[] state; // 레지스터 상태를 저장하는 배열

    private ExaLogLog(byte t, byte d, byte p, byte[] state) {
        validateTParameter(t);
        validateDParameter(d, t);
        validatePParameter(p, t);
        this.t = t;
        this.d = d;
        this.p = p;
        this.state = state;
    }

    public static ExaLogLog create(int t, int d, int p) {
        return new ExaLogLog((byte)t, (byte)d, (byte)p,
                PackedArray.getHandler(getRegisterBitSize(t, d)).create(getNumRegisters(p)));
    }

    /**
     * ExaLogLog에 새로운 항목을 추가하는 메서드
     * ExaLogLog 논문의 Algorithm 2를 구현한다.
     *
     * @param hashValue 새로운 항목의 64비트 해시 값
     * @return 업데이트된 ExaLogLog 객체
     */
    public ExaLogLog add(long hashValue) {
        PackedArray.PackedArrayHandler registerAccess = PackedArray.getHandler(getRegisterBitSize(t, d));

        long mask = ((1L << t) << p) - 1;           // 인덱스 및 t 비트를 포함하는 마스크 (p + t 비트)
        int idx = (int) ((hashValue & mask) >>> t); // 레지스터 인덱스 idx (p 비트)

        int nlz = Long.numberOfLeadingZeros(hashValue | mask);
        long k = ((long) nlz << t) + (hashValue & ((1L << t) - 1)) + 1; // 업데이트 값 k (rho 값 + t 비트)

        long rOld = registerAccess.get(state, idx);
        long u = rOld >>> d; // 현재 레지스터의 최대 rho 값 u
        long delta = k - u;  // 새로운 k와 u의 차이

        // 레지스터 업데이트 (최대 rho k와 정밀도 d 비트를 사용)
        long maskD = (1L << d) - 1; // d 비트의 마스크

        // 새로운 k가 더 큰 경우 최대 rho 및 정밀도 비트 모두 업데이트
        if (delta > 0) {
            long rNew = k << d;
            if (delta <= d) {
                // (2^d | (rOld mod 2^d)) >>> delta
                rNew |= (maskD + 1 | (rOld & maskD)) >>> delta;
            }
            registerAccess.set(state, idx, rNew);
        }
        // 새로운 k가 더 작거나 같은 경우 정밀도 비트만 업데이트
        else {
            if (d + delta >= 0) {
                long rNew = rOld;
                rNew |= (1L << (d + delta));
                if (rNew != rOld) {
                    registerAccess.set(state, idx, rNew);
                }
            }
        }
        return this;
    }

    /**
     * 두 {@link ExaLogLog} 스케치를 새로운 스케치로 병합하는 메서드
     * 병합된 스케치의 정밀도(d)는 두 스케치 중 더 작은 정밀도를 따른다.
     *
     * @param sketch1 첫 번째 ExaLogLog 스케치
     * @param sketch2 두 번째 ExaLogLog 스케치
     * @return 병합된 새로운 ExaLogLog 스케치
     * @throws NullPointerException 인수로 null이 전달된 경우
     * @throws IllegalArgumentException 두 스케치의 t-파라미터가 다른 경우
     */
    public static ExaLogLog merge(ExaLogLog sketch1, ExaLogLog sketch2) {
        Objects.requireNonNull(sketch1, "첫 번째 스케치가 null입니다.");
        Objects.requireNonNull(sketch2, "두 번째 스케치가 null입니다.");

        // T 파라미터는 Rho 압축 방식을 정의하므로, 반드시 일치해야 병합 가능
        if (sketch1.t != sketch2.t) {
            throw new IllegalArgumentException("t-파라미터가 일치하지 않습니다.");
        }

        // p가 더 작은 스케치를 기준으로 병합
        if (sketch1.p <= sketch2.p) {
            if (sketch1.d <= sketch2.d) {
                // sketch1을 복사하고 sketch2를 그대로 추가(병합)
                return sketch1.copy().addSketch(sketch2);
            }
            // sketch1을 sketch2의 D와 sketch1의 P로 다운사이징한 후 sketch2를 추가
            return sketch1.downsize(sketch2.d, sketch1.p).addSketch(sketch2);
        }

        if (sketch1.d >= sketch2.d) {
            // sketch2를 복사하고 sketch1을 그대로 추가(병합)
            return sketch2.copy().addSketch(sketch1);
        }
        // sketch2를 sketch1의 D와 sketch2의 P로 다운사이징한 후 sketch1을 추가
        return sketch2.downsize(sketch1.d, sketch2.p).addSketch(sketch1);
    }

    public double getDistinctCount() {
        int m = getNumRegisters(p);

        long agg = 0;
        int[] b = new int[64];
        PackedArray.PackedArrayHandler registerAccess = PackedArray.getHandler(getRegisterBitSize(t, d));
        for (int idx = 0; idx < m; idx += 1) {
            agg += contribute(registerAccess.get(state, idx), b, t, d, p);
        }
        if (agg == 0) {
            return (b[63 - t - p] == 0) ? 0 : Double.POSITIVE_INFINITY;
        }

        double factor = m << (t + 1);
        double a = unsignedLongToDouble(agg) * 0x1p-64 * factor;

        return factor * DistinctCountUtil.solveMaximumLikelihoodEquation(a, b, 63 - p - t, 0.)
                / (1 + ML_BIAS_CORRECTION_CONSTANTS[t][d] / m);
    }

    public ExaLogLog copy() {
        return new ExaLogLog(t, d, p, Arrays.copyOf(state, state.length));
    }

    /**
     * 다른 ExaLogLog 스케치(other)의 정보를 현재 스케치(this)에 병합하는 메서드
     * <p> other의 정밀도 d와 레지스터 개수 p는 this보다 작을 수 없다.
     *
     * @param other 병합할 다른 ExaLogLog 스케치
     * @return 병합이 완료된 현재 스케치 (this)
     * @throws IllegalArgumentException 파라미터 제약을 위반하거나 t가 다른 경우
     */
    private ExaLogLog addSketch(ExaLogLog other) {
        Objects.requireNonNull(other, "null argument");

        if (other.t != this.t) {
            throw new IllegalArgumentException("t-파라미터가 다르면 병합할 수 없습니다.");
        }
        if (other.d < this.d) {
            throw new IllegalArgumentException("other 스케치의 d-파라미터가 더 작습니다.");
        }
        if (other.p < this.p) {
            throw new IllegalArgumentException("other 스케치의 레지스터 개수(p)가 더 작습니다.");
        }

        final int m = getNumRegisters(p);
        PackedArray.PackedArrayHandler handler = PackedArray.getHandler(getRegisterBitSize(t, d));

        // 파라미터가 완전히 일치하여 다운사이징이 필요없는 경우
        if (other.d == this.d && other.p == this.p) {
            for (int registerIndex = 0; registerIndex < m; ++registerIndex) {

                long thisR = handler.get(state, registerIndex);
                long otherR = handler.get(other.state, registerIndex);

                // 레지스터 값 병합
                long mergedR = mergeRegister(thisR, otherR, d);

                if (thisR != mergedR) {
                    handler.set(state, registerIndex, mergedR);
                }
            }
            return this;
        }

        // 다운사이징이 필요한 경우
        PackedArray.PackedArrayHandler otherHandler = PackedArray
                .getHandler(getRegisterBitSize(other.getT(), other.getD()));
        final int maxSubIndex = 1 << (other.p - p);
        final long downsizeThresholdU = computeDownsizeThresholdU(t, other.p);

        for (int registerIndex = 0; registerIndex < m; ++registerIndex) {
            // other 레지스터를 this의 파라미터(d, p)에 맞게 다운사이징
            long mergedR = downsizeRegister(handler.get(other.state, registerIndex), t,
                    other.d, d, other.p, p, 0, downsizeThresholdU);

            // 다운사이징된 other 레지스터 값을 현재 그룹 병합 값(mergedR)과 병합
            for (int subIndex = 1; subIndex < maxSubIndex; ++subIndex) {
                long otherR = downsizeRegister(otherHandler.get(other.state, registerIndex + (subIndex << p)), t,
                        other.d, d, other.p, p, subIndex, downsizeThresholdU);
                mergedR = mergeRegister(mergedR, otherR, d);
            }

            // 최종 병합 (mergedR != 0 일 때만 this 레지스터와 병합)
            if (mergedR != 0) {
                final long thisR = handler.get(state, registerIndex);
                mergedR = mergeRegister(mergedR, thisR, d);
                if (thisR != mergedR) {
                    handler.set(state, registerIndex, mergedR);
                }
            }
        }
        return this;
    }

    private static long mergeRegister(long r1, long r2, int d) {
        long u1 = r1 >>> d;
        long u2 = r2 >>> d;
        if (u1 > u2 && u2 > 0) {
            long x = 1L << d;
            return r1 | shiftRight(x | (r2 & (x - 1)), u1 - u2);
        } else if (u2 > u1 && u1 > 0) {
            long x = 1L << d;
            return r2 | shiftRight(x | (r1 & (x - 1)), u2 - u1);
        } else {
            return r1 | r2;
        }
    }

    private static long shiftRight(long s, long delta) {
        if (delta < 64) {
            return s >>> delta;
        } else {
            return 0;
        }
    }

    private static long computeDownsizeThresholdU(int t, int fromP) {
        return ((64L - t - fromP) << t) + 1;
    }

    private static long downsizeRegister(
            long r, int t, int fromD, int toD, int fromP, int toP, int subIdx, long downsizeThresholdU) {
        long u = r >>> fromD;
        r >>>= fromD - toD;
        if (u >= downsizeThresholdU) {
            long shift = ((fromP - toP) - (32 - Integer.numberOfLeadingZeros(subIdx))) << t;
            if (shift > 0) {
                long numBitsToShift = toD + downsizeThresholdU - u;
                if (numBitsToShift > 0) {
                    long mask = 0xFFFFFFFFFFFFFFFFL << numBitsToShift;
                    r = (mask & r) | shiftRight((r & ~mask), shift);
                }
                r += shift << toD;
            }
        }
        return r;
    }

    private static long contribute(long r, int[] b, int t, int d, int p) {
        int u = (int) (r >>> d);
        if (u == 0) return 1L << -p;
        int q = 63 - t - p;
        int j = (u - 1) >>> t;
        int i = Math.min(q, j);
        long rInv = ~r;
        int numBits = (u - 1) - (i << t);
        long mask = 0xFFFFFFFFFFFFFFFFL << Math.max(0, d - numBits);
        long mask2 = mask & ((1L << d) - 1);
        long a = (((i + 2L) << t) - u + Long.bitCount(rInv & mask2)) << (q - i);
        if (b != null) b[i] += 1 + Long.bitCount(r & mask2);
        if (t <= 5) {
            int shift = 1 << t;
            mask ^= (mask >> shift);
            while (i > 0 && mask != 0) {
                i -= 1;
                a += (long) Long.bitCount(mask & rInv) << (q - i);
                if (b != null) b[i] += Long.bitCount(mask & r);
                mask >>>= shift;
            }
        } else if (i > 0) {
            mask = ~mask;
            i -= 1;
            a += (long) Long.bitCount(mask & rInv) << (q - i);
            if (b != null) b[i] += Long.bitCount(mask & r);
        }
        return a;
    }

    private void validateTParameter(byte t) {
        if (t < 0 || t > MAX_T) {
            throw new IllegalArgumentException("illegal T parameter");
        }
    }

    private void validateDParameter(byte d, byte t) {
        if (d < 0 || d > getMaxD(t)) {
            throw new IllegalArgumentException("illegal D parameter");
        }
    }

    private void validatePParameter(byte p, byte t) {
        if (p < MIN_P || p > VALUE_BIT - t) {
            throw new IllegalArgumentException("illegal P parameter");
        }
    }

    private ExaLogLog downsize(int d, int p) {
        validatePParameter((byte) p, t);
        validateDParameter((byte) d, t);
        if (p >= this.p && d >= this.d) {
            return copy();
        } else {
            return create(t, d, p).addSketch(this);
        }
    }

    private static int getRegisterBitSize(int t, int d) {
        return 6 + t + d;
    }

    private static int getMaxD(int t) {
        return 64 - 6 - t;
    }

    private static int getNumRegisters(int p) {
        return 1 << p;
    }
}
