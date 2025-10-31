FROM gcr.io/distroless/java21-debian12@sha256:e9ed0a9d3a0114f2d471e8fbbc7fd76b80dbf59890831814281506c1e81aee43

COPY build/libs/app.jar /app/app.jar
COPY build/generated/migrations /app/migrations

WORKDIR /app
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]