FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21

COPY build/libs/app.jar /app/app.jar
COPY build/generated/migrations /app/migrations

WORKDIR /app
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]