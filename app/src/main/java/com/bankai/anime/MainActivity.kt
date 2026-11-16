package com.bankai.anime

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File
import java.util.UUID

private val Ink = Color(0xFF090A0F)
private val SurfaceOne = Color(0xFF12141C)
private val SurfaceTwo = Color(0xFF1A1D28)
private val Line = Color(0xFF292D3A)
private val Violet = Color(0xFF9B7BFF)
private val VioletSoft = Color(0xFF5F4D9B)
private val Paper = Color(0xFFF4F1FA)
private val Muted = Color(0xFFA5A5B5)
private val Mint = Color(0xFF79D7B2)

private enum class AppPage { HOME, LIBRARY, WEEKLY }
private data class EpisodeAddress(val animeId: String, val seasonId: String, val number: Int)
private data class PlayingEpisode(val title: String, val path: String, val address: EpisodeAddress)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BankaiRoot() }
    }
}

@Composable
private fun BankaiRoot() {
    val context = LocalContext.current
    var library by remember { mutableStateOf(LibraryStore.load(context)) }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var selectedAnimeId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<PlayingEpisode?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showAnimeEditor by remember { mutableStateOf(false) }
    var editingAnimeId by remember { mutableStateOf<String?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftPosterUrl by remember { mutableStateOf("") }
    var draftPosterPath by remember { mutableStateOf<String?>(null) }
    var showSeasonEditor by remember { mutableStateOf(false) }
    var pendingVideo by remember { mutableStateOf<EpisodeAddress?>(null) }
    var showDeleteAnime by remember { mutableStateOf(false) }

    fun save(next: List<AnimeRecord>) {
        library = next
        LibraryStore.save(context, next)
    }

    fun updateAnime(id: String, transform: (AnimeRecord) -> AnimeRecord) {
        save(library.map { if (it.id == id) transform(it) else it })
    }

    fun beginAnimeEdit(existing: AnimeRecord? = null) {
        editingAnimeId = existing?.id
        draftTitle = existing?.title.orEmpty()
        draftPosterUrl = existing?.posterUrl.orEmpty()
        draftPosterPath = existing?.posterPath
        showAnimeEditor = true
    }

    val posterPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val newPath = copyUriToPrivateStorage(context, uri, "posters")
            if (newPath != null) draftPosterPath = newPath
            else Toast.makeText(context, "Poster could not be imported.", Toast.LENGTH_SHORT).show()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingVideo
        pendingVideo = null
        if (uri != null && target != null) {
            val newPath = copyUriToPrivateStorage(context, uri, "videos")
            if (newPath != null) {
                var oldPath: String? = null
                updateAnime(target.animeId) { anime ->
                    anime.copy(seasons = anime.seasons.map { season ->
                        if (season.id != target.seasonId) season else season.copy(
                            episodes = season.episodes.map { episode ->
                                if (episode.number == target.number) {
                                    oldPath = episode.videoPath
                                    episode.copy(videoPath = newPath)
                                } else episode
                            }
                        )
                    })
                }
                if (oldPath != null && oldPath != newPath) deletePrivateFile(oldPath)
                Toast.makeText(context, "Episode saved inside BANKAI.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Video import failed. Check free storage.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeEpisodeVideo(address: EpisodeAddress) {
        val anime = library.firstOrNull { it.id == address.animeId }
        val path = anime?.seasons?.firstOrNull { it.id == address.seasonId }
            ?.episodes?.firstOrNull { it.number == address.number }?.videoPath
        deletePrivateFile(path)
        updateAnime(address.animeId) { item ->
            item.copy(seasons = item.seasons.map { season ->
                if (season.id == address.seasonId) season.copy(
                    episodes = season.episodes.map { episode ->
                        if (episode.number == address.number) episode.copy(videoPath = null) else episode
                    }
                ) else season
            })
        }
    }

    fun deleteEpisode(address: EpisodeAddress) {
        val anime = library.firstOrNull { it.id == address.animeId }
        val path = anime?.seasons?.firstOrNull { it.id == address.seasonId }
            ?.episodes?.firstOrNull { it.number == address.number }?.videoPath
        deletePrivateFile(path)
        updateAnime(address.animeId) { item ->
            item.copy(seasons = item.seasons.map { season ->
                if (season.id == address.seasonId) season.copy(
                    episodes = season.episodes.filterNot { it.number == address.number }
                ) else season
            })
        }
    }

    fun addVideo(address: EpisodeAddress) {
        pendingVideo = address
        videoPicker.launch(arrayOf("video/*"))
    }

    fun openExternal(path: String) {
        runCatching {
            val file = File(path)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            }
            context.startActivity(Intent.createChooser(intent, "Open episode with"))
        }.onFailure {
            val message = if (it is ActivityNotFoundException) "No compatible video player found." else "Could not open this video."
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val selectedAnime = library.firstOrNull { it.id == selectedAnimeId }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, onPrimary = Ink, background = Ink, onBackground = Paper,
            surface = SurfaceOne, onSurface = Paper, secondary = Mint, outline = Line
        )
    ) {
        Column(Modifier.fillMaxSize().background(Ink).systemBarsPadding()) {
            if (playing != null) {
                VideoPlayerScreen(
                    item = playing!!,
                    onBack = { playing = null },
                    onExternal = { openExternal(playing!!.path) },
                    onCompleted = {
                        val address = playing!!.address
                        updateAnime(address.animeId) { anime ->
                            anime.copy(seasons = anime.seasons.map { season ->
                                if (season.id == address.seasonId) season.copy(
                                    episodes = season.episodes.map { ep ->
                                        if (ep.number == address.number) ep.copy(watched = true) else ep
                                    }
                                ) else season
                            })
                        }
                    }
                )
            } else {
                if (selectedAnime == null) {
                    AppHeader(
                        searchOpen = searchOpen,
                        onSearchToggle = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchText = ""
                        },
                        onAdd = { beginAnimeEdit() }
                    )
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("Search your library", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                            trailingIcon = {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Close, null, tint = Muted)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Violet, unfocusedBorderColor = Line,
                                focusedTextColor = Paper, unfocusedTextColor = Paper
                            )
                        )
                    }
                    val filtered = if (searchText.isBlank()) library else library.filter {
                        it.title.contains(searchText.trim(), ignoreCase = true)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (page) {
                            AppPage.HOME -> HomeScreen(filtered, { selectedAnimeId = it.id }, { beginAnimeEdit() })
                            AppPage.LIBRARY -> LibraryScreen(filtered) { selectedAnimeId = it.id }
                            AppPage.WEEKLY -> WeeklyScreen(filtered.filter { it.weekly }) { selectedAnimeId = it.id }
                        }
                    }
                    BottomNavigation(page = page, onSelect = { page = it })
                } else {
                    AnimeDetailScreen(
                        anime = selectedAnime,
                        onBack = { selectedAnimeId = null },
                        onEdit = { beginAnimeEdit(selectedAnime) },
                        onDelete = { showDeleteAnime = true },
                        onStatus = { status -> updateAnime(selectedAnime.id) { it.copy(status = status) } },
                        onWeekly = { updateAnime(selectedAnime.id) { it.copy(weekly = !it.weekly) } },
                        onAddSeason = { showSeasonEditor = true },
                        onAddEpisode = { seasonId ->
                            updateAnime(selectedAnime.id) { anime ->
                                anime.copy(seasons = anime.seasons.map { season ->
                                    if (season.id == seasonId) {
                                        val nextNumber = (season.episodes.maxOfOrNull { it.number } ?: 0) + 1
                                        season.copy(episodes = season.episodes + EpisodeRecord(nextNumber))
                                    } else season
                                })
                            }
                        },
                        onDeleteSeason = { seasonId ->
                            selectedAnime.seasons.firstOrNull { it.id == seasonId }?.episodes?.forEach { deletePrivateFile(it.videoPath) }
                            updateAnime(selectedAnime.id) { it.copy(seasons = it.seasons.filterNot { s -> s.id == seasonId }) }
                        },
                        onToggleWatched = { seasonId, number ->
                            updateAnime(selectedAnime.id) { anime ->
                                anime.copy(seasons = anime.seasons.map { season ->
                                    if (season.id == seasonId) season.copy(
                                        episodes = season.episodes.map { ep -> if (ep.number == number) ep.copy(watched = !ep.watched) else ep }
                                    ) else season
                                })
                            }
                        },
                        onAddVideo = { seasonId, number -> addVideo(EpisodeAddress(selectedAnime.id, seasonId, number)) },
                        onPlay = { seasonId, episode ->
                            val path = episode.videoPath
                            if (path != null && File(path).exists()) {
                                playing = PlayingEpisode(
                                    selectedAnime.title + " · S" + (selectedAnime.seasons.indexOfFirst { it.id == seasonId } + 1) + " E" + episode.number,
                                    path, EpisodeAddress(selectedAnime.id, seasonId, episode.number)
                                )
                            }
                        },
                        onExternal = { path -> openExternal(path) },
                        onRemoveVideo = { seasonId, number -> removeEpisodeVideo(EpisodeAddress(selectedAnime.id, seasonId, number)) },
                        onDeleteEpisode = { seasonId, number -> deleteEpisode(EpisodeAddress(selectedAnime.id, seasonId, number)) }
                    )
                }
            }
        }

        if (showAnimeEditor) {
            AnimeEditorDialog(
                title = draftTitle,
                posterUrl = draftPosterUrl,
                hasPoster = draftPosterPath != null,
                isEditing = editingAnimeId != null,
                onTitleChange = { draftTitle = it },
                onUrlChange = { draftPosterUrl = it },
                onChoosePoster = { posterPicker.launch(arrayOf("image/*")) },
                onDismiss = { showAnimeEditor = false },
                onSave = {
                    val cleanTitle = draftTitle.trim()
                    if (cleanTitle.isNotEmpty()) {
                        val old = library.firstOrNull { it.id == editingAnimeId }
                        if (old != null) {
                            if (old.posterPath != draftPosterPath) deletePrivateFile(old.posterPath)
                            save(library.map {
                                if (it.id == old.id) it.copy(
                                    title = cleanTitle, posterPath = draftPosterPath,
                                    posterUrl = draftPosterUrl.trim().ifBlank { null }
                                ) else it
                            })
                        } else {
                            val record = AnimeRecord(
                                id = UUID.randomUUID().toString(), title = cleanTitle,
                                posterPath = draftPosterPath, posterUrl = draftPosterUrl.trim().ifBlank { null }
                            )
                            save(library + record)
                            selectedAnimeId = record.id
                        }
                        showAnimeEditor = false
                    }
                }
            )
        }

        if (showSeasonEditor && selectedAnime != null) {
            SeasonEditorDialog(
                onDismiss = { showSeasonEditor = false },
                onSave = { name, count ->
                    val season = SeasonRecord(
                        id = UUID.randomUUID().toString(), name = name,
                        episodes = (1..count).map { EpisodeRecord(it) }
                    )
                    updateAnime(selectedAnime.id) { it.copy(seasons = it.seasons + season) }
                    showSeasonEditor = false
                }
            )
        }

        if (showDeleteAnime && selectedAnime != null) {
            AlertDialog(
                onDismissRequest = { showDeleteAnime = false },
                title = { Text("Delete " + selectedAnime.title + "?", color = Paper) },
                text = { Text("This removes the anime and its imported poster and episode files from BANKAI.", color = Muted) },
                confirmButton = {
                    TextButton(onClick = {
                        selectedAnime.seasons.flatMap { it.episodes }.forEach { deletePrivateFile(it.videoPath) }
                        deletePrivateFile(selectedAnime.posterPath)
                        save(library.filterNot { it.id == selectedAnime.id })
                        selectedAnimeId = null
                        showDeleteAnime = false
                    }) { Text("Delete", color = Color(0xFFFF8D94)) }
                },
                dismissButton = { TextButton(onClick = { showDeleteAnime = false }) { Text("Cancel", color = Muted) } },
                containerColor = SurfaceOne
            )
        }
    }
}

