package com.jp.otherworld.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "You have a phone call": set when the phone app posts a missed-call notification, cleared once the
 * alien has said it (the first time home is opened after the miss). Kept on the phone only.
 */
object MissedCall {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending
    private fun prefs(c: Context) = c.getSharedPreferences("missed_call", Context.MODE_PRIVATE)

    fun init(c: Context) { _pending.value = prefs(c).getBoolean("pending", false) }

    fun missed(c: Context) { prefs(c).edit().putBoolean("pending", true).apply(); _pending.value = true }

    fun said(c: Context) { prefs(c).edit().putBoolean("pending", false).apply(); _pending.value = false }
}
