package com.ben.inly.domain.selfhost.sync

import com.ben.inly.data.local.room.NoteBlockEntity
import com.ben.inly.data.local.room.NoteMetadataEntity
import com.ben.inly.domain.selfhost.translation.BlockTombstone
import com.ben.inly.domain.selfhost.translation.EmbeddedBlockPayload

data class PreparedSyncOperations(
    val metadataUpsert: NoteMetadataEntity,
    val blockUpserts: List<NoteBlockEntity>,
    val blockDeletions: List<BlockTombstone>,
    val embeddedBlocks: List<EmbeddedBlockPayload> = emptyList()
)
