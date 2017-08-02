#!/usr/bin/env node

'use strict;'

var mqtt = require('mqtt');


var args = process.argv.slice(2);
if (args.length != 3) {
    console.log('Usage: consumer.js consumerId initialInstanceId connectionsNumber');
    process.exit(1);
}

var host = 'localhost';
var port = 15675;
var userId = args[0];
var initialInstanceId = args[1];
var connectionsNumber = args[2];

var authTokenPeriodMillis = 5 * 60 * 1000;

var url  = 'ws://' + host + ':' + port + '/ws';


console.log('connections number: ' + connectionsNumber);

for (i = 0; i < connectionsNumber; i++) {
    startConnection(initialInstanceId + i);
}


function startConnection(instanceId) {

    var client = mqtt.connect(url, {
        username: getUserDataStr(userId, instanceId),
        password: '[ignored]',
        clientId: userId + '-' + instanceId + '-',
        reconnectPeriod: 1000, // ms
        clean: false
    });


    client.on('connect', function () {
        console.log('Connected');
        client.subscribe(userId, { qos: 1 });
        console.log('Subscribed');
    });


    client.on('reconnect', function () {
        console.log('Reconnected - Refreshing Access Token');
        client.options.username = getUserDataStr(userId, instanceId);
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
}

function getUserDataStr(userId, instanceId) {
    return instanceId + ',' + getAuthToken(userId);
}


function getAuthToken(userId) {
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
