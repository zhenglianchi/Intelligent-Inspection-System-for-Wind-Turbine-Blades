package com.itheima.agent.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 持久化记忆系统 — 每个 session 独立存储。
 *
 * 存储结构:
 *   memory/{sessionId}/
 *   ├── MEMORY.md           ← 索引文件
 *   ├── user_role.md         ← 用户画像
 *   ├── feedback_xxx.md      ← 行为反馈
 *   └── ...
 */
@Slf4j
@Service
public class MemoryManager {

    private static final Pattern FRONTMATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);

    @Value("${rag.memory.persistence.path:memory}")
    private String memoryPath;

    private Path basePath;

    @PostConstruct
    public void init() {
        basePath = Paths.get(memoryPath).toAbsolutePath();
        log.info("[MemoryManager] 路径: {}", basePath);
    }

    private Path sessionDir(String sessionId) { return basePath.resolve(sanitize(sessionId)); }
    private Path indexPath(String sessionId) { return sessionDir(sessionId).resolve("MEMORY.md"); }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // ---- CRUD ----

    public MemoryEntry saveMemory(String sessionId, String name, String desc, MemoryType type, String content) {
        try {
            Path dir = sessionDir(sessionId);
            Files.createDirectories(dir);
            String filename = name.replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase() + ".md";
            Path file = dir.resolve(filename);
            StringBuilder sb = new StringBuilder();
            sb.append("---\nname: ").append(name).append("\ndescription: ").append(desc)
              .append("\nmetadata:\n  type: ").append(type.name().toLowerCase()).append("\n---\n\n").append(content);
            Files.writeString(file, sb.toString());
            updateIndex(sessionId, filename, desc);
            log.info("[Memory] 保存: session={}, name={}, type={}", sessionId, name, type);
            return new MemoryEntry(name, desc, type, content, file.toString());
        } catch (IOException e) { log.error("[Memory] 保存失败: {}", e.getMessage()); return null; }
    }

    public MemoryEntry readMemory(String sessionId, String name) {
        String filename = name.replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase() + ".md";
        Path file = sessionDir(sessionId).resolve(filename);
        if (!Files.exists(file)) return null;
        try { return parseFile(Files.readString(file), file.toString()); }
        catch (IOException e) { return null; }
    }

    public MemoryEntry updateMemory(String sessionId, String name, String newContent) {
        MemoryEntry existing = readMemory(sessionId, name);
        if (existing == null) return null;
        return saveMemory(sessionId, existing.getName(), existing.getDescription(), existing.getType(), newContent);
    }

    public MemoryEntry updateMemory(String sessionId, String name, String newDesc, String newContent) {
        MemoryEntry existing = readMemory(sessionId, name);
        if (existing == null) return null;
        return saveMemory(sessionId, existing.getName(), newDesc, existing.getType(), newContent);
    }

    public boolean deleteMemory(String sessionId, String name) {
        String filename = name.replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase() + ".md";
        Path file = sessionDir(sessionId).resolve(filename);
        try { return Files.deleteIfExists(file); } catch (IOException e) { return false; }
    }

    public List<MemoryEntry> listAll(String sessionId) {
        List<MemoryEntry> entries = new ArrayList<>();
        Path dir = sessionDir(sessionId);
        if (!Files.exists(dir)) return entries;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.md")) {
            for (Path p : ds) {
                if (p.getFileName().toString().equals("MEMORY.md")) continue;
                MemoryEntry e = parseFile(Files.readString(p), p.toString());
                if (e != null) entries.add(e);
            }
        } catch (IOException ignored) {}
        return entries;
    }

    public List<MemoryEntry> search(String sessionId, String query) {
        String q = query.toLowerCase();
        return listAll(sessionId).stream()
            .filter(e -> e.name.toLowerCase().contains(q) || e.description.toLowerCase().contains(q) || e.content.toLowerCase().contains(q))
            .collect(Collectors.toList());
    }

    // ---- helpers ----

    private void updateIndex(String sessionId, String filename, String desc) throws IOException {
        Path idx = indexPath(sessionId);
        List<String> lines = Files.exists(idx) ? new ArrayList<>(Files.readAllLines(idx)) : new ArrayList<>();
        String prefix = "- [" + filename + "](" + filename + ")";
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) { lines.set(i, prefix + " — " + desc); found = true; break; }
        }
        if (!found) lines.add(prefix + " — " + desc);
        Files.write(idx, lines);
    }

    private MemoryEntry parseFile(String raw, String filePath) {
        Matcher m = FRONTMATTER.matcher(raw);
        if (!m.find()) return null;
        String fm = m.group(1);
        String body = m.group(2);
        String name = extract(fm, "name:");
        String desc = extract(fm, "description:");
        String typeStr = extract(fm, "type:");
        MemoryType type;
        try { type = MemoryType.valueOf(typeStr.toUpperCase()); } catch (Exception e) { type = MemoryType.USER; }
        return new MemoryEntry(name, desc, type, body.trim(), filePath);
    }

    private String extract(String text, String key) {
        Matcher m = Pattern.compile(key + "\\s*(\\S.*)").matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    // ---- types ----

    public enum MemoryType { USER, FEEDBACK, PROJECT, REFERENCE }

    @Data
    public static class MemoryEntry {
        final String name, description;
        final MemoryType type;
        final String content, filePath;
        final String savedAt = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
