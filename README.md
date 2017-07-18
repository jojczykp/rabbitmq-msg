# Rabbit MSG

Pet project for playing with RabbitMQ


# Prerequisites

Docker, Maven, Node.js


# Run

1. Build Consumer

``cd webapp``
``npm install amqplib``

2. Build Producer

``mvn clean package``

3. Start docker RabbitMQ image

``mvn docker:start``

4. Run consumer

``node consumer.js``

5. Run Producer

``java -jar target\rabbitmsg-1.0-SNAPSHOT.jar``

6. Stop docker RabbitMQ image

``mvn docker:stop``
