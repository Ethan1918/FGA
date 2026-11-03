package io.github.fate_grand_automata.scripts.models

import io.github.fate_grand_automata.scripts.enums.CardTypeEnum

/**
 * 手牌条件技能规则：当指定场上从者的可用手牌不满足条件时，释放指定技能。
 *
 * 场景：先看一眼本回合手牌，若目标从者的某色卡数量不够、或可用卡总数不足，
 * 就先返回战斗主界面释放第 n 个技能，然后再出卡。
 *
 * 配置语法（分号分隔的 key=value，大小写不敏感，未知键会被忽略）：
 * - `observe=1|2|3` 观察的场上位置，1 为最左，默认 1
 * - `type=Any|Buster|Arts|Quick` 关注的卡色，默认 Any（不计卡色）
 * - `minCards=2` 该色可用卡的下限，低于该值触发技能，默认 3
 * - `minTotal=0` 该从者可用卡总数的下限，低于该值触发技能，0 表示不检查
 * - `skill=1|2|3` 释放技能的场上位置，默认与 observe 相同
 * - `skillIndex=1|2|3` 释放第几个技能，默认 1
 * - `target=none|self|observe` 技能目标选择，默认 none（不选目标，适用于全体技能）
 * - `waves=1,2` 生效波次（从 1 开始），留空表示所有波次
 *
 * 示例：`observe=1;type=Arts;minCards=2;minTotal=3;skill=1;skillIndex=3`
 * 表示：场上最左从者的 Arts 可用卡少于 2 张，或该从者可用卡总数少于 3 张时，
 * 释放该从者的第 3 个技能。
 */
data class HandCardSkillRule(
    val observeSlot: FieldSlot,
    val cardType: CardTypeEnum?,
    val minCards: Int,
    val minTotalCards: Int,
    val skillSlot: FieldSlot,
    val skillIndex: Int,
    val target: Target,
    val waves: Set<Int>
) {
    enum class Target {
        None,
        Self,
        Observe
    }

    /** 要释放的技能。skillIndex 越界时调用方需先经 [parse] 校验。 */
    val skill: Skill.Servant
        get() = skillSlot.skills()[skillIndex - 1]

    /** 技能目标；返回 null 表示不需要选择目标（全体技能）。 */
    fun servantTarget(): ServantTarget? =
        when (target) {
            Target.None -> null
            Target.Self -> skillSlot.toServantTarget()
            Target.Observe -> observeSlot.toServantTarget()
        }

    fun matchesWave(stage: Int) = waves.isEmpty() || (stage + 1) in waves

    /**
     * 该从者的手牌是否"不满足"条件。
     * 不可用卡（被眩晕、无法行动、识别失败）不计入任何数量。
     */
    fun isBroken(cards: List<ParsedCard>): Boolean {
        val usable = cards.filter {
            it.fieldSlot == observeSlot && !it.isStunned && it.type != CardTypeEnum.Unknown
        }

        val totalOk = minTotalCards <= 0 || usable.size >= minTotalCards
        val colorOk = usable.count { cardType == null || it.type == cardType } >= minCards

        return !(totalOk && colorOk)
    }

    companion object {
        private val validIndices = 1..3

        /**
         * 解析配置字符串，无法识别或设置非法时返回 null（视为未启用）。
         */
        fun parse(raw: String): HandCardSkillRule? {
            if (raw.isBlank()) return null

            val entries = raw
                .split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) return@mapNotNull null

                    val key = line.substring(0, separator).trim().lowercase()
                    val value = line.substring(separator + 1).trim()

                    if (value.isEmpty()) null else key to value
                }
                .toMap()

            if (entries.isEmpty()) return null

            fun slotOf(value: String?): FieldSlot? =
                when (value?.trim()?.lowercase()) {
                    "1", "a", "left" -> FieldSlot.A
                    "2", "b", "middle" -> FieldSlot.B
                    "3", "c", "right" -> FieldSlot.C
                    else -> null
                }

            fun indexOf(value: String?): Int? =
                value?.trim()?.toIntOrNull()?.takeIf { it in validIndices }

            fun intOf(value: String?, default: Int): Int =
                value?.trim()?.toIntOrNull() ?: default

            val observeSlot = slotOf(entries["observe"]) ?: return null
            val skillSlot = entries["skill"]?.let { slotOf(it) ?: return null } ?: observeSlot
            val skillIndex = entries["skillindex"]?.let { indexOf(it) ?: return null } ?: 1

            val cardType = when (entries["type"]?.trim()?.lowercase()) {
                null, "", "any", "all" -> null
                "b", "buster" -> CardTypeEnum.Buster
                "a", "arts" -> CardTypeEnum.Arts
                "q", "quick" -> CardTypeEnum.Quick
                else -> return null
            }

            val target = when (entries["target"]?.trim()?.lowercase()) {
                null, "", "none" -> Target.None
                "self" -> Target.Self
                "observe" -> Target.Observe
                else -> return null
            }

            val waves = entries["waves"]
                ?.split(',')
                ?.mapNotNull { it.trim().takeIf { text -> text.isNotEmpty() }?.toIntOrNull() }
                ?.filter { it > 0 }
                ?.toSet()
                ?: emptySet()

            return HandCardSkillRule(
                observeSlot = observeSlot,
                cardType = cardType,
                minCards = intOf(entries["mincards"], 3),
                minTotalCards = intOf(entries["mintotal"], 0),
                skillSlot = skillSlot,
                skillIndex = skillIndex,
                target = target,
                waves = waves
            )
        }
    }
}

private fun FieldSlot.toServantTarget() = when (this) {
    FieldSlot.A -> ServantTarget.A
    FieldSlot.B -> ServantTarget.B
    FieldSlot.C -> ServantTarget.C
}
