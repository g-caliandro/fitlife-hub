# Fitness Copilot: ricerca fase 0

Data della ricerca: 2026-11-16. Contesto app: minSdk 26, targetSdk/compileSdk 35, Room 2.6.1, WorkManager 2.10.0, kotlinx-serialization 1.7.3, nessun client HTTP dichiarato nel version catalog (usare `HttpURLConnection` o aggiungere OkHttp/Ktor), nessun DataStore, nessun `security-crypto`. Il manifest non dichiara `allowBackup`, `dataExtractionRules` né `fullBackupContent` (quindi il backup automatico è attivo di default) e `res/xml` contiene solo `file_paths.xml`.

Legenda affidabilità: **[verificato]** letto nella documentazione o provato con una chiamata reale in questa sessione; **[da verificare]** dedotto o ricordato, da controllare prima di implementare.

---

## 1. Tabella alimenti base

### Esito CREA: non utilizzabile come asset incluso nell'app
- Il portale ufficiale (alimentinutrizione.it) non offre un download delle tabelle: solo consultazione web. **[verificato]**
- Le note legali dicono: "Dati e testi di www.alimentinutrizione.it non possono essere copiati o in altro modo riprodotti", con obbligo di citare la fonte "CREA Centro di ricerca Alimenti e Nutrizione". **[verificato]** Nessuna licenza aperta. Per un permesso formale: crea@crea.gov.it.
- Le tabelle CREA 2019 non distinguono in un formato scaricabile crudo/cotto: la distinzione esiste nelle singole voci (es. "pasta di semola, cruda/cotta") ma non c'è un dataset strutturato. **[da verificare]**
- BDA IEO (bda.ieo.it): deriva dalle tabelle CREA (edizioni 1998, 2008, 2015, 2022); il sito ha pagine "Terms and conditions", "Copyright" e un PDF "Contribution for the use", quindi l'uso è soggetto a condizioni e a un contributo. Non l'ho potuto leggere. **[da verificare]** Non adatta alla redistribuzione dentro un APK.

### Alternative
| Fonte | Licenza | Formato | Crudo/cotto | Note |
| --- | --- | --- | --- | --- |
| USDA FoodData Central: SR Legacy (04/2018, ultima release) e Foundation Foods (10/2026) | Pubblico dominio / CC0 1.0 secondo FDC (la pagina download non lo riporta, controllare "Data licensing" su fdc.nal.usda.gov) **[da verificare]** | CSV (SR Legacy 6,7 MB zip) e JSON (12,3 MB zip) **[verificato]** | Sì, nel nome (`Chicken, broilers or fryers, breast, meat only, raw` / `cooked, roasted`) | Nomi in inglese, vanno tradotti a mano. Copertura ottima di alimenti non confezionati. |
| CIQUAL (ANSES, versione del 19/11/2025, 3.484 alimenti) | Accesso libero; licenza esatta (Licence Ouverte/Etalab attesa) da controllare su recherche.data.gouv.fr **[da verificare]** | Excel/XML | Sì, per voce | Nomi francesi, alimenti europei più vicini a quelli italiani. |
| Open Food Facts | ODbL (database), DbCL | API | no | Per confezionati, non per la tabella base. |

### Raccomandazione
Usare **USDA SR Legacy come sorgente dei valori per 100 g** (dominio pubblico, formato CSV semplice, voci crude e cotte separate), filtrato a mano a 150-300 alimenti, con nomi italiani e sinonimi scritti da noi. Per i 20-30 alimenti più usati da Gabriele, confrontare a mano il valore con la consultazione web del CREA (consultare per controllo non è copiare) e, se differisce molto, usare il valore italiano inserito manualmente come dato proprio dell'app. CIQUAL come seconda fonte per alimenti tipicamente europei assenti in USDA (da verificare la licenza prima).

