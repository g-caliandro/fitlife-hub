# Fitness Copilot: design doc e prompt per l'agent

Nov 16, 2026 · @Gabriele Caliandro

## 1. Obiettivo e contesto

Aggiungere all'app fitness esistente (Android nativo, dati locali) un copilot conversazionale che legge i dati dell'app e li aggiorna, con input vocale. Il principio è il minimo attrito: i log di pasti e peso si scrivono subito e si annullano con un tap, solo le bozze di piano richiedono approvazione, e ciò che il copilot non riesce a risolvere resta in sospeso finché l'utente risponde.

- L'app è personale, mono-utente, senza backend. Deve restare così: nessun server nostro.
- Le uniche chiamate di rete nuove sono verso il provider LLM, il provider speech-to-text e Open Food Facts.
- Il copilot deve essere indipendente dal modello: provider, URL base e modello si cambiano dalle impostazioni.
- I costi operativi devono restare bassi: prompt di sistema corto, cronologia limitata, nessuna chiamata superflua.
- La rifinitura grafica generale dell'app non fa parte di questo lavoro. Le nuove schermate devono però riusare tema e componenti già presenti.

Uso tipico: durante il pranzo l'utente fa partire la cattura dal tasto laterale, detta il pasto e blocca il telefono. L'app non viene mai aperta: arriva una notifica con l'esito, oppure con la domanda a cui rispondere.

## 2. Istruzioni di lavoro per l'agent

Prima di scrivere codice esplora la repo e presenta un piano: questo documento descrive il comportamento voluto, non la struttura del codice esistente.

1. Mappa il modello dati attuale: alimenti, log pasti, target nutrizionali, obiettivi, allenamenti pianificati, piani di allenamento, peso corporeo, scanner barcode, client Open Food Facts.
2. Riporta cosa esiste già, cosa manca e quali migrazioni di database servono. Aspetta l'approvazione di Gabriele prima di procedere.
3. Segui le convenzioni della repo per linguaggio, architettura, dependency injection, navigazione e stile del codice. Non introdurre un secondo stile.
4. Per ogni API esterna verifica la documentazione ufficiale aggiornata prima di implementare. Non fidarti di questo documento per nomi di endpoint, parametri o prezzi.
5. Preferisci librerie mantenute e già presenti nel progetto a implementazioni custom. Motiva ogni nuova dipendenza.
6. Lavora per fasi (sezione 12). Ogni fase deve compilare, avere test e lasciare l'app utilizzabile.
7. Se un requisito è ambiguo o contraddice il codice esistente, fermati e chiedi. Non inventare comportamenti.
8. Non toccare funzionalità non citate qui, salvo quanto serve per esporre i dati al copilot.

## 3. Use case v1

La v1 copre dodici use case. I log di pasti e peso si scrivono subito e si annullano con un tap. Solo le bozze di piano richiedono approvazione. Quello che il copilot non riesce a risolvere resta in sospeso finché l'utente risponde.

| # | Use case | Esempio | Tipo |
| --- | --- | --- | --- |
| 1 | Log pasto a voce o testo, più alimenti in una frase | "Ho mangiato 90 grammi di riso basmati, 180 di petto di pollo e un cucchiaio di olio" | Scrittura diretta, annullabile |
| 2 | Autocorrezione dentro la stessa frase | "180 grammi di pollo, no aspetta, erano 120" registra 120 | Scrittura diretta, annullabile |
| 3 | Log retroattivo | "Ieri sera ho mangiato 200 grammi di salmone" | Scrittura diretta, annullabile |
| 4 | Barcode come contesto della chat | Scansiono un prodotto nel composer, poi "ne ho mangiati 30 grammi" | Scrittura diretta, annullabile |
| 5 | Log peso corporeo | "Stamattina pesavo 74,2" | Scrittura diretta, annullabile |
| 6 | Domande sui dati | "Quante proteine mi mancano oggi?", "Cosa devo allenare domani?" | Lettura |
| 7 | Suggerimento pasto sui macro mancanti | "Cosa preparo per cena?" | Lettura, con azione opzionale |
| 8 | Bozza di piano di allenamento | "Impostami 4 settimane verso il muscle up" | Bozza da approvare |
| 9 | Consulenza in conversazione lunga | Discussione sul piano, con cronologia salvata | Lettura |
| 10 | Prodotto noto, per porzione o frazione di confezione | "Ho mangiato mezzo merluzzo Findus gratinato, il solito" registra metà dei grammi della confezione, con i valori del prodotto già in storico o nei preferiti | Scrittura diretta, annullabile |
| 11 | Cattura vocale senza aprire l'app | Doppia pressione del tasto, pasto detto a voce, telefono bloccato: arriva una notifica con l'esito | Scrittura diretta più notifica |
| 12 | Richiesta in sospeso | Prodotto non trovato o ambiguo: l'item resta in sospeso, arriva una notifica e la richiesta resta nell'inbox finché l'utente risponde | Sospesa fino alla risposta |