@Composable
private fun AppHeader(searchOpen: Boolean, onSearchToggle: () -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFB7A3FF), VioletSoft))),
            contentAlignment = Alignment.Center
        ) { Text("✿", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("BANKAI", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.1.sp)
            Text("YOUR PRIVATE ANIME LIBRARY", color = Muted, fontSize = 8.sp, letterSpacing = 1.3.sp)
        }
        IconButton(onClick = onSearchToggle) {
            Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search", tint = Paper)
        }
        FilledIconButton(
            onClick = onAdd,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet, contentColor = Ink),
            modifier = Modifier.size(42.dp)
        ) { Icon(Icons.Default.Add, "Add anime") }
    }
}

@Composable
private fun BottomNavigation(page: AppPage, onSelect: (AppPage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceOne).padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem("Home", Icons.Default.Home, page == AppPage.HOME) { onSelect(AppPage.HOME) }
        BottomNavItem("Library", Icons.Default.GridView, page == AppPage.LIBRARY) { onSelect(AppPage.LIBRARY) }
        BottomNavItem("Weekly", Icons.Default.CalendarMonth, page == AppPage.WEEKLY) { onSelect(AppPage.WEEKLY) }
    }
}

@Composable
private fun BottomNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (selected) Violet else Muted, modifier = Modifier.size(21.dp))
        Text(label, color = if (selected) Paper else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun HomeScreen(library: List<AnimeRecord>, onOpen: (AnimeRecord) -> Unit, onAdd: () -> Unit) {
    val weekly = library.filter { it.weekly }
    val recent = library.sortedByDescending { it.createdAt }.take(12)
    val current = library.firstOrNull { it.status == "Watching" }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text("A quieter way to keep up.", color = Paper, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
                Text("Your shows, seasons and episodes — all in one place.", color = Muted, fontSize = 12.sp)
            }
        }
        if (current != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionHeading("PICK UP WHERE YOU LEFT OFF", "Continue")
                    ContinueCard(current) { onOpen(current) }
                }
            }
        } else if (library.isEmpty()) {
            item { EmptyLibraryCard(onAdd) }
        }
        if (weekly.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionHeading("YOUR WEEKLY LINEUP", "See all")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        items(weekly, key = { it.id }) { record -> PosterTile(record, Modifier.width(124.dp)) { onOpen(record) } }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionHeading(if (library.size > 12) "RECENTLY ADDED" else "YOUR COLLECTION", library.size.toString() + " titles")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        items(recent, key = { it.id }) { record -> PosterTile(record, Modifier.width(124.dp)) { onOpen(record) } }
                    }
                }
            }
        }
        if (library.isNotEmpty() && current == null) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceOne)
                        .clickable(onClick = onAdd).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = Violet)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Add another anime", color = Paper, fontWeight = FontWeight.SemiBold)
                        Text("Build your own curated library.", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(SurfaceTwo, SurfaceOne))).padding(23.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("✿", color = Violet, fontSize = 29.sp)
        Text("Make this library yours.", color = Paper, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text("Add the dubbed anime you follow, then organise every season and episode your way.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink), shape = RoundedCornerShape(13.dp)) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add your first anime")
        }
    }
}

