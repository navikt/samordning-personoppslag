FROM gcr.io/distroless/java21-debian13:nonroot

ENV TZ="Europe/Oslo"

COPY build/libs/samordning-personoppslag.jar /app/app.jar

COPY .nais/jvm-tuning.sh /init-scripts/

CMD ["-jar", "/app/app.jar"]
