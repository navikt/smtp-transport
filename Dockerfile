FROM ghcr.io/navikt/baseimages/temurin:21
COPY init/init.s[h] /init-scripts/
COPY build/libs/app.jar ./
COPY build/generated/migrations /app/migrations