### Come produrre l'asset
1. Script Python fuori dall'app (es. `tools/build_base_foods.py`, non nel build Gradle) che legge `food.csv`, `food_nutrient.csv`, `nutrient.csv` di SR Legacy.
2. Un file di curatela `tools/base_foods_curation.csv` scritto a mano: `fdc_id, nome_it, sinonimi, stato(crudo|cotto|n/a), categoria`. Si parte da una lista di ~200 alimenti italiani comuni (riso, pasta, petto di pollo, uova, patate, olio, pane, latte, yogurt, mele, banane...) e si cercano gli `fdc_id` a mano.
3. Per ogni voce lo script estrae kcal, proteine, carboidrati, grassi, fibre, zuccheri per 100 g (numeri nutriente FDC: 1008 energia kcal, 1003 proteine, 1004 grassi, 1005 carboidrati, 1079 fibre, 2000 zuccheri **[da verificare sul file nutrient.csv]**). Se manca kcal si calcola con Atwater 4/9/4 e si marca.
4. Output: `app/src/main/assets/base_foods.json` (kotlinx-serialization, ~50-80 KB) con `{id, nameIt, synonyms[], state, kcal100, protein100, carbs100, fat100, fiber100, sugars100, source:"usda_sr_legacy", sourceId:<fdc_id>, density?, portions[]}`. Importazione in Room alla prima apertura o con migrazione, con versione dell'asset per aggiornarlo.
5. Citare la fonte nelle impostazioni ("Dati: USDA FoodData Central"). Verifica unitaria: ogni voce con kcal ≈ 4P + 4C + 9F entro il 15 %.

Fonti: https://www.alimentinutrizione.it · https://bda.ieo.it/?p=47 · https://fdc.nal.usda.gov/download-datasets/ · https://www.anses.fr/en/content/ciqual-nutritional-composition-table · https://www.anses.fr/fr/actualite/table-ciqual-nouvelle-version-enrichie-plus-representative

---

## 2. Misure casalinghe: valori iniziali (da validare con Gabriele)

Riferimento principale: tabella "peso del cucchiaio" basata sui LARN (cucchiaio da tavola da 10 mL, cucchiaino da tè) pubblicata da dica33.it. **[verificato]** Gli altri valori sono stime comuni da tabelle di cucina e porzioni standard LARN; la tabella LARN-Porzioni di Zanichelli è dietro login e non l'ho potuta leggere, quindi le porzioni di pezzo/fetta sono **[da verificare]**.

Convenzione proposta: "cucchiaio" senza altro = **raso**, "cucchiaio colmo" solo se detto. Se l'utente non specifica, il copilot usa raso e lo mostra nell'esito.

### Generiche (se l'alimento non ha una voce specifica)
| Misura | Grammi | Nota |
| --- | --- | --- |
| cucchiaio (raso) | 10 | equivale a ~10 mL, peso reale dipende dalla densità |
| cucchiaino (raso) | 5 | |
| bicchiere (liquido) | 200 | ml ≈ g per liquidi acquosi |
| tazza (colazione) | 250 | |
| tazzina caffè | 40 | acqua ≈ 40 g; solo bevande |
| pugno (frutta secca) | 30 | |
| fetta (generica) | 30 | vedi voci specifiche |
| pezzo | nessuna generica | richiede alimento (vedi sotto) |

### Specifiche per alimento
| Alimento | Cucchiaio raso | Cucchiaio colmo | Cucchiaino raso | Altro | Fonte |
| --- | --- | --- | --- | --- | --- |
| Olio d'oliva | 9 | 9 (non si usa colmo) | 5 (colmo, tabella) | tazzina ~60 g | LARN/dica33 |
| Zucchero semolato | 9 | 16 | 5 (colmo 10) | bicchiere ~180 | LARN/dica33 |
| Miele | 14 | 22 | 7 (colmo 8) | | LARN/dica33 |
| Riso crudo | 10 | 17 | 6 (colmo 9) | bicchiere ~150 | LARN/dica33 |
| Pastina cruda | 14 (colmo) | | 7 | | LARN/dica33 |
| Farina di frumento | 7 | 15 | 4 (colmo 7) | | LARN/dica33 |
| Marmellata | | 27 | 10 (colmo) | | LARN/dica33 |
| Maionese | 9 | 18 | 5 (colmo 9) | | LARN/dica33 |
| Cacao amaro | 5 | 9 | 2 (colmo 4) | | LARN/dica33 |
| Formaggio grattugiato | 7 | 10 | 3 (colmo 4) | | LARN/dica33 |
| Panna | 10 | | 5 | | LARN/dica33 |
| Sale fino | 14 | | 7 | | LARN/dica33 |
| Burro | 12 | 20 | 5 | noce di burro ~10 | stima da verificare |
| Burro di arachidi | 15 | 20 | 7 | | stima da verificare (densità ~1,1) |
| Latte | 15 | | 5 | bicchiere 200 (ml≈1,03 g/ml → ~206), tazza 250 | stima da verificare |
| Yogurt bianco | 20 | | 7 | vasetto 125 | stima da verificare |
| Pane | | | | fetta di pane in cassetta 25-30; fetta di pane comune 40-50; panino 50 (rosetta/michetta 50-60) | stima da verificare, LARN porzione 50 g |
| Uovo | | | | pezzo medio (con guscio 60 g, edibile ~50 g); tuorlo ~18; albume ~32 | stima da verificare |
| Mela / pera | | | | pezzo medio 150 (edibile ~130) | stima da verificare |
| Banana | | | | pezzo medio 120 sbucciata (180 con buccia) | stima da verificare |
| Prosciutto cotto/crudo | | | | fetta 15-20 | stima da verificare |
| Formaggio fresco/mozzarella | | | | mozzarella 125 | stima da verificare |
| Frutta secca (mandorle/noci) | | | | pugno 30; noce singola 4-5 | stima da verificare |
| Pasta/riso cotti | | | | porzione: usare grammi da crudo (default crudo, §6 spec) | |
| Biscotto secco | | | | pezzo 8-10 | stima da verificare |
| Cereali da colazione | | 12-15 | | ciotola 40 | stima da verificare |

