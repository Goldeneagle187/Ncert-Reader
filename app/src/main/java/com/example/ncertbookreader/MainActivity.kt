package com.example.ncertbookreader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class Subject(
    val name: String,
    val gradeLevels: List<GradeLevel>
)

@Serializable
data class GradeLevel(
    val name: String,
    val chapters: List<Chapter>
)

@Serializable
data class Chapter(
    val name: String,
    val pdfPath: String,
    val audioPath: String
)

@Composable
fun formatTimeMillis(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NcertApp()
            }
        }
    }

    private fun viewPdfFromAssets(context: Context, pdfPath: String) {
        val assetManager = context.assets
        try {
            val inputStream = assetManager.open(pdfPath)
            val file = File(context.cacheDir, pdfPath.substringAfterLast('/'))
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            context.startActivity(intent)
        } catch (e: IOException) {
            Toast.makeText(context, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer app found.", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    @Composable
    fun NcertApp() {
        val navController = rememberNavController()
        val context = LocalContext.current
        val subjects = loadSubjectsFromAssets(context)

        // 1. Get an instance of the PlaybackViewModel
        val playbackViewModel: PlaybackViewModel = viewModel()
        val playbackUiState by playbackViewModel.uiState.collectAsState()

        // 2. Initialize the MediaBrowser when the app starts
        LaunchedEffect(Unit) {
            playbackViewModel.initializeBrowser(context)
        }

        // Display any errors from the ViewModel
        playbackUiState.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }

        Scaffold(
            topBar = {
                AppTopBar(navController = navController)
            }
        ) { innerPadding ->
            if (subjects == null) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("Error: Could not load data.json or data is invalid.", style = MaterialTheme.typography.bodyLarge)
                }
                return@Scaffold
            }
            // ... (rest of the NavHost setup is the same)

            NavHost(
                navController = navController,
                startDestination = "subjects",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("subjects") {
                    SubjectScreen(subjects = subjects, onSubjectClick = { subjectName ->
                        navController.navigate("gradeLevels/$subjectName")
                    })
                }
                composable(
                    "gradeLevels/{subjectName}",
                    arguments = listOf(navArgument("subjectName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val subjectName = backStackEntry.arguments?.getString("subjectName")
                    val subject = subjects.find { it.name == subjectName }
                    subject?.let {
                        GradeLevelScreen(
                            subject = it,
                            onGradeLevelClick = { gradeLevelName ->
                                navController.navigate("chapters/${it.name}/$gradeLevelName")
                            }
                        )
                    } ?: Text("Subject not found")
                }
                composable(
                    "chapters/{subjectName}/{gradeLevelName}",
                    arguments = listOf(
                        navArgument("subjectName") { type = NavType.StringType },
                        navArgument("gradeLevelName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val subjectName = backStackEntry.arguments?.getString("subjectName")
                    val gradeLevelName = backStackEntry.arguments?.getString("gradeLevelName")
                    val subject = subjects.find { it.name == subjectName }
                    val gradeLevel = subject?.gradeLevels?.find { it.name == gradeLevelName }
                    gradeLevel?.let {
                        ChapterScreen(
                            gradeLevel = it,
                            onViewPdf = { pdfPath -> viewPdfFromAssets(context, pdfPath) },
                            // Pass the state from the ViewModel
                            playbackState = playbackUiState,
                            // Pass the control methods from the ViewModel
                            onPlayPauseAudio = { chapter ->
                                val isCurrentlyPlayingThis = playbackUiState.isPlaying && playbackUiState.currentAudioPath == chapter.audioPath
                                if (isCurrentlyPlayingThis) {
                                    playbackViewModel.pauseAudio()
                                } else {
                                    // Check if it's the same track but paused
                                    if (playbackUiState.currentAudioPath == chapter.audioPath) {
                                        playbackViewModel.resumeAudio()
                                    } else {
                                        // It's a new track
                                        playbackViewModel.playAudio(context, chapter.audioPath, chapter.name)
                                    }
                                }
                            },
                            onSeekTo = { position ->
                                playbackViewModel.seekAudio(position)
                            }
                        )
                    } ?: Text("Chapter list not found")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppTopBar(navController: NavHostController) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val title = when {
            currentRoute == "subjects" -> "Select Subject"
            currentRoute?.startsWith("gradeLevels/") == true -> {
                navBackStackEntry?.arguments?.getString("subjectName") ?: "Select Class"
            }
            currentRoute?.startsWith("chapters/") == true -> {
                navBackStackEntry?.arguments?.getString("gradeLevelName") ?: "Select Chapter"
            }
            else -> "Ncert Book Reader"
        }

        TopAppBar(
            title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (navController.previousBackStackEntry != null) {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        )
    }

    fun loadSubjectsFromAssets(context: Context): List<Subject>? {
        return try {
            context.assets.open("data.json").bufferedReader().use {
                Json { ignoreUnknownKeys = true }.decodeFromString<List<Subject>>(it.readText())
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: kotlinx.serialization.SerializationException) {
            e.printStackTrace()
            null
        }
    }

    // All globalMediaPlayer logic and lifecycle overrides (onStop, onDestroy) are removed.
    // The ViewModel lifecycle is automatically handled by the 'viewModel()' delegate.

    // ... (SubjectScreen and GradeLevelScreen are unchanged) ...
    @Composable
    fun SubjectScreen(subjects: List<Subject>, onSubjectClick: (String) -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(subjects) { subject ->
                SubjectItem(subject = subject, onClick = { onSubjectClick(subject.name) })
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SubjectItem(subject: Subject, onClick: () -> Unit) {
        ElevatedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Go to ${subject.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @Composable
    fun GradeLevelScreen(subject: Subject, onGradeLevelClick: (String) -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(subject.gradeLevels) { gradeLevel ->
                GradeLevelItem(gradeLevel = gradeLevel, onClick = { onGradeLevelClick(gradeLevel.name) })
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GradeLevelItem(gradeLevel: GradeLevel, onClick: () -> Unit) {
        ElevatedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = gradeLevel.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Go to ${gradeLevel.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @Composable
    fun ChapterScreen(
        gradeLevel: GradeLevel,
        onViewPdf: (String) -> Unit,
        playbackState: PlaybackUiState, // Use the ViewModel's state
        onPlayPauseAudio: (Chapter) -> Unit,
        onSeekTo: (Long) -> Unit
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(gradeLevel.chapters) { chapter ->
                ChapterItem(
                    chapter = chapter,
                    onViewPdf = onViewPdf,
                    isPlayingThisChapter = playbackState.isPlaying && playbackState.currentAudioPath == chapter.audioPath,
                    onPlayPauseClick = { onPlayPauseAudio(chapter) },
                    currentPosition = if (playbackState.currentAudioPath == chapter.audioPath) playbackState.currentPositionMs else 0L,
                    totalDuration = if (playbackState.currentAudioPath == chapter.audioPath) playbackState.totalDurationMs else 0L,
                    isBuffering = playbackState.isBuffering && playbackState.currentAudioPath == chapter.audioPath,
                    onSeekTo = onSeekTo
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChapterItem(
        chapter: Chapter,
        onViewPdf: (String) -> Unit,
        isPlayingThisChapter: Boolean,
        onPlayPauseClick: () -> Unit,
        currentPosition: Long,
        totalDuration: Long,
        isBuffering: Boolean,
        onSeekTo: (Long) -> Unit
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = chapter.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onViewPdf(chapter.pdfPath) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = "Read PDF", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Read PDF")
                    }
                    Button(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.weight(1f),
                        enabled = !isBuffering // Disable button while buffering
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlayingThisChapter) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlayingThisChapter) "Pause Audio" else "Play Audio",
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        }
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (isPlayingThisChapter) "Pause" else "Play")
                    }
                }
                if (totalDuration > 0) {
                    // State to hold slider position during user interaction (seeking)
                    var sliderPosition by remember(currentPosition, isPlayingThisChapter) { mutableStateOf(currentPosition.toFloat()) }
                    var isSeeking by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Slider(
                            value = if (isSeeking) sliderPosition else currentPosition.toFloat(),
                            onValueChange = { newPosition ->
                                isSeeking = true
                                sliderPosition = newPosition
                            },
                            onValueChangeFinished = {
                                onSeekTo(sliderPosition.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..totalDuration.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Update time label instantly during seek
                            val displayPosition = if (isSeeking) sliderPosition.toLong() else currentPosition
                            Text(text = formatTimeMillis(displayPosition), style = MaterialTheme.typography.bodySmall)
                            Text(text = formatTimeMillis(totalDuration), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}