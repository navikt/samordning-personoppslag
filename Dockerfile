#FROM ghcr.io/navikt/baseimages/temurin:21
#COPY .nais/jvm-tuning.sh /init-scripts/
#COPY build/libs/samordning-personoppslag.jar /app/app.jar

FROM gcr.io/distroless/java21-debian12:nonroot

ENV TZ="Europe/Oslo"

COPY build/libs/samordning-personoppslag.jar /app/app.jar

COPY .nais/jvm-tuning.sh /init-scripts/

CMD ["-jar", "/app/app.jar"]
