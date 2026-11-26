package enerjy.musicxService.messaging.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Фикстуры контракта в двух сервисах обязаны совпадать побайтно (с точностью до переводов строк). */
class FixturesInSyncTest {

    private static final Path OWN = Path.of("src/test/resources/contracts/v1");
    private static final Path OTHER = Path.of("../javaMusicxTG/src/test/resources/contracts/v1");

    @Test
    void fixturesMatchTheOtherService() throws Exception {
        assumeTrue(Files.isDirectory(OWN) && Files.isDirectory(OTHER), "other service directory is not available");

        assertThat(names(OTHER)).containsExactlyInAnyOrderElementsOf(names(OWN));
        for (String name : names(OWN)) {
            assertThat(read(OTHER.resolve(name))).as(name).isEqualTo(read(OWN.resolve(name)));
        }
    }

    private static List<String> names(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static String read(Path file) throws Exception {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
