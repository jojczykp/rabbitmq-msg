# RabbitMQ MSG

Pet project for playing with RabbitMQ.

https://www.rabbitmq.com/

Demonstrates usage of a single exchange to deliver messages from multiple producers to multiple consumers, for two different producer/consumer sets (user1, user2), using _routingKey_.

Host (_localhost_) and exchange name hardcoded in _.java_ and _.js_ files. 

Queue parameters (i.e. expiry time) defined on server (see _rabbit_definitions.json_).

* RabbitMQ - Docker image
* Producer - Java
* Consumer - Node.js

Hardcoded 'allow' credentials for:
* producer1, producer2
* consumer1, consumer2, consumer3

Other producers/consumers fails on access permissions.


# Running

1. Build

    `mvn clean package`


2. Start docker RabbitMQ and Auth Service images

    `mvn docker:start`

    To see admin console go to [http://localhost:15672/#/queues](http://localhost:15672/#/queues) (admin/admin)


3. Producers (in main folder)

    Make sure you are in same folder as _pom.xml_.
    
    Run 2 producers:

    1.  Sending to consumer1 and consumer2:
    
        `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.producer.Producer producer1 consumer1 consumer2`

    2. Sending to consumer1 only:
    
        `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.producer.Producer producer2 consumer1`

    3. Failing on access denied (only producer{1,2} have permission):

        `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.producer.Producer producer3 consumer1`
        
4. Consumers (in `consumer` folder)

    `cd consumer`
    
    1. Build
    
        `npm install`

    2. Run (each command in separate console)

        1. Consuming from both producers:
    
            `node consumer.js consumer1 consumerInstance11`
    
            `node consumer.js consumer1 consumerInstance12`

        2. Consuming from producer1 only (since producer2 does not send to consumer2):

            `node consumer.js consumer2 consumerInstance21`
    
            `node consumer.js consumer2 consumerInstance22`

        3. Not consuming at all (no producer sending to consumer3):

            `node consumer.js consumer3 consumerInstance31`

        4. Failing with access denied (only consumer{1,2,3} have permission):

            `node consumer.js consumer4 consumerInstance41`


5. Stop docker RabbitMQ image

    `mvn docker:stop`
