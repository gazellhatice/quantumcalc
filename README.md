# ⚛️ QuantumCalc — Cloud-Native Distributed Calculator

Spring Boot mikroservisleri, gRPC, Kafka, Redis, PostgreSQL, MongoDB ve Kubernetes ile
geliştirilmiş dağıtık bilimsel hesaplama platformu.

```
 [ İstemci ] ──HTTP/JSON──▶ [ API Gateway ] ──gRPC──▶ [ Core Math ]      ── PostgreSQL
                              │  (API key,             [ Advanced Math ]  ── MongoDB   (Circuit Breaker)
                              │   Resilience4j)        [ Statistics ]     ── MongoDB
                              │                              │
                              │ HTTP route                   │ CalculationCompletedEvent
                              ▼                              ▼
                        [ History Service ] ◀── Kafka ◀──────┘
                              │
                        Redis (son 100 işlem, TTL 10 dk)           Zipkin ◀── tüm servisler (Trace ID)
```

## Servisler

| Servis | Teknoloji | Port (yerel) | Sorumluluk |
|---|---|---|---|
| `api-gateway` | Spring Cloud Gateway, Resilience4j | 8080 | Kimlik doğrulama, HTTP→gRPC çevirisi, circuit breaker, `/api/history/**` route'u |
| `core-math-service` | Spring Boot, JPA, **PostgreSQL** | 8081 / gRPC 9091 | add, subtract, multiply, divide (`BigDecimal`, `NUMERIC`) |
| `advanced-math-service` | Spring Boot, **MongoDB** | 8082 / gRPC 9092 | sin, cos, log, sqrt, power |
| `statistics-service` | Spring Boot, **MongoDB** | 8083 / gRPC 9093 | factorial, combination, permutation, stddev |
| `history-service` | Spring Boot, Kafka consumer, **Redis** | 8084 | Event'leri dinler; son 100 işlemi Redis'te 10 dk TTL ile tutar |
| `common` | Protobuf, Kafka producer, tracing | – | `.proto` sözleşmesi, `CalculationCompletedEvent`, ortak yardımcılar |

### Tasarım kararları

