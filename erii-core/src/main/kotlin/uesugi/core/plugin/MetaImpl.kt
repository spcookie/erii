package uesugi.core.plugin

import uesugi.common.IBotManage
import uesugi.spi.Meta

internal class MetaImpl(
    override val botId: String,
    override val groupId: String,
    override val roledBot: IBotManage.RoledBot,
    override val senderId: String? = null,
    override val input: String? = null,
    override val echo: String? = null
) : Meta
