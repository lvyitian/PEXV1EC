package net.gravitydevelopment.updater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class Updater {
   private static final String TITLE_VALUE = "name";
   private static final String LINK_VALUE = "downloadUrl";
   private static final String TYPE_VALUE = "releaseType";
   private static final String VERSION_VALUE = "gameVersion";
   private static final String QUERY = "/servermods/files?projectIds=";
   private static final String HOST = "https://api.curseforge.com";
   private static final String USER_AGENT = "Updater (by Gravity)";
   private static final String DELIMETER = "^v|[\\s_-]v";
   private static final String[] NO_UPDATE_TAG = new String[]{"-DEV", "-PRE", "-SNAPSHOT"};
   private static final int BYTE_SIZE = 1024;
   private static final String API_KEY_CONFIG_KEY = "api-key";
   private static final String DISABLE_CONFIG_KEY = "disable";
   private static final String API_KEY_DEFAULT = "PUT_API_KEY_HERE";
   private static final boolean DISABLE_DEFAULT = false;
   private final Plugin plugin;
   private final Updater.UpdateType type;
   private final boolean announce;
   private final File file;
   private final File updateFolder;
   private final Updater.UpdateCallback callback;
   private int id;
   private String apiKey;
   private String versionName;
   private String versionLink;
   private String versionType;
   private String versionGameVersion;
   private URL url;
   private Thread thread;
   private Updater.UpdateResult result;

   public Updater(Plugin plugin, int id, File file, Updater.UpdateType type, boolean announce) {
      this(plugin, id, file, type, (Updater.UpdateCallback)null, announce);
   }

   public Updater(Plugin plugin, int id, File file, Updater.UpdateType type, Updater.UpdateCallback callback) {
      this(plugin, id, file, type, callback, false);
   }

   public Updater(Plugin plugin, int id, File file, Updater.UpdateType type, Updater.UpdateCallback callback, boolean announce) {
      this.id = -1;
      this.apiKey = null;
      this.result = Updater.UpdateResult.SUCCESS;
      this.plugin = plugin;
      this.type = type;
      this.announce = announce;
      this.file = file;
      this.id = id;
      this.updateFolder = this.plugin.getServer().getUpdateFolderFile();
      this.callback = callback;
      File pluginFile = this.plugin.getDataFolder().getParentFile();
      File updaterFile = new File(pluginFile, "Updater");
      File updaterConfigFile = new File(updaterFile, "config.yml");
      YamlConfiguration config = new YamlConfiguration();
      config.options().header("This configuration file affects all plugins using the Updater system (version 2+ - http://forums.bukkit.org/threads/96681/ )\nIf you wish to use your API key, read http://wiki.bukkit.org/ServerMods_API and place it below.\nSome updating systems will not adhere to the disabled value, but these may be turned off in their plugin's configuration.");
      config.addDefault("api-key", "PUT_API_KEY_HERE");
      config.addDefault("disable", false);
      if (!updaterFile.exists()) {
         this.fileIOOrError(updaterFile, updaterFile.mkdir(), true);
      }

      boolean createFile = !updaterConfigFile.exists();

      try {
         if (createFile) {
            this.fileIOOrError(updaterConfigFile, updaterConfigFile.createNewFile(), true);
            config.options().copyDefaults(true);
            config.save(updaterConfigFile);
         } else {
            config.load(updaterConfigFile);
         }
      } catch (Exception var15) {
         String message;
         if (createFile) {
            message = "The updater could not create configuration at " + updaterFile.getAbsolutePath();
         } else {
            message = "The updater could not load configuration at " + updaterFile.getAbsolutePath();
         }

         this.plugin.getLogger().log(Level.SEVERE, message, var15);
      }

      if (config.getBoolean("disable")) {
         this.result = Updater.UpdateResult.DISABLED;
      } else {
         String key = config.getString("api-key");
         if ("PUT_API_KEY_HERE".equalsIgnoreCase(key) || "".equals(key)) {
            key = null;
         }

         this.apiKey = key;

         try {
            this.url = new URL("https://api.curseforge.com/servermods/files?projectIds=" + this.id);
         } catch (MalformedURLException var14) {
            this.plugin.getLogger().log(Level.SEVERE, "The project ID provided for updating, " + this.id + " is invalid.", var14);
            this.result = Updater.UpdateResult.FAIL_BADID;
         }

         if (this.result != Updater.UpdateResult.FAIL_BADID) {
            this.thread = new Thread(new Updater.UpdateRunnable());
            this.thread.start();
         } else {
            this.runUpdater();
         }

      }
   }

   public Updater.UpdateResult getResult() {
      this.waitForThread();
      return this.result;
   }

   public Updater.ReleaseType getLatestType() {
      this.waitForThread();
      if (this.versionType != null) {
         Updater.ReleaseType[] var1 = Updater.ReleaseType.values();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            Updater.ReleaseType type = var1[var3];
            if (this.versionType.equalsIgnoreCase(type.name())) {
               return type;
            }
         }
      }

      return null;
   }

   public String getLatestGameVersion() {
      this.waitForThread();
      return this.versionGameVersion;
   }

   public String getLatestName() {
      this.waitForThread();
      return this.versionName;
   }

   public String getLatestFileLink() {
      this.waitForThread();
      return this.versionLink;
   }

   private void waitForThread() {
      if (this.thread != null && this.thread.isAlive()) {
         try {
            this.thread.join();
         } catch (InterruptedException var2) {
            this.plugin.getLogger().log(Level.SEVERE, (String)null, var2);
         }
      }

   }

   private void saveFile(String file) {
      File folder = this.updateFolder;
      this.deleteOldFiles();
      if (!folder.exists()) {
         this.fileIOOrError(folder, folder.mkdir(), true);
      }

      this.downloadFile();
      File dFile = new File(folder.getAbsolutePath(), file);
      if (dFile.getName().endsWith(".zip")) {
         this.unzip(dFile.getAbsolutePath());
      }

      if (this.announce) {
         this.plugin.getLogger().info("Finished updating.");
      }

   }

   private void downloadFile() {
      BufferedInputStream in = null;
      FileOutputStream fout = null;

      try {
         URL fileUrl = this.followRedirects(this.versionLink);
         int fileLength = fileUrl.openConnection().getContentLength();
         in = new BufferedInputStream(fileUrl.openStream());
         fout = new FileOutputStream(new File(this.updateFolder, this.file.getName()));
         byte[] data = new byte[1024];
         if (this.announce) {
            this.plugin.getLogger().info("About to download a new update: " + this.versionName);
         }

         long downloaded = 0L;

         int count;
         while((count = in.read(data, 0, 1024)) != -1) {
            downloaded += (long)count;
            fout.write(data, 0, count);
            int percent = (int)(downloaded * 100L / (long)fileLength);
            if (this.announce && percent % 10 == 0) {
               this.plugin.getLogger().info("Downloading update: " + percent + "% of " + fileLength + " bytes.");
            }
         }
      } catch (Exception var22) {
         this.plugin.getLogger().log(Level.WARNING, "The auto-updater tried to download a new update, but was unsuccessful.", var22);
         this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
      } finally {
         try {
            if (in != null) {
               in.close();
            }
         } catch (IOException var21) {
            this.plugin.getLogger().log(Level.SEVERE, (String)null, var21);
         }

         try {
            if (fout != null) {
               fout.close();
            }
         } catch (IOException var20) {
            this.plugin.getLogger().log(Level.SEVERE, (String)null, var20);
         }

      }

   }

   private URL followRedirects(String location) throws IOException {
      while(true) {
         URL resourceUrl = new URL(location);
         HttpURLConnection conn = (HttpURLConnection)resourceUrl.openConnection();
         conn.setConnectTimeout(15000);
         conn.setReadTimeout(15000);
         conn.setInstanceFollowRedirects(false);
         conn.setRequestProperty("User-Agent", "Mozilla/5.0...");
         switch(conn.getResponseCode()) {
         case 301:
         case 302:
            String redLoc = conn.getHeaderField("Location");
            URL base = new URL(location);
            URL next = new URL(base, redLoc);
            location = next.toExternalForm();
            break;
         default:
            return conn.getURL();
         }
      }
   }

   private void deleteOldFiles() {
      File[] list = this.listFilesOrError(this.updateFolder);
      File[] var2 = list;
      int var3 = list.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         File xFile = var2[var4];
         if (xFile.getName().endsWith(".zip")) {
            this.fileIOOrError(xFile, xFile.mkdir(), true);
         }
      }

   }

   private void unzip(String file) {
      File fSourceZip = new File(file);

      try {
         String zipPath = file.substring(0, file.length() - 4);
         ZipFile zipFile = new ZipFile(fSourceZip);
         Enumeration e = zipFile.entries();

         while(true) {
            ZipEntry entry;
            File destinationFilePath;
            do {
               if (!e.hasMoreElements()) {
                  zipFile.close();
                  this.moveNewZipFiles(zipPath);
                  return;
               }

               entry = (ZipEntry)e.nextElement();
               destinationFilePath = new File(zipPath, entry.getName());
               this.fileIOOrError(destinationFilePath.getParentFile(), destinationFilePath.getParentFile().mkdirs(), true);
            } while(entry.isDirectory());

            BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
            byte[] buffer = new byte[1024];
            FileOutputStream fos = new FileOutputStream(destinationFilePath);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 1024);

            int b;
            while((b = bis.read(buffer, 0, 1024)) != -1) {
               bos.write(buffer, 0, b);
            }

            bos.flush();
            bos.close();
            bis.close();
            String name = destinationFilePath.getName();
            if (name.endsWith(".jar") && this.pluginExists(name)) {
               File output = new File(this.updateFolder, name);
               this.fileIOOrError(output, destinationFilePath.renameTo(output), true);
            }
         }
      } catch (IOException var18) {
         this.plugin.getLogger().log(Level.SEVERE, "The auto-updater tried to unzip a new update file, but was unsuccessful.", var18);
         this.result = Updater.UpdateResult.FAIL_DOWNLOAD;
      } finally {
         this.fileIOOrError(fSourceZip, fSourceZip.delete(), false);
      }

   }

   private void moveNewZipFiles(String zipPath) {
      File[] list = this.listFilesOrError(new File(zipPath));
      File[] var3 = list;
      int var4 = list.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         File dFile = var3[var5];
         if (dFile.isDirectory() && this.pluginExists(dFile.getName())) {
            File oFile = new File(this.plugin.getDataFolder().getParent(), dFile.getName());
            File[] dList = this.listFilesOrError(dFile);
            File[] oList = this.listFilesOrError(oFile);
            File[] var10 = dList;
            int var11 = dList.length;

            for(int var12 = 0; var12 < var11; ++var12) {
               File cFile = var10[var12];
               boolean found = false;
               File[] var15 = oList;
               int var16 = oList.length;

               for(int var17 = 0; var17 < var16; ++var17) {
                  File xFile = var15[var17];
                  if (xFile.getName().equals(cFile.getName())) {
                     found = true;
                     break;
                  }
               }

               if (!found) {
                  File output = new File(oFile, cFile.getName());
                  this.fileIOOrError(output, cFile.renameTo(output), true);
               } else {
                  this.fileIOOrError(cFile, cFile.delete(), false);
               }
            }
         }

         this.fileIOOrError(dFile, dFile.delete(), false);
      }

      File zip = new File(zipPath);
      this.fileIOOrError(zip, zip.delete(), false);
   }

   private boolean pluginExists(String name) {
      File[] plugins = this.listFilesOrError(new File("plugins"));
      File[] var3 = plugins;
      int var4 = plugins.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         File file = var3[var5];
         if (file.getName().equals(name)) {
            return true;
         }
      }

      return false;
   }

   private boolean versionCheck() {
      String title = this.versionName;
      if (this.type != Updater.UpdateType.NO_VERSION_CHECK) {
         String localVersion = this.plugin.getDescription().getVersion();
         String remoteVersion;
         if (title.split("^v|[\\s_-]v").length < 2) {
            remoteVersion = this.plugin.getDescription().getAuthors().isEmpty() ? "" : " (" + (String)this.plugin.getDescription().getAuthors().get(0) + ")";
            this.plugin.getLogger().warning("The author of this plugin" + remoteVersion + " has misconfigured their Auto Update system");
            this.plugin.getLogger().warning("File versions should follow the format 'PluginName vVERSION'");
            this.plugin.getLogger().warning("Please notify the author of this error.");
            this.result = Updater.UpdateResult.FAIL_NOVERSION;
            return false;
         }

         remoteVersion = title.split("^v|[\\s_-]v")[title.split("^v|[\\s_-]v").length - 1].split(" ")[0];
         if (this.hasTag(localVersion) || !this.shouldUpdate(localVersion, remoteVersion)) {
            this.result = Updater.UpdateResult.NO_UPDATE;
            return false;
         }
      }

      return true;
   }

   public boolean shouldUpdate(String localVersion, String remoteVersion) {
      return !localVersion.equalsIgnoreCase(remoteVersion);
   }

   private boolean hasTag(String version) {
      String[] var2 = NO_UPDATE_TAG;
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         String string = var2[var4];
         if (version.contains(string)) {
            return true;
         }
      }

      return false;
   }

   private boolean read() {
      try {
         URLConnection conn = this.url.openConnection();
         conn.setConnectTimeout(5000);
         if (this.apiKey != null) {
            conn.addRequestProperty("X-API-Key", this.apiKey);
         }

         conn.addRequestProperty("User-Agent", "Updater (by Gravity)");
         conn.setDoOutput(true);
         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         String response = reader.readLine();
         JSONArray array = (JSONArray)JSONValue.parse(response);
         if (array.isEmpty()) {
            this.plugin.getLogger().warning("The updater could not find any files for the project id " + this.id);
            this.result = Updater.UpdateResult.FAIL_BADID;
            return false;
         } else {
            JSONObject latestUpdate = (JSONObject)array.get(array.size() - 1);
            this.versionName = (String)latestUpdate.get("name");
            this.versionLink = (String)latestUpdate.get("downloadUrl");
            this.versionType = (String)latestUpdate.get("releaseType");
            this.versionGameVersion = (String)latestUpdate.get("gameVersion");
            return true;
         }
      } catch (IOException var6) {
         if (var6.getMessage().contains("HTTP response code: 403")) {
            this.plugin.getLogger().severe("dev.bukkit.org rejected the API key provided in plugins/Updater/config.yml");
            this.plugin.getLogger().severe("Please double-check your configuration to ensure it is correct.");
            this.result = Updater.UpdateResult.FAIL_APIKEY;
         } else {
            this.plugin.getLogger().severe("The updater could not contact dev.bukkit.org for updating.");
            this.plugin.getLogger().severe("If you have not recently modified your configuration and this is the first time you are seeing this message, the site may be experiencing temporary downtime.");
            this.result = Updater.UpdateResult.FAIL_DBO;
         }

         this.plugin.getLogger().log(Level.SEVERE, (String)null, var6);
         return false;
      }
   }

   private void fileIOOrError(File file, boolean result, boolean create) {
      if (!result) {
         this.plugin.getLogger().severe("The updater could not " + (create ? "create" : "delete") + " file at: " + file.getAbsolutePath());
      }

   }

   private File[] listFilesOrError(File folder) {
      File[] contents = folder.listFiles();
      if (contents == null) {
         this.plugin.getLogger().severe("The updater could not access files at: " + this.updateFolder.getAbsolutePath());
         return new File[0];
      } else {
         return contents;
      }
   }

   private void runUpdater() {
      if (this.url != null && this.read() && this.versionCheck()) {
         if (this.versionLink != null && this.type != Updater.UpdateType.NO_DOWNLOAD) {
            String name = this.file.getName();
            if (this.versionLink.endsWith(".zip")) {
               name = this.versionLink.substring(this.versionLink.lastIndexOf("/") + 1);
            }

            this.saveFile(name);
         } else {
            this.result = Updater.UpdateResult.UPDATE_AVAILABLE;
         }
      }

      if (this.callback != null) {
         (new BukkitRunnable() {
            public void run() {
               Updater.this.runCallback();
            }
         }).runTask(this.plugin);
      }

   }

   private void runCallback() {
      this.callback.onFinish(this);
   }

   private class UpdateRunnable implements Runnable {
      private UpdateRunnable() {
      }

      public void run() {
         Updater.this.runUpdater();
      }

      // $FF: synthetic method
      UpdateRunnable(Object x1) {
         this();
      }
   }

   public interface UpdateCallback {
      void onFinish(Updater var1);
   }

   public static enum ReleaseType {
      ALPHA,
      BETA,
      RELEASE;
   }

   public static enum UpdateType {
      DEFAULT,
      NO_VERSION_CHECK,
      NO_DOWNLOAD;
   }

   public static enum UpdateResult {
      SUCCESS,
      NO_UPDATE,
      DISABLED,
      FAIL_DOWNLOAD,
      FAIL_DBO,
      FAIL_NOVERSION,
      FAIL_BADID,
      FAIL_APIKEY,
      UPDATE_AVAILABLE;
   }
}