- **Database per Service:** Her servisin kendi veri tabanı var; servisler birbirinin DB'sine dokunmaz.
- **Senkron = gRPC, asenkron = Kafka:** Hesap sonucu gRPC ile hemen döner; raporlama yükü Kafka event'i
  (`calculation-events` topic'i) ile History servisine devredilir. Kafka erişilemese bile hesaplama çalışmaya devam eder.
- **Gateway ve gRPC:** Spring Cloud Gateway HTTP route'lar içindir, gRPC'yi doğrudan yönlendiremez. Bu yüzden
  `CalculatorController` HTTP/JSON isteğini gRPC stub çağrısına çevirir; History ise klasik Gateway route'udur.
- **Circuit Breaker:** İleri Matematik çökerse/gecikirse (3 sn timeout, %50 hata oranı) kullanıcı
  `503` + *"İleri matematik işlemleri şu anda geçici olarak servis dışıdır"* mesajını alır.
  Geçersiz girdi (ör. `log(-1)`) servis arızası sayılmaz, `400` döner ve breaker'ı açmaz.
- **Distributed Tracing:** Micrometer Tracing + Brave + Zipkin. Brave'in gRPC interceptor'ları Trace ID'yi
  Gateway→servis arasında, Spring Kafka observation'ı servis→History arasında taşır. Loglarda
  `[uygulama,traceId,spanId]` görünür; event içinde de `traceId` alanı vardır.
- **History TTL:** Her yeni event kullanıcının listesinin TTL'ini 10 dk'ya yeniler (10 dk hareketsizlikte geçmiş silinir).

## Yerel çalıştırma (Docker + Maven)

Gereksinimler: JDK 21, Maven 3.9+, Docker.

```bash
# 1) Altyapı: Postgres, Mongo, Redis, Kafka, Zipkin
docker compose up -d

# 2) Derle (protobuf kodu otomatik üretilir) + testler
mvn clean package

# 3) Her servisi ayrı terminalde başlat
java -jar core-math-service/target/core-math-service-1.0.0.jar
java -jar advanced-math-service/target/advanced-math-service-1.0.0.jar
java -jar statistics-service/target/statistics-service-1.0.0.jar
java -jar history-service/target/history-service-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

### Deneme

```bash
H='-H X-API-KEY:dev-key -H Content-Type:application/json -H X-User-Id:ali'

curl -s $H localhost:8080/api/core/add      -d '{"a":0.1,"b":0.2}'
curl -s $H localhost:8080/api/core/divide   -d '{"a":10,"b":0}'          # 400
curl -s $H localhost:8080/api/advanced/sqrt -d '{"value":144}'
curl -s $H localhost:8080/api/advanced/power -d '{"a":2,"b":10}'
curl -s $H localhost:8080/api/stats/factorial -d '{"n":10}'
curl -s $H localhost:8080/api/stats/combination -d '{"n":5,"r":2}'
curl -s $H localhost:8080/api/stats/stddev  -d '{"values":[2,4,4,4,5,5,7,9],"sample":false}'

curl -s -H X-API-KEY:dev-key localhost:8080/api/history/ali               # Redis'ten son işlemler
curl -s -H X-API-KEY:dev-key localhost:8080/api/history/analytics/summary
```

Zipkin arayüzü: <http://localhost:9411>
Circuit breaker durumu: `curl localhost:8080/actuator/health` ve `/actuator/circuitbreakers`

**Circuit breaker testi:** `advanced-math-service`'i durdurup `/api/advanced/sqrt` çağrısını birkaç kez tekrarlayın → 503 + fallback mesajı.

## Kubernetes (Minikube)

```bash
minikube start --memory=6g --cpus=4
eval $(minikube docker-env)            # imajlar doğrudan Minikube'un Docker'ına build edilir

for s in core-math-service advanced-math-service statistics-service history-service api-gateway; do
  docker build -f $s/Dockerfile -t quantumcalc/$s:1.0.0 .
done

kubectl apply -f k8s/00-namespace-secrets.yaml
kubectl apply -f k8s/10-infra.yaml
kubectl apply -f k8s/          # tüm servisler
kubectl -n quantumcalc get pods -w

kubectl -n quantumcalc port-forward svc/api-gateway 8080:8080     # sonra yukarıdaki curl'ler
kubectl -n quantumcalc port-forward svc/zipkin 9411:9411
```

Tüm uygulama pod'larında `startupProbe`, `livenessProbe` (`/actuator/health/liveness`) ve
`readinessProbe` (`/actuator/health/readiness`) tanımlıdır; altyapı pod'larında da probe'lar vardır.

## Google Cloud (GKE)

```bash
export PROJECT_ID=<proje-id>  REGION=europe-west1
gcloud config set project $PROJECT_ID
gcloud services enable container.googleapis.com artifactregistry.googleapis.com

gcloud artifacts repositories create quantumcalc --repository-format=docker --location=$REGION
gcloud auth configure-docker $REGION-docker.pkg.dev
REG=$REGION-docker.pkg.dev/$PROJECT_ID/quantumcalc

for s in core-math-service advanced-math-service statistics-service history-service api-gateway; do
  docker build -f $s/Dockerfile -t $REG/$s:1.0.0 . && docker push $REG/$s:1.0.0
done

gcloud container clusters create-auto quantumcalc --region=$REGION
gcloud container clusters get-credentials quantumcalc --region=$REGION

# k8s/*.yaml içindeki "quantumcalc/<servis>:1.0.0" imajlarını $REG/<servis>:1.0.0 ile değiştirin:
sed -i "s#image: quantumcalc/#image: $REG/#" k8s/*-service.yaml k8s/api-gateway.yaml
kubectl apply -f k8s/00-namespace-secrets.yaml -f k8s/10-infra.yaml
kubectl apply -f k8s/
kubectl -n quantumcalc get svc api-gateway     # EXTERNAL-IP gelince istek atın
```

> ⚠️ Maliyet: işiniz bitince `gcloud container clusters delete quantumcalc --region=$REGION` ile kümeyi silin.

## Bilinen sınırlar (demo projesi)

- DB'ler `emptyDir` kullanır: pod silinirse veri gider. Kalıcılık için PVC/StatefulSet ekleyin.
- Kafka, Redis, Postgres, Mongo tek replikadır (yüksek erişilebilirlik yok).
- Secret'lar demo değerleridir; gerçek ortamda Secret Manager kullanın, API key yerine JWT/OAuth2 düşünün.
- K8s ClusterIP üzerinde gRPC yük dengelemesi bağlantı bazlıdır; çok replikada headless Service veya service mesh gerekir.

## Proje yapısı

```
quantumcalc/
├── common/                  # .proto, event, Kafka producer, tracing config
├── core-math-service/       # PostgreSQL
├── advanced-math-service/   # MongoDB
├── statistics-service/      # MongoDB (+ birim testleri)
├── history-service/         # Kafka consumer + Redis
├── api-gateway/             # Gateway + gRPC istemcileri + Resilience4j
├── k8s/                     # Kubernetes manifest'leri
└── docker-compose.yml       # yerel altyapı
```
