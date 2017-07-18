# Rabbit MSG

Pet project for playing with RabbitMQ


# Prerequisites

Docker, Maven, Node.js


# Run

1. Start docker RabbitMQ image

``mvn docker:start``

2. Consumer (`consumer` folder)
    1. Build
    
    `npm install amqplib`

    2. Run

    `node consumer`

3. Producer (main folder)

    1. Build

    `mvn clean package`

    2. Run

    `java -jar target\rabbitmsg-1.0-SNAPSHOT.jar`

4. Stop docker RabbitMQ image

`mvn docker:stop`
