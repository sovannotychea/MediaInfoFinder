package edu.media.info.finder;
import com.google.gson.*;
import okhttp3.*;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class MusicTagEditor02 extends JFrame {

    // ============================================================
    // UI
    // ============================================================

    private final JTextField pathField =
            new JTextField();

    /*
     * File table:
     *
     * File | Track | Artist
     */
    private final DefaultTableModel fileTableModel =
            new DefaultTableModel(
                    new Object[]{
                            "File",
                            "Track",
                            "Artist"
                    },
                    0
            ) {

                @Override
                public boolean isCellEditable(
                        int row,
                        int column) {

                    return false;
                }
            };

    private final JTable fileTable =
            new JTable(fileTableModel);

    /*
     * iTunes result panel
     */
    private final JPanel resultPanel =
            new JPanel();

    private final JLabel statusLabel =
            new JLabel("Ready");

    // ============================================================
    // HTTP / JSON
    // ============================================================

    private final OkHttpClient httpClient =
            new OkHttpClient();

    private final Gson gson =
            new Gson();

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public MusicTagEditor02() {

        setTitle("Music Tag Editor");

        setSize(1200, 750);

        setLocationRelativeTo(null);

        setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        buildUI();
    }

    // ============================================================
    // BUILD UI
    // ============================================================

    private void buildUI() {

        JPanel main =
                new JPanel(
                        new BorderLayout(
                                10,
                                10
                        )
                );

        main.setBorder(
                new EmptyBorder(
                        10,
                        10,
                        10,
                        10
                )
        );

        // ========================================================
        // TOP
        // ========================================================

        JPanel top =
                new JPanel(
                        new BorderLayout(
                                5,
                                5
                        )
                );

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

        topButtons.add(
                browseButton
        );

        topButtons.add(
                scanButton
        );

        top.add(
                topButtons,
                BorderLayout.EAST
        );

        main.add(
                top,
                BorderLayout.NORTH
        );

        // ========================================================
        // FILE TABLE
        // ========================================================

        fileTable.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        fileTable.setAutoCreateRowSorter(true);

        fileTable.setRowHeight(25);

        fileTable.getColumnModel()
                .getColumn(0)
                .setPreferredWidth(350);

        fileTable.getColumnModel()
                .getColumn(1)
                .setPreferredWidth(220);

        fileTable.getColumnModel()
                .getColumn(2)
                .setPreferredWidth(180);

        JScrollPane fileScroll =
                new JScrollPane(fileTable);

        fileScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "Media Files"
                )
        );

        // ========================================================
        // ITUNES RESULTS
        // ========================================================

        resultPanel.setLayout(
                new BoxLayout(
                        resultPanel,
                        BoxLayout.Y_AXIS
                )
        );

        resultPanel.setBorder(
                new EmptyBorder(
                        5,
                        5,
                        5,
                        5
                )
        );

        JScrollPane resultScroll =
                new JScrollPane(
                        resultPanel
                );

        resultScroll.setBorder(
                BorderFactory.createTitledBorder(
                        "iTunes Results"
                )
        );

        // ========================================================
        // SPLIT
        // ========================================================

        JSplitPane splitPane =
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        fileScroll,
                        resultScroll
                );

        splitPane.setDividerLocation(500);

        main.add(
                splitPane,
                BorderLayout.CENTER
        );

        // ========================================================
        // BOTTOM
        // ========================================================

        JPanel bottom =
                new JPanel(
                        new BorderLayout()
                );

        JButton searchButton =
                new JButton(
                        "Search iTunes"
                );

        JButton refreshButton =
                new JButton(
                        "Refresh Tags"
                );

        JPanel buttonPanel =
                new JPanel();

        buttonPanel.add(
                searchButton
        );

        buttonPanel.add(
                refreshButton
        );

        bottom.add(
                buttonPanel,
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

        // ========================================================
        // EVENTS
        // ========================================================

        browseButton.addActionListener(
                e -> chooseFolder()
        );

        scanButton.addActionListener(
                e -> scanFiles()
        );

        searchButton.addActionListener(
                e -> searchSelectedFile()
        );

        refreshButton.addActionListener(
                e -> refreshTags()
        );

        /*
         * Selecting a file automatically searches iTunes.
         */
        fileTable.getSelectionModel()
                .addListSelectionListener(
                        e -> {

                            if (e.getValueIsAdjusting()) {
                                return;
                            }

                            int row =
                                    fileTable
                                            .getSelectedRow();

                            if (row < 0) {
                                return;
                            }

                            int modelRow =
                                    fileTable
                                            .convertRowIndexToModel(
                                                    row
                                            );

                            File file =
                                    (File)
                                            fileTableModel
                                                    .getValueAt(
                                                            modelRow,
                                                            0
                                                    );

                            searchITunes(file);
                        }
                );

        setContentPane(main);
    }

    // ============================================================
    // CHOOSE FOLDER
    // ============================================================

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

    // ============================================================
    // SCAN FILES
    // ============================================================

    private void scanFiles() {

        fileTableModel.setRowCount(0);

        resultPanel.removeAll();

        resultPanel.revalidate();
        resultPanel.repaint();

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
                                            MusicTagEditor02
                                                    ::isMediaFile
                                    )
                                    .forEach(
                                            p ->
                                                    files.add(
                                                            p.toFile()
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

                                Metadata metadata =
                                        readMetadata(
                                                file
                                        );

                                fileTableModel.addRow(
                                        new Object[]{
                                                file,
                                                metadata.track,
                                                metadata.artist
                                        }
                                );
                            }

                            statusLabel.setText(
                                    "Found "
                                            + files.size()
                                            + " media files"
                            );

                        } catch (Exception ex) {

                            showError(ex);
                        }
                    }
                };

        worker.execute();
    }

    // ============================================================
    // MEDIA FILE CHECK
    // ============================================================

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

    // ============================================================
    // READ EXISTING TAGS
    // ============================================================

    private Metadata readMetadata(
            File file) {

        Metadata metadata =
                new Metadata();

        try {

            AudioFile audioFile =
                    AudioFileIO.read(file);

            Tag tag =
                    audioFile.getTag();

            if (tag != null) {

                metadata.track =
                        tag.getFirst(
                                FieldKey.TITLE
                        );

                metadata.artist =
                        tag.getFirst(
                                FieldKey.ARTIST
                        );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Cannot read tags: "
                            + file.getName()
            );
        }

        return metadata;
    }

    // ============================================================
    // REFRESH TAGS
    // ============================================================

    private void refreshTags() {

        if (fileTableModel.getRowCount() == 0) {

            statusLabel.setText(
                    "No files to refresh"
            );

            return;
        }

        statusLabel.setText(
                "Refreshing tags..."
        );

        SwingWorker<Void, Void> worker =
                new SwingWorker<>() {

                    @Override
                    protected Void doInBackground() {

                        for (int i = 0;
                             i < fileTableModel
                                     .getRowCount();
                             i++) {

                            File file =
                                    (File)
                                            fileTableModel
                                                    .getValueAt(
                                                            i,
                                                            0
                                                    );

                            Metadata metadata =
                                    readMetadata(
                                            file
                                    );

                            fileTableModel.setValueAt(
                                    metadata.track,
                                    i,
                                    1
                            );

                            fileTableModel.setValueAt(
                                    metadata.artist,
                                    i,
                                    2
                            );
                        }

                        return null;
                    }

                    @Override
                    protected void done() {

                        statusLabel.setText(
                                "Tags refreshed"
                        );

                        fileTable.revalidate();
                        fileTable.repaint();
                    }
                };

        worker.execute();
    }

    // ============================================================
    // SEARCH SELECTED FILE
    // ============================================================

    private void searchSelectedFile() {

        File file =
                getSelectedFile();

        if (file == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Select a media file first."
            );

            return;
        }

        searchITunes(file);
    }

    // ============================================================
    // GET SELECTED FILE
    // ============================================================

    private File getSelectedFile() {

        int row =
                fileTable.getSelectedRow();

        if (row < 0) {
            return null;
        }

        int modelRow =
                fileTable.convertRowIndexToModel(
                        row
                );

        return (File)
                fileTableModel.getValueAt(
                        modelRow,
                        0
                );
    }

    // ============================================================
    // ITUNES SEARCH
    // ============================================================

    private void searchITunes(
            File file) {

        resultPanel.removeAll();

        resultPanel.revalidate();
        resultPanel.repaint();

        String searchText =
                getSearchText(file);

        statusLabel.setText(
                "Searching iTunes: "
                        + searchText
        );

        SwingWorker<
                List<TrackInfo>,
                Void
                > worker =
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
                                            + " iTunes results"
                            );

                        } catch (Exception ex) {

                            showError(ex);
                        }
                    }
                };

        worker.execute();
    }

    // ============================================================
    // SEARCH TEXT
    // ============================================================

    private String getSearchText(
            File file) {

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

        /*
         * Remove:
         *
         * 01 - Song
         * 01. Song
         * 01 Song
         */

        name =
                name.replaceFirst(
                        "^\\s*\\d+\\s*[-.]?\\s*",
                        ""
                );

        return name.trim();
    }

    // ============================================================
    // ITUNES API
    // ============================================================

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

    // ============================================================
    // PARSE ITUNES
    // ============================================================

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

    // ============================================================
    // SHOW RESULTS
    // ============================================================

    private void showResults(
            List<TrackInfo> results) {

        resultPanel.removeAll();

        for (TrackInfo track :
                results) {

            JPanel panel =
                    createResultPanel(
                            track
                    );

            resultPanel.add(panel);

            resultPanel.add(
                    Box.createVerticalStrut(8)
            );
        }

        resultPanel.revalidate();
        resultPanel.repaint();
    }

    // ============================================================
    // CREATE ITUNES RESULT
    // ============================================================

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

        // ========================================================
        // INFO
        // ========================================================

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

        // ========================================================
        // APPLY BUTTON
        // ========================================================

        JButton applyButton =
                new JButton(
                        "Apply to File"
                );

        applyButton.addActionListener(
                e -> {

                    File file =
                            getSelectedFile();

                    if (file == null) {

                        JOptionPane.showMessageDialog(
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

    // ============================================================
    // APPLY TRACK
    // ============================================================

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

            /*
             * Update table immediately.
             */
            updateTableMetadata(
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

    // ============================================================
    // UPDATE TABLE AFTER APPLY
    // ============================================================

    private void updateTableMetadata(
            File file,
            TrackInfo track) {

        for (int i = 0;
             i < fileTableModel.getRowCount();
             i++) {

            File tableFile =
                    (File)
                            fileTableModel
                                    .getValueAt(
                                            i,
                                            0
                                    );

            if (tableFile.equals(file)) {

                fileTableModel.setValueAt(
                        track.trackName,
                        i,
                        1
                );

                fileTableModel.setValueAt(
                        track.artistName,
                        i,
                        2
                );

                break;
            }
        }
    }

    // ============================================================
    // WRITE TAGS
    // ============================================================

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

        if (!isEmpty(
                track.trackName)) {

            tag.setField(
                    FieldKey.TITLE,
                    track.trackName
            );
        }

        // ARTIST

        if (!isEmpty(
                track.artistName)) {

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

        if (!isEmpty(
                track.genre)) {

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

    // ============================================================
    // HELPERS
    // ============================================================

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

    // ============================================================
    // ERROR
    // ============================================================

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

    // ============================================================
    // METADATA
    // ============================================================

    private static class Metadata {

        String track = "";
        String artist = "";
    }

    // ============================================================
    // ITUNES TRACK
    // ============================================================

    private static class TrackInfo {

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

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(
            String[] args) {

        SwingUtilities.invokeLater(
                () -> {

                    MusicTagEditor02 editor =
                            new MusicTagEditor02();

                    editor.setVisible(true);
                }
        );
    }
}
