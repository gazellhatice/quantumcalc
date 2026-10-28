package com.quantumcalc.gateway;

import com.quantumcalc.proto.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * HTTP/JSON -> gRPC çevirici. (Spring Cloud Gateway HTTP route'lar için tasarlandığından
 * gRPC servislere giden çağrılar bu controller üzerinden yapılır.)
 */
@RestController
@RequestMapping("/api")
public class CalculatorController {

    private static final long DEADLINE_SECONDS = 2;
    static final String ADVANCED_FALLBACK = "İleri matematik işlemleri şu anda geçici olarak servis dışıdır";

    @GrpcClient("core-math")
    private MathServiceGrpc.MathServiceBlockingStub math;

    @GrpcClient("advanced-math")
    private AdvancedMathServiceGrpc.AdvancedMathServiceBlockingStub advanced;

    @GrpcClient("statistics")
    private StatServiceGrpc.StatServiceBlockingStub stats;

    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakers;

    public CalculatorController(ReactiveCircuitBreakerFactory<?, ?> circuitBreakers) {
        this.circuitBreakers = circuitBreakers;
    }

    // ---------------- Temel İşlemler ----------------
    @PostMapping("/core/{op}")
    public Mono<ResponseEntity<Object>> core(@PathVariable String op, @RequestBody CalcRequest q,
                                             @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String user) {
        BinaryRequest req = BinaryRequest.newBuilder()
                .setA(required(q.a(), "a")).setB(required(q.b(), "b")).setUserId(user).build();
        var stub = math.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
        Supplier<CalcResponse> call = switch (op) {
            case "add" -> () -> stub.add(req);
            case "subtract" -> () -> stub.subtract(req);
            case "multiply" -> () -> stub.multiply(req);
            case "divide" -> () -> stub.divide(req);
            default -> throw unknownOperation(op);
        };
        return invoke(call);
    }

    // ---------------- İleri Matematik (Circuit Breaker korumalı) ----------------
    @PostMapping("/advanced/{op}")
    public Mono<ResponseEntity<Object>> advanced(@PathVariable String op, @RequestBody CalcRequest q,
                                                 @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String user) {
        var stub = advanced.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
        Supplier<CalcResponse> call;
        if (op.equals("power")) {
            BinaryRequest req = BinaryRequest.newBuilder()
                    .setA(required(q.a(), "a")).setB(required(q.b(), "b")).setUserId(user).build();
            call = () -> stub.power(req);
        } else {
            UnaryRequest req = UnaryRequest.newBuilder().setValue(required(q.value(), "value")).setUserId(user).build();
            call = switch (op) {
                case "sin" -> () -> stub.sin(req);
                case "cos" -> () -> stub.cos(req);
                case "log" -> () -> stub.log(req);
                case "sqrt" -> () -> stub.sqrt(req);
                default -> throw unknownOperation(op);
            };
        }
        return circuitBreakers.create("advancedMath").run(
                invoke(call),
                t -> Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body((Object) Map.of("error", ADVANCED_FALLBACK))));
    }

    // ---------------- İstatistik ----------------
    @PostMapping("/stats/{op}")
    public Mono<ResponseEntity<Object>> stats(@PathVariable String op, @RequestBody CalcRequest q,
                                              @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String user) {
        var stub = stats.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
        Supplier<CalcResponse> call = switch (op) {
            case "factorial" -> {
                IntRequest req = IntRequest.newBuilder().setN(required(q.n(), "n")).setUserId(user).build();
                yield () -> stub.factorial(req);
            }
            case "combination", "permutation" -> {
                CombinatoricsRequest req = CombinatoricsRequest.newBuilder()
                        .setN(required(q.n(), "n")).setR(required(q.r(), "r")).setUserId(user).build();
                yield op.equals("combination") ? () -> stub.combination(req) : () -> stub.permutation(req);
            }
            case "stddev" -> {
                List<Double> values = required(q.values(), "values");
                DataSetRequest req = DataSetRequest.newBuilder().addAllValues(values)
                        .setSample(Boolean.TRUE.equals(q.sample())).setUserId(user).build();
                yield () -> stub.standardDeviation(req);
            }
            default -> throw unknownOperation(op);
        };
        return invoke(call);
    }

    // ---------------- yardımcılar ----------------

    /**
     * Blocking gRPC çağrısını reaktif zincire güvenle taşır. Geçersiz girdi (INVALID_ARGUMENT)
     * bir iş kuralı hatasıdır, servis arızası değildir; bu yüzden hata olarak değil 400 cevabı olarak
     * döndürülür ve circuit breaker'ı yanlışlıkla açmaz.
     */
    private Mono<ResponseEntity<Object>> invoke(Supplier<CalcResponse> call) {
        return Mono.fromCallable(call::get)
                .subscribeOn(Schedulers.boundedElastic())
                .map(r -> ResponseEntity.ok((Object) Map.of("operation", r.getOperation(), "result", r.getResult())))
                .onErrorResume(StatusRuntimeException.class, e -> {
                    if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body((Object) Map.of("error", String.valueOf(e.getStatus().getDescription()))));
                    }
                    return Mono.error(e);
                });
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + name + "' alanı zorunlu");
        }
        return value;
    }

    private static ResponseStatusException unknownOperation(String op) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Bilinmeyen işlem: " + op);
    }
}
