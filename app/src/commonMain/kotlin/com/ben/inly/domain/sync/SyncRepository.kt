package com.ben.inly.domain.sync

interface SyncRepository {
    // Collects all changes since `since` - takes the cursor explicitly rather than reading a
    // stored watermark internally, so the desktop server can answer each peer's fetch using THAT
    // peer's own cursor instead of a single shared server-side watermark that starves a second
    // client device and can't be safely advanced before the peer confirms it applied the changes.
    suspend fun collectLocalChanges(since: Long): List<SyncEnvelope>

    // Applies incoming changes to the local Room database and file storage. Returns true only if
    // every envelope applied without error - the caller must not advance its own watermark past
    // this batch otherwise, or a single failed envelope is silently never retried.
    suspend fun applyRemoteChanges(changes: List<SyncEnvelope>): Boolean

    // Deletes media files that no local note references anymore AND have sat that way for over a
    // day (see the grace-period reasoning on the self-host equivalent) - without this, every
    // deleted image/attachment accumulates on the desktop server and on-device forever.
    suspend fun cleanupOrphanedMedia()
}