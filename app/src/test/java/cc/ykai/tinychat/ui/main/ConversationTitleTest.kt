package cc.ykai.tinychat.ui.main

import junit.framework.TestCase.assertEquals
import org.junit.Test

class ConversationTitleTest {
  @Test
  fun title_collapsesWhitespace() {
    assertEquals("帮我 写一份计划", titleFor("  帮我\n写一份计划  "))
  }

  @Test
  fun title_truncatesLongPrompt() {
    val prompt = "这是一段明显超过二十八个字符的用户问题，用来验证标题是否会被正确截断"

    assertEquals(29, titleFor(prompt).length)
    assertEquals('…', titleFor(prompt).last())
  }

  @Test
  fun explicitImageCommand_extractsPrompt() {
    assertEquals("夕阳下的橘猫", explicitImagePrompt("/image 夕阳下的橘猫"))
    assertEquals("水彩风格的海边", explicitImagePrompt("/生图 水彩风格的海边"))
    assertEquals(null, explicitImagePrompt("解释图片生成原理"))
  }
}
