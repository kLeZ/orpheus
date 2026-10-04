# Plan: English migration and publication

Decision in [ADR-0022](adr/0022-english-and-public-project.md) (2026-09-28). This document is the
operating plan.

## Inventory (2026-09-28)

| Module | Sources | Lines | Tests | Test lines |
| ------ | ------- | ----- | ----- | ---------- |
| `orpheus-domain` | 66 | 3651 | 26 | 3139 |
| `orpheus-persistence` | 9 | 300 | 1 | 135 |
| `orpheus-musicbrainz-client` | 17 | 673 | 6 | 403 |
| `orpheus-acoustid-client` | 10 | 374 | 3 | 264 |
| `orpheus-tagging` | 4 | 503 | 3 | 582 |
| `orpheus-cli` | 8 | 1140 | 0 | 0 |
| `orpheus-app` | 1 | 5 | 12 | 1366 |

About 6600 lines of code and 5900 of tests at the time (120 types in 7 modules on 2026-10-04).
Documentation: README, DESIGN, SPEC, ROADMAP, TODO and the ADRs, all translated in phase 2.

Besides the names in the code, these are in Italian:

- the database schema: tables `movimento`, `segnalazione`, `segnale_grezzo` and their columns
  (`origine`, `destinazione`, `azione`, `motivazione`, `traccia_*`, `categoria`, `stato`...);
- the enum values stored as text in the database (`IMPORTED_AS_IS`, `RICOSTRUITA`,
  `TAG_CORRECTION`, `IN_ATTESA`...);
- the `--simula` option and all the CLI messages;
- the review item texts and the ledger reasons;
- the fields written into the audio files: `ORPHEUS_ORIGINALE_<CAMPO>`;
- the categories of the disk cache (`generi-v1`, `ricerca-release-group-v3`...);
- the names of the test methods (they are sentences:
  `unaTracciaIsolataVieneCopiataERegistrataInCodaDiRevisione`).

The folder placeholders ("Unknown Artist", "Unknown Year", "Unidentified"...) are already in English
and configurable (`orpheus.placeholders.*`, 2026-09-28).

## Publication checks (done on 2026-09-28)

- Secrets in the history (97 commits): the AcoustID key of `.env` never appears; `.env` was never
  tracked; no Anthropic key; the lines with `apiKey` are variable assignments.
- Personal data: the author's email appears only in the commit metadata (visible to anyone who
  clones the public repository); the home directory path appeared only in the context file, now
  outside the repository; the rest mentions `~/Musica` and artists and albums of the user's library.
- License: none.

## Phases

Every phase ends with a green `./mvnw clean install` and a commit. Types and members are renamed with
the IDE refactoring (IntelliJ), not with text substitutions: it also updates the references.

0. **Preliminary decisions** (taken by the user on 2026-09-28):
   - license: **AGPL-3.0-or-later** (copyleft that also covers use over a network, relevant if the
     review interface becomes a web service);
   - history cleaned before publication, so that no commit without a license stays reachable in the
     public repository (for example a single initial commit with the license). Note: code published
     without a license grants no rights by law, so that commit alone would not enable a relicensing;
     the cleanup stays the user's choice;
   - the author's email in the commits is not a problem;
   - the context file translated into English, converted to org and removed from the repository
     (done on 2026-09-28); all harness and AI assistant files (`CLAUDE.md`, `AGENTS.md`, `*.org`...)
     excluded with `.git/info/exclude`, so the agent instructions are not part of the repository;
   - folder placeholders in English and configurable (done on 2026-09-28);
   - glossary: main terms decided on 2026-10-04 (Glossary section).
1. **Glossary** (done on 2026-10-04). Italian to English table of the domain terms, confirmed by the
   user. It is the reference for all the following phases.
2. **Documentation** (done on 2026-10-04). DESIGN, SPEC, README, the ADRs (files renamed with English
   slugs, cross references updated), ROADMAP, TODO, this plan. Before the code, so that the code is
   translated against documents already in English; the documents use the glossary names ahead of
   the code.
3. **Domain** (`orpheus-domain`, done on 2026-10-04): types, members, comments, tests. The largest module, split by
   package (`model`, `identification`, `importazione` to `ingest`, `matching`, `port`, `review`).
   Types renamed on 2026-10-04 through the MCP server of IntelliJ (`rename_refactoring`, references
   updated across all modules); the package moved with `git mv` and the qualified name, because the
   tool does not rename packages. Enum constants stored by name (database, cache) stay until phase
   5; review item texts and defect descriptions stay until phase 6. Records nested in the cache JSON
   files had their keys migrated in the same commit as the rename, and every step was checked with a
   full simulation against a baseline taken before the phase: same ledger, same review queue.
4. **Adapters** (done on 2026-10-04), one module at a time: persistence, musicbrainz-client,
   acoustid-client, tagging, cli, app. Midway IntelliJ was switched to another project, so from the
   tagging module on the renames were scripted (whole-word, compiler-checked), with the same
   verification by build, tests and simulation.
