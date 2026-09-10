package com.onikiri.open;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Moduleparse {

    private static final String PREFS = "settings";
    private static final String REPO_STANDARD = "repo_standard";
    private static final String REPO_URL = "repo_url";

    private static final String STANDARD_REPO =
            "dtech-special/Onikiri/refs/heads/main/modules.jsonl";

    private static final String MODULES_FILE = "modules.jsonl";
    private static final String SHA_FILE = "modules.sha";

    public static List<Module> loadModules(Context context) throws Exception {
        File modulesFile = new File(context.getFilesDir(), MODULES_FILE);
        File shaFile = new File(context.getFilesDir(), SHA_FILE);

        String repo = getRepository(context);
        String remoteSha = getRemoteSha(repo);

        if (!modulesFile.exists() || !shaFile.exists()) {
            downloadModules(repo, modulesFile);
            saveText(shaFile, remoteSha);
        } else if (!remoteSha.equals(readText(shaFile).trim())) {
            downloadModules(repo, modulesFile);
            saveText(shaFile, remoteSha);
        }

        return parse(modulesFile);
    }

    private static String getRepository(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (prefs.getBoolean(REPO_STANDARD, true)) {
            return STANDARD_REPO;
        }

        return prefs.getString(REPO_URL, "").trim();
    }

    private static String getRemoteSha(String repo) throws Exception {
        String[] parts = repo.split("/", 3);

        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid repository URL");
        }

        String owner = parts[0];
        String repository = parts[1];
        String path = parts[2];

        if (!path.startsWith("refs/heads/")) {
            throw new IllegalArgumentException("Invalid repository URL");
        }

        String branchAndFile = path.substring("refs/heads/".length());
        int slash = branchAndFile.indexOf('/');

        if (slash == -1) {
            throw new IllegalArgumentException("Invalid repository URL");
        }

        String branch = branchAndFile.substring(0, slash);
        String filePath = branchAndFile.substring(slash + 1);

        String apiUrl =
                "https://api.github.com/repos/"
                        + owner
                        + "/"
                        + repository
                        + "/contents/"
                        + filePath
                        + "?ref="
                        + branch;

        HttpURLConnection connection =
                (HttpURLConnection) new URL(apiUrl).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
        );

        try {
            String response = readStream(connection);

            return new JSONObject(response).getString("sha");
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadModules(
            String repo,
            File file
    ) throws Exception {

        String[] parts = repo.split("/", 3);

        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid repository URL");
        }

        String owner = parts[0];
        String repository = parts[1];
        String path = parts[2];

        if (!path.startsWith("refs/heads/")) {
            throw new IllegalArgumentException("Invalid repository URL");
        }

        String branchAndFile = path.substring("refs/heads/".length());
        int slash = branchAndFile.indexOf('/');

        if (slash == -1) {
            throw new IllegalArgumentException("Invalid repository URL");
        }

        String branch = branchAndFile.substring(0, slash);
        String filePath = branchAndFile.substring(slash + 1);

        String rawUrl =
                "https://raw.githubusercontent.com/"
                        + owner
                        + "/"
                        + repository
                        + "/"
                        + branch
                        + "/"
                        + filePath;

        HttpURLConnection connection =
                (HttpURLConnection) new URL(rawUrl).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );
                FileOutputStream output = new FileOutputStream(file)
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                output.write(
                        (line + "\n").getBytes(StandardCharsets.UTF_8)
                );
            }
        } finally {
            connection.disconnect();
        }
    }

    private static List<Module> parse(File file) throws Exception {
        List<Module> modules = new ArrayList<>();

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(file),
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                JSONObject json = new JSONObject(line);

                modules.add(
                        new Module(
                                json.getString("id"),
                                json.getString("title"),
                                json.getString("description"),
                                json.getString("github_repo"),
                                json.getString("status")
                        )
                );
            }
        }

        return modules;
    }

    private static String readStream(
            HttpURLConnection connection
    ) throws Exception {

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            return result.toString();
        }
    }

    private static void saveText(
            File file,
            String text
    ) throws Exception {

        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(File file) throws Exception {
        try (
                FileInputStream input = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                input,
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            return result.toString();
        }
    }
}