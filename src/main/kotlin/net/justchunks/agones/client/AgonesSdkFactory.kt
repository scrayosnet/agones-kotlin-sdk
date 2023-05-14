package net.justchunks.agones.client

/**
 * An [AgonesSdkFactory] is a factory for the creation of new instance of the [Agones SDK][AgonesSdk]. Through this
 * factory, the concrete implementations of the SDK can be retrieved and used for the corresponding platform afterward.
 * The factory already sets defaults and preferences for the different implementations, so that the most robust and
 * most performant variant will be chosen.
 */
class AgonesSdkFactory private constructor() {

    companion object {

        /**
         * Creates a new instance of an [Agones SDK][AgonesSdk] with the best possible robustness and performance. This
         * method returns a new, independent instance with each invocation, that shared no connections or resources with
         * all instances that have been created before. In particular, it is guaranteed, that two invocations of this
         * method will create distinct objects. Each platform only needs a single instance of the SDK.
         *
         * @return A new instance of the [Agones SDK][AgonesSdk], that will be used for the communication with the
         * external interface of the Agones sidecar on this platform.
         */
        fun createNewSdk(): AgonesSdk {
            return GrpcAgonesSdk()
        }
    }
}
