package lpbot.main.lastfm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.common.io.Files;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import se.michaelthelin.spotify.model_objects.specification.Track;
import lpbot.main.playlist.LPEntity;
import lpbot.main.spotify.util.BotLogger;

@Component
public class LastFmDataHandler {

  private final BotLogger log;

  private UriComponentsBuilder lastFmApiUrl;

  public LastFmDataHandler(BotLogger botLogger) {
    this.log = botLogger;

    try {
      String lastFmApiToken = readToken();
      this.lastFmApiUrl = UriComponentsBuilder.newInstance()
          .scheme("http")
          .host("ws.audioscrobbler.com")
          .path("/2.0")
          .queryParam("api_key", lastFmApiToken)
          .queryParam("format", "json");
    } catch (IOException e) {
      log.error("Failed to start bot! (Couldn't read last.fm token). Terminating...");
      e.printStackTrace();
      System.exit(1);
    }
  }

  public LPEntityWithLastFmData getLastFmDataForLPEntity(LPEntity lpEntity, Track spotifyTrack) {
    LPEntityWithLastFmData lpEntityWithLastFmData = new LPEntityWithLastFmData(lpEntity);

    String lastFmName = lpEntity.getLastFmName();
    try {
      // User info
      String lastFmApiUrlForUserInfo = assembleLastFmApiUrlForUserInfo(lastFmName);
      JsonObject user = executeRequest(lastFmApiUrlForUserInfo, "user");
      String userPageUrl = user.get("url").getAsString();
      lpEntityWithLastFmData.setUserPageUrl(userPageUrl);
      String userImageUrl = user.get("image").getAsJsonArray().get(0).getAsJsonObject().get("#text").getAsString();
      lpEntityWithLastFmData.setProfilePictureUrl(userImageUrl);

      // Track info
      String artistName = spotifyTrack.getArtists()[0].getName();
      String trackName = spotifyTrack.getName();
      String url = assembleLastFmApiUrlForTrackGetInfo(lastFmName, artistName, trackName);
      JsonObject jsonTrack = executeRequest(url, "track");
      String songUrl = jsonTrack.get("url").getAsString();
      lpEntityWithLastFmData.setSongLinkUrl(songUrl);
      int scrobbleCount = jsonTrack.get("userplaycount").getAsInt();
      lpEntityWithLastFmData.setScrobbleCount(scrobbleCount);

    } catch (Exception e) {
      log.error("Error during last.fm API call. Likely caused by an unknown username: " + lastFmName);
    }
    return lpEntityWithLastFmData;
  }

  private JsonObject executeRequest(String url, String rootElement) throws IOException {
    String rawJson = Jsoup.connect(url).ignoreContentType(true).execute().body();
    JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject();
    return json.get(rootElement).getAsJsonObject();
  }

  private String assembleLastFmApiUrlForUserInfo(String lfmUserName) {
    return lastFmApiUrl.cloneBuilder()
        .queryParam("method", "user.getInfo")
        .queryParam("username", lfmUserName)
        .build().toUriString();
  }

  private String assembleLastFmApiUrlForTrackGetInfo(String lfmUserName, String artistName, String trackName) {
    return lastFmApiUrl.cloneBuilder()
        .queryParam("method", "track.getInfo")
        .queryParam("artist", artistName)
        .queryParam("track", trackName)
        .queryParam("username", lfmUserName)
        .build().toUriString();
  }

  private String readToken() throws IOException {
    File tokenFile = new File("./lastfmtoken.txt");
    if (tokenFile.canRead()) {
      return Files.asCharSource(tokenFile, Charset.defaultCharset()).readFirstLine();
    }
    throw new IOException("Can't read token file!");
  }
}