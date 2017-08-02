#!/usr/bin/node

'use strict;'

// https://github.com/mqttjs/MQTT.js
// https://www.npmjs.com/package/mqtt

var mqtt = require('mqtt');


var args = process.argv.slice(2);
if (args.length != 2) {
    console.log('Usage: consumer.js userId instanceId');
    process.exit(1);
}

var host = 'localhost';
var port = 15675;
var userId = args[0];
var instanceId = args[1];

var authTokenPeriodMillis = 60 * 1000;

var url  = 'ws://' + host + ':' + port + '/ws';


var client = mqtt.connect(url, {
    username: getUserDataStr(),
    password: '[ignored]',
    clientId: userId + '-' + instanceId + '-',
    reconnectPeriod: 1000, // ms
    clean: false // no immediate auto-delete queue
});


client.on('connect', function () {
    console.log('Connected');
    client.subscribe(userId, { qos: 1 });
    console.log('Subscribed');
});


client.on('reconnect', function () {
    console.log('Reconnected - Refreshing Access Token');
    client.options.username = getUserDataStr();
});


client.on('close', function () {
    console.log('Disconnected');
});


client.on('offline', function () {
    console.log('Offline');
});


client.on('message', function (topic, message, packet) {
    console.log('Received message: ' + message.toString());
});


client.on('error', function (error) {
    console.log('Error: ' + error);
});


//client.on('packetreceive', function (packet) {
//    console.log('Received: ' + packet);
//});


function getUserDataStr() {
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
