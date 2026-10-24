package com.techport.tvremote.protocol.core

class ProtocolRegistry(val protocols: List<RemoteProtocol>) {
    private val byId = protocols.associateBy { it.id }

    init {
        require(byId.size == protocols.size) { "Duplicate protocol ids" }
    }

    operator fun get(id: String): RemoteProtocol? = byId[id]
}
