# Agones Client SDK (Java)

The Agones Client SDK provides the interface for lifecycle management, metadata and player tracking of the individual
GameServer instances within Agones. The different interfaces need to be used in order to inform the controller of Agones
about the current state of this instance. Each instance has its own endpoint for the receiving of API calls (gRPC and
REST), that is launched next to the container of the instance within the same pod. The client SDK stays in contact with
that Endpoint throughout the whole lifetime of the instance.

We needed to implement our own Agones Client SDK because there is no official SDK and the existing alternatives are all
sparsely documented (at best) and some mandatory methods from the SDK specification are missing. Therefore, we started
implementing our own solution on-top of the Protobuf definitions that Agones already provides. We'll try to update the
Agones mappings every time that there is an API change.