### Fuori scope nella v1

- Modifica via copilot di dati già registrati. L'unica eccezione è Annulla sul log appena creato. Il resto si fa dalle schermate esistenti.
- Pasti salvati richiamabili a voce ("il solito pranzo"). Resta in scope "il solito" riferito a un singolo prodotto (sezione 6).
- Log dell'allenamento svolto a voce.
- Riepilogo settimanale automatico dei progressi.
- Foto di piatti o etichette.
- Qualsiasi backend, sincronizzazione o integrazione con altre app.

## 4. Architettura e componenti

Tutto gira dentro l'app. Il modello non esegue mai nulla: restituisce richieste di tool, ed è il codice dell'app a eseguirle.

Flusso di una cattura, uguale da chat e da avvio rapido:

1. L'utente parla o scrive. L'audio viene salvato su disco e nasce una `Capture` in stato registrata: da qui nulla si perde, anche se l'app si chiude o il telefono si blocca.
2. Il `CaptureWorker` chiama il `Transcriber` e ottiene il testo.
3. L'`AgentLoop` invia al modello: prompt di sistema, memoria utente, data e fuso orario correnti, contesto della cattura (testo ed eventuale prodotto scansionato), definizioni dei tool.
4. I tool di lettura tornano subito al modello. I tool di log scrivono subito nel database, in transazione, e restituiscono gli id creati.
5. Gli item che non si risolvono non bloccano il resto: si registra ciò che è certo. Per il resto il modello chiama `request_user_input` e il turno si sospende con una richiesta salvata.
6. Il `NotificationCenter` pubblica l'esito: registrato con Annulla, serve una risposta, oppure errore con Riprova.
7. Quando l'utente risponde, un nuovo turno riprende dalla cattura salvata: testo originale, risultati dei tool già eseguiti, risposta.

&#91;embedded content: ciclo di vita di una cattura · 6 stati\]

La risposta dell'utente riporta la cattura in Elaborazione. Riprova la rimette in Registrata e salta i passi già completati.

| Componente | Responsabilità |
| --- | --- |
| `Transcriber` | Interfaccia audio verso testo. Prima implementazione: API cloud. Deve restare sostituibile con un modello on-device. |
| `LlmClient` | Interfaccia chat con tool calling su formato OpenAI-compatible. URL base, modello e chiave arrivano dalle impostazioni. |
| `AgentLoop` | Orchestrazione del turno, limite massimo di iterazioni tool, errori e timeout. Può sospendersi su una richiesta di input e riprendere da una cattura salvata. |
| `ToolRegistry` | Elenco dei tool con schema, validazione degli argomenti e classificazione: lettura, scrittura diretta, bozza o richiesta di input. |
| `FoodResolver` | Dal nome detto all'alimento con valori per 100 g e porzioni note (sezione 6). |
| `QuantityNormalizer` | Da quantità e unità dette (grammi, porzioni, frazioni di confezione, misure casalinghe) a grammi. |
| `CaptureStore` | Catture con audio, trascrizione, stato, esito e id dei log creati, usati da Annulla. |
| `PendingInputStore` | Richieste aperte all'utente: tipo, opzioni, risposta, stato. Persistono finché l'utente risponde o le scarta. |
| `CaptureWorker` | Job in background che porta una cattura da registrata a uno stato finale, senza app aperta (sezione 11). |
| `NotificationCenter` | Notifiche di esito, di richiesta di input e di errore, con azioni Annulla, Scegli, Riprova. |
| `ChatStore` | Thread e messaggi persistiti, incluso il payload delle card. |
| `UserMemoryStore` | Fatti stabili sull'utente iniettati in ogni richiesta. |
| UI chat | Schermata chat, composer con microfono e barcode, card native, inbox delle richieste in sospeso. |

