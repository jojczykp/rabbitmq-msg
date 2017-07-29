#!/usr/bin/env node

'use strict;'

var amqp = require('amqp'); //https://github.com/postwait/node-amqp


var args = process.argv.slice(2);
if (args.length != 2) {
    console.log('Usage: consumer.js userId instanceId');
    process.exit(1);
}

var host = 'localhost';
var port = 5672;
var exchangeName = 'sample-exchange';
var userId = args[0];
var instanceId = args[1];

var timestampPeriodMillis = 15 * 60 * 1000;

var connection = amqp.createConnection(
{
    host: host,
    port: port,
    login: instanceId + ',' + getAuthToken(userId), // TODO update on reconnect
    password: ''
}, {
    reconnect: true
});


connection.on('ready', function () {
    var queueName = userId + '.' + instanceId;
    connection.queue(queueName, { exclusive: false, autoDelete: false },  function (q) {
        console.log('%s.%s: Waiting for messages to %s@%s/%s. To exit press CTRL+C', userId, instanceId, userId, exchangeName, host);
        q.bind(exchangeName, userId, function (q) {
            console.log('%s.%s: Connection to %s@%s established', userId, instanceId, q.name, host);
        });
        q.subscribe(function (msg) {
            console.log('%s.%s: Received on %s@%s/%s: %s', userId, instanceId, userId, exchangeName, host, msg.data.toString());
        });
    });
});


function getAuthToken() {
    var authTokenExpiryTimestamp = Date.now() + timestampPeriodMillis;
    var authTokenData = userId + ',' + authTokenExpiryTimestamp;
    var authTokenChecksum = 123;

    return base64Encode('Bearer ' + authTokenData + ',' + authTokenChecksum);
}


function base64Encode(data) { // fake :)
    return '[' + data + ']';
}