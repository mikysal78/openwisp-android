# Google Play: scheda e moduli

Promemoria per pubblicare e aggiornare l'app sulla Play Console. Testi,
icona, immagine in evidenza e schermate stanno in
[`fastlane/metadata/android/`](../fastlane/metadata/android) (`it-IT` e
`en-US`), nel formato usato da fastlane e F-Droid.

## Dati dell'app

| Campo | Valore |
|---|---|
| Nome | NINUX OpenWISP Manager |
| Package | `it.ninuxnnxx.openwisp` |
| Lingua predefinita | Italiano (it-IT), traduzione English (en-US) |
| Tipo | App, **gratuita** |
| Categoria | Strumenti |
| Email di contatto | app@ninux-nnxx.it |
| Sito web | https://github.com/mikysal78/openwisp-android |
| Informativa privacy | https://mikysal78.github.io/ninux-basilicata-infra/privacy-app/ |
| File da caricare | `openwisp-android-<versione>.aab` dalla release GitHub |

Il nome evita «OpenWISP» da solo: l'app non è ufficiale del progetto
OpenWISP, e la descrizione lo dice. Se la revisione contesta l'uso del logo di
OpenWISP come icona, va sostituito con un'icona propria.

**Firma**: con Play App Signing, Google firma l'app con una sua chiave; la
nostra chiave (quella delle release GitHub) fa da **chiave di caricamento**.
Gli APK di GitHub e quelli di Play avranno quindi firme diverse: chi passa da
una fonte all'altra deve disinstallare l'app prima.

## Accesso all'app (App access)

Tutte le funzioni richiedono l'accesso a un server OpenWISP, quindi i revisori
di Google hanno bisogno di credenziali:

- **Raccomandato**: un utente dedicato sul nostro OpenWISP (es.
  `play-review`), staff, con **solo permessi di visualizzazione** sui device
  dell'organizzazione e nessun permesso su comandi, firmware e utenti RADIUS.
  Lo crea e lo inserisce nella console l'amministratore; la password non va
  in questo file.
- Istruzioni per i revisori: «Server: openwisp.ninux-nnxx.it. Accedi con
  l'utente fornito. L'utente vede i router e i grafici; le azioni sui router
  sono disattivate per questo account.»

## Annunci

L'app **non contiene annunci**.

## Classificazione dei contenuti (questionario IARC)

- Categoria: **Utilità, produttività, comunicazione o altro**.
- Violenza, paura, sessualità, linguaggio volgare, droghe, gioco d'azzardo:
  **no** a tutte.
- Gli utenti interagiscono o si scambiano contenuti tra loro? **No**.
- Condivide la posizione dell'utente con altri? **No**.
- Acquisti digitali? **No**.
- Risultato atteso: PEGI 3 / Everyone.

## Pubblico di destinazione

- Fasce d'età: **18 anni e oltre** (strumento di amministrazione di rete).
- L'app non è pensata per bambini e non attira bambini.

## Sicurezza dei dati (Data safety)

Risposte prudenti. L'app manda dati solo al server OpenWISP scelto
dall'utente, mai a noi o a terzi; per Google però ogni dato che lascia il
telefono conta come «raccolto», quindi dichiariamo il nome utente.

- Raccoglie o condivide dati richiesti? **Sì**.
- Dati crittografati in transito? **Sì** (solo HTTPS).
- L'utente può chiedere la cancellazione dei dati? **Sì**: uscendo dall'app o
  disinstallandola; sul server la gestisce l'amministratore di OpenWISP.
- Tipi di dati:

| Categoria | Tipo | Raccolto | Condiviso | Facoltativo | Scopi |
|---|---|---|---|---|---|
| Informazioni personali | ID utente (nome utente OpenWISP) | sì | no | no, richiesto | Funzionalità dell'app, gestione dell'account |

- Nessun altro tipo: niente posizione, contatti, foto, file, cronologia,
  identificatori pubblicitari, dati di diagnostica o arresti anomali.
- La password è inviata al server dell'utente solo per ottenere il token e non
  è memorizzata.

## Altre dichiarazioni

| Domanda | Risposta |
|---|---|
| App governativa | No |
| Funzionalità finanziarie | Nessuna |
| App per la salute | No |
| App di notizie | No |
| Tracciamento dei contatti / COVID-19 | No |
| Creazione di account nell'app | No (gli account li crea l'amministratore di OpenWISP): non serve l'URL di cancellazione account |
| Permessi sensibili (posizione, SMS, chiamate, foreground service, accessibilità) | Nessuno |

## Test chiuso

1. Traccia **Test chiuso** → crea una lista di tester con un **Google Group**
   (es. `openwisp-android-testers@googlegroups.com`) o un elenco di email.
2. Carica l'AAB della release, note di versione da
   `fastlane/metadata/android/<lingua>/changelogs/<versionCode>.txt`.
3. Copia il link di adesione dei tester nel README (sezione «Diventa tester»)
   e nella guida.
4. Account personali creati dopo novembre 2023: servono almeno **12 tester per
   14 giorni** di test chiuso prima della produzione.

## A ogni nuova versione

1. Release su GitHub dal tag (`versionCode` più alto).
2. `fastlane/metadata/android/*/changelogs/<versionCode>.txt` con le novità
   (massimo 500 caratteri).
3. Carica l'AAB nella traccia, poi promuovi.
