package com.kurogane.dividend.model

data class RoadmapAchievement(
    val step: Int,
    val stepName: String,
    val targetMonthly: Int,
    val monthlyIncome: Long,
    val completedSteps: Int,
    val completedStageTitle: String,
    val completedStageBenefit: String,
    val completedStageTarget: Int
) {
    val completedLevel: Int get() = (completedSteps / 10).coerceIn(0, 3)
    val currentLevel: Int get() = (completedLevel + 1).coerceAtMost(3)
    val progressPercent: Int get() = if (targetMonthly > 0)
        (monthlyIncome.toDouble() / targetMonthly * 100).toInt().coerceIn(0, 100) else 0
    val canCertify: Boolean get() = completedLevel > 0
}
