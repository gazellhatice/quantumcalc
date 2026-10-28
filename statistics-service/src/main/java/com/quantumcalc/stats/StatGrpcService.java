package com.quantumcalc.stats;

import com.quantumcalc.common.grpc.GrpcResponses;
import com.quantumcalc.common.messaging.CalculationProducer;
import com.quantumcalc.proto.CalcResponse;
import com.quantumcalc.proto.CombinatoricsRequest;
import com.quantumcalc.proto.DataSetRequest;
import com.quantumcalc.proto.IntRequest;
import com.quantumcalc.proto.StatServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.Date;
import java.util.function.Supplier;
import net.devh.boot.grpc.server.service.GrpcService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@GrpcService
public class StatGrpcService extends StatServiceGrpc.StatServiceImplBase {

    private static final String COLLECTION = "stat_calculations";

    private final MongoTemplate mongo;
    private final CalculationProducer producer;

    public StatGrpcService(MongoTemplate mongo, CalculationProducer producer) {
        this.mongo = mongo;
        this.producer = producer;
    }

    @Override
    public void factorial(IntRequest r, StreamObserver<CalcResponse> o) {
        run("factorial", new Document("n", r.getN()), r.getUserId(), o, () -> StatsCalculator.factorial(r.getN()));
    }

    @Override
    public void combination(CombinatoricsRequest r, StreamObserver<CalcResponse> o) {
        run("combination", new Document("n", r.getN()).append("r", r.getR()), r.getUserId(), o,
                () -> StatsCalculator.combination(r.getN(), r.getR()));
    }

    @Override
    public void permutation(CombinatoricsRequest r, StreamObserver<CalcResponse> o) {
        run("permutation", new Document("n", r.getN()).append("r", r.getR()), r.getUserId(), o,
                () -> StatsCalculator.permutation(r.getN(), r.getR()));
    }

    @Override
    public void standardDeviation(DataSetRequest r, StreamObserver<CalcResponse> o) {
        run("stddev", new Document("values", r.getValuesList()).append("sample", r.getSample()), r.getUserId(), o,
                () -> StatsCalculator.standardDeviation(r.getValuesList(), r.getSample()));
    }

    private void run(String op, Document inputs, String userId, StreamObserver<CalcResponse> o,
                     Supplier<Double> calc) {
        try {
            double result = calc.get();
            String user = GrpcResponses.user(userId);
            mongo.insert(new Document("operation", op).append("inputs", inputs).append("result", result)
                    .append("userId", user).append("createdAt", new Date()), COLLECTION);
            producer.publish("statistics", op, inputs.toJson(), result, user);
            GrpcResponses.ok(o, op, result);
        } catch (Exception e) {
            GrpcResponses.fail(o, e);
        }
    }
}