Regola di separazione: validazione, calcolo dei macro e scrittura stanno nel codice dell'app. Il modello interpreta il linguaggio e sceglie i tool, non calcola calorie.

## 5. Tool dell'agente

I tool si dividono in lettura, scrittura diretta e bozza o richiesta di input. I nomi qui sotto sono indicativi: adattali al modello dati reale.

### Lettura

| Tool | Restituisce |
| --- | --- |
| `get_daily_summary(date)` | Pasti del giorno, totali di kcal e macro, target, residuo |
| `get_nutrition_targets()` | Target calorici e di macro attivi |
| `get_goals()` | Obiettivi fitness con scadenza, per esempio muscle up entro 6 mesi |
| `resolve_food(name, state)` | Candidati dal `FoodResolver` con fonte, valori per 100 g e porzioni note (etichetta e grammi) |
| `get_product_by_barcode(barcode)` | Prodotto da cache locale o Open Food Facts |
| `get_planned_workouts(from, to)` | Allenamenti pianificati nell'intervallo |
| `get_workout_plan()` | Piano attivo e bozze esistenti |
| `get_body_weight(from, to)` | Storico del peso |

### Scrittura diretta

| Tool | Effetto |
| --- | --- |
| `log_meal(items, meal_type, datetime)` | Scrive subito il pasto in transazione e restituisce gli id creati. Gli item non risolti non si scrivono: restano in una richiesta di input. |
| `log_body_weight(weight_kg, date)` | Scrive subito il peso e restituisce l'id creato. |
| `save_user_memory(key, value)` | Scrive un fatto stabile nella memoria utente. Disponibile solo in chat, non nell'avvio rapido. |

### Bozza e richiesta di input

| Tool | Effetto |
| --- | --- |
| `propose_workout_plan(plan)` | Salva il piano come bozza, mai come piano attivo. Richiede approvazione (sezione 7). |
| `request_user_input(kind, prompt, options)` | Sospende il turno e salva una richiesta aperta: scelta tra candidati, quantità o peso della confezione mancanti, uso di una stima. Non blocca la registrazione degli altri item. |

Vincoli per tutti i tool:

- Schema degli argomenti rigido e validato dall'app. Argomenti non validi tornano al modello come errore leggibile, senza crash.
- Ogni `item` di un pasto contiene: riferimento all'alimento risolto, quantità (grammi, oppure numero di porzioni con la porzione scelta), stato crudo o cotto, fonte del dato.
- Se l'utente dice "mezza scatola", il modello passa `servings: 0.5`. I grammi li ricava l'app moltiplicando per i grammi della porzione.
- I macro del pasto li calcola l'app dai valori per 100 g. Il modello non li passa come argomenti.
- Le date arrivano in formato ISO. La risoluzione di "ieri sera" o "stamattina" è a carico del modello, che riceve data, ora e fuso correnti nel contesto.
- I tool di scrittura sono idempotenti rispetto alla cattura: rieseguire la stessa cattura non duplica i log.
- Nessun tool di modifica o cancellazione nella v1. Annulla è un'azione dell'app su notifica e card, non un tool del modello.

## 6. Risoluzione alimenti e quantità

Un alimento detto a voce si risolve in quattro passi, in ordine, e ci si ferma al primo risultato affidabile.

1. **Storico dell'utente**: alimenti già loggati e preferiti, con le porzioni già note, match tollerante su nome e sinonimi. A parità vince il più usato di recente.
2. **Tabella locale di alimenti base**: alimenti non confezionati (riso, petto di pollo, patate, olio, uova) con valori per 100 g, distinti tra crudo e cotto dove serve.
3. **Open Food Facts**: ricerca per nome, utile soprattutto per prodotti confezionati. La ricerca per barcode resta la via più affidabile.
4. **Stima del modello**: ammessa solo se i passi precedenti falliscono. Non si scrive mai da sola: compare come opzione marcata come stima in una richiesta in sospeso e l'utente la sceglie o la scarta.

Regole:

