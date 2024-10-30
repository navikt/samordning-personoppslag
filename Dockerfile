FROM ghcr.io/navikt/baseimages/temurin:21

COPY .nais/jvm-tuning.sh /init-scripts/

COPY build/libs/samordning-personoppslag.jar /app/app.jar