Avvertenza: le fonti divergono molto (olio da 9 a 15 g, zucchero da 10 a 25 g per cucchiaio a seconda del blog). Per questo le misure vanno nella tabella modificabile dalle impostazioni e Gabriele deve pesare una volta con la bilancia i propri cucchiai/bicchieri.

Fonti: https://www.dica33.it/guida-alimenti/larn-peso-cucchiaio/alimenti-peso-cucchiaio.asp · https://universita.zanichelli.it/liguri/files/2015/05/LARN-Porzioni.pdf (login) · ricerca di confronto su blog di cucina (giallozafferano.it, dissapore.com).

---

## 3. Archiviazione API key su Android

### Stato ufficiale **[verificato]**
- Tutte le API di `androidx.security:security-crypto` (incluso `EncryptedSharedPreferences`, `MasterKey`) sono **deprecate dalla release stabile 1.1.0** e non ci saranno altre release. La pagina Android "Cryptography" non indica un sostituto Jetpack: raccomanda direttamente l'**Android Keystore** e AES-GCM a 256 bit (`AES/GCM/NoPadding`).
- Pattern diffuso: Jetpack DataStore + Tink (`AndroidKeysetManager` con master key Keystore, oppure `datastore-tink` / `AeadSerializer`).

### Raccomandazione
**AES-256-GCM con chiave non estraibile nell'Android Keystore (`KeyGenParameterSpec`, `PURPOSE_ENCRYPT|DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`)**, cifrando le chiavi con `javax.crypto.Cipher` e salvando `IV + ciphertext` in un piccolo file (o DataStore Preferences) dentro **`context.noBackupFilesDir`**. Motivi: nessuna dipendenza nuova, coerente con la guida ufficiale, funziona a minSdk 26, poche decine di righe. Usare come "associated data" il nome del campo. Non servono biometria né `setUserAuthenticationRequired`, perché il worker deve poter leggere la chiave anche a telefono bloccato.
Se la solidità del keyset managed conta più della semplicità: Tink `AndroidKeysetManager` + DataStore (aggiunge `com.google.crypto.tink:tink-android` e `androidx.datastore`). Minimo API di Tink **[da verificare]**.
Non usare: `EncryptedSharedPreferences` (deprecato), SharedPreferences in chiaro, BuildConfig/gradle.properties (vietato dalla spec).

Altre regole: non loggare mai chiave né corpo richiesta; nella UI mostrare solo gli ultimi 4 caratteri; mantenere la chiave come `CharArray`/`ByteArray` quando possibile; intercettore di log disattivato in release.

### Esclusione da backup e trasferimento **[verificato]**
Semantica: `fullBackupContent` vale fino ad Android 11 (anche per app che targettano ≤ 11), `dataExtractionRules` da Android 12 con targetSdk ≥ 31. Con targetSdk 35 vanno dichiarati entrambi. Un file in `getNoBackupFilesDir()` è sempre escluso da backup e trasferimento; la regola XML è una seconda cintura (e serve se si usa DataStore sotto `filesDir`).

