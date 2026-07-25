package org.example.agentScope.util.session;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
// 核心修复1：引入 Column 类，用于把字符串转为列对象
import com.mybatisflex.core.query.QueryColumn;
import io.agentscope.core.session.ListHashUtil;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;

import org.example.repository.dal.entity.AgentscopeSessionsEntity;
import org.example.repository.dal.mapper.AgentScopeSessionsMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis-Flex 的 PostgreSQL Session 实现
 * (修正版：修复 Mapper 方法名和 QueryWrapper 列名引用)
 */
@Component
@DS("postgresql-session") // 确保使用正确的数据源
public class PostgresSession implements Session {

    private static final String HASH_KEY_SUFFIX = ":_hash";
    private static final int SINGLE_STATE_INDEX = 0;

    private static final QueryColumn COL_SESSION_ID = new QueryColumn("session_id");
    private static final QueryColumn COL_STATE_KEY = new QueryColumn("state_key");
    private static final QueryColumn COL_ITEM_INDEX = new QueryColumn("item_index");
    private static final QueryColumn COL_STATE_DATA = new QueryColumn("state_data");

    // 校验正则
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final int MAX_IDENTIFIER_LENGTH = 64;

    private final AgentScopeSessionsMapper sessionMapper;

    public PostgresSession(AgentScopeSessionsMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        String sessionId = sessionKey.toIdentifier();
        validateSessionId(sessionId);
        validateStateKey(key);

        try {
            String json = JsonUtils.getJsonCodec().toJson(value);

            AgentscopeSessionsEntity entity = new AgentscopeSessionsEntity();
            entity.setSessionId(sessionId);
            entity.setStateKey(key);
            entity.setItemIndex(SINGLE_STATE_INDEX);
            entity.setStateData(json);

            // 核心修复2：BaseMapper 的方法名是 insertOrUpdate，不是 saveOrUpdate
            sessionMapper.insertOrUpdate(entity);

        } catch (Exception e) {
            throw new RuntimeException(STR."Failed to save state: \{key}", e);
        }
    }

    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        String sessionId = sessionKey.toIdentifier();
        validateSessionId(sessionId);
        validateStateKey(key);

        if (values.isEmpty()) return;

        String hashKey = key + HASH_KEY_SUFFIX;

        try {
            String currentHash = ListHashUtil.computeHash(values);
            String storedHash = getStoredHash(sessionId, hashKey);
            int existingCount = getListCount(sessionId, key);

            boolean needsFullRewrite = ListHashUtil.needsFullRewrite(
                    currentHash, storedHash, values.size(), existingCount);

            if (needsFullRewrite) {
                Db.tx(() -> {
                    deleteListItems(sessionId, key);
                    insertAllItems(sessionId, key, values);
                    saveHash(sessionId, hashKey, currentHash);
                    return true;
                });
            } else if (values.size() > existingCount) {
                List<? extends State> newItems = values.subList(existingCount, values.size());
                insertItems(sessionId, key, newItems, existingCount);
                saveHash(sessionId, hashKey, currentHash);
            }
        } catch (Exception e) {
            throw new RuntimeException(STR."Failed to save list: \{key}", e);
        }
    }

    // --- 辅助方法 ---

    private String getStoredHash(String sessionId, String hashKey) {
        // 使用 COL_xxx 对象，现在 .eq() 可以正常使用了
        AgentscopeSessionsEntity entity = sessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .select(COL_STATE_DATA)
                        .where(COL_SESSION_ID.eq(sessionId))
                        .and(COL_STATE_KEY.eq(hashKey))
                        .and(COL_ITEM_INDEX.eq(SINGLE_STATE_INDEX))
        );
        return entity != null ? entity.getStateData() : null;
    }

    private int getListCount(String sessionId, String key) {
        // SELECT MAX(item_index) ...
        // 注意：这里用 select("MAX(item_index)") 字符串形式比较方便
        Object maxIndexObj = sessionMapper.selectObjectByQuery(
                QueryWrapper.create()
                        .select("MAX(item_index)")
                        .where(COL_SESSION_ID.eq(sessionId))
                        .and(COL_STATE_KEY.eq(key))
        );

        if (maxIndexObj == null) return 0;
        return (Integer) maxIndexObj + 1;
    }

    private void deleteListItems(String sessionId, String key) {
        sessionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(COL_SESSION_ID.eq(sessionId))
                        .and(COL_STATE_KEY.eq(key))
        );
    }

    private void insertAllItems(String sessionId, String key, List<? extends State> values) {
        insertItems(sessionId, key, values, 0);
    }

    private void insertItems(String sessionId, String key, List<? extends State> items, int startIndex) {
        if (items.isEmpty()) return;

        List<AgentscopeSessionsEntity> entities = new ArrayList<>();
        int index = startIndex;
        for (State item : items) {
            String json = JsonUtils.getJsonCodec().toJson(item);

            AgentscopeSessionsEntity entity = new AgentscopeSessionsEntity();
            entity.setSessionId(sessionId);
            entity.setStateKey(key);
            entity.setItemIndex(index++);
            entity.setStateData(json);

            entities.add(entity);
        }
        sessionMapper.insertBatch(entities);
    }

    private void saveHash(String sessionId, String hashKey, String hash) {
        AgentscopeSessionsEntity entity = new AgentscopeSessionsEntity();
        entity.setSessionId(sessionId);
        entity.setStateKey(hashKey);
        entity.setItemIndex(SINGLE_STATE_INDEX);
        entity.setStateData(hash);
        // 核心修复2
        sessionMapper.insertOrUpdate(entity);
    }

    // --- 标准接口实现 ---

    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        String sessionId = sessionKey.toIdentifier();
        validateSessionId(sessionId);
        validateStateKey(key);

        AgentscopeSessionsEntity entity = sessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .select(COL_STATE_DATA)
                        .where(COL_SESSION_ID.eq(sessionId))
                        .and(COL_STATE_KEY.eq(key))
                        .and(COL_ITEM_INDEX.eq(SINGLE_STATE_INDEX))
        );

        if (entity != null) {
            return Optional.of(JsonUtils.getJsonCodec().fromJson(entity.getStateData(), type));
        }
        return Optional.empty();
    }

    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        String sessionId = sessionKey.toIdentifier();
        validateSessionId(sessionId);
        validateStateKey(key);

        List<AgentscopeSessionsEntity> entities = sessionMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(COL_STATE_DATA)
                        .where(COL_SESSION_ID.eq(sessionId))
                        .and(COL_STATE_KEY.eq(key))
                        .orderBy(COL_ITEM_INDEX.asc())
        );

        List<T> result = new ArrayList<>();
        for (AgentscopeSessionsEntity entity : entities) {
            result.add(JsonUtils.getJsonCodec().fromJson(entity.getStateData(), itemType));
        }
        return result;
    }

    @Override
    public boolean exists(SessionKey sessionKey) {
        String sessionId = sessionKey.toIdentifier();
        validateSessionId(sessionId);

        long count = sessionMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(COL_SESSION_ID.eq(sessionId))
        );
        return count > 0;
    }

    @Override
    public void delete(SessionKey sessionKey) {
        String sessionId = sessionKey.toIdentifier();
        validateSessionId(sessionId);

        sessionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(COL_SESSION_ID.eq(sessionId))
        );
    }

    public void deleteBySessionId(String sessionId) {

        validateSessionId(sessionId);

        sessionMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(COL_SESSION_ID.eq(sessionId))
        );
    }

    @Override
    public Set<SessionKey> listSessionKeys() {
        // SELECT DISTINCT session_id
        List<String> ids = sessionMapper.selectObjectListByQueryAs(
                QueryWrapper.create()
                        .select("DISTINCT session_id")
                        .orderBy(COL_SESSION_ID.asc()),
                String.class
        );

        return ids.stream()
                .map(SimpleSessionKey::of)
                .collect(Collectors.toSet());
    }
    public int clearAllSessions() {
        return sessionMapper.deleteByQuery(QueryWrapper.create());
    }

    @Override
    public void close() {}
    // 校验逻辑
    protected void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) throw new IllegalArgumentException("Session ID empty");
        if (sessionId.contains("/") || sessionId.contains("\\")) throw new IllegalArgumentException("Invalid Session ID");
        if (sessionId.length() > 255) throw new IllegalArgumentException("Session ID too long");
    }

    private void validateStateKey(String key) {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Key empty");
        if (key.length() > 255) throw new IllegalArgumentException("Key too long");
    }
}