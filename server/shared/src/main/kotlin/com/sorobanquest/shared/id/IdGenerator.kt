package com.sorobanquest.shared.id

import java.util.UUID

/** Порт генерации идентификаторов — позволяет сделать тесты детерминированными. */
fun interface IdGenerator {
    fun newId(): UUID
}

object RandomIdGenerator : IdGenerator {
    override fun newId(): UUID = UUID.randomUUID()
}
