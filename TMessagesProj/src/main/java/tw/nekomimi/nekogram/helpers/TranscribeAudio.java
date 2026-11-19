package tw.nekomimi.nekogram.helpers;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Base64OutputStream;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

final class TranscribeAudio implements AutoCloseable {
    private final File file;
    private final boolean temporary;
    private final String extension;

    private TranscribeAudio(File file, boolean temporary, String extension) {
        this.file = file;
        this.temporary = temporary;
        this.extension = extension;
    }

    static TranscribeAudio prepare(String path, boolean video, boolean wav) throws IOException {
        File source = new File(path);
        if (!source.isFile() || source.length() == 0) throw new IOException("Audio file is missing or empty");
        if (!video && !wav) return new TranscribeAudio(source, false, detectExtension(source));
        File output = File.createTempFile("transcribe-", wav ? ".wav" : ".m4a", AndroidUtilities.getCacheDir());
        boolean success = false;
        try {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(source.getAbsolutePath());
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime == null || !mime.startsWith("audio/")) continue;
                    extractor.selectTrack(i);
                    if (wav) writeWav(extractor, format, mime, output);
                    else writeM4a(extractor, format, output);
                    success = true;
                    return new TranscribeAudio(output, true, wav ? ".wav" : ".m4a");
                }
                throw new IOException("File has no audio track");
            } finally {
                extractor.release();
            }
        } finally {
            if (!success) output.delete();
        }
    }

    File file() {
        return file;
    }

    String uploadName() {
        return "audio" + extension;
    }

    String mimeType() {
        return switch (extension) {
            case ".m4a" -> "audio/mp4";
            case ".wav" -> "audio/wav";
            case ".mp3" -> "audio/mpeg";
            case ".flac" -> "audio/flac";
            default -> "audio/ogg";
        };
    }

    private static String detectExtension(File audio) throws IOException {
        byte[] header = new byte[12];
        try (var input = new DataInputStream(new FileInputStream(audio))) {
            input.readFully(header);
        }
        ByteBuffer signature = ByteBuffer.wrap(header);
        int magic = signature.getInt(0);
        if (magic == 0x4f676753) return ".ogg"; // OggS
        if (magic == 0x52494646 && signature.getInt(8) == 0x57415645) return ".wav"; // RIFF/WAVE
        if (signature.getInt(4) == 0x66747970) return ".m4a"; // ftyp
        if (magic == 0x664c6143) return ".flac"; // fLaC
        if ((magic >>> 8) == 0x494433 || (magic & 0xffe00000) == 0xffe00000) return ".mp3";
        throw new IOException("Unsupported audio container");
    }

    RequestBody inlineJson(String json, String placeholder, MediaType jsonType) {
        int marker = json.indexOf("\"" + placeholder + "\"");
        if (marker < 0) throw new IllegalArgumentException("Missing audio field");
        int start = marker + 1;
        byte[] prefix = json.substring(0, start).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = json.substring(start + placeholder.length()).getBytes(StandardCharsets.UTF_8);
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return jsonType;
            }

            @Override
            public long contentLength() {
                return prefix.length + ((file.length() + 2) / 3) * 4 + suffix.length;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(prefix);
                try (var input = new FileInputStream(file);
                     var base64 = new Base64OutputStream(sink.outputStream(), Base64.NO_WRAP | Base64.NO_CLOSE)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) base64.write(buffer, 0, count);
                }
                sink.write(suffix);
            }
        };
    }

    private static void writeM4a(MediaExtractor extractor, MediaFormat format, File output) throws IOException {
        MediaMuxer muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            int track = muxer.addTrack(format);
            muxer.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            ByteBuffer buffer = ByteBuffer.allocate(65536);
            while (true) {
                if (Build.VERSION.SDK_INT >= 28) {
                    long size = extractor.getSampleSize();
                    if (size < 0) break;
                    if (size > Integer.MAX_VALUE) throw new IOException("Audio sample is too large");
                    if (size > buffer.capacity()) buffer = ByteBuffer.allocate((int) size);
                }
                buffer.clear();
                int read = extractor.readSampleData(buffer, 0);
                if (read < 0) break;
                info.set(0, read, extractor.getSampleTime(),
                        (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                muxer.writeSampleData(track, buffer, info);
                extractor.advance();
            }
            muxer.stop();
        } finally {
            muxer.release();
        }
    }

    private static void writeWav(MediaExtractor extractor, MediaFormat format, String mime, File output) throws IOException {
        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        try (RandomAccessFile pcm = new RandomAccessFile(output, "rw")) {
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            decoder.configure(format, null, null, 0);
            decoder.start();
            pcm.seek(44);
            FileChannel channel = pcm.getChannel();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputFinished = false;
            boolean outputFinished = false;
            long lastProgress = SystemClock.elapsedRealtime();
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            while (!outputFinished) {
                if (!inputFinished) {
                    int inputIndex = decoder.dequeueInputBuffer(10_000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = decoder.getInputBuffer(inputIndex);
                        if (input == null) throw new IOException("Audio decoder has no input buffer");
                        input.clear();
                        int bytes = extractor.readSampleData(input, 0);
                        inputFinished = bytes < 0;
                        decoder.queueInputBuffer(inputIndex, 0, Math.max(0, bytes),
                                inputFinished ? 0 : extractor.getSampleTime(),
                                inputFinished ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        if (!inputFinished) extractor.advance();
                        lastProgress = SystemClock.elapsedRealtime();
                    }
                }
                int outputIndex = decoder.dequeueOutputBuffer(info, 10_000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded = decoder.getOutputFormat();
                    if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            && decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT) {
                        throw new IOException("Audio decoder did not produce 16-bit PCM");
                    }
                    int newRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int newChannels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (channel.position() > 44 && (newRate != sampleRate || newChannels != channels)) {
                        throw new IOException("Audio format changed mid-stream");
                    }
                    sampleRate = newRate;
                    channels = newChannels;
                    lastProgress = SystemClock.elapsedRealtime();
                } else if (outputIndex >= 0) {
                    try {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            ByteBuffer data = decoder.getOutputBuffer(outputIndex);
                            if (data == null) throw new IOException("Audio decoder has no output buffer");
                            if (channel.position() - 44 + info.size > 0xffffffffL - 36) throw new IOException("Decoded audio is too large");
                            data.position(info.offset);
                            data.limit(info.offset + info.size);
                            while (data.hasRemaining()) channel.write(data);
                        }
                        outputFinished = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    } finally {
                        decoder.releaseOutputBuffer(outputIndex, false);
                    }
                    lastProgress = SystemClock.elapsedRealtime();
                }
                if (SystemClock.elapsedRealtime() - lastProgress > 30_000) throw new IOException("Audio decoder stalled");
            }
            long dataSize = channel.position() - 44;
            if (dataSize <= 0 || sampleRate <= 0 || channels <= 0 || channels > 8 || dataSize % (channels * 2) != 0) {
                throw new IOException("Invalid decoded audio");
            }
            ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(0x46464952).putInt((int) (36 + dataSize)).putInt(0x45564157);
            header.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) channels);
            header.putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
            header.putInt(0x61746164).putInt((int) dataSize);
            pcm.seek(0);
            pcm.write(header.array());
        } finally {
            decoder.release();
        }
    }

    @Override
    public void close() {
        if (temporary) file.delete();
    }
}
