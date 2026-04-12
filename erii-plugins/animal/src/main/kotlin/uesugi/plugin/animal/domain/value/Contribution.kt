package uesugi.plugin.animal.domain.value

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Contribution(
    val year: Int,
    var contribution: Int,
    @Contextual
    var lastUpdatedContribution: Instant,
)
