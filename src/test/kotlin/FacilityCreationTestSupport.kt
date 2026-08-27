package com.doduohor

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityCreationResult

fun FacilityCreationResult<Facility>.getOrThrow(): Facility = when (this) {
    FacilityCreationResult.InvalidName -> error("Test fixture contains an invalid facility name")
    is FacilityCreationResult.Success -> value
}
