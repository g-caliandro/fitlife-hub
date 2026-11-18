package com.lifehub.app.domain

import com.lifehub.app.data.model.BodyEntry

/** Atwater estimate (4 / 4 / 9 kcal per gram), used wherever kcal are not given. */
fun autoKcal(proteinG: Int, carbsG: Int, fatG: Int): Int = 4 * proteinG + 4 * carbsG + 9 * fatG
fun autoKcal(proteinG: Double, carbsG: Double, fatG: Double): Double = 4 * proteinG + 4 * carbsG + 9 * fatG
fun fatPct(e: BodyEntry): Double? = e.fatKg?.let { it / e.weightKg * 100 }
