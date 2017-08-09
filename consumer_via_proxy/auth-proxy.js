'use strict;'


const WebSocket = require('ws');
const amqp = require('amqp');


const listeningPort = 9000;
const rabbitHost = 'rabbitmq';
const rabbitPort = 5672;
const exchangeName = 'exchange.direct';


const wss = new WebSocket.Server({ port: listeningPort });


console.log('Waiting for consumer connections on port %d to forward them to %s:%s', listeningPort, rabbitHost, rabbitPort);


wss.on('connection', function connection(consumerWs) {
    console.log('Incoming connection');

    var started = false;

    consumerWs.on('message', function incoming(userDataStr) {
        console.log('Received from client: %s', userDataStr);

        if (started) {
            console.log('Client already started - skipping');
        } else {
            startRabbitClient(userDataStr, consumerWs);
        }
    });
});


function startRabbitClient(userDataStr, consumerWs) {
    //TODO extract from userDataStr, validate
    const consumerId = "consumer1";
    const instanceId = 1;

    const queueName = 'amqp.subscription.' + consumerId + '.' + instanceId;
    const logPrefix = userDataStr + ': ';

    console.log(logPrefix + 'Starting RabbitMQ AMQP client');

    const rabbitConnection = amqp.createConnection({
        host: rabbitHost,
        port: rabbitPort,
        login: userDataStr,
        password: '[ignored]'
    }, {
        reconnect: false
    });


    rabbitConnection.on('ready', function () {
        initialConnect = false;
        rabbitConnection.queue(queueName, { exclusive: false, autoDelete: false, durable: true }, function (q) {
            q.bind(exchangeName, consumerId);
            q.subscribe({ ack: true }, function (message, headers, deliveryInfo, messageObject) {
                onMessage(message, messageObject);
            });
            console.log(logPrefix + 'Subscribed');
        });
    });


    function onMessage(message, messageObject) {
        var messageStr = message.data.toString();

        console.log(logPrefix + 'Forwarding %s', messageStr);

        try {
            consumerWs.send(messageStr);
            messageObject.acknowledge();
        } catch (err) {
            console.log(logPrefix + 'Delivery failed: %s', err);
            closeBothConnections();
        }
    };


    rabbitConnection.on('close', function () {
        console.log(logPrefix + 'Connection to RabbitMQ closed');
        closeBothConnections();
    });


    rabbitConnection.on('error', function (error) {
        console.log(logPrefix + error.toString());
    });


    function closeBothConnections() {
        console.log(logPrefix + 'Disconnecting from RabbitMQ');
        rabbitConnection.disconnect();
        console.log(logPrefix + 'Disconnected from RabbitMQ');
        consumerWs.close();
        console.log(logPrefix + 'Disconnected from consumer');
    }
}
