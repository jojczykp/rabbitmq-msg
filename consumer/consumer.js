#!/usr/bin/env node

'use strict;'

var amqp = require('amqplib/callback_api');

var host = 'localhost';
var exchangeName = 'sample-exchange';

var args = process.argv.slice(2);
if (args.length != 2) {
    console.log('Usage: consumer.js userId clientId');
    process.exit(1);
}
var userId = args[0];
var clientId = args[1];

amqp.connect('amqp://' + host, function(err, conn) {
    conn.createChannel(function(err, ch) {
        ch.assertExchange(exchangeName, 'direct', {durable: true});

        var queueName = userId + '.' + clientId;

        // http://www.squaremobius.net/amqp.node/channel_api.html#channel_assertQueue
        var queueProps = {
            durable: true,
            arguments: {
                'x-expires': 60 * 1000, // how long (ms) queue waits for client to reconnect before removed
                'x-max-length': 20      // on overflow oldest dropped
            }
        }

        ch.assertQueue(queueName, queueProps, function(err, q) {
            console.log('%s: Waiting for messages to %s@%s/%s. To exit press CTRL+C', clientId, userId, exchangeName, host);

            ch.bindQueue(q.queue, exchangeName, userId);

            ch.consume(q.queue, function(msg) {
                console.log('%s: Received on %s@%s/%s: %s', clientId, msg.fields.routingKey, msg.fields.exchange, host, msg.content.toString());
            }, {noAck: true});
        });
    });
});
