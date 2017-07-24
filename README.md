# RabbitMQ MSG

Pet project for playing with RabbitMQ.

https://www.rabbitmq.com/

Demonstrates usage of a single exchange to deliver messages from multiple producers to multiple consumers, for two different producer/consumer sets (user1, user2), using _routingKey_.

Host (_localhost_) and exchange name hardcoded in _.java_ and _.js_ files. 

Queue parameters (i.e. expiry time) defined on server (see _pom.xml_).

* RabbitMQ - Docker image
* Producer - Java
* Consumer - Node.js


# Running

We are going to start 2 producers and 2 consumers.

We expect:
* consumer1 receiving from producer1 and producer2
* consumer2 receiving from producer1 and producer2
* Other producers/consumers have no access permission (hardcoded in Auth Service)

1. Build

    `mvn clean package`


2. Start docker RabbitMQ and Auth Service images

    `mvn docker:start`

    To see admin console go to [http://localhost:15672/#/queues](http://localhost:15672/#/queues)


3. Producers (in main folder)

    Make sure you are in same folder as _pom.xml_.
    
    Run (each command in separate console)

    `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.producer.Producer producer1 consumer1`

    `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.producer.Producer producer1 consumer2`

    `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.producer.Producer producer2 consumer1`

    `java -cp target/rabbitmq-msg-0.0.1.jar pl.jojczykp.rabbitmq_msg.producer.Producer producer2 consumer2`


4. Consumers (in `consumer` folder)

    `cd consumer`
    
    1. Build
    
        `npm install`

    2. Run (each command in separate console)
    
        `node consumer.js consumer1 consumerInstance11`
    
        `node consumer.js consumer1 consumerInstance12`
    
        `node consumer.js consumer2 consumerInstance21`
    
        `node consumer.js consumer2 consumerInstance22`


5. Stop docker RabbitMQ image

    `mvn docker:stop`
