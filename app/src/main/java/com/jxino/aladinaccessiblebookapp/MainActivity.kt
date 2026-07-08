package com.jxino.aladinaccessiblebookapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jxino.aladinaccessiblebookapp.data.AladinRepository
import com.jxino.aladinaccessiblebookapp.domain.BasicResultAnnouncer
import com.jxino.aladinaccessiblebookapp.domain.RuleBasedUserUtteranceParser
import com.jxino.aladinaccessiblebookapp.speech.SpeechRecognizerManager
import com.jxino.aladinaccessiblebookapp.speech.TtsManager
import com.jxino.aladinaccessiblebookapp.ui.AladinAccessibleBookApp
import com.jxino.aladinaccessiblebookapp.ui.AppScreen
import com.jxino.aladinaccessiblebookapp.ui.BookSearchViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<com.jxino.aladinaccessiblebookapp.ui.BookSearchViewModel> {
        BookSearchViewModelFactory(
            repository = AladinRepository(BuildConfig.ALADIN_TTB_KEY),
            parser = RuleBasedUserUtteranceParser(),
            announcer = BasicResultAnnouncer(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val screen by viewModel.screen.collectAsStateWithLifecycle()
            var hasAudioPermission by remember {
                mutableStateOf(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                hasAudioPermission = granted
                if (!granted) viewModel.onPermissionDenied()
            }
            val ttsManager = remember { TtsManager(this) }
            val speechManager = remember {
                SpeechRecognizerManager(
                    context = this,
                    onResult = viewModel::onSpeechText,
                    onError = viewModel::onSpeechError,
                )
            }

            LaunchedEffect(Unit) {
                viewModel.ttsEvents.collect { ttsManager.speak(it) }
            }

            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    speechManager.destroy()
                    ttsManager.shutdown()
                }
            }

            AladinAccessibleBookApp(
                uiState = uiState,
                screen = screen,
                hasAudioPermission = hasAudioPermission,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onStartListening = {
                    if (hasAudioPermission) {
                        viewModel.onListeningStarted()
                        speechManager.startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopListening = speechManager::stopListening,
                onResultClicked = viewModel::onResultClicked,
                onBackToSearch = viewModel::onBackToSearch,
                onWebViewLoadingChanged = viewModel::onWebViewLoadingChanged,
            )
        }
    }
}