`AndroidManifest.xml` (da aggiungere in `<application>`):
```xml
android:allowBackup="true"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```
`res/xml/data_extraction_rules.xml`:
```xml
<data-extraction-rules>
  <cloud-backup>
    <exclude domain="file" path="copilot_secrets"/>
    <exclude domain="sharedpref" path="copilot_secrets.xml"/>
  </cloud-backup>
  <device-transfer>
    <exclude domain="file" path="copilot_secrets"/>
    <exclude domain="sharedpref" path="copilot_secrets.xml"/>
  </device-transfer>
</data-extraction-rules>
```
`res/xml/backup_rules.xml`:
```xml
<full-backup-content>
  <exclude domain="file" path="copilot_secrets"/>
  <exclude domain="sharedpref" path="copilot_secrets.xml"/>
</full-backup-content>
```
Attenzione: se nel file c'è un `<include>` il resto non viene salvato; usare solo `<exclude>`. `path` non supporta wildcard. Le chiavi Keystore comunque non sono trasferibili su un altro telefono: dopo un restore il ciphertext sarebbe illeggibile, quindi l'app deve gestire "chiave non decifrabile" chiedendo di reinserirla. Il database Room (thread chat, trascrizioni) resta nel backup: decidere se escludere anche `database/` (contiene contenuto personale, ma è anche utile ripristinare). Esiste inoltre l'export dati in-app: non esportare mai le chiavi.

Fonti: https://developer.android.com/privacy-and-security/cryptography · https://developer.android.com/identity/data/autobackup · https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/ · https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a

---

## 4. Open Food Facts

### Versione e endpoint **[verificato, anche con chiamate reali]**
- API **v3** raccomandata per nuove integrazioni (ultima sottoversione indicata v3.6); v2 deprecata ma funzionante.
- **Barcode**: `GET https://world.openfoodfacts.org/api/v3/product/{barcode}.json?fields=code,product_name,brands,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,serving_quantity_unit,nutriments` (provato: risposta `status: success`, `result.id: product_found`). Anche `/api/v2/product/{barcode}` funziona.
- **Ricerca per nome**: v2/v3 NON hanno ricerca full-text. `/api/v2/search` è solo filtri strutturati (tag, categorie, `code=a,b`), `/api/v3/search` non esiste. La ricerca per testo si fa con **Search-a-licious**: `GET https://search.openfoodfacts.org/search?q=merluzzo%20findus%20gratinato&langs=it&page_size=10&fields=code,product_name,brands,quantity,product_quantity,serving_size,serving_quantity,nutriments` (provato: restituisce hit con `code`, `product_name`, `quantity`). Il vecchio `/cgi/search.pl` è sconsigliato e durante il mio test ha restituito la pagina "temporarily unavailable".
- **Regole d'uso**: User-Agent obbligatorio e personalizzato nella forma `NomeApp/Versione (email di contatto)`, es. `LifeHub/1.0 (gabriele@example.com)` (da configurare in impostazioni, non in repo). Limiti per IP: **15 richieste/min** per letture prodotto, **10/min** per ricerche, nessun limite in scrittura; oltre i limiti si rischia 503 e ban IP. Mai usare per autocomplete. Quindi: cache locale per barcode, una ricerca per frase, niente ricerche a ogni tasto.

### Campi peso/porzione e affidabilità
Provati su due prodotti reali:
| Campo | Tipo | Esempio osservato | Uso |
| --- | --- | --- | --- |
| `quantity` | stringa libera | `"700 g"`, `"400 g e"` (suffisso "e" = stimato) | solo display, mai parsing numerico diretto |
| `product_quantity` | numero | `700`, `400` | peso della confezione; unità in `product_quantity_unit` (`"g"`, per liquidi `"ml"`) |
| `serving_size` | stringa libera | `"15g"` | display |
| `serving_quantity` | numero | `15` | grammi della porzione dichiarata; unità in `serving_quantity_unit` |
Affidabilità: dati inseriti da volontari. `serving_size`/`serving_quantity` spesso assenti (la crema di nocciole Nutella 3017620422003 non li aveva, la Nocciolata 8001505005707 sì); due hit su tre di una ricerca "merluzzo gratinato" non avevano `quantity`. Regola: se `product_quantity` manca o è 0 si apre la richiesta in sospeso del peso confezione (spec §6); se `product_quantity_unit` è `ml` non trattarlo come grammi senza densità. Se `serving_quantity` manca, nessuna porzione "porzione"; resta solo "confezione/scatola" quando `product_quantity` esiste.

