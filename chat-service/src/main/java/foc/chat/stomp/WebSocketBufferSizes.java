/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: raises the servlet container's WebSocket message buffer (8 KB
 * by default) to the STOMP frame limit, so a frame carrying a 2 MB
 * image (C7, C8) fits.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.stomp;

import jakarta.servlet.ServletContext;
import jakarta.websocket.server.ServerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ServletContextAware;

/**
 * Does what Spring's {@code ServletServerContainerFactoryBean} does, but
 * only when a real WebSocket container is there: the factory bean fails
 * outright without one, which breaks every MockMvc test context.
 */
@Component
class WebSocketBufferSizes implements ServletContextAware {

    @Override
    public void setServletContext(ServletContext servletContext) {
        if (servletContext.getAttribute(ServerContainer.class.getName()) instanceof ServerContainer container) {
            container.setDefaultMaxTextMessageBufferSize(WebSocketStompConfig.MAX_FRAME_BYTES);
            container.setDefaultMaxBinaryMessageBufferSize(WebSocketStompConfig.MAX_FRAME_BYTES);
        }
    }
}
