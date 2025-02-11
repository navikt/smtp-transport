FROM gcr.io/distroless/java21-debian12@sha256:f995f26f78b65251a0511109b07401a5c6e4d7b5284ae73e8d6577d24ff26763
WORKDIR /app

COPY build/libs/app.jar ./
COPY build/generated/migrations /migrations

ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]