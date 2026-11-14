package com.triparchive.presentation.home

import com.triparchive.domain.navigation.DestinationCategory

internal val DestinationCategory.label: String
    get() =
        when (this) {
            DestinationCategory.MART -> "마트"
            DestinationCategory.SCHOOL -> "학교"
            DestinationCategory.TOURISM -> "관광"
            DestinationCategory.FOOD -> "음식점"
            DestinationCategory.HOSPITAL -> "병원"
        }

internal val DestinationCategory.assetFileName: String
    get() =
        when (this) {
            DestinationCategory.MART -> "mart.glb"
            DestinationCategory.SCHOOL -> "school.glb"
            DestinationCategory.TOURISM -> "tourism.glb"
            DestinationCategory.FOOD -> "restaurant.glb"
            DestinationCategory.HOSPITAL -> "hospital.glb"
        }
