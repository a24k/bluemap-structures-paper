package org.ykak.minecraft.bluemapstructurespaper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.ykak.minecraft.bluemapstructurespaper.core.IconComposer;
import org.ykak.minecraft.bluemapstructurespaper.core.IconSources;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureCatalog;
import org.ykak.minecraft.bluemapstructurespaper.core.StructureLayer;

/**
 * Generates the 22x22 structure marker icons from Mojang's officially distributed client
 * jar instead of bundling them: piston-meta's version manifest resolves the running
 * server's Minecraft version to a client jar download + sha1, the jar (cached under the
 * plugin data folder) is opened to read each layer's vanilla texture, and
 * {@link IconComposer} renders it into the marker icon written to
 * {@code icons/<mcVersion>/<layerId>.png}.
 *
 * <p>Any network, IO or parsing failure along the way is logged once and swallowed: the
 * plugin still runs, it just leaves whichever layers failed without a custom icon so
 * BlueMap falls back to its default POI icon for them (see {@link MarkerPublisher}).
 */
final class ClientAssetIcons {

  private static final String VERSION_MANIFEST_URL =
      "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration MANIFEST_REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration JAR_REQUEST_TIMEOUT = Duration.ofSeconds(180);

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder()
          .connectTimeout(CONNECT_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private final BlueMapStructuresPlugin plugin;

  /** Guarded by {@code this}: set exactly once, on the first {@link #ensureIcons()} call. */
  private Map<String, Path> memoized;

  ClientAssetIcons(BlueMapStructuresPlugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Blocking; safe to call from any thread, including concurrently from several async scan
   * tasks. The first caller does the (possibly slow) work of downloading and decoding the
   * client jar; every other caller in this server session — concurrent or later — blocks
   * briefly on the same call and then reuses the memoized result. The network fetch never
   * runs more than once per session, even if it failed the first time.
   */
  synchronized Map<String, Path> ensureIcons() {
    if (memoized == null) {
      memoized = computeIcons();
    }
    return memoized;
  }

  private Map<String, Path> computeIcons() {
    String mcVersion = Bukkit.getMinecraftVersion();
    Path iconsDir = plugin.getDataFolder().toPath().resolve("icons").resolve(mcVersion);

    Map<String, Path> onDisk = existingIcons(iconsDir);
    if (isComplete(onDisk)) {
      return onDisk;
    }

    try {
      Path clientJar = ensureClientJar(mcVersion);
      Map<String, Path> generated = generateIcons(clientJar, iconsDir);
      Map<String, Path> merged = new HashMap<>(onDisk);
      merged.putAll(generated);
      plugin
          .getLogger()
          .info(
              "Generated " + generated.size() + " structure marker icon(s) for Minecraft "
                  + mcVersion + " under " + iconsDir);
      return merged;
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      String cause =
          e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
      plugin
          .getLogger()
          .warning(
              "Could not generate structure marker icons from the Minecraft client jar ("
                  + cause + "); affected markers will use BlueMap's default POI icon.");
      return onDisk;
    }
  }

  private static Map<String, Path> existingIcons(Path iconsDir) {
    Map<String, Path> icons = new HashMap<>();
    for (StructureLayer layer : StructureCatalog.layers()) {
      if (IconSources.texturePath(layer.id()).isEmpty()) {
        continue;
      }
      Path png = iconsDir.resolve(layer.id() + ".png");
      if (Files.isRegularFile(png)) {
        icons.put(layer.id(), png);
      }
    }
    return icons;
  }

  private static boolean isComplete(Map<String, Path> icons) {
    long expected =
        StructureCatalog.layers().stream()
            .filter(layer -> IconSources.texturePath(layer.id()).isPresent())
            .count();
    return icons.size() == expected;
  }

  /** Resolves (downloading/verifying if needed) the cached client jar for {@code mcVersion}. */
  private Path ensureClientJar(String mcVersion) throws Exception {
    Path assetsDir = plugin.getDataFolder().toPath().resolve("assets");
    Path jarPath = assetsDir.resolve("client-" + mcVersion + ".jar");

    String versionUrl = findVersionManifestUrl(mcVersion);
    if (versionUrl == null) {
      throw new IOException(
          "Minecraft version '" + mcVersion + "' not found in the piston-meta version"
              + " manifest");
    }
    ClientDownload download = fetchClientDownload(versionUrl);

    if (Files.isRegularFile(jarPath) && download.sha1().equalsIgnoreCase(sha1Of(jarPath))) {
      return jarPath;
    }

    Files.createDirectories(assetsDir);
    Path tempFile = Files.createTempFile(assetsDir, "client-" + mcVersion + "-", ".jar.tmp");
    try {
      downloadTo(download.url(), tempFile);
      String actualSha1 = sha1Of(tempFile);
      if (!download.sha1().equalsIgnoreCase(actualSha1)) {
        throw new IOException(
            "Downloaded client jar sha1 mismatch (expected " + download.sha1() + ", got "
                + actualSha1 + ")");
      }
      Files.move(
          tempFile, jarPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(tempFile);
    }
    return jarPath;
  }

  /** Reads {@code assets/minecraft/textures/...} entries out of the client jar. */
  private Map<String, Path> generateIcons(Path clientJar, Path iconsDir) throws IOException {
    Files.createDirectories(iconsDir);
    Map<String, Path> icons = new HashMap<>();
    try (ZipFile zip = new ZipFile(clientJar.toFile())) {
      for (StructureLayer layer : StructureCatalog.layers()) {
        Optional<String> texturePath = IconSources.texturePath(layer.id());
        if (texturePath.isEmpty()) {
          continue;
        }
        ZipEntry entry = zip.getEntry(texturePath.get());
        if (entry == null) {
          plugin
              .getLogger()
              .warning(
                  "Client jar has no texture '" + texturePath.get() + "' for layer '"
                      + layer.id() + "'; it will use BlueMap's default POI icon.");
          continue;
        }
        BufferedImage source;
        try (InputStream in = zip.getInputStream(entry)) {
          source = ImageIO.read(in);
        }
        if (source == null) {
          plugin
              .getLogger()
              .warning(
                  "Could not decode texture '" + texturePath.get() + "' for layer '"
                      + layer.id() + "'; it will use BlueMap's default POI icon.");
          continue;
        }

        BufferedImage composed = IconComposer.compose(source);
        Path out = iconsDir.resolve(layer.id() + ".png");
        try (OutputStream os = Files.newOutputStream(out)) {
          ImageIO.write(composed, "png", os);
        }
        icons.put(layer.id(), out);
      }
    }
    return icons;
  }

  private static String findVersionManifestUrl(String mcVersion)
      throws IOException, InterruptedException {
    String body = httpGetString(VERSION_MANIFEST_URL, MANIFEST_REQUEST_TIMEOUT);
    JsonObject manifest = JsonParser.parseString(body).getAsJsonObject();
    JsonArray versions = manifest.getAsJsonArray("versions");
    for (JsonElement element : versions) {
      JsonObject version = element.getAsJsonObject();
      if (mcVersion.equals(version.get("id").getAsString())) {
        return version.get("url").getAsString();
      }
    }
    return null;
  }

  private static ClientDownload fetchClientDownload(String versionUrl)
      throws IOException, InterruptedException {
    String body = httpGetString(versionUrl, MANIFEST_REQUEST_TIMEOUT);
    JsonObject versionJson = JsonParser.parseString(body).getAsJsonObject();
    JsonObject client = versionJson.getAsJsonObject("downloads").getAsJsonObject("client");
    return new ClientDownload(client.get("url").getAsString(), client.get("sha1").getAsString());
  }

  private static String httpGetString(String url, Duration timeout)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
    HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
    }
    return response.body();
  }

  private static void downloadTo(String url, Path destination)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url)).timeout(JAR_REQUEST_TIMEOUT).GET().build();
    HttpResponse<Path> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(destination));
    if (response.statusCode() != 200) {
      throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
    }
  }

  private static String sha1Of(Path path) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    try (InputStream in = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    StringBuilder hex = new StringBuilder(40);
    for (byte b : digest.digest()) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  private record ClientDownload(String url, String sha1) {}
}
