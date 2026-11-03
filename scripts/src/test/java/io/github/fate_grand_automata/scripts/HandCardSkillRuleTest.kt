package io.github.fate_grand_automata.scripts

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.github.fate_grand_automata.scripts.enums.CardTypeEnum
import io.github.fate_grand_automata.scripts.models.CommandCard
import io.github.fate_grand_automata.scripts.models.FieldSlot
import io.github.fate_grand_automata.scripts.models.HandCardSkillRule
import io.github.fate_grand_automata.scripts.models.ParsedCard
import io.github.fate_grand_automata.scripts.models.ServantTarget
import io.github.fate_grand_automata.scripts.models.Skill
import io.github.fate_grand_automata.scripts.models.TeamSlot
import kotlin.test.Test

class HandCardSkillRuleTest {
    private fun card(
        face: CommandCard.Face,
        slot: FieldSlot,
        type: CardTypeEnum,
        stunned: Boolean = false
    ) = ParsedCard(
        card = face,
        servant = TeamSlot.A,
        fieldSlot = slot,
        type = if (stunned) CardTypeEnum.Unknown else type,
        isStunned = stunned
    )

    private val arts = card(CommandCard.Face.A, FieldSlot.A, CardTypeEnum.Arts)
    private val buster = card(CommandCard.Face.B, FieldSlot.A, CardTypeEnum.Buster)
    private val quick = card(CommandCard.Face.C, FieldSlot.A, CardTypeEnum.Quick)
    private val stunned = card(CommandCard.Face.D, FieldSlot.A, CardTypeEnum.Arts, stunned = true)
    private val otherSlot = card(CommandCard.Face.E, FieldSlot.B, CardTypeEnum.Arts)

    @Test
    fun `parses every field from the config string`() {
        val rule = HandCardSkillRule.parse(
            "observe=2;type=Arts;minCards=2;minTotal=3;skill=3;skillIndex=3;target=observe;waves=1,2"
        )

        assertThat(rule?.observeSlot).isEqualTo(FieldSlot.B)
        assertThat(rule?.cardType).isEqualTo(CardTypeEnum.Arts)
        assertThat(rule?.minCards).isEqualTo(2)
        assertThat(rule?.minTotalCards).isEqualTo(3)
        assertThat(rule?.skillSlot).isEqualTo(FieldSlot.C)
        assertThat(rule?.skillIndex).isEqualTo(3)
        assertThat(rule?.target).isEqualTo(HandCardSkillRule.Target.Observe)
        assertThat(rule?.waves).isEqualTo(setOf(1, 2))
        assertThat(rule?.skill).isEqualTo(Skill.Servant.C3)
        assertThat(rule?.servantTarget()).isEqualTo(ServantTarget.B)
    }

    @Test
    fun `defaults fill in when optional keys are missing`() {
        val rule = HandCardSkillRule.parse("observe=1")

        assertThat(rule?.cardType).isNull()
        assertThat(rule?.minCards).isEqualTo(3)
        assertThat(rule?.minTotalCards).isEqualTo(0)
        assertThat(rule?.skillSlot).isEqualTo(FieldSlot.A)
        assertThat(rule?.skillIndex).isEqualTo(1)
        assertThat(rule?.target).isEqualTo(HandCardSkillRule.Target.None)
        assertThat(rule?.servantTarget()).isNull()
    }

    @Test
    fun `invalid config yields null so the rule stays disabled`() {
        assertThat(HandCardSkillRule.parse("")).isNull()
        assertThat(HandCardSkillRule.parse("   ")).isNull()
        assertThat(HandCardSkillRule.parse("minCards=2")).isNull()
        assertThat(HandCardSkillRule.parse("observe=9")).isNull()
        assertThat(HandCardSkillRule.parse("observe=1;skillIndex=4")).isNull()
        assertThat(HandCardSkillRule.parse("observe=1;type=Extra")).isNull()
        assertThat(HandCardSkillRule.parse("observe=1;target=everyone")).isNull()
    }

    @Test
    fun `not enough cards of the watched colour counts as broken`() {
        val rule = HandCardSkillRule.parse("observe=1;type=Arts;minCards=2")!!

        // 只有 1 张 Arts 可用卡
        assertThat(rule.isBroken(listOf(arts, buster, quick, otherSlot))).isTrue()

        // 2 张 Arts 可用卡，达标
        val secondArts = card(CommandCard.Face.C, FieldSlot.A, CardTypeEnum.Arts)
        assertThat(rule.isBroken(listOf(arts, secondArts, buster, otherSlot))).isFalse()
    }

    @Test
    fun `other field slots do not count towards the watched servant`() {
        val rule = HandCardSkillRule.parse("observe=1;type=Arts;minCards=2")!!

        // 另一位置的 Arts 卡不算进来
        assertThat(rule.isBroken(listOf(arts, otherSlot, otherSlot))).isTrue()
    }

    @Test
    fun `total card count below the minimum counts as broken`() {
        val rule = HandCardSkillRule.parse("observe=1;minCards=0;minTotal=3")!!

        // 3 张可用卡，达标
        assertThat(rule.isBroken(listOf(arts, buster, quick))).isFalse()

        // 眩晕卡不算可用，只剩 2 张
        assertThat(rule.isBroken(listOf(arts, buster, stunned))).isTrue()
    }

    @Test
    fun `stunned cards never satisfy the colour requirement`() {
        val rule = HandCardSkillRule.parse("observe=1;type=Arts;minCards=2")!!

        assertThat(rule.isBroken(listOf(arts, stunned, buster))).isTrue()
    }

    @Test
    fun `wave filter limits when the rule applies`() {
        val rule = HandCardSkillRule.parse("observe=1;waves=2")!!

        assertThat(rule.matchesWave(0)).isFalse()
        assertThat(rule.matchesWave(1)).isTrue()
        assertThat(rule.matchesWave(2)).isFalse()
    }

    @Test
    fun `empty wave list applies to every wave`() {
        val rule = HandCardSkillRule.parse("observe=1")!!

        assertThat(rule.matchesWave(0)).isTrue()
        assertThat(rule.matchesWave(2)).isTrue()
    }
}
