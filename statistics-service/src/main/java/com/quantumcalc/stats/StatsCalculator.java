package com.quantumcalc.stats;

import com.quantumcalc.common.grpc.GrpcResponses;
import java.math.BigInteger;
import java.util.List;

/** Saf iş mantığı: gRPC / veri tabanından bağımsız, kolay test edilir. */
public final class StatsCalculator {

    private StatsCalculator() {}

    public static double factorial(int n) {
        if (n < 0 || n > 170) throw new IllegalArgumentException("n 0 ile 170 arasında olmalı");
        double result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }

    public static double combination(int n, int r) {
        checkNr(n, r);
        BigInteger result = BigInteger.ONE;
        for (int i = 1; i <= r; i++) {
            result = result.multiply(BigInteger.valueOf(n - r + i)).divide(BigInteger.valueOf(i));
        }
        return GrpcResponses.finite(result.doubleValue());
    }

    public static double permutation(int n, int r) {
        checkNr(n, r);
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < r; i++) {
            result = result.multiply(BigInteger.valueOf(n - i));
        }
        return GrpcResponses.finite(result.doubleValue());
    }

    public static double standardDeviation(List<Double> values, boolean sample) {
        int n = values.size();
        if (n == 0 || (sample && n < 2)) {
            throw new IllegalArgumentException(sample
                    ? "Örneklem sapması için en az 2 değer gerekli" : "En az 1 değer gerekli");
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sumSq = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return GrpcResponses.finite(Math.sqrt(sumSq / (sample ? n - 1 : n)));
    }

    private static void checkNr(int n, int r) {
        if (n < 0 || n > 1000) throw new IllegalArgumentException("n 0 ile 1000 arasında olmalı");
        if (r < 0 || r > n) throw new IllegalArgumentException("r, 0 ile n arasında olmalı");
    }
}
