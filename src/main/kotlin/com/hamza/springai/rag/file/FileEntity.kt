package com.hamza.springai.rag.file

import com.hamza.springai.data.BaseEntity
import com.hamza.springai.data.EntityId
import com.hamza.springai.data.TSIDGenerator
import com.hamza.springai.data.decodeToTSID
import com.hamza.springai.data.encodeToString
import io.hypersistence.tsid.TSID
import jakarta.persistence.Cacheable
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.hibernate.validator.constraints.Length

@Embeddable
data class FileId(
    override var id: TSID,
) : EntityId {
    constructor() : this(TSIDGenerator.next())
    constructor(id: String) : this(id.decodeToTSID())

    override fun toString(): String = this.id.encodeToString()
}

@Entity
@Table(name = "files")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "files")
class File(
    override var id: FileId = FileId(),
    //
    @field:Column(nullable = false, length = 1000)
    @field:NotBlank
    @field:Length(max = 1000)
    var name: String,
    //
    @field:Column(nullable = false, unique = true, length = 1000)
    @field:NotBlank
    @field:Length(max = 1000)
    var hash: String,
) : BaseEntity<FileId>(id) {
    override fun equals(other: Any?): Boolean = this === other || (other is File && this.id == other.id)

    override fun hashCode(): Int = this.id.hashCode()
}
