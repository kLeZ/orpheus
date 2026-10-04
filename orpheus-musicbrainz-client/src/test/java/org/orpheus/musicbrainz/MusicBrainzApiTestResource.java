package org.orpheus.musicbrainz;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Embedded JDK HTTP server (no new dependency) imitating the MusicBrainz lookup endpoint for
 * {@link MusicBrainzReleaseLookupTest}: it answers with canonical JSON fixed per recording id, so every
 * test exercises the real adapter (REST client and parsing) without depending on the network. The URL
 * of the server, known only at runtime because the port is chosen by the operating system, is injected
 * into {@code quarkus.rest-client.musicbrainz-api.url} before Quarkus builds the final test
 * configuration.
 */
public class MusicBrainzApiTestResource implements QuarkusTestResourceLifecycleManager {

	/** Recording id -> JSON body of the response of {@code GET /ws/2/recording/{id}}. */
	static final Map<String, String> RESPONSES = Map.ofEntries(
	        Map.entry("year-only", """
	                {"id":"year-only","releases":[{"id":"release-year-only","date":"1994",\
	                "release-group":{"primary-type":"Album"}}]}"""), Map.entry("full-date", """
	                {"id":"full-date","releases":[{"id":"release-full-date","date":"1994-03-15",\
	                "release-group":{"primary-type":"Album"}}]}"""), Map.entry("year-month", """
	                {"id":"year-month","releases":[{"id":"release-year-month","date":"1994-03",\
	                "release-group":{"primary-type":"Album"}}]}"""), Map.entry("no-date", """
	                {"id":"no-date","releases":[{"id":"release-no-date",\
	                "release-group":{"primary-type":"Album"}}]}"""), Map.entry("type-missing", """
	                {"id":"type-missing","releases":[{"id":"release-type-missing","date":"2001"}]}"""), Map.entry("type-single", """
	                {"id":"type-single","releases":[{"id":"release-type-single","date":"2001-07",\
	                "release-group":{"primary-type":"Single"}}]}"""), Map.entry("type-ep", """
	                {"id":"type-ep","releases":[{"id":"release-type-ep","date":"2001-07",\
	                "release-group":{"primary-type":"EP"}}]}"""), Map.entry("type-album", """
	                {"id":"type-album","releases":[{"id":"release-type-album","date":"2001-07-04",\
	                "release-group":{"primary-type":"Album"}}]}"""), Map.entry("several-releases", """
	                {"id":"several-releases","releases":[\
	                {"id":"release-1","date":"1994","release-group":{"primary-type":"Album"}},\
	                {"id":"release-2","date":"1994-03-15","release-group":{"primary-type":"Single"}},\
	                {"id":"release-3","release-group":{"primary-type":"Album"}}]}"""), Map.entry("compilation-va", """
	                {"id":"compilation-va","releases":[\
	                {"id":"release-compilation","date":"2000","artist-credit":[{"name":"Various Artists"}],\
	                "release-group":{"primary-type":"Album","secondary-types":["Compilation"]}},\
	                {"id":"release-original","date":"2001-10-02","artist-credit":[{"name":"Queen"},{"name":"David Bowie"}],\
	                "release-group":{"primary-type":"Album","secondary-types":[]}}]}"""), Map.entry("secondary", """
	                {"id":"secondary","releases":[\
	                {"id":"release-demo","date":"1994","release-group":{"primary-type":"Album","secondary-types":["Demo"]}},\
	                {"id":"release-live","date":"1995","release-group":{"primary-type":"Album","secondary-types":["Live"]}},\
	                {"id":"release-bootleg","date":"1996","status":"Bootleg","release-group":{"primary-type":"Album"}},\
	                {"id":"release-soundtrack","date":"1965","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Soundtrack"]}}]}"""), Map.entry("with-media", """
	                {"id":"with-media","releases":[{"id":"release-hot-space","title":"Hot Space","date":"1982-05-21",\
	                "artist-credit":[{"name":"Queen","joinphrase":" & ","artist":{"id":"id-queen"}},{"name":"David Bowie","joinphrase":"","artist":{"id":"id-bowie"}}],\
	                "release-group":{"id":"rg-hot-space","primary-type":"Album"},\
	                "media":[{"position":2,"tracks":[{"position":11,"number":"11","title":"Under Pressure",\
	                "artist-credit":[{"name":"Queen","joinphrase":" & ","artist":{"id":"id-queen"}},{"name":"David Bowie","joinphrase":"","artist":{"id":"id-bowie"}}]}]}]}]}"""));

	static final Map<String, String> GENRES = Map.of("rg-hot-space", """
	        {"id":"rg-hot-space","genres":[{"name":"rock","count":3},{"name":"funk","count":5},{"name":"disco","count":3}]}""", "id-queen", """
	        {"id":"id-queen","genres":[]}""");

	private HttpServer server;

	@Override
	public Map<String, String> start() {
		try {
			server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot start the fake MusicBrainz HTTP server for the tests", e);
		}
		server.createContext("/ws/2/recording/", this::handle);
		server.createContext("/ws/2/release-group/", e -> respond(e, GENRES));
		server.createContext("/ws/2/release-group", this::search);
		server.createContext("/ws/2/artist/", e -> respond(e, GENRES));
		server.start();
		return Map.of("quarkus.rest-client.musicbrainz-api.url", "http://localhost:" + server.getAddress().getPort());
	}

	// Ricerca di release-group: response reale ridotta per "Burn" dei Deep Purple; la query ricevuta
	// resta leggibile dai test.
	static volatile String lastQuery;

	private void search(HttpExchange exchange) throws IOException {
		lastQuery = java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
		byte[] response = """
		        {"count":2,"release-groups":[\
		        {"id":"rg-burn-ep","score":100,"title":"Burn","primary-type":"EP",\
		        "artist-credit":[{"name":"Deep Purple","artist":{"id":"id-dp","name":"Deep Purple"}}]},\
		        {"id":"rg-burn","score":100,"title":"Burn","primary-type":"Album",\
		        "artist-credit":[{"name":"Deep Purple","joinphrase":"","artist":{"id":"id-dp","name":"Deep Purple"}}]}]}""".getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, response.length);
		exchange.getResponseBody().write(response);
		exchange.close();
	}

	private void handle(HttpExchange exchange) throws IOException {
		respond(exchange, RESPONSES);
	}

	private void respond(HttpExchange exchange, Map<String, String> responses) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String recordingId = path.substring(path.lastIndexOf('/') + 1);
		String corpo = responses.get(recordingId);
		try {
			if (corpo == null) {
				byte[] response = "{\"error\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(404, response.length);
				exchange.getResponseBody().write(response);
			} else {
				byte[] response = corpo.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, response.length);
				exchange.getResponseBody().write(response);
			}
		} finally {
			exchange.close();
		}
	}

	@Override
	public void stop() {
		if (server != null) {
			server.stop(0);
		}
	}
}
