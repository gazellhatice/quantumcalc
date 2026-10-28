package com.quantumcalc.advanced;

import com.quantumcalc.common.grpc.GrpcResponses;
import com.quantumcalc.common.messaging.CalculationProducer;
import com.quantumcalc.proto.AdvancedMathServiceGrpc;
import com.quantumcalc.proto.BinaryRequest;
import com.quantumcalc.proto.CalcResponse;
import com.quantumcalc.proto.UnaryRequest;
import io.grpc.stub.StreamObserver;
import java.util.Date;
import java.util.function.DoubleUnaryOperator;
import net.devh.boot.grpc.server.service.GrpcService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@GrpcService
public class AdvancedMathGrpcService extends AdvancedMathServiceGrpc.AdvancedMathServiceImplBase {

    private static final String COLLECTION = "advanced_calculations";

    private final MongoTemplate mongo;
    private final CalculationProducer producer;

    public AdvancedMathGrpcService(MongoTemplate mongo, CalculationProducer producer) {
        this.mongo = mongo;
        this.producer = producer;
    }

    @Override
    public void sin(UnaryRequest r, StreamObserver<CalcResponse> o) {
        unary("sin", r, o, Math::sin);
    }

    @Override
    public void cos(UnaryRequest r, StreamObserver<CalcResponse> o) {
        unary("cos", r, o, Math::cos);
    }

    @Override
    public void log(UnaryRequest r, StreamObserver<CalcResponse> o) {
        unary("log", r, o, v -> {
            if (v <= 0) throw new IllegalArgumentException("Logaritma için değer 0'dan büyük olmalı");
            return Math.log(v);
        });
    }

    @Override
    public void sqrt(UnaryRequest r, StreamObserver<CalcResponse> o) {
        unary("sqrt", r, o, v -> {
            if (v < 0) throw new IllegalArgumentException("Negatif sayının karekökü alınamaz");
            return Math.sqrt(v);
        });
    }

    @Override
    public void power(BinaryRequest r, StreamObserver<CalcResponse> o) {
        try {
            double result = GrpcResponses.finite(Math.pow(r.getA(), r.getB()));
            String user = GrpcResponses.user(r.getUserId());
            save("power", new Document("base", r.getA()).append("exponent", r.getB()), result, user);
            producer.publish("advanced-math", "power", r.getA() + "," + r.getB(), result, user);
            GrpcResponses.ok(o, "power", result);
        } catch (Exception e) {
            GrpcResponses.fail(o, e);
        }
    }

    private void unary(String op, UnaryRequest r, StreamObserver<CalcResponse> o, DoubleUnaryOperator fn) {
        try {
            double result = GrpcResponses.finite(fn.applyAsDouble(r.getValue()));
            String user = GrpcResponses.user(r.getUserId());
            save(op, new Document("value", r.getValue()), result, user);
            producer.publish("advanced-math", op, String.valueOf(r.getValue()), result, user);
            GrpcResponses.ok(o, op, result);
        } catch (Exception e) {
            GrpcResponses.fail(o, e);
        }
    }

    /** Girdiler şemasız (esnek) bir alt doküman olarak saklanır. */
    private void save(String op, Document inputs, double result, String user) {
        mongo.insert(new Document("operation", op)
                .append("inputs", inputs)
                .append("result", result)
                .append("userId", user)
                .append("createdAt", new Date()), COLLECTION);
    }
}
