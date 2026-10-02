/**
 * 
 */
package edu.media.info.finder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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

public class MusicTagEditorMainUI extends JFrame {

	private final JTextField pathField = new JTextField();

	private final DefaultListModel<File> fileModel = new DefaultListModel<>();

	private final JList<File> fileList = new JList<>(fileModel);

	private final DefaultListModel<TrackInfo> resultModel = new DefaultListModel<>();

	private final JList<TrackInfo> resultList = new JList<>(resultModel);

	private final JTextField trackNameField = new JTextField();
	private final JTextField artistNameField = new JTextField();
	private final JTextField collectionNameField = new JTextField();

	private final JLabel statusLabel = new JLabel("Ready");

	private final OkHttpClient httpClient = new OkHttpClient();

	private final Gson gson = new Gson();

	public MusicTagEditorMainUI() {

		setTitle("Music Tag Editor");
		setSize(1100, 700);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		buildUI();
	}

	private void buildUI() {

		JPanel main = new JPanel(new BorderLayout(10, 10));
		main.setBorder(new EmptyBorder(10, 10, 10, 10));

		/*
		 * TOP
		 */
		JPanel top = new JPanel(new BorderLayout(5, 5));

		JButton browseButton = new JButton("Browse...");
		JButton scanButton = new JButton("Scan");

		top.add(pathField, BorderLayout.CENTER);

		JPanel topButtons = new JPanel();
		topButtons.add(browseButton);
		topButtons.add(scanButton);

		top.add(topButtons, BorderLayout.EAST);

		main.add(top, BorderLayout.NORTH);

		/*
		 * FILE LIST
		 */
		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		fileList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {

				File file = (File) value;

				return super.getListCellRendererComponent(list, file.getName(), index, isSelected, cellHasFocus);
			}
		});

		JScrollPane fileScroll = new JScrollPane(fileList);
		fileScroll.setBorder(BorderFactory.createTitledBorder("Media Files"));

		/*
		 * ITUNES RESULTS
		 */
		resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		resultList.setCellRenderer(new DefaultListCellRenderer() {

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {

				TrackInfo track = (TrackInfo) value;

				String text = track.trackName + " - " + track.artistName + " [" + track.collectionName + "]";

				return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
			}
		});

		JScrollPane resultScroll = new JScrollPane(resultList);
		resultScroll.setBorder(BorderFactory.createTitledBorder("iTunes Results"));

		/*
		 * CENTER
		 */
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileScroll, resultScroll);

		splitPane.setDividerLocation(450);

		main.add(splitPane, BorderLayout.CENTER);

		/*
		 * BOTTOM
		 */
		JPanel bottom = new JPanel(new BorderLayout(5, 5));

		JPanel fields = new JPanel(new GridLayout(3, 2, 5, 5));

		fields.setBorder(BorderFactory.createTitledBorder("Selected Metadata"));

		fields.add(new JLabel("Track Name"));
		fields.add(trackNameField);

		fields.add(new JLabel("Artist Name"));
		fields.add(artistNameField);

		fields.add(new JLabel("Collection Name"));
		fields.add(collectionNameField);

		bottom.add(fields, BorderLayout.CENTER);

		JPanel buttons = new JPanel();

		JButton searchButton = new JButton("Search iTunes");

		JButton applyButton = new JButton("Apply to File");

		buttons.add(searchButton);
		buttons.add(applyButton);

		bottom.add(buttons, BorderLayout.SOUTH);

		main.add(bottom, BorderLayout.SOUTH);

		/*
		 * EVENTS
		 */

		browseButton.addActionListener(e -> chooseFolder());

		scanButton.addActionListener(e -> scanFiles());

		searchButton.addActionListener(e -> searchSelectedFile());

		applyButton.addActionListener(e -> applyMetadata());

		fileList.addListSelectionListener(e -> {

			if (!e.getValueIsAdjusting()) {

				File file = fileList.getSelectedValue();

				if (file != null) {

					statusLabel.setText("Selected: " + file.getName());

					searchITunes(file);
				}
			}
		});

		resultList.addListSelectionListener(e -> {

			if (!e.getValueIsAdjusting()) {

				TrackInfo track = resultList.getSelectedValue();

				if (track != null) {

					trackNameField.setText(track.trackName);

					artistNameField.setText(track.artistName);

					collectionNameField.setText(track.collectionName);
				}
			}
		});

		JPanel statusPanel = new JPanel(new BorderLayout());

		statusPanel.add(statusLabel, BorderLayout.WEST);

		main.add(statusPanel, BorderLayout.SOUTH);

		setContentPane(main);
	}

	/*
	 * SELECT FOLDER
	 */
	private void chooseFolder() {

		JFileChooser chooser = new JFileChooser();

		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {

			pathField.setText(chooser.getSelectedFile().getAbsolutePath());

			scanFiles();
		}
	}

	/*
	 * SCAN MEDIA FILES
	 */
	private void scanFiles() {

		fileModel.clear();

		String path = pathField.getText().trim();

		if (path.isEmpty()) {
			return;
		}

		File folder = new File(path);

		if (!folder.isDirectory()) {

			JOptionPane.showMessageDialog(this, "Invalid folder");

			return;
		}

		statusLabel.setText("Scanning...");

		SwingWorker<List<File>, Void> worker = new SwingWorker<>() {

			@Override
			protected List<File> doInBackground() throws Exception {

				List<File> files = new ArrayList<>();

				Files.walk(folder.toPath()).filter(Files::isRegularFile).filter(MusicTagEditorMainUI::isMediaFile)
						.forEach(p -> files.add(p.toFile()));

				return files;
			}

			@Override
			protected void done() {

				try {

					List<File> files = get();

					for (File file : files) {
						fileModel.addElement(file);
					}

					statusLabel.setText("Found " + files.size() + " media files");

				} catch (Exception ex) {

					showError(ex);
				}
			}
		};

		worker.execute();
	}

	private static boolean isMediaFile(Path path) {

		String name = path.getFileName().toString().toLowerCase();

		return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".m4a");
	}

	/*
	 * SEARCH BUTTON
	 */
	private void searchSelectedFile() {

		File file = fileList.getSelectedValue();

		if (file == null) {

			JOptionPane.showMessageDialog(this, "Select a media file first.");

			return;
		}

		searchITunes(file);
	}

	/*
	 * SEARCH ITUNES
	 */
	private void searchITunes(File file) {

		resultModel.clear();

		String searchText = getSearchText(file);

		statusLabel.setText("Searching iTunes: " + searchText);

		SwingWorker<List<TrackInfo>, Void> worker = new SwingWorker<>() {

			@Override
			protected List<TrackInfo> doInBackground() throws Exception {

				return searchITunesAPI(searchText);
			}

			@Override
			protected void done() {

				try {

					List<TrackInfo> results = get();

					for (TrackInfo track : results) {
						resultModel.addElement(track);
					}

					if (!results.isEmpty()) {

						resultList.setSelectedIndex(0);

						statusLabel.setText("Found " + results.size() + " iTunes results");

					} else {

						statusLabel.setText("No iTunes results");
					}

				} catch (Exception ex) {

					showError(ex);
				}
			}
		};

		worker.execute();
	}

	/*
	 * CREATE SEARCH TEXT FROM FILE NAME
	 */
	private String getSearchText(File file) {

		String name = file.getName();

		int dot = name.lastIndexOf('.');

		if (dot > 0) {
			name = name.substring(0, dot);
		}

		/*
		 * Remove common track-number prefixes:
		 *
		 * 01 - Wonderful Tonight 01. Wonderful Tonight 01 Wonderful Tonight
		 */
		name = name.replaceFirst("^\\s*\\d+\\s*[-.]?\\s*", "");

		return name.trim();
	}

	/*
	 * CALL ITUNES API
	 */
	private List<TrackInfo> searchITunesAPI(String searchText) throws IOException {

		String encoded = URLEncoder.encode(searchText, StandardCharsets.UTF_8);

		String url = "https://itunes.apple.com/search" + "?term=" + encoded + "&media=music" + "&entity=song"
				+ "&limit=20";

		Request request = new Request.Builder().url(url).get().build();

		try (Response response = httpClient.newCall(request).execute()) {

			if (!response.isSuccessful()) {

				throw new IOException("HTTP " + response.code());
			}

			String json = response.body().string();

			return parseITunes(json);
		}
	}

	/*
	 * PARSE JSON
	 */
	private List<TrackInfo> parseITunes(String json) {

		List<TrackInfo> results = new ArrayList<>();

		JsonObject root = gson.fromJson(json, JsonObject.class);

		JsonArray array = root.getAsJsonArray("results");

		if (array == null) {
			return results;
		}

		for (JsonElement element : array) {

			JsonObject obj = element.getAsJsonObject();

			TrackInfo track = new TrackInfo();

			track.trackName = getString(obj, "trackName");

			track.artistName = getString(obj, "artistName");

			track.collectionName = getString(obj, "collectionName");

			track.albumArtist = getString(obj, "artistName");

			track.trackNumber = getInt(obj, "trackNumber");

			track.discNumber = getInt(obj, "discNumber");

			track.genre = getString(obj, "primaryGenreName");

			track.releaseDate = getString(obj, "releaseDate");

			track.itunesUrl = getString(obj, "trackViewUrl");

			results.add(track);
		}

		return results;
	}

	private String getString(JsonObject obj, String key) {

		if (!obj.has(key) || obj.get(key).isJsonNull()) {

			return "";
		}

		return obj.get(key).getAsString();
	}

	private int getInt(JsonObject obj, String key) {

		if (!obj.has(key) || obj.get(key).isJsonNull()) {

			return 0;
		}

		return obj.get(key).getAsInt();
	}

	/*
	 * WRITE TAGS
	 */
	private void applyMetadata() {

		File file = fileList.getSelectedValue();

		if (file == null) {

			JOptionPane.showMessageDialog(this, "Select a file first.");

			return;
		}

		String title = trackNameField.getText().trim();

		String artist = artistNameField.getText().trim();

		String album = collectionNameField.getText().trim();

		if (title.isEmpty() && artist.isEmpty() && album.isEmpty()) {

			JOptionPane.showMessageDialog(this, "No metadata selected.");

			return;
		}

		int answer = JOptionPane.showConfirmDialog(this, "Write metadata to:\n\n" + file.getName() + "\n\n" + "Track: "
				+ title + "\n" + "Artist: " + artist + "\n" + "Album: " + album, "Confirm", JOptionPane.YES_NO_OPTION);

		if (answer != JOptionPane.YES_OPTION) {
			return;
		}

		try {

			writeTags(file, title, artist, album);

			statusLabel.setText("Metadata saved: " + file.getName());

			JOptionPane.showMessageDialog(this, "Metadata saved successfully.");

		} catch (Exception ex) {

			showError(ex);
		}
	}

	/*
	 * JAUDIOTAGGER
	 */
	private void writeTags(File file, String title, String artist, String album) throws Exception {

		AudioFile audioFile = AudioFileIO.read(file);

		Tag tag = audioFile.getTagOrCreateAndSetDefault();

		if (!title.isEmpty()) {

			tag.setField(FieldKey.TITLE, title);
		}

		if (!artist.isEmpty()) {

			tag.setField(FieldKey.ARTIST, artist);

			tag.setField(FieldKey.ALBUM_ARTIST, artist);
		}

		if (!album.isEmpty()) {

			tag.setField(FieldKey.ALBUM, album);
		}

		AudioFileIO.write(audioFile);
	}

	private void showError(Exception ex) {

		ex.printStackTrace();

		JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	}

	/*
	 * TRACK DATA
	 */
	public static class TrackInfo {

		String trackName;
		String artistName;
		String collectionName;

		String albumArtist;
		String genre;
		String releaseDate;
		String itunesUrl;

		int trackNumber;
		int discNumber;

		@Override
		public String toString() {

			return trackName + " - " + artistName + " [" + collectionName + "]";
		}
	}

	/*
	 * MAIN
	 */
	public static void main(String[] args) {

		SwingUtilities.invokeLater(() -> {

			MusicTagEditorMainUI app = new MusicTagEditorMainUI();

			app.setVisible(true);
		});
	}
}
