# RabbitMQ MSG

Pet project for playing with RabbitMQ.

https://www.rabbitmq.com/

https://rabbitmq.docs.pivotal.io/36/

Demonstrates communication between Producers and Consumers via with RabbitMQ with AuthProxy.

## Communication Architecture

1. Producers send messages to RabbitMQ with AMQP
2. RabbitMQ routes them to proper queues (queue per Consumer instance) using _consumerId_ as a _routingKey_
3. Consumers connect to AuthProxy via WebSockets
4. AuthProxy reads from queues with AMQP and forwards to proper Consumers.

    ![Architecture](/doc/architecture.png)

## Features

* Short time buffering.

  By queue expiry time (defined with policy in _rabbit_definitions.json_).
  
  RabbitMQ waits for a while before dropping queue after Consumer disconnected, giving a chance
  to reconnect and consume messages delivered in the meantime.

* Security.

  AuthProxy validates Consumer access token. Also drops connection when token expired.


# Running steps


## Prerequisites

* Java 8
* Maven (verified with 3.3.9)
* npm (verified with 3.10.10)
* node.js (verified with 6.11.1)
* Docker (verified with 1.13.1)
    
## RabbitMQ and AuthProxy

1. Let's install Javascript dependencies needed for AuthProxy:
 
    `cd auth-proxy`
    
    `npm install`
    
    `cd ..`

2. Let's start docker containers, keeping their output tailed (_-Ddocker.follow_):
    
    `mvn docker:start -Ddocker.follow`

    We should notice RabbitMQ and AuthProxy containers started
       
## Consumers

1. Let's install Javascript dependencies needed for Consumers:

    `cd consumers`
    
    `npm install`
    
2. Let's start 3 Consumers (ids: 101, 102, 103), 2 instances each (so 6 Consumer instances total):
    
    `node ./consumers.js 101 103 2`

## Producers

1. Let's compile Producers code:
    
    `mvn clean package`
    
2. Let's start 2 Producers (ids: 201, 202), each generating messages for all Consumers (101, 102, 103):
    
    `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.Producers 201 202 101 103`

## Expected messages logs

* Each Producer should periodically log fact of sending same message to all Consumers:
    
    ```
    Sending to (consumer101 to consumer103): [producer202.20475 says Hello 1] 
    Sending to (consumer101 to consumer103): [producer201.20548 says Hello 1] 
    Confirming
    Confirming
    OK
    OK
    ```
        
* For each Producer log, AuthProxy should print 6 corresponding forwarding logs (12 total):
    
    ```
    AuthProxy> AuthProxy 1: For consumer.consumer101.1: Forwarding [producer201.20902 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer103.1: Forwarding [producer201.20902 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer102.2: Forwarding [producer201.20902 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer101.2: Forwarding [producer201.20902 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer103.2: Forwarding [producer201.20902 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer102.1: Forwarding [producer201.20902 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer101.1: Forwarding [producer202.20118 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer103.1: Forwarding [producer202.20118 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer102.2: Forwarding [producer202.20118 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer103.2: Forwarding [producer202.20118 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer101.2: Forwarding [producer202.20118 says Hello 8]
    AuthProxy> AuthProxy 1: For consumer.consumer102.1: Forwarding [producer202.20118 says Hello 8]
    ```
        
* Each Consumer should log messages received from each Producer:
    
    ```
    Received for (consumer101 to consumer103)*2: 6*[producer201.20902 says Hello 8]
    Received for (consumer101 to consumer103)*2: 6*[producer202.20118 says Hello 8]
    ```
    
        
# Performance
    
I conducted a few simple performance tests to understand capacity of proposed configuration.

Following configuration was used:
* Fedora 26 on 8 Cores i7-6700HQ CPU @ 2.60GHz
* 1 RabbitMQ instance (no clustering) in 4-CPU, 12GB RAM VirtualBox
* 1 AuthProxy
* 1 Producer
* 40000 Consumers, 1 instance each
    
A few code changes were required:
* Commenting out sleep in Producer, so that it can produce messages as fast as RabbitMQ can consume
* Tweaking kernel parameters, so that it is possible to open 40000 TCP Consumer connections
* Changing RabbitMQ host in AuthProxy and Producer to point to VirtualBox
    
In all cases all messages were correctly delivered to all clients.

In all cases RabbitMQ CPU seem to be bottleneck.

## Durable queue, but transient messages

Average results:         
- Client connections established in 12 secs)
- Used ~7.7 GB of RAM
- Average throughput: 5000 msgs/sec
    
![Transient - Screen](/doc/test_transient.png) 

## Durable queue, persistent messages
         
Average results:
- Client connections established in 20 secs)
- Used ~8.1 GB of RAM
- Average throughput: 3500 msgs/sec
    
![Persistent - Screen](/doc/test_persistent.png) 


# Potential extensions

* Use wss:// (instead of ws://) between Consumer and AuthProxy
* Extend protocol between Consumer and AuthProxy by introducing ACKs (that can be kept in sync with ACKs sent from
  AuthProxy to RabbitMQ).
* ...
