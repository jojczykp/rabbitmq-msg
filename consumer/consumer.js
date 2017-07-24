#!/usr/bin/env node

'use strict;'

var amqp = require('amqplib/callback_api');

var host = 'localhost';
var exchangeName = 'sample-exchange';

var args = process.argv.slice(2);
if (args.length != 1) {
    console.log('Usage: consumer.js consumerId');
    process.exit(1);
}
var consumerId = args[0];
var clientInstanceId = getRandomInt(100, 999);

var authTokenTimestamp = 12345;
var authTokenData = consumerId + ',' + clientInstanceId + ',' + authTokenTimestamp;
var authTokenChecksum = 123;
var authToken = 'Bearer ' + authTokenData + ',' + authTokenChecksum;

// https://www.rabbitmq.com/uri-spec.html
// authToken as user name, no password (same way as in 'immature' OAuth2 plugin)
amqp.connect('amqp://' + authToken + '@' + host, function(err, conn) {
    conn.createChannel(function(err, ch) {
        var queueName = consumerId + '.' + clientInstanceId;
        var queueProps = null; // Props come from policy defined on server
        ch.assertQueue(queueName, queueProps, function(err, q) {
            console.log('%s.%s: Waiting for messages to %s@%s/%s. To exit press CTRL+C', consumerId, clientInstanceId, consumerId, exchangeName, host);
            ch.bindQueue(q.queue, exchangeName, consumerId);
            ch.consume(q.queue, function(msg) {
                console.log('%s.%s: Received on %s@%s/%s: %s', consumerId, clientInstanceId, msg.fields.routingKey, msg.fields.exchange, host, msg.content.toString());
                ch.ack(msg);
            }, {noAck: false});
        });
    });
});

function getRandomInt(min, max) {
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}
