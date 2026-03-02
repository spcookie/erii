package uesugi.core.state.memo

/**
 * 表情包相关事件定义
 */
sealed class MemoEvent {
    /**
     * 表情包被使用事件
     */
    data class MemoUsed(
        val memoId: Int,
        val botMark: String,
        val groupId: String
    ) : MemoEvent()

    /**
     * 表情包被搜索事件
     */
    data class MemoSearched(
        val query: String,
        val botMark: String,
        val groupId: String?
    ) : MemoEvent()
}
