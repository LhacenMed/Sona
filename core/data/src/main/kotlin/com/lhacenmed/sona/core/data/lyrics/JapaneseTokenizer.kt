package com.lhacenmed.sona.core.data.lyrics

import com.atilika.kuromoji.dict.CharacterDefinitions
import com.atilika.kuromoji.dict.ConnectionCosts
import com.atilika.kuromoji.dict.InsertedDictionary
import com.atilika.kuromoji.dict.TokenInfoDictionary
import com.atilika.kuromoji.dict.UnknownDictionary
import com.atilika.kuromoji.ipadic.Tokenizer
import com.atilika.kuromoji.trie.DoubleArrayTrie
import com.atilika.kuromoji.util.ResourceResolver
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile

/**
 * The Kuromoji tokenizer Japanese lyrics are romanized with - over the dictionary [JapaneseDictionary]
 * downloads, rather than one shipped in the app. Built the first time it is wanted, once per dictionary.
 */
internal object JapaneseTokenizer {
    @Volatile
    private var dictionary: File? = null

    @Volatile
    private var tokenizer: Tokenizer? = null

    /** Uses the dictionary at [file] from the next romanization on. */
    fun use(file: File) {
        synchronized(this) {
            dictionary = file
            tokenizer = null
        }
    }

    fun release() {
        synchronized(this) {
            dictionary = null
            tokenizer = null
        }
    }

    /** The tokenizer, or null while there is no dictionary to build it over. */
    fun get(): Tokenizer? {
        tokenizer?.let { return it }
        return synchronized(this) {
            tokenizer ?: dictionary?.let { file -> ZipFile(file).use { DownloadedDictionaryBuilder(it).build() } }
                .also { tokenizer = it }
        }
    }

    /**
     * Kuromoji's IPADIC builder, reading the dictionary out of the downloaded archive rather than the app's
     * own classpath. Its `loadDictionaries` always reads the classpath, so this one is the same steps - the
     * same penalties, the same five parts, in the same order - over the archive instead; every reading it
     * gives is the one the shipped dictionary gave.
     */
    private class DownloadedDictionaryBuilder(private val archive: ZipFile) : Tokenizer.Builder() {
        override fun loadDictionaries() {
            // Kuromoji's own defaults: kanji runs longer than 2 cost 3000, other runs longer than 7 cost 1700.
            penalties = mutableListOf(2, 3000, 7, 1700)
            resolver = ResourceResolver { name ->
                val entry = archive.getEntry("$DictionaryPath$name") ?: throw FileNotFoundException(name)
                archive.getInputStream(entry)
            }
            doubleArrayTrie = DoubleArrayTrie.newInstance(resolver)
            connectionCosts = ConnectionCosts.newInstance(resolver)
            tokenInfoDictionary = TokenInfoDictionary.newInstance(resolver)
            characterDefinitions = CharacterDefinitions.newInstance(resolver)
            unknownDictionary = UnknownDictionary.newInstance(resolver, characterDefinitions, totalFeatures)
            insertedDictionary = InsertedDictionary(totalFeatures)
        }
    }

    private const val DictionaryPath = "com/atilika/kuromoji/ipadic/"
}
