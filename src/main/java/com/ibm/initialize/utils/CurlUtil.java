package com.ibm.initialize.utils;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.concurrent.CountDownLatch;
import java.io.ByteArrayOutputStream;

public class CurlUtil {
    public static String toCurl(HttpRequest request) {
        StringBuilder curl = new StringBuilder("curl");
        // Add method
        curl.append(" -X ").append(request.method());
        // Add URL
        curl.append(" '").append(request.uri()).append("'");
        // Add headers
        request.headers().map().forEach((key, values) -> {
            for (String value : values) {
                curl.append(" -H '").append(key).append(": ").append(value).append("'");
            }
        });
        // Add body if available
        request.bodyPublisher().ifPresent(bodyPublisher -> {
            try {
                byte[] bodyBytes = readBody(bodyPublisher);
                if (bodyBytes.length > 0) {
                    String bodyString = new String(bodyBytes, StandardCharsets.UTF_8)
                            .replace("'", "'\\''"); // escape single quotes for shell
                    curl.append(" --data '").append(bodyString).append("'");
                }
            } catch (Exception e) {
                // ignore if body can't be read
            }
        });
        return curl.toString();
    }
    // Helper to extract bytes from BodyPublisher
    private static byte[] readBody(HttpRequest.BodyPublisher publisher) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                baos.write(bytes, 0, bytes.length);
            }
            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }
            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
        latch.await();
        return baos.toByteArray();
    }
 
}
