package com.promoapp.data.remote

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** Executa uma chamada de rede e converte falhas esperadas em [Result], sem engolir cancelamentos. */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Result.failure(e)
} catch (e: HttpException) {
    Result.failure(e)
}
