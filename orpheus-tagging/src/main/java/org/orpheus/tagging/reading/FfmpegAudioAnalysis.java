package org.orpheus.tagging.reading;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.orpheus.domain.port.AudioAnalysisPort;

/**
 * Integrity analysis with a full decoding through ffmpeg (ADR-0023): the audio comes out mono at
 * 11025 Hz in 16-bit PCM, the same parameters as the calibration, and is read as a stream without
 * keeping it in memory. From stderr ({@code -loglevel level+info}, which prefixes every line with its
 * level) come the declared duration and the decoder error lines, whatever the format.
 */
@ApplicationScoped
public class FfmpegAudioAnalysis implements AudioAnalysisPort {

	private static final Logger LOG = Logger.getLogger(FfmpegAudioAnalysis.class);
	private static final long TIMEOUT_SECONDS = 300;
	static final int SAMPLE_RATE = 11025;
	// A sample with |x| <= 1 on 16 bits is digital silence: the quantization noise of the resampling does
	// not exceed 1.
	private static final int ZERO_THRESHOLD = 1;
	private static final int MIN_DROPOUT = (int) (0.25 * SAMPLE_RATE);
	private static final int MARGIN = 2 * SAMPLE_RATE;
	private static final int WINDOW_BEFORE = (int) (0.02 * SAMPLE_RATE);
	private static final int FINAL_WINDOW = (int) (0.1 * SAMPLE_RATE);
	private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");

	@Override
	public Optional<Analysis> analyze(Path file) {
		Process process;
		try {
			process = new ProcessBuilder("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "level+info", "-skip_initial_bytes", String.valueOf(id3BeforeRiff(file)), "-i", file.toString(), "-map", "0:a:0", "-ac", "1", "-ar", String.valueOf(SAMPLE_RATE), "-f", "s16le", "-")
			                                                                                                                                                                                                                                                                     .start();
		} catch (IOException e) {
			LOG.warn("ffmpeg not available, integrity analysis skipped for " + file, e);
			return Optional.empty();
		}
		StderrReader stderr = new StderrReader(process.getErrorStream());
		Thread reader = Thread.ofVirtual().start(stderr);
		try {
			Samples samples = new Samples();
			try (InputStream in = new BufferedInputStream(process.getInputStream(), 1 << 16)) {
				samples.read(in);
			}
			if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				LOG.warn("ffmpeg did not finish within the timeout for " + file);
				return Optional.empty();
			}
			reader.join();
			if (samples.total == 0) {
				return Optional.empty();
			}
			return Optional.of(new Analysis(stderr.duration, samples.total / (double) SAMPLE_RATE, stderr.errors, samples.dropouts(), samples.finalLevel(), samples.tones.sequences()));
		} catch (IOException e) {
			LOG.warn("Error reading the decoded audio of " + file, e);
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return Optional.empty();
		}
	}

