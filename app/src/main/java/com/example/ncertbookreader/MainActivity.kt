package com.example.ncertbookreader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ncertbookreader.ui.theme.NcertBookReaderTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

// --- Data Models ---
@Serializable
data class Subject(val name: String, val gradeLevels: List<GradeLevel>)

@Serializable
data class GradeLevel(val name: String, val chapters: List<Chapter>)

@Serializable
data class Chapter(
    val name: String,
    val pdfPath: String,
    val oldPdfPath: String? = null,
    val audioPath: String? = null
)

/**
 * Loads and parses the subject data from the "data.json" file in the assets folder.
 */
fun loadSubjects(context: Context): List<Subject> {
    return try {
        val jsonString = context.assets.open("data.json").bufferedReader().use { it.readText() }
        if (jsonString.isBlank()) {
            Log.e("NcertApp", "data.json is empty.")
            Toast.makeText(context, "Error: App data file is empty.", Toast.LENGTH_LONG).show()
            return emptyList()
        }
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        json.decodeFromString(ListSerializer(Subject.serializer()), jsonString)
    } catch (e: IOException) {
        Log.e("NcertApp", "Failed to read data.json from assets.", e)
        Toast.makeText(context, "Error: Could not read app data file.", Toast.LENGTH_LONG).show()
        emptyList()
    } catch (e: Exception) {
        Log.e("NcertApp", "Failed to parse data.json.", e)
        Toast.makeText(context, "Error: Invalid format in app data file.", Toast.LENGTH_LONG).show()
        emptyList()
    }
}

/**
 * Securely opens a PDF file from the app's assets.
 */
