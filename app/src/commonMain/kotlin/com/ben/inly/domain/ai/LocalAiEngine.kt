package com.ben.inly.domain.ai

import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.GenStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalAiEngine {

    private val generatorFileName = "qwen2.5-1.5b-instruct-q8_0.gguf"
    private val embedderFileName = "nomic-embed-text-v1.5.Q8_0.gguf"

    private val nativeMutex = Mutex()
    private var loadedModel: LoadedModel = LoadedModel.NONE

    private enum class LoadedModel { NONE, EMBEDDER, GENERATOR }

    // Embedding
    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>> =
        nativeMutex.withLock {
            withContext(Dispatchers.Default) {
                if (texts.isEmpty()) return@withContext emptyList()

                ensureEmbedderLoaded()
                texts.map { LlamaBridge.embed(it).toList() }
            }
        }

    suspend fun generateEmbedding(text: String): List<Float> =
        generateEmbeddings(listOf(text)).firstOrNull() ?: emptyList()

    // Generation (streaming)
    fun generateResponseStream(
        systemPrompt: String,
        userQuestion: String,
        contextBlock: String
    ): Flow<String> = callbackFlow {
        nativeMutex.withLock {
            ensureGeneratorLoaded()

            val formattedPrompt = LlamaBridge.applyChatTemplate(
                messages = listOf(
                    "system" to "$systemPrompt\n\n$contextBlock",
                    "user" to userQuestion
                ),
                addAssistantPrefix = true
            ) ?: "$systemPrompt\n\n$contextBlock\n\nUser: $userQuestion\n\nAssistant:"

            LlamaBridge.generateStream(
                prompt = formattedPrompt,
                callback = object : GenStream {
                    override fun onDelta(text: String) { trySend(text) }
                    override fun onComplete()           { close() }
                    override fun onError(message: String) { close(Exception(message)) }
                }
            )
        }

        awaitClose()
    }.flowOn(Dispatchers.Default)

    suspend fun shutdown() = nativeMutex.withLock {
        if (loadedModel != LoadedModel.NONE) {
            LlamaBridge.shutdown()
            loadedModel = LoadedModel.NONE
        }
    }

    // Private helpers
    private fun ensureEmbedderLoaded() {
        if (loadedModel == LoadedModel.EMBEDDER) return

        val embedderPath = resolveModelPath(embedderFileName)
        if (!modelFileExists(embedderPath)) {
            throw IllegalStateException("Embedding model not found at $embedderPath")
        }

        if (loadedModel == LoadedModel.GENERATOR) {
            LlamaBridge.shutdown()
            loadedModel = LoadedModel.NONE
        }

        val loaded = LlamaBridge.initEmbedModel(embedderPath)
        if (!loaded) throw IllegalStateException(
            "Failed to load native embedding model. Check memory constraints or model corruption."
        )
        loadedModel = LoadedModel.EMBEDDER
    }

    private fun ensureGeneratorLoaded() {
        if (loadedModel == LoadedModel.GENERATOR) return

        val generatorPath = resolveModelPath(generatorFileName)
        if (!modelFileExists(generatorPath)) {
            throw IllegalStateException("Generator model not found at $generatorPath")
        }

        // If embedder is loaded, free it first
        if (loadedModel == LoadedModel.EMBEDDER) {
            LlamaBridge.shutdown()
            loadedModel = LoadedModel.NONE
        }

        LlamaBridge.updateGenerateParams(
            temperature    = 0.3f,
            maxTokens      = 512,
            topP           = 0.95f,
            topK           = 40,
            repeatPenalty  = 1.1f,
            contextLength  = 4096,
            numThreads     = 6,
            useMmap        = true,
            flashAttention = true,
            batchSize      = 512,
            gpuLayers      = 0
        )

        val loaded = LlamaBridge.initGenerateModel(generatorPath)
        if (!loaded) throw IllegalStateException("Failed to load native Phi-4 model.")
        loadedModel = LoadedModel.GENERATOR
    }

    suspend fun warmUpGenerator() = nativeMutex.withLock {
        withContext(Dispatchers.Default) {
            ensureGeneratorLoaded()
        }
    }

    fun isModelAvailable(): Boolean {
        return try {
            modelFileExists(resolveModelPath(generatorFileName)) &&
                    modelFileExists(resolveModelPath(embedderFileName))
        } catch (e: Exception) {
            false
        }
    }
}