FROM ghcr.io/navikt/baseimages/temurin:17

COPY backend/build/libs/*.jar ./

