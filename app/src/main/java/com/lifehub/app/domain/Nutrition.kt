package com.lifehub.app.domain

import com.lifehub.app.data.model.BodyEntry

fun autoKcal(proteinG: Int, carbsG: Int, fatG: Int): Int = 4 * proteinG + 4 * carbsG + 9 * fatG
fun fatPct(e: BodyEntry): Double? = e.fatKg?.let { it / e.weightKg * 100 }
