package space.wavedot.server.kernel

interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

object DirectTransactionRunner : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}
