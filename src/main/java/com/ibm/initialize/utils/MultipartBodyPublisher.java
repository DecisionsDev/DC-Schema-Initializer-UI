package com.ibm.initialize.utils;

import java.io.*;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

    public  class MultipartBodyPublisher {
        private static final String CRLF = "\r\n";
        private final String boundary;
        private final List<InputStreamSupplier> streams = new ArrayList<>();

        public MultipartBodyPublisher() {
            this.boundary = "----JavaFormBoundary" + UUID.randomUUID();
        }

        public String getContentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        public MultipartBodyPublisher addFilePart(String name, Path file, String contentType) throws IOException {
            String fileName = file.getFileName().toString();

            StringBuilder sb = new StringBuilder();
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"").append(escape(name))
              .append("\"; filename=\"").append(escape(fileName)).append("\"").append(CRLF);
            sb.append("Content-Type: ").append(contentType).append(CRLF);
            sb.append(CRLF);

            // header
            addBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
            // file content (streamed)
            addStream(() -> Files.newInputStream(file));
            // trailing CRLF for part
            addBytes(CRLF.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        public MultipartBodyPublisher addField(String name, String value) {
            StringBuilder sb = new StringBuilder();
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"").append(escape(name)).append("\"").append(CRLF);
            sb.append("Content-Type: text/plain; charset=UTF-8").append(CRLF);
            sb.append(CRLF);
            sb.append(value).append(CRLF);
            addBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
            return this;
        }

        public BodyPublisher build() {
            // add closing boundary
            String closing = "--" + boundary + "--" + CRLF;
            addBytes(closing.getBytes(StandardCharsets.UTF_8));

            // Create a publisher that supplies a SequenceInputStream over all parts
            return BodyPublishers.ofInputStream(() -> {
                List<InputStream> inputs = new ArrayList<>(streams.size());
                for (InputStreamSupplier s : streams) {
                    try {
						inputs.add(s.get());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                }
                return new SequenceInputStream(Collections.enumeration(inputs));
            });
        }

        private void addBytes(byte[] bytes) {
            addStream(() -> new ByteArrayInputStream(bytes));
        }

        private void addStream(InputStreamSupplier supplier) {
            streams.add(supplier);
        }

        private String escape(String s) {
            // Simple escape for quotes in filename/field name
            return s.replace("\"", "%22");
        }

        @FunctionalInterface
        private interface InputStreamSupplier {
            InputStream get() throws IOException;
        }
    }
