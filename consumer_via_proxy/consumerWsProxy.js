'use strict;'


const WebSocket = require('ws');


var args = process.argv.slice(2);
if (args.length < 2) {
    console.log('Usage: %s consumerId instanceId', basename(process.argv[1]));
    console.log(' E.g.: %s 1 1', basename(process.argv[1]));
    process.exit(1);
}


const url = 'ws://localhost:9000';

const reconnectPeriod = 1000; // ms

const authTokenPeriodMillis = 5 * 60 * 1000;


const consumerId = "consumer" + args[0];
const instanceId = args[1];


console.log('Connecting to ' + url);
console.log('To exit press CTRL+C');
console.log('- consumerId: ' + consumerId);
console.log('- instanceId: ' + instanceId);
console.log('');


startConsumer(getUserDataStr(consumerId, instanceId));


function startConsumer(userDataStr) {
    const ws = new WebSocket(url);

    ws.on('open', function open() {
        console.log('Connected to ' + url);
        ws.send(userDataStr);
    });

    ws.on('message', function incoming(data) {
        console.log('Received: [%s]', data);
    });

    ws.on('close', function incoming() {
        console.log('Closed. Retrying...');
        setTimeout(() => startClient(getUserDataStr(clientId, instanceId)), reconnectPeriod);
    });

    ws.on('error', function incoming(error) {
        console.log(error.toString());
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
