package com.example.anbar

import android.content.Context
import kotlin.random.Random

object MolanaHelper {
    private var cache: List<String>? = null

    fun load(context: Context): List<String> {
        cache?.let { return it }
        return try {
            context.assets.open("molana_verses.txt").bufferedReader(Charsets.UTF_8)
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .also { cache = it }
        } catch (_: Exception) {
            listOf("از محبت تلخ‌ها شیرین شود")
        }
    }

    fun randomVerse(context: Context): String {
        val list = load(context)
        return list[Random.nextInt(list.size)]
    }
}
