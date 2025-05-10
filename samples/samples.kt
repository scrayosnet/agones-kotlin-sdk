fun grpcSample() {
    // host and port are supplied by the default environment variables
    val sdk = GrpcAgonesSdk()

    // any request can be performed on the sdk while it is open
    sdk.ready()
}

fun healthSample() {
    // host and port are supplied by the default environment variables
    val sdk = GrpcAgonesSdk()

    // create a flow
    val healthFlow = MutableSharedFlow<Unit>()

    // bind the flow to the health endpoint (async)
    launch {
        sdk.health(healthFlow)
    }

    // emit health pings
    healthFlow.emit(Unit)
}

fun watchGameServerSample() {
    // host and port are supplied by the default environment variables
    val sdk = GrpcAgonesSdk()

    // watch the game server and act on updates until a condition is met
    sdk.watchGameServer()
        .takeWhile { !initialized }
        .collect { // do something }
        }
