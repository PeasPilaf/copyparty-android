package com.peaspilaf.copyparty.server

import com.peaspilaf.copyparty.server.ServerProbe.PortOwner

/**
 * Waits until the app can honestly say the server is running.
 *
 * "Running" means three things at once: copyparty itself answers on the port ([ServerProbe]), the
 * embedded interpreter is still alive ([PythonBridge.isAlive]), and therefore the URLs the app is
 * about to show really lead to the shared folders. A foreign listener is reported as
 * [ServerStatus.Failed] with [ServerProbe.portInUseMessage] and never as `Running`.
 *
 * The clock and the sleep are injectable so the host tests can drive the whole 120 s readiness
 * budget without waiting for it.
 */
class ServerReadiness(
    private val port: Int,
    private val lanAccess: Boolean,
    private val isAlive: () -> Boolean = { PythonBridge.isAlive() },
    private val probe: (Int) -> PortOwner = { ServerProbe.probe(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    /**
     * Polls until the port answers as copyparty, somebody else answers, the interpreter dies, or
     * [timeoutMs] runs out. Blocking on purpose: the foreground service calls it from its IO worker.
     */
    fun await(timeoutMs: Long = READINESS_TIMEOUT_MS): ServerStatus {
        val deadline = clock() + timeoutMs
        while (true) {
            when (probe(port)) {
                PortOwner.COPYPARTY -> return if (isAlive()) {
                    ServerStatus.Running(LocalUrls.serverUrls(port, lanAccess), port)
                } else {
                    ServerStatus.Failed("Copyparty answered on port $port but stopped before it could serve.")
                }

                PortOwner.SOMETHING_ELSE -> return ServerStatus.Failed(ServerProbe.portInUseMessage(port))

                PortOwner.NOTHING -> {
                    // A dead interpreter will not answer no matter how long we wait.
                    if (!isAlive()) return ServerStatus.Failed("Copyparty stopped before it finished starting.")
                }
            }
            if (clock() >= deadline) {
                return ServerStatus.Failed("The server did not start listening on port $port.")
            }
            sleep(POLL_INTERVAL_MS)
        }
    }

    companion object {
        /** The readiness budget for a cold start of the embedded interpreter. */
        const val READINESS_TIMEOUT_MS = 120_000L
        private const val POLL_INTERVAL_MS = 150L
    }
}
