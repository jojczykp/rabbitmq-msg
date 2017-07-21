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

We are going to start:
* 2 producers for user1
* 2 producers for user2
* 2 consumers for user1
* 2 consumers for user2

We expect:
* Both user1 consumers receiving messages form both user1 producers only
* Both user2 consumers receiving messages form both user2 producers only


1. Start docker RabbitMQ image

    `mvn docker:start`

    To see admin console go to [http://localhost:15672/#/queues](http://localhost:15672/#/queues)


2. Consumer (in `consumer` folder)

    `cd consumer`
    
    1. Build
    
        `npm install`

    2. Run (each command in separate console)
    
        `node consumer.js user1 client11`
    
        `node consumer.js user1 client12`
    
        `node consumer.js user2 client21`
    
        `node consumer.js user2 client22`


3. Producer (in main folder)

    `[make sure in same folder as pom.xml]`
    
    1. Build

        `mvn clean package`

    2. Run (each command in separate console)

        `java -jar target\rabbitmq-msg-0.0.1.jar user1`
    
        `java -jar target\rabbitmq-msg-0.0.1.jar user1`

        `java -jar target\rabbitmq-msg-0.0.1.jar user2`
    
        `java -jar target\rabbitmq-msg-0.0.1.jar user2`


4. Stop docker RabbitMQ image

    `mvn docker:stop`
