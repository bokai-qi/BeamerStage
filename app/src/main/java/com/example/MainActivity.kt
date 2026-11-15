package com.example

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.LibraryRoute
import com.example.ui.PresenterRoute
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.presenter.PresenterScreen
import com.example.ui.presenter.PresenterViewModel
import com.example.ui.theme.BeamerStageTheme

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: BeamerAppContainer
    private var activePresenterViewModel: PresenterViewModel? = null
    private var volumeKeysEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = (application as BeamerApplication).container

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val prefs by appContainer.preferencesRepository.userPreferences
                .collectAsStateWithLifecycle(initialValue = com.example.model.UserPreferences())
            volumeKeysEnabled = prefs.volumeKeysNavigate

            BeamerStageTheme(darkTheme = true) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute
                ) {
                    composable<LibraryRoute> {
                        val libraryVm: LibraryViewModel = viewModel(
                            factory = LibraryViewModel.Factory(applicationContext, appContainer)
                        )
                        LibraryScreen(
                            viewModel = libraryVm,
                            onOpenDeck = { deckId ->
                                navController.navigate(PresenterRoute(deckId = deckId))
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
        if (presenterVm != null && volumeKeysEnabled) {
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
        return super.dispatchKeyEvent(event)
    }
}
