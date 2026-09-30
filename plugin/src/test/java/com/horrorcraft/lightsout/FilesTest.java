package com.horrorcraft.lightsout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Outbox lines and state.json: format, single writer, temp + rename, fail soft. */
class FilesTest {

    private static final Logger LOG = Logger.getLogger("test");

    @Test
    void outboxLineHasTimestampRunAndKind() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("speaker", "alex");
        f.put("text", "he said \"run\"\nnow");
        f.put("voiced", false);
        String line = Outbox.line(Instant.parse("2026-09-30T20:15:03.123456Z"), "run-1", "chat", f);
        assertFalse(line.contains("\n"), "one event per line");
        JsonObject o = JsonParser.parseString(line).getAsJsonObject();
        assertEquals("2026-09-30T20:15:03.123Z", o.get("at").getAsString());
        assertEquals("run-1", o.get("run_id").getAsString());
        assertEquals("chat", o.get("kind").getAsString());
        assertEquals("he said \"run\"\nnow", o.get("text").getAsString());
        assertFalse(o.get("voiced").getAsBoolean());
    }

    @Test
    void outboxFileNameIsSafe() {
        assertEquals("no-run.jsonl", Outbox.fileName(null));
        assertEquals("abc-123.jsonl", Outbox.fileName("abc-123"));
        assertEquals("______x.jsonl", Outbox.fileName("../..\\x"));
    }

    @Test
    void outboxAppendsInOrder(@TempDir Path dir) throws Exception {
        Outbox o = new Outbox(dir.resolve("outbox"), LOG, "r1");
        for (int i = 0; i < 50; i++) o.emit("hit", Map.of("n", i));
        o.close();
        List<String> lines = Files.readAllLines(dir.resolve("outbox/r1.jsonl"), StandardCharsets.UTF_8);
        assertEquals(50, lines.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(i, JsonParser.parseString(lines.get(i)).getAsJsonObject().get("n").getAsInt());
        }
    }

    @Test
    void outboxFailsSoftWhenDiskIsUnwritable(@TempDir Path dir) throws Exception {
        Path blocker = dir.resolve("outbox");
        Files.writeString(blocker, "a file where the folder should be");
        Outbox o = new Outbox(blocker, LOG, "r1");
        assertDoesNotThrow(() -> o.emit("hit", Map.of()));
        o.close();
    }

    @Test
    void stateRoundTrips(@TempDir Path dir) {
        StateStore store = new StateStore(dir, LOG);
        StateStore.State s = new StateStore.State();
        s.run_id = "r1";
        s.seed = -1541124385142397106L;
        s.stun_index = 4;
        s.lives.put("josh", 1);
        s.killer_hidden = false;
        store.save(s);

        StateStore.State back = store.load();
        assertEquals("r1", back.run_id);
        assertEquals(-1541124385142397106L, back.seed);
        assertEquals(4, back.stun_index);
        assertEquals(1, back.lives.get("josh"));
        assertFalse(back.killer_hidden);
        assertFalse(Files.exists(dir.resolve("state.json.tmp")), "temp file renamed away");
    }

    @Test
    void missingStateIsFreshWithKillerHidden(@TempDir Path dir) {
        StateStore.State s = new StateStore(dir, LOG).load();
        assertNull(s.run_id);
        assertTrue(s.lives.isEmpty());
        assertTrue(s.killer_hidden);
    }

    @Test
    void corruptStateFallsBackToBak(@TempDir Path dir) throws Exception {
        StateStore store = new StateStore(dir, LOG);
        StateStore.State s = new StateStore.State();
        s.run_id = "r1";
        s.lives.put("josh", 2);
        store.save(s);
        s.lives.put("josh", 1);
        store.save(s); // .bak now holds josh=2, state.json josh=1

        Files.writeString(dir.resolve("state.json"), "{not json", StandardCharsets.UTF_8);
        StateStore.State back = store.load();
        assertEquals("r1", back.run_id);
        assertEquals(2, back.lives.get("josh"));
    }

    @Test
    void bothCorruptIsFreshNotACrash(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("state.json"), "garbage");
        Files.writeString(dir.resolve("state.json.bak"), "garbage");
        StateStore.State s = assertDoesNotThrow(() -> new StateStore(dir, LOG).load());
        assertNull(s.run_id);
    }
}
