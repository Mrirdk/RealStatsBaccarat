package com.stats.baccaratoverlay

import java.util.Locale
import kotlin.math.sqrt

data class RealAnalysisResult(
    val totalHands: Int,
    val distributionText: String,
    val streakText: String,
    val markovText: String,
    val zScoreText: String,
    val signalText: String
)

object BaccaratMathEngine {

    // Xác suất toán học chuẩn của Baccarat 8 bộ bài (không tính Hòa)
    private const val TRUE_BANKER_PROB = 0.5068
    private const val TRUE_PLAYER_PROB = 0.4932

    fun analyze(rawHistory: List<String>): RealAnalysisResult {
        val total = rawHistory.size
        if (total == 0) {
            return RealAnalysisResult(
                totalHands = 0,
                distributionText = "Chưa có dữ liệu bàn",
                streakText = "Chuỗi: Trống",
                markovText = "Xác suất chuyển nhịp: Chờ nhập ván",
                zScoreText = "Độ lệch chuẩn (Z): 0.00",
                signalText = "CHỜ NHẬP KẾT QUẢ THỰC TẾ"
            )
        }

        val pCount = rawHistory.count { it == "P" }
        val bCount = rawHistory.count { it == "B" }
        val tCount = rawHistory.count { it == "T" }

        val pRate = (pCount.toDouble() / total) * 100.0
        val bRate = (bCount.toDouble() / total) * 100.0
        val tRate = (tCount.toDouble() / total) * 100.0

        val distText = String.format(
            Locale.US,
            "Thực tế (%d ván): P %.1f%% (%d) | B %.1f%% (%d) | T %.1f%% (%d)",
            total, pRate, pCount, bRate, bCount, tRate, tCount
        )

        // Lọc bỏ Tie để phân tích chuỗi cầu P/B chuẩn xác theo luật bảng cầu
        val pbHistory = rawHistory.filter { it == "P" || it == "B" }
        val pbTotal = pbHistory.size

        if (pbTotal == 0) {
            return RealAnalysisResult(
                totalHands = total,
                distributionText = distText,
                streakText = "Chuỗi P/B: Chưa có",
                markovText = "Chưa đủ dữ liệu P/B",
                zScoreText = "Độ lệch chuẩn (Z): 0.00",
                signalText = "CHỜ KẾT QUẢ P HOẶC B"
            )
        }

        // 1. Tính chuỗi hiện tại (Bệt hay Đảo)
        val lastSide = pbHistory.last()
        var currentStreak = 0
        for (i in pbHistory.indices.reversed()) {
            if (pbHistory[i] == lastSide) currentStreak++ else break
        }
        val sideLabel = if (lastSide == "P") "CON (P)" else "CÁI (B)"
        val streakText = "Đang đi: $sideLabel dây $currentStreak | Gần nhất: ${pbHistory.takeLast(8).joinToString("-")}"

        // 2. Tính Z-Score (Độ lệch thực tế so với xác suất chuẩn 8 bộ bài)
        val expectedB = pbTotal * TRUE_BANKER_PROB
        val stdDev = sqrt(pbTotal * TRUE_BANKER_PROB * TRUE_PLAYER_PROB)
        val zScore = if (stdDev > 0) (bCount - expectedB) / stdDev else 0.0
        val zDesc = when {
            zScore >= 1.5 -> "Lệch Cái mạnh (+${String.format(Locale.US, "%.2f", zScore)}σ)"
            zScore <= -1.5 -> "Lệch Con mạnh (${String.format(Locale.US, "%.2f", zScore)}σ)"
            else -> "Cân bằng (${String.format(Locale.US, "%.2f", zScore)}σ)"
        }
        val zScoreText = "Độ lệch hộp bài: $zDesc"

        // 3. Tính Xác suất Markov bậc 1 từ dữ liệu thực của chính bàn này
        var followP = 0
        var followB = 0
        for (i in 0 until pbHistory.size - 1) {
            if (pbHistory[i] == lastSide) {
                if (pbHistory[i + 1] == "P") followP++
                else if (pbHistory[i + 1] == "B") followB++
            }
        }
        val transitionSample = followP + followB

        // 4. Tính Xác suất khớp mẫu 2 tay gần nhất (Markov bậc 2) nếu đủ dữ liệu
        var patP = 0
        var patB = 0
        if (pbHistory.size >= 3) {
            val s1 = pbHistory[pbHistory.size - 2]
            val s2 = pbHistory[pbHistory.size - 1]
            for (i in 0 until pbHistory.size - 2) {
                if (pbHistory[i] == s1 && pbHistory[i + 1] == s2) {
                    if (pbHistory[i + 2] == "P") patP++
                    else if (pbHistory[i + 2] == "B") patB++
                }
            }
        }
        val patSample = patP + patB

        // Tổng hợp báo cáo xác suất thực (Không bịa số)
        val markovText: String
        val signalText: String

        if (pbTotal < 4) {
            markovText = "Mẫu chuyển nhịp: $transitionSample lần (Cần tối thiểu 4 ván P/B)"
            signalText = "QUAN SÁT THÊM (${4 - pbTotal} VÁN NỮA)"
        } else if (patSample >= 3) {
            val pProb = (patP.toDouble() / patSample) * 100.0
            val bProb = (patB.toDouble() / patSample) * 100.0
            val s1 = pbHistory[pbHistory.size - 2]
            val s2 = pbHistory[pbHistory.size - 1]
            markovText = String.format(
                Locale.US,
                "Sau nhịp [%s-%s]: P %.1f%% (%d/%d) | B %.1f%% (%d/%d)",
                s1, s2, pProb, patP, patSample, bProb, patB, patSample
            )
            signalText = when {
                pProb > bProb -> String.format(Locale.US, "ƯU TIÊN: PLAYER (Thực tế %.1f%%)", pProb)
                bProb > pProb -> String.format(Locale.US, "ƯU TIÊN: BANKER (Thực tế %.1f%%)", bProb)
                else -> "CÂN BẰNG 50/50 - TẠM BỎ QUA"
            }
        } else if (transitionSample >= 2) {
            val pProb = (followP.toDouble() / transitionSample) * 100.0
            val bProb = (followB.toDouble() / transitionSample) * 100.0
            markovText = String.format(
                Locale.US,
                "Sau [%s]: ra P %.1f%% (%d/%d) | ra B %.1f%% (%d/%d)",
                lastSide, pProb, followP, transitionSample, bProb, followB, transitionSample
            )
            signalText = when {
                pProb > bProb -> String.format(Locale.US, "ƯU TIÊN: PLAYER (Thực tế %.1f%%)", pProb)
                bProb > pProb -> String.format(Locale.US, "ƯU TIÊN: BANKER (Thực tế %.1f%%)", bProb)
                else -> "CÂN BẰNG 50/50 - TẠM BỎ QUA"
            }
        } else {
            markovText = "Chưa lặp lại mẫu chuyển nhịp trong bàn"
            signalText = "CHỜ THÊM DỮ LIỆU CHUYỂN NHỊP"
        }

        return RealAnalysisResult(
            totalHands = total,
            distributionText = distText,
            streakText = streakText,
            markovText = markovText,
            zScoreText = zScoreText,
            signalText = signalText
        )
    }
}
