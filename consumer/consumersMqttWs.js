#!/usr/bin/env node

'use strict;'

var mqtt = require('mqtt');


var args = process.argv.slice(2);
if (args.length < 1 || args.length > 3) {
    console.log('Usage: %s consumerId [initialInstanceId] [numberOfInstances]', basename(process.argv[1]));
    console.log('       Optional values default to 1 if missing or not numeric');
    process.exit(1);
}

var host = 'localhost';
var port = 15675;
var consumerId = args[0];
var initialInstanceId = parseInt(args[1]) || 1;
var numberOfInstances = parseInt(args[2]) || 1;

var authTokenPeriodMillis = 5 * 60 * 1000;

var url  = 'ws://' + host + ':' + port + '/ws';


console.log('consumerId: ' + consumerId);
console.log('initialInstanceId: ' + initialInstanceId);
console.log('numberOfInstances: ' + numberOfInstances);


for (i = initialInstanceId; i <= initialInstanceId + numberOfInstances; i++) {
    startInstance(i);
}


function startInstance(instanceId) {
    var logPrefix = consumerId + '.' + instanceId + ': ';

    var client = mqtt.connect(url, {
        username: getUserDataStr(consumerId, instanceId),
        password: '[ignored]',
        clientId: consumerId + '-' + instanceId + '-',
        reconnectPeriod: 1000, // ms
        clean: false
    });


    client.on('connect', function () {
        console.log(logPrefix + 'Connected');
        client.subscribe(consumerId, { qos: 1 });
        console.log(logPrefix + 'Subscribed');
    });


    client.on('reconnect', function () {
        console.log(logPrefix + 'Reconnected - Refreshing Access Token');
        client.options.username = getUserDataStr(consumerId, instanceId);
    });


    client.on('close', function () {
        console.log(logPrefix + 'Disconnected');
    });


    client.on('offline', function () {
        console.log(logPrefix + 'Offline');
    });


    client.on('message', function (topic, message, packet) {
        console.log(logPrefix + 'Received message: ' + message.toString());
    });


    client.on('error', function (error) {
        console.log(logPrefix + 'Error: ' + error);
    });
}

function getUserDataStr(consumerId, instanceId) {
    return instanceId + ',' + getAuthToken(consumerId);
}


function getAuthToken(consumerId) {
    var authTokenExpiryTimestamp = Date.now() + authTokenPeriodMillis;
    var authTokenData = consumerId + ',' + authTokenExpiryTimestamp;
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
