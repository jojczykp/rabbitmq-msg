#!/usr/bin/env node

'use strict;'

var amqp = require('amqp');


var args = process.argv.slice(2);
if (args.length < 2) {
    console.log('Usage: %s initialConsumerId finalConsumerId [numberOfInstancesPerConsumer]', basename(process.argv[1]));
    console.log('       Optional parameters default to 1');
    process.exit(1);
}

var host = 'localhost';
var port = 5672;
var exchangeName = 'exchange.direct';
var initialConsumerId = parseInt(args[0]);
var finalConsumerId = parseInt(args[1]);
var numberOfInstancesPerConsumer = parseInt(args[2] || 1);


var authTokenPeriodMillis = 5 * 60 * 1000;

console.log('Connecting to amqp://' + host + ':' + port);
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
    var queueName = 'amqp.subscription.' + consumerId + '.' + instanceId;
    var logPrefix = consumerId + '.' + instanceId + ': ';

    var connection = amqp.createConnection( {
        host: host,
        port: port,
        login: getUserDataStr(consumerId, instanceId),
        password: ''
    }, {
        reconnect: true
    });


    var initialConnect = true;
    connection.on('ready', function () {
        if (initialConnect) {
            initialConnect = false;
            connection.queue(queueName, { exclusive: false, autoDelete: false, durable: true }, function (q) {
                q.bind(exchangeName, consumerId);
                q.subscribe({ ack: true }, function (message, headers, deliveryInfo, messageObject) {
                    onMessage(message);
                    messageObject.acknowledge();
                });
                console.log(logPrefix + 'Subscribed');
            });
        }
    });


    function onMessage(message) {
        var messageStr = message.data.toString();
        var oldCounter = received[messageStr] || 0;
        var newCounter = oldCounter + 1;
        received[messageStr] = newCounter;

        if (newCounter == numberOfInstances) {
            delete received[messageStr];
            console.log('Received for (consumer%d to consumer%d)*%d: %d*[%s]',
                   initialConsumerId, finalConsumerId, numberOfInstancesPerConsumer, numberOfInstances, messageStr);
        }
    };


    connection.on('close', function () {
        console.log(logPrefix + 'Reconnecting with new Access Token');
        connection.options.login = getUserDataStr(consumerId, instanceId);
    });


    connection.on('error', function (error) {
        console.log(logPrefix + 'Error: ' + error);
    });
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