5. **Database** (done on 2026-10-04): no real database existed yet (test runs only, deletable by the
   user's word of 2026-10-04), so the initial schema `V1` itself was rewritten in English
   (`V1__initial_schema.sql`) instead of adding a `V2`, together with the enum values.
6. **User-visible names** (done on 2026-10-04, together with phase 4): `--simula` became `--dry-run`,
   CLI and log messages, review item texts and ledger reasons in English, `ORPHEUS_ORIGINAL_<FIELD>` in
   the files, cache categories with English names (`acoustid-v4`, `musicbrainz-v4`,
   `audio-analysis-v3`, `composers-v1`, `genres-v1`, `recording-search-v2`, `release-group-search-v4`,
   `tracklist-v1`); the existing cache was migrated, keys included.
7. **Agent instructions** (done on 2026-10-04; local files, outside the repository): the exception
   removed, translated into English as `AGENTS.md`, with `CLAUDE.md` as a symlink to it.
8. **Verification** (done on 2026-10-04): a dry run compared with the baseline taken before phase 3,
   row by row (ledger, review items, raw signals, summary counts; reason texts excluded once
   translated): identical. Then a real import into `~/Musica-organizzata`: source intact (2708 files,
   same path, size and modification time), real ledger identical to the dry run, all 2197 copies on
   disk. The full check found three small defects that predate the migration (same result with the
   code of `42dae67`), recorded in `docs/TODO.md`.
9. **Publication** (done on 2026-10-04): AGPL-3.0-or-later `LICENSE`, README for an outside reader,
   history replaced by a single initial commit (the earlier history stays only in a local archive
   branch), pushed to the public repository https://github.com/kLeZ/orpheus.

## Glossary (2026-10-04)

The user's choices of 2026-10-04: Movimento to LedgerEntry, Segnalazione to ReviewItem, Edizione to
Release, package `importazione` to `ingest`; the rest are direct translations. Types already in
English (DTOs, REST clients, `ImportCommand`, `HungarianAssignment`, `NormalizedLevenshtein`, the
values of `TagField`) stay.

### Domain concepts (`model`)

| Italian | English |
| ------- | ------- |
| Traccia | Track |
| MetadatiTraccia | TrackMetadata |
| IdentificatoriEsterni | ExternalIds |
| Provenienza / StatoProvenienza | Provenance / ProvenanceState |
| Album / CompletezzaAlbum | Album / AlbumCompleteness |
| Movimento / AzioneMovimento | LedgerEntry / LedgerAction |
| Segnalazione / CategoriaSegnalazione / StatoSegnalazione | ReviewItem / ReviewCategory / ReviewStatus |
| SegnaleGrezzo | RawSignal |
| Segnaposto | Placeholder |
| TestoAscii | AsciiText |

### Identification (`identification`)

| Italian | English |
| ------- | ------- |
| Edizione / TipoEdizione / InfoRelease | Release / ReleaseType / ReleaseInfo |
| DataPubblicazione | ReleaseDate |
| SelettoreEdizione | ReleaseSelector |
| UnificazioneEdizioni | ReleaseUnification |
| MotoreIdentificazioneLocale / RisultatoIdentificazioneLocale | LocalIdentifier / LocalIdentificationResult |
| EvidenzeLocali | LocalEvidence |
| OrchestratoreIdentificazione | IdentificationPipeline |
| EsitoTraccia | TrackOutcome |
| ConsensoMultiTraccia / EsitoConsenso / EsitoTracciaConsenso | MultiTrackConsensus / ConsensusResult / ConsensusTrackOutcome |
| IdentificatoreTracciaTier2 / CandidaturaTier2 | Tier2Identifier / Tier2Candidacy |
| RecordingCandidato | CandidateRecording |
| RisultatoRicercaImpronte | FingerprintLookupResult |
| FiltroRecordingCandidati | CandidateRecordingFilter |
| DurateDaNome | DurationsByName |
| ParserNomeFile / EuristicaNomeFile | FileNameParser / FileNameHeuristic |
| ClassificazioneTestuale | TextClassification |

### Library building (package `importazione` to `ingest`)

| Italian | English |
| ------- | ------- |
| PercorsoDestinazione | DestinationPath |
| CollocazioneCopie | CopyPlacement |
| NomiArtistiCanonici | CanonicalArtistNames |
| NomiSegnaposto | PlaceholderNames |
| RegoleTag / TagDaScrivere / ContestoTag / CampoTag | TagRules / TagsToWrite / TagContext / TagField |
| GeneriPerNome | GenresByName |
| IntegritaAudio | AudioIntegrity |
| ConfrontoArtisti (`matching`) | ArtistComparison |

### Ports (`port`) and adapters

| Italian | English |
| ------- | ------- |
| RicercaImpronteAudioPort | FingerprintLookupPort |
| RicercaEdizioniPort | ReleaseLookupPort |
| RicercaReleaseGroupPort / RicercaRecordingPort | ReleaseGroupSearchPort / RecordingSearchPort |
| TracklistPort / CompositoriPort | TracklistPort / ComposersPort |
| GeneriMusicaliPort | GenresPort |
| ImprontaAudioPort | AudioHashPort |
| AnalisiAudioPort | AudioAnalysisPort |
| LettoreTagPort / ScritturaTagPort | TagReaderPort / TagWriterPort |
| RegistroMovimentiPort | LedgerPort |
| CodaRevisionePort | ReviewQueuePort |
| ClassificazioneTestualePort | TextClassificationPort |
| InizializzazioneArchivioPort | StorageInitializationPort |
| TracciaIlleggibileException | UnreadableTrackException |
| adapters `...ConCache`, `ExternalResponseCache` | `Cached...`, `ExternalResponseCache` |
| `RicercaImpronteConDurataDaNome` | `FullLengthDurationLookup` |
| `FfmpegAnalisiAudio`, `FfmpegImprontaAudio` | `FfmpegAudioAnalysis`, `FfmpegAudioHash` |
| `JaudiotaggerLettoreTag`, `JaudiotaggerScritturaTag` | `JaudiotaggerTagReader`, `JaudiotaggerTagWriter` |
| `FpcalcResult` | `FpcalcResult` |
| `MusicBrainzReleaseLookup` and similar | `MusicBrainzReleaseLookup` and similar |
| `LedgerEntryEntity`, `ReviewItemEntity`, `RawSignalEntity` | as the domain types, with `Entity` |
| packages `lettura` / `scrittura` | `reading` / `writing` |

### Values stored in the database

| Italian | English |
| ------- | ------- |
| IMPORTATA_COSI_COM_E / IMPORTATA_ARRICCHITA / RICONCILIATA / RICOSTRUITA / SCARTATA_DUPLICATA | IMPORTED_AS_IS / IMPORTED_ENRICHED / RECONCILED / RECONSTRUCTED / DISCARDED_DUPLICATE |
| QUALITA_AUDIO / EDIT_SOSPETTO / EQ_ANOMALA / CORREZIONE_TAG | AUDIO_QUALITY / SUSPECTED_EDIT / ABNORMAL_EQ / TAG_CORRECTION |
| IDENTIFICAZIONE_INCERTA / NON_IDENTIFICATA / RICOSTRUZIONE_TIER2 | UNCERTAIN_IDENTIFICATION / UNIDENTIFIED / TIER2_RECONSTRUCTION |
| DUPLICATO_IDENTICO / VERSIONE_ALTERNATIVA | IDENTICAL_DUPLICATE / ALTERNATIVE_VERSION |
| IN_ATTESA / CONFERMATA / DA_SOSTITUIRE / SOSTITUITA / RIFIUTATA | PENDING / CONFIRMED / TO_REPLACE / REPLACED / REJECTED |
| INTATTA / TAG_CORRETTI / AUDIO_SOSTITUITO | INTACT / TAGS_CORRECTED / AUDIO_REPLACED |
| COMPLETA / PARZIALE / NON_VERIFICATA | COMPLETE / PARTIAL / UNVERIFIED |
| ALBUM / EP / SINGOLO / ALTRO | ALBUM / EP / SINGLE / OTHER |
| DISTANZA_MATCH / SCOSTAMENTO_DURATA / ANOMALIA_SPETTRALE / PUNTEGGIO_FINGERPRINT / AUDIO_MANCANTE / CONFIDENZA_CLASSIFICAZIONE_TESTUALE | MATCH_DISTANCE / DURATION_DEVIATION / SPECTRAL_ANOMALY / FINGERPRINT_SCORE / MISSING_AUDIO / TEXT_CLASSIFICATION_CONFIDENCE |
| tables `movimento`, `segnalazione`, `segnale_grezzo` | `ledger_entry`, `review_item`, `raw_signal` |
| columns `origine`, `destinazione`, `azione`, `motivazione`, `confidenza`, `momento`, `categoria`, `stato`, `tipo`, `valore`, `traccia_*` | `source`, `destination`, `action`, `reason`, `confidence`, `recorded_at`, `category`, `status`, `type`, `value`, `track_*` |

### User-visible names

| Italian | English |
| ------- | ------- |
| `--simula` | `--dry-run` |
| `ORPHEUS_ORIGINALE_<CAMPO>` in the files | `ORPHEUS_ORIGINAL_<FIELD>` (files already written stay as they are: restoring reads both) |
| cache categories (`generi-v1`, `analisi-audio-v2`, `compositori-v1`...) | `genres-v1`, `audio-analysis-v2`, `composers-v1`... (the existing folders are renamed) |
| CLI messages, ledger reasons, review item texts | in English |

## Risks

- Incomplete renames: a string in the database or in the cache with the old name stops matching.
  Mitigation: a separate phase 5, a test on a real database, a compared simulation.
- Documents and code diverging during the migration. Mitigation: glossary fixed first,
  documentation before the code.
- Tests that stop saying what they check, when sentence-like names are translated badly. Mitigation:
  translate the meaning, not the words, rereading the body of the test.