### Nutrienti per 100 g
Dentro `nutriments`: `energy-kcal_100g`, `proteins_100g`, `carbohydrates_100g`, `fat_100g`, `fiber_100g`, `sugars_100g`, `saturated-fat_100g`, `salt_100g`, `sodium_100g` (tutti in grammi, energia in kcal; esiste anche `energy-kj_100g`). Se manca `energy-kcal_100g` ma c'è `energy-kj_100g`, kcal = kJ / 4,184. Se mancano proteine/carbo/grassi il prodotto non è utilizzabile senza conferma. Le chiavi `_100g` sono presenti anche se la tabella in etichetta è "per porzione".

Fonti: https://openfoodfacts.github.io/openfoodfacts-server/api/ · https://openfoodfacts.github.io/openfoodfacts-server/api/ref-cheatsheet/ · chiamate dirette a world.openfoodfacts.org e search.openfoodfacts.org del 2026-11-16.

---

## 5. LLM: gateway OpenAI-compatibile (OpenRouter)

### API **[verificato]**
- Base URL: `https://openrouter.ai/api/v1`. Chat: `POST /chat/completions`, header `Authorization: Bearer <key>`; opzionali `HTTP-Referer` e `X-OpenRouter-Title`.
- Tool calling (formato OpenAI): `tools: [{type:"function", function:{name, description, parameters:<JSON Schema>}}]`, `tool_choice: "auto"|"none"|{type:"function",function:{name}}`, `parallel_tool_calls`. La risposta ha `finish_reason: "tool_calls"` e `message.tool_calls[]` con `id`, `function.name`, `function.arguments` (stringa JSON). Si risponde con messaggi `role:"tool"`, `tool_call_id`, `content`. `tools` va rimandato a ogni richiesta, anche in quella con i risultati.
- Elenco modelli pubblico, senza chiave: `GET /api/v1/models?supported_parameters=tools` (provato: 381 modelli con prezzi in `pricing.prompt/completion` in USD per token, `context_length`, `supported_parameters`).

### Rilevare un modello senza tool calling
Attenzione: la documentazione OpenRouter dice che un parametro non supportato da un modello viene **ignorato**, e non documenta un errore specifico. Quindi un modello senza tool potrebbe rispondere in testo senza errore. Strategia in tre livelli:
1. Se l'URL base è OpenRouter, leggere `/models` e controllare che il modello scelto abbia `"tools"` in `supported_parameters`; se no, errore chiaro in impostazioni.
2. Nel "Test connessione" forzare una chiamata di prova con `tool_choice` verso un tool `ping` e verificare che la risposta contenga `tool_calls`; se arriva solo testo, errore "il modello non sembra supportare i tool".
3. A runtime, argomenti non validi o assenza di `tool_calls` dove servono tornano come errore leggibile (spec §5). Opzione da verificare nella doc di routing: `provider: {require_parameters: true}` per impedire il routing verso provider che ignorano i parametri **[da verificare]**.
Per gateway diversi da OpenRouter non esiste `/models` con metadati: restano solo i livelli 2 e 3.

### "Test connessione" minimo
1. `GET https://openrouter.ai/api/v1/key` con la chiave: 200 = chiave valida, restituisce `limit`, `limit_remaining`, `usage`, `is_free_tier` **[verificato]** (non valida il modello).
2. `POST /chat/completions` con `max_tokens: 16`, un tool `ping` e `tool_choice` forzato: costa frazioni di centesimo e verifica modello + tool in un colpo.
Errori da distinguere: 401 chiave errata, 402 crediti esauriti, 404 modello inesistente/senza endpoint, 429 rate limit (i modelli `:free` hanno 20 richieste/min e 50 al giorno sotto 10 crediti acquistati), 5xx. **[limiti free verificati; elenco codici da verificare]**

### Modelli candidati (prezzi dalla API `/models` del 2026-11-16, USD per 1M token input/output; tutti con `tools` in `supported_parameters`)
| Modello | Input | Output | Contesto | Note |
| --- | --- | --- | --- | --- |
| `anthropic/claude-haiku-5.5` | 0,10 | 0,50 | 1M | default consigliato: tool calling solido, ottimo italiano (valutazione qualitativa, **da provare**) |
| `google/gemini-3.1-flash-lite` | 0,125 | 0,75 | 1M | alternativa economica Google |
| `openai/gpt-5-mini` | 0,125 | 1,00 | 400k | alternativa OpenAI, tool calling affidabile |
| `qwen/qwen3.7-flash` | 0,03 | 0,13 | 1M | il più economico tra quelli credibili; qualità italiano da provare |
| `deepseek/deepseek-v4-flash` | 0,072 | 0,144 | 1M | molto economico |
Stima costo: una cattura tipica (prompt ~3k token + 3 giri tool ~1k) costa circa 0,0005-0,002 USD con i primi tre. Il copilot è quindi trascurabile come spesa; la scelta va fatta sulla qualità, non sul prezzo. La qualità in italiano e l'affidabilità sui tool non sono misurate qui: consigliato un mini-set di 10 frasi di Gabriele da provare sui tre modelli prima di fissare il default.

