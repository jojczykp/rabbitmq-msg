'use strict;'


const WebSocket = require('ws');


const host = 'localhost';
const port = 9000;

const reconnectPeriod = 1000; // ms

const authTokenPeriodMillis = 5 * 60 * 1000;


const clientId = "consumer1";
const instanceId = 1;


startClient(getUserDataStr(clientId, instanceId));


function startClient(userDataStr) {
    const ws = new WebSocket('ws://' + host + ':' + port);

    ws.on('open', function open() {
        console.log('Connected to %s:%d', host, port);
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
