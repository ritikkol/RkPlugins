package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://new3.katmoviehd.top"
    override var name = "KatMovieHD"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/category/bollywood-movies/" to "Bollywood",
        "$mainUrl/category/hollywood-movies/" to "Hollywood",
        "$mainUrl/category/tv-series-dubbed/" to "TV Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val document = app.get(url).document
        val home = document.select("article, div.post, div.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2 a, h3 a, .entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article, div.post, div.item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst(".entry-content img, .post-thumbnail img")?.attr("src"))
        val description = document.selectFirst(".entry-content p")?.text()?.trim()

        val links = document.select(".entry-content a[href]")
            .filter {
                val text = it.text().lowercase()
                text.contains("download") || text.contains("watch") || text.contains("480p") || text.contains("720p") || text.contains("1080p")
            }
            .map { it.attr("href") }

        val isSeries = title.contains("Season", ignoreCase = true) || title.contains("S0", ignoreCase = true)

        return if (isSeries) {
            val episodes = links.mapIndexed { index, link ->
                newEpisode(link) {
                    this.name = "Link ${index + 1}"
                    this.episode = index + 1
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, links.joinToString(",")) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val urls = data.split(",").filter { it.isNotBlank() }
        for (url in urls) {
            loadExtractor(url, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
