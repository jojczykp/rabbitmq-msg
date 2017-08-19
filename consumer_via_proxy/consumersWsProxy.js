#!/usr/bin/env node

'use strict;'


const WebSocket = require('ws');


var args = process.argv.slice(2);
if (args.length < 2) {
    console.log('Usage: %s initialConsumerId finalConsumerId [numberOfInstancesPerConsumer]', basename(process.argv[1]));
    console.log('       Optional parameters default to 1');
    process.exit(1);
}


const url = 'ws://localhost:9000';
const initialConsumerId = parseInt(args[0]);
const finalConsumerId = parseInt(args[1]);
const numberOfInstancesPerConsumer = parseInt(args[2] || 1);

const reconnectPeriodMillis = 1000;

const authTokenPeriodMillis = 5 * 60 * 1000;


console.log('Connecting to ' + url);
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
        startInstance(consumerId, instanceId);
        numberOfInstances++;
    }
}


function startInstance(consumerId, instanceId) {
    const logPrefix = 'Consumer ' + consumerId + '.' + instanceId + ': ';

    const ws = new WebSocket(url);

    ws.on('open', function open() {
        ws.send(createUserDataStr('consumer' + consumerId, instanceId));
        console.log(logPrefix + 'Subscribed');
    });

    ws.on('message', function incoming(data) {
        var messageStr = data;
        var oldCounter = received[messageStr] || 0;
        var newCounter = oldCounter + 1;
        received[messageStr] = newCounter;

        if (newCounter == numberOfInstances) {
            delete received[messageStr];
            console.log('Received for (consumer%d to consumer%d)*%d: %d*[%s]',
                   initialConsumerId, finalConsumerId, numberOfInstancesPerConsumer, numberOfInstances, messageStr);
        }
    });

    ws.on('close', function incoming() {
        console.log(logPrefix + 'Reconnecting with new Access Token');
        setTimeout(() => startInstance(consumerId, instanceId), reconnectPeriodMillis);
    });

    ws.on('error', function incoming(error) {
        console.log(error.toString());
    });
}


function createUserDataStr(consumerId, instanceId) {
    var authTokenExpiryTimestamp = Date.now() + authTokenPeriodMillis;
    var authTokenData = 'consumer,' + consumerId + ',' + authTokenExpiryTimestamp;
    var authTokenChecksum = checksum(authTokenData);

    return instanceId + ',' + base64Encode('Bearer ' + authTokenData + ',' + authTokenChecksum);
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
