FROM gcr.io/distroless/java25-debian13:nonroot
ENV LANG="nb_NO.UTF-8" LC_ALL="nb_NO.UTF-8" TZ="Europe/Oslo"

COPY build/libs/samordning-personoppslag.jar /app/app.jar
COPY .nais/jvm-tuning.sh /init-scripts/

CMD ["-jar", "/app/app.jar"]