Fonti: https://openrouter.ai/docs/api-reference/overview · https://openrouter.ai/docs/features/tool-calling · https://openrouter.ai/docs/api-reference/limits · https://openrouter.ai/api/v1/models?supported_parameters=tools

---

## 6. Speech-to-text

### Opzioni **[verificato]**
| Servizio | Endpoint | Modello | Prezzo | Formati | Limiti | Lingua |
| --- | --- | --- | --- | --- | --- | --- |
| Groq | `POST https://api.groq.com/openai/v1/audio/transcriptions` | `whisper-large-v3-turbo` | 0,04 USD/ora (≈ 0,00067/min) | flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, webm | 25 MB (free), 100 MB (dev); minimo fatturato 10 s | `language=it` (ISO 639-1) |
| Groq | stesso | `whisper-large-v3` | 0,111 USD/ora (≈ 0,0019/min) | idem | idem | idem |
| OpenAI | `POST https://api.openai.com/v1/audio/transcriptions` | `gpt-transcribe` | 0,0045 USD/min | flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, webm | 25 MB | parametro `languages` (array), non `language` |
Groq riduce comunque l'audio a 16 kHz mono prima di trascrivere; parametro `prompt` fino a 224 token per guidare vocabolario (utile: "yogurt greco, petto di pollo, Findus, grammi").

### Raccomandazione
Default **Groq `whisper-large-v3-turbo`**: una dettatura di 10 s costa circa 0,0001 USD (si paga il minimo di 10 s), formato multipart OpenAI-compatibile, e il campo `language=it` è quello standard. Con `whisper-large-v3` solo se il turbo sbaglia troppi nomi (WER dichiarato 12 % contro 10,3 %). Per la sezione impostazioni mantenere URL/modello/chiave liberi come da spec, e documentare che OpenAI `gpt-transcribe` usa `languages` invece di `language`: la richiesta va adattata se l'URL base è api.openai.com. **Sorpresa**: OpenAI ha dichiarato deprecati `whisper-1`, `gpt-4o-transcribe`, `gpt-4o-mini-transcribe` e `/v1/audio/translations`, con spegnimento il **26 febbraio 2027**, quindi non usarli come default.

### Formato di registrazione su Android
`MediaRecorder` con `OutputFormat.MPEG_4`, `AudioEncoder.AAC` (AAC-LC), **16 000 Hz, mono, 32 kbps**, file `.m4a`, MIME `audio/mp4`. Motivi: m4a è accettato direttamente da Groq e OpenAI, nessuna conversione, ~240 KB al minuto, funziona dal minSdk 26. Alternativa Ogg/Opus: richiede API 29+ per `MediaRecorder.OutputFormat.OGG` e `AudioEncoder.OPUS` **[da verificare]**, quindi non è disponibile a minSdk 26. WAV/FLAC non conviene: più grandi, serve encoder proprio. Salvare il file in `filesDir/captures/` (non in cache) finché la `Capture` non è completata; non nel backup. Fine registrazione su silenzio: calcolare il livello da `MediaRecorder.getMaxAmplitude()` ogni 200 ms (soglia da calibrare, es. 2-3 s sotto soglia).

Fonti: https://console.groq.com/docs/speech-to-text · https://developers.openai.com/api/docs/guides/speech-to-text · https://developers.openai.com/api/docs/models/gpt-transcribe · https://developers.openai.com/api/docs/pricing

---

## 7. Vincoli Android in background