- Ogni item porta la fonte: `history`, `base_table`, `open_food_facts`, `llm_estimate`.
- Una stima accettata diventa un alimento dell'utente, così la volta dopo si risolve al passo 1.
- Se la ricerca restituisce più candidati plausibili e nessuno è nello storico, il copilot apre una richiesta in sospeso con i candidati, senza scegliere a caso, e intanto registra gli altri item della frase.
- **Crudo è il default** per i pesi. Se l'utente dice "cotto", si usa la voce cotta se esiste, altrimenti si segnala nella card.
- Le misure casalinghe (cucchiaio, cucchiaino, bicchiere, fetta, pezzo) passano dal `QuantityNormalizer`, che usa una tabella modificabile dalle impostazioni e specifica per alimento dove serve.

### Porzioni, frazioni e prodotti noti

Caso tipico: "ho mangiato mezzo merluzzo Findus gratinato, il solito".

- Ogni alimento può avere porzioni: etichetta (per esempio scatola, fetta, pezzo) e grammi, di cui una predefinita. Per un prodotto confezionato la porzione "scatola" vale i grammi della confezione.
- Se l'utente nomina un prodotto già presente in storico o nei preferiti ("il solito"), si usa quel prodotto con i suoi valori e le sue porzioni. Se più prodotti corrispondono, vince il più usato di recente.
- Una frazione o un multiplo ("mezzo", "un terzo", "due") si applica alla porzione nominata o a quella predefinita: mezzo merluzzo vale 0,5 per i grammi della scatola. Il modello passa il numero di porzioni, la conversione in grammi è dell'app.
- Senza nessuna quantità si usa 1 porzione predefinita e il riepilogo la mostra. Se non esiste una porzione predefinita, si apre una richiesta in sospeso.
- Il log salva grammi, kcal e macro calcolati al momento della registrazione, così modifiche successive all'alimento non cambiano lo storico.
- Per i prodotti di Open Food Facts, importare peso della confezione e porzione indicata quando presenti. Verificare nella documentazione quali campi esistono. Se mancano, una richiesta in sospeso chiede il peso della confezione una sola volta e lo salva come porzione del prodotto.
- Un pasto con item non risolti si registra in parte: gli item certi si scrivono subito, gli altri restano in sospeso e, una volta risolti, si aggiungono allo stesso pasto con stessa data e tipo.

Da fare nella fase di esplorazione:

- Proporre la fonte per popolare la tabella base. Prima scelta: le Tabelle di Composizione degli Alimenti del CREA. Verificare formato disponibile e condizioni d'uso, e proporre un'alternativa se non sono utilizzabili.
- Proporre i valori iniziali delle misure casalinghe e farli validare a Gabriele prima di inserirli.

## 7. Scritture, annullamento e richieste in sospeso

Per ridurre l'attrito, i log di pasti e peso si scrivono subito. L'utente controlla il risultato a cose fatte e annulla con un tap. Solo le bozze di piano richiedono approvazione.

| Azione | Comportamento | Come si annulla |
| --- | --- | --- |
| Log pasto | Scrittura immediata in transazione | Annulla da notifica o card elimina i record creati dalla cattura |
| Log peso | Scrittura immediata | Annulla da notifica o card |
| Nota in memoria utente | Scrittura immediata, solo in chat | Rimozione dalla schermata memoria |
| Piano di allenamento | Sempre bozza, mai piano attivo | Rifiuta la bozza o eliminala dalla schermata allenamenti |
| Item non risolto | Mai scritto: resta in una richiesta in sospeso | Scarta la richiesta |

### Annullamento

- Ogni cattura salva gli id dei record che ha creato. Annulla elimina esattamente quelli, in una transazione, e la cattura passa a stato annullata.
- Annulla è disponibile sulla notifica e sulla card della chat, senza scadenza. Se un record non esiste più, viene saltato senza errori.
- Il copilot non rifa da solo una registrazione annullata.
- L'autocorrezione dentro la frase ("180, no, 120") la risolve il modello prima di scrivere: nel log finisce solo il valore finale.

### Richieste in sospeso

Una richiesta aperta è un record persistente collegato alla cattura. Non dipende dalla notifica né dalla chat aperta.

