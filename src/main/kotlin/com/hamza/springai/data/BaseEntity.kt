package com.hamza.springai.data

import io.hypersistence.tsid.TSID
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

interface EntityId {
    val id: TSID
}

@MappedSuperclass
abstract class BaseEntity<ID : EntityId>(
    @field:EmbeddedId
    val id: ID,
    //
    @field:Column(nullable = false)
    @field:CreationTimestamp
    var createdAt: Instant = Instant.now(),
    //
    @field:Column(nullable = false)
    @field:UpdateTimestamp
    var updatedAt: Instant = Instant.now(),
    //
    @field:Column(nullable = false)
    @field:Version
    var version: Int = 0,
)
