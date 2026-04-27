package uesugi.common.extend

interface IVision {
    val id: String
    suspend fun vision(prompt: String, url: String): String
}