- Tipi: scelta tra più prodotti candidati, quantità mancante, peso della confezione mancante, scelta di usare una stima per un alimento non trovato.
- Nasce quando il modello chiama `request_user_input`. Gli item già risolti della stessa frase si registrano subito, senza aspettare la risposta.
- Resta aperta finché l'utente risponde o la scarta. Nessuna scadenza automatica.
- Si risolve da tre posti: azioni della notifica, inbox nell'app, chat. Rispondere da uno la chiude negli altri.
- Alla risposta riparte un turno dalla cattura salvata. Il nuovo log si aggiunge allo stesso pasto, con stessa data e tipo.
- Un badge sull'ingresso della chat e sull'elenco dei thread indica quante richieste sono aperte.

### Bozze di piano

- I piani generati dal copilot sono sempre bozze e non sovrascrivono il piano attivo.
- Diventano piano attivo solo con Approva, dalla card o dalla schermata allenamenti.
- Una bozza rifiutata o sostituita non viene ripristinata dal copilot.

## 8. UI della chat e card native

Le risposte strutturate sono componenti nativi disegnati dall'app a partire dai dati dei tool. Il modello non genera interfaccia né markdown da interpretare.

Schermata chat:

- Lista messaggi con bolle di testo e card.
- Composer con campo testo, pulsante microfono e pulsante barcode.
- Microfono: tap per iniziare, tap per fermare. Stato di registrazione visibile. La trascrizione compare come messaggio dell'utente e parte subito.
- Barcode: apre lo scanner già presente nell'app. Il prodotto trovato diventa un allegato del messaggio in composizione e i suoi dati entrano nel contesto del turno.
- Stati espliciti per: trascrizione in corso, modello in elaborazione, errore di rete, chiave mancante con link alle impostazioni. Un accesso all'inbox delle richieste in sospeso, con badge.

| Card | Contenuto | Azioni |
| --- | --- | --- |
| Pasto registrato | Item con grammi, kcal e macro per item, totale, residuo del giorno, badge sulla fonte, eventuale porzione usata (per esempio mezza scatola) | Annulla, Apri nel diario |
| Peso registrato | Valore e data | Annulla |
| Richiesta in sospeso | Domanda, opzioni (candidati, stima marcata come tale) o campo di risposta | Scegli, Rispondi, Scarta |
| Riepilogo giornata | Kcal e macro consumati, target, residuo | Apri nel diario |
| Suggerimento pasto | Piatto proposto con item, grammi e macro, e copertura del residuo | Registra come pasto (scrive subito, con Annulla) |
| Bozza piano | Settimane, sessioni, esercizi con serie e ripetizioni | Approva, Rifiuta, Apri bozza |
| Prodotto | Nome, marca, valori per 100 g, porzioni note | Nessuna |

Il testo del modello accompagna le card ed è breve e in italiano. Niente tabelle o elenchi in markdown dentro le bolle.

## 9. Impostazioni, API key e provider

Le chiavi si inseriscono solo dalla schermata impostazioni e non devono mai comparire nella repo, nei log o nei file di build.

Nuova sezione "Copilot" nelle impostazioni:

| Campo | Note |
| --- | --- |
| LLM: URL base | Endpoint OpenAI-compatible |
| LLM: modello | Stringa libera, per provare modelli diversi |
| LLM: API key | Campo mascherato |
| STT: URL base | Endpoint di trascrizione, separato da quello LLM |
| STT: modello | Stringa libera |
| STT: API key | Campo mascherato, può coincidere con quella LLM |
| Lingua di trascrizione | Default italiano |
| Notifiche della cattura vocale | Esiti, richieste in sospeso ed errori. Il permesso notifiche (Android 13 e successivi) si chiede al primo uso |
| Misure casalinghe | Tabella modificabile |
| Test connessione | Verifica chiave e modello con una chiamata minima |

Requisiti:

- Chiavi salvate cifrate sul dispositivo con il meccanismo attualmente raccomandato per Android. Verifica la documentazione ufficiale, perché alcune librerie usate in passato sono deprecate.
- Chiavi escluse da backup automatici ed export dei dati.
- Nessuna chiave o contenuto di richiesta nei log in build release.
- Il modello scelto deve supportare il tool calling. Se non lo supporta, l'errore deve dirlo chiaramente.
- Default suggeriti, da confermare con Gabriele: un gateway multi-modello per l'LLM e un servizio Whisper economico per l'STT.
- Timeout e retry limitati. Mai retry automatico che duplichi log già scritti: un retry riprende dai passi non completati della cattura.

