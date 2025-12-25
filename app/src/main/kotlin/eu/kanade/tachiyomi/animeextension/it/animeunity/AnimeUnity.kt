package eu.kanade.tachiyomi.animeextension.it.animeunity

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class AnimeUnity : AnimeHttpSource() {

    override val name = "AnimeUnity"
    override val baseUrl = "https://www.animeunity.so"
    override val lang = "it"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    // ============================== Popular ===============================
    // Tradotto dal nostro Dart: getPopular usa /top-anime?popular=true&page=$page

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/top-anime?popular=true&page=$page", headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()

        // Estrai JSON dall'attributo animes="..."
        val animesRegex = """animes="([^"]+)"""".toRegex()
        val match = animesRegex.find(body) ?: return AnimesPage(emptyList(), false)

        val animesJson = match.groupValues[1]
            .replace("&quot;", "\"")
            .replace("&#039;", "'")

        val data = json.parseToJsonElement(animesJson).jsonObject
        val records = data["data"]?.jsonArray ?: return AnimesPage(emptyList(), false)

        val animeList = records.mapNotNull { animeEl ->
            val anime = animeEl.jsonObject
            val title = anime["title_eng"]?.jsonPrimitive?.content
                ?: anime["title"]?.jsonPrimitive?.content
                ?: return@mapNotNull null

            SAnime.create().apply {
                this.title = title
                thumbnail_url = anime["imageurl"]?.jsonPrimitive?.content ?: ""
                url = "/anime/${anime["id"]?.jsonPrimitive?.content}-${anime["slug"]?.jsonPrimitive?.content}"
            }
        }

        val currentPage = data["current_page"]?.jsonPrimitive?.intOrNull ?: 1
        val lastPage = data["last_page"]?.jsonPrimitive?.intOrNull ?: 1

        return AnimesPage(animeList, currentPage < lastPage)
    }

    // =============================== Latest ===============================
    // Tradotto dal nostro Dart: getLatestUpdates usa /?anime=$page

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?anime=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animeList = document.select("div.latest-anime-container").mapNotNull { container ->
            val link = container.selectFirst("a[href*=/anime/]") ?: return@mapNotNull null
            val img = container.selectFirst("img")
            val title = container.selectFirst("strong.latest-anime-title")?.text() ?: return@mapNotNull null

            SAnime.create().apply {
                this.title = title.replace("&#039;", "'").replace("&amp;", "&")
                thumbnail_url = img?.attr("src") ?: ""
                url = link.attr("href")
            }
        }

        val hasNextPage = document.html().contains("?anime=${response.request.url.queryParameter("anime")?.toIntOrNull()?.plus(1) ?: 2}")

        return AnimesPage(animeList, hasNextPage)
    }

    // =============================== Search ===============================
    // Tradotto dal nostro Dart: search usa POST a /archivio/get-animes

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        throw UnsupportedOperationException()
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val params = AnimeUnityFilters.getSearchParameters(filters)

        // Modalità random
        if (params.random) {
            return getRandomAnime()
        }

        // Ottieni CSRF token dalla pagina archivio
        val archivioResponse = client.newCall(GET("$baseUrl/archivio", headers)).execute()
        val archivioDoc = archivioResponse.asJsoup()
        val csrfToken = archivioDoc.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""

        // Estrai cookies
        var xsrfToken = ""
        var sessionCookie = ""
        archivioResponse.headers.forEach { (name, value) ->
            if (name.equals("set-cookie", ignoreCase = true)) {
                if (value.startsWith("XSRF-TOKEN")) {
                    xsrfToken = value.substringAfter("=").substringBefore(";").replace("%3D", "=")
                }
                if (value.startsWith("animeunity_session")) {
                    sessionCookie = value.substringBefore(";").replace("%3D", "=")
                }
            }
        }

        val searchHeaders = headers.newBuilder()
            .add("X-CSRF-TOKEN", csrfToken)
            .add("X-XSRF-TOKEN", xsrfToken)
            .add("Cookie", sessionCookie)
            .add("Content-Type", "application/json;charset=utf-8")
            .add("Accept", "application/json, text/plain, */*")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/archivio")
            .build()

        val offset = (page - 1) * 30

        val bodyJson = buildString {
            append("{")
            append("\"title\":${if (query.isNotEmpty()) "\"$query\"" else "false"},")
            append("\"type\":${params.type.toJsonValue()},")
            append("\"year\":${params.year.toJsonValue()},")
            append("\"order\":\"title_eng\",")
            append("\"status\":${params.status.toJsonValue()},")
            append("\"genres\":${if (params.genres.isNotEmpty()) params.genres else "false"},")
            append("\"offset\":$offset,")
            append("\"dubbed\":${params.dubbed},")
            append("\"season\":${params.season.toJsonValue()}")
            append("}")
        }

        val request = POST(
            "$baseUrl/archivio/get-animes",
            searchHeaders,
            bodyJson.toRequestBody("application/json".toMediaType())
        )

        return client.newCall(request).awaitSuccess().use { response ->
            searchAnimeParse(response, page)
        }
    }

    private fun String.toJsonValue(): String = if (this.isEmpty()) "false" else "\"$this\""

    private suspend fun getRandomAnime(): AnimesPage {
        val response = client.newCall(GET("$baseUrl/randomanime", headers)).execute()
        val body = response.body.string()

        val animeRegex = """video-player anime="(\{[^"]+\})"""".toRegex()
        val match = animeRegex.find(body) ?: return AnimesPage(emptyList(), false)

        val animeJson = match.groupValues[1]
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("\\/", "/")

        val animeData = json.parseToJsonElement(animeJson).jsonObject
        val title = animeData["title_eng"]?.jsonPrimitive?.content
            ?: animeData["title"]?.jsonPrimitive?.content
            ?: return AnimesPage(emptyList(), false)

        val anime = SAnime.create().apply {
            this.title = title
            thumbnail_url = animeData["imageurl"]?.jsonPrimitive?.content ?: ""
            url = "/anime/${animeData["id"]?.jsonPrimitive?.content}-${animeData["slug"]?.jsonPrimitive?.content}"
        }

        return AnimesPage(listOf(anime), false)
    }

    private fun searchAnimeParse(response: Response, page: Int): AnimesPage {
        val body = response.body.string()
        val data = json.parseToJsonElement(body).jsonObject

        val records = data["records"]?.jsonArray ?: return AnimesPage(emptyList(), false)
        val total = data["tot"]?.jsonPrimitive?.intOrNull ?: 0

        val animeList = records.mapNotNull { animeEl ->
            val anime = animeEl.jsonObject
            val title = anime["title_eng"]?.jsonPrimitive?.content
                ?: anime["title"]?.jsonPrimitive?.content
                ?: return@mapNotNull null

            SAnime.create().apply {
                this.title = title
                thumbnail_url = anime["imageurl"]?.jsonPrimitive?.content ?: ""
                url = "/anime/${anime["id"]?.jsonPrimitive?.content}-${anime["slug"]?.jsonPrimitive?.content}"
            }
        }

        val offset = (page - 1) * 30
        val hasNextPage = (offset + 30) < total

        return AnimesPage(animeList, hasNextPage)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        throw UnsupportedOperationException()
    }

    // =========================== Anime Details ============================
    // Tradotto dal nostro Dart: getDetail estrae info dal video-player

    override fun animeDetailsRequest(anime: SAnime): Request {
        return GET("$baseUrl${anime.url}", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val body = response.body.string()

        val animeRegex = """video-player anime="(\{[^"]+\})"""".toRegex()
        val match = animeRegex.find(body) ?: return SAnime.create()

        val animeJson = match.groupValues[1]
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("\\/", "/")

        val animeData = json.parseToJsonElement(animeJson).jsonObject

        return SAnime.create().apply {
            title = animeData["title_eng"]?.jsonPrimitive?.content
                ?: animeData["title"]?.jsonPrimitive?.content ?: ""
            description = animeData["plot"]?.jsonPrimitive?.content ?: ""
            author = animeData["studio"]?.jsonPrimitive?.content ?: ""

            genre = animeData["genres"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            }?.joinToString(", ") ?: ""

            status = when (animeData["status"]?.jsonPrimitive?.content) {
                "In Corso" -> SAnime.ONGOING
                "Terminato", "Completato" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
        }
    }

    // ============================== Episodes ==============================
    // Tradotto dal nostro Dart: getDetail estrae episodi

    override fun episodeListRequest(anime: SAnime): Request {
        return GET("$baseUrl${anime.url}", headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val body = response.body.string()
        val url = response.request.url.toString()

        val episodesRegex = """episodes="(\[[^\]]+\])"""".toRegex()
        val match = episodesRegex.find(body) ?: return emptyList()

        val episodesJson = match.groupValues[1]
            .replace("&quot;", "\"")
            .replace("&#039;", "'")

        val episodesData = json.parseToJsonElement(episodesJson).jsonArray

        return episodesData.mapNotNull { epEl ->
            val ep = epEl.jsonObject
            val epNum = ep["number"]?.jsonPrimitive?.content ?: return@mapNotNull null

            SEpisode.create().apply {
                name = "Episodio $epNum"
                this.url = "$url/$epNum"
                episode_number = epNum.toFloatOrNull() ?: 0f
            }
        }.reversed()
    }

    // ============================ Video Links =============================
    // Tradotto dal nostro Dart: getVideoList usa embed-url API + vixcloud

    override fun videoListRequest(episode: SEpisode): Request {
        return GET(episode.url, headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val body = response.body.string()
        val url = response.request.url.toString()
        val videos = mutableListOf<Video>()

        // Trova numero episodio dall'URL
        val episodeNum = url.substringAfterLast("/")

        // Estrai lista episodi
        val episodesRegex = """episodes="(\[[^\]]+\])"""".toRegex()
        val episodesMatch = episodesRegex.find(body) ?: return emptyList()

        val episodesJson = episodesMatch.groupValues[1]
            .replace("&quot;", "\"")
            .replace("&#039;", "'")

        val episodesData = json.parseToJsonElement(episodesJson).jsonArray

        // Trova l'episodio corrente
        val currentEp = episodesData.firstOrNull { ep ->
            ep.jsonObject["number"]?.jsonPrimitive?.content == episodeNum
        }?.jsonObject ?: return emptyList()

        val episodeId = currentEp["id"]?.jsonPrimitive?.content ?: return emptyList()

        // Chiama API per embed URL
        val embedHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val embedUrlResponse = client.newCall(
            GET("$baseUrl/embed-url/$episodeId", embedHeaders)
        ).execute()

        val embedUrl = embedUrlResponse.body.string()
        if (embedUrl.isEmpty() || !embedUrl.startsWith("http")) return emptyList()

        // Fetch pagina embed per estrarre download URL
        val embedPageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()

        val embedPage = client.newCall(GET(embedUrl, embedPageHeaders)).execute().body.string()

        // Estrai download URL
        val downloadRegex = """downloadUrl\s*=\s*'([^']+)'""".toRegex()
        val downloadMatch = downloadRegex.find(embedPage) ?: return emptyList()
        val downloadUrl = downloadMatch.groupValues[1]

        val videoHeaders = mapOf("Referer" to "https://vixcloud.co/")

        // Aggiungi qualità video
        videos.add(Video(downloadUrl, "1080p", downloadUrl, headers = videoHeaders.toHeaders()))
        videos.add(Video(
            downloadUrl.replace("1080p.mp4", "720p.mp4"),
            "720p",
            downloadUrl.replace("1080p.mp4", "720p.mp4"),
            headers = videoHeaders.toHeaders()
        ))
        videos.add(Video(
            downloadUrl.replace("1080p.mp4", "480p.mp4"),
            "480p",
            downloadUrl.replace("1080p.mp4", "480p.mp4"),
            headers = videoHeaders.toHeaders()
        ))

        return videos
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers {
        return okhttp3.Headers.Builder().apply {
            forEach { (key, value) -> add(key, value) }
        }.build()
    }

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = AnimeUnityFilters.FILTER_LIST
}
