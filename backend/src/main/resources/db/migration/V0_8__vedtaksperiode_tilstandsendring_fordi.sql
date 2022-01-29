CREATE TABLE arsak
(
    id         BIGSERIAL PRIMARY KEY,
    melding_id UUID UNIQUE NOT NULL,
    navn       TEXT        NOT NULL,
    opprettet  TIMESTAMP   NOT NULL
);

ALTER TABLE vedtaksperiode_tilstandsendring ADD COLUMN arsak_id BIGINT REFERENCES arsak (id);