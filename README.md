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

Avgjørelsen tas av `EmailMsgFilter`. Tjenestenavnet (`Service`) fra ebXML-konvolutten er det primære filteret,
og hver tjeneste kan konfigureres til å sende alle eller noen meldinger til EBMS. Dersom det ikke finnes et filter for en tjeneste,
vil alle meldinger for tjenesten sendes til EMOTTAK.

Hvert filter settes opp med `both = true|false`  
- `true` matchende meldinger sendes både til EBMS og EMOTTAK 
- `false` matchende meldinger sendes bare til EBMS

Matchende meldinger angis med `selection`, en av følgende:
- `all` alle meldinger for tjenesten rutes ihht. both-setting
- `percentageNN` NN prosent (heltall < 100) rutes ihht. both-setting, resten til EMOTTAK
- `lastDigitN` CPA-IDer med sistesiffer lik N (kan angi flere) rutes ihht. both-setting, resten til EMOTTAK
- `none` ingen CPA-IDer rutes ihht. both-setting. Brukes sammen med whitelist, eller for å dokumentere tjenester til EMOTTAK eksplisitt

Alle konfigurasjoner må angi `both` og `selection`.

Dersom `blacklist` er angitt, vil CPA-IDene i lista IKKE inkluderes, uansett `selection`, disse rutes alltid til EMOTTAK

Dersom `whitelist` er angitt, vil CPA-IDene i lista ALLTID inkluderes, uansett `selection`, disse rutes alltid til EBMS

Tjenestene konfigureres i `filter-dev.conf` / `filter-prod.conf`:

```hocon
services = [
  { name = "Trekkopplysning", both=false, selection = "none", whitelist = "cpa/prod/trekkopplysning.txt" },
  { name = "Sykmelding", both=false, selection = "all" },
  { name = "BehandlerKrav", both=false, selection = "none" }
]
```

`blacklist`/`whitelist` peker på en tekstfil på classpath (innhold: en CPA-id per linje, # for kommentar)
. Listene ligger under `src/main/resources/cpa/<miljø>/`, slik at store lister holdes utenfor selve konfigurasjonsfilen.

Tjenestenavn sammenlignes eksakt (case-sensitivt), mens CPA-ider sammenlignes case-insensitivt.
Applikasjonen starter ikke hvis en `both` eller `selection` mangler, eller et tjenestenavn er duplisert.

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
4. `/filter-dev.conf` eller `/filter-prod.conf` (velges basert på `NAIS_CLUSTER_NAME`), som igjen peker
   på CPA-listefilene under `/cpa/<miljø>/`

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
