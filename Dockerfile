FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:7e194fab08c2dd1353019b631fe982c8bfb9b5ddcf391e77a121a1fa944653c3

COPY build/libs/app.jar /app/app.jar
COPY build/generated/migrations /app/migrations

WORKDIR /app
ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
