package com.example

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.local.PresentationRepository
import com.example.display.ExternalDisplayController
import com.example.pdf.PdfSlideEngine
import com.example.pdf.SampleBeamerGenerator

class BeamerAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val presentationRepository: PresentationRepository by lazy {
        PresentationRepository(database.presentationDao())
    }
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(appContext)
    }
    val pdfSlideEngine: PdfSlideEngine by lazy {
        PdfSlideEngine(appContext)
    }
    val externalDisplayController: ExternalDisplayController by lazy {
        ExternalDisplayController(appContext)
    }
    val sampleBeamerGenerator: SampleBeamerGenerator by lazy {
        SampleBeamerGenerator(appContext, presentationRepository)
    }
}
