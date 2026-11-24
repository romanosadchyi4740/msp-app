import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL } from '../config';
import axiosClient from './axiosClient';
import { stompDebugLine } from './redact';

class SocketClient {
  constructor() {
    this.client = null;
    this.onMessageReceived = null;
    this.connectListeners = new Set();
  }

  /**
   * Calls the listener on every connect, including reconnects, and at once if connected now; subscriptions have to
   * be made again on each, since a new connection starts without them. Returns the function that removes it.
   */
  onConnect(listener) {
    this.connectListeners.add(listener);
    if (this.isConnected()) {
      listener();
    }
    return () => this.connectListeners.delete(listener);
  }

  isConnected() {
    return Boolean(this.client && this.client.connected);
  }

  connect(token, onMessageReceived) {
    this.onMessageReceived = onMessageReceived;

    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      // Frames carry the token (CONNECT) and chat text and room events (MESSAGE): never log them, and in development
      // only their first line (KAN-107)
      debug: import.meta.env.DEV ? (str) => console.debug(stompDebugLine(str)) : () => {},
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = () => {
      // The user ID is extracted from the token on the server side
      // The server sends messages to /user/{userId}/queue/messages
      this.client.subscribe('/user/queue/messages', (message) => {
        if (this.onMessageReceived) {
          this.onMessageReceived(JSON.parse(message.body));
        }
      });
      this.connectListeners.forEach((listener) => {
        try {
          listener();
        } catch (error) {
          console.error('A connect listener failed:', error);
        }
      });
    };

    this.client.onStompError = (frame) => {
      // The message header says why; the body can repeat what was sent, so it is not logged (KAN-107)
      console.error('Broker reported error: ' + frame.headers['message']);
      // The server refuses the session of a deleted account; if this token no longer works, the 401 from this
      // request makes axiosClient log out and redirect, which stops the reconnect loop
      axiosClient.get('/api/users/me').catch(() => {});
    };

    this.client.activate();
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
    }
  }

  sendMessage(destination, body) {
    if (this.client && this.client.connected) {
      this.client.publish({
        destination: `/app${destination}`,
        body: JSON.stringify(body),
      });
      return true;
    }
    console.error('Cannot send message: not connected');
    return false;
  }

  subscribe(destination, callback) {
    if (this.client && this.client.connected) {
      return this.client.subscribe(destination, (message) => {
        callback(JSON.parse(message.body));
      });
    }
    return null;
  }
}

const socketClient = new SocketClient();
export default socketClient;
