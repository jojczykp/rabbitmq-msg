#!/usr/bin/env node

'use strict;'

var amqp = require('amqplib/callback_api');

var host = '192.168.99.100';
var exchangeName = 'sample-exchange';

var args = process.argv.slice(2);
if (args.length == 0) {
    console.log("Usage: consumer.js userId");
    process.exit(1);
}
var userId = args[0];

amqp.connect('amqp://' + host, function(err, conn) {
    conn.createChannel(function(err, ch) {
        ch.assertExchange(exchangeName, 'direct', {durable: false});

        ch.assertQueue('', {exclusive: true}, function(err, q) {
            console.log('Waiting for messages to %s@%s/%s. To exit press CTRL+C', userId, exchangeName, host);

            ch.bindQueue(q.queue, exchangeName, userId);

            ch.consume(q.queue, function(msg) {
                console.log("Received on %s@%s/%s: %s", msg.fields.routingKey, msg.fields.exchange, host, msg.content.toString());
            }, {noAck: true});
        });
    });
});