fun openPdf(context: Context, pdfPath: String, chapterName: String) {
    val assetPath = pdfPath.removePrefix("/")
    val fileName = assetPath.substringAfterLast('/')

    if (fileName.isBlank()) {
        Toast.makeText(context, "Could not determine file name from path: $assetPath", Toast.LENGTH_LONG).show()
        return
    }
    val destinationFile = File(context.cacheDir, fileName)

    try {
        if (!destinationFile.exists() || destinationFile.length() == 0L) {
            Log.d("NcertApp", "Copying asset '$assetPath' to '${destinationFile.absolutePath}'")
            context.assets.open(assetPath).use { inputStream ->
                destinationFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        if (!destinationFile.exists() || destinationFile.length() == 0L) {
            throw IOException("Failed to copy file to cache. File is missing or empty.")
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            destinationFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Log.d("NcertApp", "Attempting to launch PDF viewer for URI: $uri")
        context.startActivity(intent)

    } catch (e: FileNotFoundException) {
        Log.e("NcertApp", "Asset not found at path: '$assetPath'. Check your data.json and assets folder.", e)
        Toast.makeText(context, "PDF file not found in the app.", Toast.LENGTH_LONG).show()
    } catch (e: ActivityNotFoundException) {
        Log.e("NcertApp", "ActivityNotFoundException: No PDF reader is installed or configured.", e)
        Toast.makeText(context, "No PDF reader app is installed.", Toast.LENGTH_LONG).show()
    } catch (e: IOException) {
        Log.e("NcertApp", "IOException while copying file '$assetPath'.", e)
        Toast.makeText(context, "Error: Could not read or save PDF file.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("NcertApp", "An unexpected error occurred for path '$assetPath'.", e)
        Toast.makeText(context, "An unexpected error occurred.", Toast.LENGTH_LONG).show()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NcertBookReaderTheme {
                val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
                val isDarkTheme = isSystemInDarkTheme()

                // This SideEffect will run after composition to update the system UI
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        // Set the status bar color to match the TopAppBar
                        window.statusBarColor = primaryContainerColor.toArgb()
                        // Set the status bar icons to be visible against the new background color
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // The main entry point for the app's UI
                    NcertApp()
                }
            }
        }
    }
}

/**
 * The main composable that sets up the app's navigation and structure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcertApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Load subjects once and remember the result
    val subjects by remember { mutableStateOf(loadSubjects(context)) }

    // Get current back stack entry to determine the current screen
    val backStackEntry by navController.currentBackStackEntryAsState()

    // Determine the title based on the current route
    val currentSubjectName = backStackEntry?.arguments?.getString("subjectName")
    val currentGradeLevelName = backStackEntry?.arguments?.getString("gradeLevelName")

    val title = when {
        currentGradeLevelName != null -> currentGradeLevelName
        currentSubjectName != null -> currentSubjectName
        else -> "NCERT Books"
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                canNavigateBack = navController.previousBackStackEntry != null,
                onNavigateUp = { navController.navigateUp() }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "subjects",
            modifier = Modifier.padding(innerPadding)
        ) {
            // Route for the main subjects screen
            composable("subjects") {
                SubjectScreen(
                    subjects = subjects,
                    onSubjectClick = { subjectName ->
                        navController.navigate("subject/$subjectName")
                    }
                )
            }
            // Route for the grade levels of a specific subject
            composable(
                route = "subject/{subjectName}",
                arguments = listOf(navArgument("subjectName") { type = NavType.StringType })
            ) { navBackStackEntry ->
                val subjectName = navBackStackEntry.arguments?.getString("subjectName")
                val subject = subjects.find { it.name == subjectName }
                if (subject != null) {
                    GradeLevelScreen(
                        subject = subject,
                        onGradeClick = { gradeLevelName ->
                            navController.navigate("subject/$subjectName/$gradeLevelName")
                        }
                    )
                } else {
                    FullScreenMessage("Subject not found.")
                }
            }
            // Route for the chapters of a specific grade level
            composable(
                route = "subject/{subjectName}/{gradeLevelName}",
                arguments = listOf(
                    navArgument("subjectName") { type = NavType.StringType },
                    navArgument("gradeLevelName") { type = NavType.StringType }
                )
            ) { navBackStackEntry ->
                val subjectName = navBackStackEntry.arguments?.getString("subjectName")
                val gradeLevelName = navBackStackEntry.arguments?.getString("gradeLevelName")
                val gradeLevel = subjects.find { it.name == subjectName }
                    ?.gradeLevels?.find { it.name == gradeLevelName }

                if (gradeLevel != null) {
                    ChapterScreen(
                        gradeLevel = gradeLevel,
                        onOpenNewNcert = { chapter ->
                            openPdf(context, chapter.pdfPath, chapter.name)
                        },
                        onOpenOldNcert = { chapter ->
                            chapter.oldPdfPath?.let { openPdf(context, it, chapter.name) }
                        },
                        onPlayAudio = { chapter ->
                            Toast.makeText(context, "Audio playback not implemented.", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    FullScreenMessage("Chapter list not found.")
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, canNavigateBack: Boolean, onNavigateUp: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(subjects: List<Subject>, onSubjectClick: (String) -> Unit) {
    if (subjects.isEmpty()) {
        FullScreenMessage("No subjects available")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(subjects) { subject ->
                ElevatedCard(
                    onClick = { onSubjectClick(subject.name) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Go to ${subject.name}"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeLevelScreen(subject: Subject, onGradeClick: (String) -> Unit) {
    if (subject.gradeLevels.isEmpty()) {
        FullScreenMessage("No grade levels available for ${subject.name}")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(subject.gradeLevels) { gradeLevel ->
                ElevatedCard(
                    onClick = { onGradeClick(gradeLevel.name) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = gradeLevel.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Go to ${gradeLevel.name}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterScreen(
    gradeLevel: GradeLevel,
    onOpenNewNcert: (Chapter) -> Unit,
    onOpenOldNcert: (Chapter) -> Unit,
    onPlayAudio: (Chapter) -> Unit
) {
    if (gradeLevel.chapters.isEmpty()) {
        FullScreenMessage("No chapters available for ${gradeLevel.name}")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(gradeLevel.chapters) { chapter ->
                ChapterItem(
                    chapter = chapter,
                    onOpenNewNcert = onOpenNewNcert,
                    onOpenOldNcert = onOpenOldNcert,
                    onPlayAudio = onPlayAudio
                )
            }
        }
    }
}

@Composable
fun ChapterItem(
    chapter: Chapter,
    onOpenNewNcert: (Chapter) -> Unit,
    onOpenOldNcert: (Chapter) -> Unit,
    onPlayAudio: (Chapter) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = chapter.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { onOpenNewNcert(chapter) }) {
                    Text("New NCERT")
                }
                chapter.oldPdfPath?.let {
                    OutlinedButton(onClick = { onOpenOldNcert(chapter) }) {
                        Text("Old NCERT")
                    }
                }
                chapter.audioPath?.let {
                    OutlinedButton(onClick = { onPlayAudio(chapter) }) {
                        Text("Play Audio")
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}
