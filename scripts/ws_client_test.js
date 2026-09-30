const SockJS = require('sockjs-client');
const { Stomp } = require('@stomp/stompjs');

const TRIP_ID = process.argv[2];
const TOKEN = process.argv[3];
const WS_URL = process.argv[4] || 'http://localhost:8080/ws/tracking';
const EXPECT_FAIL = process.argv.includes('--expect-fail');

if (!TRIP_ID) {
    console.error('Usage: node ws_client_test.js <tripId> <jwtToken> [wsUrl] [--expect-fail]');
    process.exit(1);
}

console.log(`Connecting to ${WS_URL} for trip ${TRIP_ID}... (expectFail=${EXPECT_FAIL})`);

const sock = new SockJS(WS_URL);
const stomp = Stomp.over(() => sock);
stomp.debug = () => {}; // quiet

let timeoutTimer = setTimeout(() => {
    if (EXPECT_FAIL) {
        console.error('❌ Expected connection failure, but timed out waiting for error.');
        process.exit(1);
    } else {
        console.error('❌ Timeout waiting for WebSocket events (15s).');
        process.exit(1);
    }
}, 15000);

const connectHeaders = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};

stomp.connect(
    connectHeaders,
    (frame) => {
        if (EXPECT_FAIL) {
            console.error('❌ Connection succeeded, but it was expected to FAIL with invalid/missing token!');
            process.exit(1);
        }
        console.log('✅ STOMP connected successfully!');
        const topic = `/topic/trips/${TRIP_ID}/location`;
        console.log(`Subscribing to ${topic}...`);

        stomp.subscribe(topic, (msg) => {
            console.log('📍 RECEIVED_LOCATION:', msg.body);
            clearTimeout(timeoutTimer);
            try {
                const data = JSON.parse(msg.body);
                if (data.lat && data.lon && data.driverId) {
                    console.log('✅ Verified location payload structure!');
                    setTimeout(() => {
                        stomp.disconnect(() => {
                            console.log('Client disconnected cleanly.');
                            process.exit(0);
                        });
                    }, 500);
                }
            } catch (e) {
                console.error('Failed to parse location body:', e);
                process.exit(1);
            }
        });

        console.log('READY_FOR_PINGS');
    },
    (err) => {
        if (EXPECT_FAIL) {
            console.log('✅ Connection correctly rejected as expected:', err ? (err.message || err) : 'Error frame');
            clearTimeout(timeoutTimer);
            process.exit(0);
        } else {
            console.error('❌ STOMP connection error:', err);
            clearTimeout(timeoutTimer);
            process.exit(1);
        }
    }
);
