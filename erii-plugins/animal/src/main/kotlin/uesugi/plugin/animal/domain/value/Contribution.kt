package uesugi.plugin.animal.domain.value

import java.time.Instant

data class Contribution(
    val year: Int,
    var contribution: Int,
    var lastUpdatedContribution: Instant,
)
