#!/usr/bin/env node

'use strict;'


const WebSocket = require('ws');
const amqp = require('amqp');


const listeningPort = 9000;
const rabbitHost = 'rabbitmq';
const rabbitPort = 5672;
const exchangeName = 'exchange.direct';

const authTokenPeriodMillis = 5 * 60 * 1000;


const args = process.argv.slice(2);
if (args.length < 1) {
    console.log('Usage: %s [proxyInstanceId]', basename(process.argv[1]));
    console.log('       Optional parameters default to 1');
    console.log('');
}


const proxyInstanceId = args[0] || 1;


console.log('Connecting to amqp://%s:%d', rabbitHost, rabbitPort);
console.log('Listening to incoming consumers on port %d', listeningPort);
console.log('To exit press CTRL+C');
console.log('- proxyInstanceId: ' + proxyInstanceId);
console.log('');


const rabbitConnection = amqp.createConnection({
    host: rabbitHost,
    port: rabbitPort,
    login: createProxyUserDataStr(proxyInstanceId),
    password: '[ignored]'
});


rabbitConnection.on('ready', () => {
    console.log('RabbitMQ connection ready');
});


rabbitConnection.on('close', () => {
    rabbitConnection.options.login = createProxyUserDataStr(proxyInstanceId);
    console.log('RabbitMQ connection closed, trying to reconnect with new access token');
});


rabbitConnection.on('error', (error) => {
    console.log('RabbitMQ connection error: ' + error);
});


const wss = new WebSocket.Server({ port: listeningPort });

wss.on('connection', (consumerWs) => {
    console.log('Incoming consumer connection');
    consumerWs.on('message', (userDataStr) => {
        try {
            setupConsumer(userDataStr, consumerWs);
        } catch (err) {
            console.log(err);
        }
    });
});


function setupConsumer(userDataStr, consumerWs) {
    [clientId, consumerId, consumerInstanceId, tokenTimestamp] = parseConsumerUserData(userDataStr);

    const logPrefix = 'AuthProxy ' + proxyInstanceId + ': For ' + clientId + '.' + consumerId + '.' + consumerInstanceId + ': ';
    const queueName = 'proxy-subscription-' + clientId + '-' + consumerId + '-' + consumerInstanceId;

    const queue = rabbitConnection.queue(queueName, { exclusive: false, autoDelete: false, durable: true }, (q) => {
        q.bind(exchangeName, consumerId);
        q.subscribe({ ack: true }, (message, headers, deliveryInfo, messageObject) => {
            forwardToConsumerOrClose(message, messageObject, q);
        });
        setTimeout(() => closeConsumerConnection('AuthToken expired'), tokenTimestamp - Date.now());
        console.log(logPrefix + 'Subscribed to ' + queueName);
    });


    function parseConsumerUserData(userDataStr) {
        const userData = userDataStr.split(",");
        if (userData.length < 2) {
            throw userDataStr + ': Incorrect User Data';
        }

        const consumerInstanceId = userData[0];
        const authTokenStr = base64Decode(userData.slice(1).join());
        const authToken = authTokenStr.split(" ");
        const authTokenMethod = authToken[0];

        if ("Bearer" !== authTokenMethod) {
            throw authTokenStr + ': Incorrect Auth Method: ' + authTokenMethod;
        }

        const authTokenDataStr = authToken[1];
        const authTokenData = authTokenDataStr.split(",");

        if (authTokenData.length < 4) {
            throw authTokenStr + ': Incorrect Auth Token: ' + authTokenDataStr;
        }

        const actualChecksumStr = authTokenData[3];
        const actualChecksum = parseInt(actualChecksumStr);

        if (isNaN(actualChecksum) || actualChecksum != checksum(substringBeforeLast(authTokenDataStr, ','))) {
            throw authTokenStr + ': Incorrect Auth Checksum: ' + actualChecksumStr;
        }

        [clientId, consumerId, tokenTimestampStr] = authTokenData;
        const tokenTimestamp = parseInt(tokenTimestampStr);

        if (isNaN(tokenTimestamp) || tokenTimestamp < Date.now()) {
            throw clientId + '.' + consumerId + '.' + consumerInstanceId + ': Token expired';
        }

        return [clientId, consumerId, consumerInstanceId, tokenTimestamp];
    }


    function forwardToConsumerOrClose(message, messageObject) {
        var messageStr = message.data.toString();

        console.log(logPrefix + 'Forwarding [%s]', messageStr);

        try {
            consumerWs.send(messageStr);
            messageObject.acknowledge();
        } catch (err) {
            console.log(logPrefix + 'Forwarding or ack error: ' + err);
            closeConsumerConnection('Forwarding or acknowledging error');
        }
    }


    function closeConsumerConnection(reason) {
        console.log(logPrefix + 'Closing RabbitMQ channel and ConsumerWS connection (%s)', reason);
        closeRabbitQueueChannel();
        closeConsumerWs()
    }


    function closeRabbitQueueChannel() {
        try {
            queue.close();
        } catch (err) {
            console.log(logPrefix + 'Closing RabbitMQ channel error: %s', err);
        }
    }


    function closeConsumerWs() {
        try {
            consumerWs.close();
        } catch (err) {
            console.log(logPrefix + 'Closing consumer connection error: %s', err);
        }
    }

}


function createProxyUserDataStr(proxyInstanceId) {
    var authTokenExpiryTimestamp = Date.now() + authTokenPeriodMillis;
    var authTokenData = 'proxy,proxyUser,' + authTokenExpiryTimestamp;
    var authTokenChecksum = checksum(authTokenData);

    return proxyInstanceId + ',' + base64Encode('Bearer ' + authTokenData + ',' + authTokenChecksum);
}


function checksum(data) { // fake :)
    return data.length;
}


function base64Encode(data) { // fake :)
    return '[' + data + ']';
}


function base64Decode(data) { // fake - remove leading '[' and tailing ']' :)
    return data.substring(1, data.length - 1);
}


function substringBeforeLast(str, ch) {
    return str.substring(0, str.lastIndexOf(ch));
}


function basename(path) {
    return path.split(/[\\/]/).pop();
}
