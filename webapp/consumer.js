'use strict;'

var queueName = 'sample-queue';

var open = require('amqplib').connect('amqp://192.168.99.100');

open.then(function(conn) {
  return conn.createChannel();
}).then(function(ch) {
  return ch.assertQueue(queueName, {durable: false}).then(function(ok) {
    return ch.consume(queueName, function(msg) {
      if (msg !== null) {
        console.log(msg.content.toString());
        ch.ack(msg);
      }
    });
  });
}).catch(console.warn);
