package com.sannyas.player

import com.sannyas.player.data.local.catalog.CatalogSnapshot
import com.sannyas.player.data.repository.CatalogRepositoryImpl
import com.sannyas.player.domain.model.ContentLanguage
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.DiscourseLanguage
import com.sannyas.player.domain.model.Track
import com.sannyas.player.data.local.datastore.SettingsDataStore
import com.sannyas.player.data.local.datastore.UserSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Search ranking is the part of the catalog layer most likely to regress
 * silently, since a bad ranking still returns results.
 */
class CatalogRepositoryTest {

    private lateinit var repository: CatalogRepositoryImpl

    private fun discourse(
        id: String,
        title: String,
        category: String = "Zen And Zen Masters",
        description: String = "",
        tags: List<String> = emptyList(),
        popularity: Int = 0,
        trackTitles: List<String> = listOf("$title 01"),
    ): Discourse {
        val tracks = trackTitles.mapIndexed { index, trackTitle ->
            Track(
                id = "$id#${index + 1}",
                discourseId = id,
                title = trackTitle,
                url = "https://example.invalid/$id/${index + 1}.mp3",
                durationSeconds = 3_600,
                index = index + 1,
                number = index + 1,
                artworkUrl = "",
                discourseTitle = title,
                speaker = "Osho",
            )
        }
        return Discourse(
            id = id,
            title = title,
            description = description,
            image = "",
            category = category,
            durationSeconds = tracks.sumOf { it.durationSeconds },
            speaker = "Osho",
            language = DiscourseLanguage.ENGLISH,
            tracks = tracks,
            tags = tags,
            popularity = popularity,
        )
    }

    private val catalog = listOf(
        discourse("bird", "A Bird on the Wing", tags = listOf("zen"), popularity = 100),
        discourse("birds-nest", "Birds in the Nest", popularity = 900),
        discourse(
            id = "tao",
            title = "Tao The Three Treasures",
            category = "Tao",
            description = "Talks on the Taoist masters.",
            popularity = 500,
        ),
        discourse(
            id = "thunder",
            title = "A Sudden Clash of Thunder",
            popularity = 300,
            trackTitles = listOf("A Sudden Clash of Thunder 01", "A Sudden Clash of Thunder 02"),
        ),
    )

    @Before
    fun setUp() {
        val dataSource = mockk<com.sannyas.player.data.local.catalog.CatalogDataSource>()
        coEvery { dataSource.load() } returns CatalogSnapshot(
            discourses = catalog,
            categories = emptyList(),
        )
        // Language filtering is exercised separately; here it must not narrow.
        val settings = mockk<SettingsDataStore>()
        every { settings.settings } returns flowOf(
            UserSettings(contentLanguage = ContentLanguage.ALL),
        )
        repository = CatalogRepositoryImpl(dataSource, settings)
    }

    @Test
    fun `single letter queries are ignored`() = runTest {
        assertTrue(repository.search("a").isEmpty)
    }

    @Test
    fun `an exact title match outranks a more popular partial match`() = runTest {
        val results = repository.search("a bird on the wing")
        assertEquals("bird", results.discourses.first().id)
    }

    @Test
    fun `a prefix match outranks a mid-string match`() = runTest {
        val results = repository.search("bird")
        // "Birds in the Nest" starts with the query; "A Bird on the Wing" only contains it.
        assertEquals("birds-nest", results.discourses.first().id)
    }

    @Test
    fun `every term must match, so extra words narrow the result`() = runTest {
        assertEquals(1, repository.search("bird wing").discourses.size)
        assertTrue(repository.search("bird zeppelin").discourses.isEmpty())
    }

    @Test
    fun `searching a category name finds its series`() = runTest {
        val results = repository.search("tao")
        assertEquals("tao", results.discourses.first().id)
    }

    @Test
    fun `track results match on the talk title`() = runTest {
        val results = repository.search("thunder 02")
        assertEquals(1, results.tracks.size)
        assertEquals("A Sudden Clash of Thunder 02", results.tracks.first().title)
    }

    @Test
    fun `suggestions put prefix matches first`() = runTest {
        val suggestions = repository.suggestions("bird")
        assertEquals("Birds in the Nest", suggestions.first())
    }

    @Test
    fun `getTrack resolves the positional id assigned during parsing`() = runTest {
        val track = repository.getTrack("thunder#2")
        assertEquals("A Sudden Clash of Thunder 02", track?.title)
    }
}
