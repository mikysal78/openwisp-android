# openwisp-android

[🇬🇧 English](README.md) · 🇮🇹 Italiano

Un'app Android per gestire un server [OpenWISP](https://openwisp.org) dal
telefono, tramite le API REST di OpenWISP: dispositivi e loro stato, azioni
sui router, utenti e sessioni Wi-Fi, notifiche in background.

È un'app piccola e indipendente, nata per l'istanza OpenWISP di NINUX
Basilicata. Funziona con qualsiasi server OpenWISP; non è un progetto
ufficiale di OpenWISP.

## 🧪 Diventa tester

L'app è giovane e ci piacerebbe provarla sul maggior numero possibile di
server OpenWISP e di telefoni. Arriverà anche su **Google Play**, e Google
chiede un test chiuso con dei tester registrati prima della pubblicazione.

- **Provala**: installa l'APK dalle
  [release](https://github.com/mikysal78/openwisp-android/releases) e usala con
  il tuo OpenWISP.
- **Registrati come tester** per la versione Google Play: scrivi a
  [admin@ninux-nnxx.it](mailto:admin@ninux-nnxx.it?subject=openwisp-android%20tester)
  indicando l'account Google che usi sul telefono. Il link al gruppo dei
  tester comparirà qui appena sarà aperto.
- **Segnala** problemi e idee nelle
  [issue](https://github.com/mikysal78/openwisp-android/issues) (i problemi di
  sicurezza in privato, vedi [SECURITY.md](SECURITY.md)).

## Funzioni

- **Dispositivi**: elenco con lo stato del monitoring (online, problema,
  critico), ricerca per nome, IP, MAC o modello, filtri per stato, prima i
  dispositivi con problemi.
- **Pagina del dispositivo**: IP di gestione e ultimo IP, modello, firmware,
  tempo di accensione, carico, memoria, disco, stato della configurazione;
  interfacce Wi-Fi con i loro client; i grafici del monitoring (ping, RTT,
  pacchetti persi, client Wi-Fi, traffico, CPU, memoria, disco) su 1, 3, 7 o
  30 giorni.
- **Azioni**, ognuna con una conferma: riavvio, comando di shell (con l'esito
  quando il comando finisce), aggiornamento del firmware con il firmware
  upgrader (build più recenti della stessa categoria con un'immagine dello
  stesso tipo), **LuCI** del router nel browser e log **netconsole**.
- **Wi-Fi** (openwisp-radius): utenti collegati, sessioni recenti con router,
  IP, MAC, durata e traffico; gli utenti del Wi-Fi si possono bloccare e
  sbloccare (staff e superuser non si bloccano dall'app).
- **Notifiche**: l'elenco delle notifiche di OpenWISP, da segnare come lette;
  un controllo ogni 15 minuti mostra quelle nuove come notifiche Android e
  toccandone una si apre il dispositivo.
- **Blocco dell'app** con impronta o blocco schermo, inglese e italiano, tema
  chiaro e scuro (colori Material You da Android 12).

## Requisiti

- Android 8.0 (API 26) o successivo.
- Un server OpenWISP in **HTTPS** con un certificato di una CA pubblica e un
  utente con accesso all'admin (staff). L'app può fare esattamente quello che
  questo utente può fare nell'admin di OpenWISP.
- L'app controlla quali moduli ha il server e mostra solo quello che c'è:
  senza openwisp-monitoring i dispositivi non hanno stato e grafici, senza
  openwisp-firmware-upgrader manca la sezione firmware, senza openwisp-radius
  manca la scheda Wi-Fi.
- Facoltativi, per i pulsanti LuCI e Netconsole:
  [openwisp-luci](https://github.com/mikysal78/openwisp-luci) 0.3.0 o
  successivo e [openwisp-netconsole](https://github.com/mikysal78/openwisp-netconsole)
  0.2.0 o successivo, con i loro `api_urls` inclusi sotto `api/v1/`. Senza, i
  pulsanti non compaiono.

## Installazione

Scarica l'APK dalle
[release](https://github.com/mikysal78/openwisp-android/releases) e aprilo sul
telefono (consenti l'installazione da quell'app quando Android lo chiede).

Tutte le release sono firmate con la stessa chiave; l'impronta SHA-256 del
certificato di firma è:

```
d2:88:0d:23:b5:75:8a:4b:60:57:bf:b4:d2:8f:0e:df:36:cd:81:0a:97:2d:5e:58:65:29:c6:b5:a3:16:4d:fe
```

Si controlla con `apksigner verify --print-certs openwisp-android-*.apk`, e il
file con `SHA256SUMS` nella release.

Con adb:

```bash
adb install openwisp-android-0.1.0.apk
```

## Uso

Inserisci il nome del server (per esempio `openwisp.example.com`), il nome
utente e la password. La password viene inviata una sola volta a
`/api/v1/users/token/` per ottenere il token delle API e non viene salvata.

## Sicurezza

- **Token**: OpenWISP dà un solo token delle API per utente. L'app lo salva
  cifrato con AES-GCM e una chiave del Keystore di Android non esportabile; i
  dati dell'app sono esclusi dai backup e dai trasferimenti tra telefoni.
- **Uscire** toglie il token dal telefono ma non lo revoca sul server, perché
  altri strumenti potrebbero usare lo stesso token dello stesso utente (per
  esempio una CI che carica i firmware). Per revocarlo, cancellalo nell'admin
  di OpenWISP (Auth Token).
- **Rete**: solo HTTPS, traffico in chiaro disattivato, solo le CA di sistema
  (i certificati aggiunti dall'utente non valgono), nessun redirect seguito, e
  il token va solo al server configurato (i link di paginazione verso altri
  host vengono ignorati).
- **Azioni**: riavvio, comandi, aggiornamenti e blocco degli utenti chiedono
  sempre una conferma. I comandi girano come root sul router: l'app mostra di
  nuovo il comando prima di eseguirlo.
- **Blocco dell'app**: attivo di default se il telefono ha un blocco schermo;
  lo richiede di nuovo dopo due minuti in background.
- **Intent**: l'unico componente esportato è l'activity di avvio; gli id dei
  dispositivi ricevuti dalle notifiche si accettano solo come UUID e i
  percorsi delle API non contengono mai segmenti `.` o `..`.
- **Notifiche** senza Google o altri servizi push: il telefono interroga il
  server OpenWISP, con il token, ogni 15 minuti.

Segnala le vulnerabilità in privato: vedi [SECURITY.md](SECURITY.md).

## Sviluppo

Requisiti: JDK 17 o successivo e l'Android SDK (piattaforma 36).

```bash
./gradlew testDebugUnitTest lintDebug   # test e lint
./gradlew assembleDebug                 # app/build/outputs/apk/debug/
```

Build di release: crea `keystore.properties` (fuori da git) con `storeFile`,
`storePassword`, `keyAlias` e `keyPassword`, poi `./gradlew assembleRelease`.

Il client delle API è in `app/src/main/java/.../data/Api.kt`, le schermate in
`ui/`, il controllo delle notifiche in background in
`work/NotificationWorker.kt`.

## Licenza

GPL-3.0-or-later, vedi [LICENSE](LICENSE). Il logo di OpenWISP usato come
icona dell'app viene da [openwisp-utils](https://github.com/openwisp/openwisp-utils)
(BSD-3-Clause); OpenWISP è un progetto della sua comunità, e questa app non ne
fa parte.
