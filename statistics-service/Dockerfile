# Build context: PROJE KÖK DİZİNİ  ->  docker build -f statistics-service/Dockerfile -t quantumcalc/statistics-service:1.0.0 .
# Build aşaması Debian tabanlı: protoc binary'si glibc ister (Alpine/musl'da çalışmaz).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -q -pl statistics-service -am package -DskipTests

# Çalışma aşaması: küçük Alpine JRE, root olmayan kullanıcı
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /src/statistics-service/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
