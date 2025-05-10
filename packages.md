# Module Agones Client SDK

This Agones Client SDK can be used to communicate with the Sidecar SDK Container of a gameserver that's deployed through
Agones. This SDK provides all the communication endpoints offered by the Sidecar and follows the official implementation
guidelines. Some small adjustments have been made to make it feel more native to the Kotlin/Java ecosystem and to
generally improve the usability within the language constraints.

The interface is defined independently of the different implementations to decouple the internal behaviors and params
that the implementations require from the overall functionality of the Agones SDK. The basic exception type is a gRPC
StatusException, which cannot be cleanly mapped into more distinct types because of the poor responses of the Agones
SDK (which also varies between production and the test SDK container).

# Package net.scrayos.agones.client

The main package of the SDK implementation that hosts the overall interface as well as the concrete implementations.
This package can be used to instantiate the desired implementation and to start the communication with the Sidecar
container.

