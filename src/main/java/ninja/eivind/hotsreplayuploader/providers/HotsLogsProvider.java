package ninja.eivind.hotsreplayuploader.providers;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import ninja.eivind.hotsreplayuploader.models.ReplayFile;
import ninja.eivind.hotsreplayuploader.models.Status;
import ninja.eivind.mpq.models.MpqException;
import ninja.eivind.stormparser.StormParser;
import ninja.eivind.stormparser.models.Player;
import ninja.eivind.stormparser.models.Replay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class HotsLogsProvider extends Provider {

    private static final Logger LOG = LoggerFactory.getLogger(HotsLogsProvider.class);
    private static final String ACCESS_KEY_ID = "AKIAIESBHEUH4KAAG4UA";
    private static final String SECRET_ACCESS_KEY = "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";
    private static final String CLIENT_ID = "HotSLogsUploaderFX";
    public static final String BASE_URL = "https://www.hotslogs.com/UploadFile?Source=" + CLIENT_ID;
    private static long maintenance;
    private final AmazonS3Client s3Client;

    public HotsLogsProvider() {
        super("HotSLogs.com");
        final AWSCredentials credentials = new BasicAWSCredentials(ACCESS_KEY_ID, SECRET_ACCESS_KEY);
        s3Client = new AmazonS3Client(credentials);
    }

    public static boolean isMaintenance() {
        return maintenance + 600000L > System.currentTimeMillis();
    }

    @Override
    public Status upload(final ReplayFile replayFile) {
        if (isMaintenance()) {
            return null;
        }

        File file = replayFile.getFile();
        try {
            boolean fileAlreadyUploaded = parseAndCheckStatus(file);
            if (fileAlreadyUploaded) {
                LOG.info("File already uploaded. No need to upload.");
                return Status.UPLOADED;
            } else {
                LOG.info("New replay. Uploading to HotSLogs.com.");
            }
        } catch (IOException e) {
            LOG.warn("Could not check status for replay: " + file, e);
            return Status.EXCEPTION;
        } catch (MpqException e) {
            LOG.error("Could not parse replay. Skipping directly to upload.", e);
        }

        String fileName = UUID.randomUUID() + ".StormReplay";
        LOG.info("Assigning remote file name " + fileName + " to " + replayFile);
        String uri = BASE_URL + "&FileName=" + fileName;

        return uploadFileToHotSLogs(file, fileName, uri);

    }

    private Status uploadFileToHotSLogs(File file, String fileName, String uri) {
        try {
            s3Client.putObject("heroesreplays", fileName, file);
            LOG.info("File " + fileName + "uploaded to remote storage.");
            String result = getHttpClient().simpleRequest(uri).toLowerCase();
            switch (result) {
                case "duplicate":
                case "success":
                case "computerplayerfound":
                case "trymemode":
                    LOG.info("File registered with HotSLogs.com");
                    return Status.UPLOADED;
                case "prealphawipe":
                    LOG.warn("File not supported by HotSLogs.com");
                    return Status.UNSUPPORTED_GAME_MODE;
                case "maintenance":
                    LOG.error("HotSLogs.com is currently undergoing maintenance.");
                    maintenance = System.currentTimeMillis();
                    return Status.NEW;
                default:
                    LOG.error("Could not upload file. Unknown status \"" + result + "\" received.");
                    return Status.EXCEPTION;
            }

        } catch (Exception e) {
            LOG.error("Could not upload file.", e);
            return Status.EXCEPTION;
        }
    }

    private boolean parseAndCheckStatus(File file) throws IOException {
        final StormParser stormParser = new StormParser(file);
        Replay replay = stormParser.parseReplay();
        try {
            String matchId = getMatchId(replay);
            LOG.info("Calculated matchId to be" + matchId);
            String uri = BASE_URL + "&ReplayHash=" + matchId;
            String result = getHttpClient().simpleRequest(uri).toLowerCase();
            return result.equals("duplicate");
        } catch (NoSuchAlgorithmException e) {
            LOG.warn("Platform does not support MD5; cannot proceed with parsing", e);
            return false;
        }
    }

    protected String getMatchId(Replay replay) throws NoSuchAlgorithmException {
        String concatenatedString = getConcatenatedString(replay);

        return getUUIDForString(concatenatedString).toString();

    }

    private UUID getUUIDForString(String concatenatedString) throws NoSuchAlgorithmException {
        byte[] hashed = MessageDigest.getInstance("MD5").digest(concatenatedString.getBytes());
        byte[] reArranged = reArrangeForUUID(hashed);
        return getUUID(reArranged);
    }

    private byte[] reArrangeForUUID(byte[] hashed) {
        return new byte[]{
                hashed[3],
                hashed[2],
                hashed[1],
                hashed[0],

                hashed[5],
                hashed[4],
                hashed[7],
                hashed[6],
                hashed[8],
                hashed[9],
                hashed[10],
                hashed[11],
                hashed[12],
                hashed[13],
                hashed[14],
                hashed[15],
        };
    }

    private UUID getUUID(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        assert bytes.length == 16 : "data must be 16 bytes in length";
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }

        return new UUID(msb, lsb);
    }

    private String getConcatenatedString(Replay replay) {
        String randomValue = String.valueOf(replay.getInitData().getRandomValue());
        List<String> battleNetIdsSorted = replay.getReplayDetails()
                .getPlayers()
                .stream()
                .map(Player::getBNetId)
                .map(Long::parseLong)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.toList());
        StringBuilder builder = new StringBuilder();
        battleNetIdsSorted.forEach(builder::append);
        builder.append(randomValue);
        return builder.toString();
    }
}