	/**
	 * Bytes to skip for a file with an ID3v2 tag in front of a RIFF/WAVE container (12 Nashville Pussy MP3s
	 * on the real run): ffmpeg does not recognize the format while the tag is at the start. Zero
	 * otherwise.
	 */
	static long id3BeforeRiff(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] head = in.readNBytes(10);
			if (head.length < 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') {
				return 0;
			}
			long size = 10 + (((head[6] & 0x7f) << 21) | ((head[7] & 0x7f) << 14) | ((head[8] & 0x7f) << 7) | (head[9] & 0x7f));
			if ((head[5] & 0x10) != 0) {
				size += 10;
			}
			in.skipNBytes(size - 10);
			byte[] riff = in.readNBytes(4);
			return riff.length == 4 && riff[0] == 'R' && riff[1] == 'I' && riff[2] == 'F' && riff[3] == 'F' ? size : 0;
		} catch (IOException e) {
			return 0;
		}
	}

	private static final class StderrReader implements Runnable {

		private final InputStream in;
		private Optional<Double> duration = Optional.empty();
		private int errors;

		StderrReader(InputStream in) {
			this.in = in;
		}

		@Override
		public void run() {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					if (line.contains("[error]")) {
						errors++;
					} else if (duration.isEmpty()) {
						Matcher m = DURATION.matcher(line);
						if (m.find()) {
							duration = Optional.of(Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3)));
						}
					}
				}
			} catch (IOException e) {
				LOG.debug("Reading of the ffmpeg stderr interrupted", e);
			}
		}
	}

	/** Goes through the samples once: digital silences and level of the last 100 ms. */
	static final class Samples {

		private final int[] last = new int[FINAL_WINDOW];
		private long total;
		private long zerosStart = -1;
		private double levelBefore;
		private final List<double[]> silences = new ArrayList<>();

		void read(InputStream in) throws IOException {
			byte[] buffer = new byte[1 << 16];
			int leftover = -1;
			int read;
			while ((read = in.read(buffer)) != -1) {
				int i = 0;
				if (leftover >= 0 && read > 0) {
					add((short) ((buffer[0] << 8) | leftover));
					leftover = -1;
					i = 1;
				}
				for (; i + 1 < read; i += 2) {
					add((short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff)));
				}
				if (i < read) {
					leftover = buffer[i] & 0xff;
				}
			}
			closeSilence();
		}

		final SitTones tones = new SitTones();

		void add(int x) {
			tones.add(x);
			if (Math.abs(x) <= ZERO_THRESHOLD) {
				if (zerosStart < 0) {
					zerosStart = total;
					levelBefore = level(WINDOW_BEFORE);
				}
			} else {
				closeSilence();
			}
			last[(int) (total % last.length)] = x;
			total++;
		}

		private void closeSilence() {
			if (zerosStart >= 0 && total - zerosStart >= MIN_DROPOUT && zerosStart > 0) {
				silences.add(new double[]{
				                          zerosStart,
				                          total - zerosStart,
				                          levelBefore
				});
			}
			zerosStart = -1;
		}

		// RMS of the last n samples already read, in dBFS; -200 if there are none.
		double level(int n) {
			int available = (int) Math.min(n, total);
			if (available == 0) {
				return -200.0;
			}
			double sum = 0;
			for (int k = 1; k <= available; k++) {
				double v = last[(int) ((total - k) % last.length)];
				sum += v * v;
			}
			return 20 * Math.log10(Math.sqrt(sum / available) / 32768.0 + 1e-9);
		}

		double finalLevel() {
			return level(FINAL_WINDOW);
		}

		List<Dropout> dropouts() {
			List<Dropout> dropouts = new ArrayList<>();
			for (double[] s : silences) {
				if (s[0] > MARGIN && s[0] + s[1] < total - MARGIN) {
					dropouts.add(new Dropout(s[0] / SAMPLE_RATE, s[1] / SAMPLE_RATE, s[2]));
				}
			}
			return dropouts;
		}
	}

	/**
	 * SIT tone sequences (913.8 / 1370.6 / 1776.7 Hz, measured 920 / 1420 / 1780 in "Farther Away"): 50 ms
	 * windows where more than 90% of the energy is in one of the three bands, for at least 3 windows in a
	 * row, and the three bands in order within 1.5 s. Calibrated on the library on 2026-10-04 (ADR-0023):
	 * a single file with sequences, while pure tones in general appear in 438 songs and say nothing on
	 * their own.
	 */
	static final class SitTones {

		private static final int WINDOW = 551;
		private static final double[][] BANDS = {
		                                         {
		                                          870,
		                                          960
												 },
		                                         {
		                                          1320,
		                                          1460
												 },
		                                         {
		                                          1730,
		                                          1830
												 }
		};
		private static final double THRESHOLD = 0.9;
		private static final int MIN_WINDOWS = 3;
		private static final double MAX_DISTANCE_SECONDS = 1.5;
		private static final double[] HANN = new double[WINDOW];

		static {
			for (int n = 0; n < WINDOW; n++) {
				HANN[n] = 0.5 - 0.5 * Math.cos(2 * Math.PI * n / (WINDOW - 1));
			}
		}

		private final double[] samples = new double[WINDOW];
		private int filled;
		private long windows;
		private int currentBand = -1;
		private int length;
		private long currentStart;
		private final List<double[]> runs = new ArrayList<>();

		void add(int x) {
			samples[filled++] = x / 32768.0;
			if (filled == WINDOW) {
				closeWindow(dominantBand());
				filled = 0;
				windows++;
			}
		}

		private int dominantBand() {
			double raw = 0;
			double energy = 0;
			for (int n = 0; n < WINDOW; n++) {
				raw += samples[n] * samples[n];
				double v = samples[n] * HANN[n];
				energy += v * v;
			}
			if (Math.sqrt(raw / WINDOW) <= 0.01 || energy == 0) {
				return -1;
			}
			// Parseval: the positive half of the spectrum is about N/2 times the energy of the window.
			double total = WINDOW / 2.0 * energy;
			double step = SAMPLE_RATE / (double) WINDOW;
			for (int b = 0; b < BANDS.length; b++) {
				double band = 0;
				for (int k = (int) Math.ceil((BANDS[b][0] - 20) / step); k * step <= BANDS[b][1] + 20; k++) {
					band += goertzel(k);
				}
				if (band / total > THRESHOLD) {
					return b;
				}
			}
			return -1;
		}

		private double goertzel(int k) {
			double coefficient = 2 * Math.cos(2 * Math.PI * k / WINDOW);
			double s1 = 0;
			double s2 = 0;
			for (int n = 0; n < WINDOW; n++) {
				double s = samples[n] * HANN[n] + coefficient * s1 - s2;
				s2 = s1;
				s1 = s;
			}
			return s1 * s1 + s2 * s2 - coefficient * s1 * s2;
		}

		private void closeWindow(int band) {
			if (band == currentBand && band >= 0) {
				length++;
				return;
			}
			closeRun();
			currentBand = band;
			length = band >= 0 ? 1 : 0;
			currentStart = windows;
		}

		private void closeRun() {
			if (currentBand >= 0 && length >= MIN_WINDOWS) {
				runs.add(new double[]{
				                      currentStart * WINDOW / (double) SAMPLE_RATE,
				                      currentBand
				});
			}
		}

		/**
		 * Start of every sequence: the three bands in ascending order (913, 1370, 1776 Hz), as the SIT
		 * standard prescribes, within 1.5 s from the start of the first. An introduction with pure notes
		 * ("Bella Ciao" by the Modena City Ramblers: two bands, in descending order) is not enough.
		 */
		List<Double> sequences() {
			closeRun();
			currentBand = -1;
			List<Double> starts = new ArrayList<>();
			for (int i = 0; i < runs.size(); i++) {
				double[] first = runs.get(i);
				if (first[1] != 0) {
					continue;
				}
				int expected = 1;
				for (int j = i + 1; j < runs.size() && runs.get(j)[0] - first[0] <= MAX_DISTANCE_SECONDS && expected <= 2; j++) {
					if (runs.get(j)[1] == expected) {
						expected++;
					}
				}
				if (expected > 2) {
					starts.add(first[0]);
				}
			}
			return starts;
		}
	}
}
