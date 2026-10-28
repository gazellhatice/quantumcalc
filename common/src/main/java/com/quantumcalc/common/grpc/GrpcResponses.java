package com.quantumcalc.common.grpc;

import com.quantumcalc.proto.CalcResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/** gRPC servislerinde tekrar eden cevap / hata kodlarını tek yerde toplar. */
public final class GrpcResponses {

    private GrpcResponses() {}

    public static String user(String userId) {
        return (userId == null || userId.isBlank()) ? "anonymous" : userId;
    }

    public static void ok(StreamObserver<CalcResponse> observer, String operation, double result) {
        observer.onNext(CalcResponse.newBuilder().setOperation(operation).setResult(result).build());
        observer.onCompleted();
    }

    /** Geçersiz girdi -> INVALID_ARGUMENT, diğer her şey -> INTERNAL. */
    public static void fail(StreamObserver<?> observer, Exception e) {
        Status status = (e instanceof IllegalArgumentException || e instanceof ArithmeticException)
                ? Status.INVALID_ARGUMENT : Status.INTERNAL;
        observer.onError(status.withDescription(String.valueOf(e.getMessage())).asRuntimeException());
    }

    public static double finite(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Sonuç tanımsız veya sonsuz");
        }
        return value;
    }
}
