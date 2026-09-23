FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:59aae400feb438475ef6c16275ee8a8f401707e6a09826430c01fa7450389452

COPY build/libs/app.jar /app/app.jar
COPY build/generated/migrations /app/migrations

WORKDIR /app
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
