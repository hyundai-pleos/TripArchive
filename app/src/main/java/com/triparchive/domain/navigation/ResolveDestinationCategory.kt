package com.triparchive.domain.navigation

import javax.inject.Inject

/** A missing classification is assigned once, when the arrival is recorded. */
class ResolveDestinationCategory
    @Inject
    constructor() {
        operator fun invoke(category: DestinationCategory?): DestinationCategory =
            category ?: DestinationCategory.entries.random()
    }