@Composable
private fun ContinueCard(anime: AnimeRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(190.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceTwo)
    ) {
        Box(Modifier.fillMaxSize()) {
            PosterImage(anime, Modifier.fillMaxSize(), rounded = false)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEE090A0F), Color(0x99090A0F), Color.Transparent))))
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(0.82f).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("CONTINUE WATCHING", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(anime.title, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(watchedCount(anime).toString() + " episodes watched", color = Muted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(95.dp).height(3.dp).clip(CircleShape).background(Line)) {
                        Box(Modifier.fillMaxWidth(progressOf(anime)).fillMaxHeight().background(Violet))
                    }
                    Spacer(Modifier.width(9.dp))
                    Text((progressOf(anime) * 100).toInt().toString() + "%", color = Paper, fontSize = 10.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.PlayCircle, null, tint = Paper, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(library: List<AnimeRecord>, onOpen: (AnimeRecord) -> Unit) {
    if (library.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Your library is empty. Add an anime to begin.", color = Muted)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 142.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            gridItems(library, key = { it.id }) { anime -> PosterTile(anime, Modifier.fillMaxWidth()) { onOpen(anime) } }
        }
    }
}

@Composable
private fun WeeklyScreen(library: List<AnimeRecord>, onOpen: (AnimeRecord) -> Unit) {
    if (library.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No weekly anime yet. Add one from an anime's details.", color = Muted)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Your weekly rhythm", color = Paper, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("A personal list of the anime you want to follow.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }
            items(library, key = { it.id }) { anime ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(SurfaceOne)
                        .clickable { onOpen(anime) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PosterImage(anime, Modifier.size(width = 58.dp, height = 80.dp), rounded = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(anime.title, color = Paper, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(anime.status, color = Muted, fontSize = 11.sp)
                        Text(anime.seasons.size.toString() + " seasons", color = Violet, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
        Text(trailing, color = Violet, fontSize = 10.sp)
    }
}

@Composable
private fun PosterTile(anime: AnimeRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.70f).clip(RoundedCornerShape(15.dp)).background(SurfaceTwo)) {
            PosterImage(anime, Modifier.fillMaxSize(), rounded = false)
            if (anime.status == "Watching" || anime.status == "Watched") {
                Text(
                    anime.status.uppercase(),
                    Modifier.align(Alignment.BottomStart).padding(7.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC090A0F)).padding(horizontal = 7.dp, vertical = 4.dp),
                    color = if (anime.status == "Watched") Mint else Violet,
                    fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(anime.title, color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (anime.status == "Watching") watchedCount(anime).toString() + " watched" else anime.status, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun PosterImage(anime: AnimeRecord, modifier: Modifier, rounded: Boolean = true) {
    val model: Any? = anime.posterPath?.let { File(it) } ?: anime.posterUrl?.takeIf { it.isNotBlank() }
    Box(
        modifier.clip(if (rounded) RoundedCornerShape(13.dp) else RoundedCornerShape(0.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF302947), Color(0xFF161925), Color(0xFF302439)))),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = anime.title + " poster", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✿", color = Violet, fontSize = 32.sp)
                Text(anime.title.take(1).uppercase(), color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AnimeDetailScreen(
    anime: AnimeRecord,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatus: (String) -> Unit,
    onWeekly: () -> Unit,
    onAddSeason: () -> Unit,
    onAddEpisode: (String) -> Unit,
    onDeleteSeason: (String) -> Unit,
    onToggleWatched: (String, Int) -> Unit,
    onAddVideo: (String, Int) -> Unit,
    onPlay: (String, EpisodeRecord) -> Unit,
    onExternal: (String) -> Unit,
    onRemoveVideo: (String, Int) -> Unit,
    onDeleteEpisode: (String, Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 13.dp, top = 4.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Paper) }
            Text("ANIME DETAILS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "More", tint = Paper) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, modifier = Modifier.background(SurfaceOne)) {
                    DropdownMenuItem(text = { Text("Edit anime") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete anime", color = Color(0xFFFF8D94)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF8D94)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(21.dp)).background(SurfaceTwo)) {
                PosterImage(anime, Modifier.fillMaxSize(), rounded = false)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0090A0F)))))
                Column(Modifier.align(Alignment.BottomStart).padding(17.dp)) {
                    Text(anime.title, color = Paper, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(anime.seasons.size.toString() + " seasons · " + anime.seasons.sumOf { it.episodes.size } + " episodes", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("YOUR STATUS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Plan to watch", "Watching", "Watched").forEach { status ->
                    FilterChip(
                        selected = anime.status == status,
                        onClick = { onStatus(status) },
                        label = { Text(status, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Violet, selectedLabelColor = Ink, labelColor = Muted),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = anime.status == status, borderColor = Line, selectedBorderColor = Violet)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onWeekly, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (anime.weekly) Violet else Paper),
                    border = BorderStroke(1.dp, if (anime.weekly) Violet else Line), modifier = Modifier.weight(1f)
                ) {
                    Icon(if (anime.weekly) Icons.Default.EventAvailable else Icons.Default.CalendarMonth, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (anime.weekly) "In weekly" else "Add to weekly", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { onStatus(if (anime.status == "Plan to watch") "Watching" else "Plan to watch") },
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper), modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watchlist", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(19.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Seasons & episodes", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Add your own season names and episode counts.", color = Muted, fontSize = 10.sp)
                }
                FilledIconButton(onClick = onAddSeason, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet, contentColor = Ink)) {
                    Icon(Icons.Default.Add, "Add season")
                }
            }
            Spacer(Modifier.height(9.dp))
            if (anime.seasons.isEmpty()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(SurfaceOne).padding(17.dp)) {
                    Text("No seasons yet. Add the first season to generate its episode list.", color = Muted, fontSize = 12.sp)
                }
            }
            anime.seasons.forEachIndexed { index, season ->
                SeasonCard(
                    season, season.name.ifBlank { "Season " + (index + 1) },
                    { onAddEpisode(season.id) }, { onDeleteSeason(season.id) },
                    { number -> onToggleWatched(season.id, number) },
                    { number -> onAddVideo(season.id, number) },
                    { ep -> onPlay(season.id, ep) }, onExternal,
                    { number -> onRemoveVideo(season.id, number) },
                    { number -> onDeleteEpisode(season.id, number) }
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SeasonCard(
    season: SeasonRecord,
    displayName: String,
    onAddEpisode: () -> Unit,
    onDeleteSeason: () -> Unit,
    onToggleWatched: (Int) -> Unit,
    onAddVideo: (Int) -> Unit,
    onPlay: (EpisodeRecord) -> Unit,
    onExternal: (String) -> Unit,
    onRemoveVideo: (Int) -> Unit,
    onDeleteEpisode: (Int) -> Unit
) {
    var expanded by remember(season.id) { mutableStateOf(true) }
    var seasonMenu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = SurfaceOne)) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(displayName, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(season.episodes.size.toString() + " episodes · " + season.episodes.count { it.watched } + " watched", color = Muted, fontSize = 10.sp)
                }
                IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Muted) }
                Box {
                    IconButton(onClick = { seasonMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = Muted) }
                    DropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                        DropdownMenuItem(text = { Text("Add one episode") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { seasonMenu = false; onAddEpisode() })
                        DropdownMenuItem(text = { Text("Delete season", color = Color(0xFFFF8D94)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF8D94)) }, onClick = { seasonMenu = false; onDeleteSeason() })
                    }
                }
            }
            if (expanded) {
                HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 7.dp))
                season.episodes.sortedBy { it.number }.forEach { episode ->
                    EpisodeRow(
                        episode,
                        { onToggleWatched(episode.number) },
                        { onAddVideo(episode.number) },
                        { onPlay(episode) },
                        onExternal,
                        { onRemoveVideo(episode.number) },
                        { onDeleteEpisode(episode.number) }
                    )
                }
                TextButton(onClick = onAddEpisode, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Default.Add, null, tint = Violet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add episode", color = Violet, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeRecord,
    onToggle: () -> Unit,
    onAddVideo: () -> Unit,
    onPlay: () -> Unit,
    onExternal: (String) -> Unit,
    onRemoveVideo: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(episode.number) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
            Icon(if (episode.watched) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                if (episode.watched) "Watched" else "Not watched",
                tint = if (episode.watched) Mint else Muted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 5.dp)) {
            Text("Episode " + episode.number.toString().padStart(2, '0'), color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(if (episode.videoPath != null) "Video saved offline" else if (episode.watched) "Watched" else "No video added", color = Muted, fontSize = 9.sp)
        }
        if (episode.videoPath != null && File(episode.videoPath).exists()) {
            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlayCircle, "Play in BANKAI", tint = Violet, modifier = Modifier.size(23.dp))
            }
        } else {
            IconButton(onClick = onAddVideo, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.VideoLibrary, "Import video", tint = Muted, modifier = Modifier.size(21.dp))
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.MoreHoriz, "Episode options", tint = Muted, modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (episode.videoPath != null && File(episode.videoPath).exists()) {
                    DropdownMenuItem(text = { Text("Open with VLC / MX Player") }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) }, onClick = { menuExpanded = false; onExternal(episode.videoPath) })
                    DropdownMenuItem(text = { Text("Remove video") }, leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) }, onClick = { menuExpanded = false; onRemoveVideo() })
                } else {
                    DropdownMenuItem(text = { Text("Import video") }, leadingIcon = { Icon(Icons.Default.VideoLibrary, null) }, onClick = { menuExpanded = false; onAddVideo() })
                }
                DropdownMenuItem(
                    text = { Text("Delete episode", color = Color(0xFFFF8D94)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF8D94)) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerScreen(item: PlayingEpisode, onBack: () -> Unit, onExternal: () -> Unit, onCompleted: () -> Unit) {
    val context = LocalContext.current
    val latestComplete by rememberUpdatedState(onCompleted)
    val player = remember(item.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(item.path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) latestComplete()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Paper) }
            Column(Modifier.weight(1f)) {
                Text("NOW PLAYING", color = Violet, fontSize = 8.sp, letterSpacing = 1.4.sp)
                Text(item.title, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onExternal) { Icon(Icons.Default.OpenInNew, "Open externally", tint = Paper) }
        }
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
        )
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Stored on this device", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("Your imported video is played from BANKAI's private storage. It remains available if the original Gallery copy is removed.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            OutlinedButton(onClick = onExternal, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper)) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Open with another video player")
            }
        }
    }
}

