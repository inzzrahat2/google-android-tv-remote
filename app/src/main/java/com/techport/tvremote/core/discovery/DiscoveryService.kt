package com.techport.tvremote.core.discovery

import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.protocol.core.ProtocolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Merges every protocol's discovery. Scanning runs only while the flow is collected. */
class DiscoveryService(private val registry: ProtocolRegistry) {
    fun discover(): Flow<List<DiscoveredDevice>> {
        val sources = registry.protocols.mapNotNull { it.discovery?.discover() }
        if (sources.isEmpty()) return flowOf(emptyList())
        return combine(sources) { lists -> lists.flatMap { it }.distinctBy { it.id } }
    }
}
