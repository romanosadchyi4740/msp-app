package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.StompDeliveryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompDeliveryInterceptorTest {

    @Mock
    ObjectProvider<StompDeliveryFilter> filters;

    @Mock
    StompDeliveryFilter filter;

    @Mock
    MessageChannel channel;

    StompDeliveryInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompDeliveryInterceptor(filters);
        lenient().when(filters.orderedStream()).thenAnswer(call -> Stream.of(filter));
    }

    @Test
    void preSend_AllowedMessage_PassesThrough() {
        when(filter.mayDeliver("s1", "/topic/rooms/x/state")).thenReturn(true);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "s1", "/topic/rooms/x/state");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void preSend_RefusedMessage_IsDropped() {
        when(filter.mayDeliver("s1", "/topic/rooms/x/state")).thenReturn(false);

        assertThat(interceptor.preSend(message(SimpMessageType.MESSAGE, "s1", "/topic/rooms/x/state"), channel))
                .isNull();
    }

    @Test
    void preSend_OtherFramesOrMessagesWithoutSession_PassThrough() {
        Message<byte[]> heartbeat = message(SimpMessageType.HEARTBEAT, "s1", null);
        Message<byte[]> noSession = message(SimpMessageType.MESSAGE, null, "/topic/rooms/x/state");

        assertThat(interceptor.preSend(heartbeat, channel)).isSameAs(heartbeat);
        assertThat(interceptor.preSend(noSession, channel)).isSameAs(noSession);
        verify(filter, never()).mayDeliver(anyString(), anyString());
    }

    private static Message<byte[]> message(SimpMessageType type, String sessionId, String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionId(sessionId);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
