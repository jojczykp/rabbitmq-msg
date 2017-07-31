#!/usr/bin/env node

'use strict;'

var amqp = require('amqp'); //https://github.com/postwait/node-amqp


var args = process.argv.slice(2);
if (args.length != 3) {
    console.log('Usage: consumer.js consumerId initialInstanceId connectionsNumber');
    process.exit(1);
}

var host = 'localhost';
var port = 5672;
var exchangeName = 'sample.exchange';
var userId = args[0];
var initialInstanceId = args[1];
var connectionsNumber = args[2];

var authTokenPeriodMillis = 5 * 60 * 1000;


console.log('connections number: ' + connectionsNumber);

for (i = 0; i < connectionsNumber; i++) {
    startConnection(initialInstanceId + i);
}


function startConnection(instanceId) {
    var queueName = userId + '.' + instanceId;

    var connection = amqp.createConnection(
    {
        host: host,
        port: port,
        login: getUserDataStr(userId, instanceId),
        password: ''
    }, {
        reconnect: true
    });


    var initialConnect = true;
    connection.on('ready', function () {
        if (initialConnect) {
            initialConnect = false;
            connection.queue(queueName, { exclusive: false, autoDelete: false, durable: true }, function (q) {
//                console.log('%s.%s: Waiting for messages to %s@%s/%s. To exit press CTRL+C', userId, instanceId, userId, exchangeName, host);
                q.bind(exchangeName, userId, function (q) {
//                    console.log('%s.%s: Connection to %s@%s established', userId, instanceId, q.name, host);
                });
                q.subscribe({ ack: true }, function (msg, headers, deliveryInfo, messageObject) {
//                    console.log(msg.data.toString());
                    messageObject.acknowledge();
                });
            });
        }
    });


    connection.on('close', function () {
        connection.options.login = getUserDataStr(userId, instanceId);
//        console.log('%s.%s: Connection closed - reconnecting', userId, instanceId)
    });
}

function getUserDataStr(userId, instanceId) {
    return instanceId + ',' + getAuthToken(userId);
}


function getAuthToken() {
    var authTokenExpiryTimestamp = Date.now() + authTokenPeriodMillis;
    var authTokenData = userId + ',' + authTokenExpiryTimestamp;
    var authTokenChecksum = checksum(authTokenData);

    return base64Encode('Bearer ' + authTokenData + ',' + authTokenChecksum);
}


function checksum(data) { // fake :)
    return data.length;
}


function base64Encode(data) { // fake :)
    return '[' + data + ']';
}
