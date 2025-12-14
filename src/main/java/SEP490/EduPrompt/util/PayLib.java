package SEP490.EduPrompt.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.SortedMap;
import java.util.TreeMap;

public class PayLib {
    private final SortedMap<String, String> requestData = new TreeMap<>(String::compareTo);

    public void addRequestData(String key, String value) {
        if (value != null && !value.isEmpty()) {
            requestData.put(key, value);
        }
    }

    public String createRequestUrl(String baseUrl, String vnpHashSecret) {
        StringBuilder data = new StringBuilder();
        for (var entry : requestData.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                data.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                        .append("&");
            }
        }
        String queryString = data.toString();

        baseUrl += "?" + queryString;
        String signData = queryString;
        if (!signData.isEmpty()) {
            signData = signData.substring(0, signData.length() - 1);
        }
        String vnpSecureHash = hmacSHA512(vnpHashSecret, signData);
        baseUrl += "vnp_SecureHash=" + vnpSecureHash;

        return baseUrl;
    }

    public static String hmacSHA512(String key, String inputData) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            byte[] hashValue = mac.doFinal(inputData.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : hashValue) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA512", e);
        }
    }
}
