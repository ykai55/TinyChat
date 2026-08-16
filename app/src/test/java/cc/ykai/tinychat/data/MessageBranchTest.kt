package cc.ykai.tinychat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageBranchTest {
  @Test
  fun followsSelectedChildrenAndReportsSiblingPosition() {
    val nodes =
      listOf(
        node(id = 1, parentId = null, selectedChildId = 2),
        node(id = 2, parentId = 1, selectedChildId = 4, role = MessageRole.Assistant),
        node(id = 3, parentId = 1, selectedChildId = null, role = MessageRole.Assistant),
        node(id = 4, parentId = 2, selectedChildId = null),
      )

    val path = activeMessagePath(nodes, selectedRootMessageId = 1)

    assertEquals(listOf(1L, 2L, 4L), path.map { it.id })
    assertEquals(0, path[1].branchIndex)
    assertEquals(2, path[1].branchCount)
  }

  @Test
  fun changingASelectionRestoresThatBranchesSelectedDescendants() {
    val nodes =
      listOf(
        node(id = 1, parentId = null, selectedChildId = 3),
        node(id = 2, parentId = 1, selectedChildId = 4, role = MessageRole.Assistant),
        node(id = 3, parentId = 1, selectedChildId = 5, role = MessageRole.Assistant),
        node(id = 4, parentId = 2, selectedChildId = null),
        node(id = 5, parentId = 3, selectedChildId = null),
      )

    assertEquals(listOf(1L, 3L, 5L), activeMessagePath(nodes, 1).map { it.id })
    assertEquals(
      listOf(1L, 2L, 4L),
      activeMessagePath(nodes.map { if (it.id == 1L) it.copy(selectedChildId = 2) else it }, 1)
        .map { it.id },
    )
  }

  @Test
  fun reportsRootMessageBranches() {
    val nodes =
      listOf(
        node(id = 1, parentId = null, selectedChildId = null),
        node(id = 2, parentId = null, selectedChildId = null),
      )

    val path = activeMessagePath(nodes, selectedRootMessageId = 2)

    assertEquals(listOf(2L), path.map { it.id })
    assertEquals(1, path.single().branchIndex)
    assertEquals(2, path.single().branchCount)
  }

  private fun node(
    id: Long,
    parentId: Long?,
    selectedChildId: Long?,
    role: MessageRole = MessageRole.User,
  ) =
    MessageNode(
      id = id,
      conversationId = 1,
      role = role,
      content = "message $id",
      createdAt = id,
      parentId = parentId,
      selectedChildId = selectedChildId,
    )
}
