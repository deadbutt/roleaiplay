package com.example.iot.model

data class Character(
    val id: String,
    val name: String,
    val description: String,
    val scenario: String,
    val avatarUrl: String,
    val createdAt: String
)

data class CharacterCreateRequest(
    val name: String,
    val description: String,
    val scenario: String,
    val avatarUrl: String
)

data class CharacterUpdateRequest(
    val name: String,
    val description: String,
    val scenario: String,
    val avatarUrl: String
)