@Composable
private fun AnimeEditorDialog(
    title: String, posterUrl: String, hasPoster: Boolean, isEditing: Boolean,
    onTitleChange: (String) -> Unit, onUrlChange: (String) -> Unit,
    onChoosePoster: () -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit anime" else "Add anime", color = Paper) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Anime title") },
                    singleLine = true, colors = dialogFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = posterUrl, onValueChange = onUrlChange, label = { Text("Poster image URL (optional)") },
                    singleLine = true, colors = dialogFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onChoosePoster, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper)) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (hasPoster) "Poster imported" else "Choose poster from device")
                }
                Text("Poster images and all library details stay on this device.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = title.isNotBlank()) { Text("Save", color = Violet) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
        containerColor = SurfaceOne, titleContentColor = Paper, textContentColor = Muted
    )
}

@Composable
private fun SeasonEditorDialog(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("12") }
    val count = countText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create season", color = Paper) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Season name (e.g. Season 1)") },
                    singleLine = true, colors = dialogFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = countText, onValueChange = { countText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Number of episodes") }, singleLine = true, colors = dialogFieldColors(), modifier = Modifier.fillMaxWidth())
                Text("BANKAI will create one episode entry for each number.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && count != null && count in 1..500, onClick = { onSave(name.trim(), count ?: 1) }) {
                Text("Create", color = Violet)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
        containerColor = SurfaceOne, titleContentColor = Paper, textContentColor = Muted
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Violet, unfocusedBorderColor = Line,
    focusedTextColor = Paper, unfocusedTextColor = Paper,
    focusedLabelColor = Violet, unfocusedLabelColor = Muted, cursorColor = Violet
)

private fun watchedCount(anime: AnimeRecord): Int = anime.seasons.sumOf { season -> season.episodes.count { it.watched } }

private fun progressOf(anime: AnimeRecord): Float {
    val episodes = anime.seasons.flatMap { it.episodes }
    if (episodes.isEmpty()) return 0f
    return episodes.count { it.watched }.toFloat() / episodes.size.toFloat()
}
