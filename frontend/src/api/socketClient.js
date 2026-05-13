import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class SocketClient {
  constructor() {
    this.client = null;
    this.onMessageReceived = null;
  }

  connect(token, onMessageReceived) {
    this.onMessageReceived = onMessageReceived;

    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        console.log(str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = (frame) => {
      console.log('Connected: ' + frame);
      
      // The user ID is extracted from the token on the server side
      // The server sends messages to /user/{userId}/queue/messages
      this.client.subscribe('/user/queue/messages', (message) => {
        if (this.onMessageReceived) {
          this.onMessageReceived(JSON.parse(message.body));
        }
      });
    };

    this.client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
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
    } else {
      console.error('Cannot send message: not connected');
    }
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
