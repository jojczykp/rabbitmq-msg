#!/usr/bin/env node

'use strict;'

var stomp = require('stompjs');


var args = process.argv.slice(2);
if (args.length < 2) {
    console.log('Usage: %s initialConsumerId finalConsumerId [numberOfInstancesPerConsumer]', basename(process.argv[1]));
    console.log('       Optional parameters default to 1');
    process.exit(1);
}

var host = 'localhost';
var port = 61613;
//var port = 15674;
var reconnectPeriod = 1000; // ms
var exchangeName = 'exchange.direct';
var initialConsumerId = parseInt(args[0]);
var finalConsumerId = parseInt(args[1]);
var numberOfInstancesPerConsumer = parseInt(args[2] || 1);


var authTokenPeriodMillis = 5 * 60 * 1000;

console.log('Connecting to %s:%d', host, port);
console.log('To exit press CTRL+C');
console.log('- initialConsumerId: ' + initialConsumerId);
console.log('- finalConsumerId:   ' + finalConsumerId);
console.log('- numberOfConsumers: ' + (finalConsumerId - initialConsumerId + 1));
console.log('- numberOfInstancesPerConsumer: ' + numberOfInstancesPerConsumer);
console.log('');

var received = {};

var numberOfInstances = 0;
for (var consumerId = initialConsumerId ; consumerId <= finalConsumerId ; consumerId++) {
    for (var instanceId = 1 ; instanceId <= numberOfInstancesPerConsumer ; instanceId++) {
        startInstance('consumer' + consumerId, instanceId);
        numberOfInstances++;
    }
}

function startInstance(consumerId, instanceId) {
    var logPrefix = consumerId + '.' + instanceId + ': ';
    var reconnecting = false;
    var client;

    connect();


    function connect() {
        client = stomp.overTCP(host, port);
//        var client = stomp.overWS('ws://' + host + ':' + port + '/stomp');
//        client.debug = onDebug;
        client.connect(
            getUserDataStr(consumerId, instanceId),
            '[ignored]',
            onConnected,
            onError
        );
    }


    function onError(error) {
        if (reconnecting) {
            return;
        }

        console.log(logPrefix + 'Error: ' + error);
        console.log(logPrefix + 'Reconnecting with new Access Token');

        reconnecting = true;

        setTimeout(() => {
            reconnecting = false;
            connect();
        }, reconnectPeriod);
    }


    function onConnected() {
        client.subscribe(
            '/exchange/' + exchangeName + '/' + consumerId,
            onMessage,
            {
                'x-queue-name': 'stomp-subscription-' + consumerId + '-' + instanceId,
                'auto-delete': false,
                'durable': true,
                'ack': 'client-individual',
                'id': 'qpa'
            });

        console.log(logPrefix + 'Subscribed');
        reconnecting = false;
    }


    function onMessage(messageObject) {
        var messageStr = messageObject.body;
        var oldCounter = received[messageStr] || 0;
        var newCounter = oldCounter + 1;
        received[messageStr] = newCounter;

        if (newCounter == numberOfInstances) {
            delete received[messageStr];
            console.log('Received for (consumer%d to consumer%d)*%d: %d*[%s]',
                   initialConsumerId, finalConsumerId, numberOfInstancesPerConsumer, numberOfInstances, messageStr);
        }

        messageObject.ack();
    }


//    function onDebug(message) {
//        console.log(logPrefix + 'Debug: ' + message);
//    }
}


function getUserDataStr(consumerId, instanceId) {
    return instanceId + ',' + getAuthToken(consumerId);
}


function getAuthToken(consumerId) {
    var authTokenExpiryTimestamp = Date.now() + authTokenPeriodMillis;
    var authTokenData = 'consumer,' + consumerId + ',' + authTokenExpiryTimestamp;
    var authTokenChecksum = checksum(authTokenData);

    return base64Encode('Bearer ' + authTokenData + ',' + authTokenChecksum);
}


function checksum(data) { // fake :)
    return data.length;
}


function base64Encode(data) { // fake :)
    return '[' + data + ']';
}


function basename(path) {
    return path.split(/[\\/]/).pop();
}
