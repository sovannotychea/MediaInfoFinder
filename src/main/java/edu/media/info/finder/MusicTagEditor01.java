package edu.media.info.finder;

import com.google.gson.*;
import okhttp3.*;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class MusicTagEditor01 extends JFrame {

    // =========================
    // UI
    // =========================

    private final JTextField pathField = new JTextField();

    private final DefaultListModel<File> fileModel =
            new DefaultListModel<>();

    private final JList<File> fileList =
            new JList<>(fileModel);

    private final JPanel resultPanel =
            new JPanel();

    private final JLabel statusLabel =
            new JLabel("Ready");

    // =========================
    // HTTP / JSON
    // =========================

    private final OkHttpClient httpClient =
            new OkHttpClient();

    private final Gson gson =
            new Gson();

    // =========================
    // Constructor
    // =========================

    public MusicTagEditor01() {

        setTitle("Music Tag Editor");

        setSize(1200, 750);

        setLocationRelativeTo(null);

        setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        buildUI();
    }

    // =========================
    // BUILD UI
    // =========================

    private void buildUI() {

        JPanel main =
                new JPanel(new BorderLayout(10, 10));

        main.setBorder(
                new EmptyBorder(10, 10, 10, 10)
        );

        // ---------------------------------
        // TOP
        // ---------------------------------

        JPanel top =
                new JPanel(new BorderLayout(5, 5));

        JButton browseButton =
                new JButton("Browse...");

        JButton scanButton =
                new JButton("Scan");

        top.add(
                pathField,
                BorderLayout.CENTER
        );

        JPanel topButtons =
                new JPanel();

        topButtons.add(browseButton);
        topButtons.add(scanButton);

        top.add(
                topButtons,
                BorderLayout.EAST
        );

        main.add(
                top,
                BorderLayout.NORTH
        );

        // ---------------------------------
        // FILE LIST
        // ---------------------------------

        fileList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        fileList.setCellRenderer(
                new DefaultListCellRenderer() {

                    @Override
                    public Component
                    getListCellRendererComponent(
                            JList<?> list,
                            Object value,
                            int index,
                            boolean selected,
                            boolean focus) {

                        File file =
                                (File) value;

                        return super
                                .getListCellRendererComponent(
                                        list,
                                        file.getName(),
                                        index,
                                        selected,
                                        focus
                                );
                    }
                }
        );

        JScrollPane fileScroll =
                new JScrollPane(fileList);

        fileScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "Media Files"
                )
        );

        // ---------------------------------
        // ITUNES RESULTS
        // ---------------------------------

        resultPanel.setLayout(
                new BoxLayout(
                        resultPanel,
                        BoxLayout.Y_AXIS
                )
        );

        resultPanel.setBorder(
                new EmptyBorder(5, 5, 5, 5)
        );

        JScrollPane resultScroll =
                new JScrollPane(resultPanel);

        resultScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "iTunes Results"
                )
        );

        // ---------------------------------
        // SPLIT
        // ---------------------------------

        JSplitPane splitPane =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        fileScroll,
                        resultScroll
                );

        splitPane.setDividerLocation(400);

        main.add(
                splitPane,
                BorderLayout.CENTER
        );

        // ---------------------------------
        // BOTTOM
        // ---------------------------------

        JPanel bottom =
                new JPanel(new BorderLayout());

        JPanel buttons =
                new JPanel();

        JButton searchButton =
                new JButton("Search iTunes");

        buttons.add(searchButton);

        bottom.add(
                buttons,
                BorderLayout.CENTER
        );

        bottom.add(
                statusLabel,
                BorderLayout.SOUTH
        );

        main.add(
                bottom,
                BorderLayout.SOUTH
        );

        // ---------------------------------
        // EVENTS
        // ---------------------------------

        browseButton.addActionListener(
                e -> chooseFolder()
        );

        scanButton.addActionListener(
                e -> scanFiles()
        );

        searchButton.addActionListener(
                e -> searchSelectedFile()
        );

        fileList.addListSelectionListener(
                e -> {

                    if (!e.getValueIsAdjusting()) {

                        File file =
                                fileList.getSelectedValue();

                        if (file != null) {

                            searchITunes(file);
                        }
                    }
                }
        );

        setContentPane(main);
    }

    // =========================
    // CHOOSE FOLDER
    // =========================

    private void chooseFolder() {

        JFileChooser chooser =
                new JFileChooser();

        chooser.setFileSelectionMode(
                JFileChooser.DIRECTORIES_ONLY
        );

        if (chooser.showOpenDialog(this)
                == JFileChooser.APPROVE_OPTION) {

            pathField.setText(
                    chooser
                            .getSelectedFile()
                            .getAbsolutePath()
            );

            scanFiles();
        }
    }

    // =========================
    // SCAN FILES
    // =========================

    private void scanFiles() {

        fileModel.clear();

        String path =
                pathField.getText().trim();

        if (path.isEmpty()) {
            return;
        }

        File folder =
                new File(path);

        if (!folder.isDirectory()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Invalid folder."
            );

            return;
        }

        statusLabel.setText(
                "Scanning..."
        );

        SwingWorker<List<File>, Void> worker =
                new SwingWorker<>() {

                    @Override
                    protected List<File>
                    doInBackground()
                            throws Exception {

                        List<File> files =
                                new ArrayList<>();

                        try (var stream =
                                     Files.walk(
                                             folder.toPath()
                                     )) {

                            stream
                                    .filter(
                                            Files::isRegularFile
                                    )
                                    .filter(
                                    		MusicTagEditor01
                                                    ::isMediaFile
                                    )
                                    .forEach(
                                            path ->
                                                    files.add(
                                                            path.toFile()
                                                    )
                                    );
                        }

                        return files;
                    }

                    @Override
                    protected void done() {

                        try {

                            List<File> files =
                                    get();

                            for (File file :
                                    files) {

                                fileModel.addElement(
                                        file
                                );
                            }

                            statusLabel.setText(
                                    "Found "
                                            + files.size()
                                            + " files"
                            );

                        } catch (Exception ex) {

                            showError(ex);
                        }
                    }
                };

        worker.execute();
    }

    // =========================
    // MEDIA FILE CHECK
    // =========================

    private static boolean
    isMediaFile(Path path) {

        String name =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        return name.endsWith(".mp3")
                || name.endsWith(".flac")
                || name.endsWith(".m4a");
    }

    // =========================
    // SEARCH SELECTED FILE
    // =========================

    private void searchSelectedFile() {

        File file =
                fileList.getSelectedValue();

        if (file == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Select a media file first."
            );

            return;
        }

        searchITunes(file);
    }

    // =========================
    // ITUNES SEARCH
    // =========================

    private void searchITunes(File file) {

        resultPanel.removeAll();

        resultPanel.revalidate();
        resultPanel.repaint();

        String searchText =
                getSearchText(file);

        statusLabel.setText(
                "Searching iTunes: "
                        + searchText
        );

        SwingWorker<List<TrackInfo>, Void>
                worker =
                new SwingWorker<>() {

                    @Override
                    protected List<TrackInfo>
                    doInBackground()
                            throws Exception {

                        return searchITunesAPI(
                                searchText
                        );
                    }

                    @Override
                    protected void done() {

                        try {

                            List<TrackInfo> results =
                                    get();

                            showResults(
                                    results
                            );

                            statusLabel.setText(
                                    "Found "
                                            + results.size()
                                            + " results"
                            );

                        } catch (Exception ex) {

                            showError(ex);
                        }
                    }
                };

        worker.execute();
    }

    // =========================
    // SEARCH TEXT
    // =========================

    private String
    getSearchText(File file) {

        String name =
                file.getName();

        int dot =
                name.lastIndexOf('.');

        if (dot > 0) {

            name =
                    name.substring(
                            0,
                            dot
                    );
        }

        // Remove:
        //
        // 01 - Song
        // 01. Song
        // 01 Song

        name =
                name.replaceFirst(
                        "^\\s*\\d+\\s*[-.]?\\s*",
                        ""
                );

        return name.trim();
    }

    // =========================
    // ITUNES API
    // =========================

    private List<TrackInfo>
    searchITunesAPI(
            String searchText)
            throws IOException {

        String encoded =
                URLEncoder.encode(
                        searchText,
                        StandardCharsets.UTF_8
                );

        String url =
                "https://itunes.apple.com/search"
                        + "?term="
                        + encoded
                        + "&media=music"
                        + "&entity=song"
                        + "&limit=20";

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .build();

        try (Response response =
                     httpClient
                             .newCall(request)
                             .execute()) {

            if (!response.isSuccessful()) {

                throw new IOException(
                        "HTTP "
                                + response.code()
                );
            }

            if (response.body() == null) {

                throw new IOException(
                        "Empty response"
                );
            }

            String json =
                    response.body().string();

            return parseITunes(json);
        }
    }

    // =========================
    // PARSE ITUNES JSON
    // =========================

    private List<TrackInfo>
    parseITunes(String json) {

        List<TrackInfo> results =
                new ArrayList<>();

        JsonObject root =
                gson.fromJson(
                        json,
                        JsonObject.class
                );

        JsonArray array =
                root.getAsJsonArray(
                        "results"
                );

        if (array == null) {
            return results;
        }

        for (JsonElement element :
                array) {

            JsonObject obj =
                    element.getAsJsonObject();

            TrackInfo track =
                    new TrackInfo();

            track.trackName =
                    getString(
                            obj,
                            "trackName"
                    );

            track.artistName =
                    getString(
                            obj,
                            "artistName"
                    );

            track.collectionName =
                    getString(
                            obj,
                            "collectionName"
                    );

            track.genre =
                    getString(
                            obj,
                            "primaryGenreName"
                    );

            track.releaseDate =
                    getString(
                            obj,
                            "releaseDate"
                    );

            track.trackNumber =
                    getInt(
                            obj,
                            "trackNumber"
                    );

            track.discNumber =
                    getInt(
                            obj,
                            "discNumber"
                    );

            track.artworkUrl =
                    getString(
                            obj,
                            "artworkUrl100"
                    );

            track.trackUrl =
                    getString(
                            obj,
                            "trackViewUrl"
                    );

            results.add(track);
        }

        return results;
    }

    // =========================
    // SHOW RESULTS
    // =========================

    private void showResults(
            List<TrackInfo> results) {

        resultPanel.removeAll();

        for (TrackInfo track :
                results) {

            JPanel panel =
                    createResultPanel(track);

            resultPanel.add(panel);

            resultPanel.add(
                    Box.createVerticalStrut(8)
            );
        }

        resultPanel.revalidate();
        resultPanel.repaint();
    }

    // =========================
    // RESULT PANEL
    // =========================

    private JPanel createResultPanel(
            TrackInfo track) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                10,
                                5
                        )
                );

        panel.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        110
                )
        );

        panel.setBorder(
                BorderFactory
                        .createCompoundBorder(
                                BorderFactory
                                        .createLineBorder(
                                                Color.LIGHT_GRAY
                                        ),
                                BorderFactory
                                        .createEmptyBorder(
                                                8,
                                                8,
                                                8,
                                                8
                                        )
                        )
        );

        // -------------------------
        // INFORMATION
        // -------------------------

        JPanel info =
                new JPanel();

        info.setLayout(
                new BoxLayout(
                        info,
                        BoxLayout.Y_AXIS
                )
        );

        JLabel title =
                new JLabel(
                        "Track: "
                                + track.trackName
                );

        JLabel artist =
                new JLabel(
                        "Artist: "
                                + track.artistName
                );

        JLabel album =
                new JLabel(
                        "Album: "
                                + track.collectionName
                );

        JLabel details =
                new JLabel(
                        "Track #"
                                + track.trackNumber
                                + "   "
                                + track.genre
                );

        info.add(title);
        info.add(artist);
        info.add(album);
        info.add(details);

        // -------------------------
        // APPLY BUTTON
        // -------------------------

        JButton applyButton =
                new JButton(
                        "Apply to File"
                );

        applyButton.addActionListener(
                e -> {

                    File file =
                            fileList
                                    .getSelectedValue();

                    if (file == null) {

                        JOptionPane
                                .showMessageDialog(
                                        this,
                                        "Select a file first."
                                );

                        return;
                    }

                    applyTrackToFile(
                            file,
                            track
                    );
                }
        );

        panel.add(
                info,
                BorderLayout.CENTER
        );

        panel.add(
                applyButton,
                BorderLayout.EAST
        );

        return panel;
    }

    // =========================
    // APPLY TAG
    // =========================

    private void applyTrackToFile(
            File file,
            TrackInfo track) {

        int answer =
                JOptionPane.showConfirmDialog(
                        this,

                        "Apply this metadata?\n\n"
                                + "File: "
                                + file.getName()
                                + "\n\n"
                                + "Track: "
                                + track.trackName
                                + "\n"
                                + "Artist: "
                                + track.artistName
                                + "\n"
                                + "Album: "
                                + track.collectionName,

                        "Apply Metadata",

                        JOptionPane.YES_NO_OPTION
                );

        if (answer !=
                JOptionPane.YES_OPTION) {

            return;
        }

        try {

            writeTags(
                    file,
                    track
            );

            statusLabel.setText(
                    "Updated: "
                            + file.getName()
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Metadata applied successfully."
            );

        } catch (Exception ex) {

            showError(ex);
        }
    }

    // =========================
    // WRITE TAGS
    // =========================

    private void writeTags(
            File file,
            TrackInfo track)
            throws Exception {

        AudioFile audioFile =
                AudioFileIO.read(file);

        Tag tag =
                audioFile
                        .getTagOrCreateAndSetDefault();

        // TITLE

        if (!isEmpty(track.trackName)) {

            tag.setField(
                    FieldKey.TITLE,
                    track.trackName
            );
        }

        // ARTIST

        if (!isEmpty(track.artistName)) {

            tag.setField(
                    FieldKey.ARTIST,
                    track.artistName
            );

            tag.setField(
                    FieldKey.ALBUM_ARTIST,
                    track.artistName
            );
        }

        // ALBUM

        if (!isEmpty(
                track.collectionName)) {

            tag.setField(
                    FieldKey.ALBUM,
                    track.collectionName
            );
        }

        // GENRE

        if (!isEmpty(track.genre)) {

            tag.setField(
                    FieldKey.GENRE,
                    track.genre
            );
        }

        // TRACK NUMBER

        if (track.trackNumber > 0) {

            tag.setField(
                    FieldKey.TRACK,
                    String.valueOf(
                            track.trackNumber
                    )
            );
        }

        // DISC NUMBER

        if (track.discNumber > 0) {

            tag.setField(
                    FieldKey.DISC_NO,
                    String.valueOf(
                            track.discNumber
                    )
            );
        }

        // YEAR

        if (!isEmpty(
                track.releaseDate)) {

            String year =
                    track.releaseDate
                            .substring(
                                    0,
                                    Math.min(
                                            4,
                                            track.releaseDate
                                                    .length()
                                    )
                            );

            tag.setField(
                    FieldKey.YEAR,
                    year
            );
        }

        // SAVE

        AudioFileIO.write(
                audioFile
        );
    }

    // =========================
    // HELPERS
    // =========================

    private static boolean isEmpty(
            String value) {

        return value == null
                || value.isBlank();
    }

    private String getString(
            JsonObject obj,
            String key) {

        if (!obj.has(key)
                || obj.get(key).isJsonNull()) {

            return "";
        }

        return obj
                .get(key)
                .getAsString();
    }

    private int getInt(
            JsonObject obj,
            String key) {

        if (!obj.has(key)
                || obj.get(key).isJsonNull()) {

            return 0;
        }

        return obj
                .get(key)
                .getAsInt();
    }

    // =========================
    // ERROR
    // =========================

    private void showError(
            Exception ex) {

        ex.printStackTrace();

        JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    // =========================
    // TRACK INFO
    // =========================

    public static class TrackInfo {

        String trackName = "";
        String artistName = "";
        String collectionName = "";

        String genre = "";
        String releaseDate = "";

        String artworkUrl = "";
        String trackUrl = "";

        int trackNumber;
        int discNumber;
    }

    // =========================
    // MAIN
    // =========================

    public static void main(
            String[] args) {

        SwingUtilities.invokeLater(
                () -> {

                	MusicTagEditor01 editor =
                            new MusicTagEditor01();

                    editor.setVisible(true);
                }
        );
    }
}