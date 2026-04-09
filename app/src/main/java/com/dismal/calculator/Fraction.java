package com.dismal.calculator;

public class Fraction {
    public final long numerator;
    public final long denominator;

    public Fraction(long numerator, long denominator) {
        if (denominator == 0) {
            throw new ArithmeticException("Denominator cannot be zero");
        }
        if (denominator < 0) {
            numerator = -numerator;
            denominator = -denominator;
        }
        long common = gcd(Math.abs(numerator), denominator);
        this.numerator = numerator / common;
        this.denominator = denominator / common;
    }

    private static long gcd(long a, long b) {
        while (b > 0) {
            a %= b;
            long temp = a;
            a = b;
            b = temp;
        }
        return a;
    }

    /**
     * Finds the best rational approximation to x such that denominator <= limit.
     * Uses Continued Fractions.
     */
    public static Fraction fromDouble(double x, long limit) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return null;
        }

        long sign = x < 0 ? -1 : 1;
        x = Math.abs(x);

        long m11 = 1, m12 = 0, m21 = 0, m22 = 1;
        double a = Math.floor(x);
        double x_rem = x - a;

        while (m21 * a + m22 <= limit) {
            long temp = m11;
            m11 = (long) (m11 * a + m12);
            m12 = temp;

            temp = m21;
            m21 = (long) (m21 * a + m22);
            m22 = temp;

            if (x_rem == 0) break;
            x = 1.0 / x_rem;
            a = Math.floor(x);
            x_rem = x - a;
        }

        return new Fraction(sign * m11, m21);
    }

    public String toNiceString() {
        if (denominator == 1) {
            return String.valueOf(numerator);
        }
        return numerator + "/" + denominator;
    }

    public double doubleValue() {
        return (double) numerator / denominator;
    }
}
