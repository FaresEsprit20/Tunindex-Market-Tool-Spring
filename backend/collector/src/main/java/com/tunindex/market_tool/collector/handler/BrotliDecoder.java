package com.tunindex.market_tool.collector.handler;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.BrotliInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
public class BrotliDecoder extends AbstractDataBufferDecoder<byte[]> {

    private static final MediaType BROTLI_MEDIA_TYPE = new MediaType("application", "br");

    static {
        try {
            Brotli4jLoader.ensureAvailability();
            log.info("✅ Brotli4j loaded successfully");
        } catch (Exception e) {
            log.warn("⚠️ Brotli4j not available: {}", e.getMessage());
        }
    }

    public BrotliDecoder() {
        super(List.of(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Override
    public boolean canDecode(ResolvableType elementType, MimeType mimeType) {
        if (mimeType == null) {
            return false;
        }
        return mimeType.getType().equals("application") && 
               mimeType.getSubtype().equals("br");
    }

    @Override
    public Flux<byte[]> decode(ResolvableType elementType, 
                               DataBuffer dataBuffer, 
                               int offset, 
                               MimeType mimeType) {
        return Flux.fromIterable(decodeDataBuffer(dataBuffer));
    }

    private List<byte[]> decodeDataBuffer(DataBuffer dataBuffer) {
        try {
            byte[] input = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(input);
            
            try (BrotliInputStream brotliInputStream = new BrotliInputStream(
                    new ByteArrayInputStream(input))) {
                
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int length;
                
                while ((length = brotliInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                
                log.debug("✅ Brotli decompressed: {} bytes -> {} bytes", 
                    input.length, outputStream.size());
                
                return List.of(outputStream.toByteArray());
            }
        } catch (Exception e) {
            log.error("❌ Error decoding Brotli data", e);
            return List.of(new byte[0]);
        }
    }

    @Override
    public List<MimeType> getDecodableMimeTypes() {
        return List.of(BROTLI_MEDIA_TYPE);
    }
}
