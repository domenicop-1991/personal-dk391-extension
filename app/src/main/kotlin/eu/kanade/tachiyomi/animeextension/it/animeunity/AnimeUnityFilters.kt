package eu.kanade.tachiyomi.animeextension.it.animeunity

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimeUnityFilters {

    data class FilterSearchParams(
        val random: Boolean = false,
        val type: String = "",
        val status: String = "",
        val year: String = "",
        val dubbed: Boolean = false,
        val season: String = "",
        val genres: String = ""
    )

    fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        var random = false
        var type = ""
        var status = ""
        var year = ""
        var dubbed = false
        var season = ""
        val genresList = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is RandomFilter -> random = filter.state
                is TypeFilter -> type = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                is YearFilter -> year = filter.toUriPart()
                is DubFilter -> dubbed = filter.state
                is SeasonFilter -> season = filter.toUriPart()
                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.state) {
                            genresList.add("""{"id":${genre.id},"name":"${genre.name}"}""")
                        }
                    }
                }
                else -> {}
            }
        }

        val genresJson = if (genresList.isNotEmpty()) "[${genresList.joinToString(",")}]" else ""

        return FilterSearchParams(
            random = random,
            type = type,
            status = status,
            year = year,
            dubbed = dubbed,
            season = season,
            genres = genresJson
        )
    }

    val FILTER_LIST get() = AnimeFilterList(
        AnimeFilter.Header("Attiva Random e cerca per un anime casuale"),
        RandomFilter(),
        AnimeFilter.Separator(),
        AnimeFilter.Header("Filtri (funzionano solo con la ricerca)"),
        TypeFilter(),
        StatusFilter(),
        YearFilter(),
        DubFilter(),
        SeasonFilter(),
        GenreFilter()
    )

    class RandomFilter : AnimeFilter.CheckBox("Anime Casuale", false)
    class DubFilter : AnimeFilter.CheckBox("Solo Doppiati", false)

    class TypeFilter : UriPartFilter("Tipo", arrayOf(
        Pair("Tutti", ""),
        Pair("TV", "TV"),
        Pair("Movie", "Movie"),
        Pair("OVA", "OVA"),
        Pair("ONA", "ONA"),
        Pair("Special", "Special")
    ))

    class StatusFilter : UriPartFilter("Stato", arrayOf(
        Pair("Tutti", ""),
        Pair("In Corso", "In Corso"),
        Pair("Terminato", "Terminato")
    ))

    class YearFilter : UriPartFilter("Anno", arrayOf(
        Pair("Tutti", ""),
        Pair("2025", "2025"),
        Pair("2024", "2024"),
        Pair("2023", "2023"),
        Pair("2022", "2022"),
        Pair("2021", "2021"),
        Pair("2020", "2020"),
        Pair("2019", "2019"),
        Pair("2018", "2018"),
        Pair("2017", "2017"),
        Pair("2016", "2016"),
        Pair("2015", "2015"),
        Pair("2014", "2014"),
        Pair("2013", "2013"),
        Pair("2012", "2012"),
        Pair("2011", "2011"),
        Pair("2010", "2010")
    ))

    class SeasonFilter : UriPartFilter("Stagione", arrayOf(
        Pair("Tutte", ""),
        Pair("Inverno", "winter"),
        Pair("Primavera", "spring"),
        Pair("Estate", "summer"),
        Pair("Autunno", "fall")
    ))

    class GenreFilter : AnimeFilter.Group<GenreCheckBox>("Generi", listOf(
        GenreCheckBox("Action", 51),
        GenreCheckBox("Adventure", 21),
        GenreCheckBox("Comedy", 37),
        GenreCheckBox("Demons", 13),
        GenreCheckBox("Drama", 22),
        GenreCheckBox("Ecchi", 5),
        GenreCheckBox("Fantasy", 9),
        GenreCheckBox("Game", 44),
        GenreCheckBox("Gore", 52),
        GenreCheckBox("Gourmet", 56),
        GenreCheckBox("Harem", 15),
        GenreCheckBox("Historical", 30),
        GenreCheckBox("Horror", 3),
        GenreCheckBox("Isekai", 53),
        GenreCheckBox("Josei", 45),
        GenreCheckBox("Martial Arts", 31),
        GenreCheckBox("Mecha", 38),
        GenreCheckBox("Military", 46),
        GenreCheckBox("Music", 16),
        GenreCheckBox("Mystery", 24),
        GenreCheckBox("Parody", 32),
        GenreCheckBox("Police", 39),
        GenreCheckBox("Psychological", 47),
        GenreCheckBox("Romance", 17),
        GenreCheckBox("Samurai", 25),
        GenreCheckBox("School", 33),
        GenreCheckBox("Sci-fi", 40),
        GenreCheckBox("Seinen", 49),
        GenreCheckBox("Shoujo", 18),
        GenreCheckBox("Shounen", 34),
        GenreCheckBox("Slice of Life", 50),
        GenreCheckBox("Space", 19),
        GenreCheckBox("Sports", 27),
        GenreCheckBox("Super Power", 35),
        GenreCheckBox("Supernatural", 42),
        GenreCheckBox("Thriller", 48),
        GenreCheckBox("Vampire", 20)
    ))

    class GenreCheckBox(name: String, val id: Int) : AnimeFilter.CheckBox(name, false)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
