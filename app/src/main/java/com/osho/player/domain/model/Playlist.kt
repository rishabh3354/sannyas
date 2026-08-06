package com.osho.player.domain.model

/**
 * A sequence the listener assembled themselves.
 *
 * Holds only the counts and naming the list screens need; the talks are
 * resolved against the bundled catalogue on demand, exactly as favourites and
 * history are, so a playlist survives a catalogue swap as long as the ids do.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val updatedAt: Long,
)
