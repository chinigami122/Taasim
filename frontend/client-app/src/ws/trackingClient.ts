import { Client, type IMessage } from '@stomp/stompjs';

export type Loc = {
  lat: number;
  lon: number;
  driverId: string;
  tripId?: string;
  speed?: number;
  status?: string;
  ts: number;
};

/**
 * Subscribes to real-time driver GPS updates for a trip via SockJS/STOMP.
 * Automatically unsubscribes and deactivates the client when the returned callback is called.
 */
export function subscribeToTrip(
  tripId: string,
  onMessage: (loc: Loc) => void
): () => void {
  const token = localStorage.getItem('token');
  const wsUrl = import.meta.env.VITE_WS_BASE || 'http://localhost:8080/ws/tracking';
  const rawWsUrl = wsUrl.replace(/^http/, 'ws');

  const client = new Client({
    brokerURL: rawWsUrl,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 3000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: () => {
      console.log(`[STOMP] Connected to tracking broker for trip: ${tripId}`);
      client.subscribe(`/topic/trips/${tripId}/location`, (msg: IMessage) => {
        try {
          const loc = JSON.parse(msg.body) as Loc;
          onMessage(loc);
        } catch (e) {
          console.error('[STOMP] Failed to parse location message', e);
        }
      });
    },
    onStompError: (frame) => {
      console.error('[STOMP] Error frame:', frame);
    },
    onWebSocketClose: () => {
      console.log('[STOMP] WebSocket closed');
    },
  });

  client.activate();

  return () => {
    console.log(`[STOMP] Deactivating tracking subscription for trip: ${tripId}`);
    try {
      client.deactivate();
    } catch (err) {
      console.warn('[STOMP] Error during client deactivation:', err);
    }
  };
}
