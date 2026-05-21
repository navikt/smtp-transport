FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:a27a2d84b99d9dad4644e9caff558ec3d8bdd1c7414a089957795fd98d407fb3

COPY build/libs/app.jar /app/app.jar
COPY build/generated/migrations /app/migrations

WORKDIR /app
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
