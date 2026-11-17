package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.model.AppThemeMode
import com.example.model.DarkThemeStyle
import com.example.ui.LibraryRoute
import com.example.ui.PresenterRoute
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.presenter.PresenterScreen
import com.example.ui.presenter.PresenterViewModel
import com.example.ui.theme.BeamerStageTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: BeamerAppContainer
    private var activePresenterViewModel: PresenterViewModel? = null
    private var volumeKeysEnabled: Boolean = true
    private val pendingIncomingPdfUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = (application as BeamerApplication).container

        if (savedInstanceState == null) {
            extractIncomingPdfUri(intent)?.let { uri ->
                pendingIncomingPdfUri.value = uri
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val prefs by appContainer.preferencesRepository.userPreferences
                .collectAsStateWithLifecycle(initialValue = com.example.model.UserPreferences())
            val incomingUri by pendingIncomingPdfUri.collectAsStateWithLifecycle()
            volumeKeysEnabled = prefs.volumeKeysNavigate

            val darkTheme = when (prefs.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            BeamerStageTheme(
                darkTheme = darkTheme,
                oledBlack = prefs.darkThemeStyle == DarkThemeStyle.OLED
            ) {
                val navController = rememberNavController()
                val rootLibraryVm: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.Factory(applicationContext, appContainer)
                )

                LaunchedEffect(incomingUri) {
                    val uri = incomingUri ?: return@LaunchedEffect
                    pendingIncomingPdfUri.value = null
                    rootLibraryVm.importPdfUri(uri) { deckId ->
                        navController.navigate(PresenterRoute(deckId = deckId)) {
                            launchSingleTop = true
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute
                ) {
                    composable<LibraryRoute> {
                        LibraryScreen(
                            viewModel = rootLibraryVm,
                            onOpenDeck = { deckId ->
                                navController.navigate(PresenterRoute(deckId = deckId)) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable<PresenterRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<PresenterRoute>()
                        val presenterVm: PresenterViewModel = viewModel(
                            key = "presenter_${route.deckId}",
                            factory = PresenterViewModel.Factory(route.deckId, appContainer)
                        )

                        DisposableEffect(presenterVm) {
                            activePresenterViewModel = presenterVm
                            onDispose {
                                if (activePresenterViewModel === presenterVm) {
                                    activePresenterViewModel = null
                                }
                            }
                        }

                        PresenterScreen(
                            viewModel = presenterVm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingPdfUri(intent)?.let { uri ->
            pendingIncomingPdfUri.value = uri
        }
    }

    private fun extractIncomingPdfUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.externalDisplayController.attachActivity(this)
    }

    override fun onStop() {
        super.onStop()
        appContainer.externalDisplayController.detachActivity(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val presenterVm = activePresenterViewModel
        if (presenterVm != null) {
            if (volumeKeysEnabled) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            presenterVm.nextSlide()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            presenterVm.previousSlide()
                        }
                        return true
                    }
                }
            }

            if (!presenterVm.isModalTextInputActive) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_PAGE_DOWN,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            presenterVm.nextSlide()
                        }
                        return true
                    }

                    KeyEvent.KEYCODE_PAGE_UP,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            presenterVm.previousSlide()
                        }
                        return true
                    }

                    KeyEvent.KEYCODE_B,
                    KeyEvent.KEYCODE_PERIOD -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            presenterVm.toggleBlackout()
                        }
                        return true
                    }

                    KeyEvent.KEYCODE_F -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            presenterVm.toggleFreeze()
                        }
                        return true
                    }

                    KeyEvent.KEYCODE_W -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            presenterVm.toggleChalkboard()
                        }
                        return true
                    }

                    KeyEvent.KEYCODE_ESCAPE -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            if (presenterVm.handleEscapeAction()) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
