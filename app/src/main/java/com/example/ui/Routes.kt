package com.example.ui

import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
data class PresenterRoute(val deckId: String)
