# Orchestration Protocol
Connected processes in this project communicate through this 'orchestration protocol' which is supposed
to be a simple 'broadcast' protocol. All clients will be able to send messages and receive all other messages.

The protocol is kept simple, implementation is done based upon the JDK and without any extra dependencies that
 allow the java agent to host the server without polluting the System Classpath or shading.

## Connection Handshake
The protocol 'magic number' is `24111602` 

```text
Client                                                   Server
──────                                                   ──────
                      ┌───────────────┐                        
        ──────────────│  Magic Number │───────────────►        
                      └───────────────┘                        
                                                               
                   ┌──────────────────────┐                    
        ───────────│   Protocol Version   │───────────►        
                   └──────────────────────┘                    
                                                               
                                                                                                             
                       ┌───────────────┐                       
        ◄──────────────│  Magic Number │───────────────        
                       └───────────────┘                       
                                                               
                    ┌──────────────────────┐                   
        ◄───────────│   Protocol Version   │───────────        
                    └──────────────────────┘                   
                                                                                                                                     
                                                               
                   ┌──────────────────────┐                    
        ───────────│     Introduction     │───────────►        
                   └──────────────────────┘                    
                                                               
                                                               
                    ┌──────────────────────┐                   
        ◄───────────│   ClientConnected    │───────────        
                    └──────────────────────┘                   
```


## Packages / Frames
Each package is formatted in this frame
```text
┌──────────────────┬────────────────────────────┬────────────────────────┐
│int(Message Type) │ int(Message Size in Bytes) │ bytes[](Message Binary)│
└──────────────────┴────────────────────────────┴────────────────────────┘
```


## Message Format
Messages sent and received by clients implement `org.jetbrains.compose.reload.orchestration.OrchestrationMessage`
Each message is sent as frame, typically using java.io.Serializable as message binary format.
Message binaries therefore include the information about which exact type of `OrchestrationMessage` it represents.
Lenient serialization (e.g., by using a constant serialVersionUID) is therefore desired.

### Encoded Messages
Starting from protocol version 1.4, custom encoded messages are supported. 
Each message therefore is defined by a given encoder. The frame of such an encoded message is defined
as 
```text
┌──────────────────────┬───────────────────┬───────────────────┐     
│ short(Schema Version)│int(MessageId size)│ byte[](MessageId) │..   
└──────────────────────┴───────────────────┴───────────────────┘     
┌───────────────────────────────────┬───────────────────────────────┐
│String(MessageClassifier.namespace)│ String(MessageClassifier.type)│
└───────────────────────────────────┴───────────────────────────────┘
┌─────────────────┬───────────────┐                                  
│int(Payload Size)│byte[](Payload)│                                  
└─────────────────┴───────────────┘                                  
```
where the 'Payload' is the encoded message itself.


## Message Ack
Each message, once received by the server, will get an 'Ack' response
```text
Client                                                   Server
──────                                                   ──────
                    ┌───────────────────────┐                  
        ────────────│  OrchestrationMessage │─────────►        
                    └───────────────────────┘                  
                                                               
                       ┌───────────────┐                       
        ◄──────────────│      Ack      │───────────────        
                       └───────────────┘                       
```