### 7.1 Servizi in primo piano con microfono **[verificato]**
- Da Android 12 (API 31) un'app in background non può avviare un servizio in primo piano (`ForegroundServiceStartNotAllowedException`) salvo eccezioni; tra le eccezioni utili: l'app ha una activity visibile, l'utente interagisce con una notifica/widget/bolla, l'app può avviare un'activity da background.
- Per i servizi che usano permessi "while-in-use" (microfono, camera, posizione) c'è in più il divieto di **creare** il servizio mentre l'app è in background, anche se rientra nelle eccezioni sopra. Da Android 14 con targetSdk ≥ 34 il sistema controlla alla creazione che `RECORD_AUDIO` sia effettivamente utilizzabile e altrimenti lancia `SecurityException`. Esenzioni (lista while-in-use): avvio da componente di sistema, da widget, da **interazione con una notifica**, da PendingIntent inviato da un'app visibile, ecc. `checkSelfPermission` restituisce GRANTED anche in background, quindi non protegge.
- Dichiarazioni obbligatorie da Android 14: `android:foregroundServiceType="microphone"` sul `<service>`, permesso `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_MICROPHONE`, `RECORD_AUDIO` concesso a runtime. Il tipo `microphone` non si può lanciare da un receiver `BOOT_COMPLETED`. Su Play Console i tipi vanno dichiarati (non rilevante per installazione via adb).
- Permesso "Solo questa volta" (Android 11+): se la activity è visibile si può accedere; se si avvia un FGS mentre la activity è visibile si continua anche a schermo spento.

**Conseguenza per il design**: il flusso della spec (schermata di cattura in primo piano, Fine, poi blocco) NON richiede nessun servizio con microfono: basta registrare con `MediaRecorder` dalla activity mentre è visibile. Il servizio mic serve solo se si vuole che la registrazione continui quando l'utente blocca il telefono a metà dettatura (la spec dice di no: "Nessuna registrazione senza gesto esplicito", e la registrazione finisce con Fine/silenzio). Raccomandazione: v1 senza FGS microfono; se si vuole robustezza a un blocco accidentale, avviare il FGS `microphone` dalla activity visibile (consentito). Se l'activity viene fermata a metà registrazione senza FGS, Android silenzia il microfono (non crash): gestire `onStop` fermando e salvando quanto registrato.

### 7.2 Activity sopra la schermata di blocco
`Activity.setShowWhenLocked(true)` e `setTurnScreenOn(true)` esistono da API 27; sono equivalenti agli attributi manifest `android:showWhenLocked="true"` e `android:turnScreenOn="true"`. Con minSdk 26 su API 26 servono i flag finestra `FLAG_SHOW_WHEN_LOCKED`/`FLAG_TURN_SCREEN_ON` (deprecati ma necessari lì). `showWhenLocked` mostra l'activity sopra una keyguard sicura senza sbloccare il telefono; `KeyguardManager.requestDismissKeyguard` chiede lo sblocco. **[da verificare: la pagina di riferimento era troppo lunga per essere letta fino in fondo; semantica nota ma non riletta qui]** Da testare sul telefono: se `RECORD_AUDIO` funziona a dispositivo bloccato con activity `showWhenLocked` (in genere sì perché l'activity è in primo piano) e se il lancio dal tasto laterale con telefono bloccato mostra l'activity o chiede lo sblocco.

