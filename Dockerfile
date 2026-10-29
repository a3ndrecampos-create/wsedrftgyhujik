FROM gradle:8.9-jdk17 AS build
WORKDIR /src
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/install/promo-server /app
ENV PORT=8080 DB_PATH=/data/promo.db
VOLUME /data
EXPOSE 8080
CMD ["/app/bin/promo-server"]
