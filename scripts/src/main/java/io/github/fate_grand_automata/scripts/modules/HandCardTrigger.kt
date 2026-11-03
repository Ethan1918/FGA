package io.github.fate_grand_automata.scripts.modules

import io.github.fate_grand_automata.scripts.models.HandCardSkillRule
import io.github.fate_grand_automata.scripts.models.ParsedCard
import io.github.fate_grand_automata.scripts.prefs.IBattleConfig
import io.github.lib_automata.dagger.ScriptScope
import javax.inject.Inject

/**
 * 手牌条件技能：先看一眼本回合手牌，条件不满足时释放指定技能。
 *
 * 规则来自战斗配置的 `hand_card_skill_rule` 字段，格式见 [HandCardSkillRule]。
 * 未配置或配置非法时 [enabled] 为 false，战斗流程保持原样，不会多开一次出卡界面。
 */
@ScriptScope
class HandCardTrigger @Inject constructor(
    private val battleConfig: IBattleConfig,
    private val caster: Caster
) {
    private val rule: HandCardSkillRule? by lazy {
        HandCardSkillRule.parse(battleConfig.handCardSkillRule)
    }

    val enabled: Boolean
        get() = rule != null

    /** 当前波次是否需要为本规则偷看手牌。 */
    fun shouldPeek(stage: Int): Boolean =
        rule?.matchesWave(stage) == true

    /** 手牌是否不满足条件，需要先放技能。 */
    fun shouldCast(cards: List<ParsedCard>, stage: Int): Boolean {
        val rule = rule ?: return false

        return rule.matchesWave(stage) && rule.isBroken(cards)
    }

    /** 释放规则指定的技能。调用前须确认 [shouldCast] 为真。 */
    fun cast() {
        val rule = rule ?: return

        caster.castServantSkill(rule.skill, rule.servantTarget())
    }
}
