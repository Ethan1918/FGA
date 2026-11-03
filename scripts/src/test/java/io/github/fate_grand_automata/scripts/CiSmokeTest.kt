package io.github.fate_grand_automata.scripts

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.github.fate_grand_automata.scripts.models.CommandCard
import kotlin.test.Test

/**
 * 冒烟测试：只为验证 CI 能完整编译并跑通 :scripts 的单元测试。
 * CI 验证结束后可整文件删除。
 */
class CiSmokeTest {
    @Test
    fun `there are five face card slots and three NP slots`() {
        assertThat(CommandCard.Face.list.size).isEqualTo(5)
        assertThat(CommandCard.NP.list.size).isEqualTo(3)
    }
}
