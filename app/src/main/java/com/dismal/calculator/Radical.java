package com.dismal.calculator;

/**
 * Represents a simplified square root in the form {@code a√b}.
 * <p>
 * This is intended for display only, not symbolic manipulation.
 */
public final class Radical {
    public final long coefficient;
    public final long radicand;

    private Radical(long coefficient, long radicand) {
        this.coefficient = coefficient;
        this.radicand = radicand;
    }

    /**
     * Simplifies {@code √n} into {@code a√b} where {@code b} has no square factors.
     */
    public static Radical simplify(long n) {
        if (n < 0) {
            return null;
        }
        if (n == 0) {
            return new Radical(0L, 1L);
        }
        if (n == 1) {
            return new Radical(1L, 1L);
        }

        long remaining = n;
        long outside = 1L;
        long inside = 1L;

        long p = 2L;
        while (p * p <= remaining) {
            int count = 0;
            while (remaining % p == 0) {
                remaining /= p;
                count++;
            }
            if (count > 0) {
                outside *= powLong(p, count / 2);
                if ((count & 1) == 1) {
                    inside *= p;
                }
            }
            p = (p == 2L) ? 3L : (p + 2L);
        }

        if (remaining > 1L) {
            inside *= remaining;
        }

        return new Radical(outside, inside);
    }

    /**
     * Attempts to interpret {@code x} as {@code ±√n} for some integer {@code n}, and if so returns
     * the simplified {@link Radical} for {@code √n} with the same sign as {@code x}.
     */
    public static Radical fromDouble(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return null;
        }
        if (x == 0.0d) {
            return new Radical(0L, 1L);
        }

        final double abs = Math.abs(x);
        final double nDouble = abs * abs;
        if (nDouble > (double) Long.MAX_VALUE) {
            return null;
        }

        final long n = Math.round(nDouble);
        if (n < 0L) {
            return null;
        }

        final double sqrtN = Math.sqrt((double) n);
        final double tolerance = 1e-12d * Math.max(1.0d, abs);
        if (Math.abs(abs - sqrtN) > tolerance) {
            return null;
        }

        final Radical simplified = simplify(n);
        if (simplified == null) {
            return null;
        }
        if (x < 0.0d && simplified.coefficient != 0L) {
            return new Radical(-simplified.coefficient, simplified.radicand);
        }
        return simplified;
    }

    public String toNiceString() {
        if (coefficient == 0L) {
            return "0";
        }
        if (radicand == 1L) {
            return String.valueOf(coefficient);
        }
        if (coefficient == 1L) {
            return "√" + radicand;
        }
        if (coefficient == -1L) {
            return "-√" + radicand;
        }
        return coefficient + "√" + radicand;
    }

    private static long powLong(long base, int exp) {
        long result = 1L;
        for (int i = 0; i < exp; i++) {
            result *= base;
        }
        return result;
    }
}
