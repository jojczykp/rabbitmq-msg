'use strict;'

var host = '192.168.99.100';
var queueName = 'sample-queue';

var open = require('amqplib').connect('amqp://' + host);

console.log('Listening to queue ' + queueName + ' on host ' + host);

open.then(function(conn) {
  return conn.createChannel();
}).then(function(ch) {
  return ch.assertQueue(queueName, {durable: false}).then(function(ok) {
    return ch.consume(queueName, function(msg) {
      if (msg !== null) {
        console.log('Received: ' + msg.content.toString());
        ch.ack(msg);
      }
    });
  });
}).catch(console.warn);
