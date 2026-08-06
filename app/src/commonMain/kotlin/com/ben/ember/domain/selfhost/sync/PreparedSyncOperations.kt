package com.ben.ember.domain.selfhost.sync

import com.ben.ember.data.local.room.NoteBlockEntity
import com.ben.ember.data.local.room.NoteMetadataEntity
import com.ben.ember.domain.selfhost.translation.BlockTombstone
import com.ben.ember.domain.selfhost.translation.EmbeddedBlockPayload

data class PreparedSyncOperations(
    val metadataUpsert: NoteMetadataEntity,
    val blockUpserts: List<NoteBlockEntity>,
    val blockDeletions: List<BlockTombstone>,
    val embeddedBlocks: List<EmbeddedBlockPayload> = emptyList()
)