### 7.3 WorkManager e lavoro espedito **[verificato]**
- `setExpedited()` da WorkManager 2.7; il repo usa 2.10.0. Adatto a lavori brevi (qualche minuto), importanti per l'utente. La quota è un limite di tempo di esecuzione in background, calcolato sullo standby bucket e sull'importanza del processo; **non si applica mentre l'app è in primo piano**, si applica quando va in background (il nostro caso: l'activity di cattura si chiude subito).
- `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`: usare questo (non `DROP_WORK_REQUEST`, che cancella la cattura). Il lavoro non espedito può essere rimandato da Doze/batteria: l'inbox deve mostrare "in coda".
- Prima di Android 12 WorkManager esegue il lavoro espedito come servizio in primo piano: il worker **deve** implementare `getForegroundInfo()` (altrimenti crash sui telefoni vecchi; sull'A55 con Android 14/15 non serve ma il minSdk è 26). `setForeground()` può lanciare `IllegalStateException`/`ForegroundServiceStartNotAllowedException`: avvolgerlo in try/catch.
- Il sistema può rimandare anche il lavoro espedito per carico o quota. Constraint di rete (`NetworkType.CONNECTED`) compatibili. Backoff minimo 10 s, default esponenziale 30 s; usare `Result.retry()` solo per errori rete/5xx, e il worker deve riprendere dai passi non completati (spec §9).
- Nome univoco per cattura (`enqueueUniqueWork("capture-<id>", KEEP, ...)`) per evitare duplicati.

### 7.4 POST_NOTIFICATIONS **[verificato]**
Android 13+ (API 33): permesso runtime `android.permission.POST_NOTIFICATIONS`, da dichiarare nel manifest e chiedere "in contesto" (al primo uso del copilot, non al lancio). Dopo due rifiuti il sistema non mostra più il dialog (rifiuto permanente). Se negato, l'esito resta visibile nell'inbox; non fare insistenza. Canali di notifica obbligatori da API 26: tre canali (esito, serve risposta, errore).

### 7.5 Azioni e risposta in linea **[verificato]**
- `RemoteInput.Builder(KEY)` + `NotificationCompat.Action.Builder(...).addRemoteInput(...)`; il `PendingIntent` della risposta (broadcast) deve essere **`FLAG_MUTABLE`** (le altre azioni `FLAG_IMMUTABLE`); `requestCode` diverso per ogni richiesta in sospeso, altrimenti la risposta va a quella sbagliata. Si legge con `RemoteInput.getResultsFromIntent(intent)`.
- Dopo la risposta occorre rifare `notify()` con lo stesso id (o `cancel`) per chiudere la UI di input.
- **Limite di 3 pulsanti d'azione per notifica.** **Contraddizione con la spec §11**: "fino a tre scelte rapide e risposta in linea" sono fino a 4 azioni. Soluzione: massimo 2 scelte rapide + 1 azione "Rispondi" con RemoteInput (oppure 3 scelte e risposta solo aprendo l'app). Sul telefono Samsung potrebbe mostrarne meno se la notifica è compatta.
- L'azione "Annulla" della notifica esito è un broadcast `FLAG_IMMUTABLE`, idempotente, che usa lo stesso id di log (spec §7).

### 7.6 Tasto laterale Samsung (One UI, Galaxy A55)
- Percorso: Impostazioni > Funzioni avanzate > Tasto laterale > Doppia pressione > "Apri app". **[verificato su pagine di supporto Samsung, non specifiche A55]**
- Samsung scrive che, se accanto all'app nell'elenco compare l'icona ingranaggio, l'app espone "scorciatoie in-app" e si può scegliere una funzione specifica ("Invece di lanciare solo un'app puoi attivare un'attività specifica al suo interno"). **[verificato]** Non specifica il meccanismo; è molto probabile che sia lo standard Android dei **static shortcuts** (`res/xml/shortcuts.xml` collegato con `<meta-data android:name="android.app.shortcuts">` all'activity launcher) **[inferenza da verificare sul telefono]**.
- Piano: dichiarare nella activity launcher uno shortcut statico "Parla col copilot" che apre l'activity di cattura, e tenere anche un'icona launcher dedicata come ripiego (spec §11). Verificare sul telefono dopo l'installazione: (a) compare l'ingranaggio accanto a Life Hub; (b) lo shortcut appare tra le scelte; (c) con telefono bloccato apre l'activity sopra la keyguard o chiede lo sblocco. Questi tre punti vanno scritti nel README solo dopo la prova. Il lancio da tasto laterale parte da un componente di sistema, quindi l'avvio dell'activity non incontra le restrizioni di background start.

Fonti: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start · https://developer.android.com/develop/background-work/services/fgs/service-types · https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work · https://developer.android.com/training/notify-user/build-notification · https://developer.android.com/training/permissions/requesting · https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean) · https://community.samsung.com/za/conversations/Tips-How-to/How-to-customize-the-Side-button-with-new-features-on-your/r2_22075573 · https://www.samsung.com/au/support/mobile-devices/customising-side-key/ · https://developer.android.com/guide/topics/ui/shortcuts/creating-shortcuts

---

## Punti aperti per il punto 13 della spec
- Fonte tabella base: proposta USDA SR Legacy con curatela italiana (§1); da approvare.
- Misure casalinghe: tabella §2 da validare con una pesata di Gabriele.
- Cifratura chiavi: Keystore AES-GCM in `noBackupFilesDir` (§3).
- Provider default: OpenRouter `anthropic/claude-haiku-5.5` e Groq `whisper-large-v3-turbo`; provare 10 frasi su 3 modelli (§5, §6).
- Scorciatoia tasto laterale e comportamento a telefono bloccato: da verificare sul dispositivo (§7.2, §7.6).
