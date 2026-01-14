package uesugi.core

enum class RouteRule(val title: String) {

    REQUEST_R18_IMAGE(
        "仅当用户明确索要成人向图片、涩图、R18 图片内容时才符合此分类，不要扩大解释"
    ),

    DIRECT_QA(
        "用户提出的是明确、严肃、可被直接回答的问题，包括知识、技术、事实、操作说明、学习类问题，应由 AI 直接回答，不进入任何人格 Agent"
    ),

    CHAT(
        "群内的日常闲聊、调侃、情绪互动、玩梗、吐槽或不追求严谨答案的对话，才进入人格 Agent"
    )

}
