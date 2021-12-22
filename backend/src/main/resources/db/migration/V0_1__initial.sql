CREATE TABLE tilstandsendring
(
    id           BIGSERIAL PRIMARY KEY,
    fra_tilstand VARCHAR(64) NOT NULL,
    til_tilstand VARCHAR(64) NOT NULL,
    fordi        VARCHAR(64) NOT NULL,
    forste_gang  TIMESTAMP   NOT NULL,
    siste_gang   TIMESTAMP   NOT NULL
);

CREATE UNIQUE INDEX "idx_transisjon" ON tilstandsendring (fra_tilstand, til_tilstand, fordi);

CREATE TABLE vedtaksperiode_tilstandsendring
(
    id                  BIGSERIAL PRIMARY KEY,
    melding_id          UUID UNIQUE                             NOT NULL,
    vedtaksperiode_id   UUID                                    NOT NULL,
    tilstandsendring_id BIGINT REFERENCES tilstandsendring (id) NOT NULL,
    naar                TIMESTAMP                               NOT NULL
);

CREATE INDEX "idx_vedtaksperiode_id" ON vedtaksperiode_tilstandsendring (vedtaksperiode_id);