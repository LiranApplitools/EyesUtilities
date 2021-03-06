package com.applitools.utils;

import com.sun.glass.ui.Size;
import com.sun.xml.internal.ws.util.StringUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Created by yanir on 18/04/2016.
 */
public class Utils {

    public static <T extends Enum<T>> T parseEnum(Class<T> c, String string) {
        if (c != null && string != null) {
            try {
                return Enum.valueOf(c, string.trim().toLowerCase());
            } catch (IllegalArgumentException ex) {
            }
        }
        throw new RuntimeException(String.format("Unable to parse value %s for enum %s", string, c.getName()));
    }

    public static String getEnumValues(Class type) {
        StringBuilder sb = new StringBuilder();
        for (Object val : EnumSet.allOf(type)) {
            sb.append(StringUtils.capitalize(val.toString().toLowerCase()));
            sb.append('|');
        }
        return sb.substring(0, sb.length() - 1);
    }

    public static void setClipboard(String copy) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Clipboard clipboard = toolkit.getSystemClipboard();
        StringSelection strSel = new StringSelection(copy);
        clipboard.setContents(strSel, null);
    }

    public static void saveImage(String imageUrl, String destinationFile) throws IOException {
        try {
            FileUtils.copyURLToFile(new URL(imageUrl), new File(destinationFile));
        } catch (IOException e) {
            System.out.printf("Unable to process image from %s to %s \n Error text: %s",
                    imageUrl,
                    destinationFile,
                    e.getMessage());
            throw e;
        }
    }

    public static String getRFC1123Date() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(calendar.getTime());
    }

    public static void sendAsLongRunningTask(HttpUriRequest request, String accessKey) throws IOException, InterruptedException {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        try {
            String date = Utils.getRFC1123Date();
            setLongTaskHeaders(request, date);
            CloseableHttpResponse response = client.execute(request);
            //If shorter than 10sec on server side
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) return;
            String statusUrl = buildStatusUrl(response, accessKey);
            HttpUriRequest status = setLongTaskHeaders(new HttpGet(statusUrl), date);

            do {
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                        response.close();
                        Thread.sleep(2000);
                        response = client.execute(status);
                        break;
                    case HttpStatus.SC_CREATED:
                        statusUrl = buildStatusUrl(response, accessKey);
                        response.close();
                        status = new HttpDelete(statusUrl);
                        response = client.execute(status);
                        if (response.getStatusLine().getStatusCode() != 200)
                            throw new HttpResponseException(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
                        return;
                    default:
                        throw new HttpResponseException(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
                }
            } while (true);
        } finally {
            client.close();
        }
    }

    private static String buildStatusUrl(HttpResponse response, String accessKey) {
        Header location = response.containsHeader("Location") ? response.getFirstHeader("Location") : null;
        return (location != null) ? String.format("%s?accessKey=%s", location.getValue(), accessKey) : null;
    }

    private static HttpUriRequest setLongTaskHeaders(HttpUriRequest r, String date) {
        r.addHeader("Eyes-Expect", "202+location");
        r.addHeader("Eyes-Date", date);
        return r;
    }

    public static void createAnimatedGif(List<BufferedImage> images, File target, int timeBetweenFrames) throws IOException {
        ImageOutputStream output = new FileImageOutputStream(target);
        GifSequenceWriter writer = null;

        Size max = getMaxSize(images);

        try {
            for (BufferedImage image : images) {
                BufferedImage normalized = new BufferedImage(max.width, max.height, image.getType());
                normalized.getGraphics().drawImage(image, 0, 0, null);
                if (writer == null) writer = new GifSequenceWriter(output, image.getType(), timeBetweenFrames, true);
                writer.writeToSequence(normalized);
            }
        } finally {
            writer.close();
            output.close();
        }
    }

    private static Size getMaxSize(List<BufferedImage> images) {
        Size max = new Size(0, 0);
        for (BufferedImage image : images) {
            if (max.height < image.getHeight()) max.height = image.getHeight();
            if (max.width < image.getWidth()) max.width = image.getWidth();
        }
        return max;
    }
}
