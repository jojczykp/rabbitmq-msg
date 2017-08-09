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
    const authTokenExtracts = authTokenExtractAndValidate(userDataStr);
    if (authTokenExtracts == null) {
        console.log(userDataStr + ': Disconnecting consumer');
        consumerWs.close();
        console.log(userDataStr + ': Disconnected');
        return;
    }

    consumerId = authTokenExtracts.consumerId;
    instanceId = authTokenExtracts.instanceId;
    tokenTimestamp = authTokenExtracts.tokenTimestamp;
    setTimeout(() => closeBothConnections('Access Token Expired'), tokenTimestamp - Date.now())

    const logPrefix = consumerId + '.' + instanceId + ': ';
    const queueName = 'ws-proxy-subscription-' + consumerId + '-' + instanceId;

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
            closeBothConnections('Delivery failed: ' + err.toString());
        }
    };


    rabbitConnection.on('close', function () {
        closeBothConnections('Connection to RabbitMQ closed');
    });


    rabbitConnection.on('error', function (error) {
        console.log(logPrefix + error.toString());
    });


    function closeBothConnections(reason) {
        console.log(logPrefix + reason);
        console.log(logPrefix + 'Disconnecting from RabbitMQ');
        rabbitConnection.disconnect();
        console.log(logPrefix + 'Disconnected from RabbitMQ');
        consumerWs.close();
        console.log(logPrefix + 'Disconnected from consumer');
    }
}


function authTokenExtractAndValidate(userDataStr) {
    const userData = userDataStr.split(",");
    if (userData.length < 2) {
        console.log("Wrong User Data: %s", userDataStr);
        return null;
    }

    const instanceId = userData[0];
    const authTokenStr = base64Decode(userData.slice(1).join());
    const authToken = authTokenStr.split(" ");
    const authTokenMethod = authToken[0];

    if ("Bearer" !== authTokenMethod) {
        console.log("'%s': Wrong Auth Method: %s", authTokenStr, authTokenMethod);
        return null;
    }

    const authTokenDataStr = authToken[1];
    const authTokenData = authTokenDataStr.split(",");

    if (authTokenData.length < 3) {
        console.log("'%s': Wrong Auth Token: %s", authTokenStr, authTokenDataStr);
        return null;
    }

    const actualChecksumStr = authTokenData[2];
    const actualChecksum = parseInt(actualChecksumStr);

    if (isNaN(actualChecksum) || actualChecksum != checksum(substringBeforeLast(authTokenDataStr, ','))) {
        console.log("'%s': Wrong Auth Checksum: %s", authTokenStr, actualChecksumStr);
        return null;
    }

    const consumerId = authTokenData[0];
    const tokenTimestampStr = authTokenData[1];
    const tokenTimestamp = parseInt(tokenTimestampStr);

    if (isNaN(tokenTimestamp) || tokenTimestamp < Date.now()) {
        console.log("%s.%s: Token expired", consumerId, instanceId);
        return null;
    }

    return {
        consumerId: consumerId,
        instanceId: instanceId,
        tokenTimestamp: tokenTimestamp
    };
}


function substringBeforeLast(str, ch) {
    return str.substring(0, str.lastIndexOf(ch));
}


function base64Decode(data) { // fake - remove leading '[' and tailing ']' :)
    return data.substring(1, data.length - 1);
}


function checksum(data) { // fake
    return data.length;
}
