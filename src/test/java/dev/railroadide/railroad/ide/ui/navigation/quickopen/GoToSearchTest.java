package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GoToSearchTest {
    private static final GoToContributor<String> PATHS = new GoToContributor<>() {
        @Override
        public String titleKey() {
            return "title";
        }

        @Override
        public List<String> collect(Project project, Runnable activityCheck) {
            return List.of();
        }

        @Override
        public String name(String item) {
            return item.substring(item.lastIndexOf('/') + 1);
        }

        @Override
        public String path(String item) {
            return item;
        }

        @Override
        public String detail(String item) {
            return "";
        }

        @Override
        public char separator() {
            return '/';
        }

        @Override
        public String normalizePattern(String pattern) {
            return pattern.replace('\\', '/');
        }

        @Override
        public Node icon(String item) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void open(String item, GoToSearch.Query query) {
        }
    };

    @Test
    public void rankingPutsTheBestNameMatchFirstAndDropsNonMatches() {
        List<String> items = List.of("src/ModAndIndex.java", "src/MainWindow.java", "src/Main.java", "README.md");

        List<String> ranked = names(GoToSearch.rank(PATHS, items, "main", 10));

        assertEquals(List.of("src/Main.java", "src/MainWindow.java", "src/ModAndIndex.java"), ranked);
    }

    @Test
    public void patternsWithSeparatorsMatchThePath() {
        List<String> items = List.of("app/src/Main.java", "lib/src/Main.java", "lib/Other.java");

        List<GoToSearch.Result<String>> results = GoToSearch.rank(PATHS, items, "lib\\main", 10);

        assertEquals(List.of("lib/src/Main.java"), names(results));
        assertNull(results.getFirst().nameMatch());
    }

    @Test
    public void equalScoresAreOrderedByShorterThenAlphabeticalName() {
        List<String> items = List.of("b/Abc", "a/Abc", "Abcd");

        assertEquals(List.of("a/Abc", "b/Abc", "Abcd"), names(GoToSearch.rank(PATHS, items, "", 10)));
    }

    @Test
    public void rankingHonoursTheLimit() {
        assertEquals(2, GoToSearch.rank(PATHS, List.of("a", "ab", "abc"), "a", 2).size());
    }

    @Test
    public void queriesSplitOffLineAndColumnSuffixes() {
        assertEquals(new GoToSearch.Query("Main.java", 12, 0), GoToSearch.Query.parse(" Main.java:12 "));
        assertEquals(new GoToSearch.Query("Main", 3, 7), GoToSearch.Query.parse("Main:3:7"));
        assertEquals(new GoToSearch.Query("Main:", 0, 0), GoToSearch.Query.parse("Main:"));
        assertEquals(new GoToSearch.Query("Main:x", 0, 0), GoToSearch.Query.parse("Main:x"));
    }

    @Test
    public void lineOffsetsAreClampedToTheText() {
        String text = "first\r\nsecond\nthird";

        assertEquals(-1, GoToSearch.Query.parse("A").offsetIn(text));
        assertEquals(0, GoToSearch.Query.parse("A:1").offsetIn(text));
        assertEquals(7, GoToSearch.Query.parse("A:2").offsetIn(text));
        assertEquals(9, GoToSearch.Query.parse("A:2:3").offsetIn(text));
        assertEquals(13, GoToSearch.Query.parse("A:2:99").offsetIn(text));
        assertEquals(5, GoToSearch.Query.parse("A:1:99").offsetIn(text));
        assertEquals(14, GoToSearch.Query.parse("A:99").offsetIn(text));
    }

    private static List<String> names(List<GoToSearch.Result<String>> results) {
        return results.stream().map(GoToSearch.Result::item).toList();
    }
}
