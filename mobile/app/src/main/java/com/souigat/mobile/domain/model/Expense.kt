package com.souigat.mobile.domain.model

data class Expense(
    val id: String,
    val tripId: Long,
    val amount: Long,
    val currency: String,
    val category: String,
    val description: String,
    val createdAt: Long,
    val status: String
)
