#!/usr/bin/env node

'use strict;'

var mqtt = require('mqtt');


var args = process.argv.slice(2);
if (args.length < 2) {
    console.log('Usage: %s initialConsumerId finalConsumerId [numberOfInstancesPerConsumer]', basename(process.argv[1]));
    console.log('       Optional parameters default to 1');
    process.exit(1);
}

var host = 'localhost';
var port = 1883;
var initialConsumerId = parseInt(args[0]);
var finalConsumerId = parseInt(args[1]);
var numberOfInstancesPerConsumer = parseInt(args[2] || 1);

var authTokenPeriodMillis = 5 * 60 * 1000;

var url = 'mqtt://' + host + ':' + port;

console.log('Listening to ' + url);
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

    var client = mqtt.connect(url, {
        username: getUserDataStr(consumerId, instanceId),
        password: '[ignored]',
        clientId: consumerId + '-' + instanceId + '-',
        reconnectPeriod: 1000, // ms
        clean: false
    });


    client.on('connect', function () {
        console.log(logPrefix + 'Connected');
        client.subscribe(consumerId, { qos: 1 });
        console.log(logPrefix + 'Subscribed');
    });


    client.on('message', function (topic, message, packet) {
        var messageStr = message.toString();
        var oldCounter = received[messageStr] || 0;
        var newCounter = oldCounter + 1;
        received[messageStr] = newCounter;

        if (newCounter == numberOfInstances) {
            delete received[messageStr];
            console.log('Received for (consumer%d to consumer%d)*%d: %d*[%s]',
                   initialConsumerId, finalConsumerId, numberOfInstancesPerConsumer, numberOfInstances, messageStr);
        }
    });


    client.on('reconnect', function () {
        console.log(logPrefix + 'Reconnecting with new Access Token');
        client.options.username = getUserDataStr(consumerId, instanceId);
    });


    client.on('offline', function () {
        console.log(logPrefix + 'Offline');
    });


    client.on('close', function () {
        console.log(logPrefix + 'Disconnected');
    });


    client.on('error', function (error) {
        console.log(logPrefix + 'Error: ' + error);
    });
}


function getUserDataStr(consumerId, instanceId) {
    return instanceId + ',' + getAuthToken(consumerId);
}


function getAuthToken(consumerId) {
    var authTokenExpiryTimestamp = Date.now() + authTokenPeriodMillis;
    var authTokenData = consumerId + ',' + authTokenExpiryTimestamp;
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
