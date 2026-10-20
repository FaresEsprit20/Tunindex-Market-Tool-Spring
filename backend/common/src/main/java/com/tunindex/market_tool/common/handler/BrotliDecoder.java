package com.tunindex.market_tool.common.handler;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.BrotliInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

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
        super(MediaType.APPLICATION_OCTET_STREAM);
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
    protected byte[] decodeDataBuffer(DataBuffer dataBuffer, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
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
                
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            log.error("❌ Error decoding Brotli data", e);
            return new byte[0];
        }
    }

    @Override
    public List<MimeType> getDecodableMimeTypes() {
        return List.of(BROTLI_MEDIA_TYPE);
    }
}