## 10. Persistenza: thread e memoria

Si salvano due cose distinte: la cronologia delle chat e una memoria strutturata di fatti stabili.

**Thread e messaggi**

- Ogni conversazione è un thread con titolo, data di creazione e ultimo aggiornamento.
- Ogni messaggio salva ruolo, testo, eventuali chiamate a tool con risultati e payload della card, così la card si ridisegna alla riapertura.
- Ogni cattura da avvio rapido crea un thread nuovo con la trascrizione e l'esito, così si ritrova anche in chat. Dalla chat si può aprire l'elenco dei thread passati e cancellarli.
- Al modello va solo una finestra limitata degli ultimi messaggi del thread, con un tetto configurabile nel codice. I risultati dei tool più vecchi si possono omettere.

**Memoria utente**

- Coppie chiave e valore con data di aggiornamento: obiettivi, attrezzatura disponibile, giorni di allenamento, preferenze e alimenti non graditi, livello attuale sugli esercizi chiave.
- Viene inserita in ogni richiesta, quindi deve restare corta.
- Il copilot la aggiorna tramite `save_user_memory`, subito, solo in chat. Nell'avvio rapido è disattivato.
- È visibile e modificabile a mano da una schermata delle impostazioni.

Le migrazioni del database seguono il meccanismo già in uso nell'app e non devono toccare i dati esistenti.

## 11. Avvio rapido in ascolto

L'app deve permettere di dettare un'istruzione senza aprire la chat. Il collegamento al tasto fisico si configura dal telefono, non dal codice.

Flusso voluto: l'utente preme il tasto, parla, finisce e blocca il telefono. Il resto avviene in background e termina con una notifica.

- Esporre una scorciatoia "Parla col copilot" che avvia solo la cattura: una schermata minimale con indicatore di registrazione e pulsante Fine, visibile anche sopra la schermata di blocco. Non apre la chat.
- **La registrazione deve partire con quella schermata in primo piano.** Da Android 12 un servizio in primo piano non si avvia dal background, e quello con tipo microphone non si può creare da background. Da Android 14 servono il tipo dichiarato, il permesso `FOREGROUND_SERVICE_MICROPHONE` e `RECORD_AUDIO`. Verificare nella documentazione ufficiale e sul telefono. Non esiste un modo affidabile di registrare dal background.
- La registrazione finisce con Fine o con un silenzio prolungato. Subito dopo l'audio è salvato su disco, nasce la `Capture` e la schermata si chiude.
- Da lì lavora il `CaptureWorker`, accodato con WorkManager come lavoro espedito. Le quote del lavoro espedito sono limitate: prevedere il ripiego su lavoro normale e la ripresa dopo riavvio. La cattura non si perde e, se il job ritarda, l'inbox la mostra come in coda.
- Il `NotificationCenter` pubblica tre tipi di notifica. **Esito**: cosa è stato registrato, con kcal totali e azione Annulla. **Serve una risposta**: la domanda, fino a tre scelte rapide e risposta in linea per il testo libero. **Errore**: causa e azione Riprova.
- Chiudere o ignorare la notifica non chiude la richiesta. Resta nell'inbox con badge finché l'utente risponde o la scarta (sezione 7). Non si rimanda la stessa notifica più volte.
- Senza rete o senza chiave la cattura resta salvata con l'audio e arriva la notifica di errore con Riprova.
- Nessuna registrazione parte senza un gesto esplicito dell'utente.
- Se il permesso microfono manca, chiederlo al primo avvio.
- Documentare nel README i passi lato telefono. Su Samsung: Impostazioni, Funzioni avanzate, Tasto laterale, Doppia pressione, scelta dell'app. Verificare sul dispositivo se la versione di One UI permette di scegliere anche la scorciatoia interna.
- La stessa scorciatoia resta disponibile come ingresso dedicato in launcher.

## 12. Fasi e criteri di accettazione

Sette fasi, ognuna rilasciabile da sola. La fase 0 non produce codice.

