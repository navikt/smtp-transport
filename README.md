# smtp-transport

Tjeneste som fungerer som bro mellom e-post og Kafka for ebXML-meldingsutveksling.

## Hva tjenesten gjør

### Innkommende meldinger (POP3 → Kafka)

`MailProcessor` poller en POP3-innboks på et fast intervall og behandler e-postene i batches:

- **Multipart-meldinger** (ebXML-melding med vedlegg): ebXML-konvolutten legges på Kafka-topic `*.smtp.in.ebxml.payload`, mens vedlegget lagres i PostgreSQL.
- **Singlepart-meldinger** (ebXML-signalmeldinger som Acknowledgment og MessageError): legges direkte på Kafka-topic `*.smtp.in.ebxml.signal`.

Rutingslogikken er basert på `ForwardingSystem`-enumen:

| Verdi | Beskrivelse |
|-------|-------------|
| `EBMS` | Melding legges kun på Kafka |
| `EMOTTAK` | Melding videresendes kun direkte til T1 via SMTP |
| `BOTH` | Melding legges på Kafka *og* videresendes til T1 |

Avgjørelsen tas av `EmailMsgFilter` basert på avsenderadresse og ebXML-tjenestenavn. Tillatte avsendere og tjenestenavn er konfigurert i `filter-dev.conf` / `filter-prod.conf`.

### Utgående meldinger (Kafka → SMTP)

`MessageProcessor` konsumerer to Kafka-topics og sender e-post til mottaker:

- `*.smtp.out.ebxml.payload` – payload-meldinger. Vedleggene hentes via API-kall til **ebms-async** (autentisert med Azure AD) før e-posten settes sammen og sendes.
- `*.smtp.out.ebxml.signal` – signalmeldinger

### HTTP API

| Metode | Sti | Autentisering | Beskrivelse |
|--------|-----|---------------|-------------|
| `GET` | `/api/payloads/{referenceId}` | Azure AD | Henter lagrede vedlegg fra databasen |
| `GET` | `/internal/health/liveness` | – | Liveness-probe for Kubernetes |
| `GET` | `/internal/health/readiness` | – | Readiness-probe for Kubernetes |
| `GET` | `/prometheus` | – | Prometheus-metrikker |

## Arkitektur

To samtidige prosesseringsløkker kjøres med Arrow `SuspendApp`:

1. **`MailProcessor`** – tidsstyrt, leser innboks og publiserer til Kafka
2. **`MessageProcessor`** – hendelsesdrevet, konsumerer Kafka og sender e-post

Langtlevende ressurser (Kafka, database, HTTP-klient, mail-`Store`) håndteres via Arrow `ResourceScope` for strukturert livsyklusstyring.

## Konfigurasjon

Konfigurasjon lastes med Hoplite (HOCON) i følgende prioritetsrekkefølge:

1. `/application-personal.conf` (valgfri lokal overstyring)
2. `/kafka_common.conf` (fra `emottak-utils`-avhengigheten)
3. `/application.conf`
4. `/filter-dev.conf` eller `/filter-prod.conf` (velges basert på `NAIS_CLUSTER_NAME`)

Sentrale konfigurasjonsverdier:

```
job.fixedInterval                # Intervall mellom innbokspolling (standard: 1m)
mail.inboxBatchReadLimit         # Maks antall e-poster per kjøring
mail.inboxExpunge                # Slett e-poster etter behandling
```

## Database

Vedlegg lagres i PostgreSQL. Skjemaet er definert med **Flyway**-migrasjoner i `src/main/sqldelight/.../migrations/`, og **SQLDelight** genererer typesikre spørringer fra `.sq`-filer.

```bash
# Generer migrasjoner
./gradlew generateMainPayloadDatabaseMigrations
```

## Bygg og test

```bash
# Bygg (inkluderer ktlintCheck og kodeformatering)
./gradlew build

# Kjør alle tester
./gradlew test

# Kjør én testklasse
./gradlew test --tests "no.nav.emottak.smtp.MailReaderSpec"

# Formater kode
./gradlew ktlintFormat
```

Tester bruker **Kotest `StringSpec`**-stil. Integrasjonstester benytter Testcontainers for Kafka og PostgreSQL, GreenMail for SMTP og MockOAuth2Server for Azure AD.

## Viktige avhengigheter

| Avhengighet | Formål |
|-------------|--------|
| Ktor | HTTP-server og klient |
| Arrow | Funksjonell feilhåndtering og strukturert samtidighet |
| kotlin-kafka | Kafka-integrasjon |
| SQLDelight | Typesikre SQL-spørringer |
| Flyway | Databasemigrasjoner |
| Jakarta Mail | SMTP/POP3 |
| BouncyCastle | E-postkryptering |
| Prometheus | Metrikker |
