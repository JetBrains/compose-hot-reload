package org.jetbrains.compose.reload.test

import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

/*
When the Gradle Runner from IntelliJ is started in 'Debug Mode' it will start a 'Debug Dispatch Server'.
This server can be used to start debug servers on IntelliJ.
Therefore, we will try to find a suitable free port, connect to the dispatch server and request a
debug server being created at this port. Once this server was launched, we can launch the actual
JVM instructing it to connect to the said debug server.

If we're not in debug mode, an empty array will be returned.
 */
internal fun createDebuggerJvmArguments(intellijDebugDispatchPort: Int?): Array<String> {
    val port = ServerSocket(0).use { it.localPort }

    intellijDebugDispatchPort?.let { dispatchPort ->
        //logger.info("Setting up debugger: dispatch.port=$dispatchPort; debug.port=$port")
        Socket("127.0.0.1", dispatchPort).use { debugDispatchSocket ->
            val output = DataOutputStream(debugDispatchSocket.getOutputStream())
            output.use {
                output.writeUTF("Gradle JVM") // Debugger ID
                output.writeUTF("Hot Reload Test ${System.currentTimeMillis()}") // Process Name
                output.writeUTF("DEBUG_SERVER_PORT=$port") // Arguments
                output.flush()

                // wait for any response!
                debugDispatchSocket.inputStream.read()
            }
        }
        return arrayOf("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=$port")
    }
    return arrayOf()
}
