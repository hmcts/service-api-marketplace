package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SessionFunctionalTest {

    // Fixed, so every run updates the same rows rather than adding more.
    private static final String OID = "00000000-0000-0000-0000-0000000f0001";
    private static final String OTHER_OID = "00000000-0000-0000-0000-0000000f0002";
    private static final String EMAIL = "functional-test@amp.example.com";

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", "http://localhost:8080");
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void signing_in_twice_should_keep_the_same_user_and_take_entras_latest_names() throws Exception {
        var first = upsert(OID, "{\"email\":\"" + EMAIL + "\",\"name\":\"Func Test\"}");
        var second = upsert(OID, "{\"email\":\"" + EMAIL + "\",\"name\":\"Functional Test\"}");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(idIn(second.body())).isEqualTo(idIn(first.body()));
        assertThat(second.body()).contains("\"name\":\"Functional Test\"", "\"email\":\"" + EMAIL + "\"");
    }

    @Test
    void another_entra_account_with_the_same_email_should_take_over_the_user() throws Exception {
        var original = upsert(OID, "{\"email\":\"" + EMAIL + "\"}");

        var recreated = upsert(OTHER_OID, "{\"email\":\"" + EMAIL.toUpperCase() + "\"}");

        assertThat(recreated.statusCode()).isEqualTo(200);
        assertThat(idIn(recreated.body())).isEqualTo(idIn(original.body()));
        assertThat(recreated.body()).contains("\"entraObjectId\":\"" + OTHER_OID + "\"");
    }

    @Test
    void an_object_id_that_is_not_a_guid_should_be_refused_with_400() throws Exception {
        assertThat(upsert("not-an-oid", "{\"email\":\"" + EMAIL + "\"}").statusCode()).isEqualTo(400);
    }

    @Test
    void missing_email_should_be_refused_with_400() throws Exception {
        assertThat(upsert(OID, "{\"name\":\"Func\"}").statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> upsert(final String oid, final String body) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(testUrl + "/session"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"objectId\":\"" + oid + "\"," + body.substring(1)))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String idIn(final String json) {
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
