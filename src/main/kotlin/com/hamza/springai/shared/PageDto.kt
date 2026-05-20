package com.hamza.springai.shared

import org.springframework.data.domain.Sort

data class PageMeta(
    val isFirst: Boolean,
    val isLast: Boolean,
    val number: Int,
    val size: Int,
    val totalPages: Int,
    val totalElements: Long,
)

data class SortField(
    val property: String,
    val direction: Sort.Direction,
)
