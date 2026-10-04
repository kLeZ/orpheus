-- Ledger (LedgerPort): one event per row, never updated after insertion.
CREATE TABLE ledger_entry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,
    destination TEXT NOT NULL,
    action TEXT NOT NULL,
    reason TEXT NOT NULL,
    confidence REAL NOT NULL,
    recorded_at TIMESTAMP NOT NULL
);

-- Review queue (ReviewQueuePort). The Track stored here is a snapshot at the time of the review item,
-- not a track catalog with a life of its own (the ports are write-only, no query needs a track
-- normalized elsewhere): it is denormalized on the row instead of introducing a track table with a
-- relation, an abstraction justified only by query needs that do not exist yet.
CREATE TABLE review_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    track_physical_path TEXT NOT NULL,
    track_artist TEXT,
    track_title TEXT,
    track_album TEXT,
    track_album_artist TEXT,
    track_year INTEGER,
    track_number INTEGER,
    track_disc_number INTEGER,
    track_musicbrainz_recording_id TEXT,
    track_musicbrainz_release_id TEXT,
    track_acoustid TEXT,
    track_provenance_source TEXT NOT NULL,
    track_provenance_state TEXT NOT NULL,
    category TEXT NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL
);

-- List<RawSignal> (sealed) as a child table, not a JSON column: all the variants share the same shape
-- (a single numeric metric plus a confidence in (0,1]), so a generic "value" column discriminated by
-- "type" stays typed and queryable with Panache query methods, instead of a JSON text that would need
-- ad-hoc parsing outside the control of the schema (the "stringly typed" ADR-010 wants to avoid). The
-- empty list (e.g. ReviewCategory.UNIDENTIFIED) is simply no child row.
CREATE TABLE raw_signal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    review_item_id INTEGER NOT NULL REFERENCES review_item (id),
    type TEXT NOT NULL,
    value REAL NOT NULL,
    confidence REAL NOT NULL
);

CREATE INDEX idx_raw_signal_review_item_id ON raw_signal (review_item_id);
