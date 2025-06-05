# SMTP Transport
Applikasjon som kommuniserer med en epost-server. Eposter leses fra innboks, og legges på kafka.
MIME Multipart meldinger splittes opp. Dette er som hovedregel ebXML-meldinger med vedlagte fagmeldinger. ebXML-delen legges på kafka, mens eventuelle vedlegg lagres i en database.
MIME Singlepart meldinger legges direkte på kafka. Disse er som hovedregel ebXML signalmeldinger (Acknowledgment og MessageError).

Konsumenten av kafka-topicene gjør den videre behandlingen og videresendingen av ebXML-meldingene.

Denne applikasjonen lytter også etter utgående ebXML-meldinger på to kafka topics, og sender dem ut via epost til mottaker. Dette gjelder både payload-meldinger og singalmeldinger.
