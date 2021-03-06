package ru.yandex.clickhouse;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import ru.yandex.clickhouse.settings.ClickHouseProperties;
import ru.yandex.clickhouse.util.ClickHouseVersionNumberUtil;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static java.time.temporal.ChronoUnit.SECONDS;

public class ClickHouseContainerForTest {
    private static final int HTTP_PORT = 8123;
    private static final int NATIVE_PORT = 9000;
    private static final int MYSQL_PORT = 3306;

    private static final String clickhouseVersion;
    private static final GenericContainer<?> clickhouseContainer;

    static {
        String imageTag = System.getProperty("clickhouseVersion");
        if (imageTag == null || (imageTag = imageTag.trim()).isEmpty()) {
            clickhouseVersion = imageTag = "";
        } else {
            if (ClickHouseVersionNumberUtil.getMajorVersion(imageTag) == 0) {
                clickhouseVersion = "";
            } else {
                clickhouseVersion = imageTag;
            }
            imageTag = ":" + imageTag;
        }

        clickhouseContainer = new GenericContainer<>("yandex/clickhouse-server" + imageTag)
                .waitingFor(Wait.forHttp("/ping").forPort(HTTP_PORT).forStatusCode(200)
                        .withStartupTimeout(Duration.of(60, SECONDS)))
                .withExposedPorts(HTTP_PORT, NATIVE_PORT, MYSQL_PORT);
    }

    public static String getClickHouseVersion() {
        return clickhouseVersion;
    }

    public static GenericContainer<?> getClickHouseContainer() {
        return clickhouseContainer;
    }

    public static String getClickHouseHttpAddress() {
        return getClickHouseHttpAddress(false);
    }

    public static String getClickHouseHttpAddress(boolean useIPaddress) {
        return new StringBuilder()
                .append(useIPaddress ? clickhouseContainer.getContainerIpAddress() : clickhouseContainer.getHost())
                .append(':').append(clickhouseContainer.getMappedPort(HTTP_PORT)).toString();
    }

    public static ClickHouseDataSource newDataSource() {
        return newDataSource(new ClickHouseProperties());
    }

    public static ClickHouseDataSource newDataSource(ClickHouseProperties properties) {
        return newDataSource("jdbc:clickhouse://" + getClickHouseHttpAddress(), properties);
    }

    public static ClickHouseDataSource newDataSource(String url) {
        return newDataSource(url, new ClickHouseProperties());
    }

    public static ClickHouseDataSource newDataSource(String url, ClickHouseProperties properties) {
        String baseUrl = "jdbc:clickhouse://" + getClickHouseHttpAddress();
        if (url == null) {
            url = baseUrl;
        } else if (!url.startsWith("jdbc:")) {
            url = baseUrl + "/" + url;
        }

        return new ClickHouseDataSource(url, properties);
    }

    public static BalancedClickhouseDataSource newBalancedDataSource(String... addresses) {
        return newBalancedDataSource(new ClickHouseProperties(), addresses);
    }

    public static BalancedClickhouseDataSource newBalancedDataSource(ClickHouseProperties properties,
            String... addresses) {
        return newBalancedDataSourceWithSuffix(null, properties, addresses);
    }

    public static BalancedClickhouseDataSource newBalancedDataSourceWithSuffix(String urlSuffix,
            ClickHouseProperties properties, String... addresses) {
        StringBuilder url = new StringBuilder().append("jdbc:clickhouse://");
        if (addresses == null || addresses.length == 0) {
            url.append(getClickHouseHttpAddress());
        } else {
            int position = url.length();
            for (int i = 0; i < addresses.length; i++) {
                url.append(',').append(addresses[i]);
            }
            url.deleteCharAt(position);
        }

        if (urlSuffix != null) {
            url.append('/').append(urlSuffix);
        }

        return new BalancedClickhouseDataSource(url.toString(), properties);
    }

    @BeforeSuite
    public void beforeSuite() {
        clickhouseContainer.start();
    }

    @AfterSuite
    public void afterSuite() {
        clickhouseContainer.stop();
    }
}
