package com.example.iot.network

import android.content.Context
import com.example.iot.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {

    companion object {
        private const val BASE_URL = "http://47.118.22.220:8091/api/"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // SharedPreferences配置
        private const val PREFS_NAME = "login_prefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 获取Token
    fun getToken(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, "") ?: ""
    }

    // 保存Token
    fun saveToken(token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    // 获取用户名
    fun getUsername(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }

    // 保存用户名
    fun saveUsername(username: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    // 清除登录信息
    fun clearLoginInfo() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // 构建带Token的请求
    private fun buildRequest(url: String, method: String, body: String? = null): Request {
        val builder = Request.Builder().url(url)
        val token = getToken()
        if (token.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $token")
            builder.addHeader("Cookie", "token=$token")
        }
        if (body != null) {
            builder.method(method, body.toRequestBody(JSON))
        } else {
            builder.method(method, null)
        }
        return builder.build()
    }

    // ==================== 用户管理 ====================

    // 获取用户信息
    fun getUserInfo(callback: (Boolean, String, UserInfo?) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}user/info", "GET")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    if (json.getInt("code") == 200) {
                        val data = json.getJSONObject("data")
                        val userInfo = UserInfo(
                            email = data.getString("email"),
                            username = data.optString("username", ""),
                            createdAt = data.optString("created_at", "")
                        )
                        callback(true, json.getString("msg"), userInfo)
                    } else {
                        callback(false, json.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 更新用户信息
    fun updateUserInfo(username: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("username", username)
                }
                val request = buildRequest("${BASE_URL}user/update", "POST", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    callback(resJson.getInt("code") == 200, resJson.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // 登出账号
    fun logout(callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}user/logout", "POST", "{}")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    val success = json.getInt("code") == 200
                    if (success) {
                        clearLoginInfo()
                    }
                    callback(success, json.getString("msg"))
                } else {
                    clearLoginInfo()
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                clearLoginInfo()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // ==================== 角色卡管理 ====================

    // 获取角色卡列表
    fun getCharacterList(callback: (Boolean, String, List<Character>?) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}character/list", "GET")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    if (json.getInt("code") == 200) {
                        val characters = mutableListOf<Character>()
                        val dataArray = json.getJSONArray("data")
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            characters.add(
                                Character(
                                    id = item.getString("id"),
                                    name = item.getString("name"),
                                    description = item.getString("description"),
                                    scenario = item.optString("scenario", ""),
                                    avatarUrl = item.optString("avatar_url", ""),
                                    createdAt = item.optString("created_at", "")
                                )
                            )
                        }
                        callback(true, json.getString("msg"), characters)
                    } else {
                        callback(false, json.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 创建角色卡
    fun createCharacter(
        name: String,
        description: String,
        scenario: String,
        avatarUrl: String,
        callback: (Boolean, String, Character?) -> Unit
    ) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("name", name)
                    put("description", description)
                    put("scenario", scenario)
                    put("avatar_url", avatarUrl)
                }
                val request = buildRequest("${BASE_URL}character/create", "POST", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    if (resJson.getInt("code") == 200) {
                        val data = resJson.getJSONObject("data")
                        val character = Character(
                            id = data.getString("id"),
                            name = data.getString("name"),
                            description = data.getString("description"),
                            scenario = data.optString("scenario", ""),
                            avatarUrl = data.optString("avatar_url", ""),
                            createdAt = data.optString("created_at", "")
                        )
                        callback(true, resJson.getString("msg"), character)
                    } else {
                        callback(false, resJson.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 获取角色卡详情
    fun getCharacterDetail(id: String, callback: (Boolean, String, Character?) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}character/detail/$id", "GET")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    if (json.getInt("code") == 200) {
                        val data = json.getJSONObject("data")
                        val character = Character(
                            id = data.getString("id"),
                            name = data.getString("name"),
                            description = data.getString("description"),
                            scenario = data.optString("scenario", ""),
                            avatarUrl = data.optString("avatar_url", ""),
                            createdAt = data.optString("created_at", "")
                        )
                        callback(true, json.getString("msg"), character)
                    } else {
                        callback(false, json.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 更新角色卡
    fun updateCharacter(
        id: String,
        name: String,
        description: String,
        scenario: String,
        avatarUrl: String,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("name", name)
                    put("description", description)
                    put("scenario", scenario)
                    put("avatar_url", avatarUrl)
                }
                val request = buildRequest("${BASE_URL}character/update/$id", "PUT", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    callback(resJson.getInt("code") == 200, resJson.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // 删除角色卡
    fun deleteCharacter(id: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}character/delete/$id", "DELETE", "{}")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    callback(json.getInt("code") == 200, json.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // ==================== 对话管理 ====================

    // 发送消息
    fun sendChatMessage(
        text: String,
        userId: String,
        characterId: String,
        sessionId: String,
        callback: (Boolean, String, ChatSendResponse?) -> Unit
    ) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("text", text)
                    put("user_id", userId)
                    put("character_id", characterId)
                    put("session_id", sessionId)
                }
                val request = buildRequest("${BASE_URL}chat/send", "POST", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    if (resJson.getInt("code") == 200) {
                        val data = resJson.getJSONObject("data")
                        val chatResponse = ChatSendResponse(
                            reply = data.getString("reply"),
                            sessionId = data.optString("session_id", sessionId)
                        )
                        callback(true, resJson.getString("msg"), chatResponse)
                    } else {
                        callback(false, resJson.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 创建新会话
    fun createSession(
        title: String,
        characterId: String,
        callback: (Boolean, String, Session?) -> Unit
    ) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("title", title)
                    put("character_id", characterId)
                }
                val request = buildRequest("${BASE_URL}chat/session/create", "POST", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    if (resJson.getInt("code") == 200) {
                        val data = resJson.getJSONObject("data")
                        val session = Session(
                            sessionId = data.getString("session_id"),
                            title = data.getString("title"),
                            characterName = "",
                            preview = "",
                            lastMessageTime = data.optString("created_at", ""),
                            isPinned = false
                        )
                        callback(true, resJson.getString("msg"), session)
                    } else {
                        callback(false, resJson.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 获取会话列表
    fun getSessionList(callback: (Boolean, String, SessionListResponse?) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}chat/session/list", "GET")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    if (json.getInt("code") == 200) {
                        val data = json.getJSONObject("data")
                        val sessionListResponse = SessionListResponse(
                            pinned = parseSessionArray(data.optJSONArray("pinned")),
                            today = parseSessionArray(data.optJSONArray("today")),
                            yesterday = parseSessionArray(data.optJSONArray("yesterday"))
                        )
                        callback(true, json.getString("msg"), sessionListResponse)
                    } else {
                        callback(false, json.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 获取会话详情
    fun getSessionDetail(sessionId: String, callback: (Boolean, String, SessionDetail?) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}chat/session/detail/$sessionId", "GET")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    if (json.getInt("code") == 200) {
                        val data = json.getJSONObject("data")
                        val messages = mutableListOf<Message>()
                        val messagesArray = data.optJSONArray("messages")
                        if (messagesArray != null) {
                            for (i in 0 until messagesArray.length()) {
                                val msg = messagesArray.getJSONObject(i)
                                messages.add(
                                    Message(
                                        id = msg.getString("id"),
                                        content = msg.getString("content"),
                                        isUser = msg.getBoolean("is_user"),
                                        createdAt = msg.optString("created_at", "")
                                    )
                                )
                            }
                        }
                        val sessionDetail = SessionDetail(
                            sessionId = data.getString("session_id"),
                            title = data.getString("title"),
                            characterId = data.optString("character_id", ""),
                            messages = messages
                        )
                        callback(true, json.getString("msg"), sessionDetail)
                    } else {
                        callback(false, json.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 更新会话
    fun updateSession(
        sessionId: String,
        title: String,
        isPinned: Boolean,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("title", title)
                    put("is_pinned", isPinned)
                }
                val request = buildRequest("${BASE_URL}chat/session/update/$sessionId", "PUT", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    callback(resJson.getInt("code") == 200, resJson.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // 删除会话
    fun deleteSession(sessionId: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}chat/session/delete/$sessionId", "DELETE", "{}")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    callback(json.getInt("code") == 200, json.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // 删除所有会话
    fun deleteAllSessions(callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}chat/session/delete-all", "DELETE", "{}")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    callback(json.getInt("code") == 200, json.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // 搜索会话
    fun searchSessions(keyword: String, callback: (Boolean, String, List<Session>?) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}chat/session/search?keyword=$keyword", "GET")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    if (json.getInt("code") == 200) {
                        val sessions = parseSessionArray(json.optJSONArray("data"))
                        callback(true, json.getString("msg"), sessions)
                    } else {
                        callback(false, json.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // ==================== 记忆管理 ====================

    // 获取记忆列表
    fun getMemoryList(characterId: String, category: String? = null, callback: (Boolean, String, List<Memory>?) -> Unit) {
        Thread {
            try {
                var url = "${BASE_URL}memory/list?character_id=$characterId"
                if (category != null) {
                    url += "&category=$category"
                }
                val request = buildRequest(url, "GET")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    if (json.getInt("code") == 200) {
                        val memories = mutableListOf<Memory>()
                        val dataArray = json.getJSONArray("data")
                        for (i in 0 until dataArray.length()) {
                            val item = dataArray.getJSONObject(i)
                            memories.add(
                                Memory(
                                    id = item.getString("id"),
                                    content = item.getString("content"),
                                    category = item.optString("category", ""),
                                    enabled = item.optBoolean("enabled", true),
                                    createdAt = item.optString("created_at", "")
                                )
                            )
                        }
                        callback(true, json.getString("msg"), memories)
                    } else {
                        callback(false, json.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 添加记忆
    fun addMemory(
        characterId: String,
        content: String,
        category: String,
        callback: (Boolean, String, String?) -> Unit
    ) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("character_id", characterId)
                    put("content", content)
                    put("category", category)
                }
                val request = buildRequest("${BASE_URL}memory/add", "POST", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    if (resJson.getInt("code") == 200) {
                        val id = resJson.getJSONObject("data").getString("id")
                        callback(true, resJson.getString("msg"), id)
                    } else {
                        callback(false, resJson.getString("msg"), null)
                    }
                } else {
                    callback(false, "网络错误", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}", null)
            }
        }.start()
    }

    // 更新记忆
    fun updateMemory(id: String, content: String, enabled: Boolean, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("content", content)
                    put("enabled", enabled)
                }
                val request = buildRequest("${BASE_URL}memory/update/$id", "PUT", json.toString())
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    callback(resJson.getInt("code") == 200, resJson.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // 删除记忆
    fun deleteMemory(id: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}memory/delete/$id", "DELETE", "{}")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    callback(json.getInt("code") == 200, json.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // 清空角色记忆
    fun clearMemories(characterId: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val request = buildRequest("${BASE_URL}memory/clear/$characterId", "DELETE", "{}")
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)
                    callback(json.getInt("code") == 200, json.getString("msg"))
                } else {
                    callback(false, "网络错误")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, "请求失败: ${e.message}")
            }
        }.start()
    }

    // ==================== 辅助方法 ====================

    private fun parseSessionArray(array: org.json.JSONArray?): List<Session> {
        val sessions = mutableListOf<Session>()
        if (array == null) return sessions
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            sessions.add(
                Session(
                    sessionId = item.getString("session_id"),
                    title = item.getString("title"),
                    characterName = item.optString("character_name", ""),
                    preview = item.optString("preview", ""),
                    lastMessageTime = item.optString("last_message_time", ""),
                    isPinned = item.optBoolean("is_pinned", false)
                )
            )
        }
        return sessions
    }
}

// 用户信息数据模型
data class UserInfo(
    val email: String,
    val username: String,
    val createdAt: String
)

// 记忆数据模型
data class Memory(
    val id: String,
    val content: String,
    val category: String,
    val enabled: Boolean,
    val createdAt: String
)