package uesugi.common

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * PAD 情感量表 12 题评分数据模型
 *
 * 基于中国科学院心理研究所提出的中文简化 PAD 情感量表
 * - P (Pleasure): 愉悦度，Q1/Q4/Q7/Q10
 * - A (Arousal): 唤醒度，Q2/Q5/Q8/Q11
 * - D (Dominance): 掌控感，Q3/Q6/Q9/Q12
 */
@Serializable
@SerialName("PadScale12")
data class PadScale12(

    // ===== Pleasure (P) - 愉悦度 =====

    @property:LLMDescription(
        "Q1: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示愤怒的，+4 表示有活力的。"
    )
    val q1: Double,

    @property:LLMDescription(
        "Q4: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示友好的，+4 表示轻蔑的。"
    )
    val q4: Double,

    @property:LLMDescription(
        "Q7: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示残忍的，+4 表示高兴的。"
    )
    val q7: Double,

    @property:LLMDescription(
        "Q10: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示兴奋的，+4 表示激怒的。"
    )
    val q10: Double,


    // ===== Arousal (A) - 唤醒度 =====

    @property:LLMDescription(
        "Q2: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示清醒的，+4 表示困倦的。"
    )
    val q2: Double,

    @property:LLMDescription(
        "Q5: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示平静的，+4 表示激动的。"
    )
    val q5: Double,

    @property:LLMDescription(
        "Q8: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示感兴趣的，+4 表示放松的。"
    )
    val q8: Double,

    @property:LLMDescription(
        "Q11: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示放松的，+4 表示充满希望的。"
    )
    val q11: Double,


    // ===== Dominance (D) - 掌控感 =====

    @property:LLMDescription(
        "Q3: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示被控的，+4 表示主控的。"
    )
    val q3: Double,

    @property:LLMDescription(
        "Q6: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示支配的，+4 表示顺从的。"
    )
    val q6: Double,

    @property:LLMDescription(
        "Q9: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示被引导的，+4 表示自主的。"
    )
    val q9: Double,

    @property:LLMDescription(
        "Q12: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示有影响力的，+4 表示受影响的。"
    )
    val q12: Double
)

@Serializable
data class PAD(
    val p: Double,
    val a: Double,
    val d: Double
) {

    fun normalize(): PAD {
        return this.copy(
            p = p / 4.0,
            a = a / 4.0,
            d = d / 4.0
        )
    }

    operator fun plus(other: PAD): PAD {
        return PAD(this.p + other.p, this.a + other.a, this.d + other.d)
    }

    operator fun minus(other: PAD): PAD {
        return PAD(this.p - other.p, this.a - other.a, this.d - other.d)
    }

    operator fun times(scalar: Double): PAD {
        return PAD(this.p * scalar, this.a * scalar, this.d * scalar)
    }

    operator fun times(other: PAD): PAD {
        return PAD(this.p * other.p, this.a * other.a, this.d * other.d)
    }

    operator fun div(scalar: Double): PAD {
        if (scalar == 0.0) throw IllegalArgumentException("Cannot divide by zero.")
        return PAD(this.p / scalar, this.a / scalar, this.d / scalar)
    }

    operator fun div(other: PAD): PAD {
        if (other.p == 0.0 || other.a == 0.0 || other.d == 0.0) {
            throw IllegalArgumentException("Cannot perform element-wise division by a PAD object containing zero.")
        }
        return PAD(this.p / other.p, this.a / other.a, this.d / other.d)
    }

    companion object {

        val ZERO = PAD(p = 0.0, a = 0.0, d = 0.0)

        fun from(scale: PadScale12): PAD {
            val p = (scale.q1 - scale.q4 + scale.q7 - scale.q10) / 4
            val a = (-scale.q2 + scale.q5 - scale.q8 + scale.q11) / 4
            val d = (scale.q3 - scale.q6 + scale.q9 - scale.q12) / 4
            return PAD(p, a, d)
        }

    }

}

enum class EmotionalTendencies(val pad: PAD) {
    /**
     * 喜悦
     */
    JOY(PAD(p = 2.77, a = 1.21, d = 1.42)),

    /**
     * 乐观
     */
    OPTIMISM(PAD(p = 2.48, a = 1.05, d = 1.75)),

    /**
     * 轻松
     */
    RELAXATION(PAD(p = 2.19, a = -0.66, d = 1.05)),

    /**
     * 惊奇
     */
    SURPRISE(PAD(p = 1.72, a = 1.71, d = 0.22)),

    /**
     * 温和
     */
    MILDNESS(PAD(p = 1.57, a = -0.79, d = 0.38)),

    /**
     * 依赖
     */
    DEPENDENCE(PAD(p = 0.39, a = -0.81, d = 1.48)),

    /**
     * 无聊
     */
    BOREDOM(PAD(p = -0.53, a = -1.25, d = -0.84)),

    /**
     * 悲伤
     */
    SADNESS(PAD(p = -0.89, a = 0.17, d = -0.70)),

    /**
     * 恐惧
     */
    FEAR(PAD(p = -0.93, a = 1.30, d = -0.64)),

    /**
     * 焦虑
     */
    ANXIETY(PAD(p = -0.95, a = 0.32, d = -0.63)),

    /**
     * 藐视
     */
    CONTEMPT(PAD(p = -1.58, a = 0.32, d = 1.02)),

    /**
     * 厌恶
     */
    DISGUST(PAD(p = -1.80, a = 0.40, d = 0.67)),

    /**
     * 愤懑
     */
    RESENTMENT(PAD(p = -1.98, a = 1.10, d = 0.60)),

    /**
     * 敌意
     */
    HOSTILITY(PAD(p = -2.08, a = 1.00, d = 1.12));

    companion object {

        /**
         * 计算两个PAD值之间的欧式距离
         *
         * @param pad1 第一个PAD点
         * @param pad2 第二个PAD点
         * @return 距离
         */
        private fun calculateDistance(
            pad1: PAD,
            pad2: PAD
        ): Double {
            val pDiff = pad1.p - pad2.p
            val aDiff = pad1.a - pad2.a
            val dDiff = pad1.d - pad2.d
            return sqrt(pDiff.pow(2) + aDiff.pow(2) + dDiff.pow(2))
        }

        /**
         * 找出与用户当前情感最接近的基本情感倾向
         *
         * @param pad 用户的PAD情感状态
         * @return 距离最小的基本情感
         */
        fun findClosest(pad: PAD): EmotionalTendencies {
            return EmotionalTendencies.entries.toTypedArray().minByOrNull {
                calculateDistance(pad, it.pad)
            } ?: throw IllegalStateException("EmotionalTendencies enum cannot be empty.")
        }
    }

}