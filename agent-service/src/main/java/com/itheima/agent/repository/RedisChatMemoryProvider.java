package com.itheima.agent.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.agent.service.ContextManager;
import com.itheima.agent.service.MemoryManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 会话记忆提供器 — 本地 JSON 文件存储。
 *
 * 每个 session 的完整对话历史存为 data/chat_history/{memoryId}.json。
 * 读取时通过 ContextManager 动态压缩后返回。
 */
@Slf4j
@Component
public class RedisChatMemoryProvider implements ChatMemoryProvider {

    private static final String DATA_DIR = "data/chat_history";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ContextManager contextManager;

    @Autowired
    private MemoryManager memoryManager;

    @Value("${rag.memory.short-term.window-size:20}")
    private int maxWindowMessages;

    private Path basePath;
    private final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        basePath = Paths.get(DATA_DIR).toAbsolutePath();
        Files.createDirectories(basePath);
        log.info("[ChatMemory] 本地存储目录: {}", basePath);
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxWindowMessages)
                .chatMemoryStore(new FileChatMemoryStore(memoryId.toString()))
                .build();
    }

    private class FileChatMemoryStore implements ChatMemoryStore {
        private final String memoryId;
        private final Path filePath;
        private final ReentrantLock lock;

        FileChatMemoryStore(String memoryId) {
            this.memoryId = memoryId;
            this.filePath = basePath.resolve(memoryId + ".json");
            this.lock = fileLocks.computeIfAbsent(memoryId, k -> new ReentrantLock());
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<ChatMessage> getMessages(Object id) {
            List<ChatMessage> fullHistory = readFromFile();

            // 自动注入用户画像/偏好/反馈 → 无需 LLM 手动调用 readMemory
            String profile = buildMemoryContext();
            if (!profile.isEmpty()) {
                List<ChatMessage> withProfile = new ArrayList<>();
                withProfile.add(SystemMessage.from(profile));
                withProfile.addAll(fullHistory);
                return contextManager.compact(withProfile, memoryId);
            }
            return contextManager.compact(fullHistory, memoryId);
        }

        /** 读取所有已保存记忆，构建自动注入的上下文 */
        private String buildMemoryContext() {
            try {
                List<MemoryManager.MemoryEntry> all = memoryManager.listAll(memoryId);
                if (all.isEmpty()) return "";

                StringBuilder sb = new StringBuilder("[用户画像与偏好 — 自动加载]\n");
                for (MemoryManager.MemoryEntry e : all) {
                    sb.append("- [").append(e.getType()).append("] ").append(e.getName()).append(": ").append(e.getContent()).append("\n");
                }
                sb.append("请根据以上用户画像和偏好调整回答风格和内容。\n");
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public void updateMessages(Object id, List<ChatMessage> messages) {
            if (messages == null || messages.isEmpty()) return;

            lock.lock();
            try {
                List<ChatMessage> fullHistory = readFromFile();

                Set<String> existingSigs = fullHistory.stream()
                        .map(this::signature)
                        .collect(Collectors.toSet());

                for (ChatMessage msg : messages) {
                    if (msg instanceof SystemMessage) continue;
                    String sig = signature(msg);
                    if (sig.isEmpty() || existingSigs.contains(sig)) continue;
                    fullHistory.add(msg);
                    existingSigs.add(sig);
                }

                writeToFile(fullHistory);
                contextManager.notifyNewMessages(memoryId, messages);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void deleteMessages(Object id) {
            try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
        }

        // ---- file I/O ----

        @SuppressWarnings("unchecked")
        private List<ChatMessage> readFromFile() {
            lock.lock();
            try {
                if (!Files.exists(filePath)) return new ArrayList<>();
                byte[] bytes = Files.readAllBytes(filePath);
                if (bytes.length == 0) return new ArrayList<>();
                List<Map<String, Object>> raw = JSON.readValue(bytes, List.class);
                return raw.stream().map(this::deserialize).filter(Objects::nonNull).collect(Collectors.toList());
            } catch (IOException e) {
                log.warn("[ChatMemory] 读取文件失败 session={}: {}", memoryId, e.getMessage());
                return new ArrayList<>();
            } finally {
                lock.unlock();
            }
        }

        private void writeToFile(List<ChatMessage> messages) {
            lock.lock();
            try {
                List<Map<String, Object>> raw = messages.stream()
                        .map(this::serialize).collect(Collectors.toList());
                Files.write(filePath, JSON.writeValueAsBytes(raw),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                log.error("[ChatMemory] 写入文件失败 session={}: {}", memoryId, e.getMessage());
            } finally {
                lock.unlock();
            }
        }

        // ---- serialize / deserialize ----

        private Map<String, Object> serialize(ChatMessage msg) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", msg.type().name());
            m.put("text", extractText(msg));
            if (msg instanceof ToolExecutionResultMessage tm) {
                m.put("toolName", tm.toolName());
            }
            return m;
        }

        private ChatMessage deserialize(Map<String, Object> m) {
            String type = (String) m.get("type");
            String text = (String) m.getOrDefault("text", "");
            if (type == null) return null;

            return switch (type) {
                case "USER" -> UserMessage.from(text);
                case "AI" -> AiMessage.from(text);
                case "SYSTEM" -> SystemMessage.from(text);
                case "TOOL_EXECUTION_RESULT" -> {
                    String toolName = (String) m.getOrDefault("toolName", "");
                    var tr = ToolExecutionRequest.builder().id(UUID.randomUUID().toString()).name(toolName).arguments("{}").build();
yield ToolExecutionResultMessage.from(tr, text);
                }
                default -> null;
            };
        }

        // ---- helpers ----

        private String signature(ChatMessage msg) {
            String text = extractText(msg);
            return text != null ? text.trim() : "";
        }

        private String extractText(ChatMessage msg) {
            if (msg instanceof UserMessage um) {
                return um.contents().stream()
                        .filter(c -> c instanceof TextContent)
                        .map(c -> ((TextContent) c).text())
                        .reduce("", (a, b) -> a + b);
            }
            if (msg instanceof AiMessage am) return am.text();
            if (msg instanceof SystemMessage sm) return sm.text();
            if (msg instanceof ToolExecutionResultMessage tm) return tm.text();
            return msg.toString();
        }
    }

    // ---- 公共 API ----

    public List<ChatMessage> getFullHistory(Object memoryId) {
        try {
            Path fp = basePath.resolve(memoryId.toString() + ".json");
            if (!Files.exists(fp)) return new ArrayList<>();
            byte[] bytes = Files.readAllBytes(fp);
            List<Map<String, Object>> raw = JSON.readValue(bytes, List.class);
            return raw.stream().map(m -> {
                String type = (String) m.get("type");
                String text = (String) m.getOrDefault("text", "");
                if (type == null) return null;
                return switch (type) {
                    case "USER" -> UserMessage.from(text);
                    case "AI" -> AiMessage.from(text);
                    case "SYSTEM" -> SystemMessage.from(text);
                    case "TOOL_EXECUTION_RESULT" -> {
                        String tn = (String) m.getOrDefault("toolName", "");
                        var tr2 = ToolExecutionRequest.builder().id(UUID.randomUUID().toString()).name(tn).arguments("{}").build();
                        yield ToolExecutionResultMessage.from(tr2, text);
                    }
                    default -> null;
                };
            }).filter(Objects::nonNull).collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void deleteSession(String memoryId) {
        try { Files.deleteIfExists(basePath.resolve(memoryId + ".json")); } catch (IOException ignored) {}
    }

    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> sessions = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(basePath, "*.json")) {
            for (Path p : ds) {
                String sessionId = p.getFileName().toString().replace(".json", "");
                String preview = "";
                try {
                    byte[] bytes = Files.readAllBytes(p);
                    List<Map<String, Object>> raw = JSON.readValue(bytes, List.class);
                    for (Map<String, Object> m : raw) {
                        if ("USER".equals(m.get("type"))) {
                            preview = (String) m.getOrDefault("text", "");
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                Map<String, Object> item = new HashMap<>();
                item.put("id", sessionId);
                item.put("preview", preview.length() > 30 ? preview.substring(0, 30) + "..." : preview);
                sessions.add(item);
            }
        } catch (IOException ignored) {}
        return sessions;
    }
}
