package ch.heigvd.iict.dma.labo1.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.heigvd.iict.dma.labo1.models.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.protobuf.empty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import kotlin.system.measureTimeMillis

class GraphQLRepository(
    private val scope: CoroutineScope,
    private val httpsUrl: String = "https://mobile.iict.ch/graphql"
) {

    private val TAG = this.javaClass.simpleName.toString()
    private val _working = MutableLiveData(false)
    val working: LiveData<Boolean> get() = _working

    private val _authors = MutableLiveData<List<Author>>(emptyList())
    val authors: LiveData<List<Author>> get() = _authors

    private val _books = MutableLiveData<List<Book>>(emptyList())
    val books: LiveData<List<Book>> get() = _books

    private val _requestDuration = MutableLiveData(-1L)
    val requestDuration: LiveData<Long> get() = _requestDuration

    fun resetRequestDuration() {
        _requestDuration.postValue(-1L)
    }

    /**
     * Finds the id and name of all authors in the database
     * @author Quentin Surdez
     */
    fun loadAllAuthorsList() {
        scope.launch(Dispatchers.Default) {
            _working.postValue(true)
            val elapsed = measureTimeMillis {
                withContext(Dispatchers.IO) {
                    val query = """{ findAllAuthors { id name } }"""

                    executeQuery(query)
                        ?.getAsJsonObject("data")
                        ?.getAsJsonArray("findAllAuthors")
                        ?.map { it.asJsonObject }
                        ?.map {
                            Author(
                                id = it.get("id").asInt,
                                name = it.get("name").asString,
                                books = emptyList()
                            )
                        }
                        ?.also { _authors.postValue(it) }

                }
            }
            _working.postValue(false)
            _requestDuration.postValue(elapsed)
        }
    }

    /**
     * Finds all books from a given author
     * @author Quentin Surdez
     * @param author the author to search
     */
    fun loadBooksFromAuthor(author: Author) {
        scope.launch(Dispatchers.Default) {
            _working.postValue(true)
            val elapsed = measureTimeMillis {
                withContext(Dispatchers.IO) {
                    val query = """
                        {
                            findAuthorById(id: ${author.id}) {
                                books {
                                    id
                                    title
                                    publicationDate
                                    authors { id name }
                                }
                            }
                        }
                    """.trimIndent()

                    // Find books from author
                    executeQuery(query)
                        ?.getAsJsonObject("data")
                        ?.getAsJsonObject("findAuthorById")
                        ?.getAsJsonArray("books")
                        ?.map { it.asJsonObject }
                        ?.map { bookJson ->
                            Book(
                                id = bookJson.get("id").asInt,
                                title = bookJson.get("title").asString,
                                publicationDate = bookJson.get("publicationDate").asString,
                                authors = bookJson.getAsJsonArray("authors")
                                    .map { it.asJsonObject }
                                    .map {
                                        Author(
                                            id = it.get("id").asInt,
                                            name = it.get("name").asString,
                                            books = emptyList()
                                        )
                                    }
                            )
                        }
                        ?.also { _books.postValue(it) }
                }
            }
            _working.postValue(false)
            _requestDuration.postValue(elapsed)
        }
    }


    /**
     * Opens a URL connection to graphQL and posts a given query
     * @author Quentin Surdez
     * @param query the query to execute
     */
    private fun executeQuery(query: String): JsonObject? {
        val body = """{"query":${Gson().toJson(query)}}"""

        return try {
            val connection = (URL(httpsUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 30_000
            }

            connection.getOutputStream().use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "GraphQL request failed with HTTP ${connection.responseCode}")
                return null
            }

            connection.getInputStream().bufferedReader().use { reader ->
                JsonParser.parseString(reader.readText()).asJsonObject
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during GraphQL query", e)
            null
        }
    }

}