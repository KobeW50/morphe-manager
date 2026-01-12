package app.revanced.manager.domain.manager.base

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.domain.manager.base.BasePreferencesManager.Companion.editor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Global cache for DataStore instances to prevent multiple instances for same file
private val dataStoreCache = mutableMapOf<String, DataStore<Preferences>>()
private val dataStoreLock = Any()

// Stub KProperty for DataStore delegate
private object KProperty0Stub : kotlin.reflect.KProperty0<DataStore<Preferences>> {
    override val name: String = "dataStore"
    override fun get(): DataStore<Preferences> = throw UnsupportedOperationException()
    override val annotations: List<Annotation> = emptyList()
    override val isAbstract: Boolean = false
    override val isFinal: Boolean = true
    override val isOpen: Boolean = false
    override val isSuspend: Boolean = false
    override val isConst: Boolean = false
    override val isLateinit: Boolean = false
    override val returnType: kotlin.reflect.KType
        get() = throw UnsupportedOperationException()
    override val typeParameters: List<kotlin.reflect.KTypeParameter> = emptyList()
    override val visibility: kotlin.reflect.KVisibility? = null
    override val parameters: List<kotlin.reflect.KParameter> = emptyList()
    override val getter: kotlin.reflect.KProperty0.Getter<DataStore<Preferences>>
        get() = throw UnsupportedOperationException()
    override fun getDelegate(): Any = throw UnsupportedOperationException()
    override fun invoke(): DataStore<Preferences> = throw UnsupportedOperationException()
    override fun call(vararg args: Any?): DataStore<Preferences> = throw UnsupportedOperationException()
    override fun callBy(args: Map<kotlin.reflect.KParameter, Any?>): DataStore<Preferences> = throw UnsupportedOperationException()
}

abstract class BasePreferencesManager(context: Context, name: String) {
    protected val dataStore: DataStore<Preferences> = synchronized(dataStoreLock) {
        dataStoreCache.getOrPut(name) {
            val appContext = context.applicationContext
            val property = preferencesDataStore(name = name)
            property.getValue(appContext, KProperty0Stub)
        }
    }

    suspend fun preload() {
        dataStore.data.first()
    }

    suspend fun edit(block: EditorContext.() -> Unit) = dataStore.editor(block)

    protected fun stringPreference(key: String, default: String) =
        StringPreference(dataStore, key, default)

    protected fun stringSetPreference(key: String, default: Set<String>) =
        StringSetPreference(dataStore, key, default)

    protected fun booleanPreference(key: String, default: Boolean) =
        BooleanPreference(dataStore, key, default)

    protected fun intPreference(key: String, default: Int) = IntPreference(dataStore, key, default)

    protected fun longPreference(key: String, default: Long) =
        LongPreference(dataStore, key, default)

    protected fun floatPreference(key: String, default: Float) =
        FloatPreference(dataStore, key, default)

    protected inline fun <reified E : Enum<E>> enumPreference(
        key: String,
        default: E
    ) = EnumPreference(dataStore, key, default, enumValues())

    companion object {
        suspend inline fun DataStore<Preferences>.editor(crossinline block: EditorContext.() -> Unit) {
            edit {
                EditorContext(it).run(block)
            }
        }
    }
}

class EditorContext(private val prefs: MutablePreferences) {
    var <T> Preference<T>.value
        get() = prefs.run { read() }
        set(value) = prefs.run { write(value) }

    operator fun Preference<Set<String>>.plusAssign(value: String) = prefs.run {
        write(read() + value)
    }
}

abstract class Preference<T>(
    private val dataStore: DataStore<Preferences>,
    val default: T
) {
    internal abstract fun Preferences.read(): T
    internal abstract fun MutablePreferences.write(value: T)

    val flow = dataStore.data.map { with(it) { read() } ?: default }.distinctUntilChanged()

    suspend fun get() = flow.first()
    fun getBlocking() = runBlocking { get() }

    @Composable
    fun getAsState() = flow.collectAsStateWithLifecycle(initialValue = remember {
        getBlocking()
    })

    suspend fun update(value: T) = dataStore.editor {
        this@Preference.value = value
    }
}

class EnumPreference<E : Enum<E>>(
    dataStore: DataStore<Preferences>,
    key: String,
    default: E,
    private val enumValues: Array<E>
) : Preference<E>(dataStore, default) {
    private val key = stringPreferencesKey(key)
    override fun Preferences.read() =
        this[key]?.let { name ->
            enumValues.find { it.name == name }
        } ?: default

    override fun MutablePreferences.write(value: E) {
        this[key] = value.name
    }
}

abstract class BasePreference<T>(dataStore: DataStore<Preferences>, default: T) :
    Preference<T>(dataStore, default) {
    protected abstract val key: Preferences.Key<T>
    override fun Preferences.read() = this[key] ?: default
    override fun MutablePreferences.write(value: T) {
        this[key] = value
    }
}

class StringPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: String
) : BasePreference<String>(dataStore, default) {
    override val key = stringPreferencesKey(key)
}

class StringSetPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Set<String>
) : BasePreference<Set<String>>(dataStore, default) {
    override val key = stringSetPreferencesKey(key)
}

class BooleanPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Boolean
) : BasePreference<Boolean>(dataStore, default) {
    override val key = booleanPreferencesKey(key)
}

class IntPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Int
) : BasePreference<Int>(dataStore, default) {
    override val key = intPreferencesKey(key)
}

class LongPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Long
) : BasePreference<Long>(dataStore, default) {
    override val key = longPreferencesKey(key)
}

class FloatPreference(
    dataStore: DataStore<Preferences>,
    key: String,
    default: Float
) : BasePreference<Float>(dataStore, default) {
    override val key = floatPreferencesKey(key)
}
