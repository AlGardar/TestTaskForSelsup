package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private static final String API_CREATE_DOCUMENT_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient;
    private final AtomicInteger requestCount;
    private final long timeIntervalMillis;
    private final int requestLimit;
    private long lastResetTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newBuilder().build();
        this.requestCount = new AtomicInteger(0);
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.lastResetTime = Instant.now().toEpochMilli();
    }


    public String createDocument(Document document, String signature) {
        if (document.isImportRequest()) {
            throw new ImportRequestException("Document creation is skipped for imported goods.");
        }

        synchronized (this) {
            try {
                long currentTime = Instant.now().toEpochMilli();
                long timePassed = currentTime - lastResetTime;

                if (timePassed >= timeIntervalMillis) {
                    requestCount.set(0);
                    lastResetTime = Instant.now().toEpochMilli();
                }

                while (requestCount.get() >= requestLimit) {
                    wait(timeIntervalMillis - timePassed);

                    currentTime = Instant.now().toEpochMilli();
                    timePassed = currentTime - lastResetTime;

                    if (timePassed >= timeIntervalMillis) {
                        requestCount.set(0);
                        lastResetTime = Instant.now().toEpochMilli();
                    }
                }

                String jsonDocument = JsonUtil.toJson(document);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_CREATE_DOCUMENT_URL))
                        .header("Content-Type", "application/json")
                        .header("Signature", signature)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                        .build();

                HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String response = httpResponse.body();

                requestCount.incrementAndGet();
                return response;

            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }

        }

    }


    @Getter
    @Setter
    @JsonPropertyOrder({"description", "id", "status", "type", "importRequest", "ownerInn", "participantInn", "producerInn", "productionDate", "productionType", "products", "regDate", "regNumber"})
    public static class Document {
        private Description description;
        @JsonProperty("doc_id")
        private String id;
        @JsonProperty("doc_status")
        private String status;
        @JsonProperty("doc_type")
        private String type;
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private String productionDate;
        @JsonProperty("production_type")
        private String productionType;
        private List<Product> products;
        @JsonProperty("reg_date")
        private String regDate;
        @JsonProperty("reg_number")
        private String regNumber;

        @Getter
        @Setter
        public static class Description {
            private String participantInn;
        }

        @Getter
        @Setter
        @JsonPropertyOrder({"certificateDocument", "certificateDocumentDate", "certificateDocumentNumber", "ownerInn", "producerInn", "productionDate", "tnvedCode", "uitCode", "uituCode"})
        public static class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;
            @JsonProperty("certificate_document_date")
            private String certificateDocumentDate;
            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;
            @JsonProperty("owner_inn")
            private String ownerInn;
            @JsonProperty("producer_inn")
            private String producerInn;
            @JsonProperty("production_date")
            private String productionDate;
            @JsonProperty("tnved_code")
            private String tnvedCode;
            @JsonProperty("uit_code")
            private String uitCode;
            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }

    public static class JsonUtil {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        public static String toJson(Object object) {
            try {
                return objectMapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing object to JSON", e);
            }
        }
    }

    public static class ImportRequestException extends RuntimeException {
        public ImportRequestException(String message) {
            super(message);
        }
    }


    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);

        var document = new CrptApi.Document();
        String signature = "signature";

        String response = crptApi.createDocument(document, signature);

        System.out.println(response);

    }
}