| Fase | Contenuto | Fatto quando |
| --- | --- | --- |
| 0 | Esplorazione della repo e piano | Gabriele approva mappa dei dati, migrazioni e dipendenze |
| 1 | Impostazioni, salvataggio chiavi, `LlmClient`, test connessione | Il test connessione riesce e la chiave non compare in repo, log o backup |
| 2 | Chat testuale, `AgentLoop`, tool di lettura, card riepilogo | "Quante proteine mi mancano oggi?" risponde con i numeri del diario |
| 3 | `FoodResolver`, `QuantityNormalizer`, porzioni, log pasto diretto con Annulla | L'esempio di riso, pollo e olio scrive subito il pasto con i macro corretti e Annulla lo rimuove. "Mezzo merluzzo Findus, il solito" registra metà dei grammi della confezione |
| 4 | Voce: registrazione, `Transcriber`, avvio rapido, CaptureWorker, notifiche, inbox delle richieste in sospeso | Lo stesso esempio funziona a voce dalla scorciatoia, con telefono bloccato, e arriva la notifica con Annulla. Un prodotto non trovato lascia una richiesta che sopravvive alla notifica ignorata e alla chiusura dell'app |
| 5 | Barcode in chat, log peso, log retroattivo | "Ieri sera..." finisce sul giorno giusto e il prodotto scansionato entra nel pasto |
| 6 | Bozze di piano, suggerimento pasto, memoria, elenco thread | Una bozza di piano si vede nella schermata allenamenti e non sostituisce il piano attivo |

Criteri trasversali:

- Un log di pasto o peso si scrive subito e Annulla riporta il database identico a prima. Deve esistere un test che lo dimostra.
- Un piano di allenamento non diventa mai attivo senza Approva. Deve esistere un test che lo dimostra.
- "180 grammi, no aspetta, 120" registra 120.
- "Mezzo merluzzo Findus gratinato, il solito" registra 0,5 volte i grammi della porzione scatola del prodotto già in storico, con i suoi valori. Grammi e macro salvati nel log non cambiano se il prodotto viene modificato dopo.
- Un alimento sconosciuto non viene mai scritto: genera una richiesta in sospeso con la stima come opzione marcata.
- In una frase con più item, quelli risolti si registrano subito anche se un altro resta in sospeso.
- Una richiesta in sospeso sopravvive alla notifica ignorata e al riavvio dell'app, e rispondere da notifica, inbox o chat la chiude negli altri.
- Dopo la risposta, il nuovo log si aggiunge allo stesso pasto.
- Una cattura non va mai persa: con rete assente o chiave mancante resta salvata e Riprova la porta a termine senza duplicare log.
- Con rete assente o chiave mancante l'app non va in crash e il resto delle funzioni resta usabile.
- Test unitari su `FoodResolver`, `QuantityNormalizer`, validazione dei tool e macchina a stati di catture e richieste. Il modello va sostituito con un finto nei test.
- I macro mostrati nelle card coincidono con quelli salvati nel diario.

## 13. Punti aperti

Sei decisioni restano da prendere. Le prime tre le chiude l'agent nella fase 0, le altre Gabriele.

- [ ] Fonte e formato della tabella di alimenti base, con verifica delle condizioni d'uso.
- [ ] Valori iniziali delle misure casalinghe.
- [ ] Meccanismo di cifratura delle chiavi coerente con le versioni Android supportate dall'app.
- [ ] Provider e modelli di default per LLM e speech-to-text.
- [ ] La scorciatoia interna è selezionabile dal tasto laterale su questo telefono, oppure serve l'ingresso dedicato in launcher?
- [ ] Il tasto laterale lancia la scorciatoia di cattura e la registrazione resta attiva se si blocca il telefono subito dopo Fine? Verificare sul dispositivo.

Assunzioni fatte in questo documento, da correggere se sbagliate:

- Una stima del modello non si scrive mai da sola: compare come opzione in una richiesta in sospeso, da scegliere o scartare.
- Senza quantità si usa 1 porzione predefinita, mostrata nell'esito. Senza porzione predefinita si apre una richiesta in sospeso.
- Le note in memoria utente si scrivono subito in chat e sono disattivate nell'avvio rapido.
- Ogni cattura da avvio rapido crea un thread nuovo. Le richieste in sospeso non scadono mai da sole.
