FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:686c71d5b5e5f106b5d37814233acbf779fac06adecdd7ed829b529f00ad4101

COPY build/libs/app.jar /app/app.jar
COPY build/generated/migrations /app/migrations

WORKDIR /app
ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
