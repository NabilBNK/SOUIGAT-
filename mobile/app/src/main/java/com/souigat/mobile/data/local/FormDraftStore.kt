package com.souigat.mobile.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class TicketFormDraft(
    val selectedTab: Int = 0,
    val passengerCount: Int = 1,
    val passengerPriceInput: String = "",
    val seatNumber: String = "",
    val boardingPoint: String = "",
    val alightingPoint: String = "",
    val passengerPaymentSource: String = "cash",
    val senderName: String = "",
    val senderPhone: String = "",
    val receiverName: String = "",
    val receiverPhone: String = "",
    val cargoDescription: String = "",
    val cargoTier: String = "",
    val cargoPaymentSource: String = "prepaid"
)

data class ExpenseFormDraft(
    val amount: String = "",
    val description: String = "",
    val category: String = "fuel",
    val receiptCaptured: Boolean = false
)

@Singleton
class FormDraftStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTicketDraft(tripId: Long): TicketFormDraft {
        val prefix = "ticket.$tripId"
        return TicketFormDraft(
            selectedTab = prefs.getInt("$prefix.selectedTab", 0),
            passengerCount = prefs.getInt("$prefix.passengerCount", 1),
            passengerPriceInput = prefs.getString("$prefix.passengerPriceInput", "").orEmpty(),
            seatNumber = prefs.getString("$prefix.seatNumber", "").orEmpty(),
            boardingPoint = prefs.getString("$prefix.boardingPoint", "").orEmpty(),
            alightingPoint = prefs.getString("$prefix.alightingPoint", "").orEmpty(),
            passengerPaymentSource = prefs.getString("$prefix.passengerPaymentSource", "cash").orEmpty(),
            senderName = prefs.getString("$prefix.senderName", "").orEmpty(),
            senderPhone = prefs.getString("$prefix.senderPhone", "").orEmpty(),
            receiverName = prefs.getString("$prefix.receiverName", "").orEmpty(),
            receiverPhone = prefs.getString("$prefix.receiverPhone", "").orEmpty(),
            cargoDescription = prefs.getString("$prefix.cargoDescription", "").orEmpty(),
            cargoTier = prefs.getString("$prefix.cargoTier", "").orEmpty(),
            cargoPaymentSource = prefs.getString("$prefix.cargoPaymentSource", "prepaid").orEmpty()
        )
    }

    fun saveTicketDraft(tripId: Long, draft: TicketFormDraft) {
        val prefix = "ticket.$tripId"
        prefs.edit()
            .putInt("$prefix.selectedTab", draft.selectedTab)
            .putInt("$prefix.passengerCount", draft.passengerCount)
            .putString("$prefix.passengerPriceInput", draft.passengerPriceInput)
            .putString("$prefix.seatNumber", draft.seatNumber)
            .putString("$prefix.boardingPoint", draft.boardingPoint)
            .putString("$prefix.alightingPoint", draft.alightingPoint)
            .putString("$prefix.passengerPaymentSource", draft.passengerPaymentSource)
            .putString("$prefix.senderName", draft.senderName)
            .putString("$prefix.senderPhone", draft.senderPhone)
            .putString("$prefix.receiverName", draft.receiverName)
            .putString("$prefix.receiverPhone", draft.receiverPhone)
            .putString("$prefix.cargoDescription", draft.cargoDescription)
            .putString("$prefix.cargoTier", draft.cargoTier)
            .putString("$prefix.cargoPaymentSource", draft.cargoPaymentSource)
            .apply()
    }

    fun clearTicketDraft(tripId: Long) {
        val prefix = "ticket.$tripId"
        prefs.edit()
            .remove("$prefix.selectedTab")
            .remove("$prefix.passengerCount")
            .remove("$prefix.passengerPriceInput")
            .remove("$prefix.seatNumber")
            .remove("$prefix.boardingPoint")
            .remove("$prefix.alightingPoint")
            .remove("$prefix.passengerPaymentSource")
            .remove("$prefix.senderName")
            .remove("$prefix.senderPhone")
            .remove("$prefix.receiverName")
            .remove("$prefix.receiverPhone")
            .remove("$prefix.cargoDescription")
            .remove("$prefix.cargoTier")
            .remove("$prefix.cargoPaymentSource")
            .apply()
    }

    fun getExpenseDraft(tripId: Long): ExpenseFormDraft {
        val prefix = "expense.$tripId"
        return ExpenseFormDraft(
            amount = prefs.getString("$prefix.amount", "").orEmpty(),
            description = prefs.getString("$prefix.description", "").orEmpty(),
            category = prefs.getString("$prefix.category", "fuel").orEmpty(),
            receiptCaptured = prefs.getBoolean("$prefix.receiptCaptured", false)
        )
    }

    fun saveExpenseDraft(tripId: Long, draft: ExpenseFormDraft) {
        val prefix = "expense.$tripId"
        prefs.edit()
            .putString("$prefix.amount", draft.amount)
            .putString("$prefix.description", draft.description)
            .putString("$prefix.category", draft.category)
            .putBoolean("$prefix.receiptCaptured", draft.receiptCaptured)
            .apply()
    }

    fun clearExpenseDraft(tripId: Long) {
        val prefix = "expense.$tripId"
        prefs.edit()
            .remove("$prefix.amount")
            .remove("$prefix.description")
            .remove("$prefix.category")
            .remove("$prefix.receiptCaptured")
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "souigat_form_drafts"
    }
}
