package com.triparchive.data.navigation

import com.triparchive.domain.navigation.DestinationCategory

internal fun categoryForCode(code: String): DestinationCategory? =
    when (code) {
        "MT1", "CS2" -> DestinationCategory.MART
        "PS3", "SC4", "AC5" -> DestinationCategory.SCHOOL
        "CT1", "PO3", "AT4", "AD5" -> DestinationCategory.TOURISM
        "FD6", "CE7" -> DestinationCategory.FOOD
        "HP8", "PM9" -> DestinationCategory.HOSPITAL
        else -> null
    }
