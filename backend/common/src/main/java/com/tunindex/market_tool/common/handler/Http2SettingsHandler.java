package com.tunindex.market_tool.common.handler;


import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Http2SettingsHandler extends ChannelDuplexHandler {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Send Chrome-like HTTP/2 settings
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(65535);
        settings.maxConcurrentStreams(100);
        settings.maxHeaderListSize(8192);
        settings.maxFrameSize(16384);
        settings.headerTableSize(4096);
        
        ctx.writeAndFlush(new Http2SettingsFrame(settings));
        log.debug("📡 Sent HTTP/2 settings frame matching Chrome behavior");
        
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Handle incoming HTTP/2 settings
        if (msg instanceof Http2SettingsFrame) {
            Http2Settings settings = ((Http2SettingsFrame) msg).settings();
            log.debug("📡 Received HTTP/2 settings: {}", settings);
        }
        
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("❌ HTTP/2 Settings Handler error", cause);
        ctx.close();
    }
}
