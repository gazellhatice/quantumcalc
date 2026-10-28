package com.quantumcalc.core;

import com.quantumcalc.common.grpc.GrpcResponses;
import com.quantumcalc.common.messaging.CalculationProducer;
import com.quantumcalc.proto.BinaryRequest;
import com.quantumcalc.proto.CalcResponse;
import com.quantumcalc.proto.MathServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.function.BinaryOperator;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class MathGrpcService extends MathServiceGrpc.MathServiceImplBase {

    private final CalculationRepository repository;
    private final CalculationProducer producer;

    public MathGrpcService(CalculationRepository repository, CalculationProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    @Override
    public void add(BinaryRequest r, StreamObserver<CalcResponse> o) {
        run("add", r, o, BigDecimal::add);
    }

    @Override
    public void subtract(BinaryRequest r, StreamObserver<CalcResponse> o) {
        run("subtract", r, o, BigDecimal::subtract);
    }

    @Override
    public void multiply(BinaryRequest r, StreamObserver<CalcResponse> o) {
        run("multiply", r, o, BigDecimal::multiply);
    }

    @Override
    public void divide(BinaryRequest r, StreamObserver<CalcResponse> o) {
        run("divide", r, o, (a, b) -> a.divide(b, MathContext.DECIMAL64)); // b = 0 -> ArithmeticException
    }

    private void run(String op, BinaryRequest r, StreamObserver<CalcResponse> o, BinaryOperator<BigDecimal> fn) {
        try {
            BigDecimal a = BigDecimal.valueOf(r.getA());
            BigDecimal b = BigDecimal.valueOf(r.getB());
            BigDecimal result = fn.apply(a, b);
            String user = GrpcResponses.user(r.getUserId());

            repository.save(new CalculationRecord(op, a, b, result, user));
            producer.publish("core-math", op, a.toPlainString() + "," + b.toPlainString(),
                    result.doubleValue(), user);

            GrpcResponses.ok(o, op, result.doubleValue());
        } catch (Exception e) {
            GrpcResponses.fail(o, e);
        }
    }
}
