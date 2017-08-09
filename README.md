# RabbitMQ MSG

Pet project for playing with RabbitMQ.

https://www.rabbitmq.com/

Demonstrates usage of a single exchange to deliver messages from multiple producers to multiple consumers, for two different producer/consumer sets (consumer1, consumer2), using _routingKey_.

Host (_localhost_) and exchange name hardcoded in _.java_ and _.js_ files. 

Queue parameters (i.e. expiry time) defined on server (as policy in _rabbit_definitions.json_).

* RabbitMQ  - Docker image
* Producers - Java
* Consumers - Npm / Node.js

Hardcoded 'allow' credentials for producers/consumers names generated by client:
* producers: _producer*_
* consumers: _consumer*_

Other producers/consumers fail on access permissions.


# Running

1. Build

    `mvn clean package`


2. Start docker RabbitMQ and Auth Service images

    `mvn docker:start -Ddocker.follow`

    To see admin console go to [http://localhost:15672/#/queues](http://localhost:15672/#/queues) (admin/admin)


3. Producers (in main folder)

    Make sure you are in same folder as _pom.xml_.
    
    Run 2 producers:

    1. _producer1_ sending to consumer1 and consumer2:
    
        `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.Producer 1 1 1 2`

    2. _producer2_ sending to consumer2 only:
    
        `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.Producer 2 2 2 2`

4. Consumers (in `consumer` folder)

    `cd consumer_direct`
    
    1. Build
    
        `npm install`

    2. Run (each command in separate console)

        1. Single instance of _Consumer1_, consuming from both producers:
    
            `node consumersMqtt.js 1 1`
    
        2. Single instance of _Consumer2_, consuming from _producer2_ only:
    
            `node consumersMqtt.js 2 2`
    
        3. Single instance of _Consumer3_, not consuming at all (no producers producing for it):
    
            `node consumersMqtt.js 3 3`
    
5. Way of performance check

    * 2 producers producing for each consumer
    * 5000 consumers, 3 instances each
    
    1. Producers
    
        `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.Producer 1 2 1001 5000`
        
    2. Consumers
    
        `node consumersMqtt.js 1001 5000`
    
6. Real way of performance check

    Above performance test example is not reliable due to docker and single OS limits (number of file descriptors, open
    sockets, ...). To make sure test is reliable:
    
    - Run RabbitMQ in separate host
    - Run more clients, from different hosts
    - Run AuthService in separate host (comment out or redirect standard otput to /dev/null)
    - Above requires changing RabbitMQ host in producers/consumers and making sure that 'auth-service' in RabbitMQ
      points to IP hosting AuthService (or updating _rabbitmq.config_ properly)
    - Make sure OS parameters are tuned
      (i.e. [https://www.rabbitmq.com/networking.html](https://www.rabbitmq.com/networking.html))


# Some performance results

- Server
    - RabbitMQ 3.6.10 on VirtualBox with Ubuntu 17
    - 16GB of RAM available
    - 4 CPU cores available.
- Producers/Consumers
    - Fedora 26
    - 16GB of RAM available
    - 4 CPU cores available.

    Clients startup time measured while producer was down.
    
    Message length: 30 chars.

    `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rbitmq_msg.Producer 1 2 10001 12002`
    
1. AMQP consumer
    
    `node consumersAmqp.js 10001 12002 2`
    
    - Used ~9.3 GB of RAM
    - Client connections established in 2 mins (some failed and reconnected)
    - Managed to deliver all messages

2. MQTT consumer
    
    `node consumersMqtt.js 10001 12002 2`
    
    - Used ~8.0 GB of RAM
    - Client connections established in 25 secs (all went smoothly)
    - Managed to deliver all messages
    
   
3. MQTT over WS consumer

    // To Be Done

4. STOMP
    
    `node consumersStomp.js 10001 12002 2`
    
    - Used ~9.5 GB of RAM
    - Client connections established in 25 secs (went mostly smoothly)
    - Managed to deliver all messages
    
5. STOMP over WS consumer

    // To Be Done


Current winner seems to be MQTT :)
// To Be Done - ws:// versions os MQTT and STOMP.
// To Be Done - try to tune STOMP